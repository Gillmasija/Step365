package com.step365.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.step365.app.domain.distance
import com.step365.app.domain.statistics

@Composable
fun StatisticsScreen(state: UiState) {
    val stats = statistics(state.records, state.today)
    ScreenColumn {
        ScreenTitle("The bigger picture", "Small steps. Real change.", "Your fixed 365-day challenge, in numbers.")
        StepCard {
            Text("TOTAL CHALLENGE STEPS", color = Lavender, style = MaterialTheme.typography.labelLarge)
            Text(stepNumber(stats.totalSteps), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("of 3,650,000 target steps", color = MutedText, style = MaterialTheme.typography.bodyMedium)
            ProgressLine(stats.totalSteps / 3650000.0)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatValue("km · estimated total", decimal(distance(stats.totalSteps, state.stride)), Modifier.weight(1f))
                StatValue("average daily steps", stepNumber(stats.averageSteps.toLong()), Modifier.weight(1f))
            }
            Text("The daily average includes elapsed challenge dates through today, with zero for missing dates.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        StepCard {
            Text("CONSISTENCY", color = Lavender, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatValue("current streak", "${stats.currentStreak} days", Modifier.weight(1f))
                StatValue("longest streak", "${stats.longestStreak} days", Modifier.weight(1f))
            }
            Text("A streak counts consecutive 10,000-step days. Today stays open until midnight; a missing finished day breaks the streak.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        StepCard {
            Text("365 DAYS OF POSSIBILITY", color = Lavender, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatValue("completed days", stats.completed.toString(), Modifier.weight(1f), color = CompletedGreen)
                StatValue("missed days", stats.missed.toString(), Modifier.weight(1f), color = MissedRed)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatValue("missing-data days", stats.missing.toString(), Modifier.weight(1f))
                StatValue("remaining days", stats.remaining.toString(), Modifier.weight(1f))
            }
            DetailRow("Completion rate", percent(stats.completionRate))
            Text("Completed finished days ÷ all finished challenge days. Missing-data days remain in the denominator. Completed days above also include today when its goal is reached.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        StepCard {
            Text("OVERALL CHALLENGE PROGRESS", color = Lavender, style = MaterialTheme.typography.labelLarge)
            Text(percent(stats.overallProgress), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            ProgressLine(stats.overallProgress)
            Text("Finished challenge days out of 365. Remaining days include today while it is still in progress. The challenge never restarts automatically.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
