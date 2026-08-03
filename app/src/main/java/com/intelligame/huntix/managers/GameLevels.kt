package com.intelligame.huntix.managers

/**
 * GameLevels — configurazione dei livelli progressivi dei minigiochi.
 *
 * Ogni gioco ha [MAX_LEVELS] livelli. Un livello si supera raggiungendo il
 * [target] (punti) oppure vincendo una partita ([Mode.WIN]); superarlo sblocca
 * il livello successivo. La difficoltà cresce col livello ([difficulty] 0..1)
 * e i premi vengono scalati di conseguenza (vedi MiniGameManager.completePlay).
 */
object GameLevels {

    const val MAX_LEVELS = 10

    enum class Mode { SCORE, WIN }

    data class Def(
        val gameId: String,
        val unit: String,
        val mode: Mode,
        val base: Int = 1,
        val step: Int = 1
    )

    // ─── Identificatori dei giochi ──────────────────────────────
    // (dichiarati prima delle definizioni: in un object valgono per ordine)
    const val GAME_2048        = "2048"
    const val GAME_SNAKE       = "snake"
    const val GAME_MINESWEEPER = "minesweeper"
    const val GAME_FLAPPY      = "flappy_egg"
    const val GAME_CONNECT4    = "connect4"
    const val GAME_HANGMAN     = "hangman"
    const val GAME_TIC_TAC_TOE = "tic_tac_toe"
    const val GAME_SIMON       = "simon"
    const val GAME_MEMORY      = "memory_game"
    const val GAME_NUMBER_PICK = "number_pick"
    const val GAME_HIGH_CARD   = "high_card"
    const val GAME_CATCH_EGG   = "catch_egg"
    const val GAME_MATCH3      = "match3"
    const val GAME_DINO        = "dino_runner"
    const val GAME_AR_SHOOTER  = "ar_egg_shooter"
    const val GAME_AR_BOMB     = "ar_color_bomb"
    const val GAME_AR_RADAR    = "ar_egg_radar"
    const val GAME_SLINGSHOT   = "ar_slingshot"
    const val GAME_TETRIS      = "tetris"
    const val GAME_PACMAN      = "pacman"
    const val GAME_FLOOD       = "flood"
    const val GAME_ASTEROIDS      = "asteroids"
    const val GAME_FROGGER      = "frogger"
    const val GAME_BRISCOLA    = "briscola"
    const val GAME_SCOPA       = "scopa"
    const val GAME_SOLITAIRE   = "solitaire"

    // ─── Definizioni per gioco ──────────────────────────────────
    // SCORE: target = base + (level-1)*step (score = punti della partita)
    // WIN  : target = 1 (score = 1 vittoria / 0 sconfitta)
    private val DEFS = listOf(
        Def(GAME_2048,        "punti",     Mode.SCORE, base = 5000, step = 5000),
        Def(GAME_SNAKE,       "punti",     Mode.SCORE, base = 50,   step = 50),
        Def(GAME_MINESWEEPER, "vittorie",  Mode.WIN),
        Def(GAME_FLAPPY,      "punti",     Mode.SCORE, base = 40,   step = 40),
        Def(GAME_CONNECT4,    "vittorie",  Mode.WIN),
        Def(GAME_HANGMAN,     "parole",    Mode.WIN),
        Def(GAME_TIC_TAC_TOE, "vittorie",  Mode.WIN),
        Def(GAME_SIMON,       "punti",     Mode.SCORE, base = 40,   step = 25),
        Def(GAME_MEMORY,      "partite",   Mode.WIN),
        Def(GAME_NUMBER_PICK, "indovinati", Mode.WIN),
        Def(GAME_HIGH_CARD,   "vittorie",  Mode.WIN),
        Def(GAME_CATCH_EGG,   "punti",     Mode.SCORE, base = 60,   step = 30),
        Def(GAME_MATCH3,      "punti",     Mode.SCORE, base = 100,  step = 60),
        Def(GAME_DINO,        "punti",     Mode.SCORE, base = 150,  step = 100),
        Def(GAME_AR_SHOOTER,  "punti",     Mode.SCORE, base = 60,   step = 40),
        Def(GAME_AR_BOMB,     "punti",     Mode.SCORE, base = 60,   step = 40),
        Def(GAME_AR_RADAR,    "catture",   Mode.WIN),
        Def(GAME_SLINGSHOT,   "colpi",     Mode.WIN),
        Def(GAME_TETRIS,     "punti",     Mode.SCORE, base = 100, step = 100),
        Def(GAME_PACMAN,     "punti",     Mode.SCORE, base = 100, step = 100),
        Def(GAME_FLOOD,      "mosse",     Mode.SCORE, base = 25,   step = 25),
        Def(GAME_ASTEROIDS,      "punti",     Mode.SCORE, base = 100, step = 100),
        Def(GAME_FROGGER,      "punti",     Mode.SCORE, base = 100, step = 100),
        Def(GAME_BRISCOLA,   "vittorie",  Mode.WIN),
        Def(GAME_SCOPA,      "vittorie",  Mode.WIN),
        Def(GAME_SOLITAIRE,  "vittorie",  Mode.WIN),
    )
    /** Config per gioco; se assente usa una config di default ragionevole. */
    fun def(gameId: String): Def =
        DEFS.firstOrNull { it.gameId == gameId }
            ?: Def(gameId, "punti", Mode.SCORE, base = 100, step = 100)

    /** Punteggio necessario per superare un determinato livello. */
    fun target(gameId: String, level: Int): Int {
        val d = def(gameId)
        return if (d.mode == Mode.WIN) 1 else d.base + (level - 1) * d.step
    }

    /** Difficoltà 0..1 in base al livello (0 = livello 1, 1 = livello MAX). */
    fun difficulty(gameId: String, level: Int): Float =
        ((level - 1).toFloat() / (MAX_LEVELS - 1).toFloat()).coerceIn(0f, 1f)

    /** Stelle per una partita: 3 = target raggiunto, 2 = ≥75%, 1 = ≥50%. */
    fun stars(score: Int, target: Int): Int = when {
        target <= 0 -> 0
        score >= target -> 3
        score * 4 >= target * 3 -> 2
        score * 2 >= target -> 1
        else -> 0
    }
}
