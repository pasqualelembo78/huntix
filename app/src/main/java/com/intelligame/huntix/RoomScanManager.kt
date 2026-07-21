package com.intelligame.huntix

import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.intelligame.huntix.RoomMapRepository.SemanticPlane

class RoomScanManager {

    var isScanning: Boolean = false
        private set

    var elapsedMs: Long = 0L
        private set

    var onScanProgress: ((Int) -> Unit)? = null
    var onSemanticPlanesUpdated: ((List<SemanticPlane>) -> Unit)? = null

    private var startTimeMs: Long = 0L
    private var forcedComplete = false
    private val scanDurationMs = 10_000L
    private val minScanMs = 4_000L
    private val maxScanMs = 60_000L
    private val requiredPlanes = 5
    private var cachedPlaneCount = 0
    private var lastSemanticUpdateMs = 0L

    fun startScan(session: Session) {
        isScanning = true
        forcedComplete = false
        startTimeMs = System.currentTimeMillis()
        elapsedMs = 0L
        lastSemanticUpdateMs = 0L
    }

    fun stopScan() {
        isScanning = false
        if (startTimeMs > 0) {
            elapsedMs = System.currentTimeMillis() - startTimeMs
        }
    }

    fun reset() {
        isScanning = false
        startTimeMs = 0L
        elapsedMs = 0L
        forcedComplete = false
        cachedPlaneCount = 0
        lastSemanticUpdateMs = 0L
    }

    fun getProgressPercent(session: Session): Int {
        if (forcedComplete) return 100
        if (!isScanning || startTimeMs == 0L) return 0
        elapsedMs = System.currentTimeMillis() - startTimeMs
        val timeProgress = ((elapsedMs.toFloat() / maxScanMs) * 100).toInt().coerceIn(0, 100)
        val planes = getTrackedPlaneCount(session)
        val surfaceProgress = ((planes.toFloat() / requiredPlanes) * 100).toInt().coerceIn(0, 100)
        val progress = maxOf(timeProgress, surfaceProgress).coerceIn(0, 100)
        onScanProgress?.invoke(progress)

        if (elapsedMs - lastSemanticUpdateMs > 2000) {
            lastSemanticUpdateMs = elapsedMs
            extractSemanticPlanes(session)
        }
        return progress
    }

    fun getRemainingSeconds(): Int {
        if (forcedComplete) return 0
        if (!isScanning || startTimeMs == 0L) return 60
        val remaining = ((maxScanMs - elapsedMs) / 1000).toInt().coerceIn(0, 60)
        return remaining
    }

    fun getSurfaceDescriptions(session: Session): List<String> {
        val planes = session.getAllTrackables(Plane::class.java)
            .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
        return planes.map { plane ->
            "Piano ${plane.type.name} ${String.format("%.1f", plane.extentX)}x${String.format("%.1f", plane.extentZ)}m"
        }
    }

    fun getTrackedPlaneCount(session: Session): Int {
        return session.getAllTrackables(Plane::class.java).count {
            it.trackingState == TrackingState.TRACKING && it.subsumedBy == null
        }
    }

    fun isScanComplete(session: Session): Boolean {
        if (forcedComplete) return true
        val planes = getTrackedPlaneCount(session)
        val timeElapsed = System.currentTimeMillis() - startTimeMs
        return planes >= requiredPlanes && timeElapsed >= minScanMs
    }

    fun forceComplete() {
        forcedComplete = true
    }

    private fun extractSemanticPlanes(session: Session) {
        val planes = session.getAllTrackables(Plane::class.java)
            .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
            .mapIndexed { index, plane -> plane.toSemanticPlane("plane_$index") }
        onSemanticPlanesUpdated?.invoke(planes)
    }

    suspend fun saveSemanticPlanes(roomId: String, session: Session) {
        val planes = session.getAllTrackables(Plane::class.java)
            .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
            .mapIndexed { index, plane -> plane.toSemanticPlane("plane_$index") }
        RoomMapRepository.get().updateSemanticPlanes(roomId, planes)
    }
}
