package com.intelligame.huntix

import android.content.Context
import android.content.SharedPreferences

/**
 * EggFoodManager — gestione cibi-esca per addolcire le uova.
 *
 * Stile Pokémon GO Bacche: lanciare cibo all'uovo prima di catturarlo
 * aumenta la probabilità di cattura. La compatibilità elemento-cibo
 * determina il bonus:
 *
 *   Cibo preferito dall'elemento → bonus ×1.5
 *   Cibo generico                → bonus ×1.2
 *   Cibo odiato dall'elemento    → bonus ×0.8 (malus!)
 *
 * Acquistabili SOLO al negozio. Ogni uso consuma 1 unità.
 * Il giocatore inizia SENZA cibi — deve acquistarli.
 */
object EggFoodManager {

    private const val PREFS = "egg_food_prefs"

    // ── Definizione cibi ─────────────────────────────────────────

    enum class EggFood(
        val id: String,
        val displayName: String,
        val emoji: String,
        val baseCatchBonus: Float,
        val xpMultiplier: Float,
        val favoriteElement: EggElement?,
        val hatedElement: EggElement?,
        val colorHex: String,
        val shopPrice: Int
    ) {
        MELA_DOLCE(
            id = "mela_dolce",
            displayName = "Mela Dolce",
            emoji = "🍎",
            baseCatchBonus = 1.15f,
            xpMultiplier = 1.0f,
            favoriteElement = EggElement.WATER,
            hatedElement = EggElement.FIRE,
            colorHex = "#FF3366",
            shopPrice = 20
        ),
        PEPERONCINO(
            id = "peperoncino",
            displayName = "Peperoncino Ardente",
            emoji = "🌶️",
            baseCatchBonus = 1.20f,
            xpMultiplier = 1.0f,
            favoriteElement = EggElement.FIRE,
            hatedElement = EggElement.WATER,
            colorHex = "#FF6B35",
            shopPrice = 25
        ),
        FUNGO_MAGICO(
            id = "fungo_magico",
            displayName = "Fungo Magico",
            emoji = "🍄",
            baseCatchBonus = 1.20f,
            xpMultiplier = 1.0f,
            favoriteElement = EggElement.EARTH,
            hatedElement = EggElement.AIR,
            colorHex = "#795548",
            shopPrice = 25
        ),
        MIELE_DORATO(
            id = "miele_dorato",
            displayName = "Miele Dorato",
            emoji = "🍯",
            baseCatchBonus = 1.20f,
            xpMultiplier = 1.0f,
            favoriteElement = EggElement.AIR,
            hatedElement = EggElement.EARTH,
            colorHex = "#FFC107",
            shopPrice = 25
        ),
        BANANA_DORATA(
            id = "banana_dorata",
            displayName = "Banana Dorata",
            emoji = "🍌",
            baseCatchBonus = 1.30f,
            xpMultiplier = 1.0f,
            favoriteElement = null,
            hatedElement = null,
            colorHex = "#FFD54F",
            shopPrice = 50
        ),
        ANANAS_MAGICO(
            id = "ananas_magico",
            displayName = "Ananas Magico",
            emoji = "🍍",
            baseCatchBonus = 1.0f,
            xpMultiplier = 2.0f,
            favoriteElement = null,
            hatedElement = null,
            colorHex = "#FDD835",
            shopPrice = 80
        );

        companion object {
            fun fromId(id: String) = values().firstOrNull { it.id == id }
        }
    }

    // ── Compatibilità elemento-cibo ──────────────────────────────

    fun calculateCatchBonus(food: EggFood, eggElement: EggElement): Float {
        return when {
            food.favoriteElement == eggElement -> food.baseCatchBonus * 1.30f
            food.hatedElement == eggElement    -> food.baseCatchBonus * 0.65f
            food.favoriteElement == null        -> food.baseCatchBonus
            else                               -> food.baseCatchBonus
        }
    }

    fun getReaction(food: EggFood, eggElement: EggElement): FoodReaction {
        return when {
            food.favoriteElement == eggElement -> FoodReaction.LOVES
            food.hatedElement == eggElement    -> FoodReaction.HATES
            food.favoriteElement == null        -> FoodReaction.LIKES
            else                               -> FoodReaction.NEUTRAL
        }
    }

    enum class FoodReaction(val emoji: String, val message: String) {
        LOVES("😍", "Adora questo cibo!"),
        LIKES("😊", "Sembra contento!"),
        NEUTRAL("😐", "Non sembra impressionato."),
        HATES("😤", "Non gli piace per niente!")
    }

    // ── Inventario ───────────────────────────────────────────────

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getQuantity(ctx: Context, food: EggFood): Int =
        prefs(ctx).getInt("qty_${food.id}", 0)

    fun addQuantity(ctx: Context, food: EggFood, amount: Int) {
        val current = getQuantity(ctx, food)
        prefs(ctx).edit().putInt("qty_${food.id}", current + amount).apply()
    }

    fun consume(ctx: Context, food: EggFood): Boolean {
        val current = getQuantity(ctx, food)
        if (current <= 0) return false
        prefs(ctx).edit().putInt("qty_${food.id}", current - 1).apply()
        return true
    }

    fun getAvailableFoods(ctx: Context): List<Pair<EggFood, Int>> {
        return EggFood.values().map { food ->
            Pair(food, getQuantity(ctx, food))
        }.filter { it.second > 0 }
    }

    /** Restituisce true se il giocatore non ha nessun cibo nell'inventario. */
    fun hasNoFood(ctx: Context): Boolean {
        return EggFood.values().all { getQuantity(ctx, it) <= 0 }
    }

    /** Stato del cibo applicato nell'incontro corrente. */
    var currentAppliedFood: EggFood? = null
        private set
    var currentFoodBonus: Float = 1.0f
        private set
    var currentXpMultiplier: Float = 1.0f
        private set

    fun applyFood(ctx: Context, food: EggFood, eggElement: EggElement): FoodReaction {
        if (!consume(ctx, food)) return FoodReaction.NEUTRAL
        currentAppliedFood = food
        currentFoodBonus = calculateCatchBonus(food, eggElement)
        currentXpMultiplier = food.xpMultiplier
        // Track research tasks
        com.intelligame.huntix.managers.ResearchTaskManager.trackProgress(ctx, "spend_food")
        return getReaction(food, eggElement)
    }

    fun resetEncounter() {
        currentAppliedFood = null
        currentFoodBonus = 1.0f
        currentXpMultiplier = 1.0f
    }

    /**
     * Starter kit: 5 Mele Dolci + 3 Peperoncini Ardenti.
     * Il giocatore deve poter giocare dal primo minuto.
     */
    fun giveStarterKit(ctx: Context) {
        val p = prefs(ctx)
        val migratedKey = "starter_kit_given"
        if (p.getBoolean(migratedKey, false)) return
        addQuantity(ctx, EggFood.MELA_DOLCE, 5)
        addQuantity(ctx, EggFood.PEPERONCINO, 3)
        p.edit().putBoolean(migratedKey, true).apply()
    }
}
