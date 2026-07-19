package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.PlayerProfileManager
import com.intelligame.huntix.BaseNavActivity

/**
 * CharacterSelectionActivity — Selezione del personaggio player 3D.
 *
 * Mostra tutti i personaggi disponibili in assets/characters/player/
 * (file .glb). Il player può scegliere il proprio personaggio.
 *
 * REGOLE:
 *  - Prima selezione: GRATIS
 *  - Seconda modifica: GRATIS
 *  - Modifiche successive: 500 MVC ciascuna
 *
 * I file GLB vanno copiati in:
 *   app/src/main/assets/characters/player/
 * con nome: {id}.glb  es: guerriero.glb, mago.glb, elfo.glb, ...
 */
class CharacterSelectionActivity : BaseNavActivity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT

    private var selectedCharId: String? = null
    private var previewWebView: WebView? = null
    private var confirmBtn: Button? = null
    private var availableChars: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        availableChars = listCharacterFiles()
        buildUI()
    }

    /** Lista tutti i file .glb nella cartella assets/characters/player/ */
    private fun listCharacterFiles(): List<String> {
        return try {
            assets.list("characters/player")
                ?.filter { it.endsWith(".glb") || it.endsWith(".gltf") }
                ?.map { it.removeSuffix(".glb").removeSuffix(".gltf") }
                ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A1A"))
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }

        // Header
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#12103A"))
            setPadding(dp(12), dp(40), dp(16), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@CharacterSelectionActivity).apply {
                text = "←"; textSize = 22f; setTextColor(Color.WHITE)
                setPadding(0, 0, dp(12), 0)
                setOnClickListener { finish() }
            })
            addView(TextView(this@CharacterSelectionActivity).apply {
                text = "🧑 Scegli il tuo Personaggio"; textSize = 17f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
        })

        // Info costo
        val cost = PlayerProfileManager.getCharacterChangeCost()
        val isFree = cost == 0.0
        root.addView(TextView(this).apply {
            text = if (isFree) "✅ Questa selezione è GRATUITA"
                   else "⚠️ Cambio personaggio: ${cost.toInt()} MVC"
            textSize = 13f
            setTextColor(if (isFree) Color.parseColor("#00FF88") else Color.parseColor("#FFB300"))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(8))
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        })

        // Preview 3D
        val previewContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(220))
            setBackgroundColor(Color.parseColor("#0D0D1E"))
        }
        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webViewClient = WebViewClient()
            setBackgroundColor(Color.TRANSPARENT)
        }
        previewWebView = webView
        previewContainer.addView(webView)

        // Placeholder testo
        previewContainer.addView(TextView(this).apply {
            text = "👆 Seleziona un personaggio per vedere l'anteprima"
            textSize = 13f; setTextColor(Color.parseColor("#9999CC"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER)
        })
        root.addView(previewContainer)

        // Lista personaggi
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(16))
        }

        if (availableChars.isEmpty()) {
            grid.addView(TextView(this).apply {
                text = "📂 Nessun personaggio trovato.\n\nCopia i tuoi file .glb in:\nassets/characters/player/\n\nEsempio: guerriero.glb, mago.glb, elfo.glb"
                textSize = 14f; setTextColor(Color.parseColor("#9999CC"))
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(32), dp(24), dp(32))
            })
        } else {
            // Griglia 2 colonne
            var rowLayout: LinearLayout? = null
            availableChars.forEachIndexed { idx, charId ->
                if (idx % 2 == 0) {
                    rowLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(8) }
                    }
                    grid.addView(rowLayout)
                }
                rowLayout?.addView(buildCharCard(charId))
            }
        }

        scrollView.addView(grid)
        root.addView(scrollView)

        // Bottone conferma
        val btn = Button(this).apply {
            text = "🎮 Seleziona Personaggio"
            textSize = 16f; setTextColor(Color.WHITE)
            isEnabled = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(50).toFloat()
                setColor(Color.parseColor("#2A2A4A"))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(56)).also {
                it.setMargins(dp(16), dp(8), dp(16), dp(24))
            }
            setOnClickListener { confirmSelection() }
        }
        confirmBtn = btn
        root.addView(btn)

        setContentView(root)
    }

    private fun buildCharCard(charId: String): LinearLayout {
        val currentChar = PlayerProfileManager.myProfile?.playerCharacterId
        val isSelected = charId == selectedCharId
        val isCurrent = charId == currentChar

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(100), 1f).also {
                it.marginEnd = dp(4); it.marginStart = dp(4)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor(if (isSelected) "#2A1A5A" else "#1A1A2E"))
                setStroke(dp(2), Color.parseColor(when {
                    isSelected -> "#8B5CF6"
                    isCurrent  -> "#00FF88"
                    else       -> "#2A2A4A"
                }))
            }
            setPadding(dp(8), dp(12), dp(8), dp(8))

            // Emoji placeholder (fin quando il GLB non è caricato)
            addView(TextView(this@CharacterSelectionActivity).apply {
                text = charIdToEmoji(charId)
                textSize = 32f; gravity = Gravity.CENTER
            })
            addView(TextView(this@CharacterSelectionActivity).apply {
                text = charId.replaceFirstChar { it.uppercase() }
                    .replace("_", " ")
                textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
            if (isCurrent) {
                addView(TextView(this@CharacterSelectionActivity).apply {
                    text = "✅ attuale"; textSize = 10f
                    setTextColor(Color.parseColor("#00FF88")); gravity = Gravity.CENTER
                })
            }

            setOnClickListener { selectCharacter(charId) }
        }
    }

    private fun selectCharacter(charId: String) {
        selectedCharId = charId
        // Aggiorna preview 3D
        val glbPath = "file:///android_asset/characters/player/$charId.glb"
        previewWebView?.loadDataWithBaseURL("file:///android_asset/",
            buildViewerHtml(glbPath, charId), "text/html", "UTF-8", null)
        // Abilita bottone conferma
        confirmBtn?.apply {
            isEnabled = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(50).toFloat()
                setColor(Color.parseColor("#5C35CC"))
            }
            text = "✅ Conferma: ${charId.replaceFirstChar { it.uppercase() }}"
        }
        // Ricalcola UI lista (non ridistruggiamo tutto, solo testo bottone)
    }

    private fun confirmSelection() {
        val charId = selectedCharId ?: return
        val cost = PlayerProfileManager.getCharacterChangeCost()
        if (cost > 0) {
            // Mostra conferma con costo
            android.app.AlertDialog.Builder(this)
                .setTitle("Cambio Personaggio")
                .setMessage("Vuoi davvero cambiare personaggio?\nCosto: ${cost.toInt()} MVC")
                .setPositiveButton("Conferma") { _, _ -> doSetCharacter(charId) }
                .setNegativeButton("Annulla", null)
                .show()
        } else {
            doSetCharacter(charId)
        }
    }

    private fun doSetCharacter(charId: String) {
        val pb = ProgressBar(this)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Salvataggio...").setView(pb).create()
        dialog.show()
        PlayerProfileManager.setPlayerCharacter(this, charId,
            onComplete = {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "Personaggio aggiornato! 🎉", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            },
            onError = { msg ->
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "Errore: $msg", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun buildViewerHtml(glbSrc: String, name: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
  body { margin:0; background:#0D0D1E; display:flex; align-items:center; justify-content:center; height:100vh; }
  model-viewer { width:200px; height:200px; background:transparent; }
</style>
</head>
<body>
<script type="module" src="https://ajax.googleapis.com/ajax/libs/model-viewer/3.5.0/model-viewer.min.js"></script>
<model-viewer
  src="$glbSrc"
  alt="$name"
  auto-rotate
  camera-controls
  shadow-intensity="1"
  style="width:200px;height:200px;--progress-bar-color:#7C4DFF;">
</model-viewer>
</body>
</html>""".trimIndent()

    private fun charIdToEmoji(id: String): String = when {
        id.contains("guerrier") || id.contains("warrior") -> "⚔️"
        id.contains("mago") || id.contains("wizard")  -> "🧙"
        id.contains("elfo") || id.contains("elf")     -> "🧝"
        id.contains("nano") || id.contains("dwarf")   -> "⛏️"
        id.contains("arcier") || id.contains("arch")  -> "🏹"
        id.contains("ninja")                           -> "🥷"
        id.contains("robot")                           -> "🤖"
        id.contains("fairy") || id.contains("fata")   -> "🧚"
        else                                           -> "🧑"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
