package com.intelligame.huntix.legacy.Util.directionshelpers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.maplibre.android.geometry.LatLng;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser per la risposta JSON di OSRM (router.project-osrm.org).
 * La rotta è la polilinea encoded presente in routes[0].geometry
 * (geometries=polyline&overview=full).
 */
public class DataParser {
    public List<LatLng> parse(JSONObject jObject) {
        List<LatLng> points = new ArrayList<>();
        try {
            if (!"Ok".equals(jObject.optString("code"))) {
                return points;
            }
            JSONArray jRoutes = jObject.getJSONArray("routes");
            if (jRoutes.length() == 0) {
                return points;
            }
            String polyline = ((JSONObject) jRoutes.get(0)).getString("geometry");
            return decodePoly(polyline);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return points;
    }

    /**
     * Decodifica polilinea encoded (Google Encoded Polyline Algorithm Format).
     */
    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            poly.add(new LatLng((((double) lat / 1E5)), (((double) lng / 1E5))));
        }
        return poly;
    }
}
