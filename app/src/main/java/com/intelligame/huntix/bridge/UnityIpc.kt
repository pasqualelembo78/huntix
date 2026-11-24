package com.intelligame.huntix.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import com.intelligame.huntix.AppLog

/**
 * Canale IPC fra il processo app e il runtime Unity isolato in `:unity`.
 *
 * - nel processo app: [bridge] e' il proxy del UnityBridgeService, [callback]
 *   non viene usato;
 * - in :unity: [callback] e' il proxy dell'UnityAppCallback dell'app, [bridge]
 *   non viene usato.
 *
 * Il self-kill di Unity alla fine resta attivo: uccide solo `:unity`, quindi la
 * Home (processo app) resta viva e non perde profilo/uova in volo.
 */
object UnityIpc {

    private const val TAG = "UnityIpc"
    private const val SERVICE_CLASS = "com.intelligame.huntix.bridge.UnityBridgeService"

    /** Eventi che devono girare nel processo app: i soli che leggono/scrivono
     *  profilo, inventario, prefs, POI e snapshot citta'. Tutto il resto
     *  (overlay di caricamento, toast indoor/outdoor, startActivity) resta in
     *  :unity perche' li' vivono le Activity Unity. */
    val APP_EVENTS: Set<String> = setOf(
        "CatchRequest", "PoiSelected", "PlayerPosition",
        "EggCapturedInCity", "BudgetEggSpawned", "FeatureEggHidden", "CityQualityChanged",
        "AltTouchChanged", "WorldModeChanged", "VehicleMountChanged",
        "MenuAnimationChanged", "QaConsoleChanged",
        "NpcCompanionChanged", "CompanionRecruited", "CompanionEggSpotted",
        "ActivityLogEvent",
        // Audit open source: preferenze delle migliorie addittive (Unity ->
        // app). Il ritorno viaggia nel JSON di setMode openSourceFeatures.
        "OpenSourceFeatureChanged",
        // Fase 2 · consumabili: Unity segnala l'oggetto usato indoor; Android
        // consuma la quantita' e registra luogo/coordinate nel diario.
        "ConsumableUsed",
        // Fase 2 · raccolta risorse: Miacittà segnala l'oggetto raccolto;
        // Android registra contatore, uovo posizionale e voce nel diario.
        "ResourceHarvested",
        // Fase 2 · crafting: Unity apre la UI fusione Android oppure chiede
        // direttamente la fusione sugli slot già pronti.
        "CraftRequest",
        // Fase 2 · inventario evoluto: Unity apre la collezione uova Android.
        "InventoryViewRequest",
        // Fase 2 · ricette: Unity apre il ricettario (in CraftActivity) oppure
        // chiede il solo specchio delle ricette scoperte.
        "RecipeRequest",
        "CityXpEarned", "CityPowerEarned",
        "CityGemsEarned", "CityEnergyUpdate", "PlayerReincarnated",
        "CityStateSync", "CityStateCheck", "GrowthStateSync", "GiftClaimed"
    )

    /** Vero solo dentro il processo che ospita le Activity Unity. */
    val isUnity: Boolean = try {
        Process.myProcessName().endsWith(":unity")
    } catch (_: Throwable) {
        false
    }

    /** Proxy del callback dell'app, impostato dal Service nel processo :unity. */
    @Volatile
    var callback: IUnityCallback? = null

    /** Proxy del Service Unity, impostato dal client nel processo app. */
    @Volatile
    var bridge: IUnityBridge? = null

    @Volatile
    private var appContext: Context? = null

    /** Chiamata sincrona all'app (profilo, prefs, POI...). In :unity e' l'unico
     *  modo per leggere lo stato reale: gli singleton dell'app vivono altrove. */
    fun callApp(method: String, args: String = "[]"): String {
        val cb = callback ?: run {
            AppLog.w(TAG, "callApp($method): callback app non connesso")
            return ""
        }
        return try {
            cb.call(method, args) ?: ""
        } catch (e: DeadObjectException) {
            AppLog.w(TAG, "callApp($method): processo app morto, callback azzerato")
            callback = null
            ""
        } catch (e: RemoteException) {
            AppLog.w(TAG, "callApp($method) failed: ${e.message}")
            ""
        } catch (e: Throwable) {
            AppLog.w(TAG, "callApp($method) failed: ${e.message}")
            ""
        }
    }

    /** Invio verso Unity. In :unity resta locale, nell'app passa dall'AIDL. */
    fun sendToUnity(go: String, m: String, a: String) {
        if (isUnity) {
            StoreUnityBridge.sendLocalToUnity(go, m, a)
            return
        }
        val b = bridge
        if (b == null) {
            // Se il bind e' ancora in volo non e' un errore: l'app si collega
            // all'avvio, quindi il messaggio seguente trovera' il proxy.
            AppLog.w(TAG, "sendToUnity($m): bridge :unity non connesso, messaggio scartato")
            return
        }
        try {
            b.sendToUnity(go, m, a)
        } catch (e: DeadObjectException) {
            AppLog.w(TAG, "sendToUnity($m): :unity morto, bridge azzerato")
            bridge = null
        } catch (e: RemoteException) {
            AppLog.w(TAG, "sendToUnity($m) failed: ${e.message}")
        } catch (e: Throwable) {
            AppLog.w(TAG, "sendToUnity($m) failed: ${e.message}")
        }
    }

    /** Inoltra un evento Unity al router dell'app (Bridge.onUnityMessage). */
    fun forwardUnityEvent(event: String, json: String) {
        val cb = callback ?: run {
            AppLog.w(TAG, "forwardUnityEvent($event): callback app non connesso")
            return
        }
        try {
            cb.onUnityEvent(event, json)
        } catch (e: DeadObjectException) {
            AppLog.w(TAG, "forwardUnityEvent($event): processo app morto, callback azzerato")
            callback = null
        } catch (e: RemoteException) {
            AppLog.w(TAG, "forwardUnityEvent($event) failed: ${e.message}")
        } catch (e: Throwable) {
            AppLog.w(TAG, "forwardUnityEvent($event) failed: ${e.message}")
        }
    }

    /** Chiamare solo nel processo app (da EggHuntApplication.onCreate). */
    fun bindApp(context: Context) {
        if (isUnity) return
        val ctx = context.applicationContext
        appContext = ctx
        val intent = Intent().setComponent(ComponentName(ctx.packageName, SERVICE_CLASS))
        try {
            val ok = ctx.bindService(intent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val b = IUnityBridge.Stub.asInterface(service)
                    bridge = b
                    // Il callback va registrato qui: prima del bind il Service
                    // (in :unity) non esiste e non riceverebbe nulla.
                    try {
                        b?.registerCallback(UnityAppCallback())
                        AppLog.i(TAG, "connesso a :unity, callback app registrato")
                    } catch (e: RemoteException) {
                        AppLog.w(TAG, "registerCallback failed: ${e.message}")
                    } catch (e: Throwable) {
                        AppLog.w(TAG, "registerCallback failed: ${e.message}")
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    bridge = null
                    AppLog.w(TAG, ":unity disconnesso")
                }

                override fun onBindingDied(name: ComponentName?) {
                    bridge = null
                    AppLog.w(TAG, "bind con :unity morto")
                }
            }, Context.BIND_AUTO_CREATE)
            if (!ok) AppLog.w(TAG, "bindService(:unity) rifiutato")
        } catch (e: Throwable) {
            AppLog.w(TAG, "bindService(:unity) failed: ${e.message}")
        }
    }
}
