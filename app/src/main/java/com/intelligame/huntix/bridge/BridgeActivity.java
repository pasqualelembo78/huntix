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
    public static final String MODE_OPENWORLD = "openworld";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        String poi = getIntent().getStringExtra(EXTRA_POI_DATA);
        android.util.Log.d("HuntixBridge", "BridgeActivity.onCreate mode=" + mode + " poiData=" + poi);
        if (mode != null) {
            com.unity3d.player.UnityPlayer.UnitySendMessage("GameManager", "OnEvent", "{\"action\":\"setMode\",\"mode\":\"" + mode + "\"}");
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
        UnityExitKillGuard.disableSelfKill(mUnityPlayer);
        super.onDestroy();
    }
}