package com.intelligame.huntix

/**
 * GameModule — tipi condivisi per la classificazione e difficulty scaling
 * di tutti i minigiochi (normale + AR), integrati con il sistema eventi uova.
 */
enum class GameType(val label: String, val emoji: String) {
    ARCADE("Arcade", "⚡"),
    PUZZLE("Puzzle", "🧩"),
    MEMORY("Memoria", "🧠"),
    STRATEGY("Strategia", "♟️"),
    SOLO_AR("Solo AR", "🔮");
}

enum class Difficulty(
    val level: Int,
    val levelName: String,
    val timeMultiplier: Float,
    val speedMultiplier: Float,
    val aiAggression: Float
) {
    EASY(1, "Facile", 1.8f, 0.7f, 0.3f),
    NORMAL(2, "Normale", 1.2f, 1.0f, 0.6f),
    HARD(3, "Difficile", 0.8f, 1.3f, 0.9f),
    EXPERT(4, "Esperto", 0.6f, 1.6f, 1.0f);

    companion object {
        fun fromLevel(level: Int): Difficulty = values().firstOrNull { it.level >= level } ?: EXPERT
    }
}

/**
 * EggEventType — tipi di effetti casuali applicabili alle uova durante il gioco
 * e alla fine della partita. Sistema modulare: aggiungi nuovi tipi estendendo
 * l'enum e registrando l'effetto in [EggEventEngine.registerEffect].
 */
enum class EggEventType(
    val label: String,
    val emoji: String
) {
    EXPLOSION("💥 Esplosione", "💥"),
    MAGIC("✨ Magia", "✨"),
    REWARD("🎁 Premio", "🎁"),
    HATCH("🐣 Sberla", "🐣"),
    RARE("💎 Rara", "💎"),
    PORTAL("🌀 Portale", "🌀"),
    MUSIC("🎵 Musica", "🎵"),
    GROWTH("📈 Crescita", "📈"),
    SHRINK("📉 Riduzione", "📉"),
    TELEPORT("🔮 Teletrasferimento", "🔮"),
    MELT("💧 Scioglimento", "💧"),
    FREEZE("🧊 Congelamento", "🧊"),
    STAGE_SHIFT("🔄 Cambio scena", "🔄"),
    CHAIN_REACTION("⛓️ Reazione a catena", "⛓️"),
    COMBO_BOOST("🌟 Combo", "🌟"),
    SPAWN_GAME("🎮 Nucleo gioco", "🎮");

    companion object {
        /** Eventi di gioco (durante la partita). */
        val GAME_EVENTS = listOf(
            EXPLOSION, MAGIC, REWARD, HATCH, RARE, PORTAL, MUSIC,
            GROWTH, SHRINK, TELEPORT, MELT, FREEZE, CHAIN_REACTION, COMBO_BOOST
        )

        /** Eventi di fine partita. */
        val FINAL_EVENTS = listOf(
            EXPLOSION, MAGIC, REWARD, HATCH, RARE, PORTAL, MUSIC, STAGE_SHIFT, CHAIN_REACTION
        )
    }
}

/**
 * EggEventConfig — configurazione di un singolo effetto, inclusa probabilità
 * e condizioni di attivazione (livello minimo, rarità richiesta).
 */
data class EggEventConfig(
    val type: EggEventType,
    val probability: Int,
    val minLevel: Int = 1,
    val isGameEvent: Boolean = true,
    val isFinalEvent: Boolean = true
)

/**
 * Evento applicato a un uovo: contiene il tipo e il punteggio di partenza
 * (per effetti che modificano valori scalabili).
 */
data class EggEvent(
    val type: EggEventType,
    val intensity: Float = 1f,
    val triggerTime: Long = System.currentTimeMillis()
)

/**
 * EggEventEngine — motore modulare per eventi casuali sulle uova.
 *
 * Caratteristiche:
 * - Eventi configurabili con probabilità percentuali
 * - Scaling per livello di gioco e rarità dell'uovo
 * - Estendibile: aggiungi nuovi effetti senza toccare il motore
 * - Eventi separati per "durante il gioco" e "fine partita"
 *
 * Integrazione:
 * ```
 * val engine = EggEventEngine(context)
 * engine.maybeTriggerEvent(egg, level = 3, rarity = EGG_RARITY.RARE)
 * engine.runFinalSequence(eggs, won = true)
 * ```
 */
class EggEventEngine(private val context: android.content.Context) {

    private val gameEvents = mutableListOf<EggEventConfig>()
    private val finalEvents = mutableListOf<EggEventConfig>()

    init {
        registerDefaults()
    }

    private fun registerDefaults() {
        val defaults = listOf(
            EggEventConfig(EggEventType.EXPLOSION, 35, 1),
            EggEventConfig(EggEventType.MAGIC, 12, 1),
            EggEventConfig(EggEventType.REWARD, 20, 1),
            EggEventConfig(EggEventType.HATCH, 12, 2),
            EggEventConfig(EggEventType.RARE, 8, 3),
            EggEventConfig(EggEventType.PORTAL, 4, 4),
            EggEventConfig(EggEventType.MUSIC, 5, 1)
        )
        defaults.forEach {
            gameEvents.add(it.copy(isGameEvent = true, isFinalEvent = it.type in EggEventType.FINAL_EVENTS))
            if (it.type in EggEventType.FINAL_EVENTS) {
                finalEvents.add(it.copy(isFinalEvent = true))
            }
        }
    }

    /** Registra un nuovo effetto (estendibile a runtime). */
    fun registerEffect(config: EggEventConfig) {
        if (config.isGameEvent) gameEvents.add(config)
        if (config.isFinalEvent) finalEvents.add(config)
    }

    /**
     * Tenta di attivare un evento casuale su una collana di uova.
     * @return l'evento applicato, o null se nessun evento è stato attivato.
     */
    fun maybeTriggerEvent(
        eggs: List<Any>,
        level: Int = 1,
        rng: java.util.Random = java.util.Random()
    ): EggEvent? {
        if (eggs.isEmpty()) return null
        val eligible = gameEvents.filter { it.minLevel <= level }
        if (eligible.isEmpty()) return null
        val totalWeight = eligible.sumOf { it.probability }
        if (totalWeight <= 0) return null
        var roll = rng.nextInt(totalWeight)
        for (config in eligible) {
            roll -= config.probability
            if (roll < 0) {
                val intensity = 0.5f + (level * 0.15f) + (rng.nextFloat() * 0.5f)
                return EggEvent(config.type, intensity.coerceIn(0.5f, 3f))
            }
        }
        return null
    }

    /**
     * Applica gli effetti casuali alla fine della partita.
     * @return la lista di eventi applicati (uno per uovo, se attivati).
     */
    fun runFinalSequence(
        eggs: List<Any>,
        won: Boolean,
        level: Int = 1,
        rng: java.util.Random = java.util.Random()
    ): List<Pair<Any, EggEvent>> {
        if (eggs.isEmpty()) return emptyList()
        val eligible = finalEvents.filter { it.minLevel <= level }
        if (eligible.isEmpty()) return emptyList()
        val totalWeight = eligible.sumOf { it.probability }
        val results = mutableListOf<Pair<Any, EggEvent>>()

        for (egg in eggs) {
            if (totalWeight > 0 && rng.nextInt(100) < 60) {
                var roll = rng.nextInt(totalWeight)
                for (config in eligible) {
                    roll -= config.probability
                    if (roll < 0) {
                        val intensity = if (won) 1.5f else 0.8f
                        results.add(egg to EggEvent(config.type, intensity.coerceIn(0.5f, 3f)))
                        break
                    }
                }
            }
        }
        return results
    }

    /** Restituisce le probabilità di drop in formato leggibile. */
    fun oddsDisclosure(): String {
        val total = gameEvents.sumOf { it.probability }.toFloat()
        return gameEvents.joinToString("\n") { event ->
            val pct = (event.probability / total * 100f)
            "${event.type.emoji} ${event.type.label}: ${String.format("%.1f", pct)}%"
        }
    }
}
