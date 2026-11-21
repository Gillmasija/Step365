package com.step365.app.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.step365.app.data.StepRepository
import com.step365.app.tracking.TrackingRuntime
import com.step365.app.tracking.TrackingService
import com.step365.app.widget.StepWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

class StepViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val repository = StepRepository.get(application)
    private val refresh = MutableStateFlow(0L)
    private val clock = flow { while (true) { emit(System.currentTimeMillis()); delay(15_000) } }
    val state = combine(repository.updates, clock, refresh) { value, now, _ ->
        val sensor = TrackingRuntime.sensor(context)
        val running = value.tracker.active && now - value.tracker.heartbeat in 0..90_000
        UiState(value.records, LocalDate.now(), value.stride, value.weightKg, value.personalGoal, running,
            when {
                sensor == null -> "No compatible step sensor on this phone. Steps cannot be recorded."
                !TrackingRuntime.permitted(context) -> "Physical activity permission is needed to count steps."
                value.tracker.active && !running -> "Tracking has not checked in recently. Open Start tracking to reconnect."
                else -> value.tracker.status
            }, TrackingRuntime.permitted(context), NotificationManagerCompat.from(context).areNotificationsEnabled())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun refresh() { refresh.value = System.nanoTime() }
    fun resumeIfEnabled() { viewModelScope.launch {
        if (repository.snapshot().enabled && TrackingRuntime.permitted(context)) start()
        refresh()
    } }
    fun start() {
        if (!TrackingRuntime.permitted(context)) return
        viewModelScope.launch {
            repository.setEnabled(true)
            try { TrackingRuntime.start(context) }
            catch (_: RuntimeException) { repository.stop("Android blocked tracking. Tap Start while the app is open.", System.currentTimeMillis()) }
            refresh()
        }
    }
    fun stop() { viewModelScope.launch {
        repository.setEnabled(false)
        context.stopService(Intent(context, TrackingService::class.java))
        repository.stop("Tracking paused. Steps during this gap will not be recorded.", System.currentTimeMillis())
        StepWidget.refresh(context)
    } }
    fun save(stride: Double, weight: Double?, goal: Int) { viewModelScope.launch {
        repository.saveSettings(stride, weight, goal)
        StepWidget.refresh(context)
    } }
}
