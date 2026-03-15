package com.evac.app.ui.map

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.evac.app.R
import com.evac.app.db.MessageEntity
import kotlinx.coroutines.launch
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import com.evac.app.mesh.ExtendedRangeManager
import com.evac.app.model.DisasterType
import com.evac.app.model.SafeSpot
import com.evac.app.model.SpotType
import com.evac.app.util.SafeSpotProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

class MapFragment : Fragment() {

    private val viewModel: MapViewModel by viewModels()
    private lateinit var mapView: MapView
    private val markers = mutableMapOf<String, Marker>()
    private val safeSpotMarkers = mutableListOf<Marker>()
    private var routeOverlay: Polyline? = null
    private val rangeCircles = mutableListOf<Polygon>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapView)
        setupMap()
        setupExtendedRange(view)
        setupSafeSpots(view)
        observeSosMessages()
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Default center — Bangalore
        val startPoint = GeoPoint(12.9716, 77.5946)
        mapView.controller.setZoom(13.0)
        mapView.controller.setCenter(startPoint)
    }

    private fun setupExtendedRange(view: View) {
        val swExtended = view.findViewById<SwitchMaterial>(R.id.sw_extended_range)
        swExtended.isChecked = ExtendedRangeManager.isExtendedModeActive
        swExtended.setOnCheckedChangeListener { _, isChecked ->
            ExtendedRangeManager.setExtendedMode(isChecked, requireContext())
            updateRangeCircles(isChecked)
            if (isChecked) {
                Toast.makeText(context, "Extended Range Active (Uses 3x Battery)", Toast.LENGTH_SHORT).show()
            }
        }
        // Initial state
        if (swExtended.isChecked) updateRangeCircles(true)
    }

    private fun updateRangeCircles(enabled: Boolean) {
        rangeCircles.forEach { mapView.overlays.remove(it) }
        rangeCircles.clear()

        if (enabled) {
            val center = mapView.mapCenter as GeoPoint
            
            // 100m Circle
            val circle100 = Polygon().apply {
                points = Polygon.pointsAsCircle(center, 100.0)
                fillPaint.color = Color.argb(30, 0, 212, 255)
                outlinePaint.color = Color.argb(150, 0, 212, 255)
                outlinePaint.strokeWidth = 2f
            }
            
            // 400m Circle
            val circle400 = Polygon().apply {
                points = Polygon.pointsAsCircle(center, 400.0)
                fillPaint.color = Color.argb(15, 0, 212, 255)
                outlinePaint.color = Color.argb(100, 0, 212, 255)
                outlinePaint.strokeWidth = 1f
            }

            rangeCircles.add(circle100)
            rangeCircles.add(circle400)
            mapView.overlays.add(circle100)
            mapView.overlays.add(circle400)
        }
        mapView.invalidate()
    }

    private fun setupSafeSpots(view: View) {
        val spinner = view.findViewById<Spinner>(R.id.spinner_disaster_type)
        val btnSafeSpots = view.findViewById<MaterialButton>(R.id.btn_safe_spots)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            DisasterType.values().map { it.name }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        btnSafeSpots.setOnClickListener {
            val selectedType = DisasterType.valueOf(spinner.selectedItem as String)
            showSafeSpots(selectedType)
        }
    }

    private fun showSafeSpots(disasterType: DisasterType) {
        // Clear existing
        safeSpotMarkers.forEach { mapView.overlays.remove(it) }
        safeSpotMarkers.clear()
        routeOverlay?.let { mapView.overlays.remove(it) }

        val allSpots = SafeSpotProvider.getBangaloreSpots()
        val filtered = allSpots.filter { it.suitableFor.contains(disasterType) || it.suitableFor.contains(DisasterType.GENERAL) }

        if (filtered.isEmpty()) {
            Toast.makeText(context, "No safe spots found for $disasterType", Toast.LENGTH_SHORT).show()
            return
        }

        val myLoc = mapView.mapCenter as GeoPoint
        var nearestSpot: SafeSpot? = null
        var minDistance = Float.MAX_VALUE

        filtered.forEach { spot ->
            val spotPoint = GeoPoint(spot.lat, spot.lng)
            val distance = myLoc.distanceToAsDouble(spotPoint).toFloat()
            
            if (distance < minDistance) {
                minDistance = distance
                nearestSpot = spot
            }

            val marker = Marker(mapView).apply {
                position = spotPoint
                title = spot.name
                snippet = "Type: ${spot.type} | Capacity: ${spot.capacity}\nDist: ${"%.1f".format(distance / 1000)}km"
                icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_mylocation)
                icon?.setTint(Color.GREEN)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(marker)
            safeSpotMarkers.add(marker)
        }

        // Draw route to nearest
        nearestSpot?.let { spot ->
            val route = Polyline().apply {
                color = Color.GREEN
                width = 5f
                setPoints(listOf(myLoc, GeoPoint(spot.lat, spot.lng)))
            }
            routeOverlay = route
            mapView.overlays.add(route)
            
            val walkTimeMins = (minDistance / (5000 / 60)).toInt() // 5km/h
            Toast.makeText(context, "Nearest: ${spot.name}\nDist: ${"%.0f".format(minDistance)}m (${walkTimeMins}m walk)", Toast.LENGTH_LONG).show()
        }

        mapView.invalidate()
    }

    private fun observeSosMessages() {
        lifecycleScope.launch {
            viewModel.sosMessages.collect { messages ->
                updateMarkers(messages)
            }
        }
    }

    private fun updateMarkers(messages: List<MessageEntity>) {
        // Remove old markers (but keep safe spots)
        markers.values.forEach { mapView.overlays.remove(it) }
        markers.clear()

        messages.forEach { message ->
            val lat = message.lat ?: return@forEach
            val lng = message.lng ?: return@forEach

            val marker = Marker(mapView).apply {
                position = GeoPoint(lat, lng)
                title = "${message.status} — ${message.peopleCount ?: 1} people"
                snippet = "Battery: ${message.batteryPct}% | ${message.note ?: ""}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }

            // Color based on status
            val color = when (message.status) {
                "MEDICAL" -> Color.RED
                "TRAPPED" -> Color.rgb(245, 124, 0)
                "HAZARD"  -> Color.YELLOW
                "SAFE"    -> Color.GREEN
                else      -> Color.GRAY
            }
            marker.icon?.setTint(color)

            mapView.overlays.add(marker)
            markers[message.id] = marker
        }

        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}