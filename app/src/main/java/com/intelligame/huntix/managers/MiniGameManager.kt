package com.intelligame.huntix.managers

import android.content.Context
import android.util.Log
import com.intelligame.huntix.managers.SavedManager
import com.intelligame.huntix.PlayerProfileManager
import io.sentry.Sentry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MiniGameManager — gestore centrale per tutti i mini giochi.
 *
 * Responsabilità:
 *  - Gestire lo streak multiplier (×1, ×1.5, ×2)
 *  - Applicare i premi (MVC, XP, gemme) tramite i manager esistenti
 *  - Verificare il bonus giornaliero (ogni 5 giochi → +500 MVC, +250 XP, +1 Gem)
 *  - Contare le partite giornaliere (senza limiti di giocate: tutti i giochi liberi)
 *
 * I dati sono in SharedPreferences "minigames_v1" per persistenza locale.
 * Zero dipendenze di rete — funziona completamente offline.
 *
 * === v6: Rimossi giochi gambling (Wheel of Fortune, Slot, Bingo, Scratch) ===
 * === v8: Rimossi i limiti di giocate giornalieri — tutti i giochi liberi ===
 */
object MiniGameManager {

    private const val TAG   = "MiniGameManager"
    private const val PREFS = "minigames_v1"

    // ─── MiniGame ID enum ────────────────────────────────────────
    enum class MiniGameId(val key: String) {
        MEMORY("memory_game"),
        NUMBER_PICK("number_pick"),
        HIGH_CARD("high_card"),
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
    const val GAME_CATCH_EGG   = "catch_egg"
    const val GAME_MATCH3      = "match3"

    // ─── Nuovi giochi classici (v7) ──────────────────────────────
    const val GAME_2048        = "2048"
    const val GAME_SNAKE       = "snake"
    const val GAME_MINESWEEPER = "minesweeper"
    const val GAME_FLAPPY      = "flappy_egg"
    const val GAME_CONNECT4    = "connect4"
    const val GAME_HANGMAN     = "hangman"
    const val GAME_TIC_TAC_TOE = "tic_tac_toe"
    const val GAME_SIMON       = "simon"
    const val GAME_DINO        = "dino_runner"

    // ─── AR-Native exclusive game IDs ────────────────────────────
    const val GAME_AR_SHOOTER   = "ar_egg_shooter"
    const val GAME_AR_BOMB      = "ar_color_bomb"
    const val GAME_AR_RADAR     = "ar_egg_radar"
    const val GAME_SLINGSHOT    = "ar_slingshot"
    const val GAME_TETRIS      = "tetris"
    const val GAME_FLOOD       = "flood"
    const val GAME_ASTEROIDS      = "asteroids"
    const val GAME_FROGGER      = "frogger"

    val ALL_GAME_IDS = listOf(
        GAME_MEMORY,
        GAME_NUMBER_PICK, GAME_HIGH_CARD,
        GAME_CATCH_EGG, GAME_MATCH3,
        GAME_2048, GAME_SNAKE, GAME_MINESWEEPER, GAME_FLAPPY,
        GAME_CONNECT4, GAME_HANGMAN, GAME_TIC_TAC_TOE, GAME_SIMON,
        GAME_DINO,
        GAME_SLINGSHOT,
        GAME_TETRIS,
        GAME_FLOOD,
        GAME_ASTEROIDS,
        GAME_FROGGER
    )

    val AR_NATIVE_GAME_IDS = listOf(
        GAME_AR_SHOOTER, GAME_AR_BOMB, GAME_AR_RADAR, GAME_SLINGSHOT
    )

    // ─── Key helpers ─────────────────────────────────────────────
    private fun todayStr() = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    private fun playsKey(gameId: String, day: String)  = "plays_${gameId}_$day"
    private fun mvcTodayKey(day: String)               = "mvc_today_$day"
    private fun totalGamesKey(day: String)             = "total_games_$day"
    private fun bonusClaimedKey(day: String, nth: Int) = "daily_bonus_claimed_${day}_$nth"

    // ─── Livelli (per-game) ──────────────────────────────────────
    private fun levelKey(gameId: String)        = "level_$gameId"
    private fun starsKey(gameId: String, lvl: Int) = "stars_${gameId}_$lvl"
    private fun bestKey(gameId: String)         = "best_$gameId"

    /** Livello corrente del gioco (1..GameLevels.MAX_LEVELS). */
    fun getLevel(ctx: Context, gameId: String): Int {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(levelKey(gameId), 1).coerceIn(1, GameLevels.MAX_LEVELS)
    }

    /** Obiettivo (target) del livello corrente del gioco. */
    fun getLevelTarget(ctx: Context, gameId: String): Int =
        GameLevels.target(gameId, getLevel(ctx, gameId))

    /** Obiettivo di un livello specifico. */
    fun getLevelTarget(ctx: Context, gameId: String, level: Int): Int =
        GameLevels.target(gameId, level)

    /** Difficoltà 0..1 del livello corrente del gioco. */
    fun levelDifficulty(ctx: Context, gameId: String): Float =
        GameLevels.difficulty(gameId, getLevel(ctx, gameId))

    /** Miglior punteggio mai raggiunto nel gioco. */
    fun getBestScore(ctx: Context, gameId: String): Int {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(bestKey(gameId), 0)
    }

    /** Stelle migliori (0..3) ottenute su un livello. */
    fun getStars(ctx: Context, gameId: String, level: Int): Int {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(starsKey(gameId, level), 0).coerceIn(0, 3)
    }

    /** Totale stelle accumulate nel gioco. */
    fun getTotalStars(ctx: Context, gameId: String): Int {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var sum = 0
        for (lvl in 1..GameLevels.MAX_LEVELS) sum += prefs.getInt(starsKey(gameId, lvl), 0)
        return sum.coerceAtMost(GameLevels.MAX_LEVELS * 3)
    }

    /** Totale stelle accumulate su tutti i giochi. */
    fun getTotalStarsAll(ctx: Context): Int {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var sum = 0
        for (g in ALL_GAME_IDS) {
            for (lvl in 1..GameLevels.MAX_LEVELS) sum += prefs.getInt(starsKey(g, lvl), 0)
        }
        return sum
    }

    /** Rappresentazione testuale delle stelle, es. "⭐⭐☆". */
    fun starsText(stars: Int): String = buildString {
        repeat(stars.coerceIn(0, 3)) { append("⭐") }
        repeat((3 - stars.coerceIn(0, 3))) { append("☆") }
    }

    /**
     * Esito di una partita conclusa, in ottica livelli.
     * @property level livello giocato
     * @property target obiettivo da raggiungere per superare il livello
     * @property score punteggio della partita
     * @property stars stelle guadagnate in questa partita
     * @property cleared true se il livello è stato superato
     * @property levelUp true se è stato sbloccato il livello successivo
     * @property mvc MVC effettivi assegnati (con moltiplicatore di livello)
     * @property xp XP effettivi assegnati (con moltiplicatore di livello)
     * @property gems gemme bonus assegnate (level-up)
     */
    data class LevelResult(
        val gameId: String,
        val level: Int,
        val target: Int,
        val score: Int,
        val stars: Int,
        val cleared: Boolean,
        val levelUp: Boolean,
        val mvc: Int,
        val xp: Int,
        val gems: Int
    )

    /**
     * Chiusura di una partita con gestione livelli:
     *  - conta la giocata ([consumePlay]);
     *  - aggiorna livello, stelle e record personale;
     *  - applica i premi base scalati col livello (+ bonus level-up);
     *
     * Ritorna l'esito completo ([LevelResult]) da mostrare nell'overlay.
     */
    fun completePlay(
        ctx: Context,
        gameId: String,
        score: Int,
        mvc: Int = 0,
        xp: Int = 0,
        label: String = "",
        isWin: Boolean = false,
        giftEggRarityId: String? = null
    ): LevelResult {
        consumePlay(ctx, gameId)

        val level  = getLevel(ctx, gameId)
        val target = getLevelTarget(ctx, gameId)
        val cleared = score >= target && target > 0
        val stars  = GameLevels.stars(score, target)

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (score > prefs.getInt(bestKey(gameId), 0)) {
            prefs.edit().putInt(bestKey(gameId), score).apply()
        }
        if (stars > getStars(ctx, gameId, level)) {
            prefs.edit().putInt(starsKey(gameId, level), stars).apply()
        }
        var levelUp = false
        if (cleared && level < GameLevels.MAX_LEVELS) {
            prefs.edit().putInt(levelKey(gameId), level + 1).apply()
            levelUp = true
        }

        // Premi scalati col livello + bonus per il level-up.
        val mul      = 1f + 0.15f * (level - 1)
        val finalMvc = (mvc * mul).toInt() + if (levelUp) 100 * level else 0
        val finalXp  = (xp * mul).toInt()  + if (levelUp) 50 * level else 0
        val finalGems = if (levelUp) 1 else 0

        applyReward(
            ctx,
            GameReward(
                mvcCoins        = finalMvc,
                xpPoints        = finalXp,
                gems            = finalGems,
                giftEggRarityId = giftEggRarityId,
                label           = label,
                isWin           = isWin
            ),
            gameId
        )

        return LevelResult(gameId, level, target, score, stars, cleared, levelUp, finalMvc, finalXp, finalGems)
    }

    // ─── Enum-based API ─────────────────────────────────────────

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

    fun consumePlay(ctx: Context, gameId: String): Boolean {
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

        try { SavedManager.addMvc(ctx, 500.0) } catch (e: Exception) { Sentry.captureException(e) }
        try {
            val profile = PlayerProfileManager.myProfile
            if (profile != null) {
                profile.xp   += 250
                profile.gems  = profile.gems + 1
                PlayerProfileManager.persistMyProfile()
            }
        } catch (e: Exception) { Sentry.captureException(e) }
    }

    // ─── Utilità ──────────────────────────────────────────────────
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
