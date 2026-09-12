package com.example.buswatch

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.buswatch.common.MapUtils
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
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

class StudentDetailsGeneralFragment : Fragment() {
    private lateinit var viewModel: StudentViewModel
    private val db = FirebaseFirestore.getInstance()
    
    private var parentStatus: String = "pending"
    private var homeLatLng: LatLng? = null
    private var childName: String? = null
    
    private val defaultPoint = LatLng(14.6760, 121.0437)

    // Map instances
    private lateinit var mapHome: MapView
    private lateinit var mapStop: MapView
    private var mapLibreHome: MapLibreMap? = null
    private var mapLibreStop: MapLibreMap? = null

    // Source IDs for mapStop
    private val STOP_MARKERS_SOURCE = "stop-markers-source"
    private val STOP_LINE_SOURCE = "stop-line-source"
    private val STOP_HOME_SOURCE = "stop-home-source"

    companion object {
        fun newInstance(childName: String?): StudentDetailsGeneralFragment {
            val fragment = StudentDetailsGeneralFragment()
            val args = Bundle()
            args.putString("childName", childName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
        viewModel = ViewModelProvider(requireActivity())[StudentViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_student_details_general, container, false)
        childName = arguments?.getString("childName")
        
        mapHome = view.findViewById(R.id.mapHomeLocation)
        mapStop = view.findViewById(R.id.mapStopLocation)
        
        mapHome.onCreate(savedInstanceState)
        mapStop.onCreate(savedInstanceState)

        setupUI(view)
        setupObservers(view)
        
        viewModel.loadStudentAndParentData(childName)
        
        return view
    }

    private fun setupUI(view: View) {
        view.findViewById<Button>(R.id.btnAssignStop)?.setOnClickListener {
            if (parentStatus.lowercase() == "approved") {
                homeLatLng?.let { hp ->
                    StopPickerDialogFragment.newInstance(hp.latitude, hp.longitude, childName)
                        .show(childFragmentManager, "stop_picker")
                }
            } else {
                Toast.makeText(requireContext(), "Action restricted until account is approved.", Toast.LENGTH_SHORT).show()
            }
        }
        
        view.findViewById<ImageButton>(R.id.btnGeneralEdit)?.setOnClickListener {
            EditStudentDialogFragment.newInstance(childName, parentStatus)
                .show(childFragmentManager, "edit_student")
        }

        mapHome.getMapAsync { map ->
            mapLibreHome = map
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_location, 36, 36)?.let {
                    style.addImage("home-icon", it)
                }
                style.addSource(GeoJsonSource("home-source"))
                style.addLayer(SymbolLayer("home-layer", "home-source").apply {
                    setProperties(
                        PropertyFactory.iconImage("home-icon"),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
                    )
                })
                homeLatLng?.let { updateHomeMap(it) }
            }
        }

        mapStop.getMapAsync { map ->
            mapLibreStop = map
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                setupStopMapStyle(style)
            }
        }
    }

    private fun setupStopMapStyle(style: Style) {
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_location, 36, 36)?.let { style.addImage("home-icon", it) }
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_stop_marker_blue, 32, 32)?.let { style.addImage("stop-icon-blue", it) }
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_stop_marker_red, 40, 40)?.let { style.addImage("stop-icon-red", it) }

        style.addSource(GeoJsonSource(STOP_HOME_SOURCE))
        style.addSource(GeoJsonSource(STOP_MARKERS_SOURCE))
        style.addSource(GeoJsonSource(STOP_LINE_SOURCE))

        style.addLayer(LineLayer("stop-line-layer", STOP_LINE_SOURCE).apply {
            setProperties(
                PropertyFactory.lineColor("#FEBE1E".toColorInt()),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f))
            )
        })

        style.addLayer(SymbolLayer("stop-markers-layer", STOP_MARKERS_SOURCE).apply {
            setProperties(
                PropertyFactory.iconImage("{icon}"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true)
            )
        })

        style.addLayer(SymbolLayer("stop-home-layer", STOP_HOME_SOURCE).apply {
            setProperties(
                PropertyFactory.iconImage("home-icon"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
            )
        })
    }

    private fun setupObservers(view: View) {
        viewModel.studentData.observe(viewLifecycleOwner) { childData ->
            if (childData != null) {
                displayChildInfo(view, childData)
                val currentStopId = childData["stop"] as? String
                updateStopAndMapUI(view, currentStopId)
            }
        }
        viewModel.parentStatus.observe(viewLifecycleOwner) { status -> parentStatus = status }
    }

    private fun displayChildInfo(view: View, childData: kotlin.collections.Map<String, Any>) {
        view.findViewById<TextView>(R.id.tvStudentName).text = getString(CommonR.string.name_format, childData["firstName"], childData["lastName"])
        val grade = childData["grade"] as? String ?: ""
        val section = childData["class"] as? String ?: childData["section"] as? String ?: ""
        view.findViewById<TextView>(R.id.tvHeaderGradeSection).text = if (grade.isNotEmpty() || section.isNotEmpty()) "$grade - $section".trim(' ', '-') else ""
        
        view.findViewById<TextView>(R.id.tvDob).text = childData["age"]?.toString() ?: "-"
        view.findViewById<TextView>(R.id.tvSchool).text = childData["school"] as? String ?: "-"
        view.findViewById<TextView>(R.id.tvGrade).text = childData["grade"] as? String ?: "-"
        view.findViewById<TextView>(R.id.tvSection).text = childData["class"] as? String ?: "-"
        view.findViewById<TextView>(R.id.tvAddress).text = childData["address"] as? String ?: "-"
        
        val lat = childData["latitude"] as? Double ?: 0.0
        val lng = childData["longitude"] as? Double ?: 0.0
        if (lat != 0.0 && lng != 0.0) {
            homeLatLng = LatLng(lat, lng)
            updateHomeMap(homeLatLng!!)
        }

        val avatar = childData["childAvatarUrl"] as? String ?: childData["avatarUrl"] as? String ?: ""
        if (avatar.isNotEmpty()) Glide.with(this).load(avatar).circleCrop().into(view.findViewById(R.id.imgStudentAvatar))
    }

    private fun updateHomeMap(latLng: LatLng) {
        mapLibreHome?.getStyle { style ->
            style.getSourceAs<GeoJsonSource>("home-source")?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(latLng.longitude, latLng.latitude)))
            mapLibreHome?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0))
        }
    }

    private fun updateStopAndMapUI(view: View, stopId: String?) {
        val tvStatus = view.findViewById<TextView>(R.id.tvAssignedStop)
        val btnAssign = view.findViewById<Button>(R.id.btnAssignStop)

        if (stopId.isNullOrEmpty()) {
            tvStatus?.setText(CommonR.string.pickup_stop_not_selected)
            btnAssign?.isVisible = true
            loadAllStopsOnMap(null)
        } else {
            btnAssign?.isVisible = false
            db.collection("stops").document(stopId).get().addOnSuccessListener { stopDoc ->
                if (!isAdded) return@addOnSuccessListener
                val stopName = stopDoc.getString("name") ?: "Selected Stop"
                tvStatus?.text = getString(CommonR.string.confirmed_stop_format, stopName)
                loadAllStopsOnMap(stopId)
            }
        }
    }

    private fun loadAllStopsOnMap(selectedStopId: String?) {
        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (!isAdded) return@addOnSuccessListener
            val stopFeatures = mutableListOf<Feature>()
            var selectedPoint: Point? = null
            val bounds = LatLngBounds.Builder()
            homeLatLng?.let { bounds.include(it) }

            for (doc in snapshots) {
                val lat = doc.getDouble("latitude") ?: 0.0
                val lng = doc.getDouble("longitude") ?: 0.0
                if (lat == 0.0) continue
                
                val p = Point.fromLngLat(lng, lat)
                bounds.include(LatLng(lat, lng))
                val isSelected = doc.id == selectedStopId
                if (isSelected) selectedPoint = p
                
                val f = Feature.fromGeometry(p)
                f.addStringProperty("icon", if (isSelected) "stop-icon-red" else "stop-icon-blue")
                stopFeatures.add(f)
            }

            mapLibreStop?.getStyle { style ->
                style.getSourceAs<GeoJsonSource>(STOP_MARKERS_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(stopFeatures))
                homeLatLng?.let { hp ->
                    style.getSourceAs<GeoJsonSource>(STOP_HOME_SOURCE)?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(hp.longitude, hp.latitude)))
                    if (selectedPoint != null) {
                        val line = LineString.fromLngLats(listOf(Point.fromLngLat(hp.longitude, hp.latitude), selectedPoint!!))
                        style.getSourceAs<GeoJsonSource>(STOP_LINE_SOURCE)?.setGeoJson(Feature.fromGeometry(line))
                    } else {
                        style.getSourceAs<GeoJsonSource>(STOP_LINE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                    }
                }
                
                try {
                    mapLibreStop?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
                } catch (e: Exception) {
                    homeLatLng?.let { mapLibreStop?.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 14.0)) }
                }
            }
        }
    }

    override fun onStart() { super.onStart(); mapHome.onStart(); mapStop.onStart() }
    override fun onResume() { super.onResume(); mapHome.onResume(); mapStop.onResume() }
    override fun onPause() { mapHome.onPause(); mapStop.onPause(); super.onPause() }
    override fun onStop() { mapHome.onStop(); mapStop.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapHome.onSaveInstanceState(outState); mapStop.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapHome.onLowMemory(); mapStop.onLowMemory() }
    override fun onDestroyView() { super.onDestroyView(); mapHome.onDestroy(); mapStop.onDestroy(); mapLibreHome = null; mapLibreStop = null }
}
