package com.intelligame.huntix.ui

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.*
import com.intelligame.huntix.BaseNavActivity

/**
 * Character3DActivity — ReadyPlayerMe WebView integration.
 * Allows the player to create a 3D avatar via ReadyPlayerMe web editor.
 */
class Character3DActivity : BaseNavActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUI()
    }

    private fun buildUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0F0F2A"))
        }
        setContentView(root)

        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    // Capture avatar export URL (glb)
                    if (url.contains(".glb") || url.contains("readyplayer.me")) {
                        view.loadUrl(url)
                        return false
                    }
                    return false
                }
            }
            webChromeClient = WebChromeClient()
            // Load ReadyPlayerMe subdomain iframe HTML
            loadDataWithBaseURL(null, buildHtml(), "text/html", "UTF-8", null)
        }
        root.addView(webView)

        // Close button overlay
        val closeBtn = Button(this).apply {
            text = "< Chiudi"
            textSize = 14f; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#AA000000"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).also {
                it.gravity = android.view.Gravity.TOP or android.view.Gravity.START
                it.topMargin = dp(32); it.marginStart = dp(8)
            }
            setOnClickListener { finish() }
        }
        root.addView(closeBtn)
    }

    private fun buildHtml(): String {
        return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  body { margin: 0; background: #050A1A; }
  iframe { width: 100vw; height: 100vh; border: none; }
</style>
</head>
<body>
<iframe
  src="https://demo.readyplayer.me/avatar?frameApi"
  id="rpm-frame"
  allow="camera *; microphone *">
</iframe>
<script>
window.addEventListener('message', function(event) {
  if (event.data && event.data.indexOf('readyplayerme:') > -1) {
    var msg = JSON.parse(event.data);
    if (msg.eventName === 'v1.avatar.exported') {
      Android.onAvatarExported(msg.data.url);
    }
  }
});
</script>
</body>
</html>
        """.trimIndent()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
