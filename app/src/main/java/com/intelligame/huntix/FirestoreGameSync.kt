package com.intelligame.huntix

import com.google.firebase.firestore.FirebaseFirestore

object FirestoreGameSync {
    data class ScoreEntry(val playerId: String, val playerName: String, val eggsFound: Int, val totalMs: Long, val finished: Boolean = false)

    data class IndoorMpSession(
        val roomCode: String,
        val hostName: String,
        val config: IndoorRoomManager.RoomConfig,
        val scores: List<IndoorRoomManager.PlayerScore>
    ) {
        fun toMap(): Map<String, Any> = mapOf(
            "roomCode" to roomCode,
            "hostName" to hostName,
            "config" to mapOf("eggCount" to config.eggCount),
            "scores" to scores.map { s ->
                mapOf(
                    "playerId" to s.playerId,
                    "playerName" to s.playerName,
                    "eggsFound" to s.eggsFound,
                    "totalMs" to s.totalMs,
                    "finished" to s.finished
                )
            }
        )
    }

    fun buildIndoorMpSession(
        roomCode: String,
        hostName: String,
        config: IndoorRoomManager.RoomConfig,
        scores: List<IndoorRoomManager.PlayerScore>
    ): IndoorMpSession = IndoorMpSession(roomCode, hostName, config, scores)

    fun saveSession(data: IndoorMpSession) {
        FirebaseFirestore.getInstance()
            .collection("indoor_sessions")
            .document(data.roomCode)
            .set(data.toMap())
    }
}
