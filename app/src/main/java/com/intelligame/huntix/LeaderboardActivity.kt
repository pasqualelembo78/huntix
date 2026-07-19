package com.intelligame.huntix

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * LeaderboardActivity — classifica mondiale (solo Google + outdoor).
 */
class LeaderboardActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val listContainer = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val status = TextView(c).apply {
            text = "⏳ Caricamento classifica…"; textSize = 13f
            setTextColor(android.graphics.Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, UiKit.dp(c, 8), 0, 0)
        }
        val content = UiKit.scroll(c, UiKit.title(c, "Classifica", "🏆"), status, listContainer)
        setContentView(content)

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                PlayerProfileManager.getLeaderboard(
                    onResult = { entries ->
                        runOnUiThread {
                            status.visibility = android.view.View.GONE
                            if (entries.isEmpty()) {
                                listContainer.addView(UiKit.comingSoon(c, "Nessun dato", "Gioca in modalità Outdoor per entrare in classifica!"))
                                return@runOnUiThread
                            }
                            entries.forEachIndexed { i, e ->
                                listContainer.addView(UiKit.card(c,
                                    TextView(c).apply {
                                        text = "#${i + 1}  ${e.name}  ·  Lv.${e.level}"
                                        textSize = 14f; setTextColor(android.graphics.Color.WHITE)
                                        setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                                    },
                                    TextView(c).apply {
                                        text = "${e.title}  —  ⚔️ ${e.power}  ·  🥚 ${e.eggsFound}"
                                        textSize = 12f; setTextColor(android.graphics.Color.parseColor(UiKit.TEXT_DIM))
                                        setPadding(0, UiKit.dp(c, 4), 0, 0)
                                    }
                                ))
                            }
                        }
                    },
                    onError = { msg ->
                        runOnUiThread {
                            status.text = "Errore: $msg"
                            Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}
