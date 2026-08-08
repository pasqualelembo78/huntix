package com.intelligame.huntix.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.unity3d.player.UnityPlayer
import com.unity3d.player.UnityPlayerActivity
import org.json.JSONObject

/**
 * IndoorActivity — vista 3D del negozio (Kenney Mini Market CC0).
 *
 * Estende UnityPlayerActivity: quando l'AAR Unity è presente avvia Unity
 * e carica lo store. IndoorManager riceve il POI via UnitySendMessage
 * e costruisce l'interno con StoreBuilder.
 *
 * Overlay nativo: joystick + bottone interazione + nome negozio + esci.
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

    private var joystick: JoystickView? = null
    private var btnInteract: Button? = null
    private var tvInteractHint: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val joystickTick = object : Runnable {
        override fun run() {
            val j = joystick
            if (j != null && (j.dx != 0f || j.dy != 0f)) {
                try {
                    UnityPlayer.UnitySendMessage("IndoorManager", "MovePlayer",
                        "${j.dx},${j.dy}")
                } catch (_: Exception) { }
            }
            handler.postDelayed(this, 33L) // ~30fps
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        poiJson = intent.getStringExtra(EXTRA_POI_DATA) ?: "{}"
        addOverlay()

        // Invia il POI al loader Unity una volta inizializzato.
        window?.decorView?.postDelayed({
            try {
                UnityPlayer.UnitySendMessage("IndoorManager", "LoadStoreFromPOI", poiJson)
            } catch (_: Exception) { }
        }, 800L)

        // Avvia il tick del joystick
        handler.postDelayed(joystickTick, 1000L)
    }

    private fun addOverlay() {
        val poi = try { JSONObject(poiJson) } catch (_: Exception) { JSONObject() }
        val name = poi.optString("name", "Negozio")
        val type = poi.optString("type", "")

        val root = FrameLayout(this)

        // Joystick in basso a sinistra
        joystick = JoystickView(this).apply {
            layoutParams = FrameLayout.LayoutParams(160, 160).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                marginStart = 32
                bottomMargin = 64
            }
        }
        root.addView(joystick)

        // Bottone interazione in basso a destra
        btnInteract = Button(this).apply {
            text = "👆 Interagisci"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setBackgroundColor(0xFF6C3A00.toInt())
            setPadding(24, 16, 24, 16)
            visibility = View.GONE
            setOnClickListener {
                try {
                    UnityPlayer.UnitySendMessage("InteractionManager", "TriggerInteraction", "")
                } catch (_: Exception) { }
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                marginEnd = 32
                bottomMargin = 64
            }
        }
        root.addView(btnInteract)

        // Hint testo sotto il bottone interazione
        tvInteractHint = TextView(this).apply {
            text = ""
            textSize = 11f
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                marginEnd = 16
                bottomMargin = 40
            }
        }
        root.addView(tvInteractHint)

        // HUD in alto: esci + nome
        val hud = LinearLayout(this).apply {
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
            addView(TextView(this@IndoorActivity).apply {
                text = "Muoviti con la levetta \u00B7 avvicinati agli oggetti per interagire"
                textSize = 11f
                setTextColor(0x88FFFFFF.toInt())
            })
        }
        root.addView(hud, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(root)
    }

    /** Called from UnityBridge when an interactable is found/lost. */
    fun onInteractableFound(json: String) {
        runOnUiThread {
            try {
                val obj = JSONObject(json)
                val found = obj.optBoolean("found", false)
                if (found) {
                    val data = obj.optJSONObject("data")
                    btnInteract?.visibility = View.VISIBLE
                    btnInteract?.text = "${data?.optString("emoji", "👆")}  ${data?.optString("name", "Interagisci")}"
                    tvInteractHint?.visibility = View.VISIBLE
                    tvInteractHint?.text = data?.optString("action", "") ?: ""
                } else {
                    btnInteract?.visibility = View.GONE
                    tvInteractHint?.visibility = View.GONE
                }
            } catch (_: Exception) {
                btnInteract?.visibility = View.GONE
                tvInteractHint?.visibility = View.GONE
            }
        }
    }

    /** Called from UnityBridge when interaction completes. */
    fun onInteractionResult(json: String) {
        runOnUiThread {
            // Flash feedback
            btnInteract?.setBackgroundColor(0xFF2E7D32.toInt())
            btnInteract?.text = "✅ Fatto!"
            handler.postDelayed({
                btnInteract?.visibility = View.GONE
                tvInteractHint?.visibility = View.GONE
                btnInteract?.setBackgroundColor(0xFF6C3A00.toInt())
            }, 1200L)
        }
    }

    /** Called from UnityBridge when an NPC is nearby. */
    fun onNPCNearby(json: String) {
        runOnUiThread {
            try {
                val obj = JSONObject(json)
                btnInteract?.visibility = View.VISIBLE
                btnInteract?.text = "${obj.optString("emoji", "🧑")}  Parla con ${obj.optString("name", "NPC")}"
                btnInteract?.setBackgroundColor(0xFF1565C0.toInt())
                btnInteract?.setOnClickListener {
                    try {
                        UnityPlayer.UnitySendMessage("NPC", "Talk", obj.optString("id", ""))
                    } catch (_: Exception) { }
                }
                tvInteractHint?.visibility = View.VISIBLE
                tvInteractHint?.text = obj.optString("role", "")
            } catch (_: Exception) { }
        }
    }

    /** Called from UnityBridge when an NPC dialogue is triggered. */
    fun onNPCDialogue(json: String) {
        runOnUiThread {
            try {
                val obj = JSONObject(json)
                val name = obj.optString("name", "NPC")
                val dialogue = obj.optString("dialogue", "")
                val hasQuest = obj.optBoolean("hasQuest", false)

                // Show dialogue as toast + update button
                android.widget.Toast.makeText(this, "$name: $dialogue", android.widget.Toast.LENGTH_LONG).show()

                if (hasQuest) {
                    btnInteract?.text = "📋 Accetta Missione"
                    btnInteract?.setBackgroundColor(0xFFE65100.toInt())
                    btnInteract?.setOnClickListener {
                        try {
                            UnityPlayer.UnitySendMessage("NPC", "AcceptQuest", obj.optString("id", ""))
                        } catch (_: Exception) { }
                    }
                } else {
                    handler.postDelayed({
                        btnInteract?.visibility = View.GONE
                        tvInteractHint?.visibility = View.GONE
                        btnInteract?.setOnClickListener(null)
                    }, 2000L)
                }
            } catch (_: Exception) { }
        }
    }

    /** Called from UnityBridge when NPC is far again. */
    fun onNPCFar(json: String) {
        runOnUiThread {
            btnInteract?.visibility = View.GONE
            tvInteractHint?.visibility = View.GONE
            btnInteract?.setOnClickListener(null)
        }
    }

    /** Callback da Unity quando la scena è pronta. */
    fun onIndoorSceneReady(poiId: String) {
        // Could refresh interactables list here
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(joystickTick)
        if (instance === this) instance = null
    }
}
