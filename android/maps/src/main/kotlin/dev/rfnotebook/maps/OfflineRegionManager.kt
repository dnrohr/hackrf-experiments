package dev.rfnotebook.maps

import android.content.Context
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

data class OfflineBounds(
    val north: Double,
    val east: Double,
    val south: Double,
    val west: Double,
) {
    init {
        require(north in -85.0..85.0 && south in -85.0..85.0 && north > south)
        require(east in -180.0..180.0 && west in -180.0..180.0 && east > west)
    }
}

data class OfflineRegionRequest(
    val name: String,
    val bounds: OfflineBounds,
    val minimumZoom: Double,
    val maximumZoom: Double,
) {
    init {
        require(name.isNotBlank())
        require(minimumZoom in 0.0..20.0 && maximumZoom in minimumZoom..20.0)
    }
}

enum class OfflineRegionPhase { INCOMPLETE, DOWNLOADING, COMPLETE, REMOVED, FAILED }

data class OfflineRegionProgress(
    val id: Long,
    val request: OfflineRegionRequest,
    val phase: OfflineRegionPhase,
    val completedResources: Long,
    val requiredResources: Long?,
    val completedBytes: Long,
    val explanation: String,
)

class FieldOfflineRegionManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager = OfflineManager.getInstance(
        appContext.also { MapLibre.getInstance(it, null, WellKnownTileServer.MapLibre) },
    )

    fun list(callback: (List<OfflineRegionProgress>) -> Unit) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions.orEmpty().mapNotNull { region ->
                    OfflineRegionMetadata.decode(region.metadata)?.let { region to it }
                }
                if (regions.isEmpty()) return callback(emptyList())
                val values = arrayOfNulls<OfflineRegionProgress>(regions.size)
                regions.forEachIndexed { index, pair ->
                    pair.first.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            values[index] = progress(pair.first.id, pair.second, status, null)
                            if (values.all { it != null }) callback(values.filterNotNull())
                        }
                        override fun onError(error: String?) {
                            values[index] = OfflineRegionProgress(
                                pair.first.id, pair.second, OfflineRegionPhase.FAILED, 0, null, 0,
                                "Could not read saved region: ${error ?: "unknown error"}",
                            )
                            if (values.all { it != null }) callback(values.filterNotNull())
                        }
                    })
                }
            }
            override fun onError(error: String) = callback(listOf(failure(-1, error)))
        })
    }

    fun download(request: OfflineRegionRequest, callback: (OfflineRegionProgress) -> Unit) {
        val definition = OfflineTilePyramidRegionDefinition(
            FieldMapView.PRODUCTION_STYLE,
            LatLngBounds.from(request.bounds.north, request.bounds.east, request.bounds.south, request.bounds.west),
            request.minimumZoom,
            request.maximumZoom,
            appContext.resources.displayMetrics.density,
        )
        manager.createOfflineRegion(definition, OfflineRegionMetadata.encode(request), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) = activate(offlineRegion, request, callback)
            override fun onError(error: String) = callback(failure(-1, "Could not create offline region: $error", request))
        })
    }

    fun resume(id: Long, callback: (OfflineRegionProgress) -> Unit) = find(id) { region, request ->
        if (region == null || request == null) callback(failure(id, "Saved offline region was not found."))
        else activate(region, request, callback)
    }

    fun remove(id: Long, callback: (OfflineRegionProgress) -> Unit) = find(id) { region, request ->
        if (region == null || request == null) return@find callback(failure(id, "Saved offline region was not found."))
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() = callback(OfflineRegionProgress(
                id, request, OfflineRegionPhase.REMOVED, 0, null, 0, "Offline region removed from this device.",
            ))
            override fun onError(error: String) = callback(failure(id, "Could not remove offline region: $error", request))
        })
    }

    private fun activate(region: OfflineRegion, request: OfflineRegionRequest, callback: (OfflineRegionProgress) -> Unit) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                val value = progress(region.id, request, status, OfflineRegionPhase.DOWNLOADING)
                if (status.isComplete) region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                callback(value)
            }
            override fun onError(error: OfflineRegionError) = callback(
                failure(region.id, "Offline download failed: ${error.reason}: ${error.message}", request),
            )
            override fun mapboxTileCountLimitExceeded(limit: Long) = callback(
                failure(region.id, "Offline tile limit exceeded: $limit", request),
            )
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    private fun find(id: Long, callback: (OfflineRegion?, OfflineRegionRequest?) -> Unit) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val region = offlineRegions.orEmpty().firstOrNull { it.id == id }
                callback(region, region?.metadata?.let(OfflineRegionMetadata::decode))
            }
            override fun onError(error: String) = callback(null, null)
        })
    }

    private fun progress(
        id: Long,
        request: OfflineRegionRequest,
        status: OfflineRegionStatus?,
        activePhase: OfflineRegionPhase?,
    ): OfflineRegionProgress {
        val completed = status?.completedResourceCount ?: 0
        val required = status?.takeIf { it.isRequiredResourceCountPrecise }?.requiredResourceCount
        val bytes = status?.completedResourceSize ?: 0
        val phase = if (status?.isComplete == true) OfflineRegionPhase.COMPLETE else activePhase ?: OfflineRegionPhase.INCOMPLETE
        val count = required?.let { "$completed/$it" } ?: "$completed/?"
        val explanation = if (phase == OfflineRegionPhase.COMPLETE) {
            "Available offline: $completed resources, $bytes bytes."
        } else "${phase.name.lowercase()}: $count resources, $bytes bytes stored; interrupted downloads can resume."
        return OfflineRegionProgress(id, request, phase, completed, required, bytes, explanation)
    }

    private fun failure(id: Long, error: String, request: OfflineRegionRequest = placeholderRequest()) =
        OfflineRegionProgress(id, request, OfflineRegionPhase.FAILED, 0, null, 0, error)

    private companion object {
        fun placeholderRequest() = OfflineRegionRequest("Unavailable", OfflineBounds(1.0, 1.0, 0.0, 0.0), 0.0, 0.0)
    }
}

internal object OfflineRegionMetadata {
    private const val PREFIX = "rf-field-notebook-m3-v1"

    fun encode(request: OfflineRegionRequest): ByteArray = listOf(
        PREFIX,
        request.name.replace('|', ' '),
        request.bounds.north,
        request.bounds.east,
        request.bounds.south,
        request.bounds.west,
        request.minimumZoom,
        request.maximumZoom,
    ).joinToString("|").encodeToByteArray()

    fun decode(value: ByteArray): OfflineRegionRequest? = runCatching {
        val parts = value.decodeToString().split('|')
        require(parts.size == 8 && parts[0] == PREFIX)
        OfflineRegionRequest(
            parts[1],
            OfflineBounds(parts[2].toDouble(), parts[3].toDouble(), parts[4].toDouble(), parts[5].toDouble()),
            parts[6].toDouble(),
            parts[7].toDouble(),
        )
    }.getOrNull()
}
