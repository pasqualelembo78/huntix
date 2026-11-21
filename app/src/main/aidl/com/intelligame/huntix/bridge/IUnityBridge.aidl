// Bridge AIDL: il Stub vive nel processo :unity, il proxy nel processo app.
// I messaggi fire-and-forget sono oneway; registerCallback e' sincrono per
// poter confermare che il callback dell'app sia stato registrato.
package com.intelligame.huntix.bridge;

import com.intelligame.huntix.bridge.IUnityCallback;

interface IUnityBridge {
    oneway void sendToUnity(String gameObject, String method, String arg);
    void registerCallback(IUnityCallback cb);
    oneway void sessionEnded();
}
