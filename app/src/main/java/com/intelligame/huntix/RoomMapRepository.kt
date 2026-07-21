package com.intelligame.huntix

import com.google.ar.core.Plane
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
            "centerPose" to centerPose,
            "extentX" to extentX,
            "extentZ" to extentZ,
            "polygon" to polygon
        )

        fun PersistentAnchor.toMap(): Map<String, Any?> = mapOf(
            "anchorId" to anchorId,
            "cloudAnchorId" to cloudAnchorId,
            "anchorType" to anchorType,
            "semanticLabel" to semanticLabel,
            "customName" to customName,
            "roomName" to roomName,
            "worldPose" to worldPose,
            "relativeToSafe" to relativeToSafe,
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
            docRef.set(roomMap.toFirestore(), SetOptions.merge()).await()
            realtimeDb.child(roomMap.roomId).setValue(roomMap.toFirestore()).await()
            Result.success(roomMap.roomId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadRoomMap(roomId: String): Result<RoomMap> = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection(COLLECTION).document(roomId).get().await()
            if (!doc.exists()) return@withContext Result.failure(IllegalStateException("Room map non trovato: $roomId"))
            val data = doc.data!!
            Result.success(parseRoomMap(data))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listUserRooms(userId: String): Result<List<RoomMap>> = withContext(Dispatchers.IO) {
        try {
            val query = firestore.collection(COLLECTION).whereEqualTo("ownerId", userId).get().await()
            Result.success(query.documents.map { doc -> parseRoomMap(doc.data!!) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseRoomMap(data: Map<String, Any>): RoomMap {
        val planes = (data["semanticPlanes"] as? List<Map<String, Any>> ?: emptyList()).map { p ->
            SemanticPlane(
                planeId = p["planeId"] as String,
                semanticLabel = p["semanticLabel"] as String,
                centerPose = (p["centerPose"] as List<Double>).map { it.toFloat() }.toFloatArray(),
                extentX = (p["extentX"] as Number).toFloat(),
                extentZ = (p["extentZ"] as Number).toFloat(),
                polygon = (p["polygon"] as List<List<Double>>).map { it.map { v -> v.toFloat() }.toFloatArray() }
            )
        }
        val anchors = (data["anchors"] as? List<Map<String, Any>> ?: emptyList()).map { a ->
            PersistentAnchor(
                anchorId = a["anchorId"] as String,
                cloudAnchorId = a["cloudAnchorId"] as? String,
                anchorType = a["anchorType"] as String,
                semanticLabel = a["semanticLabel"] as String,
                customName = a["customName"] as String,
                roomName = a["roomName"] as String,
                worldPose = (a["worldPose"] as List<Double>).map { it.toFloat() }.toFloatArray(),
                relativeToSafe = (a["relativeToSafe"] as List<Double>).map { it.toFloat() }.toFloatArray(),
                metadata = (a["metadata"] as? Map<String, String>) ?: emptyMap(),
                createdAt = (a["createdAt"] as Number).toLong(),
                ttlDays = (a["ttlDays"] as Number).toInt()
            )
        }
        return RoomMap(
            roomId = data["roomId"] as String,
            name = data["name"] as String,
            floorPlanImage = data["floorPlanImage"] as? String,
            semanticPlanes = planes,
            anchors = anchors,
            safeAnchorId = data["safeAnchorId"] as? String,
            createdAt = (data["createdAt"] as Number).toLong(),
            updatedAt = (data["updatedAt"] as Number).toLong(),
            version = (data["version"] as Number).toInt(),
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
    } catch (_: Exception) {}

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
