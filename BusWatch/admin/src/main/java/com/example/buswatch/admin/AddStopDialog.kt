package com.example.buswatch.admin

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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
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
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.util.Locale

class AddStopDialog(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val onStopAdded: () -> Unit
) {
    private var currentLatLng: LatLng = LatLng(14.5995, 120.9842) // Default to Manila
    private var isMaximized = false
    private var mapLibreMap: MapLibreMap? = null

    private val SELECTION_SOURCE_ID = "selection-source"
    private val SELECTION_LAYER_ID = "selection-layer"
    private val EXISTING_STOPS_SOURCE_ID = "existing-stops-source"
    private val EXISTING_STOPS_LAYER_ID = "existing-stops-layer"

    fun show() {
        MapLibre.getInstance(context)
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_stop, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        val etStopName = dialogView.findViewById<EditText>(R.id.etStopName)
        val mapPicker = dialogView.findViewById<MapView>(R.id.mapPicker)
        val tvCoordinates = dialogView.findViewById<TextView>(R.id.tvCoordinates)
        val btnAddAnother = dialogView.findViewById<TextView>(R.id.btnAddAnother)
        val btnSaveStop = dialogView.findViewById<TextView>(R.id.btnSaveStop)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseAddStop)
        val btnMyLocation = dialogView.findViewById<ImageButton>(R.id.btnMyLocation)
        val btnMaximize = dialogView.findViewById<ImageButton>(R.id.btnMaximizeMap)
        val btnSmartFill = dialogView.findViewById<ImageButton>(R.id.btnSmartFill)

        val headerLayout = dialogView.findViewById<View>(R.id.headerLayout)
        val layoutStopName = dialogView.findViewById<View>(R.id.layoutStopName)
        val layoutMapLabel = dialogView.findViewById<View>(R.id.layoutMapLabel)
        val layoutActions = dialogView.findViewById<View>(R.id.layoutActions)
        val mapContainer = dialogView.findViewById<FrameLayout>(R.id.mapContainer)

        mapPicker.onCreate(null)
        mapPicker.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) { style ->
                setupStyle(style)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15.0))
                updateSelectionMarker(currentLatLng)
                loadExistingStops()

                map.addOnMapClickListener { latLng ->
                    currentLatLng = latLng
                    updateSelectionMarker(latLng)
                    updateLocationText(latLng, tvCoordinates)
                    true
                }
            }
        }

        btnMaximize?.setOnClickListener {
            isMaximized = !isMaximized
            headerLayout.isVisible = !isMaximized
            layoutStopName.isVisible = !isMaximized
            layoutMapLabel.isVisible = !isMaximized
            tvCoordinates.isVisible = !isMaximized
            layoutActions.isVisible = !isMaximized

            val params = mapContainer?.layoutParams
            if (isMaximized) {
                params?.height = (450 * context.resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_close)
            } else {
                params?.height = (250 * context.resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            }
            mapContainer?.layoutParams = params
            mapPicker.post { mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng)) }
        }

        btnMyLocation?.setOnClickListener { goToCurrentLocation(tvCoordinates) }
        btnSmartFill?.setOnClickListener { autoFillStopName(etStopName) }
        btnClose?.setOnClickListener { dialog.dismiss() }

        btnAddAnother?.setOnClickListener {
            saveStopToFirestore(etStopName?.text?.toString() ?: "", currentLatLng) {
                etStopName?.text?.clear()
                loadExistingStops()
                Toast.makeText(context, "Stop added! You can add another.", Toast.LENGTH_SHORT).show()
                onStopAdded()
            }
        }

        btnSaveStop?.setOnClickListener {
            saveStopToFirestore(etStopName?.text?.toString() ?: "", currentLatLng) {
                dialog.dismiss()
                onStopAdded()
            }
        }

        updateLocationText(currentLatLng, tvCoordinates)
        
        dialog.setOnDismissListener {
            mapPicker.onDestroy()
        }
        dialog.show()
    }

    private fun setupStyle(style: Style) {
        MapUtils.getScaledBitmap(context, CommonR.drawable.ic_stop_marker, 28, 28)?.let {
            style.addImage("existing-stop-icon", it)
        }
        MapUtils.getScaledBitmap(context, CommonR.drawable.ic_stop_marker_red, 36, 36)?.let {
            style.addImage("selection-icon", it)
        }

        style.addSource(GeoJsonSource(EXISTING_STOPS_SOURCE_ID))
        style.addSource(GeoJsonSource(SELECTION_SOURCE_ID))

        style.addLayer(SymbolLayer(EXISTING_STOPS_LAYER_ID, EXISTING_STOPS_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage("existing-stop-icon"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true)
            )
        })

        style.addLayer(SymbolLayer(SELECTION_LAYER_ID, SELECTION_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage("selection-icon"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true)
            )
        })
    }

    private fun updateSelectionMarker(latLng: LatLng) {
        mapLibreMap?.getStyle { style ->
            val source = style.getSourceAs<GeoJsonSource>(SELECTION_SOURCE_ID)
            source?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(latLng.longitude, latLng.latitude)))
        }
    }

    private fun loadExistingStops() {
        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            val features = mutableListOf<Feature>()
            for (doc in snapshots) {
                val lat = doc.getDouble("latitude")
                val lng = doc.getDouble("longitude")
                if (lat != null && lng != null) {
                    features.add(Feature.fromGeometry(Point.fromLngLat(lng, lat)))
                }
            }
            mapLibreMap?.getStyle { style ->
                val source = style.getSourceAs<GeoJsonSource>(EXISTING_STOPS_SOURCE_ID)
                source?.setGeoJson(FeatureCollection.fromFeatures(features))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun goToCurrentLocation(tvCoords: TextView?) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location: Location? = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { null }

        if (location != null) {
            val userPoint = LatLng(location.latitude, location.longitude)
            currentLatLng = userPoint
            mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userPoint, 18.0))
            updateSelectionMarker(userPoint)
            updateLocationText(userPoint, tvCoords)
        } else {
            Toast.makeText(context, "Could not find current location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun autoFillStopName(etName: EditText?) {
        if (etName == null) return
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
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
        } catch (_: Exception) { Toast.makeText(context, "Geocoding failed", Toast.LENGTH_SHORT).show() }
    }

    private fun updateLocationText(latLng: LatLng, tvCoords: TextView?) {
        tvCoords?.text = String.format(Locale.US, "Lat: %.6f, Lng: %.6f", latLng.latitude, latLng.longitude)
    }

    private fun saveStopToFirestore(name: String, latLng: LatLng, onSuccess: () -> Unit) {
        if (name.isEmpty()) {
            Toast.makeText(context, "Please enter a stop name", Toast.LENGTH_SHORT).show()
            return
        }
        val stopData = hashMapOf(
            "name" to name,
            "latitude" to latLng.latitude,
            "longitude" to latLng.longitude,
            "status" to "active",
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        db.collection("stops").add(stopData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
    }
}
