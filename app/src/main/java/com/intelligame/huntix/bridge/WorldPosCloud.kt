package com.intelligame.huntix.bridge

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.intelligame.huntix.PlayerProfileManager

/**
 * Posizione di gioco di Miacitta: persistenza locale (world_game_prefs) +
 * cloud su Google (Firestore, documento "players/{uid}"). Regole:
 *  - Unity invia "PlayerPosition" ogni volta che il player si sposta (throttle
 *    interno): qui si scrive worldPos/gpsLat/gpsLng in prefs e, se autenticati,
 *    si aggiorna anche Firestore (cosi' la posizione sopravvive a reinstall).
 *  - Prima del selettore, pull() scarica la posizione cloud piu' recente.
 *  - Il GPS del dispositivo non viene MAI usato come posizione di spawn.
 */
object WorldPosCloud {

    private const val TAG = "WorldPosCloud"

    private const val PREF_FILE = "world_game_prefs"
    private const val KEY_WORLD_POS = "worldPos"
    private const val KEY_GPS_LAT = "gpsLat"
    private const val KEY_GPS_LNG = "gpsLng"

    // Scelta dello spawn fatta dal selettore (valida per la sessione corrente
    // e riusata come default all'apertura successiva del selettore).
    private const val KEY_SPAWN_MODE = "spawn_mode"        // "city" | "last"
    private const val KEY_SPAWN_NAME = "spawn_city_name"   // nome citta' o "Ultima posizione"
    private const val KEY_SPAWN_LAT  = "spawnLat"
    private const val KEY_SPAWN_LNG  = "spawnLng"

    private val CITIES =
        // nome, lat, lng
        arrayOf(
            arrayOf("Roma", "41.9028", "12.4964"),
            arrayOf("Milano", "45.4642", "9.1900"),
            arrayOf("Napoli", "40.8518", "14.2681"),
            arrayOf("Torino", "45.0703", "7.6869"),
            arrayOf("Palermo", "38.1157", "13.3615"),
            arrayOf("Genova", "44.4056", "8.9463"),
            arrayOf("Bologna", "44.4949", "11.3426"),
            arrayOf("Firenze", "43.7696", "11.2558"),
            arrayOf("Bari", "41.1171", "16.8719"),
            arrayOf("Venezia", "45.4408", "12.3155"),
            arrayOf("Verona", "45.4384", "10.9916"),
            arrayOf("Catania", "37.5079", "15.0830"),
            arrayOf("Cagliari", "39.2238", "9.1217"),
            arrayOf("Perugia", "43.1107", "12.3908"),
            arrayOf("Pescara", "42.4618", "14.2160"),
            arrayOf("Ancona", "43.6158", "13.5189"),
            arrayOf("Reggio Calabria", "38.1106", "15.6613"),
            arrayOf("Trento", "46.0748", "11.1217"),
            arrayOf("Trieste", "45.6495", "13.7768"),
            arrayOf("Pisa", "43.7228", "10.4017"),
            arrayOf("Udine", "46.0711", "13.2346"),
            arrayOf("Lecce", "40.3515", "18.1750"),
            arrayOf("Messina", "38.1938", "15.5540"),
            arrayOf("Brescia", "45.5416", "10.2118"),
            arrayOf("Modena", "44.6471", "10.9252"),
            arrayOf("Padova", "45.4064", "11.8768"),
            arrayOf("Parma", "44.8015", "10.3279"),
            arrayOf("Bergamo", "45.6983", "9.6773"),
            arrayOf("Salerno", "40.6820", "14.7681"),
            arrayOf("Como", "45.8081", "9.0852"),
            arrayOf("Livorno", "43.5485", "10.3098"),
            arrayOf("Foggia", "41.4623", "15.5431"),
            arrayOf("Taranto", "40.4644", "17.2470"),
            arrayOf("Rimini", "44.0678", "12.5695"),
            arrayOf("Pistoia", "43.9333", "10.9167"),
            arrayOf("Aosta", "45.7350", "7.3134"),
            arrayOf("Bolzano", "46.4983", "11.3548"),
            arrayOf("L'Aquila", "42.3540", "13.3920")
        )

    data class City(val name: String, val lat: Double, val lng: Double)

    val cities: List<City> by lazy {
        CITIES.map { c ->
            City(c[0], c[1].toDouble(), c[2].toDouble())
        }
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    /** Chiave documento Firestore: UID Firebase o playerId locale. */
    private fun docRef(uid: String) =
        FirebaseFirestore.getInstance().collection("players").document(uid)

    private fun authenticatedUid(ctx: Context): String? {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (!uid.isNullOrBlank()) return uid
        // fallback: playerId locale usato per il profilo Firestore
        prefs(ctx).getString("world_player_id", null)?.takeIf { it.isNotBlank() }?.let { return it }
        val profile = PlayerProfileManager.myProfile
        profile?.firebaseUid?.takeIf { it.isNotBlank() }?.let { return it }
        return profile?.playerId?.takeIf { it.isNotBlank() }
    }

    /** true se esiste una posizione di gioco salvata (locale o cloud residua). */
    fun hasLastPosition(ctx: Context): Boolean {
        val p = prefs(ctx)
        val wp = p.getString(KEY_WORLD_POS, null)
        if (!wp.isNullOrEmpty() && wp.split(",").size == 2) return true
        if (Math.abs(p.getFloat(KEY_GPS_LAT, 0f)) > 0.001f &&
            Math.abs(p.getFloat(KEY_GPS_LNG, 0f)) > 0.001f) return true
        return p.getString(KEY_SPAWN_MODE, null) == "last"
    }

    /** Coordinate dell'ultima posizione di gioco, se disponibile. */
    fun lastPosition(ctx: Context): Pair<Double, Double>? {
        val p = prefs(ctx)
        val wp = p.getString(KEY_WORLD_POS, null)
        if (!wp.isNullOrEmpty()) {
            val parts = wp.split(",")
            if (parts.size == 2) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                if (lat != null && lng != null && Math.abs(lat) > 0.001 && Math.abs(lng) > 0.001)
                    return Pair(lat, lng)
            }
        }
        val glat = p.getFloat(KEY_GPS_LAT, 0f).toDouble()
        val glng = p.getFloat(KEY_GPS_LNG, 0f).toDouble()
        if (Math.abs(glat) > 0.001 && Math.abs(glng) > 0.001)
            return Pair(glat, glng)
        return null
    }

    /** Scelta di spawn corrente (per il preloader e il JSON a Unity). */
    fun currentSpawnChoice(ctx: Context): Pair<Double, Double> {
        val p = prefs(ctx)
        val slat = p.getFloat(KEY_SPAWN_LAT, 0f).toDouble()
        val slng = p.getFloat(KEY_SPAWN_LNG, 0f).toDouble()
        if (Math.abs(slat) > 0.001 && Math.abs(slng) > 0.001)
            return Pair(slat, slng)
        lastPosition(ctx)?.let { return it }
        return Pair(41.9028, 12.4964) // default Roma
    }

    /** Salva la scelta del selettore e la riusa come default. */
    fun saveSpawnChoice(ctx: Context, mode: String, name: String, lat: Double, lng: Double) {
        prefs(ctx).edit()
            .putString(KEY_SPAWN_MODE, mode)
            .putString(KEY_SPAWN_NAME, name)
            .putFloat(KEY_SPAWN_LAT, lat.toFloat())
            .putFloat(KEY_SPAWN_LNG, lng.toFloat())
            .apply()
        Log.d(TAG, "spawn scelto: $mode $name ($lat,$lng)")
    }

    fun lastSpawnMode(ctx: Context): String = prefs(ctx).getString(KEY_SPAWN_MODE, "city") ?: "city"
    fun lastSpawnName(ctx: Context): String = prefs(ctx).getString(KEY_SPAWN_NAME, "Roma") ?: "Roma"

    /**
     * Unity ("PlayerPosition") ha aggiornato la posizione di gioco: la salva in
     * prefs (fonte per mondo reale/preloader) e, se autenticati, su Firestore.
     */
    fun updateLocalAndCloud(ctx: Context, lat: Double, lng: Double) {
        val clampedLat = lat.coerceIn(-90.0, 90.0)
        val clampedLng = lng.coerceIn(-180.0, 180.0)
        prefs(ctx).edit()
            .putString(KEY_WORLD_POS, "$clampedLat,$clampedLng")
            .putFloat(KEY_GPS_LAT, clampedLat.toFloat())
            .putFloat(KEY_GPS_LNG, clampedLng.toFloat())
            .apply()
        val uid = authenticatedUid(ctx) ?: return
        try {
            docRef(uid).set(
                mapOf(
                    "gpsLat" to clampedLat,
                    "gpsLng" to clampedLng,
                    "lastSeen" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).addOnFailureListener { e -> Log.w(TAG, "cloud posizione non salvata: ${e.message}") }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore non disponibile per la posizione: ${e.message}")
        }
    }

    /**
     * Scarica l'ultima posizione salvata su Firestore (se autenticati) e la
     * riporta in prefs, cosi' il selettore puo' offrire "ultima posizione".
     */
    fun pull(ctx: Context, onDone: (() -> Unit)? = null) {
        val uid = authenticatedUid(ctx) ?: run { onDone?.invoke(); return }
        try {
            docRef(uid).get().addOnSuccessListener { snap ->
                if (!snap.exists()) { onDone?.invoke(); return@addOnSuccessListener }
                val lat = (snap.get("gpsLat") as? Number)?.toDouble()
                val lng = (snap.get("gpsLng") as? Number)?.toDouble()
                if (lat != null && lng != null && Math.abs(lat) > 0.001 && Math.abs(lng) > 0.001) {
                    prefs(ctx).edit()
                        .putString(KEY_WORLD_POS, "$lat,$lng")
                        .putFloat(KEY_GPS_LAT, lat.toFloat())
                        .putFloat(KEY_GPS_LNG, lng.toFloat())
                        .apply()
                    Log.d(TAG, "posizione cloud ripristinata: ($lat,$lng)")
                }
                onDone?.invoke()
            }.addOnFailureListener { e ->
                Log.w(TAG, "pull posizione cloud fallito: ${e.message}")
                onDone?.invoke()
            }
        } catch (e: Exception) {
            Log.w(TAG, "pull posizione cloud fallito: ${e.message}")
            onDone?.invoke()
        }
    }
}