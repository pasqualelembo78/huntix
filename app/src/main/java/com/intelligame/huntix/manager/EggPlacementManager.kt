package com.intelligame.huntix.manager

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.intelligame.huntix.IndoorArSync
import com.intelligame.huntix.IndoorSessionManager
import com.intelligame.huntix.MainActivity
import com.intelligame.huntix.updateUI
import com.intelligame.huntix.model.EggObject
import com.intelligame.huntix.model.GamePhase
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class EggPlacementManager(internal val activity: MainActivity) {

    internal val binding get() = activity.binding
    internal val viewModel get() = activity.viewModel

    // ── Place egg ────────────────────────────────────────────────

    fun placeEgg(hit: HitResult, isTrap: Boolean, shape: String) {
        placeEggAtAnchor(hit.createAnchor(), isTrap, shape)
        activity.runOnUiThread {
            binding.btnStart.visibility = View.VISIBLE
            activity.updateUI()
            val lbl = if (isTrap) "Trappola #${activity.eggs.size}"
            else "${MainActivity.EGG_LABELS[(activity.eggs.size - 1) % MainActivity.EGG_LABELS.size]} Uovo #${activity.eggs.size}"
            Toast.makeText(activity, "$lbl nascosto!", Toast.LENGTH_SHORT).show()
        }
    }

    fun placeEggAtAnchor(anchor: Anchor, isTrap: Boolean, shape: String) {
        val sv = binding.sceneView
        val id = activity.eggs.size
        val colorIdx = id % MainActivity.EGG_COLORS.size
        val col = MainActivity.EGG_COLORS[colorIdx]
        val mat = sv.materialLoader.createColorInstance(color = col)
        val an = AnchorNode(engine = sv.engine, anchor = anchor)

        val eggNode: Node = when (shape) {
            "cube" -> CubeNode(sv.engine, Size(0.10f, 0.12f, 0.10f), materialInstance = mat).apply { position = Position(0f, 0.075f, 0f) }
            "cylinder" -> CylinderNode(sv.engine, 0.055f, 0.12f, materialInstance = mat).apply { position = Position(0f, 0.075f, 0f) }
            "diamond" -> CubeNode(sv.engine, Size(0.09f, 0.13f, 0.09f), materialInstance = mat).apply { position = Position(0f, 0.075f, 0f); rotation = Rotation(45f, 45f, 0f) }
            else -> SphereNode(sv.engine, 0.055f, materialInstance = mat).apply { position = Position(0f, 0.075f, 0f); scale = Scale(1f, 1.45f, 1f) }
        }
        an.addChildNode(eggNode)

        val trapMarker: SphereNode? = if (isTrap) {
            val tm = sv.materialLoader.createColorInstance(color = MainActivity.TRAP_COLOR)
            SphereNode(sv.engine, 0.022f, materialInstance = tm).apply { position = Position(0f, 0.26f, 0f); isVisible = true }
                .also { an.addChildNode(it) }
        } else null

        an.isVisible = true
        sv.addChildNode(an)
        activity.eggs.add(EggObject(id, colorIdx, shape, an, eggNode, isTrap, trapMarker))

        if (viewModel.isIndoorMp && viewModel.indoorIsHost && viewModel.indoorRoomCode.isNotEmpty()) {
            try {
                val session = sv.session ?: throw IllegalStateException("Sessione AR non disponibile")
                IndoorArSync.hostEggAnchor(session, anchor, id, colorIdx, shape, isTrap)
                IndoorArSync.onEggHosted = { eggIdx, cloudId ->
                    IndoorSessionManager.updateEggCloudAnchor(viewModel.indoorRoomCode, eggIdx, cloudId)
                }
            } catch (e: Exception) {
                android.util.Log.w("EggPlacement", "hostEggAnchor[$id] skipped: ${e.message}")
            }
        }

        if (viewModel.arMode == "room_scan") {
            saveAnchorLocally(id, anchor, colorIdx, shape, isTrap)
        }
    }

    fun placeEggAtAnchorDirect(anchor: Anchor, colorIdx: Int, shape: String, isTrap: Boolean) {
        val sv = binding.sceneView
        val id = activity.eggs.size
        val col = MainActivity.EGG_COLORS[colorIdx % MainActivity.EGG_COLORS.size]
        val mat = sv.materialLoader.createColorInstance(color = col)
        val an = AnchorNode(engine = sv.engine, anchor = anchor)
        val eggNode: Node = when (shape) {
            "cube" -> CubeNode(sv.engine, Size(0.10f, 0.12f, 0.10f), materialInstance = mat).apply { position = Position(0f, 0.075f, 0f) }
            "cylinder" -> CylinderNode(sv.engine, 0.055f, 0.12f, materialInstance = mat).apply { position = Position(0f, 0.075f, 0f) }
            "diamond" -> CubeNode(sv.engine, Size(0.09f, 0.13f, 0.09f), materialInstance = mat).apply { position = Position(0f, 0.075f, 0f); rotation = Rotation(45f, 45f, 0f) }
            else -> SphereNode(sv.engine, 0.055f, materialInstance = mat).apply { position = Position(0f, 0.075f, 0f); scale = Scale(1f, 1.45f, 1f) }
        }
        an.addChildNode(eggNode)
        an.isVisible = false
        sv.addChildNode(an)
        activity.eggs.add(EggObject(id, colorIdx, shape, an, eggNode, isTrap))
    }

    // ── Auto place ───────────────────────────────────────────────

    fun autoPlaceEggs(eggCount: Int, trapCount: Int) {
        val session = binding.sceneView.session ?: run {
            activity.runOnUiThread { Toast.makeText(activity, "Sessione AR non disponibile", Toast.LENGTH_LONG).show() }
            return
        }
        val planes = session.getAllTrackables(Plane::class.java).filter {
            it.trackingState == TrackingState.TRACKING && it.subsumedBy == null &&
            it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.extentX >= 0.25f && it.extentZ >= 0.25f
        }
        if (planes.isEmpty()) {
            activity.runOnUiThread {
                Toast.makeText(activity, "Nessun piano rilevato. Muovi il telefono lentamente.", Toast.LENGTH_LONG).show()
            }
            return
        }
        val placed = mutableListOf<FloatArray>()
        var attempts = 0
        while (placed.size < eggCount && attempts < eggCount * 40) {
            attempts++
            val plane = planes.random()
            val nearEdge = Random.nextFloat() > 0.3f
            val rx: Float
            val rz: Float
            if (nearEdge) {
                val frac = 0.55f + Random.nextFloat() * 0.35f
                when (Random.nextInt(4)) {
                    0 -> {
                        rx = frac * plane.extentX / 2f
                        rz = (Random.nextFloat() - 0.5f) * plane.extentZ * 0.8f
                    }
                    1 -> {
                        rx = -frac * plane.extentX / 2f
                        rz = (Random.nextFloat() - 0.5f) * plane.extentZ * 0.8f
                    }
                    2 -> {
                        rx = (Random.nextFloat() - 0.5f) * plane.extentX * 0.8f
                        rz = frac * plane.extentZ / 2f
                    }
                    else -> {
                        rx = (Random.nextFloat() - 0.5f) * plane.extentX * 0.8f
                        rz = -frac * plane.extentZ / 2f
                    }
                }
            } else {
                rx = (Random.nextFloat() - 0.5f) * plane.extentX * 0.6f
                rz = (Random.nextFloat() - 0.5f) * plane.extentZ * 0.6f
            }
            val cx = plane.centerPose.tx() + rx
            val cy = plane.centerPose.ty()
            val cz = plane.centerPose.tz() + rz
            if (placed.any { p -> hypot(p[0] - cx, p[2] - cz) < 0.35f }) continue
            try {
                val anchor = session.createAnchor(Pose(floatArrayOf(cx, cy, cz), floatArrayOf(0f, 0f, 0f, 1f)))
                placeEggAtAnchor(anchor, false, viewModel.nextEggShape)
                placed.add(floatArrayOf(cx, cy, cz))
            } catch (_: Exception) {}
        }
        if (trapCount > 0 && activity.eggs.isNotEmpty()) {
            activity.eggs.shuffled().take(trapCount.coerceAtMost(activity.eggs.size)).forEach { egg ->
                egg.isTrap = true
                egg.trapMarkerNode?.isVisible = true
            }
        }
        activity.runOnUiThread {
            if (placed.isNotEmpty()) {
                binding.btnStart.visibility = View.VISIBLE
                activity.updateUI()
                Toast.makeText(activity, "${placed.size} uova piazzate!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(activity, "Nessuna posizione trovata. Muovi il telefono lentamente.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Selection ────────────────────────────────────────────────

    fun selectEgg(egg: EggObject) {
        deselectEgg()
        activity.selectedEgg = egg
        egg.eggNode.scale = Scale(1.35f, 1.35f * 1.45f, 1.35f)
        binding.btnDeleteEgg.visibility = View.VISIBLE
        val trapLbl = if (egg.isTrap) " [TRAPPOLA]" else ""
        binding.tvInstruction.text = "Uovo #${egg.id + 1}$trapLbl selezionato"
    }

    fun deselectEgg() {
        activity.selectedEgg?.eggNode?.scale = Scale(1f, 1.45f, 1f)
        activity.selectedEgg = null
        binding.btnDeleteEgg.visibility = View.GONE
        if (activity.gamePhase == GamePhase.SETUP_EGGS) binding.tvInstruction.text = setupInstructionText()
    }

    fun deleteSelectedEgg() {
        val egg = activity.selectedEgg ?: return
        egg.pulseAnim?.cancel()
        egg.anchorNode.destroy()
        activity.eggs.remove(egg)
        activity.selectedEgg = null
        binding.btnDeleteEgg.visibility = View.GONE
        activity.runOnUiThread { activity.updateUI() }
        Toast.makeText(activity, "Uovo eliminato", Toast.LENGTH_SHORT).show()
    }

    fun setupInstructionText(): String = when (viewModel.eggSetupMode) {
        "auto" -> "Uova piazzate in automatico - avvia la caccia"
        "combined" -> "Tocca pavimento per aggiungerne - tocca uovo per selezionare"
        else -> "Tocca il pavimento - tocca un uovo per selezionarlo"
    }

    // ── Pulse ────────────────────────────────────────────────────

    fun startEggPulse(egg: EggObject) {
        egg.pulseAnim?.cancel()
        egg.pulseAnim = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 950
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                if (egg.anchorNode.isVisible) {
                    val p = 1f + 0.25f * sin((anim.animatedValue as Float).toDouble()).toFloat()
                    egg.eggNode.scale = Scale(p, p * 1.45f, p)
                }
            }
            start()
        }
    }
}
