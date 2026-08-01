package com.intelligame.huntix.minigames.ar

import android.os.SystemClock
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.ar.node.AnchorNode

/**
 * AR Memory immersivo.
 *
 * Le carte non appaiono in una posizione fissa davanti alla fotocamera: vengono
 * posizionate come una griglia 3D su una superficie reale rilevata con il Plane
 * Detection di ARCore (pavimento, tavolo...). A inizio partita c'è una breve fase
 * di scansione (raycast AR sul piano dal centro dello schermo); poi ogni carta ha
 * il proprio AR Anchor, quindi resta ferma nello spazio mentre il giocatore si
 * muove e cammina intorno alla griglia. Il tocco di selezione usa il raycast AR
 * della scena (già gestito in ARGameActivity.onTouchEvent).
 */
class ARMemoryActivity : ARGameActivity() {

    private val N = 6
    private val nodes = arrayOfNulls<AREgg>(N)
    private val types = IntArray(N) { it / 2 }
    private val matched = BooleanArray(N)
    private val revealed = BooleanArray(N)
    private var firstPick = -1
    private var pairsFound = 0
    private var lock = false
    private var moves = 0
    private var scanning = false
    private var placed = false
    private var scanHintShown = false
    private var scanStart = 0L

    init {
        // Plane detection (pavimento/tavolo): deve essere attivo PRIMA che la
        // sessione ARCore venga configurata in ARGameActivity.onCreate.
        usePlaneDetection = true
    }

    override fun onGameCreate() {
        pairsFound = 0; firstPick = -1; moves = 0; lock = false
        matched.fill(false); revealed.fill(false)
        types.shuffle()
        nodes.forEach { it?.let { e -> removeEgg(e) } }
        nodes.fill(null)
        scanning = true
        placed = false
        scanHintShown = false
        scanStart = SystemClock.elapsedRealtime()
        statusText.text = "🔍 Scansiona l'ambiente: punta la fotocamera su una superficie piana…"
        updateHud()
        startGame()
    }

    /**
     * Fase di scansione: a ogni frame TRACKING proviamo un raycast AR dal centro
     * dello schermo; appena colpiamo un piano orizzontale posizioniamo la griglia.
     */
    override fun onArFrame(session: Session, frame: Frame) {
        if (!scanning || placed) return
        val arena = tryAnchorToPlane()
        if (arena != null) {
            scanning = false
            placed = true
            statusText.text = "Memory AR: trova le coppie! 🧠"
            AppLog.i("ARMemoryActivity", "Plane found — placing grid")
            placeGrid(arena)
        } else if (!scanHintShown && SystemClock.elapsedRealtime() - scanStart > 8000) {
            scanHintShown = true
            statusText.text = "⚠️ Nessuna superficie rilevata: muovi il telefono e inquadra il pavimento o un tavolo."
        }
    }

    private fun placeGrid(arena: AnchorNode) {
        val cols = 3
        val spacing = 0.34f
        val radius = 0.1f
        for (i in 0 until N) {
            val col = i % cols
            val row = i / cols
            // Offset locale rispetto all'anchor del piano: la griglia giace sulla
            // superficie (y=radius così le carte si appoggiano, non affondano).
            val local = Pose(
                floatArrayOf((col - 1) * spacing, radius, row * spacing),
                floatArrayOf(0f, 0f, 0f, 1f)
            )
            val pose = arena.anchor.pose.compose(local)
            val egg = spawnEggAt(pose, 5, radius = radius)
            egg?.phase = i.toFloat()
            nodes[i] = egg
        }
        AppLog.i("ARMemoryActivity", "Grid placed (${nodes.count { it != null }}/$N cards)")
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || lock || !egg.alive) return
        val i = egg.phase.toInt()
        if (i !in 0 until N || matched[i] || revealed[i]) return
        recolorEgg(egg, types[i]); revealed[i] = true
        if (firstPick == -1) {
            firstPick = i
            return
        }
        moves++
        val a = firstPick; val b = i
        firstPick = -1
        if (types[a] == types[b]) {
            matched[a] = true; matched[b] = true
            pairsFound++
            updateHud()
            if (pairsFound == N / 2) postDelayed(700) { endGame() }
        } else {
            lock = true
            postDelayed(900) {
                if (!running) return@postDelayed
                nodes[a]?.let { recolorEgg(it, 5) }
                nodes[b]?.let { recolorEgg(it, 5) }
                revealed[a] = false; revealed[b] = false
                lock = false
            }
        }
    }

    private fun updateHud() {
        livesText.text = "💡 ${N / 2 - pairsFound}"
        scoreText.text = "$pairsFound/${N / 2}"
        timerText.text = ""
    }

    private fun endGame() {
        stopGame()
        finishGame(pairsFound * 90, "AR Memory ($pairsFound/${N / 2})", pairsFound == N / 2,
            MiniGameManager.GAME_MEMORY)
    }
}
