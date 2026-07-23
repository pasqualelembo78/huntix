package com.intelligame.huntix.ui

import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.SoundManager
import com.intelligame.huntix.battle3d.BattleEngine3D
import com.intelligame.huntix.battle3d.BattleEngine3D.AnimEvent
import com.intelligame.huntix.battle3d.FighterDef
import com.intelligame.huntix.battle3d.FighterRegistry
import com.intelligame.huntix.battle3d.FighterState
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import kotlin.math.*
import kotlin.random.Random

class FightingGame3DActivity : BaseNavActivity() {
    lateinit var engine: BattleEngine3D
        private set
    private lateinit var playerFighter: FighterDef
    private lateinit var enemyFighter: FighterDef
    private lateinit var sceneView: SceneView
    private lateinit var effectView: EffectOverlayView
    private lateinit var hud: FrameLayout
    private lateinit var countdownText: TextView
    private lateinit var comboText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var gameLoop: Runnable? = null
    private var lastFrameTime = 0L
    private val vib by lazy { getSystemService(Vibrator::class.java) }

    private var player3D: Fighter3DNode? = null
    private var enemy3D: Fighter3DNode? = null
    private var use3D = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val pid = intent.getStringExtra("player_id") ?: FighterRegistry.ALL[0].id
        val eid = intent.getStringExtra("enemy_id") ?: FighterRegistry.ALL[1].id
        playerFighter = FighterRegistry.byId(pid)
        enemyFighter = FighterRegistry.byId(eid)

        engine = BattleEngine3D(playerFighter, enemyFighter)
        setupEngineCallbacks()
        buildUI()
        loadFighters()
        engine.startCountdown()
    }

    override fun onPause() { super.onPause(); gameLoop?.let { handler.removeCallbacks(it) } }
    override fun onDestroy() {
        super.onDestroy()
        gameLoop?.let { handler.removeCallbacks(it) }
        player3D?.destroy()
        enemy3D?.destroy()
        engine.destroy()
    }

    private val filEngine get() = sceneView.engine
    private val matLoader get() = sceneView.materialLoader

    private fun buildUI() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0A0022")) }

        sceneView = SceneView(this).apply {
            cameraManipulator = null
            lifecycle = this@FightingGame3DActivity.lifecycle
        }
        root.addView(sceneView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        sceneView.post {
            val cam = CameraNode(filEngine).apply { far = 100f; near = 0.1f }
            cam.position = Position(0f, 1.5f, 3.5f)
            sceneView.setCameraNode(cam)

            buildArena()
        }

        effectView = EffectOverlayView(this, engine).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        root.addView(effectView)

        hud = FrameLayout(this)
        hud.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)

        val hpHud = HUDView(this, engine, playerFighter, enemyFighter).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        hud.addView(hpHud)

        countdownText = TextView(this).apply {
            textSize = 64f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            visibility = View.GONE
        }
        hud.addView(countdownText, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        comboText = TextView(this).apply {
            textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#FFD700"))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            visibility = View.GONE; y = dp(200).toFloat()
        }
        hud.addView(comboText, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(180) })

        root.addView(hud)
        root.addView(createControls())
        setContentView(root)
        startGameLoop()
    }

    private fun buildArena() {
        val floorY = -0.3f
        val groundMat = matLoader.createColorInstance(color = Color.rgb(0x2A, 0x1A, 0x4E))
        val ground = CubeNode(filEngine, Size(8f, 0.05f, 4f), materialInstance = groundMat).apply {
            position = Position(0f, floorY, 0f)
        }
        sceneView.addChildNode(ground)

        val glowMat = matLoader.createColorInstance(color = Color.rgb(0x00, 0xBC, 0xD4))
        val glowLine = CubeNode(filEngine, Size(6f, 0.02f, 0.1f), materialInstance = glowMat).apply {
            position = Position(0f, floorY + 0.05f, 1.2f)
        }
        sceneView.addChildNode(glowLine)

        val bgMat = matLoader.createColorInstance(color = Color.rgb(0x0A, 0x00, 0x22))
        val bg = CubeNode(filEngine, Size(12f, 0.01f, 6f), materialInstance = bgMat).apply {
            position = Position(0f, -0.4f, 2f)
        }
        sceneView.addChildNode(bg)
    }

    private fun loadFighters() {
        val p3d = Fighter3DNode(sceneView.modelLoader)
        val e3d = Fighter3DNode(sceneView.modelLoader)

        p3d.load(playerFighter.glbFile) {
            runOnUiThread {
                p3d.modelNode?.let { sceneView.addChildNode(it) }
                if (p3d.isLoaded() && e3d.isLoaded()) use3D = true
            }
        }
        e3d.load(enemyFighter.glbFile) {
            runOnUiThread {
                e3d.modelNode?.let { sceneView.addChildNode(it) }
                if (p3d.isLoaded() && e3d.isLoaded()) use3D = true
            }
        }

        player3D = p3d
        enemy3D = e3d
    }

    private fun setupEngineCallbacks() {
        engine.onCountdown = { msg -> runOnUiThread {
            countdownText.text = msg
            countdownText.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE
            if (msg == "K.O.!") countdownText.setTextColor(Color.parseColor("#FF4444"))
            else countdownText.setTextColor(Color.WHITE)
        }}
        engine.onDamageDealt = { isPlayer, _, crit ->
            runOnUiThread {
                effectView.showHitEffect(!isPlayer, crit)
                SoundManager.playButtonTap()
                if (crit) { vib?.vibrate(VibrationEffect.createOneShot(30, 255)); SoundManager.playLevelUp() }
                else vib?.vibrate(VibrationEffect.createOneShot(15, 128))
            }
        }
        engine.onKO = { _ -> runOnUiThread {
            countdownText.text = "K.O.!"; countdownText.visibility = View.VISIBLE
            countdownText.setTextColor(Color.parseColor("#FF4444"))
            vib?.vibrate(VibrationEffect.createOneShot(200, 255))
            SoundManager.playVictory()
            SoundManager.playAchievement()
        }}
        engine.onBattleEnd = { r -> runOnUiThread { showResult(r) } }
        engine.onSuperReady = { effectView.flashSuper(!it) }
        engine.onEnergyWave = { fromPlayer -> runOnUiThread {
            effectView.spawnEnergyWave(fromPlayer,
                if (fromPlayer) playerFighter.secondaryColor else enemyFighter.secondaryColor)
            SoundManager.playLevelUp()
        }}
        engine.onComboUpdate = { combo, _ -> runOnUiThread {
            comboText.text = "${combo}x COMBO!"
            comboText.visibility = if (combo > 1) View.VISIBLE else View.GONE
        }}
        engine.onCameraShake = { intensity, _ -> effectView.shake(intensity) }
    }

    private fun startGameLoop() {
        lastFrameTime = System.currentTimeMillis()
        gameLoop = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val dt = ((now - lastFrameTime) / 1000f).coerceAtMost(0.05f)
                lastFrameTime = now
                engine.tick(dt)
                effectView.engineTick(dt)
                update3D(dt)
                effectView.invalidate()
                sceneView.invalidate()
                handler.postDelayed(this, 16)
            }
        }
        handler.post(gameLoop!!)
    }

    private fun update3D(dt: Float) {
        if (!use3D || player3D == null || enemy3D == null) return
        val p = engine.player; val e = engine.enemy

        player3D!!.setPosition((p.x - 0.5f) * 3.5f, -0.1f + p.y * 0.01f, 0f)
        player3D!!.setFacing(p.facing > 0)
        player3D!!.playAnimation(engine.playerAnimEvent)

        enemy3D!!.setPosition((e.x - 0.5f) * 3.5f, -0.1f + e.y * 0.01f, 0f)
        enemy3D!!.setFacing(e.facing > 0)
        enemy3D!!.playAnimation(engine.enemyAnimEvent)
    }

    private fun createControls(): View {
        val controlLayer = FrameLayout(this)
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(16))
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.gravity = Gravity.BOTTOM }
        }

        val moveGroup = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        moveGroup.addView(ctrlBtn("\u25C0\u25C0", "#6B5B95") { engine.playerMove(-1) }.also {
            it.setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> engine.playerMove(-1)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> engine.playerStop()
                }; true
            }
        })
        moveGroup.addView(ctrlBtn("\u25B6\u25B6", "#6B5B95") { engine.playerMove(1) }.also {
            it.setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> engine.playerMove(1)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> engine.playerStop()
                }; true
            }
        })
        bottomBar.addView(moveGroup)
        bottomBar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 0.3f) })

        val atkGroup = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        atkGroup.addView(ctrlBtn("SALTO", "#FFD93D") { engine.playerJump() })
        atkGroup.addView(ctrlBtn("PUGNO", "#FF6B6B") { engine.playerPunch() })
        atkGroup.addView(ctrlBtn("CALCIO", "#6BCBFF") { engine.playerKick() })
        atkGroup.addView(ctrlBtn("SUPER", "#E879F9") { engine.playerSpecial() })
        atkGroup.addView(ctrlBtn("BLOCCO", "#69DB7C") { engine.playerBlock() }.also {
            it.setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> engine.playerBlock()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> engine.playerReleaseBlock()
                }; true
            }
        })
        bottomBar.addView(atkGroup)
        controlLayer.addView(bottomBar)

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), 0)
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.gravity = Gravity.TOP }
        }
        topBar.addView(TextView(this).apply {
            text = "\u2190 TORNA"; textSize = 14f; setTextColor(Color.parseColor("#9999CC"))
            setOnClickListener { finish() }
        })
        controlLayer.addView(topBar)
        return controlLayer
    }

    private fun ctrlBtn(text: String, color: String = "#4444AA", onClick: () -> Unit = {}): Button {
        val b = Button(this)
        b.text = text; b.textSize = 12f; b.setTextColor(Color.WHITE)
        b.typeface = Typeface.DEFAULT_BOLD; b.gravity = Gravity.CENTER
        b.setPadding(dp(4), dp(6), dp(4), dp(6))
        b.layoutParams = LinearLayout.LayoutParams(dp(48), dp(44)).also { it.marginEnd = dp(3) }
        b.background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(Color.parseColor(color)); alpha = 200 }
        b.setOnClickListener { onClick() }
        return b
    }

    private fun showResult(result: BattleEngine3D.BattleResult) {
        gameLoop?.let { handler.removeCallbacks(it) }
        val win = result == BattleEngine3D.BattleResult.PLAYER_WIN
        val sc = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0A1A")) }
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(80), dp(24), dp(32))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        sc.addView(r)
        r.addView(TextView(this).apply {
            text = if (win) "VITTORIA!" else "SCONFITTA..."
            textSize = 36f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor(if (win) "#FFD700" else "#FF4444"))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })
        r.addView(TextView(this).apply {
            text = "${playerFighter.name} vs ${enemyFighter.name}"
            textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#8080A0"))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(4) }
        })
        r.addView(spacer(dp(12)))
        r.addView(TextView(this).apply {
            text = "Round: ${engine.playerRoundWins}-${engine.enemyRoundWins}"
            textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#FFD700"))
        })
        r.addView(TextView(this).apply {
            text = "Colpi: ${engine.player.hitsLanded} | Danno: ${engine.player.totalDamage}"
            textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#8080A0"))
        })
        r.addView(spacer(dp(20)))
        r.addView(Button(this).apply {
            text = "RIVINCITA!"; textSize = 17f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#1565C0")) }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(50))
            setOnClickListener { restartGame() }
        })
        r.addView(spacer(dp(10)))
        r.addView(TextView(this).apply {
            text = "Cambia Lottatore"; textSize = 14f; setTextColor(Color.parseColor("#9999CC"))
            gravity = Gravity.CENTER
            setOnClickListener { restartSelect() }
        })
        setContentView(sc)
    }

    private fun restartGame() {
        val pid = playerFighter.id; val eid = enemyFighter.id
        startActivity(Intent(this, FightingGame3DActivity::class.java).apply {
            putExtra("player_id", pid); putExtra("enemy_id", eid)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun restartSelect() {
        startActivity(Intent(this, FighterSelectActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun spacer(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, h) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    class HUDView(
        ctx: FightingGame3DActivity,
        private val engine: BattleEngine3D,
        private val pDef: FighterDef,
        private val eDef: FighterDef
    ) : View(ctx) {
        private val pPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 40; strokeWidth = 1f; style = Paint.Style.STROKE }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val barW = w * 0.38f; val barH = dp(14).toFloat(); val barY = dp(20).toFloat()

            barBgPaint.color = Color.parseColor("#1A1A3E")
            c.drawRoundRect(barW * 0.05f, barY, barW * 0.05f + barW, barY + barH, 6f, 6f, barBgPaint)
            c.drawRoundRect(w - barW * 1.05f, barY, w - barW * 0.05f, barY + barH, 6f, 6f, barBgPaint)
            c.drawRoundRect(barW * 0.05f, barY, barW * 0.05f + barW, barY + barH, 6f, 6f, borderPaint)
            c.drawRoundRect(w - barW * 1.05f, barY, w - barW * 0.05f, barY + barH, 6f, 6f, borderPaint)

            val php = (engine.player.hp / engine.player.maxHp).coerceIn(0f, 1f)
            val ehp = (engine.enemy.hp / engine.enemy.maxHp).coerceIn(0f, 1f)

            pPaint.color = pDef.primaryColor
            ePaint.color = eDef.primaryColor
            c.drawRoundRect(barW * 0.05f, barY, barW * 0.05f + barW * php, barY + barH, 6f, 6f, pPaint)
            c.drawRoundRect(w - barW * 1.05f + barW * (1f - ehp), barY, w - barW * 0.05f, barY + barH, 6f, 6f, ePaint)

            textPaint.color = Color.WHITE; textPaint.textSize = dp(10).toFloat(); textPaint.typeface = Typeface.DEFAULT_BOLD; textPaint.textAlign = Paint.Align.LEFT
            c.drawText(pDef.name, barW * 0.05f, barY - dp(3).toFloat(), textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            c.drawText(eDef.name, w - barW * 0.05f, barY - dp(3).toFloat(), textPaint)

            val time = engine.timeRemainingMs / 1000
            textPaint.textAlign = Paint.Align.CENTER; textPaint.textSize = dp(20).toFloat()
            textPaint.color = Color.parseColor("#FFD700")
            c.drawText("${time / 60}:${"%02d".format(time % 60)}", w * 0.5f, barY + barH + dp(22).toFloat(), textPaint)

            val rounds = "${engine.playerRoundWins} - ${engine.enemyRoundWins}"
            textPaint.textSize = dp(14).toFloat(); textPaint.color = Color.WHITE
            c.drawText("R $rounds", w * 0.5f, barY + barH + dp(42).toFloat(), textPaint)

            val pSuper = engine.player.superBar; val eSuper = engine.enemy.superBar
            val sw = barW * 0.35f; val sh = dp(5).toFloat(); val sy = barY + barH + dp(50).toFloat()
            barBgPaint.color = Color.parseColor("#2A003355")
            c.drawRoundRect(barW * 0.05f, sy, barW * 0.05f + sw, sy + sh, 3f, 3f, barBgPaint)
            c.drawRoundRect(w - barW * 0.4f, sy, w - barW * 0.05f, sy + sh, 3f, 3f, barBgPaint)
            pPaint.color = Color.parseColor("#E879F9")
            c.drawRoundRect(barW * 0.05f, sy, barW * 0.05f + sw * pSuper, sy + sh, 3f, 3f, pPaint)
            ePaint.color = Color.parseColor("#E879F9")
            c.drawRoundRect(w - barW * 0.4f + sw * (1f - eSuper), sy, w - barW * 0.05f, sy + sh, 3f, 3f, ePaint)

            if (pSuper >= 1f) {
                textPaint.textSize = dp(8).toFloat(); textPaint.color = Color.parseColor("#E879F9"); textPaint.textAlign = Paint.Align.LEFT
                c.drawText("SUPER!", barW * 0.05f, sy - dp(2).toFloat(), textPaint)
            }
            if (eSuper >= 1f) {
                textPaint.textSize = dp(8).toFloat(); textPaint.color = Color.parseColor("#E879F9"); textPaint.textAlign = Paint.Align.RIGHT
                c.drawText("SUPER!", w - barW * 0.05f, sy - dp(2).toFloat(), textPaint)
            }
        }
        private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    }

    class EffectOverlayView(
        ctx: FightingGame3DActivity,
        private val engine: BattleEngine3D
    ) : View(ctx) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val figPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var shakeIntensity = 0f
        private var hitFreeze = 0
        private var superFlashLeft = 0f
        private var superFlashRight = 0f

        data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var color: Int)
        private val particles = mutableListOf<Particle>()

        data class Wave(val x: Float, val y: Float, val dir: Int, val life: Float, val maxLife: Float, val color: Int)
        private val waves = mutableListOf<Wave>()

        fun shake(i: Float) { shakeIntensity = max(shakeIntensity, i) }
        fun showHitEffect(isEnemy: Boolean, crit: Boolean) {
            val f = if (isEnemy) engine.enemy else engine.player
            val px = f.x * width; val py = height * 0.45f
            val color = if (crit) Color.parseColor("#FFD700") else Color.parseColor("#66FF88")
            val n = if (crit) 18 else 10
            repeat(n) {
                particles.add(Particle(px + Random.nextInt(-15, 15), py + Random.nextInt(-15, 15),
                    Random.nextFloat() * 250f - 125f, Random.nextFloat() * -250f - 50f,
                    0.4f + Random.nextFloat() * 0.3f, color))
            }
            hitFreeze = if (crit) 5 else 2
        }
        fun flashSuper(isEnemy: Boolean) {
            if (isEnemy) superFlashRight = 0.3f else superFlashLeft = 0.3f
        }
        fun spawnEnergyWave(fromPlayer: Boolean, color: Int) {
            val f = if (fromPlayer) engine.player else engine.enemy
            waves.add(Wave(f.x, 0.45f, if (fromPlayer) 1 else -1, 0.8f, 0.8f, color))
        }

        fun engineTick(dt: Float) {
            particles.removeAll { p ->
                p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 500f * dt; p.life -= dt; p.life <= 0f
            }
            waves.removeAll { it.life <= 0f }
            waves.replaceAll { it.copy(life = it.life - dt) }
            if (hitFreeze > 0) hitFreeze--
            shakeIntensity = max(0f, shakeIntensity * (1f - dt * 8f))
            superFlashLeft = max(0f, superFlashLeft - dt * 3f)
            superFlashRight = max(0f, superFlashRight - dt * 3f)
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val sx = if (shakeIntensity > 0.5f) (Random.nextFloat() - 0.5f) * shakeIntensity * 12f else 0f
            val sy = if (shakeIntensity > 0.5f) (Random.nextFloat() - 0.5f) * shakeIntensity * 8f else 0f
            c.save()
            if (sx != 0f || sy != 0f) c.translate(sx, sy)

            if (superFlashLeft > 0 || superFlashRight > 0) {
                bgPaint.color = Color.WHITE
                bgPaint.alpha = (max(superFlashLeft, superFlashRight) * 150).toInt()
                c.drawRect(0f, 0f, w, h, bgPaint); bgPaint.alpha = 255
            }

            drawParticles(c)
            drawWaves(c, w, h)

            if (hitFreeze > 0) {
                bgPaint.color = Color.WHITE; bgPaint.alpha = 25
                c.drawRect(0f, 0f, w, h, bgPaint); bgPaint.alpha = 255
            }
            c.restore()
        }

        private fun drawParticles(c: Canvas) {
            particles.forEach { p ->
                sparkPaint.color = p.color; sparkPaint.alpha = (p.life / 0.7f * 255).toInt().coerceIn(0, 255)
                sparkPaint.style = Paint.Style.FILL
                c.drawCircle(p.x, p.y, 5f, sparkPaint)
            }
        }

        private fun drawWaves(c: Canvas, w: Float, h: Float) {
            waves.forEach { wave ->
                val progress = 1f - wave.life / wave.maxLife
                val wx = wave.x * w + wave.dir * progress * w * 0.3f; val wy = h * 0.45f
                val r = (25f + progress * 35f) * (1f - progress * 0.2f)
                val alpha = (wave.life / wave.maxLife * 180).toInt().coerceIn(0, 180)

                glowPaint.color = wave.color; glowPaint.alpha = alpha; glowPaint.style = Paint.Style.FILL; glowPaint.strokeWidth = 1f
                c.drawCircle(wx, wy, r, glowPaint)
                glowPaint.color = Color.WHITE; glowPaint.alpha = ((alpha * 0.6f).toInt()).coerceAtMost(150)
                c.drawCircle(wx, wy, r * 0.5f, glowPaint)
                figPaint.color = wave.color; figPaint.alpha = ((alpha * 0.3f).toInt()).coerceAtMost(80); figPaint.style = Paint.Style.FILL
                c.drawCircle(wx, wy, r * 1.8f, figPaint)
                figPaint.alpha = 255
                sparkPaint.color = Color.WHITE; sparkPaint.alpha = (alpha * 0.7f).toInt()
                c.drawCircle(wx - wave.dir * r * 0.4f, wy, r * 0.25f, sparkPaint)
                sparkPaint.alpha = 255
            }
        }
    }
}
