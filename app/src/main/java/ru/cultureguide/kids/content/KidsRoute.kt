package ru.cultureguide.kids.content

import android.content.Context
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
    val task: String,
    /** Подсказка для взрослого: даты и как объяснить ребёнку. */
    val parent: String
)

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
    val stops: List<KidsStop>
)

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

    fun narrator(index: Int) = "stop${index + 1}_narrator"
    fun trosha(index: Int) = "stop${index + 1}_trosha"
    fun task(index: Int) = "stop${index + 1}_task"

    /** Всё, что звучит при подходе к точке: звон, рассказ, находка Троши и задание. */
    fun arrival(index: Int) = listOf(BELL, ARRIVED, narrator(index), trosha(index), task(index))
}

object KidsRouteLoader {
    private const val ROUTE_ASSET = "kids/route.json"

    fun load(context: Context): KidsRoute =
        parse(context.assets.open(ROUTE_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() })

    fun parse(text: String): KidsRoute {
        val json = JSONObject(text)
        val stops = json.getJSONArray("stops")
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
                    task = s.getString("task"),
                    parent = s.optString("parent")
                )
            }
        )
    }
}
