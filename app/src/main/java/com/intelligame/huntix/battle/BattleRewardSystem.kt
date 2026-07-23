package com.intelligame.huntix.battle

import android.content.Context
import com.intelligame.huntix.PlayerProfileManager
import com.intelligame.huntix.managers.SavedManager

/** Ricompense di fine battaglia. */
data class Reward(
    val xpGained: Int = 0,
    val mvcGained: Int = 0,
    val gemsGained: Int = 0
)

/** Calcola e applica le ricompense della battaglia. */
object BattleRewardSystem {

    fun calculateRewards(
        battle: BattleEngine,
        result: BattleEngine.TickResult,
        isRare: Boolean,
        isUnstable: Boolean
    ): Reward {
        val scale = battle.difficultyScale
        return when (result) {
            BattleEngine.TickResult.ENEMY_DEFEATED -> {
                val xp = (100 * scale).toInt() + if (isRare) 80 else 0
                val mvc = (50 * scale).toInt() + if (isUnstable) 40 else 0
                val gems = if (isRare) 5 else 1
                Reward(xp, mvc, gems)
            }
            BattleEngine.TickResult.TIME_UP -> {
                val xp = (30 * scale).toInt()
                val mvc = (10 * scale).toInt()
                Reward(xp, mvc, 0)
            }
            BattleEngine.TickResult.PLAYER_DEFEATED -> Reward(0, 0, 0)
        }
    }

    fun applyRewards(context: Context, reward: Reward, isWin: Boolean = false) {
        if (reward.xpGained > 0 || reward.gemsGained > 0) {
            val p = PlayerProfileManager.myProfile
            p?.let {
                it.xp = it.xp + reward.xpGained
                it.gems = it.gems + reward.gemsGained
                PlayerProfileManager.persistMyProfile()
            }
        }
        if (reward.mvcGained > 0) {
            SavedManager.addMvc(context, reward.mvcGained.toDouble())
            com.intelligame.huntix.managers.ResearchTaskManager.trackProgress(context, "earn_500_mvc", reward.mvcGained)
        }
        if (isWin) {
            com.intelligame.huntix.managers.ResearchTaskManager.trackProgress(context, "win_battle")
            com.intelligame.huntix.managers.ResearchTaskManager.trackProgress(context, "win_5_battles")
        }
    }
}
