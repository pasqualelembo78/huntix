package com.intelligame.huntix

import com.google.ar.core.Plane
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class RadarTarget(
    val id: String,
    val name: String,
    val semanticLabel: String,
    val customName: String,
    val distance: Float,
    val bearing: Float,
    val isCurrentTarget: Boolean = false,
    val isFound: Boolean = false,
    val iconRes: Int = 0
)

class RoomMapRepository private constructor() {

    data class SemanticPlane(
        val planeId: String,
        val semanticLabel: String,
        val centerPose: FloatArray,
        val extentX: Float,
        val extentZ: Float,
        val polygon: List<FloatArray>
    )

    data class PersistentAnchor(
        val anchorId: String,
        val cloudAnchorId: String?,
        val anchorType: String,
        val semanticLabel: String,
        val customName: String,
        val roomName: String,
        val worldPose: FloatArray,
        val relativeToSafe: FloatArray,
        val metadata: Map<String, String>,
        val createdAt: Long,
        val ttlDays: Int
    )

    data class RoomMap(
        val roomId: String,
        val name: String,
        val floorPlanImage: String?,
        val semanticPlanes: List<SemanticPlane>,
        val anchors: List<PersistentAnchor>,
        val safeAnchorId: String?,
        val createdAt: Long,
        val updatedAt: Long,
        val version: Int,
        val sceneAnchorFile: String?
    ) {
        fun toFirestore(): Map<String, Any?> = mapOf(
            "roomId" to roomId,
            "name" to name,
            "floorPlanImage" to floorPlanImage,
            "semanticPlanes" to semanticPlanes.map { it.toMap() },
            "anchors" to anchors.map { it.toMap() },
            "safeAnchorId" to safeAnchorId,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "version" to version,
            "sceneAnchorFile" to sceneAnchorFile
        )
    }

    companion object {
        private var instance: RoomMapRepository? = null
        fun get(): RoomMapRepository = instance ?: RoomMapRepository().also { instance = it }

        private const val COLLECTION = "room_maps"
        private const val MAX_TTL_DAYS = 365

        fun SemanticPlane.toMap(): Map<String, Any?> = mapOf(
            "planeId" to planeId,
            "semanticLabel" to semanticLabel,
            "centerPose" to centerPose.toList().map { it.toDouble() },
            "extentX" to extentX,
            "extentZ" to extentZ,
            "polygon" to polygon.map { it.toList().map { v -> v.toDouble() } }
        )

        fun PersistentAnchor.toMap(): Map<String, Any?> = mapOf(
            "anchorId" to anchorId,
            "cloudAnchorId" to cloudAnchorId,
            "anchorType" to anchorType,
            "semanticLabel" to semanticLabel,
            "customName" to customName,
            "roomName" to roomName,
            "worldPose" to worldPose.toList().map { it.toDouble() },
            "relativeToSafe" to relativeToSafe.toList().map { it.toDouble() },
            "metadata" to metadata,
            "createdAt" to createdAt,
            "ttlDays" to ttlDays
        )
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val realtimeDb = FirebaseDatabase.getInstance().getReference("room_maps_v2")

    suspend fun saveRoomMap(roomMap: RoomMap): Result<String> = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection(COLLECTION).document(roomMap.roomId)
            withTimeoutOrNull(30_000L) {
                docRef.set(roomMap.toFirestore(), SetOptions.merge()).await()
                realtimeDb.child(roomMap.roomId).setValue(roomMap.toFirestore()).await()
            } ?: return@withContext Result.failure(Exception("Firestore save timeout"))
            Result.success(roomMap.roomId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadRoomMap(roomId: String): Result<RoomMap> = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection(COLLECTION).document(roomId).get().await()
            if (!doc.exists()) return@withContext Result.failure(IllegalStateException("Room map non trovato: $roomId"))
            val data = doc.data ?: return@withContext Result.failure(IllegalStateException("Room map dati vuoti: $roomId"))
            Result.success(parseRoomMap(data))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listUserRooms(userId: String): Result<List<RoomMap>> = withContext(Dispatchers.IO) {
        try {
            val query = firestore.collection(COLLECTION).whereEqualTo("ownerId", userId).get().await()
            Result.success(query.documents.mapNotNull { doc -> doc.data?.let { parseRoomMap(it) } })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseRoomMap(data: Map<String, Any>): RoomMap {
        val planes = (data["semanticPlanes"] as? List<Map<String, Any>> ?: emptyList()).mapNotNull { p ->
            try {
                SemanticPlane(
                    planeId = p["planeId"] as? String ?: return@mapNotNull null,
                    semanticLabel = p["semanticLabel"] as? String ?: "UNKNOWN",
                    centerPose = (p["centerPose"] as? List<Number> ?: emptyList()).map { it.toFloat() }.toFloatArray(),
                    extentX = (p["extentX"] as? Number)?.toFloat() ?: 0f,
                    extentZ = (p["extentZ"] as? Number)?.toFloat() ?: 0f,
                    polygon = (p["polygon"] as? List<List<Number>> ?: emptyList()).map { it.map { v -> v.toFloat() }.toFloatArray() }
                )
            } catch (e: Exception) { null }
        }
        val anchors = (data["anchors"] as? List<Map<String, Any>> ?: emptyList()).mapNotNull { a ->
            try {
                PersistentAnchor(
                    anchorId = a["anchorId"] as? String ?: return@mapNotNull null,
                    cloudAnchorId = a["cloudAnchorId"] as? String,
                    anchorType = a["anchorType"] as? String ?: "unknown",
                    semanticLabel = a["semanticLabel"] as? String ?: "UNKNOWN",
                    customName = a["customName"] as? String ?: "",
                    roomName = a["roomName"] as? String ?: "",
                    worldPose = (a["worldPose"] as? List<Double> ?: emptyList()).map { it.toFloat() }.toFloatArray(),
                    relativeToSafe = (a["relativeToSafe"] as? List<Double> ?: emptyList()).map { it.toFloat() }.toFloatArray(),
                    metadata = (a["metadata"] as? Map<String, String>) ?: emptyMap(),
                    createdAt = (a["createdAt"] as? Number)?.toLong() ?: 0L,
                    ttlDays = (a["ttlDays"] as? Number)?.toInt() ?: 365
                )
            } catch (e: Exception) { null }
        }
        return RoomMap(
            roomId = data["roomId"] as? String ?: "",
            name = data["name"] as? String ?: "Stanza",
            floorPlanImage = data["floorPlanImage"] as? String,
            semanticPlanes = planes,
            anchors = anchors,
            safeAnchorId = data["safeAnchorId"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
            version = (data["version"] as? Number)?.toInt() ?: 1,
            sceneAnchorFile = data["sceneAnchorFile"] as? String
        )
    }

    fun createNewRoomMap(name: String, ownerId: String): RoomMap {
        return RoomMap(
            roomId = UUID.randomUUID().toString(),
            name = name,
            floorPlanImage = null,
            semanticPlanes = emptyList(),
            anchors = emptyList(),
            safeAnchorId = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            version = 1,
            sceneAnchorFile = null
        )
    }

    suspend fun updateAnchors(roomId: String, anchors: List<PersistentAnchor>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection(COLLECTION).document(roomId)
            docRef.update("anchors", anchors.map { it.toMap() }, "updatedAt", System.currentTimeMillis()).await()
            realtimeDb.child(roomId).child("anchors").setValue(anchors.map { it.toMap() }).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSemanticPlanes(roomId: String, planes: List<SemanticPlane>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection(COLLECTION).document(roomId)
            docRef.update("semanticPlanes", planes.map { it.toMap() }, "updatedAt", System.currentTimeMillis()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRoomMap(roomId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(COLLECTION).document(roomId).delete().await()
            realtimeDb.child(roomId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun Plane.toSemanticPlane(planeId: String): RoomMapRepository.SemanticPlane {
    val centerPose = this.centerPose
    val poseArray = floatArrayOf(
        centerPose.tx(), centerPose.ty(), centerPose.tz(),
        centerPose.qx(), centerPose.qy(), centerPose.qz(), centerPose.qw()
    )
    val polygonVertices = mutableListOf<FloatArray>()
    try {
        val polygonBuf = this.polygon
        if (polygonBuf != null) {
            val remaining = FloatArray(polygonBuf.remaining())
            polygonBuf.duplicate().get(remaining)
            var i = 0
            while (i + 2 < remaining.size) {
                polygonVertices.add(floatArrayOf(remaining[i], remaining[i + 1], remaining[i + 2]))
                i += 3
            }
        }
    } catch (e: Exception) { Sentry.captureException(e) }

    val label = when (this.type) {
        Plane.Type.HORIZONTAL_UPWARD_FACING -> "FLOOR"
        Plane.Type.HORIZONTAL_DOWNWARD_FACING -> "CEILING"
        Plane.Type.VERTICAL -> "WALL"
        else -> "UNKNOWN"
    }
    return RoomMapRepository.SemanticPlane(
        planeId = planeId,
        semanticLabel = label,
        centerPose = poseArray,
        extentX = this.extentX,
        extentZ = this.extentZ,
        polygon = polygonVertices
    )
}
