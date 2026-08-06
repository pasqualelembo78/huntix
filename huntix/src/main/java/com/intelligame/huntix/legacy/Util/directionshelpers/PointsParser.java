package com.intelligame.huntix.legacy.Util.directionshelpers;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;
import org.maplibre.android.geometry.LatLng;

import java.util.List;

/**
 * Parsing del JSON OSRM in background, poi consegna i punti della rotta al callback.
 */
public class PointsParser extends AsyncTask<String, Integer, List<LatLng>> {
    TaskLoadedCallback taskCallback;
    String directionMode = "walking";

    public PointsParser(Context mContext, String directionMode) {
        this.taskCallback = (TaskLoadedCallback) mContext;
        this.directionMode = directionMode;
    }

    @Override
    protected List<LatLng> doInBackground(String... jsonData) {
        try {
            JSONObject jObject = new JSONObject(jsonData[0]);
            Log.d("mylog", "Rotta OSRM: " + jsonData[0]);
            return new DataParser().parse(jObject);
        } catch (Exception e) {
            Log.d("mylog", e.toString());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(List<LatLng> result) {
        if (result != null && !result.isEmpty()) {
            taskCallback.onTaskDone(result);
        } else {
            Log.d("mylog", "Rotta non disponibile");
        }
    }
}
