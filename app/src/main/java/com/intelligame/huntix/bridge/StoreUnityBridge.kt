package com.intelligame.huntix.bridge

import android.content.Intent
import android.net.Uri
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.ui.IndoorActivity
import com.intelligame.huntix.ui.POICustomPageActivity
import com.intelligame.huntix.managers.CustomPageRegistry
import com.intelligame.huntix.managers.PoiSearchManager
import com.intelligame.huntix.reallife.OsmClient
import com.intelligame.huntix.reallife.OsmPoiRepository
import com.intelligame.huntix.reallife.PoiJsonFactory
import com.unity3d.player.UnityPlayer
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * StoreUnityBridge — ponte chiamato DALLO store Unity.
 *
 * I metodi sono statici (@JvmStatic) così il codice C# può invocarli
 * via `AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge")`.
 */
object StoreUnityBridge {

    private const val TAG = "StoreUnityBridge"

    private const val MAX_POIS = 1200

    /**
     * Fasi progressive del caricamento Esplora: prima i negozi più vicini
     * (query Overpass veloce) e poi si espande la zona. Ogni fase arriva a
     * Unity come aggiornamento parziale (envelope con `done=false`); l'ultima
     * chiude con `done=true` e nasconde la barra di caricamento in Unity.
     */
    private val POI_STAGES_M = intArrayOf(1000, 3000, 10000)

    /** Cache POI a livello di processo: stessa zona recente → risposta immediata senza Overpass. */
    private const val POI_CACHE_MAX_AGE_MS = 10 * 60 * 1000L
    private const val POI_CACHE_MAX_DIST_M = 2000.0
    private var poiCacheCenterLat = 0.0
    private var poiCacheCenterLng = 0.0
    private var poiCacheTime = 0L
    private var poiCacheEnvelope: String? = null

    /** Chiamato da Unity quando la scena del negozio è pronta. */
    @JvmStatic
    fun onIndoorSceneReady(poiId: String) {
        IndoorActivity.instance?.onIndoorSceneReady(poiId)
    }

    /** Chiamato da Unity per uscire dal negozio. */
    @JvmStatic
    fun exitIndoor() {
        IndoorActivity.instance?.runOnUiThread {
            IndoorActivity.instance?.finish()
        }
    }

    /** Restituisce il JSON del POI corrente. */
    @JvmStatic
    fun getPoiData(): String = IndoorActivity.instance?.poiJson ?: "{}"

    /** Chiamato da Unity quando un oggetto interactable è in range. */
    @JvmStatic
    fun onInteractableFound(json: String) {
        IndoorActivity.instance?.onInteractableFound(json)
    }

    /** Chiamato da Unity quando l'interazione è completata. */
    @JvmStatic
    fun onInteractionResult(json: String) {
        IndoorActivity.instance?.onInteractionResult(json)
    }

    /** Chiamato da Unity quando un NPC è vicino. */
    @JvmStatic
    fun onNPCNearby(json: String) {
        IndoorActivity.instance?.onNPCNearby(json)
    }

    /** Chiamato da Unity quando un NPC parla. */
    @JvmStatic
    fun onNPCDialogue(json: String) {
        IndoorActivity.instance?.onNPCDialogue(json)
    }

    /** Chiamato da Unity quando il giocatore si allontana da un NPC. */
    @JvmStatic
    fun onNPCFar(json: String) {
        IndoorActivity.instance?.onNPCFar(json)
    }

    /** Chiamato da Unity quando un NPC è lontano (outdoor). */
    @JvmStatic
    fun onOutdoorNPCFar(json: String) {
        val ctx = UnityPlayer.currentActivity ?: return
        ctx.runOnUiThread {
            val name = extractJsonField(json, "name") ?: "La guida"
            android.widget.Toast.makeText(ctx, "$name si allontana", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** Chiamato da Unity quando un NPC parla (outdoor) — mostra dialogo come toast lungo. */
    @JvmStatic
    fun onOutdoorNPCDialogue(json: String) {
        val ctx = UnityPlayer.currentActivity ?: return
        val name = extractJsonField(json, "name") ?: "Guida"
        val dialogue = extractJsonField(json, "dialogue") ?: ""
        val poiName = extractJsonField(json, "poiName") ?: ""
        ctx.runOnUiThread {
            val msg = if (poiName.isNotEmpty()) "$name: $dialogue" else "$name dice: $dialogue"
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** Chiamato da Unity quando un NPC è vicino (outdoor) — mostra notifica breve. */
    @JvmStatic
    fun onOutdoorNPCNearby(json: String) {
        val ctx = UnityPlayer.currentActivity ?: return
        val name = extractJsonField(json, "name") ?: "La guida"
        val emoji = extractJsonField(json, "emoji") ?: ""
        val poiName = extractJsonField(json, "poiName") ?: ""
        val msg = if (poiName.isNotEmpty()) "$emoji  $name — vicino a $poiName" else "$emoji  $name"
        ctx.runOnUiThread {
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** Chiamato da Unity per info su un NPC/POI (outdoor). */
    @JvmStatic
    fun onOutdoorNPCInfo(json: String) {
        val ctx = UnityPlayer.currentActivity ?: return
        val poiName = extractJsonField(json, "poiName") ?: "luogo"
        val category = extractJsonField(json, "category") ?: ""
        val msg = "📍  Info su: $poiName${if (category.isNotEmpty()) " ($category)" else ""}"
        ctx.runOnUiThread {
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun extractJsonField(json: String, key: String): String? =
        "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex().find(json)?.groupValues?.get(1)

    // ── Esplora (Unity) ─────────────────────────────────────────────
    /** Posizione corrente reale/mock, JSON {"lat":..,"lng":..,"mock":bool}. */
    @JvmStatic
    fun getCurrentLocation(): String =
        com.intelligame.huntix.bridge.Bridge.getCurrentLocation()

    /** Bisogni locali (LocalNeeds) come JSON: {"hunger":..,"sleep":..,"hygiene":..,"fun":..,"thirst":..}.
     *  Usato dall'HUD bisogni di Esplora per mostrare le barre. */
    @JvmStatic
    fun getNeedsJson(): String {
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: return "{}"
        return needsJson(com.intelligame.huntix.reallife.LocalNeeds.load(ctx))
    }

    /** Applica un'azione ai bisogni locali (needKey + gain) e restituisce il JSON aggiornato. */
    @JvmStatic
    fun applyNeedAction(needKey: String, gain: Float): String {
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: return "{}"
        return needsJson(com.intelligame.huntix.reallife.LocalNeeds.applyAction(ctx, needKey, gain))
    }

    private fun needsJson(needs: Map<String, Float>): String {
        val jo = JSONObject()
        for ((k, v) in needs) jo.put(k, v.toDouble())
        return jo.toString()
    }

    /** Richiede i POI OSM entro [radiusMeters] (max 10 km) da (lat,lng).
     *  Il risultato arriva in Unity via UnitySendMessage("GameManager","OnPoisReceived",json).
     *  I POI vengono ordinati per distanza e limitati (1200) per non saturare Unity.
     *
     *  Accelerazione: caricamento a fasi (1 km → 3 km → 10 km): la prima fase
     *  arriva in pochi secondi così i negozi vicini compaiono subito, mentre le
     *  fasi successive espandono la zona in background. Ogni fase manda anche
     *  "OnPoisProgress" per aggiornare la barra di caricamento in Unity.
     *  Se la stessa zona è già in cache (memoria ≤10 min e ≤2 km, o disco ≤24h),
     *  risponde subito senza rifare la query Overpass. */
    @JvmStatic
    fun requestPoisNearby(lat: Double, lng: Double, radiusMeters: Int) {
        val activity = UnityPlayer.currentActivity ?: return
        val ctx = activity.applicationContext

        // Cache in-memory: stessa zona recente → risposta immediata (niente Overpass)
        val cached = poiCacheEnvelope
        if (cached != null &&
            System.currentTimeMillis() - poiCacheTime < POI_CACHE_MAX_AGE_MS &&
            distanceMeters(lat, lng, poiCacheCenterLat, poiCacheCenterLng) <= POI_CACHE_MAX_DIST_M) {
            AppLog.d(TAG, "requestPoisNearby: memory cache HIT (${poiCacheCenterLat},${poiCacheCenterLng})")
            UnityPlayer.UnitySendMessage("GameManager", "OnPoisReceived", cached)
            return
        }

        CustomPageRegistry.init(ctx)
        Thread {
            var lastPois: List<PoiSearchManager.SearchResult>? = null
            var lastEnvelope: String? = null
            try {
                OsmClient.init(ctx)
                val fullRadius = POI_STAGES_M.last()

                // Area completa già in cache disco → un solo fetch (quasi istantaneo)
                if (OsmClient.hasPoisCache(lat, lng, fullRadius)) {
                    AppLog.d(TAG, "requestPoisNearby: disk cache HIT per raggio $fullRadius m")
                    val pois = OsmPoiRepository.loadNearby(lat, lng, fullRadius)
                        .sortedBy { distanceMeters(lat, lng, it.lat, it.lng) }
                        .take(MAX_POIS)
                    val wrapper = buildEnvelope(pois, lat, lng, fullRadius,
                        POI_STAGES_M.size, POI_STAGES_M.size, done = true)
                    poiCacheCenterLat = lat
                    poiCacheCenterLng = lng
                    poiCacheTime = System.currentTimeMillis()
                    poiCacheEnvelope = wrapper
                    UnityPlayer.UnitySendMessage("GameManager", "OnPoisReceived", wrapper)
                    return@Thread
                }

                // Caricamento progressivo: prima i vicini, poi si espande
                for (i in POI_STAGES_M.indices) {
                    val radius = POI_STAGES_M[i]
                    val stage = i + 1
                    AppLog.d(TAG, "requestPoisNearby: stage $stage/${POI_STAGES_M.size} raggio=${radius}m")
                    sendProgress(stage, POI_STAGES_M.size, radius)

                    val pois = OsmPoiRepository.loadNearby(lat, lng, radius)
                        .sortedBy { distanceMeters(lat, lng, it.lat, it.lng) }
                        .take(MAX_POIS)
                    lastPois = pois

                    val done = stage == POI_STAGES_M.size
                    lastEnvelope = buildEnvelope(pois, lat, lng, radius, stage, POI_STAGES_M.size, done)
                    UnityPlayer.UnitySendMessage("GameManager", "OnPoisReceived", lastEnvelope)

                    if (done) break
                    // Pausa breve tra le fasi per non stressare i mirror Overpass.
                    Thread.sleep(300)
                }

                poiCacheCenterLat = lat
                poiCacheCenterLng = lng
                poiCacheTime = System.currentTimeMillis()
                poiCacheEnvelope = lastEnvelope
            } catch (e: Exception) {
                AppLog.w(TAG, "requestPoisNearby failed: ${e.message}")
                if (lastEnvelope != null && lastPois != null) {
                    // Le fasi già consegnate bastano: chiudi segnalando done=true
                    val doneEnv = buildEnvelope(lastPois, lat, lng,
                        POI_STAGES_M.last(), POI_STAGES_M.size, POI_STAGES_M.size, done = true)
                    UnityPlayer.UnitySendMessage("GameManager", "OnPoisReceived", doneEnv)
                } else {
                    val mini = miniChunkEnvelope()
                    if (mini != null) {
                        UnityPlayer.UnitySendMessage("GameManager", "OnPoisReceived", mini)
                    } else {
                        UnityPlayer.UnitySendMessage("GameManager", "OnPoisFailed", e.message ?: "error")
                    }
                }
            }
        }.start()
    }

    /** Invia l'aggiornamento di avanzamento a Unity (barra di caricamento Esplora). */
    private fun sendProgress(stage: Int, stages: Int, radiusMeters: Int) {
        val jo = JSONObject()
        jo.put("stage", stage)
        jo.put("stages", stages)
        jo.put("radiusMeters", radiusMeters)
        UnityPlayer.UnitySendMessage("GameManager", "OnPoisProgress", jo.toString())
    }

    /** Costruisce l'envelope JSON {"pois":[...],"count":N,"centerLat":..,"centerLng":..,"done":bool}. */
    private fun buildEnvelope(
        pois: List<PoiSearchManager.SearchResult>,
        lat: Double,
        lng: Double,
        radiusMeters: Int = 0,
        stage: Int = 1,
        stages: Int = 1,
        done: Boolean = true
    ): String {
        val arr = JSONArray()
        for (p in pois) {
            val custom = CustomPageRegistry.resolve(p.id)
            val jo = JSONObject()
            jo.put("id", p.id)
            jo.put("name", p.name)
            jo.put("lat", p.lat)
            jo.put("lng", p.lng)
            jo.put("buildingType", p.buildingType)
            jo.put("poiType", p.poiType)
            jo.put("category", p.category)
            jo.put("emoji", PoiJsonFactory.emojiFor(p.buildingType, p.poiType, p.category))
            if (custom != null) {
                jo.put("pageType", custom.pageType)
                jo.put("url", custom.url)
            } else {
                jo.put("pageType", p.pageType)
                jo.put("url", p.url)
            }
            jo.put("hasCustom", custom != null)
            arr.put(jo)
        }
        val wrapper = JSONObject()
        wrapper.put("pois", arr)
        wrapper.put("count", pois.size)
        wrapper.put("centerLat", lat)
        wrapper.put("centerLng", lng)
        wrapper.put("radiusMeters", radiusMeters)
        wrapper.put("stage", stage)
        wrapper.put("stages", stages)
        wrapper.put("done", done)
        return wrapper.toString()
    }

    /** Fallback offline: mini-chunk pre-inserito nell'APK (se Overpass fallisce). */
    private fun miniChunkEnvelope(): String? {
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: return null
        try {
            OsmClient.init(ctx)
            val data = OsmClient.loadMiniChunk() ?: return null
            val pois = OsmPoiRepository.classify(data)
            if (pois.isEmpty()) return null
            val cLat = (data.south + data.north) / 2
            val cLng = (data.west + data.east) / 2
            val sorted = pois
                .sortedBy { distanceMeters(cLat, cLng, it.lat, it.lng) }
                .take(MAX_POIS)
            AppLog.d(TAG, "requestPoisNearby: fallback mini-chunk (${sorted.size} POIs)")
            return buildEnvelope(sorted, cLat, cLng)
        } catch (e: Exception) {
            AppLog.w(TAG, "miniChunkEnvelope failed: ${e.message}")
            return null
        }
    }

    /** Apre la pagina del POI da Unity (custom JSON / web / JSON sintetico OSM). */
    @JvmStatic
    fun openPoiPage(osmId: String, name: String, buildingType: String, poiType: String,
                    url: String, pageType: String, lat: Double, lng: Double, category: String) {
        val activity = UnityPlayer.currentActivity ?: return
        try {
            val custom = CustomPageRegistry.resolve(osmId)
            val intent = if (custom != null) {
                Intent(activity, POICustomPageActivity::class.java).apply {
                    putExtra(POICustomPageActivity.EXTRA_JSON_URL, custom.url)
                    putExtra(POICustomPageActivity.EXTRA_POI_NAME, name)
                    putExtra(POICustomPageActivity.EXTRA_POI_TYPE, poiType)
                }
            } else if (url.isNotBlank() && !url.startsWith("osm:")) {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            } else {
                Intent(activity, POICustomPageActivity::class.java).apply {
                    putExtra(POICustomPageActivity.EXTRA_JSON_INLINE, PoiJsonFactory.build(osmId, name, buildingType, poiType, lat, lng).toString())
                    putExtra(POICustomPageActivity.EXTRA_POI_NAME, name)
                    putExtra(POICustomPageActivity.EXTRA_POI_TYPE, poiType)
                    putExtra(POICustomPageActivity.EXTRA_POI_LAT, lat)
                    putExtra(POICustomPageActivity.EXTRA_POI_LNG, lng)
                }
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            val osmType = osmId.removePrefix("osm:").substringBefore(":")
            val ref = osmId.removePrefix("osm:").substringAfter(":", "")
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/$osmType/$ref")))
        }
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
