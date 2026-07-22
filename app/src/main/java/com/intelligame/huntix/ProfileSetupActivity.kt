package com.intelligame.huntix

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class ProfileSetupActivity : BaseNavActivity() {
    private var spinnerCountry: Spinner? = null
    private var editCity: EditText? = null
    private var spinnerYear: Spinner? = null
    private var tvWarning: TextView? = null

    companion object {
        val COUNTRIES = listOf(
            "IT" to "Italia", "DE" to "Germania", "FR" to "Francia",
            "ES" to "Spagna", "GB" to "Regno Unito", "US" to "USA",
            "PT" to "Portogallo", "NL" to "Olanda", "BE" to "Belgio",
            "AT" to "Austria", "CH" to "Svizzera", "PL" to "Polonia",
            "RO" to "Romania", "GR" to "Grecia", "SE" to "Svezia",
            "NO" to "Norvegia", "DK" to "Danimarca", "FI" to "Finlandia",
            "IE" to "Irlanda", "HR" to "Croazia", "CZ" to "Rep. Ceca",
            "BR" to "Brasile", "AR" to "Argentina", "MX" to "Messico",
            "JP" to "Giappone", "KR" to "Corea del Sud", "AU" to "Australia",
            "CA" to "Canada", "IN" to "India", "TR" to "Turchia", "ZZ" to "Altro"
        )
        fun launch(activity: Activity) { activity.startActivity(Intent(activity, ProfileSetupActivity::class.java)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        buildUI()
    }
    override fun onBackPressed() { Toast.makeText(this, "Completa il profilo per continuare!", Toast.LENGTH_SHORT).show() }

    private fun buildUI() {
        val root = FrameLayout(this)
        root.addView(View(this).apply { layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT); background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#0A0022"), Color.parseColor("#1A1A3E"), Color.parseColor("#0A0022"))) })
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(32), dp(80), dp(32), dp(48)) }
        scroll.addView(content); root.addView(scroll)
        content.addView(mkLabel("Completa il Profilo", 22f, Color.WHITE, true))
        content.addView(mkLabel("Queste info servono per trovare altri giocatori.\nNon condivideremo mai i tuoi dati.", 12f, Color.parseColor("#AABBDD"), false).also { (it.layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(6); bottomMargin = dp(24) } })

        content.addView(mkLabel("Paese", 14f, Color.parseColor("#88AADD"), true))
        spinnerCountry = Spinner(this).apply { adapter = ArrayAdapter(this@ProfileSetupActivity, android.R.layout.simple_spinner_dropdown_item, COUNTRIES.map { it.second }); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).also { it.topMargin = dp(4); it.bottomMargin = dp(16) } }
        content.addView(spinnerCountry)

        content.addView(mkLabel("Citta", 14f, Color.parseColor("#88AADD"), true))
        editCity = EditText(this).apply { hint = "Es: Roma, Milano..."; setHintTextColor(Color.parseColor("#555577")); setTextColor(Color.WHITE); textSize = 15f; maxLines = 1; background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#1A1A3E")); setStroke(1, Color.parseColor("#334466")) }; setPadding(dp(14), dp(12), dp(14), dp(12)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4); it.bottomMargin = dp(16) } }
        content.addView(editCity)

        // ✅ FIX v7.2.1: Neutral Age Gate — non suggerire età minima
        content.addView(mkLabel("Anno di nascita", 14f, Color.parseColor("#88AADD"), true))
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear - 80..currentYear - 5).toList().reversed().map { it.toString() }
        // ✅ FIX v7.2.1: Se l'età è già bloccata, non permettere modifica
        val ageAlreadyLocked = AgeGateManager.isAgeLocked(this)
        spinnerYear = Spinner(this).apply {
            adapter = ArrayAdapter(this@ProfileSetupActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Seleziona...") + years)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).also { it.topMargin = dp(4); it.bottomMargin = dp(8) }
            if (ageAlreadyLocked) {
                val lockedYear = AgeGateManager.getLockedBirthYear(this@ProfileSetupActivity)
                val idx = years.indexOf(lockedYear.toString())
                if (idx >= 0) setSelection(idx + 1) // +1 per "Seleziona..."
                isEnabled = false; alpha = 0.6f
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { updateWarning() }
                override fun onNothingSelected(p: AdapterView<*>?) {} }
        }
        content.addView(spinnerYear)

        tvWarning = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#FF8A65")); visibility = View.GONE; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(16) } }
        content.addView(tvWarning)

        content.addView(Button(this).apply { text = "CONFERMA"; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-black", Typeface.BOLD); background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#00E5FF")) }; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).also { it.topMargin = dp(8) }; setOnClickListener { onConfirm() } })
        content.addView(mkLabel("I tuoi dati sono protetti e non vengono condivisi.", 10f, Color.parseColor("#667788"), false).also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(16) })
        setContentView(root)
    }

    private fun updateWarning() {
        val year = spinnerYear?.selectedItem?.toString()?.toIntOrNull() ?: return
        if (AgeGateManager.isMinor(year)) { tvWarning?.text = "Hai meno di 18 anni. Chat, amici e scambi saranno limitati."; tvWarning?.visibility = View.VISIBLE } else tvWarning?.visibility = View.GONE
    }

    private fun onConfirm() {
        val countryIdx = spinnerCountry?.selectedItemPosition ?: 0
        val country = COUNTRIES[countryIdx].first
        val city = editCity?.text?.toString()?.trim() ?: ""
        val year = spinnerYear?.selectedItem?.toString()?.toIntOrNull()
        if (city.isBlank() || city.length < 2) { Toast.makeText(this, "Inserisci la tua citta!", Toast.LENGTH_SHORT).show(); return }
        if (year == null) { Toast.makeText(this, "Seleziona anno di nascita!", Toast.LENGTH_SHORT).show(); return }
        val profile = PlayerProfileManager.myProfile
        if (profile == null) { Toast.makeText(this, "Errore: profilo non caricato. Riprova.", Toast.LENGTH_LONG).show(); return }
        profile.country = country; profile.city = city.replaceFirstChar { it.uppercase() }; profile.birthYear = year
        profile.isMinor = AgeGateManager.isMinor(year); profile.profileCompleted = true
        PlayerProfileManager.persistMyProfile()
        Toast.makeText(this, if (profile.isMinor) "Profilo completato! Alcune funzionalita limitate." else "Profilo completato!", Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        try {
            startActivity(Intent(this, HomeActivity::class.java))
        } catch (_: Exception) {}
        finish()
    }
    private fun mkLabel(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply { this.text = text; textSize = size; setTextColor(color); if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
