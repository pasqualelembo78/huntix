package com.intelligame.huntix.manager

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.Session
import com.intelligame.huntix.WorldEgg
import io.github.sceneview.node.Node

/**
 * GeospatialAnchorManager — gestisce anchor geospaziali tramite ARCore Geospatial API.
 *
 * Permette di piazzare oggetti AR a coordinate GPS reali (lat/lng/alt)
 * con precisione centimetrica dove Google Street View e' disponibile.
 *
 * Fallback: se la Geospatial API non e' disponibile (zone non coperte),
 * usa il positioning relativo via bussola + GPS come prima.
 */
class GeospatialAnchorManager {

    companion object {
        private const val TAG = "GeoAnchorMgr"
        private const val DEFAULT_ALTITUDE_M = 1.5
    }

    enum class GeoState {
        UNAVAILABLE,
        TRACKING,
        ERROR
    }

    var state: GeoState = GeoState.UNAVAILABLE
        private set

    private var earth: Earth? = null
    private val placedAnchors = mutableMapOf<String, Anchor>()
    private val anchorNodes = mutableMapOf<String, Node>()

    /**
     * Abilita il GeospatialMode sul Config fornito.
     * Da chiamare dentro sceneView.configureSession { _, config -> ... }.
     */
    fun configureSession(session: Session, config: Config) {
        try {
            config.geospatialMode = Config.GeospatialMode.ENABLED
            Log.i(TAG, "Geospatial mode abilitato")
        } catch (e: Exception) {
            Log.e(TAG, "Errore config Geospatial: ${e.message}")
        }
    }

    /**
     * Aggiorna lo stato della Terra. Da chiamare in ogni frame AR.
     */
    fun updateEarthState(session: Session) {
        try {
            val earthSession = session.earth
            if (earthSession != null) {
                earth = earthSession
                state = when (earthSession.earthState) {
                    Earth.EarthState.ENABLED -> GeoState.TRACKING
                    else -> GeoState.UNAVAILABLE
                }
            } else {
                state = GeoState.UNAVAILABLE
            }
        } catch (e: Exception) {
            state = GeoState.ERROR
            Log.w(TAG, "Earth state error: ${e.message}")
        }
    }

    fun isTracking(): Boolean = state == GeoState.TRACKING

    /**
     * Crea un anchor geospatial alle coordinate GPS fornite.
     * Restituisce l'Anchor ARCore o null se non possibile.
     */
    fun createAnchor(
        latitude: Double,
        longitude: Double,
        altitude: Double = DEFAULT_ALTITUDE_M
    ): Anchor? {
        val e = earth ?: return null
        if (!isTracking()) return null
        return try {
            val anchor = e.createAnchor(latitude, longitude, altitude, 0f, 0f, 0f, 1f)
            Log.d(TAG, "Anchor creato: lat=$latitude, lng=$longitude, alt=$altitude")
            anchor
        } catch (ex: Exception) {
            Log.e(TAG, "Errore creazione anchor: ${ex.message}")
            null
        }
    }

    /**
     * Crea un anchor geospatial per un uovo del mondo.
     * Se la Geospatial API non e' disponibile, restituisce null (fallback a compass).
     */
    fun createAnchorForEgg(egg: WorldEgg): Anchor? {
        return createAnchor(egg.lat, egg.lng, DEFAULT_ALTITUDE_M)
    }

    /**
     * Ottiene la posizione geospatiale della camera.
     */
    fun getCameraGeoPosition(): Triple<Double, Double, Double>? {
        val e = earth ?: return null
        if (!isTracking()) return null
        return try {
            val pose = e.cameraGeospatialPose
            Triple(pose.latitude, pose.longitude, pose.altitude)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Registra un nodo associato a un egg ID per gestione lifecycle.
     */
    fun trackNode(eggId: String, node: Node) {
        anchorNodes[eggId] = node
    }

    /**
     * Rimuove e distrugge l'anchor e il nodo per un uovo specifico.
     */
    fun removeAnchor(eggId: String) {
        anchorNodes[eggId]?.let { node ->
            try { node.destroy() } catch (_: Exception) {}
        }
        anchorNodes.remove(eggId)

        placedAnchors[eggId]?.let { anchor ->
            try { anchor.detach() } catch (_: Exception) {}
        }
        placedAnchors.remove(eggId)
    }

    /**
     * Rimuove tutti gli anchor e i nodi.
     */
    fun removeAll() {
        anchorNodes.values.forEach { node ->
            try { node.destroy() } catch (_: Exception) {}
        }
        anchorNodes.clear()
        placedAnchors.values.forEach { anchor ->
            try { anchor.detach() } catch (_: Exception) {}
        }
        placedAnchors.clear()
    }

    /**
     * Registra un anchor creato esternamente (per tracciamento).
     */
    fun registerAnchor(eggId: String, anchor: Anchor) {
        placedAnchors[eggId] = anchor
    }
}
