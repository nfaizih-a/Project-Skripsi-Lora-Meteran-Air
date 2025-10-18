package com.lastdance.smartpdam.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.lastdance.smartpdam.adapter.RumahAdapter
import com.lastdance.smartpdam.adapter.RumahModel
import com.lastdance.smartpdam.databinding.ActivityRumahBinding

class RumahActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRumahBinding
    private lateinit var adapter: RumahAdapter
    private val listRumah = mutableListOf<RumahModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRumahBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val namaPerumahan = intent.getStringExtra("namaPerumahan") ?: return

        adapter = RumahAdapter(listRumah, namaPerumahan,this)
        binding.rvRumah.layoutManager = GridLayoutManager(this, 2)
        //  binding.rvRumah.layoutManager = LinearLayoutManager(this)
        binding.rvRumah.adapter = adapter

        val db = FirebaseFirestore.getInstance()
        db.collection("perumahan")
            .document(namaPerumahan)
            .collection("rumah")
            .get()
            .addOnSuccessListener { result ->
                listRumah.clear()
                for (doc in result) {
                    val nama = doc.getString("nama") ?: doc.id
                    listRumah.add(RumahModel(id = doc.id, nama = nama))
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat rumah", Toast.LENGTH_SHORT).show()
            }
    }
}