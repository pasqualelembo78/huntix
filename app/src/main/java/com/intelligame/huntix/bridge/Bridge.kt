package com.intelligame.huntix.bridge

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.intelligame.huntix.legacy.poi.data.PoiRepository
import com.intelligame.huntix.legacy.poi.gps.OutdoorManager
import com.intelligame.huntix.legacy.poi.game.CatchController
import com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge
import com.unity3d.player.UnityPlayer

object Bridge {

    @JvmStatic
    fun openUnityActivity(context: Context, mode: String) {
        val intent = Intent(context, BridgeActivity::class.java)
        intent.putExtra(BridgeActivity.EXTRA_MODE, mode)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JvmStatic
    fun showToast(message: String) {
        val ctx = UnityPlayer.currentActivity ?: return
        ctx.runOnUiThread { Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show() }
    }

    @JvmStatic
    fun saveData(json: String) {
        // Persistenza dati gioco (TODO: Firestore/file)
    }

    @JvmStatic
    fun loadData(): String = "{}"

    @JvmStatic
    fun getCurrentLocation(): String {
        val ctx = UnityPlayer.currentActivity ?: return "{\"lat\":0.0,\"lng\":0.0,\"mock\":false}"
        val loc = OutdoorManager.get(ctx).currentLocationSync()
        val mock = OutdoorManager.get(ctx).isMockMode()
        return if (loc != null)
            "{\"lat\":${loc.latitude},\"lng\":${loc.longitude},\"mock\":$mock,\"acc\":${loc.accuracy}}"
        else "{\"lat\":0.0,\"lng\":0.0,\"mock\":false,\"acc\":0.0}"
    }

    @JvmStatic
    fun setMockWalk(enable: Boolean) {
        val ctx = UnityPlayer.currentActivity ?: return
        val outdoor = OutdoorManager.get(ctx)
        if (enable) {
            outdoor.enableMockWalk(true)
        } else {
            outdoor.enableMockWalk(false)
        }
    }

    @JvmStatic
    fun onUnityMessage(eventName: String, jsonData: String) {
        when (eventName) {
            "CatchRequest" -> {
                val id = extractJsonField(jsonData, "storeId")
                if (id != null) tryCatch(id)
            }
             "PoiSelected" -> {
                val id = extractJsonField(jsonData, "id")
                val lat = extractJsonField(jsonData, "lat")?.toDoubleOrNull()
                val lng = extractJsonField(jsonData, "lng")?.toDoubleOrNull()
                if (id != null && lat != null && lng != null) {
                    PoiUnityBridge.onPoiSelected(id, lat, lng)
                }
            }
            // ── Indoor store events (Unity → IndoorActivity) ──
            "IndoorSceneReady" -> {
                val poiId = extractJsonField(jsonData, "poiId") ?: ""
                StoreUnityBridge.onIndoorSceneReady(poiId)
            }
            "ExitIndoor" -> StoreUnityBridge.exitIndoor()
            "IndoorInteractable" -> StoreUnityBridge.onInteractableFound(jsonData)
            "IndoorInteractionResult" -> StoreUnityBridge.onInteractionResult(jsonData)
            "IndoorNPCNearby" -> StoreUnityBridge.onNPCNearby(jsonData)
            "IndoorNPCFar" -> StoreUnityBridge.onNPCFar(jsonData)
            "IndoorNPCDialogue" -> StoreUnityBridge.onNPCDialogue(jsonData)
            "IndoorNPCQuestAccepted" -> {} // TODO: gestire accettazione quest in IndoorActivity
            "IndoorARPlaneFound" -> {}     // TODO: gestire in IndoorActivity se necessario
             // ── Outdoor NPC events (Unity → BridgeActivity/Outdoor) ──
            "OutdoorNPCNearby" -> StoreUnityBridge.onOutdoorNPCNearby(jsonData)
            "OutdoorNPCFar" -> StoreUnityBridge.onOutdoorNPCFar(jsonData)
            "OutdoorNPCDialogue" -> StoreUnityBridge.onOutdoorNPCDialogue(jsonData)
            "OutdoorNPCInfo" -> StoreUnityBridge.onOutdoorNPCInfo(jsonData)
        }
    }

    @JvmStatic
    fun tryCatch(storeId: String) {
        val ctx = UnityPlayer.currentActivity ?: return
        val outdoor = OutdoorManager.get(ctx)
        val repo = PoiRepository(ctx)
        val controller = CatchController(outdoor, repo)
        controller.attemptCatch(storeId) { outcome ->
            outcome ?: return@attemptCatch
            val res = outcome.toJson(storeId)
            PoiUnityBridge.sendEvent("CatchResult", res)
        }
    }

    private fun extractJsonField(json: String, key: String): String? =
        "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex().find(json)?.groupValues?.get(1)
}
