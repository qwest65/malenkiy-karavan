package ru.cultureguide.kids.content

import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.distanceMeters

/**
 * Когда начинать рассказ: мы в зоне точки ([radiusMeters]) и простояли там [dwellMs].
 * Пока идём — даже совсем рядом — рассказ молчит.
 *
 * «Стоим» — это не «замерли»: ребёнок может крутиться и переминаться. Идём, если скорость
 * по GPS не меньше [WALKING_SPEED] или за последние секунды мы сместились больше чем на
 * [STILL_SPREAD_M]. Один случайный скачок GPS за пределы зоны отсчёт не сбрасывает:
 * ушли — это [LEAVE_FIXES] замера подряд снаружи.
 *
 * Время идёт и без новых замеров ([check]): стоящему телефону GPS присылает координаты реже.
 */
class ArrivalDetector(
    private val radiusMeters: Double,
    private val dwellMs: Long = DWELL_MS
) {
    private var stillSince: Long? = null
    private var anchor: GeoPoint? = null
    private var outside = 0

    /** Отсчёт идёт: мы в зоне и стоим. Для экрана — сколько ещё ждать. */
    fun remainingMs(nowMs: Long): Long? = stillSince?.let { (dwellMs - (nowMs - it)).coerceAtLeast(0) }

    /**
     * Новый замер: [distanceMeters] — до точки по прямой, [speedMps] — скорость по GPS, если есть.
     * @return true — пора начинать рассказ
     */
    fun onFix(position: GeoPoint, distanceMeters: Double, speedMps: Float?, nowMs: Long): Boolean {
        if (distanceMeters > radiusMeters) {
            outside++
            if (outside >= LEAVE_FIXES) reset()
            return check(nowMs)
        }
        outside = 0
        val start = anchor
        when {
            speedMps != null && speedMps >= WALKING_SPEED -> {
                // Идём: отсчёт начнётся, когда остановимся.
                stillSince = null
                anchor = position
            }
            start != null && dist(start, position) > STILL_SPREAD_M -> {
                // Заметно сместились — отсчёт заново с этого места.
                stillSince = nowMs
                anchor = position
            }
            stillSince == null -> {
                stillSince = nowMs
                anchor = anchor ?: position
            }
        }
        return check(nowMs)
    }

    /** Проверка по времени, без нового замера. */
    fun check(nowMs: Long): Boolean {
        val since = stillSince ?: return false
        return nowMs - since >= dwellMs
    }

    fun reset() {
        stillSince = null
        anchor = null
        outside = 0
    }

    private fun dist(a: GeoPoint, b: GeoPoint) = distanceMeters(a.lat, a.lon, b.lat, b.lon)

    companion object {
        const val DWELL_MS = 5_000L
        /** Обычный шаг взрослого с ребёнком — около 1 м/с. */
        const val WALKING_SPEED = 0.7f
        const val STILL_SPREAD_M = 10.0
        const val LEAVE_FIXES = 2
    }
}
