package com.music.zenith

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

private const val COLOR_BG = 0xFF0E0E10.toInt()
private const val COLOR_CARD = 0xFF1A1A1E.toInt()
private const val COLOR_BUTTON = 0xFF2A2A30.toInt()
private const val COLOR_TEXT = 0xFFF2F2F4.toInt()
private const val COLOR_MUTED = 0xFF9A9AA3.toInt()
private const val COLOR_ERROR = 0xFFFF8A80.toInt()
private const val COLOR_ACCENT = 0xFFFA3C55.toInt()

/**
 * Halaman laporan crash. Hanya memakai View biasa dan membaca laporan dari file,
 * jadi tidak bergantung pada state app yang baru saja crash. Berjalan di proses ":crash".
 *
 * Seluruh isi bisa di-scroll, sedangkan tombol aksi selalu menempel di bawah layar
 * sehingga tidak terdorong keluar oleh pesan error yang sangat panjang.
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

    // ---- Membaca isi laporan ----

    private fun section(title: String): String =
        report.substringAfter("\n== $title ==\n", "").substringBefore("\n== ")

    private fun value(section: String, key: String): String =
        section(section).lineSequence()
            .firstOrNull { it.startsWith("$key: ") }
            ?.removePrefix("$key: ")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: "-"

    private fun header(key: String): String =
        report.lineSequence().firstOrNull { it.startsWith("$key: ") }?.removePrefix("$key: ") ?: "-"

    private fun errorText(): String {
        val full = report.substringAfter("\nError: ", "").substringBefore("\n\n== ").trim()
        return full.ifEmpty { report.take(2_000) }
    }

    /** Daftar parameter yang sangat panjang diringkas supaya alasan error tetap terlihat. */
    private fun shortError(): String {
        val short = errorText()
            .replace(Regex("\\([^()]{80,}\\)"), "(…)")
            .replace("com.music.zenith.", "")
        return if (short.length <= 1_200) short else short.take(500) + "\n…\n" + short.takeLast(700)
    }

    private fun errorType(): String =
        errorText().substringBefore(':').substringBefore('\n').substringAfterLast('.').take(60)

    private fun location(): String {
        val trace = section("STACK TRACE").lineSequence().map { it.trim() }.toList()
        val own = trace.filter { it.startsWith("at com.music.zenith") }.take(4)
        val causes = trace.filter { it.startsWith("Caused by:") }.take(2)
        val lines = (own.ifEmpty { trace.filter { it.startsWith("at ") }.take(4) } + causes)
        return lines.joinToString("\n") { it.replace("com.music.zenith.", "").take(220) }
    }

    private fun deviceRows(): List<Pair<String, String>> = listOf(
        "Ponsel" to "${value("PERANGKAT", "Pabrikan")} ${value("PERANGKAT", "Model")}",
        "Merek / kode" to "${value("PERANGKAT", "Merek")} / ${value("PERANGKAT", "Device")}",
        "Chipset" to value("PERANGKAT", "SoC").let { if (it == "-") value("PERANGKAT", "Hardware") else it },
        "Arsitektur" to value("PERANGKAT", "ABI"),
        "Android" to value("SISTEM", "Android"),
        "Patch keamanan" to value("SISTEM", "Patch keamanan"),
        "Build sistem" to value("SISTEM", "Build sistem"),
        "Layar" to "${value("LAYAR", "Resolusi")}, ${value("LAYAR", "Refresh rate")}",
        "RAM" to value("MEMORI DAN PENYIMPANAN", "RAM"),
        "Penyimpanan" to value("MEMORI DAN PENYIMPANAN", "Penyimpanan"),
        "Baterai" to value("BATERAI DAN JARINGAN", "Baterai"),
        "Jaringan" to value("BATERAI DAN JARINGAN", "Jaringan"),
        "Bahasa" to value("SISTEM", "Bahasa"),
    )

    private fun appRows(): List<Pair<String, String>> = listOf(
        "Versi" to value("APLIKASI", "Versi"),
        "Jenis build" to value("APLIKASI", "Build"),
        "Paket" to value("APLIKASI", "Paket"),
        "Waktu crash" to header("Waktu"),
        "Thread" to header("Thread"),
        "Lama berjalan" to value("APLIKASI", "Umur proses"),
    )

    private fun summaryText(): String = buildString {
        append("ZENITH CRASH (ringkasan)\n\n")
        append(shortError()).append("\n\n")
        append(location()).append("\n\n")
        (appRows() + deviceRows()).forEach { append(it.first).append(": ").append(it.second).append('\n') }
    }

    // ---- Tampilan ----

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun spaced(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun card(parent: LinearLayout, title: String, body: LinearLayout.() -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(COLOR_CARD, 16)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        box.addView(label(title, 12f, COLOR_MUTED, bold = true))
        box.body()
        parent.addView(box, spaced(12))
    }

    private fun LinearLayout.rows(items: List<Pair<String, String>>) {
        items.forEach { (key, content) ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(
                label(key, 13f, COLOR_MUTED),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f),
            )
            row.addView(
                label(content, 13f, COLOR_TEXT).apply { setTextIsSelectable(true) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f),
            )
            addView(row, spaced(8))
        }
    }

    private fun code(value: String, color: Int) = label(value, 12f, color).apply {
        typeface = Typeface.MONOSPACE
        setTextIsSelectable(true)
    }

    private fun button(title: String, color: Int, onClick: () -> Unit) =
        label(title, 14f, COLOR_TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(color, 14), null)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun buildUi(): View {
        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        content.addView(label("Zenith berhenti karena error", 22f, COLOR_TEXT, bold = true))
        content.addView(
            label(
                "Salin laporan lalu kirim ke pengembang. Sertakan juga apa yang kamu lakukan sebelum crash.",
                13f,
                COLOR_MUTED,
            ),
            spaced(6),
        )

        card(content, "PENYEBAB") {
            addView(label(errorType(), 17f, COLOR_ERROR, bold = true), spaced(8))
            addView(code(shortError(), COLOR_TEXT), spaced(8))
        }
        val where = location()
        if (where.isNotEmpty()) {
            card(content, "LOKASI ERROR") { addView(code(where, COLOR_TEXT), spaced(8)) }
        }
        card(content, "PERANGKAT") { rows(deviceRows()) }
        card(content, "APLIKASI") { rows(appRows()) }
        card(content, "LAPORAN LENGKAP") {
            val full = code("", COLOR_TEXT).apply { visibility = View.GONE }
            lateinit var toggle: TextView
            toggle = button("Tampilkan", COLOR_BUTTON) {
                val show = full.visibility != View.VISIBLE
                if (show && full.text.isEmpty()) full.text = report
                full.visibility = if (show) View.VISIBLE else View.GONE
                toggle.text = if (show) "Sembunyikan" else "Tampilkan"
            }
            addView(toggle, spaced(10))
            addView(full, spaced(10))
        }

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = true }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8), pad, dp(12))
        }
        fun actionRow(vararg buttons: View) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.forEachIndexed { index, view ->
                row.addView(
                    view,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { if (index > 0) marginStart = dp(8) },
                )
            }
            actions.addView(row, spaced(8))
        }
        actionRow(
            button("Salin laporan", COLOR_ACCENT) { copy(report, "Laporan lengkap disalin") },
            button("Salin ringkasan", COLOR_BUTTON) { copy(summaryText(), "Ringkasan disalin") },
        )
        actionRow(
            button("Bagikan", COLOR_BUTTON) { shareReport() },
            button("Buka ulang", COLOR_BUTTON) { restartApp() },
            button("Tutup", COLOR_BUTTON) { finish() },
        )
        root.addView(actions)
        return root
    }

    private fun copy(value: String, message: String) {
        runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Zenith crash report", value))
        }.onSuccess {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Gagal menyalin, gunakan Bagikan", Toast.LENGTH_SHORT).show()
        }
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
