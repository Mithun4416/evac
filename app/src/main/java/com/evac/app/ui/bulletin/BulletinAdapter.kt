package com.evac.app.ui.bulletin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

// RecyclerView adapter for bulletins
class BulletinAdapter : RecyclerView.Adapter<BulletinAdapter.BulletinViewHolder>() {

    class BulletinViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BulletinViewHolder {
        // TODO: Inflate bulletin item layout
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return BulletinViewHolder(view)
    }

    override fun onBindViewHolder(holder: BulletinViewHolder, position: Int) {
        // TODO: Bind bulletin data
    }

    override fun getItemCount(): Int = 0
}
