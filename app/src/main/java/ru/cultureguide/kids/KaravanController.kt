package ru.cultureguide.kids

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.cultureguide.kids.audio.ClipPlayer
import ru.cultureguide.kids.content.ArrivalDetector
import ru.cultureguide.kids.content.Clips
import ru.cultureguide.kids.content.HeadingFusion
import ru.cultureguide.kids.content.Instruction
import ru.cultureguide.kids.content.Journey
import ru.cultureguide.kids.content.KidsRoute
import ru.cultureguide.kids.content.ON_PATH_METERS
import ru.cultureguide.kids.content.Reroute
import ru.cultureguide.kids.content.RoutePaths
import ru.cultureguide.kids.content.StreetGraph
import ru.cultureguide.kids.content.WalkPath
import ru.cultureguide.kids.content.angleDelta
import ru.cultureguide.kids.content.formatEstimate
import ru.cultureguide.kids.content.walkEstimateMinutes
import ru.cultureguide.kids.ui.pointsWord
import ru.cultureguide.navigation.formatDistance
import ru.cultureguide.kids.content.instruction as nextInstruction
import ru.cultureguide.kids.map.ApproachRouter
import ru.cultureguide.kids.photo.Collage
import ru.cultureguide.kids.photo.CollageCard
import ru.cultureguide.kids.photo.PhotoStore
import ru.cultureguide.model.Place
import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.LocationFix
import ru.cultureguide.navigation.distanceMeters
import kotlin.math.abs

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
    /** Улицы центра без дворов: по ним путь «от меня до точки» строится прямо на телефоне. */
    private val streets: StreetGraph,
    val player: ClipPlayer,
    private val router: ApproachRouter,
    /** Фото на память с точек и коллаж из них. */
    val photos: PhotoStore
) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
    /** Где рисовать стрелку: на линии пути, если мы рядом с ней, иначе там, где показал GPS. */
    var shownLocation by mutableStateOf<LocationFix?>(null)
        private set
    /** Сколько идти до следующей точки, м; null — позиция неизвестна. */
    var distanceToTarget by mutableStateOf<Double?>(null)
        private set
    var result by mutableStateOf<WalkResult?>(null)
        private set
    /** Вариант, который ребёнок выбрал в вопросе на открытой точке; null — ещё не отвечал. */
    var quizChoice by mutableStateOf<Int?>(null)
        private set
    /**
     * Путь «от меня до точки» по улицам: к первой точке прогулки или построенный заново,
     * когда свернули. Пока он есть, он заменяет на карте линию маршрута к этой точке.
     * null — идём по линии маршрута.
     */
    var approachLine by mutableStateOf<List<GeoPoint>?>(null)
        private set
    /** Пути по улицам нет (далеко от центра и нет интернета) — к точке тянется прямая. */
    var approachStraight by mutableStateOf(false)
        private set

    /** Ближайший поворот на пути к точке; null — пути по улицам нет, идём по прямой. */
    var instruction by mutableStateOf<Instruction?>(null)
        private set
    /** Куда смотрит телефон, градусы от севера; null — пока неизвестно. */
    var heading by mutableStateOf<Double?>(null)
        private set
    /** Карта поворачивается за направлением сразу, без перерисовки экранов. */
    var onHeading: ((Double) -> Unit)? = null

    /** Точка уже рядом: линия тут мало помогает из-за погрешности GPS, показываем стрелку на точку. */
    val nearTarget: Boolean get() = (distanceToTarget ?: Double.MAX_VALUE) <= NEAR_METERS

    /** Текущая цель прогулки на карте. */
    val targetPoint: GeoPoint? get() = journey.activeStop?.let { points[it] }

    private val headingFusion = HeadingFusion()
    // Что Троша уже сказал про текущую цель — чтобы не повторяться.
    private var announcedTarget: Int? = null
    private var announcedTurnAt: Double? = null
    private var nearAnnounced = false
    private var wasOnPath = false
    // Рассказ начинается, когда мы у точки и простояли там несколько секунд.
    private var arrival: ArrivalDetector? = null
    private var lastSpeed: Float? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val arrivalTick = object : Runnable {
        override fun run() {
            // Стоящему телефону GPS присылает замеры реже — время ожидания отсчитываем сами.
            if (screen == Screen.Walk && arrival?.check(SystemClock.elapsedRealtime()) == true) arrive()
            main.postDelayed(this, ARRIVAL_TICK_MS)
        }
    }

    init {
        main.postDelayed(arrivalTick, ARRIVAL_TICK_MS)
    }
    private var offFixes = 0

    private var aheadLine: List<GeoPoint>? = null
    private var approachPath: WalkPath? = null
    private var approachTarget: Int? = null
    private var lastRerouteAt = Long.MIN_VALUE / 2
    private var lastOnlineRequestAt = Long.MIN_VALUE / 2
    private var routeRequestInFlight = false

    val parentStoryPlaying: Boolean get() = player.playing == Clips.parent(openedStop)

    /** Длина прогулки по выбранным точкам вдоль пешеходных линий; null — линий нет. */
    fun planMeters(plan: List<Int>): Double? = paths.planMeters(plan)

    /** «9 точек · 1,7 км пешком · около 1,5 часа» — точки в порядке прогулки. */
    fun planSummary(plan: List<Int>): String {
        val meters = planMeters(plan)?.takeIf { plan.size > 1 }
        return listOfNotNull(
            "${plan.size} ${pointsWord(plan.size)}",
            meters?.let { "${formatDistance(it)} пешком" },
            formatEstimate(walkEstimateMinutes(meters ?: 0.0, plan.size))
        ).joinToString(" · ")
    }

    fun openChooser() {
        stopAudio()
        screen = Screen.Choose
    }

    /** Новая прогулка по выбранным точкам. */
    fun startWalk(selection: List<Int>) {
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
        lastSpeed = loc.speed.takeIf { loc.hasSpeed() }
        headingFusion.onGps(
            loc.bearing.takeIf { loc.hasBearing() },
            loc.speed.takeIf { loc.hasSpeed() },
            SystemClock.elapsedRealtime()
        )?.let(::publishHeading)
        updateGuidance(fix)
    }

    /** Показания компаса, градусы от севера. */
    fun onCompass(degrees: Double) {
        headingFusion.onCompass(degrees, SystemClock.elapsedRealtime())?.let(::publishHeading)
    }

    private fun publishHeading(value: Double) {
        onHeading?.invoke(value)
        // Экран со стрелкой перерисовываем, только когда направление заметно изменилось.
        val old = heading
        if (old == null || abs(angleDelta(old, value)) >= HEADING_STEP_DEG) heading = value
    }

    private fun updateGuidance(fix: LocationFix) {
        val target = journey.activeStop
        if (target == null) {
            distanceToTarget = null
            instruction = null
            shownLocation = fix
            return
        }
        if (announcedTarget != target) {
            announcedTarget = target
            announcedTurnAt = null
            nearAnnounced = false
            wasOnPath = false
            offFixes = 0
            arrival = ArrivalDetector(route.stops[target].radiusMeters)
        }
        val radius = route.stops[target].radiusMeters
        val straight = distanceMeters(fix.lat, fix.lon, points[target].lat, points[target].lon)
        // Путь к точке: построенный заново «от меня», а если его нет — линия маршрута от прошлой точки.
        // К первой точке прогулки линии маршрута нет — путь сразу строится от того места, где стоим.
        val approach = approachPath?.takeIf { approachTarget == target }
        val active = approach ?: journey.previousStop?.let { paths.between(it, target) }
        val progress = active?.progress(fix)
        val onPath = progress != null && progress.offPathMeters <= ON_PATH_METERS
        if (onPath) {
            offFixes = 0
            wasOnPath = true
            // Как в навигаторе: стрелка едет по линии, а не прыгает рядом вместе с GPS.
            val snapped = active!!.pointAt(progress!!.alongMeters)
            shownLocation = LocationFix(snapped.lat, snapped.lon, fix.accuracyMeters)
            distanceToTarget = progress.remainingMeters
            guide(active, progress.alongMeters)
            // Как в навигаторе: пройденная часть линии исчезает, остаётся путь впереди.
            aheadLine = active.remainingFrom(progress.alongMeters)
        } else {
            aheadLine = null
            offFixes++
            shownLocation = fix
            distanceToTarget = straight
            instruction = null
            if (screen == Screen.Walk) reroute(fix, target, hasPath = active != null)
        }
        showApproach(target)

        val distance = distanceToTarget
        if (!nearAnnounced && distance != null && distance <= NEAR_METERS && distance > radius) {
            nearAnnounced = true
            announce(Clips.NEAR)
        }
        // Прибыли: в зоне точки по настоящему положению телефона и простояли там несколько секунд.
        val arrived = arrival?.onFix(GeoPoint(fix.lat, fix.lon), straight, lastSpeed, SystemClock.elapsedRealtime()) == true
        if (screen == Screen.Walk && arrived) arrive()
    }

    /**
     * Что рисовать до точки: путь впереди стрелки, если мы на линии; путь «от меня», если он
     * построен заново; прямую, если пути нет совсем.
     */
    private fun showApproach(target: Int) {
        val approach = approachPath?.takeIf { approachTarget == target }
        val hasLeg = journey.previousStop?.let { paths.between(it, target) } != null
        val here = location
        approachStraight = aheadLine == null && approach == null && !hasLeg && here != null
        approachLine = when {
            aheadLine != null -> aheadLine
            approach != null -> approach.points
            approachStraight -> listOf(GeoPoint(here!!.lat, here.lon), points[target])
            else -> null
        }
    }

    /**
     * Свернули или пути ещё нет — строим заново от того места, где стоим: по встроенной
     * карте улиц мгновенно и без интернета; если мы далеко от центра — у OSRM.
     */
    private fun reroute(fix: LocationFix, target: Int, hasPath: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!Reroute.needed(hasPath, offFixes, now - lastRerouteAt, Reroute.OFFLINE_INTERVAL_MS)) return
        lastRerouteAt = now
        if (hasPath && wasOnPath) {
            // Шли по линии и ушли с неё: Троша предупреждает, а путь уже строится заново.
            wasOnPath = false
            announce(Clips.OFF_ROUTE)
        }
        val from = GeoPoint(fix.lat, fix.lon)
        val line = streets.route(from, points[target])
        if (line != null) {
            setApproach(line, target)
            updateGuidance(fix)
            return
        }
        if (routeRequestInFlight || now - lastOnlineRequestAt < Reroute.ONLINE_INTERVAL_MS) return
        routeRequestInFlight = true
        lastOnlineRequestAt = now
        router.route(from, points[target]) { online ->
            routeRequestInFlight = false
            if (online != null && journey.activeStop == target) {
                setApproach(online, target)
                location?.let(::updateGuidance)
            }
        }
    }

    private fun setApproach(line: List<GeoPoint>, target: Int) {
        approachPath = WalkPath(line)
        approachTarget = target
        offFixes = 0
        // Новый путь — новые повороты.
        announcedTurnAt = null
    }

    /** Подсказка о ближайшем повороте; за [TURN_ANNOUNCE_METERS] до него Троша говорит, куда повернуть. */
    private fun guide(path: WalkPath, alongMeters: Double) {
        val next = nextInstruction(path, alongMeters)
        instruction = next
        val turn = next.turn ?: return
        val at = next.maneuver?.atMeters ?: return
        val already = announcedTurnAt
        if (next.inMeters <= TURN_ANNOUNCE_METERS && (already == null || abs(already - at) > 1.0)) {
            announcedTurnAt = at
            announce(Clips.turn(turn))
        }
    }

    private fun announce(clip: String) {
        if (screen == Screen.Walk) player.enqueue(clip)
    }

    /** Подошли к точке — по GPS или по кнопке «Мы на месте!». */
    fun arrive() {
        val stop = journey.activeStop ?: return
        // Вернулись на карту, не забрав вещь, — снова ждём, пока постоим у точки.
        arrival?.reset()
        openedStop = stop
        quizChoice = null
        screen = Screen.Stop
        player.play(Clips.arrival(stop))
    }

    val speaking: Boolean get() = player.playing != null

    /** Кнопка динамика на экране точки: остановить озвучку или послушать рассказ ещё раз. */
    fun toggleStopStory() {
        if (speaking) {
            player.stop()
        } else {
            player.play(Clips.narrator(openedStop), Clips.trosha(openedStop), Clips.task(openedStop))
        }
    }

    /** Ребёнок выбрал вариант ответа: Троша хвалит или просит попробовать ещё раз. */
    fun answer(option: Int) {
        quizChoice = option
        if (route.stops[openedStop].question.isRight(option)) {
            player.play(Clips.RIGHT, Clips.PHOTO)
        } else {
            player.play(Clips.WRONG)
        }
    }

    /** Фото сохранено: если у всех найденных вещей есть фото, коллаж собирается сам. */
    fun onPhotoSaved() {
        refreshCollage()
    }

    /** Сколько найденных вещей ещё без фото — пока их больше нуля, коллажа нет. */
    fun photosMissing(): Int = journey.found.count { !photos.has(it) }

    private fun refreshCollage() {
        val found = route.defaultOrder.filter { journey.isFound(it) } +
            journey.found.filter { it !in route.defaultOrder }.sorted()
        if (found.isEmpty() || found.any { !photos.has(it) }) {
            photos.deleteCollage()
            return
        }
        val badge = journey.badge
        val titles = found.associateWith { route.stops[it].title }
        val stickers = found.associateWith { route.stops[it].sticker }
        photos.saveCollage {
            val cards = found.mapNotNull { stop ->
                photos.load(stop, COLLAGE_PHOTO_PX)?.let { CollageCard(it, titles.getValue(stop), sticker(stickers.getValue(stop))) }
            }
            if (cards.isEmpty()) {
                null
            } else {
                Collage.render(
                    cards,
                    subtitle = "Мои находки с Трошей · ${cards.size} ${placesWord(cards.size)}",
                    badge = if (badge) sticker(route.badge) else null,
                    trosha = sticker("trosha")
                ).also { cards.forEach { c -> c.photo.recycle() } }
            }
        }
    }

    private fun sticker(name: String) =
        runCatching { appContext.assets.open("kids/stickers/$name.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull()

    /** Кнопка динамика на карте: остановить озвучку или ещё раз позвать за собой. */
    fun toggleWalkHint() {
        if (speaking) player.stop() else player.play(Clips.GO)
    }

    /** Задание выполнено: вещь Троши попадает в альбом, идём дальше. */
    fun completeStop() {
        stopAudio()
        journey = journey.collect(openedStop).also(::save)
        refreshCollage()
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
        result = WalkResult(complete, journey.plan.filter { it in journey.walkFound })
        journey = journey.finish().also(::save)
        distanceToTarget = null
        approachLine = null
        approachStraight = false
        instruction = null
        screen = Screen.Finale
        if (complete) player.play(Clips.BELL, Clips.FINALE) else player.play(Clips.LATER)
    }

    /** С экрана точки обратно на карту, не засчитывая находку. */
    fun backToWalk() {
        stopAudio()
        screen = Screen.Walk
    }

    /** Подробная историческая справка из каталога — для взрослых, голосом рассказчика. */
    fun toggleParentStory() {
        if (parentStoryPlaying) player.stop() else player.play(Clips.parent(openedStop))
    }

    fun openAlbum() {
        stopAudio()
        screen = Screen.Album
    }

    fun goHome() {
        stopAudio()
        screen = Screen.Home
    }

    /** Очистить альбом, фото и значок; начатая прогулка тоже сбрасывается. */
    fun resetAlbum() {
        stopAudio()
        photos.clear()
        journey = journey.reset().also(::save)
        screen = Screen.Home
    }

    fun stopAudio() {
        player.stop()
    }

    fun dispose() {
        main.removeCallbacks(arrivalTick)
        player.stop()
        router.shutdown()
        photos.shutdown()
    }

    private fun loadJourney(): Journey {
        val count = route.stops.size
        if (prefs.getString(KEY_ROUTE, null) != route.id) return Journey(count)
        fun indices(key: String) =
            prefs.getString(key, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0 until count }
        val plan = indices(KEY_PLAN).distinct()
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

    private fun placesWord(n: Int): String = when {
        n % 100 in 11..14 -> "мест"
        n % 10 == 1 -> "место"
        n % 10 in 2..4 -> "места"
        else -> "мест"
    }

    private companion object {
        const val COLLAGE_PHOTO_PX = 900
        /** За сколько метров до поворота Троша о нём говорит. */
        const val TURN_ANNOUNCE_METERS = 20.0
        /** С какого расстояния Троша говорит «точка уже близко» и на экране появляется стрелка. */
        const val NEAR_METERS = 70.0
        const val HEADING_STEP_DEG = 3.0
        const val ARRIVAL_TICK_MS = 1_000L

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
