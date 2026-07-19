package com.intelligame.huntix.social
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
object FriendsManager {
    private val db = FirebaseFirestore.getInstance()
    private val myUid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private const val MAX_RESULTS = 50
    data class SearchResult(val uid: String, val name: String, val level: Int, val country: String, val city: String, val gender: String)
    sealed class SearchResponse {
        data class Success(val players: List<SearchResult>) : SearchResponse()
        data class TooMany(val message: String) : SearchResponse()
        data class Error(val message: String) : SearchResponse()
    }
    fun searchPlayers(nameQuery: String = "", country: String = "", city: String = "", gender: String = "", onResult: (SearchResponse) -> Unit) {
        var query: Query = db.collection("players")
        if (country.isNotBlank()) query = query.whereEqualTo("country", country)
        if (gender.isNotBlank()) query = query.whereEqualTo("playerGender", gender)
        query.limit((MAX_RESULTS + 1).toLong()).get()
            .addOnSuccessListener { snap ->
                var results = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null; val uid = doc.id
                    if (uid == myUid) return@mapNotNull null
                    if ((d["isMinor"] as? Boolean) == true) return@mapNotNull null
                    SearchResult(uid=uid, name=d["name"] as? String ?: "?", level=((d["xp"] as? Long ?: 0)/150+1).toInt().coerceAtLeast(1), country=d["country"] as? String ?: "", city=d["city"] as? String ?: "", gender=d["playerGender"] as? String ?: "")
                }
                if (city.isNotBlank()) results = results.filter { it.city.contains(city, ignoreCase=true) }
                if (nameQuery.isNotBlank()) results = results.filter { it.name.contains(nameQuery, ignoreCase=true) }
                if (results.size > MAX_RESULTS) onResult(SearchResponse.TooMany("Troppi risultati (${results.size}+)! Aggiungi filtri per restringere la ricerca."))
                else onResult(SearchResponse.Success(results))
            }.addOnFailureListener { onResult(SearchResponse.Error(it.message ?: "Errore ricerca")) }
    }
    fun sendRequest(toUid: String, onDone: (Boolean) -> Unit) {
        val me = myUid; if (me.isBlank() || toUid == me) { onDone(false); return }
        val myName = com.intelligame.huntix.PlayerProfileManager.myProfile?.name ?: "Giocatore"
        db.collection("friend_requests").document(toUid).collection("incoming").document(me).set(mapOf("senderUid" to me, "senderName" to myName, "timestamp" to System.currentTimeMillis())).addOnSuccessListener { onDone(true) }.addOnFailureListener { onDone(false) }
    }
    fun getIncomingRequests(onResult: (List<Map<String, Any>>) -> Unit) {
        val me = myUid; if (me.isBlank()) { onResult(emptyList()); return }
        db.collection("friend_requests").document(me).collection("incoming").get().addOnSuccessListener { snap -> onResult(snap.documents.mapNotNull { it.data?.plus("docId" to it.id) }) }.addOnFailureListener { onResult(emptyList()) }
    }
    fun acceptRequest(senderUid: String, senderName: String, onDone: (Boolean) -> Unit) {
        val me = myUid; val myName = com.intelligame.huntix.PlayerProfileManager.myProfile?.name ?: "Giocatore"
        val batch = db.batch()
        batch.set(db.collection("friends").document(me).collection("list").document(senderUid), mapOf("name" to senderName, "addedAt" to System.currentTimeMillis()))
        batch.set(db.collection("friends").document(senderUid).collection("list").document(me), mapOf("name" to myName, "addedAt" to System.currentTimeMillis()))
        batch.delete(db.collection("friend_requests").document(me).collection("incoming").document(senderUid))
        batch.commit().addOnSuccessListener { onDone(true) }.addOnFailureListener { onDone(false) }
    }
    fun rejectRequest(senderUid: String, onDone: (Boolean) -> Unit) { db.collection("friend_requests").document(myUid).collection("incoming").document(senderUid).delete().addOnSuccessListener { onDone(true) }.addOnFailureListener { onDone(false) } }
    fun getFriends(onResult: (List<Map<String, Any>>) -> Unit) {
        val me = myUid; if (me.isBlank()) { onResult(emptyList()); return }
        db.collection("friends").document(me).collection("list").get().addOnSuccessListener { snap -> onResult(snap.documents.mapNotNull { it.data?.plus("uid" to it.id) }) }.addOnFailureListener { onResult(emptyList()) }
    }
}
