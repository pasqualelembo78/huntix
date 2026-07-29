package com.intelligame.huntix.ui

import com.intelligame.huntix.EggElement

enum class CaptureMethod(
    val id: String,
    val displayName: String,
    val description: String,
    val emoji: String,
    val difficultyLabel: String
) {
    SWIPE_LEGACY(
        id = "swipe",
        displayName = "Swipe Classico",
        description = "Swipe verso l'alto per lanciare l'uovo nel cestino",
        emoji = "👆",
        difficultyLabel = "Facile"
    ),
    CONCENTRATION(
        id = "concentration",
        displayName = "Concentrazione",
        description = "Premi quando il cerchio è al minimo per massimizzare la cattura",
        emoji = "🎯",
        difficultyLabel = "Media"
    ),
    PATTERN_TRACE(
        id = "pattern",
        displayName = "Disegna il Simbolo",
        description = "Traccia il simbolo elementale con il dito",
        emoji = "✏️",
        difficultyLabel = "Media"
    ),
    QUICK_CATCH(
        id = "quick",
        displayName = "Quick Catch",
        description = "Colpisci l'uovo quando passa nella zona bersaglio",
        emoji = "⚡",
        difficultyLabel = "Facile"
    ),
    RHYTHM_TAP(
        id = "rhythm",
        displayName = "Ritmo",
        description = "Tocca a tempo con il battito dell'uovo",
        emoji = "🎵",
        difficultyLabel = "Difficile"
    ),
    ELEMENT_SHIELD(
        id = "shield",
        displayName = "Scudo Elementale",
        description = "Rompi lo scudo usando sensori e voce in base all'elemento",
        emoji = "🛡️",
        difficultyLabel = "Media"
    );

    companion object {
        fun fromId(id: String): CaptureMethod =
            values().firstOrNull { it.id == id } ?: SWIPE_LEGACY

        fun getDescriptionForElement(method: CaptureMethod, element: EggElement): String {
            if (method != ELEMENT_SHIELD) return method.description
            return when (element) {
                EggElement.FIRE -> "Soffia nel microfono per spegnere la fiamma!"
                EggElement.WATER -> "Inclina il telefono per riempire la vasca!"
                EggElement.EARTH -> "Tocca velocemente per frantumare la roccia!"
                EggElement.AIR -> "Ruota il telefono per creare un vortice!"
                EggElement.NORMAL -> "Tocca l'uovo per rompere lo scudo!"
            }
        }
    }
}
