package com.intelligame.huntix.battle

import android.graphics.Color

/** Particella di impatto (posizione in pixel relativi all'arena). */
data class Particle(
    var x: Float, var y: Float,
    val color: Int,
    var vx: Float, var vy: Float,
    var life: Float
)

/** Numero di danno fluttuante. */
data class DamageNumber(
    var x: Float, var y: Float,
    val value: Int,
    val crit: Boolean,
    var life: Float
)

/** Controller delle animazioni: gestisce particelle, numeri danno e combo. */
class AnimationController {
    val particles = mutableListOf<Particle>()
    val damageNumbers = mutableListOf<DamageNumber>()
    var comboText: String? = null
        private set
    var comboLevel: Int = 0
        private set

    fun spawnHitParticles(x: Float, y: Float, color: Int, count: Int) {
        repeat(count) {
            val ang = Math.random() * Math.PI * 2
            val spd = 2f + Math.random().toFloat() * 4f
            particles.add(Particle(x, y, color,
                (Math.cos(ang) * spd).toFloat(),
                (Math.sin(ang) * spd - 2f).toFloat(),
                1f))
        }
    }

    fun spawnDamageNumber(x: Float, y: Float, value: Int, crit: Boolean) {
        damageNumbers.add(DamageNumber(x, y, value, crit, 1f))
    }

    fun showComboCounter(combo: Int, level: Int) {
        comboText = if (combo >= 2) "$combo COMBO" else null
        comboLevel = level
    }

    /** Avanza le animazioni di un frame (chiamato dall'ArenaView). */
    fun update(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx; p.y += p.vy; p.vy += 0.3f; p.life -= dt
            if (p.life <= 0f) it.remove()
        }
        val dit = damageNumbers.iterator()
        while (dit.hasNext()) {
            val d = dit.next()
            d.y -= 1f; d.life -= dt
            if (d.life <= 0f) dit.remove()
        }
        if (comboText != null) comboLevel = comboLevel // mantiene il valore
    }
}
