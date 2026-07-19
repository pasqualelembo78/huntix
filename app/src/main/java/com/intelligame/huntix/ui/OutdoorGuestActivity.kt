package com.intelligame.huntix.ui

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.LoginActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.manager.OutdoorManager

class OutdoorGuestActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private val mgr = OutdoorManager.get()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mgr.start(this)

        val content = UiKit.scroll(
            this,
            UiKit.title(this, "Modalita' Ospite", "👤"),
            UiKit.card(
                this,
                UiKit.subtitle(this, "Giochi senza account. Le uova catturate finiscono nel tuo inventario locale su questo dispositivo."),
                UiKit.button(this, "🗺️ Apri la mappa del mondo", UiKit.ACCENT) {
                    startActivity(Intent(this, OutdoorWorldActivity::class.java))
                },
                UiKit.button(this, "🔑 Accedi / Registrati", UiKit.PURPLE) {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
            )
        )
        setContentView(content)
    }

    override fun onDestroy() {
        mgr.stop()
        super.onDestroy()
    }
}
