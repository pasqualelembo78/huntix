package com.intelligame.huntix

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * InfoLegalActivity — informazioni sull'app, licenze e crediti.
 */
class InfoLegalActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val pm = android.content.pm.PackageManager.GET_ACTIVITIES
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.1"
        } catch (_: Exception) { "1.1" }

        val content = UiKit.scroll(c,
            UiKit.title(c, "Info & Legale", "ℹ️"),
            UiKit.card(c,
                UiKit.row(c, "App", "Huntix"),
                UiKit.row(c, "Versione", version),
                UiKit.row(c, "Modalità", "Indoor AR · Outdoor GPS"),
                UiKit.row(c, "Motorio 3D", "ARCore + Filament"),
                UiKit.row(c, "Backend", "Firebase")
            ),
            UiKit.section(c, "Tecnologie utilizzate"),
            UiKit.card(c,
                paragraph("• Google ARCore — Realtà Aumentata e rilevamento piani"),
                paragraph("• Mapbox — mappe e navigazione GPS"),
                paragraph("• Firebase — database, auth, push e moderazione"),
                paragraph("• Ready Player Me — avatar personalizzabili"),
                paragraph("• Sentry — crash reporting e monitoring")
            ),
            UiKit.section(c, "Riconoscimenti"),
            UiKit.card(c, paragraph("Grazie a tutti i beta tester e alla community di cacciatori di uova!")),
            UiKit.button(c, "🔒  Privacy & Termini", UiKit.PURPLE) {
                startActivity(android.content.Intent(c, LegalActivity::class.java))
            }
        )
        setContentView(content)
    }

    private fun paragraph(text: String) = android.widget.TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(android.graphics.Color.parseColor(UiKit.TEXT_DIM))
        setPadding(0, 0, 0, UiKit.dp(this@InfoLegalActivity, 6))
    }
}
