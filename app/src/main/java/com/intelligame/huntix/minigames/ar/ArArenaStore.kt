package com.intelligame.huntix.minigames.ar

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Stanza AR dei minigiochi: associa un nome a una Cloud Anchor ARCore.
 *
 * Ogni "stanza" è il ricordo di una posizione fisica (es. il tavolo del
 * soggiorno) dove il giocatore ha piazzato le arene AR. Al riavvio dell'app il
 * gioco può riapparire in quella posizione senza dover scansionare di nuovo.
 *
 * Due livelli (entrambi indipendenti dalle stanze "indoor" di [RoomMapRepository]):
 * - locale: SharedPreferences (istantaneo, offline);
 * - cloud: Firestore collection "ar_arena_rooms", per utente (sopravvive a
 *   disinstallazione / cambio telefono).
 */
data class ArRoom(
    val roomId: String,
    val name: String,
    val cloudAnchorId: String,
    val createdAt: Long = System.currentTimeMillis()
)

object ArArenaStore {
    private const val PREFS = "ar_arena_rooms"
    private const val KEY_LAST_ROOM = "last_room"
    private const val KEY_ID_PREFIX = "room_"
    private const val KEY_NAME_PREFIX = "name_"
    private const val KEY_CREATED_PREFIX = "created_"
    private const val COLLECTION = "ar_arena_rooms"

    fun currentUid(): String = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ── livello locale ────────────────────────────────────────────

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun idKey(roomId: String) = KEY_ID_PREFIX + roomId
    private fun nameKey(roomId: String) = KEY_NAME_PREFIX + roomId
    private fun createdKey(roomId: String) = KEY_CREATED_PREFIX + roomId

    /** Stanza usata più di recente (null se non ci sono stanze). */
    fun lastRoom(c: Context): String? {
        val p = prefs(c)
        val id = p.getString(KEY_LAST_ROOM, null) ?: return null
        return if (p.contains(idKey(id))) id else null
    }

    /** Tutte le stanze salvate in locale, dalla più recente alla più vecchia. */
    fun loadRooms(c: Context): List<ArRoom> {
        val p = prefs(c)
        return p.all.mapNotNull { (k, v) ->
            if (!k.startsWith(KEY_ID_PREFIX)) return@mapNotNull null
            val id = k.removePrefix(KEY_ID_PREFIX)
            val cloudId = v as? String ?: return@mapNotNull null
            if (cloudId.isEmpty()) return@mapNotNull null
            ArRoom(
                roomId = id,
                name = p.getString(nameKey(id), id) ?: id,
                cloudAnchorId = cloudId,
                createdAt = p.getLong(createdKey(id), 0L)
            )
        }.sortedByDescending { it.createdAt }
    }

    fun loadRoom(c: Context, roomId: String): ArRoom? =
        loadRooms(c).firstOrNull { it.roomId == roomId }

    /** Salva (o aggiorna) una stanza; con [setLast] la marca come stanza attiva. */
    fun saveRoom(c: Context, room: ArRoom, setLast: Boolean = true) {
        val ed = prefs(c).edit()
            .putString(idKey(room.roomId), room.cloudAnchorId)
            .putString(nameKey(room.roomId), room.name)
            .putLong(createdKey(room.roomId), room.createdAt)
        if (setLast) ed.putString(KEY_LAST_ROOM, room.roomId)
        ed.apply()
    }

    fun deleteRoom(c: Context, roomId: String) {
        prefs(c).edit()
            .remove(idKey(roomId))
            .remove(nameKey(roomId))
            .remove(createdKey(roomId))
            .apply()
        if (prefs(c).getString(KEY_LAST_ROOM, null) == roomId) {
            prefs(c).edit()
                .putString(KEY_LAST_ROOM, loadRooms(c).firstOrNull()?.roomId ?: "")
                .apply()
        }
    }

    /** Nome libero del tipo "Stanza N" (evita duplicati dopo le cancellazioni). */
    fun nextRoomName(c: Context): String {
        val existing = loadRooms(c).map { it.name }.toMutableSet()
        var i = 1
        while ("Stanza $i" in existing) i++
        return "Stanza $i"
    }

    fun createRoom(name: String, cloudAnchorId: String): ArRoom =
        ArRoom(roomId = UUID.randomUUID().toString(), name = name, cloudAnchorId = cloudAnchorId)

    // ── livello cloud (Firestore, per utente) ─────────────────────

    private fun cloudDocId(uid: String, roomId: String) = "${uid}_$roomId"

    /** Carica le stanze salvate sul cloud per l'utente corrente. */
    suspend fun pullRooms(): List<ArRoom> = withContext(Dispatchers.IO) {
        val uid = currentUid()
        if (uid.isBlank()) return@withContext emptyList()
        runCatching {
            FirebaseFirestore.getInstance().collection(COLLECTION)
                .whereEqualTo("ownerId", uid)
                .get().await()
                .documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val roomId = d["roomId"] as? String ?: return@mapNotNull null
                    val cloudId = d["cloudAnchorId"] as? String ?: return@mapNotNull null
                    if (cloudId.isEmpty()) return@mapNotNull null
                    ArRoom(
                        roomId = roomId,
                        name = d["name"] as? String ?: roomId,
                        cloudAnchorId = cloudId,
                        createdAt = (d["createdAt"] as? Number)?.toLong() ?: 0L
                    )
                }
        }.getOrDefault(emptyList())
    }

    /** Salva (o aggiorna) una stanza sul cloud per l'utente corrente. */
    suspend fun pushRoom(room: ArRoom) = withContext(Dispatchers.IO) {
        val uid = currentUid()
        if (uid.isBlank()) return@withContext
        runCatching {
            FirebaseFirestore.getInstance().collection(COLLECTION)
                .document(cloudDocId(uid, room.roomId))
                .set(
                    mapOf(
                        "ownerId" to uid,
                        "roomId" to room.roomId,
                        "name" to room.name,
                        "cloudAnchorId" to room.cloudAnchorId,
                        "createdAt" to room.createdAt
                    ),
                    SetOptions.merge()
                ).await()
        }
    }

    /** Elimina una stanza dal cloud per l'utente corrente. */
    suspend fun deleteRoomCloud(roomId: String) = withContext(Dispatchers.IO) {
        val uid = currentUid()
        if (uid.isBlank()) return@withContext
        runCatching {
            FirebaseFirestore.getInstance().collection(COLLECTION)
                .document(cloudDocId(uid, roomId)).delete().await()
        }
    }
}
