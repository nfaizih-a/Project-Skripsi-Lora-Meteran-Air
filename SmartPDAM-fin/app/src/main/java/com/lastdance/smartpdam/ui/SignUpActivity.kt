package com.lastdance.smartpdam.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lastdance.smartpdam.R
import com.lastdance.smartpdam.databinding.ActivitySignupBinding

class SignUpActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        binding.buttonregis.setOnClickListener {
            val email = binding.emailregis.text.toString()
            val password = binding.passwordregis.text.toString()
            val nama = binding.namaregis.text.toString()
            val confirmPassword = binding.confirmpasswordregis.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty() && nama.isNotEmpty()) {
                if (password == confirmPassword) {
                    firebaseAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener {
                            if (it.isSuccessful) {
                                val uid = it.result.user?.uid // ✅ gunakan dari hasil registrasi langsung
                                val db = FirebaseFirestore.getInstance()
                                val userMap = hashMapOf(
                                    "nama" to nama,
                                    "email" to email
                                )

                                uid?.let { userId ->
                                    db.collection("users").document(userId).set(userMap)
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Registrasi berhasil!", Toast.LENGTH_SHORT).show()
                                            val intent = Intent(this, LoginActivity::class.java)
                                            startActivity(intent)
                                            finish()
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(this, "Gagal menyimpan nama: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                } ?: run {
                                    Toast.makeText(this, "UID tidak ditemukan", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this, it.exception?.message ?: "Registrasi gagal", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "Password tidak sesuai", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Kolom tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }

        binding.loginRedirect.setOnClickListener {
            val loginIntent = Intent(this, LoginActivity::class.java)
            startActivity(loginIntent)
        }
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}