package ru.cultureguide.kids.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyTest {
    @Test
    fun chosenStopsAreVisitedInRouteOrder() {
        var journey = Journey(stopCount = 5).start(listOf(4, 0, 2))
        assertEquals(listOf(0, 2, 4), journey.plan)
        assertEquals(0, journey.activeStop)
        assertNull(journey.previousStop)

        journey = journey.collect(0)
        assertEquals(2, journey.activeStop)
        assertEquals(0, journey.previousStop)

        journey = journey.collect(2).collect(4)
        assertTrue(journey.walkComplete)
        assertTrue(journey.badge)
        assertEquals(setOf(0, 2, 4), journey.found)
    }

    @Test
    fun onlyTheCurrentStopCanBeCollected() {
        val journey = Journey(stopCount = 5).start(listOf(1, 3))
        assertEquals(journey, journey.collect(3))
        assertEquals(journey, journey.collect(0))
    }

    @Test
    fun skippedStopIsNotFoundButWalkStillCompletes() {
        val journey = Journey(stopCount = 3).start(listOf(0, 1)).skip().collect(1)
        assertTrue(journey.walkComplete)
        assertFalse(journey.isFound(0))
        assertTrue(journey.isFound(1))
    }

    @Test
    fun finishingEarlyKeepsAlbumButGivesNoBadge() {
        val journey = Journey(stopCount = 5).start(listOf(0, 1, 2)).collect(0).finish()
        assertFalse(journey.inProgress)
        assertFalse(journey.badge)
        assertEquals(setOf(0), journey.found)
    }

    @Test
    fun albumGrowsAcrossWalks() {
        val first = Journey(stopCount = 5).start(listOf(0, 1)).collect(0).collect(1).finish()
        val second = first.start(listOf(3)).collect(3)
        assertEquals(setOf(0, 1, 3), second.found)
        assertEquals(setOf(3), second.walkFound)
    }

    @Test
    fun resetClearsAlbumAndBadge() {
        val journey = Journey(stopCount = 2).start(listOf(0, 1)).collect(0).collect(1).reset()
        assertEquals(Journey(stopCount = 2), journey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun walkNeedsAtLeastOneStop() {
        Journey(stopCount = 5).start(emptyList())
    }

    @Test
    fun kidStepsRoundToReadableNumbers() {
        assertEquals(11, kidSteps(5.0))
        assertEquals(110, kidSteps(50.0))
        assertEquals(800, kidSteps(360.0))
    }

    @Test
    fun stepsWordAgreesWithNumber() {
        assertEquals("шаг", stepsWord(1))
        assertEquals("шага", stepsWord(3))
        assertEquals("шагов", stepsWord(12))
        assertEquals("шагов", stepsWord(100))
        assertEquals("шаг", stepsWord(21))
    }
}
