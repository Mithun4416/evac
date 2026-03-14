package com.evac.app.ui.map

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.evac.app.R
import com.evac.app.db.MessageEntity
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapFragment : Fragment() {

    private val viewModel: MapViewModel by viewModels()
    private lateinit var mapView: MapView
    private val markers = mutableMapOf<String, Marker>()

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

    private fun observeSosMessages() {
        lifecycleScope.launch {
            viewModel.sosMessages.collect { messages ->
                updateMarkers(messages)
            }
        }
    }

    private fun updateMarkers(messages: List<MessageEntity>) {
        // Remove old markers
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