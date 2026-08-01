package com.example.huntix.audio

import android.media.AudioManager
import android.media.SoundPool
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SpatialAudio {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_GAME)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundMap: MutableMap<String, Int> = mutableMapOf()

    fun loadSound(soundName: String, resourceId: Int) {
        soundMap[soundName] = soundPool.load(resourceId)
    }

    fun playSoundAtPosition(soundName: String, x: Float, y: Float, z: Float, listenerX: Float = 0f, listenerY: Float = 0f, listenerZ: Float = 0f) {
        val soundId = soundMap[soundName] ?: return
        val pan = calculatePan(x, listenerX, listenerZ, listenerX)
        val volume = calculateVolumeBasedOnDistance(x, y, z, listenerX, listenerY, listenerZ)
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    fun stopAllSounds() {
        soundPool.stopAllSounds()
    }

    fun release() {
        soundPool.release()
    }

    private fun calculatePan(sourceX: Float, listenerX: Float, sourceZ: Float, listenerZ: Float): Float {
        val dx = sourceX - listenerX
        val dz = sourceZ - listenerZ
        val distance = sqrt(dx * dx + dz * dz)
        return if (distance > 0) {
            (dx / distance).coerceIn(-1f, 1f)
        } else 0f
    }

    private fun calculateVolumeBasedOnDistance(
        x: Float, y: Float, z: Float,
        listenerX: Float, listenerY: Float, listenerZ: Float
    ): Float {
        val dx = x - listenerX
        val dy = y - listenerY
        val dz = z - listenerZ
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        // Linear attenuation: closer = louder
        return (1.0f - (distance / 10.0f)).coerceIn(0.1f, 1.0f)
    }
}