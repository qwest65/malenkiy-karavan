package ru.cultureguide.kids.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import ru.cultureguide.kids.content.formatEstimate
import ru.cultureguide.kids.content.walkEstimateMinutes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import ru.cultureguide.kids.photo.PhotoStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.maps.MapView
import ru.cultureguide.kids.KaravanController
import ru.cultureguide.kids.Screen
import ru.cultureguide.kids.WalkResult
import ru.cultureguide.kids.content.Clips
import ru.cultureguide.kids.content.Journey
import ru.cultureguide.kids.content.Turn
import ru.cultureguide.kids.content.angleDelta
import ru.cultureguide.kids.content.bearingDegrees
import ru.cultureguide.kids.content.kidSteps
import ru.cultureguide.kids.content.stepsWord
import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.LocationFix
import ru.cultureguide.navigation.formatDistance

/** Связь экранов с картой, которой владеет активность (жизненный цикл MapView). */
class MapHooks(
    val onCreated: (MapView) -> Unit,
    val onReleased: (MapView) -> Unit,
    val onUpdate: (Journey, LocationFix?, List<GeoPoint>?, Boolean) -> Unit,
    val onFitAll: () -> Unit,
    /** Следует ли карта за нами, как навигатор; читается как состояние Compose. */
    val isFollowing: () -> Boolean,
    val onFollow: () -> Unit,
    /** Куда повёрнута карта, градусы; читается как состояние Compose. */
    val bearing: () -> Double,
    val onNorth: () -> Unit
)

/**
 * @param ensureLocation просит доступ к геолокации и включает её — перед началом
 * или продолжением прогулки.
 */
/** Фото с точек: снять, выбрать из галереи, поделиться коллажем. Живёт в активности. */
class PhotoHooks(
    val takePhoto: (Int) -> Unit,
    val pickPhoto: (Int) -> Unit,
    val shareCollage: () -> Unit,
    val shareDiploma: () -> Unit
)

@Composable
fun KaravanApp(controller: KaravanController, ensureLocation: () -> Unit, map: MapHooks, photo: PhotoHooks) {
    BackHandler(enabled = controller.screen != Screen.Home) {
        when (controller.screen) {
            Screen.Stop -> controller.backToWalk()
            Screen.Walk -> controller.pause()
            else -> controller.goHome()
        }
    }
    Box(Modifier.fillMaxSize().background(Karavan.Sand)) {
        when (controller.screen) {
            Screen.Home -> HomeScreen(controller, ensureLocation)
            Screen.Choose -> ChooseScreen(controller, ensureLocation)
            Screen.Walk -> WalkScreen(controller, map)
            Screen.Stop -> StopScreen(controller, photo)
            Screen.Finale -> FinaleScreen(controller, photo)
            Screen.Album -> AlbumScreen(controller, photo)
        }
    }
}

@Composable
private fun HomeScreen(c: KaravanController, ensureLocation: () -> Unit) {
    val journey = c.journey
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    // Главный экран — как сайт проекта: небо, Троша у собора, игровой заголовок, вещи, которые предстоит найти.
    // Всё помещается на один экран: размеры подстраиваются под высоту телефона.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val w = maxWidth
        val gap = (h * 0.014f).coerceIn(6.dp, 14.dp)
        SkyBackground(home = true)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = h)
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)
        ) {
            val titleSize = minOf(w.value * 0.125f, h.value * 0.066f).coerceIn(32f, 60f).sp
            Column(Modifier.rotate(-3f), horizontalAlignment = Alignment.CenterHorizontally) {
                FunTitle("Маленький", fontSize = titleSize, stroke = 3.dp, tilt = 0f)
                FunTitle("караван", fontSize = titleSize, color = Bright.Brown, shadow = Bright.BrownShadow, stroke = 3.dp, tilt = 0f)
            }
            HomeScene(minOf(w * 0.74f, 340.dp, h * 0.33f))
            HomeCard(c, stickerSize = (h * 0.038f).coerceIn(26.dp, 34.dp))
            val goHeight = (h * 0.075f).coerceIn(54.dp, 64.dp)
            val softHeight = (h * 0.06f).coerceIn(44.dp, 52.dp)
            if (journey.inProgress) {
                BrightPanel(PanelStyle.Gold) {
                    Text("⏸ Прогулка на паузе", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
                    Text("Пройдено точек: ${journey.position} из ${journey.plan.size}", fontSize = 16.sp, color = Karavan.Ink)
                }
                ChunkyButton("Продолжить прогулку", height = goHeight, fontSize = 22.sp, onClick = { ensureLocation(); c.resumeWalk() })
                WhiteButton("Завершить прогулку", height = softHeight) { confirmFinish = true }
            } else {
                ChunkyButton("🐫 Начать прогулку", height = goHeight, fontSize = 22.sp, onClick = c::openChooser)
            }
            WhiteButton("📒 Мой альбом", height = softHeight, onClick = c::openAlbum)
            if (journey.found.isNotEmpty() || journey.badge) {
                // Мелкой ссылкой, а не кнопкой: главная должна помещаться на экран.
                Text(
                    "Очистить альбом",
                    fontSize = 14.sp,
                    color = Bright.GrassText,
                    modifier = Modifier.clickable { confirmReset = true }.padding(4.dp)
                )
            }
            HomeFoot()
        }
    }
    if (confirmFinish) {
        FinishDialog(onConfirm = { confirmFinish = false; c.finishWalk() }, onDismiss = { confirmFinish = false })
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Очистить альбом?") },
            text = { Text("Все найденные вещи и значок исчезнут, и Троша снова потеряется.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; c.resetAlbum() }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } }
        )
    }
}

/** Картинка с Трошей у собора: покачивается, слева табличка «ТРОИЦК», справа облачко с подсказкой. */
@Composable
private fun HomeScene(side: Dp) {
    val context = LocalContext.current
    val image = remember {
        runCatching { context.assets.open("kids/home-scene.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
    val sway = rememberInfiniteTransition(label = "sway")
    val phase by sway.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "swayPhase"
    )
    Box(
        Modifier
            .padding(top = 4.dp, bottom = 12.dp)
            .size(side)
            .graphicsLayer {
                rotationZ = -1f + 2.5f * phase
                translationY = -3.dp.toPx() * phase
            }
    ) {
        val shape = RoundedCornerShape(26.dp)
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Верблюжонок Троша на фоне Свято-Троицкого собора и карты",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(2f)
                    .shadow(12.dp, shape, ambientColor = Bright.Brown, spotColor = Bright.Brown)
                    .clip(shape)
                    .border(5.dp, Color.White, shape)
            )
        }
        // Табличка «ТРОИЦК», как дорожный указатель.
        val signShape = RoundedCornerShape(10.dp)
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-26).dp, y = side * 0.14f)
                .rotate(-9f)
                .background(Color(0xFF5A3216), signShape)
                .padding(bottom = 4.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFFA8662F), Color(0xFF8A5226))), signShape)
                .border(3.dp, Color(0xFF6E3F1C), signShape)
                .padding(horizontal = 11.dp, vertical = 5.dp)
        ) {
            Text("ТРОИЦК", color = Color(0xFFFFF6DC), fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        Text(
            buildAnnotatedString {
                append("Помоги ")
                withStyle(SpanStyle(color = Bright.Title)) { append("Троше") }
                append("\nсобрать вещи!")
            },
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Black,
            color = Karavan.Ink,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 16.dp, y = 14.dp)
                .rotate(-4f)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(horizontal = 11.dp, vertical = 7.dp)
        )
    }
}

/** Карточка маршрута на главной: название, ярлычки и все вещи Троши — найденные яркие, остальные бледные. */
@Composable
private fun HomeCard(c: KaravanController, stickerSize: Dp) {
    val found = c.journey.found.size
    val total = c.route.stops.size
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .rotate(-1f)
            .shadow(10.dp, shape, ambientColor = Bright.Brown, spotColor = Bright.Brown)
            .background(Bright.Cream, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(c.route.title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Bright.Brown, textAlign = TextAlign.Center)
        // Длина и время — по порядку «Весь маршрут», а не по номерам точек.
        PlanTagsFor(c, c.route.defaultOrder, center = true)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            c.route.stops.forEachIndexed { i, stop ->
                GhostSticker(stop.sticker, ghost = !c.journey.isFound(i), modifier = Modifier.size(stickerSize))
            }
        }
        Text(
            if (found > 0) "Найдено $found из $total вещей" else "Найди все $total вещей Троши!",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Karavan.Muted
        )
    }
}

/** «О проекте · Версия 0.7.2» внизу главной. */
@Composable
private fun HomeFoot() {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "О проекте",
            fontSize = 13.sp,
            color = Bright.GrassText,
            modifier = Modifier.clickable { runCatching { uri.openUri(SITE_URL) } }.padding(4.dp)
        )
        Text("·", fontSize = 13.sp, color = Bright.GrassText)
        Text("Версия $version", fontSize = 13.sp, color = Bright.GrassText, modifier = Modifier.padding(4.dp))
    }
}

/** маленький-караван.рф */
private const val SITE_URL = "https://xn----7sbbaalryjidglrj8b2n.xn--p1ai/"

/**
 * «Куда пойдём?»: готовые маршруты из route.json и кнопка «Собрать свой маршрут».
 * Готовый маршрут можно сразу начать или открыть в конструкторе и поправить.
 */
@Composable
private fun ChooseScreen(c: KaravanController, ensureLocation: () -> Unit) {
    // null — список маршрутов; иначе открыт конструктор с этими точками.
    var editing by remember { mutableStateOf<List<Int>?>(null) }
    val draft = editing
    if (draft != null) {
        BackHandler { editing = null }
        RouteBuilder(c, draft, onBack = { editing = null }, ensureLocation = ensureLocation)
        return
    }
    ScrollPage {
        BackRow("← Назад", c::goHome)
        FunTitle("Куда пойдём?")
        Subtitle("Выберите готовый маршрут или соберите свой.")
        c.route.presets.forEach { preset ->
            BrightPanel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(preset.emoji, fontSize = 34.sp)
                    Column(Modifier.weight(1f)) {
                        Text(preset.title, fontSize = 21.sp, fontWeight = FontWeight.Black, color = Bright.Brown)
                        if (preset.subtitle.isNotBlank()) Text(preset.subtitle, fontSize = 15.sp, color = Karavan.Muted)
                    }
                }
                StickerRow(c, preset.stops)
                PlanTagsFor(c, preset.stops)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChunkyButton("🐫 Идём!", Modifier.weight(1f), onClick = { ensureLocation(); c.startWalk(preset.stops) })
                    OutButton("Изменить", Modifier.padding(bottom = 5.dp)) { editing = preset.stops }
                }
            }
        }
        WhiteButton("✏️ Собрать свой маршрут") { editing = emptyList() }
    }
}

/**
 * Конструктор маршрута: родитель добавляет точки, убирает их и меняет порядок стрелками.
 * Открывается пустым («Собрать свой») или с точками готового маршрута («Изменить»).
 */
@Composable
private fun RouteBuilder(c: KaravanController, initial: List<Int>, onBack: () -> Unit, ensureLocation: () -> Unit) {
    val plan = remember(initial) { mutableStateListOf<Int>().apply { addAll(initial) } }
    val rest = c.route.stops.indices.filter { it !in plan }
    ScrollPage {
        BackRow("← К маршрутам", onBack)
        FunTitle("Свой маршрут")
        Subtitle("Добавляйте точки из списка ниже и меняйте порядок стрелками.")
        if (plan.isNotEmpty()) {
            TextButton(onClick = { plan.clear() }) { Text("Очистить", color = Karavan.Ink) }
        }
        if (plan.isEmpty()) {
            Text("Пока пусто — нажмите ＋ у нужных точек", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Karavan.Red)
        }
        plan.forEachIndexed { pos, i ->
            PlanStopCard(
                c = c,
                index = i,
                number = pos + 1,
                onUp = if (pos > 0) ({ plan.removeAt(pos); plan.add(pos - 1, i) }) else null,
                onDown = if (pos < plan.lastIndex) ({ plan.removeAt(pos); plan.add(pos + 1, i) }) else null,
                onRemove = { plan.removeAt(pos) }
            )
        }
        if (rest.isNotEmpty()) {
            Text("Добавить точку", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Bright.Brown, modifier = Modifier.fillMaxWidth())
            rest.forEach { i ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                        .clickable { plan.add(i) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StopThumb(c, i)
                    Text(c.route.stops[i].title, fontSize = 17.sp, color = Karavan.Ink, modifier = Modifier.weight(1f))
                    SmallButton("＋") { plan.add(i) }
                }
            }
        }
        if (plan.isEmpty()) {
            Text("Выберите хотя бы одну точку", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Karavan.Red)
        } else {
            PlanTagsFor(c, plan.toList(), center = true)
        }
        ChunkyButton("🐫 Идём!", enabled = plan.isNotEmpty(), onClick = { ensureLocation(); c.startWalk(plan.toList()) })
    }
}

/** Точка в составленном маршруте: номер, название и кнопки ↑ ↓ ✕. */
@Composable
private fun PlanStopCard(
    c: KaravanController,
    index: Int,
    number: Int,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRemove: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = Bright.Brown, spotColor = Bright.Brown)
            .background(Bright.Cream, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("$number", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Bright.Title)
        StopThumb(c, index)
        Column(Modifier.weight(1f)) {
            Text(c.route.stops[index].title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
            if (c.journey.isFound(index)) {
                Text("Уже в альбоме", fontSize = 13.sp, color = Karavan.Muted)
            }
        }
        SmallButton("↑", enabled = onUp != null, side = 38.dp) { onUp?.invoke() }
        SmallButton("↓", enabled = onDown != null, side = 38.dp) { onDown?.invoke() }
        SmallButton("✕", side = 38.dp, onClick = onRemove)
    }
}

@Composable
private fun StopThumb(c: KaravanController, index: Int) {
    if (c.journey.isFound(index)) {
        Sticker(c.route.stops[index].sticker, Modifier.size(44.dp))
    } else {
        Mystery(Modifier.size(40.dp), fontSize = 22)
    }
}

@Composable
private fun WalkScreen(c: KaravanController, map: MapHooks) {
    val journey = c.journey
    val active = journey.activeStop ?: return
    val stop = c.route.stops[active]
    val distance = c.distanceToTarget
    var confirmFinish by remember { mutableStateOf(false) }
    var panelExpanded by rememberSaveable { mutableStateOf(true) }
    // Панель сама сворачивается, если её не трогать: карте нужно больше места.
    // Любое касание панели начинает отсчёт заново.
    var panelTouch by remember { mutableStateOf(0) }
    LaunchedEffect(panelExpanded, panelTouch) {
        if (panelExpanded) {
            delay(PANEL_HIDE_MS)
            panelExpanded = false
        }
    }

    // Во время прогулки экран не гаснет: иначе остановится геолокация и Троша не узнает, что мы пришли.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(journey, c.shownLocation, c.approachLine, c.approachStraight) {
        map.onUpdate(journey, c.shownLocation, c.approachLine, c.approachStraight)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context -> MapView(context).also { it.onCreate(null); map.onCreated(it) } },
            onRelease = map.onReleased,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("⏸ Пауза", c::pause)
                Spacer(Modifier.weight(1f))
                val bearing = map.bearing()
                if (!map.isFollowing() && abs(bearing) >= 1.0) NorthButton(bearing, map.onNorth)
                if (!map.isFollowing()) RoundButton("🧭", map.onFollow)
                RoundButton("⤢", map.onFitAll)
            }
            NavigationBanner(c)
        }
        WalkPanel(
            expanded = panelExpanded,
            onExpandedChange = { panelExpanded = it },
            onTouch = { panelTouch++ },
            modifier = Modifier.align(Alignment.BottomCenter),
            summary = {
                Text(
                    distance?.let { "≈ ${kidSteps(it)} ${stepsWord(kidSteps(it))}" } ?: "Ищем, где мы…",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = if (distance != null) Bright.OrangeDeep else Karavan.Muted,
                    modifier = Modifier.weight(1f)
                )
                RoundButton("🔔", c::arrive)
            }
        ) {
            PlanRow(c)
            StepPill("Точка ${journey.position + 1} из ${journey.plan.size}")
            Text(stop.title, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, color = Bright.Brown)
            if (distance != null) {
                val steps = kidSteps(distance)
                Text("≈ $steps ${stepsWord(steps)}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Bright.OrangeDeep)
                Text("${formatDistance(distance)} для взрослых", fontSize = 14.sp, color = Karavan.Muted)
            } else {
                Text("Ищем, где мы…", fontSize = 20.sp, color = Karavan.Muted)
            }
            Text("🤝 Держи взрослого за руку", fontSize = 16.sp, color = Karavan.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(if (c.speaking) "⏹" else "🔊", c::toggleWalkHint)
                ChunkyButton("🔔 Мы на месте!", Modifier.weight(1f), onClick = c::arrive)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PillLink("Пропустить точку", fontSize = 14.sp, onClick = c::skipStop)
                PillLink("Завершить прогулку", fontSize = 14.sp) { confirmFinish = true }
            }
        }
    }
    if (confirmFinish) {
        FinishDialog(onConfirm = { confirmFinish = false; c.finishWalk() }, onDismiss = { confirmFinish = false })
    }
}

/**
 * Плашка навигатора над картой: куда повернуть и через сколько метров. Рядом с точкой
 * (или когда пути по улицам нет) — стрелка прямо на точку с учётом того, куда смотрит телефон.
 */
@Composable
private fun NavigationBanner(c: KaravanController) {
    val here = c.shownLocation ?: return
    val distance = c.distanceToTarget ?: return
    val target = c.targetPoint ?: return
    val instruction = c.instruction
    val heading = c.heading
    val pointer = heading != null && (c.nearTarget || instruction == null)
    val arrow: String
    val rotation: Float
    val title: String
    val subtitle: String?
    if (pointer) {
        val steps = kidSteps(distance)
        arrow = "↑"
        rotation = angleDelta(heading!!, bearingDegrees(GeoPoint(here.lat, here.lon), target)).toFloat()
        title = if (c.nearTarget) "Точка рядом — иди по стрелке" else "Иди по стрелке к точке"
        subtitle = "≈ $steps ${stepsWord(steps)} · ${formatDistance(distance)}"
    } else if (instruction?.turn != null) {
        val turn = instruction.turn
        arrow = if (turn == Turn.U_TURN) "↩" else "↑"
        rotation = when (turn) {
            Turn.LEFT -> -90f
            Turn.SLIGHT_LEFT -> -45f
            Turn.RIGHT -> 90f
            Turn.SLIGHT_RIGHT -> 45f
            Turn.U_TURN -> 0f
        }
        val what = when (turn) {
            Turn.LEFT -> "налево"
            Turn.SLIGHT_LEFT -> "чуть левее"
            Turn.RIGHT -> "направо"
            Turn.SLIGHT_RIGHT -> "чуть правее"
            Turn.U_TURN -> "разворот"
        }
        title = if (instruction.inMeters < NOW_METERS) "Сейчас $what" else "Через ${formatDistance(instruction.inMeters)} $what"
        subtitle = "До точки ${formatDistance(distance)}"
    } else if (instruction != null) {
        arrow = "↑"
        rotation = 0f
        title = "Прямо ${formatDistance(instruction.inMeters)}"
        subtitle = "и мы у точки"
    } else {
        return
    }
    val bannerShape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, bannerShape)
            .background(Bright.BlueEdge, bannerShape)
            .padding(bottom = 4.dp)
            .background(Brush.verticalGradient(listOf(Bright.BlueLight, Bright.BlueDeep)), bannerShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            arrow,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.rotate(rotation)
        )
        Column {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
            subtitle?.let { Text(it, fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f)) }
        }
    }
}

/** Ближе этого к повороту пишем «сейчас». */
private const val NOW_METERS = 8.0

/**
 * Нижняя панель прогулки. Свайп вниз (или нажатие на полоску) сворачивает её до одной строки
 * с шагами и колокольчиком, чтобы открыть карту; свайп вверх разворачивает обратно.
 */
@Composable
private fun WalkPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTouch: () -> Unit,
    modifier: Modifier = Modifier,
    summary: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var drag by remember { mutableStateOf(0f) }
    Column(
        modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Bright.Brown, spotColor = Bright.Brown)
            .background(Bright.Cream, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) onTouch()
                    }
                }
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> drag += delta },
                onDragStarted = { drag = 0f },
                onDragStopped = {
                    if (drag > SWIPE_PX) onExpandedChange(false) else if (drag < -SWIPE_PX) onExpandedChange(true)
                    drag = 0f
                }
            )
            .navigationBarsPadding()
            .animateContentSize()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(width = 48.dp, height = 5.dp).background(Karavan.Muted.copy(alpha = 0.5f), RoundedCornerShape(3.dp)))
        }
        if (expanded) {
            content()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, content = summary)
        }
    }
}

private const val SWIPE_PX = 40f
private const val PANEL_HIDE_MS = 5_000L

@Composable
private fun FinishDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Завершить прогулку?") },
        text = { Text("Найденные вещи останутся в альбоме. Остальные точки можно пройти в другой раз.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Завершить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Гуляем дальше") } }
    )
}

@Composable
private fun StopScreen(c: KaravanController, photo: PhotoHooks) {
    val index = c.openedStop
    val stop = c.route.stops[index]
    val journey = c.journey
    val last = journey.position == journey.plan.lastIndex
    // Вещь «находится», когда Троша начинает о ней рассказывать; без звука — сразу после рассказа.
    var revealed by remember(index) { mutableStateOf(false) }
    var heardSomething by remember(index) { mutableStateOf(false) }
    val playing = c.player.playing
    LaunchedEffect(playing) {
        if (playing != null) heardSomething = true
        if (playing == Clips.trosha(index) || playing == Clips.task(index) || (playing == null && heardSomething)) {
            revealed = true
        }
    }
    // Праздник находки — один раз за точку, когда Троша говорит, что вещь нашлась.
    var celebrating by remember(index) { mutableStateOf(false) }
    var landed by remember(index) { mutableStateOf(false) }
    var backpackCenter by remember { mutableStateOf<Offset?>(null) }
    val backpackBounce = remember { Animatable(1f) }
    LaunchedEffect(revealed) {
        if (revealed && c.startCelebration(index)) celebrating = true
    }
    LaunchedEffect(landed) {
        if (landed) {
            backpackBounce.animateTo(1.5f, tween(140))
            backpackBounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    val walkFound = journey.walkFound.size + if (landed && index !in journey.walkFound) 1 else 0
    var parentOpen by remember(index) { mutableStateOf(false) }
    // Правильный ответ записан первым; на экране варианты перемешаны.
    val optionOrder = remember(index) { stop.question.options.indices.shuffled() }
    val choice = c.quizChoice
    val answeredRight = choice != null && stop.question.isRight(choice)

    Box(Modifier.fillMaxSize()) {
    ScrollPage {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PillLink("← К карте", onClick = c::backToWalk)
            // Рюкзак Троши: сюда улетает найденная вещь.
            Row(
                Modifier
                    .onGloballyPositioned { backpackCenter = it.boundsInRoot().center }
                    .scale(backpackBounce.value)
                    .shadow(4.dp, RoundedCornerShape(20.dp))
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎒", fontSize = 26.sp)
                Text(" $walkFound из ${journey.plan.size}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
            }
        }
        StepPill("Точка ${journey.position + 1} из ${journey.plan.size}")
        FunTitle(stop.title, fontSize = 26.sp)
        FoundItem(stop.sticker, revealed)
        if (revealed) {
            Text("Нашлось: ${stop.item}!", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Bright.OrangeDeep, textAlign = TextAlign.Center)
        } else {
            PillLink("Показать находку", color = Bright.OrangeText) { revealed = true }
        }
        StoryCard("Рассказчик", stop.narrator)
        StoryCard("Троша", stop.trosha, avatar = "trosha")
        BrightPanel(PanelStyle.Gold) {
            Text("❓ ${stop.question.text}", fontSize = 21.sp, fontWeight = FontWeight.Black, color = Bright.Brown)
            optionOrder.forEachIndexed { k, option ->
                val picked = choice == option
                QuizOption(
                    text = stop.question.options[option],
                    edges = Bright.OptionEdges[k % Bright.OptionEdges.size],
                    state = when {
                        picked && stop.question.isRight(option) -> OptionState.Right
                        picked -> OptionState.Wrong
                        answeredRight -> OptionState.Off
                        else -> OptionState.Open
                    },
                    onClick = { c.answer(option) }
                )
            }
            when {
                answeredRight -> Text("⭐ Правильно! Молодец!", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Bright.Right)
                choice != null -> Text("Не совсем — попробуй ещё раз!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Bright.Wrong)
            }
        }
        if (stop.activity.isNotBlank()) {
            BrightPanel {
                Text("🎲 Игра", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Karavan.Muted)
                Text(stop.activity, fontSize = 18.sp, color = Karavan.Ink)
            }
        }
        PhotoPanel(c, index, photo)
        BrightPanel(PanelStyle.Kraft) {
            TextButton(onClick = { parentOpen = !parentOpen }) {
                Text(if (parentOpen) "▾ Для взрослых" else "▸ Для взрослых", fontSize = 17.sp, color = Karavan.Ink)
            }
            if (parentOpen) {
                Text(stop.parent, fontSize = 16.sp, color = Karavan.Ink)
                WhiteButton(if (c.parentStoryPlaying) "Остановить" else "Подробная история голосом", fontSize = 16.sp, onClick = c::toggleParentStory)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(if (c.speaking) "⏹" else "🔊", c::toggleStopStory)
            ChunkyButton(if (last) "Ура! Завершить прогулку" else "Готово! Идём дальше", Modifier.weight(1f), onClick = c::completeStop)
        }
    }
    if (celebrating) {
        Celebration(
            sticker = stop.sticker,
            title = "Ура! Нашлось!",
            subtitle = stop.item.replaceFirstChar { it.uppercase() },
            flyTo = backpackCenter,
            onLanded = { landed = true },
            onDone = { celebrating = false }
        )
    }
    }
}

/** Фото на память с этой точки: снять камерой или выбрать из галереи. */
@Composable
private fun PhotoPanel(c: KaravanController, index: Int, photo: PhotoHooks) {
    val image = rememberStopPhoto(c, index, PREVIEW_DP)
    BrightPanel {
        Text("📷 Фото на память", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Karavan.Muted)
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Фото с точки",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))
            )
        } else {
            Text("Сфотографируйтесь здесь вместе — из фото сложится коллаж в альбоме.", fontSize = 16.sp, color = Karavan.Ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhiteButton(if (image != null) "Переснять" else "📸 Камера", Modifier.weight(1f), height = 50.dp, fontSize = 16.sp) { photo.takePhoto(index) }
            WhiteButton("🖼 Из галереи", Modifier.weight(1f), height = 50.dp, fontSize = 16.sp) { photo.pickPhoto(index) }
        }
    }
}

@Composable
private fun FinaleScreen(c: KaravanController, photo: PhotoHooks) {
    val result = c.result ?: WalkResult(complete = false, found = emptyList())
    val pulse = rememberInfiniteTransition(label = "badge")
    val scale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "badgeScale"
    )
    // Значок за весь маршрут — с салютом во весь экран, один раз.
    var celebrating by remember(result) { mutableStateOf(result.complete && c.startCelebration(FINALE_CELEBRATION)) }
    Box(Modifier.fillMaxSize()) {
    ScrollPage {
        if (result.complete) {
            FunTitle("Ура!", fontSize = 52.sp)
            Sticker(c.route.badge, Modifier.size(260.dp).scale(scale))
            FunTitle("Ты — Юный караванщик!", fontSize = 28.sp)
            StoryCard("Троша", c.route.finale, avatar = "trosha")
        } else {
            FunTitle("Прогулка окончена")
            Sticker("trosha", Modifier.size(200.dp))
            Subtitle("Ничего, продолжим в другой раз! Остальные вещи Троша подождёт.", fontSize = 18)
        }
        BrightPanel {
            if (result.found.isEmpty()) {
                Text("Сегодня ничего не нашли", fontSize = 17.sp, color = Karavan.Muted)
            } else {
                Text("Сегодня нашли:", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.found.forEach { Sticker(c.route.stops[it].sticker, Modifier.size(56.dp)) }
                }
            }
        }
        if (result.complete) DiplomaPanel(c, photo)
        ChunkyButton("Мой альбом", onClick = c::openAlbum)
        WhiteButton("На главную", onClick = c::goHome)
    }
    if (celebrating) {
        Celebration(
            sticker = c.route.badge,
            title = "Ура!",
            subtitle = "Ты — Юный караванщик!",
            flyTo = null,
            fireworks = true,
            onDone = { celebrating = false }
        )
    }
    }
}

/** Отметка «финал уже отпраздновали» — отдельно от номеров точек. */
private const val FINALE_CELEBRATION = -1

@Composable
private fun AlbumScreen(c: KaravanController, photo: PhotoHooks) {
    val journey = c.journey
    val version = c.photos.version
    val collage = rememberCollage(c)
    val missing = remember(version, journey) { c.photosMissing() }
    ScrollPage {
        BackRow("← Назад", c::goHome)
        FunTitle("Мой альбом")
        StepPill("Найдено ${journey.found.size} из ${journey.stopCount}")
        if (collage != null) {
            BrightPanel {
                Text("🖼 Коллаж прогулки", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
                Image(
                    bitmap = collage,
                    contentDescription = "Коллаж из фото прогулки",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                )
                WhiteButton("Поделиться или сохранить", fontSize = 16.sp, onClick = photo.shareCollage)
            }
        } else if (journey.found.isNotEmpty()) {
            BrightPanel(PanelStyle.Gold) {
                Text(
                    "Добавьте фото ещё к $missing ${findsWord(missing)} — и коллаж соберётся сам.",
                    fontSize = 16.sp,
                    color = Karavan.Ink
                )
            }
        }
        if (journey.badge) DiplomaPanel(c, photo)
        c.route.stops.indices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { i ->
                    val stop = c.route.stops[i]
                    val found = journey.isFound(i)
                    val image = if (found) rememberStopPhoto(c, i, THUMB_DP) else null
                    AlbumCard(Modifier.weight(1f), found = found) {
                        if (image != null) {
                            Box {
                                Image(
                                    bitmap = image,
                                    contentDescription = "Фото: ${stop.title}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(130.dp).clip(RoundedCornerShape(14.dp))
                                )
                                Sticker(stop.sticker, Modifier.size(48.dp).align(Alignment.TopEnd))
                            }
                        } else {
                            Sticker(stop.sticker, Modifier.size(120.dp), found = found)
                        }
                        Text(
                            if (found) stop.item else "Ещё не нашли",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (found) Karavan.Ink else Karavan.Muted,
                            textAlign = TextAlign.Center
                        )
                        Text(stop.title, fontSize = 13.sp, color = Karavan.Muted, textAlign = TextAlign.Center)
                        if (found) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                SmallButton("📸") { photo.takePhoto(i) }
                                SmallButton("🖼") { photo.pickPhoto(i) }
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        AlbumCard(Modifier.fillMaxWidth(), found = journey.badge) {
            Sticker(c.route.badge, Modifier.size(150.dp), found = journey.badge)
            Text(
                if (journey.badge) "Юный караванщик" else "Значок — за прогулку, пройденную до конца",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Karavan.Ink,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Фото точки для экрана; перечитывается, когда фото меняется. */
@Composable
private fun rememberStopPhoto(c: KaravanController, index: Int, sideDp: Int): ImageBitmap? {
    val density = LocalDensity.current.density
    val version = c.photos.version
    val image by produceState<ImageBitmap?>(null, index, version) {
        value = withContext(Dispatchers.IO) {
            c.photos.load(index, PhotoStore.previewSide(density, sideDp))?.asImageBitmap()
        }
    }
    return image
}

@Composable
private fun rememberCollage(c: KaravanController): ImageBitmap? {
    val version = c.photos.version
    val image by produceState<ImageBitmap?>(null, version) {
        value = withContext(Dispatchers.IO) { c.photos.loadCollage(COLLAGE_PX)?.asImageBitmap() }
    }
    return image
}

/**
 * Грамота «Юный караванщик»: пока имени нет (или его исправляют) — поле «Имя и фамилия»,
 * потом сама грамота с кнопкой «Поделиться» и ссылкой «Исправить имя».
 */
@Composable
private fun DiplomaPanel(c: KaravanController, photo: PhotoHooks) {
    val name = c.childName
    var editing by remember { mutableStateOf(false) }
    if (name == null || editing) {
        var value by remember(name) { mutableStateOf(name.orEmpty()) }
        BrightPanel(PanelStyle.Gold) {
            Text("🏅 Грамота юного караванщика", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
            Text(
                "Как зовут юного караванщика? Имя попадёт в грамоту и останется только в этом телефоне.",
                fontSize = 16.sp,
                color = Karavan.Ink
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(40) },
                placeholder = { Text("Имя и фамилия") },
                singleLine = true,
                textStyle = TextStyle(fontSize = 20.sp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Bright.GoldEdge,
                    unfocusedBorderColor = Color(0xFFE0B04A)
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            ChunkyButton(if (name == null) "Получить грамоту" else "Исправить грамоту", enabled = value.isNotBlank()) {
                c.saveChildName(value)
                editing = false
            }
        }
    } else {
        val diploma = rememberDiploma(c)
        BrightPanel {
            Text("🏅 Грамота юного караванщика", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
            if (diploma != null) {
                Image(
                    bitmap = diploma,
                    contentDescription = "Грамота: $name — Юный караванщик",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                )
            } else {
                Text("Готовим грамоту…", fontSize = 16.sp, color = Karavan.Muted)
            }
            WhiteButton("Поделиться или сохранить", fontSize = 16.sp, onClick = photo.shareDiploma)
            TextButton(onClick = { editing = true }) { Text("Исправить имя", color = Karavan.Muted) }
        }
    }
}

@Composable
private fun rememberDiploma(c: KaravanController): ImageBitmap? {
    val version = c.photos.version
    val image by produceState<ImageBitmap?>(null, version, c.childName) {
        value = withContext(Dispatchers.IO) { c.photos.loadDiploma(COLLAGE_PX)?.asImageBitmap() }
    }
    return image
}

private const val PREVIEW_DP = 360
private const val THUMB_DP = 130
private const val COLLAGE_PX = 1080

/** Знак вопроса, который с пружинкой превращается в наклейку найденной вещи. */
@Composable
private fun FoundItem(sticker: String, revealed: Boolean) {
    Box(
        Modifier
            .size(210.dp)
            .background(Brush.radialGradient(0f to Color.White, 0.55f to Color.White, 0.7f to Color.White.copy(alpha = 0f)), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!revealed) {
            Mystery(Modifier.size(140.dp), fontSize = 80)
        }
        AnimatedVisibility(
            visible = revealed,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()
        ) {
            Sticker(sticker, Modifier.size(210.dp))
        }
    }
}

/** Все вещи Троши: найденные — наклейками, остальные — знаком вопроса. */
@Composable
private fun AlbumRow(c: KaravanController) {
    StickerRow(c, c.route.stops.indices.toList())
}

/** Точки текущей прогулки: караван растёт с каждой находкой. */
@Composable
private fun PlanRow(c: KaravanController) {
    StickerRow(c, c.journey.plan)
}

/** Ярлычки длины, числа точек и времени для маршрута [plan]. */
@Composable
private fun PlanTagsFor(c: KaravanController, plan: List<Int>, center: Boolean = false) {
    val meters = c.planMeters(plan)?.takeIf { plan.size > 1 }
    PlanTags(
        walk = meters?.let(::formatDistance),
        stops = "${plan.size} ${pointsWord(plan.size)}",
        // «около часа» → «1 час», иначе ярлычок «часа» читался бы странно.
        time = formatEstimate(walkEstimateMinutes(meters ?: 0.0, plan.size)).let { if (it == "около часа") "1 час" else it.removePrefix("около ") },
        center = center
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickerRow(c: KaravanController, stops: List<Int>) {
    // Все девять точек в одну строку не помещаются — переносятся на вторую.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        stops.forEach { i ->
            if (c.journey.isFound(i)) {
                Sticker(c.route.stops[i].sticker, Modifier.size(52.dp))
            } else {
                Mystery(Modifier.size(40.dp), fontSize = 22)
            }
        }
    }
}

@Composable
private fun StoryCard(who: String, text: String, avatar: String? = null) {
    BrightPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (avatar != null) {
                Sticker(avatar, Modifier.size(44.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(who, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Karavan.Muted)
        }
        Text(text, fontSize = 18.sp, lineHeight = 26.sp, color = Karavan.Ink)
    }
}

/** Страница на фоне неба: все экраны, кроме главной и карты. */
@Composable
private fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        SkyBackground(home = false)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

/** «← Назад» белой таблеткой в левом верхнем углу. */
@Composable
private fun BackRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth()) { PillLink(label, onClick = onClick) }
}

@Composable
private fun Subtitle(text: String, fontSize: Int = 16) {
    Text(
        text,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.SemiBold,
        color = Bright.SubText,
        textAlign = TextAlign.Center
    )
}

/**
 * Карточка альбома: найденная вещь — в белой рамке с оранжевым ободком,
 * ещё не найденная — бледнее и без тени.
 */
@Composable
private fun AlbumCard(modifier: Modifier, found: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val box = if (found) {
        modifier
            .shadow(6.dp, shape, ambientColor = Bright.Brown, spotColor = Bright.Brown)
            .background(Bright.GoldEdge, shape)
            .padding(3.dp)
            .background(Color.White, shape)
            .padding(3.dp)
            .background(Bright.Cream, shape)
    } else {
        modifier.background(Bright.Cream.copy(alpha = 0.7f), shape)
    }
    Column(
        box.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

private enum class OptionState { Open, Right, Wrong, Off }

/** Вариант ответа — белый «пузырь» с цветной рамкой; правильный зеленеет, неправильный краснеет. */
@Composable
private fun QuizOption(text: String, edges: Pair<Color, Color>, state: OptionState, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val (fill, border, edge, ink) = when (state) {
        OptionState.Open -> listOf(Color.White, edges.first, edges.second, Karavan.Ink)
        OptionState.Right -> listOf(Bright.Right, Bright.Right, Bright.Right.copy(alpha = 0.6f), Color.White)
        OptionState.Wrong -> listOf(Bright.Wrong, Bright.Wrong, Bright.Wrong.copy(alpha = 0.6f), Color.White)
        OptionState.Off -> listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Transparent, Karavan.Muted)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(61.dp)
            .background(edge, shape)
            .padding(bottom = 3.dp)
            .background(fill, shape)
            .border(3.dp, border, shape)
            .clickable(enabled = state != OptionState.Off, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ink, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Karavan.Ink)
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallButton(label: String, enabled: Boolean = true, side: Dp = 44.dp, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(side),
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Karavan.Kraft,
            contentColor = Karavan.Ink,
            disabledContainerColor = Karavan.Kraft.copy(alpha = 0.35f),
            disabledContentColor = Karavan.Ink.copy(alpha = 0.35f)
        )
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoundButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Karavan.Ink)
    ) {
        Text(label, fontSize = 24.sp)
    }
}

/** Кнопка «▲С»: красная стрелка показывает, где север на повёрнутой карте. */
@Composable
private fun NorthButton(bearing: Double, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Karavan.Ink)
    ) {
        Column(Modifier.rotate(-bearing.toFloat()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("▲", fontSize = 16.sp, color = Karavan.Red, lineHeight = 16.sp)
            Text("С", fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
        }
    }
}

/** «к 1 находке», «к 3 находкам». */
private fun findsWord(n: Int): String = if (n % 10 == 1 && n % 100 != 11) "находке" else "находкам"

internal fun pointsWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "точек"
        mod10 == 1 -> "точка"
        mod10 in 2..4 -> "точки"
        else -> "точек"
    }
}
