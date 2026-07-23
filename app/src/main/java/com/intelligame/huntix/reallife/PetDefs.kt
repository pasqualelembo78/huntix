package com.intelligame.huntix.reallife

/**
 * PetDefs — definizioni animali domestici disponibili.
 */
enum class PetType { DOG, CAT }

data class PetDef(
    val type: PetType,
    val name: String,
    val emoji: String,
    val bodyColor: Int,
    val headColor: Int,
    val size: Float,       // scala generale
    val speed: Float       // velocità di inseguimento
)

object Pets {
    val AVAILABLE = listOf(
        PetDef(PetType.DOG, "Cane", "\uD83D\uDC15",
            0xFF8D6E63.toInt(), 0xFF795548.toInt(), 1f, 3.5f),
        PetDef(PetType.CAT, "Gatto", "\uD83D\uDC08",
            0xFFEF6C00.toInt(), 0xFFE65100.toInt(), 0.8f, 2.8f)
    )
}
