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
    ],
    version = 2,
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
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
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
    }
}
