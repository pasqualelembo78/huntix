package com.intelligame.huntix.gamification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * LiveEventManager — eventi temporizzati stile Brawl Stars.
 *
 * Firestore: live_events/{eventId}  (gestito lato server/admin)
 * Locale: calcola eventi attivi in base al timestamp corrente
 *
 * Tipi eventi:
 *  - EGG_RUSH: spawn rate uova x3
 *  - DOUBLE_XP: XP doppio per ogni uovo
 *  - LEGENDARY_WEEK: probabilità leggendarie +300%
 *  - SPEED_CHALLENGE: sfida velocità (chi trova più uova in 30min)
 *  - TEAM_BATTLE: competizione tra squadre
 *  - MYSTERY_EGGS: solo uova misteriose
 *  - GOLDEN_HOUR: 1 ora con tutte le ricompense x5
 */
object LiveEventManager {

    data class LiveEvent(
        val id: String,
        val type: EventType,
        val title: String,
        val description: String,
        val emoji: String,
        val colorHex: String,
        val startMs: Long,
        val endMs: Long,
        val xpMultiplier: Float = 1f,
        val eggSpawnMultiplier: Float = 1f,
        val legendaryChanceBonus: Float = 0f,
        val rewardGems: Int = 0
    ) {
        val isActive: Boolean get() = System.currentTimeMillis() in startMs..endMs
        val isUpcoming: Boolean get() = System.currentTimeMillis() < startMs
        val isExpired: Boolean get() = System.currentTimeMillis() > endMs
        val remainingMs: Long get() = maxOf(0L, endMs - System.currentTimeMillis())
        val remainingMinutes: Int get() = (remainingMs / 60_000).toInt()
        val startsInMs: Long get() = maxOf(0L, startMs - System.currentTimeMillis())
        val progressPercent: Int get() {
            if (!isActive) return 0
            val total = (endMs - startMs).toFloat()
            val elapsed = (System.currentTimeMillis() - startMs).toFloat()
            return (100 - (elapsed / total * 100)).toInt().coerceIn(0, 100)
        }
    }

    enum class EventType { EGG_RUSH, DOUBLE_XP, LEGENDARY_WEEK, SPEED_CHALLENGE, TEAM_BATTLE, MYSTERY_EGGS, GOLDEN_HOUR }

    // ─── Calendario eventi (rotazione settimanale) ───────────────

    fun getCurrentAndUpcomingEvents(): List<LiveEvent> {
        val now = System.currentTimeMillis()
        // Base di riferimento: lunedì della settimana corrente
        val weekMs = (now / (7 * 86_400_000L)) * (7 * 86_400_000L)
        val dayMs = 86_400_000L
        val hourMs = 3_600_000L

        return listOf(
            LiveEvent(
                id = "egg_rush_${weekMs}",
                type = EventType.EGG_RUSH,
                title = "🥚 Egg Rush!",
                description = "Le uova spawnan 3x più velocemente. Caccia finché puoi!",
                emoji = "🥚", colorHex = "#FF4CAF50",
                startMs = weekMs + dayMs,           // martedì
                endMs = weekMs + dayMs + 4 * hourMs,
                eggSpawnMultiplier = 3f, rewardGems = 5
            ),
            LiveEvent(
                id = "double_xp_${weekMs}",
                type = EventType.DOUBLE_XP,
                title = "⚡ Doppio XP",
                description = "Ogni uovo trovato vale il doppio dell'XP per 3 ore!",
                emoji = "⚡", colorHex = "#FF2196F3",
                startMs = weekMs + 2 * dayMs + 18 * hourMs,   // mercoledì sera
                endMs = weekMs + 2 * dayMs + 21 * hourMs,
                xpMultiplier = 2f, rewardGems = 3
            ),
            LiveEvent(
                id = "legendary_${weekMs}",
                type = EventType.LEGENDARY_WEEK,
                title = "⭐ Settimana Leggendaria",
                description = "Probabilità uova leggendarie aumentata del 300%!",
                emoji = "⭐", colorHex = "#FF1A3A4A",
                startMs = weekMs + 4 * dayMs,       // venerdì
                endMs = weekMs + 5 * dayMs,
                legendaryChanceBonus = 3.0f, rewardGems = 10
            ),
            LiveEvent(
                id = "golden_hour_${weekMs}",
                type = EventType.GOLDEN_HOUR,
                title = "✨ Golden Hour",
                description = "Tutte le ricompense x5 per 1 ora! Non perderla!",
                emoji = "✨", colorHex = "#FFFF8F00",
                startMs = weekMs + 6 * dayMs + 20 * hourMs,   // domenica sera
                endMs = weekMs + 6 * dayMs + 21 * hourMs,
                xpMultiplier = 5f, eggSpawnMultiplier = 2f, legendaryChanceBonus = 1f, rewardGems = 20
            ),
            LiveEvent(
                id = "mystery_eggs_${weekMs}",
                type = EventType.MYSTERY_EGGS,
                title = "🎭 Uova Misteriose",
                description = "Tutte le uova sono misteriose: non sai cosa troverai!",
                emoji = "🎭", colorHex = "#FF9C27B0",
                startMs = weekMs + 3 * dayMs + 12 * hourMs,   // giovedì mezzogiorno
                endMs = weekMs + 3 * dayMs + 16 * hourMs,
                rewardGems = 8
            )
        ).filter { !it.isExpired }
    }

    fun getActiveEvents(): List<LiveEvent> = getCurrentAndUpcomingEvents().filter { it.isActive }

    fun getActiveXpMultiplier(): Float = getActiveEvents()
        .maxOfOrNull { it.xpMultiplier } ?: 1f

    fun getActiveSpawnMultiplier(): Float = getActiveEvents()
        .maxOfOrNull { it.eggSpawnMultiplier } ?: 1f

    fun getActiveLegendaryBonus(): Float = getActiveEvents()
        .sumOf { it.legendaryChanceBonus.toDouble() }.toFloat()

    // ─── Stato partecipazione ────────────────────────────────────

    fun recordParticipation(eventId: String, uid: String) {
        if (uid.isEmpty() || eventId.isEmpty()) return
        FirebaseFirestore.getInstance()
            .collection("live_events").document(eventId)
            .collection("participants").document(uid)
            .set(mapOf("uid" to uid, "joinedAt" to System.currentTimeMillis()))
    }

    // ─── Modifica spawn uova in base all'evento attivo ───────────

    fun adjustedSpawnWeight(baseWeight: Int, rarity: com.intelligame.huntix.EggRarity): Int {
        val legendaryBonus = getActiveLegendaryBonus()
        if (rarity == com.intelligame.huntix.EggRarity.LEGENDARY && legendaryBonus > 0) {
            return (baseWeight * (1 + legendaryBonus)).toInt()
        }
        return baseWeight
    }
}
