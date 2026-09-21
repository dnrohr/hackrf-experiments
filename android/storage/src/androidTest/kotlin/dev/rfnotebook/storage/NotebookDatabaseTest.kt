package dev.rfnotebook.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.testing.MigrationTestHelper
import dev.rfnotebook.domain.FrequencyRange
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
        val fix = LocationFixEntity(
            id = "fix", surveyId = survey.id, wallTimeEpochMs = 1_000, monotonicNs = 2_000,
            latitude = 0.0, longitude = 0.0, horizontalAccuracyM = 5f, altitudeM = null,
            speedMps = null, bearingDegrees = null, provider = "test", isInterpolated = false,
        )
        dao.insertAggregateBatch(null, listOf(SpectrumAggregateEntity(
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
        dao.insertAggregateBatch(fix, listOf(SpectrumAggregateEntity(
            surveyId = survey.id,
            timeBucketStartEpochMs = 2_000,
            frequencyBinHz = 915_100_000,
            minimumPowerDbfs = -91f,
            medianPowerDbfs = -86f,
            maximumPowerDbfs = -71f,
            noiseEstimateDbfs = -93f,
            sampleCount = 8,
            locationFixId = fix.id,
            locationState = "FRESH",
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
        dao.insertGap(AcquisitionGapEntity(
            id = "gap", surveyId = survey.id, reason = "USB_DETACH",
            startedWallTimeEpochMs = 3_000, startedMonotonicNs = 4_000,
            endedWallTimeEpochMs = null, endedMonotonicNs = null,
            droppedUnitCount = 0, explanation = "detached",
        ))
        dao.closeOpenGaps(survey.id, 5_000, 9_000)

        assertEquals(8, dao.survey(survey.id)!!.overrunCount)
        assertEquals(1, dao.locationFixCount(survey.id))
        assertEquals(9_000L, dao.surveyGaps(survey.id).single().endedMonotonicNs)
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
    fun referencedMetadataEditAlsoCreatesNewEquipmentVersion() = kotlinx.coroutines.runBlocking {
        val repository = NotebookSetupRepository(database.notebookDao())

        repository.createStarterSurvey("suffix", "HackRF One", 1, 1, "first", equipmentNotes = "first note")
        repository.createStarterSurvey("suffix", "HackRF One", 2, 2, "second", equipmentNotes = "changed note")

        assertEquals(listOf(1, 2), database.notebookDao().equipmentProfileVersions("equipment:suffix").map { it.version })
    }

    @Test
    fun launchUsesIncludedRangesMinusPersistedExclusions() = kotlinx.coroutines.runBlocking {
        val launch = NotebookSetupRepository(database.notebookDao()).createStarterSurvey(
            "suffix",
            "HackRF One",
            1,
            1,
            "excluded",
            bandStartHz = 902_000_000,
            bandEndHz = 928_000_000,
            excludedRanges = listOf(FrequencyRange(910_000_000, 912_000_000)),
        )

        assertEquals(
            listOf(FrequencyRange(902_000_000, 910_000_000), FrequencyRange(912_000_000, 928_000_000)),
            launch.scanRanges,
        )
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

    @Test
    fun migrationFromVersionTwoAddsM2DerivedEvidenceWithoutChangingAggregates() {
        val name = "migration-m2-${UUID.randomUUID()}"
        migrationHelper.createDatabase(name, 2).apply {
            execSQL(
                "INSERT INTO radio_devices (id, model, serialSuffix, hardwareRevision, firmwareVersion, usbApiVersion, firstSeenAtEpochMs, lastSeenAtEpochMs, connectionState, connectionRevision) VALUES ('radio', 'HackRF One', 'suffix', 'r9', 'fw', '1.10', 1, 2, 'DISCONNECTED', 0)",
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(name, 3, true, NotebookDatabase.MIGRATION_2_3).use { migrated ->
            migrated.query("SELECT COUNT(*) FROM detections").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            migrated.query("SELECT model FROM radio_devices WHERE id = 'radio'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("HackRF One", cursor.getString(0))
            }
        }
    }

    @Test
    fun completedM1SurveyReprocessesOfflineAndUserStateIsReversible() = kotlinx.coroutines.runBlocking {
        val setup = NotebookSetupRepository(database.notebookDao())
        val launch = setup.createStarterSurvey("suffix", "HackRF One", 1, 1, "offline")
        val dao = database.notebookDao()
        val fixA = LocationFixEntity(
            "m2-fix-a", launch.surveyId, 0, 0, 40.0, -74.0, 5f,
            null, null, null, "test", false,
        )
        val fixB = LocationFixEntity(
            "m2-fix-b", launch.surveyId, 3_000, 3_000_000_000, 40.01, -73.99, 7f,
            null, null, null, "test", false,
        )
        dao.insertLocationFixes(listOf(fixA, fixB))
        val aggregates = (0 until 20).flatMap { second ->
            listOf(914_900_000L, 915_000_000L, 915_100_000L).map { frequency ->
                val signal = frequency == 915_000_000L && second in setOf(0, 3, 6)
                SpectrumAggregateEntity(
                    launch.surveyId, second * 1_000L, frequency,
                    if (signal) -60f else -92f, if (signal) -55f else -90f, if (signal) -50f else -88f,
                    -90f, 10,
                    when (second) { 0 -> fixA.id; 3, 6 -> fixB.id; else -> null },
                    if (second in setOf(0, 3, 6)) "FRESH" else "MISSING",
                )
            }
        }
        dao.insertAggregates(aggregates)

        val repository = DiscoveryRepository(dao)
        val result = repository.reprocessSurvey(launch.surveyId, 30_000)
        val fingerprint = repository.discoveries().single()
        val detectionIds = repository.detail(fingerprint.id).detections.map { it.id }
        val split = repository.splitFingerprint(fingerprint.id, setOf(detectionIds.last()), 31_000)
        val mergedId = repository.mergeFingerprints(setOf(split.first, split.second), 32_000)
        repository.updateUserFields(mergedId, dev.rfnotebook.domain.FingerprintState.ARTIFACT, "local interference", setOf("reviewed"), "reversible")
        repository.updateUserFields(mergedId, dev.rfnotebook.domain.FingerprintState.INTERESTING, "local interference", setOf("reviewed"), "reversible")
        val corrected = repository.detail(mergedId)
        repository.reprocessSurvey(launch.surveyId, 33_000)
        val afterReprocessing = repository.detail(mergedId)

        assertEquals(aggregates.size.toLong(), result.aggregateCount)
        assertEquals(1, result.fingerprintCount)
        assertEquals("INTERESTING", corrected.fingerprint.state)
        assertEquals(detectionIds.toSet(), corrected.detections.map { it.id }.toSet())
        assertEquals("MERGE", corrected.provenance.last().operation)
        assertEquals(3, corrected.fingerprint.locatedObservationCount)
        assertEquals(40.0, corrected.fingerprint.minimumLatitude!!, 0.0)
        assertEquals(40.01, corrected.fingerprint.maximumLatitude!!, 0.0)
        assertEquals("local interference", afterReprocessing.fingerprint.userLabel)
        assertEquals("INTERESTING", afterReprocessing.fingerprint.state)
        assertEquals("MERGE", afterReprocessing.provenance.last().operation)
        assertEquals(aggregates.size.toLong(), dao.aggregateCount(launch.surveyId))
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
