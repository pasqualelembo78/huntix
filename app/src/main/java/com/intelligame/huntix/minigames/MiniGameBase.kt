package com.intelligame.huntix.minigames

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit

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
}
