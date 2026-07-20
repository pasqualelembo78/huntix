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
