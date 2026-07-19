package com.intelligame.huntix.avatar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.BaseNavActivity

/**
 * GenderSelectionActivity — Selezione genere avatar giocatore.
 *
 * Mostrata al primo avvio (se non ha mai scelto).
 * Il genere determina quale avatar RPM pre-scaricato usare:
 *   - assets/avatars/player_male.glb
 *   - assets/avatars/player_female.glb
 *
 * Il risultato viene salvato in SharedPreferences e usato per:
 *   1. Caricare il modello 3D corretto nella scena AR
 *   2. Mostrare il thumbnail corretto sulla mappa
 *   3. Filtrare gli accessori disponibili nel negozio
 */
class GenderSelectionActivity : BaseNavActivity() {

    companion object {
        private const val PREF_FILE = "avatar_gender_prefs"
        private const val KEY_GENDER = "player_gender"  // "male" | "female"
        private const val KEY_CHOSEN = "gender_chosen"

        fun needsSelection(ctx: Context): Boolean =
            !ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY_CHOSEN, false)

        fun getGender(ctx: Context): String =
            ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getString(KEY_GENDER, "male") ?: "male"

        fun getAvatarAssetPath(ctx: Context): String =
            "avatars/player_${getGender(ctx)}.glb"

        fun launch(activity: Activity) {
            activity.startActivity(Intent(activity, GenderSelectionActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setPadding(dp(32), dp(64), dp(32), dp(48))
        }

        // Titolo
        root.addView(TextView(this).apply {
            text = "🧑 Scegli il tuo Personaggio"
            textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "Il tuo avatar ti rappresenterà nel mondo di gioco.\nPotrai personalizzarlo con accessori dal negozio!"
            textSize = 13f; setTextColor(Color.parseColor("#666699")); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(32))
        })

        // Riga pulsanti maschio/femmina
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        row.addView(buildGenderCard("🧑‍♂️", "Maschio", "#00B4FF", "male"))
        row.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), 1)
        })
        row.addView(buildGenderCard("🧑‍♀️", "Femmina", "#E91E63", "female"))

        root.addView(row)

        // Nota
        root.addView(TextView(this).apply {
            text = "\nPuoi cambiare in qualsiasi momento dalle Impostazioni"
            textSize = 11f; setTextColor(Color.parseColor("#9999CC")); gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(root)
    }

    private fun buildGenderCard(emoji: String, label: String, colorHex: String, gender: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#16213E"))
                setStroke(dp(3), Color.parseColor(colorHex))
            }

            addView(TextView(this@GenderSelectionActivity).apply {
                text = emoji; textSize = 64f; gravity = Gravity.CENTER
            })
            addView(TextView(this@GenderSelectionActivity).apply {
                text = label; textSize = 18f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor(colorHex))
                typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
                setPadding(0, dp(12), 0, 0)
            })

            setOnClickListener {
                saveGender(gender)
                Toast.makeText(this@GenderSelectionActivity,
                    "$emoji $label selezionato!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun saveGender(gender: String) {
        getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_GENDER, gender)
            .putBoolean(KEY_CHOSEN, true)
            .apply()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
