package com.music.zenith

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

/**
 * Halaman laporan crash. Hanya memakai View biasa dan membaca laporan dari file,
 * jadi tidak bergantung pada state app yang baru saja crash. Berjalan di proses ":crash".
 */
class CrashActivity : Activity() {
    private var report = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(CrashReporter.EXTRA_PATH)
        report = runCatching { File(path.orEmpty()).readText() }
            .getOrElse { "Laporan tidak bisa dibaca: $it" }
        val root = buildUi()
        setContentView(root)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Proses ":crash" tidak dibutuhkan lagi setelah halaman ini ditutup.
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121212.toInt())
            setPadding(pad, pad, pad, pad)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        root.addView(
            TextView(this).apply {
                text = "Zenith berhenti karena error"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFFFFFFF.toInt())
            },
        )
        val summary = report.lineSequence().firstOrNull { it.startsWith("Error: ") }?.removePrefix("Error: ")
        root.addView(
            TextView(this).apply {
                text = summary.orEmpty()
                textSize = 13f
                setTextColor(0xFFFF8A80.toInt())
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) },
        )
        root.addView(
            TextView(this).apply {
                text = "Salin atau bagikan laporan di bawah ini ke pengembang. Isinya error lengkap, " +
                    "spesifikasi perangkat, memori, baterai, dan jaringan."
                textSize = 12f
                setTextColor(0xFFB0B0B0.toInt())
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) },
        )

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, onClick: () -> Unit) {
            val b = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 12f
                maxLines = 1
                setOnClickListener { onClick() }
            }
            row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        button("Salin") { copyReport() }
        button("Bagikan") { shareReport() }
        button("Buka ulang") { restartApp() }
        button("Tutup") { finish() }
        root.addView(
            row,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) },
        )

        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF1E1E1E.toInt()) }
        scroll.addView(
            TextView(this).apply {
                text = report
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(0xFFE0E0E0.toInt())
                setTextIsSelectable(true)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            },
        )
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) },
        )
        return root
    }

    private fun copyReport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Zenith crash report", report))
        Toast.makeText(this, "Laporan disalin", Toast.LENGTH_SHORT).show()
    }

    private fun shareReport() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Zenith crash report")
            // Dipotong supaya aman dari batas ukuran data antar proses.
            putExtra(Intent.EXTRA_TEXT, report.take(100_000))
        }
        startActivity(Intent.createChooser(send, "Bagikan laporan"))
    }

    private fun restartApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launch)
        finish()
    }
}