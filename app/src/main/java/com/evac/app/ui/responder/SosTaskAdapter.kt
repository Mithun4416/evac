package com.evac.app.ui.responder

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.evac.app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class SosTaskAdapter(
    private val onNavigateClick: (SosTask) -> Unit,
    private val onResolveClick: (SosTask) -> Unit
) : RecyclerView.Adapter<SosTaskAdapter.TaskViewHolder>() {

    private val items = mutableListOf<SosTask>()

    fun submitList(newItems: List<SosTask>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sos_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardContainer = itemView.findViewById<MaterialCardView>(R.id.cardContainer)
        private val tvStatus = itemView.findViewById<TextView>(R.id.tvStatus)
        private val tvAssignedIndicator = itemView.findViewById<TextView>(R.id.tvAssignedIndicator)
        private val tvDistanceEta = itemView.findViewById<TextView>(R.id.tvDistanceEta)
        private val tvDetails = itemView.findViewById<TextView>(R.id.tvDetails)
        private val btnNavigate = itemView.findViewById<MaterialButton>(R.id.btnNavigate)
        private val btnResolved = itemView.findViewById<MaterialButton>(R.id.btnResolved)

        fun bind(task: SosTask) {
            tvStatus.text = task.status
            when (task.status) {
                "MEDICAL" -> {
                    tvStatus.setTextColor(Color.parseColor("#FF0040"))
                    tvStatus.setBackgroundColor(Color.parseColor("#330000"))
                }
                "TRAPPED" -> {
                    tvStatus.setTextColor(Color.parseColor("#FF9500"))
                    tvStatus.setBackgroundColor(Color.parseColor("#331A00"))
                }
                "HAZARD" -> {
                    tvStatus.setTextColor(Color.parseColor("#FFD600"))
                    tvStatus.setBackgroundColor(Color.parseColor("#333300"))
                }
                "SAFE" -> {
                    tvStatus.setTextColor(Color.parseColor("#00FF88"))
                    tvStatus.setBackgroundColor(Color.parseColor("#00331A"))
                }
                else -> {
                    tvStatus.setTextColor(Color.parseColor("#888899"))
                    tvStatus.setBackgroundColor(Color.parseColor("#222233"))
                }
            }

            if (task.isAssignedToMe) {
                tvAssignedIndicator.visibility = View.VISIBLE
                cardContainer.strokeColor = Color.parseColor("#00D4FF")
                cardContainer.strokeWidth = 3
            } else {
                tvAssignedIndicator.visibility = View.GONE
                cardContainer.strokeColor = Color.parseColor("#333344")
                cardContainer.strokeWidth = 1
            }

            val distKm = "%.1f".format(task.distanceMeters / 1000f)
            val etaStr = if (task.etaMinutes > 0) " · ${task.etaMinutes}m ETA" else ""
            tvDistanceEta.text = "$distKm km$etaStr"

            val details = StringBuilder("👥 ${task.peopleCount}")
            if (task.batteryPct != null) details.append("  |  🔋 ${task.batteryPct}%")
            if (!task.note.isNullOrBlank()) details.append("\n\"${task.note}\"")
            tvDetails.text = details.toString()

            btnNavigate.setOnClickListener { onNavigateClick(task) }
            btnResolved.setOnClickListener { onResolveClick(task) }
        }
    }
}
