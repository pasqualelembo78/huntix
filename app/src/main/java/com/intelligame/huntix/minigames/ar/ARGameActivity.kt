package com.intelligame.huntix.minigames.ar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Config
import com.intelligame.huntix.R
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode

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
        setContentView(R.layout.activity_ar_game)
        sceneView = findViewById(R.id.sceneView)
        hud = findViewById(R.id.arOverlay)
        buildHud()

        sceneView.configureSession { session, config ->
            config.planeFindingMode = Config.PlaneFindingMode.DISABLED
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            config.focusMode = Config.FocusMode.AUTO
            config.depthMode = Config.DepthMode.DISABLED
            config.cloudAnchorMode = Config.CloudAnchorMode.DISABLED
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        }
        sceneView.onSessionUpdated = { s, f ->
            lastSession = s
            lastFrame = f
            if (!trackingReady && f.camera.trackingState == TrackingState.TRACKING) {
                trackingReady = true
                pendingAction?.let { it() }; pendingAction = null
                onTrackingReady()
            }
            onArFrame(s, f)
        }
        sceneView.onTouchEvent = { event, svHit ->
            if (event.action == MotionEvent.ACTION_UP) {
                val egg = svHit?.node?.let { eggs[it] }
                if (egg != null) onEggTapped(egg) else onBackgroundTapped(event)
            }
            true
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            onGameCreate()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        }
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
        val session = lastSession ?: return null
        val frame = lastFrame ?: return null
        if (frame.camera.trackingState != TrackingState.TRACKING) return null
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

    protected fun eggList(): List<AREgg> = eggs.values.toList()
    protected fun aliveCount() = eggs.size
    protected fun isTracking() = trackingReady

    /**
     * Esegue [action] non appena ARCore ha un frame TRACKING (necessario perché
     * le uova si ancorano allo spazio reale). Se il tracking è già pronto viene
     * eseguita subito (utile anche nei restart).
     */
    protected fun whenReady(action: () -> Unit) {
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
        eggs.values.forEach { it.anchorNode.destroy() }
        eggs.clear()
        running = false
        onGameCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        eggs.values.forEach { it.anchorNode.destroy() }
        eggs.clear()
        handler.removeCallbacksAndMessages(null)
    }
}

/** Estensione per estrarre un Float casuale da un range chiuso (non presente in stdlib). */
fun ClosedRange<Float>.random(): Float =
    start + Math.random().toFloat() * (endInclusive - start)
