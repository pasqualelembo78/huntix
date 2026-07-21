package com.intelligame.huntix

import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.intelligame.huntix.RoomMapRepository.SemanticPlane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    private val maxScanMs = 60_000L  // Aumentato per scansione completa casa
    private val requiredPlanes = 5    // Più piani per mappatura completa
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
        
        // Aggiorna piani semantici ogni 2 secondi durante la scansione
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
        return planes.map { "Piano ${it.type.name} ${it.extentX:.1f}x${it.extentZ:.1f}m [${it.semanticLabel.name}]" }
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

    /** Estrae tutti i piani semantici rilevati e li passa al callback */
    private fun extractSemanticPlanes(session: Session) {
        val planes = session.getAllTrackables(Plane::class.java)
            .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
            .mapIndexed { index, plane -> plane.toSemanticPlane("plane_$index") }
        
        onSemanticPlanesUpdated?.invoke(planes)
    }

    /** Salva i piani semantici nel repository (chiamato al completamento scansione) */
    suspend fun saveSemanticPlanes(roomId: String, session: Session) {
        val planes = session.getAllTrackables(Plane::class.java)
            .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
            .mapIndexed { index, plane -> plane.toSemanticPlane("plane_$index") }
        
        RoomMapRepository.get().updateSemanticPlanes(roomId, planes)
    }

    companion object {
        /** Etichette semantiche ARCore supportate (API 1.32+) */
        val SEMANTIC_LABELS = mapOf(
            Plane.SemanticLabel.WALL to "Muro",
            Plane.SemanticLabel.FLOOR to "Pavimento",
            Plane.SemanticLabel.CEILING to "Soffitto",
            Plane.SemanticLabel.TABLE to "Tavolo",
            Plane.SemanticLabel.CHAIR to "Sedia",
            Plane.SemanticLabel.DOOR to "Porta",
            Plane.SemanticLabel.WINDOW to "Finestra",
            Plane.SemanticLabel.SHELF to "Scaffale",
            Plane.SemanticLabel.CABINET to "Mobile/Armadio",
            Plane.SemanticLabel.PLANTER to "Fioriera/Vaso",
            Plane.SemanticLabel.COUNTER to "Bancone/Top",
            Plane.SemanticLabel.DESK to "Scrivania",
            Plane.SemanticLabel.BED to "Letto",
            Plane.SemanticLabel.SOFA to "Divano"
        )
    }
}

// Estensione per convertire Plane ARCore in SemanticPlane
fun Plane.toSemanticPlane(planeId: String): SemanticPlane {
    val centerPose = this.centerPose
    val poseArray = floatArrayOf(
        centerPose.tx(), centerPose.ty(), centerPose.tz(),
        centerPose.qx(), centerPose.qy(), centerPose.qz(), centerPose.qw()
    )
    val polygonVertices = mutableListOf<FloatArray>()
    this.polygon?.forEach { point ->
        polygonVertices.add(floatArrayOf(point.x, point.y, point.z))
    }
    val label = when (this.semanticLabel) {
        Plane.SemanticLabel.WALL -> "WALL"
        Plane.SemanticLabel.FLOOR -> "FLOOR"
        Plane.SemanticLabel.CEILING -> "CEILING"
        Plane.SemanticLabel.TABLE -> "TABLE"
        Plane.SemanticLabel.CHAIR -> "CHAIR"
        Plane.SemanticLabel.DOOR -> "DOOR"
        Plane.SemanticLabel.WINDOW -> "WINDOW"
        Plane.SemanticLabel.SHELF -> "SHELF"
        Plane.SemanticLabel.CABINET -> "CABINET"
        Plane.SemanticLabel.PLANTER -> "PLANTER"
        Plane.SemanticLabel.COUNTER -> "COUNTER"
        Plane.SemanticLabel.DESK -> "DESK"
        Plane.SemanticLabel.BED -> "BED"
        Plane.SemanticLabel.SOFA -> "SOFA"
        else -> "UNKNOWN"
    }
    return SemanticPlane(
        planeId = planeId,
        semanticLabel = label,
        centerPose = poseArray,
        extentX = this.extentX,
        extentZ = this.extentZ,
        polygon = polygonVertices
    )
}