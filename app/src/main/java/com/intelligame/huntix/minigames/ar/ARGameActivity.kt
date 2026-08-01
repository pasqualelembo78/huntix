package com.intelligame.huntix.minigames.ar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Config
import com.intelligame.huntix.R
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * ARGameActivity — base condivisa per i minigiochi in Realtà Aumentata.
 *
 * A differenza dei vecchi "AR" minigiochi (che erano Canvas 2D fasulli), qui le
 * uova sono davvero oggetti 3D ancorati nello spazio reale tramite ARCore:
 * ogni uovo è un [SphereNode] figlio di un [AnchorNode] creato a partire dalla
 * pose della camera corrente, quindi resta sospeso nell'aria della stanza e
 * viene tracciato dal mondo reale. Il tocco viene risolto tramite l'hit-test
 * della scena ([ARSceneView.onTouchEvent]) che restituisce il nodo toccato.
 */
abstract class ARGameActivity : AppCompatActivity() {

    protected lateinit var sceneView: ARSceneView
    protected lateinit var hud: FrameLayout
    protected lateinit var statusText: TextView
    protected lateinit var scoreText: TextView
    protected lateinit var livesText: TextView
    protected lateinit var timerText: TextView

    private val eggs = LinkedHashMap<Node, AREgg>()
    private var lastSession: Session? = null
    private var lastFrame: Frame? = null
    protected var running = false
        private set
    private val handler = Handler(Looper.getMainLooper())
    private var trackingReady = false
    private var pendingAction: (() -> Unit)? = null

    /** Motore audio 3D (sintesi PCM + panning distanza). */
    protected val spatialAudio = SpatialAudio()
    /** Se true, il gioco usa il rilevamento piani + depth (es. ambienti appoggiati). */
    protected var usePlaneDetection = false
    /** Effetti di rottura/particelle animati ogni frame. */
    private val fx = mutableListOf<FxParticle>()
    private var fxLast = 0L

    data class FxParticle(
        val node: SphereNode,
        var vel: Float3,
        var life: Float,
        val maxLife: Float
    )

    data class AREgg(
        val anchorNode: AnchorNode,
        var node: Node,
        var type: Int,
        var phase: Float = 0f,
        var alive: Boolean = true
    )

    companion object {
        private val EGG_COLORS = intArrayOf(
            0xFFF4C2.toInt(), 0xA78BFA.toInt(), 0x00FF88.toInt(),
            0xFF7AB6.toInt(), 0xFFD166.toInt(), 0x6AD7FF.toInt(), 0xFF6B6B.toInt()
        )
        fun eggColor(type: Int) = EGG_COLORS[type % EGG_COLORS.size]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("ARGameActivity", "onCreate start")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_ar_game)
        sceneView = findViewById(R.id.sceneView)
        hud = findViewById(R.id.arOverlay)
        android.util.Log.d("ARGameActivity", "SceneView found: ${sceneView != null}, SceneView class: ${sceneView?.javaClass?.name}")
        android.util.Log.d("ARGameActivity", "HUD found: ${hud != null}")
        android.util.Log.d("ARGameActivity", "SceneView initialized: ${sceneView != null}")
        buildHud()
        android.util.Log.d("ARGameActivity", "Checking CAMERA permission")

        sceneView.configureSession { _, config ->
            config.planeFindingMode = if (usePlaneDetection) Config.PlaneFindingMode.HORIZONTAL else Config.PlaneFindingMode.DISABLED
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            config.focusMode = Config.FocusMode.AUTO
            config.depthMode = if (usePlaneDetection) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
            config.cloudAnchorMode = Config.CloudAnchorMode.DISABLED
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        }
        sceneView.onSessionUpdated = { s, f ->
            lastSession = s
            lastFrame = f
            android.util.Log.d("ARGameActivity", "Session updated, tracking=${f.camera.trackingState}")
            if (!trackingReady && f.camera.trackingState == TrackingState.TRACKING) {
                trackingReady = true
                android.util.Log.d("ARGameActivity", "TRACKING READY!")
                pendingAction?.let { it() }; pendingAction = null
                onTrackingReady()
            }
            onArFrame(s, f)
            tickEngine()
        }
        sceneView.onTouchEvent = { event, svHit ->
            if (event.action == MotionEvent.ACTION_UP) {
                val egg = svHit?.node?.let { eggs[it] }
                when {
                    egg != null -> onEggTapped(egg)
                    svHit?.node != null -> onNodeTapped(svHit.node)
                    else -> onBackgroundTapped(event)
                }
            }
            true
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.d("ARGameActivity", "Permission granted, calling onGameCreate")
            onGameCreate()
        } else {
            android.util.Log.d("ARGameActivity", "Requesting camera permission")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        }

        // Fallback: se dopo 5 secondi non c'è tracking, forziamo l'inizio
        handler.postDelayed({
            if (!trackingReady && !isFinishing) {
                android.util.Log.w("ARGameActivity", "Tracking timeout, forcing start")
                trackingReady = true
                pendingAction?.let { it() }; pendingAction = null
                onTrackingReady()
            }
        }, 5000)
    }

    override fun onRequestPermissionsResult(
        req: Int, permissions: Array<out String>, results: IntArray
    ) {
        super.onRequestPermissionsResult(req, permissions, results)
        if (req == 1001) onGameCreate()
    }

    private fun buildHud() {
        val c = this
        val topRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(c, 12), UiKit.dp(c, 10), UiKit.dp(c, 12), UiKit.dp(c, 4))
        }
        livesText = TextView(c).apply {
            text = ""; textSize = 15f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        timerText = TextView(c).apply {
            text = ""; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        scoreText = TextView(c).apply {
            text = ""; textSize = 15f; setTextColor(Color.parseColor(UiKit.GREEN)); gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(livesText); topRow.addView(timerText); topRow.addView(scoreText)

        statusText = TextView(c).apply {
            text = "Inquadra la stanza e muovi lentamente il telefono…"
            textSize = 13f; setTextColor(Color.parseColor(UiKit.ACCENT)); gravity = Gravity.CENTER
            setPadding(UiKit.dp(c, 8), 0, UiKit.dp(c, 8), UiKit.dp(c, 6))
        }

        val wrap = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        wrap.addView(topRow); wrap.addView(statusText)
        hud.addView(wrap)
    }

    // ── lifecycle hooks ──────────────────────────────────────────

    /** Called once the camera permission is granted (or already present). */
    protected abstract fun onGameCreate()

    /** Called the first time ARCore reports a TRACKING frame. */
    protected open fun onTrackingReady() {}

    protected open fun onArFrame(session: Session, frame: Frame) {}
    protected open fun onEggSpawned(egg: AREgg) {}
    protected open fun onEggTapped(egg: AREgg) {}
    protected open fun onNodeTapped(node: Node) {}
    protected open fun onBackgroundTapped(event: MotionEvent) {}

    // ── spawning / manipulation ──────────────────────────────────

    /**
     * Crea un'uovo sospeso nell'aria davanti alla camera corrente.
     * [forward] = distanza dalla camera (m, valori positivi = davanti),
     * [right]/[up] = offset laterale/verticale nel piano della camera (m).
     */
    protected fun spawnEgg(
        type: Int, forward: Float, right: Float, up: Float, radius: Float = 0.07f
    ): AREgg? {
        val session = lastSession ?: run {
            android.util.Log.w("ARGameActivity", "spawnEgg: session is null")
            return null
        }
        val frame = lastFrame ?: run {
            android.util.Log.w("ARGameActivity", "spawnEgg: frame is null")
            return null
        }
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            android.util.Log.w("ARGameActivity", "spawnEgg: not tracking")
            return null
        }
        val camPose = frame.camera.pose
        val offset = Pose(
            floatArrayOf(right, up, -forward),
            floatArrayOf(0f, 0f, 0f, 1f)
        )
        val pose = camPose.compose(offset)
        val anchor = session.createAnchor(pose)
        val an = AnchorNode(engine = sceneView.engine, anchor = anchor)
        val mat = sceneView.materialLoader.createColorInstance(color = eggColor(type))
        val node = SphereNode(sceneView.engine, radius, materialInstance = mat).apply {
            position = Position(0f, 0f, 0f)
            scale = Scale(1f, 1.4f, 1f)
        }
        an.addChildNode(node)
        sceneView.addChildNode(an)
        val egg = AREgg(an, node, type, phase = Math.random().toFloat() * 6.28f)
        eggs[node] = egg
        android.util.Log.d("ARGameActivity", "spawnEgg: spawned type=$type at ($right, $up, $forward)")
        onEggSpawned(egg)
        return egg
    }

    /** Riposiziona l'uovo (bobbing/movimento) tramite offset locale rispetto all'anchor. */
    protected fun moveEggLocal(egg: AREgg, x: Float, y: Float, z: Float) {
        egg.node.position = Position(x, y, z)
    }

    /** Cambia il colore/tipo di un uovo ricreando il nodo figlio. */
    protected fun recolorEgg(egg: AREgg, type: Int) {
        val old = egg.node
        eggs.remove(old)
        egg.anchorNode.removeChildNode(old)
        old.destroy()
        val mat = sceneView.materialLoader.createColorInstance(color = eggColor(type))
        val node = SphereNode(sceneView.engine, 0.06f, materialInstance = mat).apply {
            position = Position(0f, 0f, 0f); scale = Scale(1f, 1.4f, 1f)
        }
        egg.anchorNode.addChildNode(node)
        egg.node = node
        egg.type = type
        eggs[node] = egg
    }

    protected fun removeEgg(egg: AREgg) {
        if (!egg.alive) return
        egg.alive = false
        eggs.remove(egg.node)
        egg.anchorNode.destroy()
    }

    // ── shared-anchor arena (per giochi con campo di gioco unico) ──

    /** Ancora in un unico spazio 3D condiviso da tutti i nodi del gioco. */
    private val sharedAnchors = mutableListOf<AnchorNode>()

    /**
     * Crea un'ancora "arena" unica davanti alla camera (come [spawnEgg] ma senza
     * uovo): tutti i nodi del gioco vanno aggiunti come figli dell'[AnchorNode]
     * restituito, così condividono lo stesso sistema di coordinate locali.
     */
    protected fun spawnAnchor(forward: Float, right: Float, up: Float): AnchorNode? {
        val session = lastSession ?: run {
            android.util.Log.w("ARGameActivity", "spawnAnchor: session is null")
            return null
        }
        val frame = lastFrame ?: run {
            android.util.Log.w("ARGameActivity", "spawnAnchor: frame is null")
            return null
        }
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            android.util.Log.w("ARGameActivity", "spawnAnchor: not tracking")
            return null
        }
        val camPose = frame.camera.pose
        val offset = Pose(
            floatArrayOf(right, up, -forward),
            floatArrayOf(0f, 0f, 0f, 1f)
        )
        val pose = camPose.compose(offset)
        val anchor = session.createAnchor(pose)
        android.util.Log.d("ARGameActivity", "spawnAnchor: creating AnchorNode, engine=${sceneView.engine != null}")
        val an = AnchorNode(engine = sceneView.engine, anchor = anchor)
        sceneView.addChildNode(an)
        sharedAnchors.add(an)
        return an
    }

    protected fun eggList(): List<AREgg> = eggs.values.toList()
    protected fun aliveCount() = eggs.size
    protected fun isTracking() = trackingReady

    /** Pose della camera corrente (usata dall'audio spaziale / effetti). */
    protected val cameraPose: Pose?
        get() = lastFrame?.camera?.pose

    /**
     * Ancora lanciata al centro dello schermo su un piano rilevato (floor/table).
     * Necessita di [usePlaneDetection] = true. Restituisce null se nessun
     * piano è stato inquadrato (ARCore depth-aided hitTest).
     */
    protected fun tryAnchorToPlane(): AnchorNode? {
        val session = lastSession ?: return null
        val frame = lastFrame ?: return null
        if (frame.camera.trackingState != TrackingState.TRACKING) return null
        val cx = (sceneView.width / 2f).toFloat()
        val cy = (sceneView.height / 2f).toFloat()
        val hit = frame.hitTest(cx, cy)
        val planeHit = hit.firstOrNull { h ->
            val tr = h.trackable
            tr is Plane && tr.isPoseInPolygon(h.hitPose)
        } ?: return null
        val anchor = session.createAnchor(planeHit.hitPose)
        val an = AnchorNode(engine = sceneView.engine, anchor = anchor)
        sceneView.addChildNode(an)
        sharedAnchors.add(an)
        return an
    }

    // ── impressività (haptics / suono / parti) ────────────────────

    protected fun haptic(heavy: Boolean = false) {
        try {
            val v = if (Build.VERSION.SDK_INT >= 31) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vm.defaultVibrator
            } else @Suppress("DEPRECATION") {
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(if (heavy) 50L else 18L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else @Suppress("DEPRECATION") {
                v.vibrate(if (heavy) 50L else 18L)
            }
        } catch (_: Exception) { }
    }

    /** Bottilo di parti colorate che esplodono dalla posizione mondiale. */
    protected fun burst(worldPos: Float3, color: Int, count: Int = 14) {
        repeat(count) {
            val part = eggNode(color, UiKit.dp(this, 3).toFloat()).apply {
                scale = Scale(0.6f, 0.6f, 0.6f)
                position = Position(worldPos.x, worldPos.y, worldPos.z)
            }
            sceneView.addChildNode(part)
            val ang = (it * (2.0 * Math.PI / count) + Math.random() * 0.5).toFloat()
            val up = (Math.random() * 0.6).toFloat()
            val speed = 0.5f + Math.random().toFloat() * 0.4f
            fx += FxParticle(part, Float3(cos(ang) * speed, up + 0.3f, -sin(ang) * speed), 0.5f, 0.5f)
        }
    }

    private fun tickEngine() {
        val now = SystemClock.elapsedRealtime()
        val dt = ((now - fxLast) / 1000f).coerceIn(0f, 0.05f)
        fxLast = now
        if (dt > 0f && fx.isNotEmpty()) {
            val iter = fx.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                p.life -= dt
                if (p.life <= 0f) {
                    iter.remove()
                    removeNode(p.node)
                    continue
                }
                val pos = p.node.position
                p.node.position = Position(pos.x + p.vel.x * dt, pos.y + p.vel.y * dt, pos.z + p.vel.z * dt)
                p.vel = Float3(p.vel.x, p.vel.y - 3f * dt, p.vel.z)
                val s = (p.life / p.maxLife) * 0.6f
                p.node.scale = Scale(s, s, s)
            }
        }
        spatialAudio.tick(cameraPose)
    }


    // ── primitive nodes ──────────────────────────────────────────

    /** Uovo (sfera schiacciata) colorata, figlia di un anchor. */
    protected fun eggNode(color: Int, radius: Float = 0.075f): SphereNode {
        val mat = sceneView.materialLoader.createColorInstance(color = color)
        return SphereNode(sceneView.engine, radius, materialInstance = mat).apply {
            scale = Scale(1f, 1.35f, 1f)
        }
    }

    /** Parallelepipedo colorato (tile/colonna/muro). */
    protected fun cubeNode(color: Int, size: Float3): CubeNode {
        val mat = sceneView.materialLoader.createColorInstance(color = color)
        return CubeNode(sceneView.engine, size, Float3(0f, 0f, 0f), mat)
    }

    /** Rimuove e distrugge un nodo qualunque. */
    protected fun removeNode(node: Node) {
        node.parent?.removeChildNode(node)
        node.destroy()
    }

    // ── input capture (drag / tap full-screen) ─────────────────────

    private var inputLayer: FrameLayout? = null
    private var inputStart: Pair<Float, Float>? = null

    /**
     * Intercetta tutti i tocchi a tutto schermo (bloccando l'hit-test della
     * scena): usato dai giochi a guida da touch/schiocco. [onStart] al tocco,
     * [onDrag] con il delta in tempo reale, [onEnd] al rilascio (con isTap).
     */
    protected fun installInputCapture(
        onStart: (() -> Unit)? = null,
        onDrag: ((dx: Float, dy: Float) -> Unit)? = null,
        onEnd: ((dx: Float, dy: Float, isTap: Boolean) -> Unit)? = null
    ) {
        val layer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        inputStart = ev.x to ev.y
                        onStart?.invoke()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val s = inputStart ?: return@setOnTouchListener true
                        onDrag?.invoke(ev.x - s.first, ev.y - s.second)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val s = inputStart ?: return@setOnTouchListener true
                        val dx = ev.x - s.first
                        val dy = ev.y - s.second
                        val isTap = abs(dx) < 40 && abs(dy) < 40
                        inputStart = null
                        onEnd?.invoke(dx, dy, isTap)
                        true
                    }
                    else -> true
                }
            }
        }
        inputLayer = layer
        hud.addView(layer)
    }

    protected fun removeInputCapture() {
        inputLayer?.let { hud.removeView(it) }
        inputLayer = null
    }

    /**
     * Esegue [action] non appena ARCore ha un frame TRACKING (necessario perché
     * le uova si ancorano allo spazio reale). Se il tracking è già pronto viene
     * eseguita subito (utile anche nei restart).
     */
    protected fun whenReady(action: () -> Unit) {
        android.util.Log.d("ARGameActivity", "whenReady called, trackingReady=$trackingReady")
        if (trackingReady) action() else pendingAction = action
    }

    protected fun startGame() { running = true }
    protected fun stopGame() { running = false }

    protected fun postDelayed(delay: Long, r: Runnable) = handler.postDelayed(r, delay)
    protected fun postDelayed(delay: Long, r: () -> Unit): Runnable {
        val runnable = Runnable(r); handler.postDelayed(runnable, delay); return runnable
    }

    protected fun removeCallback(r: Runnable?) = r?.let { handler.removeCallbacks(it) }

    // ── end of game ──────────────────────────────────────────────

    protected fun finishGame(reward: Int, label: String, isWin: Boolean, gameId: String) {
        running = false
        removeInputCapture()
        MiniGameManager.consumePlay(this, gameId)
        MiniGameManager.applyReward(
            this,
            MiniGameManager.GameReward(mvcCoins = reward, label = label, isWin = isWin),
            gameId
        )
        statusText.text = "🎮 Fine! +$reward MVC"
        statusText.setTextColor(Color.parseColor(UiKit.GREEN))
        val again = UiKit.button(this, "🔄 Gioca Ancora", UiKit.ACCENT) { restart() }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = UiKit.dp(this@ARGameActivity, 24)
            leftMargin = UiKit.dp(this@ARGameActivity, 24); rightMargin = UiKit.dp(this@ARGameActivity, 24)
        }
        again.layoutParams = lp
        hud.addView(again)
    }

    protected fun restart() {
        removeInputCapture()
        eggs.values.forEach { it.anchorNode.destroy() }
        eggs.clear()
        fx.forEach { removeNode(it.node) }
        fx.clear()
        spatialAudio.release()
        sharedAnchors.forEach { it.destroy() }
        sharedAnchors.clear()
        running = false
        onGameCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("ARGameActivity", "onDestroy called")
        running = false
        eggs.values.forEach { it.anchorNode.destroy() }
        eggs.clear()
        fx.forEach { removeNode(it.node) }
        fx.clear()
        spatialAudio.release()
        sharedAnchors.forEach { it.destroy() }
        sharedAnchors.clear()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onPause() {
        super.onPause()
        android.util.Log.d("ARGameActivity", "onPause called, running=$running")
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("ARGameActivity", "onResume called")
    }
}

/** Estensione per estrarre un Float casuale da un range chiuso (non presente in stdlib). */
fun ClosedRange<Float>.random(): Float =
    start + Math.random().toFloat() * (endInclusive - start)
