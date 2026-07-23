package com.intelligame.huntix.reallife

import android.content.Context
import android.content.SharedPreferences

/**
 * MoneyManager — gestione locale del cash (per-device, senza backend).
 * Ogni lavoro guadagna cash che viene speso nei negozi.
 */
object MoneyManager {
    private const val PREFS = "rl_money"
    private const val K_CASH = "cash"
    private const val K_TOTAL_EARNED = "total_earned"
    private const val K_JOBS_DONE = "jobs_done"
    private const val STARTING_CASH = 500

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getCash(ctx: Context): Int = prefs(ctx).getInt(K_CASH, STARTING_CASH)
    fun getTotalEarned(ctx: Context): Int = prefs(ctx).getInt(K_TOTAL_EARNED, 0)
    fun getJobsDone(ctx: Context): Int = prefs(ctx).getInt(K_JOBS_DONE, 0)

    fun addCash(ctx: Context, amount: Int): Int {
        val new = getCash(ctx) + amount
        prefs(ctx).edit().apply {
            putInt(K_CASH, new)
            putInt(K_TOTAL_EARNED, getTotalEarned(ctx) + amount)
            apply()
        }
        return new
    }

    fun spendCash(ctx: Context, amount: Int): Boolean {
        val current = getCash(ctx)
        if (current < amount) return false
        prefs(ctx).edit().apply {
            putInt(K_CASH, current - amount)
            apply()
        }
        return true
    }

    fun incrementJobsDone(ctx: Context) {
        prefs(ctx).edit().apply {
            putInt(K_JOBS_DONE, getJobsDone(ctx) + 1)
            apply()
        }
    }
}
