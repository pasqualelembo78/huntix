package com.intelligame.huntix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class WorldEggTest {

    @Test
    fun defaultsAreSensible() {
        val egg = WorldEgg()
        assertFalse(egg.found)
        assertSame(EggRarity.COMMON, egg.rarity)
        assertEquals(0.0, egg.lat, 0.0)
        assertEquals(0.0, egg.lng, 0.0)
        assertEquals("", egg.id)
    }

    @Test
    fun constructedValuesArePreserved() {
        val egg = WorldEgg(
            id = "e1",
            name = "Cristallo di Quarzo",
            displayLabel = "Cristallo di Quarzo",
            currentPower = 42,
            rarity = EggRarity.RARE,
            lat = 45.0,
            lng = 9.0,
            found = true
        )
        assertEquals("e1", egg.id)
        assertEquals(42, egg.currentPower)
        assertSame(EggRarity.RARE, egg.rarity)
        assertEquals(45.0, egg.lat, 0.0)
        assertEquals(9.0, egg.lng, 0.0)
        assertEquals(true, egg.found)
    }
}
