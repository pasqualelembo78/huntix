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
        // Dopo l'uscita il self-kill di Unity chiude :unity e il binding AIDL
        // si scioglie: va ristabilito prima della prossima sessione, altrimenti
        // i messaggi dell'app (POI/ GPS) verrebbero scartati per sempre.
        if (!UnityIpc.isUnity) UnityIpc.bindApp(context)

        val intent = Intent(context, BridgeActivity::class.java)
        intent.putExtra(BridgeActivity.EXTRA_MODE, mode)
        if (!poiData.isNullOrEmpty()) intent.putExtra(BridgeActivity.EXTRA_POI_DATA, poiData)

        // Le Activity Unity girano in :unity: non possono leggere le prefs reali
        // dell'app, quindi gli passiamo lo stato negli extra (le prefs restano
        // solo un fallback dentro BridgeActivity). Tutto letto nel processo app.
        val appCtx = context.applicationContext
        val huntixPrefs = appCtx.getSharedPreferences("huntix_prefs", Context.MODE_PRIVATE)
        val worldPrefs = appCtx.getSharedPreferences("world_game_prefs", Context.MODE_PRIVATE)
        huntixPrefs.getString("city_skin", null)?.let {
            intent.putExtra(BridgeActivity.EXTRA_SKIN, it)
        }
        huntixPrefs.getString("pet_skin", null)?.let {
            intent.putExtra(BridgeActivity.EXTRA_PET, it)
        }
        intent.putExtra(BridgeActivity.EXTRA_SPAWN_LAT, worldPrefs.getFloat("spawnLat", 0f))
        intent.putExtra(BridgeActivity.EXTRA_SPAWN_LNG, worldPrefs.getFloat("spawnLng", 0f))
        // StoreUnityBridge e' il proprietario del valore scritto da Unity; nel
        // processo app legge le stesse prefs senza passare dal Binder.
        val tileDir = StoreUnityBridge.getSharedPreference("tile_cache_dir")
        if (tileDir.isNotEmpty()) intent.putExtra(BridgeActivity.EXTRA_TILE_CACHE_DIR, tileDir)

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JvmStatic
    fun showToast(message: String) {
        // Nel processo app UnityPlayer.currentActivity e' sempre null: serve il
        // contesto cachato dall'Application di entrambi i processi.
        val ctx = UnityPlayer.currentActivity ?: UnityAppContext.context ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun saveData(json: String) {
        // Persistenza dati gioco (TODO: Firestore/file)
    }

    @JvmStatic
    fun loadData(): String = "{}"

    @JvmStatic
    fun getCurrentLocation(): String {
        val ctx = (UnityPlayer.currentActivity ?: UnityAppContext.context) ?: return "{\"lat\":0.0,\"lng\":0.0,\"mock\":false}"
        val loc = OutdoorManager.get(ctx).currentLocationSync()
        val mock = OutdoorManager.get(ctx).isMockMode()
        return if (loc != null)
            "{\"lat\":${loc.latitude},\"lng\":${loc.longitude},\"mock\":$mock,\"acc\":${loc.accuracy}}"
        else "{\"lat\":0.0,\"lng\":0.0,\"mock\":false,\"acc\":0.0}"
    }

    @JvmStatic
    fun setMockWalk(enable: Boolean) {
        val ctx = (UnityPlayer.currentActivity ?: UnityAppContext.context) ?: return
        val outdoor = OutdoorManager.get(ctx)
        if (enable) {
            outdoor.enableMockWalk(true)
        } else {
            outdoor.enableMockWalk(false)
        }
    }

    @JvmStatic
    fun onUnityMessage(eventName: String, jsonData: String) {
        // In :unity solo gli eventi che toccano lo STATO dell'app (profilo,
        // uova, prefs, POI) passano dal Binder: il router gira nel processo app
        // dove gli singleton sono reali (UnityIpc.isUnity == false, no loop).
        // Gli eventi di UI (CityReady/CityProgress/Indoor*/OutdoorNPC*) e le
        // startActivity RESTANO in :unity: li' vivono BridgeActivity e
        // IndoorActivity, quindi VANNO GESTITI QUI, non soltanto instradati.
        // Il vecchio return incondizionato gettava via CityReady: Unity lo
        // mandava, ma l'overlay di caricamento restava bloccato finche'
        // l'utente non premeva "Salta attesa".
        if (UnityIpc.isUnity && UnityIpc.APP_EVENTS.contains(eventName)) {
            UnityIpc.forwardUnityEvent(eventName, jsonData)
            return
        }
        routeUnityEvent(eventName, jsonData)
    }

    /** Router degli eventi non-forwarded: gira in :unity per la UI
     *  (BridgeActivity/IndoorActivity) e nel processo app per gli eventi di
     *  stato che arrivano dal Binder. */
    private fun routeUnityEvent(eventName: String, jsonData: String) {
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
                val ctx = (UnityPlayer.currentActivity ?: UnityAppContext.context) ?: return
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
                val ctx = (UnityPlayer.currentActivity ?: UnityAppContext.context) ?: return
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
                val ctx = (UnityPlayer.currentActivity ?: UnityAppContext.context) ?: return
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
            // ── Uovo del budget: notifica di comparsa (la persistenza resta
            //    lo snapshot CityStateSync, senza nuove SharedPreferences). ──
            "BudgetEggSpawned" -> handleBudgetEggSpawned(jsonData)
            // ── Uovo nascosto da una nuova meccanica (derapata/qualita'):
            //    Android lo registra con posizione, la cattura canonica
            //    EggCapturedInCity lo versera' nell'inventario. ──
            "FeatureEggHidden" -> handleFeatureEggHidden(jsonData)
            // ── Profilo grafico scelto in MiAcitta → preferenza persistente,
            //    rispedita a Unity all'apertura successiva (bridge bidirezionale). ──
            "CityQualityChanged" -> handleCityQualityChanged(jsonData)
            // ── Sorgente input touch scelta in MiCittà: preferenza persistente,
            //    rispedita a Unity all'apertura successiva. ──
            "AltTouchChanged" -> handleAltTouchChanged(jsonData)
            // ── Città Gioco (mondo Reale / Città Gioco): preferenza
            //    persistente, rispedita a Unity all'apertura successiva. ──
            "WorldModeChanged" -> handleWorldModeChanged(jsonData)
            // ── Posto a bordo scelto guidando in MiCittà: preferenza
            //    persistente, rispedita a Unity all'apertura successiva. ──
            "VehicleMountChanged" -> handleVehicleMountChanged(jsonData)
            // ── Innesti MIT dell'audit: animazione menu e console QA.
            //    Preferenze persistenti, rispedite a Unity in openUnityActivity. ──
            "MenuAnimationChanged" -> handleMenuAnimationChanged(jsonData)
            "QaConsoleChanged" -> handleQaConsoleChanged(jsonData)
            // ── Compagno NPC (pattern MIT SanAndreasUnity): preferenza e
            //    avvistamenti uovo con posizione, rimandati a Unity in
            //    openUnityActivity. Nessun nuovo file di preferenze. ──
            "NpcCompanionChanged" -> handleNpcCompanionChanged(jsonData)
            "CompanionRecruited" -> handleCompanionRecruited(jsonData)
            "CompanionEggSpotted" -> handleCompanionEggSpotted(jsonData)
            // ── Registro attività (Fase 1): Miacittà racconta al diario
            //    una missione/un evento compiuto; la cronologia resta una
            //    sola con le uova posizionali. ──
            "ActivityLogEvent" -> handleActivityLogEvent(jsonData)
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
     * Uovo-premio nascosto da una meccanica appena scoperta in MiCittà
     * (derapata col freno a mano, cambio profilo grafico). Non versa premi:
     * lo fa la cattura canonica EggCapturedInCity. Qui Android prende nota
     * del luogo e del momento, come richiesto dal profilo "DOVE HO TROVATO".
     */
    private fun handleFeatureEggHidden(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val feature = j.optString("feature", "")
        if (feature.isBlank()) return
        val label = j.optString("label", feature)
        val place = j.optString("place", "città")
        val lat = j.optDouble("lat", 0.0)
        val lng = j.optDouble("lng", 0.0)
        val ctx = UnityAppContext.context ?: return
        val prefs = ctx.getSharedPreferences("huntix_prefs", Context.MODE_PRIVATE)
        val seen = prefs.getString("feature_eggs", "") ?: ""
        val record = "$feature|$lat|$lng|${j.optLong("tsMt", 0L)}"
        if (!seen.contains(feature)) {
            prefs.edit().putString("feature_eggs",
                if (seen.isEmpty()) record else "$seen;$record").apply()
        }
        com.intelligame.huntix.AppLog.i(
            "HuntixSync",
            "Uovo nascosto: $label ($feature) place=$place lat=$lat lng=$lng"
        )
    }

    /** Profilo grafico Low/Medium/High scelto in MiCittà: lo stesso valore
     *  viene rimandato a Unity in openUnityActivity, così la scelta sopravvive
     *  alla ricreazione dell'Activity e Android ne resta proprietario. */
    private fun handleCityQualityChanged(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val profile = j.optString("profile", "").lowercase()
        if (profile !in setOf("low", "medium", "high")) return
        val ctx = UnityAppContext.context ?: return
        ctx.getSharedPreferences("huntix_prefs", Context.MODE_PRIVATE)
            .edit().putString("city_quality", profile).apply()
        com.intelligame.huntix.AppLog.i("HuntixSync", "Qualità MiCittà: $profile")
    }

    /** Joystick touch alternativo (pattern Zone Zero, MIT) scelto in MiCittà:
     *  Android ne diventa proprietario e lo rimanda a Unity in openUnityActivity.
     *  Nessun nuovo file: usa le stesse SharedPreferences di city_quality. */
    private fun handleAltTouchChanged(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val source = j.optString("source", "").lowercase()
        if (source !in setOf("zone-zero", "huntix")) return
        val ctx = UnityAppContext.context ?: return
        ctx.getSharedPreferences("huntix_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("alt_touch", source == "zone-zero").apply()
        com.intelligame.huntix.AppLog.i("HuntixSync", "Input touch MiCittà: $source")
    }

    /** Mondo scelto in MiCittà: Android ne diventa proprietario e lo rimanda
     *  a Unity in openUnityActivity. Nessun nuovo file: usa le stesse
     *  SharedPreferences di city_quality / alt_touch. */
    private fun handleWorldModeChanged(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val mode = j.optString("mode", "").lowercase()
        if (mode !in setOf("fictional", "real")) return
        val ctx = UnityAppContext.context ?: return
        ctx.getSharedPreferences("huntix_prefs", Context.MODE_PRIVATE)
            .edit().putString("world_mode", mode).apply()
        com.intelligame.huntix.AppLog.i(
            "HuntixSync",
            "Mondo MiCittà: $mode (ricetta=${j.optString("recipe", "-")})"
        )
    }

    /** Ultimo mezzo guidato in MiCittà (auto/moto/pullman): Android ne diventa
     *  proprietario e lo rimanda a Unity in openUnityActivity, come già fa per
     *  qualità, input touch e mondo. Nessun nuovo file di preferenze. */
    private fun handleVehicleMountChanged(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val type = j.optString("type", "").trim()
        if (type.isBlank()) return
        val mount = j.optString("mount", "inside").lowercase()
        if (mount !in setOf("inside", "straddled")) return
        val ctx = UnityAppContext.context ?: return
        ctx.getSharedPreferences("huntix_prefs", Context.MODE_PRIVATE)
            .edit().putString("vehicle_mount", "$type|$mount").apply()
        com.intelligame.huntix.AppLog.i(
            "HuntixSync",
            "Veicolo MiCittà: $type ($mount) code=${j.optString("code", "-")} " +
                "lat=${j.optDouble("lat", 0.0)} lng=${j.optDouble("lng", 0.0)}"
        )
    }

    /** Animazione d'ingresso del menu (pattern Zone Zero, MIT): Android ne
     *  diventa proprietario e rimanda la scelta a Unity all'apertura
     *  successiva. Nessun nuovo file di preferenze. */
    private fun handleMenuAnimationChanged(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val ctx = UnityAppContext.context ?: return
        ctx.getSharedPreferences("huntix_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("menu_animation", j.optBoolean("enabled", false)).apply()
        com.intelligame.huntix.AppLog.i(
            "HuntixSync", "Animazione menu MiCittà: ${j.optBoolean("enabled", false)}"
        )
    }

    /** Console QA (pattern SanAndreasUnity, MIT): preferenza persistente
     *  rimandata a Unity. La console resta un viewer locale dei log. */
    private fun handleQaConsoleChanged(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val ctx = UnityAppContext.context ?: return
        ctx.getSharedPreferences("huntix_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("qa_console", j.optBoolean("enabled", false)).apply()
        com.intelligame.huntix.AppLog.i(
            "HuntixSync", "Console QA MiCittà: ${j.optBoolean("enabled", false)}"
        )
    }

    /** Compagno NPC: preferenza scelta in MiCittà. Rimandata a Unity
     *  all'apertura successiva; lo spegnimento dimentica il nome del pedone. */
    private fun handleNpcCompanionChanged(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val ctx = UnityAppContext.context ?: return
        val enabled = j.optBoolean("enabled", false)
        val editor = ctx.getSharedPreferences("huntix_prefs", Context.MODE_PRIVATE).edit()
        editor.putBoolean("npc_companion", enabled)
        if (!enabled) {
            editor.remove("npc_companion_name")
            editor.remove("npc_companion_id")
        }
        editor.apply()
        com.intelligame.huntix.AppLog.i(
            "HuntixSync", "Compagno NPC MiCittà: $enabled"
        )
    }

    /** Pedone reclutato (o congedato, quando name e' vuoto): Android ricorda
     *  chi segue il player e il luogo dell'arruolamento per il ripristino. */
    private fun handleCompanionRecruited(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val ctx = UnityAppContext.context ?: return
        val name = j.optString("name", "").trim()
        val editor = ctx.getSharedPreferences("huntix_prefs", Context.MODE_PRIVATE).edit()
        if (name.isEmpty()) {
            editor.remove("npc_companion_name")
            editor.remove("npc_companion_id")
        } else {
            editor.putString("npc_companion_name", name)
            editor.putString("npc_companion_id", j.optString("npcId", ""))
        }
        editor.apply()
        com.intelligame.huntix.AppLog.i(
            "HuntixSync",
            "Compagno MiCittà: name=$name npcId=${j.optString("npcId", "-")} " +
                "lat=${j.optDouble("lat", 0.0)} lng=${j.optDouble("lng", 0.0)} " +
                "place=${j.optString("place", "-")}"
        )
    }

    /** Avvistamento uovo da parte del compagno: l'uovo vero passa da
     *  FeatureEggHidden e dalla cattura canonica. Qui si registra solo chi
     *  l'ha segnalato e dove, senza versare ricompense. */
    private fun handleCompanionEggSpotted(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        com.intelligame.huntix.AppLog.i(
            "HuntixSync",
            "Uovo segnalato da ${j.optString("name", "compagno")} " +
                "(${j.optString("npcId", "-")}) lat=${j.optDouble("lat", 0.0)} " +
                "lng=${j.optDouble("lng", 0.0)} place=${j.optString("place", "-")}"
        )
    }

    /** Registro attività (Fase 1): una missione completata in Miacittà entra
     *  nello stesso diario delle uova, con categoria e luogo. Non versa premi:
     *  il premio e' gia' gestito da Wallet lato Unity. */
    private fun handleActivityLogEvent(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val ctx = UnityAppContext.context ?: return
        val label = j.optString("label", "").trim()
        if (label.isEmpty()) return
        com.intelligame.huntix.managers.EggWhereLog.recordActivity(
            ctx,
            j.optString("kind", com.intelligame.huntix.managers.EggWhereLog.KIND_EVENT),
            label,
            j.optString("place", "citta"),
            j.optDouble("lat", 0.0),
            j.optDouble("lng", 0.0),
            j.optString("name", "")
        )
        com.intelligame.huntix.AppLog.i(
            "HuntixSync", "Diario attività: $label (${j.optString("place", "citta")})"
        )
    }

    /**
     * Uovo del budget comparso in MiCittà: il frame time e' restato sotto
     * soglia per 5 secondi. Non versa nulla nell'inventario (lo fara' la
     * cattura canonica EggCapturedInCity); registra solo l'evento nel mirror
     * Android e avvisa il player. Nessuna SharedPreferences nuova: lo stato
     * persistente viaggia nello snapshot CityStateSync.
     */
    private fun handleBudgetEggSpawned(jsonData: String) {
        val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
        val eggId = j.optString("eggId", "")
        val place = j.optString("place", "città→budget")
        val lat = j.optDouble("lat", 0.0)
        val lng = j.optDouble("lng", 0.0)
        val ema = j.optDouble("emaMs", 0.0)
        com.intelligame.huntix.AppLog.i(
            "HuntixSync",
            "Uovo del budget comparso: id=$eggId place=$place lat=$lat lng=$lng ema=${ema}ms"
        )
        showToast("✨ Uovo del budget: la città è fluida! Vai a catturarlo.")
    }

    /**
     * Uovo catturato in MiAcitma (Unity): lo versa nell'inventario uova del
     * player Huntix + aggiorna il profilo (contatori rarita'/XP/power) + premia
     * MVC e gemme, replicando il flusso canonico di OutdoorManager.tryCatch.
     * Registra anche il "DOVE HO TROVATO L'UOVO" (posizione + coordinate) nel
     * profilo, perché Android conosca il ritrovamento anche fuori da Unity.
     */
    private fun handleCityEggCaptured(jsonData: String) {
        val ctx = (UnityPlayer.currentActivity ?: UnityAppContext.context) ?: return
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
        if (j.optString("kind", "") == "budget") {
            val budgetEggId = j.optString("eggId", "")
            com.intelligame.huntix.AppLog.i(
                "HuntixSync",
                "Uovo del budget catturato: id=$budgetEggId place=$place"
            )
        }

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
        val ctx = (UnityPlayer.currentActivity ?: UnityAppContext.context) ?: return
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
        val ctx = (UnityPlayer.currentActivity ?: UnityAppContext.context) ?: return
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
        val ctx = (UnityPlayer.currentActivity ?: UnityAppContext.context) ?: return
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
