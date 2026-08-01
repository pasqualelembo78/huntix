package com.example.huntix.ui.minigames

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GamePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var gameId: String = ""
    var isAr: Boolean = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawPreview(canvas)
    }

    private fun drawPreview(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        val bgColor = if (isAr) Color.argb(80, 0, 150, 255) else Color.argb(80, 100, 100, 100)
        paint.color = bgColor
        canvas.drawRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            12dp(), 12dp(), paint
        )

        paint.color = Color.WHITE
        paint.textSize = 14sp()
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            gameId.replace("AR ", ""),
            width / 2f,
            height / 2f + 6.sp(),
            paint
        )
    }

    private fun dp(): Float = resources.displayMetrics.density
    private fun sp(): Float = resources.displayMetrics.scaledDensity
    private fun 12dp() = 12 * dp()
    private fun 14sp() = 14 * sp()
    private fun 6.sp() = 6 * dp()
}