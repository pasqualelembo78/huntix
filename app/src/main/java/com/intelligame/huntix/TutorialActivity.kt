package com.intelligame.huntix

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.ui.SwipeToCatchView

class TutorialActivity : AppCompatActivity() {

    private var currentPage = 0
    private lateinit var pagesContainer: LinearLayout
    private lateinit var dots: Array<TextView>
    private lateinit var nextBtn: LinearLayout
    private var pageViews = mutableListOf<View>()

    private data class Page(val emoji: String, val title: String, val desc: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val pages = listOf(
            Page("🥚", "Benvenuto in Huntix", "Una caccia alle uova con Realtà Aumentata e GPS."),
            Page("🏠", "Modalità Indoor", "Nascondi uova AR in una stanza e sfida amici a trovarle."),
            Page("🌍", "Modalità Outdoor", "Esplora la mappa reale, cattura uova e completa palestre."),
            Page("⚔️", "Battaglia", "Sfida altri giocatori 1v1 con le uova della tua squadra."),
            Page("Swipe!", "Prova a Catturare", "Swipe verso l'alto per lanciare il cestino!")
        )

        pages.forEachIndexed { idx, page ->
            if (idx == 4) {
                val demoRoot = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(UiKit.dp(c, 32), UiKit.dp(c, 24), UiKit.dp(c, 32), UiKit.dp(c, 16))
                }

                val hint = TextView(c).apply {
                    text = "Swipe verso l'alto per lanciare il cestino!"
                    textSize = 15f; setTextColor(0xFFBBBBBB.toInt())
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, UiKit.dp(c, 16))
                }

                val swipeView = SwipeToCatchView(c).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(c, 280)
                    )
                    setEggColor(Color.parseColor(UiKit.ACCENT))
                }

                val resultLabel = TextView(c).apply {
                    text = "Prova ora!"
                    textSize = 18f; setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    setPadding(0, UiKit.dp(c, 16), 0, 0)
                }

                swipeView.listener = object : SwipeToCatchView.OnThrowResult {
                    override fun onResult(quality: Float) {
                        val score = when {
                            quality >= 0.8f -> "Perfetto! ⭐⭐⭐"
                            quality >= 0.5f -> "Bene! ⭐⭐"
                            quality >= 0.3f -> "Ok ⭐"
                            else -> "Riprova!"
                        }
                        resultLabel.text = score
                    }
                }

                demoRoot.addView(hint)
                demoRoot.addView(swipeView)
                demoRoot.addView(resultLabel)
                pageViews.add(demoRoot)
            } else {
                val pageRoot = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(UiKit.dp(c, 48), UiKit.dp(c, 40), UiKit.dp(c, 48), UiKit.dp(c, 16))
                }

                pageRoot.addView(TextView(c).apply {
                    text = page.emoji; textSize = 64f; gravity = Gravity.CENTER
                    setPadding(0, UiKit.dp(c, 24), 0, UiKit.dp(c, 16))
                })

                pageRoot.addView(TextView(c).apply {
                    text = page.title; textSize = 26f; setTextColor(0xFFFFFFFF.toInt())
                    typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, UiKit.dp(c, 12))
                })

                pageRoot.addView(TextView(c).apply {
                    text = page.desc; textSize = 15f
                    setTextColor(0xFFBBBBBB.toInt()); gravity = Gravity.CENTER
                    setPadding(UiKit.dp(c, 16), 0, UiKit.dp(c, 16), 0)
                })

                pageViews.add(pageRoot)
            }
        }

        pagesContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        pageViews.forEachIndexed { idx, view ->
            view.visibility = if (idx == 0) View.VISIBLE else View.GONE
            pagesContainer.addView(view)
        }

        dots = Array(pages.size) { i ->
            TextView(c).apply {
                text = "●"; textSize = 16f
                setTextColor(if (i == 0) 0xFFFFFFFF.toInt() else 0xFF333355.toInt())
                setPadding(UiKit.dp(c, 8), 0, UiKit.dp(c, 8), 0)
            }
        }
        val dotRow = LinearLayout(c).apply {
            gravity = Gravity.CENTER
            dots.forEach { addView(it) }
        }

        nextBtn = UiKit.button(c, "Avanti", UiKit.ACCENT) {
            if (currentPage < pages.size - 1) {
                showPage(currentPage + 1)
            } else {
                goHome()
            }
        }

        val skipBtn = UiKit.button(c, "Salta Tutorial", "#666") {
            goHome()
        }

        val bottomBar = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(c, 24), UiKit.dp(c, 8), UiKit.dp(c, 24), UiKit.dp(c, 24))
        }
        bottomBar.addView(dotRow)
        bottomBar.addView(skipBtn.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(c, 12) }
        })
        bottomBar.addView(nextBtn.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(c, 12) }
        })

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0620.toInt())
        }

        root.addView(pagesContainer)
        root.addView(bottomBar)

        setContentView(root)
    }

    private fun showPage(index: Int) {
        pageViews[currentPage].visibility = View.GONE
        currentPage = index
        pageViews[currentPage].visibility = View.VISIBLE

        dots.forEachIndexed { i, dot ->
            dot.setTextColor(if (i == currentPage) 0xFFFFFFFF.toInt() else 0xFF333355.toInt())
        }

        val label = nextBtn.getChildAt(0) as? TextView
        if (label != null) {
            label.text = if (currentPage == pageViews.size - 1) "Inizia a giocare!" else "Avanti"
        }
    }

    private fun goHome() {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit().putBoolean("tutorial_done", true).apply()
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
