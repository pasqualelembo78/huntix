package com.intelligame.huntix.bridge

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.intelligame.huntix.legacy.poi.data.PoiRepository
import com.intelligame.huntix.legacy.poi.gps.OutdoorManager
import com.intelligame.huntix.legacy.poi.game.CatchController
import com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge
import com.unity3d.player.UnityPlayer
import org.json.JSONObject

object Bridge {

    @JvmStatic
    fun openUnityActivity(context: Context, mode: String) {
        openUnityActivity(context, mode, null)
    }

    @JvmStatic
    fun openUnityActivity(context: Context, mode: String, poiData: String?) {
        val intent = Intent(context, BridgeActivity::class.java)
        intent.putExtra(BridgeActivity.EXTRA_MODE, mode)
        if (!poiData.isNullOrEmpty()) intent.putExtra(BridgeActivity.EXTRA_POI_DATA, poiData)
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
            "IndoorNPCQuestAccepted" -> StoreUnityBridge.onNPCQuestAccepted(jsonData)
            "IndoorARPlaneFound" -> StoreUnityBridge.onARPlaneFound(jsonData)
            "IndoorNeedsUpdated" -> StoreUnityBridge.onNeedsUpdated(jsonData)
             // ── Outdoor NPC events (Unity → BridgeActivity/Outdoor) ──
            "OutdoorNPCNearby" -> StoreUnityBridge.onOutdoorNPCNearby(jsonData)
            "OutdoorNPCFar" -> StoreUnityBridge.onOutdoorNPCFar(jsonData)
            "OutdoorNPCDialogue" -> StoreUnityBridge.onOutdoorNPCDialogue(jsonData)
            "OutdoorNPCInfo" -> StoreUnityBridge.onOutdoorNPCInfo(jsonData)
            // ── MiAcitma: tap su un pedone → chat IA (RealLifeChatActivity) ──
            "NpcChatRequest" -> {
                // roleplay: preferisci il personaggio RealLife mappato
                val id = extractJsonField(jsonData, "characterId")
                    ?: extractJsonField(jsonData, "npcId") ?: ""
                val name = extractJsonField(jsonData, "name") ?: "Cittadino"
                val ctx = UnityPlayer.currentActivity ?: return
                val intent = Intent(ctx, com.intelligame.huntix.ui.RealLifeChatActivity::class.java).apply {
                    putExtra("CHAR_ID", id)
                    putExtra("CHAR_NAME", name)
                    putExtra("CHAR_AVATAR", "\uD83D\uDE42")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
            // ── MiAcitma: uovo catturato nel mini-gioco → inventario uova Huntix
            "EggCapturedInCity" -> handleCityEggCaptured(jsonData)
            // ── MiAcitma → profilo Huntix unificato (un solo player) ──
            "CityXpEarned" -> handleCityXpEarned(jsonData)
            "CityPowerEarned" -> handleCityPowerEarned(jsonData)
            "CityGemsEarned" -> handleCityGemsEarned(jsonData)
            "CityEnergyUpdate" -> handleCityEnergyUpdate(jsonData)
            "PlayerReincarnated" -> handlePlayerReincarnated(jsonData)
        }
    }

    /**
     * XP guadagnati in Miacitta (matrimonio/figli/missioni/reincarnazione):
     * li accredita sul profilo Huntix così alimentano XP, livello e classifica.
     */
    private fun handleCityXpEarned(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val amount = j.optLong("xp", 0L)
        if (amount <= 0) return
        val newXp = StoreUnityBridge.addXpFromCity(amount)
        val src = j.optString("source", "citta")
        com.intelligame.huntix.AppLog.i("HuntixSync", "XP +$amount ($src) -> totale $newXp")
    }

    /** Potere guadagnato in Miacitta → profilo Huntix. */
    private fun handleCityPowerEarned(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val amount = j.optLong("power", 0L)
        if (amount <= 0) return
        val newPower = StoreUnityBridge.addPowerFromCity(amount)
        com.intelligame.huntix.AppLog.i("HuntixSync", "Power +$amount -> totale $newPower")
    }

    /** Gemme guadagnate in Miacitta → profilo Huntix. */
    private fun handleCityGemsEarned(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val amount = j.optInt("gems", 0)
        if (amount <= 0) return
        val newGems = StoreUnityBridge.addGemsFromCity(amount)
        com.intelligame.huntix.AppLog.i("HuntixSync", "Gemme +$amount -> $newGems")
    }

    /** Sincronizza l'energia del player dalla citta' al profilo Huntix. */
    private fun handleCityEnergyUpdate(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val energy = j.optInt("energy", 100)
        StoreUnityBridge.syncEnergyFromCity(energy)
    }

    /**
     * Reincarnazione in Miacitta: il player nasce di nuovo e (opzionale) cambia
     * nome. Aggiorna il profilo Huntix con il nuovo nome così la classifica e
     * tutti i moduli vedono lo stesso player appena reincarnato.
     */
    private fun handlePlayerReincarnated(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val newName = j.optString("name", "")
        if (newName.isNotBlank()) StoreUnityBridge.setPlayerNameFromCity(newName)
        // Si puo' accreditare un bonus di reincarnazione per feedback positivo
        val xpBonus = j.optLong("xp", 0L)
        if (xpBonus > 0) StoreUnityBridge.addXpFromCity(xpBonus)
        com.intelligame.huntix.AppLog.i("HuntixSync", "Reincarnazione: nuovo nome '$newName' +$xpBonus xp")
    }

    /**
     * Uovo catturato in MiAcitma (Unity): lo versa nell'inventario uova del
     * player Huntix + aggiorna il profilo (contatori rarita'/XP/power) + premia
     * MVC, replicando il flusso canonico di OutdoorManager.tryCatch.
     */
    private fun handleCityEggCaptured(jsonData: String) {
        val ctx = UnityPlayer.currentActivity ?: return
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }

        val rarityId = j.optString("rarityId", "common")
        val rarity = com.intelligame.huntix.EggRarity.fromId(rarityId)
        val fantasyName = j.optString("fantasyName", rarity.randomName())
        val power = j.optInt("power", rarity.basePower)
        val xpReward = j.optInt("xpReward", rarity.xpReward)

        val item = com.intelligame.huntix.EggInventoryItem(
            eggId       = j.optString("eggId", "city_" + System.currentTimeMillis()),
            rarityId    = rarity.id,
            fantasyName = fantasyName,
            power       = power,
            xpReward    = xpReward
        )
        val added = com.intelligame.huntix.EggInventoryManager.addEgg(ctx, item)
        com.intelligame.huntix.PlayerProfileManager.recordEggCatch(rarity) { }
        val mvcReward = when (rarity) {
            com.intelligame.huntix.EggRarity.COMMON -> 5.0
            com.intelligame.huntix.EggRarity.UNCOMMON -> 15.0
            com.intelligame.huntix.EggRarity.RARE -> 40.0
            com.intelligame.huntix.EggRarity.EPIC -> 100.0
            com.intelligame.huntix.EggRarity.LEGENDARY -> 250.0
        }
        com.intelligame.huntix.managers.SavedManager.addMvc(ctx, mvcReward)
        val msg = if (added) "Uovo aggiunto all'inventario! +${mvcReward.toInt()} MVC"
        else "Inventario uova pieno!"
        showToast(msg)
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
