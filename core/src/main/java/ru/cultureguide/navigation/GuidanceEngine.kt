package ru.cultureguide.navigation

/** Координата остановки маршрута. */
data class GeoPoint(val lat: Double, val lon: Double)

/** Одна отметка геолокации. */
data class LocationFix(val lat: Double, val lon: Double, val accuracyMeters: Float? = null)

data class GuidanceUpdate(
    /** Индекс текущей цели; равен размеру списка, если маршрут пройден. */
    val activeIndex: Int,
    /** Остановки, которые этот фикс отметил как достигнутые. */
    val reached: List<Int>,
    /** Расстояние по прямой до текущей цели; null, если маршрут пройден. */
    val distanceToTarget: Double?,
    val finished: Boolean
)

/**
 * Логика режима ведения без зависимостей от Android: по очередной геопозиции решает,
 * достигнута ли текущая цель, и переключает маршрут на следующую точку.
 */
class GuidanceEngine(
    val arrivalRadiusMeters: Double = DEFAULT_ARRIVAL_RADIUS_M,
    /** Фиксы с худшей точностью не переключают цели — только обновляют расстояние. */
    private val maxUsableAccuracyMeters: Float = 80f,
    /** Сколько следующих точек проверять, чтобы засчитать срезанный путь. */
    private val lookAhead: Int = 2
) {
    fun update(stops: List<GeoPoint>, activeIndex: Int, fix: LocationFix): GuidanceUpdate {
        if (stops.isEmpty()) return GuidanceUpdate(0, emptyList(), null, finished = false)
        var active = activeIndex.coerceIn(0, stops.size)
        if (active == stops.size) return GuidanceUpdate(active, emptyList(), null, finished = true)

        val reached = mutableListOf<Int>()
        val accuracy = fix.accuracyMeters
        if (accuracy == null || accuracy <= maxUsableAccuracyMeters) {
            var lastReached = -1
            for (i in active..minOf(active + lookAhead, stops.lastIndex)) {
                if (distanceTo(stops[i], fix) <= arrivalRadiusMeters) lastReached = i
            }
            if (lastReached >= 0) {
                for (i in active..lastReached) reached += i
                active = lastReached + 1
            }
        }

        val finished = active >= stops.size
        val distance = if (finished) null else distanceTo(stops[active], fix)
        return GuidanceUpdate(active, reached, distance, finished)
    }

    companion object {
        const val DEFAULT_ARRIVAL_RADIUS_M = 45.0

        /**
         * Оставшийся путь: до текущей цели плюс пешеходные участки после неё.
         * [legMeters] — длины участков, где участок i ведёт от остановки i к i + 1.
         */
        fun remainingMeters(distanceToTarget: Double, activeIndex: Int, legMeters: List<Double>): Double {
            var total = distanceToTarget
            for (i in activeIndex until legMeters.size) total += legMeters[i]
            return total
        }

        private fun distanceTo(point: GeoPoint, fix: LocationFix): Double =
            distanceMeters(fix.lat, fix.lon, point.lat, point.lon)
    }
}
