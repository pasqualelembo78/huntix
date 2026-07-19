package com.intelligame.huntix

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView

// ── Multiplayer: leaderboard & chat in-game ────────────────────
internal fun MainActivity.updateMpLeaderboard(scores: List<MultiplayerManager.PlayerScore>) {
    if (!isMultiplayer) return
    val medals = listOf("🥇","🥈","🥉","4️⃣","5️⃣","6️⃣")
    val sb = StringBuilder()
    scores.forEachIndexed { i, s ->
        val medal = medals.getOrElse(i) { "👤" }
        val nameLabel = if (s.playerId == mpPlayerId) "${s.playerName} ◀" else s.playerName
        val finFlag = if (s.finished) " ✅" else ""
        sb.append("$medal $nameLabel  ${s.eggsFound}🥚  ${fmtMs(s.totalMs)}$finFlag\n")
    }
    binding.mpLeaderboardContent.text = sb.toString().trimEnd()
}

internal fun MainActivity.toggleChatOverlay() {
    mpChatOpen = !mpChatOpen
    if (mpChatOpen) {
        mpUnreadCount = 0
        binding.mpChatBadge.visibility = android.view.View.GONE
        binding.mpChatOverlay.visibility = android.view.View.VISIBLE
        binding.mpChatOverlay.alpha = 0f
        binding.mpChatOverlay.animate().alpha(1f).setDuration(200).start()
        binding.mpChatScrollView.post { binding.mpChatScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    } else {
        binding.mpChatOverlay.animate().alpha(0f).setDuration(180)
            .withEndAction { binding.mpChatOverlay.visibility = android.view.View.GONE }
            .start()
    }
}

internal fun MainActivity.sendInGameChat() {
    val text = binding.mpChatInput.text.toString().trim()
    if (text.isEmpty()) return
    mpManager.sendChatMessage(text)
    binding.mpChatInput.setText("")
    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
    imm.hideSoftInputFromWindow(binding.mpChatInput.windowToken, 0)
}

internal fun MainActivity.handleInGameChat(msg: MultiplayerManager.ChatMessage) {
    addChatBubbleToOverlay(msg)
    if (!mpChatOpen) {
        mpUnreadCount++
        binding.mpChatBadge.text = if (mpUnreadCount > 9) "9+" else mpUnreadCount.toString()
        binding.mpChatBadge.visibility = android.view.View.VISIBLE
        // Mini notifica toast per messaggi di altri giocatori
        if (msg.type == "msg" && msg.senderId != mpPlayerId) {
            Toast.makeText(this, "💬 ${msg.senderName}: ${msg.text.take(40)}", Toast.LENGTH_SHORT).show()
        }
    }
    if (mpChatOpen) {
        binding.mpChatScrollView.post { binding.mpChatScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }
}

internal fun MainActivity.addChatBubbleToOverlay(msg: MultiplayerManager.ChatMessage) {
    val list = binding.mpChatMessageList
    val isMe     = msg.senderId == mpPlayerId
    val isSystem = msg.type == "system"
    val dp = resources.displayMetrics.density

    if (isSystem) {
        val tv = android.widget.TextView(this).apply {
            text = msg.text; textSize = 11f
            setTextColor(android.graphics.Color.parseColor("#AAFFC107"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, (3*dp).toInt(), 0, (3*dp).toInt())
        }
        list.addView(tv); return
    }

    val bubble = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        gravity = if (isMe) android.view.Gravity.END else android.view.Gravity.START
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (6*dp).toInt() }
    }
    if (!isMe) {
        val nameTv = android.widget.TextView(this).apply {
            text = msg.senderName; textSize = 10f
            setTextColor(android.graphics.Color.parseColor("#9999CC"))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setPadding((4*dp).toInt(), 0, 0, (2*dp).toInt())
        }
        bubble.addView(nameTv)
    }
    val bgColor = if (isMe) "#00E5FF" else "#1C2E40"
    val card = androidx.cardview.widget.CardView(this).apply {
        radius = (12*dp)
        cardElevation = 0f
        setCardBackgroundColor(android.graphics.Color.parseColor(bgColor))
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.gravity = if (isMe) android.view.Gravity.END else android.view.Gravity.START
            it.marginStart = if (isMe) (40*dp).toInt() else 0
            it.marginEnd   = if (isMe) 0 else (40*dp).toInt()
        }
    }
    val tv = android.widget.TextView(this).apply {
        text = msg.text; textSize = 14f
        setTextColor(android.graphics.Color.WHITE)
        setPadding((10*dp).toInt(), (6*dp).toInt(), (10*dp).toInt(), (6*dp).toInt())
    }
    card.addView(tv); bubble.addView(card); list.addView(bubble)
}
