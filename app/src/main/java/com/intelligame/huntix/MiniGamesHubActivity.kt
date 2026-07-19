package com.intelligame.huntix

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.managers.MiniGameManager
import com.intelligame.huntix.minigames.*

/**
 * MiniGamesHubActivity — hub centrale di tutti i minigiochi (classici + AR).
 */
class MiniGamesHubActivity : BaseNavActivity() {

    override fun activeTab() = ""

    data class GameEntry(
        val id: String, val label: String, val emoji: String,
        val cls: Class<*>, val isAr: Boolean
    )

    private val games = listOf(
        GameEntry(MiniGameManager.GAME_MEMORY, "Memory", "🧠", MemoryGameActivity::class.java, false),
        GameEntry(MiniGameManager.GAME_NUMBER_PICK, "Scegli il Numero", "🔢", NumberPickActivity::class.java, false),
        GameEntry(MiniGameManager.GAME_HIGH_CARD, "Carta Alta", "🃏", HighCardActivity::class.java, false),
        GameEntry(MiniGameManager.GAME_CATCH_EGG, "Prendi l'Uovo", "🥚", CatchEggActivity::class.java, false),
        GameEntry(MiniGameManager.GAME_MATCH3, "Match 3", "💎", Match3Activity::class.java, false),
        GameEntry(MiniGameManager.GAME_AR_SHOOTER, "AR Egg Shooter", "🔫", com.intelligame.huntix.minigames.ar.AREggShooterActivity::class.java, true),
        GameEntry(MiniGameManager.GAME_AR_BOMB, "AR Color Bomb", "💣", com.intelligame.huntix.minigames.ar.ARColorBombActivity::class.java, true),
        GameEntry(MiniGameManager.GAME_AR_RADAR, "AR Egg Radar", "📡", com.intelligame.huntix.minigames.ar.AREggRadarActivity::class.java, true),
        GameEntry("ar_high_card", "AR Carta Alta", "🃏", com.intelligame.huntix.minigames.ar.ARHighCardActivity::class.java, true),
        GameEntry("ar_match3", "AR Match 3", "💎", com.intelligame.huntix.minigames.ar.ARMatch3Activity::class.java, true),
        GameEntry("ar_memory", "AR Memory", "🧠", com.intelligame.huntix.minigames.ar.ARMemoryActivity::class.java, true),
        GameEntry("ar_number_pick", "AR Numero", "🔢", com.intelligame.huntix.minigames.ar.ARNumberPickActivity::class.java, true),
        GameEntry("ar_three_card", "AR Tre Carte", "🎴", com.intelligame.huntix.minigames.ar.ARThreeCardActivity::class.java, true),
        GameEntry("ar_catch_egg", "AR Prendi Uovo", "🥚", com.intelligame.huntix.minigames.ar.ARCatchEggActivity::class.java, true)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        render(box)
        setContentView(UiKit.scroll(c, UiKit.title(c, "Minigiochi", "🎮"), UiKit.section(c, "Scegli un gioco"), box))
    }

    private fun render(box: LinearLayout) {
        box.removeAllViews()
        val c = this
        games.forEach { g ->
            val remaining = try { MiniGameManager.remainingPlays(c, g.id) } catch (_: Exception) { 3 }
            val can = remaining > 0
            box.addView(UiKit.button(c, "${if (g.isAr) "📱 " else ""}${g.emoji}  ${g.label}  (${remaining})",
                if (can) UiKit.PURPLE else "#444") {
                if (!can) return@button
                try { MiniGameManager.consumePlay(c, g.id) } catch (_: Exception) { }
                startActivity(Intent(c, g.cls))
            })
        }
    }
}
