package com.intelligame.huntix.ui

import android.animation.*
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import android.view.animation.*
import android.widget.*
import com.intelligame.huntix.EggRarity

/**
 * MicroInteractionHelper — Micro-animazioni per ogni azione utente.
 *
 * Principio: ogni azione deve essere gratificante!
 *  - Pop + bounce su raccolta uova
 *  - Floating score numbers (+XP, +💎)
 *  - Glow flash per rarità alte
 *  - Haptic per ogni rarità
 *  - Shake per errori/fallimenti
 */
object MicroInteractionHelper {

    // ─── Floating Score ──────────────────────────────────────────

    /**
     * Mostra "+150 XP ⚡" che sale e svanisce sopra la view toccata.
     */
    fun showFloatingScore(parent: ViewGroup, text: String, colorHex: String = "#E0E0FF", x: Float = -1f, y: Float = -1f) {
        val tv = TextView(parent.context).apply {
            this.text = text
            textSize = 18f
            setTextColor(Color.parseColor(colorHex))
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            if (x >= 0 && y >= 0) {
                lp.leftMargin = x.toInt()
                lp.topMargin = y.toInt()
            } else {
                lp.gravity = android.view.Gravity.CENTER_HORIZONTAL
                lp.topMargin = dp(parent.context, 200)
            }
            layoutParams = lp
        }

        // Wrap in FrameLayout if needed
        val root = if (parent is FrameLayout) parent else {
            val fl = FrameLayout(parent.context)
            parent.addView(fl, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            fl
        }
        root.addView(tv)

        val translateY = ObjectAnimator.ofFloat(tv, "translationY", 0f, -dp(parent.context, 80).toFloat())
        val fadeOut = ObjectAnimator.ofFloat(tv, "alpha", 1f, 0f)
        val scaleX = ObjectAnimator.ofFloat(tv, "scaleX", 0.5f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(tv, "scaleY", 0.5f, 1.2f, 1f)

        AnimatorSet().apply {
            playTogether(translateY, fadeOut, scaleX, scaleY)
            duration = 1200
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { root.removeView(tv) }
            })
            start()
        }
    }

    fun showXpGained(parent: ViewGroup, xp: Long) {
        if (xp <= 0) return
        showFloatingScore(parent, "+${xp} XP ⚡", "#E0E0FF")
    }

    fun showGemsGained(parent: ViewGroup, gems: Int) {
        if (gems <= 0) return
        showFloatingScore(parent, "+${gems} 💎", "#00E5FF")
    }

    fun showRarityFound(parent: ViewGroup, rarity: EggRarity) {
        showFloatingScore(parent, "${rarity.emoji} ${rarity.displayName}!", rarity.colorHex)
    }

    // ─── Button Pop ──────────────────────────────────────────────

    fun popView(view: View, onEnd: (() -> Unit)? = null) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.25f, 0.9f, 1.05f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.25f, 0.9f, 1.05f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 350
            interpolator = OvershootInterpolator(3f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { onEnd?.invoke() }
            })
            start()
        }
    }

    fun bounceIn(view: View) {
        view.scaleX = 0f; view.scaleY = 0f; view.alpha = 0f
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0f, 1.2f, 1f)
        val alpha  = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 400
            interpolator = OvershootInterpolator(2f)
            start()
        }
    }

    fun shake(view: View) {
        ObjectAnimator.ofFloat(view, "translationX",
            0f, -16f, 16f, -12f, 12f, -8f, 8f, -4f, 4f, 0f).apply {
            duration = 500
            interpolator = LinearInterpolator()
            start()
        }
    }

    fun pulseGlow(view: View, colorHex: String) {
        val start = Color.TRANSPARENT
        val end = Color.parseColor(colorHex + "66")
        ObjectAnimator.ofArgb(view, "backgroundColor", start, end, start, end, start).apply {
            duration = 800
            repeatCount = 1
            start()
        }
    }

    fun fadeInSlideUp(view: View, delayMs: Long = 0) {
        view.alpha = 0f
        view.translationY = dp(view.context, 40).toFloat()
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        val transY = ObjectAnimator.ofFloat(view, "translationY", dp(view.context, 40).toFloat(), 0f)
        AnimatorSet().apply {
            playTogether(alpha, transY)
            duration = 350
            startDelay = delayMs
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    fun progressBarAnim(progressBar: android.widget.ProgressBar, from: Int, to: Int) {
        ObjectAnimator.ofInt(progressBar, "progress", from, to).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    // ─── Level Up Celebration ─────────────────────────────────────

    fun levelUpCelebration(parent: ViewGroup, newLevel: Int) {
        // Big animated overlay
        val overlay = TextView(parent.context).apply {
            text = "🎉 LIVELLO $newLevel! 🎉"
            textSize = 28f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC000000"))
            gravity = Gravity.CENTER
            setPadding(dp(parent.context, 32), dp(parent.context, 24), dp(parent.context, 32), dp(parent.context, 24))
            setShadowLayer(8f, 0f, 4f, Color.parseColor("#E0E0FF"))
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
            )
        }

        val root = if (parent is FrameLayout) parent else {
            FrameLayout(parent.context).also { parent.addView(it, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        }
        root.addView(overlay)

        bounceIn(overlay)
        parent.postDelayed({
            ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
                duration = 600
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) { root.removeView(overlay) }
                })
                start()
            }
        }, 2500)
    }

    // ─── Quest Complete Banner ────────────────────────────────────

    fun showQuestCompleted(parent: ViewGroup, questTitle: String, rewardText: String) {
        val banner = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC1B5E20"))
            setPadding(dp(parent.context, 24), dp(parent.context, 16), dp(parent.context, 24), dp(parent.context, 16))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).also { it.topMargin = dp(parent.context, 80) }
        }
        banner.addView(TextView(parent.context).apply {
            text = "✅ MISSIONE COMPLETATA!"; textSize = 16f
            setTextColor(Color.parseColor("#00FF88"))
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        })
        banner.addView(TextView(parent.context).apply {
            text = questTitle; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        banner.addView(TextView(parent.context).apply {
            text = rewardText; textSize = 14f; setTextColor(Color.parseColor("#E0E0FF")); gravity = Gravity.CENTER
        })

        val root = if (parent is FrameLayout) parent else {
            FrameLayout(parent.context).also { parent.addView(it, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        }
        root.addView(banner)
        fadeInSlideUp(banner)
        parent.postDelayed({
            ObjectAnimator.ofFloat(banner, "alpha", 1f, 0f).apply {
                duration = 500
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) { root.removeView(banner) }
                })
                start()
            }
        }, 3000)
    }

    // ─── Haptic ───────────────────────────────────────────────────

    fun hapticForRarity(ctx: Context, rarity: EggRarity) {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = when (rarity) {
                EggRarity.COMMON    -> VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                EggRarity.UNCOMMON  -> VibrationEffect.createOneShot(40, 180)
                EggRarity.RARE      -> VibrationEffect.createWaveform(longArrayOf(0, 50, 30, 50), -1)
                EggRarity.EPIC      -> VibrationEffect.createWaveform(longArrayOf(0, 80, 40, 80), -1)
                EggRarity.LEGENDARY -> VibrationEffect.createWaveform(longArrayOf(0, 120, 60, 120, 60, 200), -1)
            }
            v.vibrate(pattern)
        } else {
            @Suppress("DEPRECATION")
            when (rarity) {
                EggRarity.LEGENDARY -> v.vibrate(longArrayOf(0, 120, 60, 120, 60, 200), -1)
                EggRarity.EPIC      -> v.vibrate(longArrayOf(0, 80, 40, 80), -1)
                else                -> v.vibrate(40)
            }
        }
    }

    // ─── Util ─────────────────────────────────────────────────────

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
