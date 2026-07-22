package com.intelligame.huntix.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.UiKit

/**
 * RaidLootActivity — schermo ricompense post-raid con animazione.
 *
 * Mostra le ricompense una ad una con animazione di apparizione.
 * Riceve i dati tramite Intent extras.
 */
class RaidLootActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private lateinit var rewardContainer: LinearLayout
    private lateinit var titleText: TextView
    private var rewards = mutableListOf<RewardItem>()

    data class RewardItem(
        val emoji: String,
        val label: String,
        val amount: String,
        val color: String
    )

    companion object {
        fun start(
            ctx: android.content.Context,
            bossEmoji: String,
            bossName: String,
            mvcReward: Int,
            xpReward: Int,
            eggRarityId: String,
            candiesDropped: Int,
            itemsDropped: List<String>
        ) {
            val intent = android.content.Intent(ctx, RaidLootActivity::class.java).apply {
                putExtra("bossEmoji", bossEmoji)
                putExtra("bossName", bossName)
                putExtra("mvcReward", mvcReward)
                putExtra("xpReward", xpReward)
                putExtra("eggRarityId", eggRarityId)
                putExtra("candiesDropped", candiesDropped)
                putStringArrayListExtra("itemsDropped", ArrayList(itemsDropped))
            }
            ctx.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bossEmoji = intent.getStringExtra("bossEmoji") ?: "🏟️"
        val bossName = intent.getStringExtra("bossName") ?: "Boss"
        val mvcReward = intent.getIntExtra("mvcReward", 100)
        val xpReward = intent.getIntExtra("xpReward", 200)
        val eggRarityId = intent.getStringExtra("eggRarityId") ?: "common"
        val candiesDropped = intent.getIntExtra("candiesDropped", 0)
        val itemsDropped = intent.getStringArrayListExtra("itemsDropped") ?: arrayListOf()

        // Build rewards list
        rewards.add(RewardItem("💰", "MVC", "+$mvcReward", "#FFD700"))
        rewards.add(RewardItem("⚡", "Esperienza", "+$xpReward XP", "#7C4DFF"))

        val rarity = EggRarity.fromId(eggRarityId)
        rewards.add(RewardItem("🥚", "Uovo ${rarity.name}", rarity.randomName(), rarity.colorHex))

        if (candiesDropped > 0) {
            rewards.add(RewardItem("🍬", "Caramelle", "+$candiesDropped", "#FF6B9D"))
        }

        itemsDropped.forEach { item ->
            rewards.add(RewardItem("📦", item, "×1", "#4FC3F7"))
        }

        // Badge raids completati
        val completedRaids = com.intelligame.huntix.managers.RaidManager.getCompletedRaids(this)
        rewards.add(RewardItem("🏅", "Raid Totali", "$completedRaids", "#66BB6A"))

        // UI
        titleText = TextView(this).apply {
            text = "🎉 Raid Vinto!"
            textSize = 28f
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(0, UiKit.dp(this@RaidLootActivity, 24), 0, UiKit.dp(this@RaidLootActivity, 8))
        }

        val bossLabel = TextView(this).apply {
            text = "$bossEmoji $bossName"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(this@RaidLootActivity, 16))
        }

        rewardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val doneButton = UiKit.button(this, "✅ Raccogli tutto!", UiKit.GREEN) {
            finish()
        }

        val content = UiKit.scroll(this, titleText, bossLabel, rewardContainer, doneButton)
        setContentView(content)

        // Animate rewards appearing one by one
        animateRewards()
    }

    private fun animateRewards() {
        rewardContainer.removeAllViews()

        rewards.forEachIndexed { index, reward ->
            val row = createRewardRow(reward)
            row.alpha = 0f
            row.translationY = 30f
            row.scaleX = 0.8f
            row.scaleY = 0.8f
            rewardContainer.addView(row)

            row.postDelayed({
                animateRewardRow(row)
            }, 300L + index * 400L)
        }
    }

    private fun createRewardRow(reward: RewardItem): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(this@RaidLootActivity, 16), UiKit.dp(this@RaidLootActivity, 12),
                UiKit.dp(this@RaidLootActivity, 16), UiKit.dp(this@RaidLootActivity, 12))

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(UiKit.dp(this@RaidLootActivity, 32), UiKit.dp(this@RaidLootActivity, 4),
                    UiKit.dp(this@RaidLootActivity, 32), UiKit.dp(this@RaidLootActivity, 4))
            }

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A2E"))
                cornerRadius = UiKit.dp(this@RaidLootActivity, 12).toFloat()
                setStroke(2, Color.parseColor(reward.color))
            }

            val emojiText = TextView(this@RaidLootActivity).apply {
                text = reward.emoji
                textSize = 28f
                setPadding(0, 0, UiKit.dp(this@RaidLootActivity, 12), 0)
            }

            val labelText = TextView(this@RaidLootActivity).apply {
                text = reward.label
                textSize = 15f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val amountText = TextView(this@RaidLootActivity).apply {
                text = reward.amount
                textSize = 16f
                setTextColor(Color.parseColor(reward.color))
                setTypeface(Typeface.DEFAULT_BOLD)
            }

            addView(emojiText)
            addView(labelText)
            addView(amountText)
        }
    }

    private fun animateRewardRow(row: LinearLayout) {
        val fadeIn = ObjectAnimator.ofFloat(row, "alpha", 0f, 1f).apply {
            duration = 300
        }
        val slideUp = ObjectAnimator.ofFloat(row, "translationY", 30f, 0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }
        val scaleInX = ObjectAnimator.ofFloat(row, "scaleX", 0.8f, 1f).apply {
            duration = 400
            interpolator = OvershootInterpolator()
        }
        val scaleInY = ObjectAnimator.ofFloat(row, "scaleY", 0.8f, 1f).apply {
            duration = 400
            interpolator = OvershootInterpolator()
        }

        AnimatorSet().apply {
            playTogether(fadeIn, slideUp, scaleInX, scaleInY)
            start()
        }
    }
}
