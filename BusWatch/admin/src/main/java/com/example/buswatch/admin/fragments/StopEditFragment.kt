package com.example.buswatch.admin.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
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
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.StopAdmin
import com.example.buswatch.common.MapUtils
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
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

class StopEditFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var stop: StopAdmin
    private var currentLatLng: LatLng = LatLng(0.0, 0.0)
    private var isMaximized = false
    private var mapLibreMap: MapLibreMap? = null
    private lateinit var mapView: MapView

    private val MARKER_SOURCE_ID = "selection-source"
    private val MARKER_LAYER_ID = "selection-layer"

    companion object {
        fun newInstance(stop: StopAdmin) = StopEditFragment().apply {
            this.stop = stop
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit_stop, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        mapView = view.findViewById(R.id.mapEditStop)
        mapView.onCreate(savedInstanceState)
        
        setupUI(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackEditStop)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.loadStops()
        }
        
        val etName = view.findViewById<EditText>(R.id.etStopName)
        val tvCoords = view.findViewById<TextView>(R.id.tvCoordinates)
        val btnMyLocation = view.findViewById<ImageButton>(R.id.btnMyLocation)
        val btnSmartFill = view.findViewById<ImageButton>(R.id.btnSmartFill)
        val btnMaximize = view.findViewById<ImageButton>(R.id.btnMaximizeMap)
        
        val layoutStopName = view.findViewById<View>(R.id.layoutStopName)
        val tvMapLabel = view.findViewById<View>(R.id.tvMapLabel)
        val layoutActions = view.findViewById<View>(R.id.layoutActions)
        val layoutSubHeader = view.findViewById<View>(R.id.layoutSubHeader)
        val mapContainer = view.findViewById<FrameLayout>(R.id.mapContainer)
        
        etName?.setText(stop.name)
        currentLatLng = LatLng(stop.latitude, stop.longitude)
        tvCoords?.text = String.format(Locale.US, "Lat: %.6f, Lng: %.6f", stop.latitude, stop.longitude)
        
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_stop_marker_red, 40, 40)?.let {
                    style.addImage("marker-icon", it)
                }
                style.addSource(GeoJsonSource(MARKER_SOURCE_ID))
                style.addLayer(SymbolLayer(MARKER_LAYER_ID, MARKER_SOURCE_ID).apply {
                    setProperties(
                        PropertyFactory.iconImage("marker-icon"),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconAllowOverlap(true)
                    )
                })

                map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17.5))
                updateMarker(currentLatLng)

                map.addOnMapClickListener { latLng ->
                    currentLatLng = latLng
                    updateMarker(latLng)
                    updateLocationInfo(latLng, tvCoords, etName)
                    true
                }
            }
        }

        btnMaximize?.setOnClickListener {
            isMaximized = !isMaximized
            layoutStopName?.isVisible = !isMaximized
            tvMapLabel?.isVisible = !isMaximized
            layoutActions?.isVisible = !isMaximized
            layoutSubHeader?.isVisible = !isMaximized
            tvCoords?.isVisible = !isMaximized

            val params = mapContainer?.layoutParams
            if (isMaximized) {
                params?.height = (480 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_close)
            } else {
                params?.height = (300 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            }
            mapContainer?.layoutParams = params
            mapView.post { mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng)) }
        }

        btnMyLocation?.setOnClickListener { goToCurrentLocation(tvCoords, etName) }
        btnSmartFill?.setOnClickListener { etName?.let { autoFillStopName(it) } }

        view.findViewById<View>(R.id.btnCancelEditStop)?.setOnClickListener { (requireActivity() as? AdminHome)?.loadStops() }
        view.findViewById<View>(R.id.btnSaveStopChanges)?.setOnClickListener {
            val newName = etName?.text?.toString()?.trim() ?: ""
            if (newName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a stop name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val updates = hashMapOf<String, Any>(
                "name" to newName,
                "latitude" to currentLatLng.latitude,
                "longitude" to currentLatLng.longitude
            )
            db.collection("stops").document(stop.id).update(updates).addOnSuccessListener {
                Toast.makeText(requireContext(), "Stop updated successfully", Toast.LENGTH_SHORT).show()
                (requireActivity() as? AdminHome)?.loadStops()
            }
        }
    }

    private fun updateMarker(latLng: LatLng) {
        mapLibreMap?.getStyle { style ->
            val source = style.getSourceAs<GeoJsonSource>(MARKER_SOURCE_ID)
            source?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(latLng.longitude, latLng.latitude)))
        }
    }

    private fun updateLocationInfo(latLng: LatLng, tvCoords: TextView?, etName: EditText?) {
        tvCoords?.text = String.format(Locale.US, "Lat: %.6f, Lng: %.6f", latLng.latitude, latLng.longitude)
        if (etName != null && etName.text.isEmpty()) {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                        if (addresses.isNotEmpty()) {
                            val streetName = addresses[0].thoroughfare ?: addresses[0].featureName ?: ""
                            if (streetName.isNotEmpty()) { etName.post { etName.hint = "Suggested: $streetName" } }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val streetName = addresses[0].thoroughfare ?: addresses[0].featureName ?: ""
                        if (streetName.isNotEmpty()) { etName.hint = "Suggested: $streetName" }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun goToCurrentLocation(tvCoords: TextView?, etName: EditText?) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location: Location? = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { null }

        if (location != null) {
            val userPoint = LatLng(location.latitude, location.longitude)
            currentLatLng = userPoint
            mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userPoint, 17.5))
            updateMarker(userPoint)
            updateLocationInfo(userPoint, tvCoords, etName)
        } else {
            Toast.makeText(requireContext(), "Could not find current location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun autoFillStopName(etName: EditText) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(currentLatLng.latitude, currentLatLng.longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val streetName = address.thoroughfare ?: address.featureName ?: ""
                        if (streetName.isNotEmpty()) { etName.post { etName.setText(streetName) } }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(currentLatLng.latitude, currentLatLng.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val streetName = addresses[0].thoroughfare ?: addresses[0].featureName ?: ""
                    if (streetName.isNotEmpty()) { etName.setText(streetName) }
                }
            }
        } catch (_: Exception) { Toast.makeText(requireContext(), "Geocoding failed", Toast.LENGTH_SHORT).show() }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroyView() { super.onDestroyView(); mapView.onDestroy(); mapLibreMap = null }
}
