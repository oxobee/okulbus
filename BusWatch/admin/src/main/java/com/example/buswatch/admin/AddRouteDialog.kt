package com.example.buswatch.admin

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
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

class AddRouteDialog(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val onRouteAdded: () -> Unit
) {
    private var selectedDriverId: String? = null
    private var selectedBusId: String? = null
    private var selectedConductorId: String? = null
    private var selectedStopIds = mutableListOf<String>()
    private var busCapacity = 0
    private var isMaximized = false
    
    private var mapLibreMap: MapLibreMap? = null
    private val STOPS_SOURCE_ID = "stops-source"
    private val STOPS_LAYER_ID = "stops-layer"
    private val ROUTE_SOURCE_ID = "route-source"
    private val ROUTE_LAYER_ID = "route-layer"

    private var morningStartTime: String = ""
    private var morningEndTime: String = ""
    private var afternoonStartTime: String = ""
    private var afternoonEndTime: String = ""

    private var allStopsFeatures = mutableListOf<Feature>()

    fun show() {
        MapLibre.getInstance(context)
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_route, null)
        val dialog = AlertDialog.Builder(context, CommonR.style.CustomDialog).setView(dialogView).create()

        val etRouteName = dialogView.findViewById<EditText>(R.id.etRouteName)
        val tvSelectedDriver = dialogView.findViewById<TextView>(R.id.tvSelectedDriver)
        val tvSelectedBus = dialogView.findViewById<TextView>(R.id.tvSelectedBus)
        val tvBusCapacity = dialogView.findViewById<TextView>(R.id.tvBusCapacity)
        val tvSelectedConductor = dialogView.findViewById<TextView>(R.id.tvSelectedConductor)
        val tvSelectedStopsCount = dialogView.findViewById<TextView>(R.id.tvSelectedStopsCount)
        val mapRoutePicker = dialogView.findViewById<MapView>(R.id.mapRoutePicker)
        val btnMaximize = dialogView.findViewById<ImageButton>(R.id.btnMaximizeMap)
        val btnMyLocation = dialogView.findViewById<ImageButton>(R.id.btnMyLocation)

        val tvMorningStart = dialogView.findViewById<TextView>(R.id.tvMorningStart)
        val tvMorningEnd = dialogView.findViewById<TextView>(R.id.tvMorningEnd)
        val tvAfternoonStart = dialogView.findViewById<TextView>(R.id.tvAfternoonStart)
        val tvAfternoonEnd = dialogView.findViewById<TextView>(R.id.tvAfternoonEnd)

        val headerLayout = dialogView.findViewById<View>(R.id.headerLayout)
        val layoutRouteName = dialogView.findViewById<View>(R.id.layoutRouteName)
        val layoutDriver = dialogView.findViewById<View>(R.id.layoutDriver)
        val layoutBus = dialogView.findViewById<View>(R.id.layoutBus)
        val layoutCapacity = dialogView.findViewById<View>(R.id.layoutCapacity)
        val layoutConductor = dialogView.findViewById<View>(R.id.layoutConductor)
        val layoutActions = dialogView.findViewById<View>(R.id.btnSaveRoute)
        val mapContainer = dialogView.findViewById<FrameLayout>(R.id.mapContainer)
        val layoutMorning = dialogView.findViewById<View>(R.id.layoutMorningTime)
        val layoutAfternoon = dialogView.findViewById<View>(R.id.layoutAfternoonTime)

        tvMorningStart?.setOnClickListener { showTimePicker { time -> morningStartTime = time; tvMorningStart.text = time } }
        tvMorningEnd?.setOnClickListener { showTimePicker { time -> morningEndTime = time; tvMorningEnd.text = time } }
        tvAfternoonStart?.setOnClickListener { showTimePicker { time -> afternoonStartTime = time; tvAfternoonStart.text = time } }
        tvAfternoonEnd?.setOnClickListener { showTimePicker { time -> afternoonEndTime = time; tvAfternoonEnd.text = time } }

        dialogView.findViewById<FrameLayout>(R.id.btnDriverDropdown).setOnClickListener { showDriverPicker(tvSelectedDriver) }
        dialogView.findViewById<FrameLayout>(R.id.btnBusDropdown).setOnClickListener { showBusPicker(tvSelectedBus, tvBusCapacity) }
        dialogView.findViewById<FrameLayout>(R.id.btnConductorDropdown).setOnClickListener { showConductorPicker(tvSelectedConductor) }

        mapRoutePicker.onCreate(null)
        mapRoutePicker.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) { style ->
                setupStyle(style)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(14.7566, 121.0450), 15.0))
                loadStopsFromFirestore(style, tvSelectedStopsCount)
                
                map.addOnMapClickListener { latLng ->
                    val point = map.projection.toScreenLocation(latLng)
                    val features = map.queryRenderedFeatures(point, STOPS_LAYER_ID)
                    if (features.isNotEmpty()) {
                        val stopId = features[0].getStringProperty("id") ?: ""
                        toggleStopSelection(stopId, style, tvSelectedStopsCount)
                        true
                    } else false
                }
            }
        }

        btnMaximize?.setOnClickListener {
            isMaximized = !isMaximized
            headerLayout?.isVisible = !isMaximized
            layoutRouteName?.isVisible = !isMaximized
            layoutDriver?.isVisible = !isMaximized
            layoutBus?.isVisible = !isMaximized
            layoutCapacity?.isVisible = !isMaximized
            layoutConductor?.isVisible = !isMaximized
            layoutActions?.isVisible = !isMaximized
            layoutMorning?.isVisible = !isMaximized
            layoutAfternoon?.isVisible = !isMaximized

            val params = mapContainer?.layoutParams
            if (isMaximized) {
                params?.height = (480 * context.resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_close)
            } else {
                params?.height = (250 * context.resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            }
            mapContainer?.layoutParams = params
            mapRoutePicker.post { mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(14.7566, 121.0450))) }
        }

        btnMyLocation?.setOnClickListener {
            if (selectedStopIds.isNotEmpty()) {
                db.collection("stops").document(selectedStopIds.random()).get().addOnSuccessListener { doc ->
                    val lat = doc.getDouble("latitude")
                    val lng = doc.getDouble("longitude")
                    if (lat != null && lng != null) {
                        mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 17.0))
                    }
                }
            } else {
                mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(14.7566, 121.0450), 15.0))
            }
        }

        dialogView.findViewById<ImageButton>(R.id.btnCloseAddRoute).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<TextView>(R.id.btnSaveRoute).setOnClickListener { saveRoute(etRouteName.text.toString(), dialog) }

        dialog.setOnDismissListener { mapRoutePicker.onDestroy() }
        dialog.show()
    }

    private fun setupStyle(style: Style) {
        MapUtils.getScaledBitmap(context, CommonR.drawable.ic_stop_marker, 36, 36)?.let { style.addImage("unselected-icon", it) }
        MapUtils.getScaledBitmap(context, CommonR.drawable.ic_stop_marker_red, 48, 48)?.let { style.addImage("selected-icon", it) }

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
                PropertyFactory.iconImage("{icon}"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true)
            )
        })
    }

    private fun loadStopsFromFirestore(style: Style, tvCount: TextView) {
        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            allStopsFeatures.clear()
            for (doc in snapshots) {
                val lat = doc.getDouble("latitude") ?: 0.0
                val lng = doc.getDouble("longitude") ?: 0.0
                val f = Feature.fromGeometry(Point.fromLngLat(lng, lat))
                f.addStringProperty("id", doc.id)
                f.addStringProperty("icon", "unselected-icon")
                allStopsFeatures.add(f)
            }
            updateStopsSource(style)
        }
    }

    private fun toggleStopSelection(stopId: String, style: Style, tvCount: TextView) {
        if (selectedStopIds.contains(stopId)) {
            selectedStopIds.remove(stopId)
        } else {
            if (selectedStopIds.size >= 10) {
                Toast.makeText(context, "Maximum of 10 stops reached", Toast.LENGTH_SHORT).show()
                return
            }
            selectedStopIds.add(stopId)
        }

        allStopsFeatures.forEach { f ->
            val id = f.getStringProperty("id")
            f.addStringProperty("icon", if (selectedStopIds.contains(id)) "selected-icon" else "unselected-icon")
        }
        updateStopsSource(style)
        updateRouteLine(style)
        tvCount.text = context.getString(R.string.selected_stops_count, selectedStopIds.size)
    }

    private fun updateStopsSource(style: Style) {
        style.getSourceAs<GeoJsonSource>(STOPS_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(allStopsFeatures))
    }

    private fun updateRouteLine(style: Style) {
        if (selectedStopIds.size < 2) {
            style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }

        val waypoints = mutableListOf<GeoPoint>()
        // We must order waypoints based on selectedStopIds sequence
        selectedStopIds.forEach { id ->
            allStopsFeatures.find { it.getStringProperty("id") == id }?.let { f ->
                val p = f.geometry() as Point
                waypoints.add(GeoPoint(p.latitude(), p.longitude()))
            }
        }

        Thread {
            try {
                val roadManager = OSRMRoadManager(context, Configuration.getInstance().userAgentValue)
                val road = roadManager.getRoad(ArrayList(waypoints))
                if (road.mStatus == Road.STATUS_OK) {
                    val routePoints = road.mRouteHigh.map { Point.fromLngLat(it.longitude, it.latitude) }
                    val lineString = LineString.fromLngLats(routePoints)
                    mapLibreMap?.getStyle { s ->
                        s.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(Feature.fromGeometry(lineString))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(context, CommonR.style.TimePickerBlack, { _, h, m ->
            val amPm = if (h < 12) "AM" else "PM"
            val hourFormatted = if (h % 12 == 0) 12 else h % 12
            onTimeSelected(String.format(Locale.getDefault(), "%02d:%02d %s", hourFormatted, m, amPm))
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    private fun showDriverPicker(target: TextView) {
        db.collection("drivers").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (snapshots.isEmpty) return@addOnSuccessListener
            val items = snapshots.map { "${it.getString("firstName") ?: ""} ${it.getString("lastName") ?: ""}".trim() }.toTypedArray()
            val ids = snapshots.map { it.id }
            AlertDialog.Builder(context).setTitle("Select Driver").setItems(items) { _, which ->
                selectedDriverId = ids[which]
                target.text = items[which]
            }.show()
        }
    }

    private fun showBusPicker(target: TextView, capacityTarget: TextView) {
        db.collection("buses").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
            if (snapshots.isEmpty) return@addOnSuccessListener
            val buses = snapshots.documents
            val items = buses.map { it.getString("busNumber") ?: "N/A" }.toTypedArray()
            AlertDialog.Builder(context).setTitle("Select Bus").setItems(items) { _, which ->
                val doc = buses[which]
                selectedBusId = doc.id
                busCapacity = (doc.get("capacity") as? Number)?.toInt() ?: 0
                target.text = items[which]
                capacityTarget.text = context.getString(CommonR.string._12_seats).replace("12", busCapacity.toString())
            }.show()
        }
    }

    private fun showConductorPicker(target: TextView) {
        db.collection("conductors").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (snapshots.isEmpty) return@addOnSuccessListener
            val items = snapshots.map { "${it.getString("firstName") ?: ""} ${it.getString("lastName") ?: ""}".trim() }.toTypedArray()
            val ids = snapshots.map { it.id }
            AlertDialog.Builder(context).setTitle("Select Conductor").setItems(items) { _, which ->
                selectedConductorId = ids[which]
                target.text = items[which]
            }.show()
        }
    }

    private fun saveRoute(name: String, dialog: AlertDialog) {
        if (name.isEmpty() || selectedDriverId == null || selectedBusId == null || selectedStopIds.isEmpty()) {
            Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }
        if (afternoonStartTime.contains("AM", true) || afternoonEndTime.contains("AM", true)) {
            Toast.makeText(context, "Afternoon schedule cannot use AM times", Toast.LENGTH_SHORT).show()
            return
        }
        val routeData = hashMapOf(
            "routeName" to name, "driverId" to selectedDriverId, "busId" to selectedBusId,
            "conductorId" to selectedConductorId, "stopIds" to selectedStopIds, "status" to "Active",
            "maxCapacity" to busCapacity, "currentCapacity" to 0, "morningStartTime" to morningStartTime,
            "morningEndTime" to morningEndTime, "afternoonStartTime" to afternoonStartTime,
            "afternoonEndTime" to afternoonEndTime, "createdAt" to com.google.firebase.Timestamp.now()
        )
        db.collection("routes").add(routeData).addOnSuccessListener {
            onRouteAdded(); dialog.dismiss(); Toast.makeText(context, "Route created", Toast.LENGTH_SHORT).show()
        }
    }
}
