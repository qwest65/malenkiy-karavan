package ru.cultureguide.kids.map

import android.content.Context
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * Карта без интернета по всему району прогулки. Сама по себе MapLibre запоминает только то,
 * куда уже смотрели. Поэтому, пока есть интернет, один раз скачиваем район маршрута целиком:
 * стиль, кусочки карты с приближения [MIN_ZOOM] (дальше карту не отдаляем), шрифты и значки.
 * Раз в [REFRESH_DAYS] дней район скачивается заново — OpenFreeMap обновляет карту.
 * Тот же район, что сохраняет веб-версия.
 */
object OfflineArea {
    const val MIN_ZOOM = 11.0
    private const val MAX_ZOOM = 16.0
    /** Запас вокруг точек маршрута, градусы (~1 км). */
    private const val MARGIN = 0.01
    private const val REFRESH_DAYS = 30L
    private const val NAME = "karavan-route"

    fun ensure(context: Context, styleUrl: String, points: List<LatLng>) {
        if (points.isEmpty()) return
        val manager = OfflineManager.getInstance(context)
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val mine = offlineRegions.orEmpty().filter { savedAt(it) != null }
                val fresh = mine.firstOrNull { System.currentTimeMillis() - savedAt(it)!! < REFRESH_DAYS * DAY_MS }
                mine.filter { it !== fresh }.forEach { it.delete(DeleteQuietly) }
                if (fresh != null) download(fresh) else create(context, manager, styleUrl, points)
            }

            override fun onError(error: String) { /* без офлайн-карты */ }
        })
    }

    private fun create(context: Context, manager: OfflineManager, styleUrl: String, points: List<LatLng>) {
        val bounds = LatLngBounds.Builder()
            .include(LatLng(points.minOf { it.latitude } - MARGIN, points.minOf { it.longitude } - MARGIN * 1.7))
            .include(LatLng(points.maxOf { it.latitude } + MARGIN, points.maxOf { it.longitude } + MARGIN * 1.7))
            .build()
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl, bounds, MIN_ZOOM, MAX_ZOOM, context.resources.displayMetrics.density
        )
        val metadata = JSONObject().put("name", NAME).put("saved", System.currentTimeMillis()).toString()
        manager.createOfflineRegion(definition, metadata.toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) = download(offlineRegion)
            override fun onError(error: String) { /* без офлайн-карты */ }
        })
    }

    /** Докачивает район, если он скачан не целиком (например, прервался интернет). */
    private fun download(region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                if (status.isComplete) region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }
            override fun onError(error: OfflineRegionError) { /* докачаем в следующий раз */ }
            override fun mapboxTileCountLimitExceeded(limit: Long) {
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }
        })
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status != null && !status.isComplete) region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }
            override fun onError(error: String?) { /* докачаем в следующий раз */ }
        })
    }

    /** Когда район сохранён; null — это чужой район, не наш. */
    private fun savedAt(region: OfflineRegion): Long? = runCatching {
        JSONObject(String(region.metadata)).takeIf { it.optString("name") == NAME }?.getLong("saved")
    }.getOrNull()

    private object DeleteQuietly : OfflineRegion.OfflineRegionDeleteCallback {
        override fun onDelete() = Unit
        override fun onError(error: String) = Unit
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}
