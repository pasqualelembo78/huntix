package com.intelligame.huntix.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.intelligame.huntix.AppLog

/**
 * Debug log viewer.
 * - "Log disco" = logs from disk (survives crash)
 * - "Sessione" = current in-memory log
 * - "Esporta" = saves to Downloads/ folder as .txt (no permissions needed)
 * - "Copia" = copy to clipboard
 */
class CityDebugLogActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var scrollView: NestedScrollView
    private lateinit var counterLabel: TextView
    private lateinit var headerTitle: TextView
    private val uiHandler = Handler(Looper.getMainLooper())
    private val diskIO = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var _pendingDiskLoad = 0  // contatore per cancellare load obsoleti
    // Limite testo mostrato nella TextView: oltre questo il LAYOUT/drawing di un
    // TextView selectable impiega secondi e finisce in ANR (Editor.drawTextRun).
    // Il viewer NON è selectable (la selezione passa dal bottone Copia), quindi
    // il carico è solo di rendering: 60K char consentono di leggere l'intera
    // sessione senza onerare il draw; oltre si mostra solo la coda.
    private val MAX_DISPLAY_CHARS = 60_000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val d = resources.displayMetrics.density
        fun Int.dp() = (this * d).toInt()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 12.dp(), 16.dp(), 8.dp())
            setBackgroundColor(Color.parseColor("#1A1030"))
        }

        // Title row
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerTitle = TextView(this).apply {
            text = "Debug Log"
            textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(headerTitle)
        counterLabel = TextView(this).apply {
            textSize = 12f; setTextColor(Color.parseColor("#888888"))
        }
        topRow.addView(counterLabel)
        header.addView(topRow)

// Button row 1: navigation
        val btnRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8.dp(), 0, 0)
            gravity = Gravity.END
        }

        fun smallBtn(text: String, color: String, onClick: () -> Unit): TextView =
            TextView(this).apply {
                this.text = text; textSize = 12f; setTextColor(Color.WHITE)
                setPadding(14.dp(), 8.dp(), 14.dp(), 8.dp())
                background = GradientDrawable().apply {
                    cornerRadius = 8f * d; setColor(Color.parseColor(color))
                }
                setOnClickListener { onClick() }
            }

        btnRow1.addView(smallBtn("Log disco", "#555555") { showDiskLog() })
        btnRow1.addView(smallBtn("Sessione", "#444466") { refreshLog() })
        header.addView(btnRow1)

        // Button row 2: actions
        val btnRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4.dp(), 0, 0)
            gravity = Gravity.END
        }

        btnRow2.addView(smallBtn("Esporta in Downloads", "#2E7D32") { exportToDownloads() })
        btnRow2.addView(smallBtn("Copia", "#A78BFA") { copyLogToClipboard() })
        btnRow2.addView(smallBtn("Clear", "#333333") {
            AppLog.clear()
            refreshLog()
        })
        header.addView(btnRow2)

        // Log text
        logTextView = TextView(this).apply {
            textSize = 11f; typeface = Typeface.MONOSPACE
            setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
            // setTextIsSelectable(true) NON va usato: abilita l'Editor (stesso
            // path di EditText) e il draw di testo lungo finisce in
            // Editor.drawHardwareAccelerated → ANR. La selezione è garantita
            // dal bottone "Copia".
            setLineSpacing(0f, 1.15f)
        }
        scrollView = NestedScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0620"))
            addView(logTextView)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0620"))
        }
        root.addView(header)
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        showDiskLog()
    }

    override fun onDestroy() {
        super.onDestroy()
        diskIO.shutdownNow()
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
    }

    private fun showDiskLog() {
        headerTitle.text = "Log Disco"
        counterLabel.text = "caricamento..."
        logTextView.text = ""
        logTextView.setTextColor(Color.parseColor("#FFD080"))
        val generation = ++_pendingDiskLoad
        diskIO.execute {
            val diskLog = AppLog.readDiskLog(this@CityDebugLogActivity)
            uiHandler.post {
                if (generation != _pendingDiskLoad) return@post
                logTextView.text = trimForDisplay(diskLog)
                counterLabel.text = if (diskLog.length <= MAX_DISPLAY_CHARS)
                    "mostrati ${diskLog.length} char"
                else "ultimi $MAX_DISPLAY_CHARS char di ${diskLog.length}"
                scrollView.post { scrollView.fullScroll(NestedScrollView.FOCUS_DOWN) }
            }
        }
    }

    private fun refreshLog() {
        headerTitle.text = "Log Sessione"
        val entries = AppLog.getAll()
        counterLabel.text = "${entries.size} entries"
        if (entries.isEmpty()) {
            logTextView.text = "Nessun log in memoria.\n\nTocca 'Log disco' per i log precedenti (anche dopo crash)."
            logTextView.setTextColor(Color.parseColor("#666666"))
            return
        }
        val full = buildString {
            for (e in entries) append(e.format()).append("\n")
        }
        logTextView.text = trimForDisplay(full)
        logTextView.setTextColor(Color.parseColor("#CCCCCC"))
        scrollView.post { scrollView.fullScroll(NestedScrollView.FOCUS_DOWN) }
    }

    private fun trimForDisplay(text: String): String =
        if (text.length <= MAX_DISPLAY_CHARS) text
        else "...[troncato, ultimi $MAX_DISPLAY_CHARS char]\n" + text.substring(text.length - MAX_DISPLAY_CHARS)

    private fun exportToDownloads() {
        val generation = ++_pendingDiskLoad
        logTextView.setTextColor(Color.parseColor("#AAAAAA"))
        val prev = logTextView.text
        logTextView.text = "Esportazione in corso..."
        diskIO.execute {
            val filename = AppLog.exportToDownloads(this@CityDebugLogActivity)
            uiHandler.post {
                if (generation != _pendingDiskLoad) return@post
                if (filename != null) {
                    logTextView.text = prev  // ripristina il testo precedente
                    Toast.makeText(this@CityDebugLogActivity, "Salvato in Downloads/$filename", Toast.LENGTH_LONG).show()
                } else {
                    logTextView.text = prev
                    Toast.makeText(this@CityDebugLogActivity, "Nessun log da esportare", Toast.LENGTH_SHORT).show()
                }
                logTextView.setTextColor(Color.parseColor("#FFD080"))
            }
        }
    }

    private fun copyLogToClipboard() {
        val generation = ++_pendingDiskLoad
        diskIO.execute {
            val diskLog = AppLog.readDiskLogFull(this@CityDebugLogActivity)
            val memLog = AppLog.getAllAsString()
            val combined = if (diskLog.isNotEmpty() && diskLog != "(nessun log su disco)") {
                diskLog
            } else memLog
            val maxChars = 200_000
            val trimmed = if (combined.length > maxChars)
                "...[troncato]\n" + combined.substring(combined.length - maxChars)
            else combined
            uiHandler.post {
                if (generation != _pendingDiskLoad) return@post
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("City3D Debug Log", trimmed))
                    Toast.makeText(this@CityDebugLogActivity, "Log copiato (${trimmed.length} caratteri)", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@CityDebugLogActivity, "Copia non riuscita: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
