// Callback AIDL: il Stub vive nel processo app (dove gli singleton del profilo
// sono reali), il proxy nel processo :unity.
// onUnityEvent e' fire-and-forget; call e' sincrono per restituire il valore
// letto/scritto dall'app (XP, MVC, profilo, prefs...).
package com.intelligame.huntix.bridge;

interface IUnityCallback {
    oneway void onUnityEvent(String event, String json);
    String call(String method, String json);
}
