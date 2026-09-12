package com.example.buswatch.driver

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.buswatch.common.MapUtils
import com.example.buswatch.common.R as CommonR
import com.example.buswatch.driver.databinding.FragmentLiveTrackingBinding
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
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
import java.util.*

class TrackingFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentLiveTrackingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DriverViewModel by activityViewModels()
    private val db = FirebaseFirestore.getInstance()

    private var mapView: MapView? = null
    private var mapLibreMap: MapLibreMap? = null
    
    private val stopPoints = mutableListOf<GeoPoint>()

    private var isMapMaximized = false
    private var locationButtonMode = 0
    
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var lastAzimuth = 0f
    private var isFirstSensorReading = true

    private var studentAdapter: StudentAdapter? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    
    private var lastRoadRequestTime: Long = 0
    private val MIN_ROAD_REQUEST_INTERVAL = 15000L
    
    private var lastStreetRequestTime: Long = 0
    private val STREET_REQUEST_INTERVAL = 10000L

    // Constants for Sources and Layers
    private val DRIVER_SOURCE_ID = "driver-source"
    private val DRIVER_LAYER_ID = "driver-layer"
    private val STOPS_SOURCE_ID = "stops-source"
    private val STOPS_LAYER_ID = "stops-layer"
    private val ROUTE_SOURCE_ID = "route-source"
    private val ROUTE_LAYER_ID = "route-layer"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLiveTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sensorManager = context?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Initialize BottomSheet
        bottomSheetBehavior = BottomSheetBehavior.from(binding.layoutBottomInfo)
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        setupRecyclerView()
        setupUI()
        setupObservers()

        mapView = binding.mapView
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                setupStyle(style)
                setupMapInteractions()
                loadStops()
                zoomToPhoneLocation()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun zoomToPhoneLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                val cameraPosition = CameraPosition.Builder()
                    .target(latLng)
                    .zoom(17.5)
                    .bearing(0.0)
                    .tilt(0.0)
                    .build()
                mapLibreMap?.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            }
        }
    }

    private fun setupStyle(style: Style) {
        val ctx = context ?: return
        MapUtils.getScaledBitmap(ctx, CommonR.drawable.ic_bus_marker_yellow, 58, 58)?.let {
            style.addImage("bus-icon", it)
        }
        MapUtils.getScaledBitmap(ctx, CommonR.drawable.ic_stop_marker, 40, 40)?.let {
            style.addImage("stop-icon", it)
        }
        MapUtils.getScaledBitmap(ctx, CommonR.drawable.ic_stop_marker_red, 52, 52)?.let {
            style.addImage("stop-icon-red", it)
        }

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
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
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

    private fun setupRecyclerView() {
        val safeContext = context ?: return
        binding.recyclerPickup.layoutManager = LinearLayoutManager(safeContext)
        studentAdapter = StudentAdapter(
            emptyList(),
            viewModel.currentTab.value ?: "Morning",
            true,
            { student -> 
                val tab = viewModel.currentTab.value ?: "Morning"
                if (tab == "Morning") {
                    viewModel.updateStudentStatus(student.id, "On Board")
                    viewModel.sendStudentBoardingNotification(student.id, student.name)
                } else {
                    viewModel.updateStudentStatus(student.id, "Riding")
                    viewModel.sendStudentBoardingNotification(student.id, student.name)
                }
            },
            { student -> viewModel.dropOffStudent(student) },
            { student -> showStudentMedicalInfo(student) }
        )
        binding.recyclerPickup.adapter = studentAdapter
    }

    private fun setupUI() {
        binding.btnSOS.setOnClickListener { (activity as? DriverHome)?.showSOSConfirmation() }
        binding.btnEndTrip.setOnClickListener { 
            val tab = viewModel.currentTab.value ?: "Morning"
            if (tab == "Morning") (activity as? DriverHome)?.loadHome() else (activity as? DriverHome)?.loadAfternoon()
        }
        binding.btnDropAll.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), CommonR.style.Theme_BusWatch_Dialog_Rounded)
                .setTitle("Drop All Students")
                .setMessage("Are you sure you want to mark all students currently on board as dropped off?")
                .setPositiveButton("Confirm") { _, _ -> viewModel.dropAllStudents() }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        binding.btnMaximizeMap.setOnClickListener { toggleMapMaximization() }
        
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                isMapMaximized = (newState == BottomSheetBehavior.STATE_HIDDEN)
                binding.btnMaximizeMap.setImageResource(if (isMapMaximized) CommonR.drawable.ic_eye_off else CommonR.drawable.ic_eye)
                binding.bannerNextStop.visibility = if (isMapMaximized) View.GONE else View.VISIBLE
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
        
        // Header clicks for quick expand
        binding.rosterHandle.setOnClickListener {
            bottomSheetBehavior.state = if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) 
                BottomSheetBehavior.STATE_COLLAPSED else BottomSheetBehavior.STATE_EXPANDED
        }

        binding.btnMyLocation.setColorFilter(Color.BLACK)
        binding.btnMyLocation.setImageResource(CommonR.drawable.ic_my_location)
        binding.btnMyLocation.setOnClickListener {
            when (locationButtonMode) {
                0 -> enterMode1()
                1 -> enterMode2()
                2 -> enterMode1()
            }
        }
        binding.btnSortRoster.setOnClickListener { showSortPopup(it) }
    }

    private fun toggleMapMaximization() {
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        viewModel.lastKnownLocation.value?.let { updateMapCamera(it) }
    }

    private fun switchToManualMode() {
        if (locationButtonMode != 0) {
            locationButtonMode = 0
            binding.btnMyLocation.setColorFilter(Color.BLACK)
            binding.btnMyLocation.setImageResource(CommonR.drawable.ic_my_location)
            unregisterSensors()
        }
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

    private fun loadStops() {
        val route = viewModel.assignedRoute.value ?: return
        val stopIds = route.stopIds
        if (stopIds.isEmpty()) return

        var loaded = 0
        val stopPointsMap = mutableMapOf<String, GeoPoint>()
        val features = mutableListOf<Feature>()
        val currentTab = viewModel.currentTab.value ?: "Morning"
        val displayStopIds = if (currentTab == "Afternoon") stopIds.reversed() else stopIds
        
        binding.tvNextStopName.text = getString(CommonR.string.calculating)

        for ((index, sid) in displayStopIds.withIndex()) {
            db.collection("stops").document(sid).get().addOnSuccessListener { sDoc ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                val lat = sDoc.getDouble("latitude")
                val lng = sDoc.getDouble("longitude")
                if (lat != null && lng != null) {
                    val p = GeoPoint(lat, lng)
                    stopPointsMap[sid] = p
                    val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
                    feature.addStringProperty("icon", if (index == 0) "stop-icon-red" else "stop-icon")
                    feature.addStringProperty("name", sDoc.getString("name"))
                    features.add(feature)
                    if (index == 0) {
                        val stopName = sDoc.getString("name") ?: "Next Stop"
                        binding.tvNextStopName.text = stopName
                        binding.tvNextStopName.tag = sid 
                        viewModel.assignedRoute.value?.busId?.let { busId ->
                            db.collection("buses").document(busId).update("nextStop", sid)
                        }
                    }
                }
                loaded++
                if (loaded == displayStopIds.size) {
                    stopPoints.clear()
                    stopPoints.addAll(displayStopIds.mapNotNull { stopPointsMap[it] })
                    mapLibreMap?.getStyle { style ->
                        style.getSourceAs<GeoJsonSource>(STOPS_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(features))
                    }
                    viewModel.lastKnownLocation.value?.let { updateRouteWithCurrentLocation(it, force = true) }
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.assignedRoute.observe(viewLifecycleOwner) { route ->
            route?.let {
                binding.tvRouteInfo.text = String.format(Locale.getDefault(), "%s \u2022 %s", it.name, it.busNumber ?: "N/A")
                loadStops()
            }
        }
        viewModel.students.observe(viewLifecycleOwner) { students ->
            val tab = viewModel.currentTab.value ?: "Morning"
            studentAdapter?.updateStudents(students, tab)
            val totalExpected = students.count { s ->
                val ride = s.rideOption.lowercase()
                when (tab) {
                    "Morning" -> ride.contains("morning") || ride.contains("round trip")
                    "Afternoon" -> ride.contains("afternoon") || ride.contains("round trip")
                    else -> !ride.contains("not riding")
                }
            }
            val onBoardCount = students.count { it.status == "On Board" || it.status == "Riding" }
            binding.tvBoardingCount.text = getString(CommonR.string.on_board_format, onBoardCount, totalExpected)
            binding.tvRosterTitle.text = String.format(Locale.getDefault(), "STUDENT ROSTER (%d / %d)", totalExpected, students.size)
            binding.btnDropAll.visibility = if (onBoardCount > 0) View.VISIBLE else View.GONE
        }
        viewModel.lastKnownLocation.observe(viewLifecycleOwner) { gp ->
            gp?.let {
                mapLibreMap?.getStyle { style ->
                    style.getSourceAs<GeoJsonSource>(DRIVER_SOURCE_ID)?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)))
                }
                if (locationButtonMode != 0) updateMapCamera(it)
                updateRouteWithCurrentLocation(it)
                updateCurrentStreetName(it)
            }
        }
        viewModel.userRole.observe(viewLifecycleOwner) { role ->
            binding.btnEndTrip.text = getString(if (role == "Conductor") CommonR.string.exit_roster else CommonR.string.end_trip)
        }
    }

    private fun updateCurrentStreetName(gp: GeoPoint) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastStreetRequestTime < STREET_REQUEST_INTERVAL) return
        lastStreetRequestTime = currentTime
        context?.let { ctx ->
            Thread {
                try {
                    val geocoder = Geocoder(ctx, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(gp.latitude, gp.longitude, 1) { addresses ->
                            if (!addresses.isNullOrEmpty()) {
                                val street = addresses[0].thoroughfare ?: addresses[0].getAddressLine(0)?.split(",")?.firstOrNull() ?: "Unknown Street"
                                activity?.runOnUiThread { binding.tvCurrentStreet.text = street }
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(gp.latitude, gp.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val street = addresses[0].thoroughfare ?: addresses[0].getAddressLine(0)?.split(",")?.firstOrNull() ?: "Unknown Street"
                            activity?.runOnUiThread { binding.tvCurrentStreet.text = street }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }.start()
        }
    }

    private fun showStudentMedicalInfo(student: Student) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_student_medical_info, null)
        val dialog = MaterialAlertDialogBuilder(requireContext(), CommonR.style.Theme_BusWatch_Dialog_Rounded).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.tvStudentName).text = student.name
        dialogView.findViewById<TextView>(R.id.tvStudentGrade).text = student.grade
        dialogView.findViewById<TextView>(R.id.tvBloodType).text = student.bloodType
        dialogView.findViewById<TextView>(R.id.tvAllergies).text = student.allergies
        dialogView.findViewById<TextView>(R.id.tvConditions).text = student.medicalConditions
        dialogView.findViewById<TextView>(R.id.tvMedications).text = student.medications
        dialogView.findViewById<TextView>(R.id.tvEmergencyName).text = student.emergencyContact
        dialogView.findViewById<TextView>(R.id.tvEmergencyPhone).text = student.emergencyPhone
        Glide.with(this).load(student.photoUrl).circleCrop().placeholder(CommonR.drawable.ic_person_placeholder).into(dialogView.findViewById(R.id.imgStudentPhoto))
        dialogView.findViewById<View>(R.id.btnClose)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun updateMapCamera(gp: GeoPoint) {
        if (locationButtonMode == 0) return
        val latLng = LatLng(gp.latitude, gp.longitude)
        val targetZoom = if (locationButtonMode == 2) 19.0 else 17.5
        val targetTilt = if (locationButtonMode == 2) 60.0 else 0.0
        val targetBearing = if (locationButtonMode == 2) -lastAzimuth.toDouble() else 0.0
        mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(latLng).zoom(targetZoom).bearing(targetBearing).tilt(targetTilt).build()
        ), 1000)
    }

    private fun updateRouteWithCurrentLocation(currentLoc: GeoPoint, force: Boolean = false) {
        if (stopPoints.isEmpty()) return
        val currentTime = System.currentTimeMillis()
        if (!force && currentTime - lastRoadRequestTime < MIN_ROAD_REQUEST_INTERVAL) return
        lastRoadRequestTime = currentTime
        val points = mutableListOf<GeoPoint>().apply { add(currentLoc); addAll(stopPoints) }
        Thread {
            try {
                val roadManager = OSRMRoadManager(requireContext(), "BusWatch-Android-App/1.0")
                val road = roadManager.getRoad(ArrayList(points))
                if (road.mStatus == Road.STATUS_OK) {
                    val routePoints = road.mRouteHigh.map { Point.fromLngLat(it.longitude, it.latitude) }
                    val lineString = LineString.fromLngLats(routePoints)
                    val etaSeconds = if (road.mLegs.isNotEmpty()) road.mLegs[0].mDuration else road.mDuration
                    val etaText = if (etaSeconds < 60) "1" else (etaSeconds / 60).toInt().toString()
                    activity?.runOnUiThread {
                        binding.tvETA.text = String.format(Locale.getDefault(), "ETA - %s MINS", etaText)
                        mapLibreMap?.getStyle { style ->
                            style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(Feature.fromGeometry(lineString))
                        }
                        viewModel.assignedRoute.value?.busId?.let { busId ->
                            val nextStopValue = binding.tvNextStopName.tag?.toString() ?: binding.tvNextStopName.text.toString()
                            db.collection("buses").document(busId).update(mapOf("nextStop" to nextStopValue, "eta" to etaText))
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun enterMode1() {
        locationButtonMode = 1
        binding.btnMyLocation.setColorFilter("#4A90E2".toColorInt())
        binding.btnMyLocation.setImageResource(CommonR.drawable.ic_my_location)
        unregisterSensors()
        viewModel.lastKnownLocation.value?.let { updateMapCamera(it) }
    }

    private fun enterMode2() {
        locationButtonMode = 2
        isFirstSensorReading = true 
        binding.btnMyLocation.setColorFilter("#4A90E2".toColorInt())
        binding.btnMyLocation.setImageResource(CommonR.drawable.ic_compass)
        registerSensors()
        lastAzimuth = viewModel.lastBearing.value ?: 0f
        viewModel.lastKnownLocation.value?.let { updateMapCamera(it) }
    }

    private fun registerSensors() {
        rotationSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun unregisterSensors() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (locationButtonMode != 2) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (isFirstSensorReading) {
                lastAzimuth = azimuth
                isFirstSensorReading = false
            } else {
                val alpha = 0.18f
                var diff = azimuth - lastAzimuth
                while (diff < -180) diff += 360
                while (diff > 180) diff -= 360
                lastAzimuth = lastAzimuth + alpha * diff
            }
            viewModel.lastKnownLocation.value?.let { updateMapCamera(it) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun showSortPopup(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menu.add("Order By:").isEnabled = false
        popup.menu.add("Ascending (A-Z)").setOnMenuItemClickListener { viewModel.setSortMode("Name", true); true }
        popup.menu.add("Descending (Z-A)").setOnMenuItemClickListener { viewModel.setSortMode("Name", false); true }
        popup.menu.add("Default (Stop Order)").setOnMenuItemClickListener { viewModel.setSortMode("Stop", true); true }
        popup.show()
    }

    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { 
        super.onResume()
        mapView?.onResume()
        if (locationButtonMode == 2) registerSensors()
    }
    override fun onPause() { 
        super.onPause()
        mapView?.onPause()
        unregisterSensors()
    }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroyView() { 
        super.onDestroyView()
        mapView?.onDestroy()
        mapView = null
        mapLibreMap = null
        _binding = null 
    }
}
