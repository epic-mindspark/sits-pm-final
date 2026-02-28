package com.smartpillbox.app.ui.caregiver

import android.os.Bundle
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.FirebaseDatabase
import com.smartpillbox.app.R

class CaregiverAddMedicineActivity : AppCompatActivity() {

    private lateinit var patientCode: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiver_add_medicine)

        patientCode = intent.getStringExtra("patient_code") ?: ""
        if (patientCode.isEmpty()) {
            Toast.makeText(this, "No patient linked!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val etName = findViewById<TextInputEditText>(R.id.etMedName)
        val etDosage = findViewById<TextInputEditText>(R.id.etMedDosage)
        val rgFrequency = findViewById<RadioGroup>(R.id.rgMedFrequency)
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveMedicine)

        findViewById<android.widget.TextView>(R.id.tvHeader).text =
            "Add Medicine for Patient ($patientCode)"

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val dosage = etDosage.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "Enter medicine name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val frequency = when (rgFrequency.checkedRadioButtonId) {
                R.id.rbOnce -> "Once daily"
                R.id.rbTwice -> "Twice daily"
                R.id.rbThrice -> "Three times daily"
                else -> "Once daily"
            }

            // Write to Firebase under patient's node
            val db = FirebaseDatabase.getInstance().reference
            val medRef = db.child("patients").child(patientCode).child("medicines").push()

            val medData = mapOf(
                "name" to name,
                "dosage" to if (dosage.isEmpty()) "As prescribed" else dosage,
                "frequency" to frequency,
                "compartment" to 0,  // Patient assigns compartment on their end
                "addedBy" to "caregiver",
                "timestamp" to System.currentTimeMillis(),
                "synced" to false    // Patient's app reads this and imports
            )

            medRef.setValue(medData)
                .addOnSuccessListener {
                    Toast.makeText(this,
                        "✅ $name added! Patient will see it on their app.",
                        Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this,
                        "❌ Failed: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
        }
    }
}