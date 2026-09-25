package ru.cultureguide.kids.content

import kotlin.math.roundToInt

/**
 * Прогулка и альбом. Родитель выбирает точки ([plan], всегда в порядке маршрута),
 * их можно пройти, пропустить или прервать прогулку. Найденные вещи ([found])
 * остаются в альбоме между прогулками.
 */
data class Journey(
    val stopCount: Int,
    /** Выбранные точки текущей прогулки; пусто — прогулки нет. */
    val plan: List<Int> = emptyList(),
    /** Номер следующей цели внутри [plan]. */
    val position: Int = 0,
    /** Все вещи, найденные за все прогулки. */
    val found: Set<Int> = emptySet(),
    /** Вещи, найденные на текущей прогулке. */
    val walkFound: Set<Int> = emptySet(),
    /** Значок «Юный караванщик» — за прогулку, пройденную до конца хотя бы с одной находкой. */
    val badge: Boolean = false
) {
    init {
        require(plan.all { it in 0 until stopCount }) { "plan=$plan вне 0 until $stopCount" }
        require(position in 0..plan.size) { "position=$position вне 0..${plan.size}" }
    }

    val inProgress: Boolean get() = plan.isNotEmpty()

    /** Все выбранные точки пройдены или пропущены. */
    val walkComplete: Boolean get() = inProgress && position == plan.size

    /** Точка маршрута, к которой идём; null — идти некуда. */
    val activeStop: Int? get() = plan.getOrNull(position)

    /** Точка, от которой идём к [activeStop]; null — первая цель прогулки. */
    val previousStop: Int? get() = plan.getOrNull(position - 1)

    fun isFound(index: Int): Boolean = index in found

    /** Новая прогулка по выбранным точкам (порядок — как в маршруте). */
    fun start(selection: Collection<Int>): Journey {
        val chosen = selection.filter { it in 0 until stopCount }.distinct().sorted()
        require(chosen.isNotEmpty()) { "Нужно выбрать хотя бы одну точку" }
        return copy(plan = chosen, position = 0, walkFound = emptySet())
    }

    /** Вещь на текущей точке найдена; другие точки не засчитываются. */
    fun collect(index: Int): Journey {
        if (index != activeStop) return this
        val done = copy(found = found + index, walkFound = walkFound + index, position = position + 1)
        return if (done.walkComplete) done.copy(badge = true) else done
    }

    /** Пропустить текущую точку и идти к следующей. Значок дают, если что-то найдено. */
    fun skip(): Journey {
        if (activeStop == null) return this
        val next = copy(position = position + 1)
        return if (next.walkComplete && next.walkFound.isNotEmpty()) next.copy(badge = true) else next
    }

    /** Закончить прогулку; найденное остаётся в альбоме. */
    fun finish(): Journey = copy(plan = emptyList(), position = 0, walkFound = emptySet())

    /** Очистить альбом и значок. */
    fun reset(): Journey = Journey(stopCount)
}

/** Средний шаг ребёнка 4–7 лет, м. */
private const val KID_STEP_M = 0.45

/** Расстояние в детских шагах, округлённое так, чтобы число легко было прочитать вслух. */
fun kidSteps(meters: Double): Int {
    val steps = meters / KID_STEP_M
    return when {
        steps < 20 -> steps.roundToInt()
        steps < 200 -> (steps / 10).roundToInt() * 10
        else -> (steps / 50).roundToInt() * 50
    }
}

/** «шаг», «шага», «шагов» — по правилам русского языка. */
fun stepsWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "шагов"
        mod10 == 1 -> "шаг"
        mod10 in 2..4 -> "шага"
        else -> "шагов"
    }
}
