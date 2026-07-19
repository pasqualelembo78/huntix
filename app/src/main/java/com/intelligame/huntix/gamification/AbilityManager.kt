package com.intelligame.huntix.gamification

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * AbilityManager — Abilità sbloccabili stile Brawl Stars.
 *
 * 6 abilità con cooldown, livelli e upgrade:
 *  1. Radar Uova — rivela uova vicine sulla mappa
 *  2. Speed Boost — aumenta raggio di cattura temporaneamente
 *  3. Magnete — attira uova comuni automaticamente
 *  4. Scudo XP — protegge dall'XP penalty nelle sfide
 *  5. Visione Notturna — migliora visibilità AR in condizioni buie
 *  6. Analizzatore — rivela rarità uova prima di aprirle
 *
 * Firestore: players/{uid}/abilities/{abilityId}
 */
object AbilityManager {

    private val db get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    data class Ability(
        val id: String,
        val name: String,
        val description: String,
        val emoji: String,
        val unlockLevel: Int,           // livello minimo per sbloccare
        var abilityLevel: Int = 1,      // 1-5, upgradabile
        var isUnlocked: Boolean = false,
        var cooldownSeconds: Int = 0,   // base cooldown
        val cooldownMultiplier: Float = 1.0f,
        var lastUsedMs: Long = 0L,
        val gemsCostUpgrade: Int = 10,
        val effectDescription: String = ""
    ) {
        val actualCooldownMs: Long get() = (cooldownSeconds * 1000L * (1.0 - (abilityLevel - 1) * 0.15)).toLong()
        val isReady: Boolean get() = System.currentTimeMillis() - lastUsedMs >= actualCooldownMs
        val cooldownRemainingMs: Long get() = maxOf(0L, actualCooldownMs - (System.currentTimeMillis() - lastUsedMs))
        val cooldownRemainingSeconds: Int get() = (cooldownRemainingMs / 1000).toInt()
        val canUpgrade: Boolean get() = abilityLevel < 5

        fun toMap(): Map<String, Any> = mapOf(
            "id" to id, "abilityLevel" to abilityLevel, "isUnlocked" to isUnlocked,
            "lastUsedMs" to lastUsedMs
        )
    }

    val ALL_ABILITIES = listOf(
        Ability(
            id = "radar", name = "Radar Uova", emoji = "📡",
            description = "Rivela tutte le uova nel raggio di 200m per 30 secondi",
            unlockLevel = 3, cooldownSeconds = 120,
            gemsCostUpgrade = 15,
            effectDescription = "Lv.1: 200m · Lv.3: 350m · Lv.5: 500m + rarità visibile"
        ),
        Ability(
            id = "speed", name = "Speed Boost", emoji = "⚡",
            description = "Raddoppia il raggio di cattura uova per 20 secondi",
            unlockLevel = 5, cooldownSeconds = 90,
            gemsCostUpgrade = 12,
            effectDescription = "Lv.1: x2 raggio · Lv.3: x3 raggio · Lv.5: x4 raggio + velocità"
        ),
        Ability(
            id = "magnet", name = "Magnete", emoji = "🧲",
            description = "Cattura automaticamente uova comuni nelle vicinanze per 15 secondi",
            unlockLevel = 8, cooldownSeconds = 180,
            gemsCostUpgrade = 18,
            effectDescription = "Lv.1: solo Comuni · Lv.3: + Insoliti · Lv.5: + Rari"
        ),
        Ability(
            id = "shield", name = "Scudo XP", emoji = "🛡️",
            description = "Protegge dal perdere posizione in classifica per 1 ora",
            unlockLevel = 10, cooldownSeconds = 3600,
            gemsCostUpgrade = 25,
            effectDescription = "Lv.1: 1 ora · Lv.3: 3 ore · Lv.5: 8 ore"
        ),
        Ability(
            id = "vision", name = "Visione AR", emoji = "👁️",
            description = "Aumenta la distanza di rilevamento AR del 50% per 60 secondi",
            unlockLevel = 15, cooldownSeconds = 240,
            gemsCostUpgrade = 20,
            effectDescription = "Lv.1: +50% visibilità · Lv.5: +200% + filtro rarità"
        ),
        Ability(
            id = "analyzer", name = "Analizzatore", emoji = "🔬",
            description = "Rivela la rarità esatta di 5 uova prima di aprirle",
            unlockLevel = 20, cooldownSeconds = 300,
            gemsCostUpgrade = 30,
            effectDescription = "Lv.1: 5 uova · Lv.3: 10 uova · Lv.5: illimitato x 60s"
        )
    )

    // ─── Carica stato abilità dal DB ─────────────────────────────

    fun loadAbilities(playerLevel: Int, onResult: (List<Ability>) -> Unit) {
        if (uid.isEmpty()) { onResult(ALL_ABILITIES.map { it.copy(isUnlocked = it.unlockLevel <= playerLevel) }); return }

        db.collection("players").document(uid).collection("abilities")
          .get()
          .addOnSuccessListener { snap ->
              val saved = snap.documents.associate { doc ->
                  val id = doc.getString("id") ?: doc.id
                  id to Pair(
                      (doc.getLong("abilityLevel") ?: 1L).toInt(),
                      (doc.getLong("lastUsedMs") ?: 0L)
                  )
              }
              val result = ALL_ABILITIES.map { base ->
                  val (lvl, lastUsed) = saved[base.id] ?: Pair(1, 0L)
                  base.copy(
                      abilityLevel = lvl,
                      isUnlocked = base.unlockLevel <= playerLevel,
                      lastUsedMs = lastUsed
                  )
              }
              onResult(result)
          }
          .addOnFailureListener {
              onResult(ALL_ABILITIES.map { it.copy(isUnlocked = it.unlockLevel <= playerLevel) })
          }
    }

    fun useAbility(ability: Ability, onSuccess: (Ability) -> Unit, onError: (String) -> Unit) {
        if (!ability.isUnlocked) { onError("Abilità non sbloccata (richiesto Lv.${ability.unlockLevel})"); return }
        if (!ability.isReady) { onError("In cooldown: ${ability.cooldownRemainingSeconds}s"); return }
        if (uid.isEmpty()) { onError("Non autenticato"); return }

        val now = System.currentTimeMillis()
        db.collection("players").document(uid).collection("abilities")
          .document(ability.id)
          .set(ability.copy(lastUsedMs = now).toMap())
          .addOnSuccessListener { onSuccess(ability.copy(lastUsedMs = now)) }
          .addOnFailureListener { onError(it.message ?: "Errore") }
    }

    fun upgradeAbility(ability: Ability, playerGems: Int, onSuccess: (Ability, gemsSpent: Int) -> Unit, onError: (String) -> Unit) {
        if (!ability.isUnlocked) { onError("Sblocca prima l'abilità"); return }
        if (!ability.canUpgrade) { onError("Livello massimo raggiunto!"); return }
        if (playerGems < ability.gemsCostUpgrade) { onError("Gemme insufficienti (servono ${ability.gemsCostUpgrade} 💎)"); return }
        if (uid.isEmpty()) { onError("Non autenticato"); return }

        val upgraded = ability.copy(abilityLevel = ability.abilityLevel + 1)
        val batch = db.batch()
        batch.set(
            db.collection("players").document(uid).collection("abilities").document(ability.id),
            upgraded.toMap()
        )
        batch.update(
            db.collection("players").document(uid),
            "gems", com.google.firebase.firestore.FieldValue.increment((-ability.gemsCostUpgrade).toLong())
        )
        batch.commit()
          .addOnSuccessListener { onSuccess(upgraded, ability.gemsCostUpgrade) }
          .addOnFailureListener { onError(it.message ?: "Errore upgrade") }
    }

    // Effetto reale del radar (ritorna raggio in metri)
    fun getRadarRangeM(abilityLevel: Int): Int = when (abilityLevel) {
        1 -> 200; 2 -> 275; 3 -> 350; 4 -> 425; 5 -> 500; else -> 200
    }

    // Moltiplicatore raggio cattura per Speed Boost
    fun getSpeedMultiplier(abilityLevel: Int): Float = when (abilityLevel) {
        1 -> 2.0f; 2 -> 2.5f; 3 -> 3.0f; 4 -> 3.5f; 5 -> 4.0f; else -> 2.0f
    }
}
