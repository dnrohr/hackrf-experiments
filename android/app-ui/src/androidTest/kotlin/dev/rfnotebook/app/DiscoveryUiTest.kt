package dev.rfnotebook.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import dev.rfnotebook.domain.FingerprintState
import dev.rfnotebook.storage.DetectionEntity
import dev.rfnotebook.storage.DiscoveryDetail
import dev.rfnotebook.storage.FingerprintHintEntity
import dev.rfnotebook.storage.SignalFingerprintEntity
import dev.rfnotebook.storage.SurveyEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class DiscoveryUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun emptyDiscoveryStateIsUsableWithoutRadio() {
        compose.setContent {
            MaterialTheme { DiscoveriesPage(DiscoveryUiState(DiscoveryPhase.EMPTY), {}, {}, {}) }
        }
        compose.onNodeWithText("No discoveries yet. Complete a survey, then process its stored aggregates.").assertIsDisplayed()
        compose.onNodeWithText("Refresh").assertIsDisplayed()
        compose.onNodeWithText("Back").assertIsDisplayed()
    }

    @Test fun partialLargeDatasetShowsEvidenceAndWorkingFilters() {
        val details = (0 until 500).map(::detail)
        compose.setContent {
            MaterialTheme {
                DiscoveriesPage(
                    DiscoveryUiState(
                        DiscoveryPhase.PARTIAL,
                        details.map { it.fingerprint },
                        details,
                        "2 acquisition gaps are retained.",
                    ), {}, {}, {},
                )
            }
        }

        compose.onNodeWithText("Partial results are shown. 2 acquisition gaps are retained.").assertIsDisplayed()
        compose.onNodeWithText("Min MHz").performTextInput("915")
        compose.onNodeWithText("915.0000 MHz discovery").assertIsDisplayed()
        capture("m2-discoveries-partial.png")
    }

    @Test fun detailAllowsReversibleStateAndMetadataEdits() {
        var savedLabel = ""
        var savedState: FingerprintState? = null
        compose.setContent {
            MaterialTheme {
                DiscoveryDetailPage(
                    detail(1),
                    onState = { savedState = it },
                    onSave = { label, _, _ -> savedLabel = label },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("User label").performTextInput("Door sensor")
        compose.onNodeWithText("Save label, tags, and notes").performClick()
        compose.onNodeWithText("Interesting").performClick()
        assertEquals("Door sensor", savedLabel)
        assertEquals(FingerprintState.INTERESTING, savedState)
        capture("m2-discovery-detail.png")
    }

    @Test fun completedSurveySummaryCanBeReopenedForExportAfterCapture() {
        var openedSurvey = ""
        val survey = SurveyEntity(
            "survey-export", "Route survey", "band:v1", "equipment:v1", "COMPLETE", 1,
            100, 200, 200, 200, 0.0, 1.0, 0, 0, 0, 0, 0,
            "test", "detector-v1", "", null,
        )
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DiscoveriesPage(
                        state = DiscoveryUiState(DiscoveryPhase.EMPTY, reprocessableSurveys = listOf(survey)),
                        onRefresh = {},
                        onSelect = {},
                        onBack = {},
                        onOpenSurvey = { openedSurvey = it },
                    )
                }
            }
        }

        compose.onNodeWithText("Open summary for Route survey").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(survey.id, openedSurvey)
    }

    @Test fun reviewedExportOptionIsOneLabeledCheckboxTarget() {
        var checked = false
        compose.setContent {
            MaterialTheme { ReviewedExportOption("Include linked IQ", checked) { checked = it } }
        }

        compose.onNodeWithContentDescription("Include linked IQ").assertIsOff().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Include linked IQ").assertIsOn()
    }

    private fun detail(index: Int): DiscoveryDetail {
        val frequency = if (index == 1) 915_000_000L else 433_920_000L + index
        val fingerprint = SignalFingerprintEntity(
            "fp-$index", "equipment:v1", frequency, 100_000,
            100, 1_000, 2, 0.1f, 500, 900, 0.5f, "cluster-v1",
            "NEW", "", "", "", 40.0, 40.01, -74.0, -73.99, 2,
        )
        return DiscoveryDetail(
            fingerprint,
            listOf(FingerprintHintEntity(fingerprint.id, 0, "REPEATING_SHORT_OOK_LIKE_BURST", 0.7f, "Two recurring energy events.")),
            listOf(DetectionEntity(
                "d-$index", "survey", fingerprint.id, "equipment:v1", 100, 600,
                frequency, 100_000, -50f, -55f, 35f, "fix", "detector-v1", "DISCRETE_BURST", "",
            )),
            emptyList(),
        )
    }

    private fun capture(name: String) {
        val directory = requireNotNull(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        )
        FileOutputStream(File(directory, name)).use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(
                android.graphics.Bitmap.CompressFormat.PNG,
                100,
                output,
            )
        }
    }
}
