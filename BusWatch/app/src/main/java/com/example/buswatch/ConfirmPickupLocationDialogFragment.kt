package com.example.buswatch

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.buswatch.common.MapUtils
import com.example.buswatch.common.R as CommonR
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

class ConfirmPickupLocationDialogFragment : DialogFragment() {

    private lateinit var viewModel: StudentViewModel
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private var childName: String? = null
    private var currentAddress: String = ""
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    
    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0
    
    private var isMaximized = false
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null

    private val SOURCE_ID = "selected-location-source"
    private val LAYER_ID = "selected-location-layer"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            moveToCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun newInstance(childName: String?, address: String, lat: Double, lng: Double): ConfirmPickupLocationDialogFragment {
            val fragment = ConfirmPickupLocationDialogFragment()
            val args = Bundle()
            args.putString("childName", childName)
            args.putString("address", address)
            args.putDouble("lat", lat)
            args.putDouble("lng", lng)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
        setStyle(STYLE_NO_TITLE, 0)
        viewModel = ViewModelProvider(requireParentFragment()).get(StudentViewModel::class.java)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        arguments?.let {
            childName = it.getString("childName")
            currentAddress = it.getString("address") ?: ""
            currentLat = it.getDouble("lat")
            currentLng = it.getDouble("lng")
            selectedLat = currentLat
            selectedLng = currentLng
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
        return inflater.inflate(R.layout.dialog_confirm_pickup_location, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapConfirmHome)
        val etAddress = view.findViewById<EditText>(R.id.etConfirmHomeAddress)
        val btnMaximize = view.findViewById<ImageButton>(R.id.btnConfirmMaximize)
        val btnMyLocation = view.findViewById<ImageButton>(R.id.btnConfirmMyLocation)
        val btnBack = view.findViewById<Button>(R.id.btnConfirmBack)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirmFinal)
        
        etAddress.setText(currentAddress)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                setupStyle(style)
                val startPoint = if (currentLat != 0.0) LatLng(currentLat, currentLng) else LatLng(14.5995, 120.9842)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(startPoint, 17.5))
                updateMarker(startPoint)

                map.addOnMapClickListener { latLng ->
                    selectedLat = latLng.latitude
                    selectedLng = latLng.longitude
                    updateMarker(latLng)
                    updateAddressFromLocation(latLng.latitude, latLng.longitude, etAddress)
                    true
                }
            }
        }

        btnMaximize.setOnClickListener {
            isMaximized = !isMaximized
            toggleMaximize(view, btnMaximize)
        }

        btnMyLocation.setOnClickListener {
            moveToCurrentLocation()
        }

        btnBack.setOnClickListener { dismiss() }

        btnConfirm.setOnClickListener {
            showConfirmationPopup(etAddress.text.toString())
        }
    }

    private fun setupStyle(style: Style) {
        MapUtils.getScaledBitmap(requireContext(), CommonR.drawable.ic_location, 36, 36)?.let {
            style.addImage("marker-icon", it)
        }
        style.addSource(GeoJsonSource(SOURCE_ID))
        style.addLayer(SymbolLayer(LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage("marker-icon"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
            )
        })
    }

    private fun updateMarker(latLng: LatLng) {
        mapLibreMap?.getStyle { style ->
            val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
            source?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(latLng.longitude, latLng.latitude)))
        }
    }

    private fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                val etAddress = view?.findViewById<EditText>(R.id.etConfirmHomeAddress)

                selectedLat = it.latitude
                selectedLng = it.longitude

                mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17.5))
                updateMarker(latLng)

                if (etAddress != null) {
                    updateAddressFromLocation(it.latitude, it.longitude, etAddress)
                }
            } ?: run {
                Toast.makeText(requireContext(), "Could not get current location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateAddressFromLocation(lat: Double, lng: Double, etAddress: EditText) {
        if (!isAdded) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (addresses?.isNotEmpty() == true) {
                    val address = addresses[0].getAddressLine(0)
                    withContext(Dispatchers.Main) {
                        etAddress.setText(address)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun toggleMaximize(view: View, btnMaximize: ImageButton) {
        val visibility = if (isMaximized) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.tvConfirmHomeTitle).visibility = visibility
        view.findViewById<View>(R.id.etConfirmHomeAddress).visibility = visibility
        view.findViewById<View>(R.id.tvPinInstruction).visibility = visibility
        view.findViewById<View>(R.id.llConfirmActions).visibility = visibility
        
        val cvMap = view.findViewById<View>(R.id.cvConfirmMap)
        val params = cvMap.layoutParams as ViewGroup.MarginLayoutParams
        if (isMaximized) {
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.topMargin = 0
            btnMaximize.setImageResource(CommonR.drawable.ic_close)
            dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            params.height = (250 * resources.displayMetrics.density).toInt()
            params.topMargin = (16 * resources.displayMetrics.density).toInt()
            btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
            dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        cvMap.layoutParams = params
    }

    private fun showConfirmationPopup(newAddress: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(CommonR.string.confirmation)
            .setMessage(CommonR.string.confirm_change_address_title)
            .setPositiveButton(CommonR.string.confirm_caps) { _, _ ->
                submitRequest(newAddress)
            }
            .setNegativeButton(CommonR.string.cancel_caps, null)
            .show()
    }

    private fun submitRequest(newAddress: String) {
        val uid = auth.currentUser?.uid ?: return
        
        db.collection("parents").document(uid).get().addOnSuccessListener { parentDoc ->
            @Suppress("UNCHECKED_CAST")
            val profile = parentDoc.get("profile") as? kotlin.collections.Map<String, Any>
            val parentAvatarUrl = profile?.get("parentAvatarUrl") as? String ?: ""
            
            val studentId = viewModel.studentData.value?.get("studentId") as? String ?: ""

            val request = hashMapOf(
                "parentId" to uid,
                "studentId" to studentId,
                "studentName" to (childName ?: ""),
                "currentAddress" to currentAddress,
                "currentLat" to currentLat,
                "currentLng" to currentLng,
                "pendingAddress" to newAddress,
                "pendingLat" to selectedLat,
                "pendingLng" to selectedLng,
                "status" to "pending",
                "parentAvatarUrl" to parentAvatarUrl,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            
            db.collection("map_requests").add(request).addOnSuccessListener {
                if (isAdded) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(CommonR.string.location_request_success_title)
                        .setMessage(CommonR.string.location_request_success_message)
                        .setPositiveButton(CommonR.string.okay_caps) { _, _ ->
                            dismiss()
                        }
                        .setCancelable(false)
                        .show()
                }
            }.addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), CommonR.string.failed_to_submit_request, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroyView() {
        mapView.onDestroy()
        super.onDestroyView()
    }
}
