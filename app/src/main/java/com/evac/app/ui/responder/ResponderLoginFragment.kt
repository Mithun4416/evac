package com.evac.app.ui.responder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.evac.app.R
import com.google.android.material.textfield.TextInputEditText

class ResponderLoginFragment : Fragment() {

    private val viewModel: ResponderViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_responder_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etPin    = view.findViewById<TextInputEditText>(R.id.etPin)
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)
        val tvError  = view.findViewById<TextView>(R.id.tvError)

        btnLogin.setOnClickListener {
            val pin = etPin.text?.toString() ?: ""

            if (viewModel.checkPin(pin)) {
                tvError.visibility = View.GONE
                // Show success message
                tvError.setTextColor(
                    requireContext().getColor(R.color.sos_safe)
                )
                tvError.text = "✅ Logged in as Field Responder"
                tvError.visibility = View.VISIBLE
                btnLogin.isEnabled = false
            } else {
                tvError.setTextColor(
                    requireContext().getColor(R.color.sos_medical)
                )
                tvError.text = getString(R.string.wrong_pin)
                tvError.visibility = View.VISIBLE
            }
        }
    }
}