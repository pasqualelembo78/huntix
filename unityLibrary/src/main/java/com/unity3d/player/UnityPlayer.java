package com.unity3d.player;

public class UnityPlayer {

    public static UnityPlayerActivity currentActivity;

    private static OnUnityMessageListener sMessageListener;

    public interface OnUnityMessageListener {
        void onUnityMessage(String gameObject, String method, String message);
    }

    public static void setMessageListener(OnUnityMessageListener listener) {
        sMessageListener = listener;
    }

    public static void UnitySendMessage(String gameObject, String method, String message) {
        if (currentActivity != null) {
            currentActivity.UnitySendMessage(gameObject, method, message);
        }
    }

    static void dispatchUnityMessage(String gameObject, String method, String message) {
        if (sMessageListener != null) {
            sMessageListener.onUnityMessage(gameObject, method, message);
        }
    }

    public static void pause() {
    }

    public static void resume() {
    }

    public static void quit() {
    }
}
