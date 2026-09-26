package ru.cultureguide.kids.ui

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
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
import ru.cultureguide.kids.content.kidSteps
import ru.cultureguide.kids.content.stepsWord
import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.LocationFix
import ru.cultureguide.navigation.formatDistance

/** Связь экранов с картой, которой владеет активность (жизненный цикл MapView). */
class MapHooks(
    val onCreated: (MapView) -> Unit,
    val onReleased: (MapView) -> Unit,
    val onUpdate: (Journey, LocationFix?, List<GeoPoint>?) -> Unit,
    val onFitAll: () -> Unit
)

/**
 * @param ensureLocation просит доступ к геолокации и включает её — перед началом
 * или продолжением прогулки.
 */
/** Фото с точек: снять, выбрать из галереи, поделиться коллажем. Живёт в активности. */
class PhotoHooks(
    val takePhoto: (Int) -> Unit,
    val pickPhoto: (Int) -> Unit,
    val shareCollage: () -> Unit
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
            Screen.Finale -> FinaleScreen(controller)
            Screen.Album -> AlbumScreen(controller, photo)
        }
    }
}

@Composable
private fun HomeScreen(c: KaravanController, ensureLocation: () -> Unit) {
    val journey = c.journey
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    ScrollPage {
        Text(
            "Маленький караван",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Karavan.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Sticker("trosha", Modifier.size(220.dp))
        Panel {
            Text(c.route.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
            Text(c.route.subtitle, fontSize = 17.sp, color = Karavan.Ink)
            Text(
                listOfNotNull(
                    "${c.route.stops.size} ${pointsWord(c.route.stops.size)}",
                    c.planMeters(c.route.stops.indices.toList())?.let { "${formatDistance(it)} пешком" },
                    c.route.duration
                ).joinToString(" · "),
                fontSize = 15.sp,
                color = Karavan.Muted
            )
            if (journey.found.isNotEmpty()) {
                Text(
                    "В альбоме ${journey.found.size} из ${journey.stopCount}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Karavan.Red
                )
                AlbumRow(c)
            }
        }
        if (journey.inProgress) {
            Panel(color = Karavan.Gold.copy(alpha = 0.35f)) {
                Text("⏸ Прогулка на паузе", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
                Text(
                    "Пройдено точек: ${journey.position} из ${journey.plan.size}",
                    fontSize = 16.sp,
                    color = Karavan.Ink
                )
            }
            BigButton("Продолжить прогулку", onClick = { ensureLocation(); c.resumeWalk() })
            SoftButton("Завершить прогулку") { confirmFinish = true }
        } else {
            BigButton("Начать прогулку", onClick = c::openChooser)
        }
        SoftButton("Мой альбом", c::openAlbum)
        if (journey.found.isNotEmpty() || journey.badge) {
            TextButton(onClick = { confirmReset = true }) { Text("Очистить альбом", color = Karavan.Muted) }
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
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = c::goHome) { Text("← Назад", color = Karavan.Muted) }
        }
        Text("Куда пойдём?", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Karavan.Ink)
        Text("Выберите готовый маршрут или соберите свой.", fontSize = 16.sp, color = Karavan.Muted, textAlign = TextAlign.Center)
        c.route.presets.forEach { preset ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(preset.emoji, fontSize = 34.sp)
                    Column(Modifier.weight(1f)) {
                        Text(preset.title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
                        if (preset.subtitle.isNotBlank()) Text(preset.subtitle, fontSize = 15.sp, color = Karavan.Muted)
                    }
                }
                StickerRow(c, preset.stops)
                Text(
                    listOfNotNull(
                        "${preset.stops.size} ${pointsWord(preset.stops.size)}",
                        c.planMeters(preset.stops)?.takeIf { preset.stops.size > 1 }?.let { "${formatDistance(it)} пешком" }
                    ).joinToString(" · "),
                    fontSize = 15.sp,
                    color = Karavan.Ink
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BigButton("Идём!", Modifier.weight(1f), onClick = { ensureLocation(); c.startWalk(preset.stops) })
                    OutlinedButton(onClick = { editing = preset.stops }, modifier = Modifier.height(64.dp)) { Text("Изменить") }
                }
            }
        }
        SoftButton("✏️ Собрать свой маршрут") { editing = emptyList() }
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
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("← К маршрутам", color = Karavan.Muted) }
        }
        Text("Свой маршрут", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Karavan.Ink)
        Text(
            "Добавляйте точки из списка ниже и меняйте порядок стрелками.",
            fontSize = 16.sp,
            color = Karavan.Muted,
            textAlign = TextAlign.Center
        )
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
            Text("Добавить точку", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink, modifier = Modifier.fillMaxWidth())
            rest.forEach { i ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { plan.add(i) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Karavan.Kraft.copy(alpha = 0.4f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StopThumb(c, i)
                        Text(c.route.stops[i].title, fontSize = 17.sp, color = Karavan.Ink, modifier = Modifier.weight(1f))
                        SmallButton("＋") { plan.add(i) }
                    }
                }
            }
        }
        Text(
            if (plan.isEmpty()) {
                "Выберите хотя бы одну точку"
            } else {
                listOfNotNull(
                    "${plan.size} ${pointsWord(plan.size)}",
                    c.planMeters(plan.toList())?.takeIf { plan.size > 1 }?.let { "${formatDistance(it)} пешком" }
                ).joinToString(" · ")
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (plan.isEmpty()) Karavan.Red else Karavan.Ink
        )
        BigButton("Идём!", enabled = plan.isNotEmpty(), onClick = { ensureLocation(); c.startWalk(plan.toList()) })
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Karavan.Card)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("$number", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Karavan.Red)
            StopThumb(c, index)
            Column(Modifier.weight(1f)) {
                Text(c.route.stops[index].title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
                if (c.journey.isFound(index)) {
                    Text("Уже в альбоме", fontSize = 13.sp, color = Karavan.Muted)
                }
            }
            SmallButton("↑", enabled = onUp != null) { onUp?.invoke() }
            SmallButton("↓", enabled = onDown != null) { onDown?.invoke() }
            SmallButton("✕", onClick = onRemove)
        }
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

    // Во время прогулки экран не гаснет: иначе остановится геолокация и Троша не узнает, что мы пришли.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(journey, c.location, c.approachLine) { map.onUpdate(journey, c.location, c.approachLine) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context -> MapView(context).also { it.onCreate(null); map.onCreated(it) } },
            onRelease = map.onReleased,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PillButton("⏸ Пауза", c::pause)
            RoundButton("⤢", map.onFitAll)
        }
        WalkPanel(
            expanded = panelExpanded,
            onExpandedChange = { panelExpanded = it },
            modifier = Modifier.align(Alignment.BottomCenter),
            summary = {
                Text(
                    distance?.let { "≈ ${kidSteps(it)} ${stepsWord(kidSteps(it))}" } ?: "Ищем, где мы…",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = if (distance != null) Karavan.Red else Karavan.Muted,
                    modifier = Modifier.weight(1f)
                )
                RoundButton("🔔", c::arrive)
            }
        ) {
            PlanRow(c)
            Text(
                "Точка ${journey.position + 1} из ${journey.plan.size}: ${stop.title}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Karavan.Ink
            )
            if (distance != null) {
                val steps = kidSteps(distance)
                Text("≈ $steps ${stepsWord(steps)}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Karavan.Red)
                Text("${formatDistance(distance)} для взрослых", fontSize = 14.sp, color = Karavan.Muted)
            } else {
                Text("Ищем, где мы…", fontSize = 20.sp, color = Karavan.Muted)
            }
            Text("🤝 Держи взрослого за руку", fontSize = 16.sp, color = Karavan.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(if (c.speaking) "⏹" else "🔊", c::toggleWalkHint)
                BigButton("🔔 Мы на месте!", Modifier.weight(1f), onClick = c::arrive)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = c::skipStop) { Text("Пропустить точку", color = Karavan.Muted) }
                TextButton(onClick = { confirmFinish = true }) { Text("Завершить прогулку", color = Karavan.Muted) }
            }
        }
    }
    if (confirmFinish) {
        FinishDialog(onConfirm = { confirmFinish = false; c.finishWalk() }, onDismiss = { confirmFinish = false })
    }
}

/**
 * Нижняя панель прогулки. Свайп вниз (или нажатие на полоску) сворачивает её до одной строки
 * с шагами и колокольчиком, чтобы открыть карту; свайп вверх разворачивает обратно.
 */
@Composable
private fun WalkPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var drag by remember { mutableStateOf(0f) }
    Column(
        modifier
            .fillMaxWidth()
            .background(Karavan.Card, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
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
    var parentOpen by remember(index) { mutableStateOf(false) }
    // Правильный ответ записан первым; на экране варианты перемешаны.
    val optionOrder = remember(index) { stop.question.options.indices.shuffled() }
    val choice = c.quizChoice
    val answeredRight = choice != null && stop.question.isRight(choice)

    ScrollPage {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = c::backToWalk) { Text("← К карте", color = Karavan.Muted) }
        }
        Text("Точка ${journey.position + 1} из ${journey.plan.size}", fontSize = 15.sp, color = Karavan.Muted)
        Text(stop.title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Karavan.Ink, textAlign = TextAlign.Center)
        FoundItem(stop.sticker, revealed)
        if (revealed) {
            Text("Нашлось: ${stop.item}!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Karavan.Red)
        } else {
            TextButton(onClick = { revealed = true }) { Text("Показать находку", color = Karavan.Muted) }
        }
        StoryCard("Рассказчик", stop.narrator)
        StoryCard("Троша", stop.trosha, avatar = "trosha")
        Panel(color = Karavan.Gold.copy(alpha = 0.35f)) {
            Text("❓ ${stop.question.text}", fontSize = 21.sp, fontWeight = FontWeight.Black, color = Karavan.Ink)
            optionOrder.forEach { option ->
                val picked = choice == option
                val right = stop.question.isRight(option)
                val color = when {
                    picked && right -> Karavan.Green
                    picked -> Karavan.Red.copy(alpha = 0.75f)
                    answeredRight -> Karavan.Card.copy(alpha = 0.6f)
                    else -> Karavan.Card
                }
                Button(
                    onClick = { c.answer(option) },
                    enabled = !answeredRight || picked,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = color,
                        contentColor = if (picked) Color.White else Karavan.Ink,
                        disabledContainerColor = color,
                        disabledContentColor = Karavan.Muted
                    )
                ) {
                    Text(stop.question.options[option], fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            when {
                answeredRight -> Text("⭐ Правильно! Молодец!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Karavan.Green)
                choice != null -> Text("Не совсем — попробуй ещё раз!", fontSize = 18.sp, color = Karavan.Red)
            }
        }
        if (stop.activity.isNotBlank()) {
            Panel {
                Text("🎲 Игра", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Karavan.Muted)
                Text(stop.activity, fontSize = 18.sp, color = Karavan.Ink)
            }
        }
        PhotoPanel(c, index, photo)
        Panel(color = Karavan.Kraft.copy(alpha = 0.6f)) {
            TextButton(onClick = { parentOpen = !parentOpen }) {
                Text(if (parentOpen) "▾ Для взрослых" else "▸ Для взрослых", fontSize = 17.sp, color = Karavan.Ink)
            }
            if (parentOpen) {
                Text(stop.parent, fontSize = 16.sp, color = Karavan.Ink)
                OutlinedButton(onClick = c::toggleParentStory) {
                    Text(if (c.parentStoryPlaying) "Остановить" else "Подробная история голосом")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(if (c.speaking) "⏹" else "🔊", c::toggleStopStory)
            BigButton(if (last) "Ура! Завершить прогулку" else "Готово! Идём дальше", Modifier.weight(1f), onClick = c::completeStop)
        }
    }
}

/** Фото на память с этой точки: снять камерой или выбрать из галереи. */
@Composable
private fun PhotoPanel(c: KaravanController, index: Int, photo: PhotoHooks) {
    val image = rememberStopPhoto(c, index, PREVIEW_DP)
    Panel {
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
            OutlinedButton(onClick = { photo.takePhoto(index) }) { Text(if (image != null) "Переснять" else "📸 Камера") }
            OutlinedButton(onClick = { photo.pickPhoto(index) }) { Text("🖼 Из галереи") }
        }
    }
}

@Composable
private fun FinaleScreen(c: KaravanController) {
    val result = c.result ?: WalkResult(complete = false, found = emptyList())
    val pulse = rememberInfiniteTransition(label = "badge")
    val scale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "badgeScale"
    )
    ScrollPage {
        if (result.complete) {
            Text("Ура!", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Karavan.Red)
            Sticker(c.route.badge, Modifier.size(260.dp).scale(scale))
            Text(
                "Ты — Юный караванщик!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Karavan.Ink,
                textAlign = TextAlign.Center
            )
            Text(c.route.finale, fontSize = 18.sp, color = Karavan.Ink, textAlign = TextAlign.Center)
        } else {
            Text("Прогулка окончена", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Karavan.Ink, textAlign = TextAlign.Center)
            Sticker("trosha", Modifier.size(200.dp))
            Text(
                "Ничего, продолжим в другой раз! Остальные вещи Троша подождёт.",
                fontSize = 18.sp,
                color = Karavan.Ink,
                textAlign = TextAlign.Center
            )
        }
        Panel {
            if (result.found.isEmpty()) {
                Text("Сегодня ничего не нашли", fontSize = 17.sp, color = Karavan.Muted)
            } else {
                Text("Сегодня нашли:", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.found.forEach { Sticker(c.route.stops[it].sticker, Modifier.size(56.dp)) }
                }
            }
        }
        BigButton("Мой альбом", onClick = c::openAlbum)
        SoftButton("На главную", c::goHome)
    }
}

@Composable
private fun AlbumScreen(c: KaravanController, photo: PhotoHooks) {
    val journey = c.journey
    val version = c.photos.version
    val collage = rememberCollage(c)
    val missing = remember(version, journey) { c.photosMissing() }
    ScrollPage {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = c::goHome) { Text("← Назад", color = Karavan.Muted) }
        }
        Text("Мой альбом", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Karavan.Ink)
        Text("Найдено ${journey.found.size} из ${journey.stopCount}", fontSize = 17.sp, color = Karavan.Muted)
        if (collage != null) {
            Panel {
                Text("🖼 Коллаж прогулки", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Karavan.Ink)
                Image(
                    bitmap = collage,
                    contentDescription = "Коллаж из фото прогулки",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                )
                OutlinedButton(onClick = photo.shareCollage) { Text("Поделиться или сохранить") }
            }
        } else if (journey.found.isNotEmpty()) {
            Panel(color = Karavan.Gold.copy(alpha = 0.3f)) {
                Text(
                    "Добавьте фото ещё к $missing ${findsWord(missing)} — и коллаж соберётся сам.",
                    fontSize = 16.sp,
                    color = Karavan.Ink
                )
            }
        }
        c.route.stops.indices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { i ->
                    val stop = c.route.stops[i]
                    val found = journey.isFound(i)
                    val image = if (found) rememberStopPhoto(c, i, THUMB_DP) else null
                    AlbumCard(Modifier.weight(1f)) {
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
        AlbumCard(Modifier.fillMaxWidth()) {
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

private const val PREVIEW_DP = 360
private const val THUMB_DP = 130
private const val COLLAGE_PX = 1080

/** Знак вопроса, который с пружинкой превращается в наклейку найденной вещи. */
@Composable
private fun FoundItem(sticker: String, revealed: Boolean) {
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
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

@Composable
private fun StickerRow(c: KaravanController, stops: List<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (avatar != null) {
                Sticker(avatar, Modifier.size(44.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(who, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Karavan.Muted)
        }
        Text(text, fontSize = 18.sp, color = Karavan.Ink)
    }
}

@Composable
private fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun Panel(color: Color = Karavan.Card, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun AlbumCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Karavan.Card)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun BigButton(
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Karavan.Red, contentColor = Color.White)
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SoftButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) {
        Text(text, fontSize = 18.sp, color = Karavan.Ink)
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Karavan.Card, contentColor = Karavan.Ink)
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Karavan.Kraft, contentColor = Karavan.Ink)
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoundButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Karavan.Card, contentColor = Karavan.Ink)
    ) {
        Text(label, fontSize = 24.sp)
    }
}

/** «к 1 находке», «к 3 находкам». */
private fun findsWord(n: Int): String = if (n % 10 == 1 && n % 100 != 11) "находке" else "находкам"

private fun pointsWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "точек"
        mod10 == 1 -> "точка"
        mod10 in 2..4 -> "точки"
        else -> "точек"
    }
}
