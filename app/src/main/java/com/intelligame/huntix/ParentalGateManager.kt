package com.intelligame.huntix

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/**
 * ParentalGateManager — Verifica parentale per under 13 (COPPA compliance).
 *
 * ✅ AGGIUNTO v7.2.1: Play Store + FTC richiedono verifica genitore
 * per sbloccare funzionalità sensibili (chat, social, acquisti) per minori.
 *
 * Implementa il pattern "Math Challenge" usato da Apple, YouTube Kids, Netflix:
 * una moltiplicazione a due cifre che un bambino <13 non risolve facilmente.
 */
object ParentalGateManager {

    private const val PREFS = "parental_gate_prefs"
    private const val KEY_VERIFIED = "parental_verified"
    private const val KEY_VERIFIED_TS = "parental_verified_ts"
    private const val VERIFICATION_DURATION_MS = 24 * 60 * 60 * 1000L // 24h

    /**
     * Controlla se la verifica parentale è ancora valida (dura 24 ore).
     */
    fun isVerified(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_VERIFIED, false)) return false
        val ts = prefs.getLong(KEY_VERIFIED_TS, 0L)
        return System.currentTimeMillis() - ts < VERIFICATION_DURATION_MS
    }

    /**
     * Se l'utente è under 13 e non ha verifica parentale attiva,
     * mostra la sfida matematica. Altrimenti procede direttamente.
     *
     * Per utenti 13+ non mostra nulla e chiama onPassed().
     */
    fun requireIfChild(
        activity: Activity,
        featureName: String,
        onPassed: () -> Unit
    ) {
        // Se non è under 13, passa direttamente
        if (!AgeGateManager.currentUserIsChild()) {
            onPassed()
            return
        }
        // Se già verificato nelle ultime 24h, passa
        if (isVerified(activity)) {
            onPassed()
            return
        }
        // Mostra sfida
        showMathChallenge(activity, featureName, onPassed)
    }

    /**
     * Mostra la sfida matematica per verifica parentale.
     */
    private fun showMathChallenge(
        activity: Activity,
        featureName: String,
        onPassed: () -> Unit
    ) {
        val a = (10..25).random()
        val b = (3..9).random()
        val c = (1..15).random()
        val answer = a * b + c

        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Inserisci il risultato"
            setPadding(48, 32, 48, 32)
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(input)
        }

        AlertDialog.Builder(activity)
            .setTitle("\uD83D\uDD12 Verifica Genitore")
            .setMessage(
                "Per accedere a \"$featureName\", " +
                "chiedi a un genitore di risolvere:\n\n" +
                "Quanto fa $a \u00D7 $b + $c?"
            )
            .setView(container)
            .setPositiveButton("Conferma") { _, _ ->
                val userAnswer = input.text.toString().trim().toIntOrNull()
                if (userAnswer == answer) {
                    // Salva verifica per 24h
                    activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_VERIFIED, true)
                        .putLong(KEY_VERIFIED_TS, System.currentTimeMillis())
                        .apply()
                    onPassed()
                } else {
                    Toast.makeText(activity,
                        "Risposta errata. Chiedi aiuto a un genitore.",
                        Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .setCancelable(false)
            .show()
    }

    /**
     * Resetta la verifica parentale (es. per debug o cambio profilo).
     */
    fun resetVerification(activity: Activity) {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
