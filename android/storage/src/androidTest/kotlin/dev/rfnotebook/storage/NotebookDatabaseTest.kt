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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
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
            appVersion = "1.0.0-rc1",
        )

        assertEquals(
            listOf(FrequencyRange(902_000_000, 910_000_000), FrequencyRange(912_000_000, 928_000_000)),
            launch.scanRanges,
        )
        assertEquals("1.0.0-rc1", database.notebookDao().survey(launch.surveyId)!!.appVersion)
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
    fun migrationFromVersionThreeAddsAtomicCaptureIndex() {
        val name = "migration-m4-${UUID.randomUUID()}"
        migrationHelper.createDatabase(name, 3).close()

        migrationHelper.runMigrationsAndValidate(name, 4, true, NotebookDatabase.MIGRATION_3_4).use { migrated ->
            migrated.query("SELECT COUNT(*) FROM iq_captures").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun corruptDatabaseFailsClosedWithoutDestructiveFallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "corrupt-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val corruptBytes = "not-a-sqlite-database".toByteArray()
        file.writeBytes(corruptBytes)

        try {
            NotebookDatabase.build(context, name)
            fail("A corrupt database must not be silently recreated")
        } catch (_: Exception) {
            assertArrayEquals(corruptBytes, file.readBytes())
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun completedSurveyBuildsCompleteExportContentFromAuthoritativeRows() = kotlinx.coroutines.runBlocking {
        val dao = database.notebookDao()
        val radio = radio()
        val equipment = equipment(radio.id)
        val band = band(equipment.versionId)
        val survey = survey(band.versionId, equipment.versionId).copy(status = "COMPLETE", endedAtEpochMs = 2_000)
        val fix = LocationFixEntity(
            "export-fix", survey.id, 1_000, 1_000, 42.1, -71.2, 6f,
            null, null, null, "gps", false,
        )
        dao.insertRadioDevice(radio)
        dao.insertEquipmentProfile(equipment)
        dao.insertBandProfile(band)
        dao.insertBandRanges(listOf(BandRangeEntity(band.versionId, "INCLUDE", 0, 902_000_000, 928_000_000)))
        dao.insertSurvey(survey)
        dao.insertAggregateBatch(fix, listOf(SpectrumAggregateEntity(
            survey.id, 1_000, 915_000_000, -90f, -70f, -60f, -92f, 4,
            fix.id, "FRESH",
        )))
        dao.insertGap(AcquisitionGapEntity(
            "export-gap", survey.id, "USB_DETACH", 1_100, 1_100,
            1_200, 1_200, 3, "physical detach",
        ))
        val captureDirectory = java.io.File(
            ApplicationProvider.getApplicationContext<Context>().cacheDir,
            "export-capture-${UUID.randomUUID()}",
        ).apply { mkdirs() }
        val iq = captureDirectory.resolve("linked.cs8").apply { writeBytes(byteArrayOf(1, -1)) }
        val sidecar = captureDirectory.resolve("linked.json").apply { writeText("{}") }
        val preview = captureDirectory.resolve("linked-preview.pgm").apply { writeBytes(byteArrayOf(0)) }
        dao.insertIqCapture(IQCaptureEntity(
            "linked", "fingerprint", survey.id, iq.absolutePath, sidecar.absolutePath,
            preview.absolutePath, 1_500, 1_000, 915_000_000, 2_000_000,
            "signed-int8-interleaved-iq", equipment.versionId, fix.id, 2, 2, 1,
            0, 0, "hash", "", "COMPLETE",
        ))

        val content = SurveyBundleContentFactory(dao).create(survey.id, "test")

        assertEquals("suffix", content.serialSuffix)
        assertTrue(content.observationsCsv.contains("915000000"))
        assertTrue(content.routeGeoJson.contains("-71.2,42.1"))
        assertTrue(content.aggregatesGeoJson.contains("observedRelativeStrengthDbfs"))
        assertTrue(content.gapsCsv.contains("USB_DETACH"))
        assertEquals(4_000_000, content.equipment?.sampleRateHz)
        assertEquals(100_000L, content.band?.binWidthHz)
        assertEquals(listOf(iq, sidecar, preview), content.captureFiles)
        captureDirectory.deleteRecursively()
    }

    @Test
    fun interruptedFinalizationCompletesIdempotentlyAndClosesOpenGaps() = kotlinx.coroutines.runBlocking {
        val dao = database.notebookDao()
        val radio = radio()
        val equipment = equipment(radio.id)
        val band = band(equipment.versionId)
        val survey = survey(band.versionId, equipment.versionId).copy(
            status = "FINALIZING",
            revision = 4,
            startedAtEpochMs = 500,
            lastWallTimeEpochMs = 1_500,
            lastMonotonicNs = 1_500,
        )
        dao.insertRadioDevice(radio)
        dao.insertEquipmentProfile(equipment)
        dao.insertBandProfile(band)
        dao.insertSurvey(survey)
        dao.insertGap(AcquisitionGapEntity(
            "finalizing-gap", survey.id, "USB_DETACH", 1_000, 1_000,
            null, null, 0, "open before finalization",
        ))
        val repository = NotebookSetupRepository(dao)

        assertEquals(listOf(survey.id), repository.recoverInterruptedFinalizations(2_000, 2_000))
        assertTrue(repository.recoverInterruptedFinalizations(3_000, 3_000).isEmpty())
        val completed = requireNotNull(dao.survey(survey.id))
        assertEquals("COMPLETE", completed.status)
        assertEquals(5, completed.revision)
        assertEquals(2_000, completed.endedAtEpochMs)
        assertEquals(2_000, dao.surveyGaps(survey.id).single().endedWallTimeEpochMs)
        val event = dao.surveyStateEvents(survey.id).single()
        assertEquals("FINALIZING", event.fromStatus)
        assertEquals("COMPLETE", event.toStatus)
    }

    @Test
    fun interruptedCaptureCommitIsRecoveredOrFailedWithoutUnindexedArtifacts() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = java.io.File(context.cacheDir, "capture-recovery-${UUID.randomUUID()}").apply { mkdirs() }
        val dao = database.notebookDao()
        try {
            val completeId = "capture-complete"
            val completeMetadata = CaptureMetadata(
                completeId, null, null, 1_000, 2_000, 250, 915_000_000, 2_000_000,
                1_750_000, 16, 16, false, false, "equipment:v1", null,
                null, null, null, null, "", "1.0.0-rc1",
            )
            dao.insertIqCapture(capturingEntity(directory, completeMetadata, expectedBytes = 4))
            AtomicIqCapture(directory, completeMetadata, expectedBytes = 4).run {
                append(byteArrayOf(1, -1, 2, -2))
                complete()
            }

            val failedId = "capture-interrupted"
            val failedMetadata = completeMetadata.copy(id = failedId)
            dao.insertIqCapture(capturingEntity(directory, failedMetadata, expectedBytes = 4))
            directory.resolve("$failedId.cs8.part").writeBytes(byteArrayOf(1, -1))

            val report = InterruptedArtifactRecovery.recoverCaptures(dao, directory)

            assertEquals(listOf(completeId), report.completedCaptureIds)
            assertEquals(listOf(failedId), report.failedCaptureIds)
            val rows = dao.iqCaptures().associateBy { it.id }
            assertEquals("COMPLETE", rows.getValue(completeId).status)
            assertEquals(4, rows.getValue(completeId).actualByteCount)
            assertEquals("FAILED", rows.getValue(failedId).status)
            assertTrue(directory.resolve("$completeId.cs8").isFile)
            assertTrue(!directory.resolve("$failedId.cs8.part").exists())
        } finally {
            directory.deleteRecursively()
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

    private fun capturingEntity(
        directory: java.io.File,
        metadata: CaptureMetadata,
        expectedBytes: Long,
    ) = IQCaptureEntity(
        metadata.id, metadata.fingerprintId, metadata.surveyId,
        directory.resolve("${metadata.id}.cs8").absolutePath,
        directory.resolve("${metadata.id}.json").absolutePath,
        directory.resolve("${metadata.id}-preview.pgm").absolutePath,
        metadata.startedAtEpochMs, metadata.durationMs, metadata.centerFrequencyHz, metadata.sampleRateHz,
        "signed-int8-interleaved-iq", metadata.equipmentProfileVersionId, null,
        expectedBytes, 0, 0, 0, 0, "", metadata.note, "CAPTURING",
    )

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
