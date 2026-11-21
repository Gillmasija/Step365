package com.step365.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.step365.app.MainActivity
import com.step365.app.R
import com.step365.app.data.StepRepository
import com.step365.app.domain.Challenge
import com.step365.app.domain.calories
import com.step365.app.domain.distance
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Database-backed launcher widget. Sensor events invoke [refresh] after their transaction commits. */
class StepWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAsync(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateAsync(context)
    }

    private fun updateAsync(context: Context) {
        val pending = goAsync()
        val application = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refresh(application)
            } catch (error: Exception) {
                Log.w("Step365Widget", "Could not refresh widget", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StepWidget::class.java))
            if (ids.isEmpty()) return
            val snapshot = StepRepository.get(context).snapshot()
            val today = LocalDate.now()
            val steps = snapshot.records.firstOrNull { it.date == today.toString() }?.steps ?: 0L
            val personalGoal = snapshot.personalGoal.coerceAtLeast(1)
            val progress = (steps.toDouble() / personalGoal * 100).coerceIn(0.0, 100.0).toInt()
            val numbers = NumberFormat.getIntegerInstance()
            val stepText = numbers.format(steps)
            val goalText = context.getString(R.string.step_widget_goal, numbers.format(personalGoal))
            val kmText = String.format(Locale.getDefault(), "%.2f km", distance(steps, snapshot.stride))
            val kcalText = calories(steps, snapshot.stride, snapshot.weightKg)?.let {
                context.getString(R.string.step_widget_kcal, numbers.format(it.toLong()))
            } ?: context.getString(R.string.step_widget_add_weight)
            val challengeText = when {
                today < Challenge.start -> context.getString(R.string.step_widget_before_challenge)
                today > Challenge.end -> context.getString(R.string.step_widget_after_challenge)
                else -> context.getString(R.string.step_widget_challenge_day, Challenge.dayNumber(today))
            }
            val challengeGoal = if (personalGoal != 10_000) {
                context.getString(R.string.step_widget_fixed_goal)
            } else ""
            val openApp = PendingIntent.getActivity(
                context,
                365,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.step_widget).apply {
                setTextViewText(R.id.widget_steps, stepText)
                setTextViewText(R.id.widget_goal, goalText)
                setTextViewText(R.id.widget_percent, "$progress%")
                setProgressBar(R.id.widget_progress, 100, progress, false)
                setTextViewText(R.id.widget_distance, kmText)
                setTextViewText(R.id.widget_calories, kcalText)
                setTextViewText(R.id.widget_challenge_day, challengeText)
                setTextViewText(R.id.widget_challenge_goal, challengeGoal)
                setViewVisibility(
                    R.id.widget_challenge_goal,
                    if (challengeGoal.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE,
                )
                setContentDescription(R.id.widget_steps, "$stepText $goalText")
                setContentDescription(R.id.widget_progress, "$progress percent of personal daily goal")
                setContentDescription(R.id.widget_distance, "Estimated distance $kmText")
                setContentDescription(R.id.widget_calories, "Estimated activity calories $kcalText")
                setOnClickPendingIntent(R.id.widget_root, openApp)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
