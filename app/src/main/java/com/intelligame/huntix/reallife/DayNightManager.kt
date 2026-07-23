package com.intelligame.huntix.reallife

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * DayNightManager — gestisce il ciclo giorno/notte virtuale.
 *
 * Il tempo virtuale avanza di ~1h ogni 30s reali (un giorno = ~12 min).
 * Fornisce colori cielo, posizioni sole/luna, intensità luce, stato finestre.
 */
class DayNightManager {

    /** Ora virtuale corrente (0-24). Inizia alle 10:00 (mattina). */
    var hour: Float = 10f
        private set

    /** Secondi reali trascorsi dall'ultimo avanzamento */
    private var accumDt = 0f

    /** Velocità: ore虚拟 per secondo reale (1h / 30s = 0.0333) */
    private val speed = 1f / 30f

    /** Avanza il tempo in base al dt reale */
    fun advance(dt: Float) {
        accumDt += dt
        hour += speed * dt
        if (hour >= 24f) hour -= 24f
    }

    /** Imposta un'ora specifica (per debug o sync) */
    fun setHour(h: Float) {
        hour = h.coerceIn(0f, 24f)
    }

    // ── Sky colors ────────────────────────────────────────────────

    data class SkyColors(
        val topColor: Int,
        val bottomColor: Int,
        val overlayAlpha: Float,   // 0 = trasparente, 1 = opaco
        val overlayColor: Int      // colore overlay (nero per notte, arancione per alba/tramonto)
    )

    /** Restituisce i colori del cielo per l'ora corrente */
    fun getSkyColors(): SkyColors {
        val h = hour
        return when {
            h < 5f -> night(h, 5f)
            h < 7f -> dawn(h, 5f, 7f)
            h < 10f -> morning(h, 7f, 10f)
            h < 16f -> day(h, 10f, 16f)
            h < 19f -> sunset(h, 16f, 19f)
            h < 21f -> dusk(h, 19f, 21f)
            else -> night(h, 24f)
        }
    }

    private fun night(h: Float, end: Float) = SkyColors(
        topColor = Color.rgb(10, 10, 26),
        bottomColor = Color.rgb(13, 13, 43),
        overlayAlpha = 0.35f,
        overlayColor = Color.rgb(5, 5, 20)
    )

    private fun dawn(h: Float, start: Float, end: Float) = SkyColors(
        topColor = lerpColor(Color.rgb(26, 10, 62), Color.rgb(255, 107, 53), (h - start) / (end - start)),
        bottomColor = lerpColor(Color.rgb(255, 140, 66), Color.rgb(255, 179, 71), (h - start) / (end - start)),
        overlayAlpha = lerp(0.3f, 0.08f, (h - start) / (end - start)),
        overlayColor = Color.rgb(255, 140, 66)
    )

    private fun morning(h: Float, start: Float, end: Float) = SkyColors(
        topColor = lerpColor(Color.rgb(74, 144, 217), Color.rgb(46, 134, 193), (h - start) / (end - start)),
        bottomColor = lerpColor(Color.rgb(135, 206, 235), Color.rgb(133, 193, 233), (h - start) / (end - start)),
        overlayAlpha = lerp(0.08f, 0.02f, (h - start) / (end - start)),
        overlayColor = Color.TRANSPARENT
    )

    private fun day(h: Float, start: Float, end: Float) = SkyColors(
        topColor = Color.rgb(46, 134, 193),
        bottomColor = Color.rgb(133, 193, 233),
        overlayAlpha = 0.02f,
        overlayColor = Color.TRANSPARENT
    )

    private fun sunset(h: Float, start: Float, end: Float) = SkyColors(
        topColor = lerpColor(Color.rgb(231, 76, 60), Color.rgb(142, 68, 173), (h - start) / (end - start)),
        bottomColor = lerpColor(Color.rgb(243, 156, 18), Color.rgb(230, 126, 34), (h - start) / (end - start)),
        overlayAlpha = lerp(0.05f, 0.25f, (h - start) / (end - start)),
        overlayColor = Color.rgb(243, 156, 18)
    )

    private fun dusk(h: Float, start: Float, end: Float) = SkyColors(
        topColor = lerpColor(Color.rgb(26, 10, 62), Color.rgb(10, 10, 26), (h - start) / (end - start)),
        bottomColor = lerpColor(Color.rgb(44, 22, 84), Color.rgb(13, 13, 43), (h - start) / (end - start)),
        overlayAlpha = lerp(0.25f, 0.35f, (h - start) / (end - start)),
        overlayColor = Color.rgb(20, 10, 50)
    )

    // ── Sun / Moon position ───────────────────────────────────────

    /**
     * Posizione del sole normalizzata [0,1] x [0,1].
     * x: 0 = est (sunrise), 0.5 = zenith, 1 = ovest (sunset)
     * y: 0 = orizzonte, 1 = alto cielo
     * Visibile solo tra le 5 e le 20.
     */
    fun getSunPosition(): Pair<Float, Float>? {
        if (hour < 5f || hour > 20f) return null
        val progress = (hour - 5f) / 15f  // 0 a 1
        val x = progress
        val y = sin(progress * Math.PI).toFloat()  // arco 0→1→0
        return x to y
    }

    /**
     * Posizione della luna normalizzata [0,1] x [0,1].
     * Visibile tra le 19 e le 5.
     */
    fun getMoonPosition(): Pair<Float, Float>? {
        if (hour > 5f && hour < 19f) return null
        val progress = if (hour >= 19f) {
            (hour - 19f) / 10f  // 19→24 = 0→0.5
        } else {
            (hour + 5f) / 10f  // 0→5 = 0.5→1
        }
        val x = progress
        val y = sin(progress * Math.PI).toFloat()
        return x to y
    }

    // ── Light intensity ───────────────────────────────────────────

    /** Intensità luce principale [0.1, 1.0] */
    fun getLightIntensity(): Float {
        return when {
            hour < 5f -> 0.15f
            hour < 7f -> lerp(0.15f, 0.8f, (hour - 5f) / 2f)
            hour < 10f -> lerp(0.8f, 1f, (hour - 7f) / 3f)
            hour < 16f -> 1f
            hour < 19f -> lerp(1f, 0.3f, (hour - 16f) / 3f)
            hour < 21f -> lerp(0.3f, 0.15f, (hour - 19f) / 2f)
            else -> 0.15f
        }
    }

    /** Colore luce principale (warm at dawn/dusk, white at day, blue at night) */
    fun getLightColor(): Int {
        return when {
            hour < 5f -> Color.rgb(100, 120, 180)
            hour < 7f -> lerpColor(Color.rgb(100, 120, 180), Color.rgb(255, 200, 150), (hour - 5f) / 2f)
            hour < 10f -> lerpColor(Color.rgb(255, 200, 150), Color.rgb(255, 255, 255), (hour - 7f) / 3f)
            hour < 16f -> Color.rgb(255, 255, 255)
            hour < 19f -> lerpColor(Color.rgb(255, 255, 255), Color.rgb(255, 150, 80), (hour - 16f) / 3f)
            hour < 21f -> lerpColor(Color.rgb(255, 150, 80), Color.rgb(100, 120, 180), (hour - 19f) / 2f)
            else -> Color.rgb(100, 120, 180)
        }
    }

    // ── Window / lamp brightness ───────────────────────────────────

    /**
     * Luminosità finestre/lampioni [0, 1].
     * 0 = spento (giorno), 1 = acceso (notte).
     */
    fun getWindowBrightness(): Float {
        return when {
            hour < 5f -> 1f
            hour < 7f -> lerp(1f, 0f, (hour - 5f) / 2f)
            hour < 18f -> 0f
            hour < 20f -> lerp(0f, 1f, (hour - 18f) / 2f)
            else -> 1f
        }
    }

    /** Colore finestra (da azzurro giorno a giallo notte) */
    fun getWindowColor(): Int {
        val b = getWindowBrightness()
        return lerpColor(0x90CAF9.toInt(), 0xFFCC02.toInt(), b)
    }

    /** Colore lampione (da grigio a giallo brillante) */
    fun getLampColor(): Int {
        val b = getWindowBrightness()
        return lerpColor(0xEEAA00.toInt(), 0xFFFF66.toInt(), b)
    }

    // ── Helpers ───────────────────────────────────────────────────

    fun isNight(): Boolean = hour < 5f || hour > 20f

    fun getTimeString(): String {
        val h = hour.toInt()
        val m = ((hour - h) * 60).toInt()
        return String.format("%02d:%02d", h, m)
    }

    fun getPeriodLabel(): String = when {
        hour < 5f -> "Notte"
        hour < 7f -> "Alba"
        hour < 12f -> "Mattina"
        hour < 16f -> "Pomeriggio"
        hour < 19f -> "Tramonto"
        hour < 21f -> "Sera"
        else -> "Notte"
    }

    // ── Math helpers ──────────────────────────────────────────────

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * tt).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * tt).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * tt).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}
