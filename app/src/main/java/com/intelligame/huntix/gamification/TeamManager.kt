package com.intelligame.huntix.gamification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * TeamManager — Sistema squadre con chat real-time e classifica cooperativa.
 *
 * Firestore:
 *   teams/{teamId}
 *     - name, tag, description, leaderId, leaderName, members[], memberCount,
 *       totalEggs, totalXp, weeklyEggs, weeklyXp, createdAt, isOpen
 *   teams/{teamId}/messages/{msgId}
 *     - senderId, senderName, text, timestamp, emoji
 *   players/{uid}.teamId → riferimento alla squadra
 */
object TeamManager {

    private val db get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var chatListener: ListenerRegistration? = null

    data class Team(
        val teamId: String = "",
        val name: String = "",
        val tag: String = "",
        val description: String = "",
        val leaderId: String = "",
        val leaderName: String = "",
        val members: List<String> = emptyList(),
        val memberCount: Int = 0,
        val totalEggs: Long = 0L,
        val totalXp: Long = 0L,
        val weeklyEggs: Long = 0L,
        val weeklyXp: Long = 0L,
        val createdAt: Long = System.currentTimeMillis(),
        val isOpen: Boolean = true
    ) {
        fun toMap(): Map<String, Any> = mapOf(
            "teamId" to teamId, "name" to name, "tag" to tag,
            "description" to description, "leaderId" to leaderId, "leaderName" to leaderName,
            "members" to members, "memberCount" to memberCount,
            "totalEggs" to totalEggs, "totalXp" to totalXp,
            "weeklyEggs" to weeklyEggs, "weeklyXp" to weeklyXp,
            "createdAt" to createdAt, "isOpen" to isOpen
        )

        companion object {
            fun fromMap(map: Map<String, Any?>): Team? {
                val id = map["teamId"] as? String ?: return null
                @Suppress("UNCHECKED_CAST")
                return Team(
                    teamId = id,
                    name = map["name"] as? String ?: "",
                    tag = map["tag"] as? String ?: "",
                    description = map["description"] as? String ?: "",
                    leaderId = map["leaderId"] as? String ?: "",
                    leaderName = map["leaderName"] as? String ?: "",
                    members = (map["members"] as? List<String>) ?: emptyList(),
                    memberCount = (map["memberCount"] as? Long)?.toInt() ?: 0,
                    totalEggs = map["totalEggs"] as? Long ?: 0L,
                    totalXp = map["totalXp"] as? Long ?: 0L,
                    weeklyEggs = map["weeklyEggs"] as? Long ?: 0L,
                    weeklyXp = map["weeklyXp"] as? Long ?: 0L,
                    createdAt = map["createdAt"] as? Long ?: 0L,
                    isOpen = map["isOpen"] as? Boolean ?: true
                )
            }
        }
    }

    data class ChatMessage(
        val msgId: String = "",
        val senderId: String = "",
        val senderName: String = "",
        val text: String = "",
        val emoji: String = "",
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun toMap(): Map<String, Any> = mapOf(
            "msgId" to msgId, "senderId" to senderId, "senderName" to senderName,
            "text" to text, "emoji" to emoji, "timestamp" to timestamp
        )

        companion object {
            fun fromMap(map: Map<String, Any?>): ChatMessage = ChatMessage(
                msgId = map["msgId"] as? String ?: "",
                senderId = map["senderId"] as? String ?: "",
                senderName = map["senderName"] as? String ?: "",
                text = map["text"] as? String ?: "",
                emoji = map["emoji"] as? String ?: "",
                timestamp = map["timestamp"] as? Long ?: 0L
            )
        }
    }

    // ─── CRUD Team ───────────────────────────────────────────────

    fun createTeam(name: String, tag: String, description: String, leaderName: String,
                   onSuccess: (Team) -> Unit, onError: (String) -> Unit) {
        if (uid.isEmpty()) { onError("Non autenticato"); return }
        if (name.length < 3) { onError("Nome squadra troppo corto (min 3 caratteri)"); return }
        if (tag.length < 2 || tag.length > 5) { onError("Tag deve essere 2-5 caratteri"); return }

        // Controlla se l'utente è già in una squadra
        db.collection("players").document(uid).get().addOnSuccessListener { playerDoc ->
            if (playerDoc.getString("teamId")?.isNotEmpty() == true) {
                onError("Sei già in una squadra. Esci prima di crearne una nuova."); return@addOnSuccessListener
            }
            val teamId = "team_${System.currentTimeMillis()}_${uid.take(6)}"
            val team = Team(
                teamId = teamId, name = name, tag = tag.uppercase(), description = description,
                leaderId = uid, leaderName = leaderName,
                members = listOf(uid), memberCount = 1
            )
            val batch = db.batch()
            batch.set(db.collection("teams").document(teamId), team.toMap())
            batch.update(db.collection("players").document(uid), mapOf("teamId" to teamId))
            batch.commit()
              .addOnSuccessListener { onSuccess(team) }
              .addOnFailureListener { onError(it.message ?: "Errore creazione squadra") }
        }.addOnFailureListener { onError(it.message ?: "Errore") }
    }

    fun joinTeam(teamId: String, playerName: String,
                 onSuccess: (Team) -> Unit, onError: (String) -> Unit) {
        if (uid.isEmpty()) { onError("Non autenticato"); return }
        val teamRef = db.collection("teams").document(teamId)
        db.runTransaction { tx ->
            val teamSnap = tx.get(teamRef)
            val team = Team.fromMap(teamSnap.data ?: emptyMap<String, Any>())
                ?: throw Exception("Squadra non trovata")
            if (!team.isOpen) throw Exception("Squadra non aperta alle iscrizioni")
            if (team.memberCount >= 30) throw Exception("Squadra piena (max 30 membri)")
            if (uid in team.members) throw Exception("Sei già in questa squadra")

            tx.update(teamRef, mapOf(
                "members" to FieldValue.arrayUnion(uid),
                "memberCount" to FieldValue.increment(1)
            ))
            tx.update(db.collection("players").document(uid), mapOf("teamId" to teamId))
            team
        }.addOnSuccessListener { team ->
            onSuccess(team as Team)
            // Manda messaggio di benvenuto in chat
            sendMessage(teamId, "Sistema", "🎉 $playerName si è unito alla squadra!", "🎉") {}
        }.addOnFailureListener { onError(it.message ?: "Errore iscrizione") }
    }

    fun leaveTeam(teamId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (uid.isEmpty()) { onError("Non autenticato"); return }
        val batch = db.batch()
        batch.update(db.collection("teams").document(teamId), mapOf(
            "members" to FieldValue.arrayRemove(uid),
            "memberCount" to FieldValue.increment(-1)
        ))
        batch.update(db.collection("players").document(uid), mapOf("teamId" to ""))
        batch.commit()
          .addOnSuccessListener { onSuccess() }
          .addOnFailureListener { onError(it.message ?: "Errore") }
    }

    fun searchTeams(query: String, onResult: (List<Team>) -> Unit) {
        db.collection("teams")
          .whereEqualTo("isOpen", true)
          .orderBy("totalXp", com.google.firebase.firestore.Query.Direction.DESCENDING)
          .limit(20)
          .get()
          .addOnSuccessListener { snap ->
              val teams = snap.documents.mapNotNull { it.data?.let { m -> Team.fromMap(m) } }
              val filtered = if (query.isEmpty()) teams
              else teams.filter { it.name.contains(query, true) || it.tag.contains(query, true) }
              onResult(filtered)
          }
          .addOnFailureListener { onResult(emptyList()) }
    }

    fun getMyTeam(onResult: (Team?) -> Unit) {
        if (uid.isEmpty()) { onResult(null); return }
        db.collection("players").document(uid).get()
          .addOnSuccessListener { playerDoc ->
              val teamId = playerDoc.getString("teamId") ?: ""
              if (teamId.isEmpty()) { onResult(null); return@addOnSuccessListener }
              db.collection("teams").document(teamId).get()
                .addOnSuccessListener { snap -> onResult(snap.data?.let { Team.fromMap(it) }) }
                .addOnFailureListener { onResult(null) }
          }
          .addOnFailureListener { onResult(null) }
    }

    // ─── Chat ────────────────────────────────────────────────────

    fun sendMessage(teamId: String, senderName: String, text: String, emoji: String,
                    onDone: () -> Unit) {
        if (text.isBlank()) return
        val msgId = "msg_${System.currentTimeMillis()}"
        val msg = ChatMessage(msgId = msgId, senderId = uid, senderName = senderName,
                              text = text.take(500), emoji = emoji, timestamp = System.currentTimeMillis())
        db.collection("teams").document(teamId).collection("messages")
          .document(msgId)
          .set(msg.toMap())
          .addOnSuccessListener { onDone() }
    }

    fun listenToChat(teamId: String, onMessages: (List<ChatMessage>) -> Unit): ListenerRegistration {
        chatListener?.remove()
        val reg = db.collection("teams").document(teamId).collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .limitToLast(50)
            .addSnapshotListener { snap, _ ->
                val msgs = snap?.documents?.mapNotNull { it.data?.let { m -> ChatMessage.fromMap(m) } } ?: emptyList()
                onMessages(msgs)
            }
        chatListener = reg
        return reg
    }

    fun stopChatListener() { chatListener?.remove(); chatListener = null }

    // ─── Contributo uova/XP alla squadra ────────────────────────

    fun contributeEggs(teamId: String, eggs: Int, xp: Long) {
        if (teamId.isEmpty()) return
        db.collection("teams").document(teamId).update(mapOf(
            "totalEggs" to FieldValue.increment(eggs.toLong()),
            "totalXp" to FieldValue.increment(xp),
            "weeklyEggs" to FieldValue.increment(eggs.toLong()),
            "weeklyXp" to FieldValue.increment(xp)
        ))
    }

    // ─── Classifica Squadre ───────────────────────────────────────

    fun getTeamLeaderboard(onResult: (List<Team>) -> Unit) {
        db.collection("teams")
          .orderBy("totalXp", com.google.firebase.firestore.Query.Direction.DESCENDING)
          .limit(50)
          .get()
          .addOnSuccessListener { snap ->
              onResult(snap.documents.mapNotNull { it.data?.let { m -> Team.fromMap(m) } })
          }
          .addOnFailureListener { onResult(emptyList()) }
    }
}
