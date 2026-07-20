package com.intelligame.huntix

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import io.sentry.Sentry
import com.intelligame.huntix.billing.BillingManager
import com.intelligame.huntix.billing.VipManager

class EggHuntApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("HuntixApp", "Firebase init failed: ${e.message}")
        }

        // Sentry (crash reporting) — DSN from manifest meta-data
        try {
            Sentry.init { options ->
                options.isEnableAutoSessionTracking = true
                options.tracesSampleRate = 0.1
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
    }
}
