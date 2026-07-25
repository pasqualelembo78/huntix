package com.intelligame.huntix

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.intelligame.huntix.BuildConfig
import io.sentry.Sentry
import kotlinx.coroutines.launch

/**
 * LoginActivity — schermata di accesso.
 * - Ospite: Firebase anonymous auth -> profilo locale.
 * - Google: Firebase Auth via Google ID token (Credential Manager).
 * - GitHub: Firebase Auth via OAuthProvider("github.com").
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



        root.addView(UiKit.button(c, "🐙  Continua con GitHub", "#24292e") {
            signInWithGitHub()
        })
        root.addView(spacer())

        // ── Email / Password ─────────────────────────────────
        val emailEdit = android.widget.EditText(c).apply {
            hint = "Email"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor(UiKit.TEXT_DIM))
            background = null; setPadding(0, UiKit.dp(c, 8), 0, UiKit.dp(c, 4))
        }
        val passEdit = android.widget.EditText(c).apply {
            hint = "Password"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor(UiKit.TEXT_DIM))
            background = null; setPadding(0, UiKit.dp(c, 8), 0, UiKit.dp(c, 8))
        }
        root.addView(emailEdit)
        root.addView(passEdit)
        root.addView(UiKit.button(c, "✉️  Continua con Email", "#1E88E5") {
            signInWithEmail(c, emailEdit.text.toString(), passEdit.text.toString())
        })
        root.addView(spacer())
        root.addView(UiKit.button(c, "▶️  Gioca come Ospite", UiKit.ACCENT) {
            loginAsGuest()
        })
        root.addView(spacer())
        root.addView(UiKit.button(c, "\uD83D\uDCF1  Gioca in locale (offline)", "#6A1B9A") {
            val name = "Cacciatore${System.currentTimeMillis().rem(10000)}"
            PlayerProfileManager.saveLoginMethod(this, "local", name)
            PlayerProfileManager.initLocalProfile(
                context = c,
                name = name,
                onReady = { goToProfile() }
            )
        })

        setContentView(root)
    }

    // ── Google (Credential Manager) ──────────────────────────
    private fun signInWithGoogle(context: android.content.Context) {
        if (BuildConfig.WEB_CLIENT_ID.isBlank()) {
            Toast.makeText(context, "Google login non configurato", Toast.LENGTH_SHORT).show()
            loginAsGuest()
            return
        }
        lifecycleScope.launch {
            try {
                val credentialManager = CredentialManager.create(this@LoginActivity)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.WEB_CLIENT_ID)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val response = credentialManager.getCredential(this@LoginActivity, request)
                val credential = response.credential

                val idToken = when {
                    credential is GoogleIdTokenCredential -> credential.idToken
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
                        credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN") ?: ""
                    else -> ""
                }

                if (idToken.isNotBlank()) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    Toast.makeText(context, "Google login: token non valido", Toast.LENGTH_SHORT).show()
                }
            } catch (_: GetCredentialCancellationException) {
                // Utente ha annullato — nessun messaggio
            } catch (e: GetCredentialException) {
                Toast.makeText(context, "Login Google fallito: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Google Sign-In non disponibile: ${e.message}", Toast.LENGTH_SHORT).show()
                loginAsGuest()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        com.google.firebase.auth.FirebaseAuth.getInstance()
            .signInWithCredential(credential)
            .addOnSuccessListener { result ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val uid = result.user?.uid ?: ""
                val googleName = result.user?.displayName ?: "Cacciatore Google"
                PlayerProfileManager.saveLoginMethod(this, "google", googleName, uid, true)
                PlayerProfileManager.initMyProfile(
                    context = this,
                    name = googleName,
                    firebaseUid = uid,
                    isGoogleUser = true,
                    onReady = { goToProfile() },
                    onError = { msg ->
                        if (isFinishing || isDestroyed) return@initMyProfile
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                Toast.makeText(this, "Auth Firebase fallita: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }



    // ── GitHub ──────────────────────────────────────────────
    private fun signInWithGitHub() {
        val provider = com.google.firebase.auth.OAuthProvider.newBuilder("github.com").build()
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val pending = auth.pendingAuthResult
        if (pending != null) {
            pending.addOnSuccessListener { res -> onGitHubSuccess(res) }
                .addOnFailureListener { e -> Toast.makeText(this, "GitHub fallito: ${e.message}", Toast.LENGTH_LONG).show() }
            return
        }
        auth.startActivityForSignInWithProvider(this, provider)
            .addOnSuccessListener { res -> onGitHubSuccess(res) }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                Toast.makeText(this, "Login GitHub fallito: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun onGitHubSuccess(result: com.google.firebase.auth.AuthResult) {
        if (isFinishing || isDestroyed) return
        val uid = result.user?.uid ?: ""
        val name = result.user?.displayName ?: "Cacciatore GitHub"
        PlayerProfileManager.saveLoginMethod(this, "github", name, uid)
        PlayerProfileManager.initMyProfile(
            context = this,
            name = name,
            firebaseUid = uid,
            isGoogleUser = false,
            onReady = { goToProfile() },
            onError = { msg ->
                if (isFinishing || isDestroyed) return@initMyProfile
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun safeGoToProfile() {
        if (isFinishing || isDestroyed) return
        try {
            startActivity(Intent(this, ProfileSetupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } catch (e: Exception) { Sentry.captureException(e) }
    }
    private fun goToProfile() = safeGoToProfile()

    // ── Email / Password ───────────────────────────────────
    private fun signInWithEmail(context: android.content.Context, email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(context, "Inserisci email e password", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(context, "La password deve essere di almeno 6 caratteri", Toast.LENGTH_SHORT).show()
            return
        }
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { res -> onEmailSuccess(res) }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                if (e is com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { res -> onEmailSuccess(res) }
                        .addOnFailureListener { ex ->
                            if (isFinishing || isDestroyed) return@addOnFailureListener
                            Toast.makeText(context, "Registrazione fallita: ${ex.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(context, "Login email fallito: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun onEmailSuccess(result: com.google.firebase.auth.AuthResult) {
        if (isFinishing || isDestroyed) return
        val uid = result.user?.uid ?: ""
        val name = result.user?.email?.substringBefore('@')?.replaceFirstChar { it.uppercase() }
            ?: "Cacciatore Email"
        PlayerProfileManager.saveLoginMethod(this, "email", name, uid)
        PlayerProfileManager.initMyProfile(
            context = this,
            name = name,
            firebaseUid = uid,
            isGoogleUser = false,
            onReady = { goToProfile() },
            onError = { msg ->
                if (isFinishing || isDestroyed) return@initMyProfile
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        )
    }

    // ── Guest ───────────────────────────────────────────────
    private fun loginAsGuest() {
        val name = "Cacciatore${System.currentTimeMillis().rem(10000)}"
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val uid = result.user?.uid
                if (uid.isNullOrBlank()) {
                    Toast.makeText(this, "Auth anonima senza UID", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                PlayerProfileManager.saveLoginMethod(this, "guest", name, uid)
                PlayerProfileManager.initMyProfile(
                    context = this,
                    name = name,
                    firebaseUid = uid,
                    onReady = { goToProfile() },
                    onError = { msg ->
                        if (isFinishing || isDestroyed) return@initMyProfile
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                )
            }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                Toast.makeText(this, "Login anonimo fallito: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }
}
