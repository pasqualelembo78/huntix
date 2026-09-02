// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
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
import com.intelligame.huntix.gamification.SpecialEventManager
import com.intelligame.huntix.gamification.DailyEventManager
import com.intelligame.huntix.gamification.DailyEventRegistry
import com.intelligame.huntix.ui.*
import com.intelligame.huntix.billing.VipManager
import com.intelligame.huntix.managers.SavedManager
import com.intelligame.huntix.managers.DistanceTracker
import com.intelligame.huntix.managers.PoiSearchManager
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.intelligame.huntix.managers.CustomPageRegistry
import com.intelligame.huntix.managers.OsmPoiRepository
import com.intelligame.huntix.manager.OutdoorManager
import com.intelligame.huntix.bridge.Bridge
import com.intelligame.huntix.bridge.BridgeActivity
import com.intelligame.huntix.reallife.OsmClient
import io.sentry.Sentry

/**
 * Home Page — Stile Brawl Stars
 * - Header compatto risorse (XP, MVC, Gemme)
 * - Avatar prominente al centro
 * - Griglia 2x2 modalita di gioco
 * - Quick access row (Missioni, Shop, Squadra)
 * - Banner eventi live
 */
class HomeActivity : BaseNavActivity() {

    private val RC_LOCATION = 101
    private lateinit var homeSearchPanel: PoiSearchPanel
    private var lastSyncMs = 0L

    override fun activeTab() = "Home"
    private val RC_RPM_AVATAR = 900

    override fun onResume() {
        super.onResume()
        try {
            val now = System.currentTimeMillis()
            if (now - lastSyncMs >= RESUME_SYNC_INTERVAL_MS) {
                lastSyncMs = now
                SavedManager.accrueInstallRewards(this)
                SavedManager.accrueMiningRewards(this)
            }
            // Start distance tracking for termocullas
            if (!DistanceTracker.isListening(this)) {
                DistanceTracker.startListening(this) { /* handled internally */ }
            }
            loadHomeNearby()
        } catch (e: Exception) { Sentry.captureException(e) }
    }

    override fun onPause() {
        super.onPause()
        try { DistanceTracker.stopListening(this) } catch (_: Exception) {}
    }

    /** Carica i locali vicini sul pannello Home (richiede eventuale permesso GPS). */
    private fun loadHomeNearby() {
        if (!::homeSearchPanel.isInitialized) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            homeSearchPanel.showPermissionHint()
            return
        }
        homeSearchPanel.loadNearby()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            homeSearchPanel.loadNearby()
        }
    }

    /** Apertura locale: pagina personalizzata / web / OSM (stesso comportamento di Esplora). */
    private fun openPoiPage(r: PoiSearchManager.SearchResult) {
        val res = CustomPageRegistry.resolve(r.id)
        val url = res?.url
        when (res?.pageType) {
            "custom" -> startActivity(Intent(this, POICustomPageActivity::class.java).apply {
                putExtra(POICustomPageActivity.EXTRA_JSON_URL, url)
                putExtra(POICustomPageActivity.EXTRA_POI_NAME, r.name)
                putExtra(POICustomPageActivity.EXTRA_POI_TYPE, r.category)
            })
            "web" -> startActivity(Intent(this, POIWebViewActivity::class.java).apply {
                putExtra(POIWebViewActivity.EXTRA_URL, url)
                putExtra(POIWebViewActivity.EXTRA_TITLE, r.name)
            })
            else -> {
                val osmUrl = "https://www.openstreetmap.org/?mlat=${r.lat}&mlon=${r.lng}#map=18/${r.lat}/${r.lng}"
                startActivity(Intent(this, POIWebViewActivity::class.java).apply {
                    putExtra(POIWebViewActivity.EXTRA_URL, osmUrl)
                    putExtra(POIWebViewActivity.EXTRA_TITLE, r.name)
                })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            GameProgressSync.restoreProgress(this) { restored ->
                if (restored && !isFinishing && !isDestroyed) {
                    try { Toast.makeText(this, "☁️ Progresso ripristinato!", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) { Sentry.captureException(e) }

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
        try {
            val mvcText = profile?.let { HatchedEgg.formatMvc(SavedManager.getMvcBalance(this)) } ?: "0"
            header.addView(resourceChip("\u26CF\uFE0F", "$mvcText MVC", "#00FF88"))
        } catch (_: Exception) {
            header.addView(resourceChip("\u26CF\uFE0F", "0 MVC", "#00FF88"))
        }
        header.addView(spacer())
        header.addView(resourceChip("\uD83D\uDC8E", "${profile?.gems ?: 0}", "#00BCD4"))
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
        // Avatar 3D Kenney character (model-viewer WebView, auto-rotate)
        val skinId = profile?.cityCharacterId?.takeIf { it.isNotBlank() } ?: "humanMaleA"
        val glbPath = "characters/kenney/kenney_${skinId}.glb"
        avatarCard.addView(WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LP_MW, dp(220)).apply { gravity = Gravity.CENTER }
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                allowFileAccess = true; allowContentAccess = true
            }
            setBackgroundColor(Color.TRANSPARENT)
            webChromeClient = WebChromeClient()
            loadDataWithBaseURL(
                "file:///android_asset/",
                """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1.0">
<script type="module" src="https://ajax.googleapis.com/ajax/libs/model-viewer/4.0.0/model-viewer.min.js"></script>
<style>*{margin:0;padding:0}body{background:transparent;overflow:hidden}model-viewer{width:100%;height:100%;background:transparent;--poster-color:transparent}model-viewer::part(default-progress-bar){display:none}</style>
</head><body><model-viewer src="$glbPath" alt="Character" auto-rotate camera-orbit="0deg 75deg 2.5m" min-camera-orbit="auto auto 1.5m" max-camera-orbit="auto auto 5m" field-of-view="30deg" autoplay shadow-intensity="1" exposure="1.2" environment-image="neutral" style="width:100%;height:100%;"></model-viewer><script>
try {
  function showAvatarFallback(){ if (document.getElementById("fb")) return; document.body.innerHTML = "<div id=\"fb\" style=\"width:100%;height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#A78BFA;font-family:sans-serif;\"><span style=\"font-size:40px;\">\u{1F464}</span><div style=\"margin-top:2px;font-size:13px;\">Avatar offline</div></div>"; }
  var mv = document.querySelector("model-viewer");
  if (mv) mv.addEventListener("error", showAvatarFallback);
  if (window.customElements) {
    var loadedFlag = false;
    window.customElements.whenDefined("model-viewer").then(function(){ loadedFlag = true; }).catch(showAvatarFallback);
    setTimeout(function(){ if (!loadedFlag) showAvatarFallback(); }, 2500);
  } else showAvatarFallback();
} catch (e) {}
</script>
</body></html>""".trimIndent(),
                "text/html", "UTF-8", null
            )
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
        xpTrack.post { xpFill.layoutParams = FrameLayout.LayoutParams((xpTrack.width * ((xpFill.tag as? Int) ?: 35) / 100), dp(8)) }
        avatarCard.addView(xpTrack)
        avatarCard.addView(TextView(this).apply {
            text = "${profile?.xpProgressInLevel ?: 0}/${profile?.xpNeededForNextLevel ?: 100} XP"
            textSize = 9f; setTextColor(Color.parseColor("#6B5B95")); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.topMargin = dp(2) }
        })
        root.addView(avatarCard)

        // ═══ 3. LIVE EVENT BANNER ═══
        var bannersShown = 0
        try {
            if (bannersShown < 2) {
                bannersShown++
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
            }
        } catch (e: Exception) { Sentry.captureException(e) }

        // ═══ 3b. SPECIAL EVENT BANNER ═══
        try {
            if (bannersShown < 2 && SpecialEventManager.hasActiveEvent()) {
                bannersShown++
                val activeEvent = SpecialEventManager.getActiveSpecialEvent()
                if (activeEvent != null) {
                    val eventCard = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(Color.parseColor("#C62828"), Color.parseColor("#1B5E20"))
                        ).apply { cornerRadius = dp(12).toFloat() }
                        setPadding(dp(14), dp(10), dp(14), dp(10))
                        layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also {
                            it.bottomMargin = dp(12)
                        }
                        isClickable = true; isFocusable = true
                        setOnClickListener {
                            startActivity(Intent(this@HomeActivity, SpecialEventsActivity::class.java))
                        }
                    }

                    eventCard.addView(TextView(this).apply {
                        text = "\uD83C\uDF84"; textSize = 28f
                        layoutParams = LinearLayout.LayoutParams(dp(40), LP_WW)
                    })

                    val eventCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LP_WW, 1f).also {
                            it.marginStart = dp(8)
                        }
                    }
                    eventCol.addView(TextView(this).apply {
                        text = "Caccia dell'Elfo"
                        textSize = 15f; setTextColor(Color.WHITE)
                        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                    })
                    eventCol.addView(TextView(this).apply {
                        text = "Trova il regalo nascosto! \uD83D\uDD0D"
                        textSize = 11f; setTextColor(Color.argb(200, 255, 255, 255))
                    })
                    eventCard.addView(eventCol)

                    eventCard.addView(TextView(this).apply {
                        text = "\u2192"; textSize = 18f; setTextColor(Color.WHITE)
                    })

                    root.addView(eventCard)
                }
            }
        } catch (e: Exception) { Sentry.captureException(e) }

        // ═══ 3c. DAILY EVENT BANNER ═══
        try {
            if (bannersShown < 2 && DailyEventManager.shouldShowDailyEventBanner()) {
                bannersShown++
                val dailyEvent = DailyEventRegistry.getTodayEvent()
                if (dailyEvent != null) {
                    val isActive = DailyEventManager.isWithinEventWindow(dailyEvent)
                    val minsLeft = DailyEventManager.minutesLeftInEvent()
                    val minsUntil = DailyEventManager.minutesUntilEvent()

                    val subtitle = when {
                        isActive -> "Attivo ora! Mancano $minsLeft min"
                        minsUntil in 0..30 -> "Inizia tra $minsUntil min!"
                        else -> "Oggi alle ${dailyEvent.startHour}:${String.format("%02d", dailyEvent.startMinute)}"
                    }

                    val dailyCard = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(dailyEvent.colorInt, dailyEvent.colorInt / 2)
                        ).apply { cornerRadius = dp(12).toFloat() }
                        setPadding(dp(14), dp(10), dp(14), dp(10))
                        layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also {
                            it.bottomMargin = dp(12)
                        }
                        isClickable = true; isFocusable = true
                        setOnClickListener {
                            startActivity(Intent(this@HomeActivity, SpecialEventsActivity::class.java))
                        }
                    }

                    dailyCard.addView(TextView(this).apply {
                        text = dailyEvent.emoji; textSize = 28f
                        layoutParams = LinearLayout.LayoutParams(dp(40), LP_WW)
                    })

                    val dailyCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LP_WW, 1f).also {
                            it.marginStart = dp(8)
                        }
                    }
                    dailyCol.addView(TextView(this).apply {
                        text = dailyEvent.title
                        textSize = 15f; setTextColor(Color.WHITE)
                        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                    })
                    dailyCol.addView(TextView(this).apply {
                        text = subtitle
                        textSize = 11f; setTextColor(Color.argb(200, 255, 255, 255))
                    })
                    dailyCard.addView(dailyCol)

                    dailyCard.addView(TextView(this).apply {
                        text = "\u2192"; textSize = 18f; setTextColor(Color.WHITE)
                    })

                    root.addView(dailyCard)
                }
            }
        } catch (e: Exception) { Sentry.captureException(e) }

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
        row1.addView(gameTile("\uD83C\uDFAE", "MINIGIOCHI", "${MiniGamesHubActivity.NORMAL_COUNT} + ${MiniGamesHubActivity.AR_COUNT} AR", "#FF6F00", "#E65100") { startActivity(Intent(this, MiniGamesHubActivity::class.java)) })
        grid.addView(row1)
        // Row 2
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW)
        }
        row2.addView(gameTile("\uD83E\uDD5A", "UOVA", "Termoculle e schiusa", "#9C27B0", "#4A148C") { startActivity(Intent(this, HatchingActivity::class.java)) })
        row2.addView(spacerH(dp(8)))
        row2.addView(gameTile("\uD83C\uDFDF\uFE0F", "RAID", "Boss battles", "#D32F2F", "#B71C1C") { startActivity(Intent(this, RaidBattleActivity::class.java)) })
        grid.addView(row2)
        root.addView(grid)

        // ═══ 5. QUICK ACCESS ROW ═══
        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.bottomMargin = dp(8) }
        }
        quickRow.addView(quickChip("\uD83D\uDCD3", "Codex", "#00E5FF") { startActivity(Intent(this, CodexActivity::class.java)) })
        quickRow.addView(spacerH(dp(6)))
        quickRow.addView(quickChip("\uD83D\uDDD3\uFE0F", "Streak", "#FFD700") { startActivity(Intent(this, DailyStreakActivity::class.java)) })
        quickRow.addView(spacerH(dp(6)))
        quickRow.addView(quickChip("\uD83D\uDCCB", "Missioni", "#00E5FF") { startActivity(Intent(this, ResearchTaskActivity::class.java)) })
        quickRow.addView(spacerH(dp(6)))
        root.addView(quickRow)

        val quickRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.bottomMargin = dp(8) }
        }
        quickRow2.addView(quickChip("\uD83C\uDFEA", "Negozio", "#E65100") { startActivity(Intent(this, ShopActivity::class.java)) })
        quickRow2.addView(spacerH(dp(6)))
        quickRow2.addView(quickChip("\uD83D\uDC65", "Squadra", "#6A1B9A") { startActivity(Intent(this, TeamActivity::class.java)) })
        quickRow2.addView(spacerH(dp(6)))
        quickRow2.addView(quickChip("\uD83C\uDFC6", "Classifica", "#FF3366") { startActivity(Intent(this, GamifiedLeaderboardActivity::class.java)) })
        quickRow2.addView(spacerH(dp(6)))
        quickRow2.addView(quickChip("\uD83C\uDFD9\uFE0F", "Miacitta", "#7E57C2") {
            AppLog.risorse(this@HomeActivity, "pre-miacitta")
            Bridge.openUnityActivity(
                this@HomeActivity,
                BridgeActivity.MODE_MIACITTA,
                "{\"id\":\"miacitta\",\"name\":\"Miacitta\"}"
            )
        })
        quickRow2.addView(spacerH(dp(6)))
        quickRow2.addView(quickChip("\u2699\uFE0F", "Impost.", "#666666") { startActivity(Intent(this, SettingsActivity::class.java)) })
        root.addView(quickRow2)

        // ═══ RICERCA LOCALI VICINI AL GPS (Overpass) ═══
        // Trova i locali intorno alla posizione corrente (negozi, bar, ristoranti,
        // gym, musei…) anche dove huntix-poi è sparso (es. Foggia). Ogni locale
        // apre la sua pagina personalizzata se presente nel CustomPageRegistry.
        homeSearchPanel = PoiSearchPanel(this).apply {
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.bottomMargin = dp(8) }
            enableNearbyMode()
            nearbyLoader = { lat, lng, r, cb ->
                OsmPoiRepository.loadNearby(lat, lng, r, applicationContext, cb)
            }
            locationSupplier = { OutdoorManager.get().currentLocation ?: OutdoorManager.lastKnownLocation(applicationContext) }
            requirePermissionCheck = {
                ContextCompat.checkSelfPermission(this@HomeActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
            onPermissionDeniedTap = {
                ActivityCompat.requestPermissions(this@HomeActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), RC_LOCATION)
            }
            onOpenPoi = { r -> openPoiPage(r) }
        }
        root.addView(homeSearchPanel)

        // Debug log (hidden but accessible)
        root.addView(TextView(this).apply {
            text = "\uD83D\uDD27 Debug Log"
            textSize = 11f
            setTextColor(Color.parseColor("#444444"))
            setPadding(dp(4), dp(8), dp(4), dp(4))
            isClickable = true; isFocusable = true
            setOnClickListener { startActivity(Intent(this@HomeActivity, com.intelligame.huntix.ui.CityDebugLogActivity::class.java)) }
        })

        setContentView(scroll)

        try {
            if (VipManager.claimDailyVipBonus(this)) {
                Toast.makeText(this, "\u2B50 VIP Bonus: +200 MVC!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { Sentry.captureException(e) }
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
            text = subtitle; textSize = 11f; setTextColor(Color.argb(180, 255, 255, 255)); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LP_WW, LP_WW).also { it.topMargin = dp(2) }
        })
        card.addView(inner); return card
    }

    // ─── Quick Chip (bottom row) ─────────────────────────────────

    private fun quickChip(emoji: String, label: String, colorHex: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor(colorHex)) }
            setPadding(dp(4), dp(10), dp(4), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LP_WW, 1f)
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
            addView(TextView(this@HomeActivity).apply { text = emoji; textSize = 18f; gravity = Gravity.CENTER })
            addView(TextView(this@HomeActivity).apply {
                text = label; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
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
        private const val RESUME_SYNC_INTERVAL_MS = 60_000L
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
