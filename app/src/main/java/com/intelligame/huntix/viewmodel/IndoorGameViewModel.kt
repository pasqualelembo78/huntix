package com.intelligame.huntix.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intelligame.huntix.model.GamePhase
import com.intelligame.huntix.model.IndoorGameUiState
import com.intelligame.huntix.model.PlayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class IndoorGameViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(IndoorGameUiState())
    val uiState: StateFlow<IndoorGameUiState> = _uiState.asStateFlow()

    // Game configuration (set once from intent)
    var turnMode: String = "sequential"
        internal set
    var eggSetupMode: String = "manual"
        internal set
    var arMode: String = "depth"
        internal set
    var autoEggCount: Int = 4
        internal set
    var trapEggCount: Int = 0
        internal set
    var penaltySecs: Int = 30
        internal set
    var selectedSafeType: String = "classic"
        internal set
    var isMultiplayer: Boolean = false
        private set
    var isIndoorMp: Boolean = false
        private set
    var indoorRoomCode: String = ""
        private set
    var indoorIsHost: Boolean = false
        private set
    var indoorCurrentPlayer: String = ""
        private set
    var isRestoreMode: Boolean = false
        internal set
    var restoreSlotId: String = ""
        private set
    var nextEggIsTrap: Boolean = false
    var nextEggShape: String = "sphere"

    private val _riddles = mutableListOf<String>()
    val riddles: List<String> get() = _riddles.toList()

    private var timerJob: Job? = null

    fun initialize(
        players: List<String>,
        riddles: List<String>,
        turnMode: String,
        eggSetupMode: String,
        autoEggCount: Int,
        trapEggCount: Int,
        penaltySecs: Int,
        arMode: String,
        isMultiplayer: Boolean,
        isIndoorMp: Boolean,
        indoorRoomCode: String,
        indoorIsHost: Boolean,
        indoorCurrentPlayer: String,
        isRestoreMode: Boolean,
        restoreSlotId: String
    ) {
        this.turnMode = turnMode
        this.eggSetupMode = eggSetupMode
        this.autoEggCount = autoEggCount
        this.trapEggCount = trapEggCount
        this.penaltySecs = penaltySecs
        this.arMode = arMode
        this.isMultiplayer = isMultiplayer
        this.isIndoorMp = isIndoorMp
        this.indoorRoomCode = indoorRoomCode
        this.indoorIsHost = indoorIsHost
        this.indoorCurrentPlayer = indoorCurrentPlayer
        this.isRestoreMode = isRestoreMode
        this.restoreSlotId = restoreSlotId
        _riddles.clear()
        _riddles.addAll(riddles)

        _uiState.update {
            it.copy(
                activePlayers = players,
                riddles = _riddles.toList(),
                riddleCount = _riddles.size,
                currentPlayer = players.firstOrNull() ?: "Giocatore",
                gamePhase = if (arMode == "room_scan") GamePhase.SCAN_ROOM else GamePhase.SETUP_SAFE
            )
        }
    }

    // ── Phase transitions ───────────────────────────────────────

    fun onScanComplete() {
        _uiState.update { it.copy(gamePhase = GamePhase.SETUP_SAFE) }
    }

    fun onSafePlaced() {
        _uiState.update { it.copy(gamePhase = GamePhase.SETUP_EGGS) }
    }

    fun startHunt(totalEggs: Int) {
        val players = _uiState.value.activePlayers
        val owners = when (turnMode) {
            "alternating" -> (0 until totalEggs).map { players[it % players.size] }
            else -> players.map { _ -> _uiState.value.currentPlayer }
        }
        val now = currentTimeMs()
        _uiState.update {
            it.copy(
                gamePhase = GamePhase.PLAYING,
                playState = PlayState.SEARCHING,
                currentEggIdx = 0,
                keyInPocket = false,
                eggCount = totalEggs,
                eggTimesMs = emptyList(),
                realEggsCaught = 0,
                penaltyAccumMs = 0,
                eggOwners = owners,
                huntStartMs = now,
                eggStartMs = now,
                isPlaying = true
            )
        }
        startTimer()
    }

    // ── Direct setters (used by Activity delegation) ────────────

    fun setGamePhase(v: GamePhase) { _uiState.update { it.copy(gamePhase = v) } }
    fun setPlayState(v: PlayState) { _uiState.update { it.copy(playState = v) } }
    fun setCurrentEggIdx(v: Int) { _uiState.update { it.copy(currentEggIdx = v) } }
    fun setKeyInPocket(v: Boolean) { _uiState.update { it.copy(keyInPocket = v) } }
    fun setRealEggsCaught(v: Int) { _uiState.update { it.copy(realEggsCaught = v) } }
    fun setActivePlayers(v: List<String>) { _uiState.update { it.copy(activePlayers = v, currentPlayer = v.firstOrNull() ?: "Giocatore") } }
    fun setCurrentPlayer(v: String) { _uiState.update { it.copy(currentPlayer = v) } }
    fun setEggOwners(v: List<String>) { _uiState.update { it.copy(eggOwners = v) } }
    fun setMenuOpen(v: Boolean) { _uiState.update { it.copy(isMenuOpen = v) } }
    fun setHuntStartMs(v: Long) { _uiState.update { it.copy(huntStartMs = v) } }
    fun setEggStartMs(v: Long) { _uiState.update { it.copy(eggStartMs = v) } }
    fun setPenaltyAccumMs(v: Long) { _uiState.update { it.copy(penaltyAccumMs = v) } }
    fun setHintCooldown(v: Long) { _uiState.update { it.copy(hintCooldownUntilMs = v) } }
    fun setIsRestoreMode(v: Boolean) { isRestoreMode = v }
    fun setRestoreSlotId(v: String) { restoreSlotId = v }

    // ── Gameplay actions ────────────────────────────────────────

    fun onEggNearby() {
        _uiState.update { it.copy(playState = PlayState.NEAR_EGG) }
    }

    fun onEggSearching() {
        _uiState.update { it.copy(playState = PlayState.SEARCHING) }
    }

    fun onThrowStart() {
        _uiState.update { it.copy(playState = PlayState.THROWING) }
    }

    fun onThrowHit(isTrap: Boolean, penaltySecs: Int) {
        if (isTrap) {
            val penalty = penaltySecs * 1000L
            _uiState.update {
                it.copy(
                    penaltyAccumMs = it.penaltyAccumMs + penalty,
                    eggStartMs = currentTimeMs(),
                    currentEggIdx = it.currentEggIdx + 1,
                    keyInPocket = false,
                    playState = PlayState.SEARCHING
                )
            }
            checkGameEnd()
            return
        }

        val elapsed = currentTimeMs() - _uiState.value.eggStartMs
        _uiState.update {
            it.copy(
                realEggsCaught = it.realEggsCaught + 1,
                keyInPocket = true,
                playState = PlayState.KEY_OBTAINED,
                eggTimesMs = it.eggTimesMs + elapsed
            )
        }
    }

    fun onNearSafe() {
        _uiState.update { it.copy(playState = PlayState.NEAR_SAFE) }
    }

    fun onSafeFar() {
        _uiState.update { it.copy(playState = PlayState.KEY_OBTAINED) }
    }

    fun onTicketShown() {
        _uiState.update { it.copy(playState = PlayState.TICKET_SHOWN) }
    }

    fun onTicketClosed() {
        _uiState.update {
            val nextIdx = it.currentEggIdx + 1
            it.copy(
                currentEggIdx = nextIdx,
                keyInPocket = false,
                eggStartMs = currentTimeMs(),
                playState = PlayState.SEARCHING
            )
        }
        checkGameEnd()
    }

    fun nextTurn() {
        _uiState.update { state ->
            val idx = state.currentPlayerIdx + 1
            state.copy(
                currentPlayerIdx = idx,
                currentPlayer = state.activePlayers.getOrElse(idx) { state.currentPlayer }
            )
        }
    }

    private fun checkGameEnd() {
        val state = _uiState.value
        if (state.currentEggIdx >= state.eggCount) {
            finishGame()
        }
    }

    fun finishGame() {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                gamePhase = GamePhase.STATS,
                isPlaying = false,
                elapsedMs = currentTimeMs() - it.huntStartMs + it.penaltyAccumMs
            )
        }
    }

    fun resetForNextPlayer() {
        val now = currentTimeMs()
        _uiState.update {
            it.copy(
                currentEggIdx = 0,
                keyInPocket = false,
                eggTimesMs = emptyList(),
                realEggsCaught = 0,
                penaltyAccumMs = 0,
                huntStartMs = now,
                eggStartMs = now,
                isPlaying = true,
                gamePhase = GamePhase.PLAYING,
                playState = PlayState.SEARCHING
            )
        }
        startTimer()
    }

    // ── Menu ────────────────────────────────────────────────────

    fun toggleMenu() {
        _uiState.update { it.copy(isMenuOpen = !it.isMenuOpen) }
    }

    fun closeMenu() {
        _uiState.update { it.copy(isMenuOpen = false) }
    }

    fun onHintConsumed() {
        _uiState.update { it.copy(hintCooldownUntilMs = currentTimeMs() + 3_000L) }
    }

    // ── Chat ────────────────────────────────────────────────────

    fun toggleChat() {
        _uiState.update {
            it.copy(
                mpChatOpen = !it.mpChatOpen,
                mpUnreadCount = if (!it.mpChatOpen) 0 else it.mpUnreadCount
            )
        }
    }

    fun incrementUnread() {
        _uiState.update { it.copy(mpUnreadCount = it.mpUnreadCount + 1) }
    }

    fun markChatRead() {
        _uiState.update { it.copy(mpUnreadCount = 0) }
    }

    // ── Riddles ─────────────────────────────────────────────────

    fun updateRiddles(newRiddles: List<String>) {
        _riddles.clear()
        _riddles.addAll(newRiddles)
        _uiState.update { it.copy(riddles = _riddles.toList(), riddleCount = _riddles.size) }
    }

    // ── Timer ───────────────────────────────────────────────────

    private var timerEnabled = false

    fun startTimer() {
        timerJob?.cancel()
        timerEnabled = true
        timerJob = viewModelScope.launch {
            while (isActive && timerEnabled) {
                val state = _uiState.value
                if (state.isPlaying && state.playState != PlayState.TICKET_SHOWN) {
                    val elapsed = currentTimeMs() - state.huntStartMs + state.penaltyAccumMs
                    _uiState.update { it.copy(elapsedMs = elapsed) }
                }
                delay(500)
            }
        }
    }

    fun stopTimer() {
        timerEnabled = false
        timerJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    fun loadRiddlesFromUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val lines = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
                        ?.filter { it.isNotBlank() } ?: emptyList()
                }
                updateRiddles(lines)
            } catch (_: Exception) {
                // silently ignore — caller handles Toast
            }
        }
    }

    // ── Utility ─────────────────────────────────────────────────

    private fun currentTimeMs(): Long = System.currentTimeMillis()

    fun formatTime(ms: Long): String {
        val secs = ms / 1000
        val mins = secs / 60
        return if (mins > 0) "${mins}m${"%02d".format(secs % 60)}s" else "${secs}s"
    }
}
