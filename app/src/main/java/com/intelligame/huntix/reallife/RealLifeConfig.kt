package com.intelligame.huntix.reallife

import android.os.Build

/**
 * RealLifeConfig — costanti di configurazione del layer "Real Life".
 *
 * Il backend gira su PORT=5100 (diverso da aria/5000) come da narrazione.txt.
 *
 * L'host del backend dipende dall'AMBIENTE:
 *   • Emulatore Android  → 10.0.2.2  (alias della macchina host)
 *   • Dispositivo reale  → DEVICE_BASE_URL (dominio/IP reale del server)
 *
 * 10.0.2.2 NON è raggiungibile da un telefono fisico: per questo BASE_URL è
 * calcolato a runtime in base a `isEmulator()`.
 */
object RealLifeConfig {
    /** Host usato solo su emulatore (loopback della macchina host). */
    private const val EMULATOR_BASE_URL = "http://10.0.2.2:5100"

    /**
     * Host per build destinate a dispositivi reali / produzione.
     * IP pubblico di questa macchina (backend su 0.0.0.0:5100).
     */
    const val DEVICE_BASE_URL = "http://82.165.218.56:5100"

    /** URL effettivo del backend, scelto in base all'ambiente. */
    val BASE_URL: String
        get() = if (isEmulator()) EMULATOR_BASE_URL else DEVICE_BASE_URL

    /** Identità "anonima" per device: il backend crea l'utente al primo accesso. */
    const val DEFAULT_BIRTH_DATE = "1990-01-01"

    const val PREFS_NAME = "reallife_auth"

    /** Rileva se l'app gira su emulatore Android (dove 10.0.2.2 è valido). */
    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true) ||
                Build.MODEL.contains("Android SDK", ignoreCase = true) ||
                Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
                Build.BRAND.startsWith("generic", ignoreCase = true) ||
                Build.DEVICE.startsWith("generic", ignoreCase = true) ||
                "google_sdk" == Build.PRODUCT ||
                "goldfish" == Build.HARDWARE ||
                "ranchu" == Build.HARDWARE
    }
}
