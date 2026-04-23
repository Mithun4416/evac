package com.evac.app.ui.bulletin

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
 * ReverseMeshFragment — UI layer that observes the Reverse Mesh via Kotlin Flow.
 *
 * Three concurrent flow collectors inside repeatOnLifecycle(STARTED):
 *
 *   1. activeBulletins (StateFlow) → RecyclerView auto-update.
 *   2. incomingAckEvent (SharedFlow) → one-time ACK popup dialog.
 *      Only fires for ACKs where target_device_id == this phone's fingerprint.
 *   3. incomingBulletinEvent (SharedFlow) → one-time toast notification.
 *
 * Key lifecycle patterns:
 *   - repeatOnLifecycle(STARTED): collectors stop when Fragment goes to background.
 *     This prevents leaked coroutines and ghost notifications.
 *   - SharedFlow for events: no replay on rotation, no stale popups.
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
                // StateFlow emits the full list whenever Room data changes.
                // The adapter diffing handles add/remove animations.
                launch {
                    viewModel.activeBulletins.collect { bulletins ->
                        adapter.submitList(bulletins.map { bulletin ->
                            // Map BulletinEntity to whatever the adapter expects.
                            // If using a dedicated BulletinEntity adapter, pass directly.
                            com.evac.app.db.MessageEntity(
                                id = bulletin.uuid,
                                type = "BULLETIN",
                                timestamp = bulletin.timestamp,
                                body = bulletin.message
                            )
                        })
                    }
                }

                // ── Flow 2: One-time ACK popup ───────────────────────────
                // SharedFlow: each emission consumed once, no replay on rotation.
                // This ONLY fires for ACKs where target == this device.
                launch {
                    viewModel.incomingAckEvent.collect { ack ->
                        showTargetedAckPopup(ack.message, ack.timestamp)
                    }
                }

                // ── Flow 3: One-time Bulletin toast ──────────────────────
                // Fires when a genuinely new Bulletin arrives (not a duplicate).
                launch {
                    viewModel.incomingBulletinEvent.collect { bulletin ->
                        Toast.makeText(
                            requireContext(),
                            "New Bulletin: ${bulletin.message.take(80)}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * One-time popup dialog when a targeted ACK arrives for THIS device.
     * This is the "your SOS has been acknowledged" confirmation.
     */
    private fun showTargetedAckPopup(message: String, timestampMs: Long) {
        if (!isAdded || isDetached) return // Safety: don't show if fragment is gone

        val formattedTime = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            .format(Date(timestampMs))

        AlertDialog.Builder(requireContext())
            .setTitle("Response from Command Center")
            .setMessage("$message\n\nReceived at: $formattedTime")
            .setPositiveButton("OK", null)
            .setCancelable(false)
            .show()
    }
}
