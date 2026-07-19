package com.intelligame.huntix

import com.intelligame.huntix.EggRarity

/** Uova posizionate nel mondo (modalità Outdoor GPS). */
data class WorldEgg(
    val id: String = "",
    val name: String = "",
    val displayLabel: String = "",
    val currentPower: Int = -1,
    val rarity: EggRarity = EggRarity.COMMON,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val found: Boolean = false
)
