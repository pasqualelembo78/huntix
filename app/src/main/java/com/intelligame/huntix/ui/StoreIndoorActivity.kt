package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * StoreIndoorActivity — vista interna (top-down) del negozio.
 *
 * Sostituisce l'approccio Unity di negozio.txt (IndoorActivity/IndoorLoader)
 * con una stanza Canvas nativa: il giocatore cammina con una levetta analogica
 * in basso a sinistra, esplora una stanza con TV a muro, tavolo e sedia,
 * e può uscire raggiungendo il tappeto verde "ESCI" (o col pulsante in alto).
 */
class StoreIndoorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_POI_NAME = "poi_name"
        const val EXTRA_POI_TYPE = "poi_type"
        const val EXTRA_JSON_URL = "json_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val poiName = intent.getStringExtra(EXTRA_POI_NAME) ?: "Negozio"
        val poiType = intent.getStringExtra(EXTRA_POI_TYPE) ?: ""

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor(UiKit.BG))

        val roomView = StoreRoomView(this) { toastHint() }
        roomView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        root.addView(roomView)

        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(14, 14, 14, 0)
            addView(LinearLayout(this@StoreIndoorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@StoreIndoorActivity).apply {
                    text = "\u2190"
                    textSize = 20f
                    setTextColor(Color.parseColor(UiKit.ACCENT))
                    setPadding(0, 0, 14, 0)
                    setOnClickListener { finish() }
                })
                addView(TextView(this@StoreIndoorActivity).apply {
                    text = if (poiType.isBlank()) poiName else "$poiName \u00B7 $poiType"
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            })
            addView(TextView(this@StoreIndoorActivity).apply {
                text = "Usa la levetta in basso a sinistra per camminare \u00B7 raggiungi il tappeto verde ESCI"
                textSize = 12f
                setTextColor(0xAAFFFFFF.toInt())
            })
        }
        root.addView(hud)

        setContentView(root)
    }

    private var hintShown = false

    private fun toastHint() {
        if (hintShown) return
        hintShown = true
        Toast.makeText(this, "Sei uscito dal negozio \uD83D\uDEE0\uFE0F", Toast.LENGTH_SHORT).show()
    }

    inner class StoreRoomView(context: Context, private val onExit: () -> Unit) : View(context) {

        companion object {
            private const val ROOM_W = 4200f
            private const val ROOM_H = 3000f
            private const val WALK_SPEED = 380f
        }

        private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E1533") }
        private val gridPaint = Paint().apply { color = 0x14000000.toInt() }
        private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A1F45") }
        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF.toInt() }
        private val rugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF.toInt() }
        private val woodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8D6E63") }
        private val woodDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5D4037") }
        private val woodStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5D4037")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        private val chairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6D4C41") }
        private val chairDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4E342E") }
        private val tvPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#11111A") }
        private val screenPaint = Paint().apply { color = Color.parseColor("#0B3D91") }
        private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF.toInt() }
        private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(UiKit.GREEN) }
        private val windowFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3E2C5A") }
        private val nightSky = Paint().apply { color = Color.parseColor("#12162B") }
        private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF3B0") }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000.toInt() }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7C4DFF") }
        private val skinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFCC80") }
        private val joyBase = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33A78BFA.toInt() }
        private val joyRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(UiKit.ACCENT)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val joyKnob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(UiKit.ACCENT) }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
        }

        private val handler = Handler(Looper.getMainLooper())
        private val tick = object : Runnable {
            override fun run() {
                step()
                handler.postDelayed(this, 16L)
            }
        }

        private var playerX = ROOM_W / 2f
        private var playerY = ROOM_H - 420f
        private var vecX = 0f
        private var vecY = 0f
        private var walkPhase = 0f
        private var walking = false
        private var exited = false

        private var joyCX = 0f
        private var joyCY = 0f
        private var joyR = 0f
        private var activeJoy = false
        private var knobX = 0f
        private var knobY = 0f

        init {
            handler.post(tick)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            handler.removeCallbacks(tick)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            joyR = min(w, h) * 0.11f
            joyCX = w * 0.16f
            joyCY = h * 0.84f
            knobX = joyCX
            knobY = joyCY
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val dx = event.x - joyCX
                    val dy = event.y - joyCY
                    if (dx * dx + dy * dy <= joyR * joyR * 4f) {
                        activeJoy = true
                        updateKnob(event.x, event.y)
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (activeJoy) {
                        updateKnob(event.x, event.y)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activeJoy = false
                    vecX = 0f
                    vecY = 0f
                    knobX = joyCX
                    knobY = joyCY
                    invalidate()
                }
            }
            return super.onTouchEvent(event)
        }

        private fun updateKnob(x: Float, y: Float) {
            var dx = x - joyCX
            var dy = y - joyCY
            val len = hypot(dx, dy)
            if (len > joyR) {
                dx = dx / len * joyR
                dy = dy / len * joyR
            }
            knobX = joyCX + dx
            knobY = joyCY + dy
            if (len < 1f) {
                vecX = 0f
                vecY = 0f
            } else {
                vecX = dx / joyR
                vecY = dy / joyR
            }
            invalidate()
        }

        private fun step() {
            if (!exited && (vecX != 0f || vecY != 0f)) {
                val mag = hypot(vecX, vecY).coerceAtMost(1f)
                playerX = (playerX + vecX * WALK_SPEED * 0.016f * 2f).coerceIn(0f, ROOM_W)
                playerY = (playerY + vecY * WALK_SPEED * 0.016f * 2f).coerceIn(0f, ROOM_H)
                walkPhase += 0.35f * mag
                walking = true

                val exitX = ROOM_W / 2f
                val exitY = ROOM_H - 190f
                if (kotlin.math.abs(playerX - exitX) < 200f && kotlin.math.abs(playerY - exitY) < 200f) {
                    exited = true
                    onExit()
                    handler.postDelayed({ finish() }, 900L)
                }
            } else {
                walking = false
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val camLeft = (playerX - w / 2f).coerceIn(-600f, ROOM_W - w + 600f)
            val camTop = (playerY - h / 2f).coerceIn(-600f, ROOM_H - h + 600f)

            canvas.save()
            canvas.translate(-camLeft, -camTop)

            // Floor + grid
            canvas.drawRect(0f, 0f, ROOM_W, ROOM_H, floorPaint)
            var gx = 0f
            while (gx <= ROOM_W) {
                canvas.drawLine(gx, 0f, gx, ROOM_H, gridPaint)
                gx += 200f
            }
            var gy = 0f
            while (gy <= ROOM_H) {
                canvas.drawLine(0f, gy, ROOM_W, gy, gridPaint)
                gy += 200f
            }

            // Walls (north = TV, south = uscita, east = finestra)
            drawWall(canvas, 0f, 0f, ROOM_W, 90f)
            drawWall(canvas, 0f, ROOM_H - 90f, ROOM_W, 90f)
            drawWall(canvas, 0f, 0f, 90f, ROOM_H)
            drawWall(canvas, ROOM_W - 90f, 0f, 90f, ROOM_H)

            // Finestra con luna (parete est)
            val winY = ROOM_H / 2f
            canvas.drawRect(ROOM_W - 90f, winY - 220f, ROOM_W, winY + 220f, windowFrame)
            canvas.drawRect(ROOM_W - 70f, winY - 200f, ROOM_W - 20f, winY + 200f, nightSky)
            canvas.drawCircle(ROOM_W - 45f, winY - 120f, 30f, moonPaint)

            // TV a muro (parete nord)
            drawTv(canvas, ROOM_W / 2f - 460f, -70f, 920f, 560f)

            // Tappeto verde ESCI (parete sud)
            val mw = 340f
            canvas.drawRoundRect(
                ROOM_W / 2f - mw / 2f, ROOM_H - 250f,
                ROOM_W / 2f + mw / 2f, ROOM_H - 130f, 10f, 10f, greenPaint
            )
            textPaint.setTypeface(Typeface.DEFAULT_BOLD)
            textPaint.color = Color.BLACK
            canvas.drawText("ESCI", ROOM_W / 2f, ROOM_H - 175f, textPaint)
            textPaint.color = Color.WHITE
            textPaint.setTypeface(null)

            // Tappeto decorativo
            canvas.drawOval(1400f, 1450f, 2700f, 2150f, rugPaint)

            // Tavolo
            drawTable(canvas, 2000f, 1750f, 780f, 470f)

            // Sedia
            drawChair(canvas, 2580f, 2200f, 270f)

            // Giocatore
            val ppx = playerX
            val ppy = playerY
            canvas.drawOval(ppx - 55f, ppy + 30f, ppx + 55f, ppy + 52f, shadowPaint)
            val bob = if (walking) sin(walkPhase) * 6f else 0f
            canvas.drawCircle(ppx, ppy + bob, 48f, bodyPaint)
            val nx = if (walking) vecX * 28f else 0f
            val ny = if (walking) vecY * 28f else 0f
            canvas.drawCircle(ppx + nx, ppy + ny + bob - 12f, 27f, skinPaint)

            canvas.restore()

            // Levetta (spazio schermo)
            canvas.drawCircle(joyCX, joyCY, joyR, joyBase)
            canvas.drawCircle(joyCX, joyCY, joyR, joyRing)
            canvas.drawCircle(knobX, knobY, joyR * 0.42f, joyKnob)
        }

        private fun drawWall(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
            canvas.drawRect(x, y, x + w, y + h, wallPaint)
            canvas.drawRect(x, y + h - 18f, x + w, y + h, basePaint)
        }

        private fun drawTv(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
            // Supporto
            canvas.drawRect(x + w / 2f - 40f, y + h - 60f, x + w / 2f + 40f, y + h + 60f, woodDark)
            canvas.drawRect(x + w / 2f - 130f, y + h + 40f, x + w / 2f + 130f, y + h + 60f, woodDark)
            // Cornice
            canvas.drawRoundRect(x, y, x + w, y + h, 12f, 12f, tvPaint)
            // Schermo
            canvas.drawRoundRect(x + 26f, y + 26f, x + w - 26f, y + h - 26f, 8f, 8f, screenPaint)
            canvas.drawRoundRect(x + 40f, y + 40f, x + w - 40f, y + h - 120f, 6f, 6f, shinePaint)
            // Spia LIVE
            canvas.drawCircle(x + w - 60f, y + 52f, 10f, greenPaint)
        }

        private fun drawTable(canvas: Canvas, cx: Float, cy: Float, tw: Float, th: Float) {
            canvas.drawRect(cx - tw / 2f + 40f, cy - th / 2f + 40f, cx - tw / 2f + 70f, cy + th / 2f - 40f, woodDark)
            canvas.drawRect(cx + tw / 2f - 70f, cy - th / 2f + 40f, cx + tw / 2f - 40f, cy + th / 2f - 40f, woodDark)
            canvas.drawRect(cx - tw / 2f + 40f, cy - th / 2f + 40f, cx + tw / 2f - 40f, cy - th / 2f + 70f, woodDark)
            canvas.drawRect(cx - tw / 2f + 40f, cy + th / 2f - 70f, cx + tw / 2f - 40f, cy + th / 2f - 40f, woodDark)
            canvas.drawRoundRect(cx - tw / 2f, cy - th / 2f, cx + tw / 2f, cy + th / 2f, 16f, 16f, woodPaint)
            canvas.drawRoundRect(cx - tw / 2f, cy - th / 2f, cx + tw / 2f, cy + th / 2f, 16f, 16f, woodStroke)
            canvas.drawText("\u2615", cx, cy + 22f, textPaint)
        }

        private fun drawChair(canvas: Canvas, cx: Float, cy: Float, s: Float) {
            canvas.drawRoundRect(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f, 14f, 14f, chairPaint)
            canvas.drawRect(cx + s / 2f - 34f, cy - s / 2f - 76f, cx + s / 2f + 6f, cy + s / 2f, chairDark)
        }
    }
}
