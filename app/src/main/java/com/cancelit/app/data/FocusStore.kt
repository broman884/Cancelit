package com.cancelit.app.data

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray

data class FocusApp(val label: String, val packageName: String, val isCustom: Boolean = false)
data class ActivityRecord(val time: Long, val label: String, val detail: String)
data class KeywordHit(val time: Long, val keyword: String, val viaAi: Boolean)
data class ChatMessage(val role: String, val text: String) // role: ROLE_USER, ROLE_MODEL or ROLE_NOTICE

/** Coach chat roles. A notice is the app's own message (AI unavailable, limit reached): shown in
 *  the chat like a reply, but never sent to the AI and never reportable as AI output. */
internal const val ROLE_USER = "user"
internal const val ROLE_MODEL = "model"
internal const val ROLE_NOTICE = "notice"

val BuiltInApps = listOf(
    FocusApp("YouTube", "com.google.android.youtube"),
    FocusApp("Instagram", "com.instagram.android"),
    FocusApp("TikTok", "com.zhiliaoapp.musically"),
    FocusApp("Chrome", "com.android.chrome"),
    FocusApp("Firefox", "org.mozilla.firefox"),
    FocusApp("Snapchat", "com.snapchat.android"),
    FocusApp("Facebook", "com.facebook.katana"),
    FocusApp("X (Twitter)", "com.twitter.android"),
    FocusApp("Reddit", "com.reddit.frontpage"),
    FocusApp("Netflix", "com.netflix.mediaclient"),
    FocusApp("Twitch", "tv.twitch.android.app"),
    FocusApp("Discord", "com.discord"),
    FocusApp("Pinterest", "com.pinterest"),
    FocusApp("Telegram", "org.telegram.messenger")
)

private val WHITESPACE = Regex("\\s+")
private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
private val PLAY_STORE_ID = Regex("[?&]id=([^&#\\s]+)")

/**
 * The package name in what the user typed for a custom app: a bare name (com.example.app) or a
 * Play Store link (…/details?id=com.example.app). Null if it isn't one. Case is kept, because
 * package names are case-sensitive and some apps use capitals (Slack is com.Slack).
 */
internal fun packageFromInput(raw: String): String? {
    val text = raw.trim()
    val candidate = PLAY_STORE_ID.find(text)?.groupValues?.get(1) ?: text
    return candidate.takeIf { PACKAGE_NAME.matches(it) }
}

/** The settings file, and the age answer kept in it. GeminiClient reads the same key, so no AI
 *  call is ever made unless the user said they're 18 or older. */
internal const val FOCUS_PREFS = "cancelit_focus"
internal const val KEY_AGE_GROUP = "age_group"
internal const val AGE_ADULT = "adult"
internal const val AGE_MINOR = "minor"

// The activity log, keyword hits and Coach chat each have their own file, so adding to one
// never rewrites the settings or the other logs.
private const val EVENTS_PREFS = "cancelit_events"
private const val KEYWORD_HITS_PREFS = "cancelit_keyword_hits"
private const val COACH_PREFS = "cancelit_coach"

/** Every file FocusStore writes, for "Delete all my data". */
internal val FOCUS_PREFS_FILES = listOf(FOCUS_PREFS, EVENTS_PREFS, KEYWORD_HITS_PREFS, COACH_PREFS)

private const val KEY_EVENTS = "events"
private const val KEY_KEYWORD_HITS = "keyword_hits"
private const val KEY_CHAT = "coach_chat"
private const val KEY_KEYWORDS = "blocked_keywords"
private const val KEY_CUSTOM_APPS = "custom_apps"

private const val KEY_PIN_FAILS = "child_pin_fails"
private const val KEY_PIN_RETRY_AT = "child_pin_retry_at"
/** Wrong PINs allowed before each further try has to wait. */
private const val PIN_FREE_TRIES = 5

private const val MAX_EVENTS = 200
private const val MAX_KEYWORD_HITS = 1_000
private const val MAX_CHAT_MESSAGES = 40

/** Foreground time is counted every second but only saved this often, and when an app locks. */
private const val USED_TIME_SAVE_MS = 30_000L

/** Salted SHA-256 of a PIN; the PIN itself is never written to storage. */
private fun hashChildPin(pin: String, salt: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    return Base64.encodeToString(digest.digest(pin.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
}

/** Small, local-only store. Nothing in it leaves the phone; the AI features send only what
 *  GeminiClient's callers pass them. */
class FocusStore(context: Context) {
    private val prefs = context.getSharedPreferences(FOCUS_PREFS, Context.MODE_PRIVATE)
    private val eventLog = context.getSharedPreferences(EVENTS_PREFS, Context.MODE_PRIVATE)
    private val hitLog = context.getSharedPreferences(KEYWORD_HITS_PREFS, Context.MODE_PRIVATE)
    private val coachChat = context.getSharedPreferences(COACH_PREFS, Context.MODE_PRIVATE)
    private val enabledKey = "enabled_packages"

    /** The keyword list is read on every keystroke in other apps, so it's only parsed again
     *  when the stored text changes. */
    @Volatile private var keywordCache: Pair<String?, List<String>>? = null

    fun enabledPackages(): Set<String> =
        prefs.getStringSet(enabledKey, setOf("com.google.android.youtube", "com.instagram.android"))
            ?: emptySet()

    fun setEnabled(packageName: String, enabled: Boolean) {
        val next = enabledPackages().toMutableSet()
        if (enabled) next += packageName else next -= packageName
        if (!enabled) resetUsed(packageName)
        // A fresh set instance is required: putStringSet keeps the reference, and mutating the
        // set returned by getStringSet is documented as undefined behavior.
        prefs.edit { putStringSet(enabledKey, LinkedHashSet(next)) }
    }

    fun blockUntil(packageName: String) = prefs.getLong("blocked_$packageName", 0L)

    /** When the current lock started, so progress bars are drawn against the real duration
     *  (an app lock or a 45-minute browser pause). */
    fun blockStart(packageName: String) = prefs.getLong("blockstart_$packageName", 0L)

    fun block(packageName: String, until: Long) = prefs.edit {
        putLong("blocked_$packageName", until)
        putLong("blockstart_$packageName", System.currentTimeMillis())
    }

    fun clearBlock(packageName: String) = prefs.edit {
        remove("blocked_$packageName")
        remove("blockstart_$packageName")
    }

    /** Apps locked right now. ("blocked_keywords" shares the prefix but holds a String.) */
    fun lockedPackages(now: Long = System.currentTimeMillis()): List<String> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith("blocked_") && value is Long && value > now) key.removePrefix("blocked_") else null
        }

    /** Emergency unlock: clears every lock at once. Returns the apps that were locked. */
    fun clearAllBlocks(): List<String> {
        val locked = lockedPackages()
        val lockKeys = prefs.all.filter { (key, value) ->
            value is Long && (key.startsWith("blocked_") || key.startsWith("blockstart_"))
        }.keys
        prefs.edit { lockKeys.forEach { remove(it) } }
        return locked
    }

    // ---- Activity log ----
    fun log(label: String, detail: String) {
        synchronized(listLock) {
            val next = listOf(ActivityRecord(System.currentTimeMillis(), label, detail)) + events(MAX_EVENTS - 1)
            eventLog.edit { putList(KEY_EVENTS, next) { JSONArray().put(it.time).put(it.label).put(it.detail) } }
        }
    }

    /** Most recent first. */
    fun events(limit: Int = MAX_EVENTS): List<ActivityRecord> =
        eventLog.getList(KEY_EVENTS) { array, i ->
            array.getJSONArray(i).let { ActivityRecord(it.getLong(0), it.getString(1), it.getString(2)) }
        }.take(limit)

    // ---- Custom blacklist entries ----
    fun customApps(): List<FocusApp> =
        prefs.getList(KEY_CUSTOM_APPS) { array, i ->
            array.getJSONArray(i).let { FocusApp(it.getString(0), it.getString(1), isCustom = true) }
        }

    private fun saveCustomApps(apps: List<FocusApp>) =
        prefs.edit { putList(KEY_CUSTOM_APPS, apps) { JSONArray().put(it.label).put(it.packageName) } }

    fun addCustomApp(label: String, packageName: String) {
        saveCustomApps(customApps().filter { it.packageName != packageName } + FocusApp(label, packageName, true))
    }

    fun removeCustomApp(packageName: String) {
        saveCustomApps(customApps().filter { it.packageName != packageName })
        setEnabled(packageName, false)
        clearBlock(packageName)
    }

    fun allCatalogApps(): List<FocusApp> {
        val custom = customApps()
        val customPkgs = custom.map { it.packageName }.toSet()
        return BuiltInApps.filter { it.packageName !in customPkgs } + custom
    }

    // ---- Grace time (user adjustable) ----
    fun graceMs(): Long = prefs.getLong("grace_ms", DEFAULT_GRACE_MS).coerceIn(MIN_GRACE_MS, MAX_GRACE_MS)
    fun setGraceMs(ms: Long) = prefs.edit { putLong("grace_ms", ms.coerceIn(MIN_GRACE_MS, MAX_GRACE_MS)) }

    // ---- Lockdown time (user adjustable) ----
    fun lockdownMs(): Long = prefs.getLong("lockdown_ms", DEFAULT_LOCKDOWN_MS).coerceIn(MIN_LOCKDOWN_MS, MAX_LOCKDOWN_MS)
    fun setLockdownMs(ms: Long) = prefs.edit { putLong("lockdown_ms", ms.coerceIn(MIN_LOCKDOWN_MS, MAX_LOCKDOWN_MS)) }

    // ---- Keyword blocker words ----
    /** Opt-in: also ask Gemini whether typed text relates to a blocked keyword. Off by default
     *  because it sends short snippets of what the user types to the Gemini API. */
    fun smartKeywordsOn() = prefs.getBoolean("keyword_ai", false)
    fun setSmartKeywords(on: Boolean) = prefs.edit { putBoolean("keyword_ai", on) }

    /** Lowercase, in the order they were added. */
    fun blockedKeywords(): List<String> {
        val raw = prefs.getString(KEY_KEYWORDS, null)
        keywordCache?.let { (cachedRaw, words) -> if (cachedRaw == raw) return words }
        return decodeList(raw) { array, i -> array.getString(i) }.also { keywordCache = raw to it }
    }

    /** Returns false if the word is too short/long or already on the list. Runs of spaces become
     *  one, so "anime  girls" still matches what people type. */
    fun addBlockedKeyword(raw: String): Boolean {
        val word = raw.replace(WHITESPACE, " ").trim().lowercase(Locale.ROOT)
        if (word.length !in 2..MAX_KEYWORD_CHARS) return false
        val current = blockedKeywords()
        if (word in current) return false
        prefs.edit { putList(KEY_KEYWORDS, current + word) { it } }
        return true
    }

    fun removeBlockedKeyword(word: String) {
        prefs.edit { putList(KEY_KEYWORDS, blockedKeywords().filter { it != word }) { it } }
    }

    // ---- Browser safety ----
    /** Off by default: explicit content is only blocked when the user asks for it, with this
     *  switch or by putting a word on their keyword list. */
    fun browserSafetyOn() = prefs.getBoolean("browser_safety", false)
    fun setBrowserSafety(on: Boolean) = prefs.edit { putBoolean("browser_safety", on) }

    // ---- PIN lock (called Child Mode in code; shown to users as "PIN lock") ----
    // Freezes the blacklist, keywords and timers so they can't be changed without the PIN. It's
    // a self-control aid (hand the PIN to someone you trust), not a parental control, and not
    // tamper-proof security: the PIN is only checked inside this app's own screens.

    /** Whether a PIN has ever been set up. */
    fun hasChildPin(): Boolean = prefs.getString("child_pin_hash", null) != null

    /** True while the blacklist, keywords, Browser safety and grace/lockdown timers are frozen. */
    fun childModeOn(): Boolean = prefs.getBoolean("child_mode_on", false)

    /** Sets (or replaces) the PIN and immediately locks. */
    fun setChildModePin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit {
            putString("child_pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            putString("child_pin_hash", hashChildPin(pin, salt))
            putBoolean("child_mode_on", true)
            remove(KEY_PIN_FAILS)
            remove(KEY_PIN_RETRY_AT)
        }
    }

    /** Until when the next PIN attempt has to wait after too many wrong ones (0 = no wait). */
    fun pinRetryAt(): Long = prefs.getLong(KEY_PIN_RETRY_AT, 0L)

    private fun verifyChildPin(pin: String): Boolean {
        val saltB64 = prefs.getString("child_pin_salt", null) ?: return false
        val expected = prefs.getString("child_pin_hash", null) ?: return false
        val salt = runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        return hashChildPin(pin, salt) == expected
    }

    /** Re-locks using the already-saved PIN, without needing to type it again. */
    fun enableChildMode() = prefs.edit { putBoolean("child_mode_on", true) }

    /**
     * Unlocks only if [pin] matches the saved PIN. Returns whether it unlocked. A 4-digit PIN has
     * only 10,000 values, so after [PIN_FREE_TRIES] wrong tries each further one waits longer
     * (30 seconds, doubling, up to an hour); during a wait nothing is checked.
     */
    fun disableChildMode(pin: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < pinRetryAt()) return false
        if (!verifyChildPin(pin)) {
            val fails = prefs.getInt(KEY_PIN_FAILS, 0) + 1
            val wait = if (fails < PIN_FREE_TRIES) 0L
            else minOf(30_000L shl (fails - PIN_FREE_TRIES).coerceAtMost(7), 60 * 60_000L)
            prefs.edit {
                putInt(KEY_PIN_FAILS, fails)
                putLong(KEY_PIN_RETRY_AT, now + wait)
            }
            return false
        }
        prefs.edit {
            putBoolean("child_mode_on", false)
            remove(KEY_PIN_FAILS)
            remove(KEY_PIN_RETRY_AT)
        }
        return true
    }

    // ---- Keyword hit history (feeds the Report graphs and the Coach) ----
    /** One record each time a blocked keyword actually triggered. Newest first, capped. Stores
     *  the keyword from the user's list only, never the text that was typed. */
    fun recordKeywordHit(keyword: String, viaAi: Boolean) {
        synchronized(listLock) {
            val next = listOf(KeywordHit(System.currentTimeMillis(), keyword, viaAi)) +
                    keywordHits().take(MAX_KEYWORD_HITS - 1)
            hitLog.edit { putList(KEY_KEYWORD_HITS, next) { JSONArray().put(it.time).put(it.keyword).put(it.viaAi) } }
        }
    }

    fun keywordHits(): List<KeywordHit> =
        hitLog.getList(KEY_KEYWORD_HITS) { array, i ->
            array.getJSONArray(i).let { KeywordHit(it.getLong(0), it.getString(1), it.getBoolean(2)) }
        }

    // ---- Time used toward each app's limit ----
    /** Time used toward [packageName]'s limit. A break from the app at least as long as the lock
     *  time starts a fresh allowance, the same as sitting out a lock would, so time left over
     *  from yesterday can't lock an app a minute after opening it today. */
    fun usedMs(packageName: String, now: Long = System.currentTimeMillis()): Long {
        val lastUsed = lastUsedAt[packageName] ?: prefs.getLong(lastUsedKey(packageName), 0L)
        if (lastUsed > 0L && now - lastUsed >= lockdownMs()) return 0L
        return unsavedUsedMs[packageName] ?: prefs.getLong(usedKey(packageName), 0L)
    }

    /** Adds [ms] to the app's counter and returns the new total. Called every second while a
     *  protected app is open, so the total lives in memory and is saved every [USED_TIME_SAVE_MS]. */
    fun addUsedMs(packageName: String, ms: Long): Long = synchronized(usedLock) {
        val now = System.currentTimeMillis()
        val next = usedMs(packageName, now) + ms
        unsavedUsedMs[packageName] = next
        lastUsedAt[packageName] = now
        if (now - usedSavedAt >= USED_TIME_SAVE_MS) saveUsedTime()
        next
    }

    fun resetUsed(packageName: String) {
        synchronized(usedLock) {
            unsavedUsedMs.remove(packageName)
            lastUsedAt.remove(packageName)
            prefs.edit {
                remove(usedKey(packageName))
                remove(lastUsedKey(packageName))
            }
        }
    }

    /** Writes the in-memory counters to storage. The service also calls this when it stops. */
    fun saveUsedTime() {
        synchronized(usedLock) {
            if (unsavedUsedMs.isNotEmpty() || lastUsedAt.isNotEmpty()) {
                prefs.edit {
                    for ((pkg, ms) in unsavedUsedMs) putLong(usedKey(pkg), ms)
                    for ((pkg, at) in lastUsedAt) putLong(lastUsedKey(pkg), at)
                }
                unsavedUsedMs.clear()
                lastUsedAt.clear()
            }
            usedSavedAt = System.currentTimeMillis()
        }
    }

    private fun usedKey(packageName: String) = "used_$packageName"
    private fun lastUsedKey(packageName: String) = "usedat_$packageName"

    // ---- Study Mode ----
    fun studyActive() = prefs.getBoolean("study_active", false)
    fun setStudyActive(v: Boolean) = prefs.edit { putBoolean("study_active", v) }
    fun studyTopic() = prefs.getString("study_topic", "") ?: ""
    fun setStudyTopic(v: String) = prefs.edit { putString("study_topic", v) }
    fun studyStart() = prefs.getLong("study_start", 0L)
    fun setStudyStart(v: Long) = prefs.edit { putLong("study_start", v) }
    fun studyLastResult() = prefs.getString("study_last_result", "") ?: ""
    fun setStudyLastResult(v: String) = prefs.edit { putString("study_last_result", v) }

    /** Anchors the topic-check cadence to the session (not to whenever the service happened to
     *  start), so the "next check in" countdown on screen matches what actually runs. */
    fun studyLastCheckAt() = prefs.getLong("study_last_check", 0L)
    fun setStudyLastCheckAt(v: Long) = prefs.edit { putLong("study_last_check", v) }

    // ---- Presence ("Are you there?") checks ----
    /** Timestamp of the prompt currently awaiting an answer, or 0 when nothing is pending. */
    fun presencePromptAt() = prefs.getLong("presence_prompt_at", 0L)
    fun setPresencePromptAt(v: Long) = prefs.edit { putLong("presence_prompt_at", v) }

    /** Timestamp of the last "yes, I'm here" — counts only if it's at or after the prompt. */
    fun presenceAckAt() = prefs.getLong("presence_ack_at", 0L)
    fun setPresenceAckAt(v: Long) = prefs.edit { putLong("presence_ack_at", v) }

    /** Start of the current 10-minute window. */
    fun presenceCycleAt() = prefs.getLong("presence_cycle_at", 0L)
    fun setPresenceCycleAt(v: Long) = prefs.edit { putLong("presence_cycle_at", v) }

    fun presenceMissed() = prefs.getInt("presence_missed", 0)

    /** Counts a missed check-in. Returns how many were missed in a row (a confirmation resets it). */
    fun bumpPresenceMissed(): Int {
        val streak = prefs.getInt("presence_miss_streak", 0) + 1
        prefs.edit {
            putInt("presence_missed", presenceMissed() + 1)
            putInt("presence_miss_streak", streak)
        }
        return streak
    }

    fun clearPresenceMissStreak() = prefs.edit { putInt("presence_miss_streak", 0) }

    fun resetPresence(now: Long = System.currentTimeMillis()) = prefs.edit {
        putLong("presence_prompt_at", 0L)
        putLong("presence_ack_at", 0L)
        putLong("presence_cycle_at", now)
        putInt("presence_missed", 0)
        putInt("presence_miss_streak", 0)
    }

    /** True while an unanswered prompt is still inside its one-minute window. */
    fun presencePending(now: Long = System.currentTimeMillis()): Boolean =
        presencePromptOpen(presencePromptAt(), presenceAckAt(), now)

    // ---- Coach chat ----
    fun chatHistory(): List<ChatMessage> =
        coachChat.getList(KEY_CHAT) { array, i -> array.getJSONArray(i).let { ChatMessage(it.getString(0), it.getString(1)) } }

    fun appendChatMessage(role: String, text: String) {
        synchronized(listLock) {
            val next = (chatHistory() + ChatMessage(role, text)).takeLast(MAX_CHAT_MESSAGES)
            coachChat.edit { putList(KEY_CHAT, next) { JSONArray().put(it.role).put(it.text) } }
        }
    }

    fun clearChatHistory() = coachChat.edit { remove(KEY_CHAT) }

    // ---- Privacy policy ----
    /** True once the user has tapped "I agree" on the privacy policy screen. Shown once, on the
     *  very first launch, before any other screen. */
    fun privacyAccepted() = prefs.getBoolean("privacy_accepted", false)
    fun setPrivacyAccepted(v: Boolean) = prefs.edit { putBoolean("privacy_accepted", v) }

    // ---- Accessibility consent ----
    /** True once the user agreed on the separate Accessibility disclosure (Google Play's
     *  prominent-disclosure rule). Until then the service ignores every event, even if it was
     *  switched on straight from system Settings. */
    fun accessibilityConsent() = prefs.getBoolean("a11y_consent", false)
    fun setAccessibilityConsent(v: Boolean) = prefs.edit { putBoolean("a11y_consent", v) }

    // ---- Age (Gemini's terms allow adults only) ----
    /** [AGE_ADULT], [AGE_MINOR], or null if the question hasn't been answered yet. */
    fun ageGroup(): String? = prefs.getString(KEY_AGE_GROUP, null)
    fun setAgeGroup(group: String) = prefs.edit { putString(KEY_AGE_GROUP, group) }
    fun aiAllowed() = ageGroup() == AGE_ADULT

    companion object {
        /** Counted time not saved yet. Shared by every FocusStore in the process (the screens
         *  and the service), so the timer on screen always shows the live count. */
        private val unsavedUsedMs = ConcurrentHashMap<String, Long>()
        /** When each app was last counted, saved together with [unsavedUsedMs]. */
        private val lastUsedAt = ConcurrentHashMap<String, Long>()
        private val usedLock = Any()
        @Volatile private var usedSavedAt = 0L

        /** Guards read-modify-write updates of the logs, which the screens and the service both append to. */
        private val listLock = Any()

        /** Drops counted time that wasn't saved yet ("Delete all my data"). */
        internal fun forgetUnsaved() {
            synchronized(usedLock) {
                unsavedUsedMs.clear()
                lastUsedAt.clear()
            }
        }
    }
}
