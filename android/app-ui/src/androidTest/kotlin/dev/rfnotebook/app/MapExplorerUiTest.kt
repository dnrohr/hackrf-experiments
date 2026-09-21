package dev.rfnotebook.app

import android.os.SystemClock
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.closeSoftKeyboard
import dev.rfnotebook.maps.MapDataset
import dev.rfnotebook.maps.MapFingerprintLayer
import dev.rfnotebook.maps.MapGapSegment
import dev.rfnotebook.maps.MapLocationKind
import dev.rfnotebook.maps.MapObservation
import dev.rfnotebook.maps.MapRoutePoint
import dev.rfnotebook.maps.MapSurveyContext
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapExplorerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun equivalentListExposesUncertaintyGapsAndComparabilityWithoutColor() {
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().statusBarsPadding()) { MapExplorerPage(dataset(), "fp-a", {}) }
            }
        }

        compose.onNodeWithText("Compare: 433.92 MHz • equipment:v2").performClick()
        compose.onNodeWithText("Comparable-equipment warning:", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Equivalent list").performScrollTo().performClick()
        compose.onNodeWithText("Equivalent geographic results").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 observations have no location", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 acquisition/location gaps remain visible", substring = true).performScrollTo().assertIsDisplayed()
        capture("m3-equivalent-list.png")
    }

    @Test fun largeTextDarkThemeKeepsNonMapEquivalentOperable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(Modifier.fillMaxSize().statusBarsPadding()) { MapExplorerPage(dataset(), "fp-a", {}) }
                }
            }
        }
        compose.onNodeWithText("Equivalent list").performScrollTo().performClick()
        compose.onNodeWithText("Equivalent geographic results").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Uncertainty and coverage").performScrollTo().assertIsDisplayed()
        capture("m3-large-text-dark-list.png")
    }

    @Test fun mapSupportsLabeledPanFilterAndSelectionWithinBudget() {
        val large = dataset(observationCount = 20_000)
        val started = SystemClock.elapsedRealtime()
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().statusBarsPadding()) { MapExplorerPage(large, "fp-a", {}) }
            }
        }
        compose.onNodeWithText("Show filters").performClick()
        compose.onNodeWithText("Min MHz").performTextInput("914")
        closeSoftKeyboard()
        compose.onNodeWithText("Hide filters").performScrollTo().performClick()
        compose.onNodeWithText("Map view").performScrollTo().performClick()
        compose.onNodeWithText("Cell zoom 14").performClick()
        compose.waitForIdle()
        val renderElapsed = SystemClock.elapsedRealtime() - started
        assertTrue("20,000-row render/filter/zoom took ${renderElapsed}ms", renderElapsed < 8_000)
        capture("m3-observed-relative-strength-map.png")
        val panStarted = SystemClock.elapsedRealtime()
        compose.onNodeWithContentDescription(
            "Observed relative strength map with route, GPS accuracy, and acquisition gaps",
        ).performScrollTo().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val panElapsed = SystemClock.elapsedRealtime() - panStarted
        Log.i(
            "M3Performance",
            "observations=20000 filter_zoom_render_ms=$renderElapsed pan_ms=$panElapsed " +
                "budgets_ms=8000/2000 device=Pixel_8a",
        )
        assertTrue("Map pan took ${panElapsed}ms", panElapsed < 2_000)
    }

    private fun dataset(observationCount: Int = 8): MapDataset {
        val observations = (0 until observationCount).map { index ->
            MapObservation(
                id = "o-$index", fingerprintId = "fp-a",
                surveyId = if (index % 2 == 0) "route-a" else "route-b",
                equipmentProfileVersionId = "equipment:v1",
                timestampEpochMs = 1_000L + index,
                centerFrequencyHz = 915_000_000,
                bandwidthHz = 100_000,
                detectionType = "DISCRETE_BURST",
                relativeStrengthDb = -75f + index % 30,
                detectionConfidence = 0.9f,
                latitude = if (index == observationCount - 1 && observationCount < 100) null else 40.7128 + (index % 100) * 0.000002,
                longitude = if (index == observationCount - 1 && observationCount < 100) null else -74.0060 + (index % 100) * 0.000002,
                horizontalAccuracyM = if (index == observationCount - 1 && observationCount < 100) null else 6f + index % 8,
                locationKind = if (index == observationCount - 1 && observationCount < 100) MapLocationKind.MISSING else if (index == 2) MapLocationKind.INTERPOLATED else MapLocationKind.DIRECT,
            )
        }
        val comparison = MapFingerprintLayer(
            "fp-b", "433.92 MHz", "equipment:v2",
            observations.take(4).map { it.copy(
                id = "b-${it.id}", fingerprintId = "fp-b", equipmentProfileVersionId = "equipment:v2",
                centerFrequencyHz = 433_920_000, relativeStrengthDb = it.relativeStrengthDb - 4,
            ) },
        )
        val routes = (0 until 12).map { index ->
            MapRoutePoint(
                if (index < 6) "route-a" else "route-b", 1_000L + index,
                40.7128 + index * 0.00002, -74.0060 + index * 0.00002,
                if (index == 4) 35f else 7f, index == 4,
            )
        }
        return MapDataset(
            layers = listOf(MapFingerprintLayer("fp-a", "915.0 MHz", "equipment:v1", observations), comparison),
            routes = routes,
            gaps = listOf(MapGapSegment("route-a", "USB_STALL", "Explicit synthetic gap", 3, 1_004, 1_006, routes[3], routes[6])),
            surveys = listOf(
                MapSurveyContext("route-a", "Repeat route A", "equipment:v1"),
                MapSurveyContext("route-b", "Repeat route B", "equipment:v1"),
            ),
        )
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val file = File(directory, name)
        compose.waitForIdle()
        SystemClock.sleep(2_000)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        FileOutputStream(file).use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/RFFieldNotebook")
        }
        val uri = requireNotNull(instrumentation.targetContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        instrumentation.targetContext.contentResolver.openOutputStream(uri)!!.use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
