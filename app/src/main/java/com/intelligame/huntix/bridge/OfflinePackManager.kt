package com.intelligame.huntix.bridge

import android.content.Context
import com.intelligame.huntix.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Gestore dei pacchetti offline di MiCittà.
 *
 * Un pacchetto e' un insieme di chiavi tile (una citta' di spawn oppure
 * l'intera Italia). Il manifest viene generato da
 * backend/preprocessing/offline_pack_manifest.py e spedito come asset.
 *
 * Il flag dell'utente ("voglio questa citta' offline") e' separato dallo stato
 * REALE, che si controlla sui file nella cache v5 di TileClient: se l'utente
 * cancella i dati dell'app il flag resta ma il pacchetto risulta da scaricare.
 */
object OfflinePackManager {

    private const val TAG = "OfflinePackManager"
    private const val ASSET = "offline_packs.json"
    private const val PREF_FILE = "world_game_prefs"
    private const val KEY_STATE = "city_offline_packs_v1"
    private const val WORLD_ID = "italia"

    data class Pack(
        val id: String,
        val label: String,
        val kind: String,
        val bbox: List<Double>,
        val keys: List<String>,
        val bytes: Long,
        val installedBytes: Long
    )

    private data class State(
        val flagged: MutableSet<String> = LinkedHashSet(),
        val declined: MutableSet<String> = LinkedHashSet(),
        val installedAt: MutableMap<String, Long> = LinkedHashMap()
    )

    private val stateLock = Any()
    private var cachedState: State? = null
    private var cachedPacks: List<Pack>? = null

    @Volatile
    private var downloading = false

    fun isDownloading(): Boolean = downloading

    // ── Manifest ──────────────────────────────────────────────────────────

    private fun parseManifest(ctx: Context): List<Pack> {
        cachedPacks?.let { return it }
        val parsed = try {
            ctx.assets.open(ASSET).use { it.bufferedReader().readText() }
        } catch (e: Exception) {
            AppLog.w(TAG, "manifest offline mancante: ${e.message}")
            return emptyList()
        }
        val packs = ArrayList<Pack>()
        try {
            val root = JSONObject(parsed)
            val arr = root.optJSONArray("packs") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val keys = ArrayList<String>()
                o.optJSONArray("keys")?.let { ka ->
                    for (j in 0 until ka.length()) keys.add(ka.getString(j))
                }
                val bb = ArrayList<Double>()
                o.optJSONArray("bbox")?.let { ba ->
                    for (j in 0 until ba.length()) bb.add(ba.getDouble(j))
                }
                packs.add(Pack(
                    id = o.getString("id"),
                    label = o.getString("label"),
                    kind = o.optString("kind", "city"),
                    bbox = bb,
                    keys = keys,
                    bytes = o.optLong("bytes", 0L),
                    installedBytes = o.optLong("installedBytes", 0L)
                ))
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "manifest offline non valido: ${e.message}")
            return emptyList()
        }
        cachedPacks = packs
        return packs
    }

    fun packs(ctx: Context): List<Pack> = parseManifest(ctx)

    fun pack(ctx: Context, id: String): Pack? = packs(ctx).firstOrNull { it.id == id }

    /** Pacchetto citta' che contiene la coordinata di spawn. "Tutta Italia" e'
     *  gestita a parte da [isFlagged]: copre qualunque coordinata. */
    fun packForLocation(ctx: Context, lat: Double, lng: Double): Pack? {
        val cities = packs(ctx).filter { it.kind != "world" && it.bbox.size == 4 }
        var best: Pack? = null
        var bestArea = Double.MAX_VALUE
        for (p in cities) {
            val b = p.bbox
            if (lat < b[0] || lat > b[2] || lng < b[1] || lng > b[3]) continue
            val area = (b[2] - b[0]) * (b[3] - b[1])
            if (area < bestArea) { bestArea = area; best = p }
        }
        if (best != null) return best

        // Fuori da ogni provincia (mare, confine): citta' piu' vicina entro ~60 km.
        var nearest: Pack? = null
        var nearestD2 = 0.6 * 0.6
        for (p in cities) {
            val b = p.bbox
            val clat = (b[0] + b[2]) / 2.0
            val clng = (b[1] + b[3]) / 2.0
            val d2 = (lat - clat) * (lat - clat) + (lng - clng) * (lng - clng)
            if (d2 < nearestD2) { nearestD2 = d2; nearest = p }
        }
        return nearest
    }

    // ── Stato persistente (flag utente) ───────────────────────────────────

    private fun loadState(ctx: Context): State {
        cachedState?.let { return it }
        val s = State()
        try {
            val raw = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getString(KEY_STATE, null)
            if (!raw.isNullOrEmpty()) {
                val o = JSONObject(raw)
                o.optJSONArray("flagged")?.let { a ->
                    for (i in 0 until a.length()) s.flagged.add(a.getString(i))
                }
                o.optJSONArray("declined")?.let { a ->
                    for (i in 0 until a.length()) s.declined.add(a.getString(i))
                }
                o.optJSONObject("installedAt")?.let { m ->
                    val it = m.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        s.installedAt[k] = m.getLong(k)
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "stato offline non valido, azzerato: ${e.message}")
        }
        cachedState = s
        return s
    }

    private fun saveState(ctx: Context, s: State) {
        try {
            val o = JSONObject()
                .put("flagged", JSONArray(s.flagged.sorted()))
                .put("declined", JSONArray(s.declined.sorted()))
            val dates = JSONObject()
            for ((k, v) in s.installedAt) dates.put(k, v)
            o.put("installedAt", dates)
            ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit()
                .putString(KEY_STATE, o.toString()).apply()
            cachedState = s
        } catch (e: Exception) {
            AppLog.w(TAG, "salvataggio stato offline fallito: ${e.message}")
        }
    }

    /** True se l'utente vuole offline quella citta' OPPURE tutta Italia. */
    fun isFlagged(ctx: Context, id: String): Boolean = synchronized(stateLock) {
        val s = loadState(ctx)
        s.flagged.contains(WORLD_ID) || s.flagged.contains(id)
    }

    fun setFlag(ctx: Context, id: String, enabled: Boolean) {
        synchronized(stateLock) {
            val s = loadState(ctx)
            if (enabled) {
                s.flagged.add(id)
                s.declined.remove(id)
            } else {
                s.flagged.remove(id)
            }
            saveState(ctx, s)
        }
    }

    fun flaggedIds(ctx: Context): Set<String> = synchronized(stateLock) {
        HashSet(loadState(ctx).flagged)
    }

    fun isDeclined(ctx: Context, id: String): Boolean = synchronized(stateLock) {
        loadState(ctx).declined.contains(id)
    }

    fun markDeclined(ctx: Context, id: String, declined: Boolean) {
        synchronized(stateLock) {
            val s = loadState(ctx)
            if (declined) s.declined.add(id) else s.declined.remove(id)
            saveState(ctx, s)
        }
    }

    // ── Stato reale sul disco ─────────────────────────────────────────────

    /** Chiavi del pacchetto che non hanno ancora grafo o geo nella cache v5. */
    fun missingKeys(ctx: Context, p: Pack): List<String> {
        val out = ArrayList<String>()
        for (key in p.keys) {
            val graph = CityTilePreloader.cachedFile(ctx, key, "graph")
            val geo = CityTilePreloader.cachedFile(ctx, key, "geo")
            if (!graph.exists() || graph.length() == 0L ||
                !geo.exists() || geo.length() == 0L) out.add(key)
        }
        return out
    }

    fun isInstalled(ctx: Context, p: Pack): Boolean = missingKeys(ctx, p).isEmpty()

    fun installedCount(ctx: Context): Int {
        var n = 0
        for (p in packs(ctx)) if (p.kind != "world" && isInstalled(ctx, p)) n++
        return n
    }

    // ── Download ─────────────────────────────────────────────────────────

    /**
     * Scarica le tile mancanti di uno o piu' pacchetti. Le chiavi vengono
     * unificate: una tile condivisa fra province viene scaricata una volta.
     * Scrive nella stessa cache del preload 3x3 (v5), senza toccare Unity.
     */
    fun downloadPacks(
        ctx: Context,
        ids: Collection<String>,
        onPhase: (fase: String, current: Int, total: Int, bytes: Long) -> Unit
    ): Boolean {
        if (downloading) return false
        val selected = ids.mapNotNull { pack(ctx, it) }
        if (selected.isEmpty()) return true
        val missingByPack = selected.associateWith { missingKeys(ctx, it) }
        val keys = LinkedHashSet<String>()
        for (list in missingByPack.values) keys.addAll(list)
        if (keys.isEmpty()) {
            onPhase("done", 0, 0, 0L)
            return true
        }

        // Controllo spazio indicativo: il JSON non compresso e' ~4x il .gz.
        val needed = missingByPack.entries.sumOf { (p, miss) ->
            (miss.size.toDouble() /
                p.keys.size.coerceAtLeast(1) * p.installedBytes).toLong()
        }
        val usable = try { CityTilePreloader.cacheDir(ctx).usableSpace } catch (_: Exception) { Long.MAX_VALUE }
        if (needed > usable * 0.9) {
            AppLog.w(TAG, "spazio insufficiente: servono ~${needed / 1_000_000} MB")
            throw IllegalStateException("Spazio insufficiente sul dispositivo: servono circa ${needed / 1_000_000} MB")
        }

        downloading = true
        return try {
            val ok = CityTilePreloader.downloadKeysSync(ctx, keys.toList(), onPhase)
            if (ok) {
                synchronized(stateLock) {
                    val s = loadState(ctx)
                    for (p in selected) s.installedAt[p.id] = System.currentTimeMillis()
                    saveState(ctx, s)
                }
                // Ponte Android -> Unity: la scena City puo' aggiornare subito
                // l'indicatore "offline" senza riavviare il gioco.
                try {
                    StoreUnityBridge.sendToUnityIfAlive(
                        "GameManager", "OnOfflinePackChanged", statusJson(ctx))
                } catch (e: Exception) {
                    AppLog.w(TAG, "notifica Unity non riuscita: ${e.message}")
                }
            }
            ok
        } finally {
            downloading = false
        }
    }

    fun downloadPack(
        ctx: Context,
        id: String,
        onPhase: (fase: String, current: Int, total: Int, bytes: Long) -> Unit
    ): Boolean = downloadPacks(ctx, listOf(id), onPhase)

    // ── Ponte con Unity ──────────────────────────────────────────────────

    /** Snapshot JSON letto da Unity tramite StoreUnityBridge.getOfflinePackStatus. */
    fun statusJson(ctx: Context): String {
        val root = JSONObject()
        root.put("cacheVersion", 5)
        root.put("downloading", downloading)
        root.put("flagged", JSONArray(flaggedIds(ctx).sorted()))
        val arr = JSONArray()
        for (p in packs(ctx)) {
            val miss = missingKeys(ctx, p).size
            arr.put(JSONObject()
                .put("id", p.id)
                .put("label", p.label)
                .put("kind", p.kind)
                .put("keys", p.keys.size)
                .put("bytes", p.bytes)
                .put("missing", miss)
                .put("installed", miss == 0)
                .put("flagged", isFlagged(ctx, p.id)))
        }
        root.put("packs", arr)
        return root.toString()
    }
}
