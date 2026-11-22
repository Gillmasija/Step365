package com.step365.app.tracking

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.*
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.step365.app.MainActivity
import com.step365.app.R
import com.step365.app.data.StepRepository
import com.step365.app.domain.CounterSample
import com.step365.app.widget.StepWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.time.*
import java.util.UUID

object TrackingRuntime {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun permitted(context: Context) = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    fun sensor(context: Context): Sensor? {
        val manager = context.getSystemService(SensorManager::class.java)
        return manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    }
    fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
}

class TrackingService : Service(), SensorEventListener {
    private val session = UUID.randomUUID().toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val samples = Channel<Reading>(Channel.UNLIMITED)
    private lateinit var repository: StepRepository
    private lateinit var manager: SensorManager
    private var started = false
    private var lastPublish = 0L
    private var dateReceiverRegistered = false
    private var stopReason = "Tracking stopped. Open Step365 to resume."
    private var activeDate = LocalDate.now()
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch {
                if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    repository.begin(LocalDate.now(), ZoneId.systemDefault().id, boot(), System.currentTimeMillis(), session)
                } else repository.heartbeat(LocalDate.now(), ZoneId.systemDefault().id, System.currentTimeMillis())
                publish(true)
            }
        }
    }
    private data class Reading(val raw: Long, val timestamp: Long, val type: Int, val date: LocalDate, val zone: String)

    override fun onCreate() {
        super.onCreate()
        repository = StepRepository.get(this)
        manager = getSystemService(SensorManager::class.java)
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Step tracking", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Required while Step365 collects steps in the background"
            setShowBadge(false)
        })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(this, 365, notification("Connecting to your step sensor"),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0)
        } catch (_: RuntimeException) {
            fail("Android blocked background tracking. Open Step365 to resume.")
            return START_NOT_STICKY
        }
        if (!TrackingRuntime.permitted(this)) {
            fail("Physical activity permission is required")
            return START_NOT_STICKY
        }
        val sensor = TrackingRuntime.sensor(this)
        if (sensor == null) {
            fail("No compatible step sensor on this phone")
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            scope.launch {
                repository.begin(LocalDate.now(), ZoneId.systemDefault().id, boot(), System.currentTimeMillis(), session)
                val registered = withContext(Dispatchers.Main) {
                    try { manager.registerListener(this@TrackingService, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0) }
                    catch (_: RuntimeException) { false }
                }
                if (!registered) { fail("The step sensor is unavailable. Try starting again."); return@launch }
                launch {
                    while (isActive) {
                        val day = LocalDate.now()
                        repository.heartbeat(day, ZoneId.systemDefault().id, System.currentTimeMillis())
                        if (day != activeDate) {
                            activeDate = day
                            // A fresh counter snapshot establishes a clean midnight baseline when stationary.
                            withContext(Dispatchers.Main) {
                                manager.unregisterListener(this@TrackingService)
                                try {
                                    if (!manager.registerListener(this@TrackingService, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0))
                                        fail("The step sensor disconnected. Open Step365 to resume.")
                                } catch (_: RuntimeException) { fail("Physical activity permission or the sensor became unavailable.") }
                            }
                        }
                        publish(true)
                        delay(30_000)
                    }
                }
                for (r in samples) {
                    if (!TrackingRuntime.permitted(this@TrackingService)) {
                        fail("Physical activity permission was revoked"); break
                    }
                    if (r.type == Sensor.TYPE_STEP_COUNTER) {
                        repository.counter(CounterSample(r.raw, r.timestamp, r.date.toString(), r.zone, boot()), System.currentTimeMillis())
                    } else repository.detector(r.date, r.zone, boot(), r.timestamp, System.currentTimeMillis())
                    publish(false)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(this, dateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            dateReceiverRegistered = true
        }
        return START_STICKY
    }

    private fun boot() = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)
    override fun onSensorChanged(event: SensorEvent) {
        // Sensor timestamps use elapsed realtime; wall-clock conversion preserves event dates.
        val eventMillis = System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000
        val zone = ZoneId.systemDefault()
        if (!event.values[0].isFinite() || event.values[0] < 0) return
        samples.trySend(Reading(event.values[0].toLong(), event.timestamp, event.sensor.type,
            Instant.ofEpochMilli(eventMillis).atZone(zone).toLocalDate(), zone.id))
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    private suspend fun publish(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPublish < 15_000) return
        lastPublish = now
        StepWidget.refresh(this)
        val today = repository.snapshot().records.firstOrNull { it.date == LocalDate.now().toString() }?.steps ?: 0
        getSystemService(NotificationManager::class.java).notify(365, notification("%,d steps today · tap to view".format(today)))
    }
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_steps)
            .setContentTitle("Step365 · tracking your day").setContentText(text).setContentIntent(open)
            .setOngoing(true).setSilent(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }
    private fun fail(message: String) {
        stopReason = message
        TrackingRuntime.scope.launch { repository.stop(message, System.currentTimeMillis(), if (started) session else null); StepWidget.refresh(this@TrackingService) }
        stopSelf()
    }
    override fun onDestroy() {
        manager.unregisterListener(this)
        if (dateReceiverRegistered) unregisterReceiver(dateReceiver)
        scope.cancel()
        samples.close()
        TrackingRuntime.scope.launch {
            repository.stop(stopReason, System.currentTimeMillis(), session)
            StepWidget.refresh(this@TrackingService)
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { private const val CHANNEL = "step_tracking" }
}
