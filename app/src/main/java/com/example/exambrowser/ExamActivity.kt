package com.example.exambrowser

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class ExamActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_HOSTS = "hosts"
        const val EXTRA_MAX = "max"
        const val EXTRA_NAMA = "nama"
        const val EXTRA_NIS = "nis"
    }

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var errorBox: View
    private lateinit var lockBox: View

    private lateinit var examUrl: String
    private var hosts: List<String> = emptyList()
    private var maxViolations = 3
    private var nama = ""
    private var nis = ""
    private lateinit var deviceId: String

    private var violations = 0
    private var lastViolation = 0L
    private var suppress = false   // true saat dialog kode tampil (hilang fokus yang sah)
    private var running = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE or            // blok screenshot & rekam layar
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        examUrl = url
        nama = intent.getStringExtra(EXTRA_NAMA) ?: ""
        nis = intent.getStringExtra(EXTRA_NIS) ?: ""
        maxViolations = intent.getIntExtra(EXTRA_MAX, 3)
        deviceId = Api.deviceId(this)

        val examHost = Uri.parse(examUrl).host?.lowercase()
        hosts = ((intent.getStringExtra(EXTRA_HOSTS) ?: "")
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() } +
            listOfNotNull(examHost)).distinct()

        setContentView(R.layout.activity_exam)
        findViewById<TextView>(R.id.tvTitle).text = intent.getStringExtra(EXTRA_TITLE) ?: "Ujian"
        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        errorBox = findViewById(R.id.errorBox)
        lockBox = findViewById(R.id.lockBox)

        findViewById<Button>(R.id.btnExit).setOnClickListener {
            askCode("Kode keluar", "exit") { exitExam() }
        }
        findViewById<Button>(R.id.btnRetry).setOnClickListener {
            errorBox.visibility = View.GONE
            web.loadUrl(examUrl)
        }
        findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            askCode("Kode pengawas", "unlock") { unlock() }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@ExamActivity,
                    "Tombol kembali dinonaktifkan selama ujian.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        setupWebView()
        hideSystemBars()
        enterLockTask()
        web.loadUrl(examUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(false)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            userAgentString = userAgentString + " ExamBrowser/" + BuildConfig.VERSION_NAME
        }
        web.isLongClickable = false
        web.setOnLongClickListener { true }
        web.isHapticFeedbackEnabled = false

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                if (isAllowed(req.url)) return false
                Toast.makeText(this@ExamActivity, "Halaman ini tidak diizinkan.", Toast.LENGTH_SHORT).show()
                report("BLOCKED_URL", req.url.toString())
                return true
            }

            override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(v: WebView, url: String) {
                progress.visibility = View.GONE
            }

            override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) errorBox.visibility = View.VISIBLE
            }

            override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: SslError) {
                h.cancel()
                errorBox.visibility = View.VISIBLE
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                progress.progress = p
            }

            override fun onCreateWindow(v: WebView, d: Boolean, u: Boolean, m: Message?): Boolean = false
        }
    }

    private fun isAllowed(uri: Uri): Boolean {
        if (uri.scheme != "https") return false
        val h = uri.host?.lowercase() ?: return false
        return hosts.any { h == it || h.endsWith(".$it") }
    }

    // ---------- Kiosk ----------

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun isDeviceOwner(): Boolean =
        getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(packageName)

    private fun enterLockTask() {
        try {
            if (isDeviceOwner()) {
                val dpm = getSystemService(DevicePolicyManager::class.java)
                val admin = ComponentName(this, AdminReceiver::class.java)
                dpm.setLockTaskPackages(admin, arrayOf(packageName))
                if (Build.VERSION.SDK_INT >= 28) {
                    dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                }
            }
            val am = getSystemService(ActivityManager::class.java)
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) startLockTask()
        } catch (_: Exception) {
        }
    }

    // ---------- Deteksi pelanggaran ----------

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!::web.isInitialized) return
        if (hasFocus) hideSystemBars() else violation("FOCUS_LOST")
    }

    override fun onResume() {
        super.onResume()
        if (!::web.isInitialized) return
        hideSystemBars()
        if (isDeviceOwner()) enterLockTask()
    }

    private fun violation(type: String) {
        if (!running || suppress) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastViolation < 2000) return
        lastViolation = now
        violations++
        report(type, "ke-$violations")
        if (maxViolations in 1..violations) {
            lockBox.visibility = View.VISIBLE
            web.visibility = View.INVISIBLE
        } else {
            Toast.makeText(
                this,
                "Peringatan: jangan keluar dari halaman ujian ($violations/$maxViolations)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun unlock() {
        violations = 0
        lockBox.visibility = View.GONE
        web.visibility = View.VISIBLE
        hideSystemBars()
    }

    private fun report(event: String, detail: String) {
        Api.async(
            mapOf(
                "action" to "log", "device" to deviceId, "nis" to nis,
                "nama" to nama, "event" to event, "detail" to detail
            )
        )
    }

    // ---------- Kode keluar / kode pengawas (diverifikasi di server) ----------

    private fun askCode(title: String, purpose: String, onOk: () -> Unit) {
        suppress = true
        val et = EditText(this).apply {
            hint = "Kode"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(et)
            .setCancelable(false)
            .setPositiveButton("OK", null)
            .setNegativeButton("Batal") { _, _ ->
                suppress = false
                hideSystemBars()
            }
            .create()
        dlg.setOnShowListener {
            val ok = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            ok.setOnClickListener {
                val code = et.text.toString().trim()
                if (code.isEmpty()) return@setOnClickListener
                ok.isEnabled = false
                Api.async(
                    mapOf(
                        "action" to "verifyExit", "code" to code, "purpose" to purpose,
                        "device" to deviceId, "nis" to nis, "nama" to nama
                    )
                ) { r ->
                    ok.isEnabled = true
                    if (r.optBoolean("ok")) {
                        suppress = false
                        dlg.dismiss()
                        onOk()
                    } else {
                        et.error = r.optString("error", "Kode salah.")
                    }
                }
            }
        }
        dlg.show()
    }

    private fun exitExam() {
        running = false
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        web.clearCache(true)
        try {
            stopLockTask()
        } catch (_: Exception) {
        }
        finish()
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
