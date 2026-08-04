package com.intelligame.huntix.minigames.ar

import android.Manifest
import android.app.AlertDialog
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.BuildConfig
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
import io.github.sceneview.ar.arcore.zDirection
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import java.util.Collections
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
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
 *
 * I giochi "posizionati" (griglie su tavolo, forca, Memory, ecc.) mostrano
 * all'avvio un dialogo che permette di scegliere la [ARGameMode] con cui
 * ancorare l'arena: 3D meshing (geometria reale via depth), rilevamento
 * superfici (piani ARCore) o modalità libera (oggetti sospesi). La scelta
 * viene ricordata tra un avvio e l'altro. I giochi "liberi" (uova volanti)
 * restano sempre fluttuanti e non mostrano il dialogo.
 */
abstract class ARGameActivity : AppCompatActivity() {

    /** Modalità di posizionamento AR dello spazio di gioco. */
    enum class ARGameMode { MESHING, PLANE, FREE }

    /** Modalità attiva, ricordata tra un avvio e l'altro. */
    protected var gameMode: ARGameMode = ARGameMode.PLANE
        private set

    /** Se true, all'avvio del gioco viene mostrato il dialogo di scelta modalità. */
    protected var showsModeDialog = false

    protected lateinit var sceneView: ARSceneView
    protected lateinit var hud: FrameLayout
    protected lateinit var statusText: TextView
    protected lateinit var scoreText: TextView
    protected lateinit var livesText: TextView
    protected lateinit var timerText: TextView
    protected lateinit var levelText: TextView

    private val eggs = Collections.synchronizedMap(LinkedHashMap<Node, AREgg>())
    private var lastSession: Session? = null
    private var lastFrame: Frame? = null
    protected var running = false
        private set
    private val handler = Handler(Looper.getMainLooper())
    private var trackingReady = false
    private var pendingAction: (() -> Unit)? = null
    private var lastTrackingState: TrackingState? = null

    // ── stanze AR ("trova il piano una volta per tutte") ──────────
    // Le arene piazzate su una superficie reale vengono salvate per stanza
    // ([ArArenaStore]: locale + cloud, separate dalle stanze indoor). Alla
    // riapertura dell'app si prova prima a risolvere la Cloud Anchor della
    // stanza scelta (stesso punto, senza riscansionare), poi si ripiega sulla
    // scansione normale; la nuova posizione diventa una stanza nuova.
    protected var usesSurfaceArena = true
    private var currentRoomId: String? = null
    private var forceNewRoom = false
    private var cloudRestoreAttempted = false
    private var cloudRestoring = false
    private var restoredArena: AnchorNode? = null
    private var resolvingCloudAnchor: Anchor? = null
    private var hostingCloudAnchor: Anchor? = null

    /** Motore audio 3D (sintesi PCM + panning distanza). */
    protected val spatialAudio = SpatialAudio()
    /** Effetti di rottura/particelle animati ogni frame. */
    private val fx = Collections.synchronizedList(mutableListOf<FxParticle>())
    private var fxLast = 0L

    // ── zoom a due dita (pinch) ─────────────────────────────────
    // Le ancore AR sono agganciate al mondo reale: per allargare/rimpicciolire
    // il campo senza perderne l'ancoraggio scaliamo le [AnchorNode] stesse.
    // PoseNode riscrive la transform a ogni aggiornamento dell'anchor, quindi
    // la scala va ri-applicata ogni frame (vedi [syncContentScale]).
    protected var contentScale = 1f
        private set
    private var pinchGesture = false
    private var suppressTap = false
    private var pinchStartDist = 0f
    private var pinchStartScale = 1f
    private var zoomText: TextView? = null

    data class FxParticle(
        val node: SphereNode,
        var vel: Float3,
        var life: Float,
        val maxLife: Float,
        var gravity: Float = 3f
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
            0xFFFFF4C2.toInt(), 0xFFA78BFA.toInt(), 0xFF00FF88.toInt(),
            0xFFFF7AB6.toInt(), 0xFFFFD166.toInt(), 0xFF6AD7FF.toInt(), 0xFFFF6B6B.toInt()
        )
        fun eggColor(type: Int) = EGG_COLORS[type % EGG_COLORS.size]

        /** Durata della Cloud Anchor dell'arena: 30gg in modalità keyless
         * (OAuth), 1gg con API key (limite ARCore). */
        private const val ARENA_TTL_DAYS_KEYLESS = 30
        private const val ARENA_TTL_DAYS_APIKEY = 1
        private const val CLOUD_RESTORE_TIMEOUT_MS = 10_000L

        /** Colori vivaci per coriandoli/fuochi delle celebrazioni. */
        private val CONFETTI_COLORS = intArrayOf(
            0xFFFFD700.toInt(), 0xFF00E5FF.toInt(), 0xFFFF5252.toInt(), 0xFF69F0AE.toInt(),
            0xFFFFB300.toInt(), 0xFFE040FB.toInt(), 0xFFFFFFFF.toInt()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("ARGameActivity", "onCreate start")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_ar_game)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.arOverlay)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom + UiKit.dp(this, 8))
            insets
        }
        sceneView = findViewById(R.id.sceneView)
        hud = findViewById(R.id.arOverlay)
        android.util.Log.d("ARGameActivity", "SceneView found: ${sceneView != null}, SceneView class: ${sceneView?.javaClass?.name}")
        android.util.Log.d("ARGameActivity", "HUD found: ${hud != null}")
        android.util.Log.d("ARGameActivity", "SceneView initialized: ${sceneView != null}")

        // Luce ambientale per i giochi AR: l'ambiente di default di ARSceneView
        // non ha IndirectLight, quindi gli oggetti sono illuminati solo dal "sole"
        // stimato da ARCore (AMBIENT_INTENSITY). Con pixelIntensity bassa (stanza
        // normale) il sole è troppo debole e le uova risultano nere. La IBL neutra
        // garantisce luce ambientale uniforme: oggetti sempre visibili e colorati.
        runCatching {
            sceneView.environment = sceneView.environmentLoader.createKTX1Environment(
                iblAssetFile = "environments/neutral/neutral_ibl.ktx",
                skyboxAssetFile = null
            )
        }

        buildHud()

        // Set callbacks BEFORE triggering the lifecycle. If the lifecycle was
        // already auto-detected by SceneView.onAttachedToWindow(), the session
        // may already be created, so configureSession's callback would be missed.
        // By setting callbacks first, we ensure they are registered before any
        // session creation happens (whether from onAttachedToWindow or the
        // manual fallback below).
        sceneView.onSessionCreated = { _ ->
            AppLog.i("ARGameActivity", "ARCore session created")
        }
        val configBlock: (Config) -> Unit = { config ->
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            config.focusMode = Config.FocusMode.AUTO
            config.depthMode = Config.DepthMode.AUTOMATIC
            config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        }
        sceneView.configureSession { _, config -> configBlock(config) }
        // Fallback: if session already created by onAttachedToWindow lifecycle auto-detection,
        // configureSession's callback was missed. Apply config immediately.
        sceneView.session?.let { session ->
            val config = session.config
            configBlock(config)
            session.configure(config)
        }
        sceneView.onSessionUpdated = { s, f ->
            lastSession = s
            lastFrame = f
            val tracking = f.camera.trackingState
            if (tracking != lastTrackingState) {
                lastTrackingState = tracking
                AppLog.i("ARGameActivity", "Tracking state: $tracking")
            }
            if (!trackingReady && tracking == TrackingState.TRACKING) {
                trackingReady = true
                AppLog.i("ARGameActivity", "TRACKING READY — starting game content")
                runPendingAction()
                onTrackingReady()
            }
            onArFrame(s, f)
            tickEngine()
        }
        sceneView.onTouchEvent = { event, svHit ->
            if (onTouchPinch(event)) {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    suppressTap = false
                }
            } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                val egg = svHit?.node?.let { eggs[it] }
                when {
                    egg != null -> onEggTapped(egg)
                    svHit?.node != null -> onNodeTapped(svHit.node)
                    else -> onBackgroundTapped(event)
                }
            }
            true
        }

        // SceneView.onAttachedToWindow() auto-detects the lifecycle from the
        // view hierarchy. Setting it explicitly a second time triggers
        // ON_DESTROY + ON_CREATE again, which destroys the SceneView engine
        // and calls arCore.create() twice (duplicate ActivityResultLauncher
        // registration → IllegalStateException). Only set it manually as a
        // fallback if onAttachedToWindow didn't auto-detect it.
        if (sceneView.lifecycle == null) {
            sceneView.lifecycle = lifecycle
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.d("ARGameActivity", "Permission granted, calling onGameCreate")
            // Delay per permettere al display di stabilizzarsi
            handler.postDelayed({
                onGameCreateWithMode()
            }, 200)
        } else {
            android.util.Log.d("ARGameActivity", "Requesting camera permission")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        }

        // Nessun tracking dopo 5s: NON forziamo l'avvio (le uova si ancorano solo
        // con un frame TRACKING, quindi un finto avvio le farebbe spawnare in modo
        // silenzioso e mai comparire). Mostriamo un suggerimento all'utente; il
        // pending action viene rieseguito automaticamente al primo frame TRACKING.
        handler.postDelayed({
            if (!trackingReady && !isFinishing) {
                AppLog.w("ARGameActivity", "Tracking not ready after 5s — waiting for real TRACKING")
                statusText.text = "⚠️ Muovi lentamente il telefono finché il tracking non si attiva…"
            }
        }, 5000)
    }

    override fun onRequestPermissionsResult(
        req: Int, permissions: Array<out String>, results: IntArray
    ) {
        super.onRequestPermissionsResult(req, permissions, results)
        if (req == 1001) onGameCreateWithMode()
    }

    // ── modalità di gioco (scelta + persistenza) ─────────────────

    private val modePrefs
        get() = getSharedPreferences("ar_game_mode", Context.MODE_PRIVATE)

    private fun loadGameMode() {
        val name = modePrefs.getString("mode", ARGameMode.PLANE.name) ?: ARGameMode.PLANE.name
        gameMode = runCatching { ARGameMode.valueOf(name) }.getOrDefault(ARGameMode.PLANE)
    }

    private fun persistGameMode() {
        modePrefs.edit().putString("mode", gameMode.name).apply()
    }

    /**
     * Punto d'ingresso del gioco: se la modalità è selezionabile mostra il
     * dialogo di scelta (con l'ultima scelta pre-selezionata), poi costruisce
     * il contenuto di gioco con [onGameCreate]. Se il dialogo viene chiuso
     * senza conferma si parte comunque con la modalità corrente.
     */
    private fun onGameCreateWithMode() {
        loadGameMode()
        if (showsModeDialog) chooseGameMode { selectRoomAndStart() }
        else selectRoomAndStart()
    }

    /**
     * Sceglie la stanza in cui giocare (se ce ne sono) e poi avvia il contenuto.
     * Se non esistono stanze salvate (o il gioco non usa un'arena su superficie)
     * parte subito: la posizione verrà salvata come stanza al primo piazzamento.
     */
    private fun selectRoomAndStart() {
        if (!usesSurfaceArena) { startContent(); return }
        syncCloudRooms()
        if (ArArenaStore.consumePendingNewRoom(this)) {
            forceNewRoom = true
            currentRoomId = null
            AppLog.i("ARGameActivity", "selectRoomAndStart: nuova stanza richiesta dalla hub → salto il selettore")
            startContent()
            return
        }
        val rooms = ArArenaStore.loadRooms(this)
        if (rooms.isEmpty()) startContent()
        else showRoomPicker(rooms)
    }

    /**
     * Selettore delle stanze AR salvate (la più recente pre-selezionata), con
     * opzioni per crearne una nuova o eliminarne una.
     */
    private fun showRoomPicker(rooms: List<ArRoom>) {
        if (rooms.isEmpty()) { startContent(); return }
        val labels = rooms.map { "🏠 ${it.name}" }.toMutableList()
        val NEW = labels.size
        labels += "➕ Nuova stanza"
        val lastId = ArArenaStore.lastRoom(this)
        var picked = rooms.indexOfFirst { it.roomId == lastId }.coerceAtLeast(0)
        val dialog = AlertDialog.Builder(this)
            .setTitle("🏠 Dove giochi?")
            .setMessage("Il gioco apparirà nella posizione salvata per la stanza scelta.")
            .setSingleChoiceItems(labels.toTypedArray(), picked) { _, which -> picked = which }
            .setPositiveButton("▶ Gioca") { _, _ ->
                forceNewRoom = picked == NEW
                currentRoomId = if (picked == NEW) null else rooms[picked].roomId
                startContent()
            }
            .setNegativeButton("🗑️ Elimina") { _, _ -> showRoomDelete(rooms) }
            .setOnCancelListener { startContent() }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    /** Elimina una stanza (locale + cloud) e riapre il selettore. */
    private fun showRoomDelete(rooms: List<ArRoom>) {
        if (rooms.isEmpty()) { startContent(); return }
        AlertDialog.Builder(this)
            .setTitle("🗑️ Elimina stanza")
            .setItems(rooms.map { it.name }.toTypedArray()) { _, which ->
                val room = rooms[which]
                if (room.roomId == currentRoomId) currentRoomId = null
                ArArenaStore.deleteRoom(this, room.roomId)
                lifecycleScope.launch { ArArenaStore.deleteRoomCloud(room.roomId) }
                showRoomPicker(ArArenaStore.loadRooms(this))
            }
            .setNegativeButton("Annulla") { _, _ -> showRoomPicker(rooms) }
            .setOnCancelListener { showRoomPicker(rooms) }
            .show()
    }

    /** Unisce in locale le stanze presenti sul cloud (fire-and-forget). */
    private fun syncCloudRooms() {
        lifecycleScope.launch {
            val remote = ArArenaStore.pullRooms()
            if (remote.isEmpty()) return@launch
            val localIds = ArArenaStore.loadRooms(this@ARGameActivity).map { it.roomId }.toSet()
            remote.filter { it.roomId !in localIds }
                .forEach { ArArenaStore.saveRoom(this@ARGameActivity, it, setLast = false) }
        }
    }

    /**
     * Avvia il contenuto di gioco SOLO quando ARCore ha un frame TRACKING.
     * Le uova si ancorano allo spazio reale a partire dalla pose della camera,
     * quindi un avvio anticipato farebbe fallire gli spawn (session null /
     * not tracking) e brucerebbe secondi di timer prima che compaia qualcosa.
     * Se il tracking è già attivo parte subito, altrimenti attende il primo
     * frame TRACKING (stesso meccanismo di [whenReady]).
     */
    private fun startContent() {
        whenReady {
            runCatching { onGameCreate() }.onFailure { e ->
                AppLog.e("ARGameActivity", "Error in onGameCreate", e)
            }
        }
    }

    /**
     * Dialogo di scelta della [ARGameMode]: ogni opzione è un bottone toccabile
     * che avvia subito il gioco con la modalità scelta (che viene ricordata).
     * Se si chiude senza toccare nulla si parte comunque con l'ultima scelta salvata.
     */
    protected fun chooseGameMode(onChosen: () -> Unit) {
        val options = arrayOf(
            "🌍 Su pareti, pavimento e soffitto",
            "📐 Su superfici piatte",
            "🕊️ Libero nell'aria"
        )
        val modes = arrayOf(ARGameMode.MESHING, ARGameMode.PLANE, ARGameMode.FREE)
        val dialog = AlertDialog.Builder(this)
            .setTitle("🎮 Dove posiziono il gioco?")
            .setMessage("Tocca l'opzione che preferisci e si parte subito. " +
                "La scelta viene ricordata per le prossime partite.")
            .setItems(options) { _, which ->
                gameMode = modes[which]
                persistGameMode()
                onChosen()
            }
            .setNegativeButton("↩ Riprendi com'era") { _, _ -> onChosen() }
            .setOnCancelListener { onChosen() }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
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
        levelText = TextView(c).apply {
            text = ""; textSize = 12f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 2))
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
        wrap.addView(topRow); wrap.addView(levelText); wrap.addView(statusText)
        hud.addView(wrap)

        zoomText = TextView(c).apply {
            text = "🔍 100%"
            textSize = 11f
            setTextColor(0xCCFFFFFF.toInt())
            setBackgroundColor(0x660D0620.toInt())
            setPadding(UiKit.dp(c, 8), UiKit.dp(c, 4), UiKit.dp(c, 8), UiKit.dp(c, 4))
            visibility = android.view.View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                topMargin = UiKit.dp(c, 52)
                rightMargin = UiKit.dp(c, 12)
            }
        }
        hud.addView(zoomText)
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
     * [right]/[up] = offset laterale/verticale (m).
     *
     * La posizione NON segue l'asse ottico della camera: [forward]/[right]
     * sono proiettati sul piano orizzontale e [up] è verticale in mondo. Così
     * le uova compaiono sempre davanti agli occhi dell'utente alla sua altezza,
     * anche se al momento dello spawn il telefono è inclinato verso il
     * pavimento o il soffitto (tipico durante la scansione iniziale del
     * tracking, quando in precedenza finivano sotto lo schermo e non si
     * vedevano mai).
     */
    protected fun spawnEgg(
        type: Int, forward: Float, right: Float, up: Float, radius: Float = 0.07f
    ): AREgg? {
        val session = lastSession ?: run {
            android.util.Log.w("ARGameActivity", "spawnEgg: session is null")
            AppLog.w("ARGameActivity", "spawnEgg: session is null")
            return null
        }
        val frame = lastFrame ?: run {
            android.util.Log.w("ARGameActivity", "spawnEgg: frame is null")
            AppLog.w("ARGameActivity", "spawnEgg: frame is null")
            return null
        }
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            android.util.Log.w("ARGameActivity", "spawnEgg: not tracking")
            AppLog.w("ARGameActivity", "spawnEgg: not tracking (${frame.camera.trackingState})")
            return null
        }
        val camPose = frame.camera.displayOrientedPose
        val pose = stabilizedPose(camPose, forward, right, up)
        val anchor = runCatching { session.createAnchor(pose) }.getOrElse {
            AppLog.e("ARGameActivity", "spawnEgg: createAnchor failed", it)
            return null
        }
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
        AppLog.i(
            "ARGameActivity",
            "Egg spawned type=$type, scene nodes=${sceneView.childNodes.size}, " +
                "cam=(${camPose.tx()},${camPose.ty()},${camPose.tz()}) " +
                "egg=(${pose.tx()},${pose.ty()},${pose.tz()}) anchor=${anchor.trackingState}"
        )
        onEggSpawned(egg)
        return egg
    }

    /**
     * Crea un'uovo ancorato a una [pose] assoluta nello spazio reale (non relativa
     * alla camera). Usato dai giochi a griglia posizionata su una superficie
     * rilevata: ogni carta/oggetto ha il proprio AR Anchor e resta fermo nel punto
     * reale mentre il giocatore si muove nella stanza.
     */
    protected fun spawnEggAt(pose: Pose, type: Int, radius: Float = 0.07f): AREgg? {
        val an = spawnAnchorAt(pose) ?: return null
        val mat = sceneView.materialLoader.createColorInstance(color = eggColor(type))
        val node = SphereNode(sceneView.engine, radius, materialInstance = mat).apply {
            position = Position(0f, 0f, 0f)
            scale = Scale(1f, 1.4f, 1f)
        }
        an.addChildNode(node)
        val egg = AREgg(an, node, type, phase = Math.random().toFloat() * 6.28f)
        eggs[node] = egg
        AppLog.i("ARGameActivity", "Egg spawned at world pose type=$type, anchor=${an.anchor.trackingState}")
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

    /** Registra un egg creato dall'esterno (es. carte ImageNode) per il tap-to-match. */
    protected fun registerEgg(egg: AREgg) {
        synchronized(eggs) { eggs[egg.node] = egg }
    }

    /** Crea un [AnchorNode] da una pose assoluta SENZA registrarlo in sharedAnchors. */
    protected fun spawnAnchorAt(pose: Pose): AnchorNode? {
        val session = lastSession ?: return null
        val anchor = runCatching { session.createAnchor(pose) }.getOrElse {
            AppLog.e("ARGameActivity", "spawnAnchorAt: createAnchor failed", it)
            return null
        }
        val an = AnchorNode(engine = sceneView.engine, anchor = anchor)
        sceneView.addChildNode(an)
        return an
    }

    // ── shared-anchor arena (per giochi con campo di gioco unico) ──

    /** Ancora in un unico spazio 3D condiviso da tutti i nodi del gioco. */
    private val sharedAnchors = mutableListOf<AnchorNode>()

    /**
     * Crea un [AnchorNode] a partire da una [pose] assoluta, lo aggiunge alla
     * scena e lo registra (verrà distrutto in restart()/onDestroy()). Restituisce
     * null se la sessione non è disponibile o la creazione dell'anchor fallisce.
     */
    protected fun createAnchorNode(pose: Pose): AnchorNode? {
        val session = lastSession ?: return null
        val anchor = runCatching { session.createAnchor(pose) }.getOrElse {
            AppLog.e("ARGameActivity", "createAnchorNode: createAnchor failed", it)
            return null
        }
        val an = AnchorNode(engine = sceneView.engine, anchor = anchor)
        sceneView.addChildNode(an)
        sharedAnchors.add(an)
        return an
    }

    /**
     * Crea un'ancora "arena" unica davanti alla camera (come [spawnEgg] ma senza
     * uovo): tutti i nodi del gioco vanno aggiunti come figli dell'[AnchorNode]
     * restituito, così condividono lo stesso sistema di coordinate locali.
     */
    protected fun spawnAnchor(forward: Float, right: Float, up: Float): AnchorNode? {
        val frame = lastFrame ?: run {
            android.util.Log.w("ARGameActivity", "spawnAnchor: frame is null")
            AppLog.w("ARGameActivity", "spawnAnchor: frame is null")
            return null
        }
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            android.util.Log.w("ARGameActivity", "spawnAnchor: not tracking")
            AppLog.w("ARGameActivity", "spawnAnchor: not tracking (${frame.camera.trackingState})")
            return null
        }
        val camPose = frame.camera.displayOrientedPose
        val pose = stabilizedPose(camPose, forward, right, up)
        val an = createAnchorNode(pose)
        if (an != null) {
            AppLog.i("ARGameActivity", "Arena anchor spawned, scene nodes=${sceneView.childNodes.size}")
        }
        return an
    }

    /**
     * Pose davanti alla camera "stabilizzata": [forward] e [right] sono applicati
     * sul piano orizzontale (proiezione degli assi della camera) e [up] è la
     * verticale di mondo. L'orientamento resta quello della camera. Senza questa
     * proiezione, con il telefono inclinato in giù le uova finivano sotto la
     * cornice dello schermo e non erano mai visibili.
     */
    private fun stabilizedPose(camPose: Pose, forward: Float, right: Float, up: Float): Pose {
        val fwd = camPose.rotateVector(floatArrayOf(0f, 0f, -1f))
        val rh = camPose.rotateVector(floatArrayOf(1f, 0f, 0f))
        var fhX = fwd[0]; var fhZ = fwd[2]
        val fhLen = kotlin.math.sqrt(fhX * fhX + fhZ * fhZ)
        if (fhLen < 1e-4f) { fhX = 0f; fhZ = -1f } else { fhX /= fhLen; fhZ /= fhLen }
        var rhX = rh[0]; var rhZ = rh[2]
        val rhLen = kotlin.math.sqrt(rhX * rhX + rhZ * rhZ)
        if (rhLen < 1e-4f) { rhX = 1f; rhZ = 0f } else { rhX /= rhLen; rhZ /= rhLen }
        val wx = camPose.tx() + right * rhX + forward * fhX
        val wy = camPose.ty() + up
        val wz = camPose.tz() + right * rhZ + forward * fhZ
        return Pose.makeTranslation(wx, wy, wz)
            .compose(Pose.makeRotation(camPose.qx(), camPose.qy(), camPose.qz(), camPose.qw()))
    }

    protected fun eggList(): List<AREgg> = synchronized(eggs) { eggs.values.toList() }
    protected fun aliveCount() = eggs.size
    protected fun isTracking() = trackingReady

    /** Pose della camera corrente (usata dall'audio spaziale / effetti). */
    protected val cameraPose: Pose?
        get() = lastFrame?.camera?.pose

    /**
     * Ancora lanciata al centro dello schermo su un piano rilevato (floor/table),
     * depth-aided hitTest. Restituisce null se nessun piano è stato inquadrato.
     *
     * Nota: in [ARGameMode.MESHING] si preferisce [tryAnchorToMesh], che accetta
     * anche geometria non-planare; [tryAnchorToPlane] è mantenuta come fallback
     * e usata internamente da [tryArenaByMode].
     */
    protected fun tryAnchorToPlane(): AnchorNode? {
        val frame = lastFrame ?: return null
        if (frame.camera.trackingState != TrackingState.TRACKING) return null
        val cx = (sceneView.width / 2f).toFloat()
        val cy = (sceneView.height / 2f).toFloat()
        val hit = frame.hitTest(cx, cy)
        val planeHit = hit.firstOrNull { h ->
            val tr = h.trackable
            tr is Plane && tr.isPoseInPolygon(h.hitPose)
        } ?: return null
        return createAnchorNode(planeHit.hitPose)
    }

    /**
     * Ancora lanciata al centro dello schermo sulla geometria REALE (depth):
     * accetta qualunque punto 3D restituito dall'hitTest (piani, point cloud,
     * mesh di profondità) entro 0.15–5 m. Restituisce null se non c'è
     * geometria inquadrata.
     */
    protected fun tryAnchorToMesh(): AnchorNode? {
        val frame = lastFrame ?: return null
        if (frame.camera.trackingState != TrackingState.TRACKING) return null
        val cx = (sceneView.width / 2f).toFloat()
        val cy = (sceneView.height / 2f).toFloat()
        val hit = frame.hitTest(cx, cy)
        val h = hit.firstOrNull() ?: return null
        val dist = h.distance
        if (dist < 0.15f || dist > 5f) return null
        return createAnchorNode(h.hitPose)
    }

    /**
     * Ancora unica dell'arena secondo la [gameMode] attiva:
     * - [ARGameMode.MESHING] → geometria reale via depth ([tryAnchorToMesh]);
     * - [ARGameMode.PLANE] → piano rilevato ([tryAnchorToPlane]);
     * - [ARGameMode.FREE] → davanti alla camera, sospesa ([spawnAnchor]).
     * [elevation] (m) solleva l'ancora sopra la superficie (per giochi verticali
     * tipo Flappy poggiati su un tavolo); ignorata in [ARGameMode.FREE].
     */
    protected fun tryArenaByMode(elevation: Float = 0f): AnchorNode? {
        // Prima chiamata: prova a recuperare la posizione salvata per la stanza
        // scelta, così il gioco riappare nello stesso punto senza riscansionare.
        if (!cloudRestoreAttempted) {
            cloudRestoreAttempted = true
            if (gameMode != ARGameMode.FREE && !forceNewRoom) {
                val savedId = savedCloudAnchorId()
                if (!savedId.isNullOrBlank()) {
                    cloudRestoring = true
                    statusText.text = "🔄 Recupero la posizione salvata…"
                    AppLog.i("ARGameActivity", "tryArenaByMode: restore cloud anchor ${savedId.take(8)}…")
                    startCloudRestore(savedId) { restored ->
                        cloudRestoring = false
                        if (restored != null) {
                            restoredArena = restored
                            statusText.text = "✅ Gioco ritrovato nello stesso punto!"
                            AppLog.i("ARGameActivity", "tryArenaByMode: restore OK")
                        } else {
                            // La posizione salvata non è qui: al prossimo
                            // piazzamento la nuova posizione diventa una stanza
                            // nuova (quella vecchia resta intatta).
                            currentRoomId = null
                            statusText.text = "⚠️ Posizione salvata non trovata: inquadra una superficie (salverò una nuova stanza)."
                            statusText.setTextColor(Color.parseColor(UiKit.ACCENT))
                            AppLog.w("ARGameActivity", "tryArenaByMode: restore failed → nuova stanza al prossimo piazzamento")
                        }
                    }
                    return null
                }
            }
        }
        // Finché il ripristino è in corso non piazziamo niente (il loop di
        // retry richiama questo metodo finché la risoluzione non finisce).
        if (cloudRestoring) return null
        restoredArena?.let { arena ->
            restoredArena = null
            return arena
        }
        val base = when (gameMode) {
            ARGameMode.MESHING -> tryAnchorToMesh()
            ARGameMode.PLANE -> tryAnchorToPlane()
            ARGameMode.FREE -> spawnAnchor(1.05f, 0f, 0f)
        } ?: return null
        if (elevation == 0f || gameMode == ARGameMode.FREE) return base
        val raisedPose = base.anchor.pose.compose(
            Pose(floatArrayOf(0f, elevation, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        )
        sharedAnchors.remove(base)
        base.destroy()
        return createAnchorNode(raisedPose)
    }

    // ── Cloud Anchor: host + resolve ─────────────────────────────

    private val cloudTtlDays
        get() = if (BuildConfig.ARCORE_API_KEY.isBlank()) ARENA_TTL_DAYS_KEYLESS else ARENA_TTL_DAYS_APIKEY

    /**
     * Salva la posizione dell'arena sul cloud ARCore (non bloccante): alla
     * prossima riapertura dell'app il gioco può essere ritrovato nello stesso
     * punto senza dover scansionare di nuovo. In modalità libera (gioco sospeso
     * davanti alla camera, non ancorato a una superficie) non viene salvato.
     *
     * Funziona sia con API key sia in modalità keyless (OAuth): se la key è
     * assente ARCore usa l'OAuth client ID (package + SHA-1) e la Cloud Anchor
     * resta valida [ARENA_TTL_DAYS_KEYLESS] giorni.
     */
    protected fun persistArena(a: AnchorNode) {
        if (gameMode == ARGameMode.FREE) {
            AppLog.i("ARGameActivity", "persistArena: skip (FREE mode)")
            return
        }
        if (hostingCloudAnchor != null) {
            AppLog.i("ARGameActivity", "persistArena: host already in progress")
            return
        }
        val session = lastSession ?: run {
            AppLog.w("ARGameActivity", "persistArena: no session, cannot host")
            return
        }
        val mode = if (BuildConfig.ARCORE_API_KEY.isBlank()) "keyless(OAuth)" else "api-key"
        AppLog.i("ARGameActivity", "persistArena: hosting arena (${mode}, ttl=${cloudTtlDays}d, room=${currentRoomId ?: "new"})")
        val hosted = runCatching { session.hostCloudAnchorWithTtl(a.anchor, cloudTtlDays) }.getOrElse {
            AppLog.w("ARGameActivity", "persistArena: hostCloudAnchor failed: ${it.message}")
            return
        }
        hostingCloudAnchor = hosted
        pollHostCloud()
    }

    private fun pollHostCloud() {
        val hosted = hostingCloudAnchor ?: return
        when (val st = hosted.cloudAnchorState) {
            Anchor.CloudAnchorState.SUCCESS -> {
                hostingCloudAnchor = null
                val cloudId = hosted.cloudAnchorId
                val existing = currentRoomId?.let { ArArenaStore.loadRoom(this, it) }
                val room = existing?.copy(cloudAnchorId = cloudId)
                    ?: ArArenaStore.createRoom(ArArenaStore.nextRoomName(this), cloudId)
                currentRoomId = room.roomId
                forceNewRoom = false
                ArArenaStore.saveRoom(this, room)
                AppLog.i("ARGameActivity", "pollHostCloud: SUCCESS → stanza '${room.name}' salvata in locale (cloud ${cloudId.take(8)}…)")
                lifecycleScope.launch {
                    val pushed = ArArenaStore.pushRoom(room)
                    AppLog.i("ARGameActivity", "pollHostCloud: pushRoom → ${if (pushed) "OK (Firestore)" else "NON riuscito (silenzioso)"}")
                }
            }
            Anchor.CloudAnchorState.NONE -> handler.postDelayed({ pollHostCloud() }, 250)
            Anchor.CloudAnchorState.ERROR_NOT_AUTHORIZED,
            Anchor.CloudAnchorState.ERROR_RESOURCE_EXHAUSTED -> {
                hostingCloudAnchor = null
                AppLog.w("ARGameActivity", "persistArena: cloud anchor not authorized/resource exhausted (${st.name})")
            }
            else -> {
                hostingCloudAnchor = null
                AppLog.w("ARGameActivity", "persistArena: host state ${st.name}")
            }
        }
    }

    /**
     * Ultimo tentativo di salvataggio all'uscita: se l'hosting della Cloud
     * Anchor ha già raggiunto [Anchor.CloudAnchorState.SUCCESS] ma il poll
     * asincrono è stato interrotto (es. back rapido), salva comunque la stanza.
     */
    private fun flushPendingRoomSave() {
        val hosted = hostingCloudAnchor ?: return
        hostingCloudAnchor = null
        if (hosted.cloudAnchorState != Anchor.CloudAnchorState.SUCCESS) {
            AppLog.w("ARGameActivity", "flushPendingRoomSave: hosting non completato (${hosted.cloudAnchorState.name})")
            return
        }
        val cloudId = hosted.cloudAnchorId
        val existing = currentRoomId?.let { ArArenaStore.loadRoom(this, it) }
        val room = existing?.copy(cloudAnchorId = cloudId)
            ?: ArArenaStore.createRoom(ArArenaStore.nextRoomName(this), cloudId)
        currentRoomId = room.roomId
        ArArenaStore.saveRoom(this, room)
        AppLog.i("ARGameActivity", "flushPendingRoomSave: stanza '${room.name}' salvata all'uscita (cloud ${cloudId.take(8)}…)")
        lifecycleScope.launch { ArArenaStore.pushRoom(room) }
    }

    /** ID della Cloud Anchor da ripristinare: la stanza attiva o, in mancanza, l'ultima usata. */
    private fun savedCloudAnchorId(): String? {
        val roomId = currentRoomId ?: ArArenaStore.lastRoom(this) ?: return null
        return ArArenaStore.loadRoom(this, roomId)?.cloudAnchorId
    }

    /** Prova a risolvere [anchorId]; [onDone] riceve l'ancora o null (fallito). */
    private fun startCloudRestore(anchorId: String, onDone: (AnchorNode?) -> Unit) {
        val session = lastSession ?: sceneView.session
        if (session == null) { onDone(null); return }
        val resolved = runCatching { session.resolveCloudAnchor(anchorId) }.getOrElse {
            AppLog.w("ARGameActivity", "startCloudRestore: resolveCloudAnchor failed: ${it.message}")
            onDone(null)
            return
        }
        resolvingCloudAnchor = resolved
        val start = SystemClock.uptimeMillis()
        fun poll() {
            val a = resolvingCloudAnchor ?: return
            when {
                a.cloudAnchorState == Anchor.CloudAnchorState.SUCCESS -> {
                    resolvingCloudAnchor = null
                    val node = anchorNodeForResolved(a)
                    AppLog.i("ARGameActivity", "Arena position restored from cloud")
                    onDone(node)
                }
                a.cloudAnchorState.name.startsWith("ERROR") ||
                    SystemClock.uptimeMillis() - start > CLOUD_RESTORE_TIMEOUT_MS -> {
                    resolvingCloudAnchor = null
                    AppLog.w("ARGameActivity", "startCloudRestore: ${a.cloudAnchorState.name} → fallback to scan")
                    onDone(null)
                }
                else -> handler.postDelayed({ poll() }, 250)
            }
        }
        poll()
    }

    /** Avvolge un anchor (risolto) in un [AnchorNode] registrato nella scena. */
    private fun anchorNodeForResolved(anchor: Anchor): AnchorNode? {
        val an = AnchorNode(engine = sceneView.engine, anchor = anchor)
        sceneView.addChildNode(an)
        sharedAnchors.add(an)
        return an
    }

    /**
     * Piazza l'arena secondo la modalità attiva, ritentando finché non trova una
     * posizione valida (piano/mesh davanti alla camera). Mostra suggerimenti nello
     * statusText e chiama [onReady] con l'ancora appena creata.
     */
    protected fun placeArena(onReady: (AnchorNode) -> Unit) {
        val a = tryArenaByMode()
        if (a == null) {
            if (!cloudRestoring) {
                statusText.text = if (gameMode == ARGameMode.FREE)
                    "⚠️ Inquadra la stanza davanti a te…"
                else
                    "⚠️ Nessuna superficie rilevata: muovi il telefono e inquadra un tavolo o il pavimento."
            }
            if (running) postDelayed(500) { placeArena(onReady) }
            return
        }
        onReady(a)
        persistArena(a)
    }

    /**
     * Angolo yaw (radianti) per far fronteggiare la camera a un anchor: la
     * direzione locale +Z dell'anchor viene allineata alla proiezione orizzontale
     * della camera. Usato dai giochi a lavagna verticale appoggiata al piano.
     */
    protected fun yawToFaceCamera(anchor: AnchorNode): Float {
        val cam = lastFrame?.camera?.pose ?: return 0f
        val local = anchor.anchor.pose.inverse().compose(cam)
        return atan2(local.tx(), local.tz())
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
            synchronized(fx) {
                fx += FxParticle(part, Float3(cos(ang) * speed, up + 0.3f, -sin(ang) * speed), 0.5f, 0.5f)
            }
        }
    }

    // ── rottura uovo → frittata ────────────────────────────────────

    /** Disco piatto colorato (albume della frittata). */
    private fun frittataDisc(color: Int, radius: Float, height: Float): CylinderNode {
        val mat = sceneView.materialLoader.createColorInstance(color = color)
        return CylinderNode(
            sceneView.engine,
            radius = radius,
            height = height,
            center = Position(0f, 0f, 0f),
            sideCount = 28,
            materialInstance = mat
        )
    }

    /** Cupola schiacciata (tuorlo / bozzi della frittata). */
    private fun frittataDome(color: Int, radius: Float): SphereNode {
        val mat = sceneView.materialLoader.createColorInstance(color = color)
        return SphereNode(sceneView.engine, radius, materialInstance = mat).apply {
            scale = Scale(1f, 0.22f, 1f)
        }
    }

    /**
     * Un uovo si rompe e si trasforma in una frittata: spruzzo di tuorlo e
     * albume (particelle) + un disco piatto con tuorlo a cupola che "piomba"
     * a terra. [parent]/[local] = dove ancorare la frittata (spazio locale),
     * [world] = posizione nel mondo per le particelle.
     */
    protected fun eggBreak(
        parent: Node?,
        local: Position,
        world: Float3,
        radius: Float = 0.085f,
        big: Boolean = false
    ) {
        val n = if (big) 14 else 9
        repeat(n) { it ->
            val part = eggNode(if (it % 2 == 0) 0xFFFFD700.toInt() else 0xFFFFF3D6.toInt(), UiKit.dp(this, 2).toFloat())
            part.position = Position(world.x, world.y + 0.03f, world.z)
            sceneView.addChildNode(part)
            val ang = it * 0.7f + Math.random().toFloat() * 0.5f
            val speed = 0.1f + Math.random().toFloat() * (if (big) 0.32f else 0.18f)
            synchronized(fx) {
                fx += FxParticle(
                    part,
                    Float3(cos(ang) * speed, 0.3f + Math.random().toFloat() * 0.2f, sin(ang) * speed),
                    0.55f, 0.55f, gravity = 1.8f
                )
            }
        }
        spatialAudio.oneShot(if (big) 75f else 120f + Math.random().toFloat() * 40f, 220, decay = true, gain = 0.5f)

        val p = parent ?: return
        val w = radius * (if (big) 1.6f else 1.1f)
        val white = frittataDisc(0xFFFFF3D6.toInt(), w, 0.02f)
        val yolk = frittataDome(0xFFFFC93C.toInt(), w * 0.55f)
        yolk.position = Position(0f, 0.022f, 0f)
        val bumps = (0..1).map {
            frittataDome(0xFFFFA726.toInt(), w * 0.13f).apply {
                val a = Math.random().toFloat() * 6.28f
                position = Position(cos(a) * w * 0.35f, 0.038f, sin(a) * w * 0.35f)
            }
        }
        val root = Node(sceneView.engine).apply {
            rotation = Rotation(0f, Math.random().toFloat() * 360f, 0f)
            addChildNode(white)
            addChildNode(yolk)
            bumps.forEach { addChildNode(it) }
        }
        p.addChildNode(root)
        val landY = 0.02f
        repeat(5) { st ->
            postDelayed(st * 40L) {
                if (isDestroyed) return@postDelayed
                val f = (st + 1) / 5f
                root.position = Position(local.x, landY + 0.08f * (1f - f), local.z)
                root.scale = Scale(f, f, f)
            }
        }
    }

    // ── celebrazioni (vittoria/sconfitta/pari) ─────────────────────

    /** Centro del campo di gioco per gli effetti: arena condivisa se c'è,
     *  altrimenti un punto davanti alla camera. */
    protected fun gameCenter(): Float3 {
        sharedAnchors.lastOrNull()?.let { return it.worldPosition }
        val cam = cameraPose ?: return Float3(0f, 0.3f, 0f)
        val f = cam.zDirection
        return Float3(cam.tx() + f.x * 0.9f, cam.ty() + 0.2f, cam.tz() + f.z * 0.9f)
    }

    /** Vittoria stile mondiale: coriandoli + fuochi d'artificio + fanfara. */
    protected fun celebrateWin(center: Float3? = null, accentColors: IntArray = CONFETTI_COLORS) {
        val c = center ?: gameCenter()
        confettiRain(c, 40, accentColors)
        fireworks(c, accentColors, 6, 260L)
        playVictoryFanfare()
    }

    /** Sconfitta: trombone "sad" + piccoli scoppi spenti (comico). */
    protected fun celebrateLose(center: Float3? = null) {
        val c = center ?: gameCenter()
        playSadTrombone()
        postDelayed(160) { burst(Float3(c.x, c.y + 0.1f, c.z), 0xFF9E9E9E.toInt(), 10) }
        postDelayed(560) { burst(Float3(c.x, c.y + 0.15f, c.z), 0xFF607D8B.toInt(), 8) }
    }

    /** Pareggio: doppio tono gentile + piccolo scoppio dorato. */
    protected fun celebrateDraw(center: Float3? = null) {
        val c = center ?: gameCenter()
        playDrawTone()
        postDelayed(240) { burst(c, 0xFFFFD700.toInt(), 8) }
    }

    /** Pioggia di coriandoli che scende lenta sopra il campo (gravità ridotta). */
    protected fun confettiRain(center: Float3, count: Int = 36, colors: IntArray = CONFETTI_COLORS) {
        synchronized(fx) {
            repeat(count) { k ->
                val part = eggNode(colors[k % colors.size], UiKit.dp(this, 3).toFloat())
                part.position = Position(
                    center.x + (Math.random().toFloat() - 0.5f) * 1.6f,
                    center.y + 0.9f + Math.random().toFloat() * 1.0f,
                    center.z + (Math.random().toFloat() - 0.5f) * 1.0f
                )
                sceneView.addChildNode(part)
                fx += FxParticle(
                    part,
                    Float3(
                        (Math.random().toFloat() - 0.5f) * 0.35f,
                        -0.05f - Math.random().toFloat() * 0.25f,
                        (Math.random().toFloat() - 0.5f) * 0.35f
                    ),
                    1.6f, 1.6f, gravity = 0.4f
                )
            }
        }
    }

    /** Batterie di fuochi d'artificio colorati sopra il campo. */
    protected fun fireworks(center: Float3, colors: IntArray, volleys: Int = 6, delay: Long = 300L) {
        var d = 0L
        repeat(volleys) { v ->
            val d0 = d
            postDelayed(d0) {
                val pos = Float3(
                    center.x + (Math.random().toFloat() - 0.5f) * 1.3f,
                    center.y + 0.7f + Math.random().toFloat() * 1.0f,
                    center.z + (Math.random().toFloat() - 0.5f) * 0.9f
                )
                burst(pos, colors[v % colors.size], 18)
                burst(pos, 0xFFFFFFFF.toInt(), 6)
                spatialAudio.oneShot(300f + Math.random().toFloat() * 520f, 200, decay = true, gain = 0.4f)
            }
            d += delay
        }
    }

    /** Fanfara di vittoria ascendente (stile podio/campionato). */
    protected fun playVictoryFanfare() {
        playNotes(listOf(
            659f to 140L, 784f to 140L, 1047f to 140L, 1319f to 340L,
            1047f to 170L, 784f to 170L, 1047f to 520L,
            659f to 140L, 784f to 140L, 1047f to 140L, 1319f to 300L,
            1568f to 240L, 1319f to 240L, 1047f to 640L
        ))
    }

    /** "Trombone triste" discendente (comico, per la sconfitta). */
    protected fun playSadTrombone() {
        playNotes(listOf(
            196f to 320L, 175f to 320L, 155f to 320L, 131f to 640L
        ))
    }

    /** Doppio tono gentile per il pareggio. */
    protected fun playDrawTone() {
        playNotes(listOf(392f to 240L, 523f to 360L), 60L)
    }

    /** Suona una sequenza di note (freq, durata) una dopo l'altra. */
    private fun playNotes(notes: List<Pair<Float, Long>>, startDelay: Long = 0L) {
        var d = startDelay
        notes.forEach { (freq, ms) ->
            val d0 = d
            postDelayed(d0) { spatialAudio.oneShot(freq, ms.toInt(), decay = true, gain = 0.35f) }
            d += ms
        }
    }

    private fun tickEngine() {
        val now = SystemClock.elapsedRealtime()
        val dt = ((now - fxLast) / 1000f).coerceIn(0f, 0.05f)
        fxLast = now
        if (dt > 0f) {
            synchronized(fx) {
                if (fx.isNotEmpty()) {
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
                        p.vel = Float3(p.vel.x, p.vel.y - p.gravity * dt, p.vel.z)
                        val s = (p.life / p.maxLife) * 0.6f
                        p.node.scale = Scale(s, s, s)
                    }
                }
            }
        }
        spatialAudio.tick(cameraPose)
        syncContentScale()
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

    // ── pinch-zoom ─────────────────────────────────────────────

    private fun pinchDistance(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val dx = e.getX(0) - e.getX(1)
        val dy = e.getY(0) - e.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Applica la scala corrente a tutte le ancore del gioco (ogni frame). */
    private fun syncContentScale() {
        val sc = Scale(contentScale, contentScale, contentScale)
        sharedAnchors.forEach { it.scale = sc }
        synchronized(eggs) {
            eggs.values.forEach { it.anchorNode.scale = sc }
        }
    }

    /** Scala tutto il contenuto AR di [scale] (0.4–2.5): grandezza E distanze. */
    protected fun applyContentScale(scale: Float) {
        contentScale = scale.coerceIn(0.4f, 2.5f)
        syncContentScale()
        zoomText?.let {
            it.visibility = if (contentScale != 1f) android.view.View.VISIBLE else android.view.View.GONE
            it.text = "🔍 ${(contentScale * 100).toInt()}%"
        }
    }

    /** Riporta lo zoom a 1×. */
    protected fun resetContentScale() {
        contentScale = 1f
        syncContentScale()
        zoomText?.let {
            it.visibility = android.view.View.GONE
            it.text = "🔍 100%"
        }
    }

    /** Gestione del tocco a due dita dentro il callback di tocco della scena. */
    private fun onTouchPinch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    pinchGesture = true
                    pinchStartDist = pinchDistance(event)
                    pinchStartScale = contentScale
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinchGesture && event.pointerCount >= 2) {
                    val d = pinchDistance(event)
                    if (pinchStartDist > 0f && d > 0f) {
                        applyContentScale(pinchStartScale * d / pinchStartDist)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (pinchGesture) {
                    pinchGesture = false
                    suppressTap = true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pinchGesture = false
                suppressTap = true
            }
        }
        return pinchGesture || suppressTap
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
     * eseguita subito; altrimenti viene ritentata finché un frame TRACKING non
     * arriva davvero (non viene consumata da un timeout).
     */
    protected fun whenReady(action: () -> Unit) {
        android.util.Log.d("ARGameActivity", "whenReady called, trackingReady=$trackingReady")
        if (trackingReady && lastFrame?.camera?.trackingState == TrackingState.TRACKING) {
            action()
        } else {
            pendingAction = action
            handler.removeCallbacks(retryPending)
            handler.postDelayed(retryPending, 250)
        }
    }

    private fun runPendingAction() {
        val action = pendingAction
        pendingAction = null
        action?.let {
            runCatching { it() }.onFailure { e ->
                AppLog.e("ARGameActivity", "Error executing pending action", e)
            }
        }
    }

    private val retryPending = object : Runnable {
        override fun run() {
            if (isFinishing) return
            if (pendingAction != null && lastFrame?.camera?.trackingState == TrackingState.TRACKING) {
                runPendingAction()
            } else if (pendingAction != null) {
                handler.postDelayed(this, 500)
            }
        }
    }

    protected fun startGame() { running = true }
    protected fun stopGame() { running = false }

    protected fun postDelayed(delay: Long, r: Runnable) = handler.postDelayed(r, delay)
    protected fun postDelayed(delay: Long, r: () -> Unit): Runnable {
        val runnable = Runnable(r); handler.postDelayed(runnable, delay); return runnable
    }

    protected fun removeCallback(r: Runnable?) = r?.let { handler.removeCallbacks(it) }

    // ── end of game ──────────────────────────────────────────────

    /** Aggiorna l'indicatore di livello/HUD con livello corrente e obiettivo. */
    protected fun updateLevelHud(gameId: String) {
        if (::levelText.isInitialized) {
            levelText.text = "⭐ Lv ${MiniGameManager.getLevel(this, gameId)}  •  🎯 ${MiniGameManager.getLevelTarget(this, gameId)}  •  ${MiniGameManager.getTotalStars(this, gameId)} stelle"
        }
    }

    protected fun finishGame(
        reward: Int,
        label: String,
        isWin: Boolean,
        gameId: String,
        celebrate: Boolean = true,
        isDraw: Boolean = false,
        accentColors: IntArray? = null,
        score: Int = 0,
        giftEggRarityId: String? = null
    ) {
        running = false
        removeInputCapture()
        if (celebrate) {
            when {
                isWin -> celebrateWin(gameCenter(), accentColors ?: CONFETTI_COLORS)
                isDraw -> celebrateDraw(gameCenter())
                else -> celebrateLose(gameCenter())
            }
        }
        val result = try {
            MiniGameManager.completePlay(
                this, gameId, score,
                mvc = reward,
                xp = (reward / 4).coerceAtLeast(1),
                label = label, isWin = isWin, giftEggRarityId = giftEggRarityId
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            null
        }
        updateLevelHud(gameId)
        statusText.text = "🎮 Fine! +${result?.mvc ?: reward} MVC"
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
        pendingAction = null
        handler.removeCallbacks(retryPending)
        synchronized(eggs) {
            eggs.values.forEach { it.anchorNode.destroy() }
            eggs.clear()
        }
        synchronized(fx) {
            fx.forEach { removeNode(it.node) }
            fx.clear()
        }
        spatialAudio.release()
        sharedAnchors.forEach { it.destroy() }
        sharedAnchors.clear()
        resetContentScale()
        running = false
        startContent()
    }

    override fun onDestroy() {
        android.util.Log.d("ARGameActivity", "onDestroy called")
        AppLog.i("ARGameActivity", "onDestroy begin (eggs=${eggs.size}, fx=${fx.size}, anchors=${sharedAnchors.size})")
        running = false
        pendingAction = null
        handler.removeCallbacks(retryPending)
        handler.removeCallbacksAndMessages(null)
        // Cleanup uova SENZA anchor.detach(): la sessione viene chiusa subito
        // dopo (arCore.destroy) e rilascia tutti gli anchor; il detach individuale
        // (dentro AnchorNode.destroy) può essere il trigger del crash nativo su
        // back. Rimuoviamo solo i nodi dalla scena e lasciamo l'engine destroy
        // liberare le entità Filament.
        synchronized(eggs) {
            eggs.values.forEach { egg ->
                runCatching { egg.anchorNode.parent?.removeChildNode(egg.anchorNode) }
            }
            eggs.clear()
        }
        AppLog.i("ARGameActivity", "eggs cleaned")
        synchronized(fx) {
            fx.forEach { runCatching { removeNode(it.node) } }
            fx.clear()
        }
        AppLog.i("ARGameActivity", "fx cleaned")
        runCatching { spatialAudio.release() }
        AppLog.i("ARGameActivity", "spatialAudio released")
        sharedAnchors.forEach { runCatching { it.parent?.removeChildNode(it) } }
        sharedAnchors.clear()
        AppLog.i("ARGameActivity", "shared anchors cleaned")
        // Se l'hosting della Cloud Anchor era ancora in corso, prova un ultimo
        // check sincrono PRIMA di chiudere la sessione: se nel frattempo è
        // arrivato a SUCCESS, salva la stanza (esci subito = niente perdita).
        flushPendingRoomSave()
        // FIX crash nativo su back: la libreria chiude la sessione ARCore su un
        // thread background (destroyArCore -> session.close()) IN CONCORRENZA con
        // il destroy dell'engine Filament/EGL sul main thread -> SIGSEGV.
        // Chiudiamo la sessione QUI, in modo sincrono e PRIMA di qualunque teardown
        // dell'engine; la destroyArCore() della libreria troverà poi session==null
        // (guardia synchronized in ARCore.destroy) e sarà un no-op: niente race.
        runCatching { sceneView.arCore.destroy() }
        AppLog.i("ARGameActivity", "session closed (arCore.destroy)")
        // Teardown esplicito dello SceneView PRIMA di super.onDestroy(): il destroy
        // di libreria (in onDetachedFromWindow, dopo onDestroy) chiude la sessione
        // ARCore in background in gara con il destroy dell'engine -> crash nativo su
        // back. Destroy una volta sola, qui, rende il detach successivo un no-op.
        runCatching { sceneView.destroy() }
        AppLog.i("ARGameActivity", "sceneView destroyed")
        super.onDestroy()
        // Marker critico: se il processo muore di crash nativo durante/giusto dopo
        // il teardown, questo log NON apparirà -> il log esportato ci dice esattamente
        // dove il processo è morto (prima di onDestroy, durante il teardown, o mai).
        AppLog.i("ARGameActivity", "onDestroy end — graceful exit")
        AppLog.flush()
    }

    override fun onPause() {
        super.onPause()
        android.util.Log.d("ARGameActivity", "onPause called, running=$running")
        AppLog.i("ARGameActivity", "onPause called, running=$running")
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("ARGameActivity", "onResume called")
        AppLog.i("ARGameActivity", "onResume called")
    }

    override fun onStop() {
        super.onStop()
        AppLog.i("ARGameActivity", "onStop called")
    }
}

/** Estensione per estrarre un Float casuale da un range chiuso (non presente in stdlib). */
fun ClosedRange<Float>.random(): Float =
    start + Math.random().toFloat() * (endInclusive - start)
