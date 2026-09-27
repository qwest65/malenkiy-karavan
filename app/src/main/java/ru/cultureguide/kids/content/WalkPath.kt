package ru.cultureguide.kids.content

import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.LocationFix
import ru.cultureguide.navigation.distanceMeters
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Ближе этого к линии стрелка «притягивается» к ней; дальше — считаем, что свернули,
 * и путь строится заново от того места, где мы стоим.
 */
const val ON_PATH_METERS = 25.0

private const val METERS_PER_DEGREE = 111_320.0

/**
 * Где мы относительно линии: насколько от неё отошли, сколько осталось пройти вдоль неё
 * и сколько уже пройдено от её начала.
 */
data class PathProgress(val offPathMeters: Double, val remainingMeters: Double, val alongMeters: Double = 0.0)

/** Пешеходная линия от одной точки маршрута до следующей. */
class WalkPath(val points: List<GeoPoint>) {
    init {
        require(points.size >= 2) { "У линии должно быть хотя бы две точки" }
    }

    private val segmentMeters = List(points.size - 1) { i -> dist(points[i], points[i + 1]) }

    val lengthMeters: Double = segmentMeters.sum()

    /** Повороты вдоль линии — для подсказок «через 30 м налево». */
    val maneuvers: List<Maneuver> by lazy { findManeuvers(points) }

    /** Точка линии в [alongMeters] от её начала — сюда «притягивается» стрелка на карте. */
    fun pointAt(alongMeters: Double): GeoPoint {
        var left = alongMeters.coerceIn(0.0, lengthMeters)
        for (i in segmentMeters.indices) {
            val seg = segmentMeters[i]
            if (left <= seg || i == segmentMeters.lastIndex) {
                val t = if (seg == 0.0) 0.0 else (left / seg).coerceIn(0.0, 1.0)
                val a = points[i]
                val b = points[i + 1]
                return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            }
            left -= seg
        }
        return points.last()
    }

    /** Часть линии впереди: от точки в [alongMeters] от начала до конца. */
    fun remainingFrom(alongMeters: Double): List<GeoPoint> {
        val start = pointAt(alongMeters)
        var passed = 0.0
        val ahead = ArrayList<GeoPoint>()
        ahead += start
        for (i in segmentMeters.indices) {
            passed += segmentMeters[i]
            if (passed > alongMeters) ahead += points[i + 1]
        }
        if (ahead.size < 2) ahead += points.last()
        return ahead
    }

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
        return PathProgress(bestOff, bestRemaining, lengthMeters - bestRemaining)
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

/**
 * Пешеходные линии между точками маршрута; индексы — номера точек. Линия хранится
 * в одну сторону, обратный путь — та же линия задом наперёд.
 */
class RoutePaths(legs: Map<Pair<Int, Int>, WalkPath>) {
    private val both: Map<Pair<Int, Int>, WalkPath> =
        legs + legs.map { (key, path) -> (key.second to key.first) to WalkPath(path.points.reversed()) }

    fun between(from: Int, to: Int): WalkPath? = both[from to to]

    /** Длина прогулки по выбранным точкам; null, если хоть одной линии нет. */
    fun planMeters(plan: List<Int>): Double? =
        plan.zipWithNext { a, b -> between(a, b)?.lengthMeters ?: return null }.sum()

    companion object {
        val EMPTY = RoutePaths(emptyMap())
    }
}

/**
 * Когда строить путь «от меня до точки» заново. Своего пути нет — строим сразу; есть —
 * только если мы с него свернули ([ON_PATH_METERS]) [CONFIRM_FIXES] замера подряд: один
 * скачок GPS не в счёт. И не чаще [intervalMs]: по встроенной карте улиц — раз в
 * [OFFLINE_INTERVAL_MS], у OSRM через интернет — раз в [ONLINE_INTERVAL_MS].
 */
object Reroute {
    const val CONFIRM_FIXES = 2
    const val OFFLINE_INTERVAL_MS = 3_000L
    const val ONLINE_INTERVAL_MS = 20_000L

    fun needed(hasPath: Boolean, offFixes: Int, sinceLastMs: Long, intervalMs: Long): Boolean =
        sinceLastMs >= intervalMs && (!hasPath || offFixes >= CONFIRM_FIXES)
}
