package com.intelligame.huntix

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.intelligame.huntix.model.GamePhase
import com.intelligame.huntix.model.PlayState
import com.intelligame.huntix.manager.saveCurrentSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── UI: timer, fasi di gioco, menu in-game, pulsanti ──────────
internal fun MainActivity.updateTimerDisplay(elapsedMs: Long) {
    binding.tvTimer.text = viewModel.formatTime(elapsedMs)
}

internal fun MainActivity.updateUI() {
    updateKeyInventoryUI()
    binding.throwZone.visibility = View.GONE
    binding.btnInsertKey.visibility = View.GONE
    binding.playerSelectOverlay.visibility = View.GONE

    when (gamePhase) {
        GamePhase.SCAN_ROOM -> {
            binding.tvGamePhase.text = "SCANSIONE STANZA"
            binding.tvInstruction.visibility = View.VISIBLE
            binding.tvInstruction.text = "🏠 Cammina per la stanza per mapparla"
            binding.btnStart.visibility = View.GONE
            binding.tvTimer.visibility = View.GONE
            binding.btnTrapToggle.visibility = View.GONE
            binding.scanOverlay.visibility = View.VISIBLE
        }
        GamePhase.SETUP_SAFE -> {
            val safeLabel = when (selectedSafeType) {
                "chest"   -> "Forziere"
                "vault"   -> "Vault"
                "present" -> "Regalo"
                else      -> "Cassaforte"
            }
            binding.tvGamePhase.text = if (isRestoreMode) "RIPRISTINO" else "GENITORE"
            binding.tvInstruction.visibility = View.VISIBLE
            binding.tvInstruction.text = if (isRestoreMode)
                "Piazza la ${safeLabel} NELLO STESSO PUNTO di prima"
            else "Tocca il pavimento per piazzare la ${safeLabel}"
            binding.btnStart.visibility = View.GONE; binding.tvTimer.visibility = View.GONE
            binding.btnTrapToggle.visibility = View.GONE
        }
        GamePhase.SETUP_EGGS -> {
            val trapCount = eggs.count { it.isTrap }
            val trapStr   = if (trapCount > 0) " - $trapCount trap" else ""
            binding.tvGamePhase.text = "UOVA - ${eggs.size} piazzate$trapStr"
            binding.tvInstruction.visibility = View.VISIBLE
            binding.tvInstruction.text = eggPlacementManager.setupInstructionText()
            binding.btnStart.visibility = if (eggs.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvTimer.visibility = View.GONE
            binding.btnTrapToggle.visibility = if (eggSetupMode != "auto") View.VISIBLE else View.GONE
            if (eggSetupMode != "auto") updateTrapToggleUI()
        }
        GamePhase.PLAYING -> {
            binding.tvInstruction.visibility = View.GONE; binding.btnStart.visibility = View.GONE
            binding.tvTimer.visibility = View.VISIBLE; binding.btnTrapToggle.visibility = View.GONE
            updatePlayingUI()
        }
        GamePhase.STATS -> {
            binding.tvTimer.visibility = View.GONE; binding.btnTrapToggle.visibility = View.GONE
            binding.statsOverlay.visibility = View.VISIBLE
        }
    }
}

internal fun MainActivity.updatePlayingUI() {
    val n = currentEggIdx + 1
    val playerLabel = if (turnMode == "alternating") "$currentPlayer - " else ""
    // Mostra il pulsante indizio solo quando si sta cercando un uovo
    binding.btnHint.visibility = if (
        playState == PlayState.SEARCHING || playState == PlayState.NEAR_EGG
    ) View.VISIBLE else View.GONE
    when (playState) {
        PlayState.SEARCHING    -> binding.tvGamePhase.text = "${playerLabel}Cerca uovo #$n  🪣 ${bucketHeld}/${getBucketCapacity()}"
        PlayState.NEAR_EGG    -> { binding.tvGamePhase.text = "${MainActivity.EGG_LABELS[currentEggIdx % MainActivity.EGG_LABELS.size]} UOVO #$n TROVATO!"; binding.throwZone.visibility = View.VISIBLE }
        PlayState.THROWING    -> Unit
        PlayState.KEY_OBTAINED -> binding.tvGamePhase.text = "🪣 Secchiello pieno (${bucketHeld}/${getBucketCapacity()}) - Vai alla cassaforte!"
        PlayState.NEAR_SAFE   -> { binding.tvGamePhase.text = "CASSAFORTE VICINA!"; binding.btnInsertKey.visibility = View.VISIBLE }
        PlayState.TICKET_SHOWN -> binding.tvGamePhase.text = "Leggi il biglietto!"
    }
}

internal fun MainActivity.updateKeyInventoryUI() {
    binding.llKeyInventory.removeAllViews()
    if (gamePhase != GamePhase.PLAYING && gamePhase != GamePhase.STATS) return
    val dp = resources.displayMetrics.density; val size = (16 * dp).toInt(); val m = (3 * dp).toInt()
    eggs.filter { !it.isTrap }.forEachIndexed { i, _ ->
        val inserted = i < realEggsCaught; val caught = i == realEggsCaught && keyInPocket
        val c = MainActivity.EGG_COLORS[i % MainActivity.EGG_COLORS.size]
        binding.llKeyInventory.addView(android.view.View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(size, size).also { lp -> lp.setMargins(m,0,m,0) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                when {
                    inserted -> { setColor(c); setStroke((2*dp).toInt(), AndroidColor.WHITE) }
                    caught   -> { setColor(c); setStroke((2*dp).toInt(), AndroidColor.YELLOW) }
                    else     -> { setColor(AndroidColor.TRANSPARENT); setStroke((2*dp).toInt(),
                        AndroidColor.argb(110, AndroidColor.red(c), AndroidColor.green(c), AndroidColor.blue(c))) }
                }
            }
        })
    }
}

internal fun MainActivity.updateMenuVisibility(visible: Boolean) {
    if (visible) {
        binding.inGameMenuBg.visibility = View.VISIBLE
        binding.inGameMenuSheet.visibility = View.VISIBLE
    } else {
        binding.inGameMenuBg.visibility = View.GONE
        binding.inGameMenuSheet.visibility = View.GONE
    }
}

internal fun MainActivity.openInGameMenu() {
    viewModel.toggleMenu()
    val state = viewModel.uiState.value
    binding.menuTvPlayer.text = "${state.currentPlayer} - Uovo ${state.currentEggIdx + 1}/${eggs.size}"
    binding.menuTvTimer.text = "Tempo: ${viewModel.formatTime(state.elapsedMs)}"
    binding.menuTvProgress.text = "${state.realEggsCaught}/${eggs.count { !it.isTrap }} uova trovate"
    binding.menuTvRiddleCount.text = "${state.riddleCount} indovinelli"
    binding.menuTvSaveInfo.text = "Salvataggi: ..."

    lifecycleScope.launch(Dispatchers.IO) {
        val count = try { dm.getSaveSlots().size } catch (_: Exception) { 0 }
        withContext(Dispatchers.Main) { binding.menuTvSaveInfo.text = "Salvataggi: $count" }
    }

    // *** FIX: cancella esplicitamente le animazioni precedenti prima di avviarne di nuove.
    binding.inGameMenuBg.animate().cancel()
    binding.inGameMenuSheet.animate().cancel()

    binding.inGameMenuBg.visibility = View.VISIBLE
    binding.inGameMenuBg.alpha = 0f
    binding.inGameMenuBg.animate().alpha(1f).setDuration(220).start()
    binding.inGameMenuSheet.visibility = View.VISIBLE
    binding.inGameMenuSheet.translationY = 1200f
    binding.inGameMenuSheet.animate().translationY(0f).setDuration(340)
        .setInterpolator(DecelerateInterpolator(2f)).start()
}

internal fun MainActivity.closeInGameMenu() {
    viewModel.closeMenu()
    binding.inGameMenuBg.animate().cancel()
    binding.inGameMenuSheet.animate().cancel()
    binding.inGameMenuBg.animate().alpha(0f).setDuration(200)
        .withEndAction { if (!viewModel.uiState.value.isMenuOpen) binding.inGameMenuBg.visibility = View.GONE }.start()
    binding.inGameMenuSheet.animate().translationY(1200f).setDuration(280)
        .setInterpolator(AccelerateInterpolator(2f))
        .withEndAction { if (!viewModel.uiState.value.isMenuOpen) binding.inGameMenuSheet.visibility = View.GONE }.start()
}

internal fun MainActivity.showGameStatusDialog() {
    val state = viewModel.uiState.value
    val sb = StringBuilder()
    sb.append("Giocatore: ${state.currentPlayer}\n")
    sb.append("Turni: ${if (state.turnMode=="alternating") "Alternati" else "Sequenziali"}\n")
    sb.append("Tempo: ${viewModel.formatTime(state.elapsedMs)}\n\n")
    sb.append("Uova trovate: ${state.realEggsCaught}/${eggs.count{!it.isTrap}}\n")
    if (state.penaltyAccumMs > 0) sb.append("Penalita' accumulate: ${viewModel.formatTime(state.penaltyAccumMs)}\n")
    AlertDialog.Builder(this).setTitle("Stato partita").setMessage(sb.toString()).setPositiveButton("OK", null).show()
}

internal fun MainActivity.confirmExit() {
    AlertDialog.Builder(this)
        .setTitle("Esci dalla partita?")
        .setMessage("Vuoi salvare la configurazione delle uova per ricominciare in seguito?")
        .setPositiveButton("Salva ed esci") { _, _ ->
            eggPlacementManager.saveCurrentSession()
            binding.inGameMenuBg.animate().cancel(); binding.inGameMenuSheet.animate().cancel()
            binding.inGameMenuBg.visibility = View.GONE; binding.inGameMenuSheet.visibility = View.GONE
            finish()
        }
        .setNeutralButton("Esci senza salvare") { _, _ ->
            binding.inGameMenuBg.animate().cancel(); binding.inGameMenuSheet.animate().cancel()
            finish()
        }
        .setNegativeButton("Annulla", null).show()
}

internal fun MainActivity.setupButtons() {
    val ctx = this
    binding.btnStart.setOnClickListener { startHunt() }
    binding.btnHint.setOnClickListener { onHintRequested() }
    binding.btnInsertKey.setOnClickListener { safeManager.insertKeyInSafe() }
    binding.btnCloseRiddle.setOnClickListener { safeManager.onCloseTicket() }
    binding.btnDeleteEgg.setOnClickListener { eggPlacementManager.deleteSelectedEgg() }
    binding.btnPlayAgain.setOnClickListener { finish() }
    binding.btnFullReset.setOnClickListener { goToHome() }
    binding.btnReset.setOnClickListener { openInGameMenu() }
    binding.btnTrapToggle.setOnClickListener {
        nextEggIsTrap = !nextEggIsTrap; updateTrapToggleUI()
    }
    binding.inGameMenuBg.setOnClickListener { closeInGameMenu() }
    binding.menuBtnResume.setOnClickListener { closeInGameMenu() }
    binding.menuBtnHome.setOnClickListener { closeInGameMenu(); goToHome() }
    binding.menuBtnSave.setOnClickListener {
        val saveTime = dm.todayString()
        binding.menuTvSaveInfo.text = "Salvataggio..."
        lifecycleScope.launch(Dispatchers.IO) {
            try { eggPlacementManager.saveCurrentSession() } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                binding.menuTvSaveInfo.text = "Salvato: $saveTime"
                Toast.makeText(ctx, "Partita salvata!", Toast.LENGTH_LONG).show()
            }
        }
    }
    binding.menuBtnRiddles.setOnClickListener { pickRiddlesInGame.launch(arrayOf("text/plain")) }
    binding.menuBtnStatus.setOnClickListener { closeInGameMenu(); showGameStatusDialog() }
    binding.menuBtnHelp.setOnClickListener { closeInGameMenu(); startActivity(Intent(this, HelpActivity::class.java)) }
    binding.menuBtnExit.setOnClickListener { closeInGameMenu(); confirmExit() }
}

internal fun MainActivity.updateTrapToggleUI() {
    if (nextEggIsTrap) {
        binding.btnTrapToggle.text = "⚠️ Trappola"
        binding.btnTrapToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(AndroidColor.parseColor("#CC7C1A3A"))
    } else {
        binding.btnTrapToggle.text = "🥚 Normale"
        binding.btnTrapToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(AndroidColor.parseColor("#CC1A0A3A"))
    }
}

internal fun MainActivity.goToHome() {
    val intent = Intent(this, HomeActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    startActivity(intent)
    finish()
}
