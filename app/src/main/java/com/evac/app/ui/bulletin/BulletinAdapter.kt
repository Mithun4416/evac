package com.evac.app.ui.bulletin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.evac.app.R
import com.evac.app.db.MessageEntity

class BulletinAdapter : RecyclerView.Adapter<BulletinAdapter.ViewHolder>() {

    private var items = listOf<MessageEntity>()

    fun submitList(list: List<MessageEntity>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bulletin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvType = view.findViewById<TextView>(R.id.tvType)
        private val tvBody = view.findViewById<TextView>(R.id.tvBody)
        private val tvTime = view.findViewById<TextView>(R.id.tvTime)

        fun bind(message: MessageEntity) {
            // Type label
            tvType.text = when (message.type) {
                "BULLETIN" -> "📢 BULLETIN"
                "ACK"      -> "✅ ACK — Response received"
                else       -> message.type
            }

            // Body
            tvBody.text = message.body ?: "No content"

            // Time ago
            val minutesAgo = (System.currentTimeMillis() - message.timestamp) / 60_000
            tvTime.text = "$minutesAgo min ago"
        }
    }
}