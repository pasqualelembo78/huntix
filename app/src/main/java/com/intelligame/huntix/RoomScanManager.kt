package com.intelligame.huntix

import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState

class RoomScanManager {

    var isScanning: Boolean = false
        private set

    var elapsedMs: Long = 0L
        private set

    var onScanProgress: ((Int) -> Unit)? = null

    private var startTimeMs: Long = 0L
    private var forcedComplete = false
    private val scanDurationMs = 10_000L
    private var cachedPlaneCount = 0

    fun startScan(session: Session) {
        isScanning = true
        forcedComplete = false
        startTimeMs = System.currentTimeMillis()
        elapsedMs = 0L
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
    }

    fun getProgressPercent(session: Session): Int {
        if (forcedComplete) return 100
        if (!isScanning || startTimeMs == 0L) return 0
        elapsedMs = System.currentTimeMillis() - startTimeMs
        val progress = ((elapsedMs.toFloat() / scanDurationMs) * 100).toInt().coerceIn(0, 100)
        onScanProgress?.invoke(progress)
        return progress
    }

    fun getRemainingSeconds(): Int {
        if (forcedComplete) return 0
        if (!isScanning || startTimeMs == 0L) return 10
        val remaining = ((scanDurationMs - elapsedMs) / 1000).toInt().coerceIn(0, 10)
        return remaining
    }

    fun getSurfaceDescriptions(session: Session): List<String> {
        val planes = getTrackedPlaneCount(session)
        return (1..planes).map { "Piano orizzontale rilevato" }
    }

    fun getTrackedPlaneCount(session: Session): Int {
        return try {
            session.update()
            val planes = session.getAllTrackables(Plane::class.java)
            planes.count { it.trackingState == TrackingState.TRACKING }
        } catch (_: Exception) {
            cachedPlaneCount
        }.also { cachedPlaneCount = it }
    }

    fun isScanComplete(session: Session): Boolean {
        return getProgressPercent(session) >= 100
    }

    fun forceComplete() {
        forcedComplete = true
        isScanning = false
        elapsedMs = scanDurationMs
        onScanProgress?.invoke(100)
    }

    fun setPlaneCount(count: Int) { cachedPlaneCount = count }
}