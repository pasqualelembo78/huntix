package com.intelligame.huntix

import com.google.firebase.database.FirebaseDatabase

object IndoorRoomManager {
    data class PlayerScore(val playerId: String, val playerName: String, val eggsFound: Int, val totalMs: Long, val finished: Boolean = false)

    data class RoomConfig(val eggCount: Int = 0)
    data class RoomInfo(val config: RoomConfig = RoomConfig())

    private val rooms get() = FirebaseDatabase.getInstance().getReference("indoor_rooms")

    fun finishGame(code: String) {
        rooms.child(code).child("finished").setValue(true)
    }

    fun getRoomInfo(code: String, onSuccess: (RoomInfo) -> Unit, onError: (String) -> Unit) {
        rooms.child(code).child("info").get()
            .addOnSuccessListener { snap ->
                var eggCount = snap.child("eggCount").getValue(Int::class.java) ?: 0
                if (eggCount == 0) {
                    rooms.child(code).child("setup/eggs").get()
                        .addOnSuccessListener { eg ->
                            eggCount = eg.childrenCount.toInt()
                            onSuccess(RoomInfo(RoomConfig(eggCount)))
                        }
                        .addOnFailureListener { onSuccess(RoomInfo(RoomConfig(eggCount))) }
                } else {
                    onSuccess(RoomInfo(RoomConfig(eggCount)))
                }
            }
            .addOnFailureListener { onError(it.message ?: "Errore info stanza") }
    }
}
