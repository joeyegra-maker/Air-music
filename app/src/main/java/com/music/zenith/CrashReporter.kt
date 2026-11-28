package com.music.zenith

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.view.Display
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Menangkap exception yang tidak tertangani, menyimpannya sebagai laporan lengkap
 * (error, spesifikasi perangkat, memori, baterai, jaringan), lalu membuka
 * [CrashActivity] di proses ":crash" sebelum proses utama dimatikan.
 *
 * Proses tidak bisa dilanjutkan setelah crash; yang bisa dilakukan hanyalah
 * menampilkan laporan dan memberi jalan untuk membuka ulang app.
 */
object CrashReporter {
    const val EXTRA_PATH = "crash_report_path"
    const val EXTRA_LOCALES = "crash_app_locales"

    private const val DIR = "crash"
    private const val MAX_FILES = 10
    private const val LOOP_WINDOW_MS = 3_000L
    private const val MAX_REPORT_CHARS = 150_000
    private val handling = AtomicBoolean(false)

    /** True di proses ":crash". Application tidak boleh menjalankan inisialisasi app di sana. */
    fun isCrashProcess(): Boolean = runCatching {
        File("/proc/self/cmdline").readText().trim('\u0000').endsWith(":crash")
    }.getOrDefault(false)

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            var shown = false
            if (handling.compareAndSet(false, true)) {
                try {
                    val file = save(app, thread, error)
                    // Crash beruntun (misalnya service yang dihidupkan ulang sistem lalu
                    // langsung crash) tidak membuka halaman ini berulang kali.
                    if (file != null && !isCrashLoop(app)) {
                        app.startActivity(
                            Intent(app, CrashActivity::class.java)
                                .putExtra(EXTRA_PATH, file.absolutePath)
                                // Sebelum Android 13, bahasa pilihan di app tidak ikut ke proses ":crash".
                                .putExtra(
                                    EXTRA_LOCALES,
                                    runCatching { AppCompatDelegate.getApplicationLocales().toLanguageTags() }.getOrNull(),
                                )
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        )
                        shown = true
                    }
                } catch (_: Throwable) {
                }
            }
            if (shown) {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    private fun isCrashLoop(app: Application): Boolean {
        val marker = File(File(app.filesDir, DIR), "last_shown")
        val now = System.currentTimeMillis()
        val loop = marker.exists() && now - marker.lastModified() < LOOP_WINDOW_MS
        marker.writeText(now.toString())
        marker.setLastModified(now)
        return loop
    }

    private fun save(app: Application, thread: Thread, error: Throwable): File? {
        val dir = File(app.filesDir, DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp.txt")
        val text = try {
            buildReport(app, thread, error)
        } catch (_: Throwable) {
            try {
                "Thread: ${thread.name}\n\n" + error.stackTraceToString()
            } catch (_: Throwable) {
                error.javaClass.name
            }
        }
        file.writeText(text.take(MAX_REPORT_CHARS))
        dir.listFiles { f -> f.name.startsWith("crash-") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
        return file
    }

    private fun mb(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1048576.0)

    private fun stamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    private fun buildReport(app: Application, thread: Thread, error: Throwable): String {
        val sb = StringBuilder(16 * 1024)

        fun StringBuilder.kv(key: String, value: Any?) {
            append(key).append(": ").append(value).append('\n')
        }

        fun section(title: String, body: StringBuilder.() -> Unit) {
            sb.append("\n== ").append(title).append(" ==\n")
            try {
                sb.body()
            } catch (e: Throwable) {
                sb.append("(gagal dibaca: ").append(e.javaClass.simpleName).append(")\n")
            }
        }

        sb.append("ZENITH CRASH REPORT\n")
        sb.kv("Waktu", SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date()))
        sb.kv("Thread", "${thread.name} (prioritas ${thread.priority})")
        sb.kv("Error", "${error.javaClass.name}: ${error.message}")

        section("APLIKASI") {
            @Suppress("DEPRECATION")
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            kv("Paket", app.packageName)
            kv("Versi", "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})")
            kv("Build", BuildConfig.BUILD_TYPE + if (BuildConfig.DEBUG) " (debug)" else "")
            kv("Pertama dipasang", stamp(info.firstInstallTime))
            kv("Terakhir diperbarui", stamp(info.lastUpdateTime))
            kv("Proses", runCatching { File("/proc/self/cmdline").readText().trim('\u0000') }.getOrDefault("?"))
            kv("Umur proses", "${(SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 1000} dtk")
            kv(
                "WebView",
                runCatching {
                    android.webkit.WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" }
                }.getOrNull(),
            )
        }

        section("PERANGKAT") {
            kv("Merek", Build.BRAND)
            kv("Pabrikan", Build.MANUFACTURER)
            kv("Model", Build.MODEL)
            kv("Device", Build.DEVICE)
            kv("Produk", Build.PRODUCT)
            kv("Hardware", Build.HARDWARE)
            kv("Board", Build.BOARD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                kv("SoC", "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
            }
            kv("ABI", Build.SUPPORTED_ABIS.joinToString())
            kv("Core CPU", Runtime.getRuntime().availableProcessors())
            kv("Fingerprint", Build.FINGERPRINT)
        }

        section("SISTEM") {
            kv("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            kv("Patch keamanan", Build.VERSION.SECURITY_PATCH)
            kv("Build sistem", Build.DISPLAY)
            kv("Incremental", Build.VERSION.INCREMENTAL)
            kv("Kernel", System.getProperty("os.version"))
            kv("Bahasa", Locale.getDefault().toLanguageTag())
            kv("Zona waktu", TimeZone.getDefault().id)
            val pm = app.getSystemService(PowerManager::class.java)
            kv("Hemat daya", pm.isPowerSaveMode)
            kv("Doze", pm.isDeviceIdleMode)
            kv("Layar menyala", pm.isInteractive)
        }

        section("LAYAR") {
            val metrics = app.resources.displayMetrics
            val config = app.resources.configuration
            kv("Resolusi", "${metrics.widthPixels} x ${metrics.heightPixels} px")
            kv("Densitas", "${metrics.densityDpi} dpi (x${metrics.density})")
            kv("Skala font", config.fontScale)
            kv("Mode gelap", (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
            val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            kv("Refresh rate", display?.refreshRate?.let { String.format(Locale.US, "%.1f Hz", it) })
        }

        section("MEMORI DAN PENYIMPANAN") {
            val am = app.getSystemService(ActivityManager::class.java)
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            kv("RAM", "${mb(mi.availMem)} bebas dari ${mb(mi.totalMem)}")
            kv("Memori rendah", mi.lowMemory)
            kv("Ambang memori rendah", mb(mi.threshold))
            kv("Kelas memori app", "${am.memoryClass} MB (large ${am.largeMemoryClass} MB)")
            val rt = Runtime.getRuntime()
            kv(
                "Heap Java",
                "terpakai ${mb(rt.totalMemory() - rt.freeMemory())}, dialokasi ${mb(rt.totalMemory())}, maks ${mb(rt.maxMemory())}",
            )
            kv("Heap native", mb(Debug.getNativeHeapAllocatedSize()))
            val stat = StatFs(app.filesDir.path)
            kv("Penyimpanan", "${mb(stat.availableBytes)} bebas dari ${mb(stat.totalBytes)}")
        }

        section("BATERAI DAN JARINGAN") {
            val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (battery != null) {
                val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                kv("Baterai", if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "?")
                kv("Mengisi daya", charging)
                kv("Suhu baterai", "${battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0} C")
            }
            val cm = app.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if (caps == null) {
                kv("Jaringan", "tidak ada")
            } else {
                val transports = buildList {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Seluler")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("Bluetooth")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
                }
                kv("Jaringan", transports.joinToString().ifEmpty { "lainnya" })
                kv("Internet tervalidasi", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                kv("Berbayar (metered)", !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            }
        }

        section("STACK TRACE") {
            val writer = StringWriter()
            PrintWriter(writer).use { error.printStackTrace(it) }
            append(writer.toString())
        }

        section("THREAD") {
            val all = Thread.getAllStackTraces()
            kv("Jumlah thread", all.size)
            if (thread.name != "main") {
                all.keys.firstOrNull { it.name == "main" }?.let { main ->
                    append("Stack thread main:\n")
                    all[main]?.take(25)?.forEach { append("    at ").append(it).append('\n') }
                }
            }
            all.keys.sortedBy { it.name }.forEach {
                append(" - ").append(it.name).append(" [").append(it.state).append(']')
                if (it.isDaemon) append(" daemon")
                append('\n')
            }
        }

        return sb.toString()
    }
}