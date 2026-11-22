package com.step365.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.step365.app.domain.Challenge
import com.step365.app.domain.DailyRecord
import com.step365.app.ui.Step365App
import com.step365.app.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Interaction coverage against real Compose screens; fixture steps are used only in these tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
@LooperMode(LooperMode.Mode.PAUSED)
class NavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun showApp(onSave: (Double, Double?, Int) -> Unit = { _, _, _ -> }) {
        compose.setContent {
            Step365App(
                state = UiState(
                    today = Challenge.start,
                    records = listOf(DailyRecord(date = Challenge.start.toString(), steps = 10_000)),
                    activityGranted = true,
                    sensorStatus = "Step counter available"
                ),
                onStart = {},
                onStop = {},
                onPermissions = {},
                onNotifications = {},
                onSaveSettings = onSave,
                onBatterySettings = {}
            )
        }
    }

    @Test
    fun calendarNavigatesMonthsAndOpensSelectedDayDetails() {
        showApp()
        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText("December 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithText("23").performScrollTo().performClick()
        compose.onNodeWithText("Monday, 23 November 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A day still to come").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No recorded total").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun statisticsDisplaysPersistedChallengeTotals() {
        showApp()
        compose.onNodeWithText("Statistics").performClick()
        compose.onNodeWithText("TOTAL CHALLENGE STEPS").assertIsDisplayed()
        compose.onAllNodesWithText("10,000").onFirst().assertIsDisplayed()
        compose.onNodeWithText("7.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("OVERALL CHALLENGE PROGRESS").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsSavesEstimationInputsAndSeparatePersonalGoal() {
        var saved: Triple<Double, Double?, Int>? = null
        showApp { stride, weight, goal -> saved = Triple(stride, weight, goal) }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Stride length · meters per step").performScrollTo().performTextReplacement("0.80")
        compose.onNodeWithText("Body weight · kg (optional)").performScrollTo().performTextReplacement("70")
        compose.onNodeWithText("Personal daily step goal").performScrollTo().performTextReplacement("8000")
        compose.onNodeWithText("Save preferences").performScrollTo().performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals(0.80, saved!!.first, 0.00001)
            assertEquals(70.0, saved!!.second!!, 0.00001)
            assertEquals(8000, saved!!.third)
            assertEquals(10_000L, Challenge.goal)
        }
        compose.onNodeWithText("Preferences saved").assertIsDisplayed()
    }

    @Test
    fun settingsRejectsOutOfRangeInputsBeforeCallingSave() {
        var saved: Triple<Double, Double?, Int>? = null
        showApp { stride, weight, goal -> saved = Triple(stride, weight, goal) }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Stride length · meters per step").performScrollTo().performTextReplacement("0.1")
        compose.onNodeWithText("Body weight · kg (optional)").performScrollTo().performTextReplacement("5")
        compose.onNodeWithText("Personal daily step goal").performScrollTo().performTextReplacement("12")
        compose.onNodeWithText("Save preferences").performScrollTo().performClick()
        compose.onNodeWithText("Enter 0.20 to 2.00 meters.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enter 20 to 350 kg, or leave blank.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enter 1,000 to 100,000 steps.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertNull(saved) }
    }
}
