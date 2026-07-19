package com.intelligame.huntix.model

import android.animation.ValueAnimator
import com.google.ar.core.Anchor
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode

enum class GamePhase { SCAN_ROOM, SETUP_SAFE, SETUP_EGGS, PLAYING, STATS }

enum class PlayState { SEARCHING, NEAR_EGG, THROWING, KEY_OBTAINED, NEAR_SAFE, TICKET_SHOWN }

data class EggObject(
    val id: Int,
    val colorIdx: Int,
    val shape: String,
    val anchorNode: AnchorNode,
    val eggNode: Node,
    var isTrap: Boolean = false,
    val trapMarkerNode: SphereNode? = null,
    var pulseAnim: ValueAnimator? = null
)

data class SafeObject(
    val type: String,
    val anchorNode: AnchorNode,
    val bodyNode: Node,
    val doorNode: Node,
    val dialNode: CylinderNode,
    val keySlots: MutableList<CylinderNode> = mutableListOf()
)

data class EggRecord(
    val eggNumber: Int,
    val timeMs: Long
)

data class PlayerRecord(
    val playerName: String,
    val eggsFound: Int,
    val totalMs: Long,
    val eggRecords: List<EggRecord>
)

data class IndoorGameUiState(
    val gamePhase: GamePhase = GamePhase.SETUP_SAFE,
    val playState: PlayState = PlayState.SEARCHING,
    val eggCount: Int = 0,
    val trapCount: Int = 0,
    val currentEggIdx: Int = 0,
    val keyInPocket: Boolean = false,
    val realEggsCaught: Int = 0,
    val activePlayers: List<String> = emptyList(),
    val currentPlayerIdx: Int = 0,
    val currentPlayer: String = "Giocatore",
    val eggOwners: List<String> = emptyList(),
    val turnMode: String = "sequential",
    val eggTimesMs: List<Long> = emptyList(),
    val penaltyAccumMs: Long = 0,
    val huntStartMs: Long = 0,
    val eggStartMs: Long = 0,
    val riddles: List<String> = emptyList(),
    val riddleCount: Int = 0,
    val isMultiplayer: Boolean = false,
    val isIndoorMp: Boolean = false,
    val isMenuOpen: Boolean = false,
    val hintCooldownUntilMs: Long = 0,
    val mpUnreadCount: Int = 0,
    val mpChatOpen: Boolean = false,
    val isPlaying: Boolean = false,
    val elapsedMs: Long = 0
)
