package com.cancelit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cancelit.app.data.ActivityRecord
import com.cancelit.app.data.FocusStore
import com.cancelit.app.data.categoryBreakdown
import com.cancelit.app.data.weeklyActivityCounts
import com.cancelit.app.relativeTime
import com.cancelit.app.ui.BarMuted
import com.cancelit.app.ui.Border
import com.cancelit.app.ui.EmptyState
import com.cancelit.app.ui.GroupCard
import com.cancelit.app.ui.MetricTile
import com.cancelit.app.ui.OnResume
import com.cancelit.app.ui.RowDivider
import com.cancelit.app.ui.ScreenHeader
import com.cancelit.app.ui.Section
import com.cancelit.app.ui.SectionCard
import com.cancelit.app.ui.StyleBodyStrong
import com.cancelit.app.ui.StyleCaption
import com.cancelit.app.ui.StyleHint
import com.cancelit.app.ui.StyleNumber
import com.cancelit.app.ui.TextPrimary
import com.cancelit.app.ui.TextSecondary
import com.cancelit.app.ui.TextTertiary
import com.cancelit.app.ui.ThinProgress

@Composable
internal fun ReportScreen(store: FocusStore) {
    var tick by remember { mutableIntStateOf(0) }
    // The service writes events while the app is in the background; re-read when it's back.
    OnResume { tick++ }

    // The chart needs the full history; the visible list only shows the newest 30.
    val history = remember(tick) { store.events() }
    val recent = remember(history) { history.take(30) }
    val weekly = remember(history) { weeklyActivityCounts(history) }
    val breakdown = remember(history) { categoryBreakdown(history) }
    val maxBreakdown = (breakdown.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val lockdownsTotal = history.count { it.detail.contains("locked down", true) }

    // Blocked-keyword hits: how often the user tried to type something on their keyword list.
    val hits = remember(tick) { store.keywordHits() }
    val hitsWeekly = remember(hits) { weeklyActivityCounts(hits.map { ActivityRecord(it.time, it.keyword, "") }) }
    val hitsByKeyword = remember(hits) {
        hits.groupingBy { it.keyword }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }
    }
    val maxHits = (hitsByKeyword.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val hitsLast24h = remember(hits) { val n = System.currentTimeMillis(); hits.count { n - it.time <= 24 * 60 * 60 * 1000L } }
    val hitsViaAi = remember(hits) { hits.count { it.viaAi } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ScreenHeader("Report", "Kept only on this phone.") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("Events", history.size.toString(), Modifier.weight(1f))
                MetricTile("Lockdowns", lockdownsTotal.toString(), Modifier.weight(1f))
                MetricTile("Protected", store.enabledPackages().size.toString(), Modifier.weight(1f))
            }
        }
        if (history.isNotEmpty()) {
            item {
                ChartCard("Last 7 days", "Events per day") { WeeklyBarChart(weekly) }
            }
            item {
                ChartCard("Breakdown", "What triggered a block") {
                    breakdown.forEach { (label, value) -> HorizontalBarRow(label, value, maxBreakdown) }
                }
            }
        }
        if (hits.isNotEmpty()) {
            item {
                ChartCard("Blocked words", "Attempts per day, last 7 days") { WeeklyBarChart(hitsWeekly) }
            }
            item {
                SectionCard {
                    Text("Keyword activity", style = StyleBodyStrong)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile("Total", hits.size.toString(), Modifier.weight(1f))
                        MetricTile("Last 24h", hitsLast24h.toString(), Modifier.weight(1f))
                        MetricTile("By AI", hitsViaAi.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    hitsByKeyword.forEach { (word, count) -> HorizontalBarRow(word, count, maxHits) }
                }
            }
        }
        item {
            Section("Recent activity", Modifier.padding(top = 8.dp)) {
                if (recent.isEmpty()) {
                    GroupCard {
                        EmptyState(
                            icon = Icons.Outlined.BarChart,
                            title = "Nothing yet",
                            body = "Events show up here when CancelIt blocks something."
                        )
                    }
                } else {
                    GroupCard {
                        recent.forEachIndexed { index, event ->
                            if (index > 0) RowDivider()
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(event.label, style = StyleBodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(2.dp))
                                    Text(event.detail, style = StyleCaption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(relativeTime(event.time), style = StyleHint)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Bar chart for the last 7 days. Today is the white bar; earlier days are muted. */
@Composable
private fun WeeklyBarChart(entries: List<Pair<String, Int>>) {
    val maxValue = (entries.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val barArea = 84.dp

    Row(
        Modifier.fillMaxWidth().height(barArea + 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        entries.forEachIndexed { index, (_, value) ->
            val isToday = index == entries.lastIndex
            val fraction = value.toFloat() / maxValue.toFloat()
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (value > 0) {
                    Text(value.toString(), style = StyleHint, color = if (isToday) TextPrimary else TextSecondary)
                    Spacer(Modifier.height(4.dp))
                }
                Box(
                    Modifier
                        .width(20.dp)
                        .height(maxOf(4f, barArea.value * fraction).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                value == 0 -> Border
                                isToday -> TextPrimary
                                else -> BarMuted
                            }
                        )
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, (label, _) ->
            val isToday = index == entries.lastIndex
            Text(
                label, style = StyleHint,
                color = if (isToday) TextPrimary else TextTertiary,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center
            )
        }
    }
}

/** Horizontal bar row: label + count, with a track/fill bar beneath. */
@Composable
private fun HorizontalBarRow(label: String, value: Int, maxValue: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = StyleCaption, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value.toString(), style = StyleNumber.copy(fontSize = 13.sp))
        }
        Spacer(Modifier.height(7.dp))
        ThinProgress(if (maxValue > 0) value.toFloat() / maxValue.toFloat() else 0f, height = 5.dp)
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard {
        Text(title, style = StyleBodyStrong)
        Text(subtitle, style = StyleHint)
        Spacer(Modifier.height(18.dp))
        content()
    }
}
