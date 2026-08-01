package com.example.hangman

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

class HangmanActivity : AppCompatActivity() {

    private lateinit var gameManager: com.example.huntix.data.MiniGameManager
    private var gameId = "game_hangman"

    private lateinit var wordDisplay: TextView
    private lateinit var hangmanView: HangmanView
    private lateinit var keyboardContainer: GridLayout

    private val words = listOf(
        "TELEFONO", "COMPUTER", "PROGRAMMAZIONE", "SVILUPPO", "ANDROID",
        "SOFTWARE", "HARDWARE", "INTERNET", "APP", "DATABASE",
        "CLOUD", "SERVER", "CLIENT", "API", "FRAMEWORK",
        "LINGUA", "ITALIANO", "INGLESE", "MATEMATICA", "STORIA"
    )
    private var secretWord = ""
    private var guessedLetters = mutableSetOf<Char>()
    private var wrongGuesses = 0
    private val maxWrong = 7
    private var score = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameManager = com.example.huntix.data.MiniGameManager(this)
        Bundle().apply {
            gameId = getString("game_id", "game_hangman")
        }

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(32, 32, 32, 32)

        wordDisplay = TextView(this)
        wordDisplay.textSize = 32f
        wordDisplay.text = "_ _ _ _ _ _"
        container.addView(wordDisplay)

        hangmanView = HangmanView(this)
        hangmanView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            300,
            1f
        )
        container.addView(hangmanView)

        keyboardContainer = GridLayout(this)
        keyboardContainer.columnCount = 7
        keyboardContainer.layoutParams = GridLayout.LayoutParams().apply {
            height = GridLayout.LayoutParams.WRAP_CONTENT
            width = GridLayout.LayoutParams.MATCH_PARENT
        }
        container.addView(keyboardContainer)

        setContentView(container)
        setupKeyboard()
        startGame()
    }

    private fun startGame() {
        secretWord = words.random()
        guessedLetters.clear()
        wrongGuesses = 0
        updateDisplay()
        hangmanView.setWrongGuesses(0)
    }

    private fun setupKeyboard() {
        val alphabet = ('A'..'Z').toList()
        alphabet.forEach { letter ->
            val btn = Button(this)
            btn.text = letter.toString()
            btn.setOnClickListener { onLetterClick(letter) }
            keyboardContainer.addView(btn)
        }
    }

    private fun onLetterClick(letter: Char) {
        if (guessedLetters.contains(letter)) return

        guessedLetters.add(letter)

        if (!secretWord.contains(letter)) {
            wrongGuesses++
            hangmanView.setWrongGuesses(wrongGuesses)
        }

        updateDisplay()

        if (isWordGuessed()) {
            score = secretWord.length * 10 - wrongGuesses * 5
            val reward = gameManager.applyReward(gameId, maxOf(0, score))
            Toast.makeText(this, "Hai vinto! Parola: $secretWord, Score: $score, XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()
            finish()
        } else if (wrongGuesses >= maxWrong) {
            score = 0
            Toast.makeText(this, "Hai perso! Parola: $secretWord", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun updateDisplay() {
        val display = secretWord.map { c ->
            if (guessedLetters.contains(c)) c.toString() else "_"
        }.joinToString(" ")
        wordDisplay.text = display
    }

    private fun isWordGuessed(): Boolean {
        return secretWord.all { guessedLetters.contains(it) }
    }
}

class HangmanView(context: android.content.Context) : androidx.appcompat.widget.AppCompatImageView(context) {

    private var wrongGuesses = 0
    private val paint = android.graphics.Paint()

    init {
        paint.color = android.graphics.Color.BLACK
        paint.strokeWidth = 10f
    }

    fun setWrongGuesses(count: Int) {
        wrongGuesses = count
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas?) {
        super.onDraw(canvas)
        canvas ?: return

        val w = width.toFloat()
        val h = height.toFloat()

        // Impiccato semplice a forma di uovo che si rompe
        if (wrongGuesses >= 1) {
            canvas.drawLine(w/2, 0f, w/2, h*0.7f, paint) // testa
        }
        if (wrongGuesses >= 2) {
            canvas.drawLine(w/2, h*0.1f, w/2 - 30, h*0.2f, paint) // braccio sx
        }
        if (wrongGuesses >= 3) {
            canvas.drawLine(w/2, h*0.1f, w/2 + 30, h*0.2f, paint) // braccio dx
        }
        if (wrongGuesses >= 4) {
            canvas.drawLine(w/2, h*0.7f, w/2 - 20, h*0.85f, paint) // gamba sx
        }
        if (wrongGuesses >= 5) {
            canvas.drawLine(w/2, h*0.7f, w/2 + 20, h*0.85f, paint) // gamba dx
        }
        if (wrongGuesses >= 6) {
            paint.color = android.graphics.Color.RED
            canvas.drawText("X", w*0.3f, h*0.4f, paint) // occhi rossi
            canvas.drawText("X", w*0.7f, h*0.4f, paint)
        }
    }
}