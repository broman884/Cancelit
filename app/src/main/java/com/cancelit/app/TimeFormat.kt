package com.cancelit.app

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Every duration and time of day CancelIt shows. Locale.US keeps the digits Western and the
// format predictable on every device locale.

/** Whole hours, minutes and seconds in [ms]; negative counts as zero. */
private fun hms(ms: Long): Triple<Long, Long, Long> {
    val total = ms.coerceAtLeast(0L) / 1000L
    return Triple(total / 3600, (total % 3600) / 60, total % 60)
}

/** "5 min", "1 h 30 min", "45 sec": compact form for values shown next to a label. */
internal fun shortDuration(ms: Long): String = durationWords(ms, long = false)

/** "5 minutes", "1 hour 30 minutes", "45 seconds": for sentences. */
internal fun describeDuration(ms: Long): String = durationWords(ms, long = true)

private fun durationWords(ms: Long, long: Boolean): String {
    val (h, m, s) = hms(ms)
    fun part(n: Long, short: String, unit: String) = when {
        !long -> "$n $short"
        n == 1L -> "1 $unit"
        else -> "$n ${unit}s"
    }
    val parts = buildList {
        if (h > 0) add(part(h, "h", "hour"))
        if (m > 0) add(part(m, "min", "minute"))
        if (s > 0) add(part(s, "sec", "second"))
    }
    return if (parts.isEmpty()) part(0L, "sec", "second") else parts.joinToString(" ")
}

/** "1h 05m", "4m 32s", "12s": the two largest units, for countdowns written as text. */
internal fun formatDuration(ms: Long): String {
    val (h, m, s) = hms(ms)
    return when {
        h > 0 -> String.format(Locale.US, "%dh %02dm", h, m)
        m > 0 -> String.format(Locale.US, "%dm %02ds", m, s)
        else -> String.format(Locale.US, "%ds", s)
    }
}

/**
 * Clock digits: "04:32", or "1:04:32" once there's an hour. Countdowns round up so they never
 * show 00:00 early; elapsed timers pass [roundUp] = false so they never run ahead of the clock.
 * [fixedHours] always shows zero-padded hours ("00:05:00").
 */
internal fun formatClock(ms: Long, roundUp: Boolean = true, fixedHours: Boolean = false): String {
    val (h, m, s) = hms(if (roundUp) ms.coerceAtLeast(0L) + 999L else ms)
    return when {
        fixedHours -> String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        h > 0 -> String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else -> String.format(Locale.US, "%02d:%02d", m, s)
    }
}

/** "Just now", "12m ago", "3h ago", then a short date once it's older than a day. */
internal fun relativeTime(time: Long): String {
    val diff = (System.currentTimeMillis() - time).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(time))
    }
}

/** The time of day in the phone's own format, like "4:42 pm". */
internal fun clockTime(millis: Long = System.currentTimeMillis()): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
