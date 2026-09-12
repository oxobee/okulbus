package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
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
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import java.util.Locale

class StopDetailFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var stop: StopAdmin
    private var onBack: (() -> Unit)? = null
    private val assignedStudents = mutableListOf<AssignedStudent>()
    private var isMaximized = false
    private var parentsListener: ListenerRegistration? = null
    
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private val SOURCE_ID = "stop-source"
    private val LAYER_ID = "stop-layer"

    companion object {
        fun newInstance(stop: StopAdmin, onBack: () -> Unit) = StopDetailFragment().apply {
            this.stop = stop
            this.onBack = onBack
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_view_stop, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        mapView = view.findViewById(R.id.mapStopView)
        mapView.onCreate(savedInstanceState)
        
        setupUI(view)
        loadMap()
        startParentsListener(view)
    }

    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackStopDetail)?.setOnClickListener { onBack?.invoke() }
        
        view.findViewById<TextView>(R.id.tvStopName).text = stop.name
        view.findViewById<TextView>(R.id.tvStopCoordinates).text = String.format(Locale.US, "%.6f, %.6f", stop.latitude, stop.longitude)
        
        val rv = view.findViewById<RecyclerView>(R.id.recyclerAssignedStudents)
        rv?.layoutManager = LinearLayoutManager(requireContext())

        val btnMaximize = view.findViewById<ImageButton>(R.id.btnMaximizeMap)
        val btnRecenter = view.findViewById<ImageButton>(R.id.btnRecenterStop)
        val mapContainer = view.findViewById<FrameLayout>(R.id.mapContainer)
        
        btnMaximize?.setOnClickListener {
            isMaximized = !isMaximized
            val params = mapContainer?.layoutParams
            if (isMaximized) {
                params?.height = (480 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_close)
            } else {
                params?.height = (250 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            }
            mapContainer?.layoutParams = params
            mapView.post {
                mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(stop.latitude, stop.longitude)))
            }
        }

        btnRecenter?.setOnClickListener {
            mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(stop.latitude, stop.longitude), 17.5))
        }
    }

    private fun loadMap() {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_stop_marker_red, 40, 40)?.let {
                    style.addImage("stop-icon", it)
                }
                
                val point = LatLng(stop.latitude, stop.longitude)
                style.addSource(GeoJsonSource(SOURCE_ID, Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))))
                style.addLayer(SymbolLayer(LAYER_ID, SOURCE_ID).apply {
                    setProperties(
                        PropertyFactory.iconImage("stop-icon"),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconAllowOverlap(true)
                    )
                })
                
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 17.5))
            }
        }
    }

    private fun startParentsListener(view: View) {
        parentsListener?.remove()
        parentsListener = db.collection("parents").addSnapshotListener { snapshots, e ->
            if (e != null || snapshots == null || !isAdded) return@addSnapshotListener

            assignedStudents.clear()
            for (doc in snapshots) {
                @Suppress("UNCHECKED_CAST")
                val child = doc.get("child") as? Map<String, Any>
                if (child != null && child["stop"] == stop.id) {
                    addStudentToList(doc.id, child)
                }

                @Suppress("UNCHECKED_CAST")
                val childrenList = doc.get("children") as? List<Map<String, Any>>
                childrenList?.forEach { c ->
                    if (c["stop"] == stop.id) {
                        addStudentToList(doc.id, c)
                    }
                }
            }
            view.findViewById<TextView>(R.id.tvStopStudentCount).text = getString(R.string.stop_student_count_format, assignedStudents.size)
            view.findViewById<RecyclerView>(R.id.recyclerAssignedStudents)?.adapter = AssignedStudentAdapter(assignedStudents)
        }
    }

    private fun addStudentToList(parentId: String, data: Map<String, Any>) {
        val fName = data["firstName"] as? String ?: ""
        val lName = data["lastName"] as? String ?: ""
        val grade = data["grade"] as? String ?: "N/A"
        val photoUrl = (data["childAvatarUrl"] as? String) ?: (data["avatarUrl"] as? String) ?: ""
        assignedStudents.add(AssignedStudent(parentId, "$fName $lName", grade, photoUrl))
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroyView() {
        parentsListener?.remove()
        mapView.onDestroy()
        mapLibreMap = null
        super.onDestroyView()
    }
}
