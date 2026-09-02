package com.intelligame.huntix.bridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.unity3d.player.UnityPlayerActivity;

public class BridgeActivity extends UnityPlayerActivity {

    public static final String EXTRA_MODE = "unity_mode";
    public static final String EXTRA_POI_DATA = "POI_DATA";
    public static final String MODE_OUTDOOR = "outdoor";
    public static final String MODE_REALLIFE = "reallife";
    public static final String MODE_INDOOR = "indoor";
    public static final String MODE_MIACITTA = "miacitta";

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
            String setModeJson = "{\"action\":\"setMode\",\"mode\":\"" + mode + "\"}";
            // Fase 6: skin personaggio scelta nel profilo (cache locale)
            String skin = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                    .getString("city_skin", null);
            if (skin != null && !skin.isEmpty()) {
                setModeJson = "{\"action\":\"setMode\",\"mode\":\"" + mode
                        + "\",\"skin\":\"" + skin + "\"}";
            }
            // Pet di compagnia scelto nel profilo (cache locale)
            String pet = getSharedPreferences("huntix_prefs", MODE_PRIVATE)
                    .getString("pet_skin", null);
            if (pet != null && !pet.isEmpty()) {
                if (skin != null && !skin.isEmpty()) {
                    setModeJson = "{\"action\":\"setMode\",\"mode\":\"" + mode
                            + "\",\"skin\":\"" + skin + "\",\"pet\":\"" + pet + "\"}";
                } else {
                    setModeJson = "{\"action\":\"setMode\",\"mode\":\"" + mode
                            + "\",\"pet\":\"" + pet + "\"}";
                }
            }
            com.unity3d.player.UnityPlayer.UnitySendMessage("GameManager", "OnEvent", setModeJson);
        }
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
        StoreUnityBridge.endUnitySession();
        UnityExitKillGuard.disableSelfKill(mUnityPlayer);
        super.onDestroy();
    }
}