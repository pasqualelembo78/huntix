package com.unity3d.player;

import android.app.Activity;
import android.os.Bundle;

public class UnityPlayerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UnityPlayer.currentActivity = this;
    }

    public void UnitySendMessage(String gameObject, String method, String message) {
        UnityPlayer.dispatchUnityMessage(gameObject, method, message);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (UnityPlayer.currentActivity == this) {
            UnityPlayer.currentActivity = null;
        }
    }
}
