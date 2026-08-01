package com.example.battle

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FighterSelectActivity : AppCompatActivity() {

    private val fighters = listOf(
        "Falcon" to "Velocità alta, attacco medio",
        "Dragon" to "Attacco alto, resistenza bassa",
        "Phoenix" to "Rigenerazione, attacco bilanciato"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL

        val title = TextView(this)
        title.text = "Seleziona il tuo combattente"
        title.textSize = 24f
        container.addView(title)

        fighters.forEachIndexed { index, (name, desc) ->
            val btn = Button(this)
            btn.text = "$name\n$desc"
            btn.setOnClickListener {
                startBattle(index)
            }
            container.addView(btn)
        }

        setContentView(container)
    }

    private fun startBattle(fighterIndex: Int) {
        // Avvia scontro AR contro nemico casuale
        val rand = kotlin.random.Random.nextInt(3)
        val playerPower = fighterIndex + 50
        val enemyPower = rand + 40
        val won = playerPower >= enemyPower

        val score = if (won) 200 else 50
        setResult(RESULT_OK, Intent().putExtra("score", score))
        finish()
    }
}