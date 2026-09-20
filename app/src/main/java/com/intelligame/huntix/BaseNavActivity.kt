// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.intelligame.huntix.social.ChatListActivity
import com.intelligame.huntix.social.FriendsActivity
import com.intelligame.huntix.ui.*

/**
 * BaseNavActivity — Bottom navigation fisso stile "iframe" su TUTTE le Activity.
 * v2: icone emoji al posto di lettere.
 */
open class BaseNavActivity : AppCompatActivity() {

    private var bottomNavView: LinearLayout? = null

    open fun activeTab(): String = ""  // Default: nessun tab selezionato. Solo i 5 tab principali sovrascrivono.

    override fun setContentView(view: View?) {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val contentWrapper = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        view?.let { contentWrapper.addView(it) }
        shell.addView(contentWrapper)

        bottomNavView = buildBottomNav()
        shell.addView(bottomNavView)

        super.setContentView(shell)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(shell) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            contentWrapper.setPadding(0, sys.top, 0, 0)
            bottomNavView?.setPadding(0, dpPx(4), 0, dpPx(6) + sys.bottom)
            insets
        }
    }

    override fun setContentView(layoutResID: Int) {
        setContentView(layoutInflater.inflate(layoutResID, null))
    }

    private fun buildBottomNav(): LinearLayout {
        val active = activeTab()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0A0618"))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dpPx(4), 0, dpPx(6))

            addView(navItem("Home",   "\uD83C\uDFE0", active == "Home")   { navigateTo("Home") })
            addView(navItem("Uova",   "\uD83E\uDD5A", active == "Uova")   { navigateTo("Uova") })
            addView(navItem("Chat",   "\uD83D\uDCAC", active == "Chat")   { navigateTo("Chat") })
            addView(navItem("Social", "\uD83D\uDC65", active == "Social") { navigateTo("Social") })
            addView(navItem("Esplora", "\uD83C\uDF0D", active == "Esplora") { navigateTo("Esplora") })
            addView(navItem("Altro",  "\u2699\uFE0F", active == "Altro")  { showAltroMenu() })
        }
    }

    private fun navItem(label: String, icon: String, isActive: Boolean, onClick: () -> Unit): LinearLayout {
        val color = if (isActive) "#A78BFA" else "#4A3870"
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }

            addView(TextView(this@BaseNavActivity).apply {
                text = icon; textSize = 20f; gravity = Gravity.CENTER
            })
            addView(TextView(this@BaseNavActivity).apply {
                text = label; textSize = 9f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor(color))
                typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            })
            if (isActive) {
                addView(android.view.View(this@BaseNavActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dpPx(20), dpPx(3)).apply {
                        gravity = Gravity.CENTER_HORIZONTAL; topMargin = dpPx(2)
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dpPx(2).toFloat()
                        setColor(Color.parseColor("#A78BFA"))
                    }
                })
            }
        }
    }

    private fun navigateTo(tab: String) {
        if (tab == activeTab()) return
        if (tab == "Esplora") {
            // Alimenta la mappa con i POI reali Huntix (negozi OSM) prima di aprirla
            com.intelligame.huntix.manager.PoiMapBridge.feed(this)
            startActivity(Intent(this, com.intelligame.huntix.legacy.Controller.SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            return
        }
        val targetClass = when (tab) {
            "Home"   -> HomeActivity::class.java
            "Uova"   -> HatchingActivity::class.java
            "Chat"   -> ChatListActivity::class.java
            "Social" -> FriendsActivity::class.java
            else     -> return
        }
        startActivity(Intent(this, targetClass).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun showAltroMenu() {
        val items = arrayOf("Profilo", "Locali & POI", "Impostazioni", "Abilità", "Eventi", "Le Tue Creature", "Invita Amico", "Info e Legale")
        AlertDialog.Builder(this).setTitle("Altro").setItems(items) { _, i -> when (i) {
            0 -> startActivity(Intent(this, PlayerProfileActivity::class.java))
            1 -> startActivity(Intent(this, PoiListActivity::class.java))
            2 -> startActivity(Intent(this, SettingsActivity::class.java))
            3 -> startActivity(Intent(this, AbilityActivity::class.java))
            4 -> startActivity(Intent(this, EventsActivity::class.java))
            5 -> startActivity(Intent(this, SurpriseInventoryActivity::class.java))
            6 -> showInviteDialog()
            7 -> startActivity(Intent(this, InfoLegalActivity::class.java))
        }}.show()
    }

    /** Invita amico completo: condividi il codice oppure inserisci quello di un amico. */
    private fun showInviteDialog() {
        val items = arrayOf("📤 Condividi il mio codice", "📲 Inserisci codice amico")
        AlertDialog.Builder(this).setTitle("Invita un Amico").setItems(items) { _, i ->
            when (i) {
                0 -> com.intelligame.huntix.social.ReferralManager.getMyCode(this) { code ->
                    runOnUiThread { if (code.isNotBlank()) com.intelligame.huntix.social.ReferralManager.shareCode(this, code) }
                }
                1 -> showInsertCodeDialog()
            }
        }.show()
    }

    private fun showInsertCodeDialog() {
        val input = EditText(this).apply {
            hint = "Es. ABC123"
        }
        AlertDialog.Builder(this)
            .setTitle("📲 Inserisci codice amico")
            .setMessage("Inserisci il codice di un amico per ricevere +500 MVC gratis!")
            .setView(input)
            .setPositiveButton("Riscatta") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isEmpty()) {
                    Toast.makeText(this, "Inserisci un codice valido", Toast.LENGTH_SHORT).show()
                } else {
                    com.intelligame.huntix.social.ReferralManager.applyCode(this, code) { _, msg ->
                        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
                    }
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (activeTab() != "Home") navigateTo("Home") else super.onBackPressed()  // "" (non-tab) -> Home, "Home" -> exit
    }

    private fun dpPx(v: Int) = (v * resources.displayMetrics.density).toInt()
}
