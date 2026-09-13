package dev.rfnotebook.maps

import android.content.Context
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

object OfflineRegionSpike {
    private val metadata = "rf-notebook-m0-region-v1".encodeToByteArray()

    fun downloadOrReopen(context: Context, callback: (String) -> Unit) {
        val manager = OfflineManager.getInstance(context)
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val saved = offlineRegions.orEmpty().firstOrNull { it.metadata.contentEquals(metadata) }
                if (saved == null) create(manager, context, callback) else activate(saved, callback, reopened = true)
            }

            override fun onError(error: String) = callback("Failed to list saved regions: $error")
        })
    }

    private fun create(manager: OfflineManager, context: Context, callback: (String) -> Unit) {
        val definition = OfflineTilePyramidRegionDefinition(
            MapPrototypeView.DEMO_STYLE,
            LatLngBounds.from(40.72, -73.99, 40.70, -74.02),
            12.0,
            15.0,
            context.resources.displayMetrics.density,
        )
        manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) = activate(offlineRegion, callback, reopened = false)
            override fun onError(error: String) = callback("Failed to create offline region: $error")
        })
    }

    private fun activate(region: OfflineRegion, callback: (String) -> Unit, reopened: Boolean) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                if (status.isComplete) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    callback("${if (reopened) "Reopened" else "Downloaded"}: ${status.completedResourceCount} resources, ${status.completedResourceSize} bytes")
                } else {
                    callback("Downloading: ${status.completedResourceCount}/${if (status.isRequiredResourceCountPrecise) status.requiredResourceCount else "?"} resources")
                }
            }

            override fun onError(error: OfflineRegionError) = callback("Offline download failed: ${error.reason}: ${error.message}")
            override fun mapboxTileCountLimitExceeded(limit: Long) = callback("Offline tile limit exceeded: $limit")
        })
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status?.isComplete == true) callback("Reopened: ${status.completedResourceCount} resources, ${status.completedResourceSize} bytes")
                else region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String?) = callback("Failed to read offline status: ${error ?: "unknown error"}")
        })
    }
}
