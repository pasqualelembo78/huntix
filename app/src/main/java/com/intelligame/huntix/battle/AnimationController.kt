package com.intelligame.huntix.battle

import android.graphics.Color

data class Particle(
    var x: Float, var y: Float,
    val color: Int,
    var vx: Float, var vy: Float,
    var life: Float
)

data class DamageNumber(
    var x: Float, var y: Float,
    val value: Int,
    val crit: Boolean,
    var life: Float
)

data class MotionTrail(
    var x: Float, var y: Float,
    var size: Float,
    val color: Int,
    var life: Float,
    val decay: Float = 0.04f
)

class AnimationController {
    val particles = mutableListOf<Particle>()
    val damageNumbers = mutableListOf<DamageNumber>()
    val motionTrails = mutableListOf<MotionTrail>()
    var comboText: String? = null
        private set
    var comboLevel: Int = 0
        private set
    private var comboFadeTimer = 0f

    fun spawnHitParticles(x: Float, y: Float, color: Int, count: Int) {
        repeat(count) {
            val ang = Math.random() * Math.PI * 2
            val spd = 3f + Math.random().toFloat() * 6f
            particles.add(Particle(
                x, y, color,
                (Math.cos(ang) * spd).toFloat(),
                (Math.sin(ang) * spd - 3f).toFloat(),
                0.8f + Math.random().toFloat() * 0.4f
            ))
        }
    }

    fun spawnDamageNumber(x: Float, y: Float, value: Int, crit: Boolean) {
        val offsetX = (Math.random().toFloat() - 0.5f) * 30f
        damageNumbers.add(DamageNumber(x + offsetX, y, value, crit, 1f))
    }

    fun spawnMotionTrail(x: Float, y: Float, color: Int, size: Float = 8f) {
        motionTrails.add(MotionTrail(
            x, y, size, color, 0.3f
        ))
    }

    fun showComboCounter(combo: Int, level: Int) {
        comboText = if (combo >= 2) "$combo COMBO" else null
        comboLevel = level
        if (comboText != null) comboFadeTimer = 2f
    }

    fun update(dt: Float) {
        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 6f * dt
            p.life -= dt
            if (p.life <= 0f) pit.remove()
        }

        val dit = damageNumbers.iterator()
        while (dit.hasNext()) {
            val d = dit.next()
            d.y -= 1.5f
            d.life -= dt * 1.2f
            if (d.life <= 0f) dit.remove()
        }

        val tit = motionTrails.iterator()
        while (tit.hasNext()) {
            val t = tit.next()
            t.life -= t.decay
            t.size *= 0.95f
            if (t.life <= 0f) tit.remove()
        }

        if (comboFadeTimer > 0f) {
            comboFadeTimer -= dt
            if (comboFadeTimer <= 0f) {
                comboText = null
                comboLevel = 0
            }
        }
    }
}
