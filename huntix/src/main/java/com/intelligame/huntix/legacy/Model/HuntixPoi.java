package com.intelligame.huntix.legacy.Model;

import java.io.Serializable;

/**
 * HuntixPoi — Punto di Interesse reale del progetto Huntix (dati OSM/OpenStreetMap).
 *
 * Sostituisce le "poi" sulla mappa: ogni POI reale (negozio, ristorante,
 * ospedale, palestra...) ha una pagina JSON personalizzata ([url] con
 * [pageType]="custom") oppure una pagina web ([pageType]="web").
 */
public class HuntixPoi implements Serializable {

    private static final long serialVersionUID = 1L;

    public final String id;
    public final String name;
    public final double lat;
    public final double lng;
    public final String buildingType;
    public final String poiType;
    public final String url;
    public final String pageType;

    public HuntixPoi(String id, String name, double lat, double lng,
                     String buildingType, String poiType, String url, String pageType) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.lat = lat;
        this.lng = lng;
        this.buildingType = buildingType == null ? "" : buildingType;
        this.poiType = poiType == null ? "" : poiType;
        this.url = url == null ? "" : url;
        this.pageType = pageType == null ? "" : pageType;
    }

    /** La pagina personalizzata del negozio (JSON) è disponibile. */
    public boolean hasJsonPage() {
        return !url.isEmpty() && !"web".equalsIgnoreCase(pageType);
    }

    /** Il POI apre una pagina web esterna. */
    public boolean hasWebPage() {
        return !url.isEmpty() && "web".equalsIgnoreCase(pageType);
    }

    /** Categoria leggibile (es. "Ristorante", "Ospedale"). */
    public String category() {
        if (poiType != null && !poiType.isEmpty()) return poiType;
        if (buildingType != null && !buildingType.isEmpty()) return buildingType;
        return "POI";
    }
}
