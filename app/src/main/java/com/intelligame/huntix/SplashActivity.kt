package com.intelligame.huntix

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SplashActivity — LAUNCHER.
 * Mostra il logo, inizializza Sentry/Analytics e smista:
 *  - profilo già presente / auto-login riuscito → HomeActivity
 *  - nessun profilo e nessun metodo salvato        → LoginActivity
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0D0620"))
        }
        root.addView(TextView(this).apply {
            text = "\uD83C\uDF08"; textSize = 72f; gravity = android.view.Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "HUNTIX"; textSize = 30f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD))
        })
        root.addView(TextView(this).apply {
            text = "Realtà Aumentata · Caccia alle Uova"; textSize = 12f
            setTextColor(Color.parseColor("#A78BFA")); gravity = android.view.Gravity.CENTER
        })
        setContentView(root)

        lifecycleScope.launch {
            delay(1200)

            val login = PlayerProfileManager.getLoginMethod(this@SplashActivity)
            val target = if (login != null && tryAutoLogin(login)) {
                HomeActivity::class.java
            } else {
                LoginActivity::class.java
            }
            startActivity(Intent(this@SplashActivity, target).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    /**
     * Tenta l'accesso automatico con l'ultimo metodo scelto.
     * - "local": profilo 100% offline, nessun Firebase.
     * - metodi Firebase (google/facebook/github/email/guest): richiede che
     *   FirebaseAuth.currentUser sia ancora valido → accesso silenzioso senza
     *   mostrare di nuovo il picker (es. Google).
     * Ritorna true solo quando il profilo è stato (ri)caricato con successo.
     */
    private suspend fun tryAutoLogin(login: Triple<String, String, String>): Boolean {
        val (method, name, uid) = login
        return if (method == "local") {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                PlayerProfileManager.initLocalProfile(this@SplashActivity, name) { cont.resume(true) {} }
            }
        } else {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUid.isNullOrBlank()) {
                false
            } else {
                val isGoogle = PlayerProfileManager.isGoogleLogin(this@SplashActivity)
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    PlayerProfileManager.initMyProfile(
                        context = this@SplashActivity,
                        name = name,
                        firebaseUid = uid.ifBlank { currentUid },
                        isGoogleUser = isGoogle,
                        onReady = { cont.resume(true) {} },
                        onError = { cont.resume(false) {} }
                    )
                }
            }
        }
    }
}
