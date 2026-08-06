package com.intelligame.huntix.legacy.poi.data

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

enum class PageType { Json, Url }

data class PoiStore(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double,
    @SerializedName("buildingType") val buildingType: String,
    @SerializedName("poiType") val poiType: String,
    @SerializedName("url") val url: String?,
    @SerializedName("pageType") val pageType: PageType
)

class PoiStoreDeserializer : JsonDeserializer<PoiStore> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): PoiStore = json.asJsonObject.let { o ->
        PoiStore(
            id = o.get("id")?.asString ?: "",
            name = o.get("name")?.asString ?: "",
            lat = o.get("lat")?.asDouble ?: 0.0,
            lng = o.get("lng")?.asDouble ?: 0.0,
            buildingType = o.get("buildingType")?.asString ?: "",
            poiType = o.get("poiType")?.asString ?: "",
            url = o.get("url")?.takeIf { !it.isJsonNull }?.asString,
            pageType = run {
                val v = o.get("pageType")?.asString ?: "url"
                when (v.lowercase()) {
                    "json" -> PageType.Json
                    else -> PageType.Url
                }
            }
        )
    }
}
