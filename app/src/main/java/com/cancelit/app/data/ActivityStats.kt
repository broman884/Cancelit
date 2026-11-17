package com.cancelit.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Count of [events], bucketed into the last 7 calendar days (oldest first), labelled "Mon", "Tue", ... */
internal fun weeklyActivityCounts(events: List<ActivityRecord>): List<Pair<String, Int>> {
    val fmt = SimpleDateFormat("EEE", Locale.getDefault())
    // Walk real calendar days instead of adding 24 h, so a daylight-saving change can't shift a bucket.
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -6)
    }
    return (0 until 7).map {
        val dayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val dayEnd = cal.timeInMillis
        fmt.format(Date(dayStart)) to events.count { e -> e.time in dayStart until dayEnd }
    }
}

/** Buckets the logged events into the event categories the app produces. */
internal fun categoryBreakdown(events: List<ActivityRecord>): List<Pair<String, Int>> {
    val lockdowns = events.count { it.detail.contains("locked down", true) }
    val studyExits = events.count { it.label.equals("Study mode", true) && it.detail.contains("Distraction", true) }
    val presenceMisses = events.count { it.label.equals("Study mode", true) && it.detail.contains("No response", true) }
    val browserPauses = events.count { it.label.equals("Browser safety", true) }
    return listOf(
        "Lockdowns" to lockdowns,
        "Study exits" to studyExits,
        "Missed check-ins" to presenceMisses,
        "Browser pauses" to browserPauses
    )
}
