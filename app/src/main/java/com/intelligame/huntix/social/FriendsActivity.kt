package com.intelligame.huntix.social

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.intelligame.huntix.BaseNavActivity
import androidx.cardview.widget.CardView
import com.intelligame.huntix.AgeGateManager
import com.intelligame.huntix.ParentalGateManager
import com.intelligame.huntix.ProfileSetupActivity

class FriendsActivity : BaseNavActivity() {

    override fun activeTab() = "Social"
    private lateinit var root: LinearLayout
    private lateinit var friendsContainer: LinearLayout
    private lateinit var requestsContainer: LinearLayout
    private lateinit var searchResultsContainer: LinearLayout
    private lateinit var searchStatus: TextView
    private lateinit var editSearchName: EditText
    private lateinit var editSearchCity: EditText
    private lateinit var spinnerCountry: Spinner
    private lateinit var spinnerGender: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AgeGateManager.checkAdultAccess(this, "Amici")) { finish(); return }
        // ✅ FIX v7.2.1: Parental gate extra per under 13
        ParentalGateManager.requireIfChild(this, "Amici") { initFriendsUI() }
    }

    private fun initFriendsUI() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#F0F2F5")) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(48), dp(16), dp(100)) }
        scroll.addView(root)
        root.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            // Back button rimosso: la navigazione è gestita dal menu in basso
            addView(TextView(this@FriendsActivity).apply { text = "Amici"; textSize = 22f; setTextColor(Color.parseColor("#E0E0FF")); typeface = Typeface.create("sans-serif-black", Typeface.BOLD) })
        })
        root.addView(secTitle("Cerca Giocatori"))
        editSearchName = mkInput("Nome giocatore..."); root.addView(editSearchName)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = lp().also { it.bottomMargin = dp(6) } }
        val countries = listOf("Tutti") + ProfileSetupActivity.COUNTRIES.map { it.second }
        spinnerCountry = Spinner(this).apply { adapter = ArrayAdapter(this@FriendsActivity, android.R.layout.simple_spinner_dropdown_item, countries); layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).also { it.marginEnd = dp(6) } }
        row2.addView(spinnerCountry)
        spinnerGender = Spinner(this).apply { adapter = ArrayAdapter(this@FriendsActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Tutti","Maschio","Femmina")); layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f) }
        row2.addView(spinnerGender); root.addView(row2)
        editSearchCity = mkInput("Citta..."); root.addView(editSearchCity)
        root.addView(Button(this).apply { text = "CERCA"; textSize = 14f; setTextColor(Color.WHITE); background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#00E5FF")) }; layoutParams = lp().also { it.bottomMargin = dp(12); it.height = dp(44) }; setOnClickListener { doSearch() } })
        searchStatus = TextView(this).apply { textSize = 12f; setTextColor(Color.GRAY); visibility = View.GONE; layoutParams = lp().also { it.bottomMargin = dp(8) } }; root.addView(searchStatus)
        searchResultsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(searchResultsContainer)
        root.addView(secTitle("Richieste")); requestsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(requestsContainer)
        root.addView(secTitle("I tuoi amici")); friendsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(friendsContainer)
        setContentView(scroll); refreshFriends()
    }

    private fun doSearch() {
        val name = editSearchName.text.toString().trim(); val city = editSearchCity.text.toString().trim()
        val countryList = listOf("") + ProfileSetupActivity.COUNTRIES.map { it.first }
        val country = countryList.getOrElse(spinnerCountry.selectedItemPosition) { "" }
        val gender = when (spinnerGender.selectedItemPosition) { 1->"male"; 2->"female"; else->"" }
        // Ricerca globale permessa
        searchStatus.text = "Ricerca..."; searchStatus.visibility = View.VISIBLE; searchStatus.setTextColor(Color.GRAY)
        searchResultsContainer.removeAllViews()
        FriendsManager.searchPlayers(name, country, city, gender) { resp -> runOnUiThread {
            searchResultsContainer.removeAllViews()
            when (resp) {
                is FriendsManager.SearchResponse.Success -> { searchStatus.text = "${resp.players.size} trovati"; searchStatus.setTextColor(Color.parseColor("#00CC6A")); if (resp.players.isEmpty()) { searchStatus.text = "Nessun risultato"; searchStatus.setTextColor(Color.GRAY) }; resp.players.forEach { p -> searchResultsContainer.addView(playerCard(p)) } }
                is FriendsManager.SearchResponse.TooMany -> { searchStatus.text = resp.message; searchStatus.setTextColor(Color.parseColor("#E65100")) }
                is FriendsManager.SearchResponse.Error -> { searchStatus.text = resp.message; searchStatus.setTextColor(Color.RED) }
            }
        }}
    }

    private fun playerCard(p: FriendsManager.SearchResult): View {
        val card = CardView(this).apply { radius = dp(10).toFloat(); setCardBackgroundColor(Color.WHITE); layoutParams = lp().also { it.bottomMargin = dp(6) }; cardElevation = dp(2).toFloat() }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(10),dp(12),dp(10)) }
        val emoji = if (p.gender=="male") "M" else if (p.gender=="female") "F" else "?"
        row.addView(TextView(this).apply { text = emoji; textSize = 20f; gravity = Gravity.CENTER; background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#E3F2FD")) }; layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)) })
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(10) } }
        info.addView(TextView(this).apply { text = p.name; textSize = 15f; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD })
        val loc = listOfNotNull(p.city.ifBlank{null}, p.country.ifBlank{null}).joinToString(", ")
        if (loc.isNotBlank()) info.addView(TextView(this).apply { text = loc; textSize = 11f; setTextColor(Color.GRAY) })
        info.addView(TextView(this).apply { text = "Lv.${p.level}"; textSize = 11f; setTextColor(Color.parseColor("#00E5FF")) })
        row.addView(info)
        row.addView(Button(this).apply { text = "+"; textSize = 18f; setTextColor(Color.WHITE); background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.parseColor("#43A047")) }; layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)); setOnClickListener { FriendsManager.sendRequest(p.uid) { ok -> runOnUiThread { Toast.makeText(this@FriendsActivity, if (ok) "Richiesta inviata a ${p.name}!" else "Errore", Toast.LENGTH_SHORT).show() }}} })
        card.addView(row); return card
    }

    private fun refreshFriends() {
        requestsContainer.removeAllViews()
        FriendsManager.getIncomingRequests { reqs -> runOnUiThread { if (reqs.isEmpty()) requestsContainer.addView(TextView(this).apply { text = "Nessuna richiesta"; textSize = 12f; setTextColor(Color.GRAY) }) else reqs.forEach { r ->
            val name = r["senderName"] as? String ?: "?"; val uid = r["docId"] as? String ?: return@forEach
            val card = CardView(this).apply { radius = dp(8).toFloat(); setCardBackgroundColor(Color.parseColor("#FFF8E1")); layoutParams = lp().also { it.bottomMargin = dp(6) } }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(10),dp(12),dp(10)) }
            row.addView(TextView(this).apply { text = name; textSize = 14f; setTextColor(Color.BLACK); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(Button(this).apply { text = "OK"; textSize = 12f; setTextColor(Color.WHITE); background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(Color.parseColor("#43A047")) }; layoutParams = LinearLayout.LayoutParams(dp(50),dp(34)); setOnClickListener { FriendsManager.acceptRequest(uid, name) { ok -> runOnUiThread { if (ok) { Toast.makeText(this@FriendsActivity, "Accettato!", Toast.LENGTH_SHORT).show(); refreshFriends() } }}} })
            row.addView(Button(this).apply { text = "X"; textSize = 12f; setTextColor(Color.WHITE); background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(Color.parseColor("#FF3366")) }; layoutParams = LinearLayout.LayoutParams(dp(34),dp(34)).also { it.marginStart = dp(4) }; setOnClickListener { FriendsManager.rejectRequest(uid) { runOnUiThread { refreshFriends() } }} })
            card.addView(row); requestsContainer.addView(card)
        }}}
        friendsContainer.removeAllViews()
        FriendsManager.getFriends { friends -> runOnUiThread { if (friends.isEmpty()) friendsContainer.addView(TextView(this).apply { text = "Nessun amico ancora"; textSize = 12f; setTextColor(Color.GRAY) }) else friends.forEach { f ->
            val name = f["name"] as? String ?: "?"; val uid = f["uid"] as? String ?: return@forEach
            val card = CardView(this).apply { radius = dp(8).toFloat(); setCardBackgroundColor(Color.WHITE); layoutParams = lp().also { it.bottomMargin = dp(6) }; cardElevation = dp(1).toFloat() }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(10),dp(12),dp(10)) }
            row.addView(TextView(this).apply { text = name; textSize = 15f; setTextColor(Color.BLACK); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(Button(this).apply { text = "Chat"; textSize = 12f; setTextColor(Color.WHITE); background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(Color.parseColor("#075E54")) }; layoutParams = LinearLayout.LayoutParams(dp(60),dp(34)); setOnClickListener { startActivity(Intent(this@FriendsActivity, ChatActivity::class.java).apply { putExtra("friendUid", uid); putExtra("friendName", name) }) } })
            card.addView(row); friendsContainer.addView(card)
        }}}
    }

    private fun secTitle(t: String) = TextView(this).apply { text = t; textSize = 16f; setTextColor(Color.parseColor("#E0E0FF")); typeface = Typeface.DEFAULT_BOLD; layoutParams = lp().also { it.topMargin = dp(20); it.bottomMargin = dp(8) } }
    private fun mkInput(hint: String) = EditText(this).apply { this.hint = hint; textSize = 14f; setTextColor(Color.BLACK); setHintTextColor(Color.GRAY); background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(Color.WHITE); setStroke(1, Color.parseColor("#DDDDDD")) }; setPadding(dp(12),dp(10),dp(12),dp(10)); layoutParams = lp().also { it.bottomMargin = dp(6) } }
    private fun lp() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
