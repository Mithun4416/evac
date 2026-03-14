package com.evac.app.ui.citizen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.evac.app.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * 4-button SOS UI — one tap sends distress signal with GPS + battery + people count.
 */
class CitizenFragment : Fragment() {

    private val viewModel: CitizenViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var peopleCount = 1

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

        val tvStatus = view.findViewById<TextView>(R.id.tv_status)
        val tvPeopleCount = view.findViewById<TextView>(R.id.tv_people_count)
        val etNote = view.findViewById<TextInputEditText>(R.id.et_note)

        // People count controls
        view.findViewById<MaterialButton>(R.id.btn_people_minus).setOnClickListener {
            if (peopleCount > 1) {
                peopleCount--
                tvPeopleCount.text = peopleCount.toString()
            }
        }
        view.findViewById<MaterialButton>(R.id.btn_people_plus).setOnClickListener {
            if (peopleCount < 99) {
                peopleCount++
                tvPeopleCount.text = peopleCount.toString()
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

        // Observe ViewModel status
        viewModel.statusMsg.observe(viewLifecycleOwner) { msg ->
            tvStatus.text = msg
        }

        viewModel.sosSent.observe(viewLifecycleOwner) { sent ->
            if (sent) {
                Toast.makeText(requireContext(), "SOS sent to mesh!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendSos(status: String, etNote: TextInputEditText, tvStatus: TextView) {
        grabLocation() // Refresh location
        val note = etNote.text?.toString() ?: ""
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
