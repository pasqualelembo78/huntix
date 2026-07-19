package com.intelligame.huntix.ui

import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.PlayerProfileManager
import com.intelligame.huntix.BaseNavActivity

class PlayerProfileActivity : BaseNavActivity() {

    private var currentTab = 0
    private val tabContents = mutableListOf<LinearLayout>()
    private val tabButtons  = mutableListOf<TextView>()
    private lateinit var contentArea: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PlayerProfileManager.myProfile == null) {
            PlayerProfileManager.loadMyProfile(this, null)
        }
        buildUI()
    }

    override fun onResume() {
        super.onResume()
        PlayerProfileManager.loadMyProfile(this) { rebuildTabs() }
    }

    // ─────────────────────────────────────────────────────────────
    // SCAFFOLDING
    // ─────────────────────────────────────────────────────────────

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A1A"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // ── Top bar ───────────────────────────────────────────────
        root.addView(buildTopBar())

        // ── Tab strip ─────────────────────────────────────────────
        val tabScrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(4) }
        }
        val tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        tabScrollView.addView(tabStrip)
        root.addView(tabScrollView)

        // ── Content area ──────────────────────────────────────────
        contentArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(contentArea)

        setContentView(root)

        // ── Build tabs ────────────────────────────────────────────
        val tabDefs = listOf(
            "👤 Profilo",
            "⭐ XP",
            "💰 MVC",
            "💎 Gemme",
            "📊 Stats",
            "🏆 Badge"
        )
        tabButtons.clear()
        tabContents.clear()

        tabDefs.forEachIndexed { index, label ->
            val btn = buildTabButton(label, index)
            tabStrip.addView(btn)
            tabButtons.add(btn)

            val content = buildTabContent(index)
            tabContents.add(content)
            contentArea.addView(content)
        }

        selectTab(0)
    }

    private fun buildTopBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#12103A"))
            setPadding(dp(12), dp(40), dp(16), dp(10))

            addView(Button(this@PlayerProfileActivity).apply {
                text = "←"; textSize = 18f; setTextColor(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                setOnClickListener { finish() }
            })
            addView(TextView(this@PlayerProfileActivity).apply {
                text = "Il mio Profilo"
                textSize = 18f; setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                    it.marginStart = dp(8)
                }
            })
        }
    }

    private fun buildTabButton(label: String, index: Int): TextView {
        return TextView(this).apply {
            text = label; textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dp(6) }
            background = tabBg(false)
            setTextColor(Color.parseColor("#AAAACC"))
            setOnClickListener { selectTab(index) }
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        tabButtons.forEachIndexed { i, btn ->
            btn.background = tabBg(i == index)
            btn.setTextColor(if (i == index) Color.WHITE else Color.parseColor("#AAAACC"))
        }
        tabContents.forEachIndexed { i, view ->
            view.visibility = if (i == index) View.VISIBLE else View.GONE
        }
    }

    private fun rebuildTabs() {
        tabContents.forEachIndexed { index, old ->
            val newContent = buildTabContent(index)
            contentArea.removeView(old)
            contentArea.addView(newContent)
            tabContents[index] = newContent
            if (index != currentTab) newContent.visibility = View.GONE
        }
    }

    // ─────────────────────────────────────────────────────────────
    // TAB CONTENT BUILDERS
    // ─────────────────────────────────────────────────────────────

    private fun buildTabContent(index: Int): LinearLayout {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(40))
        }
        scroll.addView(inner)

        // Wrap in a LinearLayout so we can return it and toggle visibility
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            addView(scroll)
        }

        when (index) {
            0 -> buildProfileTab(inner)
            1 -> buildXpTab(inner)
            2 -> buildMvcTab(inner)
            3 -> buildGemsTab(inner)
            4 -> buildStatsTab(inner)
            5 -> buildBadgesTab(inner)
        }
        return wrapper
    }

    internal fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // Simple FlowLayout per i badge
    class FlowLayout(ctx: android.content.Context) : ViewGroup(ctx) {
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            var x = 0; var y = 0; var rowH = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val lp    = child.layoutParams as? MarginLayoutParams
                    ?: MarginLayoutParams(child.measuredWidth, child.measuredHeight)
                val cw = child.measuredWidth + lp.marginEnd
                val ch = child.measuredHeight + lp.bottomMargin
                if (x + cw > width && x > 0) { x = 0; y += rowH; rowH = 0 }
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                x += cw; rowH = maxOf(rowH, ch)
            }
        }
        override fun onMeasure(wS: Int, hS: Int) {
            val w = MeasureSpec.getSize(wS)
            var x = 0; var y = 0; var rowH = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                measureChild(child, wS, hS)
                val lp = child.layoutParams as? MarginLayoutParams
                    ?: MarginLayoutParams(child.measuredWidth, child.measuredHeight)
                val cw = child.measuredWidth + lp.marginEnd
                val ch = child.measuredHeight + lp.bottomMargin
                if (x + cw > w && x > 0) { x = 0; y += rowH; rowH = 0 }
                x += cw; rowH = maxOf(rowH, ch)
            }
            setMeasuredDimension(w, y + rowH)
        }
    }
}
