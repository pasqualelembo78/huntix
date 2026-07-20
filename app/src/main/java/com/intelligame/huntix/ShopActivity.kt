package com.intelligame.huntix

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.CatchToolManager
import com.intelligame.huntix.billing.BillingManager
import com.intelligame.huntix.managers.SavedManager

/**
 * ShopActivity — negozio MVC e VIP Pass (Google Play Billing).
 */
class ShopActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        try { BillingManager.init(c) } catch (_: Exception) { }

        val children = mutableListOf<android.view.View>(
            UiKit.title(c, "Negozio", "🛒"),
            UiKit.subtitle(c, "Ricarica MVC (Moneta Virtuale di Caccia) o diventa VIP."),
            UiKit.section(c, "💎 VIP Pass")
        )
        children.add(UiKit.button(c, "⭐ Diventa VIP — vantaggi x2", "#FFD700") {
            try {
                BillingManager.purchaseVip(c) { ok, msg ->
                    Toast.makeText(c, if (ok) "Grazie per il VIP!" else msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { Toast.makeText(c, "Billing non disponibile", Toast.LENGTH_SHORT).show() }
        })
        children.add(UiKit.section(c, "🎟️ Pass & Pro"))
        children.add(UiKit.button(c, "🗓️ Season Pass — 90 giorni x2 ricompense", UiKit.ACCENT) {
            try {
                BillingManager.purchaseOneTime(c, BillingManager.PRODUCT_SEASON_PASS) { ok, msg ->
                    Toast.makeText(c, if (ok) "Season Pass attivato!" else msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { Toast.makeText(c, "Billing non disponibile", Toast.LENGTH_SHORT).show() }
        })
        children.add(UiKit.button(c, "🎮 Multiplayer Pro — lobby 8 giocatori", UiKit.PURPLE) {
            try {
                BillingManager.purchaseOneTime(c, BillingManager.PRODUCT_MULTIPLAYER) { ok, msg ->
                    Toast.makeText(c, if (ok) "Multiplayer Pro attivato!" else msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { Toast.makeText(c, "Billing non disponibile", Toast.LENGTH_SHORT).show() }
        })
        children.add(UiKit.section(c, "🪣 Secchielli (con MVC)"))
        children.add(UiKit.subtitle(c,
            "MVC attuali: ${SavedManager.getMvcBalance(c).toInt()}  🪙"))
        CatchToolManager.CatchTool.values().filter { !it.isUnlimited }.forEach { tool ->
            children.add(bucketRow(tool))
        }

        children.add(UiKit.section(c, "⚡ Pacchetti MVC"))

        BillingManager.MVC_PACKAGES.forEach { pkg ->
            children.add(shopRow(pkg.emoji, "${pkg.displayName} — ${pkg.mvcAmount} MVC ${pkg.bonus}",
                pkg.productId))
        }
        setContentView(UiKit.scroll(c, *children.toTypedArray()))
    }

    private fun shopRow(emoji: String, label: String, productId: String): LinearLayout {
        val c = this
        return UiKit.button(c, "$emoji  $label", UiKit.PURPLE) {
            try {
                BillingManager.purchaseMvcPackage(c, productId) { ok, msg ->
                    Toast.makeText(c, if (ok) "Acquisto completato!" else msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(c, "Billing non disponibile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bucketRow(tool: CatchToolManager.CatchTool): LinearLayout {
        val c = this
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, UiKit.dp(c, 6), 0, UiKit.dp(c, 10))
        }
        val owned = CatchToolManager.getQuantity(c, tool)
        val selected = CatchToolManager.getSelectedTool(c) == tool
        box.addView(UiKit.row(c,
            "${tool.emoji} ${tool.displayName}  ·  cap. ${tool.capacity}  ·  posseduti: $owned${if (selected) " ✓" else ""}"))
        if (owned > 0) {
            box.addView(UiKit.button(c, if (selected) "✓ Equipaggiato" else "Equipaggia", "#00C853") {
                CatchToolManager.setSelectedTool(c, tool)
                Toast.makeText(c, "${tool.displayName} equipaggiato!", Toast.LENGTH_SHORT).show()
                recreate()
            })
        }
        box.addView(UiKit.button(c, "🪙 Compra — ${tool.shopPrice} MVC", UiKit.PURPLE) {
            val bal = SavedManager.getMvcBalance(c)
            if (bal < tool.shopPrice) {
                Toast.makeText(c, "MVC insufficienti! Hai ${bal.toInt()} MVC", Toast.LENGTH_LONG).show()
                return@button
            }
            SavedManager.spendMvc(c, tool.shopPrice.toDouble())
            CatchToolManager.addQuantity(c, tool, 1)
            CatchToolManager.setSelectedTool(c, tool)
            Toast.makeText(c, "Acquistato ${tool.displayName}!", Toast.LENGTH_LONG).show()
            recreate()
        })
        return box
    }
}
