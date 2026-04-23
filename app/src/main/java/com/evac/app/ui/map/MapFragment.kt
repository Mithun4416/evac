package com.evac.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.evac.app.R
import com.evac.app.db.MessageEntity
import com.evac.app.db.SafeSpotEntity
import com.evac.app.mesh.ExtendedRangeManager
import com.evac.app.util.HaversineUtil
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapFragment : Fragment() {

    private val viewModel: MapViewModel by viewModels()
    private lateinit var mapView: MapView

    // Overlay tracking
    private lateinit var locationOverlay: MyLocationNewOverlay
    private val sosMarkers = mutableMapOf<String, Marker>()
    private val safeSpotMarkers = mutableListOf<Marker>()
    private val rangeCircles = mutableListOf<Polygon>()
    private var routeOverlay: Polyline? = null

    // Fallback UI
    private lateinit var fallbackContainer: LinearLayout
    private lateinit var fallbackRecyclerView: RecyclerView
    private val fallbackAdapter = SafeSpotFallbackAdapter()
    private var isListViewActive = false

    // Current GPS position (for fallback distance calc)
    private var currentLat: Double? = null
    private var currentLng: Double? = null

    // Cached safe spots for re-rendering
    private var cachedSafeSpots: List<SafeSpotEntity> = emptyList()

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
        fallbackContainer = view.findViewById(R.id.fallback_container)
        fallbackRecyclerView = view.findViewById(R.id.rv_safespot_fallback)

        fallbackRecyclerView.layoutManager = LinearLayoutManager(context)
        fallbackRecyclerView.adapter = fallbackAdapter

        setupMap()
        setupExtendedRange(view)
        setupListViewToggle(view)
        setupSyncButton(view)

        // Observe Room data
        observeSosMessages()
        observeSafeSpots()
        observeSyncState()
    }

    // ─────────────────────── MAP SETUP ───────────────────────

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Setup Location Overlay (Offline GPS)
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), mapView)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        mapView.overlays.add(locationOverlay)

        // Default center
        val startPoint = GeoPoint(12.9716, 77.5946)
        mapView.controller.setZoom(16.0)
        mapView.controller.setCenter(startPoint)

        // Center on real location when fixed
        locationOverlay.runOnFirstFix {
            activity?.runOnUiThread {
                val myLoc = locationOverlay.myLocation
                if (myLoc != null) {
                    currentLat = myLoc.latitude
                    currentLng = myLoc.longitude
                    mapView.controller.animateTo(myLoc)
                    
                    if (cachedSafeSpots.isNotEmpty()) {
                        plotSafeSpotMarkers(cachedSafeSpots)
                        if (isListViewActive) {
                            updateFallbackList(cachedSafeSpots)
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────── EXTENDED RANGE ───────────────────────

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
        if (swExtended.isChecked) updateRangeCircles(true)
    }

    private fun updateRangeCircles(enabled: Boolean) {
        rangeCircles.forEach { mapView.overlays.remove(it) }
        rangeCircles.clear()

        if (enabled) {
            val center = mapView.mapCenter as GeoPoint

            val circle100 = Polygon().apply {
                points = Polygon.pointsAsCircle(center, 100.0)
                fillPaint.color = Color.argb(30, 0, 212, 255)
                outlinePaint.color = Color.argb(150, 0, 212, 255)
                outlinePaint.strokeWidth = 2f
            }

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

    // ─────────────────────── LIST VIEW TOGGLE ───────────────────────

    private fun setupListViewToggle(view: View) {
        val swListView = view.findViewById<SwitchMaterial>(R.id.sw_list_view)
        swListView.setOnCheckedChangeListener { _, isChecked ->
            isListViewActive = isChecked
            if (isChecked) {
                showFallbackList()
            } else {
                hideFallbackList()
            }
        }
    }

    private fun showFallbackList() {
        fallbackContainer.visibility = View.VISIBLE
        mapView.visibility = View.GONE
        updateFallbackList(cachedSafeSpots)
    }

    private fun hideFallbackList() {
        fallbackContainer.visibility = View.GONE
        mapView.visibility = View.VISIBLE
    }

    // ─────────────────────── SYNC BUTTON ───────────────────────

    private fun setupSyncButton(view: View) {
        val btnSync = view.findViewById<MaterialButton>(R.id.btn_sync_safespots)
        btnSync.setOnClickListener {
            viewModel.syncSafeSpots()
        }
    }

    private fun observeSyncState() {
        lifecycleScope.launch {
            viewModel.syncState.collect { state ->
                when (state) {
                    is MapViewModel.SyncState.Loading -> {
                        Toast.makeText(context, "Syncing SafeSpots…", Toast.LENGTH_SHORT).show()
                    }
                    is MapViewModel.SyncState.Success -> {
                        Toast.makeText(
                            context,
                            "✓ Synced: ${state.written} new/updated out of ${state.fetched} spots",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is MapViewModel.SyncState.Error -> {
                        Toast.makeText(context, "✗ Sync failed: ${state.message}", Toast.LENGTH_LONG).show()
                    }
                    is MapViewModel.SyncState.Idle -> { /* no-op */ }
                }
            }
        }
    }

    // Location is handled by MyLocationNewOverlay automatically in setupMap()

    // ─────────────────────── SAFE SPOTS (from Room) ───────────────────────

    private fun observeSafeSpots() {
        lifecycleScope.launch {
            viewModel.activeSafeSpots.collect { spots ->
                cachedSafeSpots = spots
                plotSafeSpotMarkers(spots)

                if (isListViewActive) {
                    updateFallbackList(spots)
                }
            }
        }
    }

    private fun plotSafeSpotMarkers(spots: List<SafeSpotEntity>) {
        // Clear existing safe spot markers
        safeSpotMarkers.forEach { mapView.overlays.remove(it) }
        safeSpotMarkers.clear()
        routeOverlay?.let { mapView.overlays.remove(it) }

        if (spots.isEmpty()) return

        val myLoc = currentLat?.let { lat ->
            currentLng?.let { lng -> GeoPoint(lat, lng) }
        } ?: mapView.mapCenter as GeoPoint

        var nearestSpot: SafeSpotEntity? = null
        var minDistance = Double.MAX_VALUE

        spots.forEach { spot ->
            val spotPoint = GeoPoint(spot.latitude, spot.longitude)
            val distance = myLoc.distanceToAsDouble(spotPoint)

            if (distance < minDistance) {
                minDistance = distance
                nearestSpot = spot
            }

            val marker = Marker(mapView).apply {
                position = spotPoint
                title = spot.name
                snippet = "Type: ${spot.type} | Dist: ${"%.1f".format(distance / 1000)}km"
                icon = getEmojiMarker(spot.type)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) // Center anchor for emoji circles
            }
            mapView.overlays.add(marker)
            safeSpotMarkers.add(marker)
        }

        // Draw route to nearest
        nearestSpot?.let { spot ->
            val route = Polyline().apply {
                color = Color.GREEN
                width = 5f
                setPoints(listOf(myLoc, GeoPoint(spot.latitude, spot.longitude)))
            }
            routeOverlay = route
            mapView.overlays.add(route)

            val walkTimeMins = (minDistance / (5000.0 / 60.0)).toInt()
            Toast.makeText(
                context,
                "Nearest: ${spot.name}\nDist: ${"%.0f".format(minDistance)}m (${walkTimeMins}m walk)",
                Toast.LENGTH_LONG
            ).show()
        }

        mapView.invalidate()
    }

    /**
     * Dynamically generates an Emoji marker matching the Command Center style.
     * Prevents confusion with default SOS pins.
     */
    private fun getEmojiMarker(type: String): Drawable {
        val emoji = when (type.uppercase()) {
            "SHELTER" -> "🏠"
            "MEDICAL" -> "🏥"
            "FOOD" -> "🍽️"
            "WATER" -> "💧"
            "POLICE" -> "🚔"
            else -> "📍"
        }
        
        val typeColors = mapOf(
            "SHELTER" to "#00d4ff",
            "MEDICAL" to "#ff003c",
            "FOOD" to "#00ff88",
            "WATER" to "#00d4ff",
            "POLICE" to "#ff9500"
        )
        val colorHex = typeColors[type.uppercase()] ?: "#00d4ff"
        val baseColor = Color.parseColor(colorHex)
        val alphaColor = Color.argb(64, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))

        val size = 90 // pixels
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = alphaColor
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        paint.apply {
            color = baseColor
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 2f, paint)
        
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 50f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        
        val y = (size / 2f) - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(emoji, size / 2f, y, textPaint)
        
        return BitmapDrawable(resources, bitmap)
    }

    // ─────────────────────── HAVERSINE FALLBACK LIST ───────────────────────

    private fun updateFallbackList(spots: List<SafeSpotEntity>) {
        val lat = currentLat ?: 12.9716  // fallback to Bangalore center
        val lng = currentLng ?: 77.5946

        val spotsWithDistance = spots.map { spot ->
            SafeSpotFallbackAdapter.SpotWithDistance(
                spot = spot,
                distanceKm = HaversineUtil.distanceKm(lat, lng, spot.latitude, spot.longitude),
                bearing = HaversineUtil.bearing(lat, lng, spot.latitude, spot.longitude)
            )
        }.sortedBy { it.distanceKm }

        fallbackAdapter.submitList(spotsWithDistance)
    }

    // ─────────────────────── SOS MESSAGES (existing) ───────────────────────

    private fun observeSosMessages() {
        lifecycleScope.launch {
            viewModel.sosMessages.collect { messages ->
                updateSosMarkers(messages)
            }
        }
    }

    private fun updateSosMarkers(messages: List<MessageEntity>) {
        sosMarkers.values.forEach { mapView.overlays.remove(it) }
        sosMarkers.clear()

        messages.forEach { message ->
            val lat = message.lat ?: return@forEach
            val lng = message.lng ?: return@forEach

            val marker = Marker(mapView).apply {
                position = GeoPoint(lat, lng)
                title = "${message.status} — ${message.peopleCount ?: 1} people"
                snippet = "Battery: ${message.batteryPct}% | ${message.note ?: ""}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }

            val color = when (message.status) {
                "MEDICAL" -> Color.RED
                "TRAPPED" -> Color.rgb(245, 124, 0)
                "HAZARD"  -> Color.YELLOW
                "SAFE"    -> Color.GREEN
                else      -> Color.GRAY
            }
            marker.icon?.setTint(color)

            mapView.overlays.add(marker)
            sosMarkers[message.id] = marker
        }

        mapView.invalidate()
    }

    // ─────────────────────── LIFECYCLE ───────────────────────

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}