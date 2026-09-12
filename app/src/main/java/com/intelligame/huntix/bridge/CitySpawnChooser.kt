package com.intelligame.huntix.bridge

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

/**
 * Selettore di spawn per Miacitta: chiede DOVE far comparire il personaggio.
 *  - "Ultima posizione salvata" (posizione di gioco conservata su Google)
 *  - oppure una citta' italiana (il mondo OSM copre tutta Italia).
 * Il GPS del dispositivo NON viene usato. La scelta viene persistita e usata
 * come default alla prossima apertura del selettore.
 */
object CitySpawnChooser {

    private const val LABEL_LAST = "Ultima posizione salvata"

    /**
     * Mostra il dialog di scelta e chiama [onChosen] con (modo, nome, lat, lng).
     */
    fun show(
        ctx: Context,
        onChosen: (mode: String, name: String, lat: Double, lng: Double) -> Unit,
        onCanceled: (() -> Unit)? = null
    ) {
        // Prima di aprire il dialog prova a scaricare la posizione cloud piu'
        // recente, cosi' l'opzione "ultima posizione" e' aggiornata.
        WorldPosCloud.pull(ctx) {
            val activity = (ctx as? android.app.Activity)
            if (activity != null && !activity.isFinishing)
                activity.runOnUiThread { buildDialog(activity, onChosen, onCanceled) }
            else buildDialog(ctx, onChosen, onCanceled)
        }
    }

    private fun buildDialog(
        ctx: Context,
        onChosen: (mode: String, name: String, lat: Double, lng: Double) -> Unit,
        onCanceled: (() -> Unit)?
    ) {
        val hasLast = WorldPosCloud.hasLastPosition(ctx)
        val last = WorldPosCloud.lastPosition(ctx)

        val group = RadioGroup(ctx)
        group.orientation = RadioGroup.VERTICAL
        group.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))

        // Riga 0: ultima posizione salvata
        val rbLast = RadioButton(ctx)
        rbLast.text = LABEL_LAST
        rbLast.isEnabled = hasLast
        rbLast.id = View.generateViewId()
        group.addView(rbLast, lpWrap())

        if (hasLast && last != null) {
            val sub = TextView(ctx)
            sub.text = "Riparti da dove eri (%.5f, %.5f)".format(last.first, last.second)
            sub.textSize = 12f
            sub.setPadding(dp(ctx, 44), 0, dp(ctx, 8), dp(ctx, 6))
            group.addView(sub, lpWrap())
        } else if (!hasLast) {
            val sub = TextView(ctx)
            sub.text = "Nessuna posizione salvata: scegli una citta'."
            sub.textSize = 12f
            sub.setPadding(dp(ctx, 44), 0, dp(ctx, 8), dp(ctx, 6))
            group.addView(sub, lpWrap())
        }

        // Citta' italiane
        val prevMode = WorldPosCloud.lastSpawnMode(ctx)
        val prevName = WorldPosCloud.lastSpawnName(ctx)
        val cityButtons = HashMap<String, RadioButton>()
        WorldPosCloud.cities.forEach { city ->
            val rb = RadioButton(ctx)
            rb.text = city.name
            rb.id = View.generateViewId()
            group.addView(rb, lpWrap())
            cityButtons[city.name] = rb
        }

        // IMPORTANTE: la selezione di default va impostata DOPO che i radio sono
        // nel gruppo, via group.check(id). Impostando isChecked=true prima
        // dell'addView il RadioGroup non registra la scelta (checkedRadioButtonId
        // resta -1) e il click su SPAWN non fa nulla finche' non si tocca un
        // radio (come segnalato: al secondo avvio l'ultima posizione risultava
        // gia' selezionata ma SPAWN era inerte).
        var defaultChecked: RadioButton? = null
        if (hasLast && prevMode == "last") {
            defaultChecked = rbLast
        } else {
            defaultChecked = cityButtons[prevName] ?: rbLast.takeIf { hasLast }
        }
        defaultChecked?.let { group.check(it.id) }

        val scroll = ScrollView(ctx)
        scroll.addView(group)

        val padding = dp(ctx, 16)
        // android.app.AlertDialog.setView non accetta margini: uso un container
        // con padding interno come vista del dialog.
        val container = FrameLayout(ctx)
        container.setPadding(padding, padding, padding, padding)
        container.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("\uD83C\uDF0D Dove vuoi comparire?")
            .setView(container)
            .setPositiveButton("SPAWN", null)
            .setNegativeButton("Annulla", null)
            .create()

        var cancelled = false
        val cancelOnce = {
            if (!cancelled) {
                cancelled = true
                onCanceled?.invoke()
            }
        }

        dialog.setOnCancelListener { cancelOnce() }
        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val checked = group.checkedRadioButtonId
                val selected = group.findViewById<RadioButton>(checked) ?: return@setOnClickListener
                if (selected === rbLast) {
                    // se non c'e' ultima posizione non dovrebbe essere
                    // selezionabile, ma proteggiamo comunque
                    val p = last ?: return@setOnClickListener
                    if (cancelled) return@setOnClickListener
                    cancelled = false
                    WorldPosCloud.saveSpawnChoice(ctx, "last", LABEL_LAST, p.first, p.second)
                    dialog.dismiss()
                    onChosen("last", LABEL_LAST, p.first, p.second)
                } else {
                    val name = selected.text.toString()
                    val city = WorldPosCloud.cities.firstOrNull { it.name == name } ?: return@setOnClickListener
                    if (cancelled) return@setOnClickListener
                    cancelled = false
                    dialog.dismiss()
                    val activity = ctx as? android.app.Activity
                    if (activity != null) {
                        StreetSpawnDialog.show(activity, city, onChosen) { cancelOnce() }
                    } else {
                        WorldPosCloud.saveSpawnChoice(ctx, "city", city.name, city.lat, city.lng)
                        onChosen("city", city.name, city.lat, city.lng)
                    }
                }
            }
            val neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            neg.setOnClickListener {
                cancelOnce()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun lpWrap() =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

    private fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()
}