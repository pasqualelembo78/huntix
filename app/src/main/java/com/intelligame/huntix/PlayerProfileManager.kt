package com.intelligame.huntix

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.intelligame.huntix.managers.SavedManager
import com.google.firebase.firestore.SetOptions

/**
 * PlayerProfileManager — gestisce i profili giocatori su Firestore.
 *
 * Persistente per sempre: il profilo non scade mai.
 * Tutti i giocatori possono vedere il livello e il potere degli altri.
 *
 * Firestore collections:
 *   players/{playerId}     → profilo completo
 *   leaderboard/{playerId} → snapshot veloce per la classifica
 *
 * La classifica mondiale mostra SOLO:
 *  - Utenti autenticati con Google (isGoogleUser = true)
 *  - Che hanno completato almeno una caccia outdoor (hasPlayedOutdoor = true)
 *
 * Il playerId corrisponde all'UID Firebase Auth per garantire unicità:
 * lo stesso account Google non compare mai due volte.
 */
object PlayerProfileManager {

    private const val TAG         = "PlayerProfileManager"
    private const val PREF_FILE   = "world_game_prefs"
    private const val KEY_ID      = "world_player_id"
    private const val KEY_NAME    = "world_player_name"
    private const val COL_PLAYERS = "players"
    private const val COL_LEADER  = "leaderboard"

    private val db = FirebaseFirestore.getInstance()

    // ─── Profilo locale (cache) ──────────────────────────────────
    private var _myProfile: PlayerProfile? = null
    val myProfile: PlayerProfile? get() = _myProfile

    // ─── Persistenza metodo di login ────────────────────────────
    private const val LOGIN_PREF_FILE = "login_prefs"
    private const val KEY_METHOD      = "method"
    private const val KEY_LOGIN_NAME  = "name"
    private const val KEY_UID         = "uid"
    private const val KEY_IS_GOOGLE   = "isGoogleUser"

    /**
     * Salva il metodo di login scelto dall'utente, così ai prossimi avvii
     * possiamo effettuare l'accesso automatico (es. Google) senza ridomandarlo.
     */
    fun saveLoginMethod(
        context: Context,
        method: String,
        name: String,
        uid: String = "",
        isGoogleUser: Boolean = false
    ) {
        context.getSharedPreferences(LOGIN_PREF_FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_METHOD, method)
            .putString(KEY_LOGIN_NAME, name)
            .putString(KEY_UID, uid)
            .putBoolean(KEY_IS_GOOGLE, isGoogleUser)
            .apply()
    }

    /**
     * Ritorna (metodo, nome, uid) salvati, oppure null se è il primo avvio.
     * metodo ∈ { google, facebook, github, email, guest, local }.
     */
    fun getLoginMethod(context: Context): Triple<String, String, String>? {
        val p = context.getSharedPreferences(LOGIN_PREF_FILE, Context.MODE_PRIVATE)
        val method = p.getString(KEY_METHOD, null) ?: return null
        return Triple(method, p.getString(KEY_LOGIN_NAME, "") ?: "", p.getString(KEY_UID, "") ?: "")
    }

    fun isGoogleLogin(context: Context): Boolean =
        context.getSharedPreferences(LOGIN_PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_GOOGLE, false)

    fun clearLoginMethod(context: Context) {
        context.getSharedPreferences(LOGIN_PREF_FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ─── Init / get-or-create ────────────────────────────────────

    /**
     * Carica o crea il profilo del giocatore locale.
     *
     * @param firebaseUid   UID Firebase Auth (stringa vuota = guest/anonimo)
     * @param isGoogleUser  true se loggato con Google
     */
    fun initMyProfile(
        context:       Context,
        name:          String,
        firebaseUid:   String   = "",
        isGoogleUser:  Boolean  = false,
        onReady:       (profile: PlayerProfile) -> Unit,
        onError:       (msg: String) -> Unit
    ) {
        val prefs     = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

        // Se abbiamo un UID Firebase reale, lo usiamo come playerId univoco
        val playerId  = if (firebaseUid.isNotBlank()) firebaseUid
                        else prefs.getString(KEY_ID, null) ?: PlayerProfile.generateId(name)

        // Carica da Firestore (profilo già esistente)
        loadProfile(playerId, onSuccess = { profile ->
            // Aggiorna nome/provider se necessario
            // Fix bug: usa SEMPRE il nome da Firebase Auth se non è vuoto
            val updated = profile.copy(
                name         = name.ifBlank { profile.name },
                isGoogleUser = isGoogleUser || profile.isGoogleUser,
                firebaseUid  = firebaseUid.ifBlank { profile.firebaseUid },
                lastSeen     = System.currentTimeMillis()
            )
            _myProfile = updated
            prefs.edit().putString(KEY_ID, playerId).putString(KEY_NAME, updated.name).apply()
            // Salva aggiornamenti se ci sono differenze
            if (updated != profile) saveProfile(updated)
            onReady(updated)
        }, onNotFound = {
            // Nuovo profilo
            createProfile(
                name         = name.ifBlank { "Giocatore" },
                id           = playerId,
                isGoogleUser = isGoogleUser,
                firebaseUid  = firebaseUid,
                prefs        = prefs,
                onReady      = onReady,
                onError      = onError
            )
        }, onError = onError)
    }

    private fun createProfile(
        name:         String,
        id:           String,
        isGoogleUser: Boolean,
        firebaseUid:  String,
        prefs:        SharedPreferences,
        onReady:      (PlayerProfile) -> Unit,
        onError:      (String) -> Unit
    ) {
        val profile = PlayerProfile(
            playerId     = id,
            name         = name,
            isGoogleUser = isGoogleUser,
            firebaseUid  = firebaseUid
        )
        db.collection(COL_PLAYERS).document(id)
            .set(profile.toFirestore())
            .addOnSuccessListener {
                prefs.edit().putString(KEY_ID, id).putString(KEY_NAME, name).apply()
                _myProfile = profile
                if (isGoogleUser) updateLeaderboard(profile)
                Log.d(TAG, "Profilo creato: $id")
                onReady(profile)
            }
            .addOnFailureListener { e -> onError(e.message ?: "Errore creazione profilo") }
    }

    /**
     * Login 100% locale (offline, nessun Firebase).
     * Salva il profilo solo in SharedPreferences. L'app funziona senza rete.
     * Usa un ID locale stabile (generateId) così i dati persistono tra sessioni.
     */
    fun initLocalProfile(
        context: Context,
        name:    String,
        onReady: (PlayerProfile) -> Unit
    ) {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val id    = prefs.getString(KEY_ID, null) ?: PlayerProfile.generateId(name)
        val profile = PlayerProfile(
            playerId     = id,
            name         = name.ifBlank { "Cacciatore Locale" },
            isGoogleUser = false,
            firebaseUid  = ""  // nessun UID Firebase: profilo locale
        )
        _myProfile = profile
        prefs.edit().putString(KEY_ID, id).putString(KEY_NAME, profile.name).apply()
        Log.d(TAG, "Profilo locale creato: $id")
        onReady(profile)
    }

    private fun loadProfile(
        playerId:   String,
        onSuccess:  (PlayerProfile) -> Unit,
        onNotFound: () -> Unit,
        onError:    (String) -> Unit
    ) {
        db.collection(COL_PLAYERS).document(playerId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { onNotFound(); return@addOnSuccessListener }
                val profile = PlayerProfile.fromFirestore(doc.data ?: emptyMap())
                if (profile == null) { onNotFound(); return@addOnSuccessListener }
                _myProfile = profile
                onSuccess(profile)
            }
            .addOnFailureListener { e -> onError(e.message ?: "Errore caricamento profilo") }
    }

    // ─── Aggiornamento dopo cattura uovo ─────────────────────────

    fun recordEggCatch(rarity: EggRarity, onComplete: (newProfile: PlayerProfile) -> Unit) {
        val profile = _myProfile ?: return
        profile.addEggReward(rarity)
        saveProfile(profile) { onComplete(profile) }
    }

    fun recordTraining(powerGained: Long, xpGained: Long, onComplete: (PlayerProfile) -> Unit) {
        val profile = _myProfile ?: return
        profile.addTrainingReward(powerGained, xpGained)
        saveProfile(profile) { onComplete(profile) }
    }

    // ─── Marca outdoor completato ─────────────────────────────────

    /**
     * Chiama questa funzione al termine di ogni caccia outdoor.
     * Aggiorna il flag hasPlayedOutdoor per abilitare la presenza in classifica.
     */
    fun markHasPlayedOutdoor() {
        val profile = _myProfile ?: return
        if (profile.hasPlayedOutdoor) return  // già marcato
        profile.hasPlayedOutdoor = true
        saveProfile(profile)
    }

    // ─── Aggiorna nome utente ─────────────────────────────────────

    /**
     * Aggiorna il nome visualizzato del giocatore su Firestore e in cache locale.
     */
    fun updatePlayerName(newName: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        val profile = _myProfile ?: run { onError("Profilo non caricato"); return }
        if (newName.isBlank()) { onError("Il nome non può essere vuoto"); return }
        profile.name = newName
        db.collection(COL_PLAYERS).document(profile.playerId)
            .update("name", newName)
            .addOnSuccessListener {
                _myProfile = profile
                if (profile.isGoogleUser) updateLeaderboard(profile)
                onComplete()
            }
            .addOnFailureListener { e -> onError(e.message ?: "Errore aggiornamento nome") }
    }

    /**
     * Ricarica il profilo dal cache locale o da Firestore.
     * Utile per aggiornare l'UI dopo partite o mini giochi.
     */
    fun loadMyProfile(ctx: Context, onComplete: ((PlayerProfile?) -> Unit)? = null) {
        val id = _myProfile?.playerId
            ?: ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(KEY_ID, null)
            ?: run { onComplete?.invoke(null); return }
        loadProfile(id, onSuccess = { profile ->
            onComplete?.invoke(profile)
        }, onNotFound = {
            onComplete?.invoke(_myProfile)
        }, onError = {
            onComplete?.invoke(_myProfile)
        })
    }

    /** Public method to persist current _myProfile changes to Firestore */
    fun persistMyProfile(onComplete: (() -> Unit)? = null) {
        val profile = _myProfile ?: return
        saveProfile(profile, onComplete)
    }

    /** Public overload to save a specific profile (updates cache) */
    @Suppress("UNUSED_PARAMETER")
    fun saveProfile(ctx: android.content.Context, profile: PlayerProfile) {
        _myProfile = profile
        saveProfile(profile)
    }

    private fun saveProfile(profile: PlayerProfile, onComplete: (() -> Unit)? = null) {
        db.collection(COL_PLAYERS).document(profile.playerId)
            .set(profile.toFirestore(), SetOptions.merge())
            .addOnSuccessListener {
                if (profile.isGoogleUser) updateLeaderboard(profile)
                onComplete?.invoke()
            }
            .addOnFailureListener { e -> Log.e(TAG, "Errore salvataggio: ${e.message}") }
    }

    private fun updateLeaderboard(profile: PlayerProfile) {
        // Solo utenti Google vengono inseriti in classifica mondiale
        if (!profile.isGoogleUser) return
        db.collection(COL_LEADER).document(profile.playerId).set(
            mapOf(
                "playerId"         to profile.playerId,
                "name"             to profile.name,
                "level"            to profile.level,
                "power"            to profile.power,
                "xp"               to profile.xp,
                "eggsFound"        to profile.eggsFound,
                "title"            to profile.title,
                "lastSeen"         to profile.lastSeen,
                "isGoogleUser"     to profile.isGoogleUser,
                "hasPlayedOutdoor" to profile.hasPlayedOutdoor
            ), SetOptions.merge()
        )
    }

    // ─── Leaderboard ─────────────────────────────────────────────

    data class LeaderboardEntry(
        val playerId: String = "",
        val name:     String = "",
        val level:    Int    = 1,
        val power:    Long   = 0L,
        val xp:       Long   = 0L,
        val title:    String = "",
        val eggsFound:Int    = 0
    )

    /**
     * Carica i top 50 giocatori per potere.
     * Mostra SOLO utenti Google che hanno giocato outdoor almeno una volta.
     */
    fun getLeaderboard(onResult: (List<LeaderboardEntry>) -> Unit, onError: (String) -> Unit) {
        db.collection(COL_LEADER)
            .whereEqualTo("isGoogleUser", true)
            .whereEqualTo("hasPlayedOutdoor", true)
            .orderBy("power", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    LeaderboardEntry(
                        playerId  = d["playerId"] as? String ?: return@mapNotNull null,
                        name      = d["name"]     as? String ?: "?",
                        level     = (d["level"]   as? Long)?.toInt() ?: 1,
                        power     = d["power"]    as? Long ?: 0L,
                        xp        = d["xp"]       as? Long ?: 0L,
                        title     = d["title"]    as? String ?: "",
                        eggsFound = (d["eggsFound"] as? Long)?.toInt() ?: 0
                    )
                }
                onResult(list)
            }
            .addOnFailureListener { e -> onError(e.message ?: "Errore leaderboard") }
    }

    // ─── Profilo di un altro giocatore ───────────────────────────

    fun getPlayerProfile(playerId: String, onResult: (PlayerProfile?) -> Unit) {
        db.collection(COL_PLAYERS).document(playerId).get()
            .addOnSuccessListener { doc ->
                onResult(if (doc.exists()) PlayerProfile.fromFirestore(doc.data ?: emptyMap()) else null)
            }
            .addOnFailureListener { onResult(null) }
    }

    // ─── Eliminazione profilo ─────────────────────────────────────

    fun deleteMyProfile(context: Context, onComplete: () -> Unit, onError: ((String) -> Unit)? = null) {
        val id = _myProfile?.playerId ?: run { onError?.invoke("Nessun profilo da eliminare"); return }
        // 1. Elimina da Firestore
        db.collection(COL_PLAYERS).document(id).delete()
        db.collection(COL_LEADER).document(id).delete()
        // 2. Elimina account Firebase Auth
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.delete()
        // 3. Pulisci TUTTE le SharedPreferences (lista completa di tutti i file usati nel gioco)
        val allPrefs = listOf(
            // Profilo
            PREF_FILE, "world_game_prefs",
            // Schiusura e Mining (ENTRAMBE le versioni del manager)
            "hatching_manager_prefs", "hatching_v1", "gift_eggs_v1",
            // Inventario uova
            "egg_inventory_prefs",
            // Sorprese / Borsa
            "surprise_inventory_v1",
            // Buddy
            "buddy_manager_v1",
            // Camminata
            "walking_reward_prefs", "walking_rewards_v1",
            // POI
            "poi_cooldown_prefs",
            // GameData
            "easter_hunt_prefs",
            // Shop
            "shop_effects_prefs",
            // Upgrade chance
            "upgrade_chance_prefs",
            // Weather/zona
            "weather_zone_prefs_v1",
            // Minigiochi
            "minigames_v1", "mini_games_v1",
            // Badge e locazioni
            "location_badges_v1",
            // Daily spin, quest, team, abilità, indoor
            "daily_spin_v1", "quest_prefs_v1", "team_prefs_v1",
            "ability_prefs_v1", "indoor_session_v1",
            // Login
            "login_prefs"
        )
        allPrefs.forEach { prefName ->
            context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().apply()
        }
        _myProfile = null
        Log.d(TAG, "Profilo eliminato completamente: $id")
        onComplete()
    }

    // ─── Personaggio Player ───────────────────────────────────────

    /** Costo cambio personaggio: prime 2 volte gratis, poi 500 MVC */
    fun getCharacterChangeCost(): Double {
        val changes = _myProfile?.characterChangesCount ?: 0
        return if (changes < 2) 0.0 else 500.0
    }

    /** Ritorna true se il cambio personaggio è gratuito */
    fun canChangeCharacterFree(): Boolean = getCharacterChangeCost() == 0.0

    fun setPlayerCharacter(ctx: Context, characterId: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        val p = _myProfile ?: run { onError("Profilo non caricato"); return }
        val cost = getCharacterChangeCost()
        if (cost > 0) {
            val ok = SavedManager.spendMvc(ctx, cost)
            if (!ok) { onError("MVC insufficienti! Servono ${cost.toInt()} MVC"); return }
        }
        p.playerCharacterId = characterId
        p.characterChangesCount++
        saveProfile(p) { onComplete() }
    }

    // ─── Buddy Creature ───────────────────────────────────────────

    fun setBuddyCreature(creatureId: String, onComplete: (() -> Unit)? = null) {
        val p = _myProfile ?: return
        p.buddyCreatureId = creatureId
        saveProfile(p) { onComplete?.invoke() }
    }

    /**
     * Aggiunge XP guadagnati camminando al profilo corrente.
     * Sincronizza su Firestore.
     */
    fun addGymStrength(strengthGained: Int, xpGained: Long, onComplete: ((PlayerProfile) -> Unit)? = null) {
        val p = _myProfile ?: return
        p.strength += strengthGained
        p.xp       += xpGained
        p.gymTrainings++
        p.lastSeen  = System.currentTimeMillis()
        saveProfile(p) { onComplete?.invoke(p) }
    }

    fun restoreEnergy(amount: Int, onComplete: ((PlayerProfile) -> Unit)? = null) {
        val p = _myProfile ?: return
        p.energy   = (p.energy + amount).coerceAtMost(100)
        p.lastSeen = System.currentTimeMillis()
        saveProfile(p) { onComplete?.invoke(p) }
    }

    fun addWalkingXp(xpEarned: Long, onComplete: ((PlayerProfile) -> Unit)? = null) {
        val profile = _myProfile ?: return
        if (xpEarned <= 0) return
        profile.xp      += xpEarned
        profile.lastSeen = System.currentTimeMillis()
        _myProfile       = profile
        saveProfile(profile) { onComplete?.invoke(profile) }
    }
}
