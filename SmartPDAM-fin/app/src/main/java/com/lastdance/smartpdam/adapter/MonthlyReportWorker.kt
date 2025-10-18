package com.lastdance.smartpdam.adapter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.lastdance.smartpdam.R
import kotlinx.coroutines.tasks.await
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MonthlyReportWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_PERUMAHAN_ID = "PERUMAHAN_ID"
        const val KEY_RUMAH_ID = "RUMAH_ID"
        private const val TAG = "MonthlyReportWorker"
    }

    override suspend fun doWork(): Result {
        // Ambil data yang kita kirim saat menjadwalkan worker
        val perumahanId = inputData.getString(KEY_PERUMAHAN_ID) ?: return Result.failure()
        val rumahId = inputData.getString(KEY_RUMAH_ID) ?: return Result.failure()

        val today = LocalDate.now()
        val isLastDayOfMonth = today.dayOfMonth == today.lengthOfMonth()

        // Jalankan hanya jika hari ini adalah hari terakhir dalam bulan ini
        if (!isLastDayOfMonth) {
            Log.d(TAG, "Bukan hari terakhir bulan, pekerjaan dilewati.")
            return Result.success() // Pekerjaan selesai tanpa melakukan apa-apa
        }

        Log.d(TAG, "Hari terakhir bulan terdeteksi! Memulai proses ekspor...")

        // Tentukan nama dokumen bulan ini, misal: "08-2025"
        val monthFormatter = DateTimeFormatter.ofPattern("MM-yyyy")
        val documentId = today.format(monthFormatter)

        return try {
            // Mengambil data dari Firestore menggunakan .get()
            val document = Firebase.firestore
                .collection("perumahan").document(perumahanId)
                .collection("rumah").document(rumahId)
                .collection("konsumsi").document(documentId)
                .get()
                .await() // Menggunakan await() dari coroutine

            if (document != null && document.exists()) {
                val data = document.data
                if (data != null && data.isNotEmpty()) {
                    val fileName = "Laporan_Otomatis_${rumahId}_${documentId}.xlsx"
                    createExcelFile(data, fileName, applicationContext)
                    // Kirim notifikasi ke pengguna setelah berhasil
                    sendSuccessNotification(fileName, applicationContext)
                    Log.d(TAG, "Ekspor berhasil. File disimpan: $fileName")
                    Result.success()
                } else {
                    Log.w(TAG, "Dokumen $documentId ada tapi kosong.")
                    Result.success() // Tetap sukses karena prosesnya tidak error
                }
            } else {
                Log.w(TAG, "Dokumen $documentId tidak ditemukan.")
                Result.success() // Tetap sukses
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal saat ekspor otomatis", e)
            Result.failure()
        }
    }

    // Fungsi createExcelFile disalin ke sini dengan sedikit modifikasi
    private fun createExcelFile(data: Map<String, Any>, fileName: String, context: Context) {
        val HARGA_PER_M3 = 6750.0
        val LITER_PER_M3 = 1000.0

        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Laporan Konsumsi")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("Tanggal")
            headerRow.createCell(1).setCellValue("Konsumsi (m³)")

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

            val totalConsumptionM3 = totalConsumptionLiter / LITER_PER_M3
            sheet.createRow(rowIndex++)
            val totalRow = sheet.createRow(rowIndex++)
            totalRow.createCell(0).setCellValue("Total Penggunaan (m³)")
            totalRow.createCell(1).setCellValue(totalConsumptionM3)
            val billRow = sheet.createRow(rowIndex)
            billRow.createCell(0).setCellValue("Estimasi Tagihan (Rp)")
            billRow.createCell(1).setCellValue(totalConsumptionM3 * HARGA_PER_M3)

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                val outputStream: OutputStream? = resolver.openOutputStream(uri)
                outputStream.use { stream -> workbook.write(stream) }
            }
            workbook.close()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat file Excel di Worker", e)
            // Error dilempar agar WorkManager tahu bahwa pekerjaan gagal
            throw e
        }
    }

    // Fungsi untuk membuat notifikasi
    private fun sendSuccessNotification(fileName: String, context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "monthly_report_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Laporan Bulanan Otomatis", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Laporan Berhasil Disimpan")
            .setContentText("File '$fileName' telah disimpan di folder Downloads.")
            .setSmallIcon(R.drawable.ic_notification) // GANTI dengan ikon notifikasi Anda!
            .setAutoCancel(true)
            .build()

        notificationManager.notify(101, notification)
    }
}