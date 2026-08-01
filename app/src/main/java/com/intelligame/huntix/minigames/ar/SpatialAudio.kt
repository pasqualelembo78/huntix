package com.intelligame.huntix.minigames.ar

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.google.ar.core.Pose
import io.github.sceneview.node.Node
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SpatialAudio — audio 3D "fatto in casa" (nessuna dipendenza esterna).
 *
 * Suona suoni generati proceduralmente (sintesi PCM) posizionati nello spazio:
 * ogni source emette in stereo con bilanciamento sinistro/destro e attenuazione
 * in base alla distanza dal punto di ascolto (camera ARCore) e all'angolo orizzontale.
 */
@Suppress("DEPRECATION")
class SpatialAudio {

    private val SR = 44100
    private val handler = Handler(Looper.getMainLooper())
    private val releases = mutableListOf<AudioTrack>()
    private val loops = ConcurrentHashMap<Int, LoopEntry>()
    private var nextId = 0

    private data class LoopEntry(
        val node: Node,
        val track: AudioTrack,
        val freq: Float,
        val gain: Float
    )

    fun generateTone(freq: Float, durationMs: Int, decay: Boolean = false): ShortArray {
        val n = (SR * durationMs / 1000).toInt()
        val out = ShortArray(n * 2)
        val tau = 2.0 * Math.PI
        for (i in 0 until n) {
            val t = i / SR.toDouble()
            var s = sin(freq * tau * t)
            if (decay) s *= exp(-t * 6.0)
            val v = (s * 0.35 * Short.MAX_VALUE.toInt()).toInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val sample = v.toShort()
            out[i * 2] = sample
            out[i * 2 + 1] = sample
        }
        return out
    }

    /** Emite un suono puntiforme una tantum (globale, non spatializzato). */
    fun oneShot(freq: Float, durationMs: Int, decay: Boolean = true, gain: Float = 0.4f) {
        val samples = generateTone(freq, durationMs, decay)
        val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val buf = (samples.size * 2).coerceAtLeast(minBuf)
        val track = AudioTrack(AudioManager.STREAM_MUSIC, SR, AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT, buf, AudioTrack.MODE_STATIC)
        val rc = track.write(samples, 0, samples.size)
        if (rc > 0) {
            track.setStereoVolume(gain, gain)
            track.setLoopPoints(0, samples.size / 2, 0)
            track.play()
            handler.postDelayed({ safeRelease(track) }, (durationMs + 30).toLong())
        } else {
            track.release()
        }
    }

    /** Registra un nodo che emette un suono continuo (es. uovo che ronzia). */
    fun loopAt(node: Node, freq: Float, gain: Float = 0.6f): Int {
        val samples = generateTone(freq, 800, decay = false)
        val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val buf = (samples.size * 2).coerceAtLeast(minBuf)
        val track = AudioTrack(AudioManager.STREAM_MUSIC, SR, AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT, buf, AudioTrack.MODE_STATIC)
        track.write(samples, 0, samples.size)
        track.setLoopPoints(0, samples.size / 2, -1)
        track.setStereoVolume(0f, 0f)
        track.play()
        val id = nextId++
        loops[id] = LoopEntry(node, track, freq, gain)
        return id
    }

    fun stopLoop(id: Int) {
        val e = loops.remove(id) ?: return
        e.track.stop()
        e.track.flush()
        safeRelease(e.track)
    }

    /** Da chiamare ogni frame con la pose della camera per aggiornare L/R + attutimento. */
    @Suppress("DEPRECATION")
    fun tick(cameraPose: Pose?) {
        if (cameraPose == null) return
        val camPos = floatArrayOf(cameraPose.tx(), cameraPose.ty(), cameraPose.tz())
        val basis = cameraBasis(cameraPose)
        val right = basis[0]
        val forward = basis[1]
        for (e in loops.values) {
            val wp = e.node.worldPosition
            val relX = wp.x - camPos[0]
            val relY = wp.y - camPos[1]
            val relZ = wp.z - camPos[2]
            val dist = sqrt(relX * relX + relY * relY + relZ * relZ)
            val localX = relX * right[0] + relY * right[1] + relZ * right[2]
            val localZ = relX * forward[0] + relY * forward[1] + relZ * forward[2]
            val angle = if (localZ == 0f) 0f else kotlin.math.atan2(localX, localZ).toFloat()
            val panL = (1f + kotlin.math.sin(angle)) / 2f
            val panR = (1f - kotlin.math.sin(angle)) / 2f
            val att = 1f / (1f + dist * 0.35f)
            val vol = (att * e.gain).coerceIn(0f, e.gain)
            e.track.setStereoVolume(panL * vol, panR * vol)
        }
    }

    fun release() {
        loops.values.forEach { safeRelease(it.track) }
        loops.clear()
        releases.forEach { safeRelease(it) }
        releases.clear()
    }

    private fun safeRelease(t: AudioTrack) {
        try { t.release() } catch (_: Exception) {}
    }

    private fun cameraBasis(pose: Pose): Array<FloatArray> {
        val qx = pose.qx(); val qy = pose.qy(); val qz = pose.qz(); val qw = pose.qw()
        val right = rotate(qx, qy, qz, qw, 1f, 0f, 0f)
        val forward = rotate(qx, qy, qz, qw, 0f, 0f, 1f)
        return arrayOf(right, forward)
    }

    private fun rotate(qx: Float, qy: Float, qz: Float, qw: Float, x: Float, y: Float, z: Float): FloatArray {
        val xx = qx * qx; val yy = qy * qy; val zz = qz * qz
        val xy = qx * qy; val xz = qx * qz; val yz = qy * qz
        val wx = qw * qx; val wy = qw * qy; val wz = qw * qz
        val bx = x * (1 - 2 * (yy + zz)) + y * (2 * (xy - wz)) + z * (2 * (xz + wy))
        val by = x * (2 * (xy + wz)) + y * (1 - 2 * (xx + zz)) + z * (2 * (yz - wx))
        val bz = x * (2 * (xz - wy)) + y * (2 * (yz + wx)) + z * (1 - 2 * (xx + yy))
        val len = sqrt(bx * bx + by * by + bz * bz)
        return if (len == 0f) floatArrayOf(bx, by, bz) else floatArrayOf(bx / len, by / len, bz / len)
    }
}
