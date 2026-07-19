package com.intelligame.huntix

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * GameProgressSync — Sincronizza i dati di progresso su Firestore.
 *
 * ✅ v7.2.1: Sync MVC, uova, cibo, strumenti su Firestore.
 * ✅ v7.2.1b: Fix race condition — flag isRestoring impedisce
 *    che addMvc() sovrascriva i dati cloud con zeri durante il restore.
 *    Debounce di 10s per evitare troppe scritture Firestore.
 *
 * Firestore: game_progress/{uid}
 */
object GameProgressSync {

    private const val TAG = "GameProgressSync"
    private const val COL = "game_progress"
    private val db get() = FirebaseFirestore.getInstance()

    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    // ── Protezione race condition ────────────────────────────────
    @Volatile
    private var isRestoring = false

    // ── Debounce: salva max 1 volta ogni 10 secondi ─────────────
    private val handler = Handler(Looper.getMainLooper())
    private var pendingSave: Runnable? = null
    private var lastSaveMs = 0L
    private const val DEBOUNCE_MS = 10_000L  // 10 secondi

    // ═══════════════════════════════════════════════════════════
    // SALVATAGGIO → Firestore (con debounce)
    // ═══════════════════════════════════════════════════════════

    /**
     * Pianifica un salvataggio su Firestore con debounce.
     * Se chiamato più volte in 10s, esegue solo l'ultimo.
     * NON salva se un restore è in corso (previene sovrascrittura).
     */
    fun saveProgress(ctx: Context, onDone: ((Boolean) -> Unit)? = null) {
        // ✅ BLOCK: non sovrascrivere il cloud durante il restore
        if (isRestoring) {
            Log.d(TAG, "Save blocked — restore in progress")
            onDone?.invoke(false)
            return
        }

        val userId = uid()
        if (userId == null) {
            onDone?.invoke(false)
            return
        }

        // Debounce: cancella eventuali save pendenti
        pendingSave?.let { handler.removeCallbacks(it) }

        val now = System.currentTimeMillis()
        val delay = if (now - lastSaveMs < DEBOUNCE_MS) DEBOUNCE_MS else 0L

        pendingSave = Runnable {
            doSave(ctx, userId, onDone)
            lastSaveMs = System.currentTimeMillis()
        }
        handler.postDelayed(pendingSave!!, delay)
    }

    /**
     * Forza un salvataggio immediato (bypass debounce).
     * Usare SOLO per eventi critici: app in background, acquisto reale.
     */
    fun saveNow(ctx: Context, onDone: ((Boolean) -> Unit)? = null) {
        if (isRestoring) { onDone?.invoke(false); return }
        val userId = uid() ?: run { onDone?.invoke(false); return }
        pendingSave?.let { handler.removeCallbacks(it) }
        doSave(ctx, userId, onDone)
        lastSaveMs = System.currentTimeMillis()
    }

    private fun doSave(ctx: Context, userId: String, onDone: ((Boolean) -> Unit)?) {
        try {
            val mvcBalance = SavedManager.getMvcBalance(ctx)

            // Non salvare se MVC è 0 e non ci sono dati significativi
            if (mvcBalance <= 0 && !SavedManager.getHatchedEggs(ctx).isEmpty().not()) {
                Log.d(TAG, "Skip save — no meaningful data")
                onDone?.invoke(false)
                return
            }

            val data = hashMapOf<String, Any>(
                "mvcBalance" to mvcBalance,
                "lastMiningCalcMs" to ctx.getSharedPreferences("huntix_saved_v1", Context.MODE_PRIVATE)
                    .getLong("last_mining_calc_ms", System.currentTimeMillis()),
                "pendingEggs" to ctx.getSharedPreferences("huntix_saved_v1", Context.MODE_PRIVATE)
                    .getString("pending_eggs", "[]") ?: "[]",
                "hatchingSlots" to ctx.getSharedPreferences("huntix_saved_v1", Context.MODE_PRIVATE)
                    .getString("hatching_eggs", "[]") ?: "[]",
                "hatchedEggs" to ctx.getSharedPreferences("huntix_saved_v1", Context.MODE_PRIVATE)
                    .getString("hatched_eggs", "[]") ?: "[]",
                "foodInventory" to getFoodInventory(ctx),
                "toolInventory" to getToolInventory(ctx),
                "syncedAt" to System.currentTimeMillis()
            )

            db.collection(COL).document(userId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Progress saved (MVC: $mvcBalance)")
                    onDone?.invoke(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Save failed: ${e.message}")
                    onDone?.invoke(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Save error: ${e.message}")
            onDone?.invoke(false)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // RIPRISTINO ← Firestore
    // ═══════════════════════════════════════════════════════════

    /**
     * Ripristina il progresso da Firestore.
     * Imposta isRestoring=true per bloccare i salvataggi concorrenti.
     */
    fun restoreProgress(ctx: Context, onDone: ((Boolean) -> Unit)? = null) {
        val userId = uid()
        if (userId == null) {
            onDone?.invoke(false)
            return
        }

        // ✅ BLOCK salvataggi durante il restore
        isRestoring = true
        Log.d(TAG, "Starting restore for $userId...")

        db.collection(COL).document(userId).get()
            .addOnSuccessListener { doc ->
                try {
                    if (!doc.exists()) {
                        Log.d(TAG, "No cloud progress — first time user")
                        isRestoring = false
                        onDone?.invoke(false)
                        return@addOnSuccessListener
                    }

                    val data = doc.data ?: run {
                        isRestoring = false
                        onDone?.invoke(false)
                        return@addOnSuccessListener
                    }

                    val prefs = ctx.getSharedPreferences("huntix_saved_v1", Context.MODE_PRIVATE)
                    val localMvc = SavedManager.getMvcBalance(ctx)
                    val cloudMvc = (data["mvcBalance"] as? Number)?.toDouble() ?: 0.0

                    Log.d(TAG, "Local MVC: $localMvc | Cloud MVC: $cloudMvc")

                    // Ripristina se locale è vuoto (dati cancellati) O se cloud è maggiore
                    if (localMvc < 1.0 && cloudMvc > 0) {
                        val editor = prefs.edit()
                        editor.putLong("current_mvc", cloudMvc.toLong())

                        val lastCalc = (data["lastMiningCalcMs"] as? Number)?.toLong()
                            ?: System.currentTimeMillis()
                        editor.putLong("last_mining_calc_ms", lastCalc)

                        // Uova
                        editor.putString("pending_eggs", data["pendingEggs"] as? String ?: "[]")
                        editor.putString("hatching_eggs", data["hatchingSlots"] as? String ?: "[]")
                        editor.putString("hatched_eggs", data["hatchedEggs"] as? String ?: "[]")

                        editor.apply()

                        // Cibo
                        @Suppress("UNCHECKED_CAST")
                        val foodMap = data["foodInventory"] as? Map<String, Number> ?: emptyMap()
                        restoreFoodInventory(ctx, foodMap)

                        // Strumenti
                        @Suppress("UNCHECKED_CAST")
                        val toolMap = data["toolInventory"] as? Map<String, Number> ?: emptyMap()
                        restoreToolInventory(ctx, toolMap)

                        Log.d(TAG, "✅ Progress RESTORED (MVC: $cloudMvc)")
                        isRestoring = false
                        onDone?.invoke(true)
                    } else {
                        Log.d(TAG, "Local data OK (MVC: $localMvc) — skip restore")
                        isRestoring = false
                        onDone?.invoke(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Restore error: ${e.message}")
                    isRestoring = false
                    onDone?.invoke(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Restore fetch failed: ${e.message}")
                isRestoring = false
                onDone?.invoke(false)
            }
    }

    // ═══════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════

    private fun getFoodInventory(ctx: Context): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        try {
            for (food in EggFoodManager.EggFood.values()) {
                val qty = EggFoodManager.getQuantity(ctx, food)
                if (qty > 0) result[food.name] = qty
            }
        } catch (e: Exception) { Log.w(TAG, "getFoodInventory: ${e.message}") }
        return result
    }

    private fun restoreFoodInventory(ctx: Context, map: Map<String, Number>) {
        try {
            for ((name, qty) in map) {
                val food = try { EggFoodManager.EggFood.valueOf(name) } catch (_: Exception) { continue }
                if (EggFoodManager.getQuantity(ctx, food) == 0 && qty.toInt() > 0) {
                    EggFoodManager.addQuantity(ctx, food, qty.toInt())
                }
            }
        } catch (e: Exception) { Log.w(TAG, "restoreFoodInventory: ${e.message}") }
    }

    private fun getToolInventory(ctx: Context): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        try {
            for (tool in CatchToolManager.CatchTool.values()) {
                val qty = CatchToolManager.getQuantity(ctx, tool)
                if (qty > 0) result[tool.name] = qty
            }
        } catch (e: Exception) { Log.w(TAG, "getToolInventory: ${e.message}") }
        return result
    }

    private fun restoreToolInventory(ctx: Context, map: Map<String, Number>) {
        try {
            for ((name, qty) in map) {
                val tool = try { CatchToolManager.CatchTool.valueOf(name) } catch (_: Exception) { continue }
                if (CatchToolManager.getQuantity(ctx, tool) == 0 && qty.toInt() > 0) {
                    CatchToolManager.addQuantity(ctx, tool, qty.toInt())
                }
            }
        } catch (e: Exception) { Log.w(TAG, "restoreToolInventory: ${e.message}") }
    }
}
