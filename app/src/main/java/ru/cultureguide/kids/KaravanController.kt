package ru.cultureguide.kids

import android.content.Context
import android.location.Location
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.cultureguide.audio.AudioGuide
import ru.cultureguide.kids.audio.ClipPlayer
import ru.cultureguide.kids.content.Clips
import ru.cultureguide.kids.content.Journey
import ru.cultureguide.kids.content.KidsRoute
import ru.cultureguide.kids.content.ON_PATH_METERS
import ru.cultureguide.kids.content.Reroute
import ru.cultureguide.kids.content.RoutePaths
import ru.cultureguide.kids.content.WalkPath
import ru.cultureguide.kids.content.walkingMeters
import ru.cultureguide.kids.map.ApproachRouter
import ru.cultureguide.model.Place
import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.GuidanceEngine
import ru.cultureguide.navigation.LocationFix

enum class Screen { Home, Choose, Walk, Stop, Finale, Album }

/** Итог прогулки для финального экрана. */
data class WalkResult(
    /** Все выбранные точки пройдены, и что-то найдено — дают значок. */
    val complete: Boolean,
    /** Вещи, найденные на этой прогулке, в порядке маршрута. */
    val found: List<Int>
)

/**
 * Состояние «Маленького каравана»: какой экран открыт, куда идём, что уже в альбоме
 * и где сейчас ребёнок с родителем.
 */
class KaravanController(
    context: Context,
    val route: KidsRoute,
    /** Объекты общего каталога для точек маршрута, в порядке [KidsRoute.stops]. */
    val places: List<Place>,
    /** Пешеходные линии между точками; без них расстояние считается по прямой. */
    val paths: RoutePaths,
    val player: ClipPlayer,
    private val audioGuide: AudioGuide,
    private val router: ApproachRouter
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    // Точки проходятся строго по порядку: засчитываем только текущую.
    private val engine = GuidanceEngine(lookAhead = 0)
    private val points = places.map { GeoPoint(it.lat, it.lon) }

    var screen by mutableStateOf(Screen.Home)
        private set
    var journey by mutableStateOf(loadJourney())
        private set
    /** Точка, открытая на экране «Нашли!». */
    var openedStop by mutableStateOf(0)
        private set
    var location by mutableStateOf<LocationFix?>(null)
        private set
    /** Сколько идти до следующей точки, м; null — позиция неизвестна. */
    var distanceToTarget by mutableStateOf<Double?>(null)
        private set
    var result by mutableStateOf<WalkResult?>(null)
        private set
    /**
     * Линия «от меня до точки», когда до пешеходных линий маршрута далеко: по улицам,
     * если OSRM ответил, иначе прямая. null — идём по линии маршрута.
     */
    var approachLine by mutableStateOf<List<GeoPoint>?>(null)
        private set

    private var approachPath: WalkPath? = null
    private var approachTarget: Int? = null
    private var lastRouteRequestAt = Long.MIN_VALUE / 2
    private var routeRequestInFlight = false

    val parentStoryPlaying: Boolean get() = audioGuide.speakingPlaceId == places.getOrNull(openedStop)?.id

    /** Длина прогулки по выбранным точкам вдоль пешеходных линий; null — линий нет. */
    fun planMeters(plan: List<Int>): Double? = paths.planMeters(plan.sorted())

    fun openChooser() {
        stopAudio()
        screen = Screen.Choose
    }

    /** Новая прогулка по выбранным точкам. */
    fun startWalk(selection: Collection<Int>) {
        if (selection.isEmpty()) return
        stopAudio()
        journey = journey.start(selection).also(::save)
        screen = Screen.Walk
        player.play(Clips.INTRO, Clips.ROAD)
        location?.let(::updateGuidance)
    }

    fun resumeWalk() {
        if (!journey.inProgress) return
        stopAudio()
        screen = Screen.Walk
        player.play(Clips.GO)
        location?.let(::updateGuidance)
    }

    /** Пауза: прогулка сохраняется, её можно продолжить с главного экрана. */
    fun pause() {
        stopAudio()
        screen = Screen.Home
    }

    /** Закончить прогулку раньше; найденные вещи остаются в альбоме. */
    fun finishWalk() {
        if (journey.inProgress) endWalk(complete = false)
    }

    /** Пропустить текущую точку: закрыто, далеко или просто не хочется. */
    fun skipStop() {
        stopAudio()
        journey = journey.skip().also(::save)
        if (journey.walkComplete) {
            endWalk(complete = journey.walkFound.isNotEmpty())
        } else {
            screen = Screen.Walk
            player.play(Clips.GO)
            location?.let(::updateGuidance)
        }
    }

    fun onLocation(loc: Location) {
        val fix = LocationFix(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)
        location = fix
        updateGuidance(fix)
    }

    private fun updateGuidance(fix: LocationFix) {
        val target = journey.activeStop
        if (target == null) {
            distanceToTarget = null
            return
        }
        val update = engine.update(journey.plan.map { points[it] }, journey.position, fix)
        val straight = update.distanceToTarget ?: return
        // К первой точке прогулки линии маршрута нет — идём от того места, где стоим.
        val leg = journey.previousStop?.let { paths.between(it, target) }
        if (leg != null && leg.progress(fix).offPathMeters <= ON_PATH_METERS) {
            approachLine = null
            distanceToTarget = walkingMeters(leg, fix, straight)
        } else {
            val approach = approachPath?.takeIf { approachTarget == target }
            if (screen == Screen.Walk) requestApproach(fix, target, approach)
            val onApproach = approach != null && approach.progress(fix).offPathMeters <= ON_PATH_METERS
            approachLine = if (onApproach) approach!!.points else listOf(GeoPoint(fix.lat, fix.lon), points[target])
            distanceToTarget = walkingMeters(approach, fix, straight)
        }
        if (update.reached.isNotEmpty() && screen == Screen.Walk) arrive()
    }

    /** Просит у OSRM пешеходный путь до цели, если его нет или мы с него свернули. */
    private fun requestApproach(fix: LocationFix, target: Int, current: WalkPath?) {
        val now = SystemClock.elapsedRealtime()
        if (routeRequestInFlight || !Reroute.needed(current, fix, now - lastRouteRequestAt)) return
        routeRequestInFlight = true
        lastRouteRequestAt = now
        router.route(GeoPoint(fix.lat, fix.lon), points[target]) { line ->
            routeRequestInFlight = false
            if (line != null && journey.activeStop == target) {
                approachPath = WalkPath(line)
                approachTarget = target
                location?.let(::updateGuidance)
            }
        }
    }

    /** Подошли к точке — по GPS или по кнопке «Мы на месте!». */
    fun arrive() {
        val stop = journey.activeStop ?: return
        openedStop = stop
        screen = Screen.Stop
        audioGuide.stop()
        player.play(Clips.arrival(stop))
    }

    val speaking: Boolean get() = player.playing != null

    /** Кнопка динамика на экране точки: остановить озвучку или послушать рассказ ещё раз. */
    fun toggleStopStory() {
        audioGuide.stop()
        if (speaking) {
            player.stop()
        } else {
            player.play(Clips.narrator(openedStop), Clips.trosha(openedStop), Clips.task(openedStop))
        }
    }

    /** Кнопка динамика на карте: остановить озвучку или ещё раз позвать за собой. */
    fun toggleWalkHint() {
        if (speaking) player.stop() else player.play(Clips.GO)
    }

    /** Задание выполнено: вещь Троши попадает в альбом, идём дальше. */
    fun completeStop() {
        stopAudio()
        journey = journey.collect(openedStop).also(::save)
        if (journey.walkComplete) {
            endWalk(complete = true)
        } else {
            screen = Screen.Walk
            player.play(Clips.FOUND, Clips.GO)
            location?.let(::updateGuidance)
        }
    }

    private fun endWalk(complete: Boolean) {
        stopAudio()
        result = WalkResult(complete, journey.walkFound.sorted())
        journey = journey.finish().also(::save)
        distanceToTarget = null
        approachLine = null
        screen = Screen.Finale
        if (complete) player.play(Clips.BELL, Clips.FINALE) else player.play(Clips.LATER)
    }

    /** С экрана точки обратно на карту, не засчитывая находку. */
    fun backToWalk() {
        stopAudio()
        screen = Screen.Walk
    }

    /** Подробная историческая справка из общего каталога — для взрослых, голосом синтезатора. */
    fun toggleParentStory() {
        player.stop()
        places.getOrNull(openedStop)?.let(audioGuide::toggle)
    }

    fun openAlbum() {
        stopAudio()
        screen = Screen.Album
    }

    fun goHome() {
        stopAudio()
        screen = Screen.Home
    }

    /** Очистить альбом и значок; начатая прогулка тоже сбрасывается. */
    fun resetAlbum() {
        stopAudio()
        journey = journey.reset().also(::save)
        screen = Screen.Home
    }

    fun stopAudio() {
        player.stop()
        audioGuide.stop()
    }

    fun dispose() {
        player.stop()
        audioGuide.shutdown()
        router.shutdown()
    }

    private fun loadJourney(): Journey {
        val count = route.stops.size
        if (prefs.getString(KEY_ROUTE, null) != route.id) return Journey(count)
        fun indices(key: String) =
            prefs.getString(key, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0 until count }
        val plan = indices(KEY_PLAN).distinct().sorted()
        return Journey(
            stopCount = count,
            plan = plan,
            position = prefs.getInt(KEY_POSITION, 0).coerceIn(0, plan.size),
            found = indices(KEY_FOUND).toSet(),
            walkFound = indices(KEY_WALK_FOUND).toSet(),
            badge = prefs.getBoolean(KEY_BADGE, false)
        )
    }

    private fun save(journey: Journey) {
        prefs.edit()
            .putString(KEY_ROUTE, route.id)
            .putString(KEY_PLAN, journey.plan.joinToString(","))
            .putInt(KEY_POSITION, journey.position)
            .putString(KEY_FOUND, journey.found.sorted().joinToString(","))
            .putString(KEY_WALK_FOUND, journey.walkFound.sorted().joinToString(","))
            .putBoolean(KEY_BADGE, journey.badge)
            .apply()
    }

    private companion object {
        // Новое имя файла: в «journey» версии 0.1.0 прогресс хранился в другом формате.
        const val PREFS = "journey2"
        const val KEY_ROUTE = "route"
        const val KEY_PLAN = "plan"
        const val KEY_POSITION = "position"
        const val KEY_FOUND = "found"
        const val KEY_WALK_FOUND = "walk_found"
        const val KEY_BADGE = "badge"
    }
}
