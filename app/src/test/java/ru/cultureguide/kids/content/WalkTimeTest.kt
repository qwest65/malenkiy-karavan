package ru.cultureguide.kids.content

import org.junit.Assert.assertEquals
import org.junit.Test

class WalkTimeTest {
    @Test
    fun fullRouteTakesAboutAnHourAndAHalf() {
        // 1,7 км с ребёнком ≈ 34 мин + 9 точек по 7 мин.
        val minutes = walkEstimateMinutes(1682.0, 9)
        assertEquals(97, minutes)
        assertEquals("около 1,5 часа", formatEstimate(minutes))
    }

    @Test
    fun shortRoutesInMinutes() {
        assertEquals("около 40 мин", formatEstimate(walkEstimateMinutes(755.0, 3)))
        assertEquals("около 10 мин", formatEstimate(walkEstimateMinutes(0.0, 1)))
        assertEquals("около часа", formatEstimate(60))
        assertEquals("около 2 часов", formatEstimate(120))
    }
}
