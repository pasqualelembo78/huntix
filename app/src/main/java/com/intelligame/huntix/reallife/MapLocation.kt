package com.intelligame.huntix.reallife

/**
 * MapLocation — definizioni dei luoghi visualizzabili sulla mappa.
 * Ogni luogo ha nome, emoji, posizione nella città (coordinate 3D → mappa), colore, e tipo.
 */
enum class LocationType { BUILDING, LANDMARK, NATURE }

data class MapLocation(
    val id: String,
    val name: String,
    val emoji: String,
    val cityX: Float,   // coordinata X nella città (da -40 a 40)
    val cityZ: Float,   // coordinata Z nella città
    val color: Int,
    val type: LocationType,
    val description: String = ""
)

object MapLocations {
    // Conversione: città (da -HALF a +HALF = da -40 a +40) → mappa (pixel normalizzati 0..1)
    const val CITY_SIZE = 80f
    const val HALF = CITY_SIZE / 2f

    fun cityToMap(cityCoord: Float): Float {
        return (cityCoord + HALF) / CITY_SIZE  // 0..1
    }

    val LOCATIONS = listOf(
        // Case
        MapLocation("home", "Casa tua", "\uD83C\uDFE0", -5f, -5f, 0xFF4CAF50.toInt(), LocationType.BUILDING, "La tua casa"),
        MapLocation("house1", "Casa Rosa", "\uD83C\uDFE0", 15f, 15f, 0xFFE91E63.toInt(), LocationType.BUILDING),
        MapLocation("house2", "Casa Blu", "\uD83C\uDFE0", -25f, 25f, 0xFF2196F3.toInt(), LocationType.BUILDING),
        MapLocation("house3", "Casa Verde", "\uD83C\uDFE0", 25f, -15f, 0xFF66BB6A.toInt(), LocationType.BUILDING),

        // Speciali (posizioni da BuildingDefs)
        MapLocation("ristorante", "Ristorante", "\uD83C\uDF5D", -25f, -15f, 0xFFFF7043.toInt(), LocationType.BUILDING, "Mangia qui!"),
        MapLocation("supermercato", "Supermercato", "\uD83D\uDED2", 5f, -25f, 0xFF42A5F5.toInt(), LocationType.BUILDING, "Fai la spesa"),
        MapLocation("ospedale", "Ospedale", "\uD83C\uDFE5", -15f, 5f, 0xFFEF5350.toInt(), LocationType.BUILDING, "Cura i bisogni"),
        MapLocation("palestra", "Palestra", "\uD83C\uDFCB\uFE0F", 15f, 5f, 0xFFAB47BC.toInt(), LocationType.BUILDING, "Allena il tuo corpo"),

        // Città
        MapLocation("polizia", "Polizia", "\uD83D\uDEE1\uFE0F", -15f, -25f, 0xFF1565C0.toInt(), LocationType.BUILDING, "Proteggi la città"),
        MapLocation("vigili", "Vigili del Fuoco", "\uD83D\uDE92", 25f, 25f, 0xFFC62828.toInt(), LocationType.BUILDING, "Spegni gli incendi"),
        MapLocation("banca", "Banca", "\uD83C\uDFE6", 5f, 5f, 0xFFFFB300.toInt(), LocationType.BUILDING, "Gestisci i tuoi soldi"),
        MapLocation("chiesa", "Chiesa", "\u26EA", -5f, 25f, 0xFFD4A574.toInt(), LocationType.LANDMARK, "Un luogo di pace"),

        // Natura
        MapLocation("parco", "Parco", "\uD83C\uDF33", 25f, -5f, 0xFF81C784.toInt(), LocationType.NATURE, "Rilassati nella natura"),
        MapLocation("lago", "Lago", "\uD83C\uDFD7\uFE0F", -25f, 15f, 0xFF29B6F6.toInt(), LocationType.NATURE, "Acqua cristallina"),
        MapLocation("benzinaio", "Benzinaio", "⛽", 15f, -25f, 0xFF78909C.toInt(), LocationType.BUILDING, "Rifornisci l'auto"),
        MapLocation("negozi", "Centro commerciale", "\uD83D\uDECD\uFE0F", -5f, -15f, 0xFFFF8A65.toInt(), LocationType.BUILDING, "Shopping!")
    )
}
