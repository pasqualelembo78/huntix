package com.intelligame.huntix.battle

import android.content.Context
import android.graphics.*
import com.intelligame.huntix.battle.CharacterRenderer.AnimState
import kotlin.math.*

class SpriteSheetRenderer(private val context: Context? = null) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val limbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var spritesLoaded = false
    private val spriteCache = mutableMapOf<String, Bitmap>()
    private val frameCountCache = mutableMapOf<String, Int>()

    private val BRAWLER_FILE_MAP = mapOf(
        AnimState.IDLE to "idle.png",
        AnimState.WALK to "walk.png",
        AnimState.LIGHT_ATTACK to "light_attack.png",
        AnimState.HEAVY_ATTACK to "heavy_attack.png",
        AnimState.SPECIAL_ATTACK to "special_attack.png",
        AnimState.BLOCK to "block.png",
        AnimState.HIT_REACT to "hit_react.png",
        AnimState.KO to "ko.png",
        AnimState.VICTORY to "victory.png"
    )

    private val BRAWLER_FRAME_COUNTS = mapOf(
        "idle.png" to 4,
        "walk.png" to 6,
        "light_attack.png" to 4,
        "heavy_attack.png" to 4,
        "special_attack.png" to 4,
        "block.png" to 2,
        "hit_react.png" to 3,
        "ko.png" to 4,
        "victory.png" to 4
    )

    fun loadSprites() {
        if (context == null || spritesLoaded) return
        try {
            val files = context.assets.list("battle") ?: emptyArray()
            if (files.isEmpty()) return

            for ((state, fileName) in BRAWLER_FILE_MAP) {
                val path = "battle/$fileName"
                try {
                    context.assets.open(path).use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            spriteCache[state.name] = bitmap
                            frameCountCache[state.name] = BRAWLER_FRAME_COUNTS[fileName] ?: 1
                        }
                    }
                } catch (_: Exception) {}
            }
            spritesLoaded = spriteCache.isNotEmpty()
        } catch (_: Exception) {}
    }

    fun draw(
        canvas: Canvas,
        state: AnimState,
        animProgress: Float,
        x: Float,
        groundY: Float,
        scale: Float,
        facing: Int,
        bodyColor: Int,
        outlineColor: Int,
        flashAlpha: Float = 0f,
        isBlocking: Boolean = false
    ) {
        val effectiveState = if (isBlocking && state != AnimState.KO && state != AnimState.HIT_REACT)
            AnimState.BLOCK else state

        if (spritesLoaded && spriteCache.isNotEmpty()) {
            drawSpriteBased(canvas, effectiveState, animProgress, x, groundY, scale, facing, flashAlpha)
        } else {
            drawProcedural(canvas, effectiveState, animProgress, x, groundY, scale, facing,
                bodyColor, outlineColor, flashAlpha)
        }
    }

    private fun drawSpriteBased(
        canvas: Canvas, state: AnimState, progress: Float,
        x: Float, groundY: Float, s: Float, facing: Int, flashAlpha: Float
    ) {
        val bitmap = spriteCache[state.name] ?: return
        val frameCount = frameCountCache[state.name] ?: 1

        val frameW = bitmap.width / frameCount
        val frameH = bitmap.height
        val frameIndex = (progress * frameCount).toInt().coerceIn(0, frameCount - 1)

        val src = Rect(frameIndex * frameW, 0, (frameIndex + 1) * frameW, frameH)
        val drawW = frameW * s * 2f
        val drawH = frameH * s * 2f
        val dst = RectF(x - drawW / 2, groundY - drawH, x + drawW / 2, groundY)

        canvas.save()
        if (facing < 0) {
            canvas.scale(-1f, 1f, x, groundY - drawH / 2)
        }

        canvas.drawBitmap(bitmap, src, dst, null)

        if (flashAlpha > 0f) {
            paint.color = Color.WHITE
            paint.alpha = (flashAlpha * 180f).toInt().coerceIn(0, 200)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            canvas.drawRect(dst, paint)
            paint.xfermode = null
        }

        canvas.restore()
    }

    private fun drawProcedural(
        canvas: Canvas, state: AnimState, progress: Float,
        x: Float, groundY: Float, s: Float, facing: Int,
        bodyColor: Int, outlineColor: Int, flashAlpha: Float
    ) {
        val fx = facing.toFloat()
        val pose = getProceduralPose(state, progress)

        canvas.save()
        canvas.translate(x, groundY)

        drawProceduralShadow(canvas, s)

        drawProceduralBody(canvas, pose, s, fx, bodyColor, outlineColor)

        if (flashAlpha > 0f) {
            val flashIntensity = (flashAlpha * 220f).toInt().coerceIn(0, 220)
            paint.color = Color.argb(flashIntensity, 255, 255, 255)
            paint.style = Paint.Style.FILL
            drawProceduralBody(canvas, pose, s, fx, Color.WHITE, Color.WHITE)
            paint.alpha = 255
        }

        canvas.restore()
    }

    private data class ProceduralPose(
        val torsoLean: Float = 0f,
        val headTilt: Float = 0f,
        val rArmAngle: Float = -30f,
        val rForearmAngle: Float = 40f,
        val lArmAngle: Float = 30f,
        val lForearmAngle: Float = -40f,
        val rLegAngle: Float = -10f,
        val rShinAngle: Float = 20f,
        val lLegAngle: Float = 10f,
        val lShinAngle: Float = -20f,
        val squash: Float = 1f,
        val bodyWidth: Float = 1f
    )

    private fun getProceduralPose(state: AnimState, progress: Float): ProceduralPose {
        return when (state) {
            AnimState.IDLE -> {
                val bob = sin(progress * PI.toFloat() * 2.5f) * 2f
                ProceduralPose(
                    torsoLean = -4f + bob * 0.3f,
                    headTilt = -3f + bob * 0.2f,
                    lArmAngle = 60f - bob,
                    lForearmAngle = -60f + bob * 0.5f,
                    rArmAngle = -60f + bob,
                    rForearmAngle = 62f - bob * 0.5f,
                    lLegAngle = 8f - bob * 0.3f,
                    lShinAngle = -6f,
                    rLegAngle = -8f + bob * 0.3f,
                    rShinAngle = 6f,
                    squash = 1f + sin(progress * PI.toFloat() * 4f) * 0.015f
                )
            }
            AnimState.WALK -> {
                val swing = sin(progress * PI.toFloat() * 2f)
                ProceduralPose(
                    torsoLean = -3f,
                    headTilt = -2f,
                    lArmAngle = 55f,
                    lForearmAngle = -55f,
                    rArmAngle = -55f,
                    rForearmAngle = 58f,
                    lLegAngle = 28f + swing * 30f,
                    lShinAngle = -12f - swing * 10f,
                    rLegAngle = -28f - swing * 30f,
                    rShinAngle = 8f + swing * 10f
                )
            }
            AnimState.LIGHT_ATTACK -> {
                val t = progress
                when {
                    t < 0.2f -> ProceduralPose(
                        torsoLean = -6f, headTilt = -3f,
                        lArmAngle = 50f, lForearmAngle = -58f,
                        rArmAngle = -58f, rForearmAngle = 60f,
                        lLegAngle = 6f, lShinAngle = -4f,
                        rLegAngle = -6f, rShinAngle = 4f
                    )
                    t < 0.4f -> ProceduralPose(
                        torsoLean = 8f, headTilt = -1f,
                        lArmAngle = 84f, lForearmAngle = -6f,
                        rArmAngle = -52f, rForearmAngle = 55f,
                        lLegAngle = -4f, lShinAngle = -2f,
                        rLegAngle = -8f, rShinAngle = 6f,
                        bodyWidth = 1.1f
                    )
                    t < 0.55f -> ProceduralPose(
                        torsoLean = 8f, headTilt = -1f,
                        lArmAngle = 84f, lForearmAngle = -6f,
                        rArmAngle = -52f, rForearmAngle = 55f,
                        bodyWidth = 1.1f
                    )
                    t < 0.7f -> ProceduralPose(
                        torsoLean = 2f, headTilt = -2f,
                        lArmAngle = 62f, lForearmAngle = -50f,
                        rArmAngle = -50f, rForearmAngle = 52f
                    )
                    else -> ProceduralPose(
                        torsoLean = -3f, headTilt = -2f,
                        lArmAngle = 55f, lForearmAngle = -55f,
                        rArmAngle = -55f, rForearmAngle = 58f
                    )
                }
            }
            AnimState.HEAVY_ATTACK -> {
                val t = progress
                when {
                    t < 0.2f -> ProceduralPose(
                        torsoLean = -10f, headTilt = -4f,
                        lArmAngle = 55f, lForearmAngle = -55f,
                        rArmAngle = -45f, rForearmAngle = 50f,
                        lLegAngle = 8f, lShinAngle = -5f,
                        rLegAngle = -8f, rShinAngle = 5f
                    )
                    t < 0.35f -> ProceduralPose(
                        torsoLean = -12f, headTilt = -5f,
                        lArmAngle = 50f, lForearmAngle = -50f,
                        rArmAngle = -35f, rForearmAngle = 45f,
                        lLegAngle = -10f, lShinAngle = 5f,
                        rLegAngle = 65f, rShinAngle = -50f,
                        squash = 0.92f
                    )
                    t < 0.55f -> ProceduralPose(
                        torsoLean = 6f, headTilt = -2f,
                        lArmAngle = 40f, lForearmAngle = -35f,
                        rArmAngle = -20f, rForearmAngle = 50f,
                        lLegAngle = -6f, lShinAngle = 3f,
                        rLegAngle = 75f, rShinAngle = 15f,
                        bodyWidth = 1.15f, squash = 0.95f
                    )
                    t < 0.7f -> ProceduralPose(
                        torsoLean = 6f, headTilt = -2f,
                        lArmAngle = 40f, lForearmAngle = -35f,
                        rArmAngle = -20f, rForearmAngle = 50f,
                        lLegAngle = -6f, lShinAngle = 3f,
                        rLegAngle = 75f, rShinAngle = 15f
                    )
                    t < 0.85f -> ProceduralPose(
                        torsoLean = -2f, headTilt = -2f,
                        lArmAngle = 55f, lForearmAngle = -55f,
                        rArmAngle = -50f, rForearmAngle = 52f,
                        lLegAngle = 6f, lShinAngle = -4f,
                        rLegAngle = 20f, rShinAngle = -15f
                    )
                    else -> ProceduralPose(
                        torsoLean = -3f, headTilt = -2f,
                        lArmAngle = 55f, lForearmAngle = -55f,
                        rArmAngle = -55f, rForearmAngle = 58f
                    )
                }
            }
            AnimState.SPECIAL_ATTACK -> {
                val t = progress
                when {
                    t < 0.2f -> ProceduralPose(
                        torsoLean = -14f, headTilt = -4f,
                        lArmAngle = -35f, lForearmAngle = 65f,
                        rArmAngle = -50f, rForearmAngle = 75f,
                        lLegAngle = 10f, lShinAngle = -8f,
                        rLegAngle = -10f, rShinAngle = 8f,
                        squash = 0.9f
                    )
                    t < 0.35f -> ProceduralPose(
                        torsoLean = -8f, headTilt = -3f,
                        lArmAngle = -25f, lForearmAngle = 55f,
                        rArmAngle = -40f, rForearmAngle = 65f,
                        lLegAngle = -5f, lShinAngle = 3f,
                        rLegAngle = 55f, rShinAngle = -45f,
                        squash = 0.95f
                    )
                    t < 0.6f -> ProceduralPose(
                        torsoLean = 16f, headTilt = -1f,
                        lArmAngle = 82f, lForearmAngle = -5f,
                        rArmAngle = 85f, rForearmAngle = -8f,
                        lLegAngle = -20f, lShinAngle = 15f,
                        rLegAngle = 25f, rShinAngle = -20f,
                        bodyWidth = 1.2f, squash = 0.95f
                    )
                    t < 0.75f -> ProceduralPose(
                        torsoLean = 16f, headTilt = -1f,
                        lArmAngle = 82f, lForearmAngle = -5f,
                        rArmAngle = 85f, rForearmAngle = -8f,
                        lLegAngle = -20f, lShinAngle = 15f,
                        rLegAngle = 25f, rShinAngle = -20f,
                        bodyWidth = 1.2f
                    )
                    t < 0.9f -> ProceduralPose(
                        torsoLean = 2f, headTilt = -2f,
                        lArmAngle = 58f, lForearmAngle = -52f,
                        rArmAngle = -52f, rForearmAngle = 54f,
                        lLegAngle = 6f, lShinAngle = -4f,
                        rLegAngle = -6f, rShinAngle = 4f
                    )
                    else -> ProceduralPose(
                        torsoLean = -3f, headTilt = -2f,
                        lArmAngle = 55f, lForearmAngle = -55f,
                        rArmAngle = -55f, rForearmAngle = 58f
                    )
                }
            }
            AnimState.BLOCK -> ProceduralPose(
                torsoLean = -5f, headTilt = -5f,
                lArmAngle = 58f, lForearmAngle = -65f,
                rArmAngle = -58f, rForearmAngle = 62f,
                squash = 0.95f
            )
            AnimState.HIT_REACT -> {
                val t = progress
                when {
                    t < 0.3f -> ProceduralPose(
                        torsoLean = 25f, headTilt = 20f,
                        lArmAngle = -20f, lForearmAngle = 30f,
                        rArmAngle = -30f, rForearmAngle = 40f,
                        lLegAngle = 12f, rLegAngle = -12f,
                        squash = 0.92f
                    )
                    t < 0.5f -> ProceduralPose(
                        torsoLean = 12f, headTilt = 8f,
                        lArmAngle = 10f, lForearmAngle = -15f,
                        rArmAngle = -20f, rForearmAngle = 25f
                    )
                    t < 0.75f -> ProceduralPose(
                        torsoLean = 3f, headTilt = 0f,
                        lArmAngle = 40f, lForearmAngle = -40f,
                        rArmAngle = -40f, rForearmAngle = 42f
                    )
                    else -> ProceduralPose(
                        torsoLean = -3f, headTilt = -2f,
                        lArmAngle = 55f, lForearmAngle = -55f,
                        rArmAngle = -55f, rForearmAngle = 58f
                    )
                }
            }
            AnimState.KO -> {
                val t = progress
                ProceduralPose(
                    torsoLean = 28f + t * 55f,
                    headTilt = 22f + t * 15f,
                    lArmAngle = -20f - t * 20f,
                    lForearmAngle = 30f + t * 10f,
                    rArmAngle = -30f - t * 15f,
                    rForearmAngle = 35f + t * 10f,
                    lLegAngle = 15f + t * 30f,
                    lShinAngle = 25f + t * 20f,
                    rLegAngle = -15f + t * 20f,
                    rShinAngle = 25f + t * 25f,
                    squash = 1f - t * 0.3f
                )
            }
            AnimState.VICTORY -> {
                val bob = sin(progress * PI.toFloat() * 3f) * 3f
                ProceduralPose(
                    torsoLean = -5f + bob,
                    headTilt = -8f + bob * 0.5f,
                    lArmAngle = 72f,
                    lForearmAngle = -75f,
                    rArmAngle = 72f,
                    rForearmAngle = -75f
                )
            }
        }
    }

    private fun drawProceduralShadow(c: Canvas, s: Float) {
        shadowPaint.color = Color.BLACK
        shadowPaint.alpha = 35
        shadowPaint.style = Paint.Style.FILL
        c.drawOval(-22f * s, 2f, 22f * s, 8f * s, shadowPaint)
        shadowPaint.alpha = 255
    }

    private fun drawProceduralBody(
        c: Canvas, pose: ProceduralPose, s: Float, fx: Float,
        color: Int, outline: Int
    ) {
        val hipY = -50f * s * pose.squash
        val torsoH = 30f * s
        val shoulderW = 16f * s * pose.bodyWidth
        val hipW = 11f * s

        val leanRad = Math.toRadians(pose.torsoLean.toDouble())
        val shX = sin(leanRad).toFloat() * torsoH * fx
        val shY = hipY - cos(leanRad).toFloat() * torsoH

        drawTorsoShape(c, 0f, hipY, shX, shY, shoulderW, hipW, fx, s, color, outline)

        val neckX = shX + sin(leanRad).toFloat() * 6f * fx
        val neckY = shY - cos(leanRad).toFloat() * 6f
        val headR = 12f * s
        val headX = neckX + sin(leanRad).toFloat() * headR * 0.8f * fx
        val headY = neckY - cos(leanRad).toFloat() * headR * 0.8f

        drawHeadShape(c, headX, headY, headR, s, color, outline, pose.headTilt)

        val rShX = shX + shoulderW * 0.9f * fx
        val lShX = shX - shoulderW * 0.9f * fx
        val armY = shY + 4f * s

        drawArmShape(c, rShX, armY, pose.rArmAngle, pose.rForearmAngle, fx, s, color, outline, true)
        drawArmShape(c, lShX, armY, pose.lArmAngle, pose.lForearmAngle, fx, s, color, outline, false)

        val rHipX = hipW * 0.6f * fx
        val lHipX = -hipW * 0.6f * fx

        drawLegShape(c, rHipX, hipY, pose.rLegAngle, pose.rShinAngle, fx, s, color, outline)
        drawLegShape(c, lHipX, hipY, pose.lLegAngle, pose.lShinAngle, fx, s, color, outline)
    }

    private fun drawTorsoShape(
        c: Canvas, hx: Float, hy: Float, sx: Float, sy: Float,
        shoulderW: Float, hipW: Float, fx: Float, s: Float,
        color: Int, outline: Int
    ) {
        val path = Path()
        path.moveTo(hx - hipW * fx, hy)
        path.lineTo(hx + hipW * fx, hy)
        path.quadTo(
            hx + hipW * 1.3f * fx, hy + (sy - hy) * 0.3f,
            sx + shoulderW * fx, sy
        )
        path.lineTo(sx - shoulderW * fx, sy)
        path.quadTo(
            hx - hipW * 1.3f * fx, hy + (sy - hy) * 0.3f,
            hx - hipW * fx, hy
        )
        path.close()

        bodyPaint.color = color
        bodyPaint.style = Paint.Style.FILL
        c.drawPath(path, bodyPaint)

        val grad = LinearGradient(
            sx, sy, hx, hy,
            Color.argb(50, 255, 255, 255), Color.argb(0, 0, 0, 0),
            Shader.TileMode.CLAMP
        )
        bodyPaint.shader = grad
        c.drawPath(path, bodyPaint)
        bodyPaint.shader = null

        outlinePaint.color = outline
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.strokeWidth = 2.2f * s
        c.drawPath(path, outlinePaint)
    }

    private fun drawHeadShape(
        c: Canvas, hx: Float, hy: Float, r: Float, s: Float,
        color: Int, outline: Int, tilt: Float
    ) {
        bodyPaint.color = color
        bodyPaint.style = Paint.Style.FILL
        c.drawCircle(hx, hy, r, bodyPaint)

        val headGrad = RadialGradient(
            hx - r * 0.2f, hy - r * 0.2f, r,
            intArrayOf(Color.argb(60, 255, 255, 255), Color.argb(0, 0, 0, 0)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        bodyPaint.shader = headGrad
        c.drawCircle(hx, hy, r, bodyPaint)
        bodyPaint.shader = null

        outlinePaint.color = outline
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.strokeWidth = 2f * s
        c.drawCircle(hx, hy, r, outlinePaint)

        val eyeOffsetX = 3f * s * (if (tilt > 0) 1f else -1f)
        val eyeY = hy - 2f * s
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        c.drawOval(hx + eyeOffsetX - 4f * s, eyeY - 3f * s, hx + eyeOffsetX + 2f * s, eyeY + 2f * s, paint)
        paint.color = Color.BLACK
        c.drawCircle(hx + eyeOffsetX - 0.5f * s, eyeY, 1.8f * s, paint)
    }

    private fun drawArmShape(
        c: Canvas, sx: Float, sy: Float,
        armAngle: Float, forearmAngle: Float,
        fx: Float, s: Float, color: Int, outline: Int,
        @Suppress("UNUSED_PARAMETER") isRight: Boolean
    ) {
        val upperLen = 18f * s
        val forearmLen = 16f * s
        val upperW = 7f * s
        val forearmW = 6f * s
        val fistR = 5f * s

        val a1 = armAngle * fx
        val elbowX = sx + sin(Math.toRadians(a1.toDouble())).toFloat() * upperLen
        val elbowY = sy - cos(Math.toRadians(a1.toDouble())).toFloat() * upperLen

        drawLimbSegment(c, sx, sy, elbowX, elbowY, upperW, color, outline, s)

        val a2 = (armAngle + forearmAngle) * fx
        val handX = elbowX + sin(Math.toRadians(a2.toDouble())).toFloat() * forearmLen
        val handY = elbowY - cos(Math.toRadians(a2.toDouble())).toFloat() * forearmLen

        drawLimbSegment(c, elbowX, elbowY, handX, handY, forearmW, color, outline, s)

        bodyPaint.color = color
        bodyPaint.style = Paint.Style.FILL
        c.drawCircle(handX, handY, fistR, bodyPaint)

        val fistGrad = RadialGradient(
            handX - fistR * 0.2f, handY - fistR * 0.2f, fistR,
            intArrayOf(Color.argb(40, 255, 255, 255), Color.argb(0, 0, 0, 0)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        bodyPaint.shader = fistGrad
        c.drawCircle(handX, handY, fistR, bodyPaint)
        bodyPaint.shader = null

        outlinePaint.color = outline
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.strokeWidth = 1.8f * s
        c.drawCircle(handX, handY, fistR, outlinePaint)
    }

    private fun drawLegShape(
        c: Canvas, hx: Float, hy: Float,
        legAngle: Float, shinAngle: Float,
        fx: Float, s: Float, color: Int, outline: Int
    ) {
        val upperLen = 22f * s
        val shinLen = 21f * s
        val upperW = 8.5f * s
        val shinW = 7f * s
        val footW = 10f * s
        val footH = 5f * s

        val a1 = legAngle * fx
        val kneeX = hx + sin(Math.toRadians(a1.toDouble())).toFloat() * upperLen
        val kneeY = hy - cos(Math.toRadians(a1.toDouble())).toFloat() * upperLen

        drawLimbSegment(c, hx, hy, kneeX, kneeY, upperW, color, outline, s)

        val a2 = (legAngle + shinAngle) * fx
        val footX = kneeX + sin(Math.toRadians(a2.toDouble())).toFloat() * shinLen
        val footY = kneeY - cos(Math.toRadians(a2.toDouble())).toFloat() * shinLen

        drawLimbSegment(c, kneeX, kneeY, footX, footY, shinW, color, outline, s)

        bodyPaint.color = color
        bodyPaint.style = Paint.Style.FILL
        c.drawOval(
            footX - footW * 0.5f * fx, footY - footH * 0.5f,
            footX + footW * 0.5f * fx, footY + footH * 0.5f,
            bodyPaint
        )
        outlinePaint.color = outline
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.strokeWidth = 1.5f * s
        c.drawOval(
            footX - footW * 0.5f * fx, footY - footH * 0.5f,
            footX + footW * 0.5f * fx, footY + footH * 0.5f,
            outlinePaint
        )
    }

    private fun drawLimbSegment(
        c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float,
        width: Float, color: Int, outline: Int, s: Float
    ) {
        limbPaint.color = color
        limbPaint.style = Paint.Style.STROKE
        limbPaint.strokeWidth = width
        limbPaint.strokeCap = Paint.Cap.ROUND
        limbPaint.strokeJoin = Paint.Join.ROUND
        c.drawLine(x1, y1, x2, y2, limbPaint)

        val grad = LinearGradient(
            x1, y1, x2, y2,
            Color.argb(30, 255, 255, 255), Color.argb(0, 0, 0, 0),
            Shader.TileMode.CLAMP
        )
        limbPaint.shader = grad
        c.drawLine(x1, y1, x2, y2, limbPaint)
        limbPaint.shader = null

        outlinePaint.color = outline
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.strokeWidth = width + 1.5f * s
        outlinePaint.strokeCap = Paint.Cap.ROUND
        c.drawLine(x1, y1, x2, y2, outlinePaint)
        outlinePaint.strokeCap = Paint.Cap.BUTT
    }
}
