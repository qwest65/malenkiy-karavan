package ru.cultureguide.kids.content

import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.distanceMeters
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Куда повернуть на развилке. */
enum class Turn { SLIGHT_LEFT, LEFT, SLIGHT_RIGHT, RIGHT, U_TURN }

/** Поворот на пешеходной линии: [atMeters] от её начала, в точке [point]. */
data class Maneuver(val atMeters: Double, val turn: Turn, val point: GeoPoint)

/**
 * Что показать и сказать сейчас: ближайший поворот через [inMeters]
 * или, если поворотов больше нет ([turn] == null), сколько идти прямо до точки.
 */
data class Instruction(val turn: Turn?, val inMeters: Double, val maneuver: Maneuver?)

/** Изломы линии меньше этого угла — это «прямо». */
private const val MIN_TURN_DEG = 30.0
private const val SHARP_DEG = 60.0
private const val U_TURN_DEG = 160.0

/** Направление до и после излома берём по отрезку такой длины — так мелкие зигзаги OSRM не мешают. */
private const val WINDOW_M = 12.0

/** Изломы ближе этого друг к другу — один поворот. */
private const val MERGE_M = 15.0

/** Первые метры линии пропускаем: от точки или от нас на тротуар выходим как удобно. */
private const val SKIP_START_M = 10.0

/** Поворот, мимо которого прошли на столько метров, уже позади. */
private const val PASSED_M = 5.0

/** Направление от [a] на [b] в градусах по часовой стрелке от севера, 0..360. */
fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
    val p1 = Math.toRadians(a.lat)
    val p2 = Math.toRadians(b.lat)
    val dl = Math.toRadians(b.lon - a.lon)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

/** Разница направлений от [from] к [to] в диапазоне -180..180; плюс — по часовой, то есть вправо. */
fun angleDelta(from: Double, to: Double): Double {
    val d = ((to - from) % 360 + 360) % 360
    return if (d > 180) d - 360 else d
}

internal fun findManeuvers(points: List<GeoPoint>): List<Maneuver> {
    if (points.size < 3) return emptyList()
    val at = DoubleArray(points.size)
    for (i in 1 until points.size) at[i] = at[i - 1] + dist(points[i - 1], points[i])
    val length = at.last()

    fun pointAt(meters: Double): GeoPoint {
        val m = meters.coerceIn(0.0, length)
        var i = 1
        while (i < points.lastIndex && at[i] < m) i++
        val span = at[i] - at[i - 1]
        val t = if (span == 0.0) 0.0 else (m - at[i - 1]) / span
        val a = points[i - 1]
        val b = points[i]
        return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    class Bend(val at: Double, val delta: Double, val point: GeoPoint)

    val bends = (1 until points.lastIndex).mapNotNull { i ->
        val d = at[i]
        if (d < SKIP_START_M || length - d < PASSED_M) return@mapNotNull null
        val before = pointAt(d - WINDOW_M)
        val after = pointAt(d + WINDOW_M)
        if (dist(before, points[i]) < 1 || dist(points[i], after) < 1) return@mapNotNull null
        val delta = angleDelta(bearingDegrees(before, points[i]), bearingDegrees(points[i], after))
        Bend(d, delta, points[i]).takeIf { abs(delta) >= MIN_TURN_DEG }
    }
    // Соседние изломы одного поворота сливаем: оставляем самый резкий.
    val merged = mutableListOf<Bend>()
    for (bend in bends) {
        val last = merged.lastOrNull()
        if (last != null && bend.at - last.at < MERGE_M) {
            if (abs(bend.delta) > abs(last.delta)) merged[merged.lastIndex] = bend
        } else {
            merged += bend
        }
    }
    return merged.map { Maneuver(it.at, turnOf(it.delta), it.point) }
}

private fun turnOf(delta: Double): Turn = when {
    abs(delta) >= U_TURN_DEG -> Turn.U_TURN
    delta <= -SHARP_DEG -> Turn.LEFT
    delta < 0 -> Turn.SLIGHT_LEFT
    delta >= SHARP_DEG -> Turn.RIGHT
    else -> Turn.SLIGHT_RIGHT
}

/** Подсказка для того, кто прошёл [alongMeters] по линии [path]. */
fun instruction(path: WalkPath, alongMeters: Double): Instruction {
    val next = path.maneuvers.firstOrNull { it.atMeters > alongMeters - PASSED_M }
        ?: return Instruction(null, (path.lengthMeters - alongMeters).coerceAtLeast(0.0), null)
    return Instruction(next.turn, (next.atMeters - alongMeters).coerceAtLeast(0.0), next)
}

private fun dist(a: GeoPoint, b: GeoPoint) = distanceMeters(a.lat, a.lon, b.lat, b.lon)

/**
 * Куда смотрит телефон. Пока идём быстрее [MIN_GPS_SPEED] м/с, верим направлению движения
 * по GPS; стоим или идём медленно — компасу. Компас сглаживается, чтобы стрелка не дрожала.
 */
class HeadingFusion {
    private var gpsHeading: Double? = null
    private var gpsAtMs = Long.MIN_VALUE / 2
    private var compass: Double? = null

    var heading: Double? = null
        private set

    fun onGps(bearing: Float?, speedMps: Float?, nowMs: Long): Double? {
        if (bearing != null && speedMps != null && speedMps >= MIN_GPS_SPEED) {
            gpsHeading = bearing.toDouble()
            gpsAtMs = nowMs
            heading = gpsHeading
        }
        return heading
    }

    fun onCompass(degrees: Double, nowMs: Long): Double? {
        val smoothed = compass?.let { (it + angleDelta(it, degrees) * SMOOTHING + 360) % 360 } ?: ((degrees % 360 + 360) % 360)
        compass = smoothed
        if (nowMs - gpsAtMs > GPS_TRUST_MS) heading = smoothed
        return heading
    }

    companion object {
        const val MIN_GPS_SPEED = 1.0f
        const val GPS_TRUST_MS = 4_000L
        const val SMOOTHING = 0.2
    }
}
