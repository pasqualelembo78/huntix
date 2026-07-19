package com.intelligame.huntix

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SplashActivity — LAUNCHER.
 * Mostra il logo, inizializza Sentry/Analytics e smista:
 *  - profilo già presente  → HomeActivity
 *  - nessun profilo        → LoginActivity
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
            val target = if (PlayerProfileManager.myProfile != null) {
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
}
