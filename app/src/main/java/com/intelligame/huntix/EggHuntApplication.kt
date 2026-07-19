package com.intelligame.huntix

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import io.sentry.Sentry

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


    }
}
