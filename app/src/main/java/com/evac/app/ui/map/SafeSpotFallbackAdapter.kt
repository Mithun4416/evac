package com.evac.app.ui.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.evac.app.R
import com.evac.app.db.SafeSpotEntity
import com.evac.app.util.HaversineUtil

/**
 * RecyclerView adapter for the "No-Map" Haversine fallback list.
 * Displays safe spots sorted by proximity with distance and cardinal direction.
 */
class SafeSpotFallbackAdapter :
    ListAdapter<SafeSpotFallbackAdapter.SpotWithDistance, SafeSpotFallbackAdapter.ViewHolder>(DIFF) {

    /**
     * Wrapper that pairs a [SafeSpotEntity] with its pre-calculated distance and bearing.
     */
    data class SpotWithDistance(
        val spot: SafeSpotEntity,
        val distanceKm: Double,
        val bearing: Double
    )

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SpotWithDistance>() {
            override fun areItemsTheSame(a: SpotWithDistance, b: SpotWithDistance) =
                a.spot.id == b.spot.id

            override fun areContentsTheSame(a: SpotWithDistance, b: SpotWithDistance) =
                a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_safespot_fallback, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.iv_spot_icon)
        private val tvName: TextView = view.findViewById(R.id.tv_spot_name)
        private val tvType: TextView = view.findViewById(R.id.tv_spot_type)
        private val tvDistance: TextView = view.findViewById(R.id.tv_spot_distance)
        private val tvDirection: TextView = view.findViewById(R.id.tv_spot_direction)

        fun bind(item: SpotWithDistance) {
            tvName.text = item.spot.name
            tvType.text = item.spot.type
            tvDistance.text = HaversineUtil.formatDistance(item.distanceKm)
            tvDirection.text = HaversineUtil.bearingToCardinal(item.bearing)

            // Type-specific icon
            val iconRes = when (item.spot.type.uppercase()) {
                "MEDICAL"  -> R.drawable.ic_spot_medical
                "SHELTER"  -> R.drawable.ic_spot_shelter
                "FOOD"     -> R.drawable.ic_spot_food
                else       -> R.drawable.ic_spot_default
            }
            ivIcon.setImageDrawable(ContextCompat.getDrawable(itemView.context, iconRes))
        }
    }
}
