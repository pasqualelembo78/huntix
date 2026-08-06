package com.intelligame.huntix.legacy.poi.creature

enum class Rarity { Scoperta, Comune, Rara, Epicca, Leggendaria }

data class Creature(
    val id: String,
    val nome: String,
    val elemento: Elemento,
    val rarita: Rarity,
    val expBase: Int
)

enum class Elemento { Fuoco, Acqua, Terra, Aria, Luce, Ombra, Elettrico, Ghiaccio, Buio, Psichico }

data class Trainer(
    val nome: String = "Allenatore",
    val livello: Int = 1,
    val exp: Int = 0,
    val energia: Int = 100,
    val creatureCatturate: List<String> = emptyList()
) {
    fun expPerLivello(l: Int) = baseExp(l)
    private fun baseExp(l: Int) = (75 + 25 * l * l)

    fun aggiungiEsperienza(punti: Int): Trainer {
        val tot = exp + punti
        var lv = livello
        var rimanente = tot
        while (rimanente >= expPerLivello(lv + 1)) {
            rimanente -= expPerLivello(lv + 1)
            lv++
        }
        val next = expPerLivello(lv + 1)
        val newExp = next - rimanente
        return copy(livello = lv, exp = expPerLivello(lv) - newExp, energia = 100.coerceAtMost(energia + 10))
    }
}
