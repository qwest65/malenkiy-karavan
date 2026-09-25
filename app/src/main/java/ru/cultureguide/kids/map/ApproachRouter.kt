package ru.cultureguide.kids.map

import android.os.Handler
import android.os.Looper
import org.json.JSONException
import org.json.JSONObject
import ru.cultureguide.navigation.GeoPoint
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Пешеходный маршрут «от меня до точки» у OSRM (профиль foot, данные OpenStreetMap).
 * Нужен, когда до пешеходных линий маршрута далеко: к первой точке прогулки или если свернули.
 * Без интернета [route] вернёт null, и дорогу покажет прямая.
 */
class ApproachRouter(private val baseUrl: String = DEFAULT_URL) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Результат приходит в главный поток; null — маршрут получить не удалось. */
    fun route(from: GeoPoint, to: GeoPoint, onResult: (List<GeoPoint>?) -> Unit) {
        executor.execute {
            val points = try {
                fetch(from, to)
            } catch (_: IOException) {
                null
            } catch (_: JSONException) {
                null
            }
            main.post { onResult(points) }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun fetch(from: GeoPoint, to: GeoPoint): List<GeoPoint>? {
        val coords = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", from.lon, from.lat, to.lon, to.lat)
        val connection = URL("$baseUrl/route/v1/driving/$coords?overview=full&geometries=geojson")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            if (json.optString("code") != "Ok") return null
            val coordinates = json.getJSONArray("routes").getJSONObject(0)
                .getJSONObject("geometry").getJSONArray("coordinates")
            val points = List(coordinates.length()) { i ->
                val p = coordinates.getJSONArray(i)
                GeoPoint(lat = p.getDouble(1), lon = p.getDouble(0))
            }
            // OSRM привязывает концы к ближайшей дорожке — дотягиваем линию до нас и до точки.
            return (listOf(from) + points + listOf(to)).takeIf { points.size >= 2 }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val DEFAULT_URL = "https://routing.openstreetmap.de/routed-foot"
        const val USER_AGENT = "MalenkiyKaravan-Android (+https://github.com/qwest65/malenkiy-karavan)"
        const val TIMEOUT_MS = 15_000
    }
}
