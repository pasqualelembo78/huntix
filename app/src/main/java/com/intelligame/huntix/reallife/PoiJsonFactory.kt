package com.intelligame.huntix.reallife

import com.intelligame.huntix.managers.PoiSearchManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * PoiJsonFactory — costruisce la "pagina JSON sintetica" per i POI OSM che non
 * hanno una pagina personalizzata (custom_pages_index). Usata sia dal flusso
 * Unity (StoreUnityBridge.openPoiPage) sia dalla mappa legacy
 * (POICustomPageActivity quando riceve un url "osm:...").
 *
 * La pagina generata contiene: banner (icona/emoji, titolo, sottotitolo
 * categoria, colore), una sezione informativa con coordinate e un link alla
 * pagina OpenStreetMap. Il campo "store" decide se mostrare il pulsante
 * "Entra nel negozio (3D)" in POICustomPageActivity.
 */
object PoiJsonFactory {

    /** JSON di pagina sintetico per un POI OSM senza pagina dedicata. */
    fun build(osmId: String, name: String, buildingType: String, poiType: String,
              lat: Double, lng: Double): JSONObject {
        val category = categoryOf(buildingType, poiType)
        val emoji = emojiFor(buildingType, poiType, category)
        val subtitle = category.ifBlank { poiType.replaceFirstChar { it.uppercase() } }
        val store = isStoreLike(buildingType, poiType)
        val sections = JSONArray()
        sections.put(JSONObject().apply {
            put("type", "text")
            put("title", "Informazioni")
            put("content", "${poiType.replaceFirstChar { it.uppercase() }} · %.6f, %.6f".format(lat, lng))
        })
        val osmType = osmId.removePrefix("osm:").substringBefore(":")
        val ref = osmId.removePrefix("osm:").substringAfter(":", "")
        sections.put(JSONObject().apply {
            put("type", "link")
            put("title", "Vedi su OpenStreetMap")
            put("emoji", "🗺️")
            put("url", "https://www.openstreetmap.org/$osmType/$ref")
        })
        return JSONObject().apply {
            put("store", store)
            put("banner", JSONObject().apply {
                put("icon", emoji)
                put("title", name)
                put("subtitle", subtitle)
                put("color", colorFor(category))
            })
            put("sections", sections)
        }
    }

    /** Etichetta categoria Esplora del POI ("" se non riconosciuto). */
    fun categoryOf(buildingType: String, poiType: String): String {
        val mgr = PoiSearchManager()
        val sr = PoiSearchManager.SearchResult(
            "", "", 0.0, 0.0, buildingType, poiType, "", "", "", "", ""
        )
        return if (mgr.isStore(sr)) mgr.categoryOf(sr).label else ""
    }

    fun emojiFor(buildingType: String, poiType: String, category: String = ""): String {
        if (category.isNotBlank()) {
            for (cat in PoiSearchManager.STORE_CATEGORIES) {
                if (cat.label == category && cat.keywords.isNotEmpty()) return cat.emoji
            }
        }
        val t = "${buildingType} ${poiType}".lowercase(Locale.ROOT)
        return when {
            t.contains("fountain") || t.contains("fontana") || t.contains("spring") -> "⛲"
            t.contains("park") || t.contains("parco") || t.contains("garden") || t.contains("playground") -> "🌳"
            t.contains("school") || t.contains("scuola") || t.contains("kindergarten") || t.contains("university") -> "🎓"
            t.contains("supermarket") || t.contains("supermercato") || t.contains("hyper") || t.contains("mall") -> "🛒"
            t.contains("tobacco") || t.contains("tabacchi") || t.contains("kiosk") || t.contains("edicola") -> "🛍️"
            t.contains("restaurant") || t.contains("ristorante") || t.contains("pizzeria") || t.contains("fast_food") -> "🍝"
            t.contains("cafe") || t.contains("bar") || t.contains("coffee") || t.contains("pub") -> "☕"
            t.contains("gym") || t.contains("palestra") || t.contains("fitness") -> "💪"
            t.contains("museum") || t.contains("museo") || t.contains("church") || t.contains("library") -> "🏛️"
            else -> "📍"
        }
    }

    private fun colorFor(category: String): String = when (category) {
        "Ristoranti" -> "#D84315"
        "Bar & Caffè" -> "#8D6E63"
        "Supermercati" -> "#2E7D32"
        "Negozi & Tabacchi" -> "#455A64"
        "Gym & Fitness" -> "#00695C"
        "Musei & Cultura" -> "#6A1B9A"
        "Parchi & Natura" -> "#33691E"
        "Scuole & Istruzione" -> "#1565C0"
        else -> "#37474F"
    }

    private fun isStoreLike(buildingType: String, poiType: String): Boolean {
        val t = "${buildingType} ${poiType}".lowercase(Locale.ROOT)
        return buildingType == "shop" || listOf(
            "restaurant", "cafe", "bar", "fast_food", "pizzeria", "trattoria", "osteria",
            "supermarket", "supermercato", "hypermarket", "mall", "kiosk", "tobacco",
            "tabacchi", "edicola", "newsagent", "bakery", "pharmacy", "convenience",
            "grocery", "pub", "bistro", "gelateria", "clothing", "books", "shoes", "optician"
        ).any { t.contains(it) }
    }
}
