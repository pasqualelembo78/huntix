package com.intelligame.huntix

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

object SoundManager {

    var enabled: Boolean = true
    private val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)

    private fun playTone(toneType: Int, durationMs: Int = 150) {
        if (!enabled) return
        try {
            tg.startTone(toneType, durationMs)
        } catch (_: Exception) {}
    }

    // UI Sounds
    fun playButtonTap() = playTone(ToneGenerator.TONE_PROP_BEEP, 80)
    fun playHintReveal() = playTone(ToneGenerator.TONE_PROP_PROMPT, 200)
    fun playCountdown() = playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 100)
    fun playRiddleShow() = playTone(ToneGenerator.TONE_PROP_BEEP2, 180)
    fun playLevelUp() = playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350)
    fun playAchievement() = playTone(ToneGenerator.TONE_PROP_PROMPT, 250)

    // Gameplay Sounds
    fun playIntro() = playTone(ToneGenerator.TONE_PROP_PROMPT, 250)
    fun playThrow() = playTone(ToneGenerator.TONE_PROP_BEEP, 100)
    fun playTrap() = playTone(ToneGenerator.TONE_PROP_BEEP2, 200)
    fun playEggFound() = playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
    fun playKeyInsert() = playTone(ToneGenerator.TONE_PROP_ACK, 150)
    fun playSafeOpen() = playTone(ToneGenerator.TONE_PROP_PROMPT, 300)
    fun playTurnSwitch() = playTone(ToneGenerator.TONE_PROP_BEEP2, 150)
    fun playVictory() = playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
    fun playBasketCatch() = playTone(ToneGenerator.TONE_PROP_ACK, 100)
    fun playBasketMiss() = playTone(ToneGenerator.TONE_PROP_BEEP2, 150)
    fun playScanComplete() = playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
    fun playEggPlaced() = playTone(ToneGenerator.TONE_PROP_ACK, 120)

    fun release() {
        try { tg.release() } catch (_: Exception) {}
    }
}

object ApplicationHolder {
    lateinit var app: android.app.Application
}