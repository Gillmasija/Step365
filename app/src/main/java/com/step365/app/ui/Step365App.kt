package com.step365.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.step365.app.domain.DailyRecord
import java.time.LocalDate

data class UiState(
    val records: List<DailyRecord> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val stride: Double = 0.70,
    val weightKg: Double? = null,
    val personalGoal: Int = 10000,
    val tracking: Boolean = false,
    val sensorStatus: String = "Checking step sensor…",
    val activityGranted: Boolean = false,
    val notificationsGranted: Boolean = false
)

@Composable
fun Step365App(
    state: UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPermissions: () -> Unit,
    onNotifications: () -> Unit,
    onSaveSettings: (Double, Double?, Int) -> Unit,
    onBatterySettings: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(0) }
    val destinations = listOf("Today", "Calendar", "Statistics", "Settings")
    val icons = listOf(Icons.AutoMirrored.Outlined.DirectionsWalk, Icons.Outlined.CalendarMonth, Icons.Outlined.BarChart, Icons.Outlined.Tune)
    Step365Theme {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = CardBackground) {
                    destinations.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(icons[index], contentDescription = null) },
                            label = { Text(title) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (selected) {
                    0 -> DashboardScreen(state, onStart, onStop, onPermissions, { selected = 1 })
                    1 -> CalendarScreen(state)
                    2 -> StatisticsScreen(state)
                    3 -> SettingsScreen(state, onStart, onStop, onPermissions, onNotifications, onSaveSettings, onBatterySettings)
                }
            }
        }
    }
}
