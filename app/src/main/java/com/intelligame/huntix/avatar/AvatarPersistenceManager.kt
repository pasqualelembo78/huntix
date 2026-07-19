package com.intelligame.huntix.avatar

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONObject

/**
 * AvatarPersistenceManager — Doppia persistenza OBBLIGATORIA (locale + cloud).
 *
 * ═══ LOCALE ═══
 *  SharedPreferences "avatar_prefs":
 *    - rpm_avatar_url       → URL originale RPM
 *    - rpm_avatar_id        → ID avatar RPM
 *    - rpm_avatar_hash      → SHA-256 del file GLB
 *    - rpm_avatar_size      → dimensione in bytes
 *    - rpm_avatar_version   → versione incrementale
 *    - last_sync_timestamp  → ultimo sync cloud riuscito
 *
 *  File interni (gestiti da AvatarManager):
 *    - rpm_avatars/player_avatar.glb  → modello 3D
 *    - rpm_avatars/avatar_thumbnail.png → thumbnail 2D
 *
 *  File JSON accessori (gestiti da AccessoryManager):
 *    - rpm_avatars/accessories_config.json → configurazione equipaggiamento
 *
 * ═══ CLOUD (Firestore) ═══
 *  players/{uid}/avatar/config:
 *    - rpmAvatarUrl     → URL RPM (per ri-download su nuovo device)
 *    - rpmAvatarId      → ID avatar RPM
 *    - avatarHash       → hash per verificare se è cambiato
 *    - avatarVersion    → versione incrementale
 *    - accessories      → mappa accessori equipaggiati
 *    - lastModifiedMs   → timestamp ultima modifica
 *    - playerProgression → dati progressione sincronizzati
 */
object AvatarPersistenceManager {

    private const val TAG = "AvatarPersistence"
    private const val PREF_FILE = "avatar_prefs"

    // Chiavi SharedPreferences
    private const val KEY_AVATAR_URL     = "rpm_avatar_url"
    private const val KEY_AVATAR_ID      = "rpm_avatar_id"
    private const val KEY_AVATAR_HASH    = "rpm_avatar_hash"
    private const val KEY_AVATAR_SIZE    = "rpm_avatar_size"
    private const val KEY_AVATAR_VERSION = "rpm_avatar_version"
    private const val KEY_LAST_SYNC      = "last_sync_timestamp"
    private const val KEY_ACCESSORIES    = "accessories_json"
    private const val KEY_NEEDS_SYNC     = "needs_cloud_sync"

    // Firestore paths
    private const val COL_PLAYERS = "players"
    private const val DOC_AVATAR  = "avatar"
    private const val DOC_CONFIG  = "config"

    private val db get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    // ═══════════════════════════════════════════════════════════════
    // LOCALE — Lettura
    // ═══════════════════════════════════════════════════════════════

    fun getLocalAvatarUrl(ctx: Context): String =
        prefs(ctx).getString(KEY_AVATAR_URL, "") ?: ""

    fun getLocalAvatarId(ctx: Context): String =
        prefs(ctx).getString(KEY_AVATAR_ID, "") ?: ""

    fun getLocalAvatarHash(ctx: Context): String =
        prefs(ctx).getString(KEY_AVATAR_HASH, "") ?: ""

    fun getLocalAvatarVersion(ctx: Context): Int =
        prefs(ctx).getInt(KEY_AVATAR_VERSION, 0)

    fun getLastSyncTimestamp(ctx: Context): Long =
        prefs(ctx).getLong(KEY_LAST_SYNC, 0L)

    fun needsCloudSync(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_NEEDS_SYNC, false)

    fun hasLocalAvatar(ctx: Context): Boolean =
        getLocalAvatarUrl(ctx).isNotBlank()

    // ═══════════════════════════════════════════════════════════════
    // LOCALE — Scrittura
    // ═══════════════════════════════════════════════════════════════

    /** Salva metadati avatar dopo download */
    fun saveLocalAvatarMeta(
        context: Context,
        url: String,
        hash: String,
        sizeBytes: Long,
        avatarId: String = ""
    ) {
        val currentVersion = getLocalAvatarVersion(context)
        prefs(context).edit()
            .putString(KEY_AVATAR_URL, url)
            .putString(KEY_AVATAR_HASH, hash)
            .putLong(KEY_AVATAR_SIZE, sizeBytes)
            .putInt(KEY_AVATAR_VERSION, currentVersion + 1)
            .putBoolean(KEY_NEEDS_SYNC, true)  // Marca per sync cloud
            .apply {
                if (avatarId.isNotBlank()) putString(KEY_AVATAR_ID, avatarId)
            }
            .apply()

        Log.d(TAG, "Meta avatar locale salvato: v${currentVersion + 1}, hash=$hash")
    }

    /** Salva l'ID avatar RPM separatamente (dalla WebView callback) */
    fun saveAvatarId(context: Context, avatarId: String) {
        prefs(context).edit()
            .putString(KEY_AVATAR_ID, avatarId)
            .apply()
    }

    /** Salva configurazione accessori in locale (JSON) */
    fun saveLocalAccessories(context: Context, accessoriesJson: String) {
        prefs(context).edit()
            .putString(KEY_ACCESSORIES, accessoriesJson)
            .putBoolean(KEY_NEEDS_SYNC, true)
            .apply()
        Log.d(TAG, "Accessori locali salvati")
    }

    /** Carica configurazione accessori da locale */
    fun getLocalAccessories(context: Context): String =
        prefs(context).getString(KEY_ACCESSORIES, "{}") ?: "{}"

    /** Pulisci tutti i metadati locali */
    fun clearLocalAvatarMeta(context: Context) {
        prefs(context).edit().clear().apply()
        Log.d(TAG, "Metadati avatar locale eliminati")
    }

    fun markSynced(context: Context) {
        prefs(context).edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .putBoolean(KEY_NEEDS_SYNC, false)
            .apply()
    }

    // ═══════════════════════════════════════════════════════════════
    // CLOUD — Salvataggio su Firestore
    // ═══════════════════════════════════════════════════════════════

    /**
     * Salva configurazione avatar su Firestore (background).
     * Include: URL RPM, ID, hash, accessori, progressione.
     */
    fun saveToCloud(
        context: Context,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (uid.isBlank()) {
            onError("Utente non autenticato")
            return
        }

        val data = hashMapOf(
            "rpmAvatarUrl"    to getLocalAvatarUrl(context),
            "rpmAvatarId"     to getLocalAvatarId(context),
            "avatarHash"      to getLocalAvatarHash(context),
            "avatarVersion"   to getLocalAvatarVersion(context),
            "accessories"     to getLocalAccessories(context),
            "lastModifiedMs"  to System.currentTimeMillis(),
            "userId"          to uid
        )

        db.collection(COL_PLAYERS)
            .document(uid)
            .collection(DOC_AVATAR)
            .document(DOC_CONFIG)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                markSynced(context)
                Log.d(TAG, "Avatar sincronizzato su cloud")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Errore sync cloud avatar", e)
                onError(e.message ?: "Errore sconosciuto")
            }
    }

    // ═══════════════════════════════════════════════════════════════
    // CLOUD — Caricamento da Firestore
    // ═══════════════════════════════════════════════════════════════

    /**
     * Carica configurazione avatar dal cloud.
     * Usato all'avvio o al cambio dispositivo.
     *
     * @return AvatarCloudData con tutti i dati, o null se non esiste
     */
    fun loadFromCloud(
        onSuccess: (AvatarCloudData) -> Unit,
        onNotFound: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (uid.isBlank()) {
            onError("Utente non autenticato")
            return
        }

        db.collection(COL_PLAYERS)
            .document(uid)
            .collection(DOC_AVATAR)
            .document(DOC_CONFIG)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val data = AvatarCloudData(
                        rpmAvatarUrl   = doc.getString("rpmAvatarUrl") ?: "",
                        rpmAvatarId    = doc.getString("rpmAvatarId") ?: "",
                        avatarHash     = doc.getString("avatarHash") ?: "",
                        avatarVersion  = (doc.getLong("avatarVersion") ?: 0L).toInt(),
                        accessoriesJson = doc.getString("accessories") ?: "{}",
                        lastModifiedMs = doc.getLong("lastModifiedMs") ?: 0L,
                        userId         = doc.getString("userId") ?: ""
                    )
                    Log.d(TAG, "Dati avatar caricati dal cloud: v${data.avatarVersion}")
                    onSuccess(data)
                } else {
                    Log.d(TAG, "Nessun avatar trovato nel cloud")
                    onNotFound()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Errore caricamento cloud avatar", e)
                onError(e.message ?: "Errore sconosciuto")
            }
    }

    /**
     * Applica i dati cloud al salvataggio locale.
     * NON scarica il file GLB — quello lo fa AvatarManager.
     */
    fun applyCloudDataLocally(context: Context, cloudData: AvatarCloudData) {
        prefs(context).edit()
            .putString(KEY_AVATAR_URL, cloudData.rpmAvatarUrl)
            .putString(KEY_AVATAR_ID, cloudData.rpmAvatarId)
            .putString(KEY_AVATAR_HASH, cloudData.avatarHash)
            .putInt(KEY_AVATAR_VERSION, cloudData.avatarVersion)
            .putString(KEY_ACCESSORIES, cloudData.accessoriesJson)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .putBoolean(KEY_NEEDS_SYNC, false)
            .apply()

        Log.d(TAG, "Dati cloud applicati localmente: v${cloudData.avatarVersion}")
    }
}

/**
 * Dati avatar dal cloud Firestore.
 */
data class AvatarCloudData(
    val rpmAvatarUrl: String = "",
    val rpmAvatarId: String = "",
    val avatarHash: String = "",
    val avatarVersion: Int = 0,
    val accessoriesJson: String = "{}",
    val lastModifiedMs: Long = 0L,
    val userId: String = ""
)
