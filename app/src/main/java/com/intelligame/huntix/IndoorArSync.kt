package com.intelligame.huntix

import android.os.Handler
import android.os.Looper
import com.google.ar.core.Anchor
import com.google.ar.core.Anchor.CloudAnchorState
import com.google.ar.core.Session
import com.intelligame.huntix.BuildConfig
import io.github.sceneview.ar.node.AnchorNode

object IndoorArSync {
    var hasPendingOperations: Boolean = false
    var onSafeResolved: ((Anchor) -> Unit)? = null
    var onResolvingError: ((Int, String) -> Unit)? = null
    var onSafeHosted: ((String) -> Unit)? = null
    var onApiKeyMissing: (() -> Unit)? = null
    var onHostingError: ((Int, String) -> Unit)? = null
    var onEggHosted: ((Int, String) -> Unit)? = null
    var onEggResolved: ((Int, Anchor) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var hostedSafe: Anchor? = null
    private var resolvingSafe: Anchor? = null
    private var hostedEgg: Pair<Int, Anchor>? = null
    private var resolvingEgg: Pair<Int, Anchor>? = null

    fun clearCallbacks() {
        onSafeResolved = null; onResolvingError = null; onSafeHosted = null
        onApiKeyMissing = null; onHostingError = null; onEggHosted = null; onEggResolved = null
    }
    fun reset() {
        clearCallbacks(); hasPendingOperations = false
        hostedSafe = null; resolvingSafe = null; hostedEgg = null; resolvingEgg = null
        handler.removeCallbacksAndMessages(null)
    }

    // ── Safe ──────────────────────────────────────────────────────

    fun hostSafeAnchor(session: Session, anchor: Anchor) {
        if (BuildConfig.ARCORE_API_KEY.isBlank()) { onApiKeyMissing?.invoke(); return }
        hasPendingOperations = true
        val hosted = runCatching { session.hostCloudAnchor(anchor) }.getOrElse { e ->
            hasPendingOperations = false
            onHostingError?.invoke(-1, e.message ?: "host failed")
            return
        }
        hostedSafe = hosted
        pollHostSafe()
    }

    private fun pollHostSafe() {
        val a = hostedSafe ?: return
        when (val st = a.cloudAnchorState) {
            CloudAnchorState.SUCCESS -> {
                hasPendingOperations = false
                onSafeHosted?.invoke(a.cloudAnchorId)
                hostedSafe = null
            }
            CloudAnchorState.ERROR_NOT_AUTHORIZED, CloudAnchorState.ERROR_RESOURCE_EXHAUSTED -> {
                hasPendingOperations = false
                onApiKeyMissing?.invoke()
                hostedSafe = null
            }
            CloudAnchorState.NONE -> handler.postDelayed({ pollHostSafe() }, 250)
            else -> {
                if (st.name.startsWith("ERROR")) {
                    hasPendingOperations = false
                    onHostingError?.invoke(-1, st.name)
                    hostedSafe = null
                } else {
                    handler.postDelayed({ pollHostSafe() }, 250)
                }
            }
        }
    }

    fun resolveSafeAnchor(session: Session, anchorId: String) {
        if (BuildConfig.ARCORE_API_KEY.isBlank()) { onApiKeyMissing?.invoke(); return }
        hasPendingOperations = true
        val resolved = runCatching { session.resolveCloudAnchor(anchorId) }.getOrElse { e ->
            hasPendingOperations = false
            onResolvingError?.invoke(-1, e.message ?: "resolve failed")
            return
        }
        resolvingSafe = resolved
        pollResolveSafe()
    }

    private fun pollResolveSafe() {
        val a = resolvingSafe ?: return
        when (val st = a.cloudAnchorState) {
            CloudAnchorState.SUCCESS -> {
                hasPendingOperations = false
                onSafeResolved?.invoke(a)
                resolvingSafe = null
            }
            CloudAnchorState.ERROR_NOT_AUTHORIZED, CloudAnchorState.ERROR_RESOURCE_EXHAUSTED -> {
                hasPendingOperations = false
                onApiKeyMissing?.invoke()
                resolvingSafe = null
            }
            CloudAnchorState.NONE -> handler.postDelayed({ pollResolveSafe() }, 250)
            else -> {
                if (st.name.startsWith("ERROR")) {
                    hasPendingOperations = false
                    onResolvingError?.invoke(-1, st.name)
                    resolvingSafe = null
                } else {
                    handler.postDelayed({ pollResolveSafe() }, 250)
                }
            }
        }
    }

    // ── Eggs ─────────────────────────────────────────────────────

    fun hostEggAnchor(session: Session, anchor: Anchor, idx: Int, colorIdx: Int, shape: String, isTrap: Boolean) {
        if (BuildConfig.ARCORE_API_KEY.isBlank()) { onApiKeyMissing?.invoke(); return }
        hasPendingOperations = true
        val hosted = runCatching { session.hostCloudAnchor(anchor) }.getOrElse { e ->
            hasPendingOperations = false
            onHostingError?.invoke(idx, e.message ?: "host egg failed")
            return
        }
        hostedEgg = idx to hosted
        pollHostEgg()
    }

    private fun pollHostEgg() {
        val (idx, a) = hostedEgg ?: return
        when (val st = a.cloudAnchorState) {
            CloudAnchorState.SUCCESS -> {
                hasPendingOperations = false
                onEggHosted?.invoke(idx, a.cloudAnchorId)
                hostedEgg = null
            }
            CloudAnchorState.ERROR_NOT_AUTHORIZED, CloudAnchorState.ERROR_RESOURCE_EXHAUSTED -> {
                hasPendingOperations = false
                onApiKeyMissing?.invoke()
                hostedEgg = null
            }
            CloudAnchorState.NONE -> handler.postDelayed({ pollHostEgg() }, 250)
            else -> {
                if (st.name.startsWith("ERROR")) {
                    hasPendingOperations = false
                    onHostingError?.invoke(idx, st.name)
                    hostedEgg = null
                } else {
                    handler.postDelayed({ pollHostEgg() }, 250)
                }
            }
        }
    }

    fun resolveEggAnchor(session: Session, cloudId: String, idx: Int) {
        if (BuildConfig.ARCORE_API_KEY.isBlank()) { onApiKeyMissing?.invoke(); return }
        hasPendingOperations = true
        val resolved = runCatching { session.resolveCloudAnchor(cloudId) }.getOrElse { e ->
            hasPendingOperations = false
            onResolvingError?.invoke(idx, e.message ?: "resolve egg failed")
            return
        }
        resolvingEgg = idx to resolved
        pollResolveEgg()
    }

    private fun pollResolveEgg() {
        val (idx, a) = resolvingEgg ?: return
        when (val st = a.cloudAnchorState) {
            CloudAnchorState.SUCCESS -> {
                hasPendingOperations = false
                onEggResolved?.invoke(idx, a)
                resolvingEgg = null
            }
            CloudAnchorState.ERROR_NOT_AUTHORIZED, CloudAnchorState.ERROR_RESOURCE_EXHAUSTED -> {
                hasPendingOperations = false
                onApiKeyMissing?.invoke()
                resolvingEgg = null
            }
            CloudAnchorState.NONE -> handler.postDelayed({ pollResolveEgg() }, 250)
            else -> {
                if (st.name.startsWith("ERROR")) {
                    hasPendingOperations = false
                    onResolvingError?.invoke(idx, st.name)
                    resolvingEgg = null
                } else {
                    handler.postDelayed({ pollResolveEgg() }, 250)
                }
            }
        }
    }
}
