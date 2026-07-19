package com.intelligame.huntix

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.managers.SavedManager

/**
 * StatsActivity — statistiche del giocatore lette dal PlayerProfile.
 */
class StatsActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val p = PlayerProfileManager.myProfile
        val mvc = try { SavedManager.getMvcBalance(c) } catch (_: Exception) { 0.0 }

        val content = UiKit.scroll(c,
            UiKit.title(c, "Statistiche", "📊"),
            UiKit.card(c,
                UiKit.row(c, "Livello", "${p?.level ?: 1}"),
                UiKit.row(c, "Titolo", p?.title ?: "Novizio"),
                UiKit.row(c, "XP totali", "${p?.xp ?: 0}"),
                UiKit.row(c, "Potere", "${p?.power ?: 0}"),
                UiKit.row(c, "Gemme 💎", "${p?.gems ?: 0}"),
                UiKit.row(c, "MVC ⚡", "%.0f".format(mvc))
            ),
            UiKit.section(c, "Caccia alle uova"),
            UiKit.card(c,
                UiKit.row(c, "Uova trovate", "${p?.eggsFound ?: 0}"),
                UiKit.row(c, "Comuni", "${p?.commonFound ?: 0}"),
                UiKit.row(c, "Non comuni", "${p?.uncommonFound ?: 0}"),
                UiKit.row(c, "Rare", "${p?.rareFound ?: 0}"),
                UiKit.row(c, "Epiche", "${p?.epicFound ?: 0}"),
                UiKit.row(c, "Leggendarie", "${p?.legendaryFound ?: 0}")
            ),
            UiKit.section(c, "Allenamento"),
            UiKit.card(c,
                UiKit.row(c, "Forza", "${p?.strength ?: 0}"),
                UiKit.row(c, "Palestre visitate", "${p?.gymsVisited ?: 0}"),
                UiKit.row(c, "Sessioni allenamento", "${p?.gymTrainings ?: 0}"),
                UiKit.row(c, "Energia", "${p?.energy ?: 100}")
            ),
            UiKit.section(c, "Social & Settimanale"),
            UiKit.card(c,
                UiKit.row(c, "XP settimanali", "${p?.weeklyXp ?: 0}"),
                UiKit.row(c, "Uova settimanali", "${p?.weeklyEggsFound ?: 0}"),
                UiKit.row(c, "Giorni di login", "${p?.totalLoginDays ?: 0}")
            )
        )
        setContentView(content)
    }
}
