package com.example.buswatch.admin.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.*
import com.example.buswatch.common.MapUtils
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
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

class RouteDetailFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var route: RouteAdmin
    private var onBack: (() -> Unit)? = null
    private var isMaximized = false
    private var firstStopPoint: LatLng? = null
    
    private var routeListener: ListenerRegistration? = null
    private var occupancyListener: ListenerRegistration? = null
    private val assignedStudents = mutableListOf<AssignedStudent>()
    
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null

    private val STOPS_SOURCE_ID = "stops-source"
    private val STOPS_LAYER_ID = "stops-layer"
    private val ROUTE_SOURCE_ID = "route-source"
    private val ROUTE_LAYER_ID = "route-layer"

    companion object {
        fun newInstance(route: RouteAdmin, onBack: () -> Unit) = RouteDetailFragment().apply {
            this.route = route
            this.onBack = onBack
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_view_route, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ImageButton>(R.id.btnBackRouteDetail)?.setOnClickListener { onBack?.invoke() }
        
        mapView = view.findViewById(R.id.mapRouteView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) { style ->
                setupStyle(style)
                startRouteListener(view)
            }
        }
        
        val rv = view.findViewById<RecyclerView>(R.id.recyclerRouteStudents)
        rv?.layoutManager = LinearLayoutManager(requireContext())
        
        setupMapControls(view)
    }

    private fun setupStyle(style: Style) {
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_stop_marker, 36, 36)?.let {
            style.addImage("stop-icon", it)
        }
        style.addSource(GeoJsonSource(STOPS_SOURCE_ID))
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))

        style.addLayer(LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor("#4A90E2".toColorInt()),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        })

        style.addLayer(SymbolLayer(STOPS_LAYER_ID, STOPS_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage("stop-icon"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
            )
        })
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroyView() {
        routeListener?.remove()
        occupancyListener?.remove()
        mapView.onDestroy()
        mapLibreMap = null
        super.onDestroyView()
    }

    private fun setupMapControls(view: View) {
        val btnMaximize = view.findViewById<ImageButton>(R.id.btnMaximizeMap)
        val btnRecenter = view.findViewById<ImageButton>(R.id.btnRecenterRoute)
        val mapContainer = view.findViewById<FrameLayout>(R.id.mapContainer)

        btnMaximize?.setOnClickListener {
            isMaximized = !isMaximized
            val params = mapContainer?.layoutParams
            if (isMaximized) {
                params?.height = (500 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_close)
            } else {
                params?.height = (300 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            }
            mapContainer?.layoutParams = params
            mapView.postDelayed({
                firstStopPoint?.let { mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLng(it)) }
            }, 200)
        }

        btnRecenter?.setOnClickListener {
            firstStopPoint?.let {
                mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 16.0))
            }
        }
    }

    private fun startRouteListener(view: View) {
        routeListener?.remove()
        routeListener = db.collection("routes").document(route.id).addSnapshotListener { doc, e ->
            if (e != null || doc == null || !doc.exists()) return@addSnapshotListener
            
            val routeName = doc.getString("routeName") ?: "N/A"
            val busId = doc.getString("busId")
            val driverId = doc.getString("driverId")
            val conductorId = doc.getString("conductorId")
            val maxCapacity = doc.getLong("maxCapacity")?.toInt() ?: 0
            
            view.findViewById<TextView>(R.id.tvMorningStart).text = doc.getString("morningStartTime") ?: "---"
            view.findViewById<TextView>(R.id.tvMorningEnd).text = doc.getString("morningEndTime") ?: "---"
            view.findViewById<TextView>(R.id.tvAfternoonStart).text = doc.getString("afternoonStartTime") ?: "---"
            view.findViewById<TextView>(R.id.tvAfternoonEnd).text = doc.getString("afternoonEndTime") ?: "---"

            @Suppress("UNCHECKED_CAST")
            val stopIds = doc.get("stopIds") as? List<String> ?: emptyList()
            view.findViewById<TextView>(R.id.tvRouteName).text = routeName
            
            if (driverId != null) {
                db.collection("drivers").document(driverId).get().addOnSuccessListener { dDoc ->
                    if (!isAdded) return@addOnSuccessListener
                    val fullName = "${dDoc.getString("firstName") ?: ""} ${dDoc.getString("lastName") ?: ""}".trim()
                    view.findViewById<TextView>(R.id.tvDriverName).text = fullName.ifEmpty { "N/A" }
                }
            } else view.findViewById<TextView>(R.id.tvDriverName).text = "Not Assigned"

            if (busId != null) {
                db.collection("buses").document(busId).get().addOnSuccessListener { bDoc ->
                    if (!isAdded) return@addOnSuccessListener
                    view.findViewById<TextView>(R.id.tvBusNumber).text = bDoc.getString("busNumber") ?: "N/A"
                }
            } else view.findViewById<TextView>(R.id.tvBusNumber).text = "Not Assigned"
            
            if (conductorId != null) {
                db.collection("conductors").document(conductorId).get().addOnSuccessListener { cDoc ->
                    if (!isAdded) return@addOnSuccessListener
                    val fullName = "${cDoc.getString("firstName") ?: ""} ${cDoc.getString("lastName") ?: ""}".trim()
                    view.findViewById<TextView>(R.id.tvConductorName).text = fullName.ifEmpty { "N/A" }
                }
            } else view.findViewById<TextView>(R.id.tvConductorName).text = "Not Assigned"

            startOccupancyListener(view, stopIds, maxCapacity)
            loadMapData(stopIds)
        }
    }

    private fun startOccupancyListener(view: View, stopIds: List<String>, maxCapacity: Int) {
        occupancyListener?.remove()
        if (stopIds.isEmpty()) {
            updateOccupancyUI(view, 0, maxCapacity)
            assignedStudents.clear()
            view.findViewById<RecyclerView>(R.id.recyclerRouteStudents)?.adapter = AssignedStudentAdapter(emptyList())
            return
        }

        occupancyListener = db.collection("parents")
            .whereIn("child.stop", stopIds)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null || !isAdded) return@addSnapshotListener
                
                assignedStudents.clear()
                for (doc in snapshots) {
                    @Suppress("UNCHECKED_CAST")
                    val child = doc.get("child") as? Map<String, Any>
                    if (child != null && stopIds.contains(child["stop"] as? String)) addStudentToList(doc.id, child)

                    @Suppress("UNCHECKED_CAST")
                    val additional = doc.get("children") as? List<Map<String, Any>>
                    additional?.forEach { c -> if (stopIds.contains(c["stop"] as? String)) addStudentToList(doc.id, c) }
                }
                updateOccupancyUI(view, assignedStudents.size, maxCapacity)
                view.findViewById<RecyclerView>(R.id.recyclerRouteStudents)?.adapter = AssignedStudentAdapter(assignedStudents)
            }
    }

    private fun addStudentToList(parentId: String, data: Map<String, Any>) {
        val name = "${data["firstName"] ?: ""} ${data["lastName"] ?: ""}".trim()
        val grade = data["grade"] as? String ?: "N/A"
        val photoUrl = (data["childAvatarUrl"] as? String) ?: (data["avatarUrl"] as? String) ?: ""
        assignedStudents.add(AssignedStudent(parentId, name, grade, photoUrl))
    }

    private fun updateOccupancyUI(view: View, currentOccupancy: Int, maxCapacity: Int) {
        val tvStats = view.findViewById<TextView>(R.id.tvCapacityStats)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressCapacity)
        tvStats.text = "$currentOccupancy / $maxCapacity Students"
        if (maxCapacity > 0) {
            progressBar.max = maxCapacity
            progressBar.progress = currentOccupancy
            tvStats.setTextColor(if (currentOccupancy > maxCapacity) Color.RED else Color.BLACK)
        }
    }

    private fun loadMapData(stopIds: List<String>) {
        if (stopIds.isEmpty()) return
        
        var loadedCount = 0
        val stopPointsMap = mutableMapOf<String, GeoPoint>()
        val features = mutableListOf<Feature>()
        val bounds = LatLngBounds.Builder()

        for (stopId in stopIds) {
            db.collection("stops").document(stopId).get().addOnSuccessListener { sDoc ->
                if (!isAdded) return@addOnSuccessListener
                val lat = sDoc.getDouble("latitude")
                val lng = sDoc.getDouble("longitude")
                if (lat != null && lng != null) {
                    val gp = GeoPoint(lat, lng)
                    stopPointsMap[stopId] = gp
                    val ll = LatLng(lat, lng)
                    bounds.include(ll)
                    features.add(Feature.fromGeometry(Point.fromLngLat(lng, lat)))
                }
                loadedCount++
                
                if (loadedCount == stopIds.size) {
                    val orderedGeoPoints = stopIds.mapNotNull { stopPointsMap[it] }
                    if (orderedGeoPoints.isNotEmpty()) {
                        firstStopPoint = LatLng(orderedGeoPoints[0].latitude, orderedGeoPoints[0].longitude)
                        
                        mapLibreMap?.getStyle { style ->
                            style.getSourceAs<GeoJsonSource>(STOPS_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(features))
                            if (orderedGeoPoints.size > 1) drawRoadOverlay(orderedGeoPoints)
                        }
                        
                        try {
                            mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
                        } catch (e: Exception) {
                            firstStopPoint?.let { mapLibreMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 14.0)) }
                        }
                    }
                }
            }
        }
    }

    private fun drawRoadOverlay(points: List<GeoPoint>) {
        val ctx = context?.applicationContext ?: return
        Thread {
            try {
                val roadManager = OSRMRoadManager(ctx, "BusWatch-Android-App/1.0")
                val road = roadManager.getRoad(ArrayList(points))
                if (road.mStatus == Road.STATUS_OK) {
                    val routePoints = road.mRouteHigh.map { Point.fromLngLat(it.longitude, it.latitude) }
                    val lineString = LineString.fromLngLats(routePoints)
                    activity?.runOnUiThread {
                        mapLibreMap?.getStyle { style ->
                            style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(Feature.fromGeometry(lineString))
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }
}
