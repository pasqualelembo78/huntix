package com.intelligame.huntix.reallife

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * RealLifeClient — client HTTP per il backend Real Life (FastAPI, porta 5100).
 *
 * Endpoints usati (vedi backend/app_routes):
 *   POST /auth/local-login  -> crea/recupera utente, ritorna token
 *   POST /auth/reauth       -> rinnova token da persistent_token
 *   GET  /characters        -> lista NPC (jwt_optional, funziona anche senza auth)
 *   POST /chat              -> invia messaggio (jwt_required)
 */
object RealLifeClient {
    private const val TAG = "RealLifeClient"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // ── Auth ─────────────────────────────────────────────────────

    /** Garantisce un access token valido (login o reauth). */
    private suspend fun ensureTokens(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (RealLifeAuth.getAccessToken(context).isNotBlank()) return@withContext true
        if (RealLifeAuth.getPersistentToken(context).isNotBlank()) {
            if (reauth(context)) return@withContext true
        }
        return@withContext localLogin(context)
    }

    /** Rinnova i token usando il persistent_token. Ritorna true se ok. */
    private suspend fun reauth(context: Context): Boolean = withContext(Dispatchers.IO) {
        val ptk = RealLifeAuth.getPersistentToken(context)
        if (ptk.isBlank()) return@withContext false
        val body = gson.toJson(mapOf("persistent_token" to ptk))
        val req = Request.Builder()
            .url("${RealLifeConfig.BASE_URL}/auth/reauth")
            .post(body.toRequestBody(JSON))
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val ar = gson.fromJson(resp.body!!.string(), AuthResponse::class.java)
                if (ar.accessToken.isBlank()) return@withContext false
                RealLifeAuth.saveTokens(context, ar.accessToken, ar.refreshToken, ar.persistentToken)
                true
            }
        }.getOrDefault(false)
    }

    /** Login/anonimo: crea l'utente al primo avvio tramite local-login. */
    private suspend fun localLogin(context: Context): Boolean = withContext(Dispatchers.IO) {
        val body = gson.toJson(
            mapOf(
                "username" to RealLifeAuth.getUsername(context),
                "password" to RealLifeAuth.getPassword(context),
                "birth_date" to RealLifeConfig.DEFAULT_BIRTH_DATE
            )
        )
        val req = Request.Builder()
            .url("${RealLifeConfig.BASE_URL}/auth/local-login")
            .post(body.toRequestBody(JSON))
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "local-login fallito: HTTP ${resp.code}")
                    return@withContext false
                }
                val ar = gson.fromJson(resp.body!!.string(), AuthResponse::class.java)
                if (ar.accessToken.isBlank()) return@withContext false
                RealLifeAuth.saveTokens(context, ar.accessToken, ar.refreshToken, ar.persistentToken)
                true
            }
        }.getOrDefault(false)
    }

    // ── Characters ───────────────────────────────────────────────

    /** Recupera la lista personaggi (funziona anche senza auth). */
    suspend fun getCharacters(category: String? = null): Result<List<CharacterItem>> =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("${RealLifeConfig.BASE_URL}/characters?limit=200")
                if (!category.isNullOrBlank()) append("&category=").append(category)
            }
            val req = Request.Builder().url(url).get().build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                    }
                    val arr = gson.fromJson(resp.body!!.string(), Array<CharacterItem>::class.java)
                    Result.success(arr?.toList() ?: emptyList())
                }
            }.getOrElse { Result.failure(it) }
        }

    // ── Chat ─────────────────────────────────────────────────────

    /**
     * Invia un messaggio a un personaggio. Gestisce auth + un retry su 401.
     * @return ChatResponse o eccezione in caso di errore.
     */
    suspend fun sendMessage(
        context: Context,
        characterId: String,
        text: String,
        username: String
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        if (!ensureTokens(context)) {
            return@withContext Result.failure(Exception("Auth fallita: impossibile ottenere token"))
        }
        val payload = ChatRequest(character = characterId, text = text, username = username)
        val bodyStr = gson.toJson(payload)

        suspend fun doCall(token: String): Pair<Int, String?> {
            val req = Request.Builder()
                .url("${RealLifeConfig.BASE_URL}/chat")
                .addHeader("Authorization", "Bearer $token")
                .post(bodyStr.toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { resp ->
                val txt = resp.body?.string()
                return resp.code to txt
            }
        }

        var token = RealLifeAuth.getAccessToken(context)
        var (code, raw) = doCall(token)

        if (code == 401) {
            // Token scaduto/non valido: rinnova e riprova una volta.
            if (reauth(context)) {
                token = RealLifeAuth.getAccessToken(context)
                val r = doCall(token)
                code = r.first
                raw = r.second
            }
        }

        if (code != 200) {
            val msg = parseError(raw)
            return@withContext Result.failure(Exception(msg ?: "HTTP $code"))
        }
        runCatching {
            val cr = gson.fromJson(raw, ChatResponse::class.java)
            if (cr?.response.isNullOrBlank() && cr?.error != null) {
                return@withContext Result.failure(Exception(cr.error))
            }
            Result.success(cr ?: ChatResponse())
        }.getOrElse { Result.failure(it) }
    }

    private fun parseError(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val m = gson.fromJson(raw, Map::class.java)
            (m["message"] ?: m["error"] ?: m["detail"])?.toString()
        }.getOrNull()
    }

    // ── Fase B: mondo / bisogni / skill / mappa ──────────────

    /** Stato del mondo (data/ora/stagione/meteo). */
    suspend fun getWorldState(): Result<WorldState> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${RealLifeConfig.BASE_URL}/reallife/world").get().build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                Result.success(gson.fromJson(resp.body!!.string(), WorldState::class.java))
            }
        }.getOrElse { Result.failure(it) }
    }

    /** Bisogni Sims del personaggio per l'utente corrente. */
    suspend fun getNeeds(characterId: String, userId: String): Result<Needs> = withContext(Dispatchers.IO) {
        val url = "${RealLifeConfig.BASE_URL}/reallife/needs?character_id=$characterId&user_id=$userId"
        val req = Request.Builder().url(url).get().build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                Result.success(gson.fromJson(resp.body!!.string(), Needs::class.java))
            }
        }.getOrElse { Result.failure(it) }
    }

    /** Skill dell'utente + catalogo. */
    suspend fun getSkills(userId: String): Result<SkillsResponse> = withContext(Dispatchers.IO) {
        val url = "${RealLifeConfig.BASE_URL}/reallife/skills?user_id=$userId"
        val req = Request.Builder().url(url).get().build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                Result.success(gson.fromJson(resp.body!!.string(), SkillsResponse::class.java))
            }
        }.getOrElse { Result.failure(it) }
    }

    /** Mappa 2D della città con le posizioni degli NPC. */
    suspend fun getMap(): Result<MapState> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${RealLifeConfig.BASE_URL}/reallife/map").get().build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                Result.success(gson.fromJson(resp.body!!.string(), MapState::class.java))
            }
        }.getOrElse { Result.failure(it) }
    }

    /** Dopo una chat: ricarica bisogni + XP skill. */
    suspend fun interact(
        characterId: String,
        userId: String,
        characterTags: List<String>
    ): Result<InteractResponse> = withContext(Dispatchers.IO) {
        val body = gson.toJson(mapOf(
            "character_id" to characterId,
            "user_id" to userId,
            "character_tags" to characterTags,
            "interaction" to "chat"
        ))
        val req = Request.Builder()
            .url("${RealLifeConfig.BASE_URL}/reallife/interact")
            .post(body.toRequestBody(JSON))
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                Result.success(gson.fromJson(resp.body!!.string(), InteractResponse::class.java))
            }
        }.getOrElse { Result.failure(it) }
    }
}
