// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix

import android.app.Application
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.sentry.Sentry
import com.intelligame.huntix.billing.BillingManager
import com.intelligame.huntix.billing.VipManager
import com.intelligame.huntix.managers.SavedManager

class EggHuntApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // AppLog — must be first so all other init logs are captured
        AppLog.init(this)
        AppLog.installCrashHandler()
        logPreviousExitReason()

        // Firebase
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("HuntixApp", "Firebase init failed: ${e.message}")
        }

        // Crashlytics (native + Java/Kotlin crash reporting)
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        } catch (e: Exception) {
            Log.e("HuntixApp", "Crashlytics init failed: ${e.message}")
        }

        // MapLibre non richiede init globale (init in MapView.onCreate)

        // Sentry (crash reporting) — DSN from manifest meta-data
        try {
            val manifestDsn = packageManager.getApplicationInfo(packageName,
                android.content.pm.PackageManager.GET_META_DATA)
                .metaData?.getString("io.sentry.dsn") ?: ""
            if (manifestDsn.isBlank()) {
                Log.w("HuntixApp", "Sentry DSN not configured — crash reporting disabled")
            } else {
                Sentry.init { options ->
                    options.dsn = manifestDsn
                    options.isEnableAutoSessionTracking = true
                    options.tracesSampleRate = 0.1
                }
            }
        } catch (e: Exception) {
            Log.e("HuntixApp", "Sentry init failed: ${e.message}")
        }

        // Huntix Legacy: fornisce il context per il DB SQLite del gioco
        try {
            com.intelligame.huntix.legacy.Util.MyApp.initContext(this)
        } catch (e: Exception) {
            Log.e("HuntixApp", "Huntix legacy context init failed: ${e.message}")
        }

        try {
            com.intelligame.huntix.legacy.poi.creature.Persistence.init(this)
        } catch (e: Exception) {
            Log.e("HuntixApp", "Persistence init failed: ${e.message}")
        }

        // Registra il sender Unity (compatibile con GameManager.OnEvent)
        try {
            com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge.registerMessenger(
                object : com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge.Messenger {
                    override fun sendEvent(event: String, data: String) {
                        com.unity3d.player.UnityPlayer.UnitySendMessage("GameManager", "OnEvent", "$event|$data")
                    }
                    override fun openStoreJson(storeId: String, json: String) {
                        com.unity3d.player.UnityPlayer.UnitySendMessage("GameManager", "OnStoreOpened", json)
                    }
                    override fun openStoreUrl(storeId: String, url: String) {
                        com.unity3d.player.UnityPlayer.UnitySendMessage("GameManager", "OpenStoreUrl", url)
                    }
                    override fun onPoiSelected(storeId: String, lat: Double, lng: Double) {
                        com.unity3d.player.UnityPlayer.UnitySendMessage("GameManager", "OnPoiSelected", "{\"id\":\"$storeId\",\"lat\":$lat,\"lng\":$lng}")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("HuntixApp", "PoiUnityBridge register failed: ${e.message}")
        }

        // Pre-carica i POI reali Huntix (OSM) per la mappa del gioco in background
        try {
            com.intelligame.huntix.manager.PoiMapBridge.feed(this)
        } catch (e: Exception) {
            Log.e("HuntixApp", "PoiMapBridge prefeed failed: ${e.message}")
        }

        // Billing: inizializza il client e sincronizza lo stato VIP all'avvio
        try {
            BillingManager.init(this)
            VipManager.syncVipStatus(this)
        } catch (e: Exception) {
            Log.e("HuntixApp", "Billing init failed: ${e.message}")
        }

        // MVC passivi: bonus installazione + mining dalle uova schiuse
        try {
            SavedManager.accrueInstallRewards(this)
            SavedManager.accrueMiningRewards(this)
        } catch (e: Exception) {
            Log.e("HuntixApp", "MVC passive accrual failed: ${e.message}")
        }

        // Starter kit cibo: 5 Mele + 3 Peperoncini (una tantum)
        try {
            EggNutrimentManager.giveStarterKit(this)
        } catch (e: Exception) {
            Log.e("HuntixApp", "Starter kit failed: ${e.message}")
        }
    }

    /** Diagnostico crash nativo: Android (API 30+) conserva il motivo di uscita
     *  dell'istanza precedente del processo. Un crash nativo (es. SIGSEGV
     *  nell'engine Unity) NON passa dall'handler Java di AppLog, quindi questa è
     *  l'unica via per vederlo nel log esportato (AppExit: reason=CRASH_NATIVO). */
    private fun logPreviousExitReason() {
        if (Build.VERSION.SDK_INT < 30) return
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            val info = am.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull() ?: return
            val reasonName = when (info.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVO"
                android.app.ApplicationExitInfo.REASON_CRASH -> "CRASH"
                android.app.ApplicationExitInfo.REASON_ANR -> "ANR"
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                android.app.ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
                else -> "other(${info.reason})"
            }
            val signal = if (info.reason == android.app.ApplicationExitInfo.REASON_SIGNALED
                    || info.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE) info.status else null
            AppLog.i("AppExit", "uscita precedente: reason=$reasonName signal=$signal pid=${info.pid} t=${info.timestamp} desc=${info.description}")
            try {
                info.traceInputStream?.use { input ->
                    val head = input.readBytes().decodeToString().take(1500)
                    if (head.isNotBlank()) AppLog.i("AppExit", "trace: $head")
                }
            } catch (t: Throwable) {
                Log.w("HuntixApp", "exit trace dump failed: ${t.message}")
            }
        } catch (e: Exception) {
            Log.w("HuntixApp", "exit reason log failed: ${e.message}")
        }
    }
}
