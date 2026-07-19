package com.intelligame.huntix.ui

import android.graphics.*
import android.graphics.drawable.GradientDrawable
import kotlin.math.*

/**
 * HoloCrystalEggRenderer — Disegna uova cristalline olografiche su Canvas.
 *
 * Stile: uovo traslucido con riflessi viola/cyan, aura luminosa pulsante,
 * particelle cristalline che orbitano, brackets AR neon intorno.
 *
 * Ispirato al concept video: uovo olografico gigante fluttuante in AR.
 */
object HoloCrystalEggRenderer {

    /**
     * Disegna un uovo cristallino olografico su Canvas.
     * @param canvas Canvas target
     * @param cx centro X
     * @param cy centro Y
     * @param radius raggio base dell'uovo
     * @param baseColor colore base della rarità
     * @param glowColor colore glow della rarità
     * @param phase fase animazione (0..1, incrementare ogni frame per shimmer)
     * @param auraAlpha intensità aura (0..255)
     */
    fun drawCrystalEgg(
        canvas: Canvas,
        cx: Float, cy: Float,
        radius: Float,
        baseColor: Int, glowColor: Int,
        phase: Float = 0f,
        auraAlpha: Int = 120
    ) {
        val rX = radius * 0.75f
        val rYTop = radius * 1.1f
        val rYBot = radius * 0.9f

        // === 1. AURA ESTERNA (glow pulsante) ===
        val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                cx, cy, radius * 2.2f,
                intArrayOf(
                    Color.argb((auraAlpha * 0.6f).toInt(), Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                    Color.argb((auraAlpha * 0.3f).toInt(), Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, radius * 2.2f, auraPaint)

        // === 2. ANELLO GLOW INTERNO ===
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.08f
            color = Color.argb(80, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
            maskFilter = BlurMaskFilter(radius * 0.3f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawOval(cx - rX * 1.3f, cy - rYTop * 1.2f, cx + rX * 1.3f, cy + rYBot * 1.2f, ringPaint)

        // === 3. CORPO UOVO CRISTALLINO (gradiente multistrato) ===
        val eggPath = buildEggPath(cx, cy, rX, rYTop, rYBot)

        // Strato base: gradiente cristallino
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                cx - rX, cy - rYTop, cx + rX, cy + rYBot,
                intArrayOf(
                    lighten(baseColor, 0.4f),
                    baseColor,
                    darken(baseColor, 0.3f),
                    Color.argb(200, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
                ),
                floatArrayOf(0f, 0.3f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(eggPath, bodyPaint)

        // Strato trasparente: iridescenza
        val iridescentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                cx + rX * 0.3f * cos(phase * Math.PI.toFloat() * 2),
                cy - rYTop * 0.2f,
                radius * 1.2f,
                intArrayOf(
                    Color.argb(60, 0, 229, 255),   // cyan
                    Color.argb(40, 168, 85, 247),   // violet
                    Color.argb(30, 255, 215, 0),    // gold
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.3f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(eggPath, iridescentPaint)

        // === 4. RIFLESSO CRISTALLINO (highlight superiore) ===
        val highlightPath = Path().apply {
            addOval(cx - rX * 0.5f, cy - rYTop * 0.8f, cx + rX * 0.15f, cy - rYTop * 0.3f, Path.Direction.CW)
        }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                cx - rX * 0.2f, cy - rYTop * 0.6f, rX * 0.5f,
                intArrayOf(Color.argb(120, 255, 255, 255), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(highlightPath, highlightPaint)

        // === 5. SHIMMER LINE (riga di luce che si muove) ===
        val shimmerY = cy - rYTop + (rYTop + rYBot) * ((phase + 0.5f) % 1f)
        val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.06f
            shader = LinearGradient(
                cx - rX, shimmerY, cx + rX, shimmerY,
                intArrayOf(Color.TRANSPARENT, Color.argb(180, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.clipPath(eggPath)
        canvas.drawLine(cx - rX, shimmerY, cx + rX, shimmerY, shimmerPaint)
        canvas.restore()

        // === 6. BORDO CRISTALLINO ===
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.04f
            color = Color.argb(150, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
        }
        canvas.drawPath(eggPath, borderPaint)
    }

    /**
     * Disegna brackets AR neon intorno all'uovo (stile video).
     * I brackets sono angoli luminosi che inquadrano il target.
     */
    fun drawARBrackets(
        canvas: Canvas,
        cx: Float, cy: Float,
        size: Float,
        color: Int = Color.parseColor("#00E5FF"),
        phase: Float = 0f,
        lockProgress: Float = 0f // 0..1, brackets si chiudono durante lock-on
    ) {
        val bracketLen = size * 0.3f
        val bracketThick = size * 0.025f
        val gap = size * (0.6f - lockProgress * 0.25f) // si restringe durante lock-on

        val alpha = (180 + 75 * sin(phase * Math.PI * 2)).toInt().coerceIn(0, 255)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = bracketThick
            strokeCap = Paint.Cap.ROUND
            this.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            if (lockProgress > 0.5f) {
                maskFilter = BlurMaskFilter(bracketThick * 2, BlurMaskFilter.Blur.NORMAL)
            }
        }

        // Top-Left bracket ⌐
        canvas.drawLine(cx - gap, cy - gap, cx - gap + bracketLen, cy - gap, paint)
        canvas.drawLine(cx - gap, cy - gap, cx - gap, cy - gap + bracketLen, paint)
        // Top-Right bracket ¬
        canvas.drawLine(cx + gap, cy - gap, cx + gap - bracketLen, cy - gap, paint)
        canvas.drawLine(cx + gap, cy - gap, cx + gap, cy - gap + bracketLen, paint)
        // Bottom-Left bracket L
        canvas.drawLine(cx - gap, cy + gap, cx - gap + bracketLen, cy + gap, paint)
        canvas.drawLine(cx - gap, cy + gap, cx - gap, cy + gap - bracketLen, paint)
        // Bottom-Right bracket ⌙
        canvas.drawLine(cx + gap, cy + gap, cx + gap - bracketLen, cy + gap, paint)
        canvas.drawLine(cx + gap, cy + gap, cx + gap, cy + gap - bracketLen, paint)

        // Centro: puntino quando locked
        if (lockProgress > 0.8f) {
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.color = Color.argb((255 * lockProgress).toInt(), Color.red(color), Color.green(color), Color.blue(color))
            }
            canvas.drawCircle(cx, cy, bracketThick * 2, dotPaint)
        }
    }

    /**
     * Disegna particelle cristalline orbitanti.
     */
    fun drawCrystalParticles(
        canvas: Canvas,
        cx: Float, cy: Float,
        radius: Float,
        count: Int = 8,
        phase: Float = 0f,
        color: Int = Color.parseColor("#00E5FF")
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        for (i in 0 until count) {
            val angle = (i * (360f / count) + phase * 360f) * Math.PI.toFloat() / 180f
            val orbitR = radius * (1.5f + 0.3f * sin(angle * 2 + phase * 6))
            val px = cx + cos(angle) * orbitR
            val py = cy + sin(angle) * orbitR * 0.6f // ellittico
            val pSize = radius * 0.04f * (0.5f + 0.5f * sin(phase * 10 + i.toFloat()))
            val pAlpha = (120 + 100 * sin(phase * 8 + i * 0.5f)).toInt().coerceIn(30, 255)
            paint.color = Color.argb(pAlpha, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawCircle(px, py, pSize, paint)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private fun buildEggPath(cx: Float, cy: Float, rX: Float, rYT: Float, rYB: Float) = Path().apply {
        moveTo(cx, cy - rYT)
        cubicTo(cx + rX * 1.1f, cy - rYT * 0.6f, cx + rX, cy + rYB * 0.4f, cx, cy + rYB)
        cubicTo(cx - rX, cy + rYB * 0.4f, cx - rX * 1.1f, cy - rYT * 0.6f, cx, cy - rYT)
        close()
    }

    private fun lighten(c: Int, f: Float) = Color.argb(
        Color.alpha(c),
        (Color.red(c) + (255 - Color.red(c)) * f).toInt().coerceIn(0, 255),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt().coerceIn(0, 255),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt().coerceIn(0, 255)
    )

    private fun darken(c: Int, f: Float) = Color.argb(
        Color.alpha(c),
        (Color.red(c) * (1 - f)).toInt().coerceIn(0, 255),
        (Color.green(c) * (1 - f)).toInt().coerceIn(0, 255),
        (Color.blue(c) * (1 - f)).toInt().coerceIn(0, 255)
    )
}
