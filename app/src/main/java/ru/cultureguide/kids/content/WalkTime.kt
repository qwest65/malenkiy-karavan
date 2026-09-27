package ru.cultureguide.kids.content

import kotlin.math.roundToInt

/** Шаг взрослого с ребёнком 4–7 лет, м/мин (≈3 км/ч). */
const val KID_WALK_M_PER_MIN = 50.0

/** Сколько минут занимает одна точка: колокольчик, рассказ, вопрос, игра и фото. */
const val MINUTES_PER_STOP = 7

/** Сколько займёт прогулка: [meters] пешком с ребёнком и [stops] точек, в минутах. */
fun walkEstimateMinutes(meters: Double, stops: Int): Int =
    (meters / KID_WALK_M_PER_MIN).roundToInt() + stops * MINUTES_PER_STOP

/** «около 35 мин», «около часа», «около 1,5 часа», «около 2 часов». */
fun formatEstimate(minutes: Int): String = when {
    minutes < 55 -> "около ${((minutes + 4) / 5 * 5).coerceAtLeast(5)} мин"
    minutes < 75 -> "около часа"
    minutes < 105 -> "около 1,5 часа"
    minutes < 135 -> "около 2 часов"
    else -> "около ${(minutes + 15) / 30 / 2.0}".replace(".0", "").replace('.', ',') + " часа"
}
