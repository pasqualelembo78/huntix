package com.intelligame.huntix.avatar

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * AvatarManager — Gestore centrale del ciclo di vita avatar RPM.
 *
 * Responsabilità:
 *  1. Download GLB da Ready Player Me (con parametri ottimizzazione)
 *  2. Cache locale del file GLB in internal storage
 *  3. Generazione thumbnail 2D per il marker Mapbox
 *  4. Verifica se l'avatar è cambiato (hash-based)
 *  5. Pulizia avatar vecchi
 *
 * Politica di download (NO download ogni volta):
 *  - Primo login → scarica
 *  - Avatar modificato (URL cambiato) → scarica
 *  - Dati locali mancanti → scarica
 *  - Altrimenti → usa cache locale
 */
object AvatarManager {

    private const val TAG = "AvatarManager"
    private const val AVATAR_DIR = "rpm_avatars"
    private const val AVATAR_FILENAME = "player_avatar.glb"
    private const val THUMBNAIL_FILENAME = "avatar_thumbnail.png"
    private const val MAX_DOWNLOAD_SIZE_BYTES = 15 * 1024 * 1024 // 15MB max
    private const val DOWNLOAD_TIMEOUT_MS = 30_000

    // ─── Stato in-memory ─────────────────────────────────────────
    private var cachedThumbnail: Bitmap? = null
    private var isDownloading = false

    // ─── Directory helper ────────────────────────────────────────

    private fun avatarDir(context: Context): File {
        val dir = File(context.filesDir, AVATAR_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getLocalGlbFile(context: Context): File =
        File(avatarDir(context), AVATAR_FILENAME)

    fun getLocalThumbnailFile(context: Context): File =
        File(avatarDir(context), THUMBNAIL_FILENAME)

    /** Verifica se esiste un avatar scaricato localmente */
    fun hasLocalAvatar(context: Context): Boolean =
        getLocalGlbFile(context).exists() && getLocalGlbFile(context).length() > 0

    // ─── Download avatar GLB ─────────────────────────────────────

    /**
     * Scarica l'avatar GLB dall'URL RPM SOLO se necessario.
     *
     * Condizioni di download:
     *  - File locale non esiste
     *  - URL remoto diverso dall'ultimo scaricato (avatar modificato)
     *  - Force = true (utente vuole ri-scaricare)
     *
     * @return true se download riuscito o avatar già presente, false se errore
     */
    suspend fun ensureAvatarDownloaded(
        context: Context,
        remoteUrl: String,
        force: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (isDownloading) {
            Log.w(TAG, "Download già in corso, skip")
            return@withContext false
        }

        val localFile = getLocalGlbFile(context)
        val persistence = AvatarPersistenceManager

        // Controlla se serve scaricare
        val savedUrl = persistence.getLocalAvatarUrl(context)
        val savedHash = persistence.getLocalAvatarHash(context)
        val needsDownload = force
            || !localFile.exists()
            || localFile.length() == 0L
            || savedUrl != remoteUrl

        if (!needsDownload) {
            Log.d(TAG, "Avatar locale valido, skip download")
            return@withContext true
        }

        isDownloading = true
        try {
            Log.d(TAG, "Download avatar da: $remoteUrl")

            val connection = URL(remoteUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = DOWNLOAD_TIMEOUT_MS
            connection.readTimeout = DOWNLOAD_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "model/gltf-binary")

            if (connection.responseCode != 200) {
                Log.e(TAG, "Download fallito: HTTP ${connection.responseCode}")
                return@withContext false
            }

            // Download con limite dimensione
            val totalSize = connection.contentLength
            if (totalSize > MAX_DOWNLOAD_SIZE_BYTES) {
                Log.e(TAG, "Avatar troppo grande: $totalSize bytes (max $MAX_DOWNLOAD_SIZE_BYTES)")
                connection.disconnect()
                return@withContext false
            }

            // Scrivi in file temporaneo, poi rinomina (atomico)
            val tempFile = File(avatarDir(context), "avatar_download.tmp")
            var bytesRead = 0L
            val digest = MessageDigest.getInstance("SHA-256")

            connection.inputStream.buffered().use { input ->
                tempFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (input.read(buffer).also { len = it } != -1) {
                        bytesRead += len
                        if (bytesRead > MAX_DOWNLOAD_SIZE_BYTES) {
                            tempFile.delete()
                            Log.e(TAG, "Download interrotto: superato limite dimensione")
                            return@withContext false
                        }
                        output.write(buffer, 0, len)
                        digest.update(buffer, 0, len)
                    }
                }
            }
            connection.disconnect()

            // Calcola hash del file scaricato
            val fileHash = digest.digest().joinToString("") { "%02x".format(it) }

            // Rinomina atomicamente
            if (localFile.exists()) localFile.delete()
            if (!tempFile.renameTo(localFile)) {
                tempFile.copyTo(localFile, overwrite = true)
                tempFile.delete()
            }

            // Aggiorna metadati persistenza locale
            persistence.saveLocalAvatarMeta(
                context = context,
                url = remoteUrl,
                hash = fileHash,
                sizeBytes = bytesRead
            )

            Log.d(TAG, "Avatar scaricato: ${bytesRead / 1024}KB, hash=$fileHash")

            // Genera thumbnail per la mappa
            generateMapThumbnail(context)

            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Errore download avatar", e)
            return@withContext false
        } finally {
            isDownloading = false
        }
    }

    // ─── Thumbnail per marker Mapbox ─────────────────────────────

    /**
     * Genera un thumbnail 2D placeholder dell'avatar per la mappa.
     *
     * Per ora usa un placeholder disegnato; in futuro si può usare
     * un rendering offscreen con Filament o una thumbnail da RPM API.
     *
     * RPM fornisce una render API:
     *   https://models.readyplayer.me/{avatarId}.png?scene=fullbody-portrait-v1
     */
    fun generateMapThumbnail(context: Context): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Cerchio sfondo
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FF88")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, bgPaint)

        // Bordo
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, borderPaint)

        // Icona avatar stilizzata
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🧑", size / 2f, size / 2f + 20f, iconPaint)

        // Salva su disco
        val thumbFile = getLocalThumbnailFile(context)
        thumbFile.outputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
        }

        cachedThumbnail = bmp
        return bmp
    }

    /**
     * Scarica il render 2D dell'avatar da RPM API.
     * Usato come thumbnail per la mappa dopo che l'avatar è stato creato.
     */
    suspend fun downloadAvatarThumbnail(
        context: Context,
        avatarId: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val renderUrl = "https://models.readyplayer.me/$avatarId.png" +
                "?scene=fullbody-portrait-v1&size=256"

            val connection = URL(renderUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000

            if (connection.responseCode != 200) {
                Log.w(TAG, "Thumbnail RPM non disponibile: HTTP ${connection.responseCode}")
                return@withContext null
            }

            val bmp = BitmapFactory.decodeStream(connection.inputStream)
            connection.disconnect()

            if (bmp != null) {
                // Scala a 128px per il marker
                val scaled = Bitmap.createScaledBitmap(bmp, 128, 128, true)
                val thumbFile = getLocalThumbnailFile(context)
                thumbFile.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                cachedThumbnail = scaled
                return@withContext scaled
            }

            return@withContext null
        } catch (e: Exception) {
            Log.w(TAG, "Errore download thumbnail RPM", e)
            return@withContext null
        }
    }

    /** Carica il thumbnail dalla cache (memoria o disco) */
    fun getThumbnailBitmap(context: Context): Bitmap? {
        cachedThumbnail?.let { return it }

        val thumbFile = getLocalThumbnailFile(context)
        if (thumbFile.exists()) {
            cachedThumbnail = BitmapFactory.decodeFile(thumbFile.absolutePath)
            return cachedThumbnail
        }

        return null
    }

    fun getThumbnailDrawable(context: Context): BitmapDrawable? {
        val bmp = getThumbnailBitmap(context) ?: return null
        return BitmapDrawable(context.resources, bmp)
    }

    // ─── Pulizia ─────────────────────────────────────────────────

    /** Elimina tutti i file avatar locali */
    fun clearLocalAvatar(context: Context) {
        avatarDir(context).listFiles()?.forEach { it.delete() }
        cachedThumbnail = null
        AvatarPersistenceManager.clearLocalAvatarMeta(context)
        Log.d(TAG, "Avatar locale eliminato")
    }
}
