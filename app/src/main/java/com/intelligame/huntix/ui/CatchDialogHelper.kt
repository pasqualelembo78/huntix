package com.intelligame.huntix.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.EggNutrimentManager
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.EggElement
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
        val foods = EggNutrimentManager.getAvailableFoods(ctx)
        if (foods.isEmpty()) {
            showCaptureGame(ctx, egg, 1f, 1f, onReady)
            return
        }

        val items = mutableListOf<String>()
        items.add("Nessun cibo")
        foods.forEach { (food, qty) ->
            val bonus = EggNutrimentManager.calculateCatchBonus(food, egg.element)
            val reaction = EggNutrimentManager.getReaction(food, egg.element)
            items.add("${food.emoji} ${food.displayName} (×$qty) — ${reaction.emoji} bonus: ×${"%.2f".format(bonus)}")
        }

        AlertDialog.Builder(ctx)
            .setTitle("Scegli un esca per l'uovo (${egg.element.name})")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    EggNutrimentManager.resetEncounter()
                    showCaptureGame(ctx, egg, 1f, 1f, onReady)
                } else {
                    val food = foods[which - 1].first
                    EggNutrimentManager.applyFood(ctx, food, egg.element)
                    val bonus = EggNutrimentManager.currentFoodBonus
                    val xpMul = EggNutrimentManager.currentXpMultiplier
                    showCaptureGame(ctx, egg, bonus, xpMul, onReady)
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showCaptureGame(
        ctx: Context,
        egg: WorldEgg,
        foodBonus: Float,
        xpMultiplier: Float,
        onReady: OnCatchReady
    ) {
        val method = CapturePreferences.getPreferredMethod(ctx)

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 8)
        }

        val titleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val title = TextView(ctx).apply {
            text = "${egg.element.emoji()} ${egg.displayLabel}"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        titleRow.addView(title)

        val methodBadge = TextView(ctx).apply {
            text = "  ${method.emoji} ${method.displayName}  "
            textSize = 11f
            setTextColor(Color.parseColor("#888899"))
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(ctx, 8), 4, UiKit.dp(ctx, 8), 4)
            setBackgroundResource(com.intelligame.huntix.R.drawable.btn_purple)
        }
        val badgeParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = UiKit.dp(ctx, 12) }
        titleRow.addView(methodBadge, badgeParams)
        container.addView(titleRow)

        val subtitle = TextView(ctx).apply {
            text = when (method) {
                CaptureMethod.ELEMENT_SHIELD -> CaptureMethod.getDescriptionForElement(method, egg.element)
                else -> method.description
            }
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#888899"))
            setPadding(0, 0, 0, 8)
        }
        container.addView(subtitle)

        val game = createGame(ctx, method, egg.rarity.color, egg.element)
        val gameView = game.getView()
        gameView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            UiKit.dp(ctx, 320)
        )
        container.addView(gameView)

        val statusText = TextView(ctx).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        container.addView(statusText)

        val dialog = AlertDialog.Builder(ctx)
            .setView(container)
            .setCancelable(true)
            .create()

        game.setListener(object : CaptureMiniGame.Listener {
            override fun onThrowAttempt(attempt: Int, quality: Float) {
                val reactionText = if (EggNutrimentManager.currentAppliedFood != null) {
                    val reaction = EggNutrimentManager.getReaction(
                        EggNutrimentManager.currentAppliedFood!!, egg.element
                    )
                    "${reaction.emoji} ${reaction.message}"
                } else ""
                statusText.text = "Tentativo $attempt/3  $reactionText"
            }

            override fun onCaptured(totalAttempts: Int) {
                statusText.text = "Catturato in $totalAttempts tentativi!"
                dialog.dismiss()
                onReady.onCatchReady(foodBonus, xpMultiplier)
            }

            override fun onEscaped(totalAttempts: Int) {
                if (totalAttempts >= SwipeToCatchView.MAX_ATTEMPTS) {
                    statusText.text = "L'uovo è fuggito..."
                    dialog.dismiss()
                    onReady.onCatchReady(0f, 0f)
                }
            }
        })

        dialog.setOnDismissListener { game.release() }
        game.reset()
        dialog.show()
    }

    private fun createGame(
        ctx: Context,
        method: CaptureMethod,
        rarityColor: Int,
        element: com.intelligame.huntix.EggElement
    ): CaptureMiniGame {
        return when (method) {
            CaptureMethod.SWIPE_LEGACY -> SwipeToCatchView(ctx).also {
                it.setEggColor(rarityColor)
            }
            CaptureMethod.CONCENTRATION -> ConcentrationCatchView(ctx).also {
                it.setEggColor(rarityColor)
            }
            CaptureMethod.PATTERN_TRACE -> PatternTraceCatchView(ctx).also {
                it.setEggColor(rarityColor)
            }
            CaptureMethod.QUICK_CATCH -> QuickCatchView(ctx).also {
                it.setEggColor(rarityColor)
            }
            CaptureMethod.RHYTHM_TAP -> RhythmTapCatchView(ctx).also {
                it.setEggColor(rarityColor)
            }
            CaptureMethod.ELEMENT_SHIELD -> ElementShieldCatchView(ctx).also {
                it.setEggColor(rarityColor)
                it.setElement(element)
            }
        }
    }

    private fun EggElement.emoji(): String = when (this) {
        EggElement.WATER -> "\uD83D\uDCA7"
        EggElement.FIRE -> "\uD83D\uDD25"
        EggElement.EARTH -> "\uD83C\uDF0D"
        EggElement.AIR -> "\uD83D\uDCA8"
        EggElement.NORMAL -> "\u26AA"
    }
}
