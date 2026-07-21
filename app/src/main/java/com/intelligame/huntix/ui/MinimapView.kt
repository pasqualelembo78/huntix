package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.abs

class MinimapView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val size = 110f * density
    private val half = size / 2f

    private data class NpcDot(val x: Float, val z: Float, val color: Int)
    private var playerX = 0f
    private var playerZ = 0f
    private val npcDots = mutableListOf<NpcDot>()
    private val roadCenters = mutableListOf<Float>()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x60000000; style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f * density
    }
    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40AAAAAA.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f * density
    }
    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xFF, 0x6D, 0x00); style = Paint.Style.FILL
    }
    private val npcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var cityHalf = 40f

    fun setRoads(centers: List<Float>, halfCity: Float) {
        roadCenters.clear()
        roadCenters.addAll(centers)
        cityHalf = halfCity
    }

    fun update(px: Float, pz: Float, npcs: List<Triple<Float, Float, Int>>) {
        playerX = px; playerZ = pz
        npcDots.clear()
        npcs.forEach { npcDots.add(NpcDot(it.first, it.second, it.third)) }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(size.toInt(), size.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(half, half, half, bgPaint)
        canvas.drawCircle(half, half, half, borderPaint)
        canvas.save()
        canvas.clipPath(android.graphics.Path().apply {
            addCircle(half, half, half, android.graphics.Path.Direction.CW)
        })

        for (rc in roadCenters) {
            val sx = (rc / cityHalf) * half + half
            canvas.drawLine(sx, 0f, sx, size, roadPaint)
            val sz = (rc / cityHalf) * half + half
            canvas.drawLine(0f, sz, size, sz, roadPaint)
        }

        for (npc in npcDots) {
            npcPaint.color = npc.color
            val nx = (npc.x / cityHalf) * half + half
            val nz = (npc.z / cityHalf) * half + half
            canvas.drawCircle(nx, nz, 3f * density, npcPaint)
        }

        val px = (playerX / cityHalf) * half + half
        val pz = (playerZ / cityHalf) * half + half
        canvas.drawCircle(px, pz, 4f * density, playerPaint)

        canvas.restore()
    }
}
