package com.intelligame.huntix.ui

import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.*
import com.intelligame.huntix.managers.*
import com.intelligame.huntix.BaseNavActivity

/**
 * SurpriseInventoryActivity — LA BORSA
 * Mostra tutte le creature scoperte (OwnedSurprise).
 * NON mostra il team di combattimento (quello va in BattleActivity).
 * Permette di impostare l'amico fidato (buddy).
 */
class SurpriseInventoryActivity : BaseNavActivity() {

    private lateinit var root:          LinearLayout
    private lateinit var listContainer: LinearLayout
    private var sortMode = SORT_RARITY
    private var rarityFilter = "all"

    companion object {
        const val SORT_NAME    = "name"
        const val SORT_RARITY  = "rarity"
        const val SORT_STRENGTH = "strength"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildUI()
        } catch (e: Exception) {
            android.util.Log.e("SurpriseInventory", "CRASH in buildUI: ${e.message}", e)
            val tv = TextView(this).apply {
                text = "Errore caricamento borsa.\nRiavvia l'app.\n\n${e.message}"
                setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#0A0A1A"))
                setPadding(40, 80, 40, 40)
            }
            setContentView(tv)
        }
    }

    override fun onResume() {
        super.onResume()
        try { refreshList() } catch (e: Exception) {
            android.util.Log.e("SurpriseInventory", "CRASH in refreshList: ${e.message}", e)
        }
    }

    private fun buildUI() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0F0F2A")) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(48), dp(16), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        // Header
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(12) }
        }
        headerRow.addView(TextView(this).apply {
            text = "←"; textSize = 22f; setTextColor(Color.parseColor("#E0E0FF"))
            setPadding(0, 0, dp(12), 0)
            setOnClickListener { finish() }
        })
        headerRow.addView(TextView(this).apply {
            text = "🎒  La Borsa"; textSize = 20f
            setTextColor(Color.parseColor("#E0E0FF"))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(headerRow)

        // Subtitle
        root.addView(TextView(this).apply {
            text = "Le tue creature scoperte dalle uova. Scegli il tuo Amico Fidato! 🐾"
            textSize = 12f; setTextColor(Color.parseColor("#9999CC"))
            setPadding(0, 0, 0, dp(12))
        })

        // Sort bar
        root.addView(buildSortBar())

        // Rarity filter
        root.addView(buildRarityFilterBar())

        // Count label
        val countLabel = TextView(this).apply {
            textSize = 11f; setTextColor(Color.parseColor("#666699"))
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(countLabel)
        this.countLabel = countLabel

        // List container
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)
    }

    private var countLabel: TextView? = null

    private fun buildSortBar(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        row.addView(TextView(this).apply {
            text = "Ordina:"; textSize = 12f; setTextColor(Color.parseColor("#9999CC"))
            setPadding(0, 0, dp(8), 0)
        })
        listOf(
            SORT_RARITY to "Rarità",
            SORT_STRENGTH to "Forza",
            SORT_NAME to "A-Z"
        ).forEach { (mode, label) ->
            row.addView(sortChip(label, mode))
        }
        return row
    }

    private fun sortChip(label: String, mode: String): TextView {
        return TextView(this).apply {
            text = label; textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(20).toFloat()
                setColor(if (sortMode == mode) Color.parseColor("#00E5FF") else Color.parseColor("#B0BEC5"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.marginEnd = dp(6) }
            setOnClickListener {
                sortMode = mode
                refreshList()
                (root.getChildAt(3) as? LinearLayout)?.let { bar ->
                    for (i in 0 until bar.childCount) {
                        val chip = bar.getChildAt(i) as? TextView ?: continue
                        (chip.background as? GradientDrawable)?.setColor(
                            if (chip.text == label) Color.parseColor("#00E5FF") else Color.parseColor("#B0BEC5")
                        )
                    }
                }
            }
        }
    }

    private fun buildRarityFilterBar(): HorizontalScrollView {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(4) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }
        scroll.addView(row)

        val filters = listOf("all" to "Tutte 🌈") + EggRarity.values().map { it.id to "${it.emoji} ${it.displayName}" }
        filters.forEach { (id, name) ->
            val chip = TextView(this).apply {
                text = name; textSize = 10f; setTextColor(Color.WHITE)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(16).toFloat()
                    setColor(if (id == "all") Color.parseColor("#E0E0FF") else EggRarity.fromId(id).color)
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.marginEnd = dp(6) }
                setOnClickListener {
                    rarityFilter = id
                    refreshList()
                }
            }
            row.addView(chip)
        }
        return scroll
    }

    private fun refreshList() {
        listContainer.removeAllViews()

        var list = try {
            SurpriseManager.getAll(this)
        } catch (e: Exception) {
            android.util.Log.e("SurpriseInventory", "Errore caricamento borsa: ${e.message}", e)
            emptyList()
        }

        // Filter by rarity
        if (rarityFilter != "all") {
            list = list.filter { it.rarityId == rarityFilter }
        }

        // Sort (null-safe — protegge da dati corrotti Gson/R8)
        list = try {
            when (sortMode) {
                SORT_NAME -> list.sortedBy { try { it.displayName } catch (_: Exception) { "zzz" } }
                SORT_STRENGTH -> list.sortedByDescending { try { it.scaledAttack() + it.scaledDefense() + it.scaledHp() } catch (_: Exception) { 0 } }
                SORT_RARITY -> list.sortedWith(compareByDescending<OwnedSurprise> {
                    try {
                        when (it.rarityId) {
                            "legendary" -> 5; "epic" -> 4; "rare" -> 3; "uncommon" -> 2; else -> 1
                        }
                    } catch (_: Exception) { 0 }
                }.thenByDescending { it.caughtAt })
                else -> list
            }
        } catch (e: Exception) {
            android.util.Log.e("SurpriseInventory", "Errore sort: ${e.message}", e)
            list // ritorna lista non ordinata se il sort fallisce
        }

        countLabel?.text = "${list.size} creature nella borsa"

        if (list.isEmpty()) {
            listContainer.addView(buildEmptyState())
            return
        }

        val buddy = SurpriseManager.getAll(this).firstOrNull { it.isBuddy }

        list.forEach { owned ->
            try {
                listContainer.addView(buildCreatureCard(owned, isBuddy = owned.id == buddy?.id))
            } catch (e: Exception) {
                android.util.Log.e("SurpriseInventory", "Errore rendering creatura ${owned.creatureId}: ${e.message}", e)
            }
        }
    }

    private fun buildEmptyState(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(48), dp(24), dp(48))
            addView(TextView(this@SurpriseInventoryActivity).apply {
                text = "🥚"; textSize = 64f; gravity = Gravity.CENTER
            })
            addView(TextView(this@SurpriseInventoryActivity).apply {
                text = if (rarityFilter == "all")
                    "La tua borsa è vuota!\n\nCattura uova in outdoor,\nfalle schiudere e scopri le creature! 🐣"
                else
                    "Nessuna creatura ${EggRarity.fromId(rarityFilter).displayName} trovata."
                textSize = 14f; setTextColor(Color.parseColor("#9999CC")); gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, 0)
            })
            addView(TextView(this@SurpriseInventoryActivity).apply {
                text = "Vai a Schiusura & Mining →"
                textSize = 13f; setTextColor(Color.parseColor("#00E5FF")); gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setPadding(0, dp(16), 0, 0)
                setOnClickListener {
                    startActivity(Intent(this@SurpriseInventoryActivity,
                        com.intelligame.huntix.HatchingActivity::class.java))
                }
            })
        }
    }

    private fun buildCreatureCard(owned: OwnedSurprise, isBuddy: Boolean): androidx.cardview.widget.CardView {
        val rarity = EggRarity.fromId(owned.rarityId)
        val card = androidx.cardview.widget.CardView(this).apply {
            radius = dp(14).toFloat()
            cardElevation = dp(3).toFloat()
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(10) }
            isClickable = true; isFocusable = true
            setOnClickListener { showCreatureDialog(owned, isBuddy) }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        // Emoji + rarity glow border
        val emojiFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).also { it.marginEnd = dp(14) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F5F5F5"))
                setStroke(dp(2), rarity.color)
            }
        }
        emojiFrame.addView(TextView(this).apply {
            text = owned.displayEmoji; textSize = 26f; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        })
        row.addView(emojiFrame)

        // Info column
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        infoCol.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@SurpriseInventoryActivity).apply {
                text = owned.displayName; textSize = 15f; setTextColor(Color.parseColor("#E0E0FF"))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            if (isBuddy) {
                addView(TextView(this@SurpriseInventoryActivity).apply {
                    text = " ⭐ Amico Fidato"; textSize = 10f; setTextColor(Color.parseColor("#FFC107"))
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                })
            }
        })
        infoCol.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, 0)
            addView(TextView(this@SurpriseInventoryActivity).apply {
                text = rarity.displayName; textSize = 11f
                setTextColor(rarity.color)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.marginEnd = dp(8) }
            })
            addView(TextView(this@SurpriseInventoryActivity).apply {
                text = "Lv.${owned.level}"; textSize = 11f; setTextColor(Color.parseColor("#666699"))
            })
        })
        // Stats mini row
        infoCol.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
            listOf(
                "❤️ ${owned.scaledHp()}" to "#FF3366",
                "⚔️ ${owned.scaledAttack()}" to "#FF6F00",
                "🛡️ ${owned.scaledDefense()}" to "#00E5FF",
                "💨 ${owned.scaledSpeed()}" to "#00CC6A"
            ).forEach { (label, color) ->
                addView(TextView(this@SurpriseInventoryActivity).apply {
                    text = label; textSize = 10f; setTextColor(Color.parseColor(color))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .also { it.marginEnd = dp(8) }
                })
            }
        })
        row.addView(infoCol)

        // Arrow
        row.addView(TextView(this).apply {
            text = "›"; textSize = 22f; setTextColor(Color.parseColor("#B0BEC5"))
        })

        card.addView(row)
        return card
    }

    private fun showCreatureDialog(owned: OwnedSurprise, isBuddy: Boolean) {
        val creature = owned.creature
        val rarity = EggRarity.fromId(owned.rarityId)

        val items = if (isBuddy) {
            arrayOf(
                "📋 Dettagli creatura",
                "⭐ Già il tuo Amico Fidato!",
                "❌ Annulla"
            )
        } else {
            arrayOf(
                "📋 Dettagli creatura",
                "⭐ Imposta come Amico Fidato",
                "❌ Annulla"
            )
        }

        AlertDialog.Builder(this)
            .setTitle("${owned.displayEmoji}  ${owned.displayName}")
            .setMessage(
                "${rarity.displayName.uppercase()} · Lv.${owned.level}\n" +
                (creature?.description ?: "") +
                "\n\n❤️ HP: ${owned.scaledHp()}   ⚔️ ATK: ${owned.scaledAttack()}" +
                "\n🛡️ DEF: ${owned.scaledDefense()}   💨 SPD: ${owned.scaledSpeed()}" +
                "\n\n✨ Mossa Speciale: ${creature?.specialMoveEmoji ?: ""} ${creature?.specialMoveName ?: ""}" +
                "\n🍬 Caramelle: ${owned.candies}"
            )
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showDetailDialog(owned)
                    1 -> {
                        if (!isBuddy) {
                            SurpriseManager.setBuddy(this, owned.id)
                            Toast.makeText(this, "⭐ ${owned.displayName} è ora il tuo Amico Fidato!", Toast.LENGTH_LONG).show()
                            refreshList()
                        }
                    }
                    2 -> { /* annulla */ }
                }
            }
            .show()
    }

    private fun showDetailDialog(owned: OwnedSurprise) {
        val creature = owned.creature ?: return
        val rarity = EggRarity.fromId(owned.rarityId)

        AlertDialog.Builder(this)
            .setTitle("${owned.displayEmoji}  ${owned.displayName}  ${rarity.emoji}")
            .setMessage(
                "Rarità: ${rarity.displayName}\n" +
                "Livello: ${owned.level}\n" +
                "Catturato in: ${owned.catchZone}\n\n" +
                creature.description + "\n\n" +
                "━━━ STATISTICHE ━━━\n" +
                "❤️  HP:       ${owned.scaledHp()}\n" +
                "⚔️  Attacco:  ${owned.scaledAttack()}\n" +
                "🛡️  Difesa:   ${owned.scaledDefense()}\n" +
                "💨  Velocità: ${owned.scaledSpeed()}\n\n" +
                "✨ Mossa Speciale:\n" +
                "${creature.specialMoveEmoji} ${creature.specialMoveName} (${creature.specialMoveDamage} dmg)\n" +
                if (creature.specialMoveEffect.isNotBlank()) "Effetto: ${creature.specialMoveEffect}" else ""
            )
            .setPositiveButton("OK", null)
            .setNeutralButton("⭐ Imposta Amico Fidato") { _, _ ->
                SurpriseManager.setBuddy(this, owned.id)
                Toast.makeText(this, "⭐ ${owned.displayName} è ora il tuo Amico Fidato!", Toast.LENGTH_SHORT).show()
                refreshList()
            }
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
