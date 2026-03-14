package com.evac.app.ui.citizen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.evac.app.R
import com.evac.app.util.Phrases
import com.evac.app.util.ProximityAlertManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

class CitizenFragment : Fragment() {

    private val viewModel: CitizenViewModel by viewModels()
    private var selectedPhraseKey: String? = null

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

        // ── Proximity banner wiring ────────────────────────────────────────────
        proximityBanner = view.findViewById(R.id.proximityBanner)
        proximityText   = view.findViewById(R.id.proximityText)
        view.findViewById<View>(R.id.proximityDismiss)?.setOnClickListener {
            proximityBanner?.visibility = View.GONE
        }

        // People picker
        val peoplePicker = view.findViewById<NumberPicker>(R.id.peoplePicker)
        peoplePicker.minValue = 1
        peoplePicker.maxValue = 10
        peoplePicker.value = 1

        // Note field
        val etNote = view.findViewById<TextInputEditText>(R.id.etNote)

        // SOS buttons
        view.findViewById<Button>(R.id.btnMedical).setOnClickListener {
            submitSos("MEDICAL", peoplePicker, etNote)
        }
        view.findViewById<Button>(R.id.btnTrapped).setOnClickListener {
            submitSos("TRAPPED", peoplePicker, etNote)
        }
        view.findViewById<Button>(R.id.btnHazard).setOnClickListener {
            submitSos("HAZARD", peoplePicker, etNote)
        }
        view.findViewById<Button>(R.id.btnSafe).setOnClickListener {
            submitSos("SAFE", peoplePicker, etNote)
        }

        // Quick phrases
        setupPhrases(view, etNote)
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
            "MEDICAL" -> "🔴"
            "TRAPPED" -> "🟠"
            "HAZARD"  -> "🟡"
            "SAFE"    -> "🟢"
            else      -> "⚠️"
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

    private fun submitSos(
        status: String,
        peoplePicker: NumberPicker,
        etNote: TextInputEditText
    ) {
        if (!viewModel.canSendSos()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.sos_rate_limit),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val note = etNote.text?.toString()
        val peopleCount = peoplePicker.value

        viewModel.sendSos(
            status = status,
            peopleCount = peopleCount,
            note = note,
            phraseKey = selectedPhraseKey,
            context = requireContext()
        )

        Toast.makeText(
            requireContext(),
            getString(R.string.sos_sent, status),
            Toast.LENGTH_SHORT
        ).show()

        // Reset
        etNote.text?.clear()
        selectedPhraseKey = null
    }

    private fun setupPhrases(view: View, etNote: TextInputEditText) {
        val container = view.findViewById<ViewGroup>(R.id.phrasesContainer)
        val phrases = Phrases.getAll(requireContext())

        phrases.forEach { phrase ->
            val btn = Button(requireContext()).apply {
                text = phrase.text
                textSize = 12f
                setPadding(24, 8, 24, 8)
                setOnClickListener {
                    etNote.setText(phrase.text)
                    selectedPhraseKey = phrase.key
                }
            }
            container.addView(btn)
        }
    }
}