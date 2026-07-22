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
    val specialMoveEffect: String = "",
    val evolvesTo: String? = null,
    val candyCost: Int = 0
) {
    val rarity: EggRarity get() = EggRarity.fromId(rarityId)
    val canEvolve: Boolean get() = evolvesTo != null && candyCost > 0

    fun getEvolvedCreature(): SurpriseCreature? =
        evolvesTo?.let { getById(it) }

    companion object {
        val ALL: List<SurpriseCreature> = listOf(
            // === COMMON (base forms) ===
            SurpriseCreature("pulcino", "Pulcino", "\uD83D\uDC25", "Un dolce pulcino giallo pieno di energia!",
                "common", listOf("countryside", "city"), listOf("clear"),
                baseHp = 45, baseAttack = 35, baseDefense = 25, baseSpeed = 50,
                specialMoveName = "Becco Fulmine", specialMoveEmoji = "\u26A1",
                specialMoveDamage = 40, specialMoveEffect = "stun",
                evolvesTo = "gallo_ardito", candyCost = 25),
            SurpriseCreature("coniglietto", "Coniglietto", "\uD83D\uDC30", "Saltella velocissimo tra i prati!",
                "common", listOf("countryside", "forest"), listOf("clear", "cloudy"),
                baseHp = 40, baseAttack = 30, baseDefense = 30, baseSpeed = 65,
                specialMoveName = "Balzo Fulmine", specialMoveEmoji = "\uD83D\uDCA8",
                specialMoveDamage = 35, specialMoveEffect = "",
                evolvesTo = "lepre_fulmine", candyCost = 25),
            SurpriseCreature("agnellino", "Agnellino", "\uD83D\uDC11", "Soffice come una nuvola!",
                "common", listOf("countryside", "mountain"), listOf("cloudy"),
                baseHp = 55, baseAttack = 25, baseDefense = 40, baseSpeed = 35,
                specialMoveName = "Vello d'Acciaio", specialMoveEmoji = "\uD83D\uDEE1\uFE0F",
                specialMoveDamage = 20, specialMoveEffect = "",
                evolvesTo = "montone_ferro", candyCost = 25),
            SurpriseCreature("farfalla", "Farfalla Arcobaleno", "\uD83E\uDD8B", "Ali che brillano di mille colori.",
                "common", listOf("forest", "countryside"), listOf("clear"),
                baseHp = 35, baseAttack = 40, baseDefense = 20, baseSpeed = 70,
                specialMoveName = "Polvere Incantata", specialMoveEmoji = "\u2728",
                specialMoveDamage = 45, specialMoveEffect = "confuse",
                evolvesTo = "falena_arcobaleno", candyCost = 25),
            SurpriseCreature("gatto_ombra", "Gatto dell'Ombra", "\uD83D\uDC31", "Scivola silenziosamente tra le ombre.",
                "common", listOf("city", "forest"), listOf("night"),
                baseHp = 42, baseAttack = 38, baseDefense = 28, baseSpeed = 62,
                specialMoveName = "Artigli Oscuri", specialMoveEmoji = "\uD83D\uDDE1\uFE0F",
                specialMoveDamage = 42, specialMoveEffect = "poison",
                evolvesTo = "pantera_notte", candyCost = 25),

            // === UNCOMMON (base forms) ===
            SurpriseCreature("volpe_luna", "Volpe della Luna", "\uD83E\uDD8A", "Compare solo di notte sotto la luna piena.",
                "uncommon", listOf("forest", "countryside"), listOf("night"),
                baseHp = 65, baseAttack = 60, baseDefense = 45, baseSpeed = 75,
                specialMoveName = "Morso Lunare", specialMoveEmoji = "\uD83C\uDF19",
                specialMoveDamage = 70, specialMoveEffect = "poison",
                evolvesTo = "lupo_lunare", candyCost = 50),
            SurpriseCreature("cerbiatto", "Cerbiatto Magico", "\uD83E\uDEE6", "Corre veloce come il vento tra i boschi.",
                "uncommon", listOf("forest", "mountain"), listOf("cloudy", "clear"),
                baseHp = 70, baseAttack = 55, baseDefense = 50, baseSpeed = 80,
                specialMoveName = "Carica Forestale", specialMoveEmoji = "\uD83C\uDF3F",
                specialMoveDamage = 65, specialMoveEffect = "",
                evolvesTo = "alce_foresta", candyCost = 50),
            SurpriseCreature("gufo_stellato", "Gufo Stellato", "\uD83E\uDD89", "Veglia sulle stelle nella notte.",
                "uncommon", listOf("mountain", "forest"), listOf("night", "cloudy"),
                baseHp = 60, baseAttack = 70, baseDefense = 40, baseSpeed = 60,
                specialMoveName = "Occhi di Stelle", specialMoveEmoji = "\u2B50",
                specialMoveDamage = 75, specialMoveEffect = "stun",
                evolvesTo = "grifone_stellare", candyCost = 50),
            SurpriseCreature("serpente_verde", "Serpente Verde", "\uD83D\uDC0D", "Striscia tra le foglie con eleganza.",
                "uncommon", listOf("forest", "countryside"), listOf("clear", "rain"),
                baseHp = 55, baseAttack = 65, baseDefense = 50, baseSpeed = 70,
                specialMoveName = "Veleno Verde", specialMoveEmoji = "\uD83E\uDDEA",
                specialMoveDamage = 72, specialMoveEffect = "poison",
                evolvesTo = "drago_verde", candyCost = 50),

            // === RARE (base forms) ===
            SurpriseCreature("drago_pasquale", "Drago Pasquale", "\uD83D\uDC32", "Un drago dalle uova dorate!",
                "rare", listOf("mountain", "rock"), listOf("clear", "storm"),
                baseHp = 100, baseAttack = 90, baseDefense = 70, baseSpeed = 60,
                specialMoveName = "Soffio Dorato", specialMoveEmoji = "\uD83D\uDD25",
                specialMoveDamage = 110, specialMoveEffect = "burn",
                evolvesTo = "drago_alba", candyCost = 100),
            SurpriseCreature("fenice_rosa", "Fenice Rosa", "\uD83E\uDD9C", "Rinasce sempre pi\u00F9 forte dalle fiamme!",
                "rare", listOf("mountain", "snow"), listOf("clear", "wind"),
                baseHp = 90, baseAttack = 95, baseDefense = 60, baseSpeed = 85,
                specialMoveName = "Rinascita di Fuoco", specialMoveEmoji = "\uD83C\uDF05",
                specialMoveDamage = 100, specialMoveEffect = "burn",
                evolvesTo = "fenice_aurora", candyCost = 100),
            SurpriseCreature("unicorno", "Unicorno Arcobaleno", "\uD83E\uDD84", "Il suo corno porta fortuna!",
                "rare", listOf("countryside", "forest"), listOf("rain", "clear"),
                baseHp = 95, baseAttack = 85, baseDefense = 80, baseSpeed = 75,
                specialMoveName = "Raggio Arcobaleno", specialMoveEmoji = "\uD83C\uDF08",
                specialMoveDamage = 95, specialMoveEffect = "confuse",
                evolvesTo = "alicorno_divino", candyCost = 100),

            // === EPIC (evolved forms + base) ===
            SurpriseCreature("gallo_ardito", "Gallo Ardito", "\uD83D\uDC13", "Il suo canto risveglia le foreste!",
                "epic", listOf("countryside", "city"), listOf("clear", "wind"),
                baseHp = 90, baseAttack = 80, baseDefense = 60, baseSpeed = 75,
                specialMoveName = "Canto di Guerra", specialMoveEmoji = "\uD83C\uDFB5",
                specialMoveDamage = 95, specialMoveEffect = "stun"),
            SurpriseCreature("lepre_fulmine", "Lepre Fulmine", "\uD83D\uDC07\u26A1", "Un lampo tra gli alberi!",
                "epic", listOf("countryside", "forest"), listOf("clear", "storm"),
                baseHp = 80, baseAttack = 75, baseDefense = 55, baseSpeed = 95,
                specialMoveName = "Tuono Vivente", specialMoveEmoji = "\u26A1",
                specialMoveDamage = 100, specialMoveEffect = "stun"),
            SurpriseCreature("montone_ferro", "Montone di Ferro", "\uD83D\uDC11\u2694\uFE0F", "Il suo corna sono indistruttibili!",
                "epic", listOf("mountain", "countryside"), listOf("cloudy", "fog"),
                baseHp = 120, baseAttack = 70, baseDefense = 90, baseSpeed = 35,
                specialMoveName = "Carica di Ferro", specialMoveEmoji = "\u2694\uFE0F",
                specialMoveDamage = 85, specialMoveEffect = ""),
            SurpriseCreature("falena_arcobaleno", "Falena Arcobaleno", "\uD83E\uDD8B\u2728", "Ali che proiettano arcobaleni!",
                "epic", listOf("forest", "countryside"), listOf("clear", "night"),
                baseHp = 75, baseAttack = 85, baseDefense = 50, baseSpeed = 90,
                specialMoveName = "Luce Prismatica", specialMoveEmoji = "\uD83C\uDF08",
                specialMoveDamage = 105, specialMoveEffect = "confuse"),
            SurpriseCreature("pantera_notte", "Pantera della Notte", "\uD83D\uDC31\u2B50", "Regina delle tenebre.",
                "epic", listOf("city", "forest"), listOf("night"),
                baseHp = 85, baseAttack = 90, baseDefense = 60, baseSpeed = 85,
                specialMoveName = "Assalto Notturno", specialMoveEmoji = "\uD83C\uDF19",
                specialMoveDamage = 105, specialMoveEffect = "poison"),
            SurpriseCreature("lupo_lunare", "Lupo Lunare", "\uD83D\uDC3A\uD83C\uDF19", "Ulula alla luna per guadagnare potere!",
                "epic", listOf("forest", "mountain"), listOf("night", "clear"),
                baseHp = 110, baseAttack = 95, baseDefense = 70, baseSpeed = 80,
                specialMoveName = "Ululato Lunare", specialMoveEmoji = "\uD83C\uDF19",
                specialMoveDamage = 115, specialMoveEffect = "poison"),
            SurpriseCreature("alce_foresta", "Alce della Foresta", "\uD83E\uDD8C", "Il guardiano della foresta antica.",
                "epic", listOf("forest", "mountain"), listOf("cloudy", "clear"),
                baseHp = 130, baseAttack = 80, baseDefense = 85, baseSpeed = 60,
                specialMoveName = "Cornata Titanica", specialMoveEmoji = "\uD83C\uDF32",
                specialMoveDamage = 110, specialMoveEffect = ""),
            SurpriseCreature("grifone_stellare", "Grifone Stellare", "\uD83E\uDD85\u2B50", "Volta tra le stelle con maestà.",
                "epic", listOf("mountain", "city"), listOf("night", "clear"),
                baseHp = 100, baseAttack = 105, baseDefense = 65, baseSpeed = 85,
                specialMoveName = "Artigli Stellari", specialMoveEmoji = "\u2B50",
                specialMoveDamage = 120, specialMoveEffect = "stun"),
            SurpriseCreature("drago_verde", "Drago Verde", "\uD83D\uDC32\uD83C\uDF3F", "Un drago avvolto nella foresta.",
                "epic", listOf("forest", "mountain"), listOf("rain", "clear"),
                baseHp = 115, baseAttack = 100, baseDefense = 80, baseSpeed = 70,
                specialMoveName = "Sputo di Veleno", specialMoveEmoji = "\uD83E\uDDEA",
                specialMoveDamage = 125, specialMoveEffect = "poison"),
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

            // === LEGENDARY (evolved forms + base) ===
            SurpriseCreature("drago_alba", "Drago dell'Alba", "\uD83D\uDC32\uD83C\uDF05", "Risplende come il primo sole!",
                "legendary", listOf("mountain", "city"), listOf("clear", "storm"),
                baseHp = 180, baseAttack = 160, baseDefense = 110, baseSpeed = 90,
                specialMoveName = "Luce dell'Alba", specialMoveEmoji = "\uD83C\uDF05",
                specialMoveDamage = 200, specialMoveEffect = "burn"),
            SurpriseCreature("fenice_aurora", "Fenice dell'Aurora", "\uD83E\uDD9C\uD83C\uDF05", "Rinasce ogni alba con potere infinito!",
                "legendary", listOf("mountain", "snow"), listOf("clear", "wind"),
                baseHp = 160, baseAttack = 170, baseDefense = 100, baseSpeed = 110,
                specialMoveName = "Raggio dell'Aurora", specialMoveEmoji = "\uD83C\uDF05",
                specialMoveDamage = 190, specialMoveEffect = "burn"),
            SurpriseCreature("alicorno_divino", "Alicorno Divino", "\uD83E\uDD84\uD83C\uDF08", "L'essere più puro dell'universo!",
                "legendary", listOf("countryside", "forest", "mountain"), listOf("clear", "rain"),
                baseHp = 170, baseAttack = 155, baseDefense = 130, baseSpeed = 100,
                specialMoveName = "Giudizio Divino", specialMoveEmoji = "\uD83C\uDF1F",
                specialMoveDamage = 185, specialMoveEffect = "confuse"),
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
    val displayName: String get() = if (!nickname.isNullOrBlank()) nickname else creature?.name ?: "???"
    val displayEmoji: String get() = creature?.emoji ?: "❓"
    val rarityId: String get() = creature?.rarityId ?: "common"

    fun scaledHp(level: Int = this.level): Int = ((creature?.baseHp ?: 45) * (1 + level * 0.1f)).toInt()
    fun scaledAttack(level: Int = this.level): Int = ((creature?.baseAttack ?: 35) * (1 + level * 0.08f)).toInt()
    fun scaledDefense(level: Int = this.level): Int = ((creature?.baseDefense ?: 25) * (1 + level * 0.08f)).toInt()
    fun scaledSpeed(level: Int = this.level): Int = ((creature?.baseSpeed ?: 50) * (1 + level * 0.05f)).toInt()
}
