package com.intelligame.huntix.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.intelligame.huntix.*
import com.intelligame.huntix.battle.*
import com.intelligame.huntix.battle.CharacterRenderer.AnimState

class BattleActivity : BaseNavActivity(), ArcadeControls.Listener {

    private lateinit var pl: PlayerController
    private lateinit var en: Enemy
    private lateinit var ai: AIController
    private lateinit var co: ComboSystem
    private lateinit var cb: CombatSystem
    private lateinit var hf: HitFeelSystem
    private lateinit var an: AnimationController
    private lateinit var fx: ImpactEffects
    private lateinit var eg: FightingEngine
    private lateinit var sp: BattleSpawnManager.SpawnResult
    private lateinit var av: ArenaView
    private lateinit var controls: ArcadeControls

    private lateinit var root: FrameLayout
    private lateinit var previewView: PreviewView

    private val camPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { if (it) startCamera() else setupNonAR() }

    private val vib by lazy { getSystemService(VIBRATOR_SERVICE) as? Vibrator }
    private val vh = Handler(Looper.getMainLooper())
    private val vr = object : Runnable {
        override fun run() {
            if (eg.gameState == FightingEngine.GameState.FIGHTING) {
                hf.consumeVibration()?.let {
                    try {
                        @Suppress("DEPRECATION")
                        vib?.vibrate(it.durationMs)
                    } catch (_: Exception) {}
                }
            }
            vh.postDelayed(this, 16)
        }
    }

    private var moveDirHandler: Runnable? = null
    private val moveHandler = object : Runnable {
        override fun run() {
            if (lastMoveDir != 0 && eg.gameState == FightingEngine.GameState.FIGHTING) {
                eg.onPlayerMoveDirection(lastMoveDir)
            }
            moveDirHandler = this
            vh.postDelayed(this, 50)
        }
    }
    private var lastMoveDir = 0

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        AdsManager.init(this)
        setup()
    }

    override fun onPause() {
        super.onPause()
        eg.pause()
        vh.removeCallbacks(vr)
        vh.removeCallbacks(moveHandler)
    }

    override fun onResume() {
        super.onResume()
        eg.resume()
        vh.post(vr)
    }

    override fun onDestroy() {
        super.onDestroy()
        vh.removeCallbacks(vr)
        vh.removeCallbacks(moveHandler)
        eg.destroy()
    }

    private fun setup() {
        sp = BattleSpawnManager.spawnEnemy()
        val st = when (sp.element) {
            ElementType.FIRE -> Enemy.AIStyle.AGGRESSIVE
            ElementType.AIR -> Enemy.AIStyle.BALANCED
            else -> Enemy.AIStyle.DEFENSIVE
        }

        pl = PlayerController().apply {
            val profile = PlayerProfileManager.myProfile
            creatureData = CreatureData(
                id = profile?.playerCharacterId ?: "player",
                name = profile?.name ?: "Tu",
                rarityId = "rare",
                power = (profile?.level ?: 1) * 30L
            )
            elementType = when (profile?.playerCharacterId) {
                "warrior" -> ElementType.FIRE
                "mage" -> ElementType.WATER
                "archer" -> ElementType.AIR
                "tank" -> ElementType.EARTH
                else -> ElementType.NORMAL
            }
        }
        en = Enemy(sp.creature, sp.element, st, sp.difficultyScale)
        ai = AIController(sp.element, AIController.fromRarity(sp.creature.rarityId))
        co = ComboSystem()
        cb = CombatSystem()
        hf = HitFeelSystem()
        an = AnimationController()
        fx = ImpactEffects()

        eg = FightingEngine(pl, en, ai, co, cb, hf, 90000L)
        eg.animController = an
        eg.impactEffects = fx
        ai.animController = an
        eg.onBattleEvent = { t, m -> runOnUiThread { onBattleEvent(t, m) } }

        root = FrameLayout(this)

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(previewView)

        av = ArenaView(this).apply {
            engine = eg
            animController = an
            impactEffects = fx
        }
        root.addView(av, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        controls = ArcadeControls(this).apply {
            listener = this@BattleActivity
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(controls)

        setContentView(root)
        eg.startCountdown()
        vh.post(vr)
        startCameraWithPermission()
    }

    private fun startCameraWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else camPermLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun setupNonAR() {
        previewView.visibility = View.GONE
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            } catch (_: Exception) {
                setupNonAR()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onJoystickDirection(dir: Int) {
        lastMoveDir = dir
        eg.onPlayerMoveDirection(dir)
        if (moveDirHandler == null) {
            vh.post(moveHandler)
        }
    }

    override fun onJoystickRelease() {
        lastMoveDir = 0
        eg.onPlayerStopMoving()
        vh.removeCallbacks(moveHandler)
        moveDirHandler = null
    }

    override fun onButtonPress(buttonId: Int) {
        when (buttonId) {
            ArcadeControls.BTN_PUNCH -> eg.onPlayerLightAttack()
            ArcadeControls.BTN_KICK -> eg.onPlayerHeavyAttack()
            ArcadeControls.BTN_SPECIAL -> eg.onPlayerSpecialAttack()
            ArcadeControls.BTN_BLOCK -> eg.onPlayerBlock()
            ArcadeControls.BTN_HEAVY -> eg.onPlayerHeavyAttack()
            ArcadeControls.BTN_SUPER -> eg.onPlayerSpecialAttack()
        }
    }

    override fun onButtonRelease(buttonId: Int) {
        when (buttonId) {
            ArcadeControls.BTN_BLOCK -> pl.releaseBlock()
        }
    }

    private fun onBattleEvent(type: FightingEngine.BattleEventType, msg: String) {
        val eng = eg
        val hitDamage = msg.removePrefix("-").toIntOrNull() ?: 0
        when (type) {
            FightingEngine.BattleEventType.COUNTDOWN -> {
                if (msg.isNotEmpty()) av.showBanner(msg)
            }
            FightingEngine.BattleEventType.CRIT -> {
                an.spawnDamageNumber(
                    en.positionX * av.width, av.height * 0.45f,
                    hitDamage, true
                )
                av.triggerHitFreeze(4)
                av.triggerDynamicZoom(0.06f)
            }
            FightingEngine.BattleEventType.HIT, FightingEngine.BattleEventType.SPECIAL -> {
                an.spawnDamageNumber(
                    en.positionX * av.width, av.height * 0.48f,
                    hitDamage, false
                )
                if (type == FightingEngine.BattleEventType.SPECIAL) {
                    av.triggerHitFreeze(6)
                }
            }
            FightingEngine.BattleEventType.ENEMY_HIT -> {
                an.spawnDamageNumber(
                    pl.positionX * av.width, av.height * 0.48f,
                    hitDamage, false
                )
                av.triggerHitFreeze(3)
            }
            FightingEngine.BattleEventType.PLAYER_STUN -> {
                av.triggerHitFreeze(3)
            }
            FightingEngine.BattleEventType.KO -> {
                av.showBanner(msg)
                av.triggerDynamicZoom(0.12f)
            }
            FightingEngine.BattleEventType.SUPER_READY -> {
                av.showBanner("SUPER!")
                av.triggerDynamicZoom(0.04f)
            }
            FightingEngine.BattleEventType.ROUND_END -> {
                if (eng.gameState == FightingEngine.GameState.ENDED) {
                    vh.postDelayed({ showResult() }, 2000)
                }
            }
            else -> {}
        }

        if (type == FightingEngine.BattleEventType.HIT
            || type == FightingEngine.BattleEventType.CRIT
            || type == FightingEngine.BattleEventType.SPECIAL
        ) {
            an.showComboCounter(co.currentCombo, co.comboLevel)
        }
    }

    private fun showResult() {
        vh.removeCallbacks(vr)
        vh.removeCallbacks(moveHandler)

        val win = eg.battleResult == FightingEngine.BattleResult.PLAYER_WIN
        val tr = when (eg.battleResult) {
            FightingEngine.BattleResult.PLAYER_WIN -> BattleEngine.TickResult.ENEMY_DEFEATED
            FightingEngine.BattleResult.ENEMY_WIN -> BattleEngine.TickResult.PLAYER_DEFEATED
            else -> BattleEngine.TickResult.TIME_UP
        }
        val le = BattleEngine(sp.creature, sp.element, sp.difficultyScale)
        val rw = BattleRewardSystem.calculateRewards(
            le, tr,
            sp.event == BattleSpawnManager.SpawnEvent.RARE,
            sp.event == BattleSpawnManager.SpawnEvent.UNSTABLE
        )
        BattleRewardSystem.applyRewards(this, rw, win)
        AdsManager.onBattleCompleted(this)

        val sc = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0A1A")) }
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(60), dp(24), dp(32))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        sc.addView(r)

        r.addView(makeTv(
            if (win) "VITTORIA!" else "SCONFITTA...", 32f,
            Color.parseColor(if (win) "#FFD700" else "#FF4444"),
            Gravity.CENTER, true
        ))
        r.addView(spacer(dp(16)))
        r.addView(makeTv(
            "Combo max: ${co.maxCombo} | Colpi: ${co.totalHits} | Danno: ${eg.totalPlayerDamage}",
            14f, Color.parseColor("#FFD700"), Gravity.CENTER
        ))
        r.addView(makeTv(
            "Round: ${eg.playerRoundWins}-${eg.enemyRoundWins}",
            14f, Color.parseColor("#8080A0"), Gravity.CENTER
        ))
        r.addView(spacer(dp(12)))

        if (rw.xpGained > 0) r.addView(makeTv("+${rw.xpGained} XP", 20f, Color.parseColor("#66FF88"), Gravity.CENTER, true))
        if (rw.mvcGained > 0) r.addView(makeTv("+${rw.mvcGained} MVC", 20f, Color.parseColor("#00BCD4"), Gravity.CENTER, true))
        if (rw.gemsGained > 0) r.addView(makeTv("+${rw.gemsGained} Gemme!", 20f, Color.parseColor("#E879F9"), Gravity.CENTER, true))

        r.addView(spacer(dp(20)))
        r.addView(makeResBtn("COMBATTI ANCORA") { setup() })
        r.addView(spacer(dp(10)))
        r.addView(makeTv("Torna", 14f, Color.parseColor("#9999CC"), Gravity.CENTER).apply {
            setOnClickListener { finish() }
        })

        setContentView(sc)
    }

    private fun makeTv(t: String, s: Float, c: Int, g: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = s; setTextColor(c); gravity = g
            if (bold) typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }

    private fun makeResBtn(text: String, onClick: () -> Unit) =
        TextView(this).apply {
            this.text = text; textSize = 17f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#DD1565C0"))
            }
            setPadding(dp(24), dp(16), dp(24), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true; setOnClickListener { onClick() }
        }

    private fun spacer(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, h
        )
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
