package ru.cultureguide.navigation

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Средняя скорость пешехода, м/мин (≈4,5 км/ч). */
const val WALK_SPEED_M_PER_MIN = 75.0

private const val EARTH_RADIUS_M = 6_371_008.8

/** Расстояние по большому кругу (haversine), в метрах. */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Длина ломаной, заданной парами (lat, lon), в метрах. */
fun polylineLengthMeters(points: List<Pair<Double, Double>>): Double {
    var total = 0.0
    for (i in 1 until points.size) {
        val (aLat, aLon) = points[i - 1]
        val (bLat, bLon) = points[i]
        total += distanceMeters(aLat, aLon, bLat, bLon)
    }
    return total
}

fun formatDistance(meters: Double): String = when {
    meters < 10.0 -> "${meters.roundToInt()} м"
    meters < 995.0 -> "${(meters / 10.0).roundToInt() * 10} м"
    meters < 10_000.0 -> String.format(Locale.US, "%.1f км", meters / 1000.0).replace('.', ',')
    else -> "${(meters / 1000.0).roundToInt()} км"
}

fun walkMinutes(meters: Double): Int = max(1, (meters / WALK_SPEED_M_PER_MIN).roundToInt())

fun formatWalkTime(meters: Double): String {
    val minutes = walkMinutes(meters)
    return if (minutes < 60) "$minutes мин" else "${minutes / 60} ч ${minutes % 60} мин"
}
