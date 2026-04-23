package com.evac.app.ui.responder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.evac.app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import androidx.navigation.fragment.findNavController

class ResponderLoginFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_responder_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etEmail = view.findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = view.findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = view.findViewById<MaterialButton>(R.id.btnLogin)
        val tvError = view.findViewById<TextView>(R.id.tvError)
        val statusCard = view.findViewById<MaterialCardView>(R.id.statusCard)
        val tvLoggedInAs = view.findViewById<TextView>(R.id.tvLoggedInAs)
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnLogout)

        // Check if already logged in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            showLoggedInState(view, currentUser.email ?: "Unknown")
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text?.toString()?.trim() ?: ""
            val password = etPassword.text?.toString() ?: ""

            if (email.isEmpty() || password.isEmpty()) {
                tvError.text = "Please enter email and access code"
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            tvError.text = "Authenticating..."
            tvError.setTextColor(requireContext().getColor(R.color.text_secondary))
            tvError.visibility = View.VISIBLE

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    Toast.makeText(context, "Authenticated as Field Responder", Toast.LENGTH_SHORT).show()
                    showLoggedInState(view, email)
                }
                .addOnFailureListener { e ->
                    btnLogin.isEnabled = true
                    tvError.setTextColor(requireContext().getColor(R.color.sos_medical))
                    tvError.text = when {
                        e.message?.contains("no user record") == true -> "No responder account found"
                        e.message?.contains("password is invalid") == true -> "Wrong access code"
                        e.message?.contains("badly formatted") == true -> "Invalid email format"
                        else -> "Auth failed: ${e.message}"
                    }
                    tvError.visibility = View.VISIBLE
                }
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            showLoggedOutState(view)
            Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoggedInState(view: View, email: String) {
        findNavController().navigate(R.id.action_login_to_dashboard)
    }

    private fun showLoggedOutState(view: View) {
        view.findViewById<View>(R.id.etEmail)?.let { (it.parent?.parent as? View)?.visibility = View.VISIBLE }
        view.findViewById<View>(R.id.etPassword)?.let { (it.parent?.parent as? View)?.visibility = View.VISIBLE }
        view.findViewById<MaterialButton>(R.id.btnLogin).apply {
            visibility = View.VISIBLE
            isEnabled = true
        }
        view.findViewById<MaterialCardView>(R.id.statusCard).visibility = View.GONE
    }
}