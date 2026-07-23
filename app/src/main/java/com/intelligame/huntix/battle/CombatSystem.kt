package com.intelligame.huntix.battle

import java.util.concurrent.ConcurrentLinkedQueue

class CombatSystem {

    private val logQueue = ConcurrentLinkedQueue<String>()
    private var hitCount = 0
    private var missCount = 0
    private var critCount = 0
    private var totalDamageDealt = 0L
    private var totalDamageReceived = 0L

    fun log(event: String) {
        logQueue.add(event)
        while (logQueue.size > 50) logQueue.poll()
        parseEvent(event)
    }

    private fun parseEvent(event: String) {
        when {
            event.contains("CRIT") || event.contains("crit") -> {
                critCount++
                hitCount++
            }
            event.contains("HIT") || event.contains("hit") || event.contains("Colpito") -> hitCount++
            event.contains("MISS") || event.contains("miss") || event.contains("mancato") -> missCount++
            event.contains("DAMAGE") || event.contains("damage") || event.contains("danno") -> {
                val nums = event.split(" ").filter { s -> s.removePrefix("-").all { it.isDigit() } && s.removePrefix("-").isNotEmpty() }
                if (nums.isNotEmpty()) totalDamageDealt += nums.last().removePrefix("-").toLong()
            }
            event.contains("RECEIVED") || event.contains("received") || event.contains("subìto") -> {
                val nums = event.split(" ").filter { s -> s.removePrefix("-").all { it.isDigit() } && s.removePrefix("-").isNotEmpty() }
                if (nums.isNotEmpty()) totalDamageReceived += nums.last().removePrefix("-").toLong()
            }
        }
    }

    fun getRecentLogs(limit: Int = 20): List<String> {
        return logQueue.toList().takeLast(limit.coerceAtMost(logQueue.size))
    }

    fun getStats() = CombatStats(
        hits = hitCount,
        misses = missCount,
        crits = critCount,
        totalDamageDealt = totalDamageDealt,
        totalDamageReceived = totalDamageReceived,
        logEntries = logQueue.size
    )

    fun reset() {
        logQueue.clear()
        hitCount = 0; missCount = 0; critCount = 0
        totalDamageDealt = 0; totalDamageReceived = 0
    }

    data class CombatStats(
        val hits: Int,
        val misses: Int,
        val crits: Int,
        val totalDamageDealt: Long,
        val totalDamageReceived: Long,
        val logEntries: Int
    ) {
        val hitRate: Float = if (hits + misses > 0) hits.toFloat() / (hits + misses) else 0f
        val critRate: Float = if (hits > 0) crits.toFloat() / hits else 0f
    }
}