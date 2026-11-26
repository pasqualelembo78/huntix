package com.intelligame.huntix.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.bridge.OfflinePackManager
import com.intelligame.huntix.bridge.StoreUnityBridge
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.R
import org.json.JSONObject
import java.util.Locale

/**
 * Sezione "Mondo offline": l'utente flaggla le citta' (o tutta Italia) che
 * vuole avere sul dispositivo. Il flag NON basta: lo stato vero si legge dai
 * file nella cache v5 di TileClient, quindi dopo una cancellazione dati il
 * pacchetto torna a risultare "da scaricare".
 */
class OfflinePacksActivity : AppCompatActivity() {

    companion object {
        /** Unity ("💾 Gestisci spazio") apre questa schermata già focalizzata
         *  sulla card di pulizia selettiva della cache tile. */
        const val EXTRA_FOCUS_STORAGE = "EXTRA_FOCUS_STORAGE"
    }

    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var listBox: LinearLayout
    private lateinit var summary: TextView
    private lateinit var tileStatsText: TextView
    private var lastTileStats: JSONObject? = null
    private var progressDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressLabel: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun Int.dp() = (this * d).toInt()
        val focusStorage = intent.getBooleanExtra(EXTRA_FOCUS_STORAGE, false)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(UiKit.themed(this@OfflinePacksActivity, R.color.huntix_bg))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 12.dp(), 16.dp(), 24.dp())
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "💾 Mondo offline"
            textSize = 22f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 6.dp())
        })
        root.addView(TextView(this).apply {
            text = "Spunta le citta' che vuoi avere sempre disponibili. " +
                "All'ingresso in MiCittà il gioco userà i file già sul telefono " +
                "invece di chiamare il server. Puoi giocare online anche senza scaricarli."
            textSize = 13f
            setTextColor(Color.parseColor("#B9A9D6"))
            setPadding(0, 0, 0, 12.dp())
        })

        // Card in cima (evidenziata quando arriva il comando da MiCittà):
        // gestione spazio SELETTIVA, mai cancellazione di dati del giocatore.
        root.addView(buildStorageCard(d, focusStorage), 2)

        summary = TextView(this).apply {
            textSize = 13f
            setTextColor(UiKit.themed(this@OfflinePacksActivity, R.color.huntix_success))
            setPadding(0, 0, 0, 10.dp())
        }
        root.addView(summary)

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 14.dp(), 0, 0)
        }
        actions.addView(button("Aggiorna", "#555555") { refresh() })
        actions.addView(TextView(this).apply { layoutParams = LinearLayout.LayoutParams(8.dp(), 1) })
        actions.addView(button("⬇ Scarica selezionati", "#7E57C2") { downloadSelected() })
        root.addView(actions)

        setContentView(scroll)
        refresh()
    }

    private fun button(label: String, color: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(16, 10, 16, 10)
            background = GradientDrawable().apply {
                cornerRadius = 10f * resources.displayMetrics.density
                setColor(Color.parseColor(color))
            }
            setOnClickListener { onClick() }
        }

    /** Card di gestione spazio: MOSTRA e cancella solo la cache tile di
     *  MiCittà. Nessun altro dato del giocatore entra in questa schermata. */
    private fun buildStorageCard(d: Float, focus: Boolean): LinearLayout {
        fun Int.dp() = (this * d).toInt()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            background = GradientDrawable().apply {
                cornerRadius = 12f * d
                setColor(Color.parseColor(if (focus) "#263A2A" else "#1A0A33"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = 10.dp() }
        }
        card.addView(TextView(this).apply {
            text = "💾 Gestisci spazio"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 6.dp())
        })
        card.addView(TextView(this).apply {
            text = "Sono i file JSON non compressi delle tile v5 di MiCittà in " +
                "files/huntix_tiles. Cancellarli NON tocca profilo, uova, ricette, " +
                "preferenze o snapshot: il gioco li riscarica solo se serve."
            textSize = 12f
            setTextColor(Color.parseColor("#B9A9D6"))
            setPadding(0, 0, 0, 8.dp())
        })
        tileStatsText = TextView(this).apply {
            text = "Lettura statistiche cache…"
            textSize = 12f
            setTextColor(UiKit.themed(this@OfflinePacksActivity, R.color.huntix_success))
            setPadding(0, 0, 0, 8.dp())
        }
        card.addView(tileStatsText)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        row.addView(button("Pulisci solo cache tile", "#B23A48") { confirmClearTileCache() })
        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(8.dp(), 1)
        })
        row.addView(button("Aggiorna", "#555555") { loadTileStats() })
        card.addView(row)
        return card
    }

    private fun statsCounts(jo: JSONObject): String {
        val bytes = jo.optLong("bytes", 0L)
        val size = if (bytes >= 1_000_000_000L)
            String.format(Locale.ITALIAN, "%.1f GB", bytes / 1_000_000_000.0)
        else
            String.format(Locale.ITALIAN, "%.1f MB", bytes / 1_000_000.0)
        return "$size · ${jo.optInt("files", 0)} file " +
            "(attuali v5: ${jo.optInt("current", 0)}, " +
            "vecchi v1-v4: ${jo.optInt("legacy", 0)}, " +
            "temporanei .tmp: ${jo.optInt("temp", 0)})"
    }

    private fun applyTileStats(json: String) {
        val jo = try {
            JSONObject(json)
        } catch (_: Exception) {
            JSONObject()
        }
        lastTileStats = jo
        tileStatsText.text = if (json.isBlank()) "Statistiche cache non disponibili"
        else statsCounts(jo) + "\nCartella: " + jo.optString("dir", "-")
    }

    private fun loadTileStats() {
        io.execute {
            val json = try {
                StoreUnityBridge.tileCacheStats()
            } catch (e: Exception) {
                AppLog.w("OfflinePacks", "tileCacheStats: ${e.message}")
                ""
            }
            ui.post {
                if (!isFinishing && !isDestroyed) applyTileStats(json)
            }
        }
    }

    private fun confirmClearTileCache() {
        val jo = lastTileStats
        val dir = jo?.optString("dir", "") ?: ""
        val counts = if (jo != null) statsCounts(jo) else "conteggio non ancora disponibile"
        AlertDialog.Builder(this)
            .setTitle("Pulisci solo cache tile")
            .setMessage(
                "Verranno cancellati SOLO i file regolari delle tile di MiCittà in:\n" +
                "$dir\n\n$counts\n\n" +
                "Cancellazione: *.v[1-9].graph.json, *.v[1-9].geo.json e i loro .tmp.\n" +
                "Resta intatto: profilo, uova, ricette, preferenze, snapshot e ogni altro " +
                "file dell'app. Le tile verranno riscaricate solo se il gioco ne ha bisogno."
            )
            .setPositiveButton("Cancella solo le tile") { _, _ -> clearTileCache() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun clearTileCache() {
        io.execute {
            val json = try {
                StoreUnityBridge.clearTileCache()
            } catch (e: Exception) {
                AppLog.w("OfflinePacks", "clearTileCache: ${e.message}")
                ""
            }
            ui.post {
                if (isFinishing || isDestroyed) return@post
                if (json.isNotBlank()) {
                    applyTileStats(json)
                    Toast.makeText(this, "Cache tile pulita ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Pulizia cache non riuscita", Toast.LENGTH_SHORT).show()
                }
                // Aggiorna anche lo stato "installato" dei pacchetti offline.
                refresh()
            }
        }
    }

    private fun refresh() {
        loadTileStats()
        io.execute {
            val packs = OfflinePackManager.packs(this)
            val info = packs.map { p ->
                Triple(p, OfflinePackManager.isInstalled(this, p),
                    OfflinePackManager.missingKeys(this, p).size)
            }
            ui.post {
                if (isFinishing || isDestroyed) return@post
                render(info)
            }
        }
    }

    private fun render(info: List<Triple<OfflinePackManager.Pack, Boolean, Int>>) {
        listBox.removeAllViews()
        val d = resources.displayMetrics.density
        fun Int.dp() = (this * d).toInt()
        var installed = 0

        for ((p, isInstalled, missing) in info) {
            if (p.kind != "world" && isInstalled) installed++
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
                background = GradientDrawable().apply {
                    cornerRadius = 12f * d
                    setColor(Color.parseColor(
                        if (p.kind == "world") "#2A1236" else "#1A0A33"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = 8.dp() }
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val cb = CheckBox(this).apply {
                isChecked = OfflinePackManager.isFlagged(this@OfflinePacksActivity, p.id)
                setOnCheckedChangeListener { _, on ->
                    OfflinePackManager.setFlag(this@OfflinePacksActivity, p.id, on)
                    summary.text = "Preferenza aggiornata"
                }
            }
            row.addView(cb, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            texts.addView(TextView(this).apply {
                text = if (p.kind == "world") "🌍 ${p.label}" else "🏙️ ${p.label}"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            val sizeMb = p.installedBytes / 1_000_000
            val state = if (isInstalled) "Installato ✓" else "Da scaricare: $missing tile · ~$sizeMb MB"
            texts.addView(TextView(this).apply {
                text = "$state  (${p.keys.size} tile totali)"
                textSize = 12f
                setTextColor(Color.parseColor(if (isInstalled) "#00FF88" else "#FFB74D"))
            })
            row.addView(texts)

            if (!isInstalled) {
                row.addView(button("Scarica", "#2E7D32") { downloadPack(p.id, p.label) })
            }
            card.addView(row)
            listBox.addView(card)
        }

        val worldInstalled = info.firstOrNull { it.first.kind == "world" }?.second == true
        summary.text = when {
            worldInstalled -> "🌍 Tutta Italia è già sul dispositivo: nessun download necessario."
            installed > 0 -> "$installed città installate. Le altre useranno il server fino al download."
            else -> "Nessun pacchetto installato: MiCittà userà il server come sempre."
        }
    }

    private fun downloadPack(id: String, label: String) {
        showProgress("Download $label")
        io.execute {
            try {
                val ok = OfflinePackManager.downloadPack(this, id) { phase, cur, tot, bytes ->
                    ui.post { updateProgress(phase, cur, tot, bytes) }
                }
                ui.post {
                    dismissProgress()
                    Toast.makeText(this,
                        if (ok) "$label disponibile offline ✓" else "Download incompleto: riprova",
                        Toast.LENGTH_LONG).show()
                    refresh()
                }
            } catch (e: Exception) {
                AppLog.w("OfflinePacks", "download $id: ${e.message}")
                ui.post {
                    dismissProgress()
                    Toast.makeText(this, e.message ?: "Download fallito", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun downloadSelected() {
        val ids = OfflinePackManager.flaggedIds(this).toList()
        if (ids.isEmpty()) {
            Toast.makeText(this, "Seleziona almeno una città o Tutta Italia", Toast.LENGTH_SHORT).show()
            return
        }
        showProgress("Download pacchetti selezionati")
        io.execute {
            try {
                val ok = OfflinePackManager.downloadPacks(this, ids) { phase, cur, tot, bytes ->
                    ui.post { updateProgress(phase, cur, tot, bytes) }
                }
                ui.post {
                    dismissProgress()
                    Toast.makeText(this,
                        if (ok) "Pacchetti disponibili offline ✓" else "Alcune tile non sono state scaricate",
                        Toast.LENGTH_LONG).show()
                    refresh()
                }
            } catch (e: Exception) {
                AppLog.w("OfflinePacks", "download selezionati: ${e.message}")
                ui.post {
                    dismissProgress()
                    Toast.makeText(this, e.message ?: "Download fallito", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showProgress(title: String) {
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (18 * d).toInt(), (20 * d).toInt(), (18 * d).toInt())
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val label = TextView(this).apply {
            text = "Preparazione…"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, (10 * d).toInt(), 0, 0)
        }
        box.addView(bar)
        box.addView(label)
        progressBar = bar
        progressLabel = label
        progressDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setCancelable(false)
            .create()
        progressDialog?.show()
    }

    private fun updateProgress(phase: String, cur: Int, tot: Int, bytes: Long) {
        val bar = progressBar ?: return
        val label = progressLabel ?: return
        bar.progress = if (tot <= 0) 100 else ((cur - 1) * 100 / tot).coerceIn(1, 99)
        label.text = when (phase) {
            "cache" -> "Tile $cur/$tot già presente"
            "graph" -> "Tile $cur/$tot: strade…"
            "geo" -> "Tile $cur/$tot: edifici (prima generazione: qualche minuto)…"
            "done" -> "Completato ✓"
            else -> "Tile $cur/$tot"
        }
        if (bytes > 0) label.append(" · ${bytes / 1_000_000} MB")
    }

    private fun dismissProgress() {
        try { progressDialog?.dismiss() } catch (_: Exception) {}
        progressDialog = null
        progressBar = null
        progressLabel = null
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdownNow()
        dismissProgress()
    }
}
