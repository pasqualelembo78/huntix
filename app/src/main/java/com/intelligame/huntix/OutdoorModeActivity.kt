package com.intelligame.huntix

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.intelligame.huntix.ui.ArNavigationActivity
import com.intelligame.huntix.ui.OutdoorGuestActivity
import com.intelligame.huntix.ui.OutdoorHuntActivity
import com.intelligame.huntix.ui.OutdoorWorldActivity

/**
 * OutdoorModeActivity — hub della modalità Outdoor (mappa GPS).
 */
class OutdoorModeActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val hasGps = ActivityCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

        val content = UiKit.scroll(c,
            UiKit.title(c, "Outdoor", "🌍"),
            UiKit.subtitle(c, "Esplora la mappa, cattura uova GPS e completa le palestre."),
            UiKit.card(c, UiKit.row(c, "Permesso GPS", if (hasGps) "✅ Attivo" else "⚠️ Negato")),
            UiKit.button(c, "🗺️  Mappa del Mondo", UiKit.ACCENT) {
                startActivity(Intent(c, OutdoorWorldActivity::class.java))
            },
            UiKit.button(c, "🥚  Caccia (Hunt)", UiKit.PURPLE) {
                startActivity(Intent(c, OutdoorHuntActivity::class.java))
            },
            UiKit.button(c, "⚙️  Imposta caccia", UiKit.PURPLE) {
                startActivity(Intent(c, OutdoorSetupActivity::class.java))
            },
            UiKit.button(c, "🚶  Ospite (senza account)", UiKit.PURPLE) {
                startActivity(Intent(c, OutdoorGuestActivity::class.java))
            },
            UiKit.button(c, "📍  Navigazione AR", UiKit.PURPLE) {
                startActivity(Intent(c, ArNavigationActivity::class.java))
            }
        )
        setContentView(content)

        if (!hasGps) {
            ActivityCompat.requestPermissions(c,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
        }
    }
}
