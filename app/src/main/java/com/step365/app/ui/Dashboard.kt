package com.step365.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.step365.app.domain.Challenge
import com.step365.app.domain.calories
import com.step365.app.domain.distance
import com.step365.app.domain.statistics
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(state: UiState, onStart: () -> Unit, onStop: () -> Unit, onPermissions: () -> Unit, onCalendar: () -> Unit) {
    val steps = state.records.firstOrNull { it.date == state.today.toString() }?.steps ?: 0L
    val progress = steps.toDouble() / state.personalGoal
    val stats = statistics(state.records, state.today)
    val estimatedCalories = calories(steps, state.stride, state.weightKg)
    val challengeLabel = when {
        state.today < Challenge.start -> "Your challenge begins 22 November 2026"
        state.today > Challenge.end -> "365 days, one lasting habit. Challenge ended."
        else -> "Day ${Challenge.dayNumber(state.today)} of 365 · Keep showing up"
    }
    ScreenColumn {
        ScreenTitle("Step365", "A little further.", state.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")))
        StepCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TODAY’S STEPS", color = MutedText, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.5.sp)
                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, tint = Lavender)
            }
            Text(stepNumber(steps), style = MaterialTheme.typography.displayLarge.copy(fontSize = 60.sp), fontWeight = FontWeight.Bold, color = Lavender)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("of ${stepNumber(state.personalGoal.toLong())} personal goal", color = MutedText, style = MaterialTheme.typography.bodyMedium)
                Text(percent(progress), color = Lavender, style = MaterialTheme.typography.labelLarge)
            }
            ProgressLine(progress)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatValue("km · estimated", decimal(distance(steps, state.stride)), Modifier.weight(1f))
                StatValue("kcal · estimated", estimatedCalories?.let { stepNumber(it.toLong()) } ?: "—", Modifier.weight(1f), detail = if (estimatedCalories == null) "Add weight in Settings" else null)
            }
        }
        StepCard {
            StatusDot(if (state.tracking) "Tracking active" else "Tracking paused", if (state.tracking) CompletedGreen else MutedText)
            Text(state.sensorStatus, style = MaterialTheme.typography.bodyMedium)
            if (!state.activityGranted) {
                Text("Allow physical activity to count your steps. No GPS or account required.", color = MutedText, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onPermissions, modifier = Modifier.fillMaxWidth()) { Text("Allow physical activity") }
            } else if (!state.tracking) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start tracking") }
            } else {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Pause tracking") }
            }
            Text("Carry your phone while walking. Missing sensor or service time cannot be recovered reliably.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        StepCard {
            Text("THE 365-DAY CHALLENGE", color = Lavender, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
            Text(challengeLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("10,000 steps, every day. 22 Nov 2026 – 21 Nov 2027.", color = MutedText, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatValue("day streak", stats.currentStreak.toString(), Modifier.weight(1f))
                StatValue("days completed", "${stats.completed} / 365", Modifier.weight(1f), color = CompletedGreen)
            }
            ProgressLine(stats.completed / 365.0, CompletedGreen)
            if (state.personalGoal != 10000) Text("Your personal goal is separate. Challenge days still require 10,000 recorded steps.", color = MutedText, style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick = onCalendar, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("View your calendar")
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                }
            }
        }
        Text("Every recorded step is progress.", color = MutedText, style = MaterialTheme.typography.bodySmall)
    }
}
