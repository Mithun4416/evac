package com.evac.app.ui.citizen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.NumberPicker
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.evac.app.R
import com.evac.app.util.Phrases
import com.google.android.material.textfield.TextInputEditText

class CitizenFragment : Fragment() {

    private val viewModel: CitizenViewModel by viewModels()
    private var selectedPhraseKey: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_citizen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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