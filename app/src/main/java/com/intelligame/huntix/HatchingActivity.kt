package com.intelligame.huntix

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.ui.EggOpeningAnimationActivity

/**
 * HatchingActivity — tab "Uova": schiusa, slot in corso e uova schiuse.
 */
class HatchingActivity : BaseNavActivity() {

    override fun activeTab() = "Uova"

    private lateinit var slotsBox: LinearLayout
    private lateinit var pendingBox: LinearLayout
    private lateinit var hatchedBox: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        slotsBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        pendingBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        hatchedBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        val content = UiKit.scroll(c,
            UiKit.title(c, "Uova", "🥚"),
            UiKit.button(c, "🔄  Aggiorna", UiKit.ACCENT) { render() },
            UiKit.section(c, "In schiusura"), slotsBox,
            UiKit.section(c, "Da schiudere"), pendingBox,
            UiKit.section(c, "Collezione"), hatchedBox
        )
        setContentView(content)
        render()
    }

    private fun render() {
        val c = this
        slotsBox.removeAllViews(); pendingBox.removeAllViews(); hatchedBox.removeAllViews()

        // Slot in corso
        val slots = SavedManager.getHatchingSlots(c)
        if (slots.isEmpty()) slotsBox.addView(UiKit.comingSoon(c, "Nessuna schiusura", "Schiudi un'uovo per iniziare."))
        slots.forEach { slot ->
            val ready = System.currentTimeMillis() >= slot.endMs
            slotsBox.addView(UiKit.card(c,
                TextView(c).apply {
                    text = "⏳ ${EggRarity.fromId(slot.sourceRarityId).displayName}"
                    textSize = 14f; setTextColor(android.graphics.Color.WHITE)
                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                },
                TextView(c).apply {
                    text = if (ready) "Pronta da raccogliere!" else "Mancano ${((slot.endMs - System.currentTimeMillis()) / 1000)}s"
                    textSize = 12f; setTextColor(android.graphics.Color.parseColor(UiKit.TEXT_DIM))
                    setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 8))
                },
                UiKit.button(c, if (ready) "🎉 Raccogli" else "⏳ In corso", if (ready) UiKit.GREEN else "#444") {
                    if (ready) {
                        val hatched = SavedManager.collectReady(c)
                        hatched.firstOrNull()?.let {
                            startActivity(Intent(c, EggOpeningAnimationActivity::class.java)
                                .putExtra(EggOpeningAnimationActivity.EXTRA_RARITY_ID, it.sourceRarityId))
                        }
                        render()
                    }
                }
            ))
        }

        // Da schiudere
        val pending = SavedManager.getPendingEggs(c)
        if (pending.isEmpty()) pendingBox.addView(UiKit.comingSoon(c, "Nessuna uova", "Gioca per ottenere uova!"))
        pending.forEach { item ->
            pendingBox.addView(UiKit.card(c,
                TextView(c).apply {
                    text = "🥚 ${item.rarity.displayName}"
                    textSize = 14f; setTextColor(android.graphics.Color.WHITE)
                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                },
                UiKit.button(c, "🌡️ Schiudi", UiKit.PURPLE) {
                    if (SavedManager.startHatching(c, item)) {
                        SavedManager.removePendingEgg(c, item.instanceId)
                        render()
                    }
                }
            ))
        }

        // Collezione
        val hatched = SavedManager.getHatchedEggs(c)
        if (hatched.isEmpty()) hatchedBox.addView(UiKit.comingSoon(c, "Ancora vuota", "Le uova schiuse appaiono qui."))
        hatched.take(20).forEach { egg ->
            hatchedBox.addView(UiKit.row(c, "⭐ ${egg.displayName}", egg.sourceRarityId))
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed({ if (!isFinishing) render() }, 1000)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
