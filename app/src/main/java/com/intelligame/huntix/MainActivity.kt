package com.intelligame.huntix

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.intelligame.huntix.databinding.ActivityMainBinding
import com.intelligame.huntix.model.EggObject
import com.intelligame.huntix.model.GamePhase
import com.intelligame.huntix.model.PlayState
import com.intelligame.huntix.model.SafeObject
import com.intelligame.huntix.manager.ArSceneManager
import com.intelligame.huntix.manager.EggPlacementManager
import com.intelligame.huntix.manager.SafeManager
import com.intelligame.huntix.viewmodel.IndoorGameViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * MainActivity v13 â€” Refactored with IndoorGameViewModel
 */
class MainActivity : AppCompatActivity() {

    companion object {
        internal const val CAMERA_PERMISSION_CODE = 100
        internal val ADMOB_BANNER_ID   get() = BuildConfig.ADMOB_BANNER_ID
        internal val ADMOB_REWARDED_ID get() = BuildConfig.ADMOB_REWARDED_ID

        val EGG_COLORS = listOf(
            0xFFFF3366.toInt(), 0xFFFFCC00.toInt(), 0xFF7C3AED.toInt(),
            0xFF00FF88.toInt(), 0xFFFF6B35.toInt(), 0xFF00E5FF.toInt()
        )
        val KEY_COLORS = listOf(
            0xFFFF6EC7.toInt(), 0xFFFFE566.toInt(), 0xFFA855F7.toInt(),
            0xFF00E5FF.toInt(), 0xFFFFAA77.toInt(), 0xFF66CCEE.toInt()
        )
        val EGG_LABELS = listOf("\uD83E\uDDE1","\uD83D\uDC9B","\uD83D\uDC9C","\uD83D\uDC9A","\uD83E\uDDE1","\uD83D\uDC99")
        val TRAP_COLOR = 0xFFFF3366.toInt()
    }

    // ── ViewModel ────────────────────────────────────────────────
    internal val viewModel: IndoorGameViewModel by viewModels()

    // ── Managers (extracted from God Class) ──────────────────────
    lateinit var arSceneManager: ArSceneManager
    lateinit var eggPlacementManager: EggPlacementManager
    lateinit var safeManager: SafeManager

    // ── Delegation properties — Activity reads/writes ViewModel state ──
    var gamePhase: GamePhase
        get() = viewModel.uiState.value.gamePhase
        set(v) { viewModel.setGamePhase(v) }
    internal var playState: PlayState
        get() = viewModel.uiState.value.playState
        set(v) { viewModel.setPlayState(v) }
    var currentEggIdx: Int
        get() = viewModel.uiState.value.currentEggIdx
        set(v) { viewModel.setCurrentEggIdx(v) }
    internal var keyInPocket: Boolean
        get() = viewModel.uiState.value.keyInPocket
        set(v) { viewModel.setKeyInPocket(v) }
    var realEggsCaught: Int
        get() = viewModel.uiState.value.realEggsCaught
        set(v) { viewModel.setRealEggsCaught(v) }
    internal var activePlayers: MutableList<String>
        get() = viewModel.uiState.value.activePlayers.toMutableList()
        set(v) { viewModel.setActivePlayers(v) }
    internal var currentPlayer: String
        get() = viewModel.uiState.value.currentPlayer
        set(v) { viewModel.setCurrentPlayer(v) }

    internal var currentPlayerIdx: Int = 0
    internal var eggOwners: MutableList<String>
        get() = viewModel.uiState.value.eggOwners.toMutableList()
        set(v) { viewModel.setEggOwners(v) }
    internal var isMenuOpen: Boolean
        get() = viewModel.uiState.value.isMenuOpen
        set(v) { viewModel.setMenuOpen(v) }
    internal var huntStartMs: Long
        get() = viewModel.uiState.value.huntStartMs
        set(v) { viewModel.setHuntStartMs(v) }
    var eggStartMs: Long
        get() = viewModel.uiState.value.eggStartMs
        set(v) { viewModel.setEggStartMs(v) }
    internal var penaltyAccumMs: Long
        get() = viewModel.uiState.value.penaltyAccumMs
        set(v) { viewModel.setPenaltyAccumMs(v) }
    internal var riddles: MutableList<String>
        get() = viewModel.riddles.toMutableList()
        set(v) { viewModel.updateRiddles(v) }
    internal var turnMode: String
        get() = viewModel.turnMode
        set(v) { viewModel.turnMode = v }
    internal var eggSetupMode: String
        get() = viewModel.eggSetupMode
        set(v) { viewModel.eggSetupMode = v }
    private var autoEggCount: Int
        get() = viewModel.autoEggCount
        set(v) { viewModel.autoEggCount = v }
    private var trapEggCount: Int
        get() = viewModel.trapEggCount
        set(v) { viewModel.trapEggCount = v }
    internal var penaltySecs: Int
        get() = viewModel.penaltySecs
        set(v) { viewModel.penaltySecs = v }
    var nextEggIsTrap: Boolean
        get() = viewModel.nextEggIsTrap
        set(v) { viewModel.nextEggIsTrap = v }
    var nextEggShape: String
        get() = viewModel.nextEggShape
        set(v) { viewModel.nextEggShape = v }
    internal var selectedSafeType: String
        get() = viewModel.selectedSafeType
        set(v) { viewModel.selectedSafeType = v }
    private var arMode: String
        get() = viewModel.arMode
        set(v) { viewModel.arMode = v }
    internal val isMultiplayer: Boolean
        get() = viewModel.isMultiplayer
    private var isMultiplayerHost: Boolean = false  // kept locally
    internal val isIndoorMp: Boolean
        get() = viewModel.isIndoorMp
    internal val indoorRoomCode: String
        get() = viewModel.indoorRoomCode
    internal val indoorIsHost: Boolean
        get() = viewModel.indoorIsHost
    internal val indoorCurrentPlayer: String
        get() = viewModel.indoorCurrentPlayer
    internal var hintCooldownUntilMs: Long
        get() = viewModel.uiState.value.hintCooldownUntilMs
        set(v) { viewModel.setHintCooldown(v) }
    internal var isRestoreMode: Boolean
        get() = viewModel.isRestoreMode
        set(v) { viewModel.setIsRestoreMode(v) }
    private var restoreSlotId: String
        get() = viewModel.restoreSlotId
        set(v) { viewModel.setRestoreSlotId(v) }
    internal var mpRoomCode: String = ""

    // ── View / AR state (rimane nell'Activity, NON nel ViewModel) ──
    lateinit var binding: ActivityMainBinding
    lateinit var dm: GameDataManager

    val eggs      = mutableListOf<EggObject>()
    val eggTimesMs = mutableListOf<Long>()
    var safeObject: SafeObject? = null
    var selectedEgg: EggObject? = null
    private var rewardPendingExit = false

    var planeDetected = false
    var lastArFrame: Frame? = null
    var frameCount = 0
    var trackingLostFrames = 0
    var lastTrackingHintMs = 0L

    // Cloud Anchors (indoor MP)
    var pendingCloudRestore = false
    var pendingRoomSnapshot: IndoorSessionManager.RoomSnapshot? = null
    private var indoorEggsListener: com.google.firebase.database.ValueEventListener? = null

    // Room Scan
    val roomScanManager    = RoomScanManager()
    lateinit var localAnchorStore: LocalAnchorStore
    var localAnchorSessionId = ""
    var localAnchors = mutableListOf<LocalAnchorStore.LocalAnchor>()
    var loadedSession: GameDataManager.SavedSession? = null

    // MP
    var mpPlayerId         = ""
    var mpPlayerName       = ""
    lateinit var mpManager: MultiplayerManager
    var indoorPlayerUid     = ""

    @Volatile var isActive = false

    // Swipe
    var swipeStartX = 0f; var swipeStartY = 0f
    var swipeStartTime = 0L; var throwInProgress = false
    var basketSizePx = 0f
    var rewardedAd: RewardedAd? = null
    val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Room scan counters (used by ArSceneManager)
    var localSaveCount = 0

    internal val pickRiddlesInGame = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        viewModel.loadRiddlesFromUri(contentResolver, uri)
    }

    // â”€â”€â”€ Lifecycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dm = GameDataManager.get(this)
        localAnchorStore = LocalAnchorStore.get(this)
        arSceneManager = ArSceneManager(this)
        eggPlacementManager = EggPlacementManager(this)
        safeManager = SafeManager(this)
        // Pulizia sessioni scadute all'avvio (operazione veloce, locale)
        localAnchorStore.purgeExpired()
        SoundManager.enabled = dm.getSoundEnabled()
        basketSizePx = 60f * resources.displayMetrics.density

        // Initialize ViewModel with intent data
        val players = intent.getStringArrayListExtra("players")?.toMutableList()
            ?: dm.getPlayers().map { it.name }.toMutableList()
        val riddlesFromIntent = intent.getStringArrayListExtra("riddles")?.toList() ?: loadRiddles()
        val turnMode       = intent.getStringExtra(GameModeActivity.EXTRA_TURN_MODE) ?: "sequential"
        val eggSetupMode   = intent.getStringExtra(EggSetupModeActivity.EXTRA_SETUP_MODE) ?: "manual"
        val autoEggCount   = intent.getIntExtra(EggSetupModeActivity.EXTRA_AUTO_EGG_COUNT, 4)
        val trapEggCount   = intent.getIntExtra(EggSetupModeActivity.EXTRA_TRAP_EGG_COUNT, 0)
        val penaltySecs    = intent.getIntExtra(EggSetupModeActivity.EXTRA_PENALTY_SECS, 30)
        val arMode         = intent.getStringExtra("ar_mode") ?: dm.getArMode()
        val isMultiplayer  = intent.getBooleanExtra(MultiplayerManager.EXTRA_IS_MP, false)
        val isIndoorMp     = intent.getBooleanExtra("indoor_mp", false)
        val indoorRoomCode = intent.getStringExtra("room_code") ?: ""
        val indoorIsHost   = intent.getBooleanExtra("room_is_host", false)
        val indoorCurrentPlayer = intent.getStringExtra("current_player") ?: ""
        val isRestoreMode  = intent.getBooleanExtra("restore_mode", false)
        val restoreSlotId  = intent.getStringExtra("restore_slot_id") ?: ""

        viewModel.initialize(
            players = players, riddles = riddlesFromIntent,
            turnMode = turnMode, eggSetupMode = eggSetupMode,
            autoEggCount = autoEggCount, trapEggCount = trapEggCount,
            penaltySecs = penaltySecs, arMode = arMode,
            isMultiplayer = isMultiplayer, isIndoorMp = isIndoorMp,
            indoorRoomCode = indoorRoomCode, indoorIsHost = indoorIsHost,
            indoorCurrentPlayer = indoorCurrentPlayer,
            isRestoreMode = isRestoreMode, restoreSlotId = restoreSlotId
        )

        // Cloud restore mode override
        if (eggSetupMode == "cloud_restore" && isIndoorMp && indoorRoomCode.isNotEmpty()) {
            viewModel.eggSetupMode = "auto"
            pendingCloudRestore = true
        }

        // Restore saved session
        if (isRestoreMode) {
            loadedSession = if (restoreSlotId.isNotBlank()) dm.loadSaveSlot(restoreSlotId)
                            else dm.loadSession()
            loadedSession?.let { viewModel.updateRiddles(it.riddles) }
        }

        mpPlayerId   = intent.getStringExtra(MultiplayerManager.EXTRA_PLAYER_ID) ?: ""
        mpPlayerName = intent.getStringExtra(MultiplayerManager.EXTRA_PLAYER_NAME) ?: ""
        mpManager    = MultiplayerManager.get()
        indoorPlayerUid = intent.getStringExtra("room_uid")
            ?: (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "")

        initAdMob(); setupButtons(); setupBackHandler(); checkCameraPermission()

        // Multiplayer setup
        val mpRoomCode = intent.getStringExtra(MultiplayerManager.EXTRA_ROOM_CODE) ?: ""
        if (isMultiplayer && mpRoomCode.isNotEmpty()) {
            val roomName = intent.getStringExtra(MultiplayerManager.EXTRA_ROOM_NAME) ?: mpRoomCode
            binding.mpLeaderboardCard.visibility = android.view.View.VISIBLE
            binding.mpChatFab.visibility         = android.view.View.VISIBLE
            binding.mpChatTitle.text             = "Chat - $roomName"
            mpManager.onScoresChanged = { scores -> runOnUiThread { updateMpLeaderboard(scores) } }
            mpManager.onChatMessage   = { msg    -> runOnUiThread { handleInGameChat(msg) } }
            mpManager.onError = { msg -> runOnUiThread {
                Toast.makeText(this, "MP: $msg", Toast.LENGTH_SHORT).show()
            }}
            mpManager.configure(mpRoomCode, mpPlayerId, mpPlayerName)
            binding.mpChatFab.setOnClickListener { toggleChatOverlay() }
            binding.mpChatClose.setOnClickListener { toggleChatOverlay() }
            binding.mpChatSendBtn.setOnClickListener { sendInGameChat() }
            binding.mpChatInput.setOnEditorActionListener { _, _, _ -> sendInGameChat(); true }
        }

        // Sentry context
        val gameMode = when {
            isIndoorMp          -> "indoor_mp"
            isMultiplayer       -> "outdoor_mp"
            else                -> "indoor_single"
        }
        SentryDebugManager.setGameContext(mode = gameMode, roomCode = if (isIndoorMp) indoorRoomCode else mpRoomCode)
        SentryDebugManager.breadcrumb(
            category = SentryDebugManager.Category.GAME,
            msg      = "Partita AR avviata",
            data     = mapOf("mode" to gameMode, "ar_mode" to arMode)
        )

        // Observe ViewModel state -> update UI
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI()
                    updateTimerDisplay(state.elapsedMs)
                    updateMenuVisibility(state.isMenuOpen)
                }
            }
        }

        SoundManager.playIntro()
    }

    override fun onResume()  { super.onResume();  isActive = true  }
    override fun onPause()   { isActive = false;  super.onPause()  }


    override fun onDestroy() {
        isActive = false; super.onDestroy()
        IndoorArSync.clearCallbacks()
        IndoorArSync.reset()
        viewModel.stopTimer()
        indoorEggsListener?.let {
            if (viewModel.indoorRoomCode.isNotEmpty())
                IndoorSessionManager.removeListener(viewModel.indoorRoomCode, "setup/eggs", it)
        }
        if (viewModel.isIndoorMp) IndoorArSync.reset()
        val copy = eggs.toList(); eggs.clear()
        copy.forEach { egg -> egg.pulseAnim?.cancel(); try { egg.anchorNode.destroy() } catch (_: Exception) {} }
        try { safeObject?.anchorNode?.destroy() } catch (_: Exception) {}
        roomScanManager.reset()
        if (viewModel.isMultiplayer) { try { mpManager.disconnect() } catch (_: Exception) {} }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val state = viewModel.uiState.value
                    when {
                        state.isMenuOpen -> closeInGameMenu()
                        state.gamePhase == GamePhase.PLAYING -> openInGameMenu()
                        else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                    }
                }
            }
        )
    }

    // â”€â”€â”€ AdMob â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    internal var adsInitialized = false

    // â”€â”€â”€ Permessi â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == CAMERA_PERMISSION_CODE && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            arSceneManager.setupAR()
        } else {
            android.app.AlertDialog.Builder(this)
                .setTitle("ðŸ“· Fotocamera necessaria")
                .setMessage("Huntix ha bisogno della fotocamera per funzionare.\n\nVuoi abilitarla nelle impostazioni?")
                .setPositiveButton("Apri impostazioni") { _, _ ->
                    startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", packageName, null)
                    ))
                }
                .setNegativeButton("Riprova") { _, _ ->
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
                }
                .setNeutralButton("Esci") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }


    // â”€â”€â”€ Cassaforte â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€


    /** Esplosione di sparkle dorati quando la cassaforte si apre */


    // â”€â”€â”€ Biglietto â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€


    /**
     * Overlay animato "TURNO DI [NOME]"
     * In modalita' alternata: tutti finiscono insieme, mostriamo le stats finali.
     */


    // â”€â”€â”€ Gestione perdita tracking â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    // â”€â”€â”€ Picker tipo cassaforte â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    // â”€â”€â”€ Menu in-game â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€


    // â”€â”€â”€ Pulsanti â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€


    // â”€â”€â”€ UI â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€


    // â”€â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    // === INDIZIO CON REWARDED AD ==========================================

    /**
     * Chiamato quando il bambino preme il pulsante Indizio.
     * Mostra un Rewarded Ad; al completamento sblocca l'indizio.
     */

    /**
     * Calcola e mostra distanza + direzione relativa rispetto alla camera.
     */


    /** Aggiorna il leaderboard live multiplayer (angolo basso sinistra) */

    // â”€â”€â”€ Chat in-game â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    internal var mpUnreadCount = 0
    internal var mpChatOpen    = false


    /** Torna alla HomeActivity svuotando lo stack di navigazione. */

    /** Restituisce (larghezza, altezza) in pixel compatibile con API 26-34 */
    internal fun windowSizePx(): Pair<Float, Float> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            Pair(metrics.bounds.width().toFloat(), metrics.bounds.height().toFloat())
        } else {
            @Suppress("DEPRECATION")
            val dm = resources.displayMetrics
            Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
        }
    }

    internal fun fmtMs(ms: Long): String {
        val s = ms / 1000; val m = s / 60
        return if (m > 0) "${m}m${"%02d".format(s % 60)}s" else "${s}s"
    }

    internal fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
