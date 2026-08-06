package com.intelligame.huntix.bridge

import com.intelligame.huntix.ui.IndoorActivity

/**
 * StoreUnityBridge — ponte chiamato DALLO store Unity (supermarket-simulator).
 *
 * I metodi sono statici (@JvmStatic) così il codice C# (HuntixStoreBridge)
 * può invocarli via `AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge")`.
 */
object StoreUnityBridge {

    /** Chiamato da Unity quando la scena GameScene del negozio è pronta. */
    @JvmStatic
    fun onIndoorSceneReady(poiId: String) {
        IndoorActivity.instance?.onIndoorSceneReady(poiId)
    }

    /** Chiamato da Unity (o dal pulsante nativo) per uscire dal negozio. */
    @JvmStatic
    fun exitIndoor() {
        IndoorActivity.instance?.runOnUiThread {
            IndoorActivity.instance?.finish()
        }
    }

    /** Restituisce il JSON del POI corrente (usato dallo store per il nome/tipo). */
    @JvmStatic
    fun getPoiData(): String = IndoorActivity.instance?.poiJson ?: "{}"
}
