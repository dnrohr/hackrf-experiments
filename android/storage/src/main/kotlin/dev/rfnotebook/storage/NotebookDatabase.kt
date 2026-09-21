package dev.rfnotebook.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RadioDeviceEntity::class,
        EquipmentProfileEntity::class,
        BandProfileEntity::class,
        BandRangeEntity::class,
        SurveyEntity::class,
        SurveyStateEventEntity::class,
        LocationFixEntity::class,
        SpectrumAggregateEntity::class,
        AcquisitionGapEntity::class,
        HealthSnapshotEntity::class,
        ConnectionStateEventEntity::class,
        DetectionEntity::class,
        SignalFingerprintEntity::class,
        FingerprintHintEntity::class,
        FingerprintProvenanceEntity::class,
        ReprocessingJobEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class NotebookDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao

    companion object {
        const val DATABASE_NAME = "rf-field-notebook.db"

        @Volatile private var instance: NotebookDatabase? = null

        fun open(context: Context): NotebookDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NotebookDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE radio_devices ADD COLUMN connectionState TEXT NOT NULL DEFAULT 'DISCONNECTED'")
                db.execSQL("ALTER TABLE radio_devices ADD COLUMN connectionRevision INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS connection_state_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        radioDeviceId TEXT NOT NULL,
                        fromState TEXT NOT NULL,
                        toState TEXT NOT NULL,
                        revision INTEGER NOT NULL,
                        wallTimeEpochMs INTEGER NOT NULL,
                        monotonicNs INTEGER NOT NULL,
                        explanation TEXT,
                        FOREIGN KEY(radioDeviceId) REFERENCES radio_devices(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_connection_state_events_radioDeviceId ON connection_state_events(radioDeviceId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS detections (id TEXT NOT NULL PRIMARY KEY, surveyId TEXT NOT NULL, fingerprintId TEXT, equipmentProfileVersionId TEXT NOT NULL, startedAtEpochMs INTEGER NOT NULL, endedAtEpochMs INTEGER NOT NULL, centerFrequencyHz INTEGER NOT NULL, bandwidthHz INTEGER NOT NULL, peakPowerDbfs REAL NOT NULL, medianPowerDbfs REAL NOT NULL, snrDb REAL NOT NULL, locationFixId TEXT, detectorVersion TEXT NOT NULL, kind TEXT NOT NULL, qualityFlags TEXT NOT NULL, FOREIGN KEY(surveyId) REFERENCES surveys(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(fingerprintId) REFERENCES signal_fingerprints(id) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(locationFixId) REFERENCES location_fixes(id) ON UPDATE NO ACTION ON DELETE SET NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detections_surveyId ON detections(surveyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detections_fingerprintId ON detections(fingerprintId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detections_equipmentProfileVersionId ON detections(equipmentProfileVersionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detections_locationFixId ON detections(locationFixId)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS signal_fingerprints (id TEXT NOT NULL PRIMARY KEY, equipmentProfileVersionId TEXT NOT NULL, nominalFrequencyHz INTEGER NOT NULL, typicalBandwidthHz INTEGER NOT NULL, firstSeenAtEpochMs INTEGER NOT NULL, lastSeenAtEpochMs INTEGER NOT NULL, occurrenceCount INTEGER NOT NULL, dutyCycleEstimate REAL NOT NULL, typicalBurstDurationMs INTEGER, typicalRepeatIntervalMs INTEGER, noveltyScore REAL NOT NULL, algorithmVersion TEXT NOT NULL, state TEXT NOT NULL, userLabel TEXT NOT NULL, tags TEXT NOT NULL, notes TEXT NOT NULL, minimumLatitude REAL, maximumLatitude REAL, minimumLongitude REAL, maximumLongitude REAL, locatedObservationCount INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_signal_fingerprints_equipmentProfileVersionId ON signal_fingerprints(equipmentProfileVersionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_signal_fingerprints_state ON signal_fingerprints(state)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS fingerprint_hints (fingerprintId TEXT NOT NULL, rank INTEGER NOT NULL, category TEXT NOT NULL, confidence REAL NOT NULL, evidence TEXT NOT NULL, PRIMARY KEY(fingerprintId, rank), FOREIGN KEY(fingerprintId) REFERENCES signal_fingerprints(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fingerprint_hints_fingerprintId ON fingerprint_hints(fingerprintId)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS fingerprint_provenance (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fingerprintId TEXT NOT NULL, operation TEXT NOT NULL, sourceFingerprintIds TEXT NOT NULL, atEpochMs INTEGER NOT NULL, explanation TEXT NOT NULL, FOREIGN KEY(fingerprintId) REFERENCES signal_fingerprints(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fingerprint_provenance_fingerprintId ON fingerprint_provenance(fingerprintId)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS reprocessing_jobs (id TEXT NOT NULL PRIMARY KEY, surveyId TEXT NOT NULL, detectorVersion TEXT NOT NULL, clusteringVersion TEXT NOT NULL, status TEXT NOT NULL, startedAtEpochMs INTEGER NOT NULL, endedAtEpochMs INTEGER, inputAggregateCount INTEGER NOT NULL, detectionCount INTEGER NOT NULL, fingerprintCount INTEGER NOT NULL, failureExplanation TEXT)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reprocessing_jobs_surveyId ON reprocessing_jobs(surveyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reprocessing_jobs_status ON reprocessing_jobs(status)")
            }
        }
    }
}
