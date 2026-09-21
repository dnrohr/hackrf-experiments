package dev.rfnotebook.maps

import android.content.Context
import android.widget.FrameLayout
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos

data class RenderedFingerprintLayer(
    val fingerprintId: String,
    val label: String,
    val color: String,
    val aggregation: GeographicAggregationResult,
)

data class FieldMapModel(
    val layers: List<RenderedFingerprintLayer>,
    val routes: List<MapRoutePoint>,
    val gaps: List<MapGapSegment>,
)

class FieldMapView(context: Context) : FrameLayout(context) {
    private val mapView: MapView
    private var model = FieldMapModel(emptyList(), emptyList(), emptyList())
    private var loadedStyle: Style? = null

    init {
        contentDescription = "Observed relative strength map. Use the list view for equivalent labeled results."
        MapLibre.getInstance(context, null, WellKnownTileServer.MapLibre)
        mapView = MapView(context)
        addView(mapView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        mapView.onCreate(null)
        mapView.getMapAsync { map ->
            map.uiSettings.isAttributionEnabled = true
            map.setStyle(Style.Builder().fromUri(PRODUCTION_STYLE)) { style ->
                loadedStyle = style
                render(style)
                focus(map)
            }
        }
    }

    fun update(value: FieldMapModel) {
        if (model == value) return
        model = value
        loadedStyle?.let(::render)
    }

    private fun focus(map: org.maplibre.android.maps.MapLibreMap) {
        val point = model.layers.asSequence().flatMap { it.aggregation.cells.asSequence() }.firstOrNull()
            ?: model.routes.firstOrNull()
        val target = when (point) {
            is GeographicCell -> LatLng(point.centerLatitude, point.centerLongitude)
            is MapRoutePoint -> LatLng(point.latitude, point.longitude)
            else -> LatLng(0.0, 0.0)
        }
        map.cameraPosition = CameraPosition.Builder().target(target).zoom(DEFAULT_ZOOM).build()
    }

    private fun render(style: Style) {
        removeM3Layers(style)
        val routeFeatures = model.routes.groupBy { it.surveyId }.values.mapNotNull { route ->
            route.takeIf { it.size >= 2 }?.let {
                Feature.fromGeometry(LineString.fromLngLats(it.map { point -> Point.fromLngLat(point.longitude, point.latitude) }))
            }
        }
        addSource(style, ROUTE_SOURCE, FeatureCollection.fromFeatures(routeFeatures))
        style.addLayer(LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
            lineColor("#263238"), lineWidth(4f), lineOpacity(0.82f),
        ))

        val gapFeatures = model.gaps.mapNotNull { gap ->
            val before = gap.before ?: return@mapNotNull null
            val after = gap.after ?: return@mapNotNull null
            Feature.fromGeometry(LineString.fromLngLats(listOf(
                Point.fromLngLat(before.longitude, before.latitude),
                Point.fromLngLat(after.longitude, after.latitude),
            ))).also {
                it.addStringProperty("reason", gap.reason)
                it.addNumberProperty("dropped", gap.droppedUnitCount)
            }
        }
        addSource(style, GAP_SOURCE, FeatureCollection.fromFeatures(gapFeatures))

        val accuracy = model.routes.map { point ->
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).also {
                it.addNumberProperty("accuracy", point.horizontalAccuracyM)
                it.addBooleanProperty("interpolated", point.isInterpolated)
            }
        }
        addSource(style, ACCURACY_SOURCE, FeatureCollection.fromFeatures(accuracy))

        model.layers.forEachIndexed { index, layer ->
            val sourceId = "$CELL_SOURCE_PREFIX$index"
            val layerId = "$CELL_LAYER_PREFIX$index"
            val features = layer.aggregation.cells.map { cell ->
                Feature.fromGeometry(cellPolygon(cell)).also {
                    it.addNumberProperty("strength", cell.medianRelativeDb)
                    it.addStringProperty("confidence", cell.confidence.name)
                    it.addNumberProperty("support", cell.sampleCount)
                    it.addStringProperty("fingerprint", layer.label)
                }
            }
            addSource(style, sourceId, FeatureCollection.fromFeatures(features))
            style.addLayer(FillLayer(layerId, sourceId).withProperties(
                fillColor(layer.color),
                fillOpacity(interpolate(linear(), get("strength"), stop(-100, 0.15), stop(-65, 0.45), stop(-25, 0.88))),
                fillOutlineColor(layer.color),
            ))
        }
        style.addLayer(CircleLayer(ACCURACY_LAYER, ACCURACY_SOURCE).withProperties(
            circleRadius(interpolate(linear(), get("accuracy"), stop(3, 5), stop(25, 15), stop(100, 32))),
            circleColor("#1976D2"), circleOpacity(0.13f), circleStrokeColor("#0D47A1"), circleStrokeWidth(1f),
        ))
        style.addLayer(LineLayer(GAP_LAYER, GAP_SOURCE).withProperties(
            lineColor("#B71C1C"), lineWidth(6f), lineDasharray(arrayOf(1f, 1.5f)),
        ))
    }

    private fun addSource(style: Style, id: String, collection: FeatureCollection) {
        style.addSource(GeoJsonSource(id, collection))
    }

    private fun removeM3Layers(style: Style) {
        (0 until MAX_COMPARISON_LAYERS).forEach { index ->
            style.getLayer("$CELL_LAYER_PREFIX$index")?.let { style.removeLayer(it) }
            style.getSource("$CELL_SOURCE_PREFIX$index")?.let { style.removeSource(it) }
        }
        listOf(GAP_LAYER, ROUTE_LAYER, ACCURACY_LAYER).forEach { id -> style.getLayer(id)?.let { style.removeLayer(it) } }
        listOf(GAP_SOURCE, ROUTE_SOURCE, ACCURACY_SOURCE).forEach { id -> style.getSource(id)?.let { style.removeSource(it) } }
    }

    private fun cellPolygon(cell: GeographicCell): Polygon {
        val half = cell.cellSizeM / 2.0
        val latDegrees = half / 111_320.0
        val lonDegrees = half / (111_320.0 * cos(Math.toRadians(cell.centerLatitude)).coerceAtLeast(0.01))
        val lat = cell.centerLatitude
        val lon = cell.centerLongitude
        val ring = listOf(
            Point.fromLngLat(lon - lonDegrees, lat - latDegrees),
            Point.fromLngLat(lon + lonDegrees, lat - latDegrees),
            Point.fromLngLat(lon + lonDegrees, lat + latDegrees),
            Point.fromLngLat(lon - lonDegrees, lat + latDegrees),
            Point.fromLngLat(lon - lonDegrees, lat - latDegrees),
        )
        return Polygon.fromLngLats(listOf(ring))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mapView.onStart()
        mapView.onResume()
    }

    override fun onDetachedFromWindow() {
        mapView.onPause()
        mapView.onStop()
        mapView.onDestroy()
        super.onDetachedFromWindow()
    }

    companion object {
        const val PRODUCTION_STYLE = "https://tiles.openfreemap.org/styles/liberty"
        const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles Data from OpenStreetMap"
        private const val DEFAULT_ZOOM = 14.0
        private const val MAX_COMPARISON_LAYERS = 4
        private const val ROUTE_SOURCE = "m3-route-source"
        private const val ROUTE_LAYER = "m3-route"
        private const val GAP_SOURCE = "m3-gap-source"
        private const val GAP_LAYER = "m3-gaps"
        private const val ACCURACY_SOURCE = "m3-accuracy-source"
        private const val ACCURACY_LAYER = "m3-accuracy"
        private const val CELL_SOURCE_PREFIX = "m3-cells-source-"
        private const val CELL_LAYER_PREFIX = "m3-cells-layer-"
    }
}
