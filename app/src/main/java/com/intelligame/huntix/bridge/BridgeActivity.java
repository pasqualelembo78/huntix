package com.intelligame.huntix.bridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.unity3d.player.UnityPlayerActivity;

public class BridgeActivity extends UnityPlayerActivity {

    public static final String EXTRA_MODE = "unity_mode";
    public static final String MODE_OUTDOOR = "outdoor";
    public static final String MODE_REALLIFE = "reallife";
    public static final String MODE_INDOOR = "indoor";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode != null) {
            com.unity3d.player.UnityPlayer.UnitySendMessage("GameManager", "OnEvent", "{\"action\":\"setMode\",\"mode\":\"" + mode + "\"}");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}