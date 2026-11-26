package com.intelligame.huntix.gamification

import android.content.Context
import org.json.JSONObject

/**
 * Abilità dei mestieri di MiCittà — sistema ADDITIVO e separato dalle 6
 * abilità caccia-uova di [AbilityManager] (che restano intatte).
 *
 * Ogni nuovo giocatore parte da LIVELLO ZERO in tutte le abilità: non c'è
 * migrazione e nessun default nascosto a 1. Ogni lavoro completato sale di
 * livello e regala 1 punto abilità, spendibile dall'utente nell'Activity
 * "Abilità" (Sezione "Mestieri di MiCittà").
 *
 * Persistenza: SharedPreferences del processo app, così Android è sempre
 * fonte di verità anche quando Unity non è avviato. Lo stato viene rimandato
 * a Unity in setMode (BridgeActivity) e subito dopo ogni spesa (notifyUnity).
 */
object CitySkillManager {

    const val MAX_LEVEL = 10

    private const val PREF_FILE = "city_skills_prefs"
    private const val KEY_LEVELS = "city_skill_levels"
    private const val KEY_POINTS = "city_skill_points"
    private const val KEY_USES = "city_skill_uses"

    data class Skill(
        val id: String,
        val name: String,
        val emoji: String,
        val description: String,
        val unlocks: String
    )

    /** Sei mestieri: il ladro apre i furti, gli altri preparano i lavori
     *  futuri (attore, cassiere, banchiere, parcheggiatore abusivo). */
    val SKILLS = listOf(
        Skill(
            "mastro_ladro", "Mastro Ladro", "\uD83E\uDD77",
            "Rubare veicoli senza pagarli e, al livello massimo, entrare negli appartamenti.",
            "Lv1 bici/moto · Lv2 utilitarie · Lv4 SUV/furgoni · Lv6 sportive · Lv8 auto di lusso · Lv10 appartamenti"
        ),
        Skill(
            "pilota", "Pilota", "\uD83C\uDFCE\uFE0F",
            "Guida rapida, inseguimenti e parcheggi abusivi senza farsi beccare.",
            "Lv1 scooter · Lv3 trafficare · Lv5 auto veloci · Lv10 fuga perfetta"
        ),
        Skill(
            "meccanico", "Meccanico", "\uD83D\uDD27",
            "Riparazioni economiche e rimozione degli antifurti sulle auto prese.",
            "Lv1 riparazioni semplici · Lv5 antifurti · Lv10 motori e carrozzeria"
        ),
        Skill(
            "attore", "Attore", "\uD83C\uDFAC",
            "Guadagna di più quando arriveranno i lavori da attore (già il lavoro Cineasta).",
            "+5% di paga per livello sui lavori di recitazione"
        ),
        Skill(
            "assassino", "Assassino", "\uD83D\uDDE1️",
            "Prepara le missioni riservate che verranno introdotte nei prossimi lavori.",
            "Lv1 pedinamento · Lv5 imboscate · Lv10 contratti speciali"
        ),
        Skill(
            "contabile", "Contabile", "\uD83E\uDDFE",
            "Prepara cassiere, banchiere e parcheggiatore abusivo: interessi e incassi.",
            "+2% di incasso per livello sui lavori di cassa/banca"
        )
    )

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    private fun readLevels(ctx: Context): JSONObject = try {
        JSONObject(prefs(ctx).getString(KEY_LEVELS, null) ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    /** Livello 0..10. Nuovi utenti: 0 (nessun default a 1). */
    fun level(ctx: Context, id: String): Int {
        if (SKILLS.none { it.id == id }) return 0
        return readLevels(ctx).optInt(id, 0).coerceIn(0, MAX_LEVEL)
    }

    fun points(ctx: Context): Int = prefs(ctx).getInt(KEY_POINTS, 0)

    /** Punti davvero aggiunti (di norma tanti quanti i livelli saliti). */
    fun addPoints(ctx: Context, amount: Int): Int {
        if (amount <= 0) return 0
        val next = points(ctx) + amount
        prefs(ctx).edit().putInt(KEY_POINTS, next).apply()
        return amount
    }

    /** Spende 1 punto per alzare un'abilità di un livello. */
    fun spendPoint(ctx: Context, id: String): Boolean {
        if (SKILLS.none { it.id == id }) return false
        val p = prefs(ctx)
        val current = level(ctx, id)
        if (current >= MAX_LEVEL || p.getInt(KEY_POINTS, 0) <= 0) return false
        val levels = readLevels(ctx)
        levels.put(id, current + 1)
        p.edit()
            .putString(KEY_LEVELS, levels.toString())
            .putInt(KEY_POINTS, p.getInt(KEY_POINTS, 0) - 1)
            .apply()
        notifyUnity(ctx)
        return true
    }

    /** Usi registrati (telemetria locale del universo unico). */
    fun recordUse(ctx: Context, id: String, level: Int) {
        val p = prefs(ctx)
        val uses = JSONObject(p.getString(KEY_USES, null) ?: "{}")
        uses.put(id, uses.optInt(id, 0) + 1)
        uses.put(id + "_lastLevel", level)
        p.edit().putString(KEY_USES, uses.toString()).apply()
    }

    fun useCount(ctx: Context, id: String): Int = try {
        JSONObject(prefs(ctx).getString(KEY_USES, null) ?: "{}").optInt(id, 0)
    } catch (_: Exception) { 0 }

    /** Stato completo spedito a Unity: levels + punti + usi. */
    fun exportJson(ctx: Context): String {
        val root = JSONObject()
        root.put("levels", readLevels(ctx))
        root.put("points", points(ctx))
        root.put("uses", JSONObject(prefs(ctx).getString(KEY_USES, null) ?: "{}"))
        return root.toString()
    }

    /** Formato compatto usato nel JSON di setMode e nell'evento caldo:
     *  `mastro_ladro:2;pilota:0;...`, senza JSON annidato da parsare in Unity. */
    fun exportLevelsString(ctx: Context): String =
        SKILLS.joinToString(";") { it.id + ":" + level(ctx, it.id) }

    /** Android → Unity: aggiornamento a caldo dopo una spesa. */
    fun notifyUnity(ctx: Context) {
        try {
            com.intelligame.huntix.bridge.UnityIpc.sendToUnity(
                "GameManager", "OnEvent",
                "CitySkillsUpdated|" + exportLevelsString(ctx) + "|" + points(ctx)
            )
        } catch (e: Exception) {
            com.intelligame.huntix.AppLog.w("CitySkillManager", "notifyUnity: ${e.message}")
        }
    }
}
