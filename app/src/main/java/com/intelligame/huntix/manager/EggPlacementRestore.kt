package com.intelligame.huntix.manager

import android.widget.Toast
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.intelligame.huntix.IndoorArSync
import com.intelligame.huntix.IndoorSessionManager
import com.intelligame.huntix.MainActivity
import com.intelligame.huntix.model.EggObject
import com.intelligame.huntix.model.GamePhase
import com.intelligame.huntix.updateUI
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode

internal fun EggPlacementManager.restoreEggsFromCloud(safeAnchorNode: AnchorNode) {
    activity.runOnUiThread {
        binding.tvStatus.text = "Caricamento stanza dal cloud..."
        binding.btnStart.visibility = android.view.View.GONE
    }

    IndoorSessionManager.getRoomSnapshot(
        code = viewModel.indoorRoomCode,
        onSuccess = { snap ->
            val sv = binding.sceneView
            val safeCloudId = snap.safe.cloudAnchorId
            if (safeCloudId.isNotBlank() && sv.session != null) {
                activity.runOnUiThread { binding.tvStatus.text = "Risoluzione cassaforte dal cloud..." }
                try {
                    IndoorArSync.resolveSafeAnchor(sv.session!!, safeCloudId)
                    IndoorArSync.onSafeResolved = { resolvedSafeAnchor ->
                        android.util.Log.d("EggPlacement", "Cassaforte risolto da Cloud")
                        restoreEggsWithSafeAnchor(snap, resolvedSafeAnchor)
                    }
                     IndoorArSync.onResolvingError = { eggIdx, msg ->
                         if (eggIdx == -1) {
                             android.util.Log.w("EggPlacement", "Cloud cassaforte fallito, usando fallback")
                             restoreEggsWithSafeAnchor(snap, safeAnchorNode.anchor)
                         }
                     }
                    IndoorArSync.onApiKeyMissing = {
                        android.util.Log.w("EggPlacement", "ARCore API Key mancante, usando fallback")
                        restoreEggsWithSafeAnchor(snap, safeAnchorNode.anchor)
                    }
                    return@getRoomSnapshot
                } catch (e: Exception) {
                    android.util.Log.e("EggPlacement", "resolveSafeAnchor exception: ${e.message}")
                }
            }
            restoreEggsWithSafeAnchor(snap, safeAnchorNode.anchor)
        },
        onError = { msg ->
            activity.runOnUiThread {
                Toast.makeText(activity, "Errore caricamento stanza: $msg", Toast.LENGTH_LONG).show()
                activity.gamePhase = GamePhase.SETUP_EGGS
                activity.updateUI()
            }
        }
    )
}

internal fun EggPlacementManager.restoreEggsWithSafeAnchor(
    snap: IndoorSessionManager.RoomSnapshot,
    safeAnchor: Anchor
) {
    val eggList = snap.eggs.filter { !it.found }
    if (eggList.isEmpty()) {
        activity.runOnUiThread {
            Toast.makeText(activity, "Nessuna uova da trovare (tutte gia' trovate).", Toast.LENGTH_LONG).show()
            activity.gamePhase = GamePhase.SETUP_EGGS
            binding.btnStart.visibility = android.view.View.VISIBLE
            activity.updateUI()
        }
        return
    }

    activity.runOnUiThread { binding.tvStatus.text = "Caricamento uova..." }

    val sv = binding.sceneView
    val safeTrans = safeAnchor.pose.translation
    var restored = 0

    fun placeOne(egg: IndoorSessionManager.IndoorEggData) {
        val advance = {
            restored++
            if (restored >= eggList.size) finishRestore() else placeOne(eggList[restored])
        }
        try {
            val cloudId = egg.cloudAnchorId
            if (cloudId.isNotBlank() && viewModel.isIndoorMp && sv.session != null) {
                val session = sv.session!!
                // Risoluzione SEQUENZIALE: IndoorArSync gestisce un solo
                // resolve alla volta, quindi non si possono lanciare tutti
                // insieme (altrimenti le callback si sovrascrivono a vicenda).
                IndoorArSync.onEggResolved = { _, resolvedAnchor ->
                    activity.runOnUiThread {
                        placeEggAtAnchorDirect(resolvedAnchor, egg.colorIdx, egg.shape, egg.isTrap)
                        advance()
                    }
                }
                IndoorArSync.onResolvingError = { _, _ ->
                    activity.runOnUiThread {
                        placeEggByOffset(sv, safeTrans, egg)
                        advance()
                    }
                }
                IndoorArSync.resolveEggAnchor(session, cloudId, egg.idx)
                return
            }
            placeEggByOffset(sv, safeTrans, egg)
            advance()
        } catch (e: Exception) {
            android.util.Log.e("EggPlacement", "restoreEggsWithSafeAnchor egg[${egg.idx}]: ${e.message}")
            placeEggByOffset(sv, safeTrans, egg)
            advance()
        }
    }

    placeOne(eggList[0])
}

internal fun EggPlacementManager.finishRestore() {
    binding.tvStatus.text = "Stanza caricata dal cloud"
    binding.btnStart.visibility = android.view.View.VISIBLE
    activity.gamePhase = GamePhase.SETUP_EGGS
    activity.updateUI()
    Toast.makeText(activity, "Cassaforte e uova caricate!", Toast.LENGTH_SHORT).show()
}

internal fun EggPlacementManager.placeEggByOffset(
    sv: ARSceneView,
    safeTrans: FloatArray,
    egg: IndoorSessionManager.IndoorEggData
) {
    val wx = safeTrans[0] + egg.dx
    val wy = safeTrans[1] + egg.dy
    val wz = safeTrans[2] + egg.dz
    try {
        val anchor = sv.session?.createAnchor(
            Pose(floatArrayOf(wx, wy, wz), floatArrayOf(0f, 0f, 0f, 1f))
        ) ?: return
        placeEggAtAnchorDirect(anchor, egg.colorIdx, egg.shape, egg.isTrap)
    } catch (_: Exception) {}
}

/**
 * Ripristina uova salvate in locale tramite [LocalAnchorStore].
 *
 * Ogni [LocalAnchorStore.LocalAnchor] contiene la pose mondiale assoluta
 * dell'uovo (`eggTrans`/`eggRot`); [LocalAnchorStore.buildAnchor] ricrea
 * l'[Anchor] ARCore corrispondente. Funziona per ripristino nella stessa
 * stanza in cui è stato fatto il salvataggio.
 */
internal fun EggPlacementManager.restoreFromLocalStore(safeAnchorNode: AnchorNode) {
    val sessionId = activity.localAnchorSessionId
    if (sessionId.isEmpty()) return
    val stored = activity.localAnchorStore.load(sessionId) ?: return
    val sv = binding.sceneView
    val session = sv.session ?: return
    if (stored.anchors.isEmpty()) return

    activity.runOnUiThread { binding.tvStatus.text = "Ripristino uova dalla sessione locale..." }

    var restored = 0
    stored.anchors.forEachIndexed { i, la ->
        try {
            val anchor = activity.localAnchorStore.buildAnchor(session, la) ?: return@forEachIndexed
            val colorIdx = la.colorIdx % MainActivity.EGG_COLORS.size
            val mat = sv.materialLoader.createColorInstance(color = MainActivity.EGG_COLORS[colorIdx])
            val an = AnchorNode(sv.engine, anchor)
            val eggNode: Node = when (la.shape) {
                "cube" -> CubeNode(sv.engine, Size(0.10f, 0.12f, 0.10f), materialInstance = mat).apply { position = Position(0f, 0.075f, 0f) }
                "cylinder" -> CylinderNode(sv.engine, 0.055f, 0.12f, materialInstance = mat).apply { position = Position(0f, 0.075f, 0f) }
                "diamond" -> CubeNode(sv.engine, Size(0.09f, 0.13f, 0.09f), materialInstance = mat).apply { position = Position(0f, 0.075f, 0f); rotation = Rotation(45f, 45f, 0f) }
                else -> SphereNode(sv.engine, 0.055f, materialInstance = mat).apply { position = Position(0f, 0.075f, 0f); scale = Scale(1f, 1.45f, 1f) }
            }
            an.addChildNode(eggNode)
            an.isVisible = true
            sv.addChildNode(an)
            activity.eggs.add(EggObject(i, la.colorIdx, la.shape, an, eggNode, la.isTrap))
            restored++
        } catch (e: Exception) {
            android.util.Log.e("EggPlacement", "restoreFromLocalStore egg[$i]: ${e.message}")
        }
    }

    activity.runOnUiThread {
        binding.tvStatus.text = "Cassaforte e uova ripristinate"
        binding.btnStart.visibility = android.view.View.VISIBLE
        Toast.makeText(activity, "$restored uova ripristinate dalla sessione locale!", Toast.LENGTH_LONG).show()
        activity.gamePhase = GamePhase.SETUP_EGGS
        activity.updateUI()
    }
}

internal fun EggPlacementManager.restoreEggsFromSession(safeAnchorNode: AnchorNode) {
    val session = activity.loadedSession ?: return
    val sv = binding.sceneView
    val safeTrans = safeAnchorNode.anchor.pose.translation
    session.eggOffsets.forEachIndexed { i, offset ->
        val wx = safeTrans[0] + offset[0]
        val wy = safeTrans[1] + offset[1]
        val wz = safeTrans[2] + offset[2]
        try {
            val anchor = sv.session?.createAnchor(Pose(floatArrayOf(wx, wy, wz), floatArrayOf(0f, 0f, 0f, 1f))) ?: return@forEachIndexed
            val colorIdx = session.eggColors.getOrElse(i) { i % MainActivity.EGG_COLORS.size }
            val shape = session.eggShapes.getOrElse(i) { "sphere" }
            val isTrap = session.trapMask.getOrElse(i) { false }
            val mat = sv.materialLoader.createColorInstance(color = MainActivity.EGG_COLORS[colorIdx % MainActivity.EGG_COLORS.size])
            val an = AnchorNode(sv.engine, anchor)
            val eggNode: Node = when (shape) {
                "cube" -> CubeNode(sv.engine, Size(0.10f, 0.12f, 0.10f), materialInstance = mat).apply { position = Position(0f, 0.075f, 0f) }
                "cylinder" -> CylinderNode(sv.engine, 0.055f, 0.12f, materialInstance = mat).apply { position = Position(0f, 0.075f, 0f) }
                "diamond" -> CubeNode(sv.engine, Size(0.09f, 0.13f, 0.09f), materialInstance = mat).apply { position = Position(0f, 0.075f, 0f); rotation = Rotation(45f, 45f, 0f) }
                else -> SphereNode(sv.engine, 0.055f, materialInstance = mat).apply { position = Position(0f, 0.075f, 0f); scale = Scale(1f, 1.45f, 1f) }
            }
            an.addChildNode(eggNode)
            an.isVisible = true
            sv.addChildNode(an)
            activity.eggs.add(EggObject(i, colorIdx, shape, an, eggNode, isTrap))
        } catch (_: Exception) {}
    }
}
