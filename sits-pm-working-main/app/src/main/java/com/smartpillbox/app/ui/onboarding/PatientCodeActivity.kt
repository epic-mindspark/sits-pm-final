package com.smartpillbox.app.ui.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.smartpillbox.app.MainActivity
import com.smartpillbox.app.data.remote.FirebaseSyncManager
import com.smartpillbox.app.databinding.ActivityPatientCodeBinding
import com.smartpillbox.app.ui.caregiver.CaregiverDashboardActivity

class PatientCodeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPatientCodeBinding
    private var role: String = "patient"
    private var generatedCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatientCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        role = intent.getStringExtra("role") ?: "patient"

        if (role == "patient") {
            setupPatientUI()
        } else {
            setupCaregiverUI()
        }

        binding.btnContinue.setOnClickListener {
            if (role == "patient") {
                onPatientContinue()
            } else {
                onCaregiverContinue()
            }
        }
    }

    private fun setupPatientUI() {
        generatedCode = FirebaseSyncManager.generatePatientCode()

        binding.tvTitle.text = "Your Patient Code"
        binding.tvSubtitle.text = "Share this code with your caregiver to link accounts"
        binding.cardCodeDisplay.visibility = View.VISIBLE
        binding.tilPatientCode.visibility = View.GONE
        binding.tvPatientCode.text = generatedCode

        binding.btnCopyCode.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Patient Code", generatedCode))
            Toast.makeText(this, "Code copied!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCaregiverUI() {
        binding.tvTitle.text = "Enter Patient Code"
        binding.tvSubtitle.text = "Ask your patient for their 6-character code"
        binding.cardCodeDisplay.visibility = View.GONE
        binding.tilPatientCode.visibility = View.VISIBLE
        binding.btnContinue.text = "Link & Continue"
    }

    private fun onPatientContinue() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous_${System.currentTimeMillis()}"

        FirebaseSyncManager.saveUserRole(this, "patient")
        FirebaseSyncManager.savePatientCode(this, generatedCode)
        FirebaseSyncManager.registerPatientNode(generatedCode, uid)

        Toast.makeText(this, "Registered as Patient!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun onCaregiverContinue() {
        val code = binding.etPatientCode.text.toString().trim().uppercase()
        if (code.length < 4) {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = "Please enter a valid patient code"
            return
        }

        binding.btnContinue.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE

        FirebaseSyncManager.doesPatientExist(code) { exists ->
            runOnUiThread {
                binding.progressBar.visibility = View.GONE

                // Allow linking even if patient doesn't exist yet
                // (caregiver might set up first)
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous_${System.currentTimeMillis()}"

                FirebaseSyncManager.saveUserRole(this, "caregiver")
                FirebaseSyncManager.savePatientCode(this, code)
                FirebaseSyncManager.linkCaregiverToPatient(uid, code)

                if (!exists) {
                    // Also register the patient node so it's ready
                    FirebaseSyncManager.registerPatientNode(code, "pending")
                }

                Toast.makeText(
                    this,
                    if (exists) "Linked to patient!" else "Code saved! Patient will be linked when they register.",
                    Toast.LENGTH_LONG
                ).show()

                startActivity(Intent(this, CaregiverDashboardActivity::class.java))
                finish()
            }
        }
    }
}