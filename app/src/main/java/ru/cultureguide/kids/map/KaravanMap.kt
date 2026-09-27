package ru.cultureguide.kids.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import ru.cultureguide.kids.content.Journey
import ru.cultureguide.kids.content.KidsStop
import ru.cultureguide.kids.content.RoutePaths
import ru.cultureguide.model.Place
import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.LocationFix

/**
 * Карта прогулки на MapLibre с бесплатной подложкой OpenFreeMap (данные OpenStreetMap, без ключа).
 * Показываются только точки текущей прогулки, соединённые пешеходными линиями из [paths]:
 * пройденные участки серые, текущий — сплошной, следующие — пунктир. Если свернули с линии
 * или идём к первой точке, от нас к цели тянется синий пунктир — по улицам, если удалось
 * получить маршрут (см. [ApproachRouter]), иначе по прямой. Найденные вещи показываются
 * наклейками, ненайденные — знаком вопроса.
 *
 * Как в навигаторе, карта по умолчанию следует за нами ([following]): мы в центре,
 * карта повёрнута туда, куда идём, а наше место показано стрелкой. Стоит сдвинуть карту
 * пальцем — слежение выключается и само возвращается через [RESUME_FOLLOW_MS] после того,
 * как карту отпустили; кнопка 🧭 ([follow]) возвращает его сразу.
 * Без интернета подложка заменяется однотонным фоном, а маршрут и точки остаются на месте.
 */
class KaravanMap(
    private val context: Context,
    private val stops: List<KidsStop>,
    private val places: List<Place>,
    /** Пешеходные линии между точками; где линии нет, соединяем точки прямой. */
    private val paths: RoutePaths
) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var journey: Journey? = null
    private var me: LocationFix? = null
    private var approach: List<GeoPoint>? = null
    private var approachStraight = false
    private var heading: Double? = null
    private var lastCameraAtMs = 0L
    private val main = Handler(Looper.getMainLooper())
    private val resumeFollow = Runnable { follow() }

    /** Карта следует за нами и поворачивается по направлению движения. */
    var following by mutableStateOf(true)
        private set
    private var fitted = false
    private var offline = false

    fun attach(view: MapView) {
        view.addOnDidFailLoadingMapListener {
            if (!offline) {
                offline = true
                map?.setStyle(Style.Builder().fromJson(OFFLINE_STYLE), ::onStyle)
            }
        }
        view.getMapAsync { m ->
            map = m
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    following = false
                    main.removeCallbacks(resumeFollow)
                }
            }
            // Карту отпустили — через несколько секунд она сама вернётся к нам.
            m.addOnCameraIdleListener {
                if (!following) {
                    main.removeCallbacks(resumeFollow)
                    main.postDelayed(resumeFollow, RESUME_FOLLOW_MS)
                }
            }
            m.uiSettings.apply {
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isLogoEnabled = false
            }
            m.setStyle(Style.Builder().fromUri(STYLE_URL), ::onStyle)
        }
    }

    fun detach() {
        main.removeCallbacks(resumeFollow)
        map = null
        style = null
        fitted = false
    }

    /**
     * @param me где рисовать стрелку (уже «притянутую» к линии, если мы на ней)
     * @param approach путь «от меня до точки»; пока он есть, линия маршрута к этой точке не рисуется
     * @param straight пути по улицам нет — [approach] это прямая до точки, рисуется пунктиром
     */
    fun update(journey: Journey, me: LocationFix?, approach: List<GeoPoint>?, straight: Boolean) {
        this.approachStraight = straight
        this.journey = journey
        this.me = me
        this.approach = approach
        render()
    }

    fun setHeading(value: Double) {
        heading = value
        renderMe()
        if (following) followCamera(animate = true)
    }

    /** Кнопка 🧭: снова вести за собой. */
    fun follow() {
        main.removeCallbacks(resumeFollow)
        following = true
        followCamera(animate = true, force = true)
    }

    /** Кнопка ⤢: весь маршрут целиком, карта перестаёт следовать за нами. */
    fun showAll() {
        following = false
        fitAll()
    }

    private fun followCamera(animate: Boolean, force: Boolean = false) {
        val m = map ?: return
        val here = me ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastCameraAtMs < CAMERA_INTERVAL_MS) return
        lastCameraAtMs = now
        val position = CameraPosition.Builder()
            .target(LatLng(here.lat, here.lon))
            .zoom(FOLLOW_ZOOM)
            .bearing(heading ?: m.cameraPosition.bearing)
            .tilt(FOLLOW_TILT)
            .build()
        val update = CameraUpdateFactory.newCameraPosition(position)
        if (animate) m.easeCamera(update, CAMERA_INTERVAL_MS.toInt()) else m.moveCamera(update)
    }

    /** Показать весь маршрут вместе с текущей позицией, севером вверх. */
    private fun fitAll() {
        val m = map ?: return
        val plan = journey?.plan.orEmpty()
        val points = plan.map { LatLng(places[it].lat, places[it].lon) } +
            legs(plan).flatten().map { LatLng(it.lat, it.lon) } +
            listOfNotNull(me?.let { LatLng(it.lat, it.lon) })
        if (points.size < 2) return
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 0.0, 0.0, FIT_PADDING_PX))
    }

    private fun onStyle(loaded: Style) {
        style = loaded
        addImages(loaded)
        loaded.addSource(GeoJsonSource(SRC_ROUTE))
        loaded.addSource(GeoJsonSource(SRC_APPROACH))
        loaded.addSource(GeoJsonSource(SRC_STOPS))
        loaded.addSource(GeoJsonSource(SRC_ME))
        loaded.addLayer(
            routeLayer(LAYER_ROUTE_DONE, STATE_DONE).withProperties(
                lineColor(DONE_COLOR),
                lineWidth(5f)
            )
        )
        loaded.addLayer(
            routeLayer(LAYER_ROUTE_NEXT, STATE_NEXT).withProperties(
                lineColor(ROUTE_COLOR),
                lineWidth(4f),
                lineOpacity(0.6f),
                lineDasharray(arrayOf(1.5f, 1.5f))
            )
        )
        loaded.addLayer(
            routeLayer(LAYER_ROUTE_ACTIVE, STATE_ACTIVE).withProperties(
                lineColor(ROUTE_COLOR),
                lineWidth(7f)
            )
        )
        loaded.addLayer(
            LineLayer(LAYER_APPROACH, SRC_APPROACH)
                .withFilter(Expression.eq(Expression.get(PROP_STATE), STATE_STRAIGHT))
                .withProperties(
                    lineColor(ME_COLOR),
                    lineWidth(4f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineDasharray(arrayOf(0.5f, 2f))
                )
        )
        // Путь «от меня до точки» по улицам выглядит как текущий участок маршрута: его и заменяет.
        loaded.addLayer(
            LineLayer(LAYER_APPROACH_ROUTE, SRC_APPROACH)
                .withFilter(Expression.eq(Expression.get(PROP_STATE), STATE_ACTIVE))
                .withProperties(
                    lineColor(ROUTE_COLOR),
                    lineWidth(7f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND)
                )
        )
        loaded.addLayer(
            CircleLayer(LAYER_ME_HALO, SRC_ME).withProperties(
                circleRadius(18f),
                circleColor(ME_COLOR),
                circleOpacity(0.2f)
            )
        )
        loaded.addLayer(
            SymbolLayer(LAYER_ME_ARROW, SRC_ME)
                .withFilter(Expression.has(PROP_BEARING))
                .withProperties(
                    iconImage(IMG_ARROW),
                    iconRotate(Expression.get(PROP_BEARING)),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
        )
        loaded.addLayer(
            CircleLayer(LAYER_ME, SRC_ME).withFilter(Expression.not(Expression.has(PROP_BEARING))).withProperties(
                circleRadius(8f),
                circleColor(ME_COLOR),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(3f)
            )
        )
        loaded.addLayer(
            SymbolLayer(LAYER_STOPS, SRC_STOPS).withProperties(
                iconImage(Expression.get(PROP_ICON)),
                iconSize(Expression.get(PROP_SIZE)),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
        )
        render()
    }

    private fun routeLayer(id: String, state: String): LineLayer =
        LineLayer(id, SRC_ROUTE)
            .withFilter(Expression.eq(Expression.get(PROP_STATE), state))
            .withProperties(lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND))

    private fun render() {
        val s = style ?: return
        val journey = journey ?: return
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                legs(journey.plan).mapIndexedNotNull { k, leg ->
                    // Участок k ведёт к точке plan[k + 1].
                    val state = when {
                        k + 1 < journey.position -> STATE_DONE
                        k + 1 == journey.position -> STATE_ACTIVE
                        else -> STATE_NEXT
                    }
                    // Путь построен заново от нас — старая линия к этой точке больше не нужна.
                    if (state == STATE_ACTIVE && approach != null && !approachStraight) return@mapIndexedNotNull null
                    Feature.fromGeometry(lineOf(leg)).apply { addStringProperty(PROP_STATE, state) }
                }
            )
        )
        s.getSourceAs<GeoJsonSource>(SRC_APPROACH)?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(
                    approach?.takeIf { it.size >= 2 }?.let {
                        Feature.fromGeometry(lineOf(it)).apply {
                            addStringProperty(PROP_STATE, if (approachStraight) STATE_STRAIGHT else STATE_ACTIVE)
                        }
                    }
                )
            )
        )
        s.getSourceAs<GeoJsonSource>(SRC_STOPS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                journey.plan.map { i ->
                    val place = places[i]
                    Feature.fromGeometry(Point.fromLngLat(place.lon, place.lat)).apply {
                        addStringProperty(PROP_ICON, if (journey.isFound(i)) stickerImage(stops[i].sticker) else IMG_MYSTERY)
                        addNumberProperty(PROP_SIZE, if (i == journey.activeStop) 1.0 else 0.75)
                    }
                }
            )
        )
        renderMe()
        if (!fitted) {
            fitted = true
            if (following && me != null) followCamera(animate = false, force = true) else fitAll()
        } else if (following) {
            followCamera(animate = true)
        }
    }

    /** Наше место: стрелка, если известно направление, иначе кружок. */
    private fun renderMe() {
        val s = style ?: return
        val here = me
        s.getSourceAs<GeoJsonSource>(SRC_ME)?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(
                    here?.let {
                        Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                            heading?.let { h -> addNumberProperty(PROP_BEARING, h) }
                        }
                    }
                )
            )
        )
    }

    /** Линии между соседними точками прогулки; без пешеходной линии — прямая. */
    private fun legs(plan: List<Int>): List<List<GeoPoint>> =
        plan.zipWithNext { a, b ->
            paths.between(a, b)?.points ?: listOf(GeoPoint(places[a].lat, places[a].lon), GeoPoint(places[b].lat, places[b].lon))
        }

    private fun lineOf(points: List<GeoPoint>): LineString = LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })

    private fun addImages(s: Style) {
        stops.map { it.sticker }.distinct().forEach { name ->
            runCatching {
                context.assets.open("kids/stickers/$name.webp").use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { s.addImage(stickerImage(name), scaled(it, ICON_PX)) }
        }
        s.addImage(IMG_MYSTERY, mysteryIcon(ICON_PX))
        s.addImage(IMG_ARROW, arrowIcon(ARROW_PX))
    }

    /** Стрелка «я иду сюда», как в навигаторе. */
    private fun arrowIcon(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = size.toFloat()
        val path = Path().apply {
            moveTo(s * 0.5f, s * 0.1f)
            lineTo(s * 0.86f, s * 0.88f)
            lineTo(s * 0.5f, s * 0.7f)
            lineTo(s * 0.14f, s * 0.88f)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        paint.color = ME_COLOR
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.07f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.WHITE
        canvas.drawPath(path, paint)
        return bitmap
    }

    private fun scaled(bitmap: Bitmap, maxSide: Int): Bitmap {
        val k = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt(), (bitmap.height * k).toInt(), true)
    }

    /** Круглый значок «?» для ещё не найденной вещи. */
    private fun mysteryIcon(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(r, r, r, paint)
        paint.color = ROUTE_COLOR
        canvas.drawCircle(r, r, r * 0.84f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * 0.6f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("?", r, r - (paint.descent() + paint.ascent()) / 2, paint)
        return bitmap
    }

    private fun stickerImage(name: String) = "sticker-$name"

    private companion object {
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        const val OFFLINE_STYLE =
            """{"version":8,"sources":{},"layers":[{"id":"bg","type":"background","paint":{"background-color":"#F3E6CC"}}]}"""

        const val SRC_ROUTE = "karavan-route"
        const val SRC_APPROACH = "karavan-approach"
        const val SRC_STOPS = "karavan-stops"
        const val SRC_ME = "karavan-me"
        const val LAYER_ROUTE_DONE = "karavan-route-done"
        const val LAYER_ROUTE_NEXT = "karavan-route-next"
        const val LAYER_ROUTE_ACTIVE = "karavan-route-active"
        const val LAYER_APPROACH = "karavan-approach-line"
        const val LAYER_APPROACH_ROUTE = "karavan-approach-route"
        const val STATE_STRAIGHT = "straight"
        const val LAYER_STOPS = "karavan-stops-icons"
        const val LAYER_ME = "karavan-me-dot"
        const val LAYER_ME_HALO = "karavan-me-halo"
        const val LAYER_ME_ARROW = "karavan-me-arrow"
        const val PROP_BEARING = "bearing"
        const val IMG_ARROW = "me-arrow"
        const val ARROW_PX = 84
        const val FOLLOW_ZOOM = 17.5
        const val FOLLOW_TILT = 40.0
        const val CAMERA_INTERVAL_MS = 300L
        const val RESUME_FOLLOW_MS = 5_000L
        const val PROP_ICON = "icon"
        const val PROP_SIZE = "size"
        const val PROP_STATE = "state"
        const val STATE_DONE = "done"
        const val STATE_ACTIVE = "active"
        const val STATE_NEXT = "next"
        const val IMG_MYSTERY = "mystery"

        const val ICON_PX = 132
        const val FIT_PADDING_PX = 120
        val ROUTE_COLOR = Color.rgb(0xD2, 0x46, 0x3C)
        val ME_COLOR = Color.rgb(0x2F, 0x6F, 0xB5)
        val DONE_COLOR = Color.rgb(0xB5, 0xA8, 0x96)
    }
}
