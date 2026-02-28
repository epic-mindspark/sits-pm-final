package com.smartpillbox.app.ui.setup

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartpillbox.app.R
import com.smartpillbox.app.SmartPillBoxApp
import com.smartpillbox.app.data.local.MedicineEntity
import com.smartpillbox.app.databinding.ActivityAddMedicineBinding
import kotlinx.coroutines.launch

class AddMedicineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddMedicineBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMedicineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show/hide custom frequency field
        binding.rgFrequency.setOnCheckedChangeListener { _, checkedId ->
            binding.tilCustomFrequency.visibility =
                if (checkedId == R.id.rbCustom) View.VISIBLE else View.GONE
        }

        // Save button
        binding.btnSaveMedicine.setOnClickListener {
            saveMedicine(goBack = true)
        }

        // Save & Add Another button
        binding.btnAddAnother.setOnClickListener {
            saveMedicine(goBack = false)
        }
    }

    private fun saveMedicine(goBack: Boolean) {
        val name = binding.etMedicineName.text.toString().trim()
        val dosage = binding.etDosage.text.toString().trim()

        if (name.isEmpty()) {
            binding.etMedicineName.error = "Please enter medicine name"
            binding.etMedicineName.requestFocus()
            return
        }

        if (dosage.isEmpty()) {
            binding.etDosage.error = "Please enter dosage"
            binding.etDosage.requestFocus()
            return
        }

        val frequency = when (binding.rgFrequency.checkedRadioButtonId) {
            R.id.rbOnceDaily -> "Once daily"
            R.id.rbTwiceDaily -> "Twice daily"
            R.id.rbThriceDaily -> "Three times daily"
            R.id.rbCustom -> binding.etCustomFrequency.text.toString().trim()
                .ifEmpty { "As prescribed" }

            else -> "Once daily"
        }

        val timing = when (binding.rgTiming.checkedRadioButtonId) {
            R.id.rbBeforeFood -> "Before food"
            R.id.rbAfterFood -> "After food"
            R.id.rbWithFood -> "With food"
            else -> "Before food"
        }

        val dao = SmartPillBoxApp.instance.database.medicineDao()

        lifecycleScope.launch {

            val entity = MedicineEntity(
                name = name,
                dosage = dosage,
                frequency = frequency,
                timing = timing,
                compartmentNumber = 0,
                isActive = true,
                createdAt = System.currentTimeMillis()
            )

            dao.insertMedicine(entity)

            runOnUiThread {
                Toast.makeText(
                    this@AddMedicineActivity,
                    "✅ $name saved!",
                    Toast.LENGTH_SHORT
                ).show()

                if (goBack) {
                    finish()
                } else {
                    binding.etMedicineName.text?.clear()
                    binding.etDosage.text?.clear()
                    binding.etCustomFrequency.text?.clear()
                    binding.rgFrequency.check(R.id.rbOnceDaily)
                    binding.rgTiming.check(R.id.rbBeforeFood)
                    binding.etMedicineName.requestFocus()
                }
            }
        }
    }
}