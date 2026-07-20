package com.intelligame.huntix

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.graphics.Color as AndroidColor
import android.os.SystemClock
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.intelligame.huntix.FirestoreGameSync
import com.intelligame.huntix.GameDataManager
import com.intelligame.huntix.IndoorRoomManager
import com.intelligame.huntix.IndoorSessionManager
import com.intelligame.huntix.SentryDebugManager
import com.intelligame.huntix.model.GamePhase
import com.intelligame.huntix.model.PlayState
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import com.intelligame.huntix.manager.saveCurrentSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Game flow: lancio, cattura, fine partita, stats ───────────
internal fun MainActivity.launchBasket(fromX: Float, fromY: Float, hit: Boolean) {
    throwInProgress = true; playState = PlayState.THROWING
    val basket = binding.throwBasket
    val sw = binding.root.width.toFloat(); val sh = binding.root.height.toFloat()
    basket.visibility = View.VISIBLE; basket.alpha = 1f; basket.scaleX = 1f; basket.scaleY = 1f
    basket.translationX = fromX - basketSizePx / 2; basket.translationY = fromY - basketSizePx / 2
    basket.animate()
        .translationX((if (hit) sw / 2f else sw * 0.82f) - basketSizePx / 2)
        .translationY(sh * 0.15f - basketSizePx / 2)
        .scaleX(if (hit) 2.2f else 1.3f).scaleY(if (hit) 2.2f else 1.3f)
        .alpha(0f).setDuration(560).setInterpolator(DecelerateInterpolator())
        .withEndAction {
            basket.visibility = View.GONE; basket.scaleX = 1f; basket.scaleY = 1f
            basket.translationX = 0f; basket.translationY = 0f
            if (hit) onThrowHit() else onThrowMiss()
        }.start()
}

internal fun MainActivity.onThrowHit() {
    throwInProgress = false
    val egg = eggs.getOrNull(currentEggIdx) ?: return
    egg.pulseAnim?.cancel()

    if (egg.isTrap) {
        egg.anchorNode.isVisible = false
        penaltyAccumMs += penaltySecs * 1000L
        SoundManager.playTrap()
        binding.catchBurst.visibility = View.VISIBLE; binding.catchBurst.alpha = 1f
        binding.catchBurst.setTextColor(AndroidColor.parseColor("#FF4444"))
        binding.catchBurst.text = "⚠️\nTRAPPOLA!\n+${penaltySecs}s"
        binding.catchBurst.animate().alpha(0f).setStartDelay(700).setDuration(1800)
            .withEndAction { binding.catchBurst.visibility = View.GONE
                binding.catchBurst.setTextColor(AndroidColor.parseColor("#E0E0FF")) }.start()
        val nextIdx = currentEggIdx + 1
        currentEggIdx = nextIdx; keyInPocket = false
        uiHandler.postDelayed({
            if (!isActive || isFinishing) return@postDelayed
            runOnUiThread {
                if (currentEggIdx >= eggs.size) finishGame()
                else { eggStartMs = SystemClock.elapsedRealtime(); playState = PlayState.SEARCHING; updateUI() }
            }
        }, 2400)
        runOnUiThread {
            Toast.makeText(this, "TRAPPOLA! +${penaltySecs} secondi!", Toast.LENGTH_LONG).show()
            playState = PlayState.SEARCHING; updateUI()
        }
        return
    }

    // Uovo reale
    SoundManager.playEggFound()
    val elapsed = SystemClock.elapsedRealtime() - eggStartMs
    eggTimesMs.add(elapsed); realEggsCaught++

    // ─── Cloud sync: segna uovo trovato (indoor MP) ───
    if (isIndoorMp && indoorRoomCode.isNotEmpty()) {
        val uid  = FirebaseAuth.getInstance().currentUser?.uid ?: indoorPlayerUid
        val name = indoorCurrentPlayer.ifBlank { activePlayers.firstOrNull() ?: "Giocatore" }
        val totalSoFar = SystemClock.elapsedRealtime() - huntStartMs + penaltyAccumMs
        IndoorSessionManager.markEggFound(
            code       = indoorRoomCode,
            eggIdx     = currentEggIdx,
            playerUid  = uid,
            playerName = name,
            eggsFound  = realEggsCaught,
            totalMs    = totalSoFar
        )
    }

    ValueAnimator.ofFloat(1f, 0f).apply {
        duration = 400; interpolator = AccelerateInterpolator()
        addUpdateListener { anim -> val s = anim.animatedValue as Float; egg.eggNode.scale = Scale(s, s * 1.45f, s) }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) { egg.anchorNode.isVisible = false }
        })
        start()
    }

    // ─── Multiplayer sync ───
    if (isMultiplayer && mpRoomCode.isNotEmpty()) {
        val totalElapsed = SystemClock.elapsedRealtime() - huntStartMs + penaltyAccumMs
        mpManager.reportEggFound(currentEggIdx, elapsed)
        mpManager.updateMyScore(realEggsCaught, totalElapsed)
    }

    // ─── Secchiello: raccogli l'uovo e gestisci la capacità ───
    bucketHeld++
    refreshBucketModel()
    val isLast = (currentEggIdx + 1) >= eggs.size
    if (bucketHeld >= getBucketCapacity() || isLast) {
        keyInPocket = true
        playState = PlayState.KEY_OBTAINED
    } else {
        currentEggIdx++
        playState = PlayState.SEARCHING
    }

    binding.catchBurst.visibility = View.VISIBLE; binding.catchBurst.alpha = 1f
    binding.catchBurst.setTextColor(AndroidColor.parseColor("#E0E0FF"))
    val label = MainActivity.EGG_LABELS[currentEggIdx % MainActivity.EGG_LABELS.size]
    binding.catchBurst.text = "$label\nPRESA!\n${fmtMs(elapsed)}"
    binding.catchBurst.animate().alpha(0f).setStartDelay(400).setDuration(1600)
        .withEndAction { binding.catchBurst.visibility = View.GONE }.start()

    runOnUiThread {
        updateUI()
        val msg = if (keyInPocket) "🪣 Secchiello pieno! Vai alla cassaforte!"
                  else "🪣 Uovo nel secchiello ($bucketHeld/${getBucketCapacity()})"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.onThrowMiss() {
    throwInProgress = false; playState = PlayState.NEAR_EGG
    runOnUiThread { Toast.makeText(this, "Quasi! Riprova!", Toast.LENGTH_SHORT).show(); updateUI() }
}

internal fun MainActivity.finishGame() {
    viewModel.finishGame()
    val ctx = this
    val totalMs = SystemClock.elapsedRealtime() - huntStartMs + penaltyAccumMs

    // ─── Multiplayer: notifica fine partita ───
    if (isMultiplayer && mpRoomCode.isNotEmpty()) {
        mpManager.updateMyScore(realEggsCaught, totalMs, finished = true)
    }
    // ─── Indoor Multiplayer: aggiorna IndoorSessionManager ───
    if (isIndoorMp && indoorRoomCode.isNotEmpty()) {
        val uid  = FirebaseAuth.getInstance().currentUser?.uid ?: indoorPlayerUid
        val name = indoorCurrentPlayer.ifBlank { activePlayers.firstOrNull() ?: "Giocatore" }
        IndoorSessionManager.finishGame(
            code       = indoorRoomCode,
            playerUid  = uid,
            playerName = name,
            totalMs    = totalMs,
            eggsFound  = realEggsCaught
        )
        // Avanza turno se modalità alternata
        if (turnMode == "alternating") IndoorSessionManager.advanceTurn(indoorRoomCode)
    }

    // ─── Sentry: breadcrumb fine partita ───
    SentryDebugManager.breadcrumb(
        category = SentryDebugManager.Category.GAME,
        msg      = "Partita AR terminata",
        data     = mapOf(
            "eggs_caught" to realEggsCaught,
            "total_ms"    to totalMs,
            "turn_mode"   to turnMode,
            "player"      to currentPlayer
        )
    )
    SentryDebugManager.clearGameContext()

    when (turnMode) {
        "alternating" -> {
            val snapPlayers = activePlayers.toList()
            val snapOwners  = eggOwners.toList()
            val snapEggs    = eggs.map { it.isTrap }
            val snapTimes   = eggTimesMs.toList()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    snapPlayers.forEach { player ->
                        val myEggs = snapOwners.indices.filter { snapOwners[it] == player && !snapEggs[it] }
                        val stats  = myEggs.mapIndexed { si, ei ->
                            GameDataManager.EggStat(si + 1, snapTimes.getOrElse(ei) { 0L })
                        }
                        val myTotal = stats.sumOf { it.timeMs }
                        if (stats.isNotEmpty()) dm.addRun(GameDataManager.GameRun(
                            id = dm.newRunId(), playerName = player, date = dm.todayString(),
                            eggCount = stats.size, eggStats = stats, totalMs = myTotal
                        ))
                    }
                    eggPlacementManager.saveCurrentSession()
                } catch (_: Exception) {}
                withContext(Dispatchers.Main) { SoundManager.playVictory(); showFinalStats() }
            }
        }
        else -> {
            val snapPlayer   = currentPlayer
            val snapTotal    = totalMs
            val snapCaught   = realEggsCaught
            val snapEggCount = eggs.count { !it.isTrap }
            val snapTimes    = eggTimesMs.toList()
            val nextIdx      = currentPlayerIdx + 1
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    dm.addRun(GameDataManager.GameRun(
                        id = dm.newRunId(), playerName = snapPlayer, date = dm.todayString(),
                        eggCount = snapEggCount,
                        eggStats = snapTimes.mapIndexed { i, ms -> GameDataManager.EggStat(i + 1, ms) },
                        totalMs = snapTotal
                    ))
                    eggPlacementManager.saveCurrentSession()
                } catch (_: Exception) {}
                withContext(Dispatchers.Main) {
                    if (nextIdx < activePlayers.size) {
                        SoundManager.playVictory()
                        AlertDialog.Builder(ctx)
                            .setTitle("$snapPlayer ha finito!")
                            .setMessage("Tempo: ${fmtMs(snapTotal)}\nUova: $snapCaught\n\nOra tocca a ${activePlayers[nextIdx]}!\nRiposiziona le uova AR.")
                            .setPositiveButton("Pronti!") { _, _ -> currentPlayerIdx = nextIdx; resetForNextPlayer() }
                            .setNegativeButton("Esci") { _, _ -> showFinalStats() }
                            .setCancelable(false).show()
                    } else {
                        SoundManager.playVictory()
                        showFinalStats()
                    }
                }
            }
        }
    }
}

internal fun MainActivity.resetForNextPlayer() {
    safeObject?.let { s ->
        s.doorNode.rotation = Rotation(0f,0f,0f); s.keySlots.forEach { it.isVisible = false }; s.keySlots.clear()
    }
    eggs.forEach { egg -> egg.pulseAnim?.cancel(); egg.anchorNode.isVisible = false }
    currentEggIdx = 0; keyInPocket = false
    eggTimesMs.clear(); realEggsCaught = 0; penaltyAccumMs = 0
    huntStartMs = SystemClock.elapsedRealtime(); eggStartMs = huntStartMs
    viewModel.startTimer()
    playState = PlayState.SEARCHING; runOnUiThread { updateUI() }
}

internal fun MainActivity.showFinalStats() {
    gamePhase = GamePhase.STATS
    binding.statsOverlay.visibility = View.VISIBLE; binding.statsOverlay.alpha = 0f
    binding.statsOverlay.animate().alpha(1f).setDuration(600).start()
    renderStats(); updateUI()

    // ─── Firestore sync per indoor multiplayer ───
    if (isIndoorMp && indoorRoomCode.isNotEmpty()) {
        try {
            val uid  = FirebaseAuth.getInstance().currentUser?.uid
                ?: indoorCurrentPlayer
            val name = indoorCurrentPlayer.ifBlank { activePlayers.firstOrNull() ?: "Giocatore" }

            // Marca la stanza come finita (solo l'host)
            if (indoorIsHost) IndoorRoomManager.finishGame(indoorRoomCode)

            // Recupera punteggi finali e salva su Firestore
            IndoorRoomManager.getRoomInfo(
                code      = indoorRoomCode,
                onSuccess = { room ->
                    val eggCount  = room.config.eggCount
                    val scoreEntry = FirestoreGameSync.ScoreEntry(
                        playerId   = uid,
                        playerName = name,
                        eggsFound  = realEggsCaught,
                        totalMs    = SystemClock.elapsedRealtime() - huntStartMs + penaltyAccumMs,
                        finished   = true
                    )
                    val session = FirestoreGameSync.buildIndoorMpSession(
                        roomCode = indoorRoomCode,
                        hostName = name,
                        config   = room.config,
                        scores   = listOf(IndoorRoomManager.PlayerScore(
                            playerId   = uid,
                            playerName = name,
                            eggsFound  = realEggsCaught,
                            totalMs    = SystemClock.elapsedRealtime() - huntStartMs + penaltyAccumMs,
                            finished   = true
                        ))
                    )
                    FirestoreGameSync.saveSession(session)
                },
                onError = { /* log only */ }
            )
        } catch (_: Exception) { /* non blocca la UI */ }
    }
}

internal fun MainActivity.renderStats() {
    val sb = StringBuilder()
    when (turnMode) {
        "alternating" -> {
            sb.append("RISULTATI FINALI\n\n")
            val playerTotals = mutableListOf<Pair<String, Long>>()
            activePlayers.forEach { player ->
                val myIdxs = eggOwners.indices.filter { eggOwners[it] == player && !eggs[it].isTrap }
                if (myIdxs.isEmpty()) return@forEach
                val times  = myIdxs.map { eggTimesMs.getOrElse(it) { 0L } }
                val total  = times.sum()
                playerTotals.add(player to total)
                sb.append("$player\n")
                times.forEachIndexed { i, ms -> sb.append("  Uovo #${myIdxs[i]+1}: ${fmtMs(ms)}\n") }
                sb.append("  Totale: ${fmtMs(total)}\n\n")
            }
            if (playerTotals.size >= 2) {
                val winner = playerTotals.minByOrNull { it.second }!!
                sb.append("🏆 VINCITORE: ${winner.first} (${fmtMs(winner.second)})")
            }
        }
        else -> {
            activePlayers.forEach { player ->
                val runs = dm.getRunsForPlayer(player); if (runs.isEmpty()) return@forEach
                val last = runs.last()
                sb.append("$player\n  Tempo: ${fmtMs(last.totalMs)}\n")
                if (last.eggStats.isNotEmpty()) {
                    sb.append("  Migliore: ${fmtMs(last.bestMs())}  -  Peggiore: ${fmtMs(last.worstMs())}\n")
                    last.eggStats.forEach { e -> sb.append("    Uovo #${e.eggNumber}: ${fmtMs(e.timeMs)}\n") }
                }
                sb.append("\n")
            }
            if (activePlayers.size >= 2) {
                val bests = activePlayers.mapNotNull { p ->
                    dm.getRunsForPlayer(p).lastOrNull()?.let { p to it.totalMs }
                }
                if (bests.size >= 2) {
                    val winner = bests.minByOrNull { it.second }!!
                    sb.append("🏆 Vincitore: ${winner.first} (${fmtMs(winner.second)})")
                }
            }
        }
    }
    binding.tvStatsBody.text = sb.toString().trim()
    binding.tvStatsTitle.text = "🎅 Partita completata!"
}

internal fun MainActivity.startHunt() {
    if (eggs.isEmpty()) { Toast.makeText(this, "Nascondi almeno un uovo!", Toast.LENGTH_SHORT).show(); return }
    viewModel.startHunt(eggs.size)
    eggTimesMs.clear()
    eggs.forEach { egg -> egg.anchorNode.isVisible = false; egg.trapMarkerNode?.isVisible = false }
    binding.btnTrapToggle.visibility = View.GONE
    updateUI()

    val trapInfo = if (eggs.any { it.isTrap }) "  Attenzione alle trappole!" else ""
    val firstMsg = when (turnMode) {
        "alternating" -> "Inizia ${currentPlayer}! Trova l'uovo #1!$trapInfo"
        else          -> "Trova l'uovo #1!$trapInfo"
    }
    Toast.makeText(this, firstMsg, Toast.LENGTH_LONG).show()
}
