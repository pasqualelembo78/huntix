package com.example.huntix.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.huntix.model.GameInfo

class MiniGameManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("minigame_prefs", Context.MODE_PRIVATE)

    companion object {
        const val MAX_PLAYS_PER_DAY = 10
        private const val KEY_PLAYS_PREFIX = "plays_"
        private const val KEY_LAST_PLAY_DATE_PREFIX = "last_date_"

        // Mappa dei giochi con limite
        val GAME_ID_SET = setOf(
            "game_2048", "game_snake", "game_minesweeper",
            "game_flappy_egg", "game_connect4", "game_hangman",
            "game_tic_tac_toe", "game_simon",
            "ar_2048", "ar_snake", "ar_minesweeper",
            "ar_flappy_egg", "ar_connect4", "ar_hangman",
            "ar_tic_tac_toe", "ar_simon",
            "ar_egg_shooter", "ar_color_bomb", "ar_egg_radar", "ar_egg_slingshot"
        )

        val MAX_PLAYS_MAP = mapOf(
            "game_hangman" to 3,         // Lingua italiana parole
            "ar_2048" to 5,              // Versione AR premium
            "ar_egg_slingshot" to 3       // Showcase esclusivo
        ).withDefault { MAX_PLAYS_PER_DAY }
    }

    fun canPlay(gameId: String): Boolean {
        val today = getTodayKey()
        val lastDate = prefs.getString("${KEY_LAST_PLAY_DATE_PREFIX}${gameId}", "")
        if (lastDate != today) {
            prefs.edit {
                putString("${KEY_LAST_PLAY_DATE_PREFIX}${gameId}", today)
                putInt("${KEY_PLAYS_PREFIX}${gameId}", 0)
            }
        }

        val currentPlays = prefs.getInt("${KEY_PLAYS_PREFIX}${gameId}", 0)
        val maxPlays = MAX_PLAYS_MAP.getValue(gameId)
        return currentPlays < maxPlays
    }

    fun incrementPlay(gameId: String) {
        val key = "${KEY_PLAYS_PREFIX}${gameId}"
        val current = prefs.getInt(key, 0)
        prefs.edit { putInt(key, current + 1) }
    }

    fun getRemainingPlays(gameId: String): Int {
        val maxPlays = MAX_PLAYS_MAP.getValue(gameId)
        val today = getTodayKey()
        val lastDate = prefs.getString("${KEY_LAST_PLAY_DATE_PREFIX}${gameId}", "")

        return if (lastDate == today) {
            val current = prefs.getInt("${KEY_PLAYS_PREFIX}${gameId}", 0)
            max(0, maxPlays - current)
        } else maxPlays
    }

    fun resetAllCounters() {
        val today = getTodayKey()
        GAME_ID_SET.forEach { gameId ->
            if (prefs.getString("${KEY_LAST_PLAY_DATE_PREFIX}${gameId}", "") != today) {
                prefs.edit {
                    putInt("${KEY_PLAYS_PREFIX}${gameId}", 0)
                    putString("${KEY_LAST_PLAY_DATE_PREFIX}${gameId}", today)
                }
            }
        }
    }

    fun applyReward(gameId: String, score: Int): RewardResult {
        val xpEarned = calculateXp(score)
        return RewardResult(xpEarned, score)
    }

    private fun calculateXp(score: Int): Int {
        // Formula: sqrt(score) * 10, minimo 10 XP
        return maxOf(10, (score.toDouble().pow(0.5) * 10).toInt())
    }

    private fun getTodayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    data class RewardResult(val xpEarned: Int, val score: Int)
}

// Modello informazioni gioco
package com.example.huntix.model

data class GameInfo(
    val id: String,
    val name: String,
    val className: String,
    val isAr: Boolean,
    val isExclusive: Boolean = false,
    val note: String = ""
)