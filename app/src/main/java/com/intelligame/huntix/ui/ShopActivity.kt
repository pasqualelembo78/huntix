package com.intelligame.huntix.ui

import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.LocalNeeds
import com.intelligame.huntix.reallife.MoneyManager
import com.intelligame.huntix.reallife.ShopCategory
import com.intelligame.huntix.reallife.ShopDefs

/**
 * ShopActivity — negozio dove spendere cash per cibo, vestiti, accessori.
 * Mostra categorie con tab, card items, pulsante acquista.
 */
class ShopActivity : AppCompatActivity() {

    private lateinit var moneyText: TextView
    private lateinit var itemsContainer: LinearLayout
    private var selectedCategory = ShopCategory.FOOD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
        }

        // Top bar
        val topBar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A0618"))
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 12))
        }
        topBar.addView(TextView(c).apply {
            text = "← "; textSize = 20f; setTextColor(Color.parseColor(UiKit.ACCENT))
            isClickable = true; setOnClickListener { finish() }
        })
        topBar.addView(TextView(c).apply {
            text = "\uD83D\uDED2  Negozio"; textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        moneyText = TextView(c).apply {
            text = "\uD83D\uDCB5 \$${MoneyManager.getCash(c)}"; textSize = 14f
            setTextColor(Color.parseColor("#FFD86B")); typeface = Typeface.DEFAULT_BOLD
        }
        topBar.addView(moneyText)
        root.addView(topBar)

        // Category tabs
        val tabs = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(c, 10), UiKit.dp(c, 8), UiKit.dp(c, 10), UiKit.dp(c, 6))
        }
        for (cat in ShopCategory.values()) {
            val tab = TextView(c).apply {
                text = "${cat.emoji} ${cat.label}"; textSize = 12f
                setTextColor(if (cat == selectedCategory) Color.WHITE else Color.parseColor(UiKit.TEXT_DIM))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(UiKit.dp(c, 12), UiKit.dp(c, 6), UiKit.dp(c, 12), UiKit.dp(c, 6))
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(c, 8).toFloat()
                    setColor(if (cat == selectedCategory) Color.parseColor(UiKit.ACCENT) else 0x22FFFFFF)
                }
                isClickable = true; isFocusable = true
                setOnClickListener {
                    selectedCategory = cat
                    updateTabs(tabs)
                    renderItems()
                }
            }
            tabs.addView(tab)
        }
        root.addView(tabs)

        // Items
        itemsContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(c, 14), 0, UiKit.dp(c, 14), UiKit.dp(c, 14))
        }
        root.addView(itemsContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val scroll = android.widget.ScrollView(c).apply { addView(root) }
        setContentView(scroll)
        renderItems()
    }

    private fun updateTabs(tabs: LinearLayout) {
        for (i in 0 until tabs.childCount) {
            val tab = tabs.getChildAt(i) as TextView
            val cat = ShopCategory.values()[i]
            tab.setTextColor(if (cat == selectedCategory) Color.WHITE else Color.parseColor(UiKit.TEXT_DIM))
            tab.background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@ShopActivity, 8).toFloat()
                setColor(if (cat == selectedCategory) Color.parseColor(UiKit.ACCENT) else 0x22FFFFFF)
            }
        }
    }

    private fun renderItems() {
        itemsContainer.removeAllViews()
        val cash = MoneyManager.getCash(this)
        val items = ShopDefs.ITEMS.filter { it.category == selectedCategory }

        for (item in items) {
            val canAfford = cash >= item.price
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@ShopActivity, 12).toFloat()
                    setColor(Color.parseColor(UiKit.BG_CARD))
                    setStroke(1, if (canAfford) Color.parseColor("#2A2240") else Color.parseColor("#3A2220"))
                }
                setPadding(UiKit.dp(this@ShopActivity, 12), UiKit.dp(this@ShopActivity, 10),
                    UiKit.dp(this@ShopActivity, 12), UiKit.dp(this@ShopActivity, 10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = UiKit.dp(this@ShopActivity, 8) }
            }

            card.addView(TextView(this).apply {
                text = item.emoji; textSize = 28f
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@ShopActivity, 40), LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(UiKit.dp(this@ShopActivity, 8), 0, 0, 0)
            }
            info.addView(TextView(this).apply {
                text = item.name; textSize = 14f; setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            info.addView(TextView(this).apply {
                text = item.description; textSize = 11f
                setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            })
            if (item.needKey != null) {
                info.addView(TextView(this).apply {
                    text = "+${item.needGain.toInt()} ${item.needKey}"; textSize = 10f
                    setTextColor(Color.parseColor(UiKit.GREEN))
                })
            }
            card.addView(info)

            val buyBtn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@ShopActivity, 8).toFloat()
                    setColor(if (canAfford) Color.parseColor(UiKit.ACCENT) else 0x33333333)
                }
                setPadding(UiKit.dp(this@ShopActivity, 12), UiKit.dp(this@ShopActivity, 8),
                    UiKit.dp(this@ShopActivity, 12), UiKit.dp(this@ShopActivity, 8))
                isClickable = canAfford; isFocusable = canAfford
                if (canAfford) {
                    setOnClickListener { buyItem(item) }
                }
            }
            buyBtn.addView(TextView(this).apply {
                text = "\$${item.price}"; textSize = 12f
                setTextColor(if (canAfford) Color.WHITE else Color.parseColor("#666666"))
                typeface = Typeface.DEFAULT_BOLD
            })
            card.addView(buyBtn)

            itemsContainer.addView(card)
        }
    }

    private fun buyItem(item: com.intelligame.huntix.reallife.ShopItem) {
        if (MoneyManager.spendCash(this, item.price)) {
            // Apply need effect
            if (item.needKey != null) {
                LocalNeeds.applyAction(this, item.needKey, item.needGain)
            }
            moneyText.text = "\uD83D\uDCB5 \$${MoneyManager.getCash(this)}"
            renderItems()
            Toast.makeText(this, "${item.emoji} ${item.name} comprato!", Toast.LENGTH_SHORT).show()
        }
    }
}
