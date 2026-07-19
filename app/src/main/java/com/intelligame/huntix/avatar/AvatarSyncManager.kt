package com.intelligame.huntix.avatar

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*

/**
 * AvatarSyncManager — Sincronizzazione intelligente locale ↔ cloud.
 *
 * ═══ STRATEGIA ALL'AVVIO ═══
 *
 *  1. Se esiste salvataggio cloud → scarica e ripristina avatar + configurazione
 *  2. Se offline → usa dati locali
 *  3. Se utente cambia dispositivo → ripristino automatico tramite login Google
 *
 * ═══ STRATEGIA IN GIOCO ═══
 *
 *  Ogni modifica (nuovo oggetto, upgrade, avatar) aggiorna:
 *    - locale: IMMEDIATAMENTE
 *    - cloud: IN BACKGROUND (fire-and-forget)
 *
 * ═══ POLITICA DI RETE ═══
 *
 *  NON scaricare avatar ogni volta. Scarica SOLO se:
 *    - primo login (nessun avatar locale)
 *    - avatar modificato (hash cloud ≠ hash locale)
 *    - dati locali mancanti
 */
object AvatarSyncManager {

    private const val TAG = "AvatarSyncManager"
    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Sincronizzazione all'avvio dell'app.
     * Chiamare dopo che FirebaseAuth ha un utente loggato.
     *
     * Logica:
     *  1. Controlla se siamo online
     *  2. Se online → carica dati cloud → confronta con locale → scarica se necessario
     *  3. Se offline → usa locale silenziosamente
     *  4. Se dati pendenti locale → sincronizza verso cloud quando possibile
     */
    fun syncOnStartup(
        context: Context,
        onComplete: (SyncResult) -> Unit = {}
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Log.d(TAG, "Utente non loggato, skip sync")
            onComplete(SyncResult.NOT_LOGGED_IN)
            return
        }

        syncJob?.cancel()
        syncJob = scope.launch {
            try {
                val isOnline = isNetworkAvailable(context)
                val hasLocal = AvatarPersistenceManager.hasLocalAvatar(context)
                    && AvatarManager.hasLocalAvatar(context)

                if (!isOnline) {
                    // ── OFFLINE: usa dati locali ─────────────────────
                    Log.d(TAG, "Offline — uso dati locali (hasLocal=$hasLocal)")
                    withContext(Dispatchers.Main) {
                        onComplete(if (hasLocal) SyncResult.USED_LOCAL else SyncResult.NO_DATA)
                    }
                    return@launch
                }

                // ── ONLINE: controlla cloud ──────────────────────────
                val cloudResult = CompletableDeferred<CloudCheckResult>()

                AvatarPersistenceManager.loadFromCloud(
                    onSuccess = { cloudData ->
                        cloudResult.complete(CloudCheckResult.Found(cloudData))
                    },
                    onNotFound = {
                        cloudResult.complete(CloudCheckResult.NotFound)
                    },
                    onError = { msg ->
                        cloudResult.complete(CloudCheckResult.Error(msg))
                    }
                )

                when (val result = cloudResult.await()) {
                    is CloudCheckResult.Found -> {
                        val cloudData = result.data
                        val localHash = AvatarPersistenceManager.getLocalAvatarHash(context)
                        val localVersion = AvatarPersistenceManager.getLocalAvatarVersion(context)

                        if (!hasLocal || cloudData.avatarVersion > localVersion
                            || cloudData.avatarHash != localHash) {
                            // ── Cloud più recente → scarica ──────────────
                            Log.d(TAG, "Cloud più recente (cloud v${cloudData.avatarVersion} vs local v$localVersion)")

                            // Applica metadati cloud localmente
                            AvatarPersistenceManager.applyCloudDataLocally(context, cloudData)

                            // Scarica il file GLB se l'URL è valido
                            if (cloudData.rpmAvatarUrl.isNotBlank()) {
                                val downloaded = AvatarManager.ensureAvatarDownloaded(
                                    context = context,
                                    remoteUrl = cloudData.rpmAvatarUrl,
                                    force = true
                                )
                                // Scarica anche il thumbnail
                                if (downloaded && cloudData.rpmAvatarId.isNotBlank()) {
                                    AvatarManager.downloadAvatarThumbnail(context, cloudData.rpmAvatarId)
                                }
                            }

                            withContext(Dispatchers.Main) {
                                onComplete(SyncResult.DOWNLOADED_FROM_CLOUD)
                            }
                        } else {
                            // ── Locale aggiornato ────────────────────────
                            Log.d(TAG, "Locale aggiornato, nessun download necessario")

                            // Se abbiamo modifiche locali pendenti, sincronizza verso cloud
                            if (AvatarPersistenceManager.needsCloudSync(context)) {
                                pushLocalToCloud(context)
                            }

                            withContext(Dispatchers.Main) {
                                onComplete(SyncResult.ALREADY_SYNCED)
                            }
                        }
                    }

                    is CloudCheckResult.NotFound -> {
                        // Nessun dato cloud; se abbiamo locale, sincronizziamolo
                        if (hasLocal) {
                            Log.d(TAG, "Nessun cloud, push locale verso cloud")
                            pushLocalToCloud(context)
                            withContext(Dispatchers.Main) {
                                onComplete(SyncResult.PUSHED_TO_CLOUD)
                            }
                        } else {
                            Log.d(TAG, "Nessun avatar né locale né cloud")
                            withContext(Dispatchers.Main) {
                                onComplete(SyncResult.NO_DATA)
                            }
                        }
                    }

                    is CloudCheckResult.Error -> {
                        Log.e(TAG, "Errore cloud: ${result.message}")
                        withContext(Dispatchers.Main) {
                            onComplete(if (hasLocal) SyncResult.USED_LOCAL else SyncResult.ERROR)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore sync avvio", e)
                withContext(Dispatchers.Main) {
                    onComplete(SyncResult.ERROR)
                }
            }
        }
    }

    // ─── Push locale → cloud (background) ────────────────────────

    /**
     * Sincronizza i dati locali verso il cloud IN BACKGROUND.
     * Chiamare dopo ogni modifica locale (nuovo accessorio, avatar aggiornato, ecc.)
     */
    fun pushLocalToCloud(context: Context) {
        scope.launch {
            try {
                AvatarPersistenceManager.saveToCloud(
                    context = context,
                    onSuccess = {
                        Log.d(TAG, "Push cloud completato")
                    },
                    onError = { msg ->
                        Log.w(TAG, "Push cloud fallito: $msg (riproverà al prossimo avvio)")
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Errore push cloud", e)
            }
        }
    }

    /**
     * Notifica una modifica locale e aggiorna sia locale che cloud.
     * Usato come entry point singolo per tutte le modifiche avatar/accessori.
     */
    fun notifyChange(context: Context) {
        if (isNetworkAvailable(context)) {
            pushLocalToCloud(context)
        }
        // Se offline, il flag needsSync resta true e sincronizzerà al prossimo avvio
    }

    // ─── Utility rete ────────────────────────────────────────────

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun cancelSync() {
        syncJob?.cancel()
    }

    // ─── Modelli risultato ───────────────────────────────────────

    enum class SyncResult {
        ALREADY_SYNCED,
        DOWNLOADED_FROM_CLOUD,
        PUSHED_TO_CLOUD,
        USED_LOCAL,
        NO_DATA,
        NOT_LOGGED_IN,
        ERROR
    }

    private sealed class CloudCheckResult {
        data class Found(val data: AvatarCloudData) : CloudCheckResult()
        object NotFound : CloudCheckResult()
        data class Error(val message: String) : CloudCheckResult()
    }
}
