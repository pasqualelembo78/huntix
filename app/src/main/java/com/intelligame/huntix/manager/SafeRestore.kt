package com.intelligame.huntix.manager

import android.view.View
import android.widget.Toast
import com.intelligame.huntix.IndoorArSync
import com.intelligame.huntix.IndoorSessionManager

internal fun SafeManager.showSafeTypePicker() {
    val unlocked = activity.dm.getUnlockedSafes()
    val allTypes = listOf("classic" to "Cassaforte", "chest" to "Forziere", "vault" to "Vault", "present" to "Regalo")
    val avail = allTypes.filter { (t, _) -> unlocked.contains(t) }
    if (avail.size <= 1) {
        viewModel.selectedSafeType = "classic"
        return
    }
    android.app.AlertDialog.Builder(activity)
        .setTitle("Scegli il tipo di cassaforte")
        .setItems(avail.map { (t, n) -> if (t == viewModel.selectedSafeType) "$n (selezionata)" else n }.toTypedArray()) { _, idx ->
            viewModel.selectedSafeType = avail[idx].first
            Toast.makeText(activity, "Cassaforte: ${avail[idx].second}", Toast.LENGTH_SHORT).show()
        }
        .show()
}

internal fun SafeManager.attemptAutoSafeRestore() {
    binding.tvInstruction.text = "Caricamento cassaforte dal cloud..."
    binding.tvInstruction.visibility = View.VISIBLE

    IndoorSessionManager.getRoomSnapshot(
        code = viewModel.indoorRoomCode,
        onSuccess = { snap ->
            activity.pendingRoomSnapshot = snap
            val cloudId = snap.safe.cloudAnchorId
            val sv = binding.sceneView
            if (cloudId.isNotBlank() && sv.session != null) {
                activity.runOnUiThread {
                    binding.tvInstruction.text = "Rilevamento posizione cassaforte..."
                }
                try {
                    IndoorArSync.resolveSafeAnchor(sv.session!!, cloudId)
                    IndoorArSync.onSafeResolved = { resolvedAnchor ->
                        activity.runOnUiThread {
                            activity.pendingCloudRestore = false
                            buildSafeAtAnchor(resolvedAnchor, snap.safe.safeType)
                            binding.tvInstruction.visibility = View.GONE
                            activity.eggPlacementManager.restoreEggsWithSafeAnchor(snap, resolvedAnchor)
                        }
                    }
                     IndoorArSync.onResolvingError = { eggIdx, msg ->
                         if (eggIdx == -1) {
                             android.util.Log.w("SafeManager", "Cloud Anchor cassaforte fallito ($msg) - piazzamento manuale")
                             activity.runOnUiThread {
                                 binding.tvInstruction.text = "Piazza la cassaforte NELLO STESSO PUNTO dell'host"
                                 showSafeTypePicker()
                             }
                         }
                     }
                     IndoorArSync.onApiKeyMissing = {
                         android.util.Log.w("SafeManager", "ARCore API Key mancante - piazzamento manuale")
                         activity.runOnUiThread {
                             binding.tvInstruction.text = "Piazza la cassaforte NELLO STESSO PUNTO dell'host"
                             showSafeTypePicker()
                         }
                     }
                } catch (e: Exception) {
                    android.util.Log.e("SafeManager", "attemptAutoSafeRestore exception: ${e.message}")
                    activity.runOnUiThread {
                        binding.tvInstruction.text = "Piazza la cassaforte NELLO STESSO PUNTO dell'host"
                        showSafeTypePicker()
                    }
                }
            } else {
                activity.runOnUiThread {
                    binding.tvInstruction.text = "Piazza la cassaforte NELLO STESSO PUNTO dell'host"
                    showSafeTypePicker()
                }
            }
        },
        onError = { msg ->
            android.util.Log.w("SafeManager", "getRoomSnapshot failed: $msg")
            activity.runOnUiThread {
                binding.tvInstruction.text = "Piazza la cassaforte NELLO STESSO PUNTO dell'host"
                showSafeTypePicker()
            }
        }
    )
}
