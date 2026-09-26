package ru.cultureguide.kids.content

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Точка детского маршрута. Координаты берутся из общего каталога по [placeId]. */
data class KidsStop(
    val placeId: Long,
    val title: String,
    /** Вещь Троши, которая находится на этой точке. */
    val item: String,
    /** Имя наклейки в `assets/kids/stickers/<sticker>.webp`. */
    val sticker: String,
    val narrator: String,
    val trosha: String,
    /** Игра на месте: показать, найти, изобразить. */
    val activity: String,
    /** Вопрос с вариантами ответа по рассказу. */
    val question: Quiz,
    /** Подсказка для взрослого: даты и как объяснить ребёнку. */
    val parent: String,
    /** Радиус прибытия, м: у точек, стоящих рядом друг с другом, он меньше. */
    val radiusMeters: Double = DEFAULT_RADIUS_M
) {
    companion object {
        const val DEFAULT_RADIUS_M = 45.0
    }
}


data class KidsRoute(
    val id: String,
    val title: String,
    val subtitle: String,
    val cityId: Long,
    val duration: String,
    val badge: String,
    val intro: String,
    /** Троша прощается после прогулки, пройденной до конца. */
    val finale: String,
    val stops: List<KidsStop>,
    /** Порядок точек для кнопки «Весь маршрут». */
    val defaultOrder: List<Int> = stops.indices.toList(),
    /** Готовые маршруты на экране «Куда пойдём?». */
    val presets: List<RoutePreset> = listOf(RoutePreset("🐫", "Весь маршрут", "", defaultOrder))
)

/** Готовый маршрут: набор точек в нужном порядке. */
data class RoutePreset(val emoji: String, val title: String, val subtitle: String, val stops: List<Int>)

/**
 * Имена аудиофайлов в `assets/kids/audio`. Их создаёт `app-kids/tools/generate_audio.py`
 * по текстам из `route.json`; номера точек начинаются с 1.
 */
object Clips {
    const val INTRO = "intro_trosha"
    const val FINALE = "finale_trosha"
    const val BELL = "bell"
    const val GO = "phrase_go"
    const val ARRIVED = "phrase_arrived"
    const val FOUND = "phrase_found"
    const val ROAD = "phrase_road"
    const val LATER = "phrase_later"
    const val RIGHT = "phrase_right"
    const val WRONG = "phrase_wrong"
    const val PHOTO = "phrase_photo"

    fun narrator(index: Int) = "stop${index + 1}_narrator"
    fun trosha(index: Int) = "stop${index + 1}_trosha"
    fun task(index: Int) = "stop${index + 1}_task"

    /** Подробная справка для взрослых — описание места из каталога. */
    fun parent(index: Int) = "stop${index + 1}_parent"

    /** Всё, что звучит при подходе к точке: звон, рассказ, находка Троши и вопрос. */
    fun arrival(index: Int) = listOf(BELL, ARRIVED, narrator(index), trosha(index), task(index))
}

object KidsRouteLoader {
    private const val ROUTE_ASSET = "kids/route.json"

    fun load(context: Context): KidsRoute =
        parse(context.assets.open(ROUTE_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() })

    fun parse(text: String): KidsRoute {
        val json = JSONObject(text)
        val stops = json.getJSONArray("stops")
        val order = json.optJSONArray("default_order")
        return KidsRoute(
            id = json.getString("id"),
            title = json.getString("title"),
            subtitle = json.optString("subtitle"),
            cityId = json.getLong("city_id"),
            duration = json.optString("duration"),
            badge = json.getString("badge"),
            intro = json.getJSONObject("intro").getString("trosha"),
            finale = json.optJSONObject("finale")?.optString("trosha").orEmpty(),
            stops = List(stops.length()) { i ->
                val s = stops.getJSONObject(i)
                KidsStop(
                    placeId = s.getLong("place_id"),
                    title = s.getString("title"),
                    item = s.getString("item"),
                    sticker = s.getString("sticker"),
                    narrator = s.getString("narrator"),
                    trosha = s.getString("trosha"),
                    activity = s.optString("activity"),
                    question = s.getJSONObject("question").let { q ->
                        val options = q.getJSONArray("options")
                        Quiz(q.getString("text"), List(options.length()) { options.getString(it) }, q.getInt("answer"))
                    },
                    parent = s.optString("parent"),
                    radiusMeters = s.optDouble("radius", KidsStop.DEFAULT_RADIUS_M)
                )
            },
            defaultOrder = order?.let { indices(it, stops.length()) } ?: List(stops.length()) { it },
            presets = json.optJSONArray("presets")?.let { a ->
                List(a.length()) { i ->
                    val p = a.getJSONObject(i)
                    RoutePreset(p.optString("emoji"), p.getString("title"), p.optString("subtitle"), indices(p.getJSONArray("stops"), stops.length()))
                }.filter { it.stops.isNotEmpty() }
            } ?: emptyList()
        ).let { route -> if (route.presets.isEmpty()) route.copy(presets = listOf(RoutePreset("🐫", "Весь маршрут", "", route.defaultOrder))) else route }
    }

    private fun indices(array: JSONArray, count: Int): List<Int> =
        List(array.length()) { array.getInt(it) }.filter { it in 0 until count }.distinct()
}
