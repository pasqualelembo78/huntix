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

    private const val MAX_ENTRIES = 5000
    private const val LOG_FILE = "city3d_debug.log"
    private const val MAX_LOG_FILE_BYTES = 16 * 1024 * 1024L
    /** Taglia il file alla meta' quando supera il limite (~8 MB dopo il taglio). */
    private const val TRUNCATE_TO_BYTES = MAX_LOG_FILE_BYTES / 2
    /** Tail massimo letto da readDiskLog / exportToDownloads per non ANR. */
    private const val READ_TAIL_BYTES = 1024 * 1024L

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
            truncateIfNeeded(logFile)
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
            // `default` e' null (catturato prima di Crashlytics/Sentry):
            // usa System.exit(1) invece di halt(1) cosi' Crashlytics ha il
            // tempo di fare flush del dump registrato nel ContentProvider.
            default?.uncaughtException(thread, throwable)
                ?: Runtime.getRuntime().exit(1)
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
        logExecutor.execute {
            try {
                logFile?.appendText("${sdf.format(Date(ts))} ${level.name} $tag: $msg\n")
                // taglia proattivamente se il file cresce troppo durante la
                // sessione: evita che alla prossima apertura init() debba
                // leggere decine di MB su readBytes() (OOM/ANR su mobile).
                truncateIfNeeded(logFile)
            } catch (_: Exception) {}
        }
    }



    fun getAll(): List<Entry> = synchronized(lock) { entries.toList() }

    fun getAllAsString(): String = synchronized(lock) {
        entries.joinToString("\n") { it.format() }
    }

    /**
     * Taglia il file di log se supera MAX_LOG_FILE_BYTES: legge solo gli ultimi
     * TRUNCATE_TO_BYTES byte (via seek) e li riscrive. Nessun readBytes() completo:
     * il file puo' essere decine di MB senza OOM.
     */
    private fun truncateIfNeeded(f: File?) {
        if (f == null || !f.exists()) return
        if (f.length() <= MAX_LOG_FILE_BYTES) return
        try {
            val raf = java.io.RandomAccessFile(f, "rw")
            val total = raf.length()
            val n = TRUNCATE_TO_BYTES.toInt()
            raf.seek(total - n)
            val tail = ByteArray(n)
            val read = raf.read(tail)
            raf.setLength(0)
            if (read > 0) raf.write(tail, 0, read)
            raf.close()
        } catch (_: Exception) {}
    }

    /**
     * Legge solo gli ultimi maxBytes del file di log (via seek + read).
     * Ritorna stringa vuota se il file e' troppo grande da leggere tutto
     * in memoria — evita ANR nel UI thread del DebugLog viewer.
     */
    private fun readTailBytes(f: File, maxBytes: Long): String {
        try {
            if (!f.exists()) return ""
            val len = f.length()
            if (len <= maxBytes) return f.readText()
            val n = maxBytes.toInt()
            val buf = ByteArray(n)
            val raf = java.io.RandomAccessFile(f, "r")
            raf.seek(len - n)
            val read = raf.read(buf)
            raf.close()
            return "...[troncato, ultimi $read byte]\n" + String(buf, Charsets.UTF_8)
        } catch (_: Exception) {
            return ""
        }
    }

    fun readDiskLog(context: Context): String {
        return try {
            val f = File(context.filesDir, LOG_FILE)
            if (f.exists()) readTailBytes(f, READ_TAIL_BYTES) else "(nessun log su disco)"
        } catch (e: Exception) {
            "(errore lettura log: ${e.message})"
        }
    }

    /** Legge il log su disco COMPLETO (fino a TRUNCATE_TO_BYTES, 2MB).
     *  Da usare solo fuori dal UI thread (export/clipboard). */
    fun readDiskLogFull(context: Context): String {
        return try {
            val f = File(context.filesDir, LOG_FILE)
            if (!f.exists()) return "(nessun log su disco)"
            val len = f.length()
            if (len > TRUNCATE_TO_BYTES) {
                val n = TRUNCATE_TO_BYTES.toInt()
                val buf = ByteArray(n)
                val raf = java.io.RandomAccessFile(f, "r")
                raf.seek(len - n)
                val read = raf.read(buf)
                raf.close()
                return "...[troncato, ultimi $read byte]\n" + String(buf, Charsets.UTF_8)
            }
            return f.readText()
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
            if (f.exists()) readDiskLogFull(context) else getAllAsString()
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
