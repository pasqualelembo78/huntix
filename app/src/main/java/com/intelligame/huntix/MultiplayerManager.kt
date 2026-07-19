package com.intelligame.huntix

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.getValue

class MultiplayerManager private constructor() {

    data class PlayerScore(
        val playerId: String, val playerName: String,
        val eggsFound: Int, val totalMs: Long, val finished: Boolean = false
    )
    data class ChatMessage(
        val type: String = "msg",
        val senderId: String = "",
        val senderName: String = "",
        val text: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    companion object {
        private var instance: MultiplayerManager? = null
        fun get(): MultiplayerManager = instance ?: MultiplayerManager().also { instance = it }

        const val EXTRA_IS_MP = "is_multiplayer"
        const val EXTRA_IS_HOST = "is_host"
        const val EXTRA_ROOM_CODE = "room_code"
        const val EXTRA_PLAYER_ID = "player_id"
        const val EXTRA_PLAYER_NAME = "player_name"
        const val EXTRA_ROOM_NAME = "room_name"
    }

    var roomCode: String = ""
    var playerId: String = ""
    var playerName: String = ""

    var onScoresChanged: ((List<PlayerScore>) -> Unit)? = null
    var onChatMessage: ((ChatMessage) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val rooms get() = FirebaseDatabase.getInstance().getReference("mp_rooms")
    private var scoresListener: ValueEventListener? = null
    private var chatListener: ChildEventListener? = null

    fun configure(code: String, playerId: String, playerName: String) {
        this.roomCode = code
        this.playerId = playerId
        this.playerName = playerName
        attachListeners()
    }

    private fun attachListeners() {
        detachListeners()
        if (roomCode.isEmpty()) return
        scoresListener = rooms.child(roomCode).child("scores")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val list = snap.children.mapNotNull { c ->
                        PlayerScore(
                            playerId = c.child("playerId").getValue(String::class.java) ?: "",
                            playerName = c.child("playerName").getValue(String::class.java) ?: "",
                            eggsFound = c.child("eggsFound").getValue(Int::class.java) ?: 0,
                            totalMs = c.child("totalMs").getValue(Long::class.java) ?: 0L,
                            finished = c.child("finished").getValue(Boolean::class.java) ?: false
                        )
                    }
                    onScoresChanged?.invoke(list)
                }
                override fun onCancelled(e: DatabaseError) { onError?.invoke(e.message) }
            })
        chatListener = rooms.child(roomCode).child("chat")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snap: DataSnapshot, prev: String?) {
                    snap.getValue(ChatMessage::class.java)?.let { onChatMessage?.invoke(it) }
                }
                override fun onChildChanged(snap: DataSnapshot, prev: String?) {}
                override fun onChildRemoved(snap: DataSnapshot) {}
                override fun onChildMoved(snap: DataSnapshot, prev: String?) {}
                override fun onCancelled(e: DatabaseError) { onError?.invoke(e.message) }
            })
    }

    private fun detachListeners() {
        scoresListener?.let { rooms.child(roomCode).child("scores").removeEventListener(it) }
        chatListener?.let { rooms.child(roomCode).child("chat").removeEventListener(it) }
        scoresListener = null
        chatListener = null
    }

    fun reportEggFound(eggIdx: Int, elapsed: Long) {
        if (roomCode.isEmpty()) return
        rooms.child(roomCode).child("eggsFound/$playerId")
            .setValue(mapOf("eggIdx" to eggIdx, "elapsed" to elapsed))
    }

    fun updateMyScore(eggsFound: Int, totalMs: Long, finished: Boolean = false) {
        if (roomCode.isEmpty()) return
        rooms.child(roomCode).child("scores/$playerId")
            .setValue(mapOf(
                "playerId" to playerId,
                "playerName" to playerName,
                "eggsFound" to eggsFound,
                "totalMs" to totalMs,
                "finished" to finished
            ))
    }

    fun sendChatMessage(text: String) {
        if (roomCode.isEmpty() || text.isBlank()) return
        val msg = ChatMessage(
            type = "msg",
            senderId = playerId,
            senderName = playerName,
            text = text.take(500)
        )
        rooms.child(roomCode).child("chat").push().setValue(msg)
    }

    fun disconnect() {
        detachListeners()
        roomCode = ""
    }
}
