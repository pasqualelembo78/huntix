package com.intelligame.huntix

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import io.github.sceneview.ar.node.AnchorNode

object IndoorSessionManager {

    data class IndoorEggData(
        val idx: Int = 0,
        val dx: Float = 0f,
        val dy: Float = 0f,
        val dz: Float = 0f,
        val colorIdx: Int = 0,
        val shape: String = "sphere",
        val isTrap: Boolean = false,
        val found: Boolean = false,
        val cloudAnchorId: String = ""
    )

    data class SafeData(
        val safeType: String = "classic",
        val dx: Float = 0f,
        val dy: Float = 0f,
        val dz: Float = 0f,
        val cloudAnchorId: String = ""
    )

    data class RoomSnapshot(
        val safe: SafeData = SafeData(),
        val eggs: List<IndoorEggData> = emptyList(),
        val players: List<String> = emptyList(),
        val safeAnchorId: String = "",
        val eggAnchorIds: List<String> = emptyList(),
        val eggPositions: List<FloatArray> = emptyList()
    )

    private val rooms get() = FirebaseDatabase.getInstance().getReference("indoor_rooms")

    fun removeListener(roomCode: String, path: String, listener: ValueEventListener) {
        rooms.child(roomCode).child(path).removeEventListener(listener)
    }

    fun markEggFound(code: String, eggIdx: Int, playerUid: String, playerName: String, eggsFound: Int, totalMs: Long) {
        rooms.child(code).child("found/$playerUid")
            .setValue(mapOf("eggIdx" to eggIdx, "playerName" to playerName, "eggsFound" to eggsFound, "totalMs" to totalMs))
    }

    fun finishGame(code: String, playerUid: String, playerName: String, totalMs: Long, eggsFound: Int) {
        rooms.child(code).child("results/$playerUid")
            .setValue(mapOf("playerName" to playerName, "totalMs" to totalMs, "eggsFound" to eggsFound))
    }

    fun advanceTurn(roomCode: String) {
        rooms.child(roomCode).child("turnIndex").setValue(ServerValue.increment(1L))
    }

    fun getRoomSnapshot(code: String, onSuccess: (RoomSnapshot) -> Unit, onError: (String) -> Unit) {
        rooms.child(code).child("setup").get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) { onError("Stanza senza setup salvato"); return@addOnSuccessListener }
                onSuccess(snap.toRoomSnapshot())
            }
            .addOnFailureListener { onError(it.message ?: "Errore lettura stanza") }
    }

    fun updateSafeCloudAnchor(roomCode: String, anchorId: String) {
        rooms.child(roomCode).child("setup/safe/cloudAnchorId").setValue(anchorId)
    }

    fun updateEggCloudAnchor(roomCode: String, eggIdx: Int, anchorId: String) {
        rooms.child(roomCode).child("setup/eggs/$eggIdx/cloudAnchorId").setValue(anchorId)
    }

    fun saveEggSetup(
        code: String,
        safe: SafeData,
        eggs: List<IndoorEggData>,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val payload = mapOf(
            "safe" to mapOf(
                "safeType" to safe.safeType,
                "cloudAnchorId" to safe.cloudAnchorId,
                "dx" to safe.dx, "dy" to safe.dy, "dz" to safe.dz
            ),
            "eggs" to eggs.map { e ->
                mapOf(
                    "idx" to e.idx, "dx" to e.dx, "dy" to e.dy, "dz" to e.dz,
                    "colorIdx" to e.colorIdx, "shape" to e.shape,
                    "isTrap" to e.isTrap, "found" to e.found, "cloudAnchorId" to e.cloudAnchorId
                )
            }
        )
        rooms.child(code).child("setup").setValue(payload)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Errore salvataggio setup") }
    }

    private fun DataSnapshot.toRoomSnapshot(): RoomSnapshot {
        val safeSnap = child("safe")
        val safe = SafeData(
            safeType = safeSnap.child("safeType").getValue(String::class.java) ?: "classic",
            cloudAnchorId = safeSnap.child("cloudAnchorId").getValue(String::class.java) ?: "",
            dx = (safeSnap.child("dx").getValue(Double::class.java) ?: 0.0).toFloat(),
            dy = (safeSnap.child("dy").getValue(Double::class.java) ?: 0.0).toFloat(),
            dz = (safeSnap.child("dz").getValue(Double::class.java) ?: 0.0).toFloat()
        )
        val eggs = child("eggs").children.mapNotNull { e ->
            IndoorEggData(
                idx = e.child("idx").getValue(Int::class.java) ?: 0,
                dx = (e.child("dx").getValue(Double::class.java) ?: 0.0).toFloat(),
                dy = (e.child("dy").getValue(Double::class.java) ?: 0.0).toFloat(),
                dz = (e.child("dz").getValue(Double::class.java) ?: 0.0).toFloat(),
                colorIdx = e.child("colorIdx").getValue(Int::class.java) ?: 0,
                shape = e.child("shape").getValue(String::class.java) ?: "sphere",
                isTrap = e.child("isTrap").getValue(Boolean::class.java) ?: false,
                found = e.child("found").getValue(Boolean::class.java) ?: false,
                cloudAnchorId = e.child("cloudAnchorId").getValue(String::class.java) ?: ""
            )
        }
        val players = child("players").children.mapNotNull { it.getValue(String::class.java) }
        return RoomSnapshot(safe = safe, eggs = eggs, players = players)
    }
}
