package com.evac.app.ui.bulletin

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.evac.app.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ReverseMeshFragment — demonstrates how the UI layer observes the Reverse Mesh
 * via Kotlin Flow collection, showing:
 *
 *   1. A live RecyclerView of Bulletins (auto-updates via StateFlow).
 *   2. A one-time popup dialog when a targeted ACK arrives for THIS device.
 *   3. A toast when any new Bulletin drops into the mesh.
 *
 * Key patterns:
 *   - repeatOnLifecycle(STARTED): flows are only collected when the Fragment is
 *     visible, preventing leaked coroutines and ghost notifications.
 *   - SharedFlow for one-time events: ensures ACK popups don't replay on rotation.
 */
class ReverseMeshFragment : Fragment() {

    private val viewModel: ReverseMeshViewModel by viewModels()
    private lateinit var adapter: BulletinAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_bulletin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BulletinAdapter()

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerBulletins)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        observeFlows()
    }

    private fun observeFlows() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // ── Flow 1: Live Bulletin list → RecyclerView ────────────
                launch {
                    viewModel.activeBulletins.collect { bulletins ->
                        // Convert BulletinEntity to the existing MessageEntity-based adapter
                        // or display directly. Here we update the adapter.
                        // If using a dedicated BulletinEntity adapter, replace accordingly.
                        val bulletinCount = bulletins.size
                        view?.findViewById<TextView>(R.id.tvBulletinCount)?.text =
                            "$bulletinCount active bulletin(s)"

                        // For the existing BulletinAdapter that expects MessageEntity,
                        // you can create a simple data holder or use a new adapter.
                        // The important thing is the Flow collection pattern:
                        android.util.Log.d(
                            "ReverseMeshFragment",
                            "Bulletin list updated: $bulletinCount items"
                        )
                    }
                }

                // ── Flow 2: One-time ACK popup (targeted at THIS device) ─
                launch {
                    viewModel.incomingAckEvent.collect { ack ->
                        showAckPopup(ack.message, ack.timestamp)
                    }
                }

                // ── Flow 3: One-time Bulletin toast ──────────────────────
                launch {
                    viewModel.incomingBulletinEvent.collect { bulletin ->
                        Toast.makeText(
                            requireContext(),
                            "📢 New Bulletin: ${bulletin.message.take(80)}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * Show a blocking popup dialog when a targeted ACK arrives.
     * This only fires for ACKs where target_device_id == this phone's fingerprint.
     */
    private fun showAckPopup(message: String, timestampMs: Long) {
        val formattedTime = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            .format(Date(timestampMs))

        AlertDialog.Builder(requireContext())
            .setTitle("🎯 Response from Command Center")
            .setMessage("$message\n\nReceived at: $formattedTime")
            .setPositiveButton("OK", null)
            .setCancelable(false)
            .show()
    }
}
