package com.intelligame.huntix.bridge

import android.content.Intent
import android.net.Uri
import com.intelligame.huntix.ui.IndoorActivity
import com.intelligame.huntix.ui.POICustomPageActivity
import com.intelligame.huntix.managers.CustomPageRegistry
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

    private const val MAX_POIS = 1200

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

    // ── Esplora (Unity) ─────────────────────────────────────────────
    /** Posizione corrente reale/mock, JSON {"lat":..,"lng":..,"mock":bool}. */
    @JvmStatic
    fun getCurrentLocation(): String =
        com.intelligame.huntix.bridge.Bridge.getCurrentLocation()

    /** Richiede i POI OSM entro [radiusMeters] (max 10 km) da (lat,lng).
     *  Il risultato arriva in Unity via UnitySendMessage("GameManager","OnPoisReceived",json).
     *  I POI vengono ordinati per distanza e limitati (1200) per non saturare Unity. */
    @JvmStatic
    fun requestPoisNearby(lat: Double, lng: Double, radiusMeters: Int) {
        val activity = UnityPlayer.currentActivity ?: return
        val ctx = activity.applicationContext
        CustomPageRegistry.init(ctx)
        Thread {
            try {
                OsmClient.init(ctx)
                val pois = OsmPoiRepository.loadNearby(lat, lng, radiusMeters)
                    .sortedBy { distanceMeters(lat, lng, it.lat, it.lng) }
                    .take(MAX_POIS)
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
                val wrapper = org.json.JSONObject()
                wrapper.put("pois", arr)
                wrapper.put("count", pois.size)
                wrapper.put("centerLat", lat)
                wrapper.put("centerLng", lng)
                UnityPlayer.UnitySendMessage("GameManager", "OnPoisReceived", wrapper.toString())
            } catch (e: Exception) {
                UnityPlayer.UnitySendMessage("GameManager", "OnPoisFailed", e.message ?: "error")
            }
        }.start()
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
