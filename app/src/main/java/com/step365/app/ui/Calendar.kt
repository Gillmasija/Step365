package com.step365.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.step365.app.domain.Challenge
import com.step365.app.domain.DayStatus
import com.step365.app.domain.calories
import com.step365.app.domain.distance
import com.step365.app.domain.status
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(state: UiState) {
    var displayedMonth by rememberSaveable { mutableStateOf(YearMonth.from(state.today.coerceIn(Challenge.start, Challenge.end)).toString()) }
    var selected by rememberSaveable { mutableStateOf(state.today.toString()) }
    val month = YearMonth.parse(displayedMonth)
    val selectedDate = LocalDate.parse(selected)
    val records = state.records.associateBy { it.date }
    val record = records[selected]
    val selectedStatus = status(selectedDate, state.today, record)
    val steps = record?.steps ?: 0L
    val hasRecordedValue = record != null || selectedDate == state.today
    val monthlyCompleted = state.records.count { it.steps >= Challenge.goal && Challenge.contains(LocalDate.parse(it.date)) && YearMonth.from(LocalDate.parse(it.date)) == month && LocalDate.parse(it.date) <= state.today }
    ScreenColumn {
        ScreenTitle("Your journey", "One day at a time.", "22 November 2026 – 21 November 2027")
        StepCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { displayedMonth = month.minusMonths(1).toString() }, enabled = month > YearMonth.from(Challenge.start)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { displayedMonth = month.plusMonths(1).toString() }, enabled = month < YearMonth.from(Challenge.end)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), color = MutedText, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val weekCount = (offset + month.lengthOfMonth() + 6) / 7
            repeat(weekCount) { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(7) { weekday ->
                        val day = week * 7 + weekday - offset + 1
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            CalendarDay(date, status(date, state.today, records[date.toString()]), date == selectedDate,
                                (records[date.toString()]?.steps ?: 0L) / 10000.0,
                                Modifier.weight(1f)) { selected = date.toString() }
                        } else Box(Modifier.weight(1f).height(46.dp))
                    }
                }
            }
            Text("$monthlyCompleted completed ${if (monthlyCompleted == 1) "day" else "days"} this month", color = CompletedGreen, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusDot("Done", CompletedGreen)
                StatusDot("Missed", MissedRed)
                StatusDot("Today", Lavender)
                StatusDot("No data", MutedText)
            }
        }
        StepCard {
            Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (selectedStatus == DayStatus.TODAY && steps >= Challenge.goal) "Today · goal completed" else statusLabel(selectedStatus), color = statusColor(selectedStatus), style = MaterialTheme.typography.bodyMedium)
            Text(if (record == null && selectedStatus != DayStatus.TODAY) "No recorded total" else "${stepNumber(steps)} steps", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            ProgressLine(steps / 10000.0, statusColor(selectedStatus))
            DetailRow("Challenge goal", "10,000 steps")
            DetailRow("Goal achieved", if (hasRecordedValue) "${String.format(java.util.Locale.getDefault(), "%.1f", steps / 100.0)}%" else "—")
            DetailRow("Estimated distance", if (hasRecordedValue) "${decimal(distance(steps, state.stride))} km" else "—")
            DetailRow("Estimated activity calories", if (!hasRecordedValue) "—" else calories(steps, state.stride, state.weightKg)?.let { "${stepNumber(it.toLong())} kcal" } ?: "Set weight in Settings")
            if (selectedStatus == DayStatus.MISSING || (record != null && !record.complete && steps < Challenge.goal)) {
                Text("Recording was missing or incomplete. A partial day below the goal is never marked as missed.", color = MutedText, style = MaterialTheme.typography.bodySmall)
            }
            if (selectedStatus == DayStatus.OUTSIDE) Text("This date falls outside the fixed challenge. Recorded steps are kept, but do not change challenge statistics.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        Text("Green means at least 10,000 recorded steps. Red requires a finished day with complete tracking. Gray includes future days and missing data.", color = MutedText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CalendarDay(date: LocalDate, status: DayStatus, selected: Boolean, progress: Double, modifier: Modifier, onClick: () -> Unit) {
    val color = statusColor(status)
    val background = when (status) {
        DayStatus.TODAY -> Color(0xFF48345E)
        DayStatus.COMPLETE -> Color(0xFF20382D)
        DayStatus.MISSED -> Color(0xFF412930)
        else -> Color(0xFF27232F)
    }
    Column(
        modifier.height(46.dp).background(background, RoundedCornerShape(11.dp))
            .border(if (selected) 2.dp else 0.dp, if (selected) Lavender else Color.Transparent, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$date, ${statusLabel(status)}${if (selected) ", selected" else ""}" }
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(date.dayOfMonth.toString(), color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = if (status == DayStatus.TODAY) FontWeight.Bold else FontWeight.Normal)
        if (status == DayStatus.TODAY) ProgressLine(progress)
    }
}

fun statusLabel(status: DayStatus): String = when (status) {
    DayStatus.COMPLETE -> "Goal completed"
    DayStatus.MISSED -> "Finished below goal"
    DayStatus.TODAY -> "Today · in progress"
    DayStatus.MISSING -> "Insufficient tracking data"
    DayStatus.FUTURE -> "A day still to come"
    DayStatus.OUTSIDE -> "Outside the challenge"
}

fun statusColor(status: DayStatus): Color = when (status) {
    DayStatus.COMPLETE -> CompletedGreen
    DayStatus.MISSED -> MissedRed
    DayStatus.TODAY -> Lavender
    else -> MutedText
}
