package ru.cultureguide.kids.content

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import ru.cultureguide.navigation.GeoPoint
import java.io.IOException

/**
 * Пешеходные линии из `assets/kids/paths.json` (их строит `app-kids/tools/build_paths.py`)
 * между любыми двумя точками маршрута. Если файла нет или он от другого маршрута,
 * возвращается [RoutePaths.EMPTY] и карта соединяет точки прямыми.
 */
object KidsPathsLoader {
    private const val PATHS_ASSET = "kids/paths.json"

    fun load(context: Context, route: KidsRoute): RoutePaths {
        val text = try {
            context.assets.open(PATHS_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: IOException) {
            return RoutePaths.EMPTY
        }
        return try {
            parse(text, route)
        } catch (_: JSONException) {
            RoutePaths.EMPTY
        } catch (_: IllegalArgumentException) {
            RoutePaths.EMPTY
        }
    }

    private fun parse(text: String, route: KidsRoute): RoutePaths {
        val json = JSONObject(text)
        if (json.optString("route") != route.id) return RoutePaths.EMPTY
        val legs = json.getJSONArray("legs")
        val byPair = HashMap<Pair<Int, Int>, WalkPath>()
        for (i in 0 until legs.length()) {
            val leg = legs.getJSONObject(i)
            // В старом формате без from/to участки шли подряд: от точки i к точке i + 1.
            val from = leg.optInt("from", i)
            val to = leg.optInt("to", i + 1)
            if (from !in route.stops.indices || to !in route.stops.indices || from >= to) continue
            val points = leg.getJSONArray("points")
            byPair[from to to] = WalkPath(List(points.length()) { j ->
                val p = points.getJSONArray(j)
                GeoPoint(lat = p.getDouble(1), lon = p.getDouble(0))
            })
        }
        return RoutePaths(byPair)
    }
}
