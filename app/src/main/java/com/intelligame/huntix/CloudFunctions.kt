package com.intelligame.huntix

import android.content.Context
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult

object CloudFunctions {

    private const val TAG = "CloudFunctions"
    private val functions = FirebaseFunctions.getInstance()

    // ═══════════════════════════════════════════════════
    // Purchase Verification (server-side)
    // ═══════════════════════════════════════════════════

    fun verifyPurchase(
        purchaseToken: String,
        productId: String,
        signature: String? = null,
        packageName: String? = null,
        onResult: (Boolean, String?, String?) -> Unit
    ) {
        val data = mutableMapOf<String, Any>(
            "purchaseToken" to purchaseToken,
            "productId" to productId,
        )
        signature?.let { data["signature"] = it }
        packageName?.let { data["packageName"] = it }

        functions.getHttpsCallable("verifyPurchase")
            .call(data)
            .addOnSuccessListener { result ->
                val response = result.getData() as? Map<String, Any>
                val verified = response?.get("verified") as? Boolean ?: false
                val status = response?.get("status") as? String ?: ""
                Log.d(TAG, "Purchase verification: verified=$verified, status=$status")
                onResult(verified, status, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Purchase verification failed: ${e.message}")
                onResult(false, null, e.message)
            }
    }

    // ═══════════════════════════════════════════════════
    // Server-Side Score Sync (anti-cheat)
    // ═══════════════════════════════════════════════════

    fun syncServerScore(
        roomCode: String,
        eggsFound: Int,
        totalMs: Long,
        onResult: (Boolean, String?) -> Unit
    ) {
        val data = mapOf(
            "roomCode" to roomCode,
            "eggsFound" to eggsFound,
            "totalMs" to totalMs,
        )

        functions.getHttpsCallable("syncServerScore")
            .call(data)
            .addOnSuccessListener {
                Log.d(TAG, "Score synced server-side")
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Score sync failed: ${e.message}")
                onResult(false, e.message)
            }
    }

    // ═══════════════════════════════════════════════════
    // Redeem Referral (server-side)
    // ═══════════════════════════════════════════════════

    fun redeemReferral(
        code: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val data = mapOf("code" to code)

        functions.getHttpsCallable("redeemReferral")
            .call(data)
            .addOnSuccessListener { result ->
                val response = result.getData() as? Map<String, Any>
                val success = response?.get("success") as? Boolean ?: false
                val message = response?.get("message") as? String
                Log.d(TAG, "Referral redeemed: success=$success")
                onResult(success, message)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Referral redemption failed: ${e.message}")
                onResult(false, e.message)
            }
    }

    // ═══════════════════════════════════════════════════
    // Create Match Room (server-side matchmaking)
    // ═══════════════════════════════════════════════════

    fun createMatchRoom(
        mode: String,
        onResult: (Boolean, String?, String?) -> Unit
    ) {
        val data = mapOf("mode" to mode)

        functions.getHttpsCallable("createMatchRoom")
            .call(data)
            .addOnSuccessListener { result ->
                val response = result.getData() as? Map<String, Any>
                val success = response?.get("success") as? Boolean ?: false
                val code = response?.get("code") as? String ?: ""
                Log.d(TAG, "Room created: code=$code")
                onResult(success, code, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Room creation failed: ${e.message}")
                onResult(false, null, e.message)
            }
    }

    // ═══════════════════════════════════════════════════
    // Server Time Sync (anti-cheat clock validation)
    // ═══════════════════════════════════════════════════

    fun getServerTime(
        onResult: (Long?) -> Unit
    ) {
        functions.getHttpsCallable("getServerTime")
            .call()
            .addOnSuccessListener { result ->
                val data = result.getData() as? Map<String, Any>
                val serverTime = data?.get("serverTime") as? Long
                Log.d(TAG, "Server time received: $serverTime")
                onResult(serverTime)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Server time fetch failed: ${e.message}")
                onResult(null)
            }
    }

    // ═══════════════════════════════════════════════════
    // Structured Analytics Event
    // ═══════════════════════════════════════════════════

    fun logGameEvent(
        event: String,
        data: Map<String, Any> = emptyMap(),
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val payload = mapOf("event" to event, "data" to data)

        functions.getHttpsCallable("logEvent")
            .call(payload)
            .addOnSuccessListener {
                Log.d(TAG, "Analytics event logged: $event")
                onResult?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Analytics event failed: ${e.message}")
                onResult?.invoke(false)
            }
    }
}