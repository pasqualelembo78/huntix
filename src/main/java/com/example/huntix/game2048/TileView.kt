package com.example.huntix.game2048

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class TileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var value: Int = 0
        set(v) {
            field = v
            invalidate()
        }

    init {
        setBackgroundResource(android.R.drawable.edit_text)
        minimumHeight = 100.dp(this)
        minimumWidth = 100.dp(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawTile(canvas)
    }

    private fun drawTile(canvas: Canvas) {
        if (value == 0) {
            paint.color = Color.argb(50, 200, 200, 200)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            return
        }

        // Colori standard 2048
        val colors = mapOf(
            2 to Color.rgb(238, 228, 218),
            4 to Color.rgb(237, 224, 200),
            8 to Color.rgb(242, 177, 121),
            16 to Color.rgb(245, 149, 99),
            32 to Color.rgb(246, 124, 95),
            64 to Color.rgb(246, 94, 59),
            128 to Color.rgb(237, 207, 114),
            256 to Color.rgb(237, 204, 94),
            512 to Color.rgb(237, 201, 79),
            1024 to Color.rgb(237, 197, 63),
            2048 to Color.rgb(237, 194, 46),
            else to Color.rgb(30, 30, 30)
        )

        paint.color = colors[value] ?: colors[2048]!!
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            2f, 2f, width - 2f, height - 2f,
            8f, 8f, paint
        )

        paint.color = if (value <= 4) Color.BLACK else Color.WHITE
        paint.textSize = min(width, height) / 3f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            value.toString(),
            width / 2f,
            height / 2f + paint.textSize / 3,
            paint
        )
    }

    private fun 100.dp(view: View): Int =
        (100 * view.resources.displayMetrics.density + 0.5f).toInt()
}
