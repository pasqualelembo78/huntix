package com.intelligame.huntix.legacy.poi.game

import com.intelligame.huntix.legacy.poi.data.PoiStore
import com.intelligame.huntix.legacy.poi.creature.CatchEngine
import com.intelligame.huntix.legacy.poi.creature.Persistence
import com.intelligame.huntix.legacy.poi.creature.Trainer
import com.intelligame.huntix.legacy.poi.data.PoiRepository
import com.intelligame.huntix.legacy.poi.gps.OutdoorManager
import com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CatchController(
    private val outdoor: OutdoorManager,
    private val repository: PoiRepository
) {

    data class CatchOutcome(
        val caught: Boolean,
        val trainer: Trainer,
        val exp: Int,
        val creatureId: String?,
        val creatureName: String?
    ) {
        fun toJson(storeId: String): String {
            val inner = "\"storeId\":\"$storeId\"," +
                    "\"caught\":$caught,\"exp\":$exp," +
                    (if (creatureId != null) "\"creatura\":\"$creatureId\"," +
                        "\"nome\":\"$creatureName\"," else "") +
                    "\"lv\":${trainer.livello}"
            return "{$inner}"
        }
    }

    fun attemptCatch(poiId: String, onResult: (CatchOutcome?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val loc = outdoor.currentLocationSync()
            if (loc == null) { onResult(null); return@launch }
            val pois = repository.fetchPoiForLocation(loc.latitude, loc.longitude, 200).getOrNull()
                ?: run { onResult(null); return@launch }
            val poi = pois.find { it.id == poiId } ?: pois.minByOrNull { distance(it, loc) }
            if (poi == null) { onResult(null); return@launch }

            val dist = distance(poi, loc)
            val trainer = Persistence.trainer()
            val engine = CatchEngine(trainer)
            val result = engine.tryCatch(poi, dist)

            val outcome = if (result.caught) {
                val updated = trainer.aggiungiEsperienza(result.expGuadagnata)
                Persistence.saveTrainer(updated)
                CatchOutcome(true, updated, result.expGuadagnata, result.creature?.id, result.creature?.nome)
            } else {
                CatchOutcome(false, trainer, 0, null, null)
            }
            onResult(outcome)
        }
    }

    private fun distance(store: PoiStore, loc: android.location.Location): Float {
        val dLat = Math.toRadians(store.lat - loc.latitude)
        val dLng = Math.toRadians(store.lng - loc.longitude)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(loc.latitude)) * kotlin.math.cos(Math.toRadians(store.lat)) *
                kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val cl = if (a > 1.0) 1.0 else a
        return (2 * 6371e3 * kotlin.math.asin(kotlin.math.sqrt(cl))).toFloat()
    }
}
