package com.example.exambrowser

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val etNama = findViewById<EditText>(R.id.etNama)
        val etNis = findViewById<EditText>(R.id.etNis)
        val etKode = findViewById<EditText>(R.id.etKode)
        val btn = findViewById<Button>(R.id.btnMulai)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        val prefs = getSharedPreferences("peserta", MODE_PRIVATE)
        etNama.setText(prefs.getString("nama", ""))
        etNis.setText(prefs.getString("nis", ""))

        btn.setOnClickListener {
            val nama = etNama.text.toString().trim()
            val nis = etNis.text.toString().trim()
            val kode = etKode.text.toString().trim()

            if (nama.isEmpty() || nis.isEmpty()) {
                tvStatus.text = "Nama dan NIS wajib diisi."
                return@setOnClickListener
            }
            if (BuildConfig.GAS_URL.contains("GANTI")) {
                tvStatus.text = "URL server belum dikonfigurasi (secret GAS_URL)."
                return@setOnClickListener
            }

            btn.isEnabled = false
            tvStatus.setTextColor(0xFF6B7280.toInt())
            tvStatus.text = "Menghubungi server..."

            Api.async(
                mapOf(
                    "action" to "start", "code" to kode, "nis" to nis, "nama" to nama,
                    "device" to Api.deviceId(this), "model" to Build.MODEL,
                    "app" to BuildConfig.VERSION_NAME
                )
            ) { r ->
                btn.isEnabled = true
                if (r.optBoolean("ok")) {
                    prefs.edit().putString("nama", nama).putString("nis", nis).apply()
                    tvStatus.text = ""
                    startActivity(
                        Intent(this, ExamActivity::class.java)
                            .putExtra(ExamActivity.EXTRA_URL, r.optString("url"))
                            .putExtra(ExamActivity.EXTRA_TITLE, r.optString("title", "Ujian"))
                            .putExtra(ExamActivity.EXTRA_HOSTS, r.optString("hosts"))
                            .putExtra(ExamActivity.EXTRA_MAX, r.optInt("maxViolations", 3))
                            .putExtra(ExamActivity.EXTRA_NAMA, nama)
                            .putExtra(ExamActivity.EXTRA_NIS, nis)
                    )
                } else {
                    tvStatus.setTextColor(0xFFB91C1C.toInt())
                    tvStatus.text = r.optString("error", "Gagal memulai ujian.")
                }
            }
        }
    }
}
