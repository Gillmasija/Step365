package com.step365.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.step365.app.tracking.TrackingRuntime
import com.step365.app.ui.Step365App
import com.step365.app.ui.StepViewModel

class MainActivity : ComponentActivity() {
    private lateinit var model: StepViewModel
    private var startAfterPermission = false
    private val activityPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.refresh()
        if (granted && startAfterPermission) model.start()
        startAfterPermission = false
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[StepViewModel::class.java]
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            Step365App(state, onStart = { requestActivity(true) }, onStop = model::stop,
                onPermissions = { requestActivity(false) }, onNotifications = {
                    if (Build.VERSION.SDK_INT >= 33 && !state.notificationsGranted &&
                        !getPreferences(MODE_PRIVATE).getBoolean("asked_notifications", false)) {
                        getPreferences(MODE_PRIVATE).edit().putBoolean("asked_notifications", true).apply()
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }, onSaveSettings = model::save, onBatterySettings = {
                    openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                })
        }
    }
    private fun requestActivity(start: Boolean) {
        if (TrackingRuntime.permitted(this)) { if (start) model.start(); return }
        startAfterPermission = start
        if (Build.VERSION.SDK_INT >= 29) {
            val preferences = getPreferences(MODE_PRIVATE)
            if (preferences.getBoolean("asked_activity", false) && !shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION)) {
                openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } else {
                preferences.edit().putBoolean("asked_activity", true).apply()
                activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
    }
    private fun openSettings(intent: Intent) {
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) { model.refresh(); model.resumeIfEnabled() }
    }
}
