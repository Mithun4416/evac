package com.evac.app.ui.responder

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.evac.app.R
import com.evac.app.util.OsrmRouter
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class ResponderDashboardFragment : Fragment() {

    private val viewModel: ResponderDashboardViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var mapView: MapView
    private lateinit var adapter: SosTaskAdapter
    private lateinit var tvSyncStatus: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var myMarker: Marker? = null
    private val sosMarkers = mutableMapOf<String, Marker>()
    private var activeRouteOverlay: Polyline? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_responder_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val email = auth.currentUser?.email ?: "Unknown"
        view.findViewById<TextView>(R.id.tvResponderIdentity).text = "🛡️ RESPONDER MODE · $email"
        tvSyncStatus = view.findViewById(R.id.tvSyncStatus)

        view.findViewById<ImageView>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            findNavController().popBackStack()
        }

        setupMap(view)
        setupRecyclerView(view)
        setupLocationTracking()
        observeData()
    }

    private fun setupMap(view: View) {
        mapView = view.findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(14.0)
    }

    private fun setupRecyclerView(view: View) {
        val rvTasks = view.findViewById<RecyclerView>(R.id.rvTasks)
        rvTasks.layoutManager = LinearLayoutManager(requireContext())
        adapter = SosTaskAdapter(
            onNavigateClick = { task -> navigateToTask(task) },
            onResolveClick = { task -> resolveTask(task) }
        )
        rvTasks.adapter = adapter
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationTracking() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    viewModel.updateLocation(location)
                    updateMyMarker(location)
                    syncLocationToFirebase(location)
                }
            }
        }

        if (hasLocationPermission()) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else {
            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateMyMarker(loc: Location) {
        val geoPoint = GeoPoint(loc.latitude, loc.longitude)
        
        if (myMarker == null) {
            myMarker = Marker(mapView).apply {
                icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_mylocation)
                icon?.setTint(Color.parseColor("#00D4FF"))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                mapView.overlays.add(this)
            }
            mapView.controller.setCenter(geoPoint)
        }
        myMarker?.position = geoPoint
        mapView.invalidate()
    }

    private fun syncLocationToFirebase(loc: Location) {
        val email = auth.currentUser?.email ?: return
        val data = mapOf(
            "email" to email,
            "lat" to loc.latitude,
            "lng" to loc.longitude,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("responders").document(email)
            .set(data)
            .addOnSuccessListener {
                tvSyncStatus.text = "📍 Auto-sync active"
            }
            .addOnFailureListener {
                tvSyncStatus.text = "⚠️ Sync failed"
            }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.tasksFlow.collect { tasks ->
                adapter.submitList(tasks)
                updateMapMarkers(tasks)
            }
        }
    }

    private fun updateMapMarkers(tasks: List<SosTask>) {
        // Remove old markers
        val currentIds = tasks.map { it.id }.toSet()
        val toRemove = sosMarkers.keys - currentIds
        toRemove.forEach {
            mapView.overlays.remove(sosMarkers[it])
            sosMarkers.remove(it)
        }

        // Add/update markers
        for (task in tasks) {
            if (!sosMarkers.containsKey(task.id)) {
                val marker = Marker(mapView).apply {
                    position = GeoPoint(task.lat, task.lng)
                    title = task.status
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    
                    val color = when (task.status) {
                        "MEDICAL" -> Color.parseColor("#FF0040")
                        "TRAPPED" -> Color.parseColor("#FF9500")
                        "HAZARD" -> Color.parseColor("#FFD600")
                        "SAFE" -> Color.parseColor("#00FF88")
                        else -> Color.GRAY
                    }
                    icon?.setTint(color)
                    
                    // Highlight if assigned to me
                    if (task.isAssignedToMe) {
                        icon?.setTint(Color.parseColor("#00D4FF")) // Responder blue
                    }
                }
                mapView.overlays.add(marker)
                sosMarkers[task.id] = marker
            }
        }
        mapView.invalidate()
    }

    private fun navigateToTask(task: SosTask) {
        val loc = viewModel.currentLocation.value ?: return
        tvSyncStatus.text = "Routing to task..."

        lifecycleScope.launch {
            val route = OsrmRouter.getRoute(
                fromLat = loc.latitude,
                fromLng = loc.longitude,
                toLat = task.lat,
                toLng = task.lng
            )

            if (route != null) {
                tvSyncStatus.text = "Route found: ${route.durationSeconds.toInt()/60} mins"
                drawRoute(route.points)
                mapView.controller.animateTo(GeoPoint(task.lat, task.lng))
            } else {
                tvSyncStatus.text = "Routing failed"
                Toast.makeText(context, "Could not find road route", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun drawRoute(points: List<GeoPoint>) {
        activeRouteOverlay?.let { mapView.overlays.remove(it) }

        activeRouteOverlay = Polyline().apply {
            setPoints(points)
            color = Color.parseColor("#00D4FF")
            width = 8f
        }
        mapView.overlays.add(activeRouteOverlay)
        mapView.invalidate()
    }

    private fun resolveTask(task: SosTask) {
        viewModel.markResolved(task.id)
        Toast.makeText(context, "Marked as Resolved", Toast.LENGTH_SHORT).show()
        activeRouteOverlay?.let { 
            mapView.overlays.remove(it)
            activeRouteOverlay = null
            mapView.invalidate()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}
