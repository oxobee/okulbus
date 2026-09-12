package com.example.buswatch

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.example.buswatch.common.MapUtils
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.osmdroid.util.GeoPoint
import java.util.ArrayList

class StopPickerDialogFragment : DialogFragment() {

    private lateinit var viewModel: StudentViewModel
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var homePoint: LatLng? = null
    private var childName: String? = null
    private val defaultPoint = LatLng(14.6760, 121.0437)
    
    private var selectedStopId: String? = null
    private var selectedStopName: String? = null
    private var selectedStopPoint: LatLng? = null

    private var isMaximized = false
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null

    private val HOME_SOURCE_ID = "home-source"
    private val HOME_LAYER_ID = "home-layer"
    private val STOPS_SOURCE_ID = "stops-source"
    private val STOPS_LAYER_ID = "stops-layer"
    private val RADIUS_SOURCE_ID = "radius-source"
    private val RADIUS_LAYER_ID = "radius-layer"

    companion object {
        fun newInstance(homeLat: Double, homeLng: Double, childName: String?): StopPickerDialogFragment {
            val fragment = StopPickerDialogFragment()
            val args = Bundle()
            args.putDouble("homeLat", homeLat)
            args.putDouble("homeLng", homeLng)
            args.putString("childName", childName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
        setStyle(STYLE_NO_TITLE, 0)
        viewModel = ViewModelProvider(requireActivity()).get(StudentViewModel::class.java)
        
        arguments?.let {
            val lat = it.getDouble("homeLat")
            val lng = it.getDouble("homeLng")
            if (lat != 0.0 && lng != 0.0) homePoint = LatLng(lat, lng)
            childName = it.getString("childName")
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        dialog?.window?.let { window ->
            if (!isMaximized) {
                val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            } else {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val currentStopId = viewModel.studentData.value?.get("stop") as? String
        val layoutRes = if (currentStopId.isNullOrEmpty()) {
            R.layout.dialog_assign_pickup_stop
        } else {
            R.layout.dialog_pickup_stop
        }
        return inflater.inflate(layoutRes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapStopPicker)
        val tvSelectedStop = view.findViewById<TextView>(R.id.tvSelectedStopName)
        val btnMaximize = view.findViewById<ImageButton>(R.id.btnWholeView)
        val btnMyLocation = view.findViewById<ImageButton>(R.id.btnMapMyLocation)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                setupStyle(style)
                val center = homePoint ?: defaultPoint
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 15.5))
                
                homePoint?.let { hp ->
                    style.getSourceAs<GeoJsonSource>(HOME_SOURCE_ID)?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(hp.longitude, hp.latitude)))
                    drawRadius(style, hp)
                }

                viewModel.activeStops.observe(viewLifecycleOwner) { stops ->
                    val features = mutableListOf<Feature>()
                    for (stop in stops) {
                        val lat = stop["latitude"] as? Double ?: 0.0
                        val lng = stop["longitude"] as? Double ?: 0.0
                        if (lat == 0.0) continue

                        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
                        feature.addStringProperty("id", stop["id"] as? String)
                        feature.addStringProperty("name", stop["name"] as? String)
                        feature.addStringProperty("icon", if (stop["id"] == selectedStopId) "stop-icon-red" else "stop-icon-blue")
                        features.add(feature)
                    }
                    style.getSourceAs<GeoJsonSource>(STOPS_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(features))
                }
                
                map.addOnMapClickListener { latLng ->
                    val pixel = map.projection.toScreenLocation(latLng)
                    val features = map.queryRenderedFeatures(pixel, STOPS_LAYER_ID)
                    if (features.isNotEmpty()) {
                        val f = features[0]
                        selectedStopId = f.getStringProperty("id")
                        selectedStopName = f.getStringProperty("name")
                        selectedStopPoint = LatLng(latLng.latitude, latLng.longitude) 
                        
                        tvSelectedStop.text = selectedStopName
                        tvSelectedStop.setTextColor(Color.BLACK)
                        
                        // Update icons
                        viewModel.activeStops.value?.let { currentStops ->
                            val updatedFeatures = mutableListOf<Feature>()
                            for (stop in currentStops) {
                                val sLat = stop["latitude"] as? Double ?: 0.0
                                val sLng = stop["longitude"] as? Double ?: 0.0
                                val feature = Feature.fromGeometry(Point.fromLngLat(sLng, sLat))
                                feature.addStringProperty("id", stop["id"] as? String)
                                feature.addStringProperty("name", stop["name"] as? String)
                                feature.addStringProperty("icon", if (stop["id"] == selectedStopId) "stop-icon-red" else "stop-icon-blue")
                                updatedFeatures.add(feature)
                            }
                            style.getSourceAs<GeoJsonSource>(STOPS_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(updatedFeatures))
                        }
                        
                        map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                        true
                    } else false
                }
            }
        }
        
        viewModel.loadActiveStops()

        btnMaximize.setOnClickListener {
            isMaximized = !isMaximized
            toggleMaximize(view, btnMaximize)
        }

        btnMyLocation.setOnClickListener {
            homePoint?.let {
                mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 17.0))
            } ?: run {
                Toast.makeText(requireContext(), CommonR.string.home_location_not_set, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.btnCancelStop).setOnClickListener { dismiss() }
        view.findViewById<Button>(R.id.btnConfirmStop).setOnClickListener {
            if (selectedStopId != null && selectedStopName != null) {
                showConfirmationPopup()
            } else Toast.makeText(requireContext(), CommonR.string.select_stop_from_map, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupStyle(style: Style) {
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_location, 36, 36)?.let { style.addImage("home-icon", it) }
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_stop_marker_blue, 32, 32)?.let { style.addImage("stop-icon-blue", it) }
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_stop_marker_red, 40, 40)?.let { style.addImage("stop-icon-red", it) }

        style.addSource(GeoJsonSource(HOME_SOURCE_ID))
        style.addSource(GeoJsonSource(STOPS_SOURCE_ID))
        style.addSource(GeoJsonSource(RADIUS_SOURCE_ID))

        style.addLayerBelow(FillLayer(RADIUS_LAYER_ID, RADIUS_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.fillColor(Color.argb(30, 0, 122, 255)),
                PropertyFactory.fillOutlineColor("#007AFF".toColorInt())
            )
        }, "waterway")

        style.addLayer(SymbolLayer(STOPS_LAYER_ID, STOPS_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage("{icon}"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
            )
        })

        style.addLayer(SymbolLayer(HOME_LAYER_ID, HOME_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage("home-icon"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
            )
        })
    }

    private fun drawRadius(style: Style, center: LatLng) {
        val points = mutableListOf<Point>()
        val radius = 2000.0 // meters
        val steps = 64
        for (i in 0 until steps) {
            val angle = Math.toRadians(i * 360.0 / steps)
            val dx = radius * Math.cos(angle)
            val dy = radius * Math.sin(angle)
            val lat = center.latitude + (dy / 111320.0)
            val lng = center.longitude + (dx / (111320.0 * Math.cos(Math.toRadians(center.latitude))))
            points.add(Point.fromLngLat(lng, lat))
        }
        points.add(points[0]) // Close the polygon
        val polygon = Polygon.fromLngLats(listOf(points))
        style.getSourceAs<GeoJsonSource>(RADIUS_SOURCE_ID)?.setGeoJson(Feature.fromGeometry(polygon))
    }

    private fun toggleMaximize(view: View, btnMaximize: ImageButton) {
        val visibility = if (isMaximized) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.tvPickupStopTitle).visibility = visibility
        view.findViewById<View>(R.id.tvPickupStopSubtitle).visibility = visibility
        view.findViewById<View>(R.id.tvSelectedStopLabel).visibility = visibility
        view.findViewById<View>(R.id.tvSelectedStopName).visibility = visibility
        view.findViewById<View>(R.id.llStopActions).visibility = visibility
        
        val cvMap = view.findViewById<View>(R.id.cvStopMap)
        val params = cvMap.layoutParams as ViewGroup.MarginLayoutParams
        if (isMaximized) {
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.topMargin = 0
            btnMaximize.setImageResource(CommonR.drawable.ic_close)
            dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            params.height = (250 * resources.displayMetrics.density).toInt()
            params.topMargin = (20 * resources.displayMetrics.density).toInt()
            btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
            dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        cvMap.layoutParams = params
    }

    private fun showConfirmationPopup() {
        val currentStopId = viewModel.studentData.value?.get("stop") as? String
        val isFirstTime = currentStopId.isNullOrEmpty()
        val message = if (isFirstTime) getString(CommonR.string.assign_stop_confirm_message, selectedStopName)
                      else getString(CommonR.string.change_stop_confirm_message, selectedStopName)
        
        AlertDialog.Builder(requireContext())
            .setTitle(CommonR.string.confirmation)
            .setMessage(message)
            .setPositiveButton(CommonR.string.confirm_caps) { _, _ -> submitStopRequest() }
            .setNegativeButton(CommonR.string.cancel_caps, null)
            .show()
    }

    private fun submitStopRequest() {
        val uid = auth.currentUser?.uid ?: return
        val studentData = viewModel.studentData.value ?: return
        val currentStopId = studentData["stop"] as? String ?: ""
        
        if (currentStopId.isEmpty()) {
            updateStopDirectly(uid, studentData)
        } else {
            db.collection("stops").document(currentStopId).get().addOnSuccessListener { doc ->
                val currentStopName = doc.getString("name") ?: "Unknown"
                val currentLat = doc.getDouble("latitude") ?: 0.0
                val currentLng = doc.getDouble("longitude") ?: 0.0
                saveRequestToFirestore(uid, currentStopId, currentStopName, currentLat, currentLng, studentData)
            }.addOnFailureListener {
                saveRequestToFirestore(uid, currentStopId, "Unknown", 0.0, 0.0, studentData)
            }
        }
    }

    private fun updateStopDirectly(parentId: String, studentData: kotlin.collections.Map<String, Any>) {
        val firstName = studentData["firstName"] as? String
        val lastName = studentData["lastName"] as? String
        db.collection("parents").document(parentId).get().addOnSuccessListener { doc ->
            if (!doc.exists()) return@addOnSuccessListener
            val batch = db.batch()
            val parentRef = db.collection("parents").document(parentId)
            @Suppress("UNCHECKED_CAST")
            val docChild = doc.get("child") as? kotlin.collections.Map<String, Any>
            if (docChild?.get("firstName") == firstName && docChild?.get("lastName") == lastName) {
                batch.update(parentRef, "child.stop", selectedStopId)
            } else {
                @Suppress("UNCHECKED_CAST")
                val childrenList = doc.get("children") as? List<kotlin.collections.Map<String, Any>>
                val newList = childrenList?.map { c ->
                    if (c["firstName"] == firstName && c["lastName"] == lastName) c.toMutableMap().apply { put("stop", selectedStopId ?: "") }
                    else c
                }
                if (newList != null) batch.update(parentRef, "children", newList)
            }
            batch.commit().addOnSuccessListener {
                AlertDialog.Builder(requireContext()).setTitle(CommonR.string.location_request_success_title)
                    .setMessage(CommonR.string.stop_assigned_successfully).setPositiveButton(CommonR.string.okay_caps) { _, _ ->
                        viewModel.loadStudentAndParentData(childName)
                        dismiss()
                    }.setCancelable(false).show()
            }.addOnFailureListener { Toast.makeText(requireContext(), CommonR.string.failed_to_assign_stop, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun saveRequestToFirestore(uid: String, cId: String, cName: String, cLat: Double, cLng: Double, studentData: kotlin.collections.Map<String, Any>) {
        db.collection("parents").document(uid).get().addOnSuccessListener { parentDoc ->
            @Suppress("UNCHECKED_CAST")
            val profile = parentDoc.get("profile") as? kotlin.collections.Map<String, Any>
            val parentAvatarUrl = profile?.get("parentAvatarUrl") as? String ?: ""
            val firstName = studentData["firstName"] as? String ?: ""
            val lastName = studentData["lastName"] as? String ?: ""
            val studentId = studentData["studentId"] as? String ?: ""

            val request = hashMapOf(
                "parentId" to uid, "studentId" to studentId, "studentName" to "$firstName $lastName",
                "studentFirstName" to firstName, "studentLastName" to lastName, "currentStopId" to cId,
                "currentStopName" to cName, "currentStopLat" to cLat, "currentStopLng" to cLng,
                "proposedStopId" to selectedStopId, "proposedStopName" to selectedStopName,
                "proposedStopLat" to (selectedStopPoint?.latitude ?: 0.0),
                "proposedStopLng" to (selectedStopPoint?.longitude ?: 0.0),
                "status" to "pending", "parentAvatarUrl" to parentAvatarUrl,
                "timestamp" to com.google.firebase.Timestamp.now()
            )

            db.collection("stop_requests").add(request).addOnSuccessListener {
                AlertDialog.Builder(requireContext()).setTitle(CommonR.string.location_request_success_title)
                    .setMessage(CommonR.string.stop_request_success_message).setPositiveButton(CommonR.string.okay_caps) { _, _ -> dismiss() }
                    .setCancelable(false).show()
            }.addOnFailureListener { Toast.makeText(requireContext(), CommonR.string.failed_to_submit_request, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroyView() { super.onDestroyView(); mapView.onDestroy(); mapLibreMap = null }
}
