package com.intelligame.huntix.minigames

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager

/**
 * MiniGameBase — scheletro condiviso per i minigiochi (classici e AR).
 * Mostra titolo, regole e l'area di gioco. La logica di gioco completa
 * va implementata per ciascun titolo.
 */
abstract class MiniGameBase : AppCompatActivity() {

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onGameCreate()
    }

    protected abstract fun onGameCreate()

    protected fun build(title: String, emoji: String, rules: String, playArea: View) {
        val c = this
        val rulesView = android.widget.TextView(c).apply {
            text = rules; textSize = 13f
            setTextColor(android.graphics.Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, UiKit.dp(c, 6), 0, UiKit.dp(c, 10))
        }
        setContentView(UiKit.scroll(c, UiKit.title(c, title, emoji), UiKit.card(c, rulesView), playArea))
    }

    // ─── Livelli ──────────────────────────────────────────────────

    /** Difficoltà 0..1 del livello corrente del gioco. */
    protected fun levelDifficulty(gameId: String): Float =
        MiniGameManager.levelDifficulty(this, gameId)

    /** Livello corrente del gioco. */
    protected fun currentLevel(gameId: String): Int =
        MiniGameManager.getLevel(this, gameId)

    /** Banner compatto "Livello N • Obiettivo X" da mettere in testa al gioco. */
    protected fun levelBanner(gameId: String): TextView = TextView(this).apply {
        val level  = MiniGameManager.getLevel(this@MiniGameBase, gameId)
        val target = MiniGameManager.getLevelTarget(this@MiniGameBase, gameId)
        val stars  = MiniGameManager.getTotalStars(this@MiniGameBase, gameId)
        text = "⭐ Livello $level  •  🎯 Obiettivo: $target  •  $stars stelle"
        textSize = 13f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor(UiKit.ACCENT))
        setPadding(0, 0, 0, UiKit.dp(this@MiniGameBase, 8))
    }

    /** Riga di riepilogo livello da inserire nell'overlay di fine partita. */
    protected fun levelResultView(result: MiniGameManager.LevelResult): TextView {
        val c = this
        val starsTxt = MiniGameManager.starsText(result.stars)
        val text = buildString {
            append("$starsTxt  ")
            when {
                result.levelUp -> append("🎉 Livello ${result.level + 1} sbloccato! Bonus +${result.gems} 💎")
                result.cleared -> append("Livello ${result.level} superato!")
                else -> append("Obiettivo: ${result.target} — riprova!")
            }
        }
        return TextView(c).apply {
            this.text = text
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(if (result.cleared) UiKit.GREEN else UiKit.TEXT_DIM))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 10))
        }
    }
}
