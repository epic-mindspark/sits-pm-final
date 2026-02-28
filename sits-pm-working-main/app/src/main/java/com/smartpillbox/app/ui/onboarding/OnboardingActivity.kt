package com.smartpillbox.app.ui.onboarding

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.smartpillbox.app.MainActivity
import com.smartpillbox.app.databinding.ActivityOnboardingBinding
import com.smartpillbox.app.ui.caregiver.CaregiverDashboardActivity
import com.smartpillbox.app.util.PrefsManager
import java.util.Locale

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private var selectedRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If already onboarded, go to dashboard immediately
        if (PrefsManager.isOnboarded(this)) {
            navigateToDashboard()
            return
        }

        setupClickListeners()
    }

    private fun navigateToDashboard() {
        val role = PrefsManager.getRole(this)
        val intent = if (role == "CAREGIVER") {
            Intent(this, CaregiverDashboardActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    private fun setupClickListeners() {
        // STEP 1: Welcome Screen -> Role Selection
        binding.btnGetStarted.setOnClickListener {
            binding.stepWelcome.visibility = View.GONE
            binding.stepRole.visibility = View.VISIBLE
        }

        // STEP 2: Role Selection -> Profile Setup
        binding.btnRoleNext.setOnClickListener {
            val checkedId = binding.rgRole.checkedRadioButtonId
            if (checkedId == -1) {
                Toast.makeText(this, "Please select your role", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            selectedRole = if (checkedId == binding.btnRolePatient.id) "PATIENT" else "CAREGIVER"
            PrefsManager.setRole(this, selectedRole!!)
            
            binding.stepRole.visibility = View.GONE
            if (selectedRole == "PATIENT") {
                binding.stepPatientProfile.visibility = View.VISIBLE
                val code = PrefsManager.generatePatientCode()
                PrefsManager.setPatientCode(this, code)
                binding.tvPatientCode.text = "Your Patient Code: $code"
            } else {
                binding.stepCaregiverProfile.visibility = View.VISIBLE
            }
        }

        // Morning Time Picker (Inside Patient Profile)
        binding.btnSetMorningTime.setOnClickListener {
            val current = PrefsManager.getMorningTime(this).split(":")
            TimePickerDialog(this, { _, h, m ->
                val time = String.format(Locale.getDefault(), "%02d:%02d", h, m)
                PrefsManager.setMorningTime(this, time)
                binding.btnSetMorningTime.text = "Morning: $time"
            }, current[0].toInt(), current[1].toInt(), true).show()
        }

        // Night Time Picker (Inside Patient Profile)
        binding.btnSetNightTime.setOnClickListener {
            val current = PrefsManager.getNightTime(this).split(":")
            TimePickerDialog(this, { _, h, m ->
                val time = String.format(Locale.getDefault(), "%02d:%02d", h, m)
                PrefsManager.setNightTime(this, time)
                binding.btnSetNightTime.text = "Night: $time"
            }, current[0].toInt(), current[1].toInt(), true).show()
        }

        // STEP 3a: Patient Profile Complete
        binding.btnPatientDone.setOnClickListener {
            val name = binding.etPatientName.text.toString().trim()
            val age = binding.etPatientAge.text.toString().trim()
            val contact = binding.etEmergencyContact.text.toString().trim()

            if (name.isEmpty() || age.isEmpty() || contact.isEmpty()) {
                Toast.makeText(this, "Please fill in all details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            PrefsManager.setUserName(this, name)
            PrefsManager.setUserAge(this, age)
            PrefsManager.setEmergencyContact(this, contact)
            finishOnboarding()
        }

        // STEP 3b: Caregiver Profile Complete
        binding.btnCaregiverDone.setOnClickListener {
            val name = binding.etCaregiverName.text.toString().trim()
            val code = binding.etLinkPatientCode.text.toString().trim()

            if (name.isEmpty() || code.length != 6) {
                Toast.makeText(this, "Please enter name and a valid 6-digit patient code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            PrefsManager.setUserName(this, name)
            PrefsManager.setPatientCode(this, code)
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        PrefsManager.setOnboarded(this, true)
        navigateToDashboard()
    }
}