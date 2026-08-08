package com.intelligame.huntix

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * IndoorMultiplayerBridge — gestisce la sincronizzazione Firebase
 * per il multiplayer indoor (store condivisi).
 *
 * Firebase path: indoor_store_rooms/{roomCode}/
 *   - host/position: {x, y, z}
 *   - host/rotation: {x, y, z, w}
 *   - host/cloudAnchorId: "xxx"
 *   - players/{uid}: {name, joinedAt}
 *   - events/{timestamp}: {event, itemId/npcId, playerName}
 */
object IndoorMultiplayerBridge {

    private val rooms get() = FirebaseDatabase.getInstance().getReference("indoor_store_rooms")

    private val activeListeners = mutableListOf<ValueEventListener>()

    /**
     * Crea una nuova room e restituisce il room code.
     */
    fun createRoom(
        hostUid: String,
        hostName: String,
        cloudAnchorId: String,
        position: Triple<Float, Float, Float>,
        rotation: Quadruple,
        callback: (String) -> Unit
    ) {
        val roomRef = rooms.push()
        val roomCode = roomRef.key ?: return

        val roomData = mapOf(
            "host" to mapOf(
                "uid" to hostUid,
                "name" to hostName,
                "cloudAnchorId" to cloudAnchorId,
                "position" to mapOf("x" to position.first, "y" to position.second, "z" to position.third),
                "rotation" to mapOf("x" to rotation.x, "y" to rotation.y, "z" to rotation.z, "w" to rotation.w),
                "createdAt" to System.currentTimeMillis()
            ),
            "players" to mapOf(
                hostUid to mapOf("name" to hostName, "joinedAt" to System.currentTimeMillis())
            )
        )

        roomRef.setValue(roomData).addOnSuccessListener {
            callback(roomCode)
        }
    }

    /**
     * Unisce a una room esistente.
     */
    fun joinRoom(roomCode: String, playerUid: String, playerName: String, callback: (Boolean) -> Unit) {
        val playerRef = rooms.child(roomCode).child("players").child(playerUid)
        playerRef.setValue(mapOf("name" to playerName, "joinedAt" to System.currentTimeMillis()))
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    /**
     * Ottiene i dati della host (cloud anchor + posizione).
     */
    fun getHostData(roomCode: String, callback: (HostData?) -> Unit) {
        rooms.child(roomCode).child("host").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.getValue(HostData::class.java)
                callback(data)
            }
            override fun onCancelled(error: DatabaseError) {
                callback(null)
            }
        })
    }

    /**
     * Ascolta gli eventi in tempo reale (item raccolti, NPC interazioni).
     */
    fun listenEvents(roomCode: String, onEvent: (StoreEvent) -> Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val event = child.getValue(StoreEvent::class.java)
                    if (event != null) onEvent(event)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        rooms.child(roomCode).child("events").addValueEventListener(listener)
        activeListeners.add(listener)
    }

    /**
     * Pubblica un evento (item raccolto / NPC interazione).
     */
    fun publishEvent(roomCode: String, event: String, data: String, playerName: String) {
        val eventRef = rooms.child(roomCode).child("events").push()
        eventRef.setValue(mapOf(
            "event" to event,
            "data" to data,
            "playerName" to playerName,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /**
     * Lascia la room e pulisce i listener.
     */
    fun leaveRoom(roomCode: String, playerUid: String) {
        rooms.child(roomCode).child("players").child(playerUid).removeValue()
        activeListeners.forEach { rooms.child(roomCode).child("events").removeEventListener(it) }
        activeListeners.clear()
    }

    // ── Data classes ──

    data class HostData(
        val uid: String = "",
        val name: String = "",
        val cloudAnchorId: String = "",
        val position: PositionData = PositionData(),
        val rotation: RotationData = RotationData()
    )

    data class PositionData(
        val x: Float = 0f,
        val y: Float = 0f,
        val z: Float = 0f
    )

    data class RotationData(
        val x: Float = 0f,
        val y: Float = 0f,
        val z: Float = 0f,
        val w: Float = 1f
    )

    data class StoreEvent(
        val event: String = "",
        val data: String = "",
        val playerName: String = "",
        val timestamp: Long = 0
    )

    data class Quadruple(val x: Float, val y: Float, val z: Float, val w: Float)
}
