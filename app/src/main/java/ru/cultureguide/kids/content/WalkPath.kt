package ru.cultureguide.kids.content

import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.LocationFix
import ru.cultureguide.navigation.distanceMeters
import kotlin.math.cos
import kotlin.math.hypot

/** Дальше этого расстояния от пешеходной линии считаем, что идём своей дорогой. */
const val ON_PATH_METERS = 40.0

private const val METERS_PER_DEGREE = 111_320.0

/** Где мы относительно линии: насколько от неё отошли и сколько осталось пройти вдоль неё. */
data class PathProgress(val offPathMeters: Double, val remainingMeters: Double)

/** Пешеходная линия от одной точки маршрута до следующей. */
class WalkPath(val points: List<GeoPoint>) {
    init {
        require(points.size >= 2) { "У линии должно быть хотя бы две точки" }
    }

    private val segmentMeters = List(points.size - 1) { i -> dist(points[i], points[i + 1]) }

    val lengthMeters: Double = segmentMeters.sum()

    fun progress(fix: LocationFix): PathProgress {
        var bestOff = Double.MAX_VALUE
        var bestRemaining = lengthMeters
        var after = lengthMeters
        for (i in segmentMeters.indices) {
            after -= segmentMeters[i]
            val a = points[i]
            val b = points[i + 1]
            // Локальная плоская проекция: на отрезках в сотни метров погрешность — сантиметры.
            val kx = METERS_PER_DEGREE * cos(Math.toRadians(a.lat))
            val bx = (b.lon - a.lon) * kx
            val by = (b.lat - a.lat) * METERS_PER_DEGREE
            val px = (fix.lon - a.lon) * kx
            val py = (fix.lat - a.lat) * METERS_PER_DEGREE
            val len2 = bx * bx + by * by
            val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
            val off = hypot(px - t * bx, py - t * by)
            if (off < bestOff) {
                bestOff = off
                bestRemaining = (1 - t) * segmentMeters[i] + after
            }
        }
        return PathProgress(bestOff, bestRemaining)
    }

    private fun dist(a: GeoPoint, b: GeoPoint) = distanceMeters(a.lat, a.lon, b.lat, b.lon)
}

/**
 * Сколько идти до цели. Пока мы на пешеходной линии — вдоль неё; если свернули
 * или линии нет (путь к первой точке начинается где угодно) — по прямой.
 */
fun walkingMeters(path: WalkPath?, fix: LocationFix, straightMeters: Double): Double {
    val progress = path?.progress(fix) ?: return straightMeters
    return if (progress.offPathMeters <= ON_PATH_METERS) progress.remainingMeters + progress.offPathMeters else straightMeters
}

/** Пешеходные линии между точками маршрута: `from` < `to`, индексы — номера точек. */
class RoutePaths(private val legs: Map<Pair<Int, Int>, WalkPath>) {
    fun between(from: Int, to: Int): WalkPath? = legs[from to to]

    /** Длина прогулки по выбранным точкам; null, если хоть одной линии нет. */
    fun planMeters(plan: List<Int>): Double? =
        plan.zipWithNext { a, b -> between(a, b)?.lengthMeters ?: return null }.sum()

    companion object {
        val EMPTY = RoutePaths(emptyMap())
    }
}

/** Когда просить новый маршрут «от меня до точки» — не чаще раза в [MIN_INTERVAL_MS]. */
object Reroute {
    const val MIN_INTERVAL_MS = 20_000L

    /**
     * @param current уже построенный путь к этой же цели; null — пути нет или он к другой точке.
     * @param sinceLastMs сколько прошло с прошлого запроса.
     */
    fun needed(current: WalkPath?, fix: LocationFix, sinceLastMs: Long): Boolean {
        if (sinceLastMs < MIN_INTERVAL_MS) return false
        return current == null || current.progress(fix).offPathMeters > ON_PATH_METERS
    }
}
