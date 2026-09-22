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
            // ── Miacitta: i chunk della citta' sono pronti → chiudi lo splash ──
            "CityReady" -> StoreUnityBridge.onCityReady()
            // ── Miacitta: avanzamento splash (fase, %, KB/MB dei chunk) ──
            "CityProgress" -> StoreUnityBridge.onCityProgress(jsonData)
            // ── Miacitta: posizione di gioco corrente (Unity) → prefs + Google ──
            "PlayerPosition" -> {
                val ctx = UnityPlayer.currentActivity ?: return
                val j = try { JSONObject(jsonData) } catch (_: Exception) { return }
                val lat = j.optString("lat").toDoubleOrNull() ?: return
                val lng = j.optString("lng").toDoubleOrNull() ?: return
                WorldPosCloud.updateLocalAndCloud(ctx, lat, lng)
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
            // ── Realtà Aumentata: pulsante PASSA A AR (Unity) → apre la camera
            //    reale con ARCore; il mondo OSM viene ancorato al piano
            //    inquadrato. Posizione VIRTUALE del player, MAI il GPS reale. ──
            "ArCityRequest" -> {
                val ctx = UnityPlayer.currentActivity ?: return
                val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
                val lat = j.optDouble("lat", 0.0)
                val lng = j.optDouble("lng", 0.0)
                val intent = Intent(
                    ctx, com.intelligame.huntix.minigames.ar.ArCityActivity::class.java
                ).apply {
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_LAT, lat)
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_LNG, lng)
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_POI_LAT,
                        j.optDouble("poiLat", lat))
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_POI_LNG,
                        j.optDouble("poiLng", lng))
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_POI_NAME,
                        j.optString("poiName", "Luogo"))
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_POI_TYPE,
                        j.optString("poiType", "luogo"))
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_EGGS,
                        j.optJSONArray("eggs")?.toString() ?: "[]")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
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
            // ── MiAcitma P2P: tap su un altro giocatore reale → profilo + chat ──
            "PlayerProfileRequest" -> {
                val j = try { JSONObject(jsonData) } catch (_: Exception) { JSONObject() }
                val toId = j.optString("toUserId", "")
                val name = j.optString("name", "Giocatore")
                val skin = j.optString("skin", "humanMaleA")
                val lvl = j.optInt("level", 1)
                openPlayerChat(toId, name, lvl, skin)
            }
            // ── MiAcitma: uovo catturato nel mini-gioco → inventario uova Huntix
            "EggCapturedInCity" -> handleCityEggCaptured(jsonData)
            // ── MiAcitma → profilo Huntix unificato (un solo player) ──
            "CityXpEarned" -> handleCityXpEarned(jsonData)
            "CityPowerEarned" -> handleCityPowerEarned(jsonData)
            "CityGemsEarned" -> handleCityGemsEarned(jsonData)
            "CityEnergyUpdate" -> handleCityEnergyUpdate(jsonData)
            "PlayerReincarnated" -> handlePlayerReincarnated(jsonData)
            // ── City State Sync: snapshot bidirezionale stato città (Unity→Android)
            "CityStateSync" -> StoreUnityBridge.onCityStateSync(jsonData)
            // ── City State Sync: verifica di allineamento (risposta via log) ──
            "CityStateCheck" -> StoreUnityBridge.checkCityStateSync()
            // ── Growth (crescita fisiologica) Unity→Android: Unity ha applicato
            //    l'aspetto dal livello XP; lo notifica al profilo (log/mirror). ──
            "GrowthStateSync" -> StoreUnityBridge.onGrowthStateSync(jsonData)
            // ── Regali P2P (uova e gemme): Unity ha riscattato (claim) il regalo
            //    sul ledger; Android accredita inventario/gemme e lo registra. ──
            "GiftClaimed" -> handleGiftClaimed(jsonData)
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
     * MVC e gemme, replicando il flusso canonico di OutdoorManager.tryCatch.
     * Registra anche il "DOVE HO TROVATO L'UOVO" (posizione + coordinate) nel
     * profilo, perché Android conosca il ritrovamento anche fuori da Unity.
     */
    private fun handleCityEggCaptured(jsonData: String) {
        val ctx = UnityPlayer.currentActivity ?: return
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }

        val rarityId = j.optString("rarityId", "common")
        val rarity = com.intelligame.huntix.EggRarity.fromId(rarityId)
        val fantasyName = j.optString("fantasyName", rarity.randomName())
        val power = j.optInt("power", rarity.basePower)
        var xpReward = j.optInt("xpReward", rarity.xpReward)
        val gems = j.optInt("gems", 0)
        val lat = j.optDouble("lat", 0.0)
        val lng = j.optDouble("lng", 0.0)
        val place = j.optString("place", "MiAcitta")

        // Eventi stagionali: XP reale x2/x5 (non solo display).
        val xpMult = com.intelligame.huntix.gamification.LiveEventManager.getActiveXpMultiplier()
        if (xpMult > 1f) xpReward = (xpReward * xpMult).toInt()

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
        // Gemme dalla caccia alle uova (premium): stessa scala dell'outdoor.
        if (gems > 0) {
            val newGems = StoreUnityBridge.addGemsFromCity(gems)
            com.intelligame.huntix.AppLog.i("HuntixSync", "Gemme uova +$gems -> $newGems")
        }
        // "DOVE HO TROVATO L'UOVO": memoria condivisa del ritrovamento.
        com.intelligame.huntix.managers.EggWhereLog.record(
            ctx, rarity.id, place, lat, lng, fantasyName
        )
        val msg = if (added) "Uovo aggiunto all'inventario! +${mvcReward.toInt()} MVC"
        else "Inventario uova pieno!"
        showToast(msg)
    }

    /**
     * Regalo P2P riscattato da Unity (claim sul ledger): accredita il
     * contenuto — uovo → inventario + profilo, gemme → gemme del profilo —
     * replicando il flusso canônico di handleCityEggCaptured (qui però senza
     * XP/MVC aggiuntivi: il donatore li ha già spesi in codesto regalo).
     */
    private fun handleGiftClaimed(jsonData: String) {
        val ctx = UnityPlayer.currentActivity ?: return
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }

        val kind = j.optString("kind", "")
        val from = j.optString("from_name", "Giocatore")

        when (kind) {
            "gem" -> {
                val amount = j.optInt("amount", 0)
                if (amount <= 0) return
                val newGems = StoreUnityBridge.addGemsFromCity(amount)
                com.intelligame.huntix.AppLog.i("HuntixSync", "Regalo gemme +$amount -> $newGems")
                showToast("💎 Regalo ricevuto: +$amount gemme da $from")
            }
            "egg" -> {
                val rarityId = j.optString("rarity", "common")
                val rarity = com.intelligame.huntix.EggRarity.fromId(rarityId)
                val fantasyName = j.optString("egg_name", "").ifBlank { rarity.randomName() }
                val eggId = j.optString("egg_id", "gift_" + System.currentTimeMillis())
                // Idempotenza: se questo specifico regalo è già arrivato in
                // inventario (es. ritrasmissione dal toast), non duplicarlo.
                val alreadyHas = com.intelligame.huntix.EggInventoryManager.getInventory(ctx)
                    .any { it.eggId == eggId }
                if (!alreadyHas) {
                    val item = com.intelligame.huntix.EggInventoryItem(
                        eggId = eggId,
                        rarityId = rarity.id,
                        fantasyName = fantasyName,
                        power = rarity.basePower,
                        xpReward = 0
                    )
                    val added = com.intelligame.huntix.EggInventoryManager.addEgg(ctx, item)
                    com.intelligame.huntix.managers.EggWhereLog.record(
                        ctx, rarity.id, place = "Regalo da $from", name = fantasyName
                    )
                    val msg = if (added) "Regalo ricevuto: $fantasyName da $from!"
                    else "Regalo ricevuto ma inventario pieno!"
                    showToast(msg)
                } else {
                    com.intelligame.huntix.AppLog.i("HuntixSync", "Regalo gia' in inventario: $eggId (skip)")
                }
            }
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


    private fun openPlayerChat(toUserId: String, name: String, level: Int, skin: String) {
        if (toUserId.isBlank()) return
        val ctx = UnityPlayer.currentActivity ?: return
        val intent = Intent(ctx, com.intelligame.huntix.ui.PlayerChatActivity::class.java).apply {
            putExtra("TO_USER_ID", toUserId)
            putExtra("NAME", name)
            putExtra("LEVEL", level)
            putExtra("SKIN", skin)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}
