package com.cancelit.app.ai

import com.cancelit.app.data.ActivityRecord
import com.cancelit.app.data.ChatMessage
import com.cancelit.app.data.FocusStore
import com.cancelit.app.data.ROLE_MODEL
import com.cancelit.app.data.ROLE_NOTICE
import com.cancelit.app.data.weeklyActivityCounts
import com.cancelit.app.describeDuration
import com.cancelit.app.formatDuration
import com.google.firebase.ai.type.content

// Coach chat. Only the latest turns are sent as context, each trimmed, and replies are short.
private const val COACH_CONTEXT_MESSAGES = 12
private const val COACH_CONTEXT_MSG_CHARS = 800
internal const val COACH_MAX_INPUT_CHARS = 600
private const val COACH_MAX_OUTPUT_TOKENS = 450

/**
 * The Coach's reply to the chat so far, grounded in a fresh summary of the user's local
 * activity. Null on any failure or once the daily coach budget is used; the caller then shows a
 * safe local message.
 */
internal suspend fun askCoach(store: FocusStore): String? =
    callGeminiChat(buildCoachSystemPrompt(buildReportSummary(store)), store.chatHistory())

/**
 * Turns the user's local activity log + current settings into a short text summary that grounds
 * the coach's replies. It only leaves the phone as context for a chat reply the user asked for.
 */
private fun buildReportSummary(store: FocusStore): String {
    val events = store.events()
    val keywordSection = buildKeywordSummary(store)
    if (events.isEmpty() && keywordSection.isBlank()) {
        return "No activity logged yet — the user hasn't triggered any focus events on this device."
    }
    val lockdowns = events.count { it.detail.contains("locked down", true) }
    val presenceMisses = events.count { it.detail.contains("no response", true) }
    val recentLines = events.take(12).joinToString("\n") { e ->
        val ago = formatDuration(System.currentTimeMillis() - e.time)
        "- ${e.label}: ${e.detail} ($ago ago)"
    }
    return """
        Protected apps currently enabled: ${store.enabledPackages().size}
        Grace time per protected app: ${describeDuration(store.graceMs())}
        Lockdown time once an app is locked: ${describeDuration(store.lockdownMs())}
        Times locked down recently: $lockdowns
        Missed "are you there?" presence checks: $presenceMisses
        Study mode currently active: ${store.studyActive()}
        Recent activity log, most recent first:
        $recentLines
        $keywordSection
    """.trimIndent()
}

/**
 * The keyword blocker's numbers for the coach: how often the user actually tried to type one of
 * their blacklisted keywords, overall and recently. Higher and more recent counts mean a stronger
 * urge or habit. Only the keywords from the user's own list appear, never what they typed.
 */
private fun buildKeywordSummary(store: FocusStore): String {
    val listed = store.blockedKeywords()
    val hits = store.keywordHits()
    if (listed.isEmpty() && hits.isEmpty()) return ""
    val header = "Keyword blocker words the user listed: ${if (listed.isEmpty()) "none" else listed.joinToString(", ")}"
    if (hits.isEmpty()) return "$header\nNo attempts to type them have been recorded yet."

    val now = System.currentTimeMillis()
    val dayMs = 24 * 60 * 60 * 1000L
    val last24 = hits.count { now - it.time <= dayMs }
    val last7 = hits.count { now - it.time <= 7 * dayMs }
    val viaAi = hits.count { it.viaAi }
    val lastAgo = formatDuration(now - hits.maxOf { it.time })
    val perWord = hits.groupBy { it.keyword }.entries
        .sortedByDescending { it.value.size }.take(8)
        .joinToString("\n") { (word, list) ->
            "- \"$word\": ${list.size} total, ${list.count { now - it.time <= 7 * dayMs }} in the last 7 days"
        }
    val perDay = weeklyActivityCounts(hits.map { ActivityRecord(it.time, it.keyword, "") })
        .joinToString(", ") { "${it.first} ${it.second}" }
    return """
        $header
        Times the user tried to type a blacklisted keyword and was blocked (a higher count means a stronger urge or habit): ${hits.size} total, $last7 in the last 7 days, $last24 in the last 24 hours, $viaAi of them caught by AI topic detection. Most recent: $lastAgo ago.
        Per keyword:
        $perWord
        Per day over the last 7 days, oldest first: $perDay
    """.trimIndent()
}

/**
 * System instruction for the Coach chat. Keeps the model in a practical, non-clinical role as a
 * focus coach (not a companion: California's companion-chatbot law, SB 243, covers those),
 * grounded in the user's own local data, with explicit guardrails around diagnosis and crisis
 * situations. Rebuilt fresh on every send so the report summary is always current.
 */
private fun buildCoachSystemPrompt(reportSummary: String): String = """
    You are "CancelIt Coach", an AI focus and habit coach inside a phone app called CancelIt that
    helps people cut down on distracting apps (social media, short-form video, etc.), study with
    focus and build better digital habits. Users may also bring up related topics like screen
    time, doom-scrolling, procrastination, motivation or study routines. Talk about habits and
    urges, not "addiction", unless the user uses that word.

    Ground rules, always follow these:
    - You are an AI, not a person. Say so plainly if asked. Don't present yourself as a friend,
      partner or companion, and keep the conversation on focus, study and digital habits.
    - You are NOT a licensed doctor, therapist, psychiatrist, or addiction counselor. Never claim
      to be one, never diagnose a condition, and never prescribe medication or a clinical
      treatment plan.
    - Ground your coaching in the user's own activity report below where it's relevant. Never
      invent data that isn't in it.
    - The report may include how often the user tried to type their blacklisted keywords. Treat
      those counts as a sign of how strong the urge or habit is, and mention trends, recent spikes
      or quiet days gently and specifically. Never shame, never diagnose, and don't repeat explicit
      terms more than you need to.
    - Be warm, specific, and practical. Never shame, lecture, or moralize.
    - Keep replies conversational: usually 2-5 sentences, longer only if the user clearly wants
      more detail.
    - If the user's message suggests a serious substance-use disorder, self-harm, suicidal
      thoughts, or any kind of crisis, respond with care, take it seriously, and clearly encourage
      them to reach out to a real crisis line, emergency services, or another trusted person right
      away, in addition to anything else you say. Do not attempt to handle a crisis on your own.
    - For anything outside general habit, focus, or wellness coaching — specific medical, legal,
      or psychiatric questions — say plainly that's outside what you can help with here and
      suggest a qualified professional.

    The user's local, on-device activity report, to ground your coaching:
    $reportSummary
""".trimIndent()

/**
 * Multi-turn Gemini call: a system instruction plus the latest [COACH_CONTEXT_MESSAGES] turns,
 * each trimmed. The whole saved history isn't sent, because resending it on every turn would be
 * the Coach's main token cost.
 */
private suspend fun callGeminiChat(systemPrompt: String, history: List<ChatMessage>): String? {
    // The context must start with a user turn.
    // The app's own notices ("couldn't reach the AI") aren't part of the conversation.
    val turns = history.filter { it.role != ROLE_NOTICE }
    val contents = turns.takeLast(COACH_CONTEXT_MESSAGES).dropWhile { it.role == ROLE_MODEL }.map { msg ->
        content(role = if (msg.role == ROLE_MODEL) "model" else "user") { text(msg.text.take(COACH_CONTEXT_MSG_CHARS)) }
    }
    if (contents.isEmpty()) return null
    // The Lite models: the Coach is paid for on every message, and short coaching replies don't
    // need the bigger model's knowledge (Flash costs about 3 to 6 times as much).
    return GeminiClient.firstUsable(
        LITE_MODELS,
        call = { model ->
            GeminiClient.generate(
                AiFeature.COACH, model, contents,
                maxOutputTokens = COACH_MAX_OUTPUT_TOKENS, systemInstruction = systemPrompt,
                readTimeoutMs = 60_000
            )
        },
        parse = { it }
    )
}
