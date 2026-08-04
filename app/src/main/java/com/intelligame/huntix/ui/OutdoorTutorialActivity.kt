package com.intelligame.huntix.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.R
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.manager.OutdoorManager

class OutdoorTutorialActivity : AppCompatActivity() {

    private var currentStep = 0
    private val totalSteps = 4
    private lateinit var stepContent: FrameLayout
    private lateinit var nextBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1040"))
        }

        val indicator = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, UiKit.dp(this@OutdoorTutorialActivity, 24), 0, 0)
        }
        root.addView(indicator)

        stepContent = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(stepContent)

        nextBtn = TextView(this).apply {
            id = View.generateViewId()
            text = "Avanti →"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.btn_accent)
            setPadding(UiKit.dp(this@OutdoorTutorialActivity, 24),
                UiKit.dp(this@OutdoorTutorialActivity, 12),
                UiKit.dp(this@OutdoorTutorialActivity, 24),
                UiKit.dp(this@OutdoorTutorialActivity, 12))
            isClickable = true
            setOnClickListener {
                if (currentStep < totalSteps - 1) {
                    currentStep++
                    showStep()
                } else {
                    finishTutorial()
                }
            }
        }
        root.addView(nextBtn)

        val skipBtn = TextView(this).apply {
            text = "Salta"
            textSize = 14f
            setTextColor(Color.parseColor("#88FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(this@OutdoorTutorialActivity, 16), 0, UiKit.dp(this@OutdoorTutorialActivity, 16), 0)
            isClickable = true
            setOnClickListener { finishTutorial() }
        }
        root.addView(skipBtn)

        setContentView(root)
        showStep()
    }

    private fun showStep() {
        stepContent.removeAllViews()

        val stepData = listOf(
            Triple("🎯", "Caccia alle Uova", "Cammina per la città e trova uova magiche nascoste nei dintorni. Più ti avvicini, più il radar indica la direzione."),
            Triple("📱", "Modalità AR", "Attiva la vista AR per vedere le uova nel mondo reale. Segui la freccia e avvicinati per catturarle."),
            Triple("⚡", "Cattura e Ricompense", "Quando sei vicino, premi il pulsante Cattura. Uova rare danno più MVP e XP. Raccogli tutte le rarità!"),
            Triple("🏆", "Eventi e Sfide", "Partecipa agli eventi settimanali per bonus moltiplicatori. Controlla il calendario per gli orari.")
        )

        val (emoji, title, desc) = stepData[currentStep]

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(UiKit.dp(this@OutdoorTutorialActivity, 24),
                UiKit.dp(this@OutdoorTutorialActivity, 32),
                UiKit.dp(this@OutdoorTutorialActivity, 24),
                UiKit.dp(this@OutdoorTutorialActivity, 24))
        }

        card.addView(TextView(this).apply {
            text = emoji
            textSize = 64f
            gravity = Gravity.CENTER
        })

        card.addView(TextView(this).apply {
            text = title
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setPadding(0, UiKit.dp(this@OutdoorTutorialActivity, 16), 0, 0)
            gravity = Gravity.CENTER
        })

        card.addView(TextView(this).apply {
            text = desc
            textSize = 15f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(UiKit.dp(this@OutdoorTutorialActivity, 8), UiKit.dp(this@OutdoorTutorialActivity, 12),
                UiKit.dp(this@OutdoorTutorialActivity, 8), 0)
            gravity = Gravity.CENTER
        })

        stepContent.addView(card)

        val indicator = findViewById<LinearLayout>(indicatorId)
        indicator?.removeAllViews()
        for (i in 0 until totalSteps) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@OutdoorTutorialActivity, 8), UiKit.dp(this@OutdoorTutorialActivity, 8))
            }
            val params = dot.layoutParams as LinearLayout.LayoutParams
            params.leftMargin = UiKit.dp(this@OutdoorTutorialActivity, 4)
            params.rightMargin = UiKit.dp(this@OutdoorTutorialActivity, 4)
            dot.layoutParams = params
            dot.setBackgroundColor(if (i == currentStep) UiKit.ACCENT.toInt() else 0x44FFFFFF.toInt())
            indicator?.addView(dot)
        }

        nextBtn.text = if (currentStep == totalSteps - 1) "Inizia 🎮" else "Avanti →"
    }

    private fun finishTutorial() {
        val prefs = getSharedPreferences("outdoor_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("tutorial_seen", true).apply()

        val mgr = OutdoorManager.get()
        mgr.start(this)

        startActivity(Intent(this, ArNavigationActivity::class.java))
        finish()
    }

    companion object {
        private var indicatorId = 0
    }
}