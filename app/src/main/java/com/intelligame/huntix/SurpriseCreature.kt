package com.intelligame.huntix

import android.graphics.Color

data class SurpriseCreature(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val rarityId: String,
    val zoneAffinity: List<String> = emptyList(),
    val weatherAffinity: List<String> = emptyList(),
    val baseHp: Int,
    val baseAttack: Int,
    val baseDefense: Int,
    val baseSpeed: Int,
    val specialMoveName: String,
    val specialMoveEmoji: String,
    val specialMoveDamage: Int,
    val specialMoveEffect: String = ""
) {
    val rarity: EggRarity get() = EggRarity.fromId(rarityId)

    companion object {
        val ALL: List<SurpriseCreature> = listOf(
            // === COMMON ===
            SurpriseCreature("pulcino", "Pulcino", "\uD83D\uDC25", "Un dolce pulcino giallo pieno di energia!",
                "common", listOf("countryside", "city"), listOf("clear"),
                baseHp = 45, baseAttack = 35, baseDefense = 25, baseSpeed = 50,
                specialMoveName = "Becco Fulmine", specialMoveEmoji = "\u26A1",
                specialMoveDamage = 40, specialMoveEffect = "stun"),
            SurpriseCreature("coniglietto", "Coniglietto", "\uD83D\uDC30", "Saltella velocissimo tra i prati!",
                "common", listOf("countryside", "forest"), listOf("clear", "cloudy"),
                baseHp = 40, baseAttack = 30, baseDefense = 30, baseSpeed = 65,
                specialMoveName = "Balzo Fulmine", specialMoveEmoji = "\uD83D\uDCA8",
                specialMoveDamage = 35, specialMoveEffect = ""),
            SurpriseCreature("agnellino", "Agnellino", "\uD83D\uDC11", "Soffice come una nuvola!",
                "common", listOf("countryside", "mountain"), listOf("cloudy"),
                baseHp = 55, baseAttack = 25, baseDefense = 40, baseSpeed = 35,
                specialMoveName = "Vello d'Acciaio", specialMoveEmoji = "\uD83D\uDEE1\uFE0F",
                specialMoveDamage = 20, specialMoveEffect = ""),
            SurpriseCreature("farfalla", "Farfalla Arcobaleno", "\uD83E\uDD8B", "Ali che brillano di mille colori.",
                "common", listOf("forest", "countryside"), listOf("clear"),
                baseHp = 35, baseAttack = 40, baseDefense = 20, baseSpeed = 70,
                specialMoveName = "Polvere Incantata", specialMoveEmoji = "\u2728",
                specialMoveDamage = 45, specialMoveEffect = "confuse"),
            // === UNCOMMON ===
            SurpriseCreature("volpe_luna", "Volpe della Luna", "\uD83E\uDD8A", "Compare solo di notte sotto la luna piena.",
                "uncommon", listOf("forest", "countryside"), listOf("night"),
                baseHp = 65, baseAttack = 60, baseDefense = 45, baseSpeed = 75,
                specialMoveName = "Morso Lunare", specialMoveEmoji = "\uD83C\uDF19",
                specialMoveDamage = 70, specialMoveEffect = "poison"),
            SurpriseCreature("cerbiatto", "Cerbiatto Magico", "\uD83E\uDEE6", "Corre veloce come il vento tra i boschi.",
                "uncommon", listOf("forest", "mountain"), listOf("cloudy", "clear"),
                baseHp = 70, baseAttack = 55, baseDefense = 50, baseSpeed = 80,
                specialMoveName = "Carica Forestale", specialMoveEmoji = "\uD83C\uDF3F",
                specialMoveDamage = 65, specialMoveEffect = ""),
            SurpriseCreature("gufo_stellato", "Gufo Stellato", "\uD83E\uDD89", "Veglia sulle stelle nella notte.",
                "uncommon", listOf("mountain", "forest"), listOf("night", "cloudy"),
                baseHp = 60, baseAttack = 70, baseDefense = 40, baseSpeed = 60,
                specialMoveName = "Occhi di Stelle", specialMoveEmoji = "\u2B50",
                specialMoveDamage = 75, specialMoveEffect = "stun"),
            // === RARE ===
            SurpriseCreature("drago_pasquale", "Drago Pasquale", "\uD83D\uDC32", "Un drago dalle uova dorate!",
                "rare", listOf("mountain", "rock"), listOf("clear", "storm"),
                baseHp = 100, baseAttack = 90, baseDefense = 70, baseSpeed = 60,
                specialMoveName = "Soffio Dorato", specialMoveEmoji = "\uD83D\uDD25",
                specialMoveDamage = 110, specialMoveEffect = "burn"),
            SurpriseCreature("fenice_rosa", "Fenice Rosa", "\uD83E\uDD9C", "Rinasce sempre pi\u00F9 forte dalle fiamme!",
                "rare", listOf("mountain", "snow"), listOf("clear", "wind"),
                baseHp = 90, baseAttack = 95, baseDefense = 60, baseSpeed = 85,
                specialMoveName = "Rinascita di Fuoco", specialMoveEmoji = "\uD83C\uDF05",
                specialMoveDamage = 100, specialMoveEffect = "burn"),
            SurpriseCreature("unicorno", "Unicorno Arcobaleno", "\uD83E\uDD84", "Il suo corno porta fortuna!",
                "rare", listOf("countryside", "forest"), listOf("rain", "clear"),
                baseHp = 95, baseAttack = 85, baseDefense = 80, baseSpeed = 75,
                specialMoveName = "Raggio Arcobaleno", specialMoveEmoji = "\uD83C\uDF08",
                specialMoveDamage = 95, specialMoveEffect = "confuse"),
            // === EPIC ===
            SurpriseCreature("behemoth", "Behemoth di Cristallo", "\uD83D\uDC8E", "Un colosso di cristallo puro!",
                "epic", listOf("rock", "mountain"), listOf("cloudy", "fog"),
                baseHp = 160, baseAttack = 130, baseDefense = 120, baseSpeed = 40,
                specialMoveName = "Frantumazione Cristallina", specialMoveEmoji = "\uD83D\uDC8E",
                specialMoveDamage = 160, specialMoveEffect = "stun"),
            SurpriseCreature("leviatan", "Leviatano Dorato", "\uD83D\uDC09", "Il re dei mari abissali!",
                "epic", listOf("water"), listOf("rain", "storm"),
                baseHp = 150, baseAttack = 140, baseDefense = 100, baseSpeed = 65,
                specialMoveName = "Tsunami Dorato", specialMoveEmoji = "\uD83C\uDF0A",
                specialMoveDamage = 170, specialMoveEffect = "freeze"),
            // === LEGENDARY ===
            SurpriseCreature("grande_coniglio", "Il Grande Coniglio Cosmico", "\uD83D\uDC07", "Il guardiano cosmico di tutte le uova!",
                "legendary", listOf("city", "countryside"), listOf("clear", "night"),
                baseHp = 250, baseAttack = 180, baseDefense = 150, baseSpeed = 120,
                specialMoveName = "Pugno Cosmico", specialMoveEmoji = "\uD83C\uDF0C",
                specialMoveDamage = 250, specialMoveEffect = "stun"),
            SurpriseCreature("uovo_creatore", "L'Uovo del Creatore", "\uD83E\uDD5A", "L'uovo primordiale da cui tutto ha avuto origine.",
                "legendary", listOf(), listOf(),
                baseHp = 300, baseAttack = 200, baseDefense = 200, baseSpeed = 100,
                specialMoveName = "Big Bang Pasquale", specialMoveEmoji = "\u2728",
                specialMoveDamage = 300, specialMoveEffect = "")
        )

        fun forRarity(rarityId: String): List<SurpriseCreature> =
            ALL.filter { it.rarityId == rarityId }

        fun forZone(zoneType: ZoneType): List<SurpriseCreature> =
            ALL.filter { it.zoneAffinity.isEmpty() || it.zoneAffinity.contains(zoneType.id) }

        fun pickForHatch(rarityId: String, zoneType: ZoneType, weatherType: WeatherType): SurpriseCreature {
            val candidates = ALL.filter { creature ->
                creature.rarityId == rarityId &&
                (creature.zoneAffinity.isEmpty() || creature.zoneAffinity.contains(zoneType.id))
            }
            if (candidates.isEmpty()) return ALL.filter { it.rarityId == rarityId }.random()
            val weighted = candidates.flatMap { c ->
                val w = if (c.weatherAffinity.contains(weatherType.id)) 3 else 1
                List(w) { c }
            }
            return weighted.random()
        }

        fun getById(id: String?): SurpriseCreature? = if (id.isNullOrBlank()) null else ALL.firstOrNull { it.id == id }
    }
}

data class OwnedSurprise(
    val id: String = java.util.UUID.randomUUID().toString(),
    val creatureId: String,
    val nickname: String = "",
    val level: Int = 1,
    val xp: Int = 0,
    val hp: Int = 0,
    val catchZone: String = "unknown",
    val catchWeather: String = "clear",
    val caughtAt: Long = System.currentTimeMillis(),
    val inBattleTeam: Boolean = false,
    val isBuddy: Boolean = false,
    val candies: Int = 0
) {
    val creature: SurpriseCreature? get() = SurpriseCreature.getById(creatureId)
    val displayName: String get() = if (!nickname.isNullOrBlank()) nickname else creature?.name ?: (creatureId ?: "???")
    val displayEmoji: String get() = creature?.emoji ?: "❓"
    val rarityId: String get() = creature?.rarityId ?: "common"

    fun scaledHp(level: Int = this.level): Int = ((creature?.baseHp ?: 45) * (1 + level * 0.1f)).toInt()
    fun scaledAttack(level: Int = this.level): Int = ((creature?.baseAttack ?: 35) * (1 + level * 0.08f)).toInt()
    fun scaledDefense(level: Int = this.level): Int = ((creature?.baseDefense ?: 25) * (1 + level * 0.08f)).toInt()
    fun scaledSpeed(level: Int = this.level): Int = ((creature?.baseSpeed ?: 50) * (1 + level * 0.05f)).toInt()
}
