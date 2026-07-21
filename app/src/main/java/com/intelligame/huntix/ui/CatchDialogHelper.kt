package com.intelligame.huntix.ui

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.EggFoodManager
import com.intelligame.huntix.EggFoodManager.EggFood
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
            text = "Swipe verso l'alto per lanciare il cestino!"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val swipeView = SwipeToCatchView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiKit.dp(ctx, 280)
            )
            setEggColor(egg.rarity.color)
        }

        container.addView(title)
        container.addView(swipeView)

        val dialog = AlertDialog.Builder(ctx)
            .setView(container)
            .setCancelable(true)
            .create()

        swipeView.listener = object : SwipeToCatchView.OnThrowResult {
            override fun onResult(quality: Float) {
                val effectiveBonus = if (quality >= 0.3f) foodBonus * quality else 0f
                dialog.dismiss()
                onReady.onCatchReady(effectiveBonus, xpMultiplier)
            }
        }

        dialog.show()
    }
}
