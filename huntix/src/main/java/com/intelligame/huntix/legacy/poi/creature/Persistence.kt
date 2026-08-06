package com.intelligame.huntix.legacy.poi.creature

import android.content.Context
import com.google.gson.Gson

object Persistence {

    private const val PREFS = "poi_trainer"
    private const val KEY = "trainer_json"
    private val gson = Gson()

    private var cached: Trainer? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, null)
        if (json != null) {
            try { cached = gson.fromJson(json, Trainer::class.java) } catch (_: Exception) { cached = null }
        }
        if (cached == null) cached = Trainer()
    }

    fun trainer(): Trainer = cached ?: Trainer()

    fun saveTrainer(t: Trainer) {
        cached = t
    }

    fun flush(context: Context) {
        val t = cached ?: return
        val json = gson.toJson(t)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
    }
}
