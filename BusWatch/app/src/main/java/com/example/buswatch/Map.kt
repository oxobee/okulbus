package com.example.buswatch

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.example.buswatch.common.MapUtils
import com.example.buswatch.common.NotificationSender
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
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
import java.util.ArrayList
import java.util.Locale
import kotlin.collections.Map as KMap

class Map : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var childName: String? = null
    private var busListener: ListenerRegistration? = null
    private var isMapMaximized = false

    private var stopIds = mutableListOf<String>()
    private val stopPoints = mutableListOf<GeoPoint>()
    private var routeName: String = "Route"

    private var locationButtonMode = 0
    private var lastActiveFollowMode = 1
    private var isInternalCameraUpdate = false
    private var lastKnownBusLocation: GeoPoint? = null
    private var lastKnownBusBearing = 0f
    private var lastAnimatedLocation: GeoPoint? = null
    private var isFirstBusUpdate = true
    private var isInitialZoomLevelSet = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastRoadRequestTime = 0L
    private val MIN_ROAD_REQUEST_INTERVAL = 15000L
    private val MIN_DISTANCE_CHANGE_METERS = 1.0

    private var studentStopId: String? = null
    private var studentStopLocation: GeoPoint? = null
    private var hasShownProximityAlert = false
    private var isNearStopNotifEnabled = true

    // Layer and Source IDs
    private val BUS_SOURCE_ID = "bus-source"
    private val BUS_LAYER_ID = "bus-layer"
    private val STOPS_SOURCE_ID = "stops-source"
    private val STOPS_LAYER_ID = "stops-layer"
    private val ROUTE_SOURCE_ID = "route-source"
    private val ROUTE_LAYER_ID = "route-layer"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        setContentView(R.layout.map)
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        childName = intent.getStringExtra("childName")

        findViewById<TextView>(R.id.tvChildName)?.text = childName ?: getString(CommonR.string.student)
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }

        val placeholder = getString(CommonR.string.placeholder_hyphen)
        findViewById<TextView>(R.id.tvNextStopName)?.text = placeholder
        findViewById<TextView>(R.id.tvDriverName)?.text = placeholder
        findViewById<TextView>(R.id.tvBusStatus)?.text = "Bus Driver \u2022 $placeholder"
        findViewById<TextView>(R.id.tvRouteInfo)?.text = getString(CommonR.string.route_bus_info_format, routeName, placeholder)
        findViewById<TextView>(R.id.tvETA)?.text = placeholder
        findViewById<ImageView>(R.id.ivDriverAvatar)?.setImageResource(CommonR.drawable.ic_person_placeholder)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.isCompassEnabled = false

            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                setupStyle(style)
                val defaultCenter = LatLng(14.717, 121.038)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultCenter, 14.0))

                setupMapInteractions()
                fetchInitialData()
                loadNotificationPreferences()
            }
        }

        setupMaximizeButton()
        setupLocatorButton()

        findViewById<View>(R.id.btnMapBusDetails)?.setOnClickListener {
            val intent = android.content.Intent(this, BusDetails::class.java)
            intent.putExtra("childName", childName)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(CommonR.anim.slide_in_bottom, CommonR.anim.stay)
        }
    }

    private fun setupStyle(style: Style) {
        MapUtils.getScaledBitmap(this, CommonR.drawable.ic_bus_marker_yellow, 58, 58)?.let {
            style.addImage("bus-icon", it)
        }
        MapUtils.getScaledBitmap(this, CommonR.drawable.ic_stop_marker, 36, 36)?.let {
            style.addImage("stop-icon", it)
        }
        MapUtils.getScaledBitmap(this, CommonR.drawable.ic_stop_marker_red, 40, 40)?.let {
            style.addImage("stop-icon-red", it)
        }

        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
        style.addSource(GeoJsonSource(STOPS_SOURCE_ID))
        style.addSource(GeoJsonSource(BUS_SOURCE_ID))

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
                PropertyFactory.iconImage("{icon}"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
            )
        })

        style.addLayer(SymbolLayer(BUS_LAYER_ID, BUS_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage("bus-icon"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER)
            )
        })
    }

    private fun switchToManualMode() {
        if (locationButtonMode == 0 || isInternalCameraUpdate) return
        lastActiveFollowMode = locationButtonMode
        locationButtonMode = 0
        val button = findViewById<ImageButton>(R.id.btnMyLocation)
        button?.setColorFilter(Color.BLACK)
        button?.setImageResource(CommonR.drawable.ic_my_location)
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

    private fun setupLocatorButton() {
        val button = findViewById<ImageButton>(R.id.btnMyLocation)
        button?.setOnClickListener {
            if (lastKnownBusLocation == null) {
                Toast.makeText(this, "Bus location not available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Cycle: 0 -> 1, 1 -> 2, 2 -> 1
            when (locationButtonMode) {
                0 -> enterMode1()
                1 -> enterMode2()
                2 -> enterMode1()
            }
        }
    }

    private fun animateButton(view: View?) {
        view?.animate()
            ?.scaleX(0.8f)
            ?.scaleY(0.8f)
            ?.setDuration(100)
            ?.withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            ?.start()
    }

    private fun enterMode1() {
        locationButtonMode = 1
        lastActiveFollowMode = 1
        val button = findViewById<ImageButton>(R.id.btnMyLocation)
        button?.setImageResource(CommonR.drawable.ic_my_location)
        button?.setColorFilter("#4A90E2".toColorInt())
        animateButton(button)

        lastKnownBusLocation?.let { gp ->
            updateMapCamera(gp, lastKnownBusBearing)
        }
    }

    private fun enterMode2() {
        locationButtonMode = 2
        lastActiveFollowMode = 2
        val button = findViewById<ImageButton>(R.id.btnMyLocation)
        button?.setImageResource(CommonR.drawable.ic_compass)
        button?.setColorFilter("#4A90E2".toColorInt())
        animateButton(button)

        lastKnownBusLocation?.let { gp ->
            updateMapCamera(gp, lastKnownBusBearing)
        }
    }

    private fun setupMaximizeButton() {
        val button = findViewById<ImageButton>(R.id.btnMaximizeMap)
        val banner = findViewById<View>(R.id.bannerNextStop)
        val bottomInfo = findViewById<View>(R.id.layoutBottomInfo)
        val avatar = findViewById<View>(R.id.cvAvatarContainer)

        button?.setOnClickListener {
            isMapMaximized = !isMapMaximized

            val translationBanner = if (isMapMaximized) -banner!!.height.toFloat() else 0f
            val translationBottom = if (isMapMaximized) bottomInfo!!.height.toFloat() else 0f
            val alpha = if (isMapMaximized) 0f else 1f

            banner?.animate()?.translationY(translationBanner)?.alpha(alpha)?.setDuration(300)?.start()
            bottomInfo?.animate()?.translationY(translationBottom)?.alpha(alpha)?.setDuration(300)?.start()
            avatar?.animate()?.scaleX(alpha)?.scaleY(alpha)?.alpha(alpha)?.setDuration(300)?.start()

            button.animate().rotation(if (isMapMaximized) 180f else 0f).setDuration(300).start()
            button.setImageResource(if (isMapMaximized) CommonR.drawable.ic_close else CommonR.drawable.ic_eye)

            lastKnownBusLocation?.let { updateMapCamera(it, lastKnownBusBearing) }
        }
    }

    private fun fetchInitialData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            if (isFinishing || !doc.exists()) return@addOnSuccessListener

            val searchName = childName?.trim()
            @Suppress("UNCHECKED_CAST")
            val childrenList = doc.get("children") as? List<KMap<String, Any>>
            @Suppress("UNCHECKED_CAST")
            val singleChild = doc.get("child") as? KMap<String, Any>

            var targetChild: KMap<String, Any>? = null

            if (singleChild != null) {
                val fullName = "${singleChild["firstName"]} ${singleChild["lastName"]}".trim()
                if (searchName == null || fullName.equals(searchName, ignoreCase = true)) {
                    targetChild = singleChild
                }
            }

            if (targetChild == null && childrenList != null) {
                targetChild = childrenList.find {
                    val fullName = "${it["firstName"]} ${it["lastName"]}".trim()
                    fullName.equals(searchName, ignoreCase = true)
                }
            }

            targetChild?.let { child ->
                val stopId = child["stop"] as? String
                studentStopId = stopId
                if (!stopId.isNullOrEmpty()) fetchRouteForStop(stopId)
            }
        }.addOnFailureListener {
            Log.e("FIREBASE_MAP", "Error fetching parent data", it)
        }
    }

    private fun fetchRouteForStop(stopId: String) {
        db.collection("routes")
            .whereArrayContains("stopIds", stopId)
            .whereEqualTo("status", "Active")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshots ->
                if (isFinishing || snapshots.isEmpty) return@addOnSuccessListener
                val routeDoc = snapshots.documents[0]
                routeName = routeDoc.getString("routeName") ?: routeDoc.getString("name") ?: "Route"
                @Suppress("UNCHECKED_CAST")
                val sIds = (routeDoc.get("stopIds") as? List<String>) ?: emptyList()
                stopIds = sIds.toMutableList()

                val busId = routeDoc.getString("busId") ?: ""
                val driverId = routeDoc.getString("driverId")
                val conductorId = routeDoc.getString("conductorId")

                if (!driverId.isNullOrEmpty()) {
                    fetchStaffInfo(driverId, "drivers")
                } else if (!conductorId.isNullOrEmpty()) {
                    fetchStaffInfo(conductorId, "conductors")
                }

                if (busId.isNotEmpty()) {
                    startRealTimeBusUpdates(busId)
                }
                loadRouteOnMap()
            }.addOnFailureListener {
                Log.e("FIREBASE_MAP", "Error fetching active route", it)
            }
    }

    private fun fetchStaffInfo(staffId: String, collection: String) {
        db.collection(collection).document(staffId).get().addOnSuccessListener { dDoc ->
            if (isFinishing || !dDoc.exists()) return@addOnSuccessListener
            val name = "${dDoc.getString("firstName")} ${dDoc.getString("lastName")}".trim()
            val placeholder = getString(CommonR.string.placeholder_hyphen)
            findViewById<TextView>(R.id.tvDriverName)?.text = if (name.isEmpty()) placeholder else name
            val avatarUrl = dDoc.getString("photoUrl") ?: dDoc.getString("driverAvatar")
            findViewById<ImageView>(R.id.ivDriverAvatar)?.let {
                Glide.with(this).load(avatarUrl).placeholder(CommonR.drawable.ic_person_placeholder).circleCrop().into(it)
            }
        }.addOnFailureListener {
            Log.e("FIREBASE_MAP", "Error fetching staff info", it)
        }
    }

    private fun loadRouteOnMap() {
        if (stopIds.isEmpty()) return

        val localPointsMap = mutableMapOf<String, GeoPoint>()
        val localFeaturesMap = mutableMapOf<String, Feature>()
        val localNamesMap = mutableMapOf<String, String>()

        // Optimized: Fetch stops in batches using whereIn to reduce latency
        val chunks = stopIds.chunked(30)
        var completedChunks = 0

        for (chunk in chunks) {
            db.collection("stops")
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .addOnSuccessListener { snapshots ->
                    for (doc in snapshots) {
                        val lat = doc.getDouble("latitude")
                        val lng = doc.getDouble("longitude")
                        val stopName = doc.getString("name") ?: "Stop"
                        if (lat != null && lng != null) {
                            val point = GeoPoint(lat, lng)
                            localPointsMap[doc.id] = point
                            localNamesMap[doc.id] = stopName

                            val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
                            feature.addStringProperty("icon", if (doc.id == studentStopId) "stop-icon-red" else "stop-icon")
                            localFeaturesMap[doc.id] = feature

                            if (doc.id == studentStopId) studentStopLocation = point
                        }
                    }

                    completedChunks++
                    if (completedChunks == chunks.size) {
                        finalizeRouteData(localPointsMap, localFeaturesMap, localNamesMap)
                    }
                }
                .addOnFailureListener {
                    Log.e("FIREBASE_MAP", "Error batch fetching stops", it)
                    completedChunks++
                    if (completedChunks == chunks.size) {
                        finalizeRouteData(localPointsMap, localFeaturesMap, localNamesMap)
                    }
                }
        }
    }

    private fun finalizeRouteData(
        pointsMap: kotlin.collections.Map<String, GeoPoint>,
        featuresMap: kotlin.collections.Map<String, Feature>,
        namesMap: kotlin.collections.Map<String, String>
    ) {
        if (isFinishing) return
        val placeholder = getString(CommonR.string.placeholder_hyphen)

        val orderedPoints = stopIds.mapNotNull { pointsMap[it] }
        val orderedFeatures = stopIds.mapNotNull { featuresMap[it] }

        stopPoints.clear()
        stopPoints.addAll(orderedPoints)
        updateRouteWithBusLocation(force = true)

        val studentStopName = if (studentStopId != null) namesMap[studentStopId] else null
        val firstStopName = if (stopIds.isNotEmpty()) namesMap[stopIds.first()] else null
        val nextStopName = studentStopName ?: firstStopName ?: placeholder

        runOnUiThread {
            findViewById<TextView>(R.id.tvNextStopName)?.text = nextStopName
            mapLibreMap?.getStyle { style ->
                style.getSourceAs<GeoJsonSource>(STOPS_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(orderedFeatures))
            }
        }
    }

    private fun updateRouteWithBusLocation(force: Boolean = false) {
        val busLoc = lastKnownBusLocation ?: return
        if (stopPoints.isEmpty()) return
        val currentTime = System.currentTimeMillis()
        if (!force && currentTime - lastRoadRequestTime < MIN_ROAD_REQUEST_INTERVAL) {
            checkProximity(busLoc)
            return
        }
        lastRoadRequestTime = currentTime
        val points = mutableListOf<GeoPoint>().apply {
            add(busLoc)
            addAll(stopPoints)
        }
        if (points.size >= 2) drawRoadOverlay(points)
        checkProximity(busLoc)
    }

    private fun checkProximity(busLoc: GeoPoint) {
        if (!isNearStopNotifEnabled || hasShownProximityAlert) return
        studentStopLocation?.let { stopLoc ->
            if (busLoc.distanceToAsDouble(stopLoc) < 500) {
                hasShownProximityAlert = true
                sendNearStopNotification()
            }
        }
    }

    private fun sendNearStopNotification() {
        val uid = auth.currentUser?.uid ?: return
        val title = getString(CommonR.string.bus_near_stop)
        val message = getString(CommonR.string.bus_near_stop_message)
        val notificationData = hashMapOf(
            "title" to title,
            "message" to message,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "type" to "bus_near_stop"
        )
        db.collection("parents").document(uid).collection("notifications").add(notificationData)
            .addOnSuccessListener { NotificationSender.sendNotification(uid, title, message) }
    }

    private fun drawRoadOverlay(points: List<GeoPoint>) {
        Thread {
            try {
                val roadManager = OSRMRoadManager(this, "BusWatch-Android-App/1.0")
                val road = roadManager.getRoad(ArrayList(points))
                if (road.mStatus == Road.STATUS_OK) {
                    val durationInMinutes = (road.mDuration / 60).toInt()
                    val etaText = if (durationInMinutes < 1) "1" else durationInMinutes.toString()

                    val routePoints = road.mRouteHigh.map { Point.fromLngLat(it.longitude, it.latitude) }
                    val lineString = LineString.fromLngLats(routePoints)

                    runOnUiThread {
                        findViewById<TextView>(R.id.tvETA)?.text = String.format(Locale.getDefault(), "ETA - %s MINS", etaText)
                        updateRouteSource(lineString)
                    }
                } else {
                    drawSimplePolyline(points)
                }
            } catch (e: Exception) {
                drawSimplePolyline(points)
            }
        }.start()
    }

    private fun drawSimplePolyline(points: List<GeoPoint>) {
        val routePoints = points.map { Point.fromLngLat(it.longitude, it.latitude) }
        val lineString = LineString.fromLngLats(routePoints)
        runOnUiThread {
            findViewById<TextView>(R.id.tvETA)?.text = getString(CommonR.string.placeholder_hyphen)
            updateRouteSource(lineString)
        }
    }

    private fun updateRouteSource(lineString: LineString) {
        mapLibreMap?.getStyle { style ->
            style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(Feature.fromGeometry(lineString))
        }
    }

    private fun startRealTimeBusUpdates(busId: String) {
        busListener?.remove()
        busListener = db.collection("buses").document(busId).addSnapshotListener { doc, error ->
            if (isFinishing || error != null || doc == null || !doc.exists()) return@addSnapshotListener

            val busNo = doc.getString("busNumber") ?: getString(CommonR.string.placeholder_hyphen)
            findViewById<TextView>(R.id.tvRouteInfo)?.text = getString(CommonR.string.route_bus_info_format, routeName, busNo)
            findViewById<TextView>(R.id.tvBusStatus)?.text = "Bus Driver \u2022 $busNo"

            val lat = doc.getDouble("latitude")
            val lng = doc.getDouble("longitude")
            val bearing = doc.getDouble("bearing")?.toFloat() ?: 0f

            if (lat != null && lng != null) {
                val busPoint = GeoPoint(lat, lng)
                lastKnownBusLocation = busPoint
                lastKnownBusBearing = bearing

                mapLibreMap?.getStyle { style ->
                    val source = style.getSourceAs<GeoJsonSource>(BUS_SOURCE_ID)
                    source?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lng, lat)))
                }

                if (locationButtonMode != 0) updateMapCamera(busPoint, bearing)

                if (isFirstBusUpdate) {
                    updateRouteWithBusLocation(force = true)
                    isFirstBusUpdate = false
                } else {
                    updateRouteWithBusLocation()
                }
            }
        }
    }

    private fun updateMapCamera(gp: GeoPoint, bearing: Float = 0f) {
        if (locationButtonMode == 0) return

        val latLng = LatLng(gp.latitude, gp.longitude)
        val targetZoom = if (locationButtonMode == 2) 19.0 else 17.5
        val targetTilt = if (locationButtonMode == 2) 60.0 else 0.0

        // Use driver's bearing for Mode 2 if available
        val targetBearing = if (locationButtonMode == 2) bearing.toDouble() else 0.0

        isInternalCameraUpdate = true
        mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(latLng)
                .zoom(targetZoom)
                .bearing(targetBearing)
                .tilt(targetTilt)
                .build()
        ), 1000, object : MapLibreMap.CancelableCallback {
            override fun onCancel() { isInternalCameraUpdate = false }
            override fun onFinish() { isInternalCameraUpdate = false }
        })
    }

    private fun loadNotificationPreferences() {
        auth.currentUser?.uid?.let { uid ->
            db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
                if (isFinishing) return@addOnSuccessListener
                @Suppress("UNCHECKED_CAST")
                val prefs = doc.get("notificationPreferences") as? KMap<String, Any>
                isNearStopNotifEnabled = (prefs?.get("busNearStop") as? Boolean) ?: true
            }
        }
    }

    override fun onStart() { super.onStart(); if (::mapView.isInitialized) mapView.onStart() }
    override fun onResume() { super.onResume(); if (::mapView.isInitialized) mapView.onResume() }
    override fun onPause() { if (::mapView.isInitialized) mapView.onPause(); super.onPause() }
    override fun onStop() { if (::mapView.isInitialized) mapView.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { if (::mapView.isInitialized) mapView.onSaveInstanceState(outState); super.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); if (::mapView.isInitialized) mapView.onLowMemory() }
    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        busListener?.remove()
        if (::mapView.isInitialized) mapView.onDestroy()
        super.onDestroy()
    }
}
