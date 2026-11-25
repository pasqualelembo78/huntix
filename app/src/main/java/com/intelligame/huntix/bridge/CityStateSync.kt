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
    private const val KEY_SHOP_ACTIVITY = "city_shop_activity_v1"
    private const val KEY_UNIVERSAL_INTERIORS = "city_universal_interiors_v1"
    private const val KEY_HOUSE_VISITS = "city_house_visits_v1"
    private const val KEY_SHOP_STYLE = "city_shop_style_v1"
    private const val KEY_VENUE_EVENT = "city_venue_event_v1"
    private const val KEY_ENV_EVENT = "city_env_event_v1"

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

    /** Negozi walk-in (Unity → app): specchio dedicato dei contatori attività.
     *  È un mirror non distruttivo: lo snapshot completo resta la fonte. */
    fun onShopActivity(context: Context, json: String) {
        if (json.isBlank()) return
        try {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY_SHOP_ACTIVITY, json).apply()
        } catch (e: Exception) {
            AppLog.w(TAG, "onShopActivity failed: ${e.message}")
        }
    }

    fun getShopActivity(context: Context): String {
        return try {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getString(KEY_SHOP_ACTIVITY, "") ?: ""
        } catch (e: Exception) { "" }
    }

    /** Interni universali (Unity → app): visita a un locale 3D. Il mirror e'
     *  un JSON {chiave: visite}, unito per massimo (mai decrementato). */
    fun onUniversalInteriorVisit(context: Context, json: String) {
        if (json.isBlank()) return
        try {
            val j = JSONObject(json)
            val key = j.optString("key", "")
            if (key.isBlank()) return
            val visits = j.optInt("visits", 1).coerceAtLeast(0)
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val mirror = try {
                JSONObject(prefs.getString(KEY_UNIVERSAL_INTERIORS, "{}") ?: "{}")
            } catch (e: Exception) { JSONObject() }
            if (visits > mirror.optInt(key, 0)) {
                mirror.put(key, visits)
                prefs.edit().putString(KEY_UNIVERSAL_INTERIORS, mirror.toString()).apply()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "onUniversalInteriorVisit failed: ${e.message}")
        }
    }

    fun getUniversalInteriors(context: Context): String {
        return try {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getString(KEY_UNIVERSAL_INTERIORS, "{}") ?: "{}"
        } catch (e: Exception) { "{}" }
    }

    /** Unisce il riepilogo degli interni dello snapshot nel mirror Android.
     *  Chiamato anche al restore: Android conserva i locali visitati e li
     *  restituisce a Unity senza un evento dedicato aggiuntivo. */
    private fun mergeUniversalSnapshot(context: Context, json: String) {
        if (json.isBlank()) return
        try {
            val rows = JSONObject(json).optJSONArray("interiors") ?: return
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val mirror = try {
                JSONObject(prefs.getString(KEY_UNIVERSAL_INTERIORS, "{}") ?: "{}")
            } catch (e: Exception) { JSONObject() }
            var changed = false
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val key = row.optString("key", "")
                if (key.isBlank()) continue
                val visits = row.optInt("visits", 0).coerceAtLeast(0)
                if (visits > mirror.optInt(key, 0)) {
                    mirror.put(key, visits)
                    changed = true
                }
            }
            if (changed) {
                prefs.edit().putString(KEY_UNIVERSAL_INTERIORS, mirror.toString()).apply()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "mergeUniversalSnapshot failed: ${e.message}")
        }
    }

    /** Case visitabili OSM (Unity → app): visita a una casa reale di MiCittà.
     *  Il mirror conserva {houses:{chiave:{osmId,...}}, eggs:[featureId,...]}
     *  e fa vincere il conteggio/timestamp più recente (mai decrementato). */
    fun onHouseVisit(context: Context, json: String) {
        if (json.isBlank()) return
        try {
            val j = JSONObject(json)
            val key = j.optString("key", "")
            if (key.isBlank()) return
            val visits = j.optInt("visits", 1).coerceAtLeast(0)
            val lastTs = j.optLong("lastTs", 0L)
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val mirror = try {
                JSONObject(prefs.getString(KEY_HOUSE_VISITS, "{}") ?: "{}")
            } catch (e: Exception) { JSONObject() }
            val houses = mirror.optJSONObject("houses") ?: JSONObject()
            val previous = houses.optJSONObject(key)
            if (previous == null || visits > previous.optInt("visits", 0) ||
                lastTs > previous.optLong("lastTs", 0L)) {
                val row = JSONObject()
                row.put("osmId", j.optLong("osmId", 0L))
                row.put("kind", j.optString("kind", "casa"))
                row.put("label", j.optString("label", "Casa visitabile"))
                row.put("visits", visits)
                row.put("lastTs", lastTs)
                houses.put(key, row)
                mirror.put("houses", houses)
                if (!mirror.has("eggs")) mirror.put("eggs", JSONArray())
                prefs.edit().putString(KEY_HOUSE_VISITS, mirror.toString()).apply()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "onHouseVisit failed: ${e.message}")
        }
    }

    fun getHouseVisits(context: Context): String {
        return try {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getString(KEY_HOUSE_VISITS, "{}") ?: "{}"
        } catch (e: Exception) { "{}" }
    }

    /** Punti di accesso delle case visitabili (Unity → app), dallo snapshot. */
    fun getHouseTargets(context: Context): JSONArray {
        return try {
            JSONObject(getSnapshot(context)).optJSONArray("houseTargets") ?: JSONArray()
        } catch (e: Exception) { JSONArray() }
    }

    /**
     * Android → Unity: imposta come destinazione la porta della casa visitabile
     * (evento "navigateToHouse" ricevuto da GameManager: imposta la freccetta
     * della bussola e il marker sulla minimappa).
     * Nessun teletrasporto forzato: Unity decide come raggiungerla.
     */
    fun goToHouse(context: Context, osmId: Long = 204023010L): Boolean {
        val targets = getHouseTargets(context)
        for (i in 0 until targets.length()) {
            val row = targets.optJSONObject(i) ?: continue
            if (osmId != 0L && row.optLong("osmId", 0L) != osmId) continue
            val payload = JSONObject()
            payload.put("lat", row.optDouble("lat", 0.0))
            payload.put("lng", row.optDouble("lng", 0.0))
            payload.put("label", row.optString("label", "Casa visitabile"))
            UnityIpc.sendToUnity("GameManager", "navigateToHouse", payload.toString())
            return true
        }
        return false
    }

    /** Unisce lo snapshot delle case visitate nel mirror Android. Chiamato
     *  anche al restore: le uova raccolte non rinascono dopo un rebuild. */
    private fun mergeHouseSnapshot(context: Context, json: String) {
        if (json.isBlank()) return
        try {
            val incoming = JSONObject(json)
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val mirror = try {
                JSONObject(prefs.getString(KEY_HOUSE_VISITS, "{}") ?: "{}")
            } catch (e: Exception) { JSONObject() }
            val houses = mirror.optJSONObject("houses") ?: JSONObject()
            var changed = false
            val rows = incoming.optJSONArray("houses")
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val key = row.optString("key", "")
                    if (key.isBlank()) continue
                    val visits = row.optInt("visits", 0).coerceAtLeast(0)
                    val lastTs = row.optLong("lastTs", 0L)
                    val previous = houses.optJSONObject(key)
                    if (previous == null || visits > previous.optInt("visits", 0) ||
                        lastTs > previous.optLong("lastTs", 0L)) {
                        houses.put(key, row)
                        changed = true
                    }
                }
            }
            val eggs = mirror.optJSONArray("eggs") ?: JSONArray()
            val incomingEggs = incoming.optJSONArray("eggs")
            if (incomingEggs != null) {
                for (i in 0 until incomingEggs.length()) {
                    val id = incomingEggs.optString(i, "")
                    if (id.isBlank()) continue
                    var found = false
                    for (k in 0 until eggs.length()) {
                        if (eggs.optString(k, "") == id) { found = true; break }
                    }
                    if (!found) { eggs.put(id); changed = true }
                }
            }
            if (changed) {
                mirror.put("houses", houses)
                mirror.put("eggs", eggs)
                prefs.edit().putString(KEY_HOUSE_VISITS, mirror.toString()).apply()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "mergeHouseSnapshot failed: ${e.message}")
        }
    }

    /** Switch A/B del negozio (Unity → app): sistema Huntix o prefabbricato
     *  CC0. Il mirror conserva `{usePrefab, ts}` e fa vincere il timestamp
     *  più recente, esattamente come ShopStyle lato Unity. */
    fun onShopStyleChanged(context: Context, json: String) {
        if (json.isBlank()) return
        try {
            val incoming = JSONObject(json)
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val local = try {
                JSONObject(prefs.getString(KEY_SHOP_STYLE, "{}") ?: "{}")
            } catch (e: Exception) { JSONObject() }
            if (incoming.optLong("ts", 0) > local.optLong("ts", 0)) {
                val value = JSONObject()
                value.put("usePrefab", incoming.optBoolean("usePrefab", false))
                value.put("ts", incoming.optLong("ts", 0))
                prefs.edit().putString(KEY_SHOP_STYLE, value.toString()).apply()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "onShopStyleChanged failed: ${e.message}")
        }
    }

    fun getShopStyle(context: Context): String {
        return try {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getString(KEY_SHOP_STYLE, "{}") ?: "{}"
        } catch (e: Exception) { "{}" }
    }

    /** Unisce lo switch dello snapshot senza mai farlo regredire: vince il
     *  timestamp più alto, anche quando arriva da un restore Android→Unity. */
    private fun mergeShopStyleSnapshot(context: Context, json: String) {
        if (json.isBlank()) return
        try {
            val incoming = JSONObject(json)
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val local = try {
                JSONObject(prefs.getString(KEY_SHOP_STYLE, "{}") ?: "{}")
            } catch (e: Exception) { JSONObject() }
            if (incoming.optLong("ts", 0) > local.optLong("ts", 0)) {
                val value = JSONObject()
                value.put("usePrefab", incoming.optBoolean("usePrefab", false))
                value.put("ts", incoming.optLong("ts", 0))
                prefs.edit().putString(KEY_SHOP_STYLE, value.toString()).apply()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "mergeShopStyleSnapshot failed: ${e.message}")
        }
    }

    // ── eventi sportivi / ambientali ────────────────────────────

    /** Evento sportivo (Unity → app): conserva solo lo stato più recente.
     *  Nessun premio, nessuna valuta: è un mirror di stato e posizione. */
    fun onVenueEvent(context: Context, json: String) {
        if (json.isBlank()) return
        try {
            val incoming = JSONObject(json)
            val ts = incoming.optLong("tsMt", 0L)
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val local = try {
                JSONObject(prefs.getString(KEY_VENUE_EVENT, "{}") ?: "{}")
            } catch (e: Exception) { JSONObject() }
            if (ts <= local.optLong("tsMt", 0L)) return
            val value = JSONObject()
            value.put("kind", incoming.opt("kind")?.toString() ?: "Stadio")
            value.put("phase", incoming.opt("phase")?.toString() ?: "Inattivo")
            value.put("detail", incoming.optString("detail", ""))
            value.put("lat", incoming.optString("lat", "0"))
            value.put("lng", incoming.optString("lng", "0"))
            value.put("tsMt", ts)
            prefs.edit().putString(KEY_VENUE_EVENT, value.toString()).apply()
            AppLog.i(TAG, "evento sportivo: ${value.opt("kind")}/${value.opt("phase")}")
        } catch (e: Exception) {
            AppLog.w(TAG, "onVenueEvent failed: ${e.message}")
        }
    }

    fun getVenueEvent(context: Context): String {
        return try {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getString(KEY_VENUE_EVENT, "{}") ?: "{}"
        } catch (e: Exception) { "{}" }
    }

    /** Lo snapshot usa interi per kind/phase: il merge non li converte e non
     *  distrugge uno stato locale più recente. */
    private fun mergeVenueSnapshot(context: Context, json: String) {
        if (json.isBlank()) return
        try {
            val incoming = JSONObject(json)
            val ts = incoming.optLong("tsMt", 0L)
            if (ts <= 0L) return
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val local = try {
                JSONObject(prefs.getString(KEY_VENUE_EVENT, "{}") ?: "{}")
            } catch (e: Exception) { JSONObject() }
            if (ts > local.optLong("tsMt", 0L)) {
                prefs.edit().putString(KEY_VENUE_EVENT, incoming.toString()).apply()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "mergeVenueSnapshot failed: ${e.message}")
        }
    }

    /** Meteo applicato alla simulazione: ultimo evento noto, con timestamp. */
    fun onEnvironmentalEvent(context: Context, json: String) {
        if (json.isBlank()) return
        try {
            val value = JSONObject(json)
            value.put("ts", System.currentTimeMillis())
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY_ENV_EVENT, value.toString()).apply()
        } catch (e: Exception) {
            AppLog.w(TAG, "onEnvironmentalEvent failed: ${e.message}")
        }
    }

    fun getEnvironmentalEvent(context: Context): String {
        return try {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getString(KEY_ENV_EVENT, "{}") ?: "{}"
        } catch (e: Exception) { "{}" }
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
            // Attività negozi 3D: il mirror Android viene aggiornato anche
            // quando lo snapshot arriva dal restore, non solo dall'evento.
            val shopStates = j.optString("shopStates", "")
            if (shopStates.isNotBlank() && shopStates != getShopActivity(context)) {
                onShopActivity(context, shopStates)
            }
            // Interni universali: mirror dei locali visitati nello snapshot.
            mergeUniversalSnapshot(context, j.optString("universalInteriors", ""))
            // Case visitabili OSM: visite e uova dei nascondigli nello snapshot.
            mergeHouseSnapshot(context, j.optString("houseVisitState", ""))
            // Switch negozio A/B: lo specchio segue il timestamp dello snapshot.
            mergeShopStyleSnapshot(context, j.optString("shopStyle", ""))
            mergeVenueSnapshot(context, j.optString("venueEventState", ""))
            // Casa MiCittà (nome|lat|lng|garage|garagemodel|stanze): il profilo
            // conserva il riepilogo completo per il sync bidirezionale.
            val home = j.optString("home", "")
            if (home.isNotBlank() && profile.cityHome != home) {
                profile.cityHome = home
                changed = true
            }
            // Uova nascoste nei contenitori: unione (mai sostituzione) delle
            // chiavi arrivate da Unity. Il profilo Android le conserva e le
            // restituisce nello snapshot, cosi' Unity non le fa rinascere.
            val containerEggs = j.optString("foundContainerEggs", "")
            if (containerEggs.isNotBlank()) {
                val merged = (profile.cityContainerEggs.split(';') + containerEggs.split(';'))
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(";")
                if (profile.cityContainerEggs != merged) {
                    profile.cityContainerEggs = merged
                    changed = true
                }
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
        val containerEggs = j.optString("foundContainerEggs", "")
            .split(';').count { it.isNotBlank() }
        val ok = people >= 0 || eggDex >= 0 || money >= 0
        val report = JSONObject()
        report.put("snapshot", true)
        report.put("ts", ts)
        report.put("peopleKnown", people)
        report.put("eggDex", eggDex)
        report.put("containerEggs", containerEggs)
        report.put("shopStates", j.optString("shopStates", "").isNotBlank())
        report.put("shopActivityMirror", getShopActivity(context).isNotBlank())
        report.put("universalInteriors", try {
            JSONObject(getUniversalInteriors(context)).length()
        } catch (e: Exception) { 0 })
        report.put("universalInteriorsSnapshot",
            j.optString("universalInteriors", "").isNotBlank())
        report.put("houseVisits", try {
            JSONObject(getHouseVisits(context)).optJSONObject("houses")?.length() ?: 0
        } catch (e: Exception) { 0 })
        report.put("houseEggs", try {
            JSONObject(getHouseVisits(context)).optJSONArray("eggs")?.length() ?: 0
        } catch (e: Exception) { 0 })
        report.put("houseVisitSnapshot", j.optString("houseVisitState", "").isNotBlank())
        report.put("houseTargets", try {
            getHouseTargets(context).length()
        } catch (e: Exception) { 0 })
        report.put("houseTargetSnapshot",
            JSONObject(getSnapshot(context)).optJSONArray("houseTargets") != null)
        report.put("shopStyle", try {
            JSONObject(getShopStyle(context)).optBoolean("usePrefab", false)
        } catch (e: Exception) { false })
        report.put("shopStyleSnapshot", j.optString("shopStyle", "").isNotBlank())
        report.put("venueEvent", try {
            JSONObject(getVenueEvent(context)).optString("phase", "")
        } catch (e: Exception) { "" })
        report.put("venueEventSnapshot", j.optString("venueEventState", "").isNotBlank())
        report.put("money", money)
        report.put("profilePeople", PlayerProfileManager.myProfile?.cityPeopleKnown ?: 0)
        report.put("profileEggDex", PlayerProfileManager.myProfile?.cityEggDexCount ?: 0)
        report.put("profileContainerEggs", PlayerProfileManager.myProfile?.cityContainerEggs
            ?.split(';')?.count { it.isNotBlank() } ?: 0)
        report.put("ok", ok)
        AppLog.i(TAG, "checkSync: $report")
        return report.toString()
    }
}