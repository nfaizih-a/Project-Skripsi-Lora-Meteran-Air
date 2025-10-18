package com.lastdance.smartpdam.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lastdance.smartpdam.R

class PerumahanAdapter(
    private val list: List<Perumahan>,
    private val onItemClick: (Perumahan) -> Unit
) : RecyclerView.Adapter<PerumahanAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val namaTextView: TextView = itemView.findViewById(R.id.text_rumah)

        fun bind(perumahan: Perumahan) {
            namaTextView.text = perumahan.nama
            itemView.setOnClickListener {
                onItemClick(perumahan)
            }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_main, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size
}