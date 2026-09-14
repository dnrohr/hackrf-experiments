package dev.rfnotebook.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookDatabaseTest {
    private lateinit var database: NotebookDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NotebookDatabase::class.java,
        ).build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun schemaStoresUnlocatedAggregateAndCompleteHealthCounters() = kotlinx.coroutines.runBlocking {
        val dao = database.notebookDao()
        val radio = radio()
        val equipment = equipment(radio.id)
        val band = band(equipment.versionId)
        val survey = survey(band.versionId, equipment.versionId)
        dao.insertRadioDevice(radio)
        dao.insertEquipmentProfile(equipment)
        dao.insertBandProfile(band)
        dao.insertBandRanges(listOf(BandRangeEntity(band.versionId, "INCLUDE", 0, 902_000_000, 928_000_000)))
        dao.insertSurvey(survey)
        dao.insertAggregates(listOf(SpectrumAggregateEntity(
            surveyId = survey.id,
            timeBucketStartEpochMs = 1_000,
            frequencyBinHz = 915_000_000,
            minimumPowerDbfs = -90f,
            medianPowerDbfs = -85f,
            maximumPowerDbfs = -70f,
            noiseEstimateDbfs = -92f,
            sampleCount = 10,
            locationFixId = null,
            locationState = "MISSING",
        )))
        dao.insertHealthSnapshot(HealthSnapshotEntity(
            surveyId = survey.id,
            wallTimeEpochMs = 1_000,
            monotonicNs = 2_000,
            usbBytesPerSecond = 16_000_000,
            nativeQueueDepth = 1,
            processingQueueDepth = 2,
            persistenceQueueDepth = 3,
            droppedNativeUnits = 4,
            droppedProcessingUnits = 5,
            droppedPersistenceUnits = 6,
            malformedFrameCount = 7,
            overrunCount = 8,
            staleFixCount = 9,
            serviceGapCount = 10,
            availableStorageBytes = 1_000_000,
            estimatedRemainingBytes = 10_000,
            batteryPercent = 80,
            thermalStatus = 0,
            warning = null,
        ))

        assertEquals(8, dao.survey(survey.id)!!.overrunCount)
    }

    private fun radio() = RadioDeviceEntity("radio", "HackRF One", "suffix", "r9", "fw", "api", 1, 1)

    private fun equipment(radioId: String) = EquipmentProfileEntity(
        versionId = "equipment:v1", profileId = "equipment", name = "Default", version = 1,
        radioDeviceId = radioId, antennaName = "Uncalibrated", antennaBands = "[]", adapterNotes = "",
        sampleRateHz = 4_000_000, basebandFilterHz = 3_500_000, lnaGainDb = 16, vgaGainDb = 16,
        rfAmpEnabled = false, antennaPowerEnabled = false, isCalibrated = false, photoReference = null,
        notes = "Relative only", createdAtEpochMs = 1, retiredAtEpochMs = null,
    )

    private fun band(equipmentId: String) = BandProfileEntity(
        versionId = "band:v1", profileId = "band", name = "902–928 MHz", version = 1,
        equipmentProfileVersionId = equipmentId, binWidthHz = 100_000, targetRevisitMs = 1_000,
        thresholdSnrDb = 8f, minimumBandwidthHz = 100_000,
        explorationAidDisclaimer = "Receive only", createdAtEpochMs = 1, retiredAtEpochMs = null,
    )

    private fun survey(bandId: String, equipmentId: String) = SurveyEntity(
        id = UUID.randomUUID().toString(), name = "Test", bandProfileVersionId = bandId,
        equipmentProfileVersionId = equipmentId, status = "ACTIVE", revision = 1,
        startedAtEpochMs = 1, endedAtEpochMs = null, lastWallTimeEpochMs = 1,
        lastMonotonicNs = 1, distanceMeters = 0.0, locationCoverageRatio = 0.0,
        droppedFrameCount = 0, overrunCount = 8, malformedFrameCount = 0, staleFixCount = 0,
        unlocatedObservationCount = 1, appVersion = "test", detectorVersion = "none", notes = "",
        failureExplanation = null,
    )
}
