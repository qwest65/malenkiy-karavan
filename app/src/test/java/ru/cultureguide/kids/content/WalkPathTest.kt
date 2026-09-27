package ru.cultureguide.kids.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.LocationFix

class WalkPathTest {
    // Г-образная линия: ~111 м на север, затем ~131 м на восток.
    private val a = GeoPoint(54.000, 61.000)
    private val b = GeoPoint(54.001, 61.000)
    private val c = GeoPoint(54.001, 61.002)
    private val path = WalkPath(listOf(a, b, c))

    @Test
    fun lengthIsSumOfSegments() {
        assertEquals(242.0, path.lengthMeters, 1.0)
    }

    @Test
    fun atStartWholePathRemains() {
        val progress = path.progress(LocationFix(a.lat, a.lon))
        assertEquals(0.0, progress.offPathMeters, 0.5)
        assertEquals(path.lengthMeters, progress.remainingMeters, 0.5)
    }

    @Test
    fun halfwayAlongFirstSegment() {
        val progress = path.progress(LocationFix(54.0005, 61.000))
        assertEquals(0.0, progress.offPathMeters, 0.5)
        assertEquals(55.7 + 130.9, progress.remainingMeters, 1.0)
    }

    @Test
    fun besideThePathAddsTheDetour() {
        // ~20 м к западу от середины первого отрезка.
        val fix = LocationFix(54.0005, 61.000 - 20 / (111_320.0 * Math.cos(Math.toRadians(54.0005))))
        assertEquals(20.0, path.progress(fix).offPathMeters, 0.5)
        assertEquals(20.0 + 55.7 + 130.9, walkingMeters(path, fix, straightMeters = 999.0), 1.0)
    }

    @Test
    fun farFromThePathFallsBackToStraightLine() {
        val fix = LocationFix(54.003, 60.995)
        assertEquals(321.0, walkingMeters(path, fix, straightMeters = 321.0), 0.0)
    }

    @Test
    fun planLengthSumsLegsBetweenChosenStops() {
        val paths = RoutePaths(mapOf((0 to 2) to path, (2 to 4) to WalkPath(listOf(c, a))))
        assertEquals(path.lengthMeters + WalkPath(listOf(c, a)).lengthMeters, paths.planMeters(listOf(0, 2, 4))!!, 0.01)
        assertEquals(0.0, paths.planMeters(listOf(3))!!, 0.0)
        assertNull(paths.planMeters(listOf(0, 1)))
    }

    @Test
    fun reverseDirectionUsesTheSameLineBackwards() {
        val paths = RoutePaths(mapOf((0 to 2) to path))
        val back = paths.between(2, 0)!!
        assertEquals(listOf(c, b, a), back.points)
        assertEquals(path.lengthMeters, back.lengthMeters, 0.01)
        assertEquals(path.lengthMeters, paths.planMeters(listOf(2, 0))!!, 0.01)
    }

    @Test
    fun rerouteOnlyWhenNoPathOrOffItTwiceAndNotTooOften() {
        val every = Reroute.OFFLINE_INTERVAL_MS
        assertTrue(Reroute.needed(hasPath = false, offFixes = 0, sinceLastMs = Long.MAX_VALUE, intervalMs = every))
        assertFalse(Reroute.needed(hasPath = false, offFixes = 0, sinceLastMs = 1_000, intervalMs = every))
        assertFalse(Reroute.needed(hasPath = true, offFixes = 0, sinceLastMs = Long.MAX_VALUE, intervalMs = every))
        // Один скачок GPS в сторону — ещё не повод перестраивать.
        assertFalse(Reroute.needed(hasPath = true, offFixes = 1, sinceLastMs = Long.MAX_VALUE, intervalMs = every))
        assertTrue(Reroute.needed(hasPath = true, offFixes = 2, sinceLastMs = Long.MAX_VALUE, intervalMs = every))
    }

    @Test
    fun pointAlongThePath() {
        val p = path.pointAt(111.3 + 65.45)
        assertEquals(b.lat, p.lat, 1e-6)
        assertEquals((b.lon + c.lon) / 2, p.lon, 1e-4)
        assertEquals(a, path.pointAt(-5.0))
        assertEquals(c.lat, path.pointAt(1e6).lat, 1e-9)
    }

    @Test
    fun withoutPathUsesStraightLine() {
        assertEquals(77.0, walkingMeters(null, LocationFix(a.lat, a.lon), straightMeters = 77.0), 0.0)
    }
}
