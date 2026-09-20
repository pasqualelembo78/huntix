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
 * reali quando note, rarità e timestamp. Questo alimenta i futuri menu del
 * profilo che mostrano dove ogni uovo è stato trovato.
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

    fun record(ctx: Context, rarityId: String, place: String, lat: Double = 0.0, lng: Double = 0.0, name: String = "") {
        if (rarityId.isBlank()) return
        try {
            val prefs = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val arr = readArray(prefs)
            val entry = JSONObject().apply {
                put("ts", System.currentTimeMillis())
                put("rarity", rarityId)
                put("place", place)
                put("lat", lat)
                put("lng", lng)
                if (name.isNotBlank()) put("name", name)
            }
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

    fun count(ctx: Context): Int = getLog(ctx).size

    private fun readArray(prefs: android.content.SharedPreferences): JSONArray {
        val raw = prefs.getString(KEY_LOG, null) ?: return JSONArray()
        return try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
    }
}