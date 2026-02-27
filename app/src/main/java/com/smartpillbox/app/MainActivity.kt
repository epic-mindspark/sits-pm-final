package com.smartpillbox.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.smartpillbox.app.ui.scan.ScanActivity
import com.smartpillbox.app.ui.schedule.MedicationsActivity
import com.smartpillbox.app.ui.setup.AddMedicineActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cardScan = findViewById<MaterialCardView>(R.id.cardScanPrescription)
        cardScan.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }

        val cardManual = findViewById<MaterialCardView>(R.id.cardAddManually)
        cardManual.setOnClickListener {
            startActivity(Intent(this, AddMedicineActivity::class.java))
        }

        val cardMeds = findViewById<MaterialCardView>(R.id.cardMyMedications)
        cardMeds.setOnClickListener {
            startActivity(Intent(this, MedicationsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateConnectionStatus()
    }

    private fun updateConnectionStatus() {
        val tvStatus = findViewById<TextView>(R.id.tvConnectionStatus)
        val dao = SmartPillBoxApp.instance.database.medicineDao()

        lifecycleScope.launch {
            val count = dao.getActiveMedicineCount()
            runOnUiThread {
                tvStatus.text = if (count > 0) {
                    "📦 $count medicine(s) loaded | 🔌 Not Connected"
                } else {
                    "No medicines added yet"
                }
            }
        }
    }
}
