package com.intelligame.huntix.reallife

/**
 * ShopDefs — definizioni degli item disponibili nel negozio.
 * Ogni item ha nome, emoji, prezzo, categoria e effetto (need key + gain).
 */
enum class ShopCategory(val label: String, val emoji: String) {
    FOOD("Cibo", "\uD83C\uDF5C"),
    CLOTHES("Vestiti", "\uD83D\uDC55"),
    ACCESSORIES("Accessori", "\uD83C\uDF92")
}

data class ShopItem(
    val id: String,
    val name: String,
    val emoji: String,
    val category: ShopCategory,
    val price: Int,
    val needKey: String?,   // null = cosmetic
    val needGain: Float,
    val description: String
)

object ShopDefs {
    val ITEMS = listOf(
        // Cibo
        ShopItem("pizza", "Pizza", "\uD83C\uDF55", ShopCategory.FOOD, 30, "hunger", 20f, "Una fetta calda"),
        ShopItem("sushi", "Sushi", "\uD83C\uDF63", ShopCategory.FOOD, 50, "hunger", 30f, "Sushi fresco"),
        ShopItem("juice", "Succo", "\uD83E\uDDC3", ShopCategory.FOOD, 15, "thirst", 25f, "Succo di frutta"),
        ShopItem("cake", "Torta", "\uD83C\uDF70", ShopCategory.FOOD, 40, "hunger", 15f, "Torta al cioccolato"),
        ShopItem("coffee", "Caffè", "\u2615", ShopCategory.FOOD, 20, "sleep", 15f, "Caffè espresso"),
        // Vestiti
        ShopItem("shoes_red", "Scarpe rosse", "\uD83D\uDC5F", ShopCategory.CLOTHES, 100, null, 0f, "Scarpe sportive rosse"),
        ShopItem("hat", "Cappello", "\uD83E\uDDE2", ShopCategory.CLOTHES, 80, null, 0f, "Cappello da baseball"),
        ShopItem("glasses", "Occhiali", "\uD83D\uDD76\uFE0F", ShopCategory.CLOTHES, 120, null, 0f, "Occhiali da sole"),
        ShopItem("jacket", "Giacca", "\uD83E\uDDE5", ShopCategory.CLOTHES, 150, null, 0f, "Giacca di pelle"),
        // Accessori
        ShopItem("phone", "Telefono", "\uD83D\uDCF1", ShopCategory.ACCESSORIES, 150, "social", 10f, "Smartphone"),
        ShopItem("headphones", "Cuffie", "\uD83C\uDFA7", ShopCategory.ACCESSORIES, 90, "fun", 15f, "Cuffie wireless"),
        ShopItem("watch", "Orologio", "\u231A", ShopCategory.ACCESSORIES, 200, null, 0f, "Orologio da polso"),
        ShopItem("ball", "Palla", "\u26BD", ShopCategory.ACCESSORIES, 60, "fun", 20f, "Palla da calcio"),
        ShopItem("book", "Libro", "\uD83D\uDCDA", ShopCategory.ACCESSORIES, 40, "fun", 10f, "Romanzo bestseller")
    )
}
