package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.location.Location
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import com.intelligame.huntix.managers.OsmPoiRepository
import com.intelligame.huntix.managers.PoiSearchManager

/** Lambda di carico POI in modalità nearby: (lat, lng, raggio, callback). */
typealias NearbyLoader = (lat: Double, lng: Double, radiusMeters: Int, (List<PoiSearchManager.SearchResult>) -> Unit) -> Unit

/**
 * 🔎 Pannello di ricerca POI riutilizzabile (Home e OutdoorWorld).
 *
 * 1. Dropdown **Regione** (le 20 regioni italiane)
 * 2. Dropdown **Città** (dal file `_citta.csv` della regione; "Tutte le città" = intera regione)
 * 3. Campo di **ricerca realtime** (ogni 2+ caratteri, anche su frammenti di parola:
 *    "bar onofr" → "Bar Onofrio di Matteo"), risultati in ordine alfabetico.
 *
 * Uso:
 *   val panel = PoiSearchPanel(this).apply {
 *       onOpenPoi = { r ->
 *           startActivity(Intent(this@Act, POICustomPageActivity::class.java).apply {
 *               putExtra(POICustomPageActivity.EXTRA_JSON_URL, PoiSearchManager().getJsonPageUrl(r))
 *               putExtra(POICustomPageActivity.EXTRA_POI_NAME, r.name)
 *           })
 *       }
 *   }
 */
class PoiSearchPanel @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onOpenPoi: ((PoiSearchManager.SearchResult) -> Unit)? = null

    /** Notificato a ogni cambio di selezione regione/città. */
    var onSelectionChanged: ((regionSlug: String, citySlug: String) -> Unit)? = null

    /** Filtro opzionale sui risultati (es. mostra solo i negozi). */
    var resultFilter: ((PoiSearchManager.SearchResult) -> Boolean)? = null

    /**
     * ✅ MODALITÀ "NEARBY": se impostato, il pannello non usa i dropdown
     * Regione/Città ma scarica i POI intorno al GPS tramite [nearbyLoader] e li
     * filtra con [resultFilter]. Nasconde i dropdown e mostra un header dinamico.
     */
    var nearbyLoader: NearbyLoader? = null
    /** Fornisce l'ultima posizione nota (es. OutdoorManager.currentLocation). */
    var locationSupplier: (() -> Location?)? = null
    /** Notifica quando il carico nearby è completato (per aggiornare il contatore). */
    var onNearbyLoaded: (() -> Unit)? = null
    private var nearbyHeader: TextView? = null

    /** Selezione corrente (aggiornata in modo sincrono al cambio dei menu). */
    var currentRegionSlug: String = ""
        private set
    var currentCitySlug: String = ""
        private set

    private val mgr = PoiSearchManager()
    private val regions = mgr.getRegions()
    private val cities = mutableListOf<PoiSearchManager.City>()
    private var loadedPois: List<PoiSearchManager.SearchResult> = emptyList()
    private var loadingPois = false
    private val searchExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var lastQueryGen = 0

    private val handler: Handler? = try {
        Handler(Looper.getMainLooper())
    } catch (e: Exception) {
        null
    }
    private var debounceRunnable: Runnable? = null

    private lateinit var regionSpinner: Spinner
    private lateinit var citySpinner: Spinner
    private lateinit var searchEdit: EditText
    private lateinit var resultsScroll: ScrollView
    private lateinit var resultsBox: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun darkBg(radius: Int, color: Int) = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    init {
        orientation = VERTICAL

        // ─── Regione ───
        regionSpinner = themedSpinner()
        setSpinner(regionSpinner, listOf("🌍 Regione") + regions.map { it.name }, true)
        regionSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (pos <= 0 || pos - 1 >= regions.size) {
                    onRegionSelected(null)
                    return
                }
                onRegionSelected(regions[pos - 1])
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        addView(regionSpinner, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // ─── Città ───
        citySpinner = themedSpinner()
        citySpinner.isEnabled = false
        citySpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (pos < 0 || pos >= cities.size) return
                onCitySelected(cities[pos].slug)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        addView(citySpinner, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // ─── Ricerca ───
        searchEdit = EditText(context).apply {
            hint = "🔍 Cerca POI (nome o tipo)..."
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#88FFFFFF"))
            background = darkBg(dp(8), 0x22FFFFFF)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s?.toString() ?: ""
                    val h = handler ?: return
                    h.removeCallbacksAndMessages(null)

                    val runnable = Runnable { runQuery(query) }
                    h.postDelayed(runnable, 250)
                }
            })
        }
        addView(searchEdit, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // ─── Risultati ───
        resultsBox = LinearLayout(context).apply {
            orientation = VERTICAL
            isClickable = true
        }
        resultsScroll = ScrollView(context).apply {
            background = darkBg(dp(8), Color.parseColor("#DD1A1030"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(220))
            addView(resultsBox, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(resultsScroll)

        showMessage("Scegli una regione per iniziare la ricerca.")
    }

    // ─── Spinner stilizzato ───
    private fun themedSpinner(): Spinner {
        val itemLayout = android.R.layout.simple_spinner_item
        val items = mutableListOf<String>()
        return Spinner(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
                it.bottomMargin = dp(6)
            }
            setPadding(0, dp(2), 0, dp(2))
            setBackgroundDrawable(darkBg(dp(8), 0x22FFFFFF))
            adapter = ArrayAdapter(context, itemLayout, items).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
    }

    private fun setSpinner(spinner: Spinner, items: List<String>, enabled: Boolean) {
        spinner.isEnabled = enabled
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter
    }

    // ─── Region selection ───
    private var ignoreRegionChange = false
    private var ignoreCityChange = false

    private fun onRegionSelected(region: PoiSearchManager.Region?) {
        lastQueryGen++
        val currentGen = lastQueryGen
        loadedPois = emptyList()
        loadingPois = false
        cities.clear()

        currentCitySlug = ""
        currentRegionSlug = region?.slug ?: ""
        onSelectionChanged?.invoke(currentRegionSlug, currentCitySlug)

        ignoreCityChange = true
        citySpinner.isEnabled = region != null
        setSpinner(citySpinner, emptyList(), region != null)
        ignoreCityChange = false

        searchEdit.setText("")

        if (region == null) {
            showMessage("Scegli una regione per iniziare la ricerca.")
            return
        }
        showMessage("Caricamento città di ${region.name}...")
        mgr.getCitiesForRegion(region.slug, context) { list ->
            if (currentGen != lastQueryGen) return@getCitiesForRegion
            cities.clear()
            cities.add(PoiSearchManager.City("🏙️ Tutte le città", ""))
            cities.addAll(list)
            if (cities.size <= 1) {
                citySpinner.isEnabled = false
                showMessage("Nessuna città trovata per ${region.name}.")
                return@getCitiesForRegion
            }
            ignoreCityChange = true
            citySpinner.isEnabled = true
            setSpinner(citySpinner, cities.map { it.name }, true)
            ignoreCityChange = false
            citySpinner.setSelection(0, false)
            onCitySelected("")
        }
    }

    // ─── City selection ───
    private fun onCitySelected(citySlug: String) {
        if (ignoreCityChange) return
        val region = regions.getOrNull(regionSpinner.selectedItemPosition - 1) ?: run {
            showMessage("Scegli prima una regione.")
            return
        }
        lastQueryGen++
        val currentGen = lastQueryGen
        loadedPois = emptyList()
        loadingPois = true

        currentCitySlug = citySlug
        onSelectionChanged?.invoke(currentRegionSlug, currentCitySlug)
        showMessage("Caricamento POI...")

        mgr.loadPois(region.slug, citySlug, context) { pois ->
            if (currentGen != lastQueryGen) return@loadPois
            loadingPois = false
            loadedPois = pois
            if (loadedPois.isEmpty()) {
                showMessage("Nessun POI disponibile per questa selezione.")
                return@loadPois
            }
            runQuery(searchEdit.text.toString())
        }
    }

    // ─── Query ───
    private fun runQuery(query: String) {
        val q = query.trim()
        if (q.length < PoiSearchManager.MIN_QUERY_LENGTH) {
            if (q.isEmpty()) showMessage("Scrivi almeno ${PoiSearchManager.MIN_QUERY_LENGTH} caratteri per cercare.")
            return
        }
        if (loadingPois) {
            showMessage("Caricamento POI in corso...")
            return
        }
        val currentGen = ++lastQueryGen
        val pois = loadedPois
        searchExecutor.submit {
            val results = mgr.filterPois(q, pois).let { list ->
                resultFilter?.let { list.filter(it) } ?: list
            }
            handler?.post {
                if (currentGen != lastQueryGen) return@post
                renderResults(results, q)
            }
        }
    }

    /** Riaplica la query corrente (es. dopo un cambio di filtro categoria). */
    fun reapplyQuery() {
        runQuery(searchEdit.text.toString())
    }

    // ─── MODALITÀ NEARBY (GPS) ───────────────────────────────────

    /**
     * Attiva la modalità nearby: nasconde i dropdown Regione/Città e mostra
     * un header dinamico. Da chiamare prima di [loadNearby].
     */
    fun enableNearbyMode() {
        val ctx = context
        regionSpinner.visibility = GONE
        citySpinner.visibility = GONE
        searchEdit.hint = "🔍 Cerca tra i locali vicini..."
        nearbyHeader = TextView(ctx).apply {
            text = "📍 Caricamento locali vicini…"
            textSize = 12f
            setTextColor(Color.parseColor("#88FFFFFF"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackground(GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0x22FFFFFF)
            })
        }
        addView(nearbyHeader, indexOfChild(searchEdit))
        nearbyHeader?.setOnClickListener { loadNearby() }
    }

    /** (Ri)carica i POI intorno alla posizione corrente. */
    fun loadNearby(radiusMeters: Int = OsmPoiRepository.DEFAULT_RADIUS_METERS) {
        val loader = nearbyLoader
        val supplier = locationSupplier
        if (loader == null || supplier == null) {
            showMessage("Ricerca per zona non configurata.")
            return
        }
        val loc = supplier.invoke()
        if (loc == null) {
            showMessage("Posizione non disponibile. Abilita il GPS o apri la mappa.")
            nearbyHeader?.text = "📍 Posizione non disponibile — tocca per riprovare"
            return
        }
        showMessage("Caricamento locali (${radiusMeters}m)…")
        nearbyHeader?.text = "📍 ${loadedPois.size} locali disponibili"
        loader.invoke(loc.latitude, loc.longitude, radiusMeters) { pois ->
            loadedPois = pois
            nearbyHeader?.text = "📍 ${pois.size} locali vicini a te  ·  ↻"
            runQuery(searchEdit.text.toString())
            onNearbyLoaded?.invoke()
        }
    }

    private fun renderResults(results: List<PoiSearchManager.SearchResult>, query: String) {
        resultsBox.removeAllViews()
        if (results.isEmpty()) {
            showMessage("Nessun POI trovato per \"$query\".")
            return
        }
        for (r in results.take(40)) {
            val item = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                isClickable = true
                isFocusable = true
                background = darkBg(dp(4), 0x33FFFFFF)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
                    it.bottomMargin = dp(2)
                }
            }
            item.addView(TextView(context).apply {
                text = "📍 ${r.name}"
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            })
            item.addView(TextView(context).apply {
                text = r.buildingType.ifEmpty { r.poiType }
                textSize = 10f
                setTextColor(Color.parseColor("#88CCFF"))
            })
            item.setOnClickListener { onOpenPoi?.invoke(r) }
            resultsBox.addView(item)
        }
        if (results.size > 40) {
            resultsBox.addView(TextView(context).apply {
                text = "+${results.size - 40} altri..."
                textSize = 11f
                setTextColor(Color.parseColor("#88FFFFFF"))
                setPadding(dp(8), dp(6), dp(8), dp(4))
            })
        }
    }

    private var messageView: TextView? = null

    private fun showMessage(text: String) {
        messageView?.let { resultsBox.removeView(it) }
        val tv = TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor("#88FFFFFF"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        resultsBox.addView(tv)
        messageView = tv
    }
}
