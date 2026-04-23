package com.evac.app.ui.citizen

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.evac.app.R
import com.evac.app.util.Phrases
import com.evac.app.util.ProximityAlertManager
import com.evac.app.util.AcousticBeacon
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

/**
 * 4-button SOS UI — one tap sends distress signal with GPS + battery + people count.
 */
class CitizenFragment : Fragment() {

    private val viewModel: CitizenViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var peopleCount = 1

    // ── Proximity alert banner views ───────────────────────────────────────────
    private var proximityBanner: MaterialCardView? = null
    private var proximityText: TextView? = null

    // ── BroadcastReceiver for nearby SOS events ───────────────────────────────
    private val proximityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ProximityAlertManager.ACTION_NEARBY_SOS) return

            val distanceM = intent.getFloatExtra(ProximityAlertManager.EXTRA_DISTANCE_M, 0f)
            val status    = intent.getStringExtra(ProximityAlertManager.EXTRA_SOS_STATUS) ?: "UNKNOWN"
            val people    = intent.getIntExtra(ProximityAlertManager.EXTRA_PEOPLE_COUNT, 1)

            showProximityBanner(distanceM, status, people)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_citizen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        grabLocation()
        
        // ── Proximity banner wiring ────────────────────────────────────────────
        proximityBanner = view.findViewById(R.id.proximityBanner)
        proximityText   = view.findViewById(R.id.proximityText)
        view.findViewById<View>(R.id.proximityDismiss)?.setOnClickListener {
            proximityBanner?.visibility = View.GONE
        }

        val tvStatus = view.findViewById<TextView>(R.id.tv_status)
        val tvPeopleCount = view.findViewById<EditText>(R.id.tv_people_count)
        val etNote = view.findViewById<EditText>(R.id.et_note)

        // People count controls
        view.findViewById<MaterialButton>(R.id.btn_people_minus).setOnClickListener {
            val current = tvPeopleCount.text.toString().toIntOrNull() ?: 1
            if (current > 1) {
                peopleCount = current - 1
                tvPeopleCount.setText(peopleCount.toString())
            }
        }
        view.findViewById<MaterialButton>(R.id.btn_people_plus).setOnClickListener {
            val current = tvPeopleCount.text.toString().toIntOrNull() ?: 1
            if (current < 999) {
                peopleCount = current + 1
                tvPeopleCount.setText(peopleCount.toString())
            }
        }

        // Wire 4 SOS buttons
        view.findViewById<MaterialButton>(R.id.btn_medical).setOnClickListener {
            sendSos("MEDICAL", etNote, tvStatus)
        }
        view.findViewById<MaterialButton>(R.id.btn_trapped).setOnClickListener {
            sendSos("TRAPPED", etNote, tvStatus)
        }
        view.findViewById<MaterialButton>(R.id.btn_hazard).setOnClickListener {
            sendSos("HAZARD", etNote, tvStatus)
        }
        view.findViewById<MaterialButton>(R.id.btn_safe).setOnClickListener {
            sendSos("SAFE", etNote, tvStatus)
        }

        val btnBeacon = view.findViewById<MaterialButton>(R.id.btn_beacon)
        btnBeacon.setOnClickListener {
            AcousticBeacon.toggle(requireContext())
            if (AcousticBeacon.isPlaying) {
                btnBeacon.text = "⏹  STOP BEACON"
                btnBeacon.setBackgroundResource(R.drawable.bg_sos_medical)
            } else {
                btnBeacon.text = "SOUND BEACON"
                btnBeacon.setBackgroundResource(R.drawable.bg_beacon_button)
            }
        }

        // Observe ViewModel status
        viewModel.statusMsg.observe(viewLifecycleOwner) { msg ->
            tvStatus.text = msg
        }

        viewModel.sosSent.observe(viewLifecycleOwner) { sent ->
            if (sent) {
                Toast.makeText(requireContext(), "SOS sent to mesh!", Toast.LENGTH_SHORT).show()
                viewModel.resetSosSent()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Register receiver for nearby SOS broadcasts
        val filter = IntentFilter(ProximityAlertManager.ACTION_NEARBY_SOS)
        ContextCompat.registerReceiver(
            requireContext(), proximityReceiver, filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        requireContext().unregisterReceiver(proximityReceiver)
    }

    // ── Show/hide proximity banner ─────────────────────────────────────────────

    private fun showProximityBanner(distanceM: Float, status: String, people: Int) {
        val distStr = if (distanceM < 1000f) "${distanceM.toInt()}m" else "${"%.1f".format(distanceM / 1000)}km"
        val emoji = when (status) {
            "MEDICAL" -> "MED"
            "TRAPPED" -> "TRP"
            "HAZARD"  -> "HAZ"
            "SAFE"    -> "OK"
            else      -> "SOS"
        }
        proximityText?.text =
            "$emoji Survivor ${distStr} away — $status · $people person${if (people > 1) "s" else ""}\nTap × to dismiss"
        proximityBanner?.visibility = View.VISIBLE

        // Auto-dismiss after 30 seconds
        proximityBanner?.postDelayed({
            proximityBanner?.visibility = View.GONE
        }, 30_000)
    }

    // ── SOS submission ─────────────────────────────────────────────────────────

    private fun sendSos(status: String, etNote: EditText, tvStatus: TextView) {
        grabLocation() // Refresh location
        if (!viewModel.canSendSos(status)) {
            Toast.makeText(
                requireContext(),
                getString(R.string.sos_rate_limit),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val note = etNote.text?.toString() ?: ""
        // Read people count from editable field
        peopleCount = (view?.findViewById<EditText>(R.id.tv_people_count)?.text?.toString()?.toIntOrNull() ?: peopleCount).coerceIn(1, 999)
        viewModel.sendSos(
            status = status,
            note = note,
            peopleCount = peopleCount,
            location = currentLocation
        )
    }

    @SuppressLint("MissingPermission")
    private fun grabLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
            return
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                currentLocation = loc
            }
    }
}
