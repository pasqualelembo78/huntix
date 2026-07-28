// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.managers.DistanceTracker
import com.intelligame.huntix.managers.TermocullaManager
import com.intelligame.huntix.managers.SavedManager
import com.intelligame.huntix.ui.EggOpeningAnimationActivity

class HatchingActivity : BaseNavActivity() {

    override fun activeTab() = "Uova"

    private lateinit var termocullasBox: LinearLayout
    private lateinit var activeBox: LinearLayout
    private lateinit var pendingBox: LinearLayout
    private lateinit var hatchedBox: LinearLayout
    private lateinit var kmLabel: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing) { render(); handler.postDelayed(this, 1000) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        termocullasBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        activeBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        pendingBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        hatchedBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        kmLabel = TextView(c).apply {
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM)); gravity = Gravity.CENTER
        }

        val content = UiKit.scroll(c,
            UiKit.title(c, "Uova", "🥚"),
            UiKit.button(c, "🔄 Aggiorna", UiKit.ACCENT) { render() },
            UiKit.button(c, "⚔️ Battaglia", UiKit.PURPLE) {
                startActivity(Intent(c, com.intelligame.huntix.ui.BattleActivity::class.java))
            },
            kmLabel,
            UiKit.section(c, "Termoculle"), termocullasBox,
            UiKit.section(c, "In Schiusura"), activeBox,
            UiKit.section(c, "Da Schiudere"), pendingBox,
            UiKit.section(c, "Collezione"), hatchedBox
        )
        setContentView(content)

        // Start distance tracking
        if (!DistanceTracker.isListening(c)) {
            DistanceTracker.startListening(c) { /* distance fed to termocullas internally */ }
        }

        render()
    }

    private fun render() {
        val c = this
        termocullasBox.removeAllViews()
        activeBox.removeAllViews()
        pendingBox.removeAllViews()
        hatchedBox.removeAllViews()

        val totalKm = DistanceTracker.getTotalKm(c)
        val sessionSteps = DistanceTracker.getSessionSteps(c)
        kmLabel.text = "🚶 %.2f km totali · %d passi sessione".format(totalKm, sessionSteps)

        // Termocullas
        val termocullas = TermocullaManager.getTermocullas(c)
        val activeEggs = TermocullaManager.getActiveEggs(c)
        termocullas.forEach { inc ->
            val activeEgg = activeEggs.firstOrNull { it.termocullaId == inc.id }
            val status = when {
                inc.isBroken && activeEgg == null -> "❌ Rotta"
                activeEgg != null && activeEgg.isReady -> "✅ Pronta!"
                activeEgg != null -> "🚶 %.1f/%.1f km".format(activeEgg.distanceWalked, activeEgg.distanceRequired)
                else -> "🟢 Libera"
            }
            val usesText = if (inc.isIllimitato) "∞ usi" else "${inc.usiRimanenti} usi"

            termocullasBox.addView(UiKit.card(c,
                TextView(c).apply {
                    text = "${inc.name}  ·  $usesText"
                    textSize = 13f; setTextColor(Color.WHITE)
                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                },
                TextView(c).apply {
                    text = status
                    textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                    setPadding(0, UiKit.dp(c, 4), 0, 0)
                }
            ))
        }

        // Active eggs in termocullas
        activeEggs.forEach { egg ->
            val rarity = egg.rarity
            val ready = egg.isReady

            val progressBar = ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000; progress = (egg.progress * 1000).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(c, 8)
                ).apply { topMargin = UiKit.dp(c, 8) }
            }

            val card = UiKit.card(c,
                TextView(c).apply {
                    text = "${rarity.emoji} ${rarity.displayName}"
                    textSize = 14f; setTextColor(Color.WHITE)
                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                },
                TextView(c).apply {
                    text = if (ready) "✅ Pronta da raccogliere!"
                    else "🚶 %.1f / %.1f km".format(egg.distanceWalked, egg.distanceRequired)
                    textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                    setPadding(0, UiKit.dp(c, 4), 0, 0)
                },
                progressBar
            )

            if (ready) {
                card.addView(UiKit.button(c, "🎉 Raccogli", UiKit.GREEN) {
                    val collected = TermocullaManager.collectHatchedEgg(c, egg.istanzaId)
                    if (collected != null) {
                        val rarity2 = EggRarity.fromId(collected.rarityId)
                        SavedManager.addPendingEgg(c, EggInventoryItem(
                            istanzaId = collected.istanzaId,
                            rarityId = collected.rarityId,
                            fantasyName = collected.fantasyName,
                            power = rarity2.basePower
                        ))
                        startActivity(Intent(c, EggOpeningAnimationActivity::class.java)
                            .putExtra(EggOpeningAnimationActivity.EXTRA_RARITY_ID, collected.rarityId))
                        render()
                    }
                })
            }

            activeBox.addView(card)
        }

        if (activeEggs.isEmpty()) {
            activeBox.addView(UiKit.comingSoon(c, "Nessuna uova in termoculla", "Seleziona un uova da schiudere qui sotto."))
        }

        // Pending eggs (non ancora in schiusa)
        val pending = SavedManager.getPendingEggs(c)
        if (pending.isEmpty()) pendingBox.addView(UiKit.comingSoon(c, "Nessuna uova", "Gioca per ottenere uova!"))
        pending.forEach { item ->
            val rarity = EggRarity.fromId(item.rarityId)
            val distLabel = TermocullaManager.distanceLabelForRarity(rarity)
            val freeTermocullas = TermocullaManager.getFreeTermocullas(c)

            pendingBox.addView(UiKit.card(c,
                TextView(c).apply {
                    text = "${rarity.emoji} ${rarity.displayName}  ·  $distLabel"
                    textSize = 14f; setTextColor(Color.WHITE)
                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                },
                TextView(c).apply {
                    text = "${item.fantasyName.ifBlank { "Uovo" }}  ·  ⚔️ ${item.power}"
                    textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                    setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 8))
                },
                UiKit.button(c,
                    if (freeTermocullas.isNotEmpty()) "🧬 Metti in termoculla" else "❌ Nessuna termoculla libera",
                    if (freeTermocullas.isNotEmpty()) UiKit.PURPLE else "#444"
                ) {
                    if (freeTermocullas.isNotEmpty()) {
                        if (TermocullaManager.startSchiusa(c, item, freeTermocullas.first().id)) {
                            SavedManager.removePendingEgg(c, item.istanzaId)
                            render()
                        }
                    }
                }
            ))
        }

        // Hatched collection
        val hatched = SavedManager.getHatchedEggs(c)
        if (hatched.isEmpty()) hatchedBox.addView(UiKit.comingSoon(c, "Ancora vuota", "Le uova schiuse appaiono qui."))
        hatched.take(20).forEach { egg ->
            hatchedBox.addView(UiKit.row(c, "${egg.emoji} ${egg.displayName}", "${egg.sourceRarityId} · Lv.${egg.level}"))
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
