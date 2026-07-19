package com.intelligame.huntix.battle

/** Dati minima di una creatura usata in battaglia. */
data class CreatureData(
    val id: String = "",
    val name: String = "Creatura",
    val rarityId: String = "common",
    val power: Long = 0
)
