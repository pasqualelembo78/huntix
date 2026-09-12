package com.intelligame.huntix

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.sentry.Sentry

class ProfileSetupActivity : BaseNavActivity() {
    private var editNickname: EditText? = null
    private var tvWarning: TextView? = null

    // ── Scelte obbligatorie: sesso + fascia d'età ──────────────────
    private var selectedGender: String = ""            // "male" / "female"
    private var editRealAge: EditText? = null
    private var editGameAge: EditText? = null
    private var btnMaschio: Button? = null
    private var btnFemmina: Button? = null

    // ── Posizione iniziale (GPS: scelta singola, usata SOLO al primo spawn) ──
    private var chosenGpsLat: Double = 41.9028          // default: Roma
    private var chosenGpsLng: Double = 12.4964
    private var tvGpsStatus: TextView? = null
    private var btnUseGps: Button? = null
    private var RC_LOCATION = 102


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
        fun ageToBirthYear(age: Int): Int {
            return java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - age
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

        content.addView(mkLabel("Età reale del giocatore", 14f, Color.parseColor("#88AADD"), true).also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10) })
        editRealAge = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setHint("Es. 25 (devi avere almeno 18 anni)"); setHintTextColor(Color.parseColor("#555577")); setTextColor(Color.WHITE); textSize = 15f; maxLines = 1
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#1A1A3E")); setStroke(1, Color.parseColor("#334466")) }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4); it.bottomMargin = dp(4) }
        }
        content.addView(editRealAge)
        content.addView(mkLabel("⚠️ Devi essere maggiorenne (18+) per giocare.", 11f, Color.parseColor("#FF8A65"), false).also { (it.layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(2); bottomMargin = dp(12) } })

        content.addView(mkLabel("Età del personaggio in gioco", 14f, Color.parseColor("#88AADD"), true).also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(4) })
        editGameAge = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setHint("Es. 18 (età del tuo personaggio)"); setHintTextColor(Color.parseColor("#555577")); setTextColor(Color.WHITE); textSize = 15f; maxLines = 1
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#1A1A3E")); setStroke(1, Color.parseColor("#334466")) }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4); it.bottomMargin = dp(4) }
        }
        content.addView(editGameAge)
        content.addView(mkLabel("ℹ️ Questa età è fittizia e serve per le meccaniche di gioco (crescita, famiglia, adozioni). Non è l'età reale.", 10f, Color.parseColor("#667788"), false).also { (it.layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(2); bottomMargin = dp(16) } })

        content.addView(mkLabel("Posizione iniziale", 14f, Color.parseColor("#88AADD"), true).also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10) })
        content.addView(mkLabel("Dove inizia la tua prima avventura. Questa scelta vale SOLO per il primo spawn: in seguito ripartirai sempre dall'ultima posizione in cui eri.", 10f, Color.parseColor("#667788"), false).also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(2); (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(6) })
        btnUseGps = Button(this).apply {
            text = "📡 Inizia vicino a me (usa il GPS)"; textSize = 14f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#1A1A3E")); setStroke(1, Color.parseColor("#334466")) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).also { it.topMargin = dp(2); it.bottomMargin = dp(4) }
            setOnClickListener { onUseGps() }
        }
        content.addView(btnUseGps)
        tvGpsStatus = TextView(this).apply {
            textSize = 11f; setTextColor(Color.parseColor("#88AADD")); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(2); it.bottomMargin = dp(12) }
            text = "Partirai da Roma (41.9028, 12.4964)"
        }
        content.addView(tvGpsStatus)

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

    private fun updateWarning() {
        val realAge = editRealAge?.text?.toString()?.toIntOrNull() ?: 0
        if (realAge in 1..17) {
            tvWarning?.text = "Devi essere maggiorenne (18+) per giocare."
            tvWarning?.visibility = View.VISIBLE
        } else {
            tvWarning?.visibility = View.GONE
        }
    }

    /** Pulsante "usa il GPS": legge la posizione attuale del telefono. */
    private fun onUseGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), RC_LOCATION)
            return
        }
        val loc = getDeviceLocation()
        if (loc != null) {
            chosenGpsLat = loc.first
            chosenGpsLng = loc.second
            tvGpsStatus?.text = "Partirai dalla tua posizione (${"%.4f".format(loc.first)}, ${"%.4f".format(loc.second)})"
            tvGpsStatus?.setTextColor(Color.parseColor("#00E5FF"))
            Toast.makeText(this, "Posizione GPS acquisita!", Toast.LENGTH_SHORT).show()
        } else {
            tvGpsStatus?.text = "GPS non disponibile: resterai a Roma. Riapri l'app con il GPS attivo e ritocca il pulsante."
            tvGpsStatus?.setTextColor(Color.parseColor("#FF8A65"))
            Toast.makeText(this, "GPS non disponibile: partirai da Roma", Toast.LENGTH_LONG).show()
        }
    }

    private fun getDeviceLocation(): Pair<Double, Double>? {
        return try {
            val lm = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return null
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null && Math.abs(loc.latitude) > 0.001 && Math.abs(loc.longitude) > 0.001)
                Pair(loc.latitude, loc.longitude) else null
        } catch (_: Exception) { null }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_LOCATION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                onUseGps()
            } else {
                Toast.makeText(this, "Permesso GPS negato: partirai da Roma.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onConfirm() {
        val nickname = editNickname?.text?.toString()?.trim() ?: ""
        if (nickname.isBlank() || nickname.length < 2) { Toast.makeText(this, "Inserisci un nickname valido!", Toast.LENGTH_SHORT).show(); return }
        if (selectedGender.isEmpty()) { Toast.makeText(this, "Scegli il sesso (Maschio o Femmina)!", Toast.LENGTH_SHORT).show(); return }
        val realAge = editRealAge?.text?.toString()?.toIntOrNull() ?: 0
        if (realAge < 18 || realAge > 120) { Toast.makeText(this, "Devi avere almeno 18 anni per giocare!", Toast.LENGTH_LONG).show(); return }
        val gameAge = editGameAge?.text?.toString()?.toIntOrNull() ?: 18
        if (gameAge < 0 || gameAge > 99) { Toast.makeText(this, "Età del personaggio non valida (0-99)!", Toast.LENGTH_SHORT).show(); return }
        val profile = PlayerProfileManager.myProfile
        if (profile == null) { Toast.makeText(this, "Errore: profilo non caricato. Riprova.", Toast.LENGTH_LONG).show(); return }
        profile.name = nickname.replaceFirstChar { it.uppercase() }
        profile.playerGender = selectedGender
        profile.genderChosenAt = if (profile.genderChosenAt == 0L) System.currentTimeMillis() else profile.genderChosenAt
        profile.realAge = realAge
        profile.birthYear = ageToBirthYear(gameAge)
        profile.isMinor = gameAge < 18
        // posizione iniziale: scelta singola (pulsante GPS / default Roma), usata
        // SOLO al primo spawn (dopo si riparte dall'ultima posizione di gioco)
        profile.gpsLat = chosenGpsLat
        profile.gpsLng = chosenGpsLng
        profile.profileCompleted = true
        PlayerProfileManager.persistMyProfile()
        Toast.makeText(this, "Profilo completato! Personaggio: $gameAge anni", Toast.LENGTH_SHORT).show()
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
