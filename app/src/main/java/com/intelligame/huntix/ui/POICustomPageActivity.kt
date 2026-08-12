package com.intelligame.huntix.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.reallife.LocalNeeds
import com.intelligame.huntix.reallife.PoiJsonFactory
import com.intelligame.huntix.UiKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class POICustomPageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_JSON_URL = "json_url"
        const val EXTRA_JSON_INLINE = "json_inline"
        const val EXTRA_POI_NAME = "poi_name"
        const val EXTRA_POI_TYPE = "poi_type"
        const val EXTRA_POI_LAT = "poi_lat"
        const val EXTRA_POI_LNG = "poi_lng"
        const val EXTRA_POI_BUILDING_TYPE = "poi_building_type"
    }

    private var needs = mutableMapOf<String, Float>()
    private var poiName = ""
    private var poiType = ""
    private var poiBuildingType = ""
    private var jsonUrl = ""
    private var inlineJson = ""
    private var poiLat = 0.0
    private var poiLng = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val jsonUrl = intent.getStringExtra(EXTRA_JSON_URL) ?: ""
        val inlineJson = intent.getStringExtra(EXTRA_JSON_INLINE) ?: ""
        if (jsonUrl.isBlank() && inlineJson.isBlank()) { finish(); return }
        val poiName = intent.getStringExtra(EXTRA_POI_NAME) ?: ""
        val poiType = intent.getStringExtra(EXTRA_POI_TYPE) ?: ""
        poiBuildingType = intent.getStringExtra(EXTRA_POI_BUILDING_TYPE) ?: ""
        this.jsonUrl = jsonUrl
        this.inlineJson = inlineJson
        this.poiName = poiName
        this.poiType = poiType
        this.poiLat = intent.getDoubleExtra(EXTRA_POI_LAT, 0.0)
        this.poiLng = intent.getDoubleExtra(EXTRA_POI_LNG, 0.0)
        needs = LocalNeeds.load(this).toMutableMap()

        val root = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(content)

        // Top bar
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 12, 14, 12)
            setBackgroundColor(0xFF263238.toInt())
            addView(TextView(this@POICustomPageActivity).apply {
                text = "\u2190 "
                textSize = 20f
                setTextColor(Color.parseColor(UiKit.ACCENT))
                isClickable = true
                setOnClickListener { finish() }
            })
            addView(TextView(this@POICustomPageActivity).apply {
                text = poiName
                textSize = 16f
                setTextColor(Color.WHITE)
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        })

        // Loading indicator
        val loader = ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            isIndeterminate = true
        }
        content.addView(loader)

        setContentView(root)

        // Fetch and render JSON
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // url "osm:..." → pagina JSON sintetica (POI live senza pagina dedicata)
                val jsonText = when {
                    inlineJson.isNotBlank() -> inlineJson
                    jsonUrl.startsWith("osm:") ->
                        PoiJsonFactory.build(jsonUrl, poiName, poiBuildingType, poiType, poiLat, poiLng).toString()
                    else -> httpGet(jsonUrl)
                }
                if (jsonText != null) {
                    val config = JSONObject(jsonText)
                    withContext(Dispatchers.Main) {
                        content.removeView(loader)
                        renderPage(content, config)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        content.removeView(loader)
                        content.addView(TextView(this@POICustomPageActivity).apply {
                            text = "Errore caricamento pagina"
                            setTextColor(Color.RED)
                            gravity = Gravity.CENTER
                            setPadding(0, 40, 0, 0)
                        })
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    content.removeView(loader)
                    content.addView(TextView(this@POICustomPageActivity).apply {
                        text = "Errore: ${e.message}"
                        setTextColor(Color.RED)
                        gravity = Gravity.CENTER
                        setPadding(0, 40, 0, 0)
                    })
                }
            }
        }
    }

    private fun renderPage(content: LinearLayout, config: JSONObject) {
        // Banner
        if (config.has("banner")) {
            val banner = config.getJSONObject("banner")
            val bgColor = try { Color.parseColor(banner.optString("color", "#37474F")) } catch (_: Exception) { 0xFF37474F.toInt() }
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 24)
                setBackgroundColor(bgColor)
                if (banner.has("icon")) {
                    addView(TextView(this@POICustomPageActivity).apply {
                        text = banner.getString("icon")
                        textSize = 48f
                    })
                }
                if (banner.has("title")) {
                    addView(TextView(this@POICustomPageActivity).apply {
                        text = banner.getString("title")
                        textSize = 20f
                        setTextColor(Color.WHITE)
                        typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                        gravity = Gravity.CENTER
                    })
                }
                if (banner.has("subtitle")) {
                    addView(TextView(this@POICustomPageActivity).apply {
                        text = banner.optString("subtitle", "")
                        textSize = 13f
                        setTextColor(0xBBFFFFFF.toInt())
                        gravity = Gravity.CENTER
                    })
                }
            })
        }

        // Sections
        val sections = config.optJSONArray("sections")
        if (sections != null) {
            for (i in 0 until sections.length()) {
                val section = sections.getJSONObject(i)
                renderSection(content, section)
            }
        }

        // Entra nel negozio: apre lo store 3D Unity (supermarket open source).
        // Se l'AAR Unity non è disponibile, usa la vista nativa StoreIndoorActivity.
        // Mostrato solo per i POI "store" (config.store=true, default per le
        // pagine custom); i POI non commerciali (parchi, fontane, scuole…)
        // mostrano solo le sezioni (es. link OSM) senza pulsante 3D.
        if (config.optBoolean("store", true)) {
        content.addView(Button(this).apply {
            text = "\uD83D\uDEAA  Entra nel negozio (3D)"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.parseColor(UiKit.GREEN))
            setOnClickListener {
                     val banner = config.optJSONObject("banner")
                val inferred = inferBuildingType(poiBuildingType.ifBlank { poiType })
                val poiJson = JSONObject().apply {
                    put("id", poiName)
                    put("name", poiName)
                    put("type", inferred)
                    put("buildingType", inferred)
                    put("json_url", jsonUrl)
                }.toString()
                try {
                    // Solo l'AAR Unity reale include NativeLoader: se assente
                    // (build senza Unity) si ripiega sulla vista nativa.
                    val hasRealUnity = try {
                        Class.forName("com.unity3d.player.NativeLoader")
                        true
                    } catch (_: ClassNotFoundException) {
                        false
                    }
                    if (hasRealUnity) {
                        val intent = Intent(this@POICustomPageActivity, IndoorActivity::class.java).apply {
                            putExtra(IndoorActivity.EXTRA_POI_DATA, poiJson)
                            putExtra("unity_mode", "indoor")
                        }
                        startActivity(intent)
                    } else {
                        val intent = Intent(this@POICustomPageActivity, StoreIndoorActivity::class.java).apply {
                            putExtra(StoreIndoorActivity.EXTRA_POI_NAME, poiName)
                            putExtra(StoreIndoorActivity.EXTRA_POI_TYPE, poiType)
                            putExtra(StoreIndoorActivity.EXTRA_JSON_URL, jsonUrl)
                        }
                        startActivity(intent)
                    }
                } catch (_: Exception) {
                    val intent = Intent(this@POICustomPageActivity, StoreIndoorActivity::class.java).apply {
                        putExtra(StoreIndoorActivity.EXTRA_POI_NAME, poiName)
                        putExtra(StoreIndoorActivity.EXTRA_POI_TYPE, poiType)
                        putExtra(StoreIndoorActivity.EXTRA_JSON_URL, jsonUrl)
                    }
                    startActivity(intent)
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 12, 16, 8)
            }
        })
        }
    }

    private fun renderSection(content: LinearLayout, section: JSONObject) {
        val type = section.optString("type", "text")
        val title = section.optString("title", "")

        when (type) {
            "text" -> {
                if (title.isNotEmpty()) {
                    content.addView(sectionTitle(title))
                }
                content.addView(TextView(this).apply {
                    text = section.optString("content", "")
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setPadding(16, 4, 16, 16)
                })
            }

            "menu" -> {
                if (title.isNotEmpty()) {
                    content.addView(sectionTitle(title))
                }
                val items = section.optJSONArray("items")
                if (items != null) {
                    content.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(12, 0, 12, 12)
                        for (j in 0 until items.length()) {
                            val item = items.getJSONObject(j)
                            addView(menuItemView(item))
                        }
                    })
                }
            }

            "link" -> {
                content.addView(Button(this).apply {
                    text = "${section.optString("emoji", "\uD83D\uDD17")}  $title"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setBackgroundColor(0xFF455A64.toInt())
                    val urlStr = section.optString("url", "")
                    setOnClickListener {
                        if (urlStr.isNotBlank()) {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlStr)))
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(16, 4, 16, 4)
                    }
                })
            }

            "action" -> {
                content.addView(Button(this).apply {
                    text = "${section.optString("emoji", "")}  $title"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setBackgroundColor(Color.parseColor(UiKit.ACCENT))
                    val action = section.optJSONObject("action")
                    setOnClickListener {
                        if (action != null) {
                            val needKey = action.optString("need", "hunger")
                            val gain = action.optDouble("gain", 10.0).toFloat()
                            needs = LocalNeeds.applyAction(this@POICustomPageActivity, needKey, gain)
                                .toMutableMap()
                            Toast.makeText(
                                this@POICustomPageActivity,
                                "${section.optString("emoji", "")} $title: +${gain.toInt()}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(16, 4, 16, 4)
                    }
                })
            }

            "divider" -> {
                content.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply {
                        setMargins(16, 8, 16, 8)
                    }
                    setBackgroundColor(0x33FFFFFF.toInt())
                })
            }
        }
    }

    private fun sectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            setPadding(16, 16, 16, 4)
        }
    }

    private fun menuItemView(item: JSONObject): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 6, 8, 6)
            setBackgroundColor(0x0FFFFFFF.toInt())

            // Emoji
            if (item.has("emoji")) {
                addView(TextView(this@POICustomPageActivity).apply {
                    text = item.getString("emoji")
                    textSize = 22f
                    setPadding(0, 0, 10, 0)
                })
            }

            // Name + price
            addView(LinearLayout(this@POICustomPageActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@POICustomPageActivity).apply {
                    text = item.optString("name", "")
                    textSize = 14f
                    setTextColor(Color.WHITE)
                })
                if (item.has("desc")) {
                    addView(TextView(this@POICustomPageActivity).apply {
                        text = item.optString("desc", "")
                        textSize = 11f
                        setTextColor(0xAAFFFFFF.toInt())
                    })
                }
            })

            // Price
            if (item.has("price")) {
                addView(TextView(this@POICustomPageActivity).apply {
                    text = item.optString("price", "")
                    textSize = 14f
                    setTextColor(Color.parseColor(UiKit.ACCENT))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                })
            }

            // Click to consume
            setOnClickListener {
                val action = item.optJSONObject("action")
                if (action != null) {
                    val needKey = action.optString("need", "hunger")
                    val gain = action.optDouble("gain", 10.0).toFloat()
                    needs = LocalNeeds.applyAction(this@POICustomPageActivity, needKey, gain)
                        .toMutableMap()
                    Toast.makeText(
                        this@POICustomPageActivity,
                        "${item.optString("emoji", "")} ${item.optString("name", "")}: +${gain.toInt()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun httpGet(url: String): String? {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) return null
            return conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            return null
        }
    }

    /** Deriva il buildingType Unity (es. "SUPERMARKET"/"RESTAURANT") da poiType/buildingType OSM
     *  così IndoorManager → StoreBuilder costruisce il layout corretto nella scena Indoor. */
    private fun inferBuildingType(raw: String): String {
        val t = raw.lowercase(Locale.getDefault())
        return when {
            t.isEmpty() -> "SUPERMARKET"                 // default: supermercato Kenney
            t.contains("ristorante") || t.contains("restaurant") || t.contains("pizzeria") -> "RESTAURANT"
            t.contains("palestra") || t.contains("gym") || t.contains("fitness") || t.contains("yoga") -> "GYM"
            t.contains("hospital") || t.contains("ospedale") || t.contains("clinica") || t.contains("medico") -> "HOSPITAL"
            t.contains("bar") || t.contains("caffè") || t.contains("caffe") || t.contains("coffee") ||
                t.contains("café") || t.contains("pasticceria") || t.contains("gelateria") || t.contains("enoteca") -> "BAR"
            t.contains("libreria") || t.contains("library") || t.contains("biblioteca") -> "LIBRARY"
            else -> "SUPERMARKET"
        }
    }
}
