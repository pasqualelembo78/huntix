package com.intelligame.huntix
import android.content.Context
import android.widget.Toast
import java.util.Calendar

/**
 * AgeGateManager — Gestione eta con supporto COPPA (sotto 13) e under 18.
 *
 * FIX v7.1: Se birthYear non è ancora stato impostato (utente non ha completato
 * il profilo), currentUserIsChild() ritorna TRUE come safe default. Questo garantisce
 * che nessun ad invasivo venga mostrato prima della verifica dell'età.
 */
object AgeGateManager {
    fun calculateAge(birthYear: Int): Int = Calendar.getInstance().get(Calendar.YEAR) - birthYear
    fun isMinor(birthYear: Int): Boolean = calculateAge(birthYear) < 18
    fun isChild(birthYear: Int): Boolean = calculateAge(birthYear) < 13
    /**
     * ✅ FIX v7.2: Safe default — se profilo non è caricato,
     * tratta l'utente come minore (principio di precauzione).
     * Blocca accesso a Chat, Amici, Scambi fino a verifica età.
     */
    fun currentUserIsMinor(): Boolean = PlayerProfileManager.myProfile?.isMinor ?: true
    /**
     * ✅ FIX v7.1: Safe default — se birthYear non è impostato,
     * tratta l'utente come minore (principio di precauzione COPPA).
     * Questo blocca ads invasivi fino a quando l'utente non completa il profilo.
     */
    fun currentUserIsChild(): Boolean {
        val year = PlayerProfileManager.myProfile?.birthYear ?: return true  // Safe default: assume child
        return isChild(year)
    }

    /**
     * Blocca accesso a funzionalita sociali per under 18.
     */
    fun checkAdultAccess(context: Context, featureName: String): Boolean {
        if (currentUserIsMinor()) {
            Toast.makeText(context, "$featureName non disponibile per under 18", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    /**
     * Blocca accesso completo a chat/social per under 13 (COPPA).
     */
    fun checkCOPPAAccess(context: Context, featureName: String): Boolean {
        if (currentUserIsChild()) {
            Toast.makeText(context, "$featureName non disponibile per under 13", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    // ── FIX v7.2.1: Neutral Age Gate — blocco modifica età ────────

    private const val AGE_PREFS = "age_gate_prefs"
    private const val KEY_AGE_LOCKED = "age_locked"
    private const val KEY_LOCKED_YEAR = "locked_birth_year"

    /**
     * Controlla se l'età è già stata impostata e bloccata.
     * Una volta impostata, l'utente NON può cambiarla.
     */
    fun isAgeLocked(ctx: Context): Boolean =
        ctx.getSharedPreferences(AGE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AGE_LOCKED, false)

    /**
     * Blocca l'età dell'utente. Chiamare dopo il primo inserimento.
     */
    fun lockAge(ctx: Context, birthYear: Int) {
        ctx.getSharedPreferences(AGE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AGE_LOCKED, true)
            .putInt(KEY_LOCKED_YEAR, birthYear)
            .apply()
    }

    /**
     * Restituisce l'anno bloccato, o -1 se non ancora impostato.
     */
    fun getLockedBirthYear(ctx: Context): Int =
        ctx.getSharedPreferences(AGE_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LOCKED_YEAR, -1)
}
