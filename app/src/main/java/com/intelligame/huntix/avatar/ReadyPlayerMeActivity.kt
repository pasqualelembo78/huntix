package com.intelligame.huntix.avatar

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.R
import com.intelligame.huntix.BaseNavActivity

/**
 * ReadyPlayerMeActivity — WebView per la creazione dell'avatar RPM.
 *
 * Apre il creator RPM in un WebView; quando l'utente finalizza l'avatar,
 * intercetta l'URL del modello .glb e lo restituisce all'activity chiamante.
 *
 * Uso:
 *   ReadyPlayerMeActivity.launch(this, REQUEST_RPM_AVATAR)
 *
 * Nel onActivityResult:
 *   val glbUrl = data?.getStringExtra(ReadyPlayerMeActivity.EXTRA_AVATAR_URL)
 *
 * Parametri ottimizzazione RPM applicati automaticamente:
 *  - meshLod=1         → mesh semplificata
 *  - textureSizeLimit=1024 → texture ≤1024px
 *  - textureAtlas=1024 → atlas singolo
 *  - morphTargets=none → niente blend shapes (risparmio memoria)
 *  - pose=A            → T-pose standard per accessori
 */
class ReadyPlayerMeActivity : BaseNavActivity() {

    companion object {
        private const val TAG = "ReadyPlayerMeActivity"
        const val EXTRA_AVATAR_URL = "rpm_avatar_url"
        const val EXTRA_AVATAR_ID  = "rpm_avatar_id"
        private const val RPM_SUBDOMAIN = "intelligame" // cambia col tuo subdomain RPM
        private const val RPM_URL = "https://$RPM_SUBDOMAIN.readyplayer.me/avatar" +
            "?frameApi&clearCache"

        /** Parametri di ottimizzazione per il modello scaricato */
        const val GLB_OPTIMIZE_PARAMS =
            "?meshLod=1" +
            "&textureSizeLimit=1024" +
            "&textureAtlas=1024" +
            "&morphTargets=none" +
            "&pose=A" +
            "&quality=low"

        fun launch(activity: Activity, requestCode: Int) {
            val intent = Intent(activity, ReadyPlayerMeActivity::class.java)
            activity.startActivityForResult(intent, requestCode)
        }
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Layout programmatico ─────────────────────────────────
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        progressBar = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            isIndeterminate = true
        }

        // ── Barra superiore con pulsante chiudi ──────────────────
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#16213E"))
            setPadding(16, 8, 16, 8)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleTv = TextView(this).apply {
            text = "Crea il tuo Avatar"
            setTextColor(Color.WHITE)
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER_VERTICAL
        }

        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }

        topBar.addView(titleTv)
        topBar.addView(closeBtn)

        root.addView(webView)
        root.addView(progressBar)
        root.addView(topBar)
        setContentView(root)

        // ── Configura WebView ────────────────────────────────────
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android) Huntix/1.0"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = android.view.View.GONE
                // Inietta il listener JavaScript per catturare l'URL avatar
                injectAvatarExportListener()
            }
        }

        // Intercetta i messaggi da RPM iframe
        webView.addJavascriptInterface(RPMBridge(), "AndroidBridge")

        webView.loadUrl(RPM_URL)
        Log.d(TAG, "Caricamento RPM: $RPM_URL")
    }

    /**
     * Inietta JS che ascolta il postMessage di RPM e
     * passa l'URL dell'avatar al bridge Android.
     */
    private fun injectAvatarExportListener() {
        val js = """
            (function() {
                window.addEventListener('message', function(event) {
                    try {
                        var data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
                        
                        // RPM invia un messaggio con source "readyplayerme" e eventName "v1.avatar.exported"
                        if (data.source === 'readyplayerme') {
                            if (data.eventName === 'v1.avatar.exported') {
                                var avatarUrl = data.data.url;
                                if (avatarUrl && avatarUrl.endsWith('.glb')) {
                                    AndroidBridge.onAvatarExported(avatarUrl);
                                }
                            }
                        }
                    } catch(e) {
                        // Ignora messaggi non-RPM
                    }
                });
                
                // Invia messaggio di subscribe al frame RPM
                var iframe = document.querySelector('iframe');
                if (iframe) {
                    iframe.contentWindow.postMessage(
                        JSON.stringify({
                            target: 'readyplayerme',
                            type: 'subscribe',
                            eventName: 'v1.**'
                        }),
                        '*'
                    );
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    /**
     * Bridge JS → Android: riceve l'URL dell'avatar esportato.
     */
    inner class RPMBridge {
        @JavascriptInterface
        fun onAvatarExported(avatarUrl: String) {
            Log.d(TAG, "Avatar esportato: $avatarUrl")

            // Estrai l'ID avatar dall'URL (es: https://models.readyplayer.me/AVATAR_ID.glb)
            val avatarId = avatarUrl
                .substringAfterLast("/")
                .substringBefore(".glb")

            // Aggiungi parametri di ottimizzazione all'URL
            val optimizedUrl = avatarUrl + GLB_OPTIMIZE_PARAMS

            runOnUiThread {
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_AVATAR_URL, optimizedUrl)
                    putExtra(EXTRA_AVATAR_ID, avatarId)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            setResult(Activity.RESULT_CANCELED)
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
