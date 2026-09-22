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
    private const val READ_TAIL_BYTES = 4 * 1024 * 1024L
    /** Lunghezza massima di UNA riga scritta su disco/logcat: logcat spezza le
     * righe oltre ~4000 byte e le aggiunte di Unity (JSON lunghi) arrivavano
     * "troncate a meta'". Sotto il limite Android con UTF-8 multibyte. */
    private const val MAX_LINE_CHARS = 1400
    /** Numero massimo di archivi di sessione precedente tenuti su disco. */
    private const val KEEP_ARCHIVES = 3
    /** Sessioni mostrate in "Log disco": la corrente + gli ultimi KEEP_ARCHIVES
     * archivi. Il disco deve sempre contenere QUANTO MENO 3 sessioni, altrimenti
     * diventa inutilizzabile per ricostruire un problema. */
    private const val DISK_SESSIONS = KEEP_ARCHIVES + 1

    private val entries = mutableListOf<Entry>()
    private val lock = Any()
    private var logFile: File? = null
    private var appContext: Context? = null
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val exportSdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun init(context: Context) {
        appContext = context.applicationContext
        logFile = File(context.filesDir, LOG_FILE)
        // Ruota la sessione precedente in archivio PRIMA di partire: il file
        // attivo inizia sempre dall'inizio della sessione corrente (niente
        // cumulo inter-sessione che "taglia" il principio del log).
        rotateToArchive()
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
        // Una riga = UN entry logcat/disco. Le righe troppo lunghe vengono
        // spezzate (con prefisso "  '->" in continuazione) cosi' logcat non le
        // tronca a meta' e il file resta leggibile riga per riga.
        val lines = chunkLines(msg)
        for (line in lines) {
            when (level) {
                Level.D -> Log.d(tag, line)
                Level.I -> Log.i(tag, line)
                Level.W -> Log.w(tag, line)
                Level.E -> Log.e(tag, line)
            }
            val entry = Entry(ts, level, tag, line)
            synchronized(lock) {
                entries.add(entry)
                if (entries.size > MAX_ENTRIES) entries.removeAt(0)
            }
        }
        logExecutor.execute {
            try {
                for (line in lines) {
                    logFile?.appendText("${sdf.format(Date(ts))} ${level.name} $tag: $line\n")
                }
                // taglia proattivamente se il file cresce troppo durante la
                // sessione: evita che alla prossima apertura init() debba
                // leggere decine di MB su readBytes() (OOM/ANR su mobile).
                truncateIfNeeded(logFile)
            } catch (_: Exception) {}
        }
    }

    /** Spezza un messaggio in righe da <= MAX_LINE_CHARS, preferendo le
     * interruzioni negli spazi; una riga singola resta invariata. */
    private fun chunkLines(msg: String): List<String> {
        if (msg.length <= MAX_LINE_CHARS) return listOf(msg)
        val out = ArrayList<String>()
        var start = 0
        var first = true
        while (start < msg.length) {
            var end = minOf(start + MAX_LINE_CHARS, msg.length)
            if (end < msg.length) {
                val cutAt = msg.lastIndexOf(' ', end - 1)
                if (cutAt > start + MAX_LINE_CHARS / 2) end = cutAt
            }
            val prefix = if (first) "" else "  \u21B3 "
            out.add(prefix + msg.substring(start, end))
            first = false
            start = end
            if (start >= msg.length) break
        }
        return out
    }

    /** Archivia la sessione precedente e la tiene (potrebbe contenere il
     * crash) ritagliando le archive piu' vecchie oltre KEEP_ARCHIVES. */
    private fun rotateToArchive() {
        val f = logFile ?: return
        try {
            if (!f.exists() || f.length() < 2L) return
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            if (!f.renameTo(File(f.parentFile, "city3d_debug.$stamp.log"))) return
            cleanupArchives(f.parentFile)
        } catch (_: Exception) {}
    }

    private fun cleanupArchives(dir: File?) {
        if (dir == null) return
        try {
            val archives = dir.listFiles { _, name ->
                name.startsWith("city3d_debug.") && name.endsWith(".log")
            }?.sortedByDescending { it.name } ?: return
            for (i in KEEP_ARCHIVES until archives.size) {
                try { archives[i].delete() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    /** [logFile] + archivi di sessione, dal piu' recente al piu' vecchio. */
    private fun sessionFiles(): List<File> {
        val ctx = appContext ?: return emptyList()
        val archives = try {
            ctx.filesDir.listFiles { _, name ->
                name.startsWith("city3d_debug.") && name.endsWith(".log")
            }?.sortedByDescending { it.name } ?: emptyList()
        } catch (_: Exception) { emptyList() }
        val f = logFile
        if (f == null || !f.exists() || f.length() < 2L) return archives
        return listOf(f) + archives
    }

    /** Legge l'INIZIO di un file di sessione (mai la coda): cosi' l'inizio di
     * ogni sessione resta visibile per intero, anche dopo un crash. Se la
     * sessione e' enorme, mostra le prime maxBytes con marcatore di taglio. */
    private fun readSessionHead(f: File, maxBytes: Long): String {
        try {
            if (!f.exists()) return ""
            val len = f.length()
            if (len == 0L) return ""
            if (len <= maxBytes) return f.readText()
            val n = maxBytes.toInt()
            val raf = java.io.RandomAccessFile(f, "r")
            val buf = ByteArray(n)
            val read = raf.read(buf)
            raf.close()
            return String(buf, 0, read, Charsets.UTF_8) +
                    "\n...[inizio di questa sessione mostrato fino a $maxBytes byte," +
                    " il file continua piu' avanti]..."
        } catch (_: Exception) {
            return ""
        }
    }

    /**
     * "Log disco" = MULTI-SESSIONE. Compone le ultime DISK_SESSIONS sessioni
     * (la corrente + gli archivi di quelle precedenti), almeno 3 se esistono,
     * in ordine cronologico (vecchia -> corrente), LEGGENDO L'INIZIO di ognuna.
     * Preferisce le sessioni piu' recenti (il crash e' nell'ultima); il resto
     * del budget totale viene diviso in parti uguali per non ANR il viewer.
     */
    private fun sessionsText(maxTotalBytes: Long): String {
        val files = sessionFiles()
        if (files.isEmpty()) return ""
        val shown = files.take(DISK_SESSIONS)   // newest first
        val dropped = files.size - shown.size
        val perSession = (maxTotalBytes / shown.size).coerceAtLeast(32 * 1024L)
        val sb = StringBuilder()
        var idx = 1
        for (i in shown.indices.reversed()) {   // dal piu' vecchio al corrente
            val f = shown[i]
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append("\u2550\u2550\u2550 SESSIONE $idx/${shown.size}: ${f.name} \u2550\u2550\u2550\n")
            sb.append(readSessionHead(f, perSession))
            idx++
        }
        if (dropped > 0) {
            sb.append("\n[ ...$dropped sessioni piu' vecchie scartate: il disco tiene " +
                    "almeno $DISK_SESSIONS sessioni (corrente + archivi) ... ]")
        }
        return sb.toString()
    }

    fun readDiskLog(context: Context): String {
        return try {
            val s = sessionsText(READ_TAIL_BYTES)
            if (s.isBlank()) "(nessun log su disco)" else s
        } catch (e: Exception) {
            "(errore lettura log: ${e.message})"
        }
    }

    /** Legge il log su disco MULTI-SESSIONE (fino a TRUNCATE_TO_BYTES, 8MB).
     *  Da usare solo fuori dal UI thread (export/clipboard). */
    fun readDiskLogFull(context: Context): String {
        return try {
            val s = sessionsText(TRUNCATE_TO_BYTES)
            if (s.isBlank()) "(nessun log su disco)" else s
        } catch (e: Exception) {
            "(errore lettura log: ${e.message})"
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
