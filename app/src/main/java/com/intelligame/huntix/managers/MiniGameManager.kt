package com.intelligame.huntix.managers

import android.content.Context
import android.util.Log
import com.intelligame.huntix.managers.SavedManager
import com.intelligame.huntix.PlayerProfileManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MiniGameManager — gestore centrale per tutti i mini giochi.
 *
 * Responsabilità:
 *  - Tenere traccia dei tentativi giornalieri per gioco
 *  - Gestire lo streak multiplier (×1, ×1.5, ×2)
 *  - Applicare i premi (MVC, XP, gemme) tramite i manager esistenti
 *  - Verificare il bonus giornaliero (ogni 5 giochi → +500 MVC, +250 XP, +1 Gem)
 *
 * I dati sono in SharedPreferences "minigames_v1" per persistenza locale.
 * Zero dipendenze di rete — funziona completamente offline.
 *
 * === v6: Rimossi giochi gambling (Wheel of Fortune, Slot, Bingo, Scratch) ===
 */
object MiniGameManager {

    private const val TAG   = "MiniGameManager"
    private const val PREFS = "minigames_v1"

    const val MAX_DAILY_PLAYS = 5

    // ─── MiniGame ID enum ────────────────────────────────────────
    enum class MiniGameId(val key: String) {
        MEMORY("memory_game"),
        NUMBER_PICK("number_pick"),
        HIGH_CARD("high_card"),
        THREE_CARD("three_card"),
        CATCH_EGG("catch_egg"),
        MATCH3("match3")
    }

    // ─── Reward types ────────────────────────────────────────────
    enum class RewardType { MVC, XP, GEMS, EGG_RARE, EGG_COMMON, NOTHING }

    data class MiniGameReward(
        val id:          String,
        val title:       String,
        val description: String,
        val emoji:       String,
        val type:        RewardType,
        val amount:      Long
    )

    // ─── String-based ID constants (backward compat) ─────────────
    const val GAME_MEMORY      = "memory_game"
    const val GAME_NUMBER_PICK = "number_pick"
    const val GAME_HIGH_CARD   = "high_card"
    const val GAME_THREE_CARD  = "three_card"
    const val GAME_CATCH_EGG   = "catch_egg"
    const val GAME_MATCH3      = "match3"

    // ─── AR-Native exclusive game IDs ────────────────────────────
    const val GAME_AR_SHOOTER   = "ar_egg_shooter"
    const val GAME_AR_BOMB      = "ar_color_bomb"
    const val GAME_AR_RADAR     = "ar_egg_radar"

    val ALL_GAME_IDS = listOf(
        GAME_MEMORY,
        GAME_NUMBER_PICK, GAME_HIGH_CARD, GAME_THREE_CARD,
        GAME_CATCH_EGG, GAME_MATCH3
    )

    val AR_NATIVE_GAME_IDS = listOf(
        GAME_AR_SHOOTER, GAME_AR_BOMB, GAME_AR_RADAR
    )

    private val MAX_PLAYS_MAP = mapOf(
        GAME_MEMORY        to 3,
        GAME_NUMBER_PICK   to 3,
        GAME_HIGH_CARD     to 5,
        GAME_THREE_CARD    to MAX_DAILY_PLAYS,
        GAME_CATCH_EGG     to 3,
        GAME_MATCH3        to 3,
        // AR-Native exclusives
        GAME_AR_SHOOTER    to 3,
        GAME_AR_BOMB       to 3,
        GAME_AR_RADAR      to 3
    )

    // ─── Key helpers ─────────────────────────────────────────────
    private fun todayStr() = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    private fun playsKey(gameId: String, day: String)  = "plays_${gameId}_$day"
    private fun mvcTodayKey(day: String)               = "mvc_today_$day"
    private fun totalGamesKey(day: String)             = "total_games_$day"
    private fun bonusClaimedKey(day: String, nth: Int) = "daily_bonus_claimed_${day}_$nth"

    // ─── Enum-based API ─────────────────────────────────────────

    fun playsRemaining(ctx: Context, gameId: MiniGameId): Int =
        remainingPlays(ctx, gameId.key)

    fun canPlay(ctx: Context, gameId: MiniGameId): Boolean =
        playsRemaining(ctx, gameId) > 0

    fun recordPlay(ctx: Context, gameId: MiniGameId) {
        consumePlay(ctx, gameId.key)
    }

    fun applyReward(ctx: Context, reward: MiniGameReward) {
        when (reward.type) {
            RewardType.MVC -> {
                if (reward.amount > 0) {
                    try { SavedManager.addMvc(ctx, reward.amount.toDouble()) }
                    catch (e: Exception) { Log.e(TAG, "addMvc failed: ${e.message}") }
                    val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val day   = todayStr()
                    val prev  = prefs.getLong(mvcTodayKey(day), 0L)
                    prefs.edit().putLong(mvcTodayKey(day), prev + reward.amount).apply()
                }
            }
            RewardType.XP -> {
                if (reward.amount > 0) {
                    try {
                        val profile = PlayerProfileManager.myProfile
                        if (profile != null) {
                            profile.xp += reward.amount
                            PlayerProfileManager.persistMyProfile()
                        }
                    } catch (e: Exception) { Log.e(TAG, "XP reward failed: ${e.message}") }
                }
            }
            RewardType.GEMS -> {
                if (reward.amount > 0) {
                    try {
                        val profile = PlayerProfileManager.myProfile
                        if (profile != null) {
                            profile.gems = (profile.gems + reward.amount.toInt()).coerceAtMost(99_999)
                            PlayerProfileManager.persistMyProfile()
                        }
                    } catch (e: Exception) { Log.e(TAG, "Gems reward failed: ${e.message}") }
                }
            }
            RewardType.EGG_RARE -> {
                try { SavedManager.giftEgg(ctx, "rare") }
                catch (e: Exception) { Log.w(TAG, "giftEgg (rare) failed: ${e.message}") }
            }
            RewardType.EGG_COMMON -> {
                try { SavedManager.giftEgg(ctx, "common") }
                catch (e: Exception) { Log.w(TAG, "giftEgg (common) failed: ${e.message}") }
            }
            RewardType.NOTHING -> { /* nessun premio */ }
        }
    }

    // ─── String-based API (backward compat) ──────────────────────

    fun remainingPlays(ctx: Context, gameId: String): Int {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val used  = prefs.getInt(playsKey(gameId, todayStr()), 0)
        val max   = MAX_PLAYS_MAP[gameId] ?: 3
        return (max - used).coerceAtLeast(0)
    }

    fun canPlay(ctx: Context, gameId: String) = remainingPlays(ctx, gameId) > 0

    fun consumePlay(ctx: Context, gameId: String): Boolean {
        if (!canPlay(ctx, gameId)) return false
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key   = playsKey(gameId, todayStr())
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
        val day      = todayStr()
        val prevGames = prefs.getInt(totalGamesKey(day), 0)
        prefs.edit().putInt(totalGamesKey(day), prevGames + 1).apply()
        // Lo streak si incrementa una sola volta per partita giocata, qui
        // (unica fonte di conteggio, per evitare doppi conteggi con applyReward).
        incrementStreak(ctx)
        checkDailyBonus(ctx, prevGames + 1)
        return true
    }

    fun maxPlays(gameId: String) = MAX_PLAYS_MAP[gameId] ?: 3

    // ─── Old GameReward (backward compat for other activities) ───
    data class GameReward(
        val mvcCoins: Int            = 0,
        val xpPoints: Int            = 0,
        val gems: Int                = 0,
        val giftEggRarityId: String? = null,
        val label: String            = "",
        val isWin: Boolean           = true
    )

    // ─── Streak ──────────────────────────────────────────────────
    private const val KEY_STREAK      = "streak_count"
    private const val KEY_STREAK_TIME = "streak_last_time"
    private const val STREAK_WINDOW   = 15 * 60 * 1000L

    fun getCurrentStreak(ctx: Context): Int {
        val prefs    = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastTime = prefs.getLong(KEY_STREAK_TIME, 0L)
        return if (System.currentTimeMillis() - lastTime > STREAK_WINDOW) {
            prefs.edit().putInt(KEY_STREAK, 0).apply()
            0
        } else {
            prefs.getInt(KEY_STREAK, 0)
        }
    }

    private fun incrementStreak(ctx: Context) {
        val prefs  = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val streak = getCurrentStreak(ctx) + 1
        prefs.edit()
            .putInt(KEY_STREAK, streak)
            .putLong(KEY_STREAK_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getStreakMultiplier(ctx: Context): Double = when (getCurrentStreak(ctx)) {
        in 0..2 -> 1.0
        in 3..4 -> 1.5
        else    -> 2.0
    }

    // ─── Statistiche giornaliere ──────────────────────────────────
    fun totalMvcEarnedToday(ctx: Context): Long {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getLong(mvcTodayKey(todayStr()), 0L)
    }

    fun totalGamesPlayedToday(ctx: Context): Int {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(totalGamesKey(todayStr()), 0)
    }

    // ─── APPLICAZIONE PREMIO — vecchia API con GameReward ──────────
    fun applyReward(ctx: Context, reward: GameReward, gameId: String): GameReward {
        incrementStreak(ctx)
        val multiplier = getStreakMultiplier(ctx)

        val finalMvc  = (reward.mvcCoins * multiplier).toInt()
        val finalXp   = (reward.xpPoints * multiplier).toInt()
        val finalGems = reward.gems

        Log.d(TAG, "[$gameId] Reward: MVC=$finalMvc XP=$finalXp Gems=$finalGems (×$multiplier)")

        if (finalMvc > 0) {
            try { SavedManager.addMvc(ctx, finalMvc.toDouble()) }
            catch (e: Exception) { Log.e(TAG, "addMvc failed: ${e.message}") }
        }

        try {
            val profile = PlayerProfileManager.myProfile
            if (profile != null) {
                if (finalXp > 0)   profile.xp   += finalXp
                if (finalGems > 0) profile.gems   = profile.gems + finalGems
                PlayerProfileManager.persistMyProfile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore aggiornamento profilo: ${e.message}")
        }

        if (!reward.giftEggRarityId.isNullOrEmpty()) {
            try { SavedManager.giftEgg(ctx, reward.giftEggRarityId) }
            catch (e: Exception) { Log.w(TAG, "giftEgg failed: ${e.message}") }
        }

        // NOTA: il conteggio partite (totalGames), lo streak e il daily bonus
        // sono gestiti da consumePlay() — qui applichiamo SOLO i premi, altrimenti
        // ogni partita verrebbe contata due volte (consumePlay + applyReward).
        val prefs     = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day       = todayStr()
        val prevMvc   = prefs.getLong(mvcTodayKey(day), 0L)
        prefs.edit()
            .putLong(mvcTodayKey(day), prevMvc + finalMvc)
            .apply()

        return GameReward(
            mvcCoins        = finalMvc,
            xpPoints        = finalXp,
            gems            = finalGems,
            giftEggRarityId = reward.giftEggRarityId,
            label           = if (multiplier > 1.0)
                                "${reward.label}  🔥×${String.format("%.1f", multiplier)}"
                              else
                                reward.label,
            isWin           = reward.isWin
        )
    }

    private fun checkDailyBonus(ctx: Context, totalGames: Int) {
        val bonusEvery = 5
        if (totalGames % bonusEvery != 0) return
        val nth   = totalGames / bonusEvery
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day   = todayStr()
        if (prefs.getBoolean(bonusClaimedKey(day, nth), false)) return

        prefs.edit().putBoolean(bonusClaimedKey(day, nth), true).apply()
        Log.d(TAG, "🎁 Daily bonus #$nth! +500 MVC +250 XP +1 Gem")

        try { SavedManager.addMvc(ctx, 500.0) } catch (_: Exception) {}
        try {
            val profile = PlayerProfileManager.myProfile
            if (profile != null) {
                profile.xp   += 250
                profile.gems  = profile.gems + 1
                PlayerProfileManager.persistMyProfile()
            }
        } catch (_: Exception) {}
    }

    // ─── Utilità ──────────────────────────────────────────────────
    fun formatRemainingPlays(ctx: Context, gameId: String): String {
        val rem = remainingPlays(ctx, gameId)
        return if (rem > 0) "$rem rimast${if (rem == 1) "o" else "i"}" else "Esauriti per oggi"
    }

    fun dailySummary(ctx: Context): String {
        val mvc    = totalMvcEarnedToday(ctx)
        val games  = totalGamesPlayedToday(ctx)
        val streak = getCurrentStreak(ctx)
        return buildString {
            if (games > 0) append("$games giochi")
            if (mvc > 0)   append(" • +$mvc MVC")
            if (streak >= 3) append(" 🔥×${if (streak >= 5) "2.0" else "1.5"}")
        }.ifEmpty { "Nessun gioco oggi" }
    }
}
