package com.intelligame.huntix

/**
 * EggRarity — sistema di rarità uova, stile cristallino olografico.
 *
 * Ogni rarità ha colori cristallini unici con glow neon.
 * Ispirati al concept: uova traslucide con aura luminosa pulsante.
 */
enum class EggRarity(
    val id:            String,
    val displayName:   String,
    val emoji:         String,
    val colorHex:      String,
    val glowColorHex:  String,
    val basePower:     Int,
    val xpReward:      Int,
    val spawnWeight:   Int,
    val catchRadius:   Float,
    val catchSpeed:    Float,
    val ttlMinutes:    Int,
    val namePool:      List<String>,
    val defaultElement: String = "EARTH"
) {
    COMMON(
        id           = "common",
        displayName  = "Cristallo Verde",
        emoji        = "💎",
        colorHex     = "#00CC88",       // verde cristallo
        glowColorHex = "#66FFB2",       // verde neon glow
        basePower    = 10,
        xpReward     = 25,
        spawnWeight  = 55,
        catchRadius  = 0.75f,
        catchSpeed   = 0.20f,
        ttlMinutes   = 60,
        namePool     = listOf(
            "Cristallo di Quarzo", "Cristallo di Giada", "Cristallo di Smeraldo",
            "Cristallo della Bruma", "Cristallo di Muschio", "Cristallo di Malachite",
            "Cristallo di Peridoto", "Cristallo della Foresta", "Cristallo del Ruscello",
            "Cristallo di Crisoprasio", "Cristallo di Avventurina", "Cristallo di Nefrite"
        )
    ),
    UNCOMMON(
        id           = "uncommon",
        displayName  = "Cristallo Azzurro",
        emoji        = "🔮",
        colorHex     = "#00B4FF",       // azzurro cristallo
        glowColorHex = "#66DDFF",       // cyan neon glow
        basePower    = 35,
        xpReward     = 80,
        spawnWeight  = 25,
        catchRadius  = 0.60f,
        catchSpeed   = 0.35f,
        ttlMinutes   = 45,
        namePool     = listOf(
            "Cristallo di Zaffiro", "Cristallo di Acquamarina", "Cristallo Celeste",
            "Cristallo della Rugiada", "Cristallo dell'Alba", "Cristallo di Topazio Blu",
            "Cristallo del Mare", "Cristallo di Labradorite", "Cristallo del Ghiaccio"
        ),
        defaultElement = "AIR"
    ),
    RARE(
        id           = "rare",
        displayName  = "Cristallo Viola",
        emoji        = "🔮",
        colorHex     = "#A855F7",       // viola cristallo
        glowColorHex = "#D8B4FE",       // lavanda neon glow
        basePower    = 100,
        xpReward     = 250,
        spawnWeight  = 12,
        catchRadius  = 0.45f,
        catchSpeed   = 0.55f,
        ttlMinutes   = 30,
        namePool     = listOf(
            "Cristallo di Ametista", "Cristallo della Tempesta", "Cristallo Arcano",
            "Cristallo del Crepuscolo", "Cristallo dell'Ombra Argentata",
            "Cristallo del Vento Mistico", "Cristallo del Bosco Incantato",
            "Cristallo di Tanzanite", "Cristallo del Fulmine Silente"
        )
    ),
    EPIC(
        id           = "epic",
        displayName  = "Cristallo di Fuoco",
        emoji        = "🔥",
        colorHex     = "#FF6B35",       // arancio infuocato
        glowColorHex = "#FFB088",       // arancio neon glow
        basePower    = 300,
        xpReward     = 750,
        spawnWeight  = 6,
        catchRadius  = 0.30f,
        catchSpeed   = 0.70f,
        ttlMinutes   = 20,
        namePool     = listOf(
            "Cristallo del Drago", "Cristallo del Vulcano", "Cristallo dell'Abisso",
            "Cristallo della Fenice", "Cristallo del Tuono",
            "Cristallo del Leviatano", "Cristallo della Fiamma Eterna"
        )
    ),
    LEGENDARY(
        id           = "legendary",
        displayName  = "Cristallo Cosmico",
        emoji        = "⭐",
        colorHex     = "#FFD700",       // oro cosmico
        glowColorHex = "#FFFACD",       // bianco-oro glow
        basePower    = 1000,
        xpReward     = 2500,
        spawnWeight  = 2,
        catchRadius  = 0.15f,
        catchSpeed   = 0.90f,
        ttlMinutes   = 15,
        namePool     = listOf(
            "Cristallo del Cosmo", "Cristallo dell'Aurora Boreale",
            "Cristallo del Tempo", "Cristallo del Grande Spirito",
            "Cristallo della Creazione", "Cristallo dell'Infinito"
        )
    );

    val color: Int get() = parseHexColor(colorHex)
    val glowColor: Int get() = parseHexColor(glowColorHex)
    val actionRadiusM: Double get() = 70.0
    fun randomName(): String = namePool.random()

    companion object {
        /** Parser hex→Int puro (0xAARRGGBB), sostituisce android.graphics.Color.parseColor. */
        fun parseHexColor(hex: String): Int {
            val h = hex.removePrefix("#")
            require(h.length == 6) { "Hex color non valido: $hex" }
            val r = h.substring(0, 2).toInt(16)
            val g = h.substring(2, 4).toInt(16)
            val b = h.substring(4, 6).toInt(16)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        fun weightedRandom(): EggRarity {
            val total = values().sumOf { it.spawnWeight }
            var roll  = (Math.random() * total).toInt()
            for (rarity in values()) {
                roll -= rarity.spawnWeight
                if (roll < 0) return rarity
            }
            return COMMON
        }
        fun fromId(id: String): EggRarity =
            values().firstOrNull { it.id == id } ?: COMMON

        // ✅ FIX v7.2.1: Loot Box compliance — disclosure probabilità
        /**
         * Calcola e restituisce le probabilità di drop in formato leggibile.
         * Basato sui spawnWeight reali dell'enum.
         */
        fun oddsDisclosure(): String {
            val total = values().sumOf { it.spawnWeight }.toFloat()
            return values().joinToString("\n") { rarity ->
                val pct = (rarity.spawnWeight / total * 100f)
                "${rarity.emoji} ${rarity.displayName}: ${"%.1f".format(pct)}%"
            }
        }

        /**
         * Restituisce le odds come lista di Pair(displayName, percentuale).
         */
        fun oddsAsList(): List<Pair<String, Float>> {
            val total = values().sumOf { it.spawnWeight }.toFloat()
            return values().map { it.displayName to (it.spawnWeight / total * 100f) }
        }
    }
}
