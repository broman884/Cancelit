package com.cancelit.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.cancelit.app.BuildConfig
import com.cancelit.app.MainActivity
import com.cancelit.app.ai.AiFeature
import com.cancelit.app.ai.GeminiClient
import com.cancelit.app.ai.ScreenSnippet
import com.cancelit.app.ai.SmartKeywordStore
import com.cancelit.app.ai.SmartKeywords
import com.cancelit.app.ai.SmartVerdict
import com.cancelit.app.ai.StudyTopicCheck
import com.cancelit.app.clockTime
import com.cancelit.app.data.BuiltInApps
import com.cancelit.app.data.FocusStore
import com.cancelit.app.data.GRACE_WARNING_MS
import com.cancelit.app.data.PRESENCE_INTERVAL_MS
import com.cancelit.app.data.PRESENCE_MAX_MISSES
import com.cancelit.app.data.PRESENCE_TIMEOUT_MS
import com.cancelit.app.data.SEVERE_BLOCK_MS
import com.cancelit.app.data.STUDY_CHECK_INTERVAL_MS
import com.cancelit.app.describeDuration
import com.cancelit.app.formatDuration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val GRACE_TICK_MS = 1_000L
private const val ENFORCEMENT_INTERVAL_MS = 5_000L
private const val STUDY_TICK_MS = 5_000L
private const val STUDY_CAPTURE_THROTTLE_MS = 3_000L
/** Each Study capture keeps this much of the screen's text. */
private const val STUDY_SNIPPET_CHARS = 140
/** Captures kept for the next topic check; it only sends the newest few anyway. */
private const val STUDY_MAX_SCREENS = 50
private const val PRESENCE_TICK_MS = 1_000L
/** With no Study session running, the check-in loop looks this often instead. */
private const val PRESENCE_IDLE_TICK_MS = 5_000L

// Throttles and caps that keep the service from hammering the CPU or the window manager on
// chatty apps.
private const val BROWSER_SCAN_THROTTLE_MS = 4_000L
private const val HOME_KICK_THROTTLE_MS = 1_200L
private const val MAX_SCREEN_TEXT_CHARS = 2_000
private const val MAX_NODE_DEPTH = 24

// --- Keyword blocker -----------------------------------------------------------------------
// Typing a blocked word in any other app sends the user home and locks that app for the
// lockdown time. Apps aren't force-stopped: since Android 14 an app can only kill its own
// processes, so "closed" means sent home and kept out by the enforcement loop.
private const val KEYWORD_HIT_COOLDOWN_MS = 3_000L
/** After "I'm fine" on an AI match, the same topic in the same app doesn't ask again for this long. */
private const val AI_SNOOZE_MS = 10 * 60_000L
private const val MAX_TYPED_CHARS = 2_000

// Smart (Gemini) keyword detection. Text is only checked once typing pauses, only when it's
// short and not just everyday words, and at most AI_MAX_CALLS_PER_MIN times a minute (plus the
// hourly/daily caps in GeminiClient.kt), so it can't flood the API.
private const val AI_TYPING_PAUSE_MS = 1_200L
private const val AI_MIN_CHARS = 3
internal const val AI_MAX_CHARS = 100
private const val AI_MAX_CALLS_PER_MIN = 6
private const val FOCUS_READ_THROTTLE_MS = 700L
/** Focused input boxes longer than this (an open document) aren't read at all. */
private const val FOCUS_READ_MAX_CHARS = 200

/** Phone, emergency and SMS apps. Never locked, sent home or keyword-checked, so CancelIt can
 *  never stand between the user and a call for help. The default dialer is added at runtime. */
private val ESSENTIAL_PACKAGES = setOf(
    "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer", "com.android.phone",
    "com.android.server.telecom", "com.android.emergency", "com.google.android.apps.safetyhub",
    "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"
)

/** How typed text matched a blocked topic. Only the user's own rules lock without asking. */
private enum class KeywordMatch { OWN_WORD, CONFIRMED, AI }

/** Browsers Browser safety watches. */
private val BROWSER_PACKAGES = setOf(
    "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
    "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.focus",
    "com.sec.android.app.sbrowser", "com.microsoft.emmx", "com.brave.browser",
    "com.opera.browser", "com.opera.mini.native", "com.opera.gx", "com.duckduckgo.mobile.android",
    "com.vivaldi.browser", "com.kiwibrowser.browser", "com.mi.globalbrowser", "com.UCMobile.intl"
)

/**
 * Matches [keyword] case-insensitively at the start of a word: "anime" catches "Anime" and
 * "animes" but not "brainanime". Keywords of three letters or fewer ("ass", "sex", "cat") must
 * stand alone (a plural "s"/"es" is fine), or they'd fire inside "assignment" or "category". The
 * keyword itself is escaped, so it's always literal text.
 */
internal fun keywordRegex(keyword: String): Regex {
    val start = "(?<![\\p{L}\\p{N}])"
    val word = Regex.escape(keyword)
    val pattern = if (keyword.length <= 3) "$start$word(?:e?s)?(?![\\p{L}\\p{N}])" else "$start$word"
    return Regex(pattern, RegexOption.IGNORE_CASE)
}

class FocusAccessibilityService : AccessibilityService() {
    private lateinit var store: FocusStore
    private val overlay by lazy { BlockOverlay(this) }
    private val timerChip by lazy { TimerChip(this) }
    private val notifications by lazy { FocusNotifications(this, store, ::appLabel) }
    private val studyCheck = StudyTopicCheck()

    /** Browser safety's fixed word list. Only used once the user turns Browser safety on. */
    private val severeTerms = Regex("\\b(porn|pornography|xxx|nsfw|explicit videos?)\\b", RegexOption.IGNORE_CASE)

    @Volatile private var lastForegroundPackage: String? = null
    @Volatile private var lastStudyCaptureAt = 0L
    @Volatile private var lastBrowserScanAt = 0L
    @Volatile private var lastHomeKickAt = 0L
    @Volatile private var lastKeywordHitAt = 0L
    private val keywordPatterns = ConcurrentHashMap<String, Regex>()
    @Volatile private var aiJob: Job? = null
    @Volatile private var pendingAiText: String? = null
    @Volatile private var lastFocusReadAt = 0L
    /** Related titles and names the AI listed for each blocked keyword (see SmartKeywords.kt). */
    private val smartStore by lazy { SmartKeywordStore(this) }

    private val aiCallTimes = ArrayDeque<Long>()

    /** Packages that got the "almost out" heads-up for their current allowance (grace loop only). */
    private val warnedPackages = mutableSetOf<String>()

    /** Screens seen during a Study session, newest first, for the next topic check. Memory only:
     *  each check takes them, and they're dropped whenever no session is running. */
    private val studyScreens = ArrayDeque<ScreenSnippet>()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Phone, emergency and SMS apps, including whatever the default dialer and SMS app are. */
    private val essentialPackages: Set<String> by lazy {
        val dialer = runCatching { getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull()
        ESSENTIAL_PACKAGES + setOfNotNull(dialer, sms)
    }

    /** Locking any of these would leave the phone unusable (no home screen, no way back into
     *  Settings to turn the service off, no way to call for help), so they're never enforced
     *  against. */
    private val criticalPackages: Set<String> by lazy {
        val launcher = runCatching {
            packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        }.getOrNull()
        setOfNotNull(packageName, "com.android.systemui", launcher) + essentialPackages
    }

    /** Extra packages that automatic rules (keywords, study drift, missed check-ins) must never
     *  lock, even though the user could still give them a time limit. Maps is here so a check-in
     *  can't cut off navigation mid-drive. */
    private val autoLockExempt: Set<String> by lazy {
        criticalPackages + setOf(
            "com.android.settings", "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.google.android.apps.maps"
        )
    }

    /** "pkg|topic" -> until when an AI match for that topic in that app stays quiet, after the
     *  user answered "I'm fine". */
    private val aiSnoozedUntil = ConcurrentHashMap<String, Long>()

    /** Receives the "Yes, I'm here" tap from the notification action. Registered at runtime and
     *  not exported, so nothing outside the app can fake a confirmation. */
    private val presenceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PRESENCE_ACK) return
            store.setPresenceAckAt(System.currentTimeMillis())
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Here rather than in onServiceConnected: events can arrive before that callback runs.
        store = FocusStore(this)
        GeminiClient.init(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ContextCompat.registerReceiver(
            this, presenceReceiver, IntentFilter(ACTION_PRESENCE_ACK), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        startEnforcementLoop()
        startStudyLoop()
        startPresenceLoop()
        startGraceLoop()
        startRelatedTermsLoop()
    }

    /** Re-checks whichever app is in front every few seconds, so a lock can't be outlasted by
     *  leaving the overlay dismissed and sitting on the app underneath. */
    private fun startEnforcementLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(ENFORCEMENT_INTERVAL_MS)
                activePackage()?.let { enforce(it) }
            }
        }
    }

    /**
     * The app whose window is active right now. The last accessibility event can't be trusted for
     * this: the status bar sends events too (clock, notification icons), and a playing video sends
     * almost none, so the last event often comes from SystemUI while the user is still watching.
     * That stopped the time-limit clock on real phones. Falls back to the last event's app.
     */
    private fun activePackage(): String? =
        runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: lastForegroundPackage

    /**
     * Ticks often, acts on the session's own clock. Anchoring to studyLastCheckAt (instead of
     * sleeping for the whole interval) means restarting the service mid-session doesn't reset
     * the cadence, and the countdown shown in the app is the real one.
     */
    private fun startStudyLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(STUDY_TICK_MS)
                if (!store.studyActive()) {
                    synchronized(studyScreens) { studyScreens.clear() }
                    continue
                }
                val last = store.studyLastCheckAt().takeIf { it > 0L } ?: store.studyStart()
                if (System.currentTimeMillis() - last >= STUDY_CHECK_INTERVAL_MS) {
                    store.setStudyLastCheckAt(System.currentTimeMillis())
                    runStudyCheck()
                }
            }
        }
    }

    /**
     * Smart detection, layer 1. For every blocked keyword the AI is asked, one facet at a time,
     * for what people type when looking for that topic: titles, names, sites and slang, and its
     * adult side ("anime" -> "attack on titan", "levi", "crunchyroll", "hentai", ...). The lists
     * are kept on the phone, so typing any of them is caught instantly, offline, without an AI
     * call. A failed facet retries with a growing delay.
     */
    private fun startRelatedTermsLoop() {
        serviceScope.launch {
            val nextTryAt = HashMap<String, Long>()
            val waitMs = HashMap<String, Long>()
            while (isActive) {
                delay(15_000)
                if (!store.smartKeywordsOn() || !GeminiClient.canCall(AiFeature.RELATED_TERMS)) continue
                val keywords = store.blockedKeywords()
                smartStore.prune(keywords)
                val now = System.currentTimeMillis()
                val (keyword, facet) = keywords.firstNotNullOfOrNull { k ->
                    smartStore.missingFacet(k)
                        ?.takeIf { now >= (nextTryAt["$k|$it"] ?: 0L) }
                        ?.let { k to it }
                } ?: continue

                val attempt = "$keyword|$facet"
                val terms = SmartKeywords.expand(keyword, facet)
                if (terms != null) {
                    smartStore.save(keyword, facet, terms)
                    waitMs.remove(attempt)
                } else {
                    val next = minOf((waitMs[attempt] ?: 60_000L) * 2, 2 * 60 * 60_000L)
                    waitMs[attempt] = next
                    nextTryAt[attempt] = now + next
                }
            }
        }
    }

    // ---------------- Grace allowance ----------------

    /**
     * While a protected app is in front and the screen is on, its used-time counter ticks up.
     * When it reaches the grace time the app is locked for the lockdown time and the counter
     * resets, so a fresh allowance starts once the lock ends. Alongside the counting it keeps a
     * live countdown notification and the floating chip up, and fires a one-time heads-up near
     * the end.
     */
    private fun startGraceLoop() {
        serviceScope.launch {
            var lastTick = System.currentTimeMillis()
            var notCountingTicks = 0
            while (isActive) {
                delay(GRACE_TICK_MS)
                val now = System.currentTimeMillis()
                // Cap the delta so a sleep/doze gap can never be counted as usage.
                val delta = (now - lastTick).coerceIn(0L, GRACE_TICK_MS * 3)
                lastTick = now

                val pkg = activePackage()
                // Notification shade / recents drawn over the app: neither count nor clear,
                // otherwise pulling the shade down to read the timer would make it vanish.
                if (pkg == "com.android.systemui") continue

                if (pkg == null || pkg in criticalPackages || pkg !in store.enabledPackages() ||
                    !isScreenOn() || store.blockUntil(pkg) > now
                ) {
                    // Tolerate brief blips (keyboard, popups) before dropping the timers.
                    if (++notCountingTicks >= 3) {
                        notifications.clearTimeLeft()
                        timerChip.hide()
                    }
                    continue
                }
                notCountingTicks = 0

                // Read every tick so a change made in the app takes effect straight away.
                val graceMs = store.graceMs()
                val used = store.addUsedMs(pkg, delta)
                if (used >= graceMs) {
                    lockForGraceExceeded(pkg, graceMs)
                    continue
                }

                val remaining = graceMs - used
                // Very short allowances shouldn't be "almost out" from the first second.
                val warnAt = minOf(GRACE_WARNING_MS, graceMs / 3)
                notifications.showTimeLeft(pkg, remaining, now)
                timerChip.show(remaining)
                if (remaining > warnAt) warnedPackages.remove(pkg)
                else if (warnedPackages.add(pkg)) notifications.warnAlmostOut(pkg, remaining)
            }
        }
    }

    private fun lockForGraceExceeded(pkg: String, graceMs: Long) {
        val lockMs = store.lockdownMs()
        // The app's own time limit, set by the user, so only critical apps are spared.
        lockApps(listOf(pkg), lockMs, userRule = true)
        store.resetUsed(pkg)
        warnedPackages.remove(pkg)
        notifications.clearTimeLeft()
        timerChip.hide()
        val label = appLabel(pkg)
        store.log(label, "Grace time used up — closed and locked down for ${describeDuration(lockMs)}")
        overlay.showBlock(
            "Time's up",
            "You've used your ${describeDuration(graceMs)} of $label. It's now closed and " +
                    "locked for ${describeDuration(lockMs)}. Step away, then decide what you actually want to do next."
        )
    }

    private fun isScreenOn(): Boolean =
        getSystemService(PowerManager::class.java)?.isInteractive ?: true

    // ---------------- Locking ----------------

    /**
     * The one way CancelIt locks apps: records each lock, shows its unlock countdown (unless
     * [notify] is false) and sends the user home once. The enforcement loop keeps them closed
     * from then on. Critical apps are never locked; Settings, installers and Maps only by their
     * own time limit ([userRule]). Returns the apps that were locked.
     */
    private fun lockApps(
        packages: Collection<String>,
        durationMs: Long = store.lockdownMs(),
        userRule: Boolean = false,
        notify: Boolean = true
    ): List<String> {
        val spared = if (userRule) criticalPackages else autoLockExempt
        val until = System.currentTimeMillis() + durationMs
        val locked = packages.filter { it !in spared }
        locked.forEach { pkg ->
            store.block(pkg, until)
            if (notify) notifications.showLocked(pkg, until)
        }
        goHome(force = true)
        return locked
    }

    /** Sending the user home on every single event turns into a storm of global actions on
     *  chatty apps, so repeat kicks are throttled. */
    private fun goHome(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastHomeKickAt < HOME_KICK_THROTTLE_MS) return
        lastHomeKickAt = now
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * The core enforcement rule: while a lock is active, every sighting of that app (live event
     * or the polling loop) sends the user home again, so there's no way to sit inside the app
     * past the lock by ignoring the overlay. An active lock is honoured for any package, not just
     * protected ones, which is what makes keyword, study-drift and missed-check-in locks bite.
     */
    private fun enforce(pkg: String) {
        if (pkg in criticalPackages) return
        val now = System.currentTimeMillis()
        val until = store.blockUntil(pkg)

        if (until > now) {
            goHome()
            overlay.showBlock("Locked down", "${appLabel(pkg)} is locked for ${formatDuration(until - now)} more.", replace = false)
            return
        }

        // The lock has expired: clear the record so stale keys don't pile up.
        if (until != 0L) store.clearBlock(pkg)
    }

    // ---------------- Presence ("Are you there?") ----------------

    /**
     * The presence state machine, one tick per second during a session:
     *
     *  - no session      -> make sure nothing is pending and no notification is left hanging
     *  - session, idle   -> after PRESENCE_INTERVAL_MS, ask "Are you there?"
     *  - session, asking -> a confirmation clears it and restarts the 10-minute window;
     *                       PRESENCE_TIMEOUT_MS of silence closes and locks everything
     */
    private fun startPresenceLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(PRESENCE_TICK_MS)
                val now = System.currentTimeMillis()

                if (!store.studyActive() || !store.accessibilityConsent()) {
                    if (store.presencePromptAt() != 0L) {
                        store.setPresencePromptAt(0L)
                        notifications.cancelAreYouThere()
                    }
                    // Nothing can come due without a session, so look less often.
                    delay(PRESENCE_IDLE_TICK_MS - PRESENCE_TICK_MS)
                    continue
                }

                val promptAt = store.presencePromptAt()
                if (promptAt == 0L) {
                    val cycleAt = store.presenceCycleAt().takeIf { it > 0L } ?: store.studyStart()
                    if (cycleAt > 0L && now - cycleAt >= PRESENCE_INTERVAL_MS) askAreYouThere(now)
                    continue
                }

                when {
                    store.presenceAckAt() >= promptAt -> onPresenceConfirmed(now)
                    now - promptAt >= PRESENCE_TIMEOUT_MS -> onPresenceTimedOut(now)
                }
            }
        }
    }

    private fun askAreYouThere(now: Long) {
        store.setPresenceAckAt(0L)
        store.setPresencePromptAt(now)
        store.setStudyLastResult("${clockTime()} — asked \"are you there?\"")
        store.log("Study mode", "Presence check sent — are you there?")
        notifications.askAreYouThere()
        // With notifications turned off, the question would never be seen and every app would
        // lock a minute later, so ask on screen instead.
        if (!notifications.canShowPresence()) {
            overlay.ask(
                title = "Are you there?",
                message = "Confirm within a minute, or CancelIt closes what's open and locks your distracting apps.",
                confirmLabel = "Yes, I'm here",
                dismissLabel = "End session",
                onConfirm = { store.setPresenceAckAt(System.currentTimeMillis()) },
                onDismiss = { endStudySession("You ended the session from the check-in") }
            )
        }
    }

    /** Ends the Study session from the service side (the Study tab's "End session" does the same). */
    private fun endStudySession(reason: String) {
        store.setStudyActive(false)
        store.resetPresence()
        notifications.cancelAreYouThere()
        store.setStudyLastResult("${clockTime()} — session ended: $reason")
        store.log("Study mode", "Session ended — $reason")
    }

    private fun onPresenceConfirmed(now: Long) {
        store.setPresencePromptAt(0L)
        store.setPresenceCycleAt(now)
        store.clearPresenceMissStreak()
        notifications.cancelAreYouThere()
        store.setStudyLastResult("${clockTime()} — you're here, session continues")
        store.log("Study mode", "Presence confirmed — session continues")
    }

    /** Nobody answered inside the minute: send the user home and lock every protected app plus
     *  whatever was in front. One overlay explains it, instead of a notification per app. */
    private fun onPresenceTimedOut(now: Long) {
        store.setPresencePromptAt(0L)
        store.setPresenceCycleAt(now)
        val missedInARow = store.bumpPresenceMissed()
        notifications.cancelAreYouThere()

        val closed = lockApps(store.enabledPackages() + listOfNotNull(activePackage()), notify = false).size
        store.setStudyLastResult("${clockTime()} — no response, $closed app(s) locked")
        store.log("Study mode", "No response to presence check — closed and locked $closed app(s)")

        // Nobody is there (a session left running overnight): stop asking every 10 minutes.
        val ended = missedInARow >= PRESENCE_MAX_MISSES
        if (ended) endStudySession("$PRESENCE_MAX_MISSES check-ins in a row went unanswered")

        overlay.dismiss() // an on-screen "Are you there?" that's still up is out of date now
        overlay.showBlock(
            "Are you there?",
            "You didn't confirm within a minute, so CancelIt closed what was open and locked your " +
                    "distracting apps for ${describeDuration(store.lockdownMs())}.\n\n" +
                    if (ended) "After $PRESENCE_MAX_MISSES missed check-ins in a row, your Study session has ended."
                    else "Your session is still running — the next check-in is in ${describeDuration(PRESENCE_INTERVAL_MS)}."
        )
    }

    // ---------------- Accessibility events ----------------

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::store.isInitialized) return
        // Nothing is read or acted on until the user accepts the in-app Accessibility disclosure.
        if (!store.accessibilityConsent()) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) {
            // CancelIt's own screen came to the front: stop counting (and enforcing) the app the
            // user just left. Its overlays (block screen, timer chip) aren't the activity, so
            // showing them over an app doesn't count as leaving it.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.className?.toString() == MainActivity::class.java.name
            ) lastForegroundPackage = pkg
            return
        }

        // A blocked word was typed: that already closed the app and showed the block screen,
        // so nothing else should run (enforce() would overwrite the screen's text).
        if (checkTypedKeywords(pkg, event)) return


        // Window events tell us which app is in front; the grace loop counts its time and the
        // enforcement loop re-checks it.
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            lastForegroundPackage = pkg
        }

        // The browser check and the Study capture read the same screen: read it once, if at all.
        val screen = lazy(LazyThreadSafetyMode.NONE) { readScreen() }
        if (pkg in BROWSER_PACKAGES && type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && store.browserSafetyOn()) {
            checkBrowserSafety(pkg, screen)
        }
        maybeCaptureForStudy(pkg, screen)
        enforce(pkg)
    }

    /** The visible text of the window in front, depth- and length-capped. */
    private fun readScreen(): String =
        runCatching { rootInActiveWindow?.visibleText(MAX_SCREEN_TEXT_CHARS, MAX_NODE_DEPTH) }.getOrNull().orEmpty()

    private fun maybeCaptureForStudy(pkg: String, screen: Lazy<String>) {
        if (!store.studyActive()) return
        if (pkg in criticalPackages) return
        val now = System.currentTimeMillis()
        if (now - lastStudyCaptureAt < STUDY_CAPTURE_THROTTLE_MS) return
        lastStudyCaptureAt = now
        val text = screen.value.replace('\n', ' ').take(STUDY_SNIPPET_CHARS)
        if (text.isBlank()) return
        val snippet = ScreenSnippet(appLabel(pkg), text, pkg)
        synchronized(studyScreens) {
            // Same screen as the last capture (sitting on one page): nothing new to record.
            if (studyScreens.firstOrNull() == snippet) return
            studyScreens.addFirst(snippet)
            while (studyScreens.size > STUDY_MAX_SCREENS) studyScreens.removeLast()
        }
    }

    private fun checkBrowserSafety(pkg: String, screen: Lazy<String>) {
        val now = System.currentTimeMillis()
        // Content-changed fires dozens of times per second while a page renders, so the screen
        // is only scanned every few seconds.
        if (now - lastBrowserScanAt < BROWSER_SCAN_THROTTLE_MS) return
        lastBrowserScanAt = now
        // Already locked: enforce() is handling it, no need to re-scan or re-log.
        if (store.blockUntil(pkg) > now) return

        if (severeTerms.containsMatchIn(screen.value)) {
            lockApps(listOf(pkg), SEVERE_BLOCK_MS)
            store.log("Browser safety", "Explicit-content signal detected — browser locked down for ${describeDuration(SEVERE_BLOCK_MS)}")
            overlay.showBlock(
                "Browser safety pause",
                "This browser is paused for ${describeDuration(SEVERE_BLOCK_MS)}. Step away, breathe, and choose what you want your next hour to be."
            )
        }
    }

    // ---------------- Keyword blocker ----------------

    /**
     * Text typed into another app is checked against the blocked keywords: first instantly on
     * the phone (a plain word match), then, if Smart detection is on, by Gemini for related terms
     * like slang or titles. Returns true when it acted. Password fields are skipped and only the
     * matched topic is logged, never what was typed.
     */
    private fun checkTypedKeywords(pkg: String, event: AccessibilityEvent): Boolean {
        if (pkg in essentialPackages) return false // never interrupt a call or an emergency text
        val type = event.eventType
        // Some apps never send text events, so while their content is changing we also read the
        // focused input field ourselves (throttled).
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return checkFocusedField(pkg)
        if (type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        ) return false
        if (event.isPassword) return false
        if (store.blockedKeywords().isEmpty()) return false

        val source = runCatching { event.source }.getOrNull()
        if (source?.isPassword == true) return false
        // Cursor moves also fire in read-only text (articles, pages); only count edit boxes.
        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED && source?.isEditable != true) return false

        var typed = event.text.joinToString(" ")
        // Some apps leave the event's text list empty and only expose it on the source node.
        if (typed.isBlank()) typed = runCatching { source?.text?.toString().orEmpty() }.getOrDefault("")
        if (BuildConfig.DEBUG) {
            Log.d("CancelIt-Keywords", "input event pkg=$pkg type=$type len=${typed.length} editable=${source?.isEditable}")
        }
        return handleTypedText(pkg, typed)
    }

    /** Reads the text of the focused input box, if any, and checks it like typed text. Only
     *  short fields (search bars, URL bars, chat boxes), so a long open document is left alone. */
    private fun checkFocusedField(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastFocusReadAt < FOCUS_READ_THROTTLE_MS) return false
        lastFocusReadAt = now
        if (store.blockedKeywords().isEmpty()) return false
        val text = runCatching {
            val focus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focus != null && focus.isEditable && !focus.isPassword && focus.packageName?.toString() == pkg) {
                focus.text?.toString()
            } else null
        }.getOrNull()
        if (text.isNullOrBlank() || text.length > FOCUS_READ_MAX_CHARS) return false
        if (BuildConfig.DEBUG) Log.d("CancelIt-Keywords", "focused-field read pkg=$pkg len=${text.length}")
        return handleTypedText(pkg, text)
    }

    /** Shared by every way of seeing text: instant word match first, then the optional AI check. */
    private fun handleTypedText(pkg: String, raw: String): Boolean {
        val keywords = store.blockedKeywords()
        if (keywords.isEmpty()) return false
        val typed = raw.takeLast(MAX_TYPED_CHARS)
        if (typed.isBlank()) {
            aiJob?.cancel() // text was cleared: a pending check on the old text is moot
            return false
        }
        val hit = keywords.firstOrNull { word ->
            keywordPatterns.getOrPut(word) { keywordRegex(word) }.containsMatchIn(typed)
        }
        if (hit != null) return blockForKeyword(pkg, hit, KeywordMatch.OWN_WORD)
        // Not literally on the list. Then, all on the phone: phrases the user already confirmed,
        // and the related terms the AI listed for each keyword. Last, the live AI check once
        // typing pauses.
        if (store.smartKeywordsOn()) {
            SmartKeywords.matchRelated(typed, smartStore.confirmedFor(keywords))?.let { topic ->
                return blockForKeyword(pkg, topic, KeywordMatch.CONFIRMED)
            }
            SmartKeywords.matchRelated(typed, smartStore.relatedFor(keywords))?.let { topic ->
                return blockForKeyword(pkg, topic, KeywordMatch.AI, typed)
            }
        }
        scheduleAiKeywordCheck(pkg, typed, keywords)
        return false
    }

    /**
     * Reacts to [topic] being typed in [pkg]. The user's own rules close and lock the app
     * straight away: a word from their list, or a phrase they already confirmed belongs to a
     * topic. A match the AI inferred (related terms or the live check) only asks: "Close app"
     * locks it and remembers [typed] as confirmed, "I'm fine" leaves it alone and keeps that
     * topic quiet in that app for [AI_SNOOZE_MS]. Google Play doesn't allow AI to take actions
     * on its own through the Accessibility API, so the user decides.
     *
     * False means "already locked (or snoozed), let enforce() handle it"; true means handled (or
     * deliberately swallowed by the cooldown).
     */
    private fun blockForKeyword(pkg: String, topic: String, match: KeywordMatch, typed: String? = null): Boolean {
        val now = System.currentTimeMillis()
        val askFirst = match == KeywordMatch.AI
        if (store.blockUntil(pkg) > now) return false
        if (askFirst && now < (aiSnoozedUntil["$pkg|$topic"] ?: 0L)) return false
        // Typing fires an event per keystroke; act once, then give the screen a moment.
        if (now - lastKeywordHitAt < KEYWORD_HIT_COOLDOWN_MS) return true
        lastKeywordHitAt = now
        if (!askFirst) aiJob?.cancel()
        val label = appLabel(pkg)

        if (askFirst) {
            overlay.ask(
                title = "Keyword blocker",
                message = "What you typed in $label looks related to \"$topic\".\n\n" +
                        "Close $label and lock it for ${describeDuration(store.lockdownMs())}?",
                confirmLabel = "Close app",
                dismissLabel = "I'm fine",
                onConfirm = {
                    store.recordKeywordHit(topic, viaAi = true)
                    store.log("Keyword blocker", "AI flagged typed text as \"$topic\" in $label; you closed it")
                    typed?.let { SmartKeywords.learnableTerm(it) }?.let { smartStore.confirm(topic, it) }
                    lockApps(listOf(pkg))
                },
                onDismiss = {
                    aiSnoozedUntil["$pkg|$topic"] = System.currentTimeMillis() + AI_SNOOZE_MS
                    store.log("Keyword blocker", "AI flagged \"$topic\" in $label; you said it was fine")
                }
            )
            return true
        }

        store.recordKeywordHit(topic, viaAi = match == KeywordMatch.CONFIRMED)
        store.log(
            "Keyword blocker",
            if (match == KeywordMatch.CONFIRMED) "A phrase you confirmed for \"$topic\" was typed in $label"
            else "Blocked word \"$topic\" typed in $label"
        )
        val locked = lockApps(listOf(pkg)).isNotEmpty()
        overlay.showBlock(
            "Keyword blocker",
            if (locked) {
                "You typed \"$topic\" in $label. It's now closed and locked for ${describeDuration(store.lockdownMs())}. " +
                        "Take a breath, then choose what you actually want to do next."
            } else {
                "You typed \"$topic\" in $label. Take a breath, then choose what you actually want to do next."
            }
        )
        return true
    }

    /**
     * Smart detection, layer 2 (the long tail). Once typing pauses, asks the AI whether the text
     * relates to any blocked keyword, told to read every topic broadly. Opt-in, short text only,
     * cached, rate-limited, and it fails open: any error means the text is allowed. Failures are
     * logged under the tag "CancelIt-Smart" (never the typed text).
     */
    private fun scheduleAiKeywordCheck(pkg: String, typed: String, keywords: List<String>) {
        if (!store.smartKeywordsOn()) return
        val text = typed.trim()
        // Same text seen again (repeat events, focused-field reads) must not restart the pause.
        if (aiJob?.isActive == true && text == pendingAiText) return
        aiJob?.cancel() // a newer keystroke supersedes whatever was waiting
        if (text.length !in AI_MIN_CHARS..AI_MAX_CHARS) return
        if (text.none { it.isLetter() }) return // digits/symbols only: codes, card numbers, PINs
        // Everyday words only, or too many words to be a search: not worth a call.
        if (!SmartKeywords.worthChecking(text)) return
        if (store.blockUntil(pkg) > System.currentTimeMillis()) return

        // A verdict for exactly this text is already known: act on it now, no waiting.
        when (val known = SmartKeywords.peek(text, keywords)) {
            is SmartVerdict.Flagged -> {
                blockForKeyword(pkg, known.topic, KeywordMatch.AI, text)
                return
            }
            is SmartVerdict.Allowed -> return
            else -> Unit
        }
        // Over the hourly/daily AI budget or backing off after errors: fail open, quietly.
        if (!GeminiClient.canCall(AiFeature.KEYWORD_CHECK)) return

        pendingAiText = text
        aiJob = serviceScope.launch {
            delay(AI_TYPING_PAUSE_MS)
            if (!aiCallAllowed()) return@launch
            val verdict = SmartKeywords.classify(text, keywords)
            // If the user confirms, the phrase is remembered on the phone so it never costs a call again.
            if (verdict is SmartVerdict.Flagged) blockForKeyword(pkg, verdict.topic, KeywordMatch.AI, text)
        }
    }

    private fun aiCallAllowed(): Boolean = synchronized(aiCallTimes) {
        val now = System.currentTimeMillis()
        while (aiCallTimes.isNotEmpty() && now - aiCallTimes.first() > 60_000L) aiCallTimes.removeFirst()
        if (aiCallTimes.size >= AI_MAX_CALLS_PER_MIN) false else { aiCallTimes.addLast(now); true }
    }

    // ---------------- Study mode ----------------

    /**
     * Runs on the study cadence: checks the screens captured since the last check against the
     * topic. When they look off-topic the user is asked, and "Close app" locks whichever app was
     * in front, the same way a protected app is locked. The verdict comes from AI, so it never
     * locks anything by itself.
     */
    private suspend fun runStudyCheck() {
        val topic = store.studyTopic()
        val screens = synchronized(studyScreens) { studyScreens.toList().also { studyScreens.clear() } }
        if (topic.isBlank() || screens.isEmpty()) return
        // The topic check needs AI (18+ only); check-ins keep working without it.
        if (!store.aiAllowed()) {
            store.setStudyLastResult("Topic checks use AI and are only for adults (18+)")
            return
        }

        val result = studyCheck.check(topic, screens)
        val stamp = clockTime()
        if (!result.checked) {
            // Offline, over the AI limit or an error: say so instead of claiming "on track".
            store.setStudyLastResult("$stamp — couldn't check the topic (offline or AI limit reached)")
            return
        }
        if (result.isDistracted) {
            store.setStudyLastResult("$stamp — looks off-topic: ${result.reason}")
            // The app in front, unless that's one CancelIt never locks (the home screen,
            // CancelIt itself): then the most recent screen the check saw that came from an app.
            val offending = activePackage()?.takeIf { it !in autoLockExempt }
                ?: screens.firstOrNull { it.pkg !in autoLockExempt }?.pkg
            if (offending == null) {
                store.log("Study mode", "Looked off-topic — ${result.reason}")
                return
            }
            val label = appLabel(offending)
            overlay.ask(
                title = "Back to $topic?",
                message = "This looks off-topic: ${result.reason}\n\n" +
                        "Close $label and lock it for ${describeDuration(store.lockdownMs())} so you can refocus?",
                confirmLabel = "Close app",
                dismissLabel = "I'm on topic",
                onConfirm = {
                    store.log("Study mode", "Distraction detected — ${result.reason}")
                    lockApps(listOf(offending))
                },
                onDismiss = {
                    studyCheck.markOnTopic()
                    store.setStudyLastResult("$stamp — on track (you confirmed)")
                    store.log("Study mode", "Off-topic check dismissed — you said you were on topic")
                }
            )
        } else {
            store.setStudyLastResult("$stamp — on track")
            store.log("Study mode", "On track — check passed")
        }
    }

    override fun onInterrupt() = Unit

    private fun appLabel(pkg: String): String =
        BuiltInApps.firstOrNull { it.packageName == pkg }?.label
            ?: store.customApps().firstOrNull { it.packageName == pkg }?.label
            ?: installedLabel(pkg)
            ?: "Selected app"

    /** Name of any installed app. Can come back null on Android 11+ when the package isn't
     *  visible to us, in which case the caller falls back to a generic name. */
    private fun installedLabel(pkg: String): String? = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    override fun onDestroy() {
        serviceScope.cancel()
        if (::store.isInitialized) {
            store.saveUsedTime()
            notifications.cancelAreYouThere()
            notifications.clearTimeLeft()
        }
        // Never registered (service torn down before onServiceConnected) is safe to ignore.
        runCatching { unregisterReceiver(presenceReceiver) }
        timerChip.hide()
        overlay.dismiss()
        super.onDestroy()
    }

    companion object {
        /** Whether the user has switched the service on in system Settings. */
        fun isEnabled(context: Context): Boolean =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.contains(context.packageName) == true
    }
}
