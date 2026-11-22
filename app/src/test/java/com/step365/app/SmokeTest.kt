package com.step365.app

import android.content.Context
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** JVM Android 15 smoke coverage; these tests do not replace a physical sensor/launcher test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmokeTest {
    @Test
    fun mainActivityLaunchesWithoutPermissionsOnAndroid15() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertFalse(activity.isFinishing)
            assertNotNull(activity.findViewById<android.view.View>(android.R.id.content))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun widgetRemoteViewsInflatesAndAppliesRecordedValuesOnAndroid15() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val remoteViews = RemoteViews(context.packageName, R.layout.step_widget).apply {
            setTextViewText(R.id.widget_steps, "7,428")
            setTextViewText(R.id.widget_distance, "5.20 km")
            setProgressBar(R.id.widget_progress, 100, 74, false)
        }
        val widget = remoteViews.apply(context, FrameLayout(context))
        assertNotNull(widget.findViewById<android.view.View>(R.id.widget_root))
        assertEquals("7,428", widget.findViewById<TextView>(R.id.widget_steps).text.toString())
        assertEquals("5.20 km", widget.findViewById<TextView>(R.id.widget_distance).text.toString())
        assertEquals(74, widget.findViewById<ProgressBar>(R.id.widget_progress).progress)
    }
}
