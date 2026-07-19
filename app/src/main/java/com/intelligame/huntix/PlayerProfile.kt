package com.intelligame.huntix

/**
 * PlayerProfile — profilo persistente del giocatore nel mondo outdoor.
 *
 * Stored in Firestore: players/{firebaseUid}
 *
 * === AGGIORNATO con sistema gamification Brawl Stars ===
 *  - gems: gemme premium per acquisti cosmetici e upgrade abilità
 *  - teamId: ID squadra corrente
 *  - weeklyXp: XP accumulati questa settimana (per classifica settimanale)
 *  - weeklyEggsFound: uova trovate questa settimana
 *  - lastWeekResetMs: timestamp dell'ultimo reset settimanale
 *  - equippedAvatarFrameId, equippedTitleId, equippedEggSkinId: personalizzazione
 *  - totalLoginDays: per streak e bonus
 *  - lastLoginDate: per calcolo streak
 *  - playerCharacterId: ID personaggio 3D selezionato dal giocatore
 *  - characterChangesCount: numero di volte che il personaggio è stato cambiato
 *  - buddyCreatureId: ID creatura buddy (dalla schiusura uova sorpresa)
 */
data class PlayerProfile(
    val playerId:               String  = "",
    var name:                   String  = "",
    var xp:                     Long    = 0L,
    var power:                  Long    = 0L,
    var eggsFound:              Int     = 0,
    var commonFound:            Int     = 0,
    var uncommonFound:          Int     = 0,
    var rareFound:              Int     = 0,
    var epicFound:              Int     = 0,
    var legendaryFound:         Int     = 0,
    var gymsVisited:            Int     = 0,
    var gymTrainings:           Int     = 0,
    var strength:               Int     = 0,
    var energy:                 Int     = 100,
    val createdAt:              Long    = System.currentTimeMillis(),
    var lastSeen:               Long    = System.currentTimeMillis(),
    var isGoogleUser:           Boolean = false,
    var hasPlayedOutdoor:       Boolean = false,
    var firebaseUid:            String  = "",

    // ─── Gamification (Brawl Stars system) ────────────────────────
    var gems:                   Int     = 0,           // Gemme premium 💎
    var teamId:                 String  = "",           // ID squadra corrente
    var weeklyXp:               Long    = 0L,           // XP questa settimana
    var weeklyEggsFound:        Int     = 0,            // Uova questa settimana
    var lastWeekResetMs:        Long    = 0L,           // Timestamp reset settimanale
    var equippedAvatarFrameId:  String  = "frame_default",
    var equippedTitleId:        String  = "title_default",
    var equippedEggSkinId:      String  = "skin_default",
    var equippedMapThemeId:     String  = "theme_default",
    var totalLoginDays:         Int     = 0,            // Giorni totali di login
    var lastLoginDate:          Long    = 0L,           // Timestamp ultimo login
    var playerCharacterId:      String  = "",           // ID personaggio player (es: "guerriero")
    var characterChangesCount:  Int     = 0,            // Numero cambi personaggio effettuati
    var buddyCreatureId:        String  = "",            // ID creatura buddy (dalla schiusura uova)

    // Social Profile
    var country: String = "", var city: String = "",
    var birthYear: Int = 0, var isMinor: Boolean = false, var profileCompleted: Boolean = false,
    // ── Gender & Character System ─────────────────────────────
    var playerGender:           String  = "",            // "male" o "female"
    var genderChangesCount:     Int     = 0,             // Numero cambi sesso effettuati
    var genderChosenAt:         Long    = 0L,            // Timestamp prima scelta sesso
    var equippedAccessories:    String  = "",            // Lista accessori equipaggiati

    // ── Ready Player Me Avatar ────────────────────────────────
    var rpmAvatarUrl:       String  = "",    // URL modello GLB RPM
    var rpmAvatarId:        String  = "",    // ID avatar RPM
    var rpmAvatarVersion:   Int     = 0,     // Versione avatar (incrementale)
    var equippedHeadId:     String  = "",    // Accessorio testa equipaggiato
    var equippedBodyId:     String  = "",    // Accessorio corpo equipaggiato
    var equippedEffectId:   String  = ""     // Accessorio effetto equipaggiato
) {
    // ─── Livello calcolato da XP ──────────────────────────────────
    val level: Int get() {
        var lv = 1; var required = 0L
        while (xp >= required + lv * 150L) { required += lv * 150L; lv++ }
        return lv
    }

    val xpForCurrentLevel: Long get() {
        var required = 0L
        for (i in 1 until level) required += i * 150L
        return required
    }

    val xpForNextLevel: Long get() {
        var required = 0L
        for (i in 1..level) required += i * 150L
        return required
    }

    val xpProgressInLevel: Long get() = xp - xpForCurrentLevel
    val xpNeededForNextLevel: Long get() = xpForNextLevel - xpForCurrentLevel
    val levelProgressPercent: Int get() =
        ((xpProgressInLevel.toFloat() / xpNeededForNextLevel) * 100).toInt().coerceIn(0, 100)

    /** Titolo basato sul livello */
    val title: String get() = when {
        level >= 50 -> "🐉 Gran Maestro"
        level >= 40 -> "⭐ Leggenda"
        level >= 30 -> "🔥 Campione"
        level >= 20 -> "💎 Esperto"
        level >= 15 -> "🥇 Veterano"
        level >= 10 -> "🏅 Avanzato"
        level >= 5  -> "🟢 Intermedio"
        else        -> "🐣 Principiante"
    }

    /** Aggiunge XP e potere dopo la cattura di un uovo */
    fun addEggReward(rarity: EggRarity) {
        xp    += rarity.xpReward
        power += rarity.basePower
        eggsFound++
        weeklyEggsFound++
        weeklyXp += rarity.xpReward
        when (rarity) {
            EggRarity.COMMON    -> commonFound++
            EggRarity.UNCOMMON  -> uncommonFound++
            EggRarity.RARE      -> rareFound++
            EggRarity.EPIC      -> epicFound++
            EggRarity.LEGENDARY -> legendaryFound++
        }
        lastSeen = System.currentTimeMillis()
    }

    /** Aggiunge potere dopo un allenamento in palestra */
    fun addTrainingReward(powerGained: Long, xpGained: Long) {
        power += powerGained
        xp    += xpGained
        weeklyXp += xpGained
        gymTrainings++
        lastSeen = System.currentTimeMillis()
    }

    /** Reset settimanale (chiamare ogni lunedì) */
    fun resetWeeklyStats() {
        weeklyXp = 0L
        weeklyEggsFound = 0
        lastWeekResetMs = System.currentTimeMillis()
    }

    /** Aggiungi gemme (ricompensa quest/evento) */
    fun addGems(amount: Int) {
        gems = (gems + amount).coerceAtMost(99_999)
    }

    fun toFirestore(): Map<String, Any> = mapOf(
        "playerId"               to playerId,
        "name"                   to name,
        "xp"                     to xp,
        "power"                  to power,
        "eggsFound"              to eggsFound,
        "commonFound"            to commonFound,
        "uncommonFound"          to uncommonFound,
        "rareFound"              to rareFound,
        "epicFound"              to epicFound,
        "legendaryFound"         to legendaryFound,
        "gymsVisited"            to gymsVisited,
        "gymTrainings"           to gymTrainings,
        "strength"               to strength,
        "energy"                 to energy,
        "createdAt"              to createdAt,
        "lastSeen"               to lastSeen,
        "level"                  to level,
        "isGoogleUser"           to isGoogleUser,
        "hasPlayedOutdoor"       to hasPlayedOutdoor,
        "firebaseUid"            to firebaseUid,
        // Gamification fields
        "gems"                   to gems,
        "teamId"                 to teamId,
        "weeklyXp"               to weeklyXp,
        "weeklyEggsFound"        to weeklyEggsFound,
        "lastWeekResetMs"        to lastWeekResetMs,
        "equippedAvatarFrameId"  to equippedAvatarFrameId,
        "equippedTitleId"        to equippedTitleId,
        "equippedEggSkinId"      to equippedEggSkinId,
        "equippedMapThemeId"     to equippedMapThemeId,
        "totalLoginDays"         to totalLoginDays,
        "lastLoginDate"          to lastLoginDate,
        // Character & Buddy
        "playerCharacterId"      to playerCharacterId,
        "characterChangesCount"  to characterChangesCount,
        "buddyCreatureId"        to buddyCreatureId,
        "playerGender"           to playerGender,
        "genderChangesCount"     to genderChangesCount,
        "genderChosenAt"         to genderChosenAt,
        "equippedAccessories" to equippedAccessories,
        "country" to country, "city" to city, "birthYear" to birthYear, "isMinor" to isMinor, "profileCompleted" to profileCompleted
    )

    val hasChosenGender: Boolean get() = playerGender.isNotBlank()

    val characterGlbPath: String get() = when(playerGender) {
        "male" -> "characters/player/male.glb"
        "female" -> "characters/player/female.glb"
        else -> ""
    }

    companion object {
        fun fromFirestore(map: Map<String, Any?>): PlayerProfile? {
            val id = map["playerId"] as? String ?: return null
            return PlayerProfile(
                playerId               = id,
                name                   = map["name"] as? String ?: "?",
                xp                     = (map["xp"] as? Long) ?: 0L,
                power                  = (map["power"] as? Long) ?: 0L,
                eggsFound              = (map["eggsFound"] as? Long)?.toInt() ?: 0,
                commonFound            = (map["commonFound"] as? Long)?.toInt() ?: 0,
                uncommonFound          = (map["uncommonFound"] as? Long)?.toInt() ?: 0,
                rareFound              = (map["rareFound"] as? Long)?.toInt() ?: 0,
                epicFound              = (map["epicFound"] as? Long)?.toInt() ?: 0,
                legendaryFound         = (map["legendaryFound"] as? Long)?.toInt() ?: 0,
                gymsVisited            = (map["gymsVisited"] as? Long)?.toInt() ?: 0,
                gymTrainings           = (map["gymTrainings"] as? Long)?.toInt() ?: 0,
                strength               = (map["strength"] as? Long)?.toInt() ?: 0,
                energy                 = (map["energy"] as? Long)?.toInt() ?: 100,
                createdAt              = (map["createdAt"] as? Long) ?: System.currentTimeMillis(),
                lastSeen               = (map["lastSeen"] as? Long) ?: System.currentTimeMillis(),
                isGoogleUser           = (map["isGoogleUser"] as? Boolean) ?: false,
                hasPlayedOutdoor       = (map["hasPlayedOutdoor"] as? Boolean) ?: false,
                firebaseUid            = map["firebaseUid"] as? String ?: "",
                // Gamification
                gems                   = (map["gems"] as? Long)?.toInt() ?: 0,
                teamId                 = map["teamId"] as? String ?: "",
                weeklyXp               = (map["weeklyXp"] as? Long) ?: 0L,
                weeklyEggsFound        = (map["weeklyEggsFound"] as? Long)?.toInt() ?: 0,
                lastWeekResetMs        = (map["lastWeekResetMs"] as? Long) ?: 0L,
                equippedAvatarFrameId  = map["equippedAvatarFrameId"] as? String ?: "frame_default",
                equippedTitleId        = map["equippedTitleId"] as? String ?: "title_default",
                equippedEggSkinId      = map["equippedEggSkinId"] as? String ?: "skin_default",
                equippedMapThemeId     = map["equippedMapThemeId"] as? String ?: "theme_default",
                totalLoginDays         = (map["totalLoginDays"] as? Long)?.toInt() ?: 0,
                lastLoginDate          = (map["lastLoginDate"] as? Long) ?: 0L,
                // Character & Buddy
                playerCharacterId      = map["playerCharacterId"] as? String ?: "",
                characterChangesCount  = (map["characterChangesCount"] as? Long)?.toInt() ?: 0,
                buddyCreatureId        = map["buddyCreatureId"] as? String ?: "",
                playerGender           = map["playerGender"] as? String ?: "",
                genderChangesCount     = (map["genderChangesCount"] as? Number)?.toInt() ?: 0,
                genderChosenAt         = (map["genderChosenAt"] as? Number)?.toLong() ?: 0L,
                equippedAccessories = map["equippedAccessories"] as? String ?: "",
                country = map["country"] as? String ?: "", city = map["city"] as? String ?: "",
                birthYear = (map["birthYear"] as? Long)?.toInt() ?: 0,
                isMinor = (map["isMinor"] as? Boolean) ?: false, profileCompleted = (map["profileCompleted"] as? Boolean) ?: false
            )
        }

        fun generateId(name: String): String {
            val clean = name.lowercase().replace(Regex("[^a-z0-9]"), "")
            return "p_${clean}_${System.currentTimeMillis() % 100000}"
        }
    }
}
