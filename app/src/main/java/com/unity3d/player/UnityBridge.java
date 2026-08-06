package com.unity3d.player;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.intelligame.huntix.bridge.Bridge;
import com.intelligame.huntix.bridge.BridgeActivity;

public class UnityBridge {

    private static UnityBridge sInstance;
    private UnityPlayerActivity mUnityPlayer;

    public static void Initialize(UnityPlayerActivity unityPlayer) {
        if (sInstance == null) {
            sInstance = new UnityBridge();
        }
        sInstance.mUnityPlayer = unityPlayer;
    }

    public static void SetMode(String mode) {
        if (sInstance != null) {
            Context context = sInstance.mUnityPlayer;
            Intent intent = new Intent(context, BridgeActivity.class);
            intent.putExtra(BridgeActivity.EXTRA_MODE, mode);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static void SendMessageToAndroid(String eventName, String jsonData) {
        if (sInstance != null && sInstance.mUnityPlayer != null) {
            Bridge.onUnityMessage(eventName, jsonData);
        }
    }

    public static void LoadData() {
        if (sInstance != null && sInstance.mUnityPlayer != null) {
            String data = Bridge.loadData();
            UnityPlayer.UnitySendMessage("GameManager", "OnEvent", data);
        }
    }

    public static void SaveData(String json) {
        if (sInstance != null && sInstance.mUnityPlayer != null) {
            Bridge.saveData(json);
        }
    }

    public static void ShowToast(String message) {
        if (sInstance != null && sInstance.mUnityPlayer != null) {
            Toast.makeText(sInstance.mUnityPlayer, message, Toast.LENGTH_SHORT).show();
        }
    }

    public static void QuitToAndroid() {
        if (sInstance != null && sInstance.mUnityPlayer != null) {
            Intent intent = new Intent(sInstance.mUnityPlayer, BridgeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            sInstance.mUnityPlayer.startActivity(intent);
        }
    }
}
