package com.example.buswatch.admin.fragments

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.Emergency
import com.example.buswatch.admin.R
import com.example.buswatch.common.MapUtils
import com.example.buswatch.common.NotificationSender
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
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
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Locale

class EmergencyDetailFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var emergency: Emergency
    private var driverLocationListener: ListenerRegistration? = null
    
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    
    private val stopPoints = mutableListOf<GeoPoint>()
    private var locationButtonMode = 0 // 0: Manual, 1: 2D, 2: 3D
    private var lastActiveFollowMode = 1
    private var isMapMaximized = false
    private var isInternalCameraUpdate = false

    private val DRIVER_SOURCE_ID = "driver-source"
    private val DRIVER_LAYER_ID = "driver-layer"
    private val STOPS_SOURCE_ID = "stops-source"
    private val STOPS_LAYER_ID = "stops-layer"
    private val ROUTE_SOURCE_ID = "route-source"
    private val ROUTE_LAYER_ID = "route-layer"

    companion object {
        fun newInstance(emergency: Emergency) = EmergencyDetailFragment().apply {
            this.emergency = emergency
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_emergency_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnBackEmergency)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        setupDetails(view)
        
        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                setupStyle(style)
                val initialPoint = LatLng(emergency.latitude ?: 14.5995, emergency.longitude ?: 120.9842)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPoint, 17.5))
                
                setupMapInteractions()
                loadStops()
                startRealTimeTracking()
            }
        }

        view.findViewById<View>(R.id.btnResolveDetail)?.setOnClickListener { resolveEmergency() }
        view.findViewById<android.widget.ImageButton>(R.id.btnMyLocation)?.setOnClickListener {
            // Cycle: 0 -> 1, 1 -> 2, 2 -> 1
            when (locationButtonMode) {
                0 -> enterMode1()
                1 -> enterMode2()
                2 -> enterMode1()
            }
        }
        view.findViewById<android.widget.ImageButton>(R.id.btnMaximizeMap)?.setOnClickListener { toggleMapSize() }
    }

    private fun setupStyle(style: Style) {
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_bus_marker_yellow, 58, 58)?.let { style.addImage("bus-icon", it) }
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_stop_marker, 40, 40)?.let { style.addImage("stop-icon", it) }
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_stop_marker_red, 52, 52)?.let { style.addImage("stop-icon-red", it) }

        style.addSource(GeoJsonSource(DRIVER_SOURCE_ID))
        style.addSource(GeoJsonSource(STOPS_SOURCE_ID))
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))

        style.addLayer(LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor("#4A90E2".toColorInt()),
                PropertyFactory.lineWidth(8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        })

        style.addLayer(SymbolLayer(STOPS_LAYER_ID, STOPS_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage("{icon}"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true)
            )
        })

        style.addLayer(SymbolLayer(DRIVER_LAYER_ID, DRIVER_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage("bus-icon"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER)
            )
        })
    }

    private fun setupMapInteractions() {
        mapLibreMap?.addOnMapClickListener {
            switchToManualMode()
            false
        }
        mapLibreMap?.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                switchToManualMode()
            }
        }
    }

    private fun switchToManualMode() {
        if (locationButtonMode == 0 || isInternalCameraUpdate) return
        lastActiveFollowMode = locationButtonMode
        locationButtonMode = 0
        view?.findViewById<android.widget.ImageButton>(R.id.btnMyLocation)?.let { btn ->
            btn.setColorFilter(Color.BLACK)
            btn.setImageResource(CommonR.drawable.ic_my_location)
        }
    }

    private fun setupDetails(view: View) {
        val sdf = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
        view.findViewById<TextView>(R.id.tvDetailTimestamp)?.text = "Received: ${emergency.timestamp?.toDate()?.let { sdf.format(it) } ?: "Just now"}"
        view.findViewById<View>(R.id.rowDriver)?.findViewById<TextView>(R.id.tvValue)?.text = emergency.driverName
        view.findViewById<View>(R.id.rowBus)?.findViewById<TextView>(R.id.tvValue)?.text = emergency.busNumber ?: "N/A"
        view.findViewById<View>(R.id.rowRoute)?.findViewById<TextView>(R.id.tvValue)?.text = emergency.routeName ?: "N/A"
    }

    private fun loadStops() {
        val routeId = emergency.routeId ?: return
        db.collection("routes").document(routeId).get().addOnSuccessListener { rDoc ->
            val stopIds = rDoc.get("stopIds") as? List<*> ?: return@addOnSuccessListener
            if (stopIds.isEmpty()) return@addOnSuccessListener

            var loaded = 0
            val stopPointsMap = mutableMapOf<String, GeoPoint>()
            val features = mutableListOf<Feature>()

            for ((index, sidObj) in stopIds.withIndex()) {
                val sid = sidObj.toString()
                db.collection("stops").document(sid).get().addOnSuccessListener { sDoc ->
                    if (!isAdded) return@addOnSuccessListener
                    val lat = sDoc.getDouble("latitude")
                    val lng = sDoc.getDouble("longitude")
                    if (lat != null && lng != null) {
                        val p = GeoPoint(lat, lng)
                        stopPointsMap[sid] = p
                        val f = Feature.fromGeometry(Point.fromLngLat(lng, lat))
                        f.addStringProperty("icon", if (index == 0) "stop-icon-red" else "stop-icon")
                        features.add(f)
                    }
                    loaded++
                    if (loaded == stopIds.size) {
                        stopPoints.clear()
                        stopPoints.addAll(stopIds.mapNotNull { stopPointsMap[it.toString()] })
                        mapLibreMap?.getStyle { style ->
                            style.getSourceAs<GeoJsonSource>(STOPS_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(features))
                        }
                        updateRouteOverlay()
                    }
                }
            }
        }
    }

    private fun updateRouteOverlay() {
        val currentLoc = getDriverLatLng()?.let { GeoPoint(it.latitude, it.longitude) } ?: return
        
        val points = mutableListOf<GeoPoint>()
        points.add(currentLoc)
        points.addAll(stopPoints)
        if (points.size > 1) drawRoadOverlay(points)
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

    private fun enterMode1() {
        locationButtonMode = 1
        lastActiveFollowMode = 1
        view?.findViewById<android.widget.ImageButton>(R.id.btnMyLocation)?.let { btn ->
            btn.setColorFilter("#4A90E2".toColorInt())
            btn.setImageResource(CommonR.drawable.ic_my_location)
        }
        
        getDriverLatLng()?.let { target ->
            isInternalCameraUpdate = true
            mapLibreMap?.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(target).zoom(17.5).bearing(0.0).tilt(0.0).build()
            ))
            isInternalCameraUpdate = false
        }
    }

    private fun enterMode2() {
        locationButtonMode = 2
        lastActiveFollowMode = 2
        view?.findViewById<android.widget.ImageButton>(R.id.btnMyLocation)?.let { btn ->
            btn.setColorFilter("#4A90E2".toColorInt())
            btn.setImageResource(CommonR.drawable.ic_compass)
        }
        
        getDriverLatLng()?.let { target ->
            isInternalCameraUpdate = true
            mapLibreMap?.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(target).zoom(19.0).bearing(0.0).tilt(60.0).build()
            ))
            isInternalCameraUpdate = false
        }
    }

    private fun getDriverLatLng(): LatLng? {
        return mapLibreMap?.getStyle()?.getSourceAs<GeoJsonSource>(DRIVER_SOURCE_ID)?.let { source ->
            (source.querySourceFeatures(null).firstOrNull()?.geometry() as? Point)?.let {
                LatLng(it.latitude(), it.longitude())
            }
        }
    }

    private fun updateCameraToDriver() {
        if (locationButtonMode == 0) return
        getDriverLatLng()?.let { target ->
            val targetZoom = if (locationButtonMode == 2) 19.0 else 17.5
            val targetTilt = if (locationButtonMode == 2) 60.0 else 0.0

            isInternalCameraUpdate = true
            mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(target).zoom(targetZoom).tilt(targetTilt).build()
            ), 1000, object : MapLibreMap.CancelableCallback {
                override fun onCancel() { isInternalCameraUpdate = false }
                override fun onFinish() { isInternalCameraUpdate = false }
            })
        }
    }

    private fun toggleMapSize() {
        val v = view ?: return
        isMapMaximized = !isMapMaximized
        v.findViewById<View>(R.id.header_container)?.visibility = if (isMapMaximized) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.details_container)?.visibility = if (isMapMaximized) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.layout_bottom_actions)?.visibility = if (isMapMaximized) View.GONE else View.VISIBLE
        val mapContainer = v.findViewById<View>(R.id.mapContainer)
        val params = mapContainer?.layoutParams
        params?.height = if (isMapMaximized) ViewGroup.LayoutParams.MATCH_PARENT else (350 * resources.displayMetrics.density).toInt()
        mapContainer?.layoutParams = params
        v.findViewById<android.widget.ImageButton>(R.id.btnMaximizeMap)?.setImageResource(if (isMapMaximized) CommonR.drawable.ic_close else CommonR.drawable.ic_eye)
        if (locationButtonMode != 0) updateCameraToDriver()
    }

    private fun startRealTimeTracking() {
        driverLocationListener = db.collection("drivers").document(emergency.driverId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val lat = snapshot.getDouble("latitude")
                val lng = snapshot.getDouble("longitude")
                if (lat != null && lng != null) {
                    mapLibreMap?.getStyle { style ->
                        style.getSourceAs<GeoJsonSource>(DRIVER_SOURCE_ID)?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lng, lat)))
                    }
                    updateCameraToDriver()
                    updateRouteOverlay()
                }
            }
    }

    private fun resolveEmergency() {
        db.collection("emergencies").document(emergency.id).update("status", "resolved").addOnSuccessListener {
            if (isAdded) {
                notifyParentsResolved()
                parentFragmentManager.setFragmentResult("emergency_request", Bundle().apply { putBoolean("refresh", true) })
                Toast.makeText(requireContext(), "Emergency resolved", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun notifyParentsResolved() {
        val routeId = emergency.routeId ?: return
        val busLabel = emergency.busNumber ?: emergency.routeName ?: "your child's bus"
        db.collection("routes").document(routeId).get().addOnSuccessListener { rDoc ->
            val stopIds = rDoc.get("stopIds") as? List<*> ?: return@addOnSuccessListener
            if (stopIds.isEmpty()) return@addOnSuccessListener
            val chunks = stopIds.map { it.toString() }.chunked(30)
            for (chunk in chunks) {
                db.collection("parents").whereIn("child.stop", chunk).get().addOnSuccessListener { snapshots ->
                    for (doc in snapshots) {
                        val title = "✅ SOS Resolved"
                        val message = "The emergency alert for Bus $busLabel has been resolved. The situation is under control."
                        val notifData = hashMapOf("title" to title, "message" to message, "timestamp" to FieldValue.serverTimestamp(), "isRead" to false, "type" to "sos_resolved")
                        db.collection("parents").document(doc.id).collection("notifications").add(notifData)
                        NotificationSender.sendNotification(doc.id, title, message)
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroyView() { super.onDestroyView(); driverLocationListener?.remove(); mapView.onDestroy(); mapLibreMap = null }
}
