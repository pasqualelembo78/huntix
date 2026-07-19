package com.intelligame.huntix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EggRarityTest {

    @Test
    fun spawnWeightsArePositiveAndSumToOneHundred() {
        val total = EggRarity.values().sumOf { it.spawnWeight }
        assertEquals(100, total)
        EggRarity.values().forEach { assertTrue("peso non positivo per ${it.id}", it.spawnWeight > 0) }
    }

    @Test
    fun fromIdReturnsCorrectRarityOrCommonFallback() {
        assertSame(EggRarity.LEGENDARY, EggRarity.fromId("legendary"))
        assertSame(EggRarity.COMMON, EggRarity.fromId(""))
        assertSame(EggRarity.COMMON, EggRarity.fromId("does-not-exist"))
    }

    @Test
    fun weightedRandomAlwaysReturnsAValidRarity() {
        repeat(5000) {
            val r = EggRarity.weightedRandom()
            assertTrue(EggRarity.values().contains(r))
        }
    }

    @Test
    fun weightedRandomRespectsRelativeFrequencies() {
        val samples = 200_000
        val counts = EggRarity.values().associateWith { 0 }.toMutableMap()
        repeat(samples) {
            val r = EggRarity.weightedRandom()
            counts[r] = counts.getValue(r) + 1
        }
        // COMMON deve essere il più frequente, LEGENDARY il meno
        assertTrue(counts.getValue(EggRarity.COMMON) > counts.getValue(EggRarity.LEGENDARY))
        // ogni rarità deve comparire almeno una volta su 200k campioni
        EggRarity.values().forEach { assertTrue("${it.id} mai estratto", counts.getValue(it) > 0) }
    }

    @Test
    fun oddsAsListSumsToOneHundredPercent() {
        val total = EggRarity.oddsAsList().sumOf { it.second.toDouble() }
        assertEquals(100.0, total, 0.01)
    }

    @Test
    fun parseHexColorMatchesAndroidFormat() {
        assertEquals(0xFF00CC88.toInt(), EggRarity.parseHexColor("#00CC88"))
        assertEquals(0xFFFF6B35.toInt(), EggRarity.parseHexColor("#FF6B35"))
        assertEquals(0xFFFFD700.toInt(), EggRarity.parseHexColor("#FFD700"))
    }

    @Test
    fun colorAccessorsAreNonZeroAndConsistent() {
        EggRarity.values().forEach {
            assertTrue(it.color != 0)
            assertTrue(it.glowColor != 0)
        }
    }
}
