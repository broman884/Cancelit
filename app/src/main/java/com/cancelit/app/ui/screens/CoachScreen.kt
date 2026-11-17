package com.cancelit.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cancelit.app.BuildConfig
import com.cancelit.app.ai.AiFeature
import com.cancelit.app.ai.COACH_MAX_INPUT_CHARS
import com.cancelit.app.ai.GeminiClient
import com.cancelit.app.ai.askCoach
import com.cancelit.app.data.ChatMessage
import com.cancelit.app.data.FocusStore
import com.cancelit.app.data.ROLE_MODEL
import com.cancelit.app.data.ROLE_NOTICE
import com.cancelit.app.data.ROLE_USER
import com.cancelit.app.ui.AppDialog
import com.cancelit.app.ui.AppTextField
import com.cancelit.app.ui.Border
import com.cancelit.app.ui.Ink
import com.cancelit.app.ui.PillButton
import com.cancelit.app.ui.PillShape
import com.cancelit.app.ui.ScreenHeader
import com.cancelit.app.ui.StyleBody
import com.cancelit.app.ui.StyleCaption
import com.cancelit.app.ui.StyleHeading
import com.cancelit.app.ui.StyleHint
import com.cancelit.app.ui.Surface1
import com.cancelit.app.ui.Surface2
import com.cancelit.app.ui.TextPrimary
import com.cancelit.app.ui.TextSecondary
import com.cancelit.app.ui.TextTertiary
import kotlinx.coroutines.launch

private val UserBubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
private val CoachBubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)

private val CoachPrompts = listOf(
    "Why do I keep opening Instagram?",
    "Help me get through an urge",
    "How did my week go?"
)

@Composable
internal fun CoachScreen(store: FocusStore) {
    var messages by remember { mutableStateOf(store.chatHistory()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf<String?>(null) }
    val adult = remember { store.aiAllowed() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun send(raw: String = input) {
        val text = raw.trim()
        if (text.isBlank() || sending || !adult) return
        input = ""
        store.appendChatMessage(ROLE_USER, text)
        messages = store.chatHistory()
        sending = true
        scope.launch {
            val reply = if (GeminiClient.canCall(AiFeature.COACH)) askCoach(store) else null
            if (reply != null) {
                store.appendChatMessage(ROLE_MODEL, reply)
            } else {
                // The app's own words, not the AI's: shown in the chat, but never sent to the AI
                // as something it said, and with no Report link.
                val why = when {
                    !GeminiClient.isConfigured() -> "The Coach isn't available in this version of the app."
                    !GeminiClient.adultConfirmed() -> "To talk to the AI Coach, please confirm you are 18 or older."
                    GeminiClient.overLimit(AiFeature.COACH) -> "That's all the coaching messages for now. I'll be back later."
                    else -> "I couldn't reach the AI service just now. Check your connection and try again in a minute."
                }
                store.appendChatMessage(ROLE_NOTICE, "$why In the meantime: pick one small, kind action for the next 10 minutes.")
            }
            messages = store.chatHistory()
            sending = false
        }
    }

    LaunchedEffect(messages.size, sending) {
        val lastIndex = messages.size - 1 + if (sending) 1 else 0
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            ScreenHeader(
                title = "Coach",
                subtitle = "An AI focus coach, not a person.",
                trailing = {
                    // Not while a reply is on its way: it would land in the cleared chat.
                    if (messages.isNotEmpty() && !sending) {
                        PillButton("Clear") { store.clearChatHistory(); messages = emptyList() }
                    }
                }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = TextPrimary, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Your habit coach", style = StyleHeading)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (adult) "Ask about urges, habits or how your week went. I can see your local report."
                            else "The Coach uses AI, which is only for adults (18+). The rest of CancelIt works as usual.",
                            style = StyleCaption, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        if (adult) {
                            CoachPrompts.forEach { prompt ->
                                PromptChip(prompt) { send(prompt) }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Not a doctor or therapist. For anything serious, contact a professional or a crisis line. " +
                                    "Google may use chats to improve its AI, so don't share sensitive personal details.",
                            style = StyleHint, textAlign = TextAlign.Center
                        )
                    }
                }
            }
            items(messages) { msg ->
                ChatBubble(msg, onReport = if (msg.role == ROLE_MODEL) ({ reporting = msg.text }) else null)
            }
            if (sending) {
                item { TypingBubble() }
            }
        }

        reporting?.let { reply ->
            ReportReplyDialog(
                onDismiss = { reporting = null },
                onReport = { reason ->
                    reporting = null
                    scope.launch {
                        val sent = GeminiClient.reportReply(reply, reason)
                        if (!sent && !emailReport(context, reply, reason)) {
                            Toast.makeText(context, "Couldn't send the report. Try again later.", Toast.LENGTH_SHORT).show()
                        } else if (sent) {
                            Toast.makeText(context, "Thanks. The reply was reported.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        if (adult) Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AppTextField(
                value = input,
                onValueChange = { input = it.take(COACH_MAX_INPUT_CHARS) },
                placeholder = "Message your coach",
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 4
            )
            Spacer(Modifier.width(10.dp))
            val canSend = input.isNotBlank() && !sending
            Box(
                Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (canSend) TextPrimary else Surface2)
                    .clickable(enabled = canSend) { send() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, "Send", tint = if (canSend) Ink else TextTertiary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun PromptChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(PillShape)
            .border(1.dp, Border, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, style = StyleCaption.copy(color = TextPrimary))
    }
}

/** [onReport] adds a "Report" link under the Coach's replies (Google Play's AI-generated content
 *  policy: users must be able to flag AI output from inside the app). */
@Composable
private fun ChatBubble(msg: ChatMessage, onReport: (() -> Unit)? = null) {
    val isUser = msg.role == ROLE_USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(if (isUser) UserBubbleShape else CoachBubbleShape)
                .background(if (isUser) TextPrimary else Surface1)
                .then(if (isUser) Modifier else Modifier.border(1.dp, Border, CoachBubbleShape))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(msg.text, style = StyleBody, color = if (isUser) Ink else TextPrimary)
        }
        if (onReport != null) {
            Text(
                "Report",
                style = StyleHint,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onReport)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

/** Asks for an optional reason before a Coach reply is reported. */
@Composable
private fun ReportReplyDialog(onDismiss: () -> Unit, onReport: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AppDialog(
        title = "Report this reply?",
        confirmLabel = "Report",
        onConfirm = { onReport(reason.trim()) },
        onDismiss = onDismiss
    ) {
        Column {
            Text(
                "The reply and anything you add below are sent to the CancelIt developer so it " +
                        "can be reviewed and the Coach improved.",
                style = StyleCaption
            )
            Spacer(Modifier.height(12.dp))
            AppTextField(
                value = reason,
                onValueChange = { reason = it.take(500) },
                placeholder = "What was wrong? (optional)",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 4
            )
        }
    }
}

/** Fallback when Firestore can't take the report (not set up, or offline): opens an email to
 *  SUPPORT_EMAIL. Returns false if there's no address or no email app. */
private fun emailReport(context: Context, reply: String, reason: String): Boolean {
    if (BuildConfig.SUPPORT_EMAIL.isBlank()) return false
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "CancelIt: reported Coach reply")
        putExtra(Intent.EXTRA_TEXT, "Reported reply:\n\n${reply.take(2_000)}\n\nWhat was wrong:\n$reason")
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

@Composable
private fun TypingBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            Modifier
                .clip(CoachBubbleShape)
                .background(Surface1)
                .border(1.dp, Border, CoachBubbleShape)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(0, 160, 320).forEach { offsetMs ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(offsetMs)
                    ),
                    label = "dot$offsetMs"
                )
                Box(Modifier.size(7.dp).clip(CircleShape).background(TextSecondary.copy(alpha = alpha)))
            }
        }
    }
}
