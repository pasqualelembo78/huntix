package com.intelligame.huntix.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.intelligame.huntix.PlayerProfileManager
import com.intelligame.huntix.gamification.CustomizationManager
import com.intelligame.huntix.gamification.CustomizationManager.Loadout
import com.intelligame.huntix.gamification.CustomizationManager.Category
import com.intelligame.huntix.gamification.CustomizationManager.CosmeticItem
import com.intelligame.huntix.gamification.CustomizationManager.UnlockCondition
import com.intelligame.huntix.BaseNavActivity

class CustomizationActivity : BaseNavActivity() {

    private var currentLoadout = Loadout()
    private var playerLevel = 1
    private var eggsFound = 0
    private var playerGems = 0
    private lateinit var equippedLabel: TextView
    private lateinit var contentContainer: LinearLayout
    private var currentCategory = Category.AVATAR_FRAME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0F0F2A")) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(56), dp(20), dp(40))
        }
        scroll.addView(root)

        root.addView(backBtn())
        root.addView(titleTv("🎨 Personalizzazione", "#FF9C27B0"))

        equippedLabel = TextView(this).apply {
            textSize = 13f; setTextColor(Color.parseColor("#9999CC")); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(equippedLabel)

        // Category tabs
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
                .also { it.bottomMargin = dp(16) }
        }
        Category.values().forEach { cat ->
            val tab = Button(this).apply {
                text = when (cat) {
                    Category.AVATAR_FRAME -> "Frame"
                    Category.TITLE        -> "Titolo"
                    Category.EGG_SKIN     -> "Skin"
                    Category.MAP_THEME    -> "Mappa"
                }
                textSize = 12f; setTextColor(Color.WHITE)
                setBackgroundColor(if (cat == currentCategory) Color.parseColor("#6A1B9A") else Color.parseColor("#1A1A3A"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    .also { it.marginEnd = dp(2) }
                setOnClickListener { currentCategory = cat; loadItems() }
            }
            tabRow.addView(tab)
        }
        root.addView(tabRow)

        contentContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(contentContainer)

        setContentView(scroll)

        val profile = PlayerProfileManager.myProfile
        playerLevel = profile?.level ?: 1
        eggsFound = profile?.eggsFound ?: 0

        CustomizationManager.loadLoadout(playerLevel, eggsFound) { loadout, _ ->
            currentLoadout = loadout
            runOnUiThread {
                updateEquippedLabel()
                // Check for new unlocks
                CustomizationManager.checkAndUnlockItems(playerLevel, eggsFound, loadout) { newItems ->
                    if (newItems.isNotEmpty()) {
                        runOnUiThread {
                            Toast.makeText(this, "🎉 Sbloccato: ${newItems.joinToString(", ") { it.name }}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                loadItems()
            }
        }
    }

    private fun loadItems() {
        val items = CustomizationManager.ALL_ITEMS.filter { it.category == currentCategory }
        contentContainer.removeAllViews()
        items.forEach { item ->
            val isUnlocked = item.id in currentLoadout.unlockedIds
            val isEquipped = when (item.category) {
                Category.AVATAR_FRAME -> currentLoadout.avatarFrameId == item.id
                Category.TITLE        -> currentLoadout.titleId == item.id
                Category.EGG_SKIN     -> currentLoadout.eggSkinId == item.id
                Category.MAP_THEME    -> currentLoadout.mapThemeId == item.id
            }
            contentContainer.addView(buildItemCard(item, isUnlocked, isEquipped))
        }
    }

    private fun buildItemCard(item: CosmeticItem, isUnlocked: Boolean, isEquipped: Boolean): CardView {
        val bgColor = when {
            isEquipped  -> "#1A3A1A"
            isUnlocked  -> "#FFFFFF"
            else        -> "#0A0A1A"
        }
        val card = CardView(this).apply {
            radius = dp(12).toFloat(); cardElevation = dp(4).toFloat()
            setCardBackgroundColor(Color.parseColor(bgColor))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(8) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        row.addView(TextView(this).apply {
            text = item.emoji; textSize = 28f; alpha = if (!isUnlocked) 0.3f else 1f
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(10) }
        }
        infoCol.addView(tv(
            if (isEquipped) "✅ ${item.name}" else item.name,
            15f, if (isUnlocked) item.rarityColorHex else "#444444", bold = true
        ))
        infoCol.addView(tv(item.description, 11f, if (isUnlocked) "#9999CC" else "#333333").also { it.setPadding(0, dp(2), 0, 0) })

        // Unlock condition label
        val condLabel = when (val c = item.unlockCondition) {
            is UnlockCondition.Default   -> if (isUnlocked) "✅ Sbloccato" else "Gratuito"
            is UnlockCondition.Level     -> if (isUnlocked) "✅ Lv.${c.level}" else "🔒 Lv.${c.level}"
            is UnlockCondition.EggsFound -> if (isUnlocked) "✅ ${c.count} uova" else "🔒 ${c.count} uova"
            is UnlockCondition.Gems      -> "${c.cost} 💎"
            is UnlockCondition.Quest     -> if (isUnlocked) "✅ Quest" else "🔒 Quest speciale"
        }
        infoCol.addView(tv(condLabel, 11f, if (isUnlocked) "#888888" else "#555555").also { it.setPadding(0, dp(2), 0, 0) })
        row.addView(infoCol)

        // Action button
        val btnText = when {
            isEquipped   -> "Equipaggiato"
            isUnlocked   -> "Equipaggia"
            item.unlockCondition is UnlockCondition.Gems -> "Acquista"
            else         -> "Bloccato"
        }
        val btnColor = when {
            isEquipped  -> "#00CC6A"
            isUnlocked  -> "#00E5FF"
            item.unlockCondition is UnlockCondition.Gems -> "#FF9800"
            else        -> "#333333"
        }
        val btn = Button(this).apply {
            text = btnText; setTextColor(Color.WHITE); textSize = 11f
            setBackgroundColor(Color.parseColor(btnColor))
            isEnabled = !isEquipped && (isUnlocked || item.unlockCondition is UnlockCondition.Gems)
            layoutParams = LinearLayout.LayoutParams(dp(80), dp(36))
            setOnClickListener {
                if (isUnlocked) {
                    CustomizationManager.equipItem(item.id, item.category, currentLoadout,
                        onSuccess = { newLoadout ->
                            currentLoadout = newLoadout; runOnUiThread { updateEquippedLabel(); loadItems() }
                        },
                        onError = { msg -> runOnUiThread { Toast.makeText(this@CustomizationActivity, msg, Toast.LENGTH_SHORT).show() } }
                    )
                } else if (item.unlockCondition is UnlockCondition.Gems) {
                    CustomizationManager.purchaseWithGems(item.id, playerGems, currentLoadout,
                        onSuccess = { newLoadout, cost ->
                            currentLoadout = newLoadout; playerGems -= cost
                            runOnUiThread { Toast.makeText(this@CustomizationActivity, "Acquistato!", Toast.LENGTH_SHORT).show(); loadItems() }
                        },
                        onError = { msg -> runOnUiThread { Toast.makeText(this@CustomizationActivity, msg, Toast.LENGTH_SHORT).show() } }
                    )
                }
            }
        }
        row.addView(btn)
        card.addView(row)
        return card
    }

    private fun updateEquippedLabel() {
        val frame = CustomizationManager.ALL_ITEMS.find { it.id == currentLoadout.avatarFrameId }
        val title = CustomizationManager.ALL_ITEMS.find { it.id == currentLoadout.titleId }
        equippedLabel.text = "Frame: ${frame?.emoji ?: "⬜"}  Titolo: ${title?.name ?: "Cacciatore"}"
    }

    private fun backBtn() = TextView(this).apply {
        text = "← Torna"; textSize = 14f; setTextColor(Color.parseColor("#666699"))
        setPadding(0, 0, 0, dp(8)); setOnClickListener { finish() }
    }

    private fun titleTv(text: String, colorHex: String) = TextView(this).apply {
        this.text = text; textSize = 24f; setTextColor(Color.parseColor(colorHex))
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun tv(text: String, size: Float, colorHex: String, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.parseColor(colorHex))
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
