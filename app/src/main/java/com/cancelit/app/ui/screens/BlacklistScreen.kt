package com.cancelit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cancelit.app.ai.GeminiClient
import com.cancelit.app.ai.SmartKeywordStore
import com.cancelit.app.data.FocusApp
import com.cancelit.app.data.FocusStore
import com.cancelit.app.data.MAX_KEYWORD_CHARS
import com.cancelit.app.data.SEVERE_BLOCK_MS
import com.cancelit.app.data.packageFromInput
import com.cancelit.app.describeDuration
import com.cancelit.app.formatDuration
import com.cancelit.app.service.AI_MAX_CHARS
import com.cancelit.app.shortDuration
import com.cancelit.app.ui.AppAvatar
import com.cancelit.app.ui.AppSwitch
import com.cancelit.app.ui.AppTextField
import com.cancelit.app.ui.Border
import com.cancelit.app.ui.Disclosure
import com.cancelit.app.ui.EmptyState
import com.cancelit.app.ui.GroupCard
import com.cancelit.app.ui.Ink
import com.cancelit.app.ui.OnResume
import com.cancelit.app.ui.PrimaryButton
import com.cancelit.app.ui.RowDivider
import com.cancelit.app.ui.ScreenHeader
import com.cancelit.app.ui.Section
import com.cancelit.app.ui.SectionCard
import com.cancelit.app.ui.SegmentedControl
import com.cancelit.app.ui.StyleBodyStrong
import com.cancelit.app.ui.StyleCaption
import com.cancelit.app.ui.StyleHint
import com.cancelit.app.ui.Surface1
import com.cancelit.app.ui.Surface2
import com.cancelit.app.ui.TextPrimary
import com.cancelit.app.ui.TextSecondary
import com.cancelit.app.ui.TextTertiary
import com.cancelit.app.ui.ThinProgress
import kotlinx.coroutines.delay

private enum class ListTab { APPS, KEYWORDS }

/** The List tab: blacklisted apps on one side, the keyword blocker's words on the other. */
@Composable
internal fun BlacklistScreen(store: FocusStore) {
    var listTab by remember { mutableStateOf(ListTab.APPS) }
    val locked = store.childModeOn()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            ScreenHeader("Blocklist")
            Spacer(Modifier.height(12.dp))
            SegmentedControl(
                options = listOf("Apps", "Keywords"),
                selectedIndex = listTab.ordinal
            ) { listTab = if (it == 0) ListTab.APPS else ListTab.KEYWORDS }
        }
        Box(Modifier.weight(1f)) {
            when (listTab) {
                ListTab.APPS -> BlacklistAppsTab(store, locked)
                ListTab.KEYWORDS -> KeywordsTab(store, locked)
            }
        }
    }
}

/** Small notice shown wherever PIN lock has frozen the controls on screen. */
@Composable
private fun ChildLockBanner() {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Surface1).border(1.dp, Border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Lock, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text("Locked by PIN lock. Unlock it in the PIN tab to make changes.", style = StyleCaption)
    }
}

@Composable
private fun BlacklistAppsTab(store: FocusStore, locked: Boolean = false) {
    var tick by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var newLabel by remember { mutableStateOf("") }
    var newPackage by remember { mutableStateOf("") }
    var packageError by remember { mutableStateOf<String?>(null) }

    // Lock countdowns on the rows tick every second.
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    val apps = remember(tick) { store.allCatalogApps() }
    val enabledSet = remember(tick, now) { store.enabledPackages() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column {
                Text(
                    "${shortDuration(store.graceMs())} per app, then locked for ${shortDuration(store.lockdownMs())}.",
                    style = StyleCaption
                )
                if (locked) {
                    Spacer(Modifier.height(12.dp))
                    ChildLockBanner()
                }
            }
        }
        item {
            GroupCard {
                apps.forEachIndexed { index, app ->
                    if (index > 0) RowDivider()
                    AppRow(
                        app = app, enabled = app.packageName in enabledSet, store = store, now = now, locked = locked,
                        onToggle = { store.setEnabled(app.packageName, it); tick++ },
                        onRemove = if (app.isCustom) {
                            { store.removeCustomApp(app.packageName); tick++ }
                        } else null
                    )
                }
            }
        }
        item {
            Section("Add another app") {
                SectionCard {
                    AppTextField(newLabel, { newLabel = it }, "App name", enabled = !locked)
                    Spacer(Modifier.height(10.dp))
                    AppTextField(
                        newPackage, { newPackage = it; packageError = null },
                        "Play Store link or package name", enabled = !locked
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        packageError ?: "Paste the app's Play Store link, or its package name (com.example.app).",
                        style = if (packageError != null) StyleCaption.copy(color = TextPrimary) else StyleHint
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "Add app",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !locked && newLabel.isNotBlank() && newPackage.isNotBlank(),
                        onClick = {
                            val label = newLabel.trim()
                            val pkg = packageFromInput(newPackage)
                            when {
                                pkg == null -> packageError = "That isn't a package name. Paste the app's Play Store link instead."
                                label.isNotBlank() -> {
                                    store.addCustomApp(label, pkg)
                                    store.setEnabled(pkg, true)
                                    newLabel = ""; newPackage = ""; packageError = null
                                    tick++
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: FocusApp,
    enabled: Boolean,
    store: FocusStore,
    now: Long,
    locked: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val until = store.blockUntil(app.packageName)
    val isLocked = until > now
    // Progress is measured against the lock's real duration: a browser pause is 45 minutes,
    // an app lock uses the lock length.
    val progress = if (isLocked) {
        val start = store.blockStart(app.packageName).takeIf { it > 0L } ?: (until - store.lockdownMs())
        val total = (until - start).coerceAtLeast(1L)
        ((now - start).coerceIn(0L, total)).toFloat() / total.toFloat()
    } else 0f

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppAvatar(app)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = StyleBodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    statusText(enabled, isLocked, until, now, store.usedMs(app.packageName), store.graceMs()),
                    style = if (isLocked) StyleCaption.copy(color = TextPrimary) else StyleHint
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, enabled = !locked, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Close, "Remove ${app.label}", tint = if (locked) Border else TextTertiary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(4.dp))
            }
            AppSwitch(checked = enabled, onCheckedChange = onToggle, enabled = !locked)
        }
        if (isLocked) {
            Spacer(Modifier.height(10.dp))
            ThinProgress(progress, height = 3.dp)
        }
    }
}

private fun statusText(enabled: Boolean, isLocked: Boolean, until: Long, now: Long, usedMs: Long, graceMs: Long): String = when {
    isLocked -> "Locked · ${formatDuration(until - now)} left"
    !enabled -> "Not protected"
    else -> "Protected · ${formatDuration((graceMs - usedMs).coerceAtLeast(0L))} left"
}

/** Keyword blocker: words that, when typed in any other app, close it and lock it. */
@Composable
private fun KeywordsTab(store: FocusStore, locked: Boolean = false) {
    var tick by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val keywords = remember(tick) { store.blockedKeywords() }
    var smartOn by remember { mutableStateOf(store.smartKeywordsOn()) }
    var browserSafety by remember { mutableStateOf(store.browserSafetyOn()) }
    val aiInBuild = remember { GeminiClient.isConfigured() }
    val adult = remember { store.aiAllowed() }
    val hasAiKey = aiInBuild && adult
    val showTermCounts = smartOn && hasAiKey

    // The service fills in each word's related terms in the background; re-count on return.
    val context = LocalContext.current
    OnResume { tick++ }
    val termCounts = remember(tick, showTermCounts) {
        if (showTermCounts) SmartKeywordStore(context).let { terms -> keywords.associateWith { terms.termCount(it) } } else emptyMap()
    }

    fun submit() {
        val word = input.trim()
        when {
            store.addBlockedKeyword(word) -> { input = ""; error = null; tick++ }
            word.length < 2 -> error = "Enter at least 2 characters."
            word.length > MAX_KEYWORD_CHARS -> error = "Keep it under $MAX_KEYWORD_CHARS characters."
            else -> error = "That word is already on the list."
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column {
                Text(
                    "Type a blocked word in any app and it closes for ${shortDuration(store.lockdownMs())}.",
                    style = StyleCaption
                )
                if (locked) {
                    Spacer(Modifier.height(12.dp))
                    ChildLockBanner()
                }
            }
        }
        item {
            GroupCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("Smart detection", style = StyleBodyStrong)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when {
                                hasAiKey -> "Each word becomes a whole topic: its titles, names, sites, slang and adult side. With “anime” listed, “Solo Leveling” and “hentai” are caught too."
                                !aiInBuild -> "Not available in this build. Word matching still works."
                                else -> "AI features are for adults (18+). Word matching still works."
                            },
                            style = StyleCaption
                        )
                        if (hasAiKey) {
                            Spacer(Modifier.height(6.dp))
                            Text("When on, short text you type (never passwords) is sent to Google's Gemini API, and Google may use it to improve its products.", style = StyleHint)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    AppSwitch(
                        checked = smartOn && hasAiKey, enabled = hasAiKey && !locked,
                        onCheckedChange = { smartOn = it; store.setSmartKeywords(it) }
                    )
                }
                RowDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("Browser safety", style = StyleBodyStrong)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Off unless you turn it on. When on, a browser showing explicit-content words (like porn or nsfw) pauses for ${describeDuration(SEVERE_BLOCK_MS)}.",
                            style = StyleCaption
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Checked on your phone only. Nothing is sent anywhere.", style = StyleHint)
                    }
                    Spacer(Modifier.width(12.dp))
                    AppSwitch(
                        checked = browserSafety, enabled = !locked,
                        onCheckedChange = { browserSafety = it; store.setBrowserSafety(it) }
                    )
                }
            }
        }
        item {
            Section("Add a word") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(
                        value = input, onValueChange = { input = it; error = null },
                        placeholder = "e.g. anime",
                        modifier = Modifier.weight(1f), enabled = !locked,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) submit() })
                    )
                    Spacer(Modifier.width(10.dp))
                    val canAdd = !locked && input.isNotBlank()
                    Box(
                        Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (canAdd) TextPrimary else Surface2)
                            .clickable(enabled = canAdd) { submit() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Add, "Add word", tint = if (canAdd) Ink else TextTertiary)
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = StyleCaption.copy(color = TextPrimary))
                }
            }
        }
        item {
            Section(if (keywords.isEmpty()) "Blocked words" else "Blocked words (${keywords.size})") {
                if (keywords.isEmpty()) {
                    GroupCard {
                        EmptyState(
                            icon = Icons.Outlined.Block,
                            title = "No words yet",
                            body = "Add words or topics you want to stay away from."
                        )
                    }
                } else {
                    GroupCard {
                        keywords.forEachIndexed { index, word ->
                            if (index > 0) RowDivider()
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(word, style = StyleBodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    termCounts[word]?.let { count ->
                                        Text(
                                            if (count == 0) "Finding related terms…" else "$count related terms on your phone",
                                            style = StyleHint
                                        )
                                    }
                                }
                                IconButton(onClick = { store.removeBlockedKeyword(word); tick++ }, enabled = !locked) {
                                    Icon(Icons.Outlined.Close, "Remove $word", tint = if (locked) Border else TextTertiary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Disclosure(
                title = "How it works",
                points = listOf(
                    "CancelIt watches what you type in other apps. The accessibility service needs to be on.",
                    "A word matches when it starts with your keyword, so “anime” also catches “animes”.",
                    "Smart detection treats each word as a whole topic, including its adult side. For each word, AI lists hundreds of titles, names, sites and slang terms once, and they're caught on your phone. Other short text (under $AI_MAX_CHARS characters, not just everyday words) is checked by AI after you pause typing. AI checks are capped per hour and per day, and if Gemini can't be reached the text is allowed.",
                    "Only your own topics are blocked. Nothing else, explicit or not, is blocked unless it belongs to a word on your list, or you turn on Browser safety.",
                    "Your exact words lock the app straight away. When only AI thinks something matches, you're asked first. \"Close app\" also remembers that phrase, so it locks straight away next time. \"I'm fine\" keeps that topic quiet in that app for 10 minutes.",
                    "Password fields are ignored, and only the matched word (never what you typed) goes in your activity log.",
                    "Typing inside CancelIt isn't checked, so you can always edit this list."
                )
            )
        }
    }
}
