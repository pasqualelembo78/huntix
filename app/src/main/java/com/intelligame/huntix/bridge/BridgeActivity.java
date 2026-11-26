package com.intelligame.huntix.bridge;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.Locale;
import com.intelligame.huntix.R;
import com.unity3d.player.UnityPlayerActivity;

public class BridgeActivity extends UnityPlayerActivity {

    public static final String EXTRA_MODE = "unity_mode";
    public static final String EXTRA_POI_DATA = "POI_DATA";
    // Stato letto dall'app prima di lanciare Unity: in :unity le prefs dell'app
    // non sono la fonte di verita', quindi gli extra hanno priorita'.
    public static final String EXTRA_SKIN = "unity_skin";
    public static final String EXTRA_PET = "unity_pet";
    public static final String EXTRA_SPAWN_LAT = "unity_spawn_lat";
    public static final String EXTRA_SPAWN_LNG = "unity_spawn_lng";
    public static final String EXTRA_TILE_CACHE_DIR = "unity_tile_cache_dir";
    public static final String MODE_OUTDOOR = "outdoor";
    public static final String MODE_REALLIFE = "reallife";
    public static final String MODE_INDOOR = "indoor";
    public static final String MODE_MIACITTA = "miacitta";

    /**
     * Miacitta: la seed city e' nascosta e i chunk OSM arrivano dopo 10-30s.
     * L'overlay resta sopra la scena Unity (che intanto builda i chunk in
     * sottofondo) finche' Unity non segnala "CityReady" (Bridge.onUnityMessage).
     * Rete di sicurezza: timeout e bottone "Salta attesa" per non bloccare mai
     * il giocatore.
     */
    private static final long CITY_LOADING_TIMEOUT_MS = 120_000L;

    private View cityLoadingOverlay;
    private ProgressBar cityProgressBar;
    private TextView cityStatusText;
    private TextView cityBytesText;
    private final Handler cityTimeoutHandler = new Handler(Looper.getMainLooper());

    private final Runnable cityTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            android.util.Log.d("HuntixBridge", "cityLoading: timeout "
                    + (CITY_LOADING_TIMEOUT_MS / 1000) + "s, rimuovo overlay d'attesa");
            dismissCityLoading();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Nuova sessione Unity: riabilita gli invii (guardia shutdown dell'uscita
        // precedente) PRIMA di qualunque UnitySendMessage.
        StoreUnityBridge.beginUnitySession();
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        String poi = getIntent().getStringExtra(EXTRA_POI_DATA);
        android.util.Log.d("HuntixBridge", "BridgeActivity.onCreate mode=" + mode + " poiData=" + poi);
        if (mode != null) {
            org.json.JSONObject j = new org.json.JSONObject();
            try {
                j.put("action", "setMode");
                j.put("mode", mode);
                // Fase 6: skin personaggio scelta nel profilo. prima gli extra
                // passati dall'app, poi le prefs come fallback.
                String skin = getIntent().getStringExtra(EXTRA_SKIN);
                if (skin == null || skin.isEmpty()) {
                    skin = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                            .getString("city_skin", null);
                }
                if (skin != null && !skin.isEmpty()) j.put("skin", skin);
                // Pet di compagnia scelto nel profilo (stessa priorita').
                String pet = getIntent().getStringExtra(EXTRA_PET);
                if (pet == null || pet.isEmpty()) {
                    pet = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                            .getString("pet_skin", null);
                }
                if (pet != null && !pet.isEmpty()) j.put("pet", pet);
                // Miacitta: punto di spawn DECISO dal selettore (citta' o ultima
                // posizione salvata su Google). Il GPS del device non si usa mai.
                if (MODE_MIACITTA.equals(mode)) {
                    android.content.SharedPreferences wp = getSharedPreferences("world_game_prefs", MODE_PRIVATE);
                    float slat = getIntent().getFloatExtra(EXTRA_SPAWN_LAT, 0f);
                    if (slat == 0f) slat = wp.getFloat("spawnLat", 0f);
                    float slng = getIntent().getFloatExtra(EXTRA_SPAWN_LNG, 0f);
                    if (slng == 0f) slng = wp.getFloat("spawnLng", 0f);
                    if (Math.abs(slat) > 0.001f && Math.abs(slng) > 0.001f) {
                        java.util.Locale L = java.util.Locale.US;
                        j.put("spawnLat", String.format(L, "%.6f", slat));
                        j.put("spawnLng", String.format(L, "%.6f", slng));
                    }
                    String tileDir = getIntent().getStringExtra(EXTRA_TILE_CACHE_DIR);
                    if (tileDir == null || tileDir.isEmpty()) {
                        tileDir = wp.getString("tile_cache_dir", null);
                    }
                    if (tileDir != null && !tileDir.isEmpty()) j.put("tileCacheDir", tileDir);
                    // Profilo grafico scelto in Miacitta e salvato da Android:
                    // rimandato a Unity per il ripristino Android -> Unity.
                    String quality = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                            .getString("city_quality", null);
                    if (quality != null && !quality.isEmpty()) j.put("cityQuality", quality);
                    // Sorgente input touch salvata da Android → Unity.
                    boolean altTouch = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                            .getBoolean("alt_touch", false);
                    if (altTouch) j.put("altTouch", true);
                    // CITTA' GIOCO: preferenza scelta in MiCitta' e salvata da
                    // Android. Rimandata a Unity prima del caricamento della
                    // scena City (il flag blocca lo streaming OSM a runtime).
                    String worldMode = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                            .getString("world_mode", "real");
                    if ("fictional".equals(worldMode)) j.put("worldMode", "fictional");
                    // Ultimo mezzo guidato salvato da Android -> Unity (posto
                    // a bordo e tipo). Non fa salire il player in auto.
                    String vehicleMount = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                            .getString("vehicle_mount", null);
                    if (vehicleMount != null && !vehicleMount.isEmpty()) {
                        j.put("vehicleMount", vehicleMount);
                    }
                    // Abilità dei mestieri (fonte di verità Android): i livelli
                    // (zero per i nuovi giocatori) e i punti da spendere arrivano
                    // a Unity prima del caricamento della scena City.
                    com.intelligame.huntix.gamification.CitySkillManager skills =
                            com.intelligame.huntix.gamification.CitySkillManager.INSTANCE;
                    j.put("citySkills", skills.exportLevelsString(this));
                    j.put("citySkillPoints", skills.points(this));
                    // Innesti MIT dell'audit: preferenze scelte in MiCitta' e
                    // salvate da Android, rimandate a Unity in ripristino passivo.
                    boolean menuAnimation = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                            .getBoolean("menu_animation", false);
                    if (menuAnimation) j.put("menuAnimation", true);
                    boolean qaConsole = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                            .getBoolean("qa_console", false);
                    if (qaConsole) j.put("qaConsole", true);
                    // Compagno NPC: preferenza e ultimo pedone salvati da Android,
                    // ripristinati passivamente in GameManager (nessun uovo all'avvio).
                    boolean npcCompanion = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                            .getBoolean("npc_companion", false);
                    if (npcCompanion) {
                        j.put("npcCompanion", true);
                        String compName = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                                .getString("npc_companion_name", null);
                        String compId = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                                .getString("npc_companion_id", null);
                        if (compName != null && !compName.isEmpty()) j.put("npcCompanionName", compName);
                        if (compId != null && !compId.isEmpty()) j.put("npcCompanionId", compId);
                    }
                    // Fase 1 · registro attività: Unity deve sapere quante voci
                    // (uova con luogo + attività) Android conserva nel diario.
                    // Viene passato il solo contatore, non la cronologia: il
                    // dettaglio resta nel profilo Android, dove e' consultabile.
                    j.put("activityLogCount",
                            com.intelligame.huntix.managers.EggWhereLog.INSTANCE.count(this));
                    // Audit open source: rimanda a Unity solo le preferenze
                    // delle migliorie addittive (id=1/0), mai il diario o le
                    // uova: Android resta l'unico proprietario dello stato.
                    android.content.SharedPreferences osPrefs =
                            getSharedPreferences("huntix_prefs", MODE_PRIVATE);
                    StringBuilder osFlags = new StringBuilder();
                    for (java.util.Map.Entry<String, ?> e : osPrefs.getAll().entrySet()) {
                        String k = e.getKey();
                        if (k.startsWith("huntix_os_") && e.getValue() instanceof Boolean) {
                            if (osFlags.length() > 0) osFlags.append(',');
                            osFlags.append(k.substring("huntix_os_".length()))
                                    .append(((Boolean) e.getValue()) ? "=1" : "=0");
                        }
                    }
                    j.put("openSourceFeatures", osFlags.toString());
                    // Grafica cinematografica: secondo gruppo di preferenze,
                    // rimandato a Unity con chiave dedicata (huntix_gfx_*).
                    StringBuilder gfxFlags = new StringBuilder();
                    for (java.util.Map.Entry<String, ?> e : osPrefs.getAll().entrySet()) {
                        String k = e.getKey();
                        if (k.startsWith("huntix_gfx_") && e.getValue() instanceof Boolean) {
                            if (gfxFlags.length() > 0) gfxFlags.append(',');
                            gfxFlags.append(k.substring("huntix_gfx_".length()))
                                    .append(((Boolean) e.getValue()) ? "=1" : "=0");
                        }
                    }
                    j.put("cinematicFeatures", gfxFlags.toString());
                    // Dettagli naturali: terzo gruppo di preferenze visive,
                    // rimandato a Unity con chiave dedicata (huntix_veg_*).
                    // Chi non ha ancora scelto non ha pref: Unity mantiene i
                    // dettagli attivi di default.
                    StringBuilder vegFlags = new StringBuilder();
                    for (java.util.Map.Entry<String, ?> e : osPrefs.getAll().entrySet()) {
                        String k = e.getKey();
                        if (k.startsWith("huntix_veg_") && e.getValue() instanceof Boolean) {
                            if (vegFlags.length() > 0) vegFlags.append(',');
                            vegFlags.append(k.substring("huntix_veg_".length()))
                                    .append(((Boolean) e.getValue()) ? "=1" : "=0");
                        }
                    }
                    j.put("vegetationFeatures", vegFlags.toString());
                    // Mandato §13: aree globali e attività di MiCittà tornano a
                    // Unity come ripristino passivo (contatori, nessun premio).
                    j.put("globalAreas",
                            com.intelligame.huntix.managers.GlobalAreas.INSTANCE.exportFlags(this));
                    // Fase 2 · consumabili: solo lo specchio dell'esca attiva e
                    // il numero di aromi disponibili. Nessun premio, nessun
                    // uovo generato su un ripristino passivo.
                    String baitSnapshot = com.intelligame.huntix.managers.EggBaitManager.INSTANCE.snapshot(this);
                    if (baitSnapshot != null && !baitSnapshot.isEmpty()) {
                        org.json.JSONObject bait = new org.json.JSONObject(baitSnapshot);
                        j.put("activeBaitId", bait.optString("activeBaitId", ""));
                        j.put("activeBaitBonus", bait.optDouble("bonus", 1.0));
                        j.put("baitCount", bait.optInt("count", 0));
                    }
                    // Prestazioni adattive: Android e' il proprietario del toggle
                    // e del livello qualita'. Rimandati a Unity in setMode come
                    // ripristino passivo (nessun uovo, nessun evento di ritorno).
                    android.content.SharedPreferences perfPrefs =
                            getSharedPreferences("huntix_prefs", MODE_PRIVATE);
                    String perfQuality = perfPrefs.getString("city_quality", "medium");
                    if (perfQuality == null || perfQuality.isEmpty()) perfQuality = "medium";
                    org.json.JSONObject adaptive = new org.json.JSONObject();
                    adaptive.put("enabled", perfPrefs.getBoolean("adaptive_performance", true));
                    adaptive.put("quality", perfQuality);
                    j.put("adaptivePerformance", adaptive);
                    // Fase 2 · raccolta risorse: solo lo specchio dei contatori.
                    // Nessun uovo generato e nessun premio su un ripristino.
                    org.json.JSONObject harvest =
                            com.intelligame.huntix.managers.EggResourceHarvestManager.INSTANCE.snapshot(this);
                    j.put("resourceHarvestCount", harvest.optInt("resourceHarvestCount", 0));
                    j.put("resourceHarvestKinds", harvest.optInt("resourceHarvestKinds", 0));
                    j.put("resourceHarvestDetail", harvest.optString("resourceHarvestDetail", ""));
                    // Fase 2 · crafting: solo contatori del wrapper additivo;
                    // nessuna uovo generata su un ripristino passivo.
                    org.json.JSONObject craft =
                            com.intelligame.huntix.managers.EggFusionCraftManager.INSTANCE.snapshot(this);
                    j.put("fusionCraftCount", craft.optInt("fusionCraftCount", 0));
                    j.put("fusionCraftLastLevel", craft.optInt("fusionCraftLastLevel", 0));
                    j.put("fusionCraftLastRarity", craft.optString("fusionCraftLastRarity", ""));
                    // Fase 2 · inventario evoluto: solo contatori e preferenza
                    // di vista; nessun uovo generato sul ripristino.
                    org.json.JSONObject inventory =
                            com.intelligame.huntix.managers.EggInventoryViewManager.INSTANCE.snapshot(this);
                    j.put("inventoryCount", inventory.optInt("inventoryCount", 0));
                    j.put("inventoryTeamCount", inventory.optInt("inventoryTeamCount", 0));
                    j.put("inventorySortMode", inventory.optString("inventorySortMode", "recent"));
                    // Fase 2 · ricette: solo contatori di scoperta; nessuna
                    // ricetta viene "usata" e nessun uovo generato all'avvio.
                    org.json.JSONObject recipes =
                            com.intelligame.huntix.managers.EggFusionRecipeManager.INSTANCE.snapshot(this);
                    j.put("recipeDiscoveredCount", recipes.optInt("recipeDiscoveredCount", 0));
                    j.put("recipeLastId", recipes.optString("recipeLastId", ""));
                    j.put("recipeUses", recipes.optInt("recipeUses", 0));
                }
            } catch (org.json.JSONException e) {
                android.util.Log.w("HuntixBridge", "setMode json: " + e.getMessage());
            }
            String setModeJson = j.toString();
            android.util.Log.d("HuntixBridge", "setModeJson=" + setModeJson);
            com.unity3d.player.UnityPlayer.UnitySendMessage("GameManager", "OnEvent", setModeJson);
            // Copre l'attesa della build dei chunk dentro Unity: mostra lo
            // splash sopra la scena finche' i chunk non sono pronti.
            if (MODE_MIACITTA.equals(mode)) {
                showCityLoading();
            }
        }
    }

    /**
     * Overlay di caricamento di Miacitta sopra la scena Unity: splash screen
     * center-crop (stesso aspetto del dialog di preload della Home), scrim in
     * basso con barra indeterminata e testo. addContentView piazza il layout
     * sopra il player Unity (SurfaceView), quindi la build dei chunk avviene
     * in sottofondo mentre il giocatore vede il caricamento.
     */
    private void showCityLoading() {
        if (cityLoadingOverlay != null) return;
        android.util.Log.d("HuntixBridge", "showCityLoading: overlay attesa chunk Unity");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ImageView splash = new ImageView(this);
        splash.setImageResource(R.drawable.splashscreen);
        splash.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout scrim = new LinearLayout(this);
        scrim.setOrientation(LinearLayout.VERTICAL);
        scrim.setGravity(Gravity.CENTER);
        scrim.setPadding(dp(24), dp(18), dp(24), dp(16));
        GradientDrawable grad = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xCC000000});
        grad.setCornerRadius(dp(16));
        scrim.setBackground(grad);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        bar.setMax(100);
        bar.setProgress(0);
        scrim.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
        cityProgressBar = bar;

        TextView status = new TextView(this);
        status.setText("Caricamento città…");
        status.setTextSize(13f);
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(8), 0, 0);
        scrim.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cityStatusText = status;

        TextView bytesText = new TextView(this);
        bytesText.setText("");
        bytesText.setTextSize(12f);
        bytesText.setTextColor(0xFFB0B0B0);
        bytesText.setGravity(Gravity.CENTER);
        bytesText.setPadding(0, dp(4), 0, 0);
        scrim.addView(bytesText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cityBytesText = bytesText;

        TextView skip = new TextView(this);
        skip.setText("Salta attesa");
        skip.setTextSize(13f);
        skip.setTextColor(0xFF87CEEB);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(0, dp(8), 0, 0);
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissCityLoading();
            }
        });
        scrim.addView(skip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        addContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cityLoadingOverlay = root;

        cityTimeoutHandler.removeCallbacks(cityTimeoutRunnable);
        cityTimeoutHandler.postDelayed(cityTimeoutRunnable, CITY_LOADING_TIMEOUT_MS);
    }

    /**
     * Aggiorna l'overlay di caricamento con l'avanzamento reale dei chunk:
     * barra 0-100, fase ("Mappa"/"Costruzione"), sezione in costruzione e
     * byte letti (KB/MB reali). Chiamato da Unity via "CityProgress" (main
     * thread: StoreUnityBridge lo delega con runOnUiThread).
     */
    public void updateCityProgress(String phase, String section, int percent, long bytes) {
        if (cityLoadingOverlay == null) return;
        if (cityProgressBar != null) cityProgressBar.setProgress(percent);
        if (cityStatusText != null) {
            String label = "Caricamento città…";
            if (phase != null && !phase.isEmpty()) {
                label = phase;
                if ("Costruzione".equals(phase) && section != null && !section.isEmpty()) {
                    label += " · " + section;
                }
            }
            cityStatusText.setText(label + " — " + percent + "%");
        }
        if (cityBytesText != null) cityBytesText.setText(formatBytes(bytes) + " caricati");
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ITALY, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024L) {
            return String.format(Locale.ITALY, "%.0f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    /** Rimuove l'overlay di caricamento (chiamato da Unity via CityReady o dal
     *  pulsante "Salta attesa" o dal timeout di sicurezza). */
    public void dismissCityLoading() {
        cityTimeoutHandler.removeCallbacks(cityTimeoutRunnable);
        if (cityLoadingOverlay == null) return;
        ViewParent parent = cityLoadingOverlay.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(cityLoadingOverlay);
        }
        cityLoadingOverlay = null;
        android.util.Log.d("HuntixBridge", "dismissCityLoading: overlay rimosso");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    /**
     * Chiude la Activity in modo sicuro: mette in pausa il renderer Unity e
     * attende ~350ms lo svuotamento della coda buffer prima di distruggere la
     * surface con finish(). Senza la pausa il teardown puo colpire la surface
     * con transazioni in volo (BLASTBufferQueue dtor) e il processo viene
     * ucciso (SIG 9) su emulatore ARM-translated.
     */
    public void pauseUnityThenFinish() {
        android.util.Log.d("HuntixBridge", "pauseUnityThenFinish: pausa renderer Unity");
        if (mUnityPlayer != null) {
            try {
                mUnityPlayer.pause();
                android.util.Log.d("HuntixBridge", "pauseUnityThenFinish: pausa ok, finish tra 250ms");
            } catch (Throwable t) {
                android.util.Log.w("HuntixBridge", "pauseUnityThenFinish: pause fallita", t);
            }
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                android.util.Log.d("HuntixBridge", "pauseUnityThenFinish: finish()");
                try {
                    BridgeActivity.this.finish();
                } catch (Throwable t) {
                    android.util.Log.w("HuntixBridge", "pauseUnityThenFinish: finish fallita", t);
                }
            }
        }, 250);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // Fine sessione Unity su QUALUNQUE percorso di distruzione (uscita
        // volontaria, swipe-away, sistema): blocca gli invii futuri a Unity e
        // ferma il tracking GPS. Con l'engine smontato un messaggio in volo
        // e' SIGSEGV del processo (che resta vivo per la Home).
        dismissCityLoading();
        StoreUnityBridge.endUnitySession();
        // UnityExitKillGuard non si chiama piu': il self-kill di Unity ora uccide
        // solo il processo :unity, la Home resta viva. Vedi UnityBridgeService.
        super.onDestroy();
    }
}