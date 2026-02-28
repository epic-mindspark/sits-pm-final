package com.smartpillbox.app.ui.scan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartpillbox.app.R
import com.smartpillbox.app.data.model.Medicine

class MedicineAdapter(
    private val medicines: MutableList<Medicine>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<MedicineAdapter.MedicineViewHolder>() {

    class MedicineViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvMedicineName)
        val tvDosage: TextView = view.findViewById(R.id.tvDosage)
        val tvFrequency: TextView = view.findViewById(R.id.tvFrequency)
        val tvTiming: TextView? = view.findViewById(R.id.tvTiming)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medicine, parent, false)
        return MedicineViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedicineViewHolder, position: Int) {
        val medicine = medicines[position]
        holder.tvName.text = medicine.name
        holder.tvDosage.text = "Dosage: ${medicine.dosage}"
        holder.tvFrequency.text = "Frequency: ${medicine.frequency}"
        holder.tvTiming?.text = "Timing: ${medicine.timing}"
        holder.btnRemove.setOnClickListener {
            onRemove(position)
        }
    }

    override fun getItemCount(): Int = medicines.size

    fun removeItem(position: Int) {
        medicines.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, medicines.size)
    }
}