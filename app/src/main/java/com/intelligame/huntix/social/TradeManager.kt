package com.intelligame.huntix.social

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object TradeManager {
    private val db = FirebaseFirestore.getInstance()
    private val myUid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private const val TRADE_COST_MVC = 500
    private const val EXPIRE_MS = 24 * 60 * 60 * 1000L

    data class TradeOffer(
        val id: String = "", val fromUid: String = "", val fromName: String = "",
        val toUid: String = "", val toName: String = "",
        val offeredCreatureId: String = "", val offeredCreatureName: String = "",
        val offeredCreatureEmoji: String = "", val mvcCost: Int = TRADE_COST_MVC,
        val status: String = "pending", val createdAt: Long = 0, val expiresAt: Long = 0
    )

    fun createOffer(toUid: String, toName: String, creatureId: String,
                    creatureName: String, creatureEmoji: String, onDone: (Boolean, String) -> Unit) {
        // ✅ FIX v7.2: Blocca scambi per minori (Play Store compliance)
        if (com.intelligame.huntix.AgeGateManager.currentUserIsMinor()) {
            onDone(false, "Scambi non disponibili per under 18")
            return
        }
        val me = myUid; if (me.isBlank()) { onDone(false, "Non autenticato"); return }
        val myName = com.intelligame.huntix.PlayerProfileManager.myProfile?.name ?: "Giocatore"
        val now = System.currentTimeMillis()
        db.collection("trade_offers").add(hashMapOf(
            "fromUid" to me, "fromName" to myName, "toUid" to toUid, "toName" to toName,
            "offeredCreatureId" to creatureId, "offeredCreatureName" to creatureName,
            "offeredCreatureEmoji" to creatureEmoji, "mvcCost" to TRADE_COST_MVC,
            "status" to "pending", "createdAt" to now, "expiresAt" to now + EXPIRE_MS
        )).addOnSuccessListener { onDone(true, it.id) }.addOnFailureListener { onDone(false, it.message ?: "Errore") }
    }

    fun acceptOffer(offerId: String, onDone: (Boolean, String) -> Unit) {
        val ref = db.collection("trade_offers").document(offerId)
        db.runTransaction { tx ->
            val snap = tx.get(ref); val offer = snap.data ?: throw Exception("Non trovata")
            if (offer["status"] != "pending") throw Exception("Gia gestita")
            if (System.currentTimeMillis() > (offer["expiresAt"] as? Long ?: 0)) throw Exception("Scaduta")
            val fromUid = offer["fromUid"] as String; val toUid = offer["toUid"] as String
            val cost = (offer["mvcCost"] as? Long ?: 500).toInt()
            tx.update(ref, "status", "accepted")
            tx.update(db.collection("players").document(fromUid), "mvc", FieldValue.increment(cost.toLong()))
            tx.update(db.collection("players").document(toUid), "mvc", FieldValue.increment(-cost.toLong()))
        }.addOnSuccessListener { onDone(true, "Scambio completato!") }
         .addOnFailureListener { onDone(false, it.message ?: "Errore") }
    }

    fun rejectOffer(offerId: String, onDone: (Boolean) -> Unit) {
        db.collection("trade_offers").document(offerId).update("status", "rejected")
            .addOnSuccessListener { onDone(true) }.addOnFailureListener { onDone(false) }
    }

    fun getIncomingOffers(onResult: (List<TradeOffer>) -> Unit) {
        val me = myUid; if (me.isBlank()) { onResult(emptyList()); return }
        db.collection("trade_offers").whereEqualTo("toUid", me).whereEqualTo("status", "pending").get()
            .addOnSuccessListener { snap -> onResult(snap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                TradeOffer(id=doc.id, fromUid=d["fromUid"] as? String ?: "", fromName=d["fromName"] as? String ?: "",
                    toUid=d["toUid"] as? String ?: "", offeredCreatureId=d["offeredCreatureId"] as? String ?: "",
                    offeredCreatureName=d["offeredCreatureName"] as? String ?: "",
                    offeredCreatureEmoji=d["offeredCreatureEmoji"] as? String ?: "",
                    mvcCost=(d["mvcCost"] as? Long ?: 500).toInt(), status="pending",
                    createdAt=d["createdAt"] as? Long ?: 0, expiresAt=d["expiresAt"] as? Long ?: 0)
            })}.addOnFailureListener { onResult(emptyList()) }
    }

    fun getMyOffers(onResult: (List<TradeOffer>) -> Unit) {
        val me = myUid; if (me.isBlank()) { onResult(emptyList()); return }
        db.collection("trade_offers").whereEqualTo("fromUid", me).get()
            .addOnSuccessListener { snap -> onResult(snap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                TradeOffer(id=doc.id, fromUid=me, fromName=d["fromName"] as? String ?: "",
                    toUid=d["toUid"] as? String ?: "", toName=d["toName"] as? String ?: "",
                    offeredCreatureId=d["offeredCreatureId"] as? String ?: "",
                    offeredCreatureName=d["offeredCreatureName"] as? String ?: "",
                    status=d["status"] as? String ?: "pending", createdAt=d["createdAt"] as? Long ?: 0)
            })}.addOnFailureListener { onResult(emptyList()) }
    }
}
