package ru.cultureguide.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import ru.cultureguide.model.City
import ru.cultureguide.model.Place
import ru.cultureguide.model.RouteLine

/**
 * Локальное хранилище каталога. Источник данных — `assets/catalog.json`
 * (`core/src/main/assets/catalog.json`). При изменении поля `revision` в JSON
 * каталог повторно сливается с базой: описания обновляются, новые объекты и маршруты добавляются.
 */
class CatalogDatabase(private val context: Context) :
    SQLiteOpenHelper(context, "culture.db", null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cities(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,country TEXT NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL)")
        db.execSQL("CREATE TABLE places(id INTEGER PRIMARY KEY AUTOINCREMENT,city_id INTEGER NOT NULL,name TEXT NOT NULL,category TEXT NOT NULL,description TEXT NOT NULL,address TEXT NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL,source_url TEXT NOT NULL DEFAULT '',image_url TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE routes(id INTEGER PRIMARY KEY AUTOINCREMENT,city_id INTEGER NOT NULL,name TEXT NOT NULL,description TEXT NOT NULL)")
        db.execSQL("CREATE TABLE route_places(route_id INTEGER NOT NULL,place_id INTEGER NOT NULL,station_order INTEGER NOT NULL,PRIMARY KEY(route_id,place_id))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS routes(id INTEGER PRIMARY KEY AUTOINCREMENT,city_id INTEGER NOT NULL,name TEXT NOT NULL,description TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS route_places(route_id INTEGER NOT NULL,place_id INTEGER NOT NULL,station_order INTEGER NOT NULL,PRIMARY KEY(route_id,place_id))")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE places ADD COLUMN source_url TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE places ADD COLUMN image_url TEXT NOT NULL DEFAULT ''")
        }
    }

    /** Загружает встроенный каталог, если он новее уже импортированного. */
    fun ensureBundledCatalog() {
        val text = context.assets.open(BUNDLED_CATALOG).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val revision = JSONObject(text).optInt("revision", 1)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val empty = countOf("cities") == 0L
        if (!empty && prefs.getInt(KEY_REVISION, 0) >= revision) return
        mergeCatalogJson(text)
        prefs.edit().putInt(KEY_REVISION, revision).apply()
    }

    fun cities(): List<City> =
        readableDatabase.rawQuery("SELECT id,name,country,lat,lon FROM cities ORDER BY name", null).use {
            buildList {
                while (it.moveToNext()) add(City(it.getLong(0), it.getString(1), it.getString(2), it.getDouble(3), it.getDouble(4)))
            }
        }

    fun places(cityId: Long): List<Place> =
        readableDatabase.rawQuery(
            "SELECT id,name,category,description,address,lat,lon,source_url,image_url FROM places WHERE city_id=? ORDER BY id",
            arrayOf(cityId.toString())
        ).use {
            buildList {
                while (it.moveToNext()) add(
                    Place(
                        it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4),
                        it.getDouble(5), it.getDouble(6), it.getString(7), it.getString(8)
                    )
                )
            }
        }

    fun routes(cityId: Long): List<RouteLine> {
        val db = readableDatabase
        val base = db.rawQuery("SELECT id,name,description FROM routes WHERE city_id=? ORDER BY id", arrayOf(cityId.toString())).use {
            buildList { while (it.moveToNext()) add(Triple(it.getLong(0), it.getString(1), it.getString(2))) }
        }
        return base.map { (id, name, description) ->
            val ids = db.rawQuery("SELECT place_id FROM route_places WHERE route_id=? ORDER BY station_order", arrayOf(id.toString())).use {
                buildList { while (it.moveToNext()) add(it.getLong(0)) }
            }
            RouteLine(id, name, description, ids)
        }
    }

    fun routePlaces(route: RouteLine, all: List<Place>): List<Place> {
        val byId = all.associateBy { it.id }
        return route.placeIds.mapNotNull { byId[it] }
    }

    private fun countOf(table: String): Long =
        readableDatabase.compileStatement("SELECT COUNT(*) FROM $table").simpleQueryForLong()

    /**
     * Сливает каталог формата `cultureguide` с базой. Совпадения ищутся по названию
     * (город — по имени и стране, объект и маршрут — по имени внутри города).
     */
    fun mergeCatalogJson(text: String) {
        val root = JSONObject(text)
        require(root.optString("format") == "cultureguide") { "Неверный формат каталога" }
        require(root.optInt("version", -1) in 1..SUPPORTED_FORMAT) { "Неподдерживаемая версия каталога" }

        val db = writableDatabase
        db.beginTransaction()
        try {
            val cityIds = HashMap<Long, Long>()
            val placeIds = HashMap<Long, Long>()
            val routeIds = HashMap<Long, Long>()

            val cities = root.getJSONArray("cities")
            for (i in 0 until cities.length()) {
                val o = cities.getJSONObject(i)
                val name = o.getString("name").trim()
                val country = o.optString("country").trim()
                val existing = db.findId("SELECT id FROM cities WHERE name=? AND country=? LIMIT 1", name, country)
                val values = ContentValues().apply {
                    put("name", name); put("country", country)
                    put("lat", o.getDouble("lat")); put("lon", o.getDouble("lon"))
                }
                cityIds[o.getLong("id")] = existing?.also { db.update("cities", values, "id=?", arrayOf(it.toString())) }
                    ?: db.insertOrThrow("cities", null, values)
            }

            val places = root.getJSONArray("places")
            for (i in 0 until places.length()) {
                val o = places.getJSONObject(i)
                val city = cityIds[o.getLong("city_id")] ?: error("Город объекта не найден")
                val name = o.getString("name").trim()
                val lat = o.getDouble("lat")
                val lon = o.getDouble("lon")
                require(lat in -90.0..90.0 && lon in -180.0..180.0) { "Некорректные координаты: $name" }
                val values = ContentValues().apply {
                    put("city_id", city); put("name", name)
                    put("category", o.optString("category").trim())
                    put("description", o.optString("description").trim())
                    put("address", o.optString("address").trim())
                    put("lat", lat); put("lon", lon)
                    put("source_url", o.optString("source_url").trim())
                    put("image_url", o.optString("image_url").trim())
                }
                val existing = db.findId("SELECT id FROM places WHERE city_id=? AND name=? LIMIT 1", city.toString(), name)
                placeIds[o.getLong("id")] = existing?.also { db.update("places", values, "id=?", arrayOf(it.toString())) }
                    ?: db.insertOrThrow("places", null, values)
            }

            val routes = root.getJSONArray("routes")
            for (i in 0 until routes.length()) {
                val o = routes.getJSONObject(i)
                val city = cityIds[o.getLong("city_id")] ?: error("Город маршрута не найден")
                val name = o.getString("name").trim()
                val values = ContentValues().apply {
                    put("city_id", city); put("name", name); put("description", o.optString("description").trim())
                }
                val existing = db.findId("SELECT id FROM routes WHERE city_id=? AND name=? LIMIT 1", city.toString(), name)
                routeIds[o.getLong("id")] = existing?.also { db.update("routes", values, "id=?", arrayOf(it.toString())) }
                    ?: db.insertOrThrow("routes", null, values)
            }

            val linksByRoute = HashMap<Long, MutableList<Pair<Int, Long>>>()
            val links = root.getJSONArray("route_places")
            for (i in 0 until links.length()) {
                val o = links.getJSONObject(i)
                val route = routeIds[o.getLong("route_id")] ?: error("Маршрут связи не найден")
                val place = placeIds[o.getLong("place_id")] ?: error("Объект связи не найден")
                linksByRoute.getOrPut(route) { mutableListOf() } += o.getInt("station_order") to place
            }
            linksByRoute.forEach { (routeId, items) ->
                val seen = HashSet<Long>()
                val ordered = items.sortedBy { it.first }.map { it.second }.filter { seen.add(it) }
                db.delete("route_places", "route_id=?", arrayOf(routeId.toString()))
                ordered.forEachIndexed { order, placeId ->
                    db.insertOrThrow("route_places", null, ContentValues().apply {
                        put("route_id", routeId); put("place_id", placeId); put("station_order", order)
                    })
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun SQLiteDatabase.findId(sql: String, vararg args: String): Long? =
        rawQuery(sql, args).use { if (it.moveToFirst()) it.getLong(0) else null }

    companion object {
        private const val DB_VERSION = 5
        private const val SUPPORTED_FORMAT = 3
        private const val BUNDLED_CATALOG = "catalog.json"
        private const val PREFS = "catalog"
        private const val KEY_REVISION = "bundled_revision"
    }
}
