package com.lastdance.smartpdam.adapter

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lastdance.smartpdam.R
import com.lastdance.smartpdam.ui.VisualisasiActivity

class RumahAdapter(
    private val list: List<RumahModel>,
    private val perumahanId: String,
    private val activity: Activity
) : RecyclerView.Adapter<RumahAdapter.RumahViewHolder>() {

    inner class RumahViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textNama: TextView = itemView.findViewById(R.id.rec_rumah)

        init {
            itemView.setOnClickListener {
                val rumah = list[adapterPosition]
                val intent = Intent(activity, VisualisasiActivity::class.java)
                intent.putExtra("nomorRumah", rumah.id) // Bukan rumah.nama
                intent.putExtra("namaPerumahan", "griya_alam_sejati") // jika perlu
                activity.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RumahViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycle_norumah, parent, false)
        return RumahViewHolder(view)
    }

    override fun onBindViewHolder(holder: RumahViewHolder, position: Int) {
        val rumah = list[position]
        holder.textNama.text = rumah.nama
    }

    override fun getItemCount(): Int = list.size
}