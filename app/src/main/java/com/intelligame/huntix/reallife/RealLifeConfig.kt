package com.intelligame.huntix.reallife

/**
 * RealLifeConfig — costanti di configurazione del layer "Real Life".
 *
 * Il backend gira su PORT=5100 (diverso da aria/5000) come da narrazione.txt.
 * Su emulatore Android l'host della macchina è 10.0.2.2; su dispositivo fisico
 * usare l'IP LAN della macchina che ospita il backend (es. http://192.168.x.y:5100).
 */
object RealLifeConfig {
    const val BASE_URL = "http://10.0.2.2:5100"

    // Identità "anonima" per device: il backend crea l'utente al primo accesso.
    // birth_date fisso (adulto) richiesto dalla verifica età lato server per i nuovi account.
    const val DEFAULT_BIRTH_DATE = "1990-01-01"

    const val PREFS_NAME = "reallife_auth"
}
