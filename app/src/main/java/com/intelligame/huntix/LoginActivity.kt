package com.intelligame.huntix

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * LoginActivity — schermata di accesso.
 * - Ospite: crea profilo locale via PlayerProfileManager e va al setup.
 * - Google / Facebook: placeholder (richiede wiring Credentials/SDK login).
 */
class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#0D0620"))
            setPadding(UiKit.dp(c, 28), UiKit.dp(c, 64), UiKit.dp(c, 28), UiKit.dp(c, 32))
        }

        root.addView(TextView(c).apply {
            text = "\uD83C\uDF08"; textSize = 64f; gravity = android.view.Gravity.CENTER
        })
        root.addView(TextView(c).apply {
            text = "Benvenuto in Huntix"; textSize = 22f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(c, 8), 0, UiKit.dp(c, 4))
        })
        root.addView(TextView(c).apply {
            text = "Accedi per salvare i tuoi progressi"; textSize = 12f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM)); gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 32))
        })

        fun spacer() = android.view.View(c).apply {
            layoutParams = LinearLayout.LayoutParams(1, UiKit.dp(c, 12))
        }

        root.addView(UiKit.button(c, "\uD83D\uDC64  Continua con Google", "#DB4437") {
            signInWithGoogle(c)
        })
        root.addView(spacer())
        root.addView(UiKit.button(c, "\uD83D\uDCAC  Continua con Facebook", "#4267B2") {
            Toast.makeText(c, "Login Facebook in arrivo", Toast.LENGTH_SHORT).show()
        })
        root.addView(spacer())
        root.addView(UiKit.button(c, "▶️  Gioca come Ospite", UiKit.ACCENT) {
            loginAsGuest()
        })

        setContentView(root)
    }

    private fun signInWithGoogle(context: android.content.Context) {
        // Google Sign-In via Credential Manager (modern approach)
        // Requires WEB_CLIENT_ID in BuildConfig (from keystore.properties)
        try {
            val signInIntent = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(
                this,
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                )
                    .requestIdToken(BuildConfig.WEB_CLIENT_ID)
                    .requestEmail()
                    .build()
            ).signInIntent
            @Suppress("DEPRECATION")
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
        } catch (e: Exception) {
            Toast.makeText(context, "Google Sign-In non disponibile", Toast.LENGTH_SHORT).show()
            // Fallback to guest
            loginAsGuest()
        }
    }

    private fun loginAsGuest() {
        val name = "Cacciatore${System.currentTimeMillis().rem(10000)}"
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid.isNullOrBlank()) {
                    Toast.makeText(this, "Auth anonima senza UID", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                PlayerProfileManager.initMyProfile(
                    context = this,
                    name = name,
                    firebaseUid = uid,
                    onReady = {
                        startActivity(Intent(this, ProfileSetupActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    },
                    onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Login anonimo fallito: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    @Deprecated("Use ActivityResult API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                    com.google.firebase.auth.FirebaseAuth.getInstance()
                        .signInWithCredential(credential)
                        .addOnSuccessListener { result ->
                            val uid = result.user?.uid ?: ""
                            val googleName = account.displayName ?: "Cacciatore Google"
                            PlayerProfileManager.initMyProfile(
                                context = this,
                                name = googleName,
                                firebaseUid = uid,
                                isGoogleUser = true,
                                onReady = {
                                    startActivity(Intent(this, ProfileSetupActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    })
                                    finish()
                                },
                                onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                            )
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Auth Firebase fallita: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Token Google non ricevuto", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Login Google fallito: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }
}
