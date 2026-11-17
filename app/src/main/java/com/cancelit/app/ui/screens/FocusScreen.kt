package com.cancelit.app.ui.screens

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.cancelit.app.data.FocusApp
import com.cancelit.app.data.FocusStore
import com.cancelit.app.data.GRACE_WARNING_MS
import com.cancelit.app.data.MAX_GRACE_MS
import com.cancelit.app.data.MAX_LOCKDOWN_MS
import com.cancelit.app.data.MIN_GRACE_MS
import com.cancelit.app.data.MIN_LOCKDOWN_MS
import com.cancelit.app.data.PRESENCE_INTERVAL_MS
import com.cancelit.app.data.deleteAllData
import com.cancelit.app.describeDuration
import com.cancelit.app.formatClock
import com.cancelit.app.formatDuration
import com.cancelit.app.service.FocusAccessibilityService
import com.cancelit.app.service.lockNotificationId
import com.cancelit.app.shortDuration
import com.cancelit.app.ui.AppAvatar
import com.cancelit.app.ui.AppDialog
import com.cancelit.app.ui.Border
import com.cancelit.app.ui.CancelItLogo
import com.cancelit.app.ui.EmptyState
import com.cancelit.app.ui.GroupCard
import com.cancelit.app.ui.Ink
import com.cancelit.app.ui.InfoRow
import com.cancelit.app.ui.OnResume
import com.cancelit.app.ui.PillButton
import com.cancelit.app.ui.PillShape
import com.cancelit.app.ui.PrimaryButton
import com.cancelit.app.ui.ProgressRing
import com.cancelit.app.ui.RowDivider
import com.cancelit.app.ui.ScreenHeader
import com.cancelit.app.ui.SecondaryButton
import com.cancelit.app.ui.Section
import com.cancelit.app.ui.StatusPill
import com.cancelit.app.ui.StyleBodyStrong
import com.cancelit.app.ui.StyleCaption
import com.cancelit.app.ui.StyleHint
import com.cancelit.app.ui.StyleNumber
import com.cancelit.app.ui.Surface1
import com.cancelit.app.ui.Surface2
import com.cancelit.app.ui.TextPrimary
import com.cancelit.app.ui.TextSecondary
import com.cancelit.app.ui.TextTertiary
import kotlinx.coroutines.delay

/** Seconds the emergency unlock makes you wait before it can be confirmed. */
private const val EMERGENCY_UNLOCK_WAIT_S = 10

@Composable
internal fun FocusScreen(
    store: FocusStore,
    openAccessibility: () -> Unit,
    context: Context,
    goToChild: () -> Unit,
    goToBlacklist: () -> Unit,
    openPrivacyPolicy: () -> Unit,
    openLicenses: () -> Unit
) {
    var refreshTick by remember { mutableIntStateOf(0) }
    // Coming back from system Settings doesn't recompose this by itself.
    OnResume { refreshTick++ }

    // Switched on but not agreed to counts as off: the service ignores everything until then.
    val serviceOn = remember(refreshTick) { FocusAccessibilityService.isEnabled(context) && store.accessibilityConsent() }
    val protectedCount = remember(refreshTick) { store.enabledPackages().size }
    val childLocked = remember(refreshTick) { store.childModeOn() }
    val graceMs = remember(refreshTick) { store.graceMs() }
    val lockMs = remember(refreshTick) { store.lockdownMs() }
    val anyLocked = remember(refreshTick) { store.lockedPackages().isNotEmpty() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            ScreenHeader(
                title = "CancelIt",
                subtitle = "Take back your attention.",
                leading = { CancelItLogo(size = 40.dp) },
                trailing = {
                    StatusPill(
                        if (serviceOn) "Protected" else "Not active", on = serviceOn,
                        onClick = if (serviceOn) null else openAccessibility
                    )
                }
            )
        }
        if (childLocked) {
            item { ChildModeNotice(onManage = goToChild) }
        }
        if (!serviceOn) {
            item {
                Section("Finish setup") {
                    GroupCard {
                        SetupRow("Accessibility access", "Lets CancelIt close and lock apps.", "Turn on", openAccessibility)
                    }
                }
            }
        }
        item { TimerHero(store, goToBlacklist) }
        if (anyLocked) {
            item {
                EmergencyUnlockRow {
                    val unlocked = store.clearAllBlocks()
                    val notifications = NotificationManagerCompat.from(context)
                    unlocked.forEach { notifications.cancel(lockNotificationId(it)) }
                    store.log("Emergency unlock", "Unlocked ${unlocked.size} app(s)")
                    refreshTick++
                }
            }
        }
        item {
            Section("Rules") {
                GroupCard {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Protected apps", style = StyleBodyStrong)
                            Text("Apps CancelIt keeps in check", style = StyleHint)
                        }
                        Text(if (protectedCount == 1) "1 app" else "$protectedCount apps", style = StyleNumber)
                        Spacer(Modifier.width(12.dp))
                        PillButton("Manage", onClick = goToBlacklist)
                    }
                    RowDivider()
                    GraceTimeRule(store, childLocked) { refreshTick++ }
                    RowDivider()
                    LockdownTimeRule(store, childLocked) { refreshTick++ }
                }
            }
        }
        item {
            Section("How it works") {
                GroupCard {
                    InfoRow(
                        Icons.Outlined.Timer, "Time limits",
                        "Each app gets ${describeDuration(graceMs)}, then closes and locks for ${describeDuration(lockMs)}. " +
                                "A break that long resets the time too."
                    )
                    RowDivider()
                    InfoRow(
                        Icons.Outlined.Notifications, "Check-ins",
                        "In Study mode we ask “Are you there?” every ${describeDuration(PRESENCE_INTERVAL_MS)}. Miss it for a minute and your apps lock."
                    )
                    RowDivider()
                    InfoRow(
                        Icons.Outlined.Block, "Blocked words",
                        "Type a word or topic from your list in any app and it closes. Nothing is blocked unless you add it."
                    )
                }
            }
        }
        item {
            Column {
                SecondaryButton("Privacy policy", Modifier.fillMaxWidth(), onClick = openPrivacyPolicy)
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Open-source licenses", Modifier.fillMaxWidth(), onClick = openLicenses)
                Spacer(Modifier.height(10.dp))
                DeleteDataButton(enabled = !childLocked) {
                    deleteAllData(context)
                    (context as? ComponentActivity)?.recreate()
                }
            }
        }
    }
}

/** Shown while any app is locked: unlocks everything after a short, deliberate wait. Works
 *  even with PIN lock on, because needing an app in an emergency comes first. */
@Composable
private fun EmergencyUnlockRow(onUnlock: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    GroupCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Need a locked app urgently?", style = StyleBodyStrong)
                Text("Emergency unlock opens every app now. It's noted in your activity log.", style = StyleHint)
            }
            Spacer(Modifier.width(12.dp))
            PillButton("Unlock", onClick = { asking = true })
        }
    }
    if (asking) {
        var secondsLeft by remember { mutableIntStateOf(EMERGENCY_UNLOCK_WAIT_S) }
        LaunchedEffect(Unit) { while (secondsLeft > 0) { delay(1_000); secondsLeft-- } }
        AppDialog(
            title = "Emergency unlock",
            confirmLabel = if (secondsLeft > 0) "Unlock in $secondsLeft" else "Unlock all",
            onConfirm = { asking = false; onUnlock() },
            onDismiss = { asking = false },
            confirmEnabled = secondsLeft == 0
        ) {
            Text(
                "This unlocks every app right away. Use it when you really need one, like for " +
                        "directions, a ticket or work. Phone and messaging apps are never locked.",
                style = StyleCaption
            )
        }
    }
}

/** Disabled while PIN lock is on, so it can't be used to skip the PIN (uninstalling CancelIt
 *  still removes everything). */
@Composable
private fun DeleteDataButton(enabled: Boolean, onDelete: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    SecondaryButton("Delete all my data", Modifier.fillMaxWidth()) { if (enabled) asking = true }
    if (!enabled) {
        Spacer(Modifier.height(6.dp))
        Text("Turn off PIN lock first, or uninstall CancelIt to remove everything.", style = StyleHint)
    }
    if (asking) {
        AppDialog(
            title = "Delete all your data?",
            confirmLabel = "Delete",
            onConfirm = { asking = false; onDelete() },
            onDismiss = { asking = false }
        ) {
            Text(
                "This erases your settings, blocked apps and words, activity log and Coach chat " +
                        "from this phone, and CancelIt starts over. It can't be undone. " +
                        "CancelIt keeps no copy elsewhere.",
                style = StyleCaption
            )
        }
    }
}

@Composable
private fun ChildModeNotice(onManage: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface1)
            .border(1.dp, Border, shape)
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Lock, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text("Settings are locked by PIN lock.", style = StyleCaption, modifier = Modifier.weight(1f))
        PillButton("Manage", onClick = onManage)
    }
}

@Composable
private fun SetupRow(title: String, body: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = StyleBodyStrong)
            Text(body, style = StyleCaption)
        }
        Spacer(Modifier.width(12.dp))
        PillButton(action, filled = true, onClick = onClick)
    }
}

// ---------------- Live timer ----------------

private data class TimerRow(
    val app: FocusApp,
    val locked: Boolean,
    val leftMs: Long,
    val usedMs: Long,
    val fraction: Float
)

/**
 * The hero of the Focus tab. The unlocked app closest to running out is the big ring; anything
 * else that's locked or partly used is listed under it. Ticks every second on its own so it
 * doesn't force the whole Focus screen to recompose.
 */
@Composable
private fun TimerHero(store: FocusStore, goToBlacklist: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }

    // The catalog only changes on the List tab, so it doesn't need re-parsing every second.
    val catalog = remember { store.allCatalogApps() }
    val enabled = store.enabledPackages()
    val graceMs = store.graceMs()
    val lockMs = store.lockdownMs()
    val rows = catalog.filter { it.packageName in enabled }.map { app ->
        val until = store.blockUntil(app.packageName)
        val used = store.usedMs(app.packageName)
        if (until > now) {
            val start = store.blockStart(app.packageName).takeIf { it > 0L } ?: (until - lockMs)
            val total = (until - start).coerceAtLeast(1L)
            TimerRow(app, true, until - now, used, ((until - now).toFloat() / total.toFloat()).coerceIn(0f, 1f))
        } else {
            TimerRow(app, false, (graceMs - used).coerceAtLeast(0L), used, (used.toFloat() / graceMs.toFloat()).coerceIn(0f, 1f))
        }
    }
    val featured = rows.filter { !it.locked }.minByOrNull { it.leftMs }
    // When nothing is unlocked, the ring counts down to the next app coming back.
    val hero = featured ?: rows.minByOrNull { it.leftMs }
    val others = rows.filter { it !== hero && (it.locked || it.usedMs > 0L) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (hero == null) {
            EmptyState(
                icon = Icons.Outlined.Shield,
                title = "No protected apps yet",
                body = "Choose the apps you want to keep in check.",
                action = { PrimaryButton("Choose apps", Modifier.width(180.dp), onClick = goToBlacklist) }
            )
        } else {
            // Ring shows what's left: full at the start of the allowance, empty at zero.
            val ringFraction = if (hero.locked) hero.fraction else 1f - hero.fraction
            // Last 10 seconds: the number blinks.
            val dim = !hero.locked && hero.leftMs <= 10_000L && (now / 1000L) % 2L == 0L

            ProgressRing(ringFraction, Modifier.size(236.dp).padding(4.dp), stroke = 10.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(hero.app.label, style = StyleCaption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        formatClock(hero.leftMs),
                        style = TextStyle(
                            fontSize = 54.sp, lineHeight = 60.sp, fontWeight = FontWeight.Light,
                            letterSpacing = (-1).sp, fontFeatureSettings = "tnum"
                        ),
                        color = if (dim) TextTertiary else TextPrimary
                    )
                    Text(if (hero.locked) "until unlocked" else "left", style = StyleHint)
                }
            }
            Spacer(Modifier.height(14.dp))
            val almostOut = !hero.locked && hero.leftMs <= minOf(GRACE_WARNING_MS, graceMs / 3)
            Text(
                when {
                    hero.locked -> "Everything on your list is locked. Take a breather."
                    almostOut -> "Almost out. At zero it locks for ${shortDuration(lockMs)}."
                    hero.usedMs > 0L -> "Used ${formatDuration(hero.usedMs)} of ${shortDuration(graceMs)}."
                    else -> "${shortDuration(graceMs)} per app."
                },
                style = StyleCaption, textAlign = TextAlign.Center
            )
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                GroupCard {
                    others.forEachIndexed { index, r ->
                        if (index > 0) RowDivider()
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppAvatar(r.app, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(r.app.label, style = StyleBodyStrong, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (r.locked) {
                                Icon(Icons.Outlined.Lock, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(formatClock(r.leftMs), style = StyleNumber.copy(color = TextSecondary))
                            } else {
                                Text("${formatClock(r.leftMs)} left", style = StyleNumber.copy(color = TextSecondary))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Duration editing ----------------

@Composable
private fun GraceTimeRule(store: FocusStore, locked: Boolean, onChanged: () -> Unit) = DurationRule(
    title = "Time per app",
    caption = "Then the app locks",
    dialCaption = "per app",
    footnote = "Time an app has already used still counts, so a shorter limit can lock it straight away.",
    savedMs = store.graceMs(),
    minMs = MIN_GRACE_MS,
    maxMs = MAX_GRACE_MS,
    presets = listOf(
        5 * 60_000L to "5m", 15 * 60_000L to "15m", 30 * 60_000L to "30m",
        60 * 60_000L to "1h", 2 * 60 * 60_000L to "2h"
    ),
    locked = locked,
    onApply = { store.setGraceMs(it); onChanged(); store.graceMs() }
)

@Composable
private fun LockdownTimeRule(store: FocusStore, locked: Boolean, onChanged: () -> Unit) = DurationRule(
    title = "Lock length",
    caption = "How long a locked app stays closed",
    dialCaption = "per lock",
    footnote = "Applies to new locks. An app that's already locked keeps its current countdown.",
    savedMs = store.lockdownMs(),
    minMs = MIN_LOCKDOWN_MS,
    maxMs = MAX_LOCKDOWN_MS,
    presets = listOf(
        5 * 60_000L to "5m", 15 * 60_000L to "15m", 30 * 60_000L to "30m",
        60 * 60_000L to "1h", 3 * 60 * 60_000L to "3h"
    ),
    locked = locked,
    onApply = { store.setLockdownMs(it); onChanged(); store.lockdownMs() }
)

/**
 * One editable duration inside the Rules card. Collapsed it's a single row with the current
 * value; "Edit" opens a dial with steppers and presets. Nothing changes until "Apply" is tapped.
 * [onApply] saves the value and returns what was actually stored (after clamping).
 */
@Composable
private fun DurationRule(
    title: String,
    caption: String,
    dialCaption: String,
    footnote: String,
    savedMs: Long,
    minMs: Long,
    maxMs: Long,
    presets: List<Pair<Long, String>>,
    locked: Boolean = false,
    onApply: (Long) -> Long
) {
    var saved by remember { mutableLongStateOf(savedMs) }
    var pending by remember { mutableLongStateOf(saved) }
    var expanded by remember { mutableStateOf(false) }

    // PIN lock engaging mid-edit closes the editor instead of leaving a stale one open.
    LaunchedEffect(locked) { if (locked) { expanded = false; pending = saved } }

    fun bump(deltaMs: Long) { pending = (pending + deltaMs).coerceIn(minMs, maxMs) }

    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = StyleBodyStrong)
                Text(caption, style = StyleHint)
            }
            Text(shortDuration(saved), style = StyleNumber)
            Spacer(Modifier.width(12.dp))
            if (locked) {
                Box(Modifier.width(76.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(Icons.Outlined.Lock, "Locked by PIN lock", tint = TextTertiary, modifier = Modifier.size(18.dp))
                }
            } else {
                PillButton(if (expanded) "Close" else "Edit", Modifier.width(76.dp)) {
                    expanded = !expanded
                    if (!expanded) pending = saved
                }
            }
        }
        if (expanded && !locked) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                ProgressRing(
                    fraction = dialFraction(pending),
                    modifier = Modifier.size(196.dp).align(Alignment.CenterHorizontally).padding(4.dp),
                    stroke = 8.dp,
                    animate = false
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatClock(pending, fixedHours = true),
                            style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Light, fontFeatureSettings = "tnum"),
                            color = TextPrimary
                        )
                        Text(dialCaption, style = StyleHint)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    UnitStepper("Hours", { bump(3_600_000L) }, { bump(-3_600_000L) })
                    UnitStepper("Minutes", { bump(60_000L) }, { bump(-60_000L) })
                    UnitStepper("Seconds", { bump(5_000L) }, { bump(-5_000L) })
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (ms, pillLabel) ->
                        TimePill(pillLabel, selected = pending == ms, modifier = Modifier.weight(1f)) { pending = ms }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(footnote, style = StyleHint)
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = "Apply ${describeDuration(pending)}",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = pending != saved
                ) {
                    saved = onApply(pending)
                    pending = saved
                    expanded = false
                }
            }
        }
    }
}

/** Sweep is the minute hand: 30 min is half a turn, a full hour (or 2, 3...) is a full turn. */
private fun dialFraction(ms: Long): Float {
    val hourMs = 3_600_000L
    return if (ms > 0L && ms % hourMs == 0L) 1f else (ms % hourMs).toFloat() / hourMs.toFloat()
}

@Composable
private fun UnitStepper(label: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepperButton(Icons.Outlined.KeyboardArrowUp, "More $label", onUp)
        Spacer(Modifier.height(4.dp))
        Text(label, style = StyleHint)
        Spacer(Modifier.height(4.dp))
        StepperButton(Icons.Outlined.KeyboardArrowDown, "Less $label", onDown)
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Surface2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = TextPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TimePill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(36.dp)
            .clip(PillShape)
            .background(if (selected) TextPrimary else Surface2)
            .border(1.dp, if (selected) TextPrimary else Border, PillShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Ink else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
