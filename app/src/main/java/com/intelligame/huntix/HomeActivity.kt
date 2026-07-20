package com.intelligame.huntix

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import com.intelligame.huntix.avatar.ReadyPlayerMeActivity
import com.intelligame.huntix.avatar.AvatarManager
import com.intelligame.huntix.avatar.AvatarPersistenceManager
import com.intelligame.huntix.avatar.AvatarSyncManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.intelligame.huntix.gamification.LiveEventManager
import com.intelligame.huntix.ui.*
import com.intelligame.huntix.billing.VipManager
import com.intelligame.huntix.managers.SavedManager
import android.widget.Toast

/**
 * Home Page — Stile Brawl Stars
 * - Header compatto risorse (XP, MVC, Gemme)
 * - Avatar prominente al centro
 * - Griglia 2x2 modalita di gioco
 * - Quick access row (Missioni, Shop, Squadra)
 * - Banner eventi live
 */
class HomeActivity : BaseNavActivity() {

    override fun activeTab() = "Home"
    private val RC_RPM_AVATAR = 900

    override fun onResume() {
        super.onResume()
        try {
            SavedManager.accrueInstallRewards(this)
            SavedManager.accrueMiningRewards(this)
        } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ✅ FIX v7.2.1: Ripristina progresso da Firestore se dati locali cancellati
        GameProgressSync.restoreProgress(this) { restored ->
            if (restored) {
                android.widget.Toast.makeText(this, "☁️ Progresso ripristinato!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0620"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(16))
        }
        scroll.addView(root)

        val profile = PlayerProfileManager.myProfile

        // ═══ 1. HEADER RISORSE (compatto, orizzontale) ═══
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.bottomMargin = dp(8) }
        }
        header.addView(resourceChip("\u26A1", "Lv.${profile?.level ?: 1}", "#A78BFA"))
        header.addView(spacer())
        header.addView(resourceChip("\u26CF\uFE0F", "${profile?.let { HatchedEgg.formatMvc(SavedManager.getMvcBalance(this)) } ?: "0"} MVC", "#00FF88"))
        header.addView(spacer())
        header.addView(resourceChip("\uD83D\uDC8E", "${profile?.gems ?: 0}", "#00BCD4"))
        header.addView(spacer())
        // Settings gear
        header.addView(TextView(this).apply {
            text = "\u2699\uFE0F"; textSize = 18f; gravity = Gravity.CENTER
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) }
        })
        root.addView(header)

        // ═══ 2. AVATAR AREA (prominente) ═══
        val avatarCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#1A0A33"), Color.parseColor("#0D0620"))
            ).apply { cornerRadius = dp(16).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.bottomMargin = dp(12) }
            isClickable = true; isFocusable = true
            setOnClickListener { startActivity(Intent(this@HomeActivity, PlayerProfileActivity::class.java)) }
        }
        // Avatar emoji (fallback, RPM thumbnail if available)
        avatarCard.addView(TextView(this).apply {
            text = "\uD83E\uDDD1\u200D\uD83D\uDE80"; textSize = 56f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LP_WW, LP_WW).apply { gravity = Gravity.CENTER }
        })
        // Player name
        avatarCard.addView(TextView(this).apply {
            text = profile?.name ?: "Giocatore"; textSize = 20f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.topMargin = dp(6) }
        })
        // Title + Level
        avatarCard.addView(TextView(this).apply {
            text = "${profile?.title ?: "Novizio"}  \u00B7  Livello ${profile?.level ?: 1}"
            textSize = 12f; setTextColor(Color.parseColor("#A78BFA")); gravity = Gravity.CENTER
        })
        // XP Bar
        val xpTrack = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(200), dp(8)).apply { gravity = Gravity.CENTER; topMargin = dp(8) }
            background = GradientDrawable().apply { cornerRadius = dp(4).toFloat(); setColor(Color.parseColor("#2A1A4A")) }
        }
        val xpPct = profile?.levelProgressPercent?.coerceIn(0, 100) ?: 35
        val xpFill = android.view.View(this).apply {
            layoutParams = FrameLayout.LayoutParams(0, dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(4).toFloat(); setColor(Color.parseColor("#A78BFA")) }
            tag = xpPct
        }
        xpTrack.addView(xpFill)
        xpTrack.post { xpFill.layoutParams = FrameLayout.LayoutParams((xpTrack.width * (xpFill.tag as Int) / 100), dp(8)) }
        avatarCard.addView(xpTrack)
        avatarCard.addView(TextView(this).apply {
            text = "${profile?.xpProgressInLevel ?: 0}/${profile?.xpNeededForNextLevel ?: 100} XP"
            textSize = 9f; setTextColor(Color.parseColor("#6B5B95")); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.topMargin = dp(2) }
        })
        root.addView(avatarCard)

        // ═══ 3. LIVE EVENT BANNER ═══
        val activeEvents = LiveEventManager.getActiveEvents()
        val evtText = if (activeEvents.isNotEmpty()) "\uD83D\uDD34  LIVE: ${activeEvents.first().title}" else "\uD83D\uDD34  LIVE: Uova Misteriose \u2014 Doppio XP!"
        root.addView(TextView(this).apply {
            text = evtText; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#E91E63"), Color.parseColor("#FF6EC7"))
            ).apply { cornerRadius = dp(8).toFloat() }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.bottomMargin = dp(12) }
            if (activeEvents.isNotEmpty()) setOnClickListener { startActivity(Intent(this@HomeActivity, LiveEventsActivity::class.java)) }
        })

        // ═══ 4. GAME MODES GRID (2x2) ═══
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.bottomMargin = dp(10) }
        }
        // Row 1
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.bottomMargin = dp(8) }
        }
        row1.addView(gameTile("\uD83C\uDFE0", "INDOOR", "Nascondi e cerca", "#3F51B5", "#1A237E") { startActivity(Intent(this, IndoorModeSelectionActivity::class.java)) })
        row1.addView(spacerH(dp(8)))
        row1.addView(gameTile("\uD83C\uDF0D", "OUTDOOR", "Esplora la mappa", "#43A047", "#1B5E20") { startActivity(Intent(this, OutdoorModeActivity::class.java)) })
        grid.addView(row1)
        // Row 2
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW)
        }
        row2.addView(gameTile("\uD83C\uDFAE", "MINIGIOCHI", "9 + 13 AR", "#FF6F00", "#E65100") { startActivity(Intent(this, MiniGamesHubActivity::class.java)) })
        row2.addView(spacerH(dp(8)))
        row2.addView(gameTile("\u2694\uFE0F", "BATTAGLIA", "1v1 Street Fighter", "#C62828", "#B71C1C") { startActivity(Intent(this, com.intelligame.huntix.ui.BattleActivity::class.java)) })
        grid.addView(row2)
        root.addView(grid)

        // ═══ 5. QUICK ACCESS ROW ═══
        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.bottomMargin = dp(8) }
        }
        quickRow.addView(quickChip("\uD83C\uDFAF", "Missioni", "#00E5FF") { startActivity(Intent(this, QuestActivity::class.java)) })
        quickRow.addView(spacerH(dp(6)))
        quickRow.addView(quickChip("\uD83C\uDFEA", "Negozio", "#E65100") { startActivity(Intent(this, ShopActivity::class.java)) })
        quickRow.addView(spacerH(dp(6)))
        quickRow.addView(quickChip("\uD83D\uDC65", "Squadra", "#6A1B9A") { startActivity(Intent(this, TeamActivity::class.java)) })
        quickRow.addView(spacerH(dp(6)))
        quickRow.addView(quickChip("\uD83C\uDFC6", "Classifica", "#FF3366") { startActivity(Intent(this, GamifiedLeaderboardActivity::class.java)) })
        root.addView(quickRow)

        setContentView(scroll)

        // VIP daily bonus
        if (VipManager.claimDailyVipBonus(this)) {
            Toast.makeText(this, "\u2B50 VIP Bonus: +200 MVC!", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Game Tile (2x2 grid) ────────────────────────────────────

    private fun gameTile(emoji: String, title: String, subtitle: String, c1: String, c2: String, onClick: () -> Unit): CardView {
        val card = CardView(this).apply {
            radius = dp(14).toFloat(); cardElevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(0, dp(110), 1f)
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
            )
            setPadding(dp(10), dp(12), dp(10), dp(12))
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }
        inner.addView(TextView(this).apply { text = emoji; textSize = 28f; gravity = Gravity.CENTER })
        inner.addView(TextView(this).apply {
            text = title; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LP_WW, LP_WW).also { it.topMargin = dp(4) }
        })
        inner.addView(TextView(this).apply {
            text = subtitle; textSize = 9f; setTextColor(Color.argb(180, 255, 255, 255)); gravity = Gravity.CENTER
        })
        card.addView(inner); return card
    }

    // ─── Quick Chip (bottom row) ─────────────────────────────────

    private fun quickChip(emoji: String, label: String, colorHex: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor(colorHex)) }
            setPadding(dp(6), dp(8), dp(6), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LP_WW, 1f)
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
            addView(TextView(this@HomeActivity).apply { text = emoji; textSize = 16f; gravity = Gravity.CENTER })
            addView(TextView(this@HomeActivity).apply {
                text = label; textSize = 9f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            })
        }
    }

    // ─── Resource Chip (header) ──────────────────────────────────

    private fun resourceChip(emoji: String, value: String, colorHex: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1A1030"))
                setStroke(dp(1), Color.parseColor(colorHex + "44"))
            }
            setPadding(dp(8), dp(4), dp(10), dp(4))
            addView(TextView(this@HomeActivity).apply { text = emoji; textSize = 12f })
            addView(TextView(this@HomeActivity).apply {
                text = value; textSize = 11f; setTextColor(Color.parseColor(colorHex))
                typeface = Typeface.DEFAULT_BOLD; setPadding(dp(4), 0, 0, 0)
            })
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private fun spacer() = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
    }
    private fun spacerH(w: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(w, 1)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val LP_MW = LinearLayout.LayoutParams.MATCH_PARENT
        private const val LP_WW = LinearLayout.LayoutParams.WRAP_CONTENT
    }

    // ── Ready Player Me Avatar ────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_RPM_AVATAR && resultCode == android.app.Activity.RESULT_OK) {
            val avatarUrl = data?.getStringExtra(ReadyPlayerMeActivity.EXTRA_AVATAR_URL) ?: return
            val avatarId = data.getStringExtra(ReadyPlayerMeActivity.EXTRA_AVATAR_ID) ?: ""
            AvatarPersistenceManager.saveAvatarId(this, avatarId)
            lifecycleScope.launch {
                val success = AvatarManager.ensureAvatarDownloaded(this@HomeActivity, avatarUrl)
                if (success) {
                    AvatarManager.downloadAvatarThumbnail(this@HomeActivity, avatarId)
                    AvatarSyncManager.pushLocalToCloud(this@HomeActivity)
                    Toast.makeText(this@HomeActivity, "\u2705 Avatar salvato!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@HomeActivity, "\u274C Errore download avatar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
