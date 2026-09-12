package com.intelligame.huntix.bridge

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.intelligame.huntix.AppLog

/**
 * Secondo passo del selettore di spawn: dopo aver scelto la CITTA' si chiede
 * IN QUALE VIA apparire. Il player digita anche solo una parte del nome
 * ("pellico"): il matcher fuzzy propone le vie piu' vicine e, se ce n'e' una
 * sola chiara, chiede "Volevi dire via Silvio Pellico?"; se i candidati sono
 * piu' simili tra loro (via Silvio vs via Alfonso Pellico) chiede "Quale tra
 * queste intendi?" e lascia scegliere. Spunto sempre disponibile anche il
 * centro citta' (passo considerato nullo).
 */
object StreetSpawnDialog {

    private const val TAG = "StreetSpawnDialog"

    fun show(activity: Activity, city: WorldPosCloud.City,
             onChosen: (mode: String, name: String, lat: Double, lng: Double) -> Unit,
             onCanceled: () -> Unit) {
        val ctx = activity as Context
        val cacheKey = StreetIndexer.safeKey(city.name)

        // ── UI ──
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        val pad = dp(ctx, 14)
        root.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), 0)

        val status = TextView(ctx)
        status.text = "Scarico le vie della citta'..."
        status.textSize = 13f
        root.addView(status, lpWrap())

        val input = EditText(ctx)
        input.hint = "es. Pellico, Via Silvio Pellico..."
        input.setTextSize(15f)
        root.addView(input, lpWrap())

        val header = TextView(ctx)
        header.text = ""
        header.textSize = 13f
        header.setPadding(0, dp(ctx, 6), 0, 0)
        root.addView(header, lpWrap())

        val listWrap = LinearLayout(ctx)
        listWrap.orientation = LinearLayout.VERTICAL
        listWrap.setPadding(0, 0, 0, 0)
        val scroll = ScrollView(ctx)
        scroll.addView(listWrap, lpWrap())
        root.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(ctx, 260)))

        val centerBtn = Button(ctx)
        centerBtn.text = "\uD83D\uDCCD SPAWN AL CENTRO CITTA'"
        centerBtn.setPadding(0, 0, 0, 0)
        root.addView(centerBtn, lpWrap())

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("\uD83C\uDFD9\uFE0F In quale via? \u2014 " + city.name)
            .setView(root)
            .setPositiveButton("SPAWN SULLA VIA", null)
            .setNegativeButton("Annulla", null)
            .create()

        // ── stato ──
        var streets: List<StreetIndexer.Street> = emptyList()
        var loading = true
        var selected: StreetIndexer.Street? = null
        val refresh = Handler(Looper.getMainLooper())

        fun hideKeyboard() {
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(input.windowToken, 0)
        }

        fun renderSuggestions(query: String) {
            if (loading) return
            listWrap.removeAllViews()
            selected = null
            val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            posBtn?.isEnabled = false
            if (query.isBlank()) {
                header.text = "Digita per cercare la via della citta'."
                return
            }
            val sugg = FuzzyStreet.suggest(query, streets, 6)
            if (sugg.isEmpty()) {
                header.text = "Nessuna via trovata per \"" + query.trim() + "\". Scegli il centro citta'."
                return
            }
            val top = sugg[0].score
            val second = if (sugg.size > 1) sugg[1].score else -100000
            val multiClose = sugg.size >= 2 && top - second <= 120
            val singleClear = sugg.size == 1 && top >= 500
            header.text = when {
                singleClear -> "Volevi dire " + sugg[0].street.name + "?"
                multiClose -> "Quale tra queste intendi?"
                else -> "Suggerimenti:"
            }
            for (sc in sugg) {
                val b = Button(ctx)
                b.text = sc.street.name
                b.setPadding(0, 0, 0, 0)
                b.setOnClickListener {
                    selected = sc.street
                    posBtn?.isEnabled = true
                    header.text = "Scelta: " + sc.street.name
                    hideKeyboard()
                }
                listWrap.addView(b, lpWrap())
            }
            if (singleClear) {
                selected = sugg[0].street
                posBtn?.isEnabled = true
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val t = s?.toString() ?: ""
                refresh.removeCallbacksAndMessages(null)
                refresh.postDelayed({ renderSuggestions(t) }, 250)
            }
        })

        // Carica le vie (cache o download) su thread di background.
        Thread {
            val loaded = StreetIndexer.ensure(
                ctx, cacheKey, city.lat, city.lng, 15000)
            activity.runOnUiThread {
                if (!dialog.isShowing) return@runOnUiThread
                loading = false
                streets = loaded
                if (streets.isEmpty()) {
                    status.text = "Nessuna via disponibile per questa mappa. Puoi comunque scegliere il centro citta'."
                    header.text = ""
                } else {
                    status.text = streets.size.toString() + " vie disponibili \u2014 scrivi per cercare."
                }
            }
        }.start()

        centerBtn.setOnClickListener {
            WorldPosCloud.saveSpawnChoice(ctx, "city", city.name, city.lat, city.lng)
            refresh.removeCallbacksAndMessages(null)
            dialog.dismiss()
            onChosen("city", city.name, city.lat, city.lng)
        }

        dialog.setOnShowListener {
            val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE) ?: return@setOnShowListener
            posBtn?.isEnabled = false
            posBtn.setOnClickListener {
                val s = selected ?: return@setOnClickListener
                WorldPosCloud.saveSpawnChoice(ctx, "city", city.name, s.lat, s.lng)
                refresh.removeCallbacksAndMessages(null)
                dialog.dismiss()
                val label = s.name + " (" + city.name + ")"
                onChosen("city", label, s.lat, s.lng)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                refresh.removeCallbacksAndMessages(null)
                dialog.dismiss()
                onCanceled()
            }
        }

        dialog.setOnCancelListener {
            refresh.removeCallbacksAndMessages(null)
            onCanceled()
        }

        dialog.show()
    }

    private fun lpWrap() =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()
}
