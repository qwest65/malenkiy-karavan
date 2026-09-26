package ru.cultureguide.kids.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.cultureguide.navigation.GeoPoint
import kotlin.math.cos

class NavigationTest {
    private val lat0 = 54.0
    private val lon0 = 61.0

    /** Точка в [north] м к северу и [east] м к востоку от начала. */
    private fun p(north: Double, east: Double) =
        GeoPoint(lat0 + north / 111_320.0, lon0 + east / (111_320.0 * cos(Math.toRadians(lat0))))

    @Test
    fun rightTurnAtCorner() {
        val path = WalkPath(listOf(p(0.0, 0.0), p(100.0, 0.0), p(100.0, 100.0)))
        val m = path.maneuvers.single()
        assertEquals(Turn.RIGHT, m.turn)
        assertEquals(100.0, m.atMeters, 1.0)
    }

    @Test
    fun leftTurnAtCorner() {
        val path = WalkPath(listOf(p(0.0, 0.0), p(100.0, 0.0), p(100.0, -100.0)))
        assertEquals(Turn.LEFT, path.maneuvers.single().turn)
    }

    @Test
    fun slightTurn() {
        val path = WalkPath(listOf(p(0.0, 0.0), p(100.0, 0.0), p(170.0, 70.0)))
        assertEquals(Turn.SLIGHT_RIGHT, path.maneuvers.single().turn)
    }

    @Test
    fun uTurn() {
        val path = WalkPath(listOf(p(0.0, 0.0), p(100.0, 0.0), p(100.0, 3.0), p(0.0, 3.0)))
        assertEquals(Turn.U_TURN, path.maneuvers.single().turn)
    }

    @Test
    fun straightLineWithManyVerticesHasNoTurns() {
        val path = WalkPath((0..20).map { p(it * 10.0, if (it % 2 == 0) 0.0 else 0.8) })
        assertTrue(path.maneuvers.isEmpty())
    }

    @Test
    fun cornerSplitIntoSeveralVerticesIsOneTurn() {
        val path = WalkPath(listOf(p(0.0, 0.0), p(100.0, 0.0), p(103.0, 1.5), p(104.0, 4.0), p(104.0, 100.0)))
        val m = path.maneuvers.single()
        assertEquals(Turn.RIGHT, m.turn)
    }

    @Test
    fun turnsRightAtTheStartAreSkipped() {
        val path = WalkPath(listOf(p(0.0, 0.0), p(4.0, 0.0), p(4.0, 100.0)))
        assertTrue(path.maneuvers.isEmpty())
    }

    @Test
    fun instructionCountsDownToNextTurnThenToTheEnd() {
        val path = WalkPath(listOf(p(0.0, 0.0), p(100.0, 0.0), p(100.0, 100.0), p(200.0, 100.0)))
        assertEquals(listOf(Turn.RIGHT, Turn.LEFT), path.maneuvers.map { it.turn })
        val first = instruction(path, 30.0)
        assertEquals(Turn.RIGHT, first.turn)
        assertEquals(70.0, first.inMeters, 1.5)
        val second = instruction(path, 120.0)
        assertEquals(Turn.LEFT, second.turn)
        assertEquals(80.0, second.inMeters, 1.5)
        val last = instruction(path, 250.0)
        assertNull(last.turn)
        assertEquals(50.0, last.inMeters, 1.5)
    }

    @Test
    fun bearingAndDelta() {
        assertEquals(0.0, bearingDegrees(p(0.0, 0.0), p(100.0, 0.0)), 0.5)
        assertEquals(90.0, bearingDegrees(p(0.0, 0.0), p(0.0, 100.0)), 0.5)
        assertEquals(-20.0, angleDelta(10.0, 350.0), 1e-9)
        assertEquals(20.0, angleDelta(350.0, 10.0), 1e-9)
    }

    @Test
    fun headingPrefersGpsWhileWalking() {
        val f = HeadingFusion()
        assertEquals(90.0, f.onCompass(90.0, 0)!!, 1e-9)
        assertEquals(180.0, f.onGps(180f, 1.5f, 1_000)!!, 1e-9)
        // Сразу после GPS компас не перебивает направление движения.
        assertEquals(180.0, f.onCompass(90.0, 2_000)!!, 1e-9)
        // Остановились — снова компас.
        f.onGps(null, 0f, 3_000)
        assertEquals(90.0, f.onCompass(90.0, 10_000)!!, 1e-9)
    }

    @Test
    fun compassSmoothingWrapsAroundNorth() {
        val f = HeadingFusion()
        f.onCompass(350.0, 0)
        val h = f.onCompass(10.0, 100)!!
        assertEquals(354.0, h, 1e-6)
    }
}
