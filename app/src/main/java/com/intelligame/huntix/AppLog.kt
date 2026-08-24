package com.intelligame.huntix

import android.content.ContentValues
import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app log buffer. Stores last 500 entries in memory, writes to internal disk
 * (survives crash), and can export to Downloads/ (no permissions needed on Android 10+).
 */
object AppLog {

    enum class Level { D, I, W, E }

    data class Entry(
        val time: Long,
        val level: Level,
        val tag: String,
        val msg: String
    ) {
        fun format(): String {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            return "${sdf.format(Date(time))} ${level.name} $tag: $msg"
        }
    }

    private const val MAX_ENTRIES = 500
    private const val LOG_FILE = "city3d_debug.log"
    private const val MAX_LOG_FILE_BYTES = 512 * 1024L

    private val entries = mutableListOf<Entry>()
    private val lock = Any()
    private var logFile: File? = null
    private var appContext: Context? = null
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val exportSdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun init(context: Context) {
        appContext = context.applicationContext
        logFile = File(context.filesDir, LOG_FILE)
        try {
            val f = logFile
            if (f != null && f.exists() && f.length() > MAX_LOG_FILE_BYTES) {
                val bytes = f.readBytes()
                val half = bytes.size / 2
                f.writeBytes(bytes.copyOfRange(half, bytes.size))
            }
        } catch (_: Exception) {}
        log(
            Level.I, "AppLog",
            "=== NEW SESSION === ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})"
        )
        try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, 0)
            val updTxt = sdf.format(Date(pi.lastUpdateTime))
            log(
                Level.I, "AppLog",
                "APP VERSION ${pi.versionName} (${PackageInfoCompat.getLongVersionCode(pi)}) installata: $updTxt"
            )
        } catch (_: Throwable) {}
    }

    fun risorse(context: Context, tag: String) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val internalMb = android.os.StatFs(context.filesDir.absolutePath).availableBytes / 1048576L
            val extDir = context.getExternalFilesDir(null)
            val externalMb = if (extDir != null) android.os.StatFs(extDir.absolutePath).availableBytes / 1048576L else -1L
            log(
                Level.I, "Risorse",
                "$tag memAvail=${mi.availMem / 1048576L}MB lowMem=${mi.lowMemory} interno=${internalMb}MB esterno=${externalMb}MB"
            )
        } catch (_: Throwable) {}
    }

    fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sb = StringBuilder()
                sb.append("UNCAUGHT on thread [${thread.name}]\n")
                var t: Throwable? = throwable
                var depth = 0
                while (t != null && depth < 6) {
                    if (depth > 0) sb.append("Caused by: ")
                    sb.append(t.javaClass.name).append(": ").append(t.message).append('\n')
                    t.stackTrace.take(60).forEach { sb.append("    at $it\n") }
                    t = t.cause
                    depth++
                }
                crashWrite(sb.toString())
            } catch (_: Exception) {}
            default?.uncaughtException(thread, throwable)
                ?: Runtime.getRuntime().halt(1)
        }
    }

    /** Scrive il dump di crash in modo SINCRONO (il processo muore subito dopo). */
    private fun crashWrite(text: String) {
        val f = logFile ?: return
        try {
            val line = "${sdf.format(Date())} E AppLog: $text\n"
            synchronized(lock) { f.appendText(line) }
            Log.e("AppLog", text)
        } catch (_: Exception) {}
    }

    /**
     * Attende che tutte le scritture su disco pendenti vengano completate.
     * Da chiamare nei punti critici (es. fine teardown onDestroy) perché la
     * scrittura è asincrona e un processo che muore subito dopo rischierebbe
     * di perdere l'ultimo marker.
     */
    fun flush() {
        val barrier = java.util.concurrent.CountDownLatch(1)
        logExecutor.execute { barrier.countDown() }
        try {
            barrier.await(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    fun d(tag: String, msg: String) = log(Level.D, tag, msg)
    fun i(tag: String, msg: String) = log(Level.I, tag, msg)
    fun w(tag: String, msg: String) = log(Level.W, tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) {
        val fullMsg = if (t != null) "$msg — ${t.javaClass.simpleName}: ${t.message}" else msg
        log(Level.E, tag, fullMsg)
    }

private val logExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
private fun log(level: Level, tag: String, msg: String) {
        val ts = System.currentTimeMillis()
        when (level) {
            Level.D -> Log.d(tag, msg)
            Level.I -> Log.i(tag, msg)
            Level.W -> Log.w(tag, msg)
            Level.E -> Log.e(tag, msg)
        }
        val entry = Entry(ts, level, tag, msg)
        synchronized(lock) {
            entries.add(entry)
            if (entries.size > MAX_ENTRIES) entries.removeAt(0)
        }
        logExecutor.execute { runCatching { logFile?.appendText("${sdf.format(Date(ts))} ${level.name} $tag: $msg\n") } }
    }



    fun getAll(): List<Entry> = synchronized(lock) { entries.toList() }

    fun getAllAsString(): String = synchronized(lock) {
        entries.joinToString("\n") { it.format() }
    }

    fun readDiskLog(context: Context): String {
        return try {
            val f = File(context.filesDir, LOG_FILE)
            if (f.exists()) f.readText() else "(nessun log su disco)"
        } catch (e: Exception) {
            "(errore lettura log: ${e.message})"
        }
    }

    /**
     * Export the internal log file to the device Downloads folder.
     * Uses MediaStore on Android 10+ — no permissions needed.
     * Returns the filename written or null on failure.
     */
    fun exportToDownloads(context: Context): String? {
        val content = try {
            val f = File(context.filesDir, LOG_FILE)
            if (f.exists()) f.readText() else getAllAsString()
        } catch (_: Exception) { getAllAsString() }

        if (content.isBlank()) return null

        val filename = "huntix_log_${exportSdf.format(Date())}.txt"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: use MediaStore — no permission needed
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(content.toByteArray())
                    }
                    filename
                }
            } else {
                // Android 9 and below: write directly to Downloads
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, filename)
                file.writeText(content)
                filename
            }
        } catch (e: Exception) {
            Log.e("AppLog", "Export to Downloads failed: ${e.message}")
            null
        }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
        try { logFile?.delete() } catch (_: Exception) {}
    }

    fun count(): Int = synchronized(lock) { entries.size }
}
