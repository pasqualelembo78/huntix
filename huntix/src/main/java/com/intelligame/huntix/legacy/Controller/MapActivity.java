package com.intelligame.huntix.legacy.Controller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdate;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.utils.BitmapUtils;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.intelligame.huntix.legacy.Model.Apparizione;
import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.Model.HuntixPoi;
import com.intelligame.huntix.legacy.Model.InterazionePoi;
import com.intelligame.huntix.legacy.Model.Poi;
import com.intelligame.huntix.legacy.Model.Utente;
import com.intelligame.huntix.legacy.R;
import com.intelligame.huntix.legacy.Util.DatabaseSingleton;
import com.intelligame.huntix.legacy.Util.HuntixPoiBridge;
import com.intelligame.huntix.legacy.Util.directionshelpers.FetchURL;
import com.intelligame.huntix.legacy.Util.directionshelpers.TaskLoadedCallback;
import com.intelligame.huntix.legacy.poi.domain.PoiBridge;
import com.intelligame.huntix.legacy.poi.domain.PoiRenderer;

public class MapActivity extends Activity implements LocationListener, Runnable, TaskLoadedCallback, OnMapReadyCallback {

    public MapLibreMap map;
    public LocationManager lm;
    public Criteria criteria;
    public String provider;
    private MapView mapView;

    private static final String LAYER_EU = "eu-layer";
    private static final String SOURCE_EU = "eu-source";
    private static final String LAYER_CREATURAS = "creature-layer";
    private static final String SOURCE_CREATURAS = "creature-source";
    private static final String LAYER_POI = "poi-layer";
    private static final String SOURCE_POI = "poi-source";
    private static final String LAYER_ROTA = "rota-layer";
    private static final String SOURCE_ROTA = "rota-source";

    private static final float ICONA_MAX_DP = 20f;
    private static final float PERSONAGGIO_MAX_DP = 36f;

    private boolean mappaPronto = false;
    private final Set<String> iconasAdicionados = new HashSet<>();

    boolean permissao_cam = false;
    boolean permissao_local = false;
    private final int CAMERA_PERMISSION = 1;
    private final int LOCATION_PERMISSION = 2;

    public int TEMPO_REQUISICAO_LATLONG = 5000;
    public int DISTANCIA_MIN_METROS = 0;
    public int intervaloEntreSorteggiosEmMinutos = 1;
    public double distanzaMinimaCombattimento = 150.0;

    public boolean primeiraPosicao = true;
    public boolean continuaSorteando = true;

    //usato in onActivityResult
    public final static int MENU_PERFIL = 1;
    public final static int MENU_MAPA = 2;
    public final static int MENU_BESTIARIO = 3;
    public final static int MENU_UOVA = 4;

    public List<Apparizione> apparizioni;
    public Map<String, Apparizione> apparizioneMap;
    public Map<String, Poi> poiMap;
    public Map<String, HuntixPoi> huntixPoiMap;
    public String LastPoiId = null;

    public Apparizione targetCreatura = null;

    public Location posizioneAttuale, posicaoInit;

    MediaPlayer mp; //musica della mappa

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MapLibre.getInstance(this);
        setContentView(R.layout.activity_map);

        //carica la sessione legacy salvata (login effettuato in un flusso precedente)
        if (GiocoSingleton.getInstance().getUtente() == null)
            GiocoSingleton.getInstance().sessione();

        mapView = findViewById(R.id.mappa);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        //creando MediaPlayer alla creazione della activity per evitare null pointer exception
        mp = MediaPlayer.create(getBaseContext(), R.raw.tema_rota_1);

        //alloca lista e Map
        apparizioni = new ArrayList<Apparizione>();
        apparizioneMap = new HashMap<String, Apparizione>();

        //alloca la map dei poi
        poiMap = new HashMap<String, Poi>();

        //alloca la map dei POI reali Huntix (OSM)
        huntixPoiMap = new HashMap<String, HuntixPoi>();

        // Quando il feed POI (PoiMapBridge, app) completa il caricamento, riplotta
        // subito i POI reali sulla mappa (render chiamato fuori dal main thread).
        PoiBridge.setRenderer(new PoiRenderer() {
            @Override
            public void render(List<com.intelligame.huntix.legacy.poi.data.PoiStore> stores) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mappaPronto && posizioneAttuale != null)
                            plotarHuntixPois();
                    }
                });
            }
        });

        targetCreatura = null;

        //Configura la web view loader del sorteggio della creatura
        WebView webViewLoader = (WebView) findViewById(R.id.imgLoader);
        webViewLoader.loadUrl("file:///android_asset/loading.gif");
        webViewLoader.setBackgroundColor(Color.TRANSPARENT);
        webViewLoader.setVisibility(View.GONE);

        //Sceglie l'immagine del pulsante di profilo in base alla skin Kenney
        ImageButton imgProfilo = (ImageButton) findViewById(R.id.bottoneProfilo);
        Utente utente = GiocoSingleton.getInstance().getUtente();
        String citySkinBtn = getSharedPreferences("huntix_prefs", Context.MODE_PRIVATE)
                .getString("city_skin", null);
        if (citySkinBtn != null && citySkinBtn.contains("Female"))
            imgProfilo.setImageResource(R.drawable.female_profile);
        else
            imgProfilo.setImageResource(R.drawable.male_profile);

        //Configura il nome dell'utente sotto il pulsante di profilo
        TextView txtNomeUser = (TextView) findViewById(R.id.txtNomeUser);
        if (utente != null)
            txtNomeUser.setText(utente.getLogin());
        PackageManager packageManager = getPackageManager();
        boolean hasCam = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA);
        if (hasCam)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
        } else {
            configuraCriterioLocation();
            avviaGeolocalizzazione(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        try {
            //mette in pausa la musica
            if(mp != null)
                mp.pause();
        }catch (Exception e){
            Log.e("MAPA", "ERRORE: " + e.getMessage());
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        try {
            //riavvia la musica
            if(mp != null)
                mp.start();
        }catch (Exception e){
            Log.e("MAPA", "ERRORE: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        try {
            if (LastPoiId != null) {
                Poi poiVicino = poiMap.get(LastPoiId);
                if (poiVicino != null) {
                    InterazionePoi interc = GiocoSingleton.getInstance().getUltimaInterazione(poiVicino);
                }
            }
            if(mappaPronto) {
                aggiornaPoi();
            }
        }catch (Exception e){
            Log.e("RESUME", "ERRORE: " + e.getMessage());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        if (lm != null)
            lm.removeUpdates(this);
        Log.d("PROVIDER", "Provider " + provider + " parado!");

        continuaSorteando = false; //ferma il thread di sorteggio

        try {
            //restituisce la risorsa di musica al sistema
            mp.release();
        }catch (Exception e){
            Log.e("MAPA", "ERRORE: " + e.getMessage());
        }

        mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null)
            mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null)
            mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLocationChanged(Location location) {
        posizioneAttuale = location;

        if(mappaPronto){
            //aggiorna la posizione del personaggio sulla mappa
            aggiornaPersonaggio();

            if(targetCreatura != null){
                double distanzaCreatura = getDistanzaCreatura(posizioneAttuale, targetCreatura.getLatitude(), targetCreatura.getLongitude());
                double distanzaMin = distanzaMinimaCombattimento;
                if(distanzaCreatura <= distanzaMin){
                    Toast.makeText(this,"Sei già vicino a " + targetCreatura.getCreatura().getNome() + "!\n" +
                            "Prova a catturarla ora! ", Toast.LENGTH_LONG).show();
                }
            }
        }

        //centra la camera alla prima volta che ottiene la posizione
        if(primeiraPosicao) {
            primeiraPosicao = false;
            posicaoInit = location;

            if(mappaPronto){
                aggiornaPersonaggio();
                CameraUpdate c = CameraUpdateFactory.newCameraPosition(
                        new org.maplibre.android.camera.CameraPosition.Builder()
                                .target(new LatLng(location.getLatitude(), location.getLongitude()))
                                .tilt(60)
                                .zoom(18)
                                .build());
                map.animateCamera(c);
            }

            //avvia il thread di sorteggio della creatura
            new Thread(this).start();

            //avvia la musica della mappa - rota 1 quando ottiene la posizione dell'utente
            mp.setLooping(true);
            mp.start();

            plotarPoi();
        }

        //se l'allenatore si allontana 200m dall'ultimo punto in cui abbiamo aggiornato i poi
        //aggiorniamo i poi di nuovo e piazziamo le creature
        double deltaDistanza = posizioneAttuale.distanceTo(posicaoInit);
        if (deltaDistanza > 200.00) {
            posicaoInit = posizioneAttuale;
            pulisciPoi();
            plotarPoi();
        }
        else
            aggiornaPoi();
        Log.i("GPS", "NUOVA POSIZIONE");
    }

    private void aggiornaPersonaggio() {
        if(!mappaPronto || map.getStyle() == null || posizioneAttuale == null) return;
        Style style = map.getStyle();
        GeoJsonSource src = style.getSourceAs(SOURCE_EU);
        if(src == null) return;
        // Legge la skin Kenney scelta nel profilo (city_skin)
        String citySkin = getSharedPreferences("huntix_prefs", Context.MODE_PRIVATE)
                .getString("city_skin", null);
        int iconRes;
        if (citySkin != null) {
            switch (citySkin) {
                case "humanFemaleA":  iconRes = R.drawable.female; break;
                case "zombieMaleA":   iconRes = R.drawable.male;   break;  // TODO: icona zombie
                case "zombieFemaleA": iconRes = R.drawable.female; break;  // TODO: icona zombie
                default:              iconRes = R.drawable.male;   break;
            }
        } else {
            boolean masculino = GiocoSingleton.getInstance().getUtente().getSesso().equals("M");
            iconRes = masculino ? R.drawable.male : R.drawable.female;
        }
        addIconaAoStyle(style, iconRes, "personagem", PERSONAGGIO_MAX_DP);
        Point p = Point.fromLngLat(posizioneAttuale.getLongitude(), posizioneAttuale.getLatitude());
        src.setGeoJson(FeatureCollection.fromFeature(featureCom(p, "eu", "", "personagem", null)));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    public void configuraCriterioLocation() {
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        criteria = new Criteria();

        PackageManager packageManager = getPackageManager();
        boolean hasGPS = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);

        if (hasGPS) {
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            Log.i("LOCATION", "usando GPS");
        } else {
            criteria.setAccuracy(Criteria.ACCURACY_COARSE);
            Log.i("LOCATION", "usando WI-FI o dati");
        }
    }

    @SuppressLint("MissingPermission")
    public void avviaGeolocalizzazione(Context ctx) {
        //ottiene il miglior provider abilitato con il criterio
        provider = lm.getBestProvider(criteria, true);

        if (provider == null) {
            Log.e("PROVIDER", "Nessun provider trovato");
        } else {
            Log.i("PROVIDER", "È in uso il provider " + provider);

            lm.requestLocationUpdates(provider, TEMPO_REQUISICAO_LATLONG, DISTANCIA_MIN_METROS, (LocationListener) ctx);
        }
    }

    private void handleCreaturaClick(Apparizione ap, String id) {
        if (posizioneAttuale == null) return;
        double distanzaCreatura = getDistanzaCreatura(posizioneAttuale, ap.getLatitude(), ap.getLongitude());
        double distanzaMin = distanzaMinimaCombattimento;

        if (distanzaCreatura <= distanzaMin) {
            try {
                //mette in pausa la musica
                mp.pause();
                Intent it = new Intent(this, CatturaActivity.class);
                it.putExtra("creatura", ap);

                startActivity(it);

                if (ap.equals(targetCreatura)) {
                    pulisciRota();
                    targetCreatura = null;
                }
                //rimuove la creatura dal giro
                apparizioneMap.remove(id);
                pulisciMarcatori();
                plotarMarcadores();
            } catch (Exception e) {
                Log.e("CliqueMarker", "Errore: " + e.getMessage());
            }
        } else {
            if (ap.equals(targetCreatura)) {//se l'utente clicca di nuovo sulla creatura bersaglio, la rotta deve sparire
                pulisciRota();
                targetCreatura = null;
            } else {
                targetCreatura = ap;
                String url = getDirectionsUrl(new LatLng(posizioneAttuale.getLatitude(), posizioneAttuale.getLongitude()),
                        new LatLng(ap.getLatitude(), ap.getLongitude()));
                new FetchURL(MapActivity.this).execute(url);
                DecimalFormat df = new DecimalFormat("0.##");
                Toast.makeText(this, "Sei a " + df.format(distanzaCreatura) + " metri da " + ap.getCreatura().getNome() + ".\n" +
                        "Avvicinati di almeno " + df.format(distanzaCreatura - distanzaMin) + " metri!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void handlePoiClick(Poi poi, String id) {
        if (posizioneAttuale == null) return;
        double DistPoi = getDistanzaPoi(posizioneAttuale, poi.getLat(), poi.getLng());
        double distMin = distanzaMinimaCombattimento; //finché non decidiamo una distanza appropriata lasciare la stessa della battaglia
        if (DistPoi > distMin) {
            DecimalFormat df = new DecimalFormat("0.##");
            Toast.makeText(this, "Sei a " + df.format(DistPoi) + " metri da " + poi.getNome() + ".\n" + "Avvicinati di almeno " + df.format(DistPoi - distMin) + " metri!", Toast.LENGTH_LONG).show();
        } else {
            //Prende il poi equivalente a quello marcato sulla mappa e avvia lo schermo del poi
            Intent it = new Intent(this, PoiActivity.class);
            //salvare l'immagine e il poi per il recupero dei dati nell'altra activity
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            Bitmap foto = poi.getFoto();
            if (foto != null)
                foto.compress(Bitmap.CompressFormat.PNG, 80, stream);
            byte[] byteArray = stream.toByteArray();

            LastPoiId = id;
            it.putExtra("foto", byteArray);
            it.putExtra("poi", poi);
            startActivity(it);
        }
    }

    /**
     * Click su un POI reale Huntix: apre direttamente il negozio.
     * pagina JSON personalizzata → POICustomPageActivity
     * pagina web              → POIWebViewActivity
     */
    private void handleHuntixPoiClick(HuntixPoi poi) {
        if (posizioneAttuale == null) return;
        double DistPoi = getDistanzaPoi(posizioneAttuale, poi.lat, poi.lng);
        double distMin = distanzaMinimaCombattimento;
        if (DistPoi > distMin) {
            DecimalFormat df = new DecimalFormat("0.##");
            Toast.makeText(this, "Sei a " + df.format(DistPoi) + " m da " + poi.name + ".\n" +
                    "Avvicinati di almeno " + df.format(DistPoi - distMin) + " m!", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            mp.pause();
        } catch (Exception e) {
            Log.e("HUNTIX_POI", "pause musica: " + e.getMessage());
        }

        // Pagina JSON personalizzata del negozio
        if (poi.hasJsonPage()) {
            try {
                Intent it = new Intent();
                it.setClassName("com.intelligame.huntix",
                        "com.intelligame.huntix.ui.POICustomPageActivity");
                it.putExtra("json_url", poi.url);
                it.putExtra("poi_name", poi.name);
                it.putExtra("poi_type", poi.poiType);
                it.putExtra("poi_lat", poi.lat);
                it.putExtra("poi_lng", poi.lng);
                startActivity(it);
                return;
            } catch (Exception e) {
                Log.e("HUNTIX_POI", "Errore apertura POICustomPageActivity: " + e.getMessage());
            }
        }

        // Pagina web esterna
        if (poi.hasWebPage()) {
            try {
                Intent it = new Intent();
                it.setClassName("com.intelligame.huntix",
                        "com.intelligame.huntix.ui.POIWebViewActivity");
                it.putExtra("url", poi.url);
                it.putExtra("title", poi.name);
                startActivity(it);
                return;
            } catch (Exception e) {
                Log.e("HUNTIX_POI", "Errore apertura POIWebViewActivity: " + e.getMessage());
            }
        }

        Toast.makeText(this, poi.name + "\n" + poi.category(), Toast.LENGTH_LONG).show();
    }

    public void requestLocationPermission(){
        //verifica se deve spiegare il permesso
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
                || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {

            //chiede il permesso
            ActivityCompat.requestPermissions(this
                    , new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}
                    , LOCATION_PERMISSION
            );

            Toast.makeText(this
                    , "Consenti l'accesso alla posizione del dispositivo per\n" +
                            "misurare la distanza fino al luogo selezionato."
                    , Toast.LENGTH_LONG).show();

        } else {
            //chiede il permesso
            ActivityCompat.requestPermissions(this
                    , new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}
                    , LOCATION_PERMISSION
            );
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case CAMERA_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissao_cam = true;
                    Toast.makeText(this, "Permesso concesso", Toast.LENGTH_LONG).show();
                }
                else {
                    permissao_cam = true;
                    Toast.makeText(this, "Permesso necessario per usare la fotocamera", Toast.LENGTH_LONG).show();
                    Log.d("PERMISSIONE", "NON HA CONSENTITOOOOO");
                }
            }
            case LOCATION_PERMISSION: {
                if (grantResults.length > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    configuraCriterioLocation();
                    avviaGeolocalizzazione(this);
                }
            }
        }
    }

    public void pulisciPoi() {
        try {
            poiMap.clear();
            huntixPoiMap.clear();
            if(mappaPronto && map.getStyle() != null){
                GeoJsonSource src = map.getStyle().getSourceAs(SOURCE_POI);
                if(src != null)
                    src.setGeoJson(FeatureCollection.fromFeatures(new ArrayList<Feature>()));
            }
        } catch (Exception e) {
            Log.e("PulisciMarker", "ERRORE: " + e.getMessage());
        }
    }

    public void aggiornaPoi(){
        try {
            if(!mappaPronto || map.getStyle() == null || posizioneAttuale == null) return;

            // Con i POI reali Huntix attivi, riplotta semplicemente quelli
            if (HuntixPoiBridge.isEnabled()) {
                plotarHuntixPois();
                return;
            }

            Style style = map.getStyle();
            List<Feature> features = new ArrayList<>();

            for (Map.Entry<String, Poi> entry : poiMap.entrySet()) {
                Poi p = entry.getValue();

                if(p != null){
                    if(!p.getDisponibile()) {
                        Date TempoAtual = Calendar.getInstance().getTime();
                        InterazionePoi it = GiocoSingleton.getInstance().getUltimaInterazione(p);
                        if (it.getUltimoAccesso() != null) {
                            double diff = TempoAtual.getTime() - it.getUltimoAccesso().getTime();
                            int diffSec = (int) diff / (1000);
                            if (diffSec > 300) {
                                p.setDisponibile(true);
                                ContentValues valores = new ContentValues();
                                valores.put("disponibile", true);
                                DatabaseSingleton.getInstance().aggiorna("Poi", valores, "idPoi = '" + p.getID() + "'");
                            } else
                                p.setDisponibile(false);
                        }
                    }

                    double distanza = getDistanzaPoi(posizioneAttuale, p.getLat(), p.getLng());
                    double distanzaMin = distanzaMinimaCombattimento;

                    String iconName = addIconaAoStyle(style, p.getIcon(distanza < distanzaMin), "poi_" + p.getID());
                    features.add(featurePoi(p, iconName));
                }
            }

            GeoJsonSource src = style.getSourceAs(SOURCE_POI);
            if(src != null)
                src.setGeoJson(FeatureCollection.fromFeatures(features));
        } catch (Exception e) {
            Log.e("AggiornaMarker", "ERRORE: " + e.getMessage());
        }
    }

    private String getDirectionsUrl(LatLng origin, LatLng dest) {
        // Rota a piedi via OSRM (open source, nessuna API key)
        String coordinates = origin.getLongitude() + "," + origin.getLatitude() + ";" + dest.getLongitude() + "," + dest.getLatitude();
        return "https://router.project-osrm.org/route/v1/walking/" + coordinates + "?overview=full&geometries=polyline";
    }

    public void pulisciMarcatori(){
        try{
            //itera nel dizionario dei marcatori delle apparizioni
            apparizioneMap.clear();
            pulisciRota();
            targetCreatura = null;
            if(mappaPronto && map.getStyle() != null){
                GeoJsonSource src = map.getStyle().getSourceAs(SOURCE_CREATURAS);
                if(src != null)
                    src.setGeoJson(FeatureCollection.fromFeatures(new ArrayList<Feature>()));
            }
        }catch (Exception e){
            Log.e("PulisciMarker","ERRORE: " + e.getMessage());
        }
    }

    private void pulisciRota(){
        if(mappaPronto && map.getStyle() != null){
            GeoJsonSource src = map.getStyle().getSourceAs(SOURCE_ROTA);
            if(src != null)
                src.setGeoJson(FeatureCollection.fromFeatures(new ArrayList<Feature>()));
        }
    }

    private Feature featureCom(Point g, String kind, String id, String icon, String nome){
        com.google.gson.JsonObject props = new com.google.gson.JsonObject();
        props.addProperty("kind", kind);
        props.addProperty("id", id);
        props.addProperty("icon", icon);
        if (nome != null) props.addProperty("nome", nome);
        return Feature.fromGeometry(g, props);
    }

    private Feature featurePoi(Poi p, String iconName){
        return featureCom(Point.fromLngLat(p.getLng(), p.getLat()), "poi", p.getID(), iconName, p.getNome());
    }

    public void plotarPoi() {
        if(posizioneAttuale == null) return;

        // Con i POI reali Huntix (OSM) attivi, la mappa mostra quelli
        if (HuntixPoiBridge.isEnabled()) {
            plotarHuntixPois();
            return;
        }

        List<Poi> list = GiocoSingleton.getInstance().getPoi(posizioneAttuale.getLatitude(), posizioneAttuale.getLongitude());
        List<Feature> features = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Poi p = list.get(i);

            if (p.getUltimoAccesso() != null) {
                Date TempoAtual = Calendar.getInstance().getTime();
                double diff = TempoAtual.getTime() - p.getUltimoAccesso().getTime();
                double diffMinuto = diff / (1000);
                if (diffMinuto > 300) {
                    p.setDisponibile(true);
                }
            }

            String id = p.getID();
            poiMap.put(id, p);

            if(mappaPronto && map.getStyle() != null){
                double distanza = getDistanzaPoi(posizioneAttuale, p.getLat(), p.getLng());
                double distanzaMin = distanzaMinimaCombattimento;
                String iconName = addIconaAoStyle(map.getStyle(), p.getIcon(distanza < distanzaMin), "poi_" + id);
                features.add(featurePoi(p, iconName));
            }
        }
        if(mappaPronto && map.getStyle() != null){
            GeoJsonSource src = map.getStyle().getSourceAs(SOURCE_POI);
            if(src != null)
                src.setGeoJson(FeatureCollection.fromFeatures(features));
        }
    }

    /** Plotta i POI reali Huntix (negozi/edifici OSM) al posto delle poi di prova. */
    private void plotarHuntixPois() {
        if(!mappaPronto || map.getStyle() == null || posizioneAttuale == null) return;
        List<HuntixPoi> list = HuntixPoiBridge.getPois();
        List<Feature> features = new ArrayList<>();
        huntixPoiMap.clear();
        Style style = map.getStyle();
        double distanzaMin = distanzaMinimaCombattimento;

        for (int i = 0; i < list.size(); i++) {
            HuntixPoi p = list.get(i);
            huntixPoiMap.put(p.id, p);

            double distanza = getDistanzaPoi(posizioneAttuale, p.lat, p.lng);
            boolean perto = distanza < distanzaMin;
            String iconName = addIconaAoStyle(style,
                    perto ? R.drawable.poi_vicino : R.drawable.poi_lontano,
                    perto ? "huntix_poi_perto" : "huntix_poi_longe");
            features.add(featureCom(Point.fromLngLat(p.lng, p.lat),
                    "poi", p.id, iconName, p.name));
        }

        GeoJsonSource src = style.getSourceAs(SOURCE_POI);
        if(src != null)
            src.setGeoJson(FeatureCollection.fromFeatures(features));
        Log.i("HUNTIX_POI", "Piazzate " + huntixPoiMap.size() + " POI reais na mappa");
    }

    public void plotarMarcadores() {
        //Piazza Marcatori
        try {
            Apparizione [] apVet = GiocoSingleton.getInstance().getApparizioni();
            if(!mappaPronto || map.getStyle() == null) return;
            Style style = map.getStyle();
            List<Feature> features = new ArrayList<>();

            for(int i = 0; i < apVet.length; i++){
                Log.d("PlotarMarker", "Creatura: " + apVet[i].getCreatura().getNome() + " Lat: " + apVet[i].getLatitude() + " Long: " + apVet[i].getLongitude());

                String id = "pk_" + i;
                Apparizione ap = apVet[i];
                apparizioneMap.put(id, ap);

                String iconName = addIconaAoStyle(style, ap.getCreatura().getIcona(), "pkmon_" + ap.getCreatura().getNumero());
                features.add(featureCom(Point.fromLngLat(ap.getLongitude(), ap.getLatitude()),
                        "creatura", id, iconName, ap.getCreatura().getNome()));
            }

            GeoJsonSource src = style.getSourceAs(SOURCE_CREATURAS);
            if(src != null)
                src.setGeoJson(FeatureCollection.fromFeatures(features));
        }catch (Exception e){
            Log.e("PlotarMarker","ERRORE: " + e.getMessage());
        }
    }

    public double getDistanzaCreatura(Location allenatore, double lat, double lng){
        Location poke = new Location(provider);
        poke.setLatitude(lat);
        poke.setLongitude(lng);
        return allenatore.distanceTo(poke);
    }

    public double getDistanzaPoi(Location allenatore, double lat, double lng){
        Location poi = new Location(provider);
        poi.setLatitude(lat);
        poi.setLongitude(lng);
        return allenatore.distanceTo(poi);
    }

    public void calcolaLatLongMinMaxPerSorteggio(Location location){
        double kmInLongitudeDegree = 111.320 * Math.cos( location.getLatitude() / 180.0 * Math.PI);
        double radiusInKm = 0.3;
        double deltaLat = radiusInKm / 111.1;
        double deltaLong = radiusInKm / kmInLongitudeDegree;

        double minLat = location.getLatitude() - deltaLat;
        double maxLat = location.getLatitude() + deltaLat;
        double minLong = location.getLongitude() - deltaLong;
        double maxLong = location.getLongitude() + deltaLong;

        //Sorteggia le apparizioni di creatura nel gestore generale
        GiocoSingleton.getInstance().sorteggiaApparizioni(minLat, maxLat, minLong, maxLong);

        //aggiorna la mappa nel thread principale
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pulisciMarcatori();
                plotarMarcadores();
            }
        });
    }

    public void clickBestiario(View v){
        Intent it = new Intent(this, BestiarioActivity.class);
        startActivityForResult(it, MENU_BESTIARIO);
    }

    public void clickProfilo(View v){
        Intent it = new Intent(this, ProfiloActivity.class);
        startActivityForResult(it,MENU_PERFIL);
    }

    public void clickMappaCattura(View v){
        Intent it = new Intent(this, MappaCattureActivity.class);
        startActivityForResult(it, MENU_MAPA);
    }

    public void clickUovo(View v){
        Intent it = new Intent(this, UovaActivity.class);
        it.putExtra("location", posizioneAttuale);
        startActivityForResult(it, MENU_UOVA);
    }

    @Override
    public void run() {
        try {
            while (continuaSorteando) {
                Log.d("SORTEGGIO","Risvegliato il thread di sorteggio!");

                //aggiorna la mappa nel thread principale lasciando il loader visibile
                final WebView wv = findViewById(R.id.imgLoader);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        wv.setVisibility(View.VISIBLE);
                    }
                });

                //dorme 3 secondi per lasciare il loader mostrato
                TimeUnit.SECONDS.sleep(3);

                //aggiorna la mappa nel thread principale lasciando il loader invisibile
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        wv.setVisibility(View.GONE);
                    }
                });

                //Calcola il raggio rispetto alla posizione e sorteggia le creature
                calcolaLatLongMinMaxPerSorteggio(posizioneAttuale);
                TimeUnit.MINUTES.sleep(intervaloEntreSorteggiosEmMinutos);
            }
        }catch (Exception e){
            Log.e("SORTEGGIO","ERRORE: " + e.getMessage());
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == MENU_PERFIL && resultCode == MENU_PERFIL){
            //Logout
            Intent intent = new Intent(this,LoginActivity.class);
            startActivity(intent);
            finish();
        }

    }

    @Override
    public void onMapReady(MapLibreMap mapLibreMap) {
        map = mapLibreMap;
        map.setStyle("https://tiles.openfreemap.org/styles/liberty", new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(Style style) {
                configuraLayers(style);
                mappaPronto = true;
                if(posizioneAttuale != null){
                    aggiornaPersonaggio();
                }
            }
        });
    }

    private void configuraLayers(Style style){
        // Personagem
        style.addSource(new GeoJsonSource(SOURCE_EU, FeatureCollection.fromFeatures(new ArrayList<Feature>())));
        style.addLayer(new SymbolLayer(LAYER_EU, SOURCE_EU).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
        ));

        // Creature
        style.addSource(new GeoJsonSource(SOURCE_CREATURAS, FeatureCollection.fromFeatures(new ArrayList<Feature>())));
        style.addLayer(new SymbolLayer(LAYER_CREATURAS, SOURCE_CREATURAS).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
        ));

        // Poi
        style.addSource(new GeoJsonSource(SOURCE_POI, FeatureCollection.fromFeatures(new ArrayList<Feature>())));
        style.addLayer(new SymbolLayer(LAYER_POI, SOURCE_POI).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
        ));

        // Rota (polyline)
        style.addSource(new GeoJsonSource(SOURCE_ROTA, FeatureCollection.fromFeatures(new ArrayList<Feature>())));
        style.addLayer(new LineLayer(LAYER_ROTA, SOURCE_ROTA).withProperties(
                PropertyFactory.lineColor(Color.YELLOW),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
        ));

        map.addOnMapClickListener(new MapLibreMap.OnMapClickListener() {
            @Override
            public boolean onMapClick(LatLng point) {
                if(map == null) return false;
                List<Feature> features = map.queryRenderedFeatures(
                        map.getProjection().toScreenLocation(point),
                        LAYER_CREATURAS, LAYER_POI);
                for (Feature f : features) {
                    String kind = f.getStringProperty("kind");
                    String id = f.getStringProperty("id");
                    if ("creatura".equals(kind)) {
                        Apparizione ap = apparizioneMap.get(id);
                        if (ap != null) {
                            handleCreaturaClick(ap, id);
                            return true;
                        }
                    } else if ("poi".equals(kind)) {
                        HuntixPoi hp = huntixPoiMap.get(id);
                        if (hp != null) {
                            handleHuntixPoiClick(hp);
                            return true;
                        }
                        Poi p = poiMap.get(id);
                        if (p != null) {
                            handlePoiClick(p, id);
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    private String addIconaAoStyle(Style style, int drawableRes, String nome){
        return addIconaAoStyle(style, drawableRes, nome, ICONA_MAX_DP);
    }

    private String addIconaAoStyle(Style style, int drawableRes, String nome, float maxDp){
        if (iconasAdicionados.contains(nome)) return nome;
        try {
            Bitmap bmp = BitmapUtils.getBitmapFromDrawable(getResources().getDrawable(drawableRes, getTheme()));
            if (bmp != null) {
                style.addImage(nome, scalaIcona(bmp, maxDp));
                iconasAdicionados.add(nome);
            }
        } catch (Exception e) {
            Log.e("MAPA", "Errore nell'aggiunta dell'icona " + nome + ": " + e.getMessage());
        }
        return nome;
    }

    private Bitmap scalaIcona(Bitmap src, float maxDp){
        if (src.getDensity() == 0) src.setDensity(getResources().getDisplayMetrics().densityDpi);
        float density = getResources().getDisplayMetrics().density;
        int maxPx = Math.max(1, Math.round(maxDp * density));
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxPx && h <= maxPx) return src;
        float scale = Math.min((float) maxPx / w, (float) maxPx / h);
        return Bitmap.createScaledBitmap(
                src, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
    }

    @Override
    public void onTaskDone(List<LatLng> points) {
        if(!mappaPronto || map.getStyle() == null) return;
        Style style = map.getStyle();
        GeoJsonSource src = style.getSourceAs(SOURCE_ROTA);
        if(src == null) return;
        List<Point> geoPoints = new ArrayList<>();
        for (LatLng p : points) {
            geoPoints.add(Point.fromLngLat(p.getLongitude(), p.getLatitude()));
        }
        LineString line = LineString.fromLngLats(geoPoints);
        src.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(line)));
    }
}
