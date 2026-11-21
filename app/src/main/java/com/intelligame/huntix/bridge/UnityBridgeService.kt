package com.intelligame.huntix.bridge

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.intelligame.huntix.AppLog

/**
 * Service che vive in `:unity`: e' l'endpoint AIDL verso il processo app.
 *
 * Le chiamate Binder arrivano su thread Binder: `sendToUnity` deve essere
 * marshallato sul main looper di :unity perche' UnitySendMessage puo' toccare
 * il runtime solo dal thread dell'Activity.
 */
class UnityBridgeService : Service() {

    private val binder = object : IUnityBridge.Stub() {
        override fun sendToUnity(gameObject: String?, method: String?, arg: String?) {
            if (gameObject.isNullOrEmpty() || method.isNullOrEmpty()) return
            Handler(Looper.getMainLooper()).post {
                try {
                    StoreUnityBridge.sendLocalToUnity(gameObject, method, arg ?: "")
                } catch (t: Throwable) {
                    AppLog.w(TAG, "sendToUnity($method) failed: ${t.message}")
                }
            }
        }

        override fun registerCallback(cb: IUnityCallback?) {
            UnityIpc.callback = cb
            AppLog.i(TAG, "callback dell'app registrato in :unity")
        }

        override fun sessionEnded() {
            UnityIpc.callback = null
            AppLog.i(TAG, "sessione Unity terminata: callback dell'app rilasciato")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        private const val TAG = "UnityBridgeService"
    }
}
