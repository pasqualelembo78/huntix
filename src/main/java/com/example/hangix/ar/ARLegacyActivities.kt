package com.example.hangix.ar

import android.os.Bundle
import com.example.hangix.R
import com.example.hangix.ar.base.ARGameActivity
import com.google.ar.core.Frame

// Memory AR: coppie di uova illuminate
class ARMemoryActivity : ARGameActivity() {
    override fun setupGame() {}
    override fun handleFrame(frame: Frame) {}
    override fun onGameOver(score: Int) {}
}

// Prendi Uovo AR
class ARCatchEggActivity : ARGameActivity() {
    override fun setupGame() {}
    override fun handleFrame(frame: Frame) {}
    override fun onGameOver(score: Int) {}
}

// Match 3 AR
class ARMatch3Activity : ARGameActivity() {
    override fun setupGame() {}
    override fun handleFrame(frame: Frame) {}
    override fun onGameOver(score: Int) {}
}