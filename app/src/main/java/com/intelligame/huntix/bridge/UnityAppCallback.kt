package com.intelligame.huntix.bridge

import android.os.Handler
import android.os.Looper
import com.intelligame.huntix.AppLog
import org.json.JSONArray

/**
 * Callback AIDL che gira nel PROCESSO APP: qui gli singleton del profilo, delle
 * uova e delle prefs sono reali, quindi le chiamate sincrone di Unity vengono
 * risolte su StoreUnityBridge senza passare da IPC (UnityIpc.isUnity == false,
 * niente delegate: non c'e' rischio di ricorsione).
 */
class UnityAppCallback : IUnityCallback.Stub() {

    /** Eventi Unity → router pesante Bridge.onUnityMessage, sul main thread app. */
    override fun onUnityEvent(event: String?, json: String?) {
        Handler(Looper.getMainLooper()).post {
            try {
                Bridge.onUnityMessage(event ?: "", json ?: "")
            } catch (t: Throwable) {
                AppLog.w(TAG, "onUnityEvent(${event ?: ""}) failed: ${t.message}")
            }
        }
    }

    /**
     * Dispatcher sincrono: [json] e' un array di argomenti, il ritorno e' una
     * stringa (JSON/valore) che Unity converte nel tipo atteso.
     */
    override fun call(method: String?, json: String?): String {
        val a = parseArgs(json)
        return try {
            when (method) {
                // ── Posizione e valuta ──
                "getCurrentLocation" -> StoreUnityBridge.getCurrentLocation()
                "startLocationTracking" -> {
                    StoreUnityBridge.startLocationTracking()
                    ""
                }
                "stopLocationTracking" -> {
                    StoreUnityBridge.stopLocationTracking()
                    ""
                }
                "getMvcBalance" -> StoreUnityBridge.getMvcBalance().toString()
                "spendMvc" -> StoreUnityBridge.spendMvc(a.optDouble(0, 0.0)).toString()
                "addMvc" -> StoreUnityBridge.addMvc(a.optDouble(0, 0.0)).toString()

                // ── Profilo unificato (un solo giocatore, un solo universo) ──
                "getPlayerName" -> StoreUnityBridge.getPlayerName()
                "getAccessToken" -> StoreUnityBridge.getAccessToken()
                "getPlayerXp" -> StoreUnityBridge.getPlayerXp().toString()
                "getPlayerLevel" -> StoreUnityBridge.getPlayerLevel().toString()
                "getPlayerPower" -> StoreUnityBridge.getPlayerPower().toString()
                "getPlayerGems" -> StoreUnityBridge.getPlayerGems().toString()
                "getPlayerEnergy" -> StoreUnityBridge.getPlayerEnergy().toString()
                "getEggCount" -> StoreUnityBridge.getEggCount().toString()
                "getPlayerProfileJson" -> StoreUnityBridge.getPlayerProfileJson()
                "addXpFromCity" -> StoreUnityBridge.addXpFromCity(a.optLong(0, 0L)).toString()
                "addPowerFromCity" -> StoreUnityBridge.addPowerFromCity(a.optLong(0, 0L)).toString()
                "addGemsFromCity" -> StoreUnityBridge.addGemsFromCity(a.optInt(0, 0)).toString()
                "setPlayerNameFromCity" -> StoreUnityBridge.setPlayerNameFromCity(a.optString(0, "")).toString()
                "syncEnergyFromCity" -> StoreUnityBridge.syncEnergyFromCity(a.optInt(0, 100)).toString()

                // ── Bisogni, POI, OSM, log ──
                "getNeedsJson" -> StoreUnityBridge.getNeedsJson()
                "applyNeedAction" -> StoreUnityBridge.applyNeedAction(
                    a.optString(0, ""), a.optDouble(1, 0.0).toFloat()
                )
                "logFromUnity" -> {
                    StoreUnityBridge.logFromUnity(a.optString(0, "Unity"), a.optString(1, ""))
                    ""
                }
                "requestPoisNearby" -> {
                    StoreUnityBridge.requestPoisNearby(
                        a.optDouble(0, 0.0), a.optDouble(1, 0.0), a.optInt(2, 1000)
                    )
                    ""
                }
                "requestOsmCity" -> {
                    StoreUnityBridge.requestOsmCity(
                        a.optDouble(0, 0.0), a.optDouble(1, 0.0), a.optInt(2, 1000)
                    )
                    ""
                }

                // ── Prefs e cache tile ──
                "getSharedPreference" -> StoreUnityBridge.getSharedPreference(a.optString(0, ""))
                "setSharedPreference" -> {
                    StoreUnityBridge.setSharedPreference(a.optString(0, ""), a.optString(1, ""))
                    ""
                }
                "setTileCacheDir" -> {
                    StoreUnityBridge.setTileCacheDir(a.optString(0, ""))
                    ""
                }
                "getOfflinePackStatus" -> StoreUnityBridge.getOfflinePackStatus()
                "isTileOffline" -> StoreUnityBridge.isTileOffline(a.optString(0, "")).toString()
                "setOfflinePackFlag" -> {
                    StoreUnityBridge.setOfflinePackFlag(
                        a.optString(0, ""), a.optBoolean(1, false))
                    ""
                }
                "requestOfflinePack" -> {
                    StoreUnityBridge.requestOfflinePack(a.optString(0, ""))
                    ""
                }
                // ── Spazio selettivo: statistiche/pulizia SOLO delle tile v5 ──
                "tileCacheStats" -> StoreUnityBridge.tileCacheStats()
                "clearTileCache" -> StoreUnityBridge.clearTileCache(a.optInt(0, -1))
                // Ricerca destinazione: il risultato torna a Unity in modo
                // asincrono via OnGeocodeResult, non come valore di ritorno.
                "geocodeAddress" -> {
                    StoreUnityBridge.geocodeAddress(a.optString(0, ""))
                    ""
                }

                // ── World Streaming: stato settori, prefetch e memoria device ──
                "getWorldStreamingStatus" -> StoreUnityBridge.getWorldStreamingStatus()
                "requestWorldSector" -> {
                    StoreUnityBridge.requestWorldSector(a.optString(0, ""))
                    ""
                }
                "getDeviceMemoryInfo" -> StoreUnityBridge.getDeviceMemoryInfo()

                // ── Snapshot città (uova della crescita incluse) ──
                "getCityStateSync" -> StoreUnityBridge.getCityStateSync()
                "checkCityStateSync" -> StoreUnityBridge.checkCityStateSync()
                "onCityStateSync" -> {
                    StoreUnityBridge.onCityStateSync(a.optString(0, ""))
                    ""
                }
                "onGrowthStateSync" -> {
                    StoreUnityBridge.onGrowthStateSync(a.optString(0, ""))
                    ""
                }

                else -> {
                    AppLog.w(TAG, "call($method): metodo non coperto dal dispatcher app")
                    ""
                }
            }
        } catch (t: Throwable) {
            // Un'eccezione qui attraverserebbe il Binder e ucciderebbe Unity:
            // va loggata e trasformata in valore vuoto.
            AppLog.w(TAG, "call($method) failed: ${t.message}")
            ""
        }
    }

    private fun parseArgs(json: String?): JSONArray = try {
        JSONArray(json ?: "[]")
    } catch (_: Exception) {
        JSONArray()
    }

    companion object {
        private const val TAG = "UnityAppCallback"
    }
}
