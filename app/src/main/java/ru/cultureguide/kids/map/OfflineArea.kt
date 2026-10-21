package ru.cultureguide.kids.map

import android.content.Context
import org.json.JSONObject
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion

/**
 * Версия 0.7.0 скачивала район прогулки с сервера карт для работы без интернета.
 * Теперь карта Троицка лежит внутри приложения (assets/map), и скачанный район только
 * занимает место — удаляем его.
 */
object OfflineArea {
    private const val NAME = "karavan-route"

    fun forget(context: Context) {
        OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                offlineRegions.orEmpty().filter(::isOurs).forEach { it.delete(DeleteQuietly) }
            }

            override fun onError(error: String) = Unit
        })
    }

    private fun isOurs(region: OfflineRegion) = runCatching {
        JSONObject(String(region.metadata)).optString("name") == NAME
    }.getOrDefault(false)

    private object DeleteQuietly : OfflineRegion.OfflineRegionDeleteCallback {
        override fun onDelete() = Unit
        override fun onError(error: String) = Unit
    }
}
