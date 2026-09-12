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
                // Fase 6: skin personaggio scelta nel profilo (cache locale)
                String skin = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                        .getString("city_skin", null);
                if (skin != null && !skin.isEmpty()) j.put("skin", skin);
                // Pet di compagnia scelto nel profilo (cache locale)
                String pet = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                        .getString("pet_skin", null);
                if (pet != null && !pet.isEmpty()) j.put("pet", pet);
                // Miacitta: punto di spawn DECISO dal selettore (citta' o ultima
                // posizione salvata su Google). Il GPS del device non si usa mai.
                if (MODE_MIACITTA.equals(mode)) {
                    android.content.SharedPreferences wp = getSharedPreferences("world_game_prefs", MODE_PRIVATE);
                    float slat = wp.getFloat("spawnLat", 0f);
                    float slng = wp.getFloat("spawnLng", 0f);
                    if (Math.abs(slat) > 0.001f && Math.abs(slng) > 0.001f) {
                        java.util.Locale L = java.util.Locale.US;
                        j.put("spawnLat", String.format(L, "%.6f", slat));
                        j.put("spawnLng", String.format(L, "%.6f", slng));
                    }
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
        UnityExitKillGuard.disableSelfKill(mUnityPlayer);
        super.onDestroy();
    }
}