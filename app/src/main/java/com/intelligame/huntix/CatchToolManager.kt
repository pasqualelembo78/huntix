package com.intelligame.huntix

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * CatchToolManager — gestione strumenti di cattura (Secchielli).
 *
 * Stile Pokémon GO: il giocatore può scegliere tra diversi strumenti
 * con catch rate crescente. Acquistabili al negozio.
 *
 * 🪣 Secchiello Base  — catch rate ×1.0 (illimitato, sempre disponibile)
 * 🪣 Super Secchiello — catch rate ×1.3 (acquistabile, scade dopo 10 giorni)
 * 🏆 Ultra Secchiello — catch rate ×1.6 (acquistabile, scade dopo 10 giorni)
 *
 * I secchielli acquistati hanno una durata temporale: dopo expiryDays giorni
 * dall'acquisto si "rompono" e devono essere riacquistati.
 */
object CatchToolManager {

    private const val PREFS = "catch_tools_prefs"
    private const val TAG   = "CatchToolManager"

    // ── Definizione strumenti ────────────────────────────────────

    enum class CatchTool(
        val id: String,
        val displayName: String,
        val emoji: String,
        val catchMultiplier: Float,
        val colorHex: String,
        val isUnlimited: Boolean,
        val shopPrice: Int,    // prezzo per 1 unità, 0 = non acquistabile
        val expiryDays: Int    // giorni prima che si rompa (0 = nessuna scadenza)
    ) {
        BUCKET_BASE(
            id = "bucket_base",
            displayName = "Secchiello",
            emoji = "🪣",
            catchMultiplier = 1.0f,
            colorHex = "#00FF88",
            isUnlimited = true,
            shopPrice = 0,
            expiryDays = 0
        ),
        BUCKET_SUPER(
            id = "bucket_super",
            displayName = "Super Secchiello",
            emoji = "🪣✨",
            catchMultiplier = 1.3f,
            colorHex = "#00B4FF",
            isUnlimited = false,
            shopPrice = 50,
            expiryDays = 10
        ),
        BUCKET_ULTRA(
            id = "bucket_ultra",
            displayName = "Ultra Secchiello",
            emoji = "🏆",
            catchMultiplier = 1.6f,
            colorHex = "#FFD700",
            isUnlimited = false,
            shopPrice = 150,
            expiryDays = 10
        );

        companion object {
            fun fromId(id: String) = values().firstOrNull { it.id == id } ?: BUCKET_BASE
        }
    }

    // ── Inventario ───────────────────────────────────────────────

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Restituisce la quantità disponibile di un tool. BUCKET_BASE = sempre 99. */
    fun getQuantity(ctx: Context, tool: CatchTool): Int {
        if (tool.isUnlimited) return 99
        // Prima purga i lotti scaduti, poi conta
        purgeExpiredBatches(ctx, tool)
        return prefs(ctx).getInt("qty_${tool.id}", 0)
    }

    /**
     * Aggiunge N unità di un tool all'inventario.
     * Registra anche il timestamp di acquisto per il tracking scadenza.
     */
    fun addQuantity(ctx: Context, tool: CatchTool, amount: Int) {
        if (tool.isUnlimited) return
        val current = prefs(ctx).getInt("qty_${tool.id}", 0)
        prefs(ctx).edit().putInt("qty_${tool.id}", current + amount).apply()

        // Registra un nuovo "lotto" con timestamp di acquisto
        if (tool.expiryDays > 0) {
            val batchKey = "batch_${tool.id}_${System.currentTimeMillis()}"
            prefs(ctx).edit()
                .putInt(batchKey + "_qty", amount)
                .putLong(batchKey + "_ts", System.currentTimeMillis())
                .apply()
            // Aggiungi il batch alla lista dei batch attivi
            val batchList = getBatchKeys(ctx, tool).toMutableSet()
            batchList.add(batchKey)
            prefs(ctx).edit().putStringSet("batches_${tool.id}", batchList).apply()
        }
    }

    /** Consuma 1 unità del tool. Restituisce false se non ce ne sono. */
    fun consume(ctx: Context, tool: CatchTool): Boolean {
        if (tool.isUnlimited) return true
        purgeExpiredBatches(ctx, tool)
        val current = prefs(ctx).getInt("qty_${tool.id}", 0)
        if (current <= 0) return false
        prefs(ctx).edit().putInt("qty_${tool.id}", current - 1).apply()
        // Consuma dal batch più vecchio
        consumeFromOldestBatch(ctx, tool)
        return true
    }

    /** Restituisce tutti i tool disponibili (qty > 0 o illimitato). */
    fun getAvailableTools(ctx: Context): List<Pair<CatchTool, Int>> {
        return CatchTool.values().map { tool ->
            Pair(tool, getQuantity(ctx, tool))
        }.filter { it.second > 0 }
    }

    /** Tool selezionato attualmente (persiste tra sessioni). */
    fun getSelectedTool(ctx: Context): CatchTool {
        val id = prefs(ctx).getString("selected_tool", CatchTool.BUCKET_BASE.id)
        val tool = CatchTool.fromId(id ?: CatchTool.BUCKET_BASE.id)
        // Se il tool selezionato non ha più unità, torna al base
        if (!tool.isUnlimited && getQuantity(ctx, tool) <= 0) {
            setSelectedTool(ctx, CatchTool.BUCKET_BASE)
            return CatchTool.BUCKET_BASE
        }
        return tool
    }

    fun setSelectedTool(ctx: Context, tool: CatchTool) {
        prefs(ctx).edit().putString("selected_tool", tool.id).apply()
    }

    /**
     * Giorni rimanenti prima che il lotto più vecchio di un tool scada.
     * Restituisce -1 se non ha scadenza o non ha lotti.
     */
    fun getDaysRemaining(ctx: Context, tool: CatchTool): Int {
        if (tool.expiryDays <= 0 || tool.isUnlimited) return -1
        val batches = getBatchKeys(ctx, tool)
        if (batches.isEmpty()) return -1
        val oldestTs = batches.mapNotNull { key ->
            prefs(ctx).getLong(key + "_ts", 0L).takeIf { it > 0 }
        }.minOrNull() ?: return -1
        val expiresAt = oldestTs + tool.expiryDays * 24L * 60 * 60 * 1000
        val remaining = (expiresAt - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)
        return remaining.toInt().coerceAtLeast(0)
    }

    // ── Scadenza lotti ──────────────────────────────────────────

    private fun getBatchKeys(ctx: Context, tool: CatchTool): Set<String> {
        return prefs(ctx).getStringSet("batches_${tool.id}", emptySet()) ?: emptySet()
    }

    /**
     * Rimuove i lotti scaduti (più vecchi di expiryDays) e scala la quantità.
     */
    private fun purgeExpiredBatches(ctx: Context, tool: CatchTool) {
        if (tool.expiryDays <= 0) return
        val now = System.currentTimeMillis()
        val expiryMs = tool.expiryDays * 24L * 60 * 60 * 1000
        val batches = getBatchKeys(ctx, tool).toMutableSet()
        var totalExpired = 0

        val toRemove = mutableListOf<String>()
        for (key in batches) {
            val ts = prefs(ctx).getLong(key + "_ts", 0L)
            if (ts > 0 && (now - ts) > expiryMs) {
                val qty = prefs(ctx).getInt(key + "_qty", 0)
                totalExpired += qty
                toRemove.add(key)
                Log.d(TAG, "Lotto scaduto: $key (qty=$qty, acquistato ${(now - ts) / 86400000}gg fa)")
            }
        }

        if (toRemove.isNotEmpty()) {
            val editor = prefs(ctx).edit()
            for (key in toRemove) {
                editor.remove(key + "_qty")
                editor.remove(key + "_ts")
                batches.remove(key)
            }
            // Scala la quantità totale
            val current = prefs(ctx).getInt("qty_${tool.id}", 0)
            val newQty = (current - totalExpired).coerceAtLeast(0)
            editor.putInt("qty_${tool.id}", newQty)
            editor.putStringSet("batches_${tool.id}", batches)
            editor.apply()
            Log.d(TAG, "${tool.displayName}: rimossi $totalExpired scaduti, rimanenti: $newQty")
        }
    }

    private fun consumeFromOldestBatch(ctx: Context, tool: CatchTool) {
        if (tool.expiryDays <= 0) return
        val batches = getBatchKeys(ctx, tool)
        // Trova il batch più vecchio con qty > 0
        val oldest = batches
            .filter { prefs(ctx).getInt(it + "_qty", 0) > 0 }
            .minByOrNull { prefs(ctx).getLong(it + "_ts", Long.MAX_VALUE) }
            ?: return
        val qty = prefs(ctx).getInt(oldest + "_qty", 0)
        if (qty > 1) {
            prefs(ctx).edit().putInt(oldest + "_qty", qty - 1).apply()
        } else {
            // Rimuovi il batch esaurito
            val mutableBatches = batches.toMutableSet()
            mutableBatches.remove(oldest)
            prefs(ctx).edit()
                .remove(oldest + "_qty")
                .remove(oldest + "_ts")
                .putStringSet("batches_${tool.id}", mutableBatches)
                .apply()
        }
    }

    /**
     * Starter kit DISABILITATO — i secchielli si ottengono solo dal negozio.
     * Il giocatore inizia con il solo Secchiello Base (illimitato).
     */
    fun giveStarterKit(ctx: Context) {
        // Starter kit rimosso: il giocatore usa il Secchiello Base di default
        // e deve acquistare Super/Ultra dal negozio.
    }
}
