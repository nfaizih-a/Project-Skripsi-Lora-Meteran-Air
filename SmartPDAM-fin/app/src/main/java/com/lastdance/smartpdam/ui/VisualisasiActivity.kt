package com.lastdance.smartpdam.ui

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.lastdance.smartpdam.R
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.github.mikephil.charting.components.AxisBase
import com.lastdance.smartpdam.adapter.MonthlyReportWorker
import java.util.concurrent.TimeUnit

class VisualisasiActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private lateinit var monthSpinner: Spinner
    private var monthList: MutableList<String> = mutableListOf()
    private var dataListener: ListenerRegistration? = null
    private lateinit var penggunaanAirText: TextView
    private lateinit var tagihanAirText: TextView
    private lateinit var namaPerum: TextView
    private lateinit var blokPerum: TextView
    private lateinit var exportButton: FloatingActionButton

    companion object {

        private const val HARGA_PER_M3 = 6750.0
        private const val LITER_PER_M3 = 1000.0
        private const val TAG = "VisualisasiActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visualisasi)

        barChart = findViewById(R.id.barChart)
        monthSpinner = findViewById(R.id.spinner_bulan)
        penggunaanAirText = findViewById(R.id.penggunaan_air)
        tagihanAirText = findViewById(R.id.tagihan_air)
        namaPerum = findViewById(R.id.nama_perum)
        blokPerum = findViewById(R.id.blok_perum)
        exportButton = findViewById(R.id.btn_export)

        fetchMonthsFromFirestore()
        scheduleAutoExportWorker() // Worker untuk laporan bulanan
        fetchHeaderInfo()
        monthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (monthList.isNotEmpty()) {
                    val selectedMonth = monthList[position]
                    loadDataForMonth(selectedMonth)
                }
            }
            override fun onNothingSelected(parentView: AdapterView<*>) {

            }
        }

        exportButton.setOnClickListener {
            exportCurrentMonthToExcel()
        }
    }
    private fun fetchHeaderInfo() {
        val perumahanId = intent.getStringExtra("namaPerumahan") ?: return
        val rumahId = intent.getStringExtra("nomorRumah") ?: return
        val db = FirebaseFirestore.getInstance()

        // Ambil nama perumahan dari field "nama"
        db.collection("perumahan").document(perumahanId)
            .get()
            .addOnSuccessListener { doc ->
                val namaPerumahan = doc.getString("nama") ?: perumahanId  // fallback ke ID
                namaPerum.text = "Perumahan    : " + namaPerumahan
            }
            .addOnFailureListener { e ->
                Log.e("HeaderInfo", "Gagal ambil nama perumahan", e)
                namaPerum.text = perumahanId
            }

        // Ambil nama rumah dari field "nama"
        db.collection("perumahan").document(perumahanId)
            .collection("rumah").document(rumahId)
            .get()
            .addOnSuccessListener { doc ->
                val namaRumah = doc.getString("nama") ?: rumahId // fallback ke ID
                blokPerum.text = "Blok/No             : " + namaRumah
            }
            .addOnFailureListener { e ->
                Log.e("HeaderInfo", "Gagal ambil nama rumah", e)
                blokPerum.text = rumahId
            }
    }
    private fun fetchMonthsFromFirestore() {
        val perumahanId = intent.getStringExtra("namaPerumahan") ?: return
        val rumahId = intent.getStringExtra("nomorRumah") ?: return

        FirebaseFirestore.getInstance()
            .collection("perumahan").document(perumahanId)
            .collection("rumah").document(rumahId)
            .collection("konsumsi")
            .get()
            .addOnSuccessListener { documents ->
                monthList.clear() // Bersihkan list sebelum diisi ulang
                for (document in documents) {
                    monthList.add(document.id)
                }
                monthList.sortDescending() // Urutkan bulan terbaru di atas

                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, monthList)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                monthSpinner.adapter = adapter

                if (monthList.isNotEmpty()) {
                    loadDataForMonth(monthList[0])
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal mengambil data bulan", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Gagal mengambil daftar bulan", it)
            }
    }
    private fun scheduleAutoExportWorker() {
        val perumahanId = intent.getStringExtra("namaPerumahan")
        val rumahId = intent.getStringExtra("nomorRumah")   

        if (perumahanId == null || rumahId == null) {
            Log.e("ScheduleWorker", "ID Perumahan atau Rumah tidak ditemukan, penjadwalan dibatalkan.")
            return
        }

        // Siapkan data untuk dikirim ke Worker
        val inputData = Data.Builder()
            .putString(MonthlyReportWorker.KEY_PERUMAHAN_ID, perumahanId)
            .putString(MonthlyReportWorker.KEY_RUMAH_ID, rumahId)
            .build()

        // Buat request untuk dijalankan berulang setiap 24 jam
        val autoExportRequest = PeriodicWorkRequestBuilder<MonthlyReportWorker>(24, TimeUnit.HOURS)
            .setInputData(inputData)
            .build()

        // Jadwalkan pekerjaan dengan nama unik.
        // ExistingPeriodicWorkPolicy.KEEP berarti jika pekerjaan dengan nama ini sudah ada, jangan buat yang baru.
        // Ini mencegah penjadwalan duplikat setiap kali activity dibuka.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AutoMonthlyExport",
            ExistingPeriodicWorkPolicy.KEEP,
            autoExportRequest
        )

        Log.d("ScheduleWorker", "Pekerjaan ekspor otomatis telah dijadwalkan.")
        // Anda bisa menampilkan Toast di sini untuk memberitahu pengguna bahwa fitur aktif
        //Toast.makeText(this, "Fitur laporan otomatis bulanan telah diaktifkan.", Toast.LENGTH_LONG).show()
    }

    private fun loadDataForMonth(month: String) {
        val perumahanId = intent.getStringExtra("namaPerumahan") ?: return
        val rumahId = intent.getStringExtra("nomorRumah") ?: return

        dataListener?.remove()

        dataListener = FirebaseFirestore.getInstance()
            .collection("perumahan").document(perumahanId)
            .collection("rumah").document(rumahId)
            .collection("konsumsi").document(month)
            .addSnapshotListener { doc, e ->
                if (e != null) {
                    Toast.makeText(this, "Gagal memuat data", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Gagal listener snapshot", e)
                    return@addSnapshotListener
                }
                if (doc != null && doc.exists()) {
                    val dataMap = doc.data
                    if (dataMap != null) {
                        updateUIWithData(dataMap)
                    }
                } else {
                    barChart.clear()
                    barChart.invalidate()
                    penggunaanAirText.text = "Total penggunaan air  : 0 Liter"
                    tagihanAirText.text = "Estimasi tagihan air        : Rp 0,00"
                    Toast.makeText(this, "Data untuk bulan $month tidak ditemukan", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Memisahkan logika update UI agar bisa digunakan kembali
    private fun updateUIWithData(data: Map<String, Any>) {
        val entries = ArrayList<BarEntry>()
        var totalLiter = 0f

        val sortedData = data.toSortedMap(compareBy { it.toIntOrNull() ?: 0 })

        for ((day, value) in sortedData) {
            val dayFloat = day.toFloatOrNull() ?: continue
            val valueLiter = (value as? Number)?.toFloat() ?: 0f

            val valueM3 = valueLiter / LITER_PER_M3.toFloat()
            entries.add(BarEntry(dayFloat, valueM3))

            totalLiter += valueLiter
        }

        // Konversi total dari Liter ke m³
        val totalM3 = totalLiter / LITER_PER_M3.toFloat()

        // Tampilkan total penggunaan dalam m³ dengan 4 angka desimal
        penggunaanAirText.text = "Total penggunaan air    : ${String.format("%.4f", totalM3)} m³"

        // Hitung tagihan berdasarkan harga per m³
        val tagihan = totalM3 * HARGA_PER_M3
        tagihanAirText.text = "Estimasi tagihan air        : Rp ${String.format("%,.2f", tagihan)}"

        // Ubah label dataset chart
        val dataSet = BarDataSet(entries, " ")
        dataSet.color = resources.getColor(R.color.blue_text, theme)
        dataSet.valueTextSize = 8f

        // Formatter untuk menampilkan nilai di atas bar dengan 3 angka desimal
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getBarLabel(barEntry: BarEntry?): String {
                return String.format("%.3f", barEntry?.y ?: 0f)
            }
        }
        // Ukuran teks bisa disesuaikan
        dataSet.valueTextSize = 8f
        barChart.data = BarData(dataSet)
        barChart.description.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.setFitBars(true)

        //FORMAT SUMBU X (Hari) Menjadi Bilangan Bulat
        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                return value.toInt().toString()
            }
        }

        xAxis.setLabelCount(entries.size)
        xAxis.labelRotationAngle = -45f

        barChart.animateY(1000, Easing.EaseInOutQuad)
        barChart.invalidate()
    }

    private fun exportCurrentMonthToExcel() {
        if (monthSpinner.selectedItem == null) {
            Toast.makeText(this, "Pilih bulan terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedMonth = monthSpinner.selectedItem.toString()
        fetchDataForExport(selectedMonth)
    }

    private fun fetchDataForExport(month: String) {
        val perumahanId = intent.getStringExtra("namaPerumahan") ?: return
        val rumahId = intent.getStringExtra("nomorRumah") ?: return

        // Menggunakan .get() untuk mengambil data sekali saja, bukan listener
        FirebaseFirestore.getInstance()
            .collection("perumahan").document(perumahanId)
            .collection("rumah").document(rumahId)
            .collection("konsumsi").document(month)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val data = document.data
                    if (data != null && data.isNotEmpty()) {
                        val fileName = "Laporan_Konsumsi_${rumahId}_${month.replace("_", "-")}.xlsx"
                        createExcelFile(data, fileName)
                    } else {
                        Toast.makeText(this, "Tidak ada data untuk diekspor", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Dokumen tidak ditemukan untuk diekspor", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal mengambil data untuk ekspor", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Gagal .get() data untuk ekspor", it)
            }
    }

    private fun createExcelFile(data: Map<String, Any>, fileName: String) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Laporan Konsumsi")

            // Header - DIUBAH
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("Tanggal")
            headerRow.createCell(1).setCellValue("Konsumsi (m³)") // Label diubah ke m³

            // Mengisi Data
            val sortedData = data.toSortedMap(compareBy { it.toIntOrNull() ?: 0 })
            var rowIndex = 1
            var totalConsumptionLiter = 0.0
            for ((day, value) in sortedData) {
                val row = sheet.createRow(rowIndex++)
                row.createCell(0).setCellValue(day)

                val valueLiter = (value as? Number)?.toDouble() ?: 0.0
                val valueM3 = valueLiter / LITER_PER_M3
                row.createCell(1).setCellValue(valueM3)

                totalConsumptionLiter += valueLiter
            }

            // Konversi total ke m³
            val totalConsumptionM3 = totalConsumptionLiter / LITER_PER_M3

            // Baris Total dan Tagihan
            sheet.createRow(rowIndex++) // Baris kosong
            val totalRow = sheet.createRow(rowIndex++)
            totalRow.createCell(0).setCellValue("Total Penggunaan (m³)")
            totalRow.createCell(1).setCellValue(totalConsumptionM3)

            val billRow = sheet.createRow(rowIndex)
            billRow.createCell(0).setCellValue("Estimasi Tagihan (Rp)")
            // Hitung tagihan berdasarkan total m³ dan harga per m³
            billRow.createCell(1).setCellValue(totalConsumptionM3 * HARGA_PER_M3)


            // Menyimpan file menggunakan MediaStore (kode tetap sama)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                val outputStream: OutputStream? = resolver.openOutputStream(uri)
                outputStream.use { stream ->
                    workbook.write(stream)
                }
                Toast.makeText(this, "File disimpan di folder Downloads", Toast.LENGTH_LONG).show()
            }
            workbook.close()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuat file Excel", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Gagal membuat file Excel", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dataListener?.remove()
    }
}