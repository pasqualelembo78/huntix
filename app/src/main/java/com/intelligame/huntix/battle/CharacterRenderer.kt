package com.intelligame.huntix.battle

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

class CharacterRenderer {

    enum class AnimState {
        IDLE, WALK, LIGHT_ATTACK, HEAVY_ATTACK, SPECIAL_ATTACK,
        BLOCK, HIT_REACT, KO, VICTORY
    }

    data class Pose(
        val spine: Float = 0f,
        val head: Float = 0f,
        val lArm1: Float = 0f,
        val lArm2: Float = 0f,
        val rArm1: Float = 0f,
        val rArm2: Float = 0f,
        val lLeg1: Float = 0f,
        val lLeg2: Float = 0f,
        val rLeg1: Float = 0f,
        val rLeg2: Float = 0f
    )

    data class KeyFrame(val t: Float, val pose: Pose)

    class Animation(
        val frames: List<KeyFrame>,
        val durationSec: Float,
        val loop: Boolean
    )

    companion object {
        private const val HEAD_R = 10f
        private const val TORSO_H = 26f
        private const val SHOULDER_HW = 14f
        private const val HIP_HW = 9f
        private const val UPPER_ARM = 17f
        private const val FOREARM = 15f
        private const val FIST_R = 4.5f
        private const val UPPER_LEG = 21f
        private const val LOWER_LEG = 20f
        private const val FOOT_W = 9f
        private const val FOOT_H = 4f

        private const val HIP_Y = -46f

        private val GUARD = Pose(
            spine = -3f, head = -2f,
            lArm1 = 55f, lArm2 = -55f,
            rArm1 = -55f, rArm2 = 58f,
            lLeg1 = 6f, lLeg2 = -4f,
            rLeg1 = -6f, rLeg2 = 4f
        )

        val IDLE = Animation(listOf(
            KeyFrame(0f, GUARD),
            KeyFrame(0.4f, Pose(
                spine = -2f, head = -3f,
                lArm1 = 57f, lArm2 = -58f,
                rArm1 = -57f, rArm2 = 60f,
                lLeg1 = 6f, lLeg2 = -4f,
                rLeg1 = -6f, rLeg2 = 4f
            )),
            KeyFrame(0.8f, Pose(
                spine = -4f, head = -1f,
                lArm1 = 53f, lArm2 = -52f,
                rArm1 = -53f, rArm2 = 56f,
                lLeg1 = 7f, lLeg2 = -5f,
                rLeg1 = -7f, rLeg2 = 5f
            )),
            KeyFrame(1f, GUARD)
        ), 1.2f, true)

        val WALK = Animation(listOf(
            KeyFrame(0f, Pose(
                spine = -3f, head = -2f,
                lArm1 = 55f, lArm2 = -55f,
                rArm1 = -55f, rArm2 = 58f,
                lLeg1 = 28f, lLeg2 = -12f,
                rLeg1 = -28f, rLeg2 = 8f
            )),
            KeyFrame(0.5f, Pose(
                spine = -2f, head = -2f,
                lArm1 = 50f, lArm2 = -50f,
                rArm1 = -50f, rArm2 = 52f,
                lLeg1 = -28f, lLeg2 = 8f,
                rLeg1 = 28f, rLeg2 = -12f
            )),
            KeyFrame(1f, Pose(
                spine = -3f, head = -2f,
                lArm1 = 55f, lArm2 = -55f,
                rArm1 = -55f, rArm2 = 58f,
                lLeg1 = 28f, lLeg2 = -12f,
                rLeg1 = -28f, rLeg2 = 8f
            ))
        ), 0.5f, true)

        val LIGHT_ATTACK = Animation(listOf(
            KeyFrame(0f, Pose(
                spine = -6f, head = -3f,
                lArm1 = 50f, lArm2 = -58f,
                rArm1 = -58f, rArm2 = 60f,
                lLeg1 = 6f, lLeg2 = -4f,
                rLeg1 = -6f, rLeg2 = 4f
            )),
            KeyFrame(0.2f, Pose(
                spine = 8f, head = -1f,
                lArm1 = 84f, lArm2 = -6f,
                rArm1 = -52f, rArm2 = 55f,
                lLeg1 = -4f, lLeg2 = -2f,
                rLeg1 = -8f, rLeg2 = 6f
            )),
            KeyFrame(0.4f, Pose(
                spine = 8f, head = -1f,
                lArm1 = 84f, lArm2 = -6f,
                rArm1 = -52f, rArm2 = 55f
            )),
            KeyFrame(0.65f, Pose(
                spine = 2f, head = -2f,
                lArm1 = 62f, lArm2 = -50f,
                rArm1 = -50f, rArm2 = 52f
            )),
            KeyFrame(1f, GUARD)
        ), 0.28f, false)

        val HEAVY_ATTACK = Animation(listOf(
            KeyFrame(0f, Pose(
                spine = -10f, head = -4f,
                lArm1 = 55f, lArm2 = -55f,
                rArm1 = -45f, rArm2 = 50f,
                lLeg1 = 8f, lLeg2 = -5f,
                rLeg1 = -8f, rLeg2 = 5f
            )),
            KeyFrame(0.2f, Pose(
                spine = -12f, head = -5f,
                lArm1 = 50f, lArm2 = -50f,
                rArm1 = -35f, rArm2 = 45f,
                lLeg1 = -10f, lLeg2 = 5f,
                rLeg1 = 65f, rLeg2 = -50f
            )),
            KeyFrame(0.4f, Pose(
                spine = 6f, head = -2f,
                lArm1 = 40f, lArm2 = -35f,
                rArm1 = -20f, rArm2 = 50f,
                lLeg1 = -6f, lLeg2 = 3f,
                rLeg1 = 75f, rLeg2 = 15f
            )),
            KeyFrame(0.55f, Pose(
                spine = 6f, head = -2f,
                lArm1 = 40f, lArm2 = -35f,
                rArm1 = -20f, rArm2 = 50f,
                lLeg1 = -6f, lLeg2 = 3f,
                rLeg1 = 75f, rLeg2 = 15f
            )),
            KeyFrame(0.75f, Pose(
                spine = -2f, head = -2f,
                lArm1 = 55f, lArm2 = -55f,
                rArm1 = -50f, rArm2 = 52f,
                lLeg1 = 6f, lLeg2 = -4f,
                rLeg1 = 20f, rLeg2 = -15f
            )),
            KeyFrame(1f, GUARD)
        ), 0.5f, false)

        val SPECIAL_ATTACK = Animation(listOf(
            KeyFrame(0f, Pose(
                spine = -14f, head = -4f,
                lArm1 = -35f, lArm2 = 65f,
                rArm1 = -50f, rArm2 = 75f,
                lLeg1 = 10f, lLeg2 = -8f,
                rLeg1 = -10f, rLeg2 = 8f
            )),
            KeyFrame(0.2f, Pose(
                spine = -8f, head = -3f,
                lArm1 = -25f, lArm2 = 55f,
                rArm1 = -40f, rArm2 = 65f,
                lLeg1 = -5f, lLeg2 = 3f,
                rLeg1 = 55f, rLeg2 = -45f
            )),
            KeyFrame(0.4f, Pose(
                spine = 16f, head = -1f,
                lArm1 = 82f, lArm2 = -5f,
                rArm1 = 85f, rArm2 = -8f,
                lLeg1 = -20f, lLeg2 = 15f,
                rLeg1 = 25f, rLeg2 = -20f
            )),
            KeyFrame(0.6f, Pose(
                spine = 16f, head = -1f,
                lArm1 = 82f, lArm2 = -5f,
                rArm1 = 85f, rArm2 = -8f,
                lLeg1 = -20f, lLeg2 = 15f,
                rLeg1 = 25f, rLeg2 = -20f
            )),
            KeyFrame(0.8f, Pose(
                spine = 2f, head = -2f,
                lArm1 = 58f, lArm2 = -52f,
                rArm1 = -52f, rArm2 = 54f,
                lLeg1 = 6f, lLeg2 = -4f,
                rLeg1 = -6f, rLeg2 = 4f
            )),
            KeyFrame(1f, GUARD)
        ), 0.55f, false)

        val BLOCK = Animation(listOf(
            KeyFrame(0f, Pose(
                spine = -5f, head = -5f,
                lArm1 = 58f, lArm2 = -65f,
                rArm1 = -58f, rArm2 = 62f
            ))
        ), 0.2f, false)

        val HIT_REACT = Animation(listOf(
            KeyFrame(0f, Pose(
                spine = 25f, head = 20f,
                lArm1 = -20f, lArm2 = 30f,
                rArm1 = -30f, rArm2 = 40f,
                lLeg1 = 12f, rLeg1 = -12f
            )),
            KeyFrame(0.35f, Pose(
                spine = 12f, head = 8f,
                lArm1 = 10f, lArm2 = -15f,
                rArm1 = -20f, rArm2 = 25f
            )),
            KeyFrame(0.7f, Pose(
                spine = 3f, head = 0f,
                lArm1 = 40f, lArm2 = -40f,
                rArm1 = -40f, rArm2 = 42f
            )),
            KeyFrame(1f, GUARD)
        ), 0.35f, false)

        val KO = Animation(listOf(
            KeyFrame(0f, Pose(
                spine = 28f, head = 22f,
                lArm1 = -20f, lArm2 = 30f,
                rArm1 = -30f, rArm2 = 35f
            )),
            KeyFrame(0.2f, Pose(
                spine = 55f, head = 30f,
                lLeg1 = 30f, rLeg1 = -15f, rLeg2 = 25f,
                lArm1 = -30f, lArm2 = 35f,
                rArm1 = -35f, rArm2 = 30f
            )),
            KeyFrame(0.5f, Pose(
                spine = 80f, head = 25f,
                lLeg1 = 40f, lLeg2 = 35f,
                rLeg1 = 20f, rLeg2 = 45f,
                lArm1 = -35f, lArm2 = 20f,
                rArm1 = -40f, rArm2 = 15f
            )),
            KeyFrame(1f, Pose(
                spine = 85f, head = 20f,
                lLeg1 = 45f, lLeg2 = 40f,
                rLeg1 = 25f, rLeg2 = 50f,
                lArm1 = -40f, lArm2 = 10f,
                rArm1 = -45f, rArm2 = 5f
            ))
        ), 0.9f, false)

        val VICTORY = Animation(listOf(
            KeyFrame(0f, Pose(
                spine = -5f, head = -8f,
                lArm1 = 72f, lArm2 = -75f,
                rArm1 = 72f, rArm2 = -75f
            )),
            KeyFrame(0.4f, Pose(
                spine = -7f, head = -12f,
                lArm1 = 80f, lArm2 = -85f,
                rArm1 = 80f, rArm2 = -85f
            )),
            KeyFrame(0.8f, Pose(
                spine = -5f, head = -8f,
                lArm1 = 72f, lArm2 = -75f,
                rArm1 = 72f, rArm2 = -75f
            )),
            KeyFrame(1f, Pose(
                spine = -5f, head = -8f,
                lArm1 = 72f, lArm2 = -75f,
                rArm1 = 72f, rArm2 = -75f
            ))
        ), 1.2f, true)

        private val ANIMS = mapOf(
            AnimState.IDLE to IDLE,
            AnimState.WALK to WALK,
            AnimState.LIGHT_ATTACK to LIGHT_ATTACK,
            AnimState.HEAVY_ATTACK to HEAVY_ATTACK,
            AnimState.SPECIAL_ATTACK to SPECIAL_ATTACK,
            AnimState.BLOCK to BLOCK,
            AnimState.HIT_REACT to HIT_REACT,
            AnimState.KO to KO,
            AnimState.VICTORY to VICTORY
        )

        fun animDuration(state: AnimState): Float =
            (ANIMS[state] ?: IDLE).durationSec
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

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
        val anim = ANIMS[effectiveState] ?: ANIMS[AnimState.IDLE]!!
        val pose = sample(anim, animProgress)

        canvas.save()
        canvas.translate(x, groundY)

        drawBody(canvas, pose, scale, facing, bodyColor, outlineColor)

        if (flashAlpha > 0f) {
            val flashIntensity = (flashAlpha * 255f).toInt().coerceIn(0, 220)
            paint.color = Color.argb(flashIntensity, 255, 255, 255)
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0f
            drawBody(canvas, pose, scale, facing, Color.WHITE, Color.WHITE)
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.alpha = 255
        }

        canvas.restore()
    }

    private fun drawBody(
        c: Canvas, pose: Pose, s: Float, f: Int,
        color: Int, outline: Int
    ) {
        val fx = f.toFloat()
        val hipX = 0f
        val hipY = HIP_Y * s

        val spineRad = Math.toRadians(pose.spine.toDouble())
        val shX = hipX + (sin(spineRad) * TORSO_H * s).toFloat() * fx
        val shY = hipY - (cos(spineRad) * TORSO_H * s).toFloat()

        val neckAngle = pose.spine + pose.head
        val neckRad = Math.toRadians(neckAngle.toDouble())
        val neckX = shX + (sin(neckRad) * 5f * s).toFloat() * fx
        val neckY = shY - (cos(neckRad) * 5f * s).toFloat()
        val headX = neckX + (sin(neckRad) * HEAD_R * s).toFloat() * fx
        val headY = neckY - (cos(neckRad) * HEAD_R * s).toFloat()

        drawTorso(c, hipX, hipY, shX, shY, fx, s, color, outline)

        drawLimb(c, neckX, neckY, neckAngle, HEAD_R * s, 0f, fx, s, color, outline, isHead = true)
        drawHeadDetail(c, headX, headY, HEAD_R * s, fx, pose.head)

        val lShX = shX - SHOULDER_HW * s * fx
        val rShX = shX + SHOULDER_HW * s * fx
        val shArmY = shY + 3f * s

        drawArm(c, rShX, shArmY, pose.rArm1, pose.rArm2, fx, s, color, outline)
        drawArm(c, lShX, shArmY, pose.lArm1, pose.lArm2, fx, s, color, outline)

        val lHipX = hipX - HIP_HW * s * fx
        val rHipX = hipX + HIP_HW * s * fx

        drawLeg(c, rHipX, hipY, pose.rLeg1, pose.rLeg2, fx, s, color, outline)
        drawLeg(c, lHipX, hipY, pose.lLeg1, pose.lLeg2, fx, s, color, outline)
    }

    private fun drawTorso(
        c: Canvas, hx: Float, hy: Float, sx: Float, sy: Float,
        fx: Float, s: Float, color: Int, outline: Int
    ) {
        path.reset()
        path.moveTo(hx - HIP_HW * s * fx, hy)
        path.lineTo(hx + HIP_HW * s * fx, hy)
        path.lineTo(sx + SHOULDER_HW * s * fx, sy)
        path.lineTo(sx - SHOULDER_HW * s * fx, sy)
        path.close()

        paint.color = color
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        c.drawPath(path, paint)

        paint.color = outline
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * s
        c.drawPath(path, paint)
    }

    private fun drawHeadDetail(c: Canvas, hx: Float, hy: Float, r: Float, fx: Float, headTilt: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(60, 0, 0, 0)
        val visorW = r * 0.7f
        val visorH = r * 0.3f
        val visorAngle = Math.toRadians((headTilt * 0.3f).toDouble())
        val vx = hx + (sin(visorAngle) * r * 0.15f).toFloat() * fx
        val vy = hy - (cos(visorAngle) * r * 0.15f).toFloat()
        c.drawOval(
            vx - visorW * 0.5f * fx, vy - visorH,
            vx + visorW * 0.5f * fx, vy + visorH,
            paint
        )
    }

    private fun drawArm(
        c: Canvas, sx: Float, sy: Float,
        arm1: Float, arm2: Float,
        fx: Float, s: Float, color: Int, outline: Int
    ) {
        val ua = arm1 * fx
        val elbowX = sx + (sin(Math.toRadians(ua.toDouble())) * UPPER_ARM * s).toFloat()
        val elbowY = sy - (cos(Math.toRadians(ua.toDouble())) * UPPER_ARM * s).toFloat()
        drawLimbSeg(c, sx, sy, elbowX, elbowY, 6.5f * s, color)

        val fa = (arm1 + arm2) * fx
        val handX = elbowX + (sin(Math.toRadians(fa.toDouble())) * FOREARM * s).toFloat()
        val handY = elbowY - (cos(Math.toRadians(fa.toDouble())) * FOREARM * s).toFloat()
        drawLimbSeg(c, elbowX, elbowY, handX, handY, 5.5f * s, color)

        paint.color = color
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        c.drawCircle(handX, handY, FIST_R * s, paint)
        paint.color = outline
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * s
        c.drawCircle(handX, handY, FIST_R * s, paint)
    }

    private fun drawLeg(
        c: Canvas, hx: Float, hy: Float,
        leg1: Float, leg2: Float,
        fx: Float, s: Float, color: Int, outline: Int
    ) {
        val l1 = leg1 * fx
        val kneeX = hx + (sin(Math.toRadians(l1.toDouble())) * UPPER_LEG * s).toFloat()
        val kneeY = hy - (cos(Math.toRadians(l1.toDouble())) * UPPER_LEG * s).toFloat()
        drawLimbSeg(c, hx, hy, kneeX, kneeY, 7.5f * s, color)

        val l2 = (leg1 + leg2) * fx
        val footX = kneeX + (sin(Math.toRadians(l2.toDouble())) * LOWER_LEG * s).toFloat()
        val footY = kneeY - (cos(Math.toRadians(l2.toDouble())) * LOWER_LEG * s).toFloat()
        drawLimbSeg(c, kneeX, kneeY, footX, footY, 6.5f * s, color)

        paint.color = color
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        c.drawOval(
            footX - FOOT_W * s * 0.5f * fx, footY - FOOT_H * s * 0.5f,
            footX + FOOT_W * s * 0.5f * fx, footY + FOOT_H * s * 0.5f,
            paint
        )
    }

    private fun drawLimb(
        c: Canvas, sx: Float, sy: Float,
        angle: Float, length: Float, extraAngle: Float,
        fx: Float, s: Float, color: Int, outline: Int,
        isHead: Boolean = false
    ) {
        if (!isHead) return
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        c.drawCircle(sx, sy, length, paint)
        paint.color = outline
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * s
        c.drawCircle(sx, sy, length, paint)
    }

    private fun drawLimbSeg(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        c.drawLine(x1, y1, x2, y2, paint)
    }

    fun sample(anim: Animation, progress: Float): Pose {
        val frames = anim.frames
        if (frames.size <= 1) return frames.first().pose

        val p = if (anim.loop) (progress % 1f) else progress.coerceIn(0f, 1f)
        val t = p * anim.durationSec

        var i = 0
        while (i < frames.size - 1 && frames[i + 1].t * anim.durationSec <= t) i++
        if (i >= frames.size - 1) return frames.last().pose

        val k1 = frames[i]
        val k2 = frames[i + 1]
        val t1 = k1.t * anim.durationSec
        val t2 = k2.t * anim.durationSec
        val blend = if (t2 > t1) ((t - t1) / (t2 - t1)).coerceIn(0f, 1f) else 0f

        return lerp(k1.pose, k2.pose, blend)
    }

    private fun lerp(a: Pose, b: Pose, t: Float): Pose {
        fun l(v: Float, w: Float) = v + (w - v) * t
        return Pose(
            spine = l(a.spine, b.spine),
            head = l(a.head, b.head),
            lArm1 = l(a.lArm1, b.lArm1), lArm2 = l(a.lArm2, b.lArm2),
            rArm1 = l(a.rArm1, b.rArm1), rArm2 = l(a.rArm2, b.rArm2),
            lLeg1 = l(a.lLeg1, b.lLeg1), lLeg2 = l(a.lLeg2, b.lLeg2),
            rLeg1 = l(a.rLeg1, b.rLeg1), rLeg2 = l(a.rLeg2, b.rLeg2)
        )
    }
}
