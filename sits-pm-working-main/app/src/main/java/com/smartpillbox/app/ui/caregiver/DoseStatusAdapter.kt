package com.smartpillbox.app.ui.caregiver

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartpillbox.app.R
import com.smartpillbox.app.data.model.DoseItem

class DoseStatusAdapter(
    private val items: List<DoseItem>
) : RecyclerView.Adapter<DoseStatusAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDoseMedicineName)
        val tvDosage: TextView = view.findViewById(R.id.tvDoseDosage)
        val tvTime: TextView = view.findViewById(R.id.tvDoseTime)
        val tvStatus: TextView = view.findViewById(R.id.tvDoseStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dose_status, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.medicineName
        holder.tvDosage.text = item.dosage
        holder.tvTime.text = item.scheduledTime
        holder.tvStatus.text = item.status.uppercase()
        holder.tvStatus.setTextColor(
            when (item.status) {
                "taken" -> Color.parseColor("#4CAF50")
                "missed" -> Color.parseColor("#F44336")
                else -> Color.parseColor("#FF9800")
            }
        )
    }

    override fun getItemCount(): Int = items.size
}