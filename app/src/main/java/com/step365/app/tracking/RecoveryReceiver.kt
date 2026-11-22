package com.step365.app.tracking

import android.content.*
import com.step365.app.data.StepRepository
import com.step365.app.widget.StepWidget
import kotlinx.coroutines.launch

class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val result = goAsync()
        TrackingRuntime.scope.launch {
            try {
                val repository = StepRepository.get(context)
                val saved = repository.snapshot()
                repository.stop("Phone restarted. Open Step365 if tracking does not resume.", System.currentTimeMillis())
                if (saved.enabled && TrackingRuntime.permitted(context) && TrackingRuntime.sensor(context) != null) {
                    try { TrackingRuntime.start(context) }
                    catch (_: RuntimeException) {
                        repository.stop("Tap Start tracking to resume after restart.", System.currentTimeMillis())
                    }
                }
                StepWidget.refresh(context)
            } finally { result.finish() }
        }
    }
}
