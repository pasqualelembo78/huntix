package com.intelligame.huntix.bridge

import android.content.Intent
import android.net.Uri
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.ui.IndoorActivity
import com.intelligame.huntix.ui.POICustomPageActivity
import com.intelligame.huntix.managers.CustomPageRegistry
import com.intelligame.huntix.managers.PoiSearchManager
import com.intelligame.huntix.reallife.OsmCityJsonFactory
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
     * Shutdown dell'engine Unity in corso (uscita dalla scena). I thread di fetch
     * (requestOsmCity/requestPoisNearby) verificano questo flag PRIMA di chiamare
     * UnitySendMessage: durante il teardown dell'activity il runtime Unity è in
     * smontaggio e un messaggio in volo può causare un crash nativo del processo.
     */
    @Volatile
    private var shuttingDown = false

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

    /** Scrive un log nel sistema AppLog dell'app, richiesto dal codice Unity
     *  (MiAcitma): il viewer log dell'app così mostra anche il comportamento
     *  della sezione MiAcitma che vive interamente in Unity. */
    @JvmStatic
    fun logFromUnity(tag: String, message: String) {
        AppLog.d(tag, message)
    }

    /** Chiamato da Unity quando la scena del negozio è pronta. */
    @JvmStatic
    fun onIndoorSceneReady(poiId: String) {
        IndoorActivity.instance?.onIndoorSceneReady(poiId)
    }

    /** Chiamato da Unity per uscire dal negozio. */
    @JvmStatic
    fun exitIndoor() {
        endUnitySession()
        finishUnityActivity(IndoorActivity.instance)
    }

    /** Chiamato da Unity (scena City, "La Mia Città") per tornare alla Home:
     *  chiude l'Activity Unity (BridgeActivity, che sta sopra HomeActivity nel
     *  back stack) come fa exitIndoor per il negozio. */
    @JvmStatic
    fun exitMiacitta() {
        val activity = UnityPlayer.currentActivity ?: return
        endUnitySession()
        AppLog.d(TAG, "exitMiacitta: ritorno alla Home (shutdown fetch + stop GPS)")
        finishUnityActivity(activity)
    }

    /** Contesto applicativo cachato all'avvio del tracking GPS: serve per
     *  fermare il tracking quando l'Activity Unity e' gia' distrutta
     *  (UnityPlayer.currentActivity == null). */
    @Volatile
    private var trackedAppContext: android.content.Context? = null

    /** Inizio sessione Unity (BridgeActivity.onCreate): riabilita gli invii a
     *  Unity dopo l'eventuale uscita precedente. */
    @JvmStatic
    fun beginUnitySession() {
        shuttingDown = false
    }

    /** Fine sessione Unity (BridgeActivity.onDestroy / exitMiacitta): blocca
     *  OGNI invio a Unity da qualunque thread e ferma il tracking GPS. Con
     *  l'engine in smontaggio un UnitySendMessage in volo e' crash nativo. */
    @JvmStatic
    fun endUnitySession() {
        shuttingDown = true
        stopLocationTrackingInternal()
    }

    /** Ferma il tracking GPS avviato con startLocationTracking (idempotente). */
    @JvmStatic
    fun stopLocationTracking() = stopLocationTrackingInternal()

    private fun stopLocationTrackingInternal() {
        try {
            val ctx = trackedAppContext ?: UnityPlayer.currentActivity?.applicationContext
            if (ctx != null) {
                com.intelligame.huntix.legacy.poi.gps.OutdoorManager.get(ctx).stop()
                AppLog.d(TAG, "stopLocationTracking: tracking GPS fermato")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "stopLocationTracking failed: ${e.message}")
        }
    }

    /** Invio generico sorvegliato verso Unity: scarta i messaggi se la sessione
     *  e' chiusa (engine in teardown o assente). Da usare per TUTTI gli invii
     *  esterni a StoreUnityBridge (messenger POI, Indoor, eventi legacy). */
    @JvmStatic
    fun sendToUnityIfAlive(gameObject: String, method: String, arg: String) {
        if (shuttingDown) return
        try {
            UnityPlayer.UnitySendMessage(gameObject, method, arg)
        } catch (e: Exception) {
            AppLog.w(TAG, "sendToUnityIfAlive($method): ${e.message}")
        }
    }

    /** Chiude una Activity Unity in modo sicuro: se la Activity e una
     *  BridgeActivity mette in pausa il renderer e attende lo svuotamento della
     *  coda buffer prima di distruggere la surface (evita BLASTBufferQueue dtor
     *  / SIG 9). Per le altre Activity chiude direttamente, comunque fuori dal
     *  callback JNI (sempre asincrono per non rientrare nel teardown engine). */
    private fun finishUnityActivity(activity: android.app.Activity?) {
        if (activity == null) return
        AppLog.d(TAG, "finishUnityActivity: chiusura asincrona (activity=" + activity.javaClass.simpleName + ")")
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            if (activity is BridgeActivity) {
                activity.pauseUnityThenFinish()
            } else {
                activity.finish()
            }
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

    /** Chiamato da Unity quando il giocatore accetta una quest NPC. */
    @JvmStatic
    fun onNPCQuestAccepted(json: String) {
        IndoorActivity.instance?.onNPCQuestAccepted(json)
    }

    /** Chiamato da Unity quando AR trova un piano. */
    @JvmStatic
    fun onARPlaneFound(json: String) {
        IndoorActivity.instance?.onARPlaneFound(json)
    }

    /** Mostra il banner "Turno di [nome]" in Unity 3D. */
    @JvmStatic
    fun showTurnBanner(playerName: String) {
        sendToUnityIfAlive("IndoorManager", "ShowTurnBanner", playerName)
    }

    /** Nasconde il banner turno in Unity. */
    @JvmStatic
    fun hideTurnBanner() {
        sendToUnityIfAlive("IndoorManager", "HideTurnBanner", "")
    }

    /** Aggiornamento bisogni ricevuto da Unity dopo interazione con mobili. */
    @JvmStatic
    fun onNeedsUpdated(json: String) {
        IndoorActivity.instance?.onNeedsUpdated(json)
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

    /**
     * Avvia il tracking GPS (legacy OutdoorManager, FusedLocationProviderClient)
     * così la città OSM di MiAcitma può seguire il giocatore reale.
     * Nessun effetto se il permesso location è negato (si resta sul default Roma).
     */
    @JvmStatic
    fun startLocationTracking() {
        // Nuova sessione City: il flag di shutdown della scorsa uscita non deve
        // bloccare gli invii a Unity di questa sessione.
        shuttingDown = false
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: return
        trackedAppContext = ctx   // serve a stopLocationTracking dopo la distruzione dell'Activity
        AppLog.d(TAG, "startLocationTracking: avvio tracking GPS MiAcitma")
        try {
            com.intelligame.huntix.legacy.poi.gps.OutdoorManager.get(ctx).start(true)
            AppLog.d(TAG, "startLocationTracking: OutdoorManager.start ok")
        } catch (e: Exception) {
            // Permesso location negato / sensore non disponibile: si resta sul
            // default Roma. Non propagare MAI verso Unity: ucciderebbe il
            // bootstrap della città OSM (StartCoroutine non partirebbe).
            AppLog.w(TAG, "startLocationTracking failed: ${e.message}")
        }
    }

    /**
     * Richiede i dati OSM completi (strade + edifici + alberi + parchi) per
     * l'area circolare di [radiusMeters] attorno a (lat,lng). Usata da MiAcitma
     * (scena Unity "City") per costruire la città reale al posto del quartiere finto.
     *
     * Il risultato arriva in Unity via UnitySendMessage("GameManager","OnOsmCityReceived",json).
     * In caso di errore arriva "OnOsmCityFailed" (Unity mantiene la città corrente).
     * Riusa la cache disco coordinate-aware di [OsmClient] (24h), quindi la
     * stessa zona è istantanea e lo streaming non stressa Overpass.
     */
    @JvmStatic
    fun requestOsmCity(lat: Double, lng: Double, radiusMeters: Int) {
        val activity = UnityPlayer.currentActivity ?: return
        val ctx = activity.applicationContext
        if (shuttingDown) return
        AppLog.d(TAG, "requestOsmCity: richiesta (${lat},${lng}) r=${radiusMeters}m")
        // ACK immediato: informa Unity che la richiesta è stata ricevuta e che il
        // fetch (cache/Overpass) è in corso, così il watchdog Unity non la rispedisce
        // mentre Overpass è lento (evita doppie fetch e HTTP 429).
        sendToUnity("OnOsmCityFetchStarted", "$lat|$lng")
        Thread {
            if (shuttingDown) return@Thread
            try {
                OsmClient.init(ctx)
                val data = OsmClient.fetchAreaCached(lat, lng, radiusMeters)
                AppLog.d(TAG, "requestOsmCity: fetch ok roads=${data.roads.size}, buildings=${data.buildings.size}, trees=${data.trees.size}, parks=${data.parks.size}")
                val json = OsmCityJsonFactory.build(data, lat, lng, radiusMeters)
                AppLog.d(TAG, "requestOsmCity: json=${json.length} chars, invio a Unity")
                sendToUnity("OnOsmCityReceived", json)
            } catch (e: Exception) {
                AppLog.w(TAG, "requestOsmCity: fresh fetch failed: ${e.message}")
                // Overpass may be down; OsmClient.fetchAreaCached already tried stale cache
                // inside its own loadStaleFromDisk fallback. If we still got here, try
                // the POI repo which also reads from disk cache independently.
                try {
                    val stale = OsmClient.fetchAreaCached(lat, lng, radiusMeters)
                    val json = OsmCityJsonFactory.build(stale, lat, lng, radiusMeters)
                    AppLog.d(TAG, "requestOsmCity: stale cache success, json=${json.length} chars")
                    sendToUnity("OnOsmCityReceived", json)
                } catch (e2: Exception) {
                    AppLog.w(TAG, "requestOsmCity failed completely: ${e2.message}")
                    sendToUnity("OnOsmCityFailed", e2.message ?: "error")
                }
            }
        }.start()
    }

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
        if (shuttingDown) return

        // Cache in-memory: stessa zona recente → risposta immediata (niente Overpass)
        val cached = poiCacheEnvelope
        if (cached != null &&
            System.currentTimeMillis() - poiCacheTime < POI_CACHE_MAX_AGE_MS &&
            distanceMeters(lat, lng, poiCacheCenterLat, poiCacheCenterLng) <= POI_CACHE_MAX_DIST_M) {
            AppLog.d(TAG, "requestPoisNearby: memory cache HIT (${poiCacheCenterLat},${poiCacheCenterLng})")
            sendToUnity("OnPoisReceived", cached)
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
                    if (shuttingDown) return@Thread
                    val pois = OsmPoiRepository.loadNearby(lat, lng, fullRadius)
                        .sortedBy { distanceMeters(lat, lng, it.lat, it.lng) }
                        .take(MAX_POIS)
                    if (shuttingDown) return@Thread
                    val wrapper = buildEnvelope(pois, lat, lng, fullRadius,
                        POI_STAGES_M.size, POI_STAGES_M.size, done = true)
                    poiCacheCenterLat = lat
                    poiCacheCenterLng = lng
                    poiCacheTime = System.currentTimeMillis()
                    poiCacheEnvelope = wrapper
                    sendToUnity("OnPoisReceived", wrapper)
                    return@Thread
                }

                // Caricamento progressivo: prima i vicini, poi si espande
                for (i in POI_STAGES_M.indices) {
                    if (shuttingDown) return@Thread
                    val radius = POI_STAGES_M[i]
                    val stage = i + 1
                    AppLog.d(TAG, "requestPoisNearby: stage $stage/${POI_STAGES_M.size} raggio=${radius}m")
                    sendProgress(stage, POI_STAGES_M.size, radius)

                    if (shuttingDown) return@Thread
                    val pois = OsmPoiRepository.loadNearby(lat, lng, radius)
                        .sortedBy { distanceMeters(lat, lng, it.lat, it.lng) }
                        .take(MAX_POIS)
                    if (shuttingDown) return@Thread
                    lastPois = pois

                    val done = stage == POI_STAGES_M.size
                    lastEnvelope = buildEnvelope(pois, lat, lng, radius, stage, POI_STAGES_M.size, done)
                    sendToUnity("OnPoisReceived", lastEnvelope)

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
                    sendToUnity("OnPoisReceived", doneEnv)
                } else {
                    val mini = miniChunkEnvelope(lat, lng)
                    if (mini != null) {
                        sendToUnity("OnPoisReceived", mini)
                    } else {
                        sendToUnity("OnPoisFailed", e.message ?: "error")
                    }
                }
            }
        }.start()
    }

    /** Invia un messaggio a Unity solo se il teardown non è in corso: durante lo
     *  shutdown (uscita dalla scena) UnitySendMessage su un runtime in smontaggio
     *  può essere un crash nativo, quindi i messaggi in volo vengono scartati. */
    private fun sendToUnity(method: String, arg: String) {
        if (shuttingDown) return
        UnityPlayer.UnitySendMessage("GameManager", method, arg)
    }

    /** Invia l'aggiornamento di avanzamento a Unity (barra di caricamento Esplora). */
    private fun sendProgress(stage: Int, stages: Int, radiusMeters: Int) {
        val jo = JSONObject()
        jo.put("stage", stage)
        jo.put("stages", stages)
        jo.put("radiusMeters", radiusMeters)
        sendToUnity("OnPoisProgress", jo.toString())
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
    private fun miniChunkEnvelope(lat: Double, lng: Double): String? {
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: return null
        try {
            OsmClient.init(ctx)
            val data = OsmClient.loadMiniChunk(lat, lng) ?: return null
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

    /** Saldo MVC (Huntix Coins) attuale, gestito dall'app esterna. */
    @JvmStatic
    fun getMvcBalance(): Double {
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: return 0.0
        return com.intelligame.huntix.managers.SavedManager.getMvcBalance(ctx)
    }

    /** Tenta di spendere [amount] MVC. True se il saldo bastava, false altrimenti. */
    @JvmStatic
    fun spendMvc(amount: Double): Boolean {
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: return false
        return com.intelligame.huntix.managers.SavedManager.spendMvc(ctx, amount)
    }

    /** Accredita [amount] MVC (es. scambio da soldi citta a MVC). Ritorna il nuovo saldo. */
    @JvmStatic
    fun addMvc(amount: Double): Double {
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: return 0.0
        if (amount <= 0) return getMvcBalance()
        return com.intelligame.huntix.managers.SavedManager.addMvc(ctx, amount)
    }

    // ─────────────────────────────────────────────────────────────
    // ── Unified player profile (unico giocatore, unico universo) ──
    // Questi metodi espongono/aggiornano il PlayerProfile Huntix (la
    // fonte di verita'), così Miacitta (Unity) e gli altri moduli
    // leggono/scrivo lo STESSO profilo: un solo player, una sola XP,
    // un solo nome, una sola classifica.
    // ─────────────────────────────────────────────────────────────

    /** Nome del player Huntix (Unified profile). */
    @JvmStatic
    fun getPlayerName(): String =
        com.intelligame.huntix.PlayerProfileManager.myProfile?.name ?: "Giocatore"

    /** XP totale cumulato del player (Unity non ha un proprio XP separato). */
    @JvmStatic
    fun getPlayerXp(): Long =
        com.intelligame.huntix.PlayerProfileManager.myProfile?.xp ?: 0L

    /** Livello calcolato dalla XP cumulata (getter PlayerProfile.level). */
    @JvmStatic
    fun getPlayerLevel(): Int =
        com.intelligame.huntix.PlayerProfileManager.myProfile?.level ?: 1

    /** Potere totale del player. */
    @JvmStatic
    fun getPlayerPower(): Long =
        com.intelligame.huntix.PlayerProfileManager.myProfile?.power ?: 0L

    /** Gemme premium del player. */
    @JvmStatic
    fun getPlayerGems(): Int =
        com.intelligame.huntix.PlayerProfileManager.myProfile?.gems ?: 0

    /** Energia corrente del player (0..100). */
    @JvmStatic
    fun getPlayerEnergy(): Int =
        com.intelligame.huntix.PlayerProfileManager.myProfile?.energy ?: 100

    /** Inventario uova del player (conteggio totale). */
    @JvmStatic
    fun getEggCount(): Int {
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: return 0
        return com.intelligame.huntix.EggInventoryManager.getInventoryCount(ctx)
    }

    /**
     * Snapshot completo del profilo come JSON, così Unity può mostrare
     * lo stesso nome/XP/livello/uova ovunque. Compatibile anche quando il
     * profilo non è ancora stato inizializzato (default sicuri).
     */
    @JvmStatic
    fun getPlayerProfileJson(): String {
        val p = com.intelligame.huntix.PlayerProfileManager.myProfile
        if (p == null)
            return "{\"name\":\"Giocatore\",\"xp\":0,\"level\":1,\"power\":0,\"gems\":0,\"energy\":100,\"eggCount\":0,\"eggsFound\":0}"
        val ctx = UnityPlayer.currentActivity?.applicationContext
        val eggCount = ctx?.let { com.intelligame.huntix.EggInventoryManager.getInventoryCount(it) } ?: 0
        val j = org.json.JSONObject()
        try {
            j.put("name", p.name)
            j.put("xp", p.xp)
            j.put("level", p.level)
            j.put("power", p.power)
            j.put("gems", p.gems)
            j.put("energy", p.energy)
            j.put("eggCount", eggCount)
            j.put("eggsFound", p.eggsFound)
            j.put("playerId", p.playerId)
            // Sesso ed età scelti in registrazione (fonte di verità per Miacittà).
            j.put("gender", p.playerGender)          // "male" / "female"
            j.put("birthYear", p.birthYear)
            j.put("isMinor", p.isMinor)
        } catch (_: Exception) { }
        return j.toString()
    }

    /**
     * Accredita XP guadagnati in Miacitta (matrimonio, figli, missioni,
     * reincarnazione) direttamente sul profilo Huntix, così alimentano XP,
     * livello e classifica mondiale.
     *
     * @return il nuovo totale XP (Long).
     */
    @JvmStatic
    fun addXpFromCity(xpAmount: Long): Long {
        if (xpAmount <= 0) return getPlayerXp()
        val pm = com.intelligame.huntix.PlayerProfileManager
        val p = pm.myProfile ?: return 0L
        p.xp += xpAmount
        p.weeklyXp += xpAmount
        p.lastSeen = System.currentTimeMillis()
        pm.persistMyProfile()
        return p.xp
    }

    /**
     * Accredita potere guadagnato in Miacitta al profilo Huntix.
     * @return il nuovo potere totale.
     */
    @JvmStatic
    fun addPowerFromCity(powerAmount: Long): Long {
        if (powerAmount <= 0) return getPlayerPower()
        val pm = com.intelligame.huntix.PlayerProfileManager
        val p = pm.myProfile ?: return 0L
        p.power += powerAmount
        p.lastSeen = System.currentTimeMillis()
        pm.persistMyProfile()
        return p.power
    }

    /**
     * Accreditare gemme guadagnate in Miacitta.
     * @return il nuovo saldo gemme.
     */
    @JvmStatic
    fun addGemsFromCity(amount: Int): Int {
        if (amount <= 0) return getPlayerGems()
        val pm = com.intelligame.huntix.PlayerProfileManager
        val p = pm.myProfile ?: return 0
        p.addGems(amount)
        pm.persistMyProfile()
        return p.gems
    }

    /**
     * Sincronizza l'energia del player dalla citta' al profilo Huntix
     * (0..100). Aggiorna anche Firestore così gli altri moduli la leggono.
     */
    @JvmStatic
    fun syncEnergyFromCity(energy: Int): Int {
        val pm = com.intelligame.huntix.PlayerProfileManager
        val p = pm.myProfile ?: return energy.coerceIn(0, 100)
        val clamped = energy.coerceIn(0, 100)
        if (p.energy != clamped) {
            p.energy = clamped
            pm.persistMyProfile()
        }
        return p.energy
    }

    /**
     * Cambia il nome del player da Miacitta (es. dopo la reincarnazione).
     * Replica updatePlayerName sul profilo locale + Firestore best-effort.
     *
     * @return true se il nome è stato aggiornato.
     */
    @JvmStatic
    fun setPlayerNameFromCity(newName: String): Boolean {
        if (newName.isBlank()) return false
        val pm = com.intelligame.huntix.PlayerProfileManager
        val p = pm.myProfile ?: return false
        p.name = newName
        pm.persistMyProfile()
        // Usa il canale esistente per tenere aggiornata anche la classifica
        pm.updatePlayerName(newName, onComplete = { }, onError = { })
        return true
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
