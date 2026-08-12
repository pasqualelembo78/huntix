package com.intelligame.huntix.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.managers.MiniGameManager
import com.unity3d.player.UnityPlayerActivity

/**
 * Base per i nuovi minigiochi Unity (Laugh Little Lamb normale + AR-Dice AR).
 *
 * Imposta l'extra "unity_mode" PRIMA di super.onCreate() così GameManager.Start()
 * carica la scena giusta ("game" -> Preload, "argame" -> MainScene).
 * Al ritorno registra la giocata su MiniGameManager (ricompense + livelli).
 */
abstract class UnityGameBaseActivity(
    private val unityMode: String,
    private val gameId: String,
    private val gameLabel: String
) : UnityPlayerActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLog.i("UnityGame", "openUnity start mode=$unityMode ($gameLabel)")
        intent.putExtra("unity_mode", unityMode)
        super.onCreate(savedInstanceState)
        AppLog.i("UnityGame", "openUnity end mode=$unityMode")
        addExitButton()
    }

    /** Piccolo bottone "Esci" in alto a sinistra, sopra la vista Unity. */
    private fun addExitButton() {
        try {
            val root = FrameLayout(this)
            root.addView(TextView(this).apply {
                text = "\u2716  Esci"
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                setPadding(18, 8, 18, 8)
                setBackgroundColor(0x661A1030.toInt())
                setOnClickListener { finish() }
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                marginStart = 12
                topMargin = 12
            })
            addContentView(root, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        } catch (_: Exception) { }
    }

    override fun onBackPressed() {
        try { finish() } catch (_: Exception) { super.onBackPressed() }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            MiniGameManager.completePlay(
                this, gameId, 1,
                mvc = 25, xp = 10, label = gameLabel, isWin = true
            )
        } catch (_: Exception) { }
    }
}

/** Gioco Unity normale (Laugh Little Lamb): scena "Preload". */
class UnityGameActivity : UnityGameBaseActivity(
    unityMode = "game",
    gameId = MiniGameManager.GAME_UNITY_SHEEP,
    gameLabel = "Pecorelle"
)

/** Gioco Unity in AR (AR-Dice): scena "MainScene". */
class UnityARGameActivity : UnityGameBaseActivity(
    unityMode = "argame",
    gameId = MiniGameManager.GAME_UNITY_AR_DICE,
    gameLabel = "AR Dadi"
)
