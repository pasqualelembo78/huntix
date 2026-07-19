package com.intelligame.huntix.gamification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * CustomizationManager — Avatar frame, titoli, skin uova, temi mappa.
 *
 * Firestore: players/{uid}/customization/equipped
 *   - avatarFrameId, titleId, eggSkinId, mapThemeId, unlockedIds[]
 */
object CustomizationManager {

    private val db get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ─── Definizioni ─────────────────────────────────────────────

    data class CosmeticItem(
        val id: String,
        val name: String,
        val emoji: String,
        val description: String,
        val category: Category,
        val unlockCondition: UnlockCondition,
        val rarityColorHex: String = "#808080"
    )

    enum class Category { AVATAR_FRAME, TITLE, EGG_SKIN, MAP_THEME }

    sealed class UnlockCondition {
        object Default : UnlockCondition()
        data class Level(val level: Int) : UnlockCondition()
        data class EggsFound(val count: Int) : UnlockCondition()
        data class Gems(val cost: Int) : UnlockCondition()
        data class Quest(val questKey: String) : UnlockCondition()
    }

    data class Loadout(
        val avatarFrameId: String = "frame_default",
        val titleId: String = "title_default",
        val eggSkinId: String = "skin_default",
        val mapThemeId: String = "theme_default",
        val unlockedIds: List<String> = listOf("frame_default", "title_default", "skin_default", "theme_default")
    ) {
        fun toMap(): Map<String, Any> = mapOf(
            "avatarFrameId" to avatarFrameId, "titleId" to titleId,
            "eggSkinId" to eggSkinId, "mapThemeId" to mapThemeId,
            "unlockedIds" to unlockedIds
        )

        companion object {
            fun fromMap(map: Map<String, Any?>): Loadout {
                @Suppress("UNCHECKED_CAST")
                return Loadout(
                    avatarFrameId = map["avatarFrameId"] as? String ?: "frame_default",
                    titleId = map["titleId"] as? String ?: "title_default",
                    eggSkinId = map["eggSkinId"] as? String ?: "skin_default",
                    mapThemeId = map["mapThemeId"] as? String ?: "theme_default",
                    unlockedIds = (map["unlockedIds"] as? List<String>) ?: listOf("frame_default", "title_default", "skin_default", "theme_default")
                )
            }
        }
    }

    // ─── Catalogo Items ──────────────────────────────────────────

    val ALL_ITEMS: List<CosmeticItem> = listOf(
        // Avatar Frames
        CosmeticItem("frame_default", "Base",           "⬜", "Il frame di partenza", Category.AVATAR_FRAME, UnlockCondition.Default, "#808080"),
        CosmeticItem("frame_bronze",  "Bronzo",         "🟫", "Raggiungi livello 5",  Category.AVATAR_FRAME, UnlockCondition.Level(5), "#CD7F32"),
        CosmeticItem("frame_silver",  "Argento",        "⬜", "Raggiungi livello 15", Category.AVATAR_FRAME, UnlockCondition.Level(15), "#C0C0C0"),
        CosmeticItem("frame_gold",    "Oro",            "🟨", "Raggiungi livello 30", Category.AVATAR_FRAME, UnlockCondition.Level(30), "#E0E0FF"),
        CosmeticItem("frame_diamond", "Diamante",       "💎", "Trova 500 uova",       Category.AVATAR_FRAME, UnlockCondition.EggsFound(500), "#B9F2FF"),
        CosmeticItem("frame_legend",  "Leggendario",    "⭐", "Trova 1000 uova",      Category.AVATAR_FRAME, UnlockCondition.EggsFound(1000), "#E0E0FF"),
        CosmeticItem("frame_rainbow", "Arcobaleno",     "🌈", "Acquista con gemme",   Category.AVATAR_FRAME, UnlockCondition.Gems(50), "#FF0080"),

        // Titles
        CosmeticItem("title_default",    "Cacciatore",       "🥚", "Titolo di partenza",    Category.TITLE, UnlockCondition.Default, "#808080"),
        CosmeticItem("title_explorer",   "Esploratore",      "🌍", "Livello 10",            Category.TITLE, UnlockCondition.Level(10), "#00B4FF"),
        CosmeticItem("title_veteran",    "Veterano",         "🏅", "100 uova trovate",      Category.TITLE, UnlockCondition.EggsFound(100), "#FF9800"),
        CosmeticItem("title_legend",     "Leggenda",         "⭐", "Livello 40",            Category.TITLE, UnlockCondition.Level(40), "#E0E0FF"),
        CosmeticItem("title_egg_master", "Maestro delle Uova","🐉", "1000 uova trovate",   Category.TITLE, UnlockCondition.EggsFound(1000), "#FF6B35"),
        CosmeticItem("title_team_hero",  "Eroe della Squadra","🤝", "Missione speciale",   Category.TITLE, UnlockCondition.Quest("team_contrib"), "#00FF88"),

        // Egg Skins
        CosmeticItem("skin_default",   "Classico",    "🥚", "Skin di partenza",   Category.EGG_SKIN, UnlockCondition.Default, "#808080"),
        CosmeticItem("skin_golden",    "Dorato",      "🌟", "Livello 20",         Category.EGG_SKIN, UnlockCondition.Level(20), "#E0E0FF"),
        CosmeticItem("skin_crystal",   "Cristallo",   "💎", "Acquista con gemme", Category.EGG_SKIN, UnlockCondition.Gems(30), "#00E5FF"),
        CosmeticItem("skin_rainbow",   "Arcobaleno",  "🌈", "Acquista con gemme", Category.EGG_SKIN, UnlockCondition.Gems(40), "#FF0080"),
        CosmeticItem("skin_fire",      "Infuocato",   "🔥", "50 uova epiche",     Category.EGG_SKIN, UnlockCondition.EggsFound(50), "#FF6B35"),
        CosmeticItem("skin_cosmos",    "Cosmico",     "🌌", "Livello 50",         Category.EGG_SKIN, UnlockCondition.Level(50), "#8B5CF6"),

        // Map Themes
        CosmeticItem("theme_default",  "Standard",   "🗺️", "Tema di partenza",   Category.MAP_THEME, UnlockCondition.Default, "#808080"),
        CosmeticItem("theme_night",    "Notturno",   "🌙", "Livello 12",         Category.MAP_THEME, UnlockCondition.Level(12), "#1A237E"),
        CosmeticItem("theme_forest",   "Foresta",    "🌲", "Acquista con gemme", Category.MAP_THEME, UnlockCondition.Gems(20), "#1B5E20"),
        CosmeticItem("theme_ocean",    "Oceano",     "🌊", "Acquista con gemme", Category.MAP_THEME, UnlockCondition.Gems(20), "#0288D1"),
        CosmeticItem("theme_desert",   "Deserto",    "🏜️", "500 km camminati",   Category.MAP_THEME, UnlockCondition.Quest("walk_5km"), "#FF8F00")
    )

    // ─── Carica / Salva Loadout ──────────────────────────────────

    fun loadLoadout(playerLevel: Int, eggsFound: Int, onResult: (Loadout, List<CosmeticItem>) -> Unit) {
        if (uid.isEmpty()) { onResult(Loadout(), computeUnlocked(Loadout(), playerLevel, eggsFound)); return }
        db.collection("players").document(uid).collection("customization")
          .document("equipped")
          .get()
          .addOnSuccessListener { doc ->
              val loadout = doc.data?.let { Loadout.fromMap(it) } ?: Loadout()
              onResult(loadout, computeUnlocked(loadout, playerLevel, eggsFound))
          }
          .addOnFailureListener { onResult(Loadout(), computeUnlocked(Loadout(), playerLevel, eggsFound)) }
    }

    fun equipItem(itemId: String, category: Category, currentLoadout: Loadout,
                  onSuccess: (Loadout) -> Unit, onError: (String) -> Unit) {
        if (uid.isEmpty()) { onError("Non autenticato"); return }
        if (itemId !in currentLoadout.unlockedIds) { onError("Item non sbloccato!"); return }

        val newLoadout = when (category) {
            Category.AVATAR_FRAME -> currentLoadout.copy(avatarFrameId = itemId)
            Category.TITLE        -> currentLoadout.copy(titleId = itemId)
            Category.EGG_SKIN     -> currentLoadout.copy(eggSkinId = itemId)
            Category.MAP_THEME    -> currentLoadout.copy(mapThemeId = itemId)
        }
        db.collection("players").document(uid).collection("customization")
          .document("equipped")
          .set(newLoadout.toMap())
          .addOnSuccessListener { onSuccess(newLoadout) }
          .addOnFailureListener { onError(it.message ?: "Errore") }
    }

    fun purchaseWithGems(itemId: String, playerGems: Int, currentLoadout: Loadout,
                         onSuccess: (Loadout, Int) -> Unit, onError: (String) -> Unit) {
        val item = ALL_ITEMS.find { it.id == itemId } ?: run { onError("Item non trovato"); return }
        val cost = (item.unlockCondition as? UnlockCondition.Gems)?.cost ?: run { onError("Non acquistabile"); return }
        if (playerGems < cost) { onError("Gemme insufficienti (servono $cost 💎)"); return }
        if (itemId in currentLoadout.unlockedIds) { onError("Già sbloccato!"); return }
        if (uid.isEmpty()) { onError("Non autenticato"); return }

        val newUnlocked = currentLoadout.unlockedIds + itemId
        val newLoadout = currentLoadout.copy(unlockedIds = newUnlocked)
        val batch = db.batch()
        batch.set(
            db.collection("players").document(uid).collection("customization").document("equipped"),
            newLoadout.toMap()
        )
        batch.update(
            db.collection("players").document(uid),
            "gems", com.google.firebase.firestore.FieldValue.increment(-cost.toLong())
        )
        batch.commit()
          .addOnSuccessListener { onSuccess(newLoadout, cost) }
          .addOnFailureListener { onError(it.message ?: "Errore acquisto") }
    }

    // ─── Unlock automatico per livello / uova ───────────────────

    fun checkAndUnlockItems(playerLevel: Int, eggsFound: Int, currentLoadout: Loadout,
                            onNewUnlocks: (List<CosmeticItem>) -> Unit) {
        val newUnlocks = ALL_ITEMS.filter { item ->
            item.id !in currentLoadout.unlockedIds && when (val c = item.unlockCondition) {
                is UnlockCondition.Default   -> true
                is UnlockCondition.Level     -> playerLevel >= c.level
                is UnlockCondition.EggsFound -> eggsFound >= c.count
                else                         -> false
            }
        }
        if (newUnlocks.isEmpty()) return

        val newUnlockedIds = currentLoadout.unlockedIds + newUnlocks.map { it.id }
        val newLoadout = currentLoadout.copy(unlockedIds = newUnlockedIds)
        if (uid.isNotEmpty()) {
            db.collection("players").document(uid).collection("customization")
              .document("equipped")
              .set(newLoadout.toMap())
        }
        onNewUnlocks(newUnlocks)
    }

    private fun computeUnlocked(loadout: Loadout, level: Int, eggs: Int): List<CosmeticItem> =
        ALL_ITEMS.filter { item ->
            item.id in loadout.unlockedIds || when (val c = item.unlockCondition) {
                is UnlockCondition.Default   -> true
                is UnlockCondition.Level     -> level >= c.level
                is UnlockCondition.EggsFound -> eggs >= c.count
                else                         -> false
            }
        }
}
