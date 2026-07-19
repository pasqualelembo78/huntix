package com.intelligame.huntix.manager

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Pose
import com.intelligame.huntix.IndoorArSync
import com.intelligame.huntix.IndoorRoomManager
import com.intelligame.huntix.IndoorSessionManager
import com.intelligame.huntix.MainActivity
import com.intelligame.huntix.SoundManager
import com.intelligame.huntix.updateUI
import com.intelligame.huntix.model.GamePhase
import com.intelligame.huntix.model.PlayState
import com.intelligame.huntix.model.SafeObject
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import android.widget.Toast

class SafeManager(internal val activity: MainActivity) {

    internal val binding get() = activity.binding
    internal val viewModel get() = activity.viewModel

    // ── Build safe ───────────────────────────────────────────────

    fun buildSafeAtAnchor(anchor: Anchor, safeType: String = viewModel.selectedSafeType) {
        val sv = binding.sceneView
        val an = AnchorNode(engine = sv.engine, anchor = anchor)
        val dialMat = sv.materialLoader.createColorInstance(color = AndroidColor.rgb(210, 170, 20))
        val dial: CylinderNode
        val door: Node
        val body: Node
        when (safeType) {
            "chest" -> {
                val bodyMat = sv.materialLoader.createColorInstance(color = AndroidColor.rgb(101, 67, 33))
                val lidMat = sv.materialLoader.createColorInstance(color = AndroidColor.rgb(139, 90, 43))
                val metalMat = sv.materialLoader.createColorInstance(color = AndroidColor.rgb(210, 170, 20))
                body = CubeNode(sv.engine, Size(0.32f, 0.26f, 0.24f), materialInstance = bodyMat).also { it.position = Position(0f, 0.13f, 0f); an.addChildNode(it) }
                door = CubeNode(sv.engine, Size(0.32f, 0.10f, 0.26f), materialInstance = lidMat).also { it.position = Position(0f, 0.31f, 0f); an.addChildNode(it) }
                CubeNode(sv.engine, Size(0.06f, 0.06f, 0.028f), materialInstance = metalMat).also { it.position = Position(0f, 0.13f, 0.125f); an.addChildNode(it) }
                dial = CylinderNode(sv.engine, 0.025f, 0.022f, materialInstance = dialMat).also { it.position = Position(0.06f, 0.13f, 0.125f); it.rotation = Rotation(90f, 0f, 0f); an.addChildNode(it) }
            }
            "vault" -> {
                val bodyMat = sv.materialLoader.createColorInstance(color = AndroidColor.rgb(70, 70, 80))
                val doorMat = sv.materialLoader.createColorInstance(color = AndroidColor.rgb(90, 90, 110))
                body = CubeNode(sv.engine, Size(0.34f, 0.38f, 0.28f), materialInstance = bodyMat).also { it.position = Position(0f, 0.19f, 0f); an.addChildNode(it) }
                door = CylinderNode(sv.engine, 0.16f, 0.030f, materialInstance = doorMat).also { it.position = Position(0f, 0.22f, 0.145f); it.rotation = Rotation(90f, 0f, 0f); an.addChildNode(it) }
                dial = CylinderNode(sv.engine, 0.040f, 0.028f, materialInstance = dialMat).also { it.position = Position(0f, 0.22f, 0.162f); it.rotation = Rotation(90f, 0f, 0f); an.addChildNode(it) }
            }
            "present" -> {
                val bodyMat = sv.materialLoader.createColorInstance(color = AndroidColor.rgb(200, 30, 80))
                val ribbonMat = sv.materialLoader.createColorInstance(color = AndroidColor.rgb(255, 215, 0))
                body = CubeNode(sv.engine, Size(0.30f, 0.30f, 0.30f), materialInstance = bodyMat).also { it.position = Position(0f, 0.15f, 0f); an.addChildNode(it) }
                door = CubeNode(sv.engine, Size(0.30f, 0.02f, 0.32f), materialInstance = ribbonMat).also { it.position = Position(0f, 0.31f, 0f); an.addChildNode(it) }
                CylinderNode(sv.engine, 0.025f, 0.32f, materialInstance = ribbonMat).also { it.position = Position(0f, 0.15f, 0f); it.rotation = Rotation(0f, 0f, 90f); an.addChildNode(it) }
                CylinderNode(sv.engine, 0.025f, 0.32f, materialInstance = ribbonMat).also { it.position = Position(0f, 0.15f, 0f); an.addChildNode(it) }
                dial = CylinderNode(sv.engine, 0.030f, 0.022f, materialInstance = dialMat).also { it.position = Position(0.06f, 0.31f, 0.01f); it.rotation = Rotation(90f, 0f, 0f); an.addChildNode(it) }
            }
            else -> { // classic
                val bodyMat = sv.materialLoader.createColorInstance(color = AndroidColor.rgb(28, 36, 46))
                val doorMat = sv.materialLoader.createColorInstance(color = AndroidColor.rgb(46, 60, 74))
                val handleMat = sv.materialLoader.createColorInstance(color = AndroidColor.rgb(185, 185, 185))
                body = CubeNode(sv.engine, Size(0.28f, 0.34f, 0.24f), materialInstance = bodyMat).also { it.position = Position(0f, 0.17f, 0f); an.addChildNode(it) }
                door = CubeNode(sv.engine, Size(0.26f, 0.32f, 0.026f), materialInstance = doorMat).also { it.position = Position(0f, 0.17f, 0.133f); an.addChildNode(it) }
                dial = CylinderNode(sv.engine, 0.036f, 0.026f, materialInstance = dialMat).also { it.position = Position(0.075f, 0.22f, 0.148f); it.rotation = Rotation(90f, 0f, 0f); an.addChildNode(it) }
                CylinderNode(sv.engine, 0.012f, 0.10f, materialInstance = handleMat).also { it.position = Position(-0.05f, 0.17f, 0.148f); it.rotation = Rotation(0f, 0f, 90f); an.addChildNode(it) }
            }
        }
        sv.addChildNode(an)
        activity.safeObject = SafeObject(safeType, an, body, door, dial)
    }

    // ── Place safe ───────────────────────────────────────────────

    fun placeSafe(hit: HitResult) {
        val sv = binding.sceneView
        val rawPose = hit.hitPose
        val leveledPose = Pose(rawPose.translation, floatArrayOf(0f, 0f, 0f, 1f))
        val anchor = hit.trackable.createAnchor(leveledPose)
        buildSafeAtAnchor(anchor, viewModel.selectedSafeType)

        if (activity.pendingCloudRestore && viewModel.isIndoorMp && viewModel.indoorRoomCode.isNotEmpty()) {
            activity.pendingCloudRestore = false
            val cached = activity.pendingRoomSnapshot
            if (cached != null) {
                activity.pendingRoomSnapshot = null
                activity.eggPlacementManager.restoreEggsWithSafeAnchor(cached, activity.safeObject!!.anchorNode.anchor)
            } else {
                activity.eggPlacementManager.restoreEggsFromCloud(activity.safeObject!!.anchorNode)
            }
        } else if (viewModel.isRestoreMode) {
            activity.eggPlacementManager.restoreEggsFromSession(activity.safeObject!!.anchorNode)
            viewModel.isRestoreMode = false
            activity.gamePhase = GamePhase.SETUP_EGGS
            activity.runOnUiThread {
                binding.tvStatus.text = "Cassaforte e uova ripristinate"
                binding.btnStart.visibility = View.VISIBLE
                Toast.makeText(activity, "${activity.eggs.size} uova ripristinate!", android.widget.Toast.LENGTH_LONG).show()
                activity.updateUI()
            }
        } else {
            activity.gamePhase = GamePhase.SETUP_EGGS
            activity.runOnUiThread {
                binding.tvStatus.text = "Cassaforte pronta"
                activity.updateUI()
            }

            if (viewModel.isIndoorMp && viewModel.indoorIsHost && viewModel.indoorRoomCode.isNotEmpty()) {
                try {
                    val session = binding.sceneView.session ?: throw IllegalStateException("Sessione AR non disponibile")
                    IndoorArSync.hostSafeAnchor(session, anchor)
                    IndoorArSync.onSafeHosted = { cloudId ->
                        IndoorSessionManager.updateSafeCloudAnchor(viewModel.indoorRoomCode, cloudId)
                        activity.runOnUiThread { Toast.makeText(activity, "Cassaforte sincronizzata!", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                    IndoorArSync.onApiKeyMissing = {
                        activity.runOnUiThread {
                            Toast.makeText(activity, "ARCore API Key mancante. Il guest dovra' piazzare la cassaforte manualmente.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    IndoorArSync.onHostingError = { idx, msg ->
                        if (idx == -1) activity.runOnUiThread {
                            android.util.Log.w("SafeManager", "Cloud Anchor safe hosting: $msg")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SafeManager", "hostSafeAnchor skipped: ${e.message}")
                }
            }

            if (viewModel.eggSetupMode == "auto" || viewModel.eggSetupMode == "combined") {
                activity.uiHandler.postDelayed({
                    activity.runOnUiThread { Toast.makeText(activity, "Piazzamento automatico...", android.widget.Toast.LENGTH_SHORT).show() }
                    activity.eggPlacementManager.autoPlaceEggs(viewModel.autoEggCount, viewModel.trapEggCount)
                }, 500)
            }
        }
    }

    // ── Key insertion ────────────────────────────────────────────

    fun insertKeyInSafe() {
        if (!activity.keyInPocket || activity.safeObject == null) return
        SoundManager.playKeyInsert()
        val safe = activity.safeObject!!
        val keyColor = MainActivity.KEY_COLORS[(activity.realEggsCaught - 1) % MainActivity.KEY_COLORS.size]
        val sv = binding.sceneView
        val mat = sv.materialLoader.createColorInstance(color = keyColor)
        val filled = CylinderNode(engine = sv.engine, radius = 0.020f, height = 0.016f, materialInstance = mat).apply {
            position = slotPositionFor(activity.realEggsCaught - 1)
            rotation = Rotation(90f, 0f, 0f)
        }
        safe.anchorNode.addChildNode(filled)
        safe.keySlots.add(filled)

        val curY = safe.dialNode.rotation.y
        ValueAnimator.ofFloat(curY, curY + 90f).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim -> safe.dialNode.rotation = Rotation(90f, anim.animatedValue as Float, 0f) }
            start()
        }

        activity.uiHandler.postDelayed({
            if (!activity.isActive || activity.isFinishing) return@postDelayed
            openSafeDoor {
                SoundManager.playSafeOpen()
                val riddleText = activity.riddles.getOrNull(activity.currentEggIdx) ?: ""
                if (riddleText.isNotEmpty()) showTicket(activity.currentEggIdx + 1, riddleText)
                else onTicketClosed()
            }
        }, 600)
    }

    private fun slotPositionFor(idx: Int): Position {
        val col = (idx % 3) - 1
        val row = idx / 3
        return Position(col * 0.055f, 0.22f - row * 0.065f, 0.148f)
    }

    // ── Safe door ────────────────────────────────────────────────

    private fun openSafeDoor(onComplete: () -> Unit) {
        val safe = activity.safeObject ?: run { onComplete(); return }
        val startDialY = safe.dialNode.rotation.y
        ValueAnimator.ofFloat(0f, 720f).apply {
            duration = 700
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                safe.dialNode.rotation = Rotation(90f, startDialY + (anim.animatedValue as Float), 0f)
            }
            start()
        }

        activity.uiHandler.postDelayed({
            if (!activity.isActive || activity.isFinishing) return@postDelayed
            val flashView = View(activity).apply {
                setBackgroundColor(AndroidColor.parseColor("#FFDD44"))
                alpha = 0f
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.MATCH_PARENT)
            }
            binding.arContainer.addView(flashView)
            flashView.animate().alpha(0.65f).setDuration(110).withEndAction {
                flashView.animate().alpha(0f).setDuration(420)
                    .withEndAction { try { binding.arContainer.removeView(flashView) } catch (_: Exception) {} }
                    .start()
            }.start()

            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 950
                interpolator = OvershootInterpolator(0.5f)
                addUpdateListener { anim ->
                    val t = anim.animatedValue as Float
                    safe.doorNode.rotation = Rotation(0f, t * -88f, 0f)
                    safe.doorNode.scale = Scale(1f - t * 0.55f, 1f, 1f)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) {
                        launchGoldenBurst()
                        onComplete()
                    }
                })
                start()
            }
        }, 600)
    }

    fun closeSafeDoor(onComplete: () -> Unit) {
        val safe = activity.safeObject ?: run { onComplete(); return }
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 700
            interpolator = AccelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                safe.doorNode.rotation = Rotation(0f, t * -88f, 0f)
                safe.doorNode.scale = Scale(1f - t * 0.55f, 1f, 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { onComplete() }
            })
            start()
        }
    }

    // ── Ticket ───────────────────────────────────────────────────

    fun showTicket(eggNumber: Int, riddleText: String) {
        activity.playState = PlayState.TICKET_SHOWN
        val stars = listOf("star", "star2", "sparkles", "mail", "party_popper", "confetti_ball")
        val star = stars[(eggNumber - 1) % stars.size]
        binding.tvRiddleTitle.text = "$star  Uovo #$eggNumber  $star"
        binding.tvRiddle.text = riddleText
        binding.btnCloseRiddle.text = "CAPITO! VADO A TROVARLO!"

        val overlay = binding.riddleOverlay
        val card = overlay.getChildAt(0)
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        card.scaleX = 0.4f
        card.scaleY = 0.15f
        card.translationY = activity.windowSizePx().second * 0.55f

        overlay.animate().alpha(1f).setDuration(200).start()
        activity.uiHandler.postDelayed({
            if (!activity.isActive || activity.isFinishing) return@postDelayed
            card.animate().scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(700).setInterpolator(OvershootInterpolator(1.8f))
                .withEndAction { wobbleCard(card) }.start()
        }, 80)
        launchSparkles()
        activity.updateUI()
    }

    private fun wobbleCard(card: View) {
        ValueAnimator.ofFloat(-4f, 4f, -2f, 2f, 0f).apply {
            duration = 500
            interpolator = LinearInterpolator()
            addUpdateListener { anim -> card.rotation = anim.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { card.rotation = 0f }
            })
            start()
        }
    }

    fun onCloseTicket() {
        val overlay = binding.riddleOverlay
        val card = overlay.getChildAt(0)
        card.animate().translationY(-activity.windowSizePx().second)
            .scaleX(0.5f).scaleY(0.5f).rotation(15f)
            .setDuration(380).setInterpolator(AccelerateInterpolator(1.5f)).start()
        overlay.animate().alpha(0f).setStartDelay(260).setDuration(220)
            .withEndAction {
                overlay.visibility = View.GONE
                overlay.alpha = 1f
                card.translationY = 0f
                card.scaleX = 1f
                card.scaleY = 1f
                card.rotation = 0f
                onTicketClosed()
            }.start()
    }
}
