package com.intelligame.huntix.legacy.poi.domain

import com.intelligame.huntix.legacy.Model.HuntixPoi
import com.intelligame.huntix.legacy.Util.HuntixPoiBridge
import com.intelligame.huntix.legacy.poi.data.PoiStore

interface PoiRenderer {
    fun render(stores: List<PoiStore>)
}

object PoiBridge {

    private var renderer: PoiRenderer? = null

    fun setRenderer(renderer: PoiRenderer) {
        this.renderer = renderer
    }

    fun setPois(stores: List<PoiStore>) {
        renderer?.render(stores)
        convert(stores).let { HuntixPoiBridge.setPois(it) }
    }

    private fun convert(stores: List<PoiStore>): List<HuntixPoi> = stores.map { s ->
        val pageType = when (s.pageType) {
            com.intelligame.huntix.legacy.poi.data.PageType.Json -> "custom"
            else -> "web"
        }
        HuntixPoi(s.id, s.name, s.lat, s.lng, s.buildingType, s.poiType, s.url ?: "", pageType)
    }
}
