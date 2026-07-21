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
            showTimingBar(ctx, 1f, 1f, onReady)
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
                    showTimingBar(ctx, 1f, 1f, onReady)
                } else {
                    val food = foods[which - 1].first
                    val reaction = EggFoodManager.applyFood(ctx, food, egg.element)
                    val bonus = EggFoodManager.currentFoodBonus
                    val xpMul = EggFoodManager.currentXpMultiplier
                    showTimingBar(ctx, bonus, xpMul, onReady)
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showTimingBar(
        ctx: Context,
        foodBonus: Float,
        xpMultiplier: Float,
        onReady: OnCatchReady
    ) {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 8)
        }

        val title = TextView(ctx).apply {
            text = "Tocca per fermare nella zona dorata!"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val timingBar = CatchTimingBarView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiKit.dp(ctx, 64)
            )
        }

        container.addView(title)
        container.addView(timingBar)

        val dialog = AlertDialog.Builder(ctx)
            .setView(container)
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            timingBar.listener = object : CatchTimingBarView.OnTimingResult {
                override fun onResult(success: Boolean, zoneMultiplier: Float) {
                    dialog.dismiss()
                    val effectiveBonus = if (success) foodBonus * zoneMultiplier else 0f
                    onReady.onCatchReady(effectiveBonus, xpMultiplier)
                }
            }
            timingBar.startTiming()
        }

        dialog.show()
    }
}
