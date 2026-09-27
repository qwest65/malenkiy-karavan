package ru.cultureguide.kids.content

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import ru.cultureguide.navigation.GeoPoint
import java.io.IOException

/**
 * Пешеходные линии из `assets/kids/paths.json` (их строит `app/tools/build_streets.py`)
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

/**
 * Улицы центра из `assets/kids/streets.json` (их строит `app/tools/build_streets.py`).
 * Нет файла — [StreetGraph.EMPTY], и путь «от меня до точки» спрашивается у OSRM.
 */
object StreetsLoader {
    private const val STREETS_ASSET = "kids/streets.json"

    fun load(context: Context): StreetGraph = try {
        val json = JSONObject(context.assets.open(STREETS_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() })
        val nodes = json.getJSONArray("nodes")
        val edges = json.getJSONArray("edges")
        StreetGraph.fromFlat(
            DoubleArray(nodes.length()) { nodes.getDouble(it) },
            IntArray(edges.length()) { edges.getInt(it) }
        )
    } catch (_: IOException) {
        StreetGraph.EMPTY
    } catch (_: JSONException) {
        StreetGraph.EMPTY
    } catch (_: IllegalArgumentException) {
        StreetGraph.EMPTY
    } catch (_: IndexOutOfBoundsException) {
        StreetGraph.EMPTY
    }
}
