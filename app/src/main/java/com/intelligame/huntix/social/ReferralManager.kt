package com.intelligame.huntix.social

import android.content.Context
import com.intelligame.huntix.managers.SavedManager
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object ReferralManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    fun getMyCode(ctx: Context, onResult: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onResult(""); return }
        db.collection("players").document(uid).get().addOnSuccessListener { doc ->
            val existing = doc.getString("referralCode")
            if (!existing.isNullOrBlank()) { onResult(existing) } else {
                val code = uid.takeLast(6).uppercase()
                db.collection("players").document(uid).update("referralCode", code)
                db.collection("referral_codes").document(code).set(mapOf("ownerUid" to uid, "ownerName" to (doc.getString("name") ?: "Giocatore")))
                onResult(code)
            }
        }.addOnFailureListener { onResult("") }
    }

    fun applyCode(ctx: Context, code: String, onResult: (Boolean, String) -> Unit) {
        val myUid = auth.currentUser?.uid ?: run { onResult(false, "Non autenticato"); return }
        val c = code.trim().uppercase()
        db.collection("referral_codes").document(c).get().addOnSuccessListener { doc ->
            if (!doc.exists()) { onResult(false, "Codice non valido"); return@addOnSuccessListener }
            val ownerUid = doc.getString("ownerUid") ?: ""
            if (ownerUid == myUid) { onResult(false, "Non puoi usare il tuo codice!"); return@addOnSuccessListener }
            db.collection("players").document(myUid).get().addOnSuccessListener { myDoc ->
                if (!myDoc.getString("referredBy").isNullOrBlank()) { onResult(false, "Hai gia usato un codice"); return@addOnSuccessListener }
                val batch = db.batch()
                batch.update(db.collection("players").document(myUid), mapOf("referredBy" to c))
                batch.update(db.collection("players").document(ownerUid), "referralCount", FieldValue.increment(1))
                batch.commit().addOnSuccessListener {
                    SavedManager.addMvc(ctx, 500.0)
                    onResult(true, "+500 MVC!")
                }.addOnFailureListener { onResult(false, "Errore") }
            }
        }.addOnFailureListener { onResult(false, "Errore di rete") }
    }

    fun shareCode(ctx: Context, code: String) {
        val text = "Gioca a Huntix! Usa il mio codice $code per 500 MVC gratis!\nhttps://play.google.com/store/apps/details?id=com.intelligame.huntix"
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Invita un amico"))
    }
}
