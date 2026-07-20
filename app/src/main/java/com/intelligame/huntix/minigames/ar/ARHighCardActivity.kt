package com.intelligame.huntix.minigames.ar

import android.view.MotionEvent
import com.intelligame.huntix.managers.MiniGameManager

class ARHighCardActivity : ARGameActivity() {

    private val SUIT_EMOJI = arrayOf("♠️", "♥️", "♦️", "♣️")
    private var playerNode: AREgg? = null
    private var oppNode: AREgg? = null
    private var round = 0
    private var wins = 0
    private var drawn = false

    override fun onGameCreate() {
        round = 0; wins = 0; drawn = false
        statusText.text = "Carta Alta AR: tocca per pescare la carta! 🃏"
        startGame()
        whenReady {
            playerNode = spawnEgg(0, 0.9f, -0.28f, 0f, radius = 0.1f)
            oppNode = spawnEgg(0, 0.9f, 0.28f, 0f, radius = 0.1f)
            updateHud(null, null)
        }
    }

    override fun onBackgroundTapped(event: MotionEvent) {
        if (!running || drawn) return
        drawn = true
        round++
        val pRank = (2..14).random()
        val oRank = (2..14).random()
        val pSuit = (0..3).random()
        val oSuit = (0..3).random()
        playerNode?.let { recolorEgg(it, pSuit) }
        oppNode?.let { recolorEgg(it, oSuit) }
        val playerWin = pRank > oRank
        if (playerWin) wins++
        updateHud(pRank to pSuit, oRank to oSuit, playerWin)
        if (round >= 5) {
            postDelayed(1200) { endGame() }
        } else {
            postDelayed(900) { drawn = false; statusText.text = "Tocca per la prossima carta" }
        }
    }

    private fun rankStr(r: Int) = when (r) {
        14 -> "A"; 13 -> "K"; 12 -> "Q"; 11 -> "J"; else -> r.toString()
    }

    private fun updateHud(p: Pair<Int, Int>? = null, o: Pair<Int, Int>? = null, lastWin: Boolean? = null) {
        livesText.text = "🏆 $wins"
        scoreText.text = "Round $round/5"
        timerText.text = ""
        statusText.text = when {
            p == null -> "Carta Alta AR: tocca per pescare! 🃏"
            lastWin == true -> "Hai vinto la mano! ${rankStr(p.first)}${SUIT_EMOJI[p.second]}"
            lastWin == false -> "Persa: ${rankStr(p.first)}${SUIT_EMOJI[p.second]} vs ${rankStr(o!!.first)}${SUIT_EMOJI[o.second]}"
            else -> statusText.text
        }
    }

    private fun endGame() {
        stopGame()
        finishGame(wins * 80, "AR High Card ($wins/5)", wins >= 3, MiniGameManager.GAME_HIGH_CARD)
    }
}
