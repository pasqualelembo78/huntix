package com.intelligame.huntix.legacy.Util.directionshelpers;

import org.maplibre.android.geometry.LatLng;

import java.util.List;

/**
 * Created by Vishal on 10/20/2018.
 * (portato da Google Maps a MapLibre: ora consegna la lista di punti della rotta)
 */
public interface TaskLoadedCallback {
    void onTaskDone(List<LatLng> points);
}
