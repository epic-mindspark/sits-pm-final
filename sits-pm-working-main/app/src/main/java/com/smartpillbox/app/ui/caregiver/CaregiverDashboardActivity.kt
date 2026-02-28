package com.smartpillbox.app.ui.caregiver

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.smartpillbox.app.R
// ADD THIS IMPORT BELOW
import com.smartpillbox.app.ui.onboarding.OnboardingActivity
import com.smartpillbox.app.data.remote.FirebaseSyncManager
import com.smartpillbox.app.ui.scan.ScanActivity
import com.smartpillbox.app.ui.setup.AddMedicineActivity
import com.smartpillbox.app.util.PrefsManager

class CaregiverDashboardActivity : AppCompatActivity() {

    private var patientCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiver_dashboard)

        // Ensure your PrefsManager has these methods or update to match your PreferenceManager
        patientCode = PrefsManager.getPatientCode(this) ?: ""

        val tvWelcome = findViewById<TextView>(R.id.tvCaregiverWelcome)
        val tvPatientCode = findViewById<TextView>(R.id.tvPatientCode)
        val btnScan = findViewById<Button>(R.id.btnCaregiverScan)
        val btnAddManual = findViewById<Button>(R.id.btnCaregiverAddManual)
        val btnSwitch = findViewById<Button>(R.id.btnCaregiverSwitch)
        val tvMedCount = findViewById<TextView>(R.id.tvCaregiverMedCount)

        tvWelcome.text = "Welcome, ${PrefsManager.getUserName(this) ?: "Caregiver"}"
        tvPatientCode.text = "Patient Code: $patientCode"

        btnScan.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java)
            intent.putExtra("mode", "caregiver")
            intent.putExtra("patient_code", patientCode)
            startActivity(intent)
        }

        btnAddManual.setOnClickListener {
            val intent = Intent(this, AddMedicineActivity::class.java)
            intent.putExtra("mode", "caregiver")
            intent.putExtra("patient_code", patientCode)
            startActivity(intent)
        }

        btnSwitch.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Switch Account")
                .setMessage("This will log you out and show the onboarding screen. Continue?")
                .setPositiveButton("Yes") { _, _ ->
                    PrefsManager.logout(this)
                    // This now works because of the import above
                    val intent = Intent(this, OnboardingActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        loadPatientMedicines(tvMedCount)
    }

    override fun onResume() {
        super.onResume()
        val tvMedCount = findViewById<TextView>(R.id.tvCaregiverMedCount)
        loadPatientMedicines(tvMedCount)
    }

    private fun loadPatientMedicines(tvMedCount: TextView) {
        if (patientCode.isBlank()) {
            tvMedCount.text = "No patient linked"
            return
        }

        FirebaseSyncManager.listenForMedicineChanges(patientCode) { medicines ->
            runOnUiThread {
                tvMedCount.text = "${medicines.size} medicine(s) for patient"
            }
        }
    }
}