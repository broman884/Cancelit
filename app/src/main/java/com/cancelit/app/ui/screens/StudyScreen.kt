package com.cancelit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cancelit.app.data.FocusStore
import com.cancelit.app.data.PRESENCE_INTERVAL_MS
import com.cancelit.app.data.PRESENCE_TIMEOUT_MS
import com.cancelit.app.data.STUDY_CHECK_INTERVAL_MS
import com.cancelit.app.data.presencePromptOpen
import com.cancelit.app.describeDuration
import com.cancelit.app.formatClock
import com.cancelit.app.formatDuration
import com.cancelit.app.ui.AppTextField
import com.cancelit.app.ui.ButtonShape
import com.cancelit.app.ui.DotStyle
import com.cancelit.app.ui.GroupCard
import com.cancelit.app.ui.InfoRow
import com.cancelit.app.ui.Ink
import com.cancelit.app.ui.MetricTile
import com.cancelit.app.ui.PrimaryButton
import com.cancelit.app.ui.RowDivider
import com.cancelit.app.ui.ScreenHeader
import com.cancelit.app.ui.SecondaryButton
import com.cancelit.app.ui.Section
import com.cancelit.app.ui.SectionCard
import com.cancelit.app.ui.StatusDot
import com.cancelit.app.ui.StyleBody
import com.cancelit.app.ui.StyleCaption
import com.cancelit.app.ui.StyleHeading
import com.cancelit.app.ui.StyleHint
import com.cancelit.app.ui.StyleNumber
import com.cancelit.app.ui.StyleSection
import com.cancelit.app.ui.TextPrimary
import kotlinx.coroutines.delay

@Composable
internal fun StudyScreen(store: FocusStore) {
    var tick by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var topicInput by remember { mutableStateOf(store.studyTopic()) }

    // 500 ms keeps the one-minute check-in countdown honest without burning battery.
    LaunchedEffect(Unit) { while (true) { delay(500); now = System.currentTimeMillis() } }

    // Re-read on every tick: the accessibility service owns this state and writes to it from
    // outside Compose, so nothing here can be cached across frames.
    val state = remember(tick, now) { readStudyState(store) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ScreenHeader("Study", "Stay on topic. Drifting apps get closed.") }

        if (!state.active) {
            item {
                SectionCard {
                    Text("What are you studying?", style = StyleHeading)
                    Spacer(Modifier.height(12.dp))
                    AppTextField(topicInput, { topicInput = it }, "e.g. Organic chemistry")
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "Start session",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = topicInput.isNotBlank(),
                        onClick = {
                            if (topicInput.isNotBlank()) {
                                val start = System.currentTimeMillis()
                                store.setStudyTopic(topicInput.trim())
                                store.setStudyStart(start)
                                store.setStudyLastCheckAt(start)
                                store.setStudyLastResult("")
                                store.resetPresence(start)
                                store.setStudyActive(true)
                                store.log("Study mode", "Session started — topic: ${topicInput.trim()}")
                                tick++
                            }
                        }
                    )
                }
            }
        } else {
            // The check-in prompt takes over the top of the screen while it's live.
            if (state.presencePending(now)) {
                item {
                    PresencePromptCard(
                        remaining = state.presenceDeadline() - now,
                        onConfirm = { store.setPresenceAckAt(System.currentTimeMillis()); tick++ }
                    )
                }
            }
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(DotStyle.ACTIVE)
                        Spacer(Modifier.width(8.dp))
                        Text("Session active", style = StyleSection)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(state.topic, style = StyleHeading, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        formatClock(now - state.start, roundUp = false),
                        style = TextStyle(fontSize = 48.sp, lineHeight = 54.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
                        color = TextPrimary
                    )
                    Text("elapsed", style = StyleHint)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("Next topic check", formatDuration(state.nextTopicCheckAt() - now), Modifier.weight(1f))
                    MetricTile(
                        "Next check-in",
                        if (state.presencePending(now)) "Now" else formatDuration(state.nextCheckAt() - now),
                        Modifier.weight(1f)
                    )
                }
            }
            if (state.missed > 0 || state.lastResult.isNotBlank()) {
                item {
                    GroupCard {
                        if (state.missed > 0) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Missed check-ins", style = StyleBody, modifier = Modifier.weight(1f))
                                Text("${state.missed}", style = StyleNumber)
                            }
                        }
                        if (state.missed > 0 && state.lastResult.isNotBlank()) RowDivider()
                        if (state.lastResult.isNotBlank()) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Last check", style = StyleHint)
                                Spacer(Modifier.height(4.dp))
                                Text(state.lastResult, style = StyleCaption, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            item {
                SecondaryButton(
                    text = "End session",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        store.setStudyActive(false)
                        store.resetPresence()
                        store.log("Study mode", "Session ended")
                        tick++
                    }
                )
            }
        }

        item {
            Section("How it works", Modifier.padding(top = 8.dp)) {
                GroupCard {
                    InfoRow(
                        Icons.Outlined.School, "Topic checks",
                        "Every ${describeDuration(STUDY_CHECK_INTERVAL_MS)}, recent screen text is checked against your topic. If it looks off-topic, you're asked whether to close that app for ${describeDuration(store.lockdownMs())}. Adults (18+) only."
                    )
                    RowDivider()
                    InfoRow(
                        Icons.Outlined.Notifications, "Check-ins",
                        "Every ${describeDuration(PRESENCE_INTERVAL_MS)} we ask if you're still there. Miss it and your apps lock."
                    )
                    RowDivider()
                    InfoRow(
                        Icons.Outlined.Lock, "Privacy",
                        "Short on-screen text (no audio, video or images) goes to Google's Gemini API, through Google's Firebase, for the topic check. Google may use it to improve its products. Check-ins stay on your phone."
                    )
                }
            }
        }
    }
}

/** The in-app half of the check-in. It mirrors the notification, so a blocked or missed
 *  notification isn't the only way to answer. Deliberately the one white card on the screen. */
@Composable
private fun PresencePromptCard(remaining: Long, onConfirm: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TextPrimary)
            .padding(20.dp)
    ) {
        Text("Are you there?", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Confirm within ${formatDuration(remaining)} or your apps will lock.",
            fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF4A4A4A)
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = TextPrimary)
        ) { Text("Yes, I'm here", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
    }
}

/** One read of every study/check-in field, so the screen can't show a half-updated mix of
 *  values while the accessibility service writes to the same storage. */
private data class StudyUiState(
    val active: Boolean,
    val topic: String,
    val start: Long,
    val lastResult: String,
    val lastCheckAt: Long,
    val promptAt: Long,
    val ackAt: Long,
    val cycleAt: Long,
    val missed: Int
) {
    fun presencePending(now: Long) = presencePromptOpen(promptAt, ackAt, now)
    fun presenceDeadline() = promptAt + PRESENCE_TIMEOUT_MS
    fun nextCheckAt() = (if (cycleAt > 0L) cycleAt else start) + PRESENCE_INTERVAL_MS
    fun nextTopicCheckAt() = (if (lastCheckAt > 0L) lastCheckAt else start) + STUDY_CHECK_INTERVAL_MS
}

private fun readStudyState(store: FocusStore) = StudyUiState(
    active = store.studyActive(),
    topic = store.studyTopic(),
    start = store.studyStart(),
    lastResult = store.studyLastResult(),
    lastCheckAt = store.studyLastCheckAt(),
    promptAt = store.presencePromptAt(),
    ackAt = store.presenceAckAt(),
    cycleAt = store.presenceCycleAt(),
    missed = store.presenceMissed()
)
