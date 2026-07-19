package com.intelligame.huntix.gamification

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

/**
 * QuestManager — Missioni giornaliere e settimanali stile Brawl Stars.
 *
 * Firestore: players/{uid}/quests/{questId}
 *   - id, type (daily/weekly), title, description, target, progress, completed, claimedAt, expiresAt, rewardXp, rewardGems
 */
object QuestManager {

    private val db get() = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()
    private val uid get() = auth.currentUser?.uid ?: ""

    // ─── Definizioni Quest ──────────────────────────────────────

    data class Quest(
        val id: String = "",
        val type: String = "daily",           // "daily" | "weekly"
        val title: String = "",
        val description: String = "",
        val emoji: String = "",
        val target: Int = 1,
        var progress: Int = 0,
        var completed: Boolean = false,
        var claimed: Boolean = false,
        val rewardXp: Long = 0L,
        val rewardGems: Int = 0,
        val expiresAt: Long = 0L,
        val createdAt: Long = System.currentTimeMillis(),
        val questKey: String = ""              // chiave per tracking (es. "eggs_found")
    ) {
        val progressPercent: Int get() = ((progress.toFloat() / target) * 100).toInt().coerceIn(0, 100)
        val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
        val canClaim: Boolean get() = completed && !claimed && !isExpired

        fun toMap(): Map<String, Any> = mapOf(
            "id" to id, "type" to type, "title" to title, "description" to description,
            "emoji" to emoji, "target" to target, "progress" to progress,
            "completed" to completed, "claimed" to claimed, "rewardXp" to rewardXp,
            "rewardGems" to rewardGems, "expiresAt" to expiresAt, "createdAt" to createdAt,
            "questKey" to questKey
        )

        companion object {
            fun fromMap(map: Map<String, Any?>): Quest = Quest(
                id = map["id"] as? String ?: "",
                type = map["type"] as? String ?: "daily",
                title = map["title"] as? String ?: "",
                description = map["description"] as? String ?: "",
                emoji = map["emoji"] as? String ?: "🎯",
                target = (map["target"] as? Long)?.toInt() ?: 1,
                progress = (map["progress"] as? Long)?.toInt() ?: 0,
                completed = map["completed"] as? Boolean ?: false,
                claimed = map["claimed"] as? Boolean ?: false,
                rewardXp = map["rewardXp"] as? Long ?: 0L,
                rewardGems = (map["rewardGems"] as? Long)?.toInt() ?: 0,
                expiresAt = map["expiresAt"] as? Long ?: 0L,
                createdAt = map["createdAt"] as? Long ?: 0L,
                questKey = map["questKey"] as? String ?: ""
            )
        }
    }

    // ─── Template Quest Giornaliere ─────────────────────────────

    private val DAILY_TEMPLATES = listOf(
        Triple("eggs_found_3",    "Cacciatore Mattutino",   Triple("🥚", "Trova 3 uova oggi", 3)),
        Triple("rare_found_1",    "Cercatore di Rarità",    Triple("💜", "Trova 1 uovo raro o superiore", 1)),
        Triple("outdoor_session", "Esploratore Outdoor",    Triple("🌍", "Gioca una sessione outdoor", 1)),
        Triple("walk_500m",       "Passeggiata del Mattino",Triple("🚶", "Cammina 500m", 1)),
        Triple("eggs_found_5",    "Caccia Intensa",         Triple("🔥", "Trova 5 uova in un giorno", 5)),
        Triple("multiplayer_win", "Giocatore Sociale",      Triple("👥", "Vinci una partita multiplayer", 1)),
        Triple("gym_visit",       "Allenatore",             Triple("🏋️", "Visita una palestra", 1)),
        Triple("login_streak",    "Presenza Costante",      Triple("📅", "Accedi all'app oggi", 1))
    )

    private val WEEKLY_TEMPLATES = listOf(
        Triple("eggs_found_25",   "Cacciatore della Settimana", Triple("🏆", "Trova 25 uova questa settimana", 25)),
        Triple("epic_found_3",    "Raro tra i Rari",            Triple("🔥", "Trova 3 uova epiche o leggendarie", 3)),
        Triple("walk_5km",        "Maratoneta",                 Triple("🏃", "Cammina 5 km questa settimana", 1)),
        Triple("team_contrib",    "Spirito di Squadra",         Triple("🤝", "Contribuisci 10 uova alla squadra", 10)),
        Triple("legendary_find",  "Leggenda Vivente",           Triple("⭐", "Trova 1 uovo leggendario", 1)),
        Triple("events_complete", "Partecipante",               Triple("🎉", "Completa 3 eventi live", 3))
    )

    // ─── Genera/Sincronizza Quest ────────────────────────────────

    fun ensureDailyQuests(onDone: (List<Quest>) -> Unit) {
        if (uid.isEmpty()) { onDone(emptyList()); return }
        val col = db.collection("players").document(uid).collection("quests")
        val todayStart = getDayStart()
        val todayEnd   = todayStart + 86_400_000L

        col.get()
           .addOnSuccessListener { snap ->
               val now = System.currentTimeMillis()
               val existing = snap.documents
                   .mapNotNull { it.data?.let { m -> Quest.fromMap(m) } }
                   .filter { it.type == "daily" && it.expiresAt > now }
               if (existing.size >= 3) { onDone(existing); return@addOnSuccessListener }

               // Genera 3 quest giornaliere casuali
               val picked = DAILY_TEMPLATES.shuffled().take(3)
               val newQuests = picked.map { (key, name, data) ->
                   val (emoji, desc, target) = data
                   Quest(
                       id = "daily_${key}_${todayStart}",
                       type = "daily",
                       title = name,
                       description = desc,
                       emoji = emoji,
                       target = target,
                       questKey = key,
                       rewardXp = (target * 80L).coerceAtLeast(100L),
                       rewardGems = if (key.contains("rare") || key.contains("epic") || key.contains("legendary")) 5 else 2,
                       expiresAt = todayEnd
                   )
               }
               val batch = db.batch()
               newQuests.forEach { q -> batch.set(col.document(q.id), q.toMap()) }
               batch.commit().addOnSuccessListener { onDone(newQuests) }
           }
    }

    fun ensureWeeklyQuests(onDone: (List<Quest>) -> Unit) {
        if (uid.isEmpty()) { onDone(emptyList()); return }
        val col = db.collection("players").document(uid).collection("quests")
        val weekStart = getWeekStart()
        val weekEnd   = weekStart + 7 * 86_400_000L

        col.get()
           .addOnSuccessListener { snap ->
               val now = System.currentTimeMillis()
               val existing = snap.documents
                   .mapNotNull { it.data?.let { m -> Quest.fromMap(m) } }
                   .filter { it.type == "weekly" && it.expiresAt > now }
               if (existing.size >= 2) { onDone(existing); return@addOnSuccessListener }

               val picked = WEEKLY_TEMPLATES.shuffled().take(2)
               val newQuests = picked.map { (key, name, data) ->
                   val (emoji, desc, target) = data
                   Quest(
                       id = "weekly_${key}_${weekStart}",
                       type = "weekly",
                       title = name,
                       description = desc,
                       emoji = emoji,
                       target = target,
                       questKey = key,
                       rewardXp = (target * 200L).coerceAtLeast(500L),
                       rewardGems = if (key.contains("legendary")) 20 else 10,
                       expiresAt = weekEnd
                   )
               }
               val batch = db.batch()
               newQuests.forEach { q -> batch.set(col.document(q.id), q.toMap()) }
               batch.commit().addOnSuccessListener { onDone(newQuests) }
           }
    }

    fun loadAllActiveQuests(onResult: (List<Quest>) -> Unit) {
        if (uid.isEmpty()) { onResult(emptyList()); return }
        db.collection("players").document(uid).collection("quests")
          .get()
          .addOnSuccessListener { snap ->
              val now = System.currentTimeMillis()
              onResult(snap.documents.mapNotNull { it.data?.let { m -> Quest.fromMap(m) } }
                           .filter { it.expiresAt > now }
                           .sortedWith(compareBy({ it.claimed }, { -it.progressPercent })))
          }
          .addOnFailureListener { onResult(emptyList()) }
    }

    // ─── Aggiorna Progresso ──────────────────────────────────────

    fun trackProgress(questKey: String, amount: Int = 1, onLevelUp: ((Quest) -> Unit)? = null) {
        if (uid.isEmpty()) return
        val now = System.currentTimeMillis()
        db.collection("players").document(uid).collection("quests")
          .get()
          .addOnSuccessListener { snap ->
              snap.documents.forEach { doc ->
                  val q = doc.data?.let { Quest.fromMap(it) } ?: return@forEach
                  // Filtro in-memory per evitare composite index Firestore
                  if (q.questKey != questKey) return@forEach
                  if (q.expiresAt <= now) return@forEach
                  if (q.completed) return@forEach
                  val newProgress = (q.progress + amount).coerceAtMost(q.target)
                  val nowComplete = newProgress >= q.target
                  doc.reference.update(mapOf(
                      "progress" to newProgress,
                      "completed" to nowComplete
                  ))
                  if (nowComplete && !q.completed) {
                      onLevelUp?.invoke(q.copy(progress = newProgress, completed = true))
                  }
              }
          }
    }

    fun claimReward(questId: String, onSuccess: (Quest) -> Unit) {
        if (uid.isEmpty()) return
        val ref = db.collection("players").document(uid).collection("quests").document(questId)
        ref.get().addOnSuccessListener { doc ->
            val q = doc.data?.let { Quest.fromMap(it) } ?: return@addOnSuccessListener
            if (!q.canClaim) return@addOnSuccessListener
            ref.update("claimed", true).addOnSuccessListener {
                // Assegna ricompense al profilo giocatore
                val playerRef = db.collection("players").document(uid)
                db.runTransaction { tx ->
                    val snap = tx.get(playerRef)
                    val curXp = snap.getLong("xp") ?: 0L
                    val curGems = (snap.getLong("gems") ?: 0L).toInt()
                    tx.update(playerRef, mapOf(
                        "xp" to curXp + q.rewardXp,
                        "gems" to curGems + q.rewardGems
                    ))
                }.addOnSuccessListener { onSuccess(q) }
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────

    private fun getDayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getWeekStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
