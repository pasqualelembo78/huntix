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

class ProfileSetupActivity : BaseNavActivity() {
    private var editNickname: EditText? = null
    private var cbAdult: CheckBox? = null
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

        content.addView(mkLabel("Benvenuto in Huntix!", 22f, Color.WHITE, true))
        content.addView(mkLabel("Scegli il tuo nickname e conferma l'età per iniziare.", 12f, Color.parseColor("#AABBDD"), false).also { (it.layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(6); bottomMargin = dp(24) } })

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

        cbAdult = CheckBox(this).apply {
            text = "Confermo di aver compiuto 18 anni"
            setTextColor(Color.WHITE); textSize = 14f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(4) }
            setOnCheckedChangeListener { _, _ -> updateWarning() }
        }
        content.addView(cbAdult)

        tvWarning = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#FF8A65")); visibility = View.GONE; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(16) } }
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

    private fun updateWarning() {
        if (cbAdult?.isChecked == true) {
            tvWarning?.visibility = View.GONE
        } else {
            tvWarning?.text = "Non hai confermato la maggiore età. Chat, amici e scambi saranno limitati."
            tvWarning?.visibility = View.VISIBLE
        }
    }

    private fun onConfirm() {
        val nickname = editNickname?.text?.toString()?.trim() ?: ""
        if (nickname.isBlank() || nickname.length < 2) { Toast.makeText(this, "Inserisci un nickname valido!", Toast.LENGTH_SHORT).show(); return }
        val profile = PlayerProfileManager.myProfile
        if (profile == null) { Toast.makeText(this, "Errore: profilo non caricato. Riprova.", Toast.LENGTH_LONG).show(); return }
        profile.name = nickname.replaceFirstChar { it.uppercase() }
        profile.isMinor = !(cbAdult?.isChecked ?: false)
        profile.profileCompleted = true
        PlayerProfileManager.persistMyProfile()
        if (profile.isMinor) {
            Toast.makeText(this, "Profilo completato! Chat, amici e scambi limitati.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Profilo completato!", Toast.LENGTH_SHORT).show()
        }
        setResult(Activity.RESULT_OK)
        try {
            startActivity(Intent(this, HomeActivity::class.java))
        } catch (_: Exception) {}
        finish()
    }
    private fun mkLabel(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply { this.text = text; textSize = size; setTextColor(color); if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
