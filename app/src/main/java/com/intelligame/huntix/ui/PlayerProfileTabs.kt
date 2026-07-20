package com.intelligame.huntix.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.webkit.*
import android.widget.*
import com.intelligame.huntix.LocationBadge
import com.intelligame.huntix.PlayerProfileManager
import com.intelligame.huntix.managers.MiniGameManager
import com.intelligame.huntix.managers.SavedManager

// ── TAB 0: PROFILO ────────────────────────────────────────────
internal fun PlayerProfileActivity.buildProfileTab(root: LinearLayout) {
    val p = PlayerProfileManager.myProfile

    val avatarEmoji = when (p?.equippedAvatarFrameId) {
        "chick"   -> "🐥"; "dragon"  -> "🐲"; "phoenix" -> "🦅"
        "unicorn" -> "🦄"; "bunny"   -> "🐰"; else      -> "🥚"
    }

    // Avatar card
    root.addView(LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(20).toFloat()
            colors = intArrayOf(Color.parseColor("#1A0A4A"), Color.parseColor("#0A1A4A"))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dp(2), Color.parseColor("#5C35CC"))
        }
        setPadding(dp(24), dp(28), dp(24), dp(28))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(16) }

        addView(tv(avatarEmoji, 72f, Color.WHITE, Gravity.CENTER))
        addView(tv(p?.name ?: "Giocatore", 24f, Color.WHITE, Gravity.CENTER, true)
            .also { it.setPadding(0, dp(10), 0, dp(4)) })
        addView(tv(p?.title ?: "🐣 Principiante", 15f, Color.parseColor("#D8B4FE"), Gravity.CENTER))
        if (p?.equippedTitleId?.isNotEmpty() == true && p.equippedTitleId != "title_default") {
            addView(tv("\"${p.equippedTitleId}\"", 12f, Color.parseColor("#FFD700"), Gravity.CENTER)
                .also { it.setPadding(0, dp(4), 0, 0) })
        }
    })

    // Info card
    root.addView(sectionCard("#0D1030", "#5C35CC") {
        addView(rowText("🆔 ID Giocatore", p?.playerId?.take(12) ?: "—", "#C4B5FD", "#CCBBFF"))
        addView(rowText("⭐ Livello",       "${p?.level ?: 1}",            "#C4B5FD", "#FFFFFF"))
        addView(rowText("💪 Forza",         "${p?.strength ?: 0}",         "#C4B5FD", "#FFFFFF"))
        addView(rowText("⚡ Energia",       "${p?.energy ?: 100} / 100",   "#C4B5FD", "#FFFFFF"))
        addView(rowText("🏋️ Allenamenti",   "${p?.gymTrainings ?: 0}",     "#C4B5FD", "#CCBBFF"))
        addView(rowText("📅 Giorni login",  "${p?.totalLoginDays ?: 0}",   "#C4B5FD", "#CCBBFF"))
    })

    // Personalizzazione
    root.addView(sectionCard("#1A0A30", "#7B1FA2") {
        addView(tv("🎨 Personalizzazione", 15f, Color.parseColor("#D8B4FE"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(8)) })
        addView(rowText("🖼️ Frame avatar",  p?.equippedAvatarFrameId ?: "default", "#A855F7", "#E1BEE7"))
        addView(rowText("🎭 Skin uovo",     p?.equippedEggSkinId ?: "default",     "#A855F7", "#E1BEE7"))
        addView(rowText("🗺️ Tema mappa",    p?.equippedMapThemeId ?: "default",    "#A855F7", "#E1BEE7"))
    })

    // Team
    if (p?.teamId?.isNotEmpty() == true) {
        root.addView(sectionCard("#001A10", "#00FF88") {
            addView(rowText("🛡️ Squadra", p.teamId, "#00FF88", "#FFFFFF"))
        })
    }
}

// ── TAB 1: XP / LIVELLO ───────────────────────────────────────
internal fun PlayerProfileActivity.buildXpTab(root: LinearLayout) {
    val p = PlayerProfileManager.myProfile

    root.addView(sectionCard("#001830", "#00B4FF") {
        addView(tv("⭐ Punti Esperienza", 16f, Color.parseColor("#66CCFF"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(12)) })

        val level    = p?.level ?: 1
        val xpTotal  = p?.xp ?: 0L
        val xpIn     = p?.xpProgressInLevel ?: 0L
        val xpNext   = p?.xpNeededForNextLevel ?: 150L
        val progress = (p?.levelProgressPercent ?: 0).coerceIn(0, 100)

        addView(rowText("🏅 Livello attuale",  "$level",        "#42A5F5", "#FFFFFF"))
        addView(rowText("🔮 XP totale",        "$xpTotal XP",   "#42A5F5", "#90CAF9"))
        addView(rowText("📅 XP questa settimana", "${p?.weeklyXp ?: 0L} XP", "#42A5F5", "#90CAF9"))
        addView(spacer(dp(8)))

        // XP bar
        addView(tv("Progressione verso livello ${level + 1}", 12f,
            Color.parseColor("#9999CC"), Gravity.START).also { it.setPadding(0, 0, 0, dp(4)) })
        addView(buildProgressBar(progress, "#00B4FF"))
        addView(tv("$xpIn / $xpNext XP  ($progress%)", 11f,
            Color.parseColor("#78909C"), Gravity.CENTER).also { it.setPadding(0, dp(4), 0, 0) })
    })

    // XP per livello prossimo
    root.addView(sectionCard("#00101A", "#0288D1") {
        addView(tv("🔭 Prossimi livelli", 15f, Color.parseColor("#4FC3F7"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(8)) })
        val baseLevel = p?.level ?: 1
        for (lv in baseLevel..(baseLevel + 4)) {
            val needed = (1..lv).sumOf { it * 150L }
            addView(rowText("Livello ${lv + 1}", "$needed XP totali", "#0288D1", "#B3E5FC"))
        }
    })

    // Potere
    root.addView(sectionCard("#0A0A1A", "#00BCD4") {
        addView(tv("⚔️ Potere di Battaglia", 15f, Color.parseColor("#4DD0E1"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(8)) })
        addView(rowText("💥 Potere totale", "${p?.power ?: 0}", "#00BCD4", "#FFFFFF"))
        addView(tv("Il potere si accumula raccogliendo uova e allenandosi in palestra.",
            11f, Color.parseColor("#9999CC"), Gravity.START).also { it.setPadding(0, dp(6), 0, 0) })
    })
}

// ── TAB 2: MVC ────────────────────────────────────────────────
internal fun PlayerProfileActivity.buildMvcTab(root: LinearLayout) {
    val c = this
    val mvcBalance = SavedManager.getMvcBalance(c)
    val mvcTotal   = SavedManager.getTotalEarned(c)
    val mvcToday   = MiniGameManager.totalMvcEarnedToday(c)
    val claimable  = SavedManager.canCheckInToday(c)

    fun fmtHms(ms: Long): String {
        val s = (ms / 1000).toInt()
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return "%02d:%02d:%02d".format(h, m, sec)
    }

    fun makeButton(label: String, color: String, onClick: () -> Unit): Button =
        Button(c).apply {
            text = label; textSize = 15f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(50).toFloat()
                setColor(Color.parseColor(color))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50))
                .also { it.topMargin = dp(8) }
            setOnClickListener { onClick() }
        }

    // ── Saldo ──
    root.addView(sectionCard("#001500", "#00FF88") {
        addView(tv("💰 Huntix Coins (MVC)", 16f, Color.parseColor("#66FFB2"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(12)) })
        addView(rowText("💳 Saldo disponibile",   SavedManager.formatMvc(mvcBalance), "#00FF88", "#FFFFFF"))
        addView(rowText("📈 Totale guadagnato",  SavedManager.formatMvc(mvcTotal),   "#00FF88", "#A5D6A7"))
        addView(rowText("📅 Guadagnati oggi",    "$mvcToday MVC",                       "#00FF88", "#A5D6A7"))
    })

    // ── Check-in giornaliero ──
    root.addView(sectionCard("#0A1A00", "#7CB342") {
        addView(tv("📅 Check-in Giornaliero", 15f, Color.parseColor("#AED581"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(8)) })
        if (claimable) {
            addView(tv("Riscatta il tuo bonus quotidiano di +10 MVC! 🎁", 12f,
                Color.parseColor("#0F0F2A"), Gravity.START).also { it.setPadding(0, 0, 0, dp(8)) })
            addView(makeButton("🎁  Riscatta +10 MVC", "#00FF88") {
                val got = SavedManager.doDailyCheckIn(c)
                if (got > 0) {
                    Toast.makeText(c, "+$got MVC! 🎉", Toast.LENGTH_LONG).show()
                    recreate()
                } else {
                    Toast.makeText(c, "Già riscattato oggi.", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            addView(tv("✅ Check-in già riscattato oggi!", 13f,
                Color.parseColor("#A5D6A7"), Gravity.START).also { it.setPadding(0, 0, 0, dp(4)) })
            addView(tv("⏳ Prossimo tra: ${fmtHms(SavedManager.millisUntilNextCheckIn())}", 12f,
                Color.parseColor("#0F0F2A"), Gravity.START).also { it.setPadding(0, dp(4), 0, dp(8)) })
        }
    })

    // ── Bonus installazione (passivo) ──
    root.addView(sectionCard("#001A1A", "#26A69A") {
        addView(tv("📱 Bonus Presenza App", 15f, Color.parseColor("#80CBC4"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(8)) })
        addView(tv("MVC aumentano dal momento dell'installazione: il solo " +
                   "fatto di avere l'app installata genera guadagni passivi.",
            12f, Color.parseColor("#0F0F2A"), Gravity.START).also { it.setPadding(0, 0, 0, dp(6)) })
        addView(rowText("⚡ Guadagno passivo",
            "${String.format("%.3f", SavedManager.getInstallRatePerHour())} MVC/ora", "#26A69A", "#B2DFDB"))
    })

    // ── Come guadagnare ──
    root.addView(sectionCard("#001000", "#388E3C") {
        addView(tv("ℹ️ Come guadagnare MVC", 15f, Color.parseColor("#A5D6A7"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(8)) })
        val tips = listOf(
            "📅 Check-in giornaliero (+10 MVC al giorno)",
            "📱 Bonus presenza app (MVC passivi dall'installazione)",
            "⛏️ Mining delle uova schiuse (MVC nel tempo)",
            "🥚 Raccogliere uova rare ed epic",
            "🎮 Mini giochi (Memory, Carte, Match-3, ecc.)",
            "⚔️ Vincere battaglie contro altri giocatori",
            "🤝 Invitare amici (referral +500 MVC)",
            "👑 Ricompense VIP e eventi"
        )
        tips.forEach {
            addView(tv("• $it", 12f, Color.parseColor("#0F0F2A"), Gravity.START)
                .also { v -> v.setPadding(dp(4), dp(3), 0, dp(3)) })
        }
    })

    // Uova regalo nell'inventario
    val rarities = listOf("common","uncommon","rare","epic","legendary")
    val hasGift  = rarities.any { SavedManager.getGiftEggCount(c, it) > 0 }
    if (hasGift) {
        root.addView(sectionCard("#1A0010", "#E91E63") {
            addView(tv("🥚 Uova Regalo (da schiudere)", 15f, Color.parseColor("#F48FB1"), Gravity.START, true)
                .also { it.setPadding(0, 0, 0, dp(8)) })
            rarities.forEach { rarity ->
                val count = SavedManager.getGiftEggCount(c, rarity)
                if (count > 0) {
                    val emoji = when (rarity) {
                        "common"    -> "🥚"; "uncommon" -> "🌿🥚"; "rare"      -> "💙🥚"
                        "epic"      -> "💜🥚"; "legendary" -> "🌟🥚"; else -> "🥚"
                    }
                    addView(rowText("$emoji ${rarity.replaceFirstChar { it.uppercase() }}", "×$count", "#E91E63", "#FFFFFF"))
                }
            }
        })
    }
}

// ── TAB 3: GEMME ──────────────────────────────────────────────
internal fun PlayerProfileActivity.buildGemsTab(root: LinearLayout) {
    val p    = PlayerProfileManager.myProfile
    val gems = p?.gems ?: 0

    root.addView(sectionCard("#1A0030", "#A855F7") {
        addView(tv("💎 Le Tue Gemme", 16f, Color.parseColor("#D8B4FE"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(12)) })

        // Big gem display
        addView(LinearLayout(this@buildGemsTab).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            addView(tv("💎", 56f, Color.WHITE, Gravity.CENTER))
            addView(tv("  $gems", 48f, Color.WHITE, Gravity.CENTER, true))
        })

        addView(tv("gemme possedute", 13f, Color.parseColor("#D8B4FE"), Gravity.CENTER)
            .also { it.setPadding(0, 0, 0, dp(12)) })
    })

    root.addView(sectionCard("#120020", "#7B1FA2") {
        addView(tv("🛒 Cosa puoi fare con le gemme", 15f, Color.parseColor("#E1BEE7"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(8)) })
        val uses = listOf(
            "🖼️  Sbloccare frame avatar speciali",
            "🎭  Acquistare skin per le uova",
            "🗺️  Personalizzare il tema della mappa",
            "⚡  Ricaricare energia in anticipo",
            "🃏  Partite extra ai Mini Giochi"
        )
        uses.forEach {
            addView(tv("• $it", 12f, Color.parseColor("#D1C4E9"), Gravity.START)
                .also { v -> v.setPadding(dp(4), dp(4), 0, dp(4)) })
        }
    })

    root.addView(sectionCard("#0A0020", "#6A1B9A") {
        addView(tv("💡 Come ottenere gemme", 15f, Color.parseColor("#D1C4E9"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(8)) })
        val sources = listOf(
            "🏆  Completare achievement speciali",
            "📅  Bonus login giornaliero",
            "🌟  Schiudere un uovo Leggendario",
            "🎉  Premi degli eventi stagionali",
            "🤝  Invitare amici che si registrano"
        )
        sources.forEach {
            addView(tv("• $it", 12f, Color.parseColor("#D8B4FE"), Gravity.START)
                .also { v -> v.setPadding(dp(4), dp(4), 0, dp(4)) })
        }
    })
}

// ── TAB 4: STATISTICHE ────────────────────────────────────────
internal fun PlayerProfileActivity.buildStatsTab(root: LinearLayout) {
    val p         = PlayerProfileManager.myProfile
    val gamesPlayed = MiniGameManager.totalGamesPlayedToday(this)
    val streak    = MiniGameManager.getCurrentStreak(this)

    // Uova
    root.addView(sectionCard("#001A10", "#00FF88") {
        addView(tv("🥚 Uova Raccolte", 15f, Color.parseColor("#66FFB2"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(8)) })
        addView(rowText("🥚 Totale",       "${p?.eggsFound ?: 0}",       "#00FF88", "#FFFFFF"))
        addView(rowText("⚪ Common",        "${p?.commonFound ?: 0}",     "#78909C", "#CFD8DC"))
        addView(rowText("🟢 Uncommon",      "${p?.uncommonFound ?: 0}",   "#66BB6A", "#0F0F2A"))
        addView(rowText("🔵 Rare",          "${p?.rareFound ?: 0}",       "#42A5F5", "#BBDEFB"))
        addView(rowText("🟣 Epic",          "${p?.epicFound ?: 0}",       "#AB47BC", "#E1BEE7"))
        addView(rowText("⭐ Legendary",     "${p?.legendaryFound ?: 0}",  "#FFB300", "#FFF9C4"))
        addView(rowText("📅 Sett. corrente","${p?.weeklyEggsFound ?: 0}", "#00FF88", "#A5D6A7"))
    })

    // Mini giochi
    root.addView(sectionCard("#001530", "#00B4FF") {
        addView(tv("🎮 Mini Giochi", 15f, Color.parseColor("#66CCFF"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(8)) })
        addView(rowText("🎮 Partite oggi",    "$gamesPlayed",         "#00B4FF", "#FFFFFF"))
        addView(rowText("🔥 Streak attivo",   "$streak giorni",       "#FF6B35", "#FFCCBC"))
        addView(rowText("💰 MVC oggi",        "${MiniGameManager.totalMvcEarnedToday(this@buildStatsTab)} MVC", "#00FF88", "#A5D6A7"))
    })

    // Palestra
    root.addView(sectionCard("#1A1000", "#FF9800") {
        addView(tv("🏋️ Palestra & Forza", 15f, Color.parseColor("#FFB74D"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(8)) })
        addView(rowText("🏋️ Allenamenti totali", "${p?.gymTrainings ?: 0}",  "#FF9800", "#FFFFFF"))
        addView(rowText("🏟️ Palestre visitate",   "${p?.gymsVisited ?: 0}",   "#FF9800", "#FFCC80"))
        addView(rowText("💪 Forza attuale",        "${p?.strength ?: 0}",      "#FF9800", "#FFCC80"))
        addView(rowText("⚡ Energia attuale",      "${p?.energy ?: 100}/100",  "#FFC107", "#FFECB3"))
    })

    // Generale
    root.addView(sectionCard("#100010", "#A855F7") {
        addView(tv("📊 Generale", 15f, Color.parseColor("#D8B4FE"), Gravity.START, true)
            .also { it.setPadding(0, 0, 0, dp(8)) })
        addView(rowText("📅 Giorni di login",  "${p?.totalLoginDays ?: 0}", "#A855F7", "#FFFFFF"))
        addView(rowText("⭐ XP settimana",     "${p?.weeklyXp ?: 0L} XP",  "#A855F7", "#E1BEE7"))
    })
}

// ── TAB 5: BADGE ──────────────────────────────────────────────
internal fun PlayerProfileActivity.buildBadgesTab(root: LinearLayout) {
    val locationBadges = LocationBadge.loadAll(this)
    val achBadges      = emptyList<String>()

    // Location badges
    root.addView(sectionCard("#0A0A1A", "#00B4FF") {
        addView(tv("📍 Badge Luogo (${locationBadges.size})", 15f, Color.parseColor("#66CCFF"),
            Gravity.START, true).also { it.setPadding(0, 0, 0, dp(8)) })
        if (locationBadges.isEmpty()) {
            addView(tv("Nessun badge ancora — visita luoghi speciali nella mappa! 🗺️",
                12f, Color.parseColor("#9999CC"), Gravity.START))
        } else {
            val wrap = PlayerProfileActivity.FlowLayout(this@buildBadgesTab)
            locationBadges.forEach { badge ->
                wrap.addView(tv("${badge.emoji} ${badge.name}", 12f, Color.WHITE, Gravity.CENTER).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(20).toFloat()
                        setColor(Color.parseColor("#0D2A4A"))
                    }
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    (layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                        marginEnd = dp(6); bottomMargin = dp(6)
                    }
                })
            }
            addView(wrap)
        }
    })

    // Achievement badge
    root.addView(sectionCard("#001A00", "#00FF88") {
        addView(tv("🏆 Achievement (${achBadges.size})", 15f, Color.parseColor("#66FFB2"),
            Gravity.START, true).also { it.setPadding(0, 0, 0, dp(8)) })
        if (achBadges.isEmpty()) {
            addView(tv("Nessun achievement ancora — gioca, combatti, raccogli uova! 🥚",
                12f, Color.parseColor("#9999CC"), Gravity.START))
        } else {
            achBadges.forEach { id ->
                addView(tv("🏅 $id", 12f, Color.parseColor("#A5D6A7"), Gravity.START)
                    .also { it.setPadding(0, dp(2), 0, dp(2)) })
            }
        }
    })

    // Bottone mini giochi
    root.addView(Button(this).apply {
        text = "🎮 Vai ai Mini Giochi"
        textSize = 15f; setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(50).toFloat()
            setColor(Color.parseColor("#E91E63"))
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
            .also { it.topMargin = dp(16) }
        setOnClickListener {
            startActivity(Intent(this@buildBadgesTab,
                com.intelligame.huntix.MiniGamesHubActivity::class.java))
        }
    })
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
internal fun PlayerProfileActivity.buildGenderCharacterSection(parent: LinearLayout) {
    val profile = PlayerProfileManager.myProfile ?: return
    if (profile.playerGender.isBlank()) return
    val gE = if (profile.playerGender == "male") "🧑" else "👩"
    val gN = if (profile.playerGender == "male") "Maschio" else "Femmina"
    val gC = if (profile.playerGender == "male") "#00B4FF" else "#E91E63"
    parent.addView(TextView(this).apply {
        text = "$gE Sesso: $gN"; textSize = 15f; setTextColor(Color.parseColor(gC)); gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(8) }
    })
    if (profile.equippedAccessories.isNotBlank()) {
        parent.addView(TextView(this).apply {
            text = "🎩 Accessori: ${profile.equippedAccessories.replace(",", ", ")}"
            textSize = 13f; setTextColor(Color.parseColor("#FFD700")); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(8) }
        })
    }
    parent.addView(TextView(this).apply {
        text = "👆 Tocca e ruota il personaggio"; textSize = 11f; setTextColor(Color.parseColor("#9999CC")); gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(6) }
    })
    val glb = if (profile.playerGender == "male") "characters/player/male.glb" else "characters/player/female.glb"
    val accH = if (profile.equippedAccessories.contains("cappello")) """<div slot="hotspot-hat" data-position="0 1.7 0" data-normal="0 1 0">🎩</div>""" else ""
    val viewer = WebView(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(300))
        settings.apply { javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = true; allowContentAccess = true }
        setBackgroundColor(Color.TRANSPARENT); webChromeClient = WebChromeClient()
        setOnTouchListener { v, _ -> v.parent.requestDisallowInterceptTouchEvent(true); false }
        loadDataWithBaseURL("file:///android_asset/", """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1.0">
<script type="module" src="https://ajax.googleapis.com/ajax/libs/model-viewer/4.0.0/model-viewer.min.js"></script>
<style>*{margin:0;padding:0}body{background:transparent;overflow:hidden}model-viewer{width:100vw;height:100vh;background:radial-gradient(ellipse at center,#1a1a3e 0%,#0a0a1a 100%);--poster-color:transparent}model-viewer::part(default-progress-bar){display:none}</style>
</head><body><model-viewer src="$glb" alt="Character" auto-rotate camera-controls touch-action="pan-y" camera-orbit="0deg 75deg 2.5m" min-camera-orbit="auto auto 1.5m" max-camera-orbit="auto auto 5m" field-of-view="30deg" autoplay shadow-intensity="1" exposure="1.2" environment-image="neutral" style="width:100%;height:100%;">$accH</model-viewer></body></html>""".trimIndent(), "text/html", "UTF-8", null)
    }
    parent.addView(viewer)
}
