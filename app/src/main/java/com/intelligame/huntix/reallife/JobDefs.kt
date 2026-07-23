package com.intelligame.huntix.reallife

/**
 * JobDefs — definizioni dei lavori disponibili nella città.
 * Ogni lavoro ha un mini-game associato che il giocatore deve completare per guadagnare cash.
 */
enum class JobType { DELIVERY, COOK, TAXI }

data class JobDef(
    val type: JobType,
    val name: String,
    val emoji: String,
    val desc: String,
    val payMin: Int,
    val payMax: Int,
    val difficulty: String   // "Facile", "Medio", "Difficile"
)

object Jobs {
    val ALL = listOf(
        JobDef(
            type = JobType.DELIVERY,
            name = "Consegna",
            emoji = "📦",
            desc = "Consegna pacchi nella città. Segui la freccia e arrivale prima del tempo!",
            payMin = 80, payMax = 200,
            difficulty = "Facile"
        ),
        JobDef(
            type = JobType.COOK,
            name = "Cuoco",
            emoji = "👨‍🍳",
            desc = "Prepara gli ordini dei clienti. Tocca gli ingredienti giusti nel giusto ordine!",
            payMin = 100, payMax = 250,
            difficulty = "Medio"
        ),
        JobDef(
            type = JobType.TAXI,
            name = "Taxi",
            emoji = "🚕",
            desc = "Guida i passeggeri a destinazione. Più veloce guadagni di più!",
            payMin = 120, payMax = 300,
            difficulty = "Medio"
        )
    )
}
