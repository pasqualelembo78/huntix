package com.intelligame.huntix.gamification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * GamifiedLeaderboardManager — classifiche multiple stile Brawl Stars.
 *
 * Tipi: Globale XP, Uova Totali, Settimanale, Squadre, Amici
 */
object GamifiedLeaderboardManager {

    private val db get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    enum class LeaderboardType(val label: String, val emoji: String) {
        GLOBAL_XP("Globale XP", "⚡"),
        GLOBAL_EGGS("Uova Totali", "🥚"),
        GLOBAL_POWER("Potere", "💪"),
        WEEKLY_XP("Settimana", "📅"),
        TEAMS("Squadre", "👥"),
        LEGENDARY("Leggendari", "⭐")
    }

    data class LeaderboardEntry(
        val rank: Int,
        val playerId: String,
        val name: String,
        val value: Long,
        val secondaryValue: Long = 0L,
        val level: Int = 1,
        val titleId: String = "title_default",
        val avatarEmoji: String = "🐣",
        val isCurrentUser: Boolean = false,
        val teamName: String = ""
    )

    data class TeamLeaderboardEntry(
        val rank: Int,
        val teamId: String,
        val teamName: String,
        val tag: String,
        val totalXp: Long,
        val totalEggs: Long,
        val memberCount: Int,
        val leaderName: String
    )

    fun loadLeaderboard(
        type: LeaderboardType,
        limit: Int = 50,
        onResult: (List<LeaderboardEntry>, myEntry: LeaderboardEntry?) -> Unit
    ) {
        when (type) {
            LeaderboardType.TEAMS      -> { loadTeamLeaderboard(limit) { _teams -> onResult(emptyList(), null) }; return }
            LeaderboardType.GLOBAL_XP  -> loadPlayerLeaderboard("xp", limit, onResult)
            LeaderboardType.GLOBAL_EGGS -> loadPlayerLeaderboard("eggsFound", limit, onResult)
            LeaderboardType.GLOBAL_POWER -> loadPlayerLeaderboard("power", limit, onResult)
            LeaderboardType.WEEKLY_XP  -> loadPlayerLeaderboard("weeklyXp", limit, onResult)
            LeaderboardType.LEGENDARY  -> loadPlayerLeaderboard("legendaryFound", limit, onResult)
        }
    }

    private fun loadPlayerLeaderboard(
        field: String,
        limit: Int,
        onResult: (List<LeaderboardEntry>, myEntry: LeaderboardEntry?) -> Unit
    ) {
        db.collection("players")
          .whereGreaterThan(field, 0)
          .orderBy(field, Query.Direction.DESCENDING)
          .limit(limit.toLong())
          .get()
          .addOnSuccessListener { snap ->
              val entries = snap.documents.mapIndexedNotNull { i, doc ->
                  val data = doc.data ?: return@mapIndexedNotNull null
                  val playerId = data["playerId"] as? String ?: doc.id
                  val value = when (field) {
                      "xp" -> data["xp"] as? Long ?: 0L
                      "eggsFound" -> (data["eggsFound"] as? Long) ?: 0L
                      "power" -> data["power"] as? Long ?: 0L
                      "weeklyXp" -> data["weeklyXp"] as? Long ?: 0L
                      "legendaryFound" -> (data["legendaryFound"] as? Long) ?: 0L
                      else -> 0L
                  }
                  LeaderboardEntry(
                      rank = i + 1,
                      playerId = playerId,
                      name = data["name"] as? String ?: "?",
                      value = value,
                      secondaryValue = data["eggsFound"] as? Long ?: 0L,
                      level = (data["level"] as? Long)?.toInt() ?: 1,
                      isCurrentUser = playerId == uid || doc.id == uid
                  )
              }
              val myEntry = entries.find { it.isCurrentUser }
              onResult(entries, myEntry)
          }
          .addOnFailureListener { onResult(emptyList(), null) }
    }

    fun loadTeamLeaderboard(limit: Int, onResult: (List<TeamLeaderboardEntry>) -> Unit) {
        db.collection("teams")
          .orderBy("totalXp", Query.Direction.DESCENDING)
          .limit(limit.toLong())
          .get()
          .addOnSuccessListener { snap ->
              val entries = snap.documents.mapIndexedNotNull { i, doc ->
                  val data = doc.data ?: return@mapIndexedNotNull null
                  TeamLeaderboardEntry(
                      rank = i + 1,
                      teamId = data["teamId"] as? String ?: doc.id,
                      teamName = data["name"] as? String ?: "?",
                      tag = data["tag"] as? String ?: "",
                      totalXp = data["totalXp"] as? Long ?: 0L,
                      totalEggs = data["totalEggs"] as? Long ?: 0L,
                      memberCount = (data["memberCount"] as? Long)?.toInt() ?: 0,
                      leaderName = data["leaderName"] as? String ?: ""
                  )
              }
              onResult(entries)
          }
          .addOnFailureListener { onResult(emptyList()) }
    }

    fun formatValue(type: LeaderboardType, value: Long): String = when (type) {
        LeaderboardType.GLOBAL_XP, LeaderboardType.WEEKLY_XP -> "⚡ ${formatNumber(value)} XP"
        LeaderboardType.GLOBAL_EGGS -> "🥚 ${formatNumber(value)}"
        LeaderboardType.GLOBAL_POWER -> "💪 ${formatNumber(value)}"
        LeaderboardType.LEGENDARY -> "⭐ ${formatNumber(value)}"
        LeaderboardType.TEAMS -> "👥 ${formatNumber(value)} XP"
    }

    private fun formatNumber(n: Long): String = when {
        n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
        n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}k"
        else -> n.toString()
    }
}
