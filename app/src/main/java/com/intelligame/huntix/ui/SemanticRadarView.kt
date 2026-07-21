package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class SemanticRadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class RadarTarget(
        val id: String,
        val name: String,              // "Cassetto cucina alto"
        val semanticLabel: String,     // "CABINET", "PLANTER", "SHELF", "DRAWER"
        val icon: Int,                 // Resource ID dell'icona
        val distance: Float,           // metri
        val bearing: Float,            // gradi 0-360 (relativo al nord dispositivo)
        val isCurrentTarget: Boolean = false,
        val isFound: Boolean = false
    )

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A0A2E")
        style = Paint.Style.FILL
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A1A4A")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A2A5A")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A78BFA")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val targetStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f
        isFakeBoldText = true
    }
    private val textPaintSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        textSize = 9f
    }
    private val currentTargetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val foundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }

    private var targets = mutableListOf<RadarTarget>()
    private var headingDeg = 0f
    private var maxRange = 10f  // metri
    private var pulseAngle = 0f
    private var pulseRadius = 0f
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A78BFA")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 180
    }

    var onTargetClick: ((RadarTarget) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) / 2f - 10f

        // Background
        canvas.drawCircle(cx, cy, radius, backgroundPaint)

        // Cerchi di distanza
        for (i in 1..4) {
            val r = radius * i / 4f
            canvas.drawCircle(cx, cy, r, circlePaint)
            // Label distanza
            val dist = maxRange * i / 4f
            canvas.drawText("${dist.toInt()}m", cx + 4f, cy - r + 12f, textPaintSmall)
        }

        // Linee cardinali
        for (angle in 0..3) {
            val a = Math.toRadians(angle * 90.0)
            val x = cx + radius * Math.cos(a).toFloat()
            val y = cy + radius * Math.sin(a).toFloat()
            canvas.drawLine(cx, cy, x, y, linePaint)
        }

        // Nord (freccia)
        val northRad = Math.toRadians(-headingDeg)
        val northX = cx + radius * 0.9f * Math.cos(northRad).toFloat()
        val northY = cy + radius * 0.9f * Math.sin(northRad).toFloat()
        canvas.drawLine(cx, cy, northX, northY, northPaint)
        // Punta freccia nord
        val arrowRad = northRad + Math.PI
        canvas.drawLine(northX, northY,
            northX + 12f * Math.cos(arrowRad + 0.3).toFloat(),
            northY + 12f * Math.sin(arrowRad + 0.3).toFloat(), northPaint)
        canvas.drawLine(northX, northY,
            northX + 12f * Math.cos(arrowRad - 0.3).toFloat(),
            northY + 12f * Math.sin(arrowRad - 0.3).toFloat(), northPaint)

        // Animazione pulse (scansione)
        pulseAngle += 2f
        pulseRadius += radius / 60f
        if (pulseRadius > radius) {
            pulseRadius = 0f
        }
        canvas.drawCircle(cx, cy, pulseRadius, pulsePaint)

        // Disegna target
        for (target in targets) {
            drawTarget(canvas, cx, cy, radius, target)
        }
    }

    private fun drawTarget(canvas: Canvas, cx: Float, cy: Float, radius: Float, target: RadarTarget) {
        val relativeBearing = target.bearing - headingDeg
        val rad = Math.toRadians(relativeBearing.toDouble())
        val distRatio = (target.distance / maxRange).coerceIn(0f, 1f)
        val r = radius * distRatio
        val x = cx + r * Math.cos(rad).toFloat()
        val y = cy + r * Math.sin(rad).toFloat()

        // Colore per label semantico
        val color = colorForSemanticLabel(target.semanticLabel)
        targetPaint.color = color
        targetStrokePaint.color = color

        // Cerchio target
        val targetRadius = if (target.isCurrentTarget) 14f else 10f
        canvas.drawCircle(x, y, targetRadius, targetPaint)
        canvas.drawCircle(x, y, targetRadius, targetStrokePaint)

        // Se è il target corrente, anello pulsante
        if (target.isCurrentTarget) {
            val pulseR = targetRadius + 6f + 4f * Math.sin(System.currentTimeMillis() / 200.0).toFloat()
            canvas.drawCircle(x, y, pulseR, currentTargetPaint)
        }

        // Se trovato, checkmark
        if (target.isFound) {
            canvas.drawCircle(x, y, targetRadius, foundPaint)
            textPaint.color = Color.WHITE
            canvas.drawText("✓", x - 3f, y + 4f, textPaint)
        }

        // Etichetta nome (solo per target corrente o vicini)
        if (target.isCurrentTarget || target.distance < maxRange * 0.5f) {
            textPaint.color = Color.WHITE
            val label = target.name
            val textWidth = textPaint.measureText(label)
            canvas.drawText(label, x - textWidth / 2, y - targetRadius - 8f, textPaint)
            
            // Distanza
            textPaintSmall.color = Color.parseColor("#B0BEC5")
            val distText = "${target.distance.toInt()}m"
            val distWidth = textPaintSmall.measureText(distText)
            canvas.drawText(distText, x - distWidth / 2, y + targetRadius + 16f, textPaintSmall)
        }

        // Icona semantica (piccola) sopra il target
        // TODO: draw icon from resource
    }

    private fun colorForSemanticLabel(label: String): Int = when (label.uppercase()) {
        "CABINET", "DRAWER" -> Color.parseColor("#FF6B35")      // Arancio - mobili/cassetti
        "PLANTER" -> Color.parseColor("#4CAF50")                // Verde - fioriere
        "SHELF" -> Color.parseColor("#2196F3")                  // Blu - scaffali
        "TABLE", "COUNTER", "DESK" -> Color.parseColor("#9C27B0") // Viola - superfici piane
        "BED", "SOFA" -> Color.parseColor("#E91E63")            // Rosa - letti/divani
        "WALL" -> Color.parseColor("#795548")                   // Marrone - muri
        "FLOOR" -> Color.parseColor("#607D8B")                  // Grigio blu - pavimenti
        "CEILING" -> Color.parseColor("#9E9E9E")                // Grigio - soffitti
        "DOOR", "WINDOW" -> Color.parseColor("#FF9800")         // Arancio chiaro - porte/finestre
        "CHAIR" -> Color.parseColor("#FFEB3B")                  // Giallo - sedie
        else -> Color.parseColor("#A78BFA")                     // Viola default
    }

    fun updateTargets(newTargets: List<RadarTarget>) {
        targets = newTargets.toMutableList()
        invalidate()
    }

    fun setHeading(heading: Float) {
        headingDeg = heading
        invalidate()
    }

    fun setMaxRange(range: Float) {
        maxRange = range.coerceIn(1f, 50f)
        invalidate()
    }

    fun setCurrentTarget(targetId: String?) {
        targets = targets.map { t ->
            t.copy(isCurrentTarget = targetId != null && t.id == targetId)
        }
        invalidate()
    }

    fun markTargetFound(targetId: String) {
        targets = targets.map { t ->
            if (t.id == targetId) t.copy(isFound = true) else t
        }
        invalidate()
    }

    // Touch handling per click su target
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) / 2f - 10f
            
            for (target in targets) {
                val relativeBearing = target.bearing - headingDeg
                val rad = Math.toRadians(relativeBearing.toDouble())
                val distRatio = (target.distance / maxRange).coerceIn(0f, 1f)
                val r = radius * distRatio
                val x = cx + r * Math.cos(rad).toFloat()
                val y = cy + r * Math.sin(rad).toFloat()
                
                val dx = event.x - x
                val dy = event.y - y
                if (dx * dx + dy * dy < 400f) { // 20px radius
                    onTargetClick?.invoke(target)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}