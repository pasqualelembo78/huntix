package com.intelligame.huntix.social

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * ChatModerationManager — Gestione blocco utenti, segnalazioni, moderazione chat.
 *
 * Struttura Firestore:
 *   blocked_users/{myUid}/list/{blockedUid}     → { blockedAt: Timestamp, reason: String }
 *   reports/{autoId}                             → { reporterUid, reportedUid, type, reason, content, timestamp, status }
 *   user_settings/{uid}                          → { chatPrivacy: "everyone"|"friends"|"nobody", ... }
 */
object ChatModerationManager {

    private val db = FirebaseFirestore.getInstance()
    private fun myUid() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ─── BLOCCO UTENTI ──────────────────────────────────────────

    fun blockUser(targetUid: String, reason: String = "", onDone: (Boolean) -> Unit) {
        val uid = myUid()
        if (uid.isEmpty() || targetUid == uid) { onDone(false); return }

        val data = hashMapOf(
            "blockedAt" to FieldValue.serverTimestamp(),
            "reason" to reason
        )
        db.collection("blocked_users").document(uid)
            .collection("list").document(targetUid)
            .set(data)
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    fun unblockUser(targetUid: String, onDone: (Boolean) -> Unit) {
        val uid = myUid()
        db.collection("blocked_users").document(uid)
            .collection("list").document(targetUid)
            .delete()
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    fun isBlocked(targetUid: String, onResult: (Boolean) -> Unit) {
        val uid = myUid()
        db.collection("blocked_users").document(uid)
            .collection("list").document(targetUid)
            .get()
            .addOnSuccessListener { onResult(it.exists()) }
            .addOnFailureListener { onResult(false) }
    }

    fun getBlockedList(onResult: (List<String>) -> Unit) {
        val uid = myUid()
        db.collection("blocked_users").document(uid)
            .collection("list").get()
            .addOnSuccessListener { snap ->
                onResult(snap.documents.map { it.id })
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // ─── SEGNALAZIONI ───────────────────────────────────────────

    enum class ReportType { SPAM, HARASSMENT, INAPPROPRIATE, CHEATING, OTHER }

    fun reportUser(
        targetUid: String,
        type: ReportType,
        reason: String,
        contentSnapshot: String = "",
        onDone: (Boolean) -> Unit
    ) {
        val uid = myUid()
        if (uid.isEmpty()) { onDone(false); return }

        val report = hashMapOf(
            "reporterUid" to uid,
            "reportedUid" to targetUid,
            "type" to type.name,
            "reason" to reason,
            "contentSnapshot" to contentSnapshot,
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "pending"  // pending, reviewed, resolved, dismissed
        )
        db.collection("reports").add(report)
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    fun reportContent(
        contentId: String,
        contentType: String, // "chat_message", "team_name", "riddle"
        targetUid: String,
        reason: String,
        contentSnapshot: String = "",
        onDone: (Boolean) -> Unit
    ) {
        val uid = myUid()
        val report = hashMapOf(
            "reporterUid" to uid,
            "reportedUid" to targetUid,
            "contentId" to contentId,
            "contentType" to contentType,
            "reason" to reason,
            "contentSnapshot" to contentSnapshot,
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "pending"
        )
        db.collection("reports").add(report)
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    // ─── PRIVACY CHAT ───────────────────────────────────────────

    /** ChatPrivacy: chi può inviarmi messaggi */
    enum class ChatPrivacy { EVERYONE, FRIENDS_ONLY, NOBODY }

    fun setChatPrivacy(privacy: ChatPrivacy, onDone: (Boolean) -> Unit) {
        val uid = myUid()
        db.collection("user_settings").document(uid)
            .set(hashMapOf("chatPrivacy" to privacy.name), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    fun getChatPrivacy(onResult: (ChatPrivacy) -> Unit) {
        val uid = myUid()
        db.collection("user_settings").document(uid).get()
            .addOnSuccessListener { doc ->
                val value = doc.getString("chatPrivacy") ?: "EVERYONE"
                onResult(try { ChatPrivacy.valueOf(value) } catch (e: Exception) { ChatPrivacy.EVERYONE })
            }
            .addOnFailureListener { onResult(ChatPrivacy.EVERYONE) }
    }

    /** Verifica se posso chattare con un utente (privacy + blocco) */
    fun canChatWith(targetUid: String, onResult: (Boolean) -> Unit) {
        // 1. Controlla se io ho bloccato lui
        isBlocked(targetUid) { iBlockedHim ->
            if (iBlockedHim) { onResult(false); return@isBlocked }
            // 2. Controlla se lui ha bloccato me
            val myUid = myUid()
            db.collection("blocked_users").document(targetUid)
                .collection("list").document(myUid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) { onResult(false); return@addOnSuccessListener }
                    // 3. Controlla la sua privacy
                    db.collection("user_settings").document(targetUid).get()
                        .addOnSuccessListener { settingsDoc ->
                            val privacy = settingsDoc.getString("chatPrivacy") ?: "EVERYONE"
                            when (privacy) {
                                "NOBODY" -> onResult(false)
                                "FRIENDS_ONLY" -> {
                                    // Controlla se siamo amici
                                    db.collection("friends").document(targetUid)
                                        .collection("list").document(myUid).get()
                                        .addOnSuccessListener { friendDoc -> onResult(friendDoc.exists()) }
                                        .addOnFailureListener { onResult(false) }
                                }
                                else -> onResult(true)
                            }
                        }
                        .addOnFailureListener { onResult(true) }
                }
                .addOnFailureListener { onResult(true) }
        }
    }

    // ─── MODERAZIONE CONTENUTI (filtro parole) ──────────────────

    private val bannedWords = listOf(
        "cazzo", "merda", "vaffanculo", "stronzo", "puttana", "minchia",
        "fuck", "shit", "bitch", "asshole", "dick", "pussy",
        "nazi", "hitler", "nigger", "faggot"
    )

    fun filterMessage(text: String): String {
        var filtered = text
        for (word in bannedWords) {
            val regex = Regex("(?i)\\b${Regex.escape(word)}\\b")
            filtered = regex.replace(filtered) { "***" }
        }
        return filtered
    }

    fun containsBannedContent(text: String): Boolean {
        val lower = text.lowercase()
        return bannedWords.any { lower.contains(it) }
    }

    // ─── ELIMINAZIONE DATI ──────────────────────────────────────

    /** Elimina solo i dati dell'utente SENZA eliminare l'account Firebase Auth */
    fun deleteUserData(onDone: (Boolean) -> Unit) {
        val uid = myUid()
        if (uid.isEmpty()) { onDone(false); return }

        var completed = 0
        var failed = false
        val total = 4

        fun checkDone() {
            completed++
            if (completed >= total) onDone(!failed)
        }

        // 1. Profilo Firestore
        db.collection("players").document(uid).delete()
            .addOnSuccessListener { checkDone() }.addOnFailureListener { failed = true; checkDone() }
        // 2. Statistiche minigiochi
        db.collection("mini_game_stats").document(uid).delete()
            .addOnSuccessListener { checkDone() }.addOnFailureListener { failed = true; checkDone() }
        // 3. Blocked list
        db.collection("blocked_users").document(uid).delete()
            .addOnSuccessListener { checkDone() }.addOnFailureListener { failed = true; checkDone() }
        // 4. User settings
        db.collection("user_settings").document(uid).delete()
            .addOnSuccessListener { checkDone() }.addOnFailureListener { failed = true; checkDone() }
    }

    /** Elimina account completo (dati + Firebase Auth) */
    fun deleteAccount(onDone: (Boolean) -> Unit) {
        deleteUserData { dataDeleted ->
            val user = FirebaseAuth.getInstance().currentUser
            user?.delete()?.addOnSuccessListener { onDone(true) }
                ?.addOnFailureListener { onDone(dataDeleted) }
                ?: onDone(dataDeleted)
        }
    }
}
