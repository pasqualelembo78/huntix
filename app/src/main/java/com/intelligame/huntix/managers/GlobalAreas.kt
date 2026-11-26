package com.intelligame.huntix.managers

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.EggCollectionActivity
import com.intelligame.huntix.ui.AbilityActivity
import org.json.JSONObject
import java.util.Locale

/**
 * Aree globali Huntix — Area Inventario e Area Abilità.
 *
 * Le due schermate vivono nell'app Android (sono già le sorgenti reali di
 * uova e mestieri), ma devono essere apribili da ogni parte del gioco: Home,
 * menu Altro, partita AR e menu di MiCittà. Questo oggetto è il solo punto di
 * ingresso: nessuna seconda collezione, nessun secondo archivio e nessuna
 * valuta nuova.
 *
 * L'evento `CityGlobalActivity` arriva da Unity quando in MiCittà si compra,
 * si vende o si scopre qualcosa: il contatore condiviso finisce qui e la voce
 * va nell'UNICO diario posizionale (`EggWhereLog`), con luogo, coordinate e
 * timestamp. Lo specchio torna a Unity nei flag `globalAreas` di `setMode`
 * e nello snapshot `CityStateSync`.
 */
object GlobalAreas {

    const val EVENT_INVENTORY = "AreaInventarioOpen"
    const val EVENT_SKILLS = "AreaAbilitaOpen"
    const val EVENT_ACTIVITY = "CityGlobalActivity"

    private const val TAG = "GlobalAreas"
    private const val PREF_FILE = "huntix_city_global_prefs"
    private const val KEY_INV = "inv"
    private const val KEY_SKILL = "skill"
    private const val KEY_DISC = "disc"
    private const val KEY_BUY = "buy"
    private const val KEY_SELL = "sell"
    private const val KEY_LAST_AREA = "last_area"
    private const val KEY_LAST_ACTION = "last_action"
    private const val KEY_LAST_PLACE = "last_place"
    private const val KEY_LAST_TS = "last_ts"

    /** Apre l'inventario uova da qualsiasi contesto (Activity o servizio). */
    fun openInventory(context: Context) = launch(context, EggCollectionActivity::class.java)

    /** Apre l'area abilità (abilità caccia-uova + mestieri di MiCittà). */
    fun openSkills(context: Context) = launch(context, AbilityActivity::class.java)

    private fun launch(context: Context, cls: Class<*>) {
        val intent = Intent(context, cls)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Registra un'azione di MiCittà arrivata via bridge. Idempotente per area:
     *  i contatori sono riepiloghi globali, non un doppio inventario. */
    fun record(context: Context, json: String) {
        val j = try { JSONObject(json) } catch (_: Exception) { return }
        val rawArea = j.optString("area", "").ifBlank { j.optString("kind", "") }
        if (rawArea.isBlank()) return
        val area = normalize(rawArea)
        val action = j.optString("action", "azione").ifBlank { "azione" }
        val detail = j.optString("detail", "")
        val place = j.optString("place", "").ifBlank { "città→$area→$action" }
        val lat = j.optDouble("lat", 0.0)
        val lng = j.optDouble("lng", 0.0)

        val key = counterKey(area)
        val prefs = prefs(context)
        if (key != null) {
            prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
        }
        prefs.edit()
            .putString(KEY_LAST_AREA, area)
            .putString(KEY_LAST_ACTION, action)
            .putString(KEY_LAST_PLACE, place)
            .putLong(KEY_LAST_TS, System.currentTimeMillis())
            .apply()

        // Stesso diario delle uova posizionali, categoria evento: MiCittà è
        // parte del mondo, non un archivio separato.
        EggWhereLog.recordActivity(
            context,
            EggWhereLog.KIND_EVENT,
            "MiCittà→$area→$action",
            place,
            lat,
            lng,
            detail
        )
        AppLog.i(TAG, "area=$area action=$action place=$place detail=$detail")
    }

    /** Ripristino passivo dallo snapshot/JSON ricevuto da Unity. Prende il
     *  massimo mai visto: un ripristino non può rubare progressi al profilo. */
    fun importFlags(context: Context, flags: String) {
        if (flags.isBlank()) return
        val prefs = prefs(context)
        val editor = prefs.edit()
        var changed = false
        flags.split(';').forEach { part ->
            val kv = part.split('=', limit = 2)
            if (kv.size != 2) return@forEach
            val key = when (kv[0].trim().lowercase(Locale.ITALIAN)) {
                "inv", "inventario" -> KEY_INV
                "skill", "abilita", "abilità" -> KEY_SKILL
                "disc", "scoperte" -> KEY_DISC
                "buy", "acquisti" -> KEY_BUY
                "sell", "vendita", "vendite" -> KEY_SELL
                else -> null
            } ?: return@forEach
            val value = kv[1].trim().toIntOrNull() ?: return@forEach
            if (value > prefs.getInt(key, 0)) {
                editor.putInt(key, value)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    /** Flag compatti rimandati a Unity in `setMode` e nel snapshot. */
    fun exportFlags(context: Context): String {
        val prefs = prefs(context)
        return listOf(KEY_INV, KEY_SKILL, KEY_DISC, KEY_BUY, KEY_SELL)
            .joinToString(";") { "$it=${prefs.getInt(it, 0)}" }
    }

    fun snapshot(context: Context): JSONObject {
        val prefs = prefs(context)
        return JSONObject().apply {
            put("inventario", prefs.getInt(KEY_INV, 0))
            put("abilita", prefs.getInt(KEY_SKILL, 0))
            put("scoperte", prefs.getInt(KEY_DISC, 0))
            put("acquisti", prefs.getInt(KEY_BUY, 0))
            put("vendite", prefs.getInt(KEY_SELL, 0))
            put("lastArea", prefs.getString(KEY_LAST_AREA, "") ?: "")
            put("lastAction", prefs.getString(KEY_LAST_ACTION, "") ?: "")
            put("lastPlace", prefs.getString(KEY_LAST_PLACE, "") ?: "")
            put("lastTs", prefs.getLong(KEY_LAST_TS, 0L))
        }
    }

    /** Riepilogo leggibile usato da Home, Area Inventario e Area Abilità. */
    fun summary(context: Context): String {
        val s = snapshot(context)
        val last = s.optString("lastArea", "")
        val place = s.optString("lastPlace", "")
        val coda = if (last.isNotBlank()) "\nUltima azione: $last · $place" else ""
        return "MiCittà nel mondo: inventario ${s.optInt("inventario")} · " +
               "abilità ${s.optInt("abilita")} · scoperte ${s.optInt("scoperte")} · " +
               "acquisti ${s.optInt("acquisti")} · vendite ${s.optInt("vendite")}" + coda
    }

    private fun normalize(area: String): String = when (area.lowercase(Locale.ITALIAN)) {
        "inventario", "inventory", "uova" -> "inventario"
        "abilita", "abilità", "skill", "skills", "mestieri" -> "abilita"
        "scoperta", "scoperte", "discovery", "discoveries" -> "scoperte"
        "acquisto", "acquisti", "buy", "purchase", "purchases" -> "acquisti"
        "vendita", "vendite", "sell", "sale", "sales" -> "vendita"
        else -> area.lowercase(Locale.ITALIAN)
    }

    private fun counterKey(area: String): String? = when (area) {
        "inventario" -> KEY_INV
        "abilita" -> KEY_SKILL
        "scoperte" -> KEY_DISC
        "acquisti" -> KEY_BUY
        "vendita" -> KEY_SELL
        else -> null
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
}
