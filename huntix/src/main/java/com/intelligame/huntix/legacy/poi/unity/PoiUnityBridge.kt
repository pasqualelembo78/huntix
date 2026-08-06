package com.intelligame.huntix.legacy.poi.unity

object PoiUnityBridge {

    interface Messenger {
        fun sendEvent(event: String, data: String)
        fun openStoreJson(storeId: String, json: String)
        fun openStoreUrl(storeId: String, url: String)
        fun onPoiSelected(storeId: String, lat: Double, lng: Double)
    }

    private var messenger: Messenger? = null

    fun registerMessenger(messenger: Messenger) {
        this.messenger = messenger
    }

    fun sendEvent(eventName: String, jsonData: String) {
        messenger?.sendEvent(eventName, jsonData)
    }

    fun openStoreJson(storeId: String, json: String) {
        messenger?.openStoreJson(storeId, json)
    }

    fun openStoreUrl(storeId: String, url: String) {
        messenger?.openStoreUrl(storeId, url)
    }

    fun onPoiSelected(storeId: String, lat: Double, lng: Double) {
        messenger?.onPoiSelected(storeId, lat, lng)
    }
}
