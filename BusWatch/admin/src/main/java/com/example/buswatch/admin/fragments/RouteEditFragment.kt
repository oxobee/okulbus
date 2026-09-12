package com.example.buswatch.admin.fragments

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.RouteAdmin
import com.example.buswatch.common.MapUtils
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import org.maplibre.android.MapLibre
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
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import java.util.ArrayList
import java.util.Calendar
import java.util.Locale

class RouteEditFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var route: RouteAdmin
    
    private var selectedDriverId: String? = null
    private var selectedBusId: String? = null
    private var selectedConductorId: String? = null
    private var selectedStopIds = mutableListOf<String>()
    private var busCapacity = 0
    private var isMaximized = false

    private var morningStartTime: String = ""
    private var morningEndTime: String = ""
    private var afternoonStartTime: String = ""
    private var afternoonEndTime: String = ""
    
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    
    private val STOPS_SOURCE_ID = "stops-source"
    private val STOPS_LAYER_ID = "stops-layer"
    private val ROUTE_SOURCE_ID = "route-source"
    private val ROUTE_LAYER_ID = "route-layer"

    private var stopFeatures = mutableListOf<Feature>()

    companion object {
        fun newInstance(route: RouteAdmin) = RouteEditFragment().apply {
            this.route = route
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit_route, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        busCapacity = route.maxCapacity
        
        mapView = view.findViewById(R.id.mapRoutePicker)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                setupStyle(style)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(14.7566, 121.0450), 15.0))
                
                setupUI(view, style)
                fetchRouteData(view, style)
                
                map.addOnMapClickListener { latLng ->
                    val point = map.projection.toScreenLocation(latLng)
                    val features = map.queryRenderedFeatures(point, STOPS_LAYER_ID)
                    if (features.isNotEmpty()) {
                        val stopId = features[0].getStringProperty("id") ?: ""
                        toggleStopSelection(stopId, style, view.findViewById(R.id.tvSelectedStopsCount))
                        true
                    } else false
                }
            }
        }
    }

    private fun setupStyle(style: Style) {
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_stop_marker, 36, 36)?.let {
            style.addImage("unselected-icon", it)
        }
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_stop_marker_red, 48, 48)?.let {
            style.addImage("selected-icon", it)
        }

        style.addSource(GeoJsonSource(STOPS_SOURCE_ID))
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))

        style.addLayer(LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#4A90E2")),
                PropertyFactory.lineWidth(6f),
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
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI(view: View, style: Style) {
        view.findViewById<ImageButton>(R.id.btnBackEditRoute)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.loadRouting()
        }

        val etRouteName = view.findViewById<EditText>(R.id.etRouteName)
        val tvSelectedDriver = view.findViewById<TextView>(R.id.tvSelectedDriver)
        val tvSelectedBus = view.findViewById<TextView>(R.id.tvSelectedBus)
        val tvBusCapacity = view.findViewById<TextView>(R.id.tvBusCapacity)
        val tvSelectedConductor = view.findViewById<TextView>(R.id.tvSelectedConductor)
        val btnMaximize = view.findViewById<ImageButton>(R.id.btnMaximizeMap)
        val btnMyLocation = view.findViewById<ImageButton>(R.id.btnMyLocation)

        val tvMorningStart = view.findViewById<TextView>(R.id.tvMorningStart)
        val tvMorningEnd = view.findViewById<TextView>(R.id.tvMorningEnd)
        val tvAfternoonStart = view.findViewById<TextView>(R.id.tvAfternoonStart)
        val tvAfternoonEnd = view.findViewById<TextView>(R.id.tvAfternoonEnd)

        val headerBg = view.findViewById<View>(R.id.headerBg)
        val layoutRouteBasics = view.findViewById<View>(R.id.cardRouteBasics)
        val mapContainer = view.findViewById<FrameLayout>(R.id.mapContainer)

        etRouteName.setText(route.routeName)
        tvBusCapacity.text = if (busCapacity > 0) "$busCapacity Seats" else "-"

        tvMorningStart?.setOnClickListener { showTimePicker { time -> morningStartTime = time; tvMorningStart.text = time } }
        tvMorningEnd?.setOnClickListener { showTimePicker { time -> morningEndTime = time; tvMorningEnd.text = time } }
        tvAfternoonStart?.setOnClickListener { showTimePicker { time -> afternoonStartTime = time; tvAfternoonStart.text = time } }
        tvAfternoonEnd?.setOnClickListener { showTimePicker { time -> afternoonEndTime = time; tvAfternoonEnd.text = time } }

        view.findViewById<FrameLayout>(R.id.btnDriverDropdown).setOnClickListener { showDriverPicker(tvSelectedDriver) }
        view.findViewById<FrameLayout>(R.id.btnBusDropdown).setOnClickListener { showBusPicker(tvSelectedBus, tvBusCapacity) }
        view.findViewById<FrameLayout>(R.id.btnConductorDropdown).setOnClickListener { showConductorPicker(tvSelectedConductor) }

        btnMaximize?.setOnClickListener {
            isMaximized = !isMaximized
            headerBg.isVisible = !isMaximized
            layoutRouteBasics.isVisible = !isMaximized
            
            val params = mapContainer.layoutParams
            if (isMaximized) {
                params?.height = (500 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_close)
            } else {
                params?.height = (250 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            }
            mapContainer.layoutParams = params
        }

        btnMyLocation?.setOnClickListener {
            if (selectedStopIds.isNotEmpty()) {
                val randomStopId = selectedStopIds.random()
                val feature = stopFeatures.find { it.getStringProperty("id") == randomStopId }
                (feature?.geometry() as? Point)?.let {
                    mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude(), it.longitude()), 17.5))
                }
            }
        }

        view.findViewById<View>(R.id.btnSaveRouteChanges).setOnClickListener { saveRouteChanges(etRouteName.text.toString()) }
    }

    private fun fetchRouteData(view: View, style: Style) {
        db.collection("routes").document(route.id).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                view.findViewById<EditText>(R.id.etRouteName).setText(doc.getString("routeName"))

                selectedDriverId = doc.getString("driverId")
                selectedBusId = doc.getString("busId")
                selectedConductorId = doc.getString("conductorId")
                
                morningStartTime = doc.getString("morningStartTime") ?: ""
                morningEndTime = doc.getString("morningEndTime") ?: ""
                afternoonStartTime = doc.getString("afternoonStartTime") ?: ""
                afternoonEndTime = doc.getString("afternoonEndTime") ?: ""

                if (morningStartTime.isNotEmpty()) view.findViewById<TextView>(R.id.tvMorningStart).text = morningStartTime
                if (morningEndTime.isNotEmpty()) view.findViewById<TextView>(R.id.tvMorningEnd).text = morningEndTime
                if (afternoonStartTime.isNotEmpty()) view.findViewById<TextView>(R.id.tvAfternoonStart).text = afternoonStartTime
                if (afternoonEndTime.isNotEmpty()) view.findViewById<TextView>(R.id.tvAfternoonEnd).text = afternoonEndTime

                val savedMaxCapacity = (doc.get("maxCapacity") as? Number)?.toInt()
                if (savedMaxCapacity != null) {
                    busCapacity = savedMaxCapacity
                    view.findViewById<TextView>(R.id.tvBusCapacity).text = "$busCapacity Seats"
                }

                @Suppress("UNCHECKED_CAST")
                selectedStopIds = (doc.get("stopIds") as? List<String>)?.toMutableList() ?: mutableListOf()
                
                selectedDriverId?.let { id -> 
                    db.collection("drivers").document(id).get().addOnSuccessListener { 
                        if (!isAdded) return@addOnSuccessListener
                        view.findViewById<TextView>(R.id.tvSelectedDriver).text = "${it.getString("firstName")} ${it.getString("lastName")}" 
                    } 
                }
                selectedBusId?.let { id -> 
                    db.collection("buses").document(id).get().addOnSuccessListener { 
                        if (!isAdded) return@addOnSuccessListener
                        view.findViewById<TextView>(R.id.tvSelectedBus).text = it.getString("busNumber")
                    } 
                }
                selectedConductorId?.let { id -> 
                    db.collection("conductors").document(id).get().addOnSuccessListener { 
                        if (!isAdded) return@addOnSuccessListener
                        view.findViewById<TextView>(R.id.tvSelectedConductor).text = "${it.getString("firstName")} ${it.getString("lastName")}" 
                    } 
                }
                
                loadAllStops(style, view.findViewById(R.id.tvSelectedStopsCount))
            }
        }
    }

    private fun loadAllStops(style: Style, tvCount: TextView) {
        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (!isAdded) return@addOnSuccessListener
            stopFeatures.clear()
            for (doc in snapshots) {
                val lat = doc.getDouble("latitude") ?: 0.0
                val lng = doc.getDouble("longitude") ?: 0.0
                val f = Feature.fromGeometry(Point.fromLngLat(lng, lat))
                f.addStringProperty("id", doc.id)
                f.addStringProperty("name", doc.getString("name"))
                f.addStringProperty("icon", if (selectedStopIds.contains(doc.id)) "selected-icon" else "unselected-icon")
                stopFeatures.add(f)
            }
            updateStopsSource(style)
            updateRouteLine(style)
            tvCount.text = "${selectedStopIds.size} Stops Selected"
        }
    }

    private fun toggleStopSelection(stopId: String, style: Style, tvCount: TextView) {
        if (selectedStopIds.contains(stopId)) {
            selectedStopIds.remove(stopId)
        } else {
            if (selectedStopIds.size >= 10) {
                Toast.makeText(requireContext(), "Maximum of 10 stops reached", Toast.LENGTH_SHORT).show()
                return
            }
            selectedStopIds.add(stopId)
        }

        stopFeatures.forEach { f ->
            val id = f.getStringProperty("id")
            f.addStringProperty("icon", if (selectedStopIds.contains(id)) "selected-icon" else "unselected-icon")
        }
        updateStopsSource(style)
        updateRouteLine(style)
        tvCount.text = "${selectedStopIds.size} Stops Selected"
    }

    private fun updateStopsSource(style: Style) {
        style.getSourceAs<GeoJsonSource>(STOPS_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(stopFeatures))
    }

    private fun updateRouteLine(style: Style) {
        if (selectedStopIds.size < 2) {
            style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }

        val waypoints = mutableListOf<GeoPoint>()
        selectedStopIds.forEach { id ->
            stopFeatures.find { it.getStringProperty("id") == id }?.let { f ->
                val p = f.geometry() as Point
                waypoints.add(GeoPoint(p.latitude(), p.longitude()))
            }
        }

        Thread {
            try {
                val roadManager = OSRMRoadManager(requireContext(), Configuration.getInstance().userAgentValue)
                val road = roadManager.getRoad(ArrayList(waypoints))
                if (road.mStatus == Road.STATUS_OK) {
                    val routePoints = road.mRouteHigh.map { Point.fromLngLat(it.longitude, it.latitude) }
                    val lineString = LineString.fromLngLats(routePoints)
                    activity?.runOnUiThread {
                        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(Feature.fromGeometry(lineString))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(requireContext(), CommonR.style.TimePickerBlack, { _, h, m ->
            val amPm = if (h < 12) "AM" else "PM"
            val hourFormatted = if (h % 12 == 0) 12 else h % 12
            onTimeSelected(String.format(Locale.getDefault(), "%02d:%02d %s", hourFormatted, m, amPm))
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    private fun showDriverPicker(target: TextView) {
        db.collection("drivers").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            val items = snapshots.map { "${it.getString("firstName")} ${it.getString("lastName")}" }.toTypedArray()
            val ids = snapshots.map { it.id }
            AlertDialog.Builder(requireContext()).setTitle("Select Driver").setItems(items) { _, which ->
                selectedDriverId = ids[which]
                target.text = items[which]
            }.show()
        }
    }

    private fun showBusPicker(target: TextView, capacityTarget: TextView) {
        db.collection("buses").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
            val items = snapshots.map { it.getString("busNumber") ?: "N/A" }.toTypedArray()
            val docs = snapshots.documents
            AlertDialog.Builder(requireContext()).setTitle("Select Bus").setItems(items) { _, which ->
                val doc = docs[which]
                selectedBusId = doc.id
                busCapacity = (doc.get("capacity") as? Number)?.toInt() ?: 0
                target.text = items[which]
                capacityTarget.text = "$busCapacity Seats"
            }.show()
        }
    }

    private fun showConductorPicker(target: TextView) {
        db.collection("conductors").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            val items = snapshots.map { "${it.getString("firstName")} ${it.getString("lastName")}" }.toTypedArray()
            val ids = snapshots.map { it.id }
            AlertDialog.Builder(requireContext()).setTitle("Select Conductor").setItems(items) { _, which ->
                selectedConductorId = ids[which]
                target.text = items[which]
            }.show()
        }
    }

    private fun saveRouteChanges(name: String) {
        if (name.isEmpty() || selectedDriverId == null || selectedBusId == null || selectedStopIds.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        val updates = hashMapOf(
            "routeName" to name, "driverId" to selectedDriverId, "busId" to selectedBusId,
            "conductorId" to selectedConductorId, "stopIds" to selectedStopIds, "maxCapacity" to busCapacity,
            "morningStartTime" to morningStartTime, "morningEndTime" to morningEndTime,
            "afternoonStartTime" to afternoonStartTime, "afternoonEndTime" to afternoonEndTime
        )
        db.collection("routes").document(route.id).update(updates).addOnSuccessListener {
            Toast.makeText(requireContext(), "Route updated", Toast.LENGTH_SHORT).show()
            (requireActivity() as? AdminHome)?.loadRouting()
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroyView() { super.onDestroyView(); mapView.onDestroy(); mapLibreMap = null }
}
