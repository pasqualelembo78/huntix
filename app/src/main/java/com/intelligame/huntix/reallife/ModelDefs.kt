package com.intelligame.huntix.reallife

/**
 * ModelDefs — definizioni modelli 3D per la città.
 * Ogni tipo di oggetto mappa a un file GLB in assets/city_models/.
 * Se il file non esiste, CityActivity usa il fallback procedurale.
 */
enum class CityModelType {
    HOUSE_SMALL,
    HOUSE_MEDIUM,
    HOUSE_LARGE,
    SHOP,
    HOSPITAL,
    GYM,
    POLICE,
    FIRE_STATION,
    BANK,
    CHURCH,
    TREE,
    TREE_PINE,
    BUSH,
    CAR,
    BENCH,
    LAMP_POST,
    FLOWER,
    POOL_WATER,
    PLAYER_MALE,
    PLAYER_FEMALE
}

data class CityModelDef(
    val type: CityModelType,
    val glbPath: String?,         // null = procedurale always
    val fallbackScale: Float = 1f,
    val description: String = ""
)

object CityModels {
    private val DEFS = mapOf(
        CityModelType.HOUSE_SMALL   to CityModelDef(CityModelType.HOUSE_SMALL,   "city_models/house_small.glb",   1f, "Casa piccola"),
        CityModelType.HOUSE_MEDIUM  to CityModelDef(CityModelType.HOUSE_MEDIUM,  "city_models/house_medium.glb",  1.2f, "Casa media"),
        CityModelType.HOUSE_LARGE   to CityModelDef(CityModelType.HOUSE_LARGE,   "city_models/house_large.glb",   1.5f, "Casa grande"),
        CityModelType.SHOP          to CityModelDef(CityModelType.SHOP,          "city_models/shop.glb",          1f, "Negozio"),
        CityModelType.HOSPITAL      to CityModelDef(CityModelType.HOSPITAL,      "city_models/hospital.glb",      1.3f, "Ospedale"),
        CityModelType.GYM           to CityModelDef(CityModelType.GYM,           "city_models/gym.glb",           1.1f, "Palestra"),
        CityModelType.POLICE        to CityModelDef(CityModelType.POLICE,        "city_models/police.glb",        1f, "Polizia"),
        CityModelType.FIRE_STATION  to CityModelDef(CityModelType.FIRE_STATION,  "city_models/fire_station.glb",  1f, "Vigili del fuoco"),
        CityModelType.BANK          to CityModelDef(CityModelType.BANK,          "city_models/bank.glb",          1f, "Banca"),
        CityModelType.CHURCH        to CityModelDef(CityModelType.CHURCH,        "city_models/church.glb",        1.2f, "Chiesa"),
        CityModelType.TREE          to CityModelDef(CityModelType.TREE,          "city_models/tree.glb",          0.8f, "Albero"),
        CityModelType.TREE_PINE     to CityModelDef(CityModelType.TREE_PINE,     "city_models/tree_pine.glb",     0.9f, "Pino"),
        CityModelType.BUSH          to CityModelDef(CityModelType.BUSH,          "city_models/bush.glb",          0.5f, "Cespuglio"),
        CityModelType.CAR           to CityModelDef(CityModelType.CAR,           "city_models/car.glb",           0.7f, "Auto"),
        CityModelType.BENCH         to CityModelDef(CityModelType.BENCH,         "city_models/bench.glb",         0.6f, "Panchina"),
        CityModelType.LAMP_POST     to CityModelDef(CityModelType.LAMP_POST,     "city_models/lamp_post.glb",     1f, "Lampione"),
        CityModelType.FLOWER        to CityModelDef(CityModelType.FLOWER,        "city_models/flower.glb",        0.3f, "Fiore"),
        CityModelType.PLAYER_MALE   to CityModelDef(CityModelType.PLAYER_MALE,   "city_models/player_male.glb",   1f, "Player maschio"),
        CityModelType.PLAYER_FEMALE to CityModelDef(CityModelType.PLAYER_FEMALE, "city_models/player_female.glb", 1f, "Player femmina")
    )

    fun get(type: CityModelType): CityModelDef = DEFS[type]!!

    /**
     * Controlla se un modello GLB è disponibile in assets.
     * Nota: i file GLB devono essere copiati manualmente in assets/city_models/
     */
    fun isGlbAvailable(context: android.content.Context, type: CityModelType): Boolean {
        val def = DEFS[type] ?: return false
        val path = def.glbPath ?: return false
        return try {
            context.assets.open(path).close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
