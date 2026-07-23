package com.intelligame.huntix.battle

import com.intelligame.huntix.battle.CharacterRenderer.AnimState

data class AnimDef(
    val state: AnimState,
    val fileName: String,
    val frameCount: Int,
    val frameRate: Float,
    val loop: Boolean
)

data class CharacterSpriteSet(
    val name: String,
    val animations: Map<AnimState, AnimDef>
) {
    companion object {
        val DEFAULT = CharacterSpriteSet(
            name = "fighter",
            animations = mapOf(
                AnimState.IDLE to AnimDef(AnimState.IDLE, "idle", 4, 6f, true),
                AnimState.WALK to AnimDef(AnimState.WALK, "walk", 6, 10f, true),
                AnimState.LIGHT_ATTACK to AnimDef(AnimState.LIGHT_ATTACK, "light_attack", 4, 12f, false),
                AnimState.HEAVY_ATTACK to AnimDef(AnimState.HEAVY_ATTACK, "heavy_attack", 5, 10f, false),
                AnimState.SPECIAL_ATTACK to AnimDef(AnimState.SPECIAL_ATTACK, "special_attack", 6, 10f, false),
                AnimState.BLOCK to AnimDef(AnimState.BLOCK, "block", 2, 6f, false),
                AnimState.HIT_REACT to AnimDef(AnimState.HIT_REACT, "hit_react", 3, 10f, false),
                AnimState.KO to AnimDef(AnimState.KO, "ko", 5, 8f, false),
                AnimState.VICTORY to AnimDef(AnimState.VICTORY, "victory", 4, 6f, true)
            )
        )
    }
}

object SpriteSheetPaths {
    const val DIR = "battle"

    fun characterDir(name: String) = "$DIR/$name"

    fun animationFile(charName: String, animName: String) =
        "$DIR/$charName/$animName.png"

    fun sheetFile(charName: String) =
        "$DIR/$charName/sheet.png"
}
