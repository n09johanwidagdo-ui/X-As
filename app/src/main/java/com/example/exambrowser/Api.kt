package com.example.exambrowser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Klien sederhana ke Web App Google Apps Script (semua lewat GET agar redirect aman). */
object Api {
    private val io = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    fun deviceId(ctx: Context): String =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    fun async(params: Map<String, String>, cb: ((JSONObject) -> Unit)? = null) {
        io.execute {
            val result = try {
                get(params)
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", "Tidak dapat terhubung ke server.")
            }
            if (cb != null) main.post { cb(result) }
        }
    }

    private fun get(params: Map<String, String>): JSONObject {
        val q = params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val base = BuildConfig.GAS_URL
        val conn = URL(base + (if (base.contains("?")) "&" else "?") + q)
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 25000
        conn.instanceFollowRedirects = true
        try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            return JSONObject(stream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
