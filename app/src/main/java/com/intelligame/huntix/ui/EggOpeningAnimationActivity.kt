package com.intelligame.huntix.ui

import android.animation.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.SoundManager
import com.intelligame.huntix.gamification.UpgradeChanceManager
import com.intelligame.huntix.BaseNavActivity

/**
 * EggOpeningAnimationActivity — esperienza premium di apertura uova.
 *
 * Fasi:
 *  1. Oscuramento schermo (fade in dark overlay)
 *  2. Spotlight sull'uovo (glow pulsante)
 *  3. Shake + suspense (2 sec)
 *  4. Esplosione + glow + particelle
 *  5. Rivelazione rarità con colore e titolo
 *  6. Bounce XP reward
 *  7. Schermata upgrade chance (tocca per potenziare rarità)
 */
class EggOpeningAnimationActivity : BaseNavActivity() {

    companion object {
        const val EXTRA_RARITY_ID = "rarity_id"
        const val EXTRA_EGG_NAME  = "egg_name"
        const val EXTRA_XP_REWARD = "xp_reward"
        const val RESULT_RARITY   = "result_rarity"

        fun start(ctx: Context, rarity: EggRarity, eggName: String, xpReward: Int) {
            ctx.startActivity(
                Intent(ctx, EggOpeningAnimationActivity::class.java).apply {
                    putExtra(EXTRA_RARITY_ID, rarity.id)
                    putExtra(EXTRA_EGG_NAME, eggName)
                    putExtra(EXTRA_XP_REWARD, xpReward)
                }
            )
        }
    }

    private lateinit var rarity: EggRarity
    private lateinit var eggName: String
    private var xpReward: Int = 0
    private var phase = 0
    private var upgradeAttempted = false

    private lateinit var rootLayout: FrameLayout
    private lateinit var overlayView: View
    private lateinit var eggEmoji: TextView
    private lateinit var glowRing: View
    private lateinit var rarityLabel: TextView
    private lateinit var xpLabel: TextView
    private lateinit var tapHint: TextView
    private lateinit var upgradePanel: LinearLayout
    private lateinit var upgradeBtn: Button
    private lateinit var upgradeChanceLabel: TextView
    private lateinit var skipBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val rarityId = intent.getStringExtra(EXTRA_RARITY_ID) ?: "common"
        rarity = EggRarity.fromId(rarityId)
        eggName = intent.getStringExtra(EXTRA_EGG_NAME) ?: rarity.randomName()
        xpReward = intent.getIntExtra(EXTRA_XP_REWARD, rarity.xpReward)

        buildUI()
        startOpeningSequence()
    }

    private fun buildUI() {
        rootLayout = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // Overlay scuro
        overlayView = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        rootLayout.addView(overlayView)

        // Centro
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        // Glow ring
        glowRing = View(this).apply {
            val size = dp(180)
            layoutParams = LinearLayout.LayoutParams(size, size).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = -dp(90)
            }
            setBackgroundColor(Color.TRANSPARENT)
        }
        center.addView(glowRing)

        // Emoji uovo
        eggEmoji = TextView(this).apply {
            text = rarity.emoji
            textSize = 96f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        center.addView(eggEmoji)

        // Rarità label
        rarityLabel = TextView(this).apply {
            text = ""
            textSize = 28f
            setTextColor(Color.parseColor(rarity.colorHex))
            gravity = Gravity.CENTER
            alpha = 0f
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            setPadding(0, dp(24), 0, 0)
        }
        center.addView(rarityLabel)

        // Nome uovo
        val eggNameLabel = TextView(this).apply {
            text = eggName
            textSize = 14f
            setTextColor(Color.parseColor("#9999CC"))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        center.addView(eggNameLabel)

        // XP reward
        xpLabel = TextView(this).apply {
            text = ""
            textSize = 22f
            setTextColor(Color.parseColor("#E0E0FF"))
            gravity = Gravity.CENTER
            alpha = 0f
            setPadding(0, dp(16), 0, 0)
        }
        center.addView(xpLabel)

        // Tap hint
        tapHint = TextView(this).apply {
            text = "Tocca per aprire"
            textSize = 14f
            setTextColor(Color.parseColor("#666699"))
            gravity = Gravity.CENTER
            setPadding(0, dp(48), 0, 0)
        }
        center.addView(tapHint)

        // Upgrade panel
        upgradePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            alpha = 0f
            setPadding(dp(32), dp(24), dp(32), 0)
        }

        upgradeChanceLabel = TextView(this).apply {
            text = ""
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        upgradePanel.addView(upgradeChanceLabel)

        upgradeBtn = Button(this).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(dp(24), dp(12), dp(24), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).also { it.topMargin = dp(12) }
        }
        upgradePanel.addView(upgradeBtn)

        center.addView(upgradePanel)
        rootLayout.addView(center)

        // Skip
        skipBtn = TextView(this).apply {
            text = "Salta ›"
            textSize = 14f
            setTextColor(Color.parseColor("#666699"))
            setPadding(dp(24), dp(56), dp(24), 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END)
            setOnClickListener { finishWithResult(rarity) }
        }
        rootLayout.addView(skipBtn)

        setContentView(rootLayout)
        rootLayout.setOnClickListener { onScreenTapped() }
    }

    private fun startOpeningSequence() {
        phase = 1
        tapHint.text = "Tocca per aprire"

        // Phase 1: Fade in darkness + pulsating egg
        ObjectAnimator.ofFloat(overlayView, "alpha", 0f, 0.85f).apply {
            duration = 600; start()
        }

        // Pulse animation on egg
        val pulseAnim = ObjectAnimator.ofFloat(eggEmoji, "scaleX", 1f, 1.15f, 1f).apply {
            duration = 800; repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val pulseAnimY = ObjectAnimator.ofFloat(eggEmoji, "scaleY", 1f, 1.15f, 1f).apply {
            duration = 800; repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        AnimatorSet().also { it.playTogether(pulseAnim, pulseAnimY); it.start() }

        hapticLight()
    }

    private fun onScreenTapped() {
        when (phase) {
            1 -> revealEgg()
            2 -> showUpgradeOption()
            3 -> { /* handled by buttons */ }
        }
    }

    private fun revealEgg() {
        phase = 2
        tapHint.alpha = 0f
        hapticMedium()
        SoundManager.playEggFound()

        // Shake animation
        val shakeAnim = ObjectAnimator.ofFloat(eggEmoji, "translationX",
            0f, -20f, 20f, -15f, 15f, -10f, 10f, 0f).apply {
            duration = 600; interpolator = LinearInterpolator()
        }
        shakeAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // Burst scale
                val burst = AnimatorSet()
                val scaleX = ObjectAnimator.ofFloat(eggEmoji, "scaleX", 1f, 2.5f, 1.8f).apply { duration = 400 }
                val scaleY = ObjectAnimator.ofFloat(eggEmoji, "scaleY", 1f, 2.5f, 1.8f).apply { duration = 400 }
                burst.playTogether(scaleX, scaleY)
                burst.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { showRarityReveal() }
                })
                burst.start()
                hapticHeavy()
            }
        })
        shakeAnim.start()
    }

    private fun showRarityReveal() {
        rarityLabel.text = "${rarity.emoji} ${rarity.displayName.uppercase()}"
        xpLabel.text = "+${xpReward} XP"

        val rarityAnim = ObjectAnimator.ofFloat(rarityLabel, "alpha", 0f, 1f).apply { duration = 500 }
        val xpAnim = ObjectAnimator.ofFloat(xpLabel, "alpha", 0f, 1f).apply {
            duration = 400; startDelay = 300
        }
        AnimatorSet().also { it.playTogether(rarityAnim, xpAnim); it.start() }

        // Change background color to rarity color
        ObjectAnimator.ofArgb(overlayView, "backgroundColor",
            Color.BLACK, Color.parseColor(rarity.colorHex + "33")).apply {
            duration = 800; start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            tapHint.text = "Tocca per potenziare"
            tapHint.alpha = 1f
            val tapAnim = ObjectAnimator.ofFloat(tapHint, "alpha", 0f, 1f).apply { duration = 500 }
            tapAnim.start()
        }, 800)
    }

    private fun showUpgradeOption() {
        phase = 3
        tapHint.alpha = 0f

        val nextRarity = UpgradeChanceManager.getNextRarity(rarity)
        if (nextRarity == null || upgradeAttempted) {
            finishWithResult(rarity); return
        }

        val chance = UpgradeChanceManager.getCurrentChance(this, rarity)
        val pity = UpgradeChanceManager.getPityCount(this, rarity)

        upgradeChanceLabel.text = "🎲 Tentativo upgrade → ${nextRarity.displayName} ${nextRarity.emoji}\n" +
                "Probabilità: ${UpgradeChanceManager.formatChance(chance)}" +
                (if (pity > 0) " (pity +${pity * 2}%)" else "")

        upgradeBtn.text = "⚡ POTENZIA! (${UpgradeChanceManager.formatChance(chance)})"
        upgradeBtn.setBackgroundColor(Color.parseColor(nextRarity.colorHex))

        upgradeBtn.setOnClickListener {
            upgradeAttempted = true
            val result = UpgradeChanceManager.attemptUpgrade(this, rarity)
            if (result.success) {
                hapticHeavy()
                SoundManager.playVictory()
                rarity = result.newRarity
                eggEmoji.text = rarity.emoji
                rarityLabel.text = "${rarity.emoji} ${rarity.displayName.uppercase()} ✨ UPGRADE!"
                rarityLabel.setTextColor(Color.parseColor(rarity.colorHex))
                upgradeBtn.isEnabled = false
                upgradeBtn.text = "🎉 Potenziato!"
                ObjectAnimator.ofArgb(overlayView, "backgroundColor",
                    Color.parseColor(EggRarity.fromId(intent.getStringExtra(EXTRA_RARITY_ID) ?: "common").colorHex + "33"),
                    Color.parseColor(rarity.colorHex + "55")).apply { duration = 600; start() }
                Handler(Looper.getMainLooper()).postDelayed({ finishWithResult(rarity) }, 2000)
            } else {
                hapticLight()
                upgradeBtn.text = "❌ Fallito... pity +2% (tot: ${UpgradeChanceManager.formatChance(UpgradeChanceManager.getCurrentChance(this, result.oldRarity))})"
                upgradeBtn.isEnabled = false
                Handler(Looper.getMainLooper()).postDelayed({ finishWithResult(rarity) }, 1500)
            }
        }

        val skipUpgrade = TextView(this).apply {
            text = "No grazie, tieni ${rarity.displayName}"
            textSize = 13f
            setTextColor(Color.parseColor("#666699"))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
            setOnClickListener { finishWithResult(rarity) }
        }
        upgradePanel.addView(skipUpgrade)

        ObjectAnimator.ofFloat(upgradePanel, "alpha", 0f, 1f).apply { duration = 500; start() }
    }

    private fun finishWithResult(finalRarity: EggRarity) {
        setResult(RESULT_OK, Intent().putExtra(RESULT_RARITY, finalRarity.id))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    // ─── Haptic ───────────────────────────────────────────────────

    private fun hapticLight() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else { @Suppress("DEPRECATION") v.vibrate(30) }
    }

    private fun hapticMedium() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(60, 200))
        } else { @Suppress("DEPRECATION") v.vibrate(60) }
    }

    private fun hapticHeavy() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 40, 120), -1))
        } else { @Suppress("DEPRECATION") v.vibrate(longArrayOf(0, 80, 40, 120), -1) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
