package com.intelligame.huntix

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.billing.BillingManager

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
}
