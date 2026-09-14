package dev.rfnotebook.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.testing.MigrationTestHelper
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookDatabaseTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NotebookDatabase::class.java,
    )
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

    @Test
    fun referencedEquipmentChangeCreatesNewPersistentVersion() = kotlinx.coroutines.runBlocking {
        val repository = NotebookSetupRepository(database.notebookDao())

        val first = repository.createStarterSurvey("suffix", "HackRF One", 1, 1, "first", lnaGainDb = 16)
        val second = repository.createStarterSurvey("suffix", "HackRF One", 2, 2, "second", lnaGainDb = 24)
        val versions = database.notebookDao().equipmentProfileVersions("equipment:suffix")

        assertEquals(listOf(1, 2), versions.map { it.version })
        assertEquals(2L, versions.first().retiredAtEpochMs)
        assertEquals(24, versions.last().lnaGainDb)
        assertEquals("equipment:suffix:v1", database.notebookDao().survey(first.surveyId)!!.equipmentProfileVersionId)
        assertEquals("equipment:suffix:v2", database.notebookDao().survey(second.surveyId)!!.equipmentProfileVersionId)
    }

    @Test
    fun migrationFromVersionOnePreservesRadioAndAddsDisconnectedState() {
        val name = "migration-${UUID.randomUUID()}"
        migrationHelper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO radio_devices (id, model, serialSuffix, hardwareRevision, firmwareVersion, usbApiVersion, firstSeenAtEpochMs, lastSeenAtEpochMs) VALUES ('radio', 'HackRF One', 'suffix', 'r9', 'fw', '1.10', 1, 2)",
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(name, 2, true, NotebookDatabase.MIGRATION_1_2).use { migrated ->
            migrated.query("SELECT connectionState, connectionRevision FROM radio_devices WHERE id = 'radio'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("DISCONNECTED", cursor.getString(0))
                assertEquals(0L, cursor.getLong(1))
            }
        }
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
