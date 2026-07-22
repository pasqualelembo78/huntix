package com.intelligame.huntix.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.SurpriseCreature
import com.intelligame.huntix.UiKit

object EvolutionDialogHelper {

    interface OnEvolutionResult {
        fun onEvolved(newCreature: SurpriseCreature)
        fun onCancelled()
    }

    fun showEvolutionConfirm(
        ctx: Context,
        currentCreature: SurpriseCreature,
        evolvedCreature: SurpriseCreature,
        candyCost: Int,
        onResult: OnEvolutionResult
    ) {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 16)
        }

        val title = TextView(ctx).apply {
            text = "Evoluzione!"
            textSize = 22f
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(0, 0, 0, 12)
        }

        val currentLabel = TextView(ctx).apply {
            text = "${currentCreature.emoji} ${currentCreature.name} (Lv.${currentCreature.rarityId})"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val arrow = TextView(ctx).apply {
            text = "⬇️ (${candyCost} caramelle)"
            textSize = 14f
            setTextColor(Color.parseColor("#FFCC00"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }

        val evolvedLabel = TextView(ctx).apply {
            text = "${evolvedCreature.emoji} ${evolvedCreature.name} (Lv.${evolvedCreature.rarityId})"
            textSize = 16f
            setTextColor(Color.parseColor("#00FF88"))
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
        }

        val statsLabel = TextView(ctx).apply {
            text = "HP: ${currentCreature.baseHp}→${evolvedCreature.baseHp}  ATK: ${currentCreature.baseAttack}→${evolvedCreature.baseAttack}  DEF: ${currentCreature.baseDefense}→${evolvedCreature.baseDefense}  SPD: ${currentCreature.baseSpeed}→${evolvedCreature.baseSpeed}"
            textSize = 11f
            setTextColor(Color.parseColor("#888899"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        val message = TextView(ctx).apply {
            text = "${currentCreature.name} evolverà in ${evolvedCreature.name}!"
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }

        container.addView(title)
        container.addView(currentLabel)
        container.addView(arrow)
        container.addView(evolvedLabel)
        container.addView(statsLabel)
        container.addView(message)

        AlertDialog.Builder(ctx)
            .setTitle("Vuoi evolvere?")
            .setView(container)
            .setPositiveButton("Evolvi! ⚡") { _, _ ->
                showEvolutionAnimation(ctx, currentCreature, evolvedCreature, onResult)
            }
            .setNegativeButton("Non ora", { _, _ -> onResult.onCancelled() })
            .setCancelable(false)
            .show()
    }

    private fun showEvolutionAnimation(
        ctx: Context,
        from: SurpriseCreature,
        to: SurpriseCreature,
        onResult: OnEvolutionResult
    ) {
        val evolutionView = EvolutionAnimationView(ctx, from, to)
        val dialog = AlertDialog.Builder(ctx)
            .setView(evolutionView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)

        evolutionView.onComplete = {
            dialog.dismiss()
            showEvolvedResult(ctx, to, onResult)
        }

        dialog.show()
        evolutionView.startAnimation()
    }

    private fun showEvolvedResult(
        ctx: Context,
        creature: SurpriseCreature,
        onResult: OnEvolutionResult
    ) {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 16)
        }

        val emoji = TextView(ctx).apply {
            text = creature.emoji
            textSize = 64f
            gravity = Gravity.CENTER
        }

        val name = TextView(ctx).apply {
            text = creature.name
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(0, 8, 0, 4)
        }

        val rarity = TextView(ctx).apply {
            text = creature.rarityId.uppercase()
            textSize = 14f
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
        }

        val desc = TextView(ctx).apply {
            text = creature.description
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }

        val move = TextView(ctx).apply {
            text = "${creature.specialMoveEmoji} ${creature.specialMoveName} (${creature.specialMoveDamage} DMG)"
            textSize = 12f
            setTextColor(Color.parseColor("#66BB6A"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        container.addView(emoji)
        container.addView(name)
        container.addView(rarity)
        container.addView(desc)
        container.addView(move)

        AlertDialog.Builder(ctx)
            .setTitle("Evoluzione completata!")
            .setView(container)
            .setPositiveButton("Fantastico!") { _, _ ->
                onResult.onEvolved(creature)
            }
            .setCancelable(false)
            .show()
    }

    // ── Animation View ───────────────────────────────────────────

    private class EvolutionAnimationView(
        context: Context,
        private val from: SurpriseCreature,
        private val to: SurpriseCreature
    ) : View(context) {

        var onComplete: (() -> Unit)? = null

        private val bgPaint = Paint().apply { color = Color.BLACK }
        private val creaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 80f; textAlign = Paint.Align.CENTER; color = Color.WHITE
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.parseColor("#FFD700")
        }
        private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.parseColor("#FFD700")
        }
        private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.WHITE; alpha = 0
        }

        private var phase = 0 // 0=glow, 1=flash, 2=transform, 3=reveal
        private var animProgress = 0f
        private var glowRadius = 40f
        private var flashAlpha = 0
        private var fromAlpha = 255
        private var toAlpha = 0
        private var particles = mutableListOf<Particle>()
        private var scaleFrom = 1f
        private var scaleTo = 0f
        private var revealScale = 0f

        private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var maxLife: Float)

        fun startAnimation() {
            // Phase 1: Glow ring (0-1s)
            // Phase 2: Flash white (1-1.5s)
            // Phase 3: Transform (1.5-2.5s)
            // Phase 4: Reveal new creature (2.5-3.5s)

            for (i in 0..20) {
                particles.add(Particle(
                    width / 2f, height / 2f,
                    (Math.random() * 6 - 3).toFloat(),
                    (Math.random() * 6 - 3).toFloat(),
                    1f, 1f
                ))
            }

            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 3500
                addUpdateListener {
                    animProgress = it.animatedFraction

                    when {
                        animProgress < 0.3f -> {
                            // Glow phase
                            val t = animProgress / 0.3f
                            glowRadius = 40f + t * 120f
                            glowPaint.alpha = (t * 255).toInt()
                            creaturePaint.alpha = 255
                            scaleFrom = 1f + t * 0.3f
                        }
                        animProgress < 0.45f -> {
                            // Flash
                            val t = (animProgress - 0.3f) / 0.15f
                            flashAlpha = (t * 255).toInt()
                            glowPaint.alpha = ((1f - t) * 255).toInt()
                            scaleFrom = 1.3f
                        }
                        animProgress < 0.75f -> {
                            // Transform: from fades out, to fades in
                            val t = (animProgress - 0.45f) / 0.3f
                            flashAlpha = ((1f - t) * 255).toInt()
                            fromAlpha = ((1f - t) * 255).toInt()
                            toAlpha = (t * 255).toInt()
                            scaleFrom = 1.3f * (1f - t) + 0.5f * t
                            scaleTo = 0.5f * (1f - t) + 1.2f * t

                            // Particle burst
                            particles.forEach { p ->
                                p.x += p.vx * 3
                                p.y += p.vy * 3
                                p.life -= 0.03f
                            }
                        }
                        else -> {
                            // Reveal
                            val t = (animProgress - 0.75f) / 0.25f
                            revealScale = t
                            toAlpha = 255
                            scaleTo = 1.2f * (1f - t) + 1f * t
                        }
                    }
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onComplete?.invoke()
                    }
                })
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val cx = width / 2f
            val cy = height / 2f

            // Glow ring
            if (glowPaint.alpha > 0) {
                canvas.drawCircle(cx, cy, glowRadius, glowPaint)
            }

            // From creature
            if (fromAlpha > 0) {
                creaturePaint.alpha = fromAlpha
                canvas.save()
                canvas.scale(scaleFrom, scaleFrom, cx, cy)
                creaturePaint.textSize = 80f
                canvas.drawText(from.emoji, cx, cy + 25f, creaturePaint)
                canvas.restore()
            }

            // Particles
            particles.filter { it.life > 0 }.forEach { p ->
                particlePaint.alpha = (p.life / p.maxLife * 255).toInt()
                canvas.drawCircle(p.x, p.y, 5f * (p.life / p.maxLife), particlePaint)
            }

            // To creature
            if (toAlpha > 0) {
                creaturePaint.alpha = toAlpha
                canvas.save()
                canvas.scale(scaleTo, scaleTo, cx, cy)
                creaturePaint.textSize = 80f
                canvas.drawText(to.emoji, cx, cy + 25f, creaturePaint)
                canvas.restore()
            }

            // Flash overlay
            if (flashAlpha > 0) {
                flashPaint.alpha = flashAlpha
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
            }

            // Reveal label
            if (revealScale > 0) {
                val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FFD700"); textSize = 32f
                    textAlign = Paint.Align.CENTER; isFakeBoldText = true
                    alpha = (revealScale * 255).toInt()
                }
                canvas.drawText(to.name, cx, cy + 100f, namePaint)
            }
        }
    }
}
