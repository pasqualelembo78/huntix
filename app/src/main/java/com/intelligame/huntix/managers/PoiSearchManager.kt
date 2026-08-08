package com.intelligame.huntix.managers

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale

/**
 * 📡 PoiSearchManager — ricerca POI con selezione regione/città.
 *
 * La ricerca è in tempo reale su frazioni di parola:
 *   "bar"          → tutti i bar (tipo bar_cafe + nomi contenenti "bar"), in ordine alfabetico
 *   "bar onofr"    → Bar Onofrio (ogni parola della query deve combaciare)
 *   "onofrio" / "matteo" → Bar Onofrio di Matteo
 *
 * I dati (indice città e POI) vengono scaricati dal repo huntix-poi e
 * memorizzati su disco (poi_search_cache) per la ricerca offline ripetuta.
 */
class PoiSearchManager {

    data class Region(val name: String, val slug: String)

    data class City(val name: String, val slug: String)

    data class SearchResult(
        val id: String,
        val name: String,
        val lat: Double,
        val lng: Double,
        val buildingType: String,
        val poiType: String,
        val url: String,
        val pageType: String,
        val city: String,
        val region: String,
        val category: String
    )

    /**
     * 🏪 Categoria di negozio per la ricerca filtrata.
     *
     * I [keywords] vengono matchati (case-insensitive, substring) su
     * [buildingType] e [poiType] del POI (codici Overture/OSM, es. "SHOP",
     * "RETAIL", "CAFE", "FOOD"). Una categoria con [keywords] vuoto
     * (es. "Tutti i negozi") non applica alcun filtro.
     */
    data class StoreCategory(val label: String, val emoji: String, val keywords: Set<String>) {
        /** True se corrisponde a tutti i negozi (nessun filtro specifico). */
        val isAll: Boolean get() = keywords.isEmpty()
    }

    companion object {
        private const val BASE_URL = "https://raw.githubusercontent.com/pasqualelembo78/huntix-poi/main"
        private const val CACHE_DIR = "poi_search_cache"
        const val MIN_QUERY_LENGTH = 2

        private val REGIONS = listOf(
            Region("Abruzzo", "abruzzo"),
            Region("Basilicata", "basilicata"),
            Region("Calabria", "calabria"),
            Region("Campania", "campania"),
            Region("Emilia-Romagna", "emilia-romagna"),
            Region("Friuli V.G.", "friuli_vg"),
            Region("Lazio", "lazio"),
            Region("Liguria", "liguria"),
            Region("Lombardia", "lombardia"),
            Region("Marche", "marche"),
            Region("Molise", "molise"),
            Region("Piemonte", "piemonte"),
            Region("Puglia", "puglia"),
            Region("Sardegna", "sardegna"),
            Region("Sicilia", "sicilia"),
            Region("Toscana", "toscana"),
            Region("Trentino-A.A.", "trentino-aa"),
            Region("Umbria", "umbria"),
            Region("Valle d'Aosta", "valle_daosta"),
            Region("Veneto", "veneto")
        )

        /**
         * 🏪 Categorie di locale per la ricerca filtrata in Esplora.
         *
         * I keyword sono i valori canonici OSM (lower-case, corrispondenza
         * ESATTA su buildingType/poiType così da evitare falsi positivi come
         * "bar" dentro "barber"). Coprono sia i dati huntix-poi (es. ristorante,
         * bar_cafe, palestra) sia i dati live Overpass (es. restaurant, cafe,
         * bar, shop, fitness_centre, museum).
         *
         * Il primo elemento ("Tutti i locali") ha keywords vuoto (isAll) e con
         * [isStore] mostra l'insieme di tutti i locali commerciali/culturali
         * (esclude ospedali, amministrazioni, parcheggi…).
         *
         * L'ordine determina la classificazione: un POI è assegnato alla PRIMA
         * categoria i cui keyword combacia (es. amenity=cafe → "Bar & Caffè" non
         * "Negozi" anche se shop=coffee).
         */
        val STORE_CATEGORIES: List<StoreCategory> = listOf(
            StoreCategory("Tutti i locali", "🏪", emptySet()),
            StoreCategory("Ristoranti", "🍝", setOf(
                "restaurant","ristorante","pub","bistro","fast_food",
                "pizzeria","trattoria","osteria","kebab","doner"
            )),
            StoreCategory("Bar & Caffè", "☕", setOf(
                "cafe","caffè","coffee","bar","bar_cafe","coffee_house","caffè","juice_bar"
            )),
            StoreCategory("Negozi", "🛍️", setOf(
                "supermarket","supermercato","convenience","grocery","market","mall",
                "clothing","apparel","fashion","electronics","computer","books","bookstore",
                "florist","pharmacy","chemist","perfume","alcohol","wine","beer","bakery",
                "panetteria","butcher","greengrocer","frutteto","confectionery","gelato",
                "gelateria","gift","stationery","kiosk","newsagent","tea","jewellery",
                "jewelry","pet","beauty","cosmetics","furniture","hardware","mobile_phone",
                "mobile","bicycle","car","car_repair","laundry","dry_cleaning","tailor",
                "optician","optometrist","money","lottery","video_games","music","photo",
                "sports","outdoors","general","variety_store","department_store",
                "minimarket","tabaccheria","edicola","negozio","shop","food_shop",
                "convenience_store","supermercato","barber","shoemaker","cleaner",
                "photographer","electronics_repair","jewellery_repair"
            )),
            StoreCategory("Gym & Fitness", "💪", setOf(
                "gym","palestra","palestre","fitness_centre","fitness","yoga","sports_centre"
            )),
            StoreCategory("Musei & Cultura", "🏛️", setOf(
                "museum","museo","gallery","monument","monumento","library","biblioteca",
                "church","cathedral","cattedrale","monastery","castle","castello","ruin",
                "memorial","statue","viewpoint","tourist_information","theme_park","zoo",
                "attraction","planetarium","theatre","opera"
            ))
        )
    }

    fun getRegions(): List<Region> = REGIONS

    /** Città della regione (slug) — prima voce sempre "Tutte le città". */
    fun getCitiesForRegion(regionSlug: String, context: Context, callback: (List<City>) -> Unit) {
        Thread {
            val cities = mutableListOf<City>()
            try {
                val cached = citiesCacheFile(context, regionSlug)
                val text = if (cached.exists()) cached.readText()
                else httpGet("$BASE_URL/italia/$regionSlug/_citta.csv")?.also {
                    cached.parentFile?.mkdirs()
                    cached.writeText(it)
                }
                if (text != null) {
                    for (line in text.lines()) {
                        if (line.startsWith("#") || line.isBlank()) continue
                        val p = splitCsv(line)
                        if (p.size >= 4) {
                            cities.add(City(p[2], p[3]))
                        }
                    }
                }
            } catch (_: Exception) {}
            Handler(Looper.getMainLooper()).post { callback(cities) }
        }.start()
    }

    /**
     * POI di una città (citySlug non vuota) oppure dell'intera regione (citySlug vuota).
     * city di ogni SearchResult viene derivata dall'url quando disponibile.
     */
    fun loadPois(regionSlug: String, citySlug: String, context: Context, callback: (List<SearchResult>) -> Unit) {
        Thread {
            val results = mutableListOf<SearchResult>()
            try {
                val cached = poisCacheFile(context, regionSlug, citySlug)
                val text = if (cached.exists()) cached.readText()
                else {
                    val url = if (citySlug.isBlank()) "$BASE_URL/italia/$regionSlug/_all.csv"
                    else "$BASE_URL/italia/$regionSlug/$citySlug/_all.csv"
                    httpGet(url)?.also {
                        cached.parentFile?.mkdirs()
                        cached.writeText(it)
                    }
                }
                if (text != null) {
                    for (line in text.lines()) {
                        if (line.startsWith("#") || line.isBlank()) continue
                        val parts = splitCsv(line)
                        if (parts.size < 6) continue
                        val lat = parts[0].toDoubleOrNull() ?: continue
                        val lng = parts[1].toDoubleOrNull() ?: continue
                        val url = if (parts.size >= 7) parts[6] else ""
                        val city = if (citySlug.isBlank()) cityFromUrl(regionSlug, url) else citySlug
                        results.add(SearchResult(
                            id = parts[2],
                            name = parts[3],
                            lat = lat, lng = lng,
                            buildingType = parts[4],
                            poiType = parts[5],
                            url = url,
                            pageType = if (parts.size >= 8) parts[7] else "",
                            city = city, region = regionSlug,
                            category = parts[5]
                        ))
                    }
                }
            } catch (_: Exception) {}
            Handler(Looper.getMainLooper()).post { callback(results) }
        }.start()
    }

    /**
     * Filtro in tempo reale su frazioni di parola:
     * ogni token della query deve essere sottostringa (senza accenti, case-insensitive)
     * del nome o del tipo (buildingType + poiType) del POI.
     */
    fun filterPois(query: String, pois: List<SearchResult>): List<SearchResult> {
        val tokens = query.trim().split(Regex("\\s+")).map(::normalize).filter { it.isNotBlank() }
        if (tokens.isEmpty() || query.trim().length < MIN_QUERY_LENGTH) return emptyList()
        return pois.asSequence()
            .filter { p ->
                val n = normalize(p.name)
                val t = normalize("${p.buildingType} ${p.poiType}")
                tokens.all { n.contains(it) || t.contains(it) }
            }
            .sortedBy { normalize(it.name) }
            .toList()
    }

    /** URL della pagina del POI. */
    fun getJsonPageUrl(result: SearchResult): String {
        if (result.url.isNotBlank()) return result.url
        val slug = result.name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return "$BASE_URL/italia/${result.region}/${result.city}/pages/$slug.json"
    }

    // ── Negozi / locali ───────────────────────────────────────────

    /** Token canonici (lower-case) di classificazione di un POI. */
    private fun typeTokens(result: SearchResult): Set<String> = buildSet {
        val b = result.buildingType.lowercase(Locale.ROOT).trim()
        val p = result.poiType.lowercase(Locale.ROOT).trim()
        if (b.isNotEmpty()) add(b)
        if (p.isNotEmpty()) add(p)
    }

    /** True se il POI è un locale commerciale/culturale (negozio, bar, ristorante, gym, museo…). */
    fun isStore(result: SearchResult): Boolean {
        val tokens = typeTokens(result)
        // "Tutti i locali" (isAll) corrisponde all'unione di tutte le categorie.
        return STORE_CATEGORIES.drop(1).any { cat ->
            cat.keywords.any { it in tokens }
        }
    }

    /** Classifica il POI nella categoria di locale corrispondente. */
    fun categoryOf(result: SearchResult): StoreCategory {
        if (!isStore(result)) return STORE_CATEGORIES.first()
        val tokens = typeTokens(result)
        return STORE_CATEGORIES.drop(1).firstOrNull { cat ->
            cat.keywords.any { it in tokens }
        } ?: STORE_CATEGORIES.first()
    }

    // --- PRIVATE ---

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    private fun splitCsv(line: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { fields.add(sb.toString().trim()); sb.setLength(0) }
                else -> sb.append(c)
            }
        }
        fields.add(sb.toString().trim())
        return fields
    }

    private fun cityFromUrl(region: String, url: String): String {
        val marker = "/italia/$region/"
        val idx = url.indexOf(marker)
        if (idx >= 0) {
            val rest = url.substring(idx + marker.length)
            val slash = rest.indexOf('/')
            if (slash > 0) return rest.substring(0, slash)
        }
        return ""
    }

    private fun citiesCacheFile(context: Context, region: String): File {
        val dir = File(File(context.filesDir, CACHE_DIR), region)
        dir.mkdirs()
        return File(dir, "_citta.csv")
    }

    private fun poisCacheFile(context: Context, region: String, citySlug: String): File {
        val dir = File(File(context.filesDir, CACHE_DIR), region)
        dir.mkdirs()
        val name = if (citySlug.isBlank()) "_all.csv" else "${citySlug}_all.csv"
        return File(dir, name)
    }

    private fun httpGet(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                reader.readText()
            } else null
        } catch (_: Exception) { null }
    }
}
