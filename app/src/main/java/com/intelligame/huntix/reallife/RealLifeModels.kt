package com.intelligame.huntix.reallife

import com.google.gson.annotations.SerializedName

/** Personaggio/NPC restituito da GET /characters. */
data class CharacterItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("age") val age: Int = 0,
    @SerializedName("role") val role: String? = null,
    @SerializedName("category") val category: String = "",
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("tags") val tags: List<String>? = null,
    @SerializedName("conversations") val conversations: Int = 0,
    @SerializedName("intimacy") val intimacy: Int? = null,
    @SerializedName("intimacy_label") val intimacyLabel: String? = null
)

/** Risposta di GET /characters (lista). */
data class CharactersResponse(
    @SerializedName("characters") val characters: List<CharacterItem>? = null
)

/** Categoria di personaggi (da categories.json lato backend). */
data class CategoryItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("mvc_cost") val mvcCost: Int = 0,
    @SerializedName("emoji") val emoji: String? = null
)

/** Corpo della richiesta POST /chat. */
data class ChatRequest(
    @SerializedName("character") val character: String,
    @SerializedName("text") val text: String,
    @SerializedName("username") val username: String = "Utente",
    @SerializedName("client_storage") val clientStorage: Boolean = true
)

/** Risposta di POST /chat. */
data class ChatResponse(
    @SerializedName("response") val response: String = "",
    @SerializedName("emotion") val emotion: String? = null,
    @SerializedName("emotion_intensity") val emotionIntensity: Double? = null,
    @SerializedName("ai_provider") val aiProvider: String? = null,
    @SerializedName("ai_model") val aiModel: String? = null,
    @SerializedName("character_id") val characterId: String? = null,
    @SerializedName("character_name") val characterName: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

/** Risposta login (POST /auth/local-login). */
data class AuthResponse(
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("refresh_token") val refreshToken: String = "",
    @SerializedName("persistent_token") val persistentToken: String = "",
    @SerializedName("user") val user: AuthUser? = null
)

data class AuthUser(
    @SerializedName("id") val id: String = "",
    @SerializedName("username") val username: String = "",
    @SerializedName("role") val role: String = "",
    @SerializedName("email") val email: String? = null
)

// ── Fase B: mondo vivo ───────────────────────────────────────
data class WorldState(
    @SerializedName("date") val date: String = "",
    @SerializedName("time") val time: String = "",
    @SerializedName("season") val season: String = "",
    @SerializedName("weather") val weather: String = ""
)

// ── Fase B: bisogni stile Sims ──────────────────────────────
data class Needs(
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("character_id") val characterId: String = "",
    @SerializedName("hunger") val hunger: Double = 0.0,
    @SerializedName("sleep") val sleep: Double = 0.0,
    @SerializedName("hygiene") val hygiene: Double = 0.0,
    @SerializedName("social") val social: Double = 0.0,
    @SerializedName("fun") val funLevel: Double = 0.0
)

// ── Fase B: skill ───────────────────────────────────────────
data class Skill(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("desc") val desc: String = "",
    @SerializedName("tag") val tag: String = "",
    @SerializedName("level") val level: Int = 0,
    @SerializedName("xp") val xp: Int = 0,
    @SerializedName("unlocked") val unlocked: Boolean = false
)
data class SkillsResponse(
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("skills") val skills: List<Skill> = emptyList()
)

// ── Fase B: mappa 2D ────────────────────────────────────────
data class MapNode(
    @SerializedName("id") val id: String = "",
    @SerializedName("x") val x: Double = 0.0,
    @SerializedName("y") val y: Double = 0.0,
    @SerializedName("zone") val zone: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("avatar") val avatar: String = "",
    @SerializedName("category") val category: String = ""
)
data class MapState(
    @SerializedName("width") val width: Int = 100,
    @SerializedName("height") val height: Int = 100,
    @SerializedName("nodes") val nodes: List<MapNode> = emptyList()
)

// ── Fase B: risposta interact ───────────────────────────────
data class InteractResponse(
    @SerializedName("needs") val needs: Needs? = null,
    @SerializedName("skills_leveled_up") val skillsLeveledUp: List<String> = emptyList(),
    @SerializedName("skills") val skills: SkillsResponse? = null
)
