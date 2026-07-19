package com.intelligame.huntix

import android.content.Context
import com.google.ar.core.Anchor
import io.github.sceneview.ar.node.AnchorNode
import org.json.JSONArray
import org.json.JSONObject

class LocalAnchorStore private constructor() {

    data class LocalAnchor(
        val id: Int,
        val refTrans: FloatArray,
        val refRot: FloatArray,
        val eggTrans: FloatArray,
        val eggRot: FloatArray,
        val colorIdx: Int,
        val shape: String,
        val isTrap: Boolean,
        val label: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as LocalAnchor
            return id == other.id
        }
        override fun hashCode(): Int = id
    }

    data class LocalAnchorSession(
        val sessionId: String,
        val name: String = "",
        val ttlDays: Float = 0f,
        val refDescription: String = "",
        val anchors: List<LocalAnchor> = emptyList()
    ) {
        fun copy(anchors: List<LocalAnchor>): LocalAnchorSession =
            copy(anchors = anchors)
    }

    companion object {
        private const val PREF = "local_anchor_store"
        private const val KEY_SESSIONS = "sessions"
        private var appContext: Context? = null

        private var instance: LocalAnchorStore? = null
        fun get(ctx: Context): LocalAnchorStore {
            appContext = ctx.applicationContext
            return instance ?: LocalAnchorStore().also { instance = it }
        }
    }

    fun purgeExpired() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_SESSIONS, "[]") ?: "[]")
        val out = JSONArray()
        val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val ttl = (s.optDouble("ttlDays", 0.0) * 86400000L).toLong()
            val created = s.optLong("createdAt", now)
            if (ttl <= 0 || now - created < ttl) out.put(s)
        }
        prefs.edit().putString(KEY_SESSIONS, out.toString()).apply()
    }

    fun buildAnchor(
        id: Int,
        refTrans: FloatArray,
        refRot: FloatArray,
        eggTrans: FloatArray,
        eggRot: FloatArray,
        colorIdx: Int,
        shape: String,
        isTrap: Boolean,
        label: String
    ): LocalAnchor = LocalAnchor(id, refTrans, refRot, eggTrans, eggRot, colorIdx, shape, isTrap, label)

    fun buildAnchor(session: com.google.ar.core.Session, localAnchor: LocalAnchor): Anchor? = null

    fun createSession(
        name: String,
        ttlDays: Float,
        refDescription: String = "",
        anchors: List<LocalAnchor>
    ): LocalAnchorSession = LocalAnchorSession(
        sessionId = java.util.UUID.randomUUID().toString(),
        name = name,
        ttlDays = ttlDays,
        refDescription = refDescription,
        anchors = anchors
    )

    fun load(sessionId: String): LocalAnchorSession? {
        val ctx = appContext ?: return null
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_SESSIONS, "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            if (s.optString("sessionId") == sessionId) return parseSession(s)
        }
        return null
    }

    fun save(session: LocalAnchorSession) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_SESSIONS, "[]") ?: "[]")
        val out = JSONArray()
        var replaced = false
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            if (s.optString("sessionId") == session.sessionId) {
                out.put(session.toJson())
                replaced = true
            } else out.put(s)
        }
        if (!replaced) out.put(session.toJson())
        prefs.edit().putString(KEY_SESSIONS, out.toString()).apply()
    }

    private fun LocalAnchorSession.toJson(): JSONObject = JSONObject().apply {
        put("sessionId", sessionId)
        put("name", name)
        put("ttlDays", ttlDays)
        put("refDescription", refDescription)
        put("createdAt", System.currentTimeMillis())
        val a = JSONArray()
        anchors.forEach { la ->
            a.put(JSONObject().apply {
                put("id", la.id)
                put("refTrans", la.refTrans.toJsonArray())
                put("refRot", la.refRot.toJsonArray())
                put("eggTrans", la.eggTrans.toJsonArray())
                put("eggRot", la.eggRot.toJsonArray())
                put("colorIdx", la.colorIdx)
                put("shape", la.shape)
                put("isTrap", la.isTrap)
                put("label", la.label)
            })
        }
        put("anchors", a)
    }

    private fun parseSession(s: JSONObject): LocalAnchorSession {
        val a = s.optJSONArray("anchors") ?: JSONArray()
        val anchors = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            LocalAnchor(
                id = o.optInt("id"),
                refTrans = o.optJSONArray("refTrans").toFloatArray(),
                refRot = o.optJSONArray("refRot").toFloatArray(),
                eggTrans = o.optJSONArray("eggTrans").toFloatArray(),
                eggRot = o.optJSONArray("eggRot").toFloatArray(),
                colorIdx = o.optInt("colorIdx"),
                shape = o.optString("shape", "sphere"),
                isTrap = o.optBoolean("isTrap"),
                label = o.optString("label")
            )
        }
        return LocalAnchorSession(
            sessionId = s.optString("sessionId"),
            name = s.optString("name"),
            ttlDays = s.optDouble("ttlDays", 0.0).toFloat(),
            refDescription = s.optString("refDescription"),
            anchors = anchors
        )
    }

    private fun FloatArray.toJsonArray(): JSONArray {
        val a = JSONArray()
        forEach { a.put(it.toDouble()) }
        return a
    }

    private fun JSONArray?.toFloatArray(): FloatArray {
        if (this == null) return floatArrayOf()
        return FloatArray(length()) { i -> (optDouble(i, 0.0)).toFloat() }
    }
}
