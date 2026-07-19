package com.intelligame.huntix.social

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.intelligame.huntix.BaseNavActivity
import androidx.cardview.widget.CardView
import com.intelligame.huntix.AgeGateManager

/**
 * ChatListActivity - Lista conversazioni stile WhatsApp.
 * Mostra: Chat Globale + lista amici con cui chattare.
 */
class ChatListActivity : BaseNavActivity() {

    override fun activeTab() = "Chat"
    private lateinit var chatsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AgeGateManager.checkAdultAccess(this, "Chat")) { finish(); return }
        buildUI()
        loadChats()
    }

    override fun onResume() { super.onResume(); loadChats() }

    private fun buildUI() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Header WhatsApp-style
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#075E54"))
            setPadding(dp(16), dp(40), dp(16), dp(12))
        }
        header.addView(Button(this).apply {
            text = "<"; textSize = 20f; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }; layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
        header.addView(TextView(this).apply {
            text = "Chat"; textSize = 22f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(8) }
        })
        root.addView(header)

        // Scrollable chat list
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.WHITE)
        }
        chatsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(80))
        }
        scroll.addView(chatsContainer)
        root.addView(scroll)

        setContentView(root)
    }

    private fun loadChats() {
        chatsContainer.removeAllViews()

        // 1. Chat Globale (sempre in cima)
        chatsContainer.addView(chatRow(
            initial = "G", initialColor = "#25D366", name = "Chat Globale",
            subtitle = "Tutti i giocatori", showOnline = true
        ) { startActivity(Intent(this, ChatActivity::class.java)) })

        // Separatore
        chatsContainer.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#F0F0F0"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        })

        // 2. Lista amici
        FriendsManager.getFriends { friends ->
            runOnUiThread {
                if (friends.isEmpty()) {
                    chatsContainer.addView(TextView(this).apply {
                        text = "Nessun amico ancora.\nVai su Amici per cercarne!"
                        textSize = 14f; setTextColor(Color.parseColor("#999999")); gravity = Gravity.CENTER
                        setPadding(dp(24), dp(40), dp(24), dp(40))
                    })
                } else {
                    friends.forEach { f ->
                        val name = f["name"] as? String ?: "?"
                        val uid = f["uid"] as? String ?: return@forEach
                        chatsContainer.addView(chatRow(
                            initial = name.first().uppercase(),
                            initialColor = "#128C7E",
                            name = name,
                            subtitle = "Tocca per chattare"
                        ) {
                            startActivity(Intent(this, ChatActivity::class.java).apply {
                                putExtra("friendUid", uid); putExtra("friendName", name)
                            })
                        })
                        // Divider
                        chatsContainer.addView(View(this).apply {
                            setBackgroundColor(Color.parseColor("#F5F5F5"))
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).also { it.marginStart = dp(72) }
                        })
                    }
                }
            }
        }
    }

    private fun chatRow(initial: String, initialColor: String, name: String, subtitle: String, showOnline: Boolean = false, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
            setBackgroundColor(Color.WHITE)
        }

        // Avatar circle
        val avatarFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50))
        }
        avatarFrame.addView(TextView(this).apply {
            text = initial; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(initialColor)) }
            layoutParams = FrameLayout.LayoutParams(dp(50), dp(50))
        })
        if (showOnline) {
            avatarFrame.addView(View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#00FF88")); setStroke(dp(2), Color.WHITE) }
                layoutParams = FrameLayout.LayoutParams(dp(14), dp(14), Gravity.BOTTOM or Gravity.END)
            })
        }
        row.addView(avatarFrame)

        // Name + subtitle
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(14) }
        }
        info.addView(TextView(this).apply {
            text = name; textSize = 17f; setTextColor(Color.parseColor("#1A1A1A"))
            typeface = Typeface.DEFAULT_BOLD
        })
        info.addView(TextView(this).apply {
            text = subtitle; textSize = 13f; setTextColor(Color.parseColor("#999999"))
        })
        row.addView(info)

        // Arrow
        row.addView(TextView(this).apply {
            text = ">"; textSize = 18f; setTextColor(Color.parseColor("#CCCCCC"))
        })

        return row
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
