package com.intelligame.huntix.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.EggFoodManager
import com.intelligame.huntix.EggFoodManager.EggFood
import com.intelligame.huntix.EggElement
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.WorldEgg

object CatchDialogHelper {

    interface OnCatchReady {
        fun onCatchReady(foodBonus: Float, xpMultiplier: Float)
    }

    fun showFoodSelection(
        ctx: Context,
        egg: WorldEgg,
        onReady: OnCatchReady
    ) {
        val foods = EggFoodManager.getAvailableFoods(ctx)
        if (foods.isEmpty()) {
            showSwipeCatch(ctx, egg, 1f, 1f, onReady)
            return
        }

        val items = mutableListOf<String>()
        items.add("Nessun cibo")
        foods.forEach { (food, qty) ->
            val bonus = EggFoodManager.calculateCatchBonus(food, egg.element)
            val reaction = EggFoodManager.getReaction(food, egg.element)
            items.add("${food.emoji} ${food.displayName} (×$qty) — ${reaction.emoji} bonus: ×${"%.2f".format(bonus)}")
        }

        AlertDialog.Builder(ctx)
            .setTitle("Scegli un esca per l'uovo (${egg.element.name})")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    EggFoodManager.resetEncounter()
                    showSwipeCatch(ctx, egg, 1f, 1f, onReady)
                } else {
                    val food = foods[which - 1].first
                    val reaction = EggFoodManager.applyFood(ctx, food, egg.element)
                    val bonus = EggFoodManager.currentFoodBonus
                    val xpMul = EggFoodManager.currentXpMultiplier
                    showSwipeCatch(ctx, egg, bonus, xpMul, onReady)
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showSwipeCatch(
        ctx: Context,
        egg: WorldEgg,
        foodBonus: Float,
        xpMultiplier: Float,
        onReady: OnCatchReady
    ) {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 8)
        }

        val title = TextView(ctx).apply {
            text = "${egg.element.emoji()} ${egg.displayLabel}"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(0, 0, 0, 4)
        }

        val subtitle = TextView(ctx).apply {
            text = "Swipe verso l'alto per lanciare! (3 tentativi)"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#888899"))
            setPadding(0, 0, 0, 8)
        }

        val swipeView = SwipeToCatchView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiKit.dp(ctx, 300)
            )
            setEggColor(egg.rarity.color)
        }

        val statusText = TextView(ctx).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        container.addView(title)
        container.addView(subtitle)
        container.addView(swipeView)
        container.addView(statusText)

        val dialog = AlertDialog.Builder(ctx)
            .setView(container)
            .setCancelable(true)
            .create()

        swipeView.listener = object : SwipeToCatchView.OnCatchResult {
            override fun onThrowAttempt(attempt: Int, quality: Float) {
                val reactionText = if (EggFoodManager.currentAppliedFood != null) {
                    val reaction = EggFoodManager.getReaction(
                        EggFoodManager.currentAppliedFood!!, egg.element
                    )
                    "${reaction.emoji} ${reaction.message}"
                } else ""
                statusText.text = "Lancio $attempt/3 $reactionText"
            }

            override fun onCaptured(totalAttempts: Int) {
                statusText.text = "Catturato in $totalAttempts lanci!"
                dialog.dismiss()
                val effectiveBonus = foodBonus
                onReady.onCatchReady(effectiveBonus, xpMultiplier)
            }

            override fun onEscaped(totalAttempts: Int) {
                if (totalAttempts >= SwipeToCatchView.MAX_ATTEMPTS) {
                    statusText.text = "L'uovo è fuggito..."
                    dialog.dismiss()
                    onReady.onCatchReady(0f, 0f)
                }
            }
        }

        dialog.show()
    }

    private fun EggElement.emoji(): String = when (this) {
        com.intelligame.huntix.EggElement.WATER -> "💧"
        com.intelligame.huntix.EggElement.FIRE -> "🔥"
        com.intelligame.huntix.EggElement.EARTH -> "🌍"
        com.intelligame.huntix.EggElement.AIR -> "💨"
        com.intelligame.huntix.EggElement.NORMAL -> "⚪"
    }
}
