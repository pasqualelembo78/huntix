package com.intelligame.huntix.minigames.ar

import com.intelligame.huntix.managers.MiniGameManager

class ARMemoryActivity : ARGameActivity() {

    private val N = 6
    private val nodes = arrayOfNulls<AREgg>(N)
    private val types = IntArray(N) { it / 2 }
    private val matched = BooleanArray(N)
    private val revealed = BooleanArray(N)
    private var firstPick = -1
    private var pairsFound = 0
    private var lock = false
    private var moves = 0

    override fun onGameCreate() {
        pairsFound = 0; firstPick = -1; moves = 0; lock = false
        matched.fill(false); revealed.fill(false)
        types.shuffle()
        nodes.forEach { it?.let { e -> removeEgg(e) } }
        statusText.text = "Memory AR: trova le coppie! 🧠"
        updateHud()
        startGame()
        whenReady {
            val cols = 3
            for (i in 0 until N) {
                val col = i % cols
                val row = i / cols
                val egg = spawnEgg(5, 1.0f, (col - 1) * 0.32f, if (row == 0) 0.22f else -0.18f, radius = 0.1f)
                egg?.phase = i.toFloat()
                nodes[i] = egg
            }
        }
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || lock || !egg.alive) return
        val i = egg.phase.toInt()
        if (matched[i] || revealed[i]) return
        recolorEgg(egg, types[i]); revealed[i] = true
        if (firstPick == -1) {
            firstPick = i
            return
        }
        moves++
        val a = firstPick; val b = i
        firstPick = -1
        if (types[a] == types[b]) {
            matched[a] = true; matched[b] = true
            pairsFound++
            updateHud()
            if (pairsFound == N / 2) postDelayed(700) { endGame() }
        } else {
            lock = true
            postDelayed(900) {
                if (!running) return@postDelayed
                nodes[a]?.let { recolorEgg(it, 5) }
                nodes[b]?.let { recolorEgg(it, 5) }
                revealed[a] = false; revealed[b] = false
                lock = false
            }
        }
    }

    private fun updateHud() {
        livesText.text = "💡 ${N / 2 - pairsFound}"
        scoreText.text = "$pairsFound/${N / 2}"
        timerText.text = ""
    }

    private fun endGame() {
        stopGame()
        finishGame(pairsFound * 90, "AR Memory ($pairsFound/${N / 2})", pairsFound == N / 2,
            MiniGameManager.GAME_MEMORY)
    }
}
