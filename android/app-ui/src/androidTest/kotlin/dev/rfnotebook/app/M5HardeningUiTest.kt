package dev.rfnotebook.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test

class M5HardeningUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun firstRunExplainsSafetyPrivacyAndRelativeMeasurements() {
        compose.onNodeWithText("Before the first survey").assertIsDisplayed()
        compose.onNodeWithText("Receive only: this app has no transmit control.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("app-private storage", substring = true).assertIsDisplayed()
        compose.onNodeWithText("relative dBFS", substring = true).assertIsDisplayed()
    }

    @Test fun gainControlsHaveUnambiguousScreenReaderLabels() {
        compose.onNodeWithText("LNA gain: 16 dB").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Decrease LNA gain").assertExists()
        compose.onNodeWithContentDescription("Increase LNA gain").assertExists()
        compose.onNodeWithContentDescription("Decrease VGA gain").assertExists()
        compose.onNodeWithContentDescription("Increase VGA gain").assertExists()
    }

    @Test fun setupValuesSurviveActivityRecreation() {
        compose.onNodeWithText("Antenna name").performScrollTo().performTextReplacement("Field loop")

        compose.activityRule.scenario.recreate()

        compose.onNodeWithText("Field loop").performScrollTo().assertIsDisplayed()
    }
}
