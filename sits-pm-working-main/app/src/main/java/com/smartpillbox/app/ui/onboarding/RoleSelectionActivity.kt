package com.smartpillbox.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.smartpillbox.app.databinding.ActivityRoleSelectionBinding

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardPatient.setOnClickListener {
            val intent = Intent(this, PatientCodeActivity::class.java)
            intent.putExtra("role", "patient")
            startActivity(intent)
            finish()
        }

        binding.cardCaregiver.setOnClickListener {
            val intent = Intent(this, PatientCodeActivity::class.java)
            intent.putExtra("role", "caregiver")
            startActivity(intent)
            finish()
        }
    }
}