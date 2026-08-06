package com.intelligame.huntix.legacy.poi.creature

import com.intelligame.huntix.legacy.poi.data.PoiStore
import com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge
import kotlin.random.Random

class CatchEngine(private val trainer: Trainer) {

    data class CatchResult(val caught: Boolean, val creature: Creature?, val expGuadagnata: Int)

    fun tryCatch(poi: PoiStore, distance: Float): CatchResult {
        val specie = speciePerPoi(poi)
        val dentroRaggio = distance <= CATCH_RAGGIO
        if (!dentroRaggio) return CatchResult(false, null, 0)

        val roll = rollCattura(specie.rarita)
        val caught = if (roll) Random.nextDouble(0.0, 1.0) < probabilita(specie.rarita) else false
        val exp = if (caught) specie.expBase else 0
        if (caught) notifyCatch(specie, poi)

        return CatchResult(caught, if (caught) specie else null, exp)
    }

    private fun rollCattura(rarity: Rarity): Boolean = when (rarity) {
        Rarity.Scoperta -> Random.nextDouble(0.0, 1.0) < 0.95
        else -> Random.nextDouble(0.0, 1.0) < 0.8
    }

    private fun probabilita(rarity: Rarity): Double = when (rarity) {
        Rarity.Scoperta -> 0.95
        Rarity.Comune -> 0.7
        Rarity.Rara -> 0.4
        Rarity.Epicca -> 0.2
        Rarity.Leggendaria -> 0.05
    }

    private fun notifyCatch(creature: Creature, poi: PoiStore) {
        val json = "{\"id\":\"${poi.id}\",\"creatura\":\"${creature.id}\",\"nome\":\"${creature.nome}\"," +
                "\"rarita\":\"${creature.rarita}\"}"
        PoiUnityBridge.sendEvent("Catch", json)
    }

    private fun speciePerPoi(poi: PoiStore): Creature = Creature(
        id = poi.id,
        nome = poi.name,
        elemento = Elemento.entries.random(),
        rarita = raritaPerTipo(poi.poiType),
        expBase = expBasePerRarita(Rarity.Comune)
    )

    private fun raritaPerTipo(poiType: String): Rarity = when (poiType.lowercase()) {
        "ristorante" -> Rarity.Comune
        "supermarket", "shopping" -> Rarity.Rara
        "ospite", "hospital" -> Rarity.Epicca
        "monumento", "church" -> Rarity.Leggendaria
        else -> Rarity.Scoperta
    }

    private fun expBasePerRarita(r: Rarity) = when (r) {
        Rarity.Scoperta -> 10; Rarity.Comune -> 25
        Rarity.Rara -> 75; Rarity.Epicca -> 250
        Rarity.Leggendaria -> 1000
    }

    companion object {
        const val CATCH_RAGGIO = 40f
    }
}
