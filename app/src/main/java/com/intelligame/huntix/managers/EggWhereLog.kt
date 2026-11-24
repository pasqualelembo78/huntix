package com.intelligame.huntix.managers

import android.content.Context
import com.intelligame.huntix.AppLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * EggWhereLog — il "DOVE HO TROVATO L'UOVO" dell'universo Huntix.
 *
 * Ogni uovo catturato/raccolto in QUALSIASI modalità (città Unity, outdoor GPS,
 * indoor AR, minigiochi) registra qui la sua posizione: luogo testuale
 * (es. "casa→cucina→cassetto2", "POI Chiesa", rz.raro), coordinate lat/lng
 * reali quando note, rarità e timestamp. Dal 24/11/2026 lo stesso diario
 * accoglie anche le voci non-uovo (missioni, check-in, badge, eventi Unity),
 * così il registro attività del giocatore ha UN solo storico, con filtri.
 *
 * Persistenza: SharedPreferences (lista JSON limitata agli ultimi MAX voci).
 * Il Mandato prevede che questa lista sia raggiungibile lato Android anche
 * quando Unity non è attivo: ecco la memoria dell'universo.
 */
object EggWhereLog {

    private const val TAG = "EggWhereLog"
    private const val PREF_FILE = "egg_where_prefs"
    private const val KEY_LOG = "egg_where_log"
    private const val MAX = 500

    // Categorie del diario. "uovo" e' il valore implicito delle voci storiche
    // (che non avevano il campo "kind"): nessuna migrazione, nessuna perdita.
    const val KIND_EGG = "uovo"
    const val KIND_QUEST = "missione"
    const val KIND_LOGIN = "login"
    const val KIND_BADGE = "badge"
    const val KIND_EVENT = "evento"

    private val KINDS = setOf(KIND_EGG, KIND_QUEST, KIND_LOGIN, KIND_BADGE, KIND_EVENT)

    fun record(ctx: Context, rarityId: String, place: String, lat: Double = 0.0, lng: Double = 0.0, name: String = "") {
        if (rarityId.isBlank()) return
        val entry = JSONObject().apply {
            put("kind", KIND_EGG)
            put("ts", System.currentTimeMillis())
            put("rarity", rarityId)
            put("place", place)
            put("lat", lat)
            put("lng", lng)
            if (name.isNotBlank()) {
                put("name", name)
                put("label", "Uovo $rarityId · $name")
            } else {
                put("label", "Uovo $rarityId")
            }
        }
        append(ctx, entry)
    }

    /**
     * Voce generica del registro attività. Non duplica il log delle uova:
     * scrive nello stesso array con una categoria, quindi la cronologia resta
     * unica e ordinabile. Chiamata dai riscatti missione/streak, dai badge e
     * dall'evento bridge `ActivityLogEvent` arrivato da Miacittà.
     */
    fun recordActivity(
        ctx: Context,
        kind: String,
        label: String,
        place: String,
        lat: Double = 0.0,
        lng: Double = 0.0,
        name: String = ""
    ) {
        if (label.isBlank()) return
        val category = if (kind in KINDS) kind else KIND_EVENT
        val entry = JSONObject().apply {
            put("kind", category)
            put("ts", System.currentTimeMillis())
            put("label", label)
            put("place", place)
            put("lat", lat)
            put("lng", lng)
            if (name.isNotBlank()) put("name", name)
        }
        append(ctx, entry)
    }

    private fun append(ctx: Context, entry: JSONObject) {
        try {
            val prefs = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val arr = readArray(prefs)
            arr.put(entry)
            while (arr.length() > MAX) arr.remove(0)
            prefs.edit().putString(KEY_LOG, arr.toString()).apply()
            // Il profilo generico "sa dove sono le uova trovate": aggiorna il
            // contatore condiviso così anche Home/Firestore lo vedono.
            try {
                val profile = com.intelligame.huntix.PlayerProfileManager.myProfile
                if (profile != null && profile.cityEggWhere != arr.length()) {
                    profile.cityEggWhere = arr.length()
                    com.intelligame.huntix.PlayerProfileManager.persistMyProfile()
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "profile update failed: ${e.message}")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "record failed: ${e.message}")
        }
    }

    fun getLog(ctx: Context): List<JSONObject> {
        return try {
            readArray(ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE))
                .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
        } catch (e: Exception) {
            AppLog.w(TAG, "getLog failed: ${e.message}")
            emptyList()
        }
    }

    /** Filtro del diario: le voci senza "kind" sono uova (formato storico). */
    fun getByKind(ctx: Context, kind: String): List<JSONObject> =
        getLog(ctx).filter { it.optString("kind", KIND_EGG) == kind }

    fun count(ctx: Context): Int = getLog(ctx).size

    fun countByKind(ctx: Context, kind: String): Int = getByKind(ctx, kind).size

    private fun readArray(prefs: android.content.SharedPreferences): JSONArray {
        val raw = prefs.getString(KEY_LOG, null) ?: return JSONArray()
        return try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
    }
}
