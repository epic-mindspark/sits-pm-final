package com.smartpillbox.app.ui.schedule

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartpillbox.app.SmartPillBoxApp
import com.smartpillbox.app.data.local.MedicineEntity
import com.smartpillbox.app.data.model.Medicine
import com.smartpillbox.app.databinding.ActivityMedicationsBinding
import com.smartpillbox.app.ui.scan.MedicineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MedicationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicationsBinding
    private val medicineList = mutableListOf<Medicine>()
    private lateinit var adapter: MedicineAdapter
    private val medicineIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedicationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = MedicineAdapter(medicineList) { position ->
            confirmDelete(position)
        }
        binding.rvMedications.layoutManager = LinearLayoutManager(this)
        binding.rvMedications.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadMedications()
    }

    private fun loadMedications() {
        val dao = SmartPillBoxApp.instance.database.medicineDao()

        lifecycleScope.launch {
            val entities: List<MedicineEntity> = withContext(Dispatchers.IO) {
                dao.getActiveMedicines().first()
            }

            medicineList.clear()
            medicineIds.clear()

            var i = 0
            while (i < entities.size) {
                val entity = entities[i]
                medicineList.add(
                    Medicine(
                        name = entity.name,
                        dosage = entity.dosage,
                        frequency = entity.frequency,
                        timing = ""
                    )
                )
                medicineIds.add(entity.id)
                i++
            }

            adapter.notifyDataSetChanged()

            if (medicineList.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvMedications.visibility = View.GONE
                binding.tvMedicineCount.text = "No medicines"
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.rvMedications.visibility = View.VISIBLE
                binding.tvMedicineCount.text = "${medicineList.size} medicine(s) added"
            }
        }
    }

    private fun confirmDelete(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Medicine")
            .setMessage("Remove ${medicineList[position].name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteMedicine(position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMedicine(position: Int) {
        val dao = SmartPillBoxApp.instance.database.medicineDao()
        val id = medicineIds[position]

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteMedicineById(id)
            }

            medicineList.removeAt(position)
            medicineIds.removeAt(position)
            adapter.notifyItemRemoved(position)
            adapter.notifyItemRangeChanged(position, medicineList.size)

            Toast.makeText(this@MedicationsActivity, "Deleted ✅", Toast.LENGTH_SHORT).show()

            if (medicineList.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvMedications.visibility = View.GONE
                binding.tvMedicineCount.text = "No medicines"
            } else {
                binding.tvMedicineCount.text = "${medicineList.size} medicine(s) added"
            }
        }
    }
}