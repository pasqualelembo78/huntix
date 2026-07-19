package com.intelligame.huntix.ui

import android.animation.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.*
import com.intelligame.huntix.managers.*
import com.intelligame.huntix.BaseNavActivity

class SurpriseRevealActivity : BaseNavActivity() {

    companion object {
        const val EXTRA_RARITY_ID = "rarity_id"
        fun launch(ctx: Context, rarityId: String) =
            ctx.startActivity(Intent(ctx, SurpriseRevealActivity::class.java).apply {
                putExtra(EXTRA_RARITY_ID, rarityId)
            })
    }

    private lateinit var root:         FrameLayout
    private lateinit var eggView:      TextView
    private lateinit var creatureView: TextView
    private lateinit var infoCard:     LinearLayout
    private lateinit var tapHint:      TextView

    private var rarityId  = "common"
    private var revealed  = false
    private lateinit var surprise: OwnedSurprise

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rarityId = intent.getStringExtra(EXTRA_RARITY_ID) ?: "common"

        val weather = WeatherZoneManager.getCachedWeather(this)
        val zone    = WeatherZoneManager.getCachedZone(this)
        surprise    = SurpriseManager.addFromHatch(this, rarityId, zone, weather)

        buildUI()
        startEggAnimation()
    }

    private fun buildUI() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050A1A"))
        }
        setContentView(root)

        eggView = TextView(this).apply {
            text = EggRarity.fromId(rarityId).emoji
            textSize = 96f; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(dp(200), dp(200)).also {
                it.gravity = Gravity.CENTER
                it.topMargin = -dp(60)
            }
        }
        root.addView(eggView)

        creatureView = TextView(this).apply {
            text = surprise.displayEmoji
            textSize = 100f; gravity = Gravity.CENTER; alpha = 0f
            layoutParams = FrameLayout.LayoutParams(dp(220), dp(220)).also {
                it.gravity = Gravity.CENTER; it.topMargin = -dp(60)
            }
        }
        root.addView(creatureView)

        infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            alpha = 0f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#CC0D1530"))
                setStroke(dp(2), EggRarity.fromId(rarityId).color)
            }
            setPadding(dp(24), dp(20), dp(24), dp(20))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(260)).also {
                it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dp(80); it.marginStart = dp(24); it.marginEnd = dp(24)
            }
        }

        val rarity = EggRarity.fromId(rarityId)
        infoCard.addView(tv("Hai trovato...", 14f, Color.parseColor("#9999CC"), Gravity.CENTER))
        infoCard.addView(tv(surprise.creature?.name ?: surprise.displayEmoji, 28f,
            rarity.color, Gravity.CENTER, true))
        infoCard.addView(tv(rarity.displayName.uppercase(), 13f, rarity.color, Gravity.CENTER))
        infoCard.addView(tv(surprise.creature?.description ?: "", 13f,
            Color.parseColor("#9999CC"), Gravity.CENTER).also {
            it.setPadding(0, dp(8), 0, dp(8))
        })

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        val c = surprise.creature
        if (c != null) {
            listOf("HP" to "${c.baseHp}", "ATK" to "${c.baseAttack}",
                   "DEF" to "${c.baseDefense}", "SPD" to "${c.baseSpeed}")
                .forEach { (label, value) ->
                    statsRow.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        addView(tv(label, 11f, Color.parseColor("#9999CC"), Gravity.CENTER))
                        addView(tv(value, 16f, Color.WHITE, Gravity.CENTER, true))
                    })
                }
        }
        infoCard.addView(statsRow)

        if (c != null) {
            infoCard.addView(tv("${c.specialMoveEmoji} ${c.specialMoveName} (danno: ${c.specialMoveDamage})",
                12f, Color.parseColor("#E0E0FF"), Gravity.CENTER).also {
                it.setPadding(0, dp(6), 0, 0)
            })
        }

        root.addView(infoCard)

        tapHint = tv("Tocca per continuare", 14f, Color.parseColor("#9999CC"), Gravity.CENTER)
        tapHint.alpha = 0f
        tapHint.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(40)).also {
            it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            it.bottomMargin = dp(24)
        }
        root.addView(tapHint)

        root.setOnClickListener {
            if (revealed) {
                // Porta l'utente alla borsa per vedere la creatura appena ottenuta
                startActivity(Intent(this, SurpriseInventoryActivity::class.java))
                finish()
            }
        }
    }

    private fun startEggAnimation() {
        val shake = ObjectAnimator.ofFloat(eggView, "translationX",
            0f, -20f, 20f, -15f, 15f, -10f, 10f, 0f).apply {
            duration = 800; repeatCount = 2
        }
        val scaleX  = ObjectAnimator.ofFloat(eggView, "scaleX", 1f, 1.4f, 0f)
        val scaleY  = ObjectAnimator.ofFloat(eggView, "scaleY", 1f, 1.4f, 0f)
        val fadeOut = ObjectAnimator.ofFloat(eggView, "alpha", 1f, 1f, 0f)
        val explode = AnimatorSet().apply {
            playTogether(scaleX, scaleY, fadeOut); duration = 600
        }

        val creatureScaleX = ObjectAnimator.ofFloat(creatureView, "scaleX", 0f, 1.3f, 1f).apply { duration = 600 }
        val creatureScaleY = ObjectAnimator.ofFloat(creatureView, "scaleY", 0f, 1.3f, 1f).apply { duration = 600 }
        val creatureFade   = ObjectAnimator.ofFloat(creatureView, "alpha",  0f, 1f).apply { duration = 400 }
        val cardFade       = ObjectAnimator.ofFloat(infoCard, "alpha",      0f, 1f).apply { duration = 600; startDelay = 300 }
        val hintFade       = ObjectAnimator.ofFloat(tapHint, "alpha",       0f, 1f).apply { duration = 500; startDelay = 800 }
        val reveal         = AnimatorSet().apply {
            playTogether(creatureScaleX, creatureScaleY, creatureFade, cardFade, hintFade)
        }

        shake.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                val color = EggRarity.fromId(rarityId).color
                val flash = ObjectAnimator.ofArgb(root, "backgroundColor",
                    Color.parseColor("#050A1A"), color, Color.parseColor("#050A1A"))
                flash.duration = 400
                flash.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) { explode.start() }
                })
                flash.start()
            }
        })

        explode.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                revealed = true
                reveal.start()
            }
        })

        shake.start()
    }

    private fun tv(t: String, s: Float, c: Int, g: Int, b: Boolean = false) = TextView(this).apply {
        text = t; textSize = s; setTextColor(c); gravity = g
        if (b) typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
