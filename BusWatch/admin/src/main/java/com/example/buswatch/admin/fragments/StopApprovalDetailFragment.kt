package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.StopRequest
import com.example.buswatch.common.MapUtils
import com.example.buswatch.common.NotificationSender
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
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

class StopApprovalDetailFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var request: StopRequest

    private lateinit var mapViewCurrent: MapView
    private lateinit var mapViewProposed: MapView
    private var mapLibreCurrent: MapLibreMap? = null
    private var mapLibreProposed: MapLibreMap? = null

    companion object {
        fun newInstance(request: StopRequest) = StopApprovalDetailFragment().apply {
            this.request = request
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_stop_approval_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapViewCurrent = view.findViewById(R.id.mapCurrentStop)
        mapViewProposed = view.findViewById(R.id.mapProposedStop)
        
        mapViewCurrent.onCreate(savedInstanceState)
        mapViewProposed.onCreate(savedInstanceState)

        setupUI(view)
        loadRequesterInfo(view)
        setupMaps()
    }

    private fun setupUI(view: View) {
        view.findViewById<View>(R.id.btnBackStopApprovalDetail)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.replaceFragment(ApprovalsFragment.newInstance(ApprovalsFragment.Tab.STOPS))
        }

        view.findViewById<View>(R.id.btnApproveStop)?.setOnClickListener { approveRequest() }
        view.findViewById<View>(R.id.btnRejectStop)?.setOnClickListener { rejectRequest() }
    }

    private fun loadRequesterInfo(view: View) {
        view.findViewById<TextView>(R.id.tvStudentName).text = request.studentName
        view.findViewById<TextView>(R.id.tvCurrentStopName).text = request.currentStopName
        view.findViewById<TextView>(R.id.tvProposedStopName).text = request.proposedStopName

        db.collection("parents").document(request.parentId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val profile = doc.get("profile") as? Map<String, Any>
                val fName = profile?.get("firstName") as? String ?: ""
                val lName = profile?.get("lastName") as? String ?: ""
                view.findViewById<TextView>(R.id.tvParentFullName).text = "$fName $lName"
                view.findViewById<TextView>(R.id.tvParentPhone).text = profile?.get("phone") as? String
                
                val parentAvatar = profile?.get("parentAvatarUrl") as? String
                val imgParent = view.findViewById<ImageView>(R.id.imgParent)
                if (!parentAvatar.isNullOrEmpty() && imgParent != null) {
                    Glide.with(this).load(parentAvatar).circleCrop().into(imgParent)
                }

                @Suppress("UNCHECKED_CAST")
                val docChild = doc.get("child") as? Map<String, Any>
                var studentAvatarUrl = docChild?.get("childAvatarUrl") as? String
                
                if (studentAvatarUrl.isNullOrEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    val children = doc.get("children") as? List<Map<String, Any>>
                    val child = children?.find { 
                        val cfName = it["firstName"] as? String ?: ""
                        val clName = it["lastName"] as? String ?: ""
                        "$cfName $clName" == request.studentName 
                    }
                    studentAvatarUrl = child?.get("childAvatarUrl") as? String
                }

                val imgStudent = view.findViewById<ImageView>(R.id.imgStudent)
                if (!studentAvatarUrl.isNullOrEmpty() && imgStudent != null) {
                    Glide.with(this).load(studentAvatarUrl).circleCrop().into(imgStudent)
                }
            }
        }
    }

    private fun setupMaps() {
        mapViewCurrent.getMapAsync { map ->
            mapLibreCurrent = map
            map.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) { style ->
                if (request.currentStopLat != 0.0) {
                    val point = LatLng(request.currentStopLat, request.currentStopLng)
                    setupMapMarker(map, style, point, "current-stop")
                }
            }
        }
        mapViewProposed.getMapAsync { map ->
            mapLibreProposed = map
            map.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) { style ->
                val point = LatLng(request.proposedStopLat, request.proposedStopLng)
                setupMapMarker(map, style, point, "proposed-stop")
            }
        }
    }

    private fun setupMapMarker(map: MapLibreMap, style: Style, point: LatLng, id: String) {
        MapUtils.getScaledBitmap(requireContext(), com.example.buswatch.common.R.drawable.ic_stop_marker, 36, 36)?.let {
            style.addImage("$id-icon", it)
        }
        style.addSource(GeoJsonSource("$id-source", Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))))
        style.addLayer(SymbolLayer("$id-layer", "$id-source").apply {
            setProperties(
                PropertyFactory.iconImage("$id-icon"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
            )
        })
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 17.0))
    }

    private fun approveRequest() {
        val parentId = request.parentId
        val firstName = request.studentFirstName
        val lastName = request.studentLastName
        val proposedStopId = request.proposedStopId

        db.collection("parents").document(parentId).get().addOnSuccessListener { doc ->
            if (!doc.exists()) return@addOnSuccessListener
            
            val batch = db.batch()
            val parentRef = db.collection("parents").document(parentId)
            
            @Suppress("UNCHECKED_CAST")
            val docChild = doc.get("child") as? Map<String, Any>
            if (docChild != null && docChild["firstName"] == firstName && docChild["lastName"] == lastName) {
                batch.update(parentRef, "child.stop", proposedStopId)
                batch.update(parentRef, "child.lastStopApproved", Timestamp.now())
            } else {
                @Suppress("UNCHECKED_CAST")
                val childrenList = doc.get("children") as? List<Map<String, Any>>
                val newList = childrenList?.map { c ->
                    if (c["firstName"] == firstName && c["lastName"] == lastName) {
                        val mutableChild = c.toMutableMap()
                        mutableChild["stop"] = proposedStopId
                        mutableChild["lastStopApproved"] = Timestamp.now()
                        mutableChild
                    } else c
                }
                if (newList != null) {
                    batch.update(parentRef, "children", newList)
                }
            }
            
            val requestRef = db.collection("stop_requests").document(request.id)
            batch.update(requestRef, "status", "approved")

            batch.commit().addOnSuccessListener {
                sendNotificationToParent(true)
                Toast.makeText(requireContext(), "Pickup stop updated and locked for 30 days", Toast.LENGTH_SHORT).show()
                (requireActivity() as? AdminHome)?.replaceFragment(ApprovalsFragment.newInstance(ApprovalsFragment.Tab.STOPS))
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to approve request", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun rejectRequest() {
        db.collection("stop_requests").document(request.id).update("status", "rejected").addOnSuccessListener {
            sendNotificationToParent(false)
            Toast.makeText(requireContext(), "Request rejected", Toast.LENGTH_SHORT).show()
            (requireActivity() as? AdminHome)?.replaceFragment(ApprovalsFragment.newInstance(ApprovalsFragment.Tab.STOPS))
        }
    }

    private fun sendNotificationToParent(isApproved: Boolean) {
        val statusText = if (isApproved) "APPROVED" else "REJECTED"
        val title = "Bus Stop Update $statusText"
        val message = if (isApproved) 
            "Your request to update your child's bus stop has been approved."
        else 
            "Your request to update your child's bus stop has been rejected. Please contact support for more information."

        val notifData = hashMapOf(
            "title" to title,
            "message" to message,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "type" to "approval_status"
        )
        
        db.collection("parents").document(request.parentId).collection("notifications").add(notifData)
        NotificationSender.sendNotification(request.parentId, title, message)
    }

    override fun onStart() { super.onStart(); mapViewCurrent.onStart(); mapViewProposed.onStart() }
    override fun onResume() { super.onResume(); mapViewCurrent.onResume(); mapViewProposed.onResume() }
    override fun onPause() { mapViewCurrent.onPause(); mapViewProposed.onPause(); super.onPause() }
    override fun onStop() { mapViewCurrent.onStop(); mapViewProposed.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapViewCurrent.onSaveInstanceState(outState); mapViewProposed.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapViewCurrent.onLowMemory(); mapViewProposed.onLowMemory() }
    override fun onDestroyView() { super.onDestroyView(); mapViewCurrent.onDestroy(); mapViewProposed.onDestroy(); mapLibreCurrent = null; mapLibreProposed = null }
}
