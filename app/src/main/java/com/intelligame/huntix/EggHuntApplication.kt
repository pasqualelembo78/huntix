package com.intelligame.huntix

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import io.sentry.Sentry
import com.intelligame.huntix.billing.BillingManager
import com.intelligame.huntix.billing.VipManager
import com.intelligame.huntix.managers.SavedManager

class EggHuntApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("HuntixApp", "Firebase init failed: ${e.message}")
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
                    options.isEnableAutoSessionTracking = true
                    options.tracesSampleRate = 0.1
                }
            }
        } catch (e: Exception) {
            Log.e("HuntixApp", "Sentry init failed: ${e.message}")
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
            EggFoodManager.giveStarterKit(this)
        } catch (e: Exception) {
            Log.e("HuntixApp", "Starter kit failed: ${e.message}")
        }
    }
}
