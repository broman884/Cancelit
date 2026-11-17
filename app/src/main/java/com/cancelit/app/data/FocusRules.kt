package com.cancelit.app.data

// The timing rules, shared by the accessibility service (which enforces them) and the screens
// (which describe them).
//
// Each protected app gets a grace allowance of foreground time. Once it's used up, the app is
// locked for the lockdown time. Opening it while locked just sends the user home again, so
// there's no way to wait it out by re-launching. When the lock ends, the allowance starts fresh,
// and so does it after a break from the app at least as long as the lockdown time.
// The user can change both times in the Focus tab, within the limits below.

internal const val DEFAULT_GRACE_MS = 5 * 60 * 1000L
internal const val MIN_GRACE_MS = 10_000L
internal const val MAX_GRACE_MS = 6 * 60 * 60 * 1000L

internal const val DEFAULT_LOCKDOWN_MS = 5 * 60 * 1000L
internal const val MIN_LOCKDOWN_MS = 60_000L
internal const val MAX_LOCKDOWN_MS = 24 * 60 * 60 * 1000L

/** A heads-up fires once when this little of the allowance is left. */
internal const val GRACE_WARNING_MS = 60_000L

/** With Browser safety on, how long a browser showing explicit-content words stays locked. */
internal const val SEVERE_BLOCK_MS = 45 * 60 * 1000L

/** Study mode checks recent screen text against the topic this often. */
internal const val STUDY_CHECK_INTERVAL_MS = 3 * 60 * 1000L

// While a Study session runs, CancelIt asks "Are you there?" every PRESENCE_INTERVAL_MS.
// Confirming restarts the cycle; no answer within PRESENCE_TIMEOUT_MS closes what's open and
// locks every distracting app.
internal const val PRESENCE_INTERVAL_MS = 10 * 60 * 1000L
internal const val PRESENCE_TIMEOUT_MS = 60 * 1000L
/** Missed check-ins in a row that end a session nobody is attending (a forgotten session would
 *  otherwise buzz and lock apps every 10 minutes all night). */
internal const val PRESENCE_MAX_MISSES = 3

/** Longest word the keyword blocker accepts. */
internal const val MAX_KEYWORD_CHARS = 40

/** True while the "Are you there?" prompt sent at [promptAt] is unanswered and inside its minute. */
internal fun presencePromptOpen(promptAt: Long, ackAt: Long, now: Long): Boolean =
    promptAt > 0L && ackAt < promptAt && now - promptAt < PRESENCE_TIMEOUT_MS
