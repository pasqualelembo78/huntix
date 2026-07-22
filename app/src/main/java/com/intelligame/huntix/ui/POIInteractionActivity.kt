package com.intelligame.huntix.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.R
import com.intelligame.huntix.manager.OutdoorManager
import com.intelligame.huntix.managers.SavedManager

class POIInteractionActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private val mgr = OutdoorManager.get()
    private var poiId: String = ""

    private lateinit var tvPoiIcon: TextView
    private lateinit var tvPoiName: TextView
    private lateinit var tvPoiDistance: TextView
    private lateinit var btnSpin: TextView
    private lateinit var tvSpinHint: TextView
    private lateinit var cooldownOverlay: LinearLayout
    private lateinit var tvCooldown: TextView
    private lateinit var rewardPopup: FrameLayout
    private lateinit var rewardMvc: LinearLayout
    private lateinit var tvRewardMvc: TextView
    private lateinit var rewardXp: LinearLayout
    private lateinit var tvRewardXp: TextView
    private lateinit var rewardItem: LinearLayout
    private lateinit var tvRewardItemIcon: TextView
    private lateinit var tvRewardItem: TextView
    private lateinit var rewardEgg: LinearLayout
    private lateinit var tvRewardEgg: TextView

    private var hasSpun = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_poi_interaction)

        poiId = intent.getStringExtra("poiId") ?: mgr.getPois().firstOrNull()?.id ?: ""

        tvPoiIcon = findViewById(R.id.tvPoiIcon)
        tvPoiName = findViewById(R.id.tvPoiName)
        tvPoiDistance = findViewById(R.id.tvPoiDistance)
        btnSpin = findViewById(R.id.btnSpin)
        tvSpinHint = findViewById(R.id.tvSpinHint)
        cooldownOverlay = findViewById(R.id.cooldownOverlay)
        tvCooldown = findViewById(R.id.tvCooldown)
        rewardPopup = findViewById(R.id.rewardPopup)
        rewardMvc = findViewById(R.id.rewardMvc)
        tvRewardMvc = findViewById(R.id.tvRewardMvc)
        rewardXp = findViewById(R.id.rewardXp)
        tvRewardXp = findViewById(R.id.tvRewardXp)
        rewardItem = findViewById(R.id.rewardItem)
        tvRewardItemIcon = findViewById(R.id.tvRewardItemIcon)
        tvRewardItem = findViewById(R.id.tvRewardItem)
        rewardEgg = findViewById(R.id.rewardEgg)
        tvRewardEgg = findViewById(R.id.tvRewardEgg)

        loadPoiData()

        btnSpin.setOnClickListener { onSpin() }
        rewardPopup.setOnClickListener { dismissReward() }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun loadPoiData() {
        val poi = mgr.getPois().firstOrNull { it.id == poiId }
        if (poi == null) {
            Toast.makeText(this, "POI non disponibile", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvPoiName.text = poi.name
        tvPoiDistance.text = "${mgr.distanceMeters(poi).toInt()} m"

        if (poi.spun) {
            showCooldown()
        }
    }

    private fun onSpin() {
        if (hasSpun) return

        val poi = mgr.getPois().firstOrNull { it.id == poiId } ?: return
        if (poi.spun) {
            showCooldown()
            return
        }

        // Disable spin button during animation
        btnSpin.isEnabled = false
        tvSpinHint.visibility = View.GONE

        // Phase 1: Rotate the POI icon 360 degrees
        val rotateAnim = ObjectAnimator.ofFloat(tvPoiIcon, View.ROTATION, 0f, 360f)
        rotateAnim.duration = 600
        rotateAnim.interpolator = AccelerateDecelerateInterpolator()
        rotateAnim.start()

        // Phase 2: After rotation, show rewards with stagger animation
        rotateAnim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                spinAndShowRewards()
            }
        })
    }

    private fun spinAndShowRewards() {
        val msg = mgr.spinPoi(this, poiId)
        hasSpun = true

        // Parse rewards from the message
        // Format: "Palestra spinnata! +$gemReward 💎 +${xpReward} XP"
        val mvcMatch = Regex("\\+(\\d+)").find(msg)
        val xpMatch = Regex("\\+(\\d+) XP").find(msg)

        val mvcAmount = mvcMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val xpAmount = xpMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        // Determine bonus item (random)
        val bonusItem = when ((0..100).random()) {
            in 0..40 -> Pair("\uD83C\uDF56", "Carne Base")  // food
            in 41..70 -> Pair("\uD83D\uDD27", "Cacciavite")  // tool
            in 71..90 -> Pair("\uD83C\uDF6D", "Miele")  // berry
            else -> Pair("\u2B50", "Stella Rara")  // star
        }

        // Random egg drop (20% chance)
        val hasEgg = (0..100).random() < 20

        // Show reward popup with stagger
        showRewardPopup(mvcAmount, xpAmount, bonusItem, hasEgg)
    }

    private fun showRewardPopup(mvc: Int, xp: Int, item: Pair<String, String>, hasEgg: Boolean) {
        rewardPopup.visibility = View.VISIBLE
        rewardPopup.alpha = 0f
        rewardPopup.animate().alpha(1f).setDuration(300).start()

        // Reset all rewards
        rewardMvc.visibility = View.GONE
        rewardXp.visibility = View.GONE
        rewardItem.visibility = View.GONE
        rewardEgg.visibility = View.GONE

        // Stagger rewards
        var delay = 200L

        if (mvc > 0) {
            rewardMvc.postDelayed({
                rewardMvc.visibility = View.VISIBLE
                rewardMvc.alpha = 0f
                rewardMvc.animate().alpha(1f).setDuration(300).start()
                tvRewardMvc.text = "+$mvc"
            }, delay)
            delay += 300
        }

        if (xp > 0) {
            rewardXp.postDelayed({
                rewardXp.visibility = View.VISIBLE
                rewardXp.alpha = 0f
                rewardXp.animate().alpha(1f).setDuration(300).start()
                tvRewardXp.text = "+$xp XP"
            }, delay)
            delay += 300
        }

        rewardItem.postDelayed({
            rewardItem.visibility = View.VISIBLE
            rewardItem.alpha = 0f
            rewardItem.animate().alpha(1f).setDuration(300).start()
            tvRewardItemIcon.text = item.first
            tvRewardItem.text = item.second
        }, delay)
        delay += 300

        if (hasEgg) {
            rewardEgg.postDelayed({
                rewardEgg.visibility = View.VISIBLE
                rewardEgg.alpha = 0f
                rewardEgg.animate().alpha(1f).setDuration(300).start()
                tvRewardEgg.text = "Uovo ottenuto!"
            }, delay)
        }
    }

    private fun dismissReward() {
        rewardPopup.animate().alpha(0f).setDuration(200).withEndAction {
            rewardPopup.visibility = View.GONE
            showCooldown()
        }.start()
    }

    private fun showCooldown() {
        val poi = mgr.getPois().firstOrNull { it.id == poiId }
        if (poi != null && mgr.isPoiOnCooldown(poi)) {
            val remaining = mgr.getPoiCooldownRemaining(poi)
            val mins = (remaining / 60000).toInt()
            val secs = ((remaining % 60000) / 1000).toInt()
            tvCooldown.text = "Disponibile tra ${mins}:${String.format("%02d", secs)}"
        } else {
            tvCooldown.text = "Gia visitato!\nTorna piu tardi"
        }
        btnSpin.visibility = View.GONE
        tvSpinHint.visibility = View.GONE
        cooldownOverlay.visibility = View.VISIBLE
    }
}
