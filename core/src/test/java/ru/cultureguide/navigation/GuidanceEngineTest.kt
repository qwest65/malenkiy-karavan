package ru.cultureguide.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceEngineTest {
    // Точки маршрута «Исторический центр» (Троицк).
    private val stone = GeoPoint(54.077485, 61.556362)
    private val cathedral = GeoPoint(54.077845, 61.557944)
    private val square = GeoPoint(54.0826, 61.559233)
    private val stops = listOf(stone, cathedral, square)
    private val engine = GuidanceEngine()

    @Test
    fun farFromTargetKeepsTargetAndReportsDistance() {
        val update = engine.update(stops, 0, LocationFix(54.0700, 61.5500, 10f))
        assertEquals(0, update.activeIndex)
        assertTrue(update.reached.isEmpty())
        assertTrue(update.distanceToTarget!! > 800)
        assertFalse(update.finished)
    }

    @Test
    fun arrivingWithinRadiusSwitchesToNextStop() {
        // ~30 м к северу от памятного камня.
        val update = engine.update(stops, 0, LocationFix(stone.lat + 0.00027, stone.lon, 8f))
        assertEquals(listOf(0), update.reached)
        assertEquals(1, update.activeIndex)
        assertEquals(distanceMeters(stone.lat + 0.00027, stone.lon, cathedral.lat, cathedral.lon), update.distanceToTarget!!, 0.01)
    }

    @Test
    fun justOutsideRadiusDoesNotSwitch() {
        // ~60 м от камня.
        val update = engine.update(stops, 0, LocationFix(stone.lat + 0.00054, stone.lon, 8f))
        assertEquals(0, update.activeIndex)
    }

    @Test
    fun inaccurateFixDoesNotSwitch() {
        val update = engine.update(stops, 0, LocationFix(stone.lat, stone.lon, 250f))
        assertEquals(0, update.activeIndex)
        assertEquals(0.0, update.distanceToTarget!!, 0.5)
    }

    @Test
    fun shortcutToLaterStopMarksSkippedStopsReached() {
        val update = engine.update(stops, 0, LocationFix(cathedral.lat, cathedral.lon, 5f))
        assertEquals(listOf(0, 1), update.reached)
        assertEquals(2, update.activeIndex)
    }

    @Test
    fun reachingLastStopFinishesRoute() {
        val update = engine.update(stops, 2, LocationFix(square.lat, square.lon, 5f))
        assertTrue(update.finished)
        assertEquals(3, update.activeIndex)
        assertNull(update.distanceToTarget)
    }

    @Test
    fun remainingSumsTargetDistanceAndFollowingLegs() {
        val legs = listOf(120.0, 300.0, 450.0)
        assertEquals(50.0 + 300.0 + 450.0, GuidanceEngine.remainingMeters(50.0, 1, legs), 1e-9)
        assertEquals(50.0, GuidanceEngine.remainingMeters(50.0, 3, legs), 1e-9)
    }

    @Test
    fun formatsDistances() {
        assertEquals("7 м", formatDistance(7.2))
        assertEquals("230 м", formatDistance(231.0))
        assertEquals("1,2 км", formatDistance(1234.0))
        assertEquals("3 мин", formatWalkTime(225.0))
    }
}
