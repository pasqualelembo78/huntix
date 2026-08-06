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
import kotlin.math.sqrt

class CatchController(
    private val outdoor: OutdoorManager,
    private val repository: PoiRepository
) {

    data class CatchOutcome(val caught: Boolean, val trainer: Trainer, val exp: Int)

    fun attemptCatch(poiId: String, onResult: (CatchOutcome?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val stores = repository.fetchPoiForLocation(0.0, 0.0, 1).getOrNull()
            val poi = stores?.find { it.id == poiId }
            if (poi == null) { onResult(null); return@launch }

            val player = outdoor.currentLocationSync()
            val dist = player?.distanceToMeters(poi.lat, poi.lng) ?: 0f

            val trainer = Persistence.trainer()
            val engine = CatchEngine(trainer)
            val result = engine.tryCatch(poi, dist)

            if (result.caught) {
                val updated = trainer.aggiungiEsperienza(result.expGuadagnata)
                Persistence.saveTrainer(updated)
                PoiUnityBridge.sendEvent("Catch", "{\"id\":\"${poi.id}\",\"creatura\":\"${result.creature?.id}\",\"nome\":\"${result.creature?.nome}\"}")
                onResult(CatchOutcome(true, updated, result.expGuadagnata))
            } else {
                onResult(CatchOutcome(false, trainer, 0))
            }
        }
    }

    private fun android.location.Location?.distanceToMeters(lat: Double, lng: Double): Float {
        val self = this ?: return 0f
        val dLat = Math.toRadians(lat - self.latitude)
        val dLng = Math.toRadians(lng - self.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(self.latitude)) * cos(Math.toRadians(lat)) *
                sin(dLng / 2) * sin(dLng / 2)
        return (2 * 6371e3 * asin(sqrt(if (a > 1) 1.0 else a))).toFloat()
    }

    private fun sin(x: Double) = kotlin.math.sin(x)
    private fun cos(x: Double) = kotlin.math.cos(x)
    private fun asin(x: Double) = kotlin.math.asin(x)
    private fun sqrt(x: Double) = kotlin.math.sqrt(x)
}
