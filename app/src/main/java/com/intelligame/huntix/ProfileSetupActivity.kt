package com.intelligame.huntix

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import io.sentry.Sentry

class ProfileSetupActivity : BaseNavActivity() {
    private var editNickname: EditText? = null
    private var tvWarning: TextView? = null

    // ── Scelte obbligatorie: sesso + fascia d'età ──────────────────
    private var selectedGender: String = ""            // "male" / "female"
    private var selectedFascia: Int = -1               // 0 = 6-12, 1 = 13-17, 2 = 18+
    private var btnMaschio: Button? = null
    private var btnFemmina: Button? = null
    private var btnF1: Button? = null
    private var btnF2: Button? = null
    private var btnF3: Button? = null

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
        val FASCIA_AGES = listOf(9, 15, 25)             // età rappresentativa per fascia
        val FASCIA_LABELS = listOf("Fascia 1 · 6-12 anni", "Fascia 2 · 13-17 anni", "18 anni o più")
        fun fasciaBirthYear(fascia: Int): Int {
            if (fascia < 0 || fascia >= FASCIA_AGES.size) return 0
            return java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - FASCIA_AGES[fascia]
        }
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

        content.addView(mkLabel("Benvenuto in Huntix!", 22f, Color.WHITE, true))
        content.addView(mkLabel("Scegli nickname, sesso ed età per iniziare. Sesso ed età non saranno più modificabili.", 12f, Color.parseColor("#AABBDD"), false).also { (it.layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(6); bottomMargin = dp(24) } })

        val currentName = PlayerProfileManager.myProfile?.name ?: "Cacciatore"
        content.addView(mkLabel("Nickname", 14f, Color.parseColor("#88AADD"), true))
        editNickname = EditText(this).apply {
            setText(currentName)
            setHintTextColor(Color.parseColor("#555577")); setTextColor(Color.WHITE); textSize = 15f; maxLines = 1
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#1A1A3E")); setStroke(1, Color.parseColor("#334466")) }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4); it.bottomMargin = dp(16) }
        }
        content.addView(editNickname)

        content.addView(mkLabel("Sesso", 14f, Color.parseColor("#88AADD"), true))
        val genderRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL }
        btnMaschio = mkChip("MASCHIO") { selectGender("male", btnMaschio!!, btnFemmina!!) }
        btnFemmina = mkChip("FEMMINA") { selectGender("female", btnMaschio!!, btnFemmina!!) }
        genderRow.addView(btnMaschio, LinearLayout.LayoutParams(0, dp(46), 1f).also { it.rightMargin = dp(8) })
        genderRow.addView(btnFemmina, LinearLayout.LayoutParams(0, dp(46), 1f))
        content.addView(genderRow)

        content.addView(mkLabel("Fascia d'età", 14f, Color.parseColor("#88AADD"), true).also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10) })
        btnF1 = mkChip(FASCIA_LABELS[0]) { selectFascia(0, btnF1!!, btnF2!!, btnF3!!) }
        btnF2 = mkChip(FASCIA_LABELS[1]) { selectFascia(1, btnF1!!, btnF2!!, btnF3!!) }
        btnF3 = mkChip(FASCIA_LABELS[2]) { selectFascia(2, btnF1!!, btnF2!!, btnF3!!) }
        content.addView(btnF1)
        content.addView(btnF2)
        content.addView(btnF3)

        tvWarning = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#FF8A65")); visibility = View.GONE; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8); it.bottomMargin = dp(8) } }
        content.addView(tvWarning)

        content.addView(Button(this).apply {
            text = "CONFERMA"; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#00E5FF")) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).also { it.topMargin = dp(8) }
            setOnClickListener { onConfirm() }
        })
        content.addView(mkLabel("I tuoi dati sono protetti e non vengono condivisi.", 10f, Color.parseColor("#667788"), false).also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(16) })
        setContentView(root)
    }

    private fun mkChip(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 14f; setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#1A1A3E")); setStroke(1, Color.parseColor("#334466")) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).also { it.topMargin = dp(6) }
        setOnClickListener { onClick() }
    }

    private fun setChipSelected(btn: Button, selected: Boolean) {
        val bg = btn.background as GradientDrawable
        if (selected) {
            bg.setColor(Color.parseColor("#00E5FF")); bg.setStroke(1, Color.parseColor("#00E5FF"))
            btn.setTextColor(Color.BLACK)
        } else {
            bg.setColor(Color.parseColor("#1A1A3E")); bg.setStroke(1, Color.parseColor("#334466"))
            btn.setTextColor(Color.WHITE)
        }
        updateWarning()
    }

    private fun selectGender(gender: String, maschio: Button, femmina: Button) {
        selectedGender = gender
        setChipSelected(maschio, gender == "male")
        setChipSelected(femmina, gender == "female")
    }

    private fun selectFascia(fascia: Int, f1: Button, f2: Button, f3: Button) {
        selectedFascia = fascia
        setChipSelected(f1, fascia == 0)
        setChipSelected(f2, fascia == 1)
        setChipSelected(f3, fascia == 2)
    }

    private fun updateWarning() {
        if (selectedFascia == -1) {
            tvWarning?.text = "Scegli la tua fascia d'età."
            tvWarning?.visibility = View.VISIBLE
        } else if (selectedFascia != 2) {
            tvWarning?.text = "Minorenne: chat, amici e scambi saranno limitati."
            tvWarning?.visibility = View.VISIBLE
        } else {
            tvWarning?.visibility = View.GONE
        }
    }

    private fun onConfirm() {
        val nickname = editNickname?.text?.toString()?.trim() ?: ""
        if (nickname.isBlank() || nickname.length < 2) { Toast.makeText(this, "Inserisci un nickname valido!", Toast.LENGTH_SHORT).show(); return }
        if (selectedGender.isEmpty()) { Toast.makeText(this, "Scegli il sesso (Maschio o Femmina)!", Toast.LENGTH_SHORT).show(); return }
        if (selectedFascia == -1) { Toast.makeText(this, "Scegli la fascia d'età!", Toast.LENGTH_SHORT).show(); return }
        val profile = PlayerProfileManager.myProfile
        if (profile == null) { Toast.makeText(this, "Errore: profilo non caricato. Riprova.", Toast.LENGTH_LONG).show(); return }
        profile.name = nickname.replaceFirstChar { it.uppercase() }
        // Sesso ed età scelti qui in registrazione: diventano immutabili.
        profile.playerGender = selectedGender
        profile.genderChosenAt = if (profile.genderChosenAt == 0L) System.currentTimeMillis() else profile.genderChosenAt
        profile.birthYear = fasciaBirthYear(selectedFascia)
        profile.isMinor = selectedFascia != 2
        profile.profileCompleted = true
        PlayerProfileManager.persistMyProfile()
        if (profile.isMinor) {
            Toast.makeText(this, "Profilo completato! Chat, amici e scambi limitati.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Profilo completato!", Toast.LENGTH_SHORT).show()
        }
        setResult(Activity.RESULT_OK)
        try {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } catch (e: Exception) {
            Sentry.captureException(e)
            android.util.Log.e("ProfileSetup", "Failed to start HomeActivity: ${e.message}")
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "Errore di avvio. Riavvia l'app.", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun mkLabel(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply { this.text = text; textSize = size; setTextColor(color); if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
