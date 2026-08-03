package com.intelligame.huntix.minigames.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.managers.MiniGameManager
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.ar.arcore.quaternion
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Size
import io.github.sceneview.node.ImageNode
import kotlin.math.cos
import kotlin.math.sin

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
 *
 * Ogni carta è un [ImageNode] (quadrilatero con texture): il retro mostra un "?"
 * su sfondo scuro, il fronte mostra la coppia di emoji (uovo + simbolo) come nel
 * gioco Memory 2D. L'immagine viene disegnata via [Bitmap] e applicata come
 * texture, quindi la carta è sempre illuminata e visibile in AR.
 */
class ARMemoryActivity : ARGameActivity() {

    private val N = 6
    private val SYMBOLS = arrayOf(
        "\uD83E\uDD5A\u2764\uFE0F", // 🥚❤️
        "\uD83E\uDD5A\uD83D\uDD35", // 🥚🔵
        "\uD83E\uDD5A\uD83D\uDFE2"  // 🥚🟢
    )
    private val FRONT_BG = intArrayOf(
        0xFFE15554.toInt(), 0xFF3B8EA5.toInt(), 0xFF6BAA75.toInt()
    )
    private val BACK_BG = 0xFF3A2E5C.toInt()

    private val cardEggs = arrayOfNulls<AREgg>(N)
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

    private val backBmp by lazy { cardBitmap("\u2753", BACK_BG, border = true) }
    private val frontBmps by lazy { Array(N / 2) { cardBitmap(SYMBOLS[it], FRONT_BG[it], border = false) } }

    init {
        // Posizionamento dell'arena (piano/mesh/libero): mostra il dialogo di
        // scelta modalità all'avvio.
        showsModeDialog = true
    }

    override fun onGameCreate() {
        pairsFound = 0; firstPick = -1; moves = 0; lock = false
        matched.fill(false); revealed.fill(false)
        types.shuffle()
        cardEggs.forEach { it?.let { e -> removeEgg(e) } }
        cardEggs.fill(null)
        scanning = true
        placed = false
        scanHintShown = false
        scanStart = SystemClock.elapsedRealtime()
        statusText.text = "🔍 Scansiona l'ambiente: punta la fotocamera su una superficie piana…"
        updateLevelHud(MiniGameManager.GAME_MEMORY)
        updateHud()
        startGame()
    }

    /**
     * Fase di scansione: a ogni frame TRACKING proviamo un raycast AR dal centro
     * dello schermo; appena colpiamo un piano orizzontale posizioniamo la griglia.
     */
    override fun onArFrame(session: Session, frame: Frame) {
        if (!scanning || placed) return
        val arena = tryArenaByMode()
        if (arena != null) {
            scanning = false
            placed = true
            persistArena(arena)
            statusText.text = "Memory AR: trova le coppie! 🧠"
            AppLog.i("ARMemoryActivity", "Plane found — placing grid")
            placeGrid(arena)
        } else if (!scanHintShown && SystemClock.elapsedRealtime() - scanStart > 8000) {
            scanHintShown = true
            statusText.text = "⚠️ Nessuna superficie rilevata: muovi il telefono e inquadra il pavimento o un tavolo."
        }
    }

    /**
     * Posiziona le N carte su una griglia 3x2 appoggiata alla superficie rilevata.
     * Ogni carta ha il proprio AR Anchor e viene ruotata (yaw) verso la camera
     * così da restare in piedi e rivolta verso il giocatore.
     */
    private fun placeGrid(arena: AnchorNode) {
        val cols = 3
        val spacing = 0.34f
        val cardW = 0.26f
        val cardH = 0.34f
        val arenaPose = arena.anchor.pose
        val yaw = yawToFaceCamera(arena)
        val yawQ = Quaternion(0f, sin(yaw / 2f), 0f, cos(yaw / 2f))
        for (i in 0 until N) {
            val col = i % cols
            val row = i / cols
            // Offset locale rispetto all'anchor dell'arena: y=cardH/2 così la carta
            // si appoggia sulla superficie senza affondare.
            val local = Pose(
                floatArrayOf((col - 1) * spacing, cardH / 2f, row * spacing),
                floatArrayOf(0f, 0f, 0f, 1f)
            )
            val composed = arenaPose.compose(local)
            val q = composed.quaternion * yawQ
            val pose = Pose(
                floatArrayOf(composed.tx(), composed.ty(), composed.tz()),
                floatArrayOf(q.x, q.y, q.z, q.w)
            )
            val anchor = spawnAnchorAt(pose) ?: continue
            val node = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = backBmp,
                size = Size(cardW, cardH),
                normal = Direction(0f, 0f, 1f)
            )
            anchor.addChildNode(node)
            val egg = AREgg(anchor, node, 5, phase = i.toFloat())
            registerEgg(egg)
            cardEggs[i] = egg
        }
        AppLog.i("ARMemoryActivity", "Grid placed (${cardEggs.count { it != null }}/$N cards)")
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || lock || !egg.alive) return
        val i = egg.phase.toInt()
        if (i !in 0 until N || matched[i] || revealed[i]) return
        val card = cardEggs[i]?.node as? ImageNode ?: return
        card.bitmap = frontBmps[types[i]]
        revealed[i] = true
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
                (cardEggs[a]?.node as? ImageNode)?.bitmap = backBmp
                (cardEggs[b]?.node as? ImageNode)?.bitmap = backBmp
                revealed[a] = false; revealed[b] = false
                lock = false
            }
        }
    }

    /**
     * Disegna una carta (retro o fronte) come bitmap quadrata 256x256: sfondo
     * arrotondato, bordo bianco opzionale e simbolo centrato (emoji colorato).
     */
    private fun cardBitmap(symbol: String, bg: Int, border: Boolean): Bitmap {
        val px = 256f
        val bmp = Bitmap.createBitmap(px.toInt(), px.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(px * 0.06f, px * 0.06f, px * 0.94f, px * 0.94f)
        canvas.drawRoundRect(rect, px * 0.10f, px * 0.10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
        if (border) {
            val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = px * 0.02f
            }
            canvas.drawRoundRect(rect, px * 0.10f, px * 0.10f, borderP)
        }
        val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = px * 0.42f
        }
        val baseline = px / 2f - (textP.ascent() + textP.descent()) / 2f
        canvas.drawText(symbol, px / 2f, baseline, textP)
        return bmp
    }

    private fun updateHud() {
        livesText.text = "💡 ${N / 2 - pairsFound}"
        scoreText.text = "$pairsFound/${N / 2}"
        timerText.text = ""
    }

    private fun endGame() {
        stopGame()
        finishGame(pairsFound * 90, "AR Memory ($pairsFound/${N / 2})", pairsFound == N / 2,
            MiniGameManager.GAME_MEMORY, score = pairsFound)
    }
}
