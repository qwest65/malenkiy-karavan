package ru.cultureguide.kids.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.cultureguide.navigation.GeoPoint
import kotlin.math.cos

class StreetGraphTest {
    private val lat0 = 54.08
    private val lon0 = 61.56

    /** Точка в [north] м к северу и [east] м к востоку от начала. */
    private fun p(north: Double, east: Double) =
        GeoPoint(lat0 + north / 111_320.0, lon0 + east / (111_320.0 * cos(Math.toRadians(lat0))))

    // Квартал 200 × 200 м: улицы по периметру (узлы 0–3) и дорожка сквера посередине (4–5), «дороже» улиц.
    private val corners = listOf(p(0.0, 0.0), p(0.0, 200.0), p(200.0, 200.0), p(200.0, 0.0), p(100.0, 0.0), p(100.0, 200.0))
    private val graph = StreetGraph(
        DoubleArray(corners.size) { corners[it].lat },
        DoubleArray(corners.size) { corners[it].lon },
        intArrayOf(0, 1, 2, 3, 4),
        intArrayOf(1, 2, 3, 0, 5),
        doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.1)
    )

    private fun length(points: List<GeoPoint>) = WalkPath(points).lengthMeters

    @Test
    fun routeGoesAlongStreetsAroundTheBlock() {
        // От южной улицы к северной: через угол квартала, а не напрямик через дома.
        val route = graph.route(p(-5.0, 20.0), p(205.0, 20.0))!!
        assertEquals(p(-5.0, 20.0), route.first())
        assertEquals(p(205.0, 20.0), route.last())
        assertEquals(5.0 + 20.0 + 200.0 + 20.0 + 5.0, length(route), 2.0)
    }

    @Test
    fun routeUsesParkPathWhenMuchShorter() {
        // С западной улицы на восточную через сквер: 200 м вместо 400 м по периметру.
        val route = graph.route(p(100.0, -3.0), p(100.0, 203.0))!!
        assertEquals(206.0, length(route), 2.0)
    }

    @Test
    fun routeOnOneStreetSegment() {
        val route = graph.route(p(-2.0, 30.0), p(-2.0, 150.0))!!
        assertEquals(2.0 + 120.0 + 2.0, length(route), 1.0)
    }

    @Test
    fun farFromStreetsGivesNoRoute() {
        assertNull(graph.route(p(-1000.0, 0.0), p(0.0, 100.0)))
        assertNotNull(graph.route(p(-100.0, 0.0), p(0.0, 100.0)))
    }

    @Test
    fun snapFindsNearestStreet() {
        val snap = graph.snap(p(50.0, 190.0))!!
        assertEquals(10.0, snap.distanceMeters, 0.5)
        assertEquals(p(50.0, 200.0).lon, snap.point.lon, 1e-6)
    }

    @Test
    fun flatArraysFromStreetsJson() {
        val g = StreetGraph.fromFlat(
            doubleArrayOf(corners[0].lat, corners[0].lon, corners[1].lat, corners[1].lon),
            intArrayOf(0, 1, 100)
        )
        assertTrue(!g.isEmpty)
        assertEquals(200.0, length(g.route(corners[0], corners[1])!!), 1.0)
        assertTrue(StreetGraph.EMPTY.isEmpty)
        assertNull(StreetGraph.EMPTY.route(corners[0], corners[1]))
    }
}
