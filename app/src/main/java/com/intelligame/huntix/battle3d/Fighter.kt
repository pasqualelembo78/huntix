package com.intelligame.huntix.battle3d

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class FightStyle {
    BOXE, KARATE, CAPOEIRA, KICKBOXING, NINJA, ROBOT
}

data class FighterAnimSet(
    val idle: String = "Idle",
    val walk: String = "Walking",
    val punch: String = "Punch",
    val kick: String = "Kick",
    val uppercut: String = "Uppercut",
    val roundhouse: String = "Roundhouse",
    val special: String = "Special",
    val hit: String = "Hit_Reaction",
    val ko: String = "KO",
    val victory: String = "Victory",
    val block: String = "Block"
)

data class FighterDef(
    val id: String,
    val name: String,
    val style: FightStyle,
    val glbFile: String,
    val primaryColor: Int,
    val secondaryColor: Int,
    val animSet: FighterAnimSet = FighterAnimSet(),
    val baseHp: Float = 100f,
    val speed: Float = 1f,
    val power: Float = 1f,
    val specialName: String = "Onda Energetica",
    val specialDamage: Float = 30f
)

data class FighterState(
    var hp: Float,
    var maxHp: Float,
    var x: Float,
    var y: Float = 0f,
    var vy: Float = 0f,
    var facing: Int = 1,
    var isGrounded: Boolean = true,
    var stunTimer: Float = 0f,
    var attackTimer: Float = 0f,
    var specialCooldown: Float = 0f,
    var superBar: Float = 0f,
    var isBlocking: Boolean = false,
    var isMoving: Boolean = false,
    var currentAnim: String = "Idle",
    var animTime: Float = 0f,
    var comboCount: Int = 0,
    var totalDamage: Int = 0,
    var hitsLanded: Int = 0,
    var isKO: Boolean = false
) {
    fun isStunned() = stunTimer > 0f
    fun canAct() = !isStunned() && attackTimer <= 0f && !isKO
}

object FighterRegistry {
    val ALL: List<FighterDef> = listOf(
        FighterDef("kaya", "Kaya", FightStyle.KICKBOXING, "fighters/kaya.glb",
            0xFFFF6B6B.toInt(), 0xFFFFD93D.toInt(), specialName = "Calcio Tornado", specialDamage = 32f),
        FighterDef("tim", "Tim", FightStyle.BOXE, "fighters/tim.glb",
            0xFF4ECDC4.toInt(), 0xFF292F36.toInt(), specialName = "Mega Pugno", specialDamage = 30f),
        FighterDef("paladin", "Paladin", FightStyle.KARATE, "fighters/paladin.glb",
            0xFF6C5CE7.toInt(), 0xFFFDCB6E.toInt(), specialName = "Onda Luminosa", specialDamage = 35f),
        FighterDef("aurora", "Aurora", FightStyle.CAPOEIRA, "fighters/aurora.glb",
            0xFFE84393.toInt(), 0xFFFD79A8.toInt(), specialName = "Giro della Morte", specialDamage = 28f),
        FighterDef("goblin", "Goblin", FightStyle.NINJA, "fighters/goblin.glb",
            0xFF00B894.toInt(), 0xFF55EFC4.toInt(), specialName = "Artiglio Oscuro", specialDamage = 34f),
        FighterDef("robot", "Robot", FightStyle.ROBOT, "fighters/robot.glb",
            0xFF0984E3.toInt(), 0xFF74B9FF.toInt(), specialName = "Raggio Laser", specialDamage = 38f)
    )

    fun random() = ALL[Random.nextInt(ALL.size)]
    fun byId(id: String) = ALL.find { it.id == id } ?: ALL[0]
}
