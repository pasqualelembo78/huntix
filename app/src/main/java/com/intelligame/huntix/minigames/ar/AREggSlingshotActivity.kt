package com.intelligame.huntix.minigames.ar

import android.os.SystemClock
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 🎯 AR Egg Slingshot — "Lancio dell'uovo in Realtà Aumentata".
 *
 * Inquadra una superficie piano (tavoloo, pavimento) e appoggia l'arena
 * nello spazio reale. Gli uova-bersaglio ronzano (audio 3D); trascina per
 * caricare il nastro, rilascia per lanciare l'uovo con fisica balistica.
 * Colpisci un bersaglio: bottino, esplosione di particelle, vibrazione
 * e un suono che "volta intorno a te". Completo il motore immersivo.
 */
class AREggSlingshotActivity : ARGameActivity() {

    companion object {
        private const val GRAVITY = 9.8f
        private const val TARGET_COUNT = 7
        private const val WIN_HITS = 12
        private val C_GROUND = 0xFF3A3250.toInt()
        private val C_TARGET = 0xFFFFD700.toInt()
        private val C_AIM = 0xFF6AD7FF.toInt()
        private val C_PROJECTILE = 0xFFF8F8FF.toInt()
        private val C_GLOW = 0x66FFD166.toInt()
        private val ANGLE_RANGE = 12
    }

    private var arena: AnchorNode? = null
    private var groundY = 0f
    private val targets = mutableListOf<Target>()
    private var aimLine = mutableListOf<SphereNode>()
    private var projectile: SphereNode? = null
    private var projVel = Float3(0f, 0f, 0f)
    private var flying = false
    private var score = 0
    private var lastNow = 0L
    private var aimDown = Pair(0f, 0f)
    private var gameOver = false
    private val targetLoops = HashMap<Int, Int>()

    private class Target(val node: SphereNode, var x: Float, var y: Float, var z: Float, var alive: Boolean) {
        var loopId: Int = -1
    }

    override fun onGameCreate() {
        targets.forEach { removeNode(it.node) }
        targets.clear()
        aimLine.forEach { removeNode(it) }
        aimLine.clear()
        projectile?.let { removeNode(it) }
        projectile = null
        targetLoops.values.forEach { spatialAudio.stopLoop(it) }
        targetLoops.clear()
        projVel = Float3(0f, 0f, 0f)
        flying = false
        score = 0
        gameOver = false
        usePlaneDetection = true
        statusText.text = "Inquadra una superficie piano… 📱"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = ""
        timerText.text = "🥚 Obiettivi: 0/$WIN_HITS"
        scoreText.text = "0 pt"
        startGame()
        whenReady { placeArena() }
    }

    private fun placeArena() {
        val a = tryAnchorToPlane()
        if (a == null) {
            statusText.text = "Nessun piano inquadrato. Muovi il telefono… 📱"
            if (running) postDelayed(500) { placeArena() }
            return
        }
        arena = a
        groundY = -0.05f
        // piazza un disco visivo del suolo reale
        val disc = eggNode(C_GROUND, 0.35f).apply {
            scale = io.github.sceneview.math.Scale(2.2f, 2.2f, 0.2f)
            position = Position(0f, groundY - 0.001f, 0f)
        }
        a.addChildNode(disc)
        buildTargets()
        spawnProjectileHome()
        buildAim()
        installInputCapture(
            onStart = { aimDown = 0f to 0f; drawArc(0f, 0f) },
            onDrag = { dx, dy -> aimDown = (dx * 0.012f) to (dy * 0.010f); drawArc(aimDown.first, aimDown.second) },
            onEnd = { _, _, _ -> if (!flying && !gameOver) launch() }
        )
        postDelayed(200) { updateStatus() }
    }

    private fun buildTargets() {
        val a = arena ?: return
        repeat(TARGET_COUNT) {
            val t = spawnTarget(a)
            targets.add(t)
        }
    }

    private fun spawnTarget(a: AnchorNode): Target {
        val t = eggNode(C_TARGET, 0.11f)
        t.scale = io.github.sceneview.math.Scale(1f, 1.35f, 1f)
        val x = Random.nextFloat() * 1.4f - 0.7f
        val y = Random.nextFloat() * 0.7f + 0.25f
        val z = 0.6f + Random.nextFloat() * 0.9f
        t.position = Position(x, y, z)
        a.addChildNode(t)
        val loop = spatialAudio.loopAt(t, 320f + Random.nextFloat() * 80f, 0.45f)
        val tgt = Target(t, x, y, z, true)
        tgt.loopId = loop
        targetLoops[t.hashCode()] = loop
        return tgt
    }

    private fun spawnProjectileHome() {
        val a = arena ?: return
        val egg = eggNode(C_PROJECTILE, 0.085f)
        egg.position = Position(0f, 0.3f, 0f)
        a.addChildNode(egg)
        projectile = egg
    }

    private fun buildAim() {
        val a = arena ?: return
        for (i in 0 until ANGLE_RANGE) {
            val n = eggNode(C_AIM, 0.035f).apply {
                scale = io.github.sceneview.math.Scale(0.5f, 0.5f, 0.5f)
                isVisible = false
            }
            a.addChildNode(n)
            aimLine.add(n)
        }
    }

    /** Traiettoria prevista (linea tratteggiata) in tempo reale. */
    private fun drawArc(steerX: Float, steerY: Float) {
        val vx = steerX * 8f
        val vy = -steerY * 9f
        val vz = 2.2f
        for (i in 0 until ANGLE_RANGE) {
            val t = i * 0.08f
            val n = aimLine[i]
            n.position = Position(vx * t, 0.3f + vy * t - 0.5f * GRAVITY * t * t, vz * t)
            n.isVisible = true
        }
    }

    private fun launch() {
        val proj = projectile
        if (proj == null) { spawnProjectileHome(); launch(); return }
        val vx = aimDown.first * 8f
        val vy = -aimDown.second * 9f
        val vz = 2.2f
        proj.position = Position(0f, 0.3f, 0f)
        projVel = Float3(vx, vy, vz)
        flying = true
        for (n in aimLine) n.isVisible = false
        spatialAudio.oneShot(600f, 120, decay = true, gain = 0.5f) // suono lancio
        haptic(true)
    }

    override fun onArFrame(session: com.google.ar.core.Session, frame: com.google.ar.core.Frame) {
        if (!running) return
        tickPhysics()
        if (flying) {
            val proj = projectile ?: return
            val wp = proj.worldPosition
            if (wp.y < groundY) {
                miss()
                return
            }
            for (t in targets) {
                if (!t.alive) continue
                val nn = t.node.worldPosition
                val dx = wp.x - nn.x
                val dy = wp.y - nn.y
                val dz = wp.z - nn.z
                if (sqrt(dx * dx + dy * dy + dz * dz) < 0.22f) {
                    hit(t)
                    return
                }
            }
        }
    }

    private fun tickPhysics() {
        val now = SystemClock.elapsedRealtime()
        val dt = ((now - lastNow) / 1000f).coerceIn(0f, 0.02f)
        lastNow = now
        if (!flying) return
        val proj = projectile ?: return
        val p = proj.position
        projVel = Float3(projVel.x, projVel.y - GRAVITY * dt, projVel.z)
        proj.position = Position(p.x + projVel.x * dt, p.y + projVel.y * dt, p.z + projVel.z * dt)
    }

    private fun hit(t: Target) {
        flying = false
        spatialAudio.stopLoop(t.loopId)
        spatialAudio.oneShot(220f, 150, gain = 0.6f)
        burst(t.node.worldPosition, C_TARGET, 16)
        haptic(true)
        score += 10
        t.alive = false
        t.node.isVisible = false
        t.loopId = -1
        val alive = targets.count { it.alive }
        updateHud()
        val a = arena
        if (a != null && alive > 0 && (score / 10) % 2 == 0) {
            val n = targets[targets.count { !it.alive } % targets.size]
            if (!n.alive) {
                n.x = Random.nextFloat() * 1.4f - 0.7f
                n.y = Random.nextFloat() * 0.7f + 0.25f
                n.z = 0.6f + Random.nextFloat() * 0.9f
                if (n.node.parent != null) {
                    n.node.position = Position(n.x, n.y, n.z)
                    n.node.isVisible = true
                }
                n.alive = true
                n.loopId = spatialAudio.loopAt(n.node, 320f + Random.nextFloat() * 80f, 0.45f)
                targetLoops[n.node.hashCode()] = n.loopId
            }
        }
        if (score / 10 >= WIN_HITS) {
            endGame(true)
        } else {
            postDelayed(600) {
                projectile?.let { removeNode(it) }
                projectile = null
                spawnProjectileHome()
                drawArc(0f, 0f)
            }
        }
    }

    private fun miss() {
        flying = false
        spatialAudio.oneShot(140f, 120, gain = 0.4f)
        haptic(false)
        projectile?.let { removeNode(it) }
        projectile = null
        spawnProjectileHome()
        drawArc(0f, 0f)
    }

    private fun updateHud() {
        timerText.text = "🥚 Obiettivi: ${(WIN_HITS - targets.count { !it.alive }).coerceAtLeast(0)}/$WIN_HITS"
        scoreText.text = "$score pt"
    }

    private fun updateStatus() {
        statusText.text = "Trascina per mirare, rilascia per lanciare 🎯"
    }

    private fun endGame(won: Boolean) {
        if (gameOver) return
        gameOver = true
        stopGame()
        aimLine.forEach { removeNode(it) }
        aimLine.clear()
        if (!won) {
            projectile?.let { removeNode(it) }
            projectile = null
        }
        targets.forEach { spatialAudio.stopLoop(it.loopId) }
        targetLoops.clear()
        val reward = if (won) 200 else (score)
        val label = if (won) "AR Slingshot: colpiti $score!" else "AR Slingshot: $score pt"
        try {
            finishGame(reward.coerceAtLeast(10).coerceAtMost(500), label, won, MiniGameManager.GAME_CATCH_EGG)
        } catch (e: Exception) { Sentry.captureException(e) }
    }

    override fun onDestroy() {
        super.onDestroy()
        targets.forEach { spatialAudio.stopLoop(it.loopId) }
        targetLoops.clear()
    }
}
