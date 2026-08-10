package com.intelligame.huntix.legacy.Controller;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.maplibre.android.MapLibre;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.utils.BitmapUtils;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.Model.Creatura;
import com.intelligame.huntix.legacy.Model.CreaturaCatturata;
import com.intelligame.huntix.legacy.R;

public class MappaCattureActivity extends Activity implements OnMapReadyCallback {

    private static final String LAYER_CAPTURAS = "catture-layer";
    private static final String SOURCE_CAPTURAS = "catture-source";

    private MapLibreMap map;
    private MapView mapView;
    private Creatura creatura;
    private final Set<String> iconasAdicionados = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        setContentView(R.layout.activity_mappa_catture);

        mapView = findViewById(R.id.mappaCatture);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        //recupera l'intent proveniente dai dettagli del bestiario
        Intent it = getIntent();
        creatura = (Creatura) it.getSerializableExtra("creatura");
    }

    public void clickIndietro(View v) {
        finish();
    }

    private void plotarMarcadoresCreatura(Creatura creatura) {
        //Piazza Marcatori
        try {
            if(map == null || map.getStyle() == null) return;
            List<CreaturaCatturata> listaPc = GiocoSingleton.getInstance().getUtente().getCreature().get(creatura);
            List<Feature> features = new ArrayList<>();
            String iconName = addIconaAoStyle(map.getStyle(), creatura.getIcona(), "creatura_" + creatura.getNumero());

            for (CreaturaCatturata pc : listaPc) {
                Log.d("PlotarMarker", "Creatura: " + creatura.getNome() + " Lat: " + pc.getLatitude() + " Long: " + pc.getLongitude());
                features.add(featureCattura(pc, iconName));
            }
            setCatture(features);
        } catch (Exception e) {
            Log.e("PlotarMarker", "ERRORE: " + e.getMessage());
        }
    }

    private void plotarMarcadoresTodosCreatura() {
        //Piazza Tutti i Marcatori
        try {
            if(map == null || map.getStyle() == null) return;
            //recupera la map di tutte le creature già catturate dall'allenatore
            Map<Creatura, List<CreaturaCatturata>> mapPc = GiocoSingleton.getInstance().getUtente().getCreature();
            List<Feature> features = new ArrayList<>();

            for (Map.Entry<Creatura, List<CreaturaCatturata>> entry : mapPc.entrySet()) {
                String iconName = addIconaAoStyle(map.getStyle(), entry.getKey().getIcona(), "creatura_" + entry.getKey().getNumero());
                //scorre la lista di tutte le creature catturate di una specie
                for (CreaturaCatturata pc : entry.getValue()) {
                    Log.d("PlotarMarker", "Creatura: " + entry.getKey().getNome() + " Lat: " + pc.getLatitude() + " Long: " + pc.getLongitude());
                    features.add(featureCattura(pc, iconName));
                }
            }
            setCatture(features);
        } catch (Exception e) {
            Log.e("PlotarMarker", "ERRORE: " + e.getMessage());
        }
    }

    private Feature featureCattura(CreaturaCatturata pc, String iconName){
        com.google.gson.JsonObject props = new com.google.gson.JsonObject();
        props.addProperty("icon", iconName);
        props.addProperty("data", pc.getDataCattura() == null ? "" : pc.getDataCattura());
        return Feature.fromGeometry(Point.fromLngLat(pc.getLongitude(), pc.getLatitude()), props);
    }

    private void setCatture(List<Feature> features){
        GeoJsonSource src = map.getStyle().getSourceAs(SOURCE_CAPTURAS);
        if(src != null)
            src.setGeoJson(FeatureCollection.fromFeatures(features));
    }

    private String addIconaAoStyle(Style style, int drawableRes, String nome){
        if (iconasAdicionados.contains(nome)) return nome;
        try {
            android.graphics.Bitmap bmp = BitmapUtils.getBitmapFromDrawable(getResources().getDrawable(drawableRes, getTheme()));
            if (bmp != null) {
                style.addImage(nome, scalaIcona(bmp));
                iconasAdicionados.add(nome);
            }
        } catch (Exception e) {
            Log.e("MAPA_CATTURE", "Errore nell'aggiunta dell'icona " + nome + ": " + e.getMessage());
        }
        return nome;
    }

    private android.graphics.Bitmap scalaIcona(android.graphics.Bitmap src){
        if (src.getDensity() == 0) src.setDensity(getResources().getDisplayMetrics().densityDpi);
        float density = getResources().getDisplayMetrics().density;
        int maxPx = Math.max(1, Math.round(20f * density));
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxPx && h <= maxPx) return src;
        float scale = Math.min((float) maxPx / w, (float) maxPx / h);
        return android.graphics.Bitmap.createScaledBitmap(
                src, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
    }

    @Override
    public void onMapReady(MapLibreMap mapLibreMap) {
        map = mapLibreMap;
        map.setStyle("https://tiles.openfreemap.org/styles/liberty", new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(Style style) {
                style.addSource(new GeoJsonSource(SOURCE_CAPTURAS, FeatureCollection.fromFeatures(new ArrayList<Feature>())));
                style.addLayer(new SymbolLayer(LAYER_CAPTURAS, SOURCE_CAPTURAS).withProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)
                ));

                if(creatura != null){
                    //Mostra tutte le creature DI UNA SPECIE catturate sulla mappa
                    TextView txtTitoloBarra = (TextView) findViewById(R.id.txtTitoloMapCatture);
                    txtTitoloBarra.setText("Mappa das catture - " + creatura.getNome());
                    plotarMarcadoresCreatura(creatura);
                }else{
                    //Mostra tutte le creature catturate sulla mappa - accade quando la navigazione arriva dalla mappa principale
                    plotarMarcadoresTodosCreatura();
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mapView != null) mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(android.os.Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }
}
