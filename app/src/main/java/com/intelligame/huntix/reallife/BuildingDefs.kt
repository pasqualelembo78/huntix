package com.intelligame.huntix.reallife

import android.graphics.Color

/**
 * BuildingDefs — definizioni degli edifici speciali nella città 3D.
 *
 * Ogni edificio ha un tipo, nome, emoji, colore 3D, dimensioni e posizione fissa.
 * Gli edifici vengono piazzati in blocchi specifici della griglia (strade ogni 10 unità).
 * I rimanenti blocchi vengono riempiti con edifici procedurali generici.
 */
enum class BuildingType {
    HOUSE, RESTAURANT, SUPERMARKET, HOSPITAL, GYM
}

data class BuildingAction(
    val emoji: String,
    val label: String,
    val needKey: String,
    val gain: Float
)

data class BuildingDef(
    val type: BuildingType,
    val name: String,
    val emoji: String,
    val color3D: Int,
    val roofColor: Int,
    val x: Float,
    val z: Float,
    val width: Float,
    val depth: Float,
    val height: Float,
    val actions: List<BuildingAction>
) {
    /** AABB per rilevamento collisione/prossimità */
    fun aabb() = AABB(
        minX = x - width / 2f, maxX = x + width / 2f,
        minZ = z - depth / 2f, maxZ = z + depth / 2f
    )
}

data class AABB(val minX: Float, val maxX: Float, val minZ: Float, val maxZ: Float)

object BuildingDefs {

    /** Raggio massimo per considerare il player "vicino" a un edificio (per label e "Entra") */
    const val NEAR_DISTANCE = 4.5f

    /** Tutti gli edifici speciali */
    val BUILDINGS = listOf(
        BuildingDef(
            type = BuildingType.HOUSE,
            name = "Casa Mia",
            emoji = "\uD83C\uDFE0",
            color3D = 0xFF5C6BC0.toInt(),    // indigo
            roofColor = 0xFF3949AB.toInt(),   // indigo scuro
            x = -20f, z = -20f,
            width = 3.5f, depth = 3.5f, height = 2.8f,
            actions = listOf(
                BuildingAction("\uD83D\uDCA4", "Dormi", "sleep", 25f),
                BuildingAction("\uD83D\uDEFC", "Guarda TV", "fun", 15f),
                BuildingAction("\uD83C\uDF73", "Cucina", "hunger", 15f)
            )
        ),
        BuildingDef(
            type = BuildingType.RESTAURANT,
            name = "Ristorante",
            emoji = "\uD83C\uDF55",
            color3D = 0xFFE53935.toInt(),    // rosso
            roofColor = 0xFFC62828.toInt(),   // rosso scuro
            x = 10f, z = -20f,
            width = 4f, depth = 3.5f, height = 2.5f,
            actions = listOf(
                BuildingAction("\uD83C\uDF5D", "Pasta", "hunger", 30f),
                BuildingAction("\uD83C\uDF54", "Hamburger", "hunger", 20f),
                BuildingAction("\uD83C\uDF7A", "Bevi", "thirst", 25f)
            )
        ),
        BuildingDef(
            type = BuildingType.SUPERMARKET,
            name = "Supermercato",
            emoji = "\uD83D\uDED2",
            color3D = 0xFF43A047.toInt(),    // verde
            roofColor = 0xFF2E7D32.toInt(),   // verde scuro
            x = 0f, z = 0f,
            width = 4.5f, depth = 4f, height = 2.2f,
            actions = listOf(
                BuildingAction("\uD83C\uDF4E", "Frutta", "hunger", 15f),
                BuildingAction("\uD83E\uDDC2", "Acqua", "thirst", 20f),
                BuildingAction("\uD83D\uDCE6", "Spesa", "fun", 10f)
            )
        ),
        BuildingDef(
            type = BuildingType.HOSPITAL,
            name = "Ospedale",
            emoji = "\uD83C\uDFE5",
            color3D = 0xFFECEFF1.toInt(),    // bianco grigio
            roofColor = 0xFFB0BEC5.toInt(),   // grigio azzurro
            x = -20f, z = 10f,
            width = 4f, depth = 4f, height = 3.5f,
            actions = listOf(
                BuildingAction("\uD83D\uDC8A", "Medico", "hygiene", 30f),
                BuildingAction("\uD83D\uDEE1\uFE0F", "Controllo", "fun", 10f)
            )
        ),
        BuildingDef(
            type = BuildingType.GYM,
            name = "Palestra",
            emoji = "\uD83D\uDCAA",
            color3D = 0xFFFF9800.toInt(),    // arancione
            roofColor = 0xFFE65100.toInt(),   // arancione scuro
            x = 20f, z = 10f,
            width = 3.5f, depth = 3.5f, height = 2.8f,
            actions = listOf(
                BuildingAction("\uD83C\uDFC3", "Corri", "fun", 20f),
                BuildingAction("\uD83C\uDFCB\uFE0F", "Peschi", "fun", 25f),
                BuildingAction("\uD83E\uDDD8", "Yoga", "sleep", 10f)
            )
        )
    )

    private val aabbCache = BUILDINGS.associateWith { it.aabb() }

    /** Trova l'edificio più vicino a una posizione, oppure null */
    fun findNearest(px: Float, pz: Float): Pair<BuildingDef, Float>? {
        var best: BuildingDef? = null
        var bestDist = Float.MAX_VALUE
        for (b in BUILDINGS) {
            val dx = px - b.x
            val dz = pz - b.z
            val d = Math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
            if (d < bestDist) {
                bestDist = d
                best = b
            }
        }
        return if (best != null && bestDist <= NEAR_DISTANCE) best to bestDist else null
    }

    /** Controlla se un punto è dentro un edificio (AABB) */
    fun isInside(px: Float, pz: Float): BuildingDef? {
        for (b in BUILDINGS) {
            val a = aabbCache[b] ?: continue
            if (px >= a.minX && px <= a.maxX && pz >= a.minZ && pz <= a.maxZ) return b
        }
        return null
    }

    /** Lista dei blocchi occupati da edifici speciali (per escluderli dalla generazione procedurale) */
    fun occupiedBlocks(): List<Pair<Float, Float>> {
        return BUILDINGS.map { b ->
            // Arrotonda al centro del blocco della griglia
            val bx = Math.round(b.x / 10f) * 10f
            val bz = Math.round(b.z / 10f) * 10f
            bx to bz
        }
    }
}
