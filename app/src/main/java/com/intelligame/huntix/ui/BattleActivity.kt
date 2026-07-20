package com.intelligame.huntix.ui
import android.Manifest; import android.content.pm.PackageManager; import android.graphics.Color; import android.graphics.Typeface; import android.graphics.drawable.GradientDrawable; import android.os.*; import android.view.*; import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts; import androidx.appcompat.app.AppCompatActivity; import androidx.camera.core.CameraSelector; import androidx.camera.core.Preview; import androidx.camera.lifecycle.ProcessCameraProvider; import androidx.camera.view.PreviewView; import androidx.core.content.ContextCompat
import com.intelligame.huntix.*; import com.intelligame.huntix.battle.*
import com.intelligame.huntix.BaseNavActivity
class BattleActivity : BaseNavActivity() {
    private lateinit var pl: PlayerController; private lateinit var en: Enemy; private lateinit var ai: AIController; private lateinit var co: ComboSystem; private lateinit var cb: CombatSystem; private lateinit var hf: HitFeelSystem
    private lateinit var an: AnimationController; private lateinit var eg: FightingEngine; private lateinit var sp: BattleSpawnManager.SpawnResult; private lateinit var av: ArenaView; private lateinit var root: FrameLayout; private lateinit var previewView: PreviewView; private lateinit var banner: TextView
    private val camPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startCamera() else Toast.makeText(this, "Camera necessaria per lo sfondo AR", Toast.LENGTH_LONG).show() }
    private val vib by lazy { getSystemService(VIBRATOR_SERVICE) as? Vibrator }; private val vh = Handler(Looper.getMainLooper()); private val vr = object : Runnable { override fun run() { if (eg.gameState == FightingEngine.GameState.FIGHTING) hf.consumeVibration()?.let { try { @Suppress("DEPRECATION") vib?.vibrate(it.durationMs) } catch (_: Exception) {} }; vh.postDelayed(this, 16) } }
    override fun onCreate(s: Bundle?) { super.onCreate(s); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); @Suppress("DEPRECATION") window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY; AdsManager.init(this); setup() }
    override fun onPause() { super.onPause(); eg.pause(); vh.removeCallbacks(vr) }; override fun onResume() { super.onResume(); eg.resume(); vh.post(vr) }; override fun onDestroy() { super.onDestroy(); vh.removeCallbacks(vr) }
    private fun setup() { sp = BattleSpawnManager.spawnEnemy(); val st = when (sp.element) { ElementType.FIRE -> Enemy.AIStyle.AGGRESSIVE; ElementType.AIR -> Enemy.AIStyle.BALANCED; else -> Enemy.AIStyle.DEFENSIVE }
        pl = PlayerController(); en = Enemy(sp.creature, sp.element, st, sp.difficultyScale); ai = AIController(sp.element, AIController.fromRarity(sp.creature.rarityId)); co = ComboSystem(); cb = CombatSystem(); hf = HitFeelSystem(); an = AnimationController()
        eg = FightingEngine(pl, en, ai, co, cb, hf, 90000L)
        // CONNECT animController to engine and AI for projectiles
        eg.animController = an; ai.animController = an
        eg.onBattleEvent = { t, m -> runOnUiThread { onEv(t, m) } }
        root = FrameLayout(this)
        previewView = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT) }
        root.addView(previewView)
        av = ArenaView(this).apply { engine = eg; animController = an }
        root.addView(av, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        banner = TextView(this).apply { textSize = 42f; setTextColor(Color.parseColor("#FFD700")); gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif-black", Typeface.BOLD); visibility = View.GONE }
        root.addView(banner, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        buildArcadePad()
        setContentView(root); eg.startCountdown(); vh.post(vr)
        startCameraWithPermission()
    }
    private fun buildArcadePad() { val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val dpad = FrameLayout(this); val dpadSize = dp(140)
        dpad.addView(holdBtn("\u25C0", dp(46), dp(46), "#CC333355", { eg.onPlayerMoveBackward() }, { eg.onPlayerStopMoving() }).apply { layoutParams = FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER_VERTICAL or Gravity.START) })
        dpad.addView(holdBtn("\u25B6", dp(46), dp(46), "#CC333355", { eg.onPlayerMoveForward() }, { eg.onPlayerStopMoving() }).apply { layoutParams = FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER_VERTICAL or Gravity.END) })
        dpad.addView(tapBtn("\u25B2", dp(46), dp(46), "#CC335533") { eg.onPlayerJump() }.apply { layoutParams = FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.CENTER_HORIZONTAL) })
        root.addView(dpad, FrameLayout.LayoutParams(dpadSize, dpadSize, Gravity.BOTTOM or Gravity.START).apply { leftMargin = dp(8); bottomMargin = dp(6) })
        val actions = FrameLayout(this); val actSize = dp(150)
        actions.addView(tapBtn("A", dp(52), dp(52), "#CC1565C0") { eg.onPlayerLightAttack() }.apply { layoutParams = FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER_VERTICAL or Gravity.START) })
        actions.addView(tapBtn("B", dp(52), dp(52), "#CCCC0000") { eg.onPlayerHeavyAttack() }.apply { layoutParams = FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER_VERTICAL or Gravity.END) })
        actions.addView(holdBtn("\uD83D\uDEE1", dp(46), dp(46), "#CC006677", { eg.onPlayerBlock() }, { pl.releaseBlock() }).apply { layoutParams = FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.CENTER_HORIZONTAL) })
        actions.addView(tapBtn("\u26A1", dp(46), dp(46), "#CCFF8800") { eg.onPlayerSpecialAttack() }.apply { layoutParams = FrameLayout.LayoutParams(dp(46), dp(46), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL) })
        root.addView(actions, FrameLayout.LayoutParams(actSize, actSize, Gravity.BOTTOM or Gravity.END).apply { rightMargin = dp(8); bottomMargin = dp(6) })
    }
    private fun startCameraWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else camPermLauncher.launch(Manifest.permission.CAMERA)
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
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }
    private fun tapBtn(text: String, w: Int, h: Int, hex: String, onClick: () -> Unit) = TextView(this).apply { this.text = text; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(hex)); setStroke(2, Color.parseColor("#55FFFFFF")) }; layoutParams = FrameLayout.LayoutParams(w, h); isClickable = true; setOnClickListener { onClick() } }
    private fun holdBtn(text: String, w: Int, h: Int, hex: String, onDown: () -> Unit, onUp: () -> Unit) = TextView(this).apply { this.text = text; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(hex)); setStroke(2, Color.parseColor("#55FFFFFF")) }; layoutParams = FrameLayout.LayoutParams(w, h); isClickable = true
        setOnTouchListener { _, ev -> when (ev.action) { MotionEvent.ACTION_DOWN -> { onDown(); true }; MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { onUp(); true }; else -> false } } }
    private fun onEv(t: FightingEngine.BattleEventType, m: String) { val ex = en.positionX * av.width; val ey = av.height * 0.55f
        when (t) { FightingEngine.BattleEventType.COUNTDOWN -> { banner.text = m; banner.visibility = View.VISIBLE; if (m.contains("Combatti")) Handler(Looper.getMainLooper()).postDelayed({ banner.visibility = View.GONE }, 900) }
            FightingEngine.BattleEventType.CRIT -> { an.spawnHitParticles(ex, ey, Color.parseColor("#FFD700"), 20); an.spawnDamageNumber(ex, ey - 60f, eg.totalPlayerDamage, true) }
            FightingEngine.BattleEventType.HIT -> { an.spawnHitParticles(ex, ey, Color.parseColor("#66FF88"), 10); an.spawnDamageNumber(ex, ey - 40f, eg.totalPlayerDamage, false) }
            FightingEngine.BattleEventType.SPECIAL -> an.spawnHitParticles(ex, ey, Color.parseColor("#E879F9"), 25)
            FightingEngine.BattleEventType.ENEMY_HIT -> { val px = pl.positionX * av.width; an.spawnHitParticles(px, ey, Color.parseColor("#FF4444"), 10) }
            FightingEngine.BattleEventType.ROUND_END -> Handler(Looper.getMainLooper()).postDelayed({ showRes() }, 2000); else -> {} }
        if (t == FightingEngine.BattleEventType.HIT || t == FightingEngine.BattleEventType.CRIT) an.showComboCounter(co.currentCombo, co.comboLevel) }
    private fun showRes() { vh.removeCallbacks(vr); val tr = when (eg.battleResult) { FightingEngine.BattleResult.PLAYER_WIN -> BattleEngine.TickResult.ENEMY_DEFEATED; FightingEngine.BattleResult.ENEMY_WIN -> BattleEngine.TickResult.PLAYER_DEFEATED; else -> BattleEngine.TickResult.TIME_UP }
        val le = BattleEngine(sp.creature, sp.element, sp.difficultyScale); val rw = BattleRewardSystem.calculateRewards(le, tr, sp.event == BattleSpawnManager.SpawnEvent.RARE, sp.event == BattleSpawnManager.SpawnEvent.UNSTABLE); BattleRewardSystem.applyRewards(this, rw); AdsManager.onBattleCompleted(this)
        val win = eg.battleResult == FightingEngine.BattleResult.PLAYER_WIN; val sc = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0A1A")) }; val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(60), dp(24), dp(32)); gravity = Gravity.CENTER_HORIZONTAL }; sc.addView(r)
        r.addView(tv(if (win) "VITTORIA!" else "SCONFITTA...", 32f, Color.parseColor(if (win) "#FFD700" else "#FF4444"), Gravity.CENTER, true))
        r.addView(spacer(dp(16))); r.addView(tv("Combo: ${co.maxCombo} | Colpi: ${co.totalHits} | Danno: ${eg.totalPlayerDamage}", 14f, Color.parseColor("#FFD700"), Gravity.CENTER))
        r.addView(spacer(dp(12)))
        if (rw.xpGained > 0) r.addView(tv("+${rw.xpGained} XP", 20f, Color.parseColor("#66FF88"), Gravity.CENTER, true))
        if (rw.mvcGained > 0) r.addView(tv("+${rw.mvcGained} MVC", 20f, Color.parseColor("#00BCD4"), Gravity.CENTER, true))
        if (rw.gemsGained > 0) r.addView(tv("+${rw.gemsGained} Gemme!", 20f, Color.parseColor("#E879F9"), Gravity.CENTER, true))
        r.addView(spacer(dp(20))); r.addView(resBtn("COMBATTI ANCORA") { setup() }); r.addView(spacer(dp(10)))
        r.addView(tv("Torna", 14f, Color.parseColor("#9999CC"), Gravity.CENTER).apply { setOnClickListener { finish() } }); setContentView(sc) }
    private fun resBtn(text: String, onClick: () -> Unit) = TextView(this).apply { this.text = text; textSize = 17f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#DD1565C0")) }
        setPadding(dp(24), dp(16), dp(24), dp(16)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); isClickable = true; setOnClickListener { onClick() } }
    private fun tv(t: String, s: Float, c: Int, g: Int, b: Boolean = false) = TextView(this).apply { text = t; textSize = s; setTextColor(c); gravity = g; if (b) typeface = Typeface.create("sans-serif-black", Typeface.BOLD) }
    private fun spacer(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
