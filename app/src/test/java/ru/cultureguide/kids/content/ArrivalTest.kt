package ru.cultureguide.kids.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.cultureguide.navigation.GeoPoint
import kotlin.math.cos

class ArrivalTest {
    private val target = GeoPoint(54.08, 61.56)

    /** Точка в [north] м к северу от цели. */
    private fun p(north: Double, east: Double = 0.0) =
        GeoPoint(target.lat + north / 111_320.0, target.lon + east / (111_320.0 * cos(Math.toRadians(target.lat))))

    private fun ArrivalDetector.at(north: Double, speed: Float?, t: Long, east: Double = 0.0) =
        onFix(p(north, east), kotlin.math.abs(north), speed, t)

    @Test
    fun walkingThroughTheZoneDoesNotStartTheStory() {
        val d = ArrivalDetector(radiusMeters = 20.0)
        // Идём к точке со скоростью 1 м/с: 20 м → 0 м за 20 секунд.
        for (s in 0..20) assertFalse(d.at(20.0 - s, 1.0f, s * 1000L))
    }

    @Test
    fun standingFiveSecondsInTheZoneStartsTheStory() {
        val d = ArrivalDetector(radiusMeters = 20.0)
        assertFalse(d.at(15.0, 1.0f, 0))
        assertFalse(d.at(12.0, 0.2f, 1_000))
        assertFalse(d.at(13.0, 0.3f, 3_000))
        assertFalse(d.check(5_900))
        assertTrue(d.check(6_000))
    }

    @Test
    fun timeRunsWithoutNewFixes() {
        val d = ArrivalDetector(radiusMeters = 20.0)
        assertFalse(d.at(8.0, 0.1f, 0))
        assertFalse(d.check(4_999))
        assertTrue(d.check(5_000))
    }

    @Test
    fun fidgetingWithoutSpeedCountsAsStanding() {
        val d = ArrivalDetector(radiusMeters = 20.0)
        // Скорости нет (сетевой провайдер), позиция гуляет на несколько метров.
        assertFalse(d.at(10.0, null, 0))
        assertFalse(d.at(13.0, null, 2_000, east = 4.0))
        assertTrue(d.at(9.0, null, 5_000, east = -3.0))
    }

    @Test
    fun movingMoreThanTenMetersRestartsTheCountdown() {
        val d = ArrivalDetector(radiusMeters = 20.0)
        assertFalse(d.at(18.0, null, 0))
        assertFalse(d.at(4.0, null, 4_000))   // прошли 14 м — отсчёт заново
        assertFalse(d.check(8_000))
        assertTrue(d.check(9_000))
    }

    @Test
    fun oneGpsJumpOutsideDoesNotResetButLeavingDoes() {
        val d = ArrivalDetector(radiusMeters = 20.0)
        assertFalse(d.at(10.0, 0.1f, 0))
        assertFalse(d.at(35.0, 0.1f, 2_000))  // скачок GPS
        assertTrue(d.at(10.0, 0.1f, 5_000))
        d.reset()
        assertFalse(d.at(10.0, 0.1f, 10_000))
        assertFalse(d.at(35.0, 0.1f, 11_000))
        assertFalse(d.at(40.0, 0.1f, 12_000)) // ушли
        assertFalse(d.check(16_000))
    }
}
