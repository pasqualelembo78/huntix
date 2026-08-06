package com.intelligame.huntix.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.unity3d.player.UnityPlayer
import com.unity3d.player.UnityPlayerActivity
import org.json.JSONObject

/**
 * IndoorActivity — vista 3D del negozio (supermarket Unity open source).
 *
 * Estende UnityPlayerActivity: quando l'AAR Unity (con le scene dello store)
 * è presente avvia Unity e carica lo store; il bridge C# (HuntixStoreBridge)
 * legge il JSON del POI dall'extra "POI_DATA" e lancia GameScene.
 *
 * Overlay nativo in alto: nome del negozio + pulsante per uscire.
 * Nome store del progetto Android: .ui.IndoorActivity
 */
class IndoorActivity : UnityPlayerActivity() {

    companion object {
        const val EXTRA_POI_DATA = "POI_DATA"

        @Volatile
        var instance: IndoorActivity? = null
            private set
    }

    var poiJson: String = ""
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        poiJson = intent.getStringExtra(EXTRA_POI_DATA) ?: "{}"
        addExitOverlay()

        // Invia il POI al loader Unity una volta inizializzato.
        window?.decorView?.postDelayed({
            try {
                UnityPlayer.UnitySendMessage("HuntixStoreBridge", "LoadStoreFromPOI", poiJson)
            } catch (_: Exception) {
                // Lo store legge comunque POI_DATA dall'intent (HuntixStoreBridge.Start)
            }
        }, 800L)
    }

    private fun addExitOverlay() {
        val poi = try {
            JSONObject(poiJson)
        } catch (_: Exception) {
            JSONObject()
        }
        val name = poi.optString("name", "Negozio")
        val type = poi.optString("type", "")

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 0)
            addView(LinearLayout(this@IndoorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@IndoorActivity).apply {
                    text = "\u2716  Esci"
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                    setPadding(18, 8, 18, 8)
                    setBackgroundColor(0x661A1030.toInt())
                    setOnClickListener { finish() }
                })
                addView(TextView(this@IndoorActivity).apply {
                    text = "  $name${if (type.isNotBlank()) " \u00B7 $type" else ""}"
                    textSize = 13f
                    setTextColor(0xCCFFFFFF.toInt())
                    maxLines = 1
                })
            })
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        addContentView(overlay, lp)
    }

    /** Callback da Unity quando la scena GameScene è pronta. */
    fun onIndoorSceneReady(poiId: String) {
        // Hook per aggiornamenti UI quando lo store 3D è caricato.
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }
}
