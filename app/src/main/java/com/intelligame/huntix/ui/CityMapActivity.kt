// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.LocationType
import com.intelligame.huntix.reallife.MapLocation
import com.intelligame.huntix.reallife.MapLocations
import kotlin.math.sqrt

/**
 * CityMapActivity — mappa zoomabile della città stile Brookhaven.
 * Canvas 2D con pinch-to-zoom, pan, icone luoghi, marker giocatore.
 */
class CityMapActivity : AppCompatActivity() {

    private lateinit var mapSurface: MapSurfaceView
    private var playerX = 0f
    private var playerZ = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playerX = intent.getFloatExtra("PLAYER_X", 0f)
        playerZ = intent.getFloatExtra("PLAYER_Z", 0f)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0A0618"))
        }

        mapSurface = MapSurfaceView(this, playerX, playerZ)
        root.addView(mapSurface, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xDD0A0618.toInt())
            setPadding(UiKit.dp(this@CityMapActivity, 14), UiKit.dp(this@CityMapActivity, 12),
                UiKit.dp(this@CityMapActivity, 14), UiKit.dp(this@CityMapActivity, 12))
        }
        topBar.addView(TextView(this).apply {
            text = "← "; textSize = 20f; setTextColor(Color.parseColor(UiKit.ACCENT))
            isClickable = true; setOnClickListener { finish() }
        })
        topBar.addView(TextView(this).apply {
            text = "\uD83D\uDDFA\uFE0F  Mappa di Huntix"; textSize = 16f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Center button
        val centerBtn = TextView(this).apply {
            text = "🏠"; textSize = 18f; setTextColor(Color.WHITE)
            isClickable = true; isFocusable = true
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@CityMapActivity, 16).toFloat()
                setColor(Color.parseColor(UiKit.ACCENT))
            }
            setPadding(UiKit.dp(this@CityMapActivity, 10), UiKit.dp(this@CityMapActivity, 8),
                UiKit.dp(this@CityMapActivity, 10), UiKit.dp(this@CityMapActivity, 8))
            setOnClickListener { mapSurface.centerOnPlayer() }
        }
        topBar.addView(centerBtn)
        root.addView(topBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP })

        // Legend
        val legend = TextView(this).apply {
            text = "Pinch zoom · Trascina muovi · Tocca icona per info"
            setTextColor(0x88FFFFFF.toInt()); textSize = 10f
            setPadding(UiKit.dp(this@CityMapActivity, 14), 0, UiKit.dp(this@CityMapActivity, 14), UiKit.dp(this@CityMapActivity, 8))
        }
        root.addView(legend, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })

        setContentView(root)
    }

    // ── MapSurfaceView — il cuore della mappa ──

    class MapSurfaceView(
        context: Context,
        private var playerX: Float,
        private var playerZ: Float
    ) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

        private var drawThread: Thread? = null
        private var running = false

        // Transform state
        private var zoom = 1f
        private var panX = 0f
        private var panY = 0f
        private var minZoom = 0.6f
        private var maxZoom = 5f

        // Touch tracking
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var lastTouchX2 = 0f
        private var lastTouchY2 = 0f
        private var isPanning = false
        private var isPinching = false
        private var initialSpan = 0f
        private var initialZoom = 1f

        // Paints
        private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x55, 0x55, 0x65); style = Paint.Style.FILL
        }
        private val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x4A, 0x8C, 0x3F); style = Paint.Style.FILL
        }
        private val sidewalkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xAA, 0xAA, 0xBB); style = Paint.Style.FILL
        }
        private val iconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; strokeWidth = 2f
        }
        private val iconBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.WHITE
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.FILL
        }
        private val playerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(UiKit.ACCENT); style = Paint.Style.STROKE; strokeWidth = 4f
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFFFFF; style = Paint.Style.STROKE; strokeWidth = 1f
        }

        // Buildings from BuildingDefs
        private val buildings = listOf(
            Triple(-25f, -15f, "🍕"), // Ristorante
            Triple(5f, -25f, "🛒"),   // Supermercato
            Triple(-15f, 5f, "🏥"),   // Ospedale
            Triple(15f, 5f, "🏋️"),   // Palestra
            Triple(0f, 0f, "🏠")      // Player home approx
        )

        init {
            holder.addCallback(this)
            isFocusable = true
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            running = true
            drawThread = Thread(this)
            drawThread?.start()
            centerOnPlayer()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            running = false
            drawThread?.join(500)
        }

        override fun run() {
            while (running) {
                val canvas = holder.lockCanvas() ?: continue
                try {
                    drawMap(canvas)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
                Thread.sleep(33) // ~30fps
            }
        }

        fun centerOnPlayer() {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w == 0f || h == 0f) return
            val mapX = MapLocations.cityToMap(playerX) * w
            val mapY = MapLocations.cityToMap(playerZ) * h
            panX = w / 2f - mapX * zoom
            panY = h / 2f - mapY * zoom
        }

        private fun drawMap(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w == 0f || h == 0f) return

            canvas.drawColor(Color.parseColor("#0A0618"))

            canvas.save()
            canvas.translate(panX, panY)
            canvas.scale(zoom, zoom)

            // Grass background with subtle gradient
            val grassGradient = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(0xFF3D7A35.toInt(), 0xFF4A8C3F.toInt(), 0xFF3D7A35.toInt()),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            grassPaint.shader = grassGradient
            canvas.drawRect(0f, 0f, w, h, grassPaint)

            // Grass texture dots
            val grassDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x302E7D32; style = Paint.Style.FILL }
            for (i in 0 until 200) {
                val gx = ((i * 173 + 42) % 1000).toFloat() / 1000f * w
                val gy = ((i * 311 + 87) % 1000).toFloat() / 1000f * h
                canvas.drawCircle(gx, gy, 2f + (i % 3).toFloat(), grassDotPaint)
            }

            // Roads (8 roads in each direction) with detail
            val gridStep = w / 8f
            val roadW = w / 80f * 2f
            val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x88FFD54F.toInt(); style = Paint.Style.STROKE
                strokeWidth = 1.5f; pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
            }

            for (i in 0 until 8) {
                val pos = (i + 0.5f) * gridStep
                val halfRoad = roadW / 2

                // Road shadow
                val roadShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x20000000; style = Paint.Style.FILL
                }
                canvas.drawRect(pos - halfRoad + 2, 0f, pos + halfRoad + 2, h, roadShadowPaint)
                canvas.drawRect(0f, pos - halfRoad + 2, w, pos + halfRoad + 2, roadShadowPaint)

                // Road surface
                canvas.drawRect(pos - halfRoad, 0f, pos + halfRoad, h, roadPaint)
                canvas.drawRect(0f, pos - halfRoad, w, pos + halfRoad, roadPaint)

                // Sidewalks (lighter strips)
                canvas.drawRect(pos - halfRoad - 3, 0f, pos - halfRoad, h, sidewalkPaint)
                canvas.drawRect(pos + halfRoad, 0f, pos + halfRoad + 3, h, sidewalkPaint)
                canvas.drawRect(0f, pos - halfRoad - 3, w, pos - halfRoad, sidewalkPaint)
                canvas.drawRect(0f, pos + halfRoad, w, pos + halfRoad + 3, sidewalkPaint)

                // Center lane divider
                canvas.drawLine(pos, 0f, pos, h, centerLinePaint)
                canvas.drawLine(0f, pos, w, pos, centerLinePaint)
            }

            // Special buildings with roofs and details
            val buildingBlocks = listOf(
                floatArrayOf(-25f, -15f, 4f, 3f, 0xFFFF7043.toFloat(), 0xFFD84315.toFloat()), // Ristorante
                floatArrayOf(5f, -25f, 3f, 4f, 0xFF42A5F5.toFloat(), 0xFF1565C0.toFloat()),   // Supermercato
                floatArrayOf(-15f, 5f, 4f, 3f, 0xFFEF5350.toFloat(), 0xFFC62828.toFloat()),   // Ospedale
                floatArrayOf(15f, 5f, 3f, 4f, 0xFFAB47BC.toFloat(), 0xFF6A1B9A.toFloat()),    // Palestra
                floatArrayOf(-15f, -25f, 3f, 3f, 0xFF1565C0.toFloat(), 0xFF0D47A1.toFloat()), // Polizia
                floatArrayOf(25f, 25f, 3f, 3f, 0xFFC62828.toFloat(), 0xFF8E0000.toFloat()),   // Vigili
                floatArrayOf(5f, 5f, 3f, 3f, 0xFFFFB300.toFloat(), 0xFFE65100.toFloat()),     // Banca
                floatArrayOf(-5f, 25f, 3f, 3f, 0xFFD4A574.toFloat(), 0xFF8D6E63.toFloat()),   // Chiesa
                floatArrayOf(25f, -5f, 5f, 5f, 0xFF81C784.toFloat(), 0xFF4CAF50.toFloat()),   // Parco
                floatArrayOf(-25f, 15f, 4f, 4f, 0xFF29B6F6.toFloat(), 0xFF0288D1.toFloat()),  // Lago
                floatArrayOf(15f, -25f, 3f, 3f, 0xFF78909C.toFloat(), 0xFF455A64.toFloat()),  // Benzinaio
                floatArrayOf(-5f, -15f, 4f, 4f, 0xFFFF8A65.toFloat(), 0xFFE64A19.toFloat())   // Negozi
            )
            val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val roofPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x88E3F2FD.toInt(); style = Paint.Style.FILL
            }

            for (b in buildingBlocks) {
                val bx = MapLocations.cityToMap(b[0]) * w
                val bz = MapLocations.cityToMap(b[1]) * h
                val bw = b[2] / MapLocations.CITY_SIZE * w
                val bh = b[3] / MapLocations.CITY_SIZE * h

                // Building shadow
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x30000000; style = Paint.Style.FILL
                }
                canvas.drawRoundRect(bx - bw / 2 + 3, bz - bh / 2 + 3, bx + bw / 2 + 3, bz + bh / 2 + 3, 6f, 6f, shadowPaint)

                // Building body
                bPaint.color = b[4].toInt()
                canvas.drawRoundRect(bx - bw / 2, bz - bh / 2, bx + bw / 2, bz + bh / 2, 6f, 6f, bPaint)

                // Roof accent (darker strip at top)
                roofPaint.color = b[5].toInt()
                canvas.drawRoundRect(bx - bw / 2, bz - bh / 2, bx + bw / 2, bz - bh / 2 + bh * 0.25f, 6f, 6f, roofPaint)

                // Windows (small dots in a grid)
                if (b[2] >= 3f) {
                    val cols = (b[2] / 1.2f).toInt().coerceAtLeast(2)
                    val rows = (b[3] / 1.2f).toInt().coerceAtLeast(2)
                    val wSize = 3f
                    for (wx in 0 until cols) {
                        for (wy in 0 until rows) {
                            val wX = bx - bw / 2 + bw * (wx + 1f) / (cols + 1f)
                            val wY = bz - bh / 2 + bh * (wy + 1.5f) / (rows + 1.5f)
                            canvas.drawRect(wX - wSize, wY - wSize, wX + wSize, wY + wSize, windowPaint)
                        }
                    }
                }

                // Park trees
                if (b[0] == 25f && b[1] == -5f) {
                    val treePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E7D32.toInt(); style = Paint.Style.FILL }
                    val trunkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5D4037.toInt(); style = Paint.Style.FILL }
                    for (t in 0 until 8) {
                        val tx = bx - bw / 2 + bw * ((t * 37 + 11) % 100).toFloat() / 100f
                        val tz = bz - bh / 2 + bh * ((t * 53 + 29) % 100).toFloat() / 100f
                        canvas.drawCircle(tx, tz, 5f + (t % 3).toFloat() * 2f, treePaint)
                        canvas.drawRect(tx - 1f, tz, tx + 1f, tz + 6f, trunkPaint)
                    }
                }

                // Lake waves
                if (b[0] == -25f && b[1] == 15f) {
                    val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x60FFFFFF; style = Paint.Style.STROKE; strokeWidth = 1.5f
                    }
                    for (w2 in 0 until 3) {
                        val wy = bz - bh / 4 + bh * w2 / 4f
                        canvas.drawLine(bx - bw / 3, wy, bx + bw / 3, wy, wavePaint)
                    }
                }
            }

            // Procedural buildings (residential blocks with variety)
            val procColors = intArrayOf(
                0x55FFFFFF, 0x44E8D5B5, 0x44B0BEC5, 0x44FFCCBC,
                0x44C5CAE9, 0x44B2DFDB, 0x44F0F4C3, 0x44D1C4E9
            )
            val procPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val seed = 42
            for (i in 0 until 50) {
                val sx = ((seed * 137 + i * 73) % 1000).toFloat() / 1000f * w
                val sz = ((seed * 251 + i * 113) % 1000).toFloat() / 1000f * h
                val sw = 12f + (i % 5) * 6f
                val sh = 10f + (i % 4) * 5f
                procPaint.color = procColors[i % procColors.size]
                canvas.drawRoundRect(sx - sw / 2, sz - sh / 2, sx + sw / 2, sz + sh / 2, 4f, 4f, procPaint)
            }

            // Location markers with improved design
            val markerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x40000000; style = Paint.Style.FILL
                maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
            }
            val markerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL; maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
            }
            val markerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 18f; textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(3f, 1f, 1f, Color.BLACK)
            }

            for (loc in MapLocations.LOCATIONS) {
                val lx = MapLocations.cityToMap(loc.cityX) * w
                val ly = MapLocations.cityToMap(loc.cityZ) * h
                val iconRadius = 20f

                // Shadow
                canvas.drawCircle(lx + 2, ly + 3, iconRadius, markerShadowPaint)

                // Glow
                markerGlowPaint.color = loc.color and 0x00FFFFFF or 0x30000000
                canvas.drawCircle(lx, ly, iconRadius * 1.5f, markerGlowPaint)

                // Background circle
                iconBgPaint.color = loc.color
                canvas.drawCircle(lx, ly, iconRadius, iconBgPaint)

                // Inner ring
                val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x44FFFFFF; style = Paint.Style.STROKE; strokeWidth = 1.5f
                }
                canvas.drawCircle(lx, ly, iconRadius - 3f, innerRingPaint)

                // White border
                canvas.drawCircle(lx, ly, iconRadius, iconBorderPaint)

                // Emoji
                textPaint.textSize = 24f
                canvas.drawText(loc.emoji, lx, ly + 9f, textPaint)

                // Label below
                markerLabelPaint.textSize = 14f
                canvas.drawText(loc.name, lx, ly + iconRadius + 16f, markerLabelPaint)
            }

            // Player marker with pulse effect
            val px = MapLocations.cityToMap(playerX) * w
            val py = MapLocations.cityToMap(playerZ) * h

            // Player outer pulse ring
            val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(UiKit.ACCENT); alpha = 40; style = Paint.Style.FILL
            }
            canvas.drawCircle(px, py, 20f, pulsePaint)

            // Player glow
            val playerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(UiKit.ACCENT); alpha = 80; style = Paint.Style.FILL
                maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(px, py, 14f, playerGlowPaint)

            // Player body
            canvas.drawCircle(px, py, 10f, playerPaint)
            canvas.drawCircle(px, py, 10f, playerBorderPaint)

            // Player direction indicator
            val dirPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(UiKit.ACCENT); style = Paint.Style.FILL
            }
            val path = Path()
            path.moveTo(px, py - 18f)
            path.lineTo(px - 6f, py - 10f)
            path.lineTo(px + 6f, py - 10f)
            path.close()
            canvas.drawPath(path, dirPaint)

            canvas.restore()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    isPanning = true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        isPinching = true
                        isPanning = false
                        lastTouchX = event.getX(0)
                        lastTouchY = event.getY(0)
                        lastTouchX2 = event.getX(1)
                        lastTouchY2 = event.getY(1)
                        initialSpan = fingerSpan(event)
                        initialZoom = zoom
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isPinching && event.pointerCount >= 2) {
                        val span = fingerSpan(event)
                        if (initialSpan > 0) {
                            val newZoom = (initialZoom * span / initialSpan).coerceIn(minZoom, maxZoom)
                            // Zoom toward center of fingers
                            val cx = (event.getX(0) + event.getX(1)) / 2f
                            val cy = (event.getY(0) + event.getY(1)) / 2f
                            val factor = newZoom / zoom
                            panX = cx - factor * (cx - panX)
                            panY = cy - factor * (cy - panY)
                            zoom = newZoom
                        }
                    } else if (isPanning) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        panX += dx
                        panY += dy
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPanning = false
                    isPinching = false

                    // Check if tap on a location icon
                    if (event.actionMasked == MotionEvent.ACTION_UP && event.pointerCount == 1) {
                        checkLocationTap(event.x, event.y)
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount <= 2) {
                        isPinching = false
                        isPanning = true
                        lastTouchX = event.getX(0)
                        lastTouchY = event.getY(0)
                    }
                }
            }
            return true
        }

        private fun fingerSpan(event: MotionEvent): Float {
            val dx = event.getX(0) - event.getX(1)
            val dy = event.getY(0) - event.getY(1)
            return sqrt(dx * dx + dy * dy)
        }

        private fun checkLocationTap(touchX: Float, touchY: Float) {
            val w = width.toFloat()
            val h = height.toFloat()
            val tapThreshold = 30f

            for (loc in MapLocations.LOCATIONS) {
                val lx = MapLocations.cityToMap(loc.cityX) * w * zoom + panX
                val ly = MapLocations.cityToMap(loc.cityZ) * h * zoom + panY
                val dx = touchX - lx
                val dy = touchY - ly
                if (sqrt(dx * dx + dy * dy) < tapThreshold) {
                    showLocationInfo(loc)
                    return
                }
            }
        }

        private fun showLocationInfo(loc: MapLocation) {
            val ctx = context
            android.app.AlertDialog.Builder(ctx)
                .setTitle("${loc.emoji} ${loc.name}")
                .setMessage(loc.description.ifEmpty { "Un luogo nella città di Huntix" })
                .setPositiveButton("Chiudi", null)
                .show()
        }
    }
}
