package dev.rfnotebook.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    ],
    version = 1,
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
            ).build().also { instance = it }
        }
    }
}
