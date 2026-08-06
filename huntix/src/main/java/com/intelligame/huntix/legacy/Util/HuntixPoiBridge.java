package com.intelligame.huntix.legacy.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.intelligame.huntix.legacy.Model.HuntixPoi;

/**
 * HuntixPoiBridge — ponte tra l'app Huntix e la mappa.
 *
 * L'app Huntix scarica i POI reali (OSM) e li deposita qui prima di aprire
 * la mappa. La mappa (MapActivity) legge questa lista e la plotta al posto
 * delle poi di prova. Evita una dipendenza circolare tra i moduli:
 * la libreria della mappa non conosce le classi dell'app.
 */
public final class HuntixPoiBridge {

    private static volatile List<HuntixPoi> pois = Collections.emptyList();

    private HuntixPoiBridge() {
    }

    /** Imposta i POI reali da mostrare sulla mappa (null/vuota = usa il fallback). */
    public static synchronized void setPois(List<HuntixPoi> list) {
        pois = (list == null || list.isEmpty())
                ? Collections.<HuntixPoi>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(list));
    }

    /** POI reali attualmente attivi sulla mappa. */
    public static List<HuntixPoi> getPois() {
        return pois;
    }

    /** true se ci sono POI reali da mostrare al posto delle poi di prova. */
    public static boolean isEnabled() {
        return !pois.isEmpty();
    }

    /** Ripristina il comportamento originale (poi di prova). */
    public static void clear() {
        setPois(null);
    }
}
