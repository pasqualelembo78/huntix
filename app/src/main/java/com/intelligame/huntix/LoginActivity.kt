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
 * - Ospite: Firebase anonymous auth -> profilo locale.
 * - Google: Firebase Auth via Google ID token.
 * - Facebook: Firebase Auth via Facebook Access Token.
 * - GitHub: Firebase Auth via OAuthProvider("github.com").
 */
class LoginActivity : AppCompatActivity() {

    private var fbCallbackManager: com.facebook.CallbackManager? = null

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
            signInWithFacebook(c)
        })
        root.addView(spacer())
        root.addView(UiKit.button(c, "🐙  Continua con GitHub", "#24292e") {
            signInWithGitHub(c)
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
            PlayerProfileManager.initLocalProfile(
                context = c,
                name = name,
                onReady = { goToProfile() }
            )
        })

        setContentView(root)
    }

    // ── Google ──────────────────────────────────────────────
    private fun signInWithGoogle(context: android.content.Context) {
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
            loginAsGuest()
        }
    }

    // ── Facebook ────────────────────────────────────────────
    private fun signInWithFacebook(context: android.content.Context) {
        // Se i valori in strings.xml sono ancora placeholder,Facebook non è configurato: avvisa e non tentare.
        val fbAppId = getString(R.string.facebook_app_id)
        if (fbAppId.isBlank() || fbAppId == "123456789012345" ||
            getString(R.string.facebook_client_token).let { it.isBlank() || it == "placeholder_token_replace_me" }) {
            Toast.makeText(context, "Login Facebook non ancora configurato", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = com.facebook.CallbackManager.Factory.create()
        fbCallbackManager = cm
        val permissions = listOf("email", "public_profile")
        com.facebook.login.LoginManager.getInstance().logInWithReadPermissions(this, cm, permissions)
        cm.registerCallback(cm, object : com.facebook.FacebookCallback<com.facebook.login.LoginResult> {
            override fun onSuccess(result: com.facebook.login.LoginResult) {
                val token = result.accessToken?.token
                if (token.isNullOrBlank()) {
                    Toast.makeText(context, "Token Facebook non ricevuto", Toast.LENGTH_SHORT).show()
                    return
                }
                val credential = com.google.firebase.auth.FacebookAuthProvider.getCredential(token)
                com.google.firebase.auth.FirebaseAuth.getInstance()
                    .signInWithCredential(credential)
                    .addOnSuccessListener { res ->
                        val uid = res.user?.uid ?: ""
                        val name = result.accessToken.userId?.let { "Cacciatore FB" } ?: "Cacciatore Facebook"
                        PlayerProfileManager.initMyProfile(
                            context = this@LoginActivity,
                            name = name,
                            firebaseUid = uid,
                            isGoogleUser = false,
                            onReady = { goToProfile() },
                            onError = { msg -> Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show() }
                        )
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Auth Firebase (FB) fallita: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }

            override fun onCancel() {
                Toast.makeText(context, "Login Facebook annullato", Toast.LENGTH_SHORT).show()
            }

            override fun onError(error: com.facebook.FacebookException) {
                Toast.makeText(context, "Login Facebook fallito: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    // ── GitHub ──────────────────────────────────────────────
    private fun signInWithGitHub(context: android.content.Context) {
        val provider = com.google.firebase.auth.OAuthProvider.newBuilder("github.com").build()
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        // Se c'è già una pending result (es. dopo il browser), prova a risolverla
        val pending = auth.pendingAuthResult
        if (pending != null) {
            pending.addOnSuccessListener { res -> onGitHubSuccess(res, context) }
                .addOnFailureListener { e -> Toast.makeText(context, "GitHub fallito: ${e.message}", Toast.LENGTH_LONG).show() }
            return
        }
        auth.startActivityForSignInWithProvider(this, provider)
            .addOnSuccessListener { res -> onGitHubSuccess(res, context) }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Login GitHub fallito: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun onGitHubSuccess(result: com.google.firebase.auth.AuthResult, context: android.content.Context) {
        val uid = result.user?.uid ?: ""
        val name = result.user?.displayName ?: "Cacciatore GitHub"
        PlayerProfileManager.initMyProfile(
            context = this,
            name = name,
            firebaseUid = uid,
            isGoogleUser = false,
            onReady = { goToProfile() },
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        )
    }

    private fun goToProfile() {
        startActivity(Intent(this, ProfileSetupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

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
            .addOnSuccessListener { res -> onEmailSuccess(res, context) }
            .addOnFailureListener { e ->
                // Utente non esiste -> registrazione
                if (e is com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { res -> onEmailSuccess(res, context) }
                        .addOnFailureListener { ex ->
                            Toast.makeText(context, "Registrazione fallita: ${ex.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(context, "Login email fallito: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun onEmailSuccess(result: com.google.firebase.auth.AuthResult, context: android.content.Context) {
        val uid = result.user?.uid ?: ""
        val name = result.user?.email?.substringBefore('@')?.replaceFirstChar { it.uppercase() }
            ?: "Cacciatore Email"
        PlayerProfileManager.initMyProfile(
            context = this,
            name = name,
            firebaseUid = uid,
            isGoogleUser = false,
            onReady = { goToProfile() },
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        )
    }

    // ── Guest ───────────────────────────────────────────────
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
                    onReady = { goToProfile() },
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
        fbCallbackManager?.onActivityResult(requestCode, resultCode, data)
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
                                onReady = { goToProfile() },
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
