package com.intelligame.huntix.bridge

import android.content.Context
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.PlayerProfileManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * CityStateSync — ponte bidirezionale forte tra MiCittà (Unity) e il profilo
 * generale Huntix (Android).
 *
 * Unity esporta qui lo SNAPSHOT completo dello stato città (soldi, energia,
 * sonno, età, uova bestiario, lavori, persone conosciute, famiglia, missioni)
 * e Android lo conserva; all'avvio della città Unity ri-importa lo snapshot se
 * il salvataggio locale è vergine (reinstall/Cache pulita), così:
 *   Unity → Android: lo stato di gioco città è registrato nel profilo generale.
 *   Android → Unity: lo stato città sopravvive fuori da Unity e viene ripristinato.
 *
 * Fonte di verità in sessione: Unity (Wikimedia state live). Android è il
 * mirror persistente + fonte per il profilo generale (XP/MVC/skin/energia
 * vivono già lato Android).
 */
object CityStateSync {

    private const val TAG = "CityStateSync"
    private const val PREF_FILE = "city_sync_prefs"
    private const val KEY_SNAPSHOT = "city_snapshot_v1"
    private const val KEY_TS = "city_snapshot_ts"

    // ── persistenza snapshot ────────────────────────────────────

    /** Salva lo snapshot citta ricevuto da Unity e aggiorna il profilo generale. */
    fun onCitySnapshot(context: Context, json: String) {
        if (json.isBlank()) return
        try {
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_SNAPSHOT, json)
                .putLong(KEY_TS, System.currentTimeMillis())
                .apply()
            applyToProfile(context, json)
            AppLog.i(TAG, "snapshot citta salvato (${json.length} byte)")
        } catch (e: Exception) {
            AppLog.w(TAG, "onCitySnapshot failed: ${e.message}")
        }
    }

    /** Restituisce lo snapshot citta salvato ("" se assente). */
    fun getSnapshot(context: Context): String {
        return try {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getString(KEY_SNAPSHOT, "") ?: ""
        } catch (e: Exception) {
            AppLog.w(TAG, "getSnapshot failed: ${e.message}")
            ""
        }
    }

    fun lastSyncTs(context: Context): Long {
        return try {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getLong(KEY_TS, 0L)
        } catch (e: Exception) { 0L }
    }

    // ── profilo generale ────────────────────────────────────────

    /** Copia i riepiloghi citta nello PPlayerProfile unificato (così la Home e
     *  Firestore vedono anche lo stato MiCittà). Idempotente e non distruttivo. */
    private fun applyToProfile(context: Context, json: String) {
        try {
            val j = JSONObject(json)
            val profile = PlayerProfileManager.myProfile ?: return
            var changed = false

            val money = j.optLong("money", -1L)
            if (money >= 0 && profile.cityMoney != money) {
                profile.cityMoney = money
                changed = true
            }
            // Il saldo è il massimo mai visto (il profilo può essere più
            // recente dello snapshot ma mai arretrato).
            val people = j.optInt("peopleKnown", -1)
            if (people >= 0 && profile.cityPeopleKnown < people) {
                profile.cityPeopleKnown = people
                changed = true
            }
            val eggDexStr = j.optString("eggDex", "")
            if (eggDexStr.isNotBlank()) {
                val count = eggDexStr.split(';').size
                if (profile.cityEggDexCount < count) {
                    profile.cityEggDexCount = count
                    changed = true
                }
            } else {
                val eggDex = j.optJSONArray("eggDex")
                if (eggDex != null) {
                    val count = eggDex.length()
                    if (profile.cityEggDexCount < count) {
                        profile.cityEggDexCount = count
                        changed = true
                    }
                }
            }
            val jobs = j.optString("jobs", "")
            if (jobs.isNotBlank() && profile.cityJobs != jobs) {
                profile.cityJobs = jobs
                changed = true
            }
            val family = j.optString("family", "")
            if (family.isNotBlank() && profile.cityFamily != family) {
                profile.cityFamily = family
                changed = true
            }
            // Casa MiCittà (nome|lat|lng|garage|garagemodel|stanze): il profilo
            // conserva il riepilogo completo per il sync bidirezionale.
            val home = j.optString("home", "")
            if (home.isNotBlank() && profile.cityHome != home) {
                profile.cityHome = home
                changed = true
            }
            if (changed) {
                PlayerProfileManager.persistMyProfile()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "applyToProfile failed: ${e.message}")
        }
    }

    /** Applica al profilo l'ultimo snapshot salvato (usato all'avvio app). */
    fun applyStoredToProfile(context: Context) {
        val snap = getSnapshot(context)
        if (snap.isNotBlank()) applyToProfile(context, snap)
    }

    // ── startup sync (splash "Realtà Aumentata · Caccia alle Uova") ──
    /**
     * Sincronizzazione automatica all'avvio dell'app: il profilo generale
     * assorbe l'ultimo snapshot citta senza bloccare lo splash (il lavoro è un
     * paio di SharedPreference read/write: la prima volta che la città viene
     * esportata è più lenta perché Unity deve generare lo snapshot; in seguito
     * lo snapshot è già in prefs e questa chiamata è praticamente istantanea).
     */
    fun startupSync(context: Context) {
        try {
            val snapshot = getSnapshot(context)
            if (snapshot.isBlank()) {
                AppLog.d(TAG, "startupSync: nessuno snapshot citta (primo avvio MiCittà)")
                return
            }
            applyToProfile(context, snapshot)
            AppLog.i(TAG, "startupSync: profilo allineato allo snapshot citta " +
                "(${snapshot.length} byte, ultimo sync ${lastSyncTs(context)})")
            // Firestore best-effort: se il profilo è sincronizzato, salva anche
            // i nuovi riepiloghi città sul cloud.
            try {
                PlayerProfileManager.persistMyProfile()
            } catch (e: Exception) {
                AppLog.w(TAG, "startupSync persist: ${e.message}")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "startupSync failed: ${e.message}")
        }
    }

    // ── check di sincronizzazione ───────────────────────────────

    /**
     * Verifica di allineamento: riporta i valori chiave dello snapshot lato
     * Android vs gli stessi nell'ultimo snapshot ricevuto da Unity. Ritorna un
     * report JSON/log usato dal "controllo che tutto sia sincronizzato".
     */
    fun checkSync(context: Context): String {
        val ts = lastSyncTs(context)
        val snapshot = getSnapshot(context)
        if (snapshot.isBlank()) {
            AppLog.d(TAG, "checkSync: NESSUNO snapshot (città mai avviata)")
            return "{\"snapshot\":false,\"ts\":0,\"profile\":false,\"ok\":false}"
        }
        val j = try { JSONObject(snapshot) } catch (e: Exception) { JSONObject() }
        val people = j.optInt("peopleKnown", -1)
        val eggDexStr = j.optString("eggDex", "")
        val eggDex = if (eggDexStr.isNotBlank()) eggDexStr.split(';').size
            else j.optJSONArray("eggDex")?.length() ?: -1
        val money = j.optLong("money", -1L)
        val ok = people >= 0 || eggDex >= 0 || money >= 0
        val report = JSONObject()
        report.put("snapshot", true)
        report.put("ts", ts)
        report.put("peopleKnown", people)
        report.put("eggDex", eggDex)
        report.put("money", money)
        report.put("profilePeople", PlayerProfileManager.myProfile?.cityPeopleKnown ?: 0)
        report.put("profileEggDex", PlayerProfileManager.myProfile?.cityEggDexCount ?: 0)
        report.put("ok", ok)
        AppLog.i(TAG, "checkSync: $report")
        return report.toString()
    }
}