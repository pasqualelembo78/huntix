package com.intelligame.huntix.social

/**
 * ChatProfanityFilter — Filtro automatico contenuti chat.
 *
 * ✅ AGGIUNTO v7.2.1: Play Store richiede moderazione attiva per app con UGC
 * accessibili a minori. Questo filtro blocca:
 *  - Parole volgari/offensive (IT + EN)
 *  - URL/link (prevenzione phishing)
 *  - Pattern di dati personali (email, numeri di telefono)
 *  - Spam (caratteri ripetuti)
 *
 * Il filtro è applicato client-side in ChatActivity.sendMessage()
 * e dovrebbe essere replicato server-side in una Cloud Function.
 */
object ChatProfanityFilter {

    // ── Blacklist parole offensive (minuscolo) ──────────────────
    // NOTA: lista parziale — in produzione usare una libreria come
    // https://github.com/coffee-and-fun/google-profanity-words
    private val BLOCKED_WORDS_IT = setOf(
        "cazzo", "merda", "vaffanculo", "stronzo", "stronza",
        "coglione", "puttana", "minchia", "troia", "figa",
        "negro", "frocio", "ricchione", "handicappato",
        "ammazzati", "suicidati", "muori", "crepa"
    )

    private val BLOCKED_WORDS_EN = setOf(
        "fuck", "shit", "bitch", "asshole", "nigger", "faggot",
        "retard", "whore", "dick", "pussy", "cock", "cum",
        "kill yourself", "kys"
    )

    private val ALL_BLOCKED = BLOCKED_WORDS_IT + BLOCKED_WORDS_EN

    // ── Pattern pericolosi ──────────────────────────────────────
    private val URL_PATTERN = Regex(
        """(https?://|www\.)[\w.-]+\.[a-z]{2,}[/\w?.=&%-]*""",
        RegexOption.IGNORE_CASE
    )
    private val EMAIL_PATTERN = Regex(
        """[\w.+-]+@[\w.-]+\.[a-z]{2,}""",
        RegexOption.IGNORE_CASE
    )
    private val PHONE_PATTERN = Regex(
        """(\+?\d{1,3}[\s.-]?)?\(?\d{2,4}\)?[\s.-]?\d{3,4}[\s.-]?\d{3,4}"""
    )
    private val SPAM_PATTERN = Regex(
        """(.)\1{5,}"""  // 6+ caratteri uguali di fila
    )

    // ── Risultato del filtro ────────────────────────────────────
    data class FilterResult(
        val isBlocked: Boolean,
        val reason: String = "",
        val sanitizedText: String = ""
    )

    /**
     * Controlla un messaggio e restituisce il risultato del filtro.
     *
     * @param text Testo del messaggio da controllare
     * @return FilterResult con isBlocked=true se il messaggio deve essere bloccato
     */
    fun check(text: String): FilterResult {
        val normalized = text.lowercase().trim()

        // 1. Controlla parole bloccate
        for (word in ALL_BLOCKED) {
            if (normalized.contains(word)) {
                return FilterResult(
                    isBlocked = true,
                    reason = "Il messaggio contiene linguaggio non appropriato."
                )
            }
        }

        // 2. Controlla URL (prevenzione phishing/grooming)
        if (URL_PATTERN.containsMatchIn(text)) {
            return FilterResult(
                isBlocked = true,
                reason = "Non è possibile inviare link in chat."
            )
        }

        // 3. Controlla email (protezione dati personali)
        if (EMAIL_PATTERN.containsMatchIn(text)) {
            return FilterResult(
                isBlocked = true,
                reason = "Non condividere indirizzi email in chat."
            )
        }

        // 4. Controlla numeri di telefono
        if (PHONE_PATTERN.containsMatchIn(normalized) && normalized.count { it.isDigit() } >= 8) {
            return FilterResult(
                isBlocked = true,
                reason = "Non condividere numeri di telefono in chat."
            )
        }

        // 5. Controlla spam (caratteri ripetuti)
        if (SPAM_PATTERN.containsMatchIn(text)) {
            return FilterResult(
                isBlocked = true,
                reason = "Messaggio rilevato come spam."
            )
        }

        // 6. Lunghezza minima
        if (normalized.length < 1) {
            return FilterResult(isBlocked = true, reason = "Messaggio vuoto.")
        }

        return FilterResult(isBlocked = false, sanitizedText = text.trim())
    }

    /**
     * Versione semplificata: restituisce true se il messaggio è OK.
     */
    fun isAllowed(text: String): Boolean = !check(text).isBlocked
}
