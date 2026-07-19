package com.intelligame.huntix.battle

import kotlin.random.Random

/** Gestisce lo spawn del nemico per una battaglia. */
object BattleSpawnManager {

    enum class SpawnEvent { NORMAL, RARE, UNSTABLE }

    data class SpawnResult(
        val creature: CreatureData,
        val element: ElementType,
        val difficultyScale: Float,
        val event: SpawnEvent
    )

    private val RARITIES = listOf("common", "uncommon", "rare", "epic", "legendary")
    private val ELEMENTS = ElementType.values()
    private val NAMES = listOf("Pixel", "Volt", "Ember", "Aqua", "Terra", "Zephyr", "Nova", "Glimmer")

    /** Genera un nemico casuale per la battaglia. */
    fun spawnEnemy(): SpawnResult {
        val rarity = RARITIES.random()
        val element = ELEMENTS.random()
        val creature = CreatureData(
            id = "gen_${Random.nextInt(10000)}",
            name = NAMES.random(),
            rarityId = rarity,
            power = Random.nextLong(50, 500)
        )
        val event = when {
            rarity in listOf("epic", "legendary") -> SpawnEvent.RARE
            Random.nextFloat() < 0.15f -> SpawnEvent.UNSTABLE
            else -> SpawnEvent.NORMAL
        }
        val scale = when (rarity) {
            "legendary" -> 1.8f
            "epic" -> 1.5f
            "rare" -> 1.25f
            "uncommon" -> 1.1f
            else -> 1.0f
        }
        return SpawnResult(creature, element, scale, event)
    }
}
