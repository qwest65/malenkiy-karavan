package ru.cultureguide.kids.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizTest {
    private val quiz = Quiz("Из чего построен собор?", listOf("Из камня", "Из дерева", "Изо льда"), answer = 0)

    @Test
    fun onlyTheMarkedOptionIsRight() {
        assertTrue(quiz.isRight(0))
        assertFalse(quiz.isRight(1))
        assertFalse(quiz.isRight(2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun answerMustBeOneOfTheOptions() {
        Quiz("?", listOf("а", "б"), answer = 2)
    }
}
