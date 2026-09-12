package com.example.buswatch.admin.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.common.MapUtils
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.util.GeoPoint
import java.util.ArrayList

class RouteMapFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null

    private val STOPS_SOURCE_ID = "stops-source"
    private val STOPS_LAYER_ID = "stops-layer"
    private val ROUTES_SOURCE_ID = "routes-source"
    private val ROUTES_LAYER_ID = "routes-layer"

    private val stopsFeatures = mutableListOf<Feature>()
    private val routesFeatures = mutableListOf<Feature>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_route_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        view.findViewById<TextView>(R.id.tabRouteList)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.loadRouting()
        }

        mapView = view.findViewById(R.id.mapAllRoutes)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) { style ->
                setupStyle(style)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(14.5995, 120.9842), 14.0))
                loadAllRoutesData(style)
            }
        }

        view.findViewById<ImageButton>(R.id.btnMyLocation)?.setOnClickListener {
            if (stopsFeatures.isNotEmpty()) {
                val randomStop = stopsFeatures.random()
                val point = randomStop.geometry() as Point
                mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude(), point.longitude()), 17.0))
            } else {
                Toast.makeText(requireContext(), "No stops found on map", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupStyle(style: Style) {
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_stop_marker, 32, 32)?.let {
            style.addImage("stop-icon", it)
        }

        style.addSource(GeoJsonSource(ROUTES_SOURCE_ID))
        style.addSource(GeoJsonSource(STOPS_SOURCE_ID))

        style.addLayer(LineLayer(ROUTES_LAYER_ID, ROUTES_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        })

        style.addLayer(SymbolLayer(STOPS_LAYER_ID, STOPS_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage("stop-icon"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true)
            )
        })
    }

    private fun loadAllRoutesData(style: Style) {
        val colors = listOf("#4A90E2", "#E24A4A", "#4AE24A", "#E24AE2", "#4AE2E2", "#E2E24A")
        var colorIdx = 0
        
        stopsFeatures.clear()
        routesFeatures.clear()

        db.collection("routes").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
            for (doc in snapshots) {
                val routeName = doc.getString("routeName") ?: "Unknown"
                @Suppress("UNCHECKED_CAST")
                val stopIds = doc.get("stopIds") as? List<String> ?: emptyList()
                val routeColor = colors[colorIdx % colors.size]
                colorIdx++

                if (stopIds.isNotEmpty()) {
                    loadRouteStops(style, stopIds, routeColor, routeName)
                }
            }
        }
    }

    private fun loadRouteStops(style: Style, stopIds: List<String>, color: String, routeName: String) {
        val stopPointsMap = mutableMapOf<String, GeoPoint>()
        var loadedCount = 0
        
        for (stopId in stopIds) {
            db.collection("stops").document(stopId).get().addOnSuccessListener { stopDoc ->
                val lat = stopDoc.getDouble("latitude")
                val lng = stopDoc.getDouble("longitude")
                if (lat != null && lng != null) {
                    val p = GeoPoint(lat, lng)
                    stopPointsMap[stopId] = p
                    
                    val f = Feature.fromGeometry(Point.fromLngLat(lng, lat))
                    f.addStringProperty("name", stopDoc.getString("name"))
                    f.addStringProperty("routeName", routeName)
                    stopsFeatures.add(f)
                }
                loadedCount++
                if (loadedCount == stopIds.size) {
                    val orderedPoints = stopIds.mapNotNull { stopPointsMap[it] }
                    if (orderedPoints.size > 1) {
                        drawRoadOverlay(style, orderedPoints, color)
                    }
                    updateSources(style)
                }
            }
        }
    }

    private fun updateSources(style: Style) {
        style.getSourceAs<GeoJsonSource>(STOPS_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(stopsFeatures))
        style.getSourceAs<GeoJsonSource>(ROUTES_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(routesFeatures))
    }

    private fun drawRoadOverlay(style: Style, points: List<GeoPoint>, color: String) {
        val ctx = context?.applicationContext ?: return
        Thread {
            try {
                val roadManager = OSRMRoadManager(ctx, "BusWatch-Android-App/1.0")
                val road = roadManager.getRoad(ArrayList(points))
                
                if (road.mStatus == Road.STATUS_OK) {
                    val routePoints = road.mRouteHigh.map { Point.fromLngLat(it.longitude, it.latitude) }
                    val lineString = LineString.fromLngLats(routePoints)
                    val feature = Feature.fromGeometry(lineString)
                    feature.addStringProperty("color", color)
                    
                    activity?.runOnUiThread {
                        routesFeatures.add(feature)
                        style.getSourceAs<GeoJsonSource>(ROUTES_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(routesFeatures))
                    }
                } else {
                    val lineString = LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
                    val feature = Feature.fromGeometry(lineString)
                    feature.addStringProperty("color", color)
                    activity?.runOnUiThread {
                        routesFeatures.add(feature)
                        style.getSourceAs<GeoJsonSource>(ROUTES_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(routesFeatures))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroyView() { super.onDestroyView(); mapView.onDestroy(); mapLibreMap = null }
}
