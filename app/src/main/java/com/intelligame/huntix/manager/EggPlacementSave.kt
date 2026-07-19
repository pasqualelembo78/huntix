package com.intelligame.huntix.manager

import android.widget.Toast
import com.google.ar.core.Anchor
import com.intelligame.huntix.GameDataManager
import com.intelligame.huntix.IndoorSessionManager
import com.intelligame.huntix.MainActivity
import com.intelligame.huntix.updateUI

internal fun EggPlacementManager.saveAnchorLocally(eggId: Int, anchor: Anchor, colorIdx: Int, shape: String, isTrap: Boolean) {
    val safePose = activity.safeObject?.anchorNode?.anchor?.pose ?: return
    val eggPose = anchor.pose
    val localAnchor = activity.localAnchorStore.buildAnchor(
        id = eggId,
        refTrans = safePose.translation,
        refRot = safePose.rotationQuaternion,
        eggTrans = eggPose.translation,
        eggRot = eggPose.rotationQuaternion,
        colorIdx = colorIdx,
        shape = shape,
        isTrap = isTrap,
        label = if (isTrap) "Trappola #${eggId + 1}" else "Uovo #${eggId + 1}"
    )
    activity.localAnchors.add(localAnchor)
    activity.localSaveCount++
    activity.runOnUiThread { updateLocalBadge() }
    persistLocalSession()
}

internal fun EggPlacementManager.persistLocalSession() {
    if (viewModel.arMode != "room_scan") return
    val safePose = activity.safeObject?.anchorNode?.anchor?.pose ?: return
    val ttl = activity.dm.getLocalAnchorTtlDays()
    val session = if (activity.localAnchorSessionId.isEmpty()) {
        val s = activity.localAnchorStore.createSession(
            name = "Caccia del ${activity.dm.todayString()}",
            ttlDays = ttl,
            refDescription = "Cassaforte - piazza nello stesso punto per ripristinare",
            anchors = activity.localAnchors.toList()
        )
        activity.localAnchorSessionId = s.sessionId
        s
    } else {
        activity.localAnchorStore.load(activity.localAnchorSessionId)?.copy(anchors = activity.localAnchors.toList())
            ?: activity.localAnchorStore.createSession(
                name = "Caccia del ${activity.dm.todayString()}",
                ttlDays = ttl,
                anchors = activity.localAnchors.toList()
            )
    }
    activity.localAnchorStore.save(session)
}

internal fun EggPlacementManager.updateLocalBadge() {
    if (viewModel.arMode != "room_scan") return
    val total = activity.eggs.size
    val saved = activity.localAnchors.size
    binding.tvStatus.text = when {
        saved == total && total > 0 -> "$saved/$total salvati"
        saved < total -> "$saved/$total..."
        else -> "-"
    }
}

internal fun EggPlacementManager.saveCurrentSession() {
    val safeTrans = activity.safeObject?.anchorNode?.anchor?.pose?.translation
    val offsets = if (safeTrans != null) activity.eggs.map { egg ->
        val t = egg.anchorNode.anchor.pose.translation
        floatArrayOf(t[0] - safeTrans[0], t[1] - safeTrans[1], t[2] - safeTrans[2])
    } else emptyList()
    val slotId = if (viewModel.isRestoreMode && viewModel.restoreSlotId.isNotBlank()) viewModel.restoreSlotId else activity.dm.newRunId()
    activity.dm.upsertSaveSlot(GameDataManager.SavedSession(
        id = slotId,
        savedAt = activity.dm.todayString(),
        slotName = "Partita del ${activity.dm.todayString()}",
        players = activity.activePlayers,
        eggCount = activity.eggs.size,
        riddles = activity.riddles,
        parentNote = "",
        eggOffsets = offsets,
        eggColors = activity.eggs.map { it.colorIdx },
        eggShapes = activity.eggs.map { it.shape },
        trapMask = activity.eggs.map { it.isTrap },
        safeType = viewModel.selectedSafeType,
        turnMode = viewModel.turnMode
    ))

    if (viewModel.isIndoorMp && viewModel.indoorIsHost && viewModel.indoorRoomCode.isNotEmpty() && safeTrans != null) {
        val safePose = activity.safeObject?.anchorNode?.anchor?.pose
        val safeWorldTrans = safePose?.translation ?: floatArrayOf(0f, 0f, 0f)
        val cloudEggs = activity.eggs.mapIndexed { i, egg ->
            val t = egg.anchorNode.anchor.pose.translation
            IndoorSessionManager.IndoorEggData(
                idx = i,
                dx = t[0] - safeWorldTrans[0],
                dy = t[1] - safeWorldTrans[1],
                dz = t[2] - safeWorldTrans[2],
                colorIdx = egg.colorIdx,
                shape = egg.shape,
                isTrap = egg.isTrap
            )
        }
        val safeData = IndoorSessionManager.SafeData(
            safeType = viewModel.selectedSafeType,
            dx = safeWorldTrans[0],
            dy = safeWorldTrans[1],
            dz = safeWorldTrans[2]
        )
        IndoorSessionManager.saveEggSetup(
            code = viewModel.indoorRoomCode,
            safe = safeData,
            eggs = cloudEggs,
            onSuccess = {
                android.util.Log.d("EggPlacement", "Setup cassaforte + uova salvato su cloud")
            },
            onError = { e ->
                android.util.Log.e("EggPlacement", "Errore cloud setup: $e")
                Toast.makeText(activity, "Errore salvataggio cloud: $e", Toast.LENGTH_LONG).show()
            }
        )
    }
}
