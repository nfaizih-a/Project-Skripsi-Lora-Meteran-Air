package com.lastdance.smartpdam.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lastdance.smartpdam.R
import com.lastdance.smartpdam.adapter.Perumahan
import com.lastdance.smartpdam.adapter.PerumahanAdapter
import com.lastdance.smartpdam.databinding.ActivityMainBinding
import androidx.core.view.isVisible

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private val perumahanList = mutableListOf<Perumahan>()
    private lateinit var adapter: PerumahanAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        firebaseAuth = FirebaseAuth.getInstance()

        setContentView(binding.root)
        setupRecyclerView()

        showLoading(true)
        loadPerumahanData()
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        // Ambil nama dari Intent
        val namaUser = intent.getStringExtra("namaUser")

        // Jika ada nama, simpan ke SharedPreferences
        namaUser?.let {
            val sharedPref = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
            sharedPref.edit().putString("namaUser", it).apply()
        }

        // Ambil dari SharedPreferences (untuk jaga-jaga jika app dibuka ulang)
        val sharedPref = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        val savedName = sharedPref.getString("namaUser", "Pengguna")

        // Tampilkan di TextView
        binding.namaUser.text = "Halo, $savedName"

        // Tambahkan tombol logout (jika ingin)
        binding.buttonBack.setOnClickListener {
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle("Konfirmasi Logout")
            builder.setMessage("Apakah Anda yakin ingin logout?")
            builder.setPositiveButton("Ya") { dialog, _ ->
                firebaseAuth.signOut()

                // Hapus session
                sharedPref.edit().clear().apply()

                Toast.makeText(this, "Berhasil logout", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            builder.setNegativeButton("Tidak") { dialog, _ ->
                dialog.dismiss()
            }
            builder.show()
        }
    }
    private fun showLoading(loading: Boolean) {
        binding.proBar.isVisible = loading
        binding.rvMain.isVisible = !loading
        binding.emptyState?.isVisible = false // kalau ada emptyState
    }
    private fun setupRecyclerView() {
        adapter = PerumahanAdapter(perumahanList) { selected ->
            val intent = Intent(this, RumahActivity::class.java)
            intent.putExtra("namaPerumahan", selected.id)
            startActivity(intent)
        }

        binding.rvMain.layoutManager = LinearLayoutManager(this)
        binding.rvMain.adapter = adapter
    }
    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Tutup SplashActivity
    }
    private fun loadPerumahanData() {
        showLoading(true)

        db.collection("perumahan")
            .get()
            .addOnSuccessListener { documents ->
                perumahanList.clear()
                for (doc in documents) {
                    val nama = doc.getString("nama") ?: ""
                    val id = doc.id
                    perumahanList.add(Perumahan(id = id, nama = nama))
                }
                adapter.notifyDataSetChanged()

                showLoading(false)
                // Tampilkan empty state jika kosong
                if (perumahanList.isEmpty()) {
                    binding.rvMain.isVisible = false
                    binding.emptyState?.isVisible = true
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Gagal mengambil data: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Firestore error", e)
                // Opsional: tampilkan emptyState juga
                binding.emptyState?.isVisible = true
            }
    }
}