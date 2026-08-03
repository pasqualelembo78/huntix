package com.intelligame.huntix.minigames.card

/**
 * Suit — semi delle carte da gioco.
 */
enum class Suit(val emoji: String, val color: Int) {
    HEARTS("♥️", 0xFFFF4444.toInt()),
    DIAMONDS("♦️", 0xFFFF4444.toInt()),
    CLUBS("♣️", 0xFF1A1A2E.toInt()),
    SPADES("♠️", 0xFF1A1A2E.toInt())
}

/**
 * Rank — valore nominale di una carta.
 */
enum class Rank(val label: String, val value: Int, val emoji: String) {
    ACE("A", 1, "🂡"),
    TWO("2", 2, "🂢"),
    THREE("3", 3, "🂣"),
    FOUR("4", 4, "🂤"),
    FIVE("5", 5, "🂥"),
    SIX("6", 6, "🂦"),
    SEVEN("7", 7, "🂧"),
    EIGHT("8", 8, "🂨"),
    NINE("9", 9, "🂩"),
    TEN("10", 10, "🂪"),
    JACK("J", 10, "🂫"),
    QUEEN("Q", 10, "🂭"),
    KING("K", 10, "🂮")
}

/**
 * Card — una singola carta da gioco con seme e valore.
 */
data class Card(
    val suit: Suit,
    val rank: Rank,
    var isFaceUp: Boolean = false,
    var isSelected: Boolean = false
) {
    val displayName: String get() = "${rank.emoji}${suit.emoji}"
    val pointValue: Int get() = rank.value
}

/**
 * CardDeck — mazzo standard di 40 carte (italiano, senza 8,9,10).
 * Usato per Briscola e Scopa.
 */
object CardDeck {
    fun italianDeck(): List<Card> {
        val cards = mutableListOf<Card>()
        for (suit in Suit.values()) {
            for (rank in listOf(
                Rank.ACE, Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE,
                Rank.SIX, Rank.SEVEN, Rank.JACK, Rank.QUEEN, Rank.KING
            )) {
                cards.add(Card(suit, rank))
            }
        }
        return cards
    }

    fun standardDeck(): List<Card> {
        val cards = mutableListOf<Card>()
        for (suit in Suit.values()) {
            for (rank in Rank.values()) {
                cards.add(Card(suit, rank))
            }
        }
        return cards
    }

    fun shuffle(cards: MutableList<Card>, seed: Long = System.currentTimeMillis()) {
        val rng = java.util.Random(seed)
        for (i in cards.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = cards[i]
            cards[i] = cards[j]
            cards[j] = tmp
        }
    }
}

/**
 * CardGameConfig — configurazione per un gioco di carte.
 */
data class CardGameConfig(
    val gameId: String,
    val title: String,
    val emoji: String,
    val deckType: DeckType = DeckType.ITALIAN,
    val maxPlayers: Int = 2,
    val rules: String = "",
    val rewardMultiplier: Float = 1f
) {
    enum class DeckType { ITALIAN, STANDARD }
}