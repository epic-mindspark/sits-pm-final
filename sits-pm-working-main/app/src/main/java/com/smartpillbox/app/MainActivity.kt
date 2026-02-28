package com.smartpillbox.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartpillbox.app.databinding.ActivityMainBinding
import com.smartpillbox.app.ui.onboarding.OnboardingActivity
import com.smartpillbox.app.ui.scan.ScanActivity
import com.smartpillbox.app.ui.schedule.MedicationsActivity
import com.smartpillbox.app.ui.setup.AddMedicineActivity
import com.smartpillbox.app.util.PrefsManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Check Onboarding Status FIRST
        if (!PrefsManager.isOnboarded(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        updateUI()
    }

    private fun setupClickListeners() {
        binding.cardScanPrescription.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java)
            intent.putExtra("mode", "patient")
            startActivity(intent)
        }

        binding.cardAddManually.setOnClickListener {
            val intent = Intent(this, AddMedicineActivity::class.java)
            intent.putExtra("mode", "patient")
            startActivity(intent)
        }

        binding.cardMyMedications.setOnClickListener {
            startActivity(Intent(this, MedicationsActivity::class.java))
        }

        binding.cardAlarmSchedule.setOnClickListener {
            startActivity(Intent(this, MedicationsActivity::class.java))
        }

        binding.btnSwitchAccount.setOnClickListener {
            showSwitchAccountDialog()
        }
    }

    private fun updateUI() {
        val userName = PrefsManager.getUserName(this) ?: "User"
        binding.toolbar.subtitle = "Welcome, $userName"
        
        lifecycleScope.launch {
            SmartPillBoxApp.instance.database.medicineDao().getAllMedicines().collect { meds ->
                binding.tvMedCount.text = "${meds.size} meds"
            }
        }
    }

    private fun showSwitchAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle("Switch Account")
            .setMessage("This will log you out and return to the onboarding screen. Continue?")
            .setPositiveButton("Yes") { _, _ ->
                PrefsManager.logout(this)
                val intent = Intent(this, OnboardingActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}