package dev.rfnotebook.maps

import android.content.Context
import android.widget.FrameLayout
import dev.rfnotebook.domain.Observation
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class MapPrototypeView(context: Context, observations: List<Observation>) : FrameLayout(context) {
    private val mapView: MapView

    init {
        MapLibre.getInstance(context)
        mapView = MapView(context)
        addView(mapView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        mapView.onCreate(null)
        val focus = observations.firstOrNull()?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(0.0, 0.0)
        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder().target(focus).zoom(14.0).build()
            map.setStyle(Style.Builder().fromUri(DEMO_STYLE)) { style ->
                val points = observations.map { Point.fromLngLat(it.longitude, it.latitude) }
                style.addSource(GeoJsonSource(ROUTE_SOURCE, Feature.fromGeometry(LineString.fromLngLats(points))))
                style.addLayer(LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(lineColor("#263238"), lineWidth(4f)))

                val accuracy = observations.mapIndexed { index, observation ->
                    Feature.fromGeometry(points[index]).also {
                        it.addNumberProperty("accuracy", observation.horizontalAccuracyM)
                        it.addNumberProperty("strength", observation.relativePowerDbfs)
                    }
                }
                style.addSource(GeoJsonSource(OBSERVATION_SOURCE, FeatureCollection.fromFeatures(accuracy)))
                style.addLayer(CircleLayer(ACCURACY_LAYER, OBSERVATION_SOURCE).withProperties(
                    circleRadius(interpolate(linear(), get("accuracy"), stop(5, 10), stop(25, 36))),
                    circleColor("#1976D2"), circleOpacity(0.22f),
                ))
                style.addLayer(CircleLayer(STRENGTH_LAYER, OBSERVATION_SOURCE).withProperties(
                    circleRadius(interpolate(linear(), get("strength"), stop(-80, 4), stop(-35, 11))),
                    circleColor(interpolate(linear(), get("strength"), stop(-80, "#FFF59D"), stop(-55, "#FB8C00"), stop(-35, "#C62828"))),
                    circleOpacity(0.85f),
                ))
            }
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); mapView.onStart(); mapView.onResume() }
    override fun onDetachedFromWindow() { mapView.onPause(); mapView.onStop(); mapView.onDestroy(); super.onDetachedFromWindow() }

    companion object {
        const val DEMO_STYLE = "https://demotiles.maplibre.org/style.json"
        private const val ROUTE_SOURCE = "m0-route-source"
        private const val OBSERVATION_SOURCE = "m0-observation-source"
        private const val ROUTE_LAYER = "m0-route"
        private const val ACCURACY_LAYER = "m0-accuracy"
        private const val STRENGTH_LAYER = "m0-strength"
    }
}
