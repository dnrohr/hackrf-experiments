package dev.rfnotebook.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo

@Entity(tableName = "radio_devices")
data class RadioDeviceEntity(
    @androidx.room.PrimaryKey val id: String,
    val model: String,
    val serialSuffix: String,
    val hardwareRevision: String,
    val firmwareVersion: String,
    val usbApiVersion: String,
    val firstSeenAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
    @ColumnInfo(defaultValue = "'DISCONNECTED'") val connectionState: String = "DISCONNECTED",
    @ColumnInfo(defaultValue = "0") val connectionRevision: Long = 0,
)

@Entity(
    tableName = "connection_state_events",
    indices = [Index("radioDeviceId")],
    foreignKeys = [ForeignKey(
        entity = RadioDeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["radioDeviceId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ConnectionStateEventEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val radioDeviceId: String,
    val fromState: String,
    val toState: String,
    val revision: Long,
    val wallTimeEpochMs: Long,
    val monotonicNs: Long,
    val explanation: String?,
)

@Entity(
    tableName = "equipment_profiles",
    indices = [Index(value = ["profileId", "version"], unique = true), Index("radioDeviceId")],
    foreignKeys = [ForeignKey(
        entity = RadioDeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["radioDeviceId"],
        onDelete = ForeignKey.RESTRICT,
    )],
)
data class EquipmentProfileEntity(
    @androidx.room.PrimaryKey val versionId: String,
    val profileId: String,
    val name: String,
    val version: Int,
    val radioDeviceId: String,
    val antennaName: String,
    val antennaBands: String,
    val adapterNotes: String,
    val sampleRateHz: Int,
    val basebandFilterHz: Int,
    val lnaGainDb: Int,
    val vgaGainDb: Int,
    val rfAmpEnabled: Boolean,
    val antennaPowerEnabled: Boolean,
    val isCalibrated: Boolean,
    val photoReference: String?,
    val notes: String,
    val createdAtEpochMs: Long,
    val retiredAtEpochMs: Long?,
)

@Entity(
    tableName = "band_profiles",
    indices = [Index(value = ["profileId", "version"], unique = true), Index("equipmentProfileVersionId")],
    foreignKeys = [ForeignKey(
        entity = EquipmentProfileEntity::class,
        parentColumns = ["versionId"],
        childColumns = ["equipmentProfileVersionId"],
        onDelete = ForeignKey.RESTRICT,
    )],
)
data class BandProfileEntity(
    @androidx.room.PrimaryKey val versionId: String,
    val profileId: String,
    val name: String,
    val version: Int,
    val equipmentProfileVersionId: String,
    val binWidthHz: Long,
    val targetRevisitMs: Long,
    val thresholdSnrDb: Float,
    val minimumBandwidthHz: Long,
    val explorationAidDisclaimer: String,
    val createdAtEpochMs: Long,
    val retiredAtEpochMs: Long?,
)

@Entity(
    tableName = "band_ranges",
    primaryKeys = ["bandProfileVersionId", "kind", "ordinal"],
    indices = [Index("bandProfileVersionId")],
    foreignKeys = [ForeignKey(
        entity = BandProfileEntity::class,
        parentColumns = ["versionId"],
        childColumns = ["bandProfileVersionId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class BandRangeEntity(
    val bandProfileVersionId: String,
    val kind: String,
    val ordinal: Int,
    val startHz: Long,
    val endHz: Long,
)

@Entity(
    tableName = "surveys",
    indices = [Index("bandProfileVersionId"), Index("equipmentProfileVersionId"), Index("status")],
    foreignKeys = [
        ForeignKey(
            entity = BandProfileEntity::class,
            parentColumns = ["versionId"],
            childColumns = ["bandProfileVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = EquipmentProfileEntity::class,
            parentColumns = ["versionId"],
            childColumns = ["equipmentProfileVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class SurveyEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val bandProfileVersionId: String,
    val equipmentProfileVersionId: String,
    val status: String,
    val revision: Long,
    val startedAtEpochMs: Long?,
    val endedAtEpochMs: Long?,
    val lastWallTimeEpochMs: Long,
    val lastMonotonicNs: Long,
    val distanceMeters: Double,
    val locationCoverageRatio: Double,
    val droppedFrameCount: Long,
    val overrunCount: Long,
    val malformedFrameCount: Long,
    val staleFixCount: Long,
    val unlocatedObservationCount: Long,
    val appVersion: String,
    val detectorVersion: String,
    val notes: String,
    val failureExplanation: String?,
)

@Entity(
    tableName = "survey_state_events",
    indices = [Index("surveyId")],
    foreignKeys = [ForeignKey(
        entity = SurveyEntity::class,
        parentColumns = ["id"],
        childColumns = ["surveyId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class SurveyStateEventEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surveyId: String,
    val fromStatus: String,
    val toStatus: String,
    val wallTimeEpochMs: Long,
    val monotonicNs: Long,
    val explanation: String?,
)

@Entity(
    tableName = "location_fixes",
    indices = [Index(value = ["surveyId", "monotonicNs"])],
    foreignKeys = [ForeignKey(
        entity = SurveyEntity::class,
        parentColumns = ["id"],
        childColumns = ["surveyId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class LocationFixEntity(
    @androidx.room.PrimaryKey val id: String,
    val surveyId: String,
    val wallTimeEpochMs: Long,
    val monotonicNs: Long,
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyM: Float,
    val altitudeM: Double?,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    val provider: String,
    val isInterpolated: Boolean,
)

@Entity(
    tableName = "spectrum_aggregates",
    primaryKeys = ["surveyId", "timeBucketStartEpochMs", "frequencyBinHz"],
    indices = [Index("locationFixId")],
    foreignKeys = [
        ForeignKey(
            entity = SurveyEntity::class,
            parentColumns = ["id"],
            childColumns = ["surveyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocationFixEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationFixId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class SpectrumAggregateEntity(
    val surveyId: String,
    val timeBucketStartEpochMs: Long,
    val frequencyBinHz: Long,
    val minimumPowerDbfs: Float,
    val medianPowerDbfs: Float,
    val maximumPowerDbfs: Float,
    val noiseEstimateDbfs: Float,
    val sampleCount: Int,
    val locationFixId: String?,
    val locationState: String,
)

@Entity(
    tableName = "acquisition_gaps",
    indices = [Index(value = ["surveyId", "startedMonotonicNs"])],
    foreignKeys = [ForeignKey(
        entity = SurveyEntity::class,
        parentColumns = ["id"],
        childColumns = ["surveyId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class AcquisitionGapEntity(
    @androidx.room.PrimaryKey val id: String,
    val surveyId: String,
    val reason: String,
    val startedWallTimeEpochMs: Long,
    val startedMonotonicNs: Long,
    val endedWallTimeEpochMs: Long?,
    val endedMonotonicNs: Long?,
    val droppedUnitCount: Long,
    val explanation: String,
)

@Entity(
    tableName = "health_snapshots",
    indices = [Index(value = ["surveyId", "monotonicNs"])],
    foreignKeys = [ForeignKey(
        entity = SurveyEntity::class,
        parentColumns = ["id"],
        childColumns = ["surveyId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class HealthSnapshotEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surveyId: String,
    val wallTimeEpochMs: Long,
    val monotonicNs: Long,
    val usbBytesPerSecond: Long,
    val nativeQueueDepth: Int,
    val processingQueueDepth: Int,
    val persistenceQueueDepth: Int,
    val droppedNativeUnits: Long,
    val droppedProcessingUnits: Long,
    val droppedPersistenceUnits: Long,
    val malformedFrameCount: Long,
    val overrunCount: Long,
    val staleFixCount: Long,
    val serviceGapCount: Long,
    val availableStorageBytes: Long,
    val estimatedRemainingBytes: Long,
    val batteryPercent: Int?,
    val thermalStatus: Int?,
    val warning: String?,
)
