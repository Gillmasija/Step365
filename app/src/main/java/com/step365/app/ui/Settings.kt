package com.step365.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPermissions: () -> Unit,
    onNotifications: () -> Unit,
    onSaveSettings: (Double, Double?, Int) -> Unit,
    onBatterySettings: () -> Unit
) {
    var strideText by rememberSaveable(state.stride) { mutableStateOf(state.stride.toString()) }
    var weightText by rememberSaveable(state.weightKg) { mutableStateOf(state.weightKg?.toString() ?: "") }
    var goalText by rememberSaveable(state.personalGoal) { mutableStateOf(state.personalGoal.toString()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val stride = strideText.replace(',', '.').toDoubleOrNull()
    val weight = weightText.replace(',', '.').toDoubleOrNull()
    val goal = goalText.toIntOrNull()
    val strideValid = stride != null && stride in 0.2..2.0
    val weightValid = weightText.isBlank() || (weight != null && weight in 20.0..350.0)
    val goalValid = goal != null && goal in 1000..100000
    ScreenColumn {
        ScreenTitle("Make it yours", "A comfortable rhythm.", "Simple preferences. Everything stays on your phone.")
        StepCard {
            Text("Your estimates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = strideText, onValueChange = { strideText = it; saved = false }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Stride length · meters per step") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = attempted && !strideValid,
                supportingText = { Text(if (attempted && !strideValid) "Enter 0.20 to 2.00 meters." else "Default: 0.70 m. Distance is steps × stride ÷ 1,000.") }
            )
            OutlinedTextField(
                value = weightText, onValueChange = { weightText = it; saved = false }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Body weight · kg (optional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = attempted && !weightValid,
                supportingText = { Text(if (attempted && !weightValid) "Enter 20 to 350 kg, or leave blank." else "Used only to estimate walking activity calories.") }
            )
            OutlinedTextField(
                value = goalText, onValueChange = { goalText = it; saved = false }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Personal daily step goal") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = attempted && !goalValid,
                supportingText = { Text(if (attempted && !goalValid) "Enter 1,000 to 100,000 steps." else "The original challenge stays at 10,000 steps per day.") }
            )
            Button(onClick = {
                attempted = true
                if (strideValid && weightValid && goalValid) {
                    onSaveSettings(stride!!, if (weightText.isBlank()) null else weight, goal!!)
                    saved = true
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Preferences saved" else "Save preferences") }
            Text("Calories ≈ 0.5 × body weight (kg) × distance (km), a net level-walking activity estimate. Estimates exclude resting metabolism and vary with pace, terrain and individual movement. They are not medical measurements. Changing preferences recalculates displayed estimates for all recorded days.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        StepCard {
            Text("Tracking & permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            StatusDot(if (state.tracking) "Tracking active" else "Tracking paused", if (state.tracking) CompletedGreen else MutedText)
            Text(state.sensorStatus, style = MaterialTheme.typography.bodyMedium)
            DetailRow("Physical activity", if (state.activityGranted) "Allowed" else "Not allowed")
            if (!state.activityGranted) Button(onClick = onPermissions, modifier = Modifier.fillMaxWidth()) { Text("Review physical activity permission") }
            else OutlinedButton(onClick = if (state.tracking) onStop else onStart, modifier = Modifier.fillMaxWidth()) { Text(if (state.tracking) "Pause tracking" else "Start tracking") }
            HorizontalDivider(color = MutedText.copy(alpha = 0.2f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Tracking notifications", style = MaterialTheme.typography.bodyLarge)
                    Text(if (state.notificationsGranted) "Enabled in Android settings" else "Disabled in Android settings", color = MutedText, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.notificationsGranted, onCheckedChange = { onNotifications() })
            }
            Text("Android requires a foreground-service notification while tracking. Notification visibility is controlled by your system settings.", color = MutedText, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onBatterySettings, modifier = Modifier.fillMaxWidth()) { Text("Review battery restrictions") }
            Text("If background tracking stops, review Step365 in your phone’s battery settings. Some manufacturers restrict services even with permissions enabled. After a force stop, reopen Step365 and start tracking again.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        StepCard {
            Text("Your data, your device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("No account, ads, subscriptions or GPS. Steps and preferences are stored locally. Clearing app data or uninstalling Step365 erases this history.", color = MutedText, style = MaterialTheme.typography.bodyMedium)
            Text("Tracking begins from the sensor reading when you start; the phone’s lifetime counter is never added to today. We cannot reconstruct steps during sensor or service gaps. Uncertain days stay gray instead of being reported as missed.", color = MutedText, style = MaterialTheme.typography.bodySmall)
            Text("To add your widget: long-press an empty area of your home screen, choose Widgets, then Step365. Updates follow recorded steps and Android’s background limits.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        Text("Step365 · Version 1.0.0", color = MutedText, style = MaterialTheme.typography.bodySmall)
    }
}
