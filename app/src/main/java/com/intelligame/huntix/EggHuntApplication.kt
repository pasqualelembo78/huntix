// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix

import android.app.Application
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.sentry.Sentry
import com.intelligame.huntix.billing.BillingManager
import com.intelligame.huntix.billing.VipManager
import com.intelligame.huntix.bridge.UnityAppContext
import com.intelligame.huntix.bridge.UnityIpc
import com.intelligame.huntix.managers.SavedManager

class EggHuntApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Il runtime Unity gira in :unity: li' NON devono partire i gestori
        // dell'app (profilo, prefs, billing, preload), che restano nel processo
        // principale. Entrambi i processi hanno pero' bisogno del contesto.
        val isUnity = try {
            android.os.Process.myProcessName().endsWith(":unity")
        } catch (_: Throwable) {
            false
        }
        UnityAppContext.context = applicationContext

        // AppLog — must be first so all other init logs are captured.
        // Il processo Unity ha un file dedicato: nessuna rotazione, altrimenti
        // i due processi si cancellerebbero a vicenda le sessioni.
        AppLog.init(this, if (isUnity) "_unity" else "", rotate = !isUnity)
        AppLog.installCrashHandler()
        logPreviousExitReason()
        AppLog.risorse(this, "avvio")
        if (isUnity) {
            AppLog.i("UnityIpc", "processo unity avviato")
        } else {
            // Connessione AIDL verso il Service in :unity; il callback dell'app
            // viene registrato dentro onServiceConnected.
            UnityIpc.bindApp(this)
        }

        // Firebase. Gli errori di init vanno in AppLog (viewer in-app), non
        // solo in logcat: e' l'unico modo per vedere perche' un SIGSEGV nativo
        // non arriva a Crashlytics senza usare adb/logcat.
        try {
            FirebaseApp.initializeApp(this)
            AppLog.i("CrashReport", "Firebase inizializzato")
        } catch (e: Exception) {
            AppLog.e("CrashReport", "Firebase init failed: ${e.message}", e)
        }

        // Crashlytics (native + Java/Kotlin crash reporting)
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            AppLog.i("CrashReport", "Crashlytics: raccolta abilitata (Java + NDK)")
        } catch (e: Exception) {
            AppLog.e("CrashReport", "Crashlytics init failed: ${e.message}", e)
        }

        // MapLibre non richiede init globale (init in MapView.onCreate)

        // Sentry (crash reporting) — DSN from manifest meta-data
        val manifestDsn = try {
            packageManager.getApplicationInfo(packageName,
                android.content.pm.PackageManager.GET_META_DATA)
                .metaData?.getString("io.sentry.dsn") ?: ""
        } catch (e: Throwable) {
            AppLog.e("CrashReport", "Sentry DSN non leggibile: ${e.message}", e)
            ""
        }
        try {
            if (manifestDsn.isBlank()) {
                AppLog.w("CrashReport", "Sentry DSN assente: crash reporting Sentry disattivato")
            } else {
                Sentry.init { options ->
                    options.dsn = manifestDsn
                    options.isEnableAutoSessionTracking = true
                    options.tracesSampleRate = 0.1
                }
                AppLog.i("CrashReport", "Sentry inizializzato (DSN configurato)")
            }
        } catch (e: Exception) {
            AppLog.e("CrashReport", "Sentry init failed: ${e.message}", e)
        }

        // Huntix Legacy: fornisce il context per il DB SQLite del gioco
        try {
            com.intelligame.huntix.legacy.Util.MyApp.initContext(this)
        } catch (e: Exception) {
            AppLog.e("HuntixApp", "Huntix legacy context init failed: ${e.message}")
        }

        try {
            com.intelligame.huntix.legacy.poi.creature.Persistence.init(this)
        } catch (e: Exception) {
            AppLog.e("HuntixApp", "Persistence init failed: ${e.message}")
        }

        // Tutto cio' che tocca profilo/prefs/uova/billing gira SOLO nel processo
        // app: in :unity gli invii verso Unity passano per l'AIDL del Service.
        if (!isUnity) {
            // Registra il sender Unity (compatibile con GameManager.OnEvent).
            // Gli invii passano dalla guardia di shutdown: con l'engine Unity in
            // smontaggio (uscita da MiAcitma/Indoor) un UnitySendMessage e' crash
            // nativo. Ora la guardia e' varcata solo dal processo app via IPC.
            try {
                com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge.registerMessenger(
                    object : com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge.Messenger {
                        override fun sendEvent(event: String, data: String) {
                            com.intelligame.huntix.bridge.StoreUnityBridge.sendToUnityIfAlive("GameManager", "OnEvent", "$event|$data")
                        }
                        override fun openStoreJson(storeId: String, json: String) {
                            com.intelligame.huntix.bridge.StoreUnityBridge.sendToUnityIfAlive("GameManager", "OnStoreOpened", json)
                        }
                        override fun openStoreUrl(storeId: String, url: String) {
                            com.intelligame.huntix.bridge.StoreUnityBridge.sendToUnityIfAlive("GameManager", "OpenStoreUrl", url)
                        }
                        override fun onPoiSelected(storeId: String, lat: Double, lng: Double) {
                            com.intelligame.huntix.bridge.StoreUnityBridge.sendToUnityIfAlive("GameManager", "OnPoiSelected", "{\"id\":\"$storeId\",\"lat\":$lat,\"lng\":$lng}")
                        }
                    }
                )
            } catch (e: Exception) {
                AppLog.e("HuntixApp", "PoiUnityBridge register failed: ${e.message}")
            }

            // Pre-carica i POI reali Huntix (OSM) per la mappa del gioco in background
            try {
                com.intelligame.huntix.manager.PoiMapBridge.feed(this)
            } catch (e: Exception) {
                AppLog.e("HuntixApp", "PoiMapBridge prefeed failed: ${e.message}")
            }

            // Preload tile Miacitta in background fin dall'avvio: la geo della tile
            // di spawn viene generata/scaricata mentre l'utente e' in Home, cosi'
            // all'ingresso in Miacitta il primo chunk e' gia' visibile (cache-first).
            try {
                com.intelligame.huntix.bridge.CityTilePreloader.preload(this)
            } catch (e: Exception) {
                AppLog.w("HuntixApp", "CityTilePreloader preload failed: ${e.message}")
            }

            // Billing: inizializza il client e sincronizza lo stato VIP all'avvio
            try {
                BillingManager.init(this)
                VipManager.syncVipStatus(this)
            } catch (e: Exception) {
                AppLog.e("HuntixApp", "Billing init failed: ${e.message}")
            }

            // MVC passivi: bonus installazione + mining dalle uova schiuse
            try {
                SavedManager.accrueInstallRewards(this)
                SavedManager.accrueMiningRewards(this)
            } catch (e: Exception) {
                AppLog.e("HuntixApp", "MVC passive accrual failed: ${e.message}")
            }

            // Starter kit cibo: 5 Mele + 3 Peperoncini (una tantum)
            try {
                EggNutrimentManager.giveStarterKit(this)
            } catch (e: Exception) {
                AppLog.e("HuntixApp", "Starter kit failed: ${e.message}")
            }
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
            // Fino a 3 uscite: un SIGSEGV intermittente non e' distinguibile
            // da un EXIT_SELF se si vede solo l'ultima voce dello storico.
            val infos = am.getHistoricalProcessExitReasons(packageName, 0, 3)
            if (infos.isEmpty()) {
                AppLog.i("AppExit", "nessuna uscita precedente registrata")
                return
            }
            AppLog.i("AppExit", "storico uscite disponibili: ${infos.size}")
            for ((index, info) in infos.withIndex()) {
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
                val hasTrace = try { info.traceInputStream != null } catch (_: Throwable) { false }
                AppLog.i("AppExit", "uscita ${index + 1}/${infos.size}: reason=$reasonName signal=$signal" +
                        " signame=${signalName(signal)} pid=${info.pid} t=${info.timestamp}" +
                        " desc=${info.description} dump=$hasTrace")

                // Il trace va solo per l'uscita piu' recente: tre trace da
                // decine di KB renderebbero illeggibile il viewer in-app.
                if (index != 0) continue
                // Su API 30+ lo stack nativo (`#00 pc ...`) NON passa
                // dall'handler Java: arriva da traceInputStream. Se Android non
                // l'ha popolato, Crashlytics/Sentry non hanno nulla da inviare.
                val trace = try {
                    info.traceInputStream?.use { input ->
                        input.readBytes().decodeToString()
                    }
                } catch (t: Throwable) {
                    AppLog.w("HuntixApp", "exit trace read failed: ${t.message}")
                    null
                }
                if (!trace.isNullOrBlank()) {
                    AppLog.i("AppExit", "trace (nativo, ${trace.length} byte):")
                    // Blocchi ~8KB a fine riga: una riga unica di decine di KB
                    // sarebbe illeggibile e spezzarla perderebbe i frame.
                    val lines = trace.split("\n")
                    val sb = StringBuilder()
                    for (l in lines) {
                        if (sb.length > 0 && sb.length + l.length > 8192) {
                            AppLog.i("AppExit", sb.toString())
                            sb.setLength(0)
                        }
                        sb.append(l).append('\n')
                    }
                    if (sb.isNotEmpty()) AppLog.i("AppExit", sb.toString())
                } else {
                    AppLog.i("AppExit", "trace: NESSUNO (traceInputStream vuoto/non disponibile - processo terminato senza dump)")
                }
            }
        } catch (e: Exception) {
            AppLog.w("HuntixApp", "exit reason log failed: ${e.message}")
        }
    }

    /** Nome del segnale POSIX corrispondente a un exit status (SIGSEGV=11, ...). */
    private fun signalName(signal: Int?): String? = when (signal) {
        1 -> "SIGHUP"
        2 -> "SIGINT"
        3 -> "SIGQUIT"
        4 -> "SIGILL"
        5 -> "SIGTRAP"
        6 -> "SIGABRT"
        7 -> "SIGBUS"
        8 -> "SIGFPE"
        9 -> "SIGKILL"
        11 -> "SIGSEGV"
        13 -> "SIGPIPE"
        14 -> "SIGALRM"
        15 -> "SIGTERM"
        31 -> "SIGSYS"
        else -> null
    }
}
