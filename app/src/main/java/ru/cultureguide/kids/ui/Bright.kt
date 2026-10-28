package ru.cultureguide.kids.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.cultureguide.kids.R

/**
 * Яркое оформление — как у главной веб-версии и сайта проекта (progulka/style.css):
 * небо с облаками, кремовые карточки, оранжевые «объёмные» кнопки, игровые заголовки.
 */
object Bright {
    val SkyTop = Color(0xFF58C3F2)
    val SkyMid = Color(0xFF9FE0FA)
    val Meadow = Color(0xFFE4F6D8)
    val Pale = Color(0xFFF3FBE6)
    val HomeHaze = Color(0xFFD9F4C8)
    val GrassLight = Color(0xFF8FD16A)
    val Grass = Color(0xFF78C255)
    val GrassText = Color(0xFF2D5B1C)
    val Sun = Color(0xFFFFF6A8)

    val Cream = Color(0xFFFFF7D6)
    val GoldCream = Color(0xFFFFF0B0)
    val GoldEdge = Color(0xFFFFB02E)
    val KraftCream = Color(0xFFF6E6C8)

    val Orange = Color(0xFFFF7A2E)
    val OrangeDeep = Color(0xFFF0540F)
    val OrangeEdge = Color(0xFFB13A06)
    val OrangeText = Color(0xFFE2500C)
    val OrangeSoft = Color(0xFFFFD2B0)
    val Title = Color(0xFFFF6A1F)
    val TitleShadow = Color(0xFFC94A0E)
    val Brown = Color(0xFF5A2D14)
    val BrownShadow = Color(0xFF3A1D0B)
    val SubText = Color(0xFF23465C)

    val Blue = Color(0xFF1E9BE0)
    val BlueLight = Color(0xFF2AA8EC)
    val BlueDeep = Color(0xFF1688CF)
    val BlueEdge = Color(0xFF0F6AA6)

    val TagWalk = Color(0xFFFF8A2A)
    val TagStops = Color(0xFFE5533D)

    val Right = Color(0xFF2E9E4F)
    val Wrong = Color(0xFFE5533D)
    /** Рамки вариантов ответа по кругу: оранжевая, голубая, зелёная (и их «тень» снизу). */
    val OptionEdges = listOf(
        Color(0xFFFFD2B0) to Color(0xFFF0C8A5),
        Color(0xFFB9E1F7) to Color(0xFF9CCBE6),
        Color(0xFFC4E8B4) to Color(0xFFA6D391)
    )
}

/** Шрифт Rubik (SIL Open Font License, assets/licenses/Rubik-OFL.txt) — тот же, что в веб-версии. */
val Rubik = FontFamily(
    Font(R.font.rubik_regular, FontWeight.Normal),
    Font(R.font.rubik_semibold, FontWeight.SemiBold),
    Font(R.font.rubik_bold, FontWeight.Bold),
    Font(R.font.rubik_black, FontWeight.Black)
)

val RubikTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Rubik),
        displayMedium = t.displayMedium.copy(fontFamily = Rubik),
        displaySmall = t.displaySmall.copy(fontFamily = Rubik),
        headlineLarge = t.headlineLarge.copy(fontFamily = Rubik),
        headlineMedium = t.headlineMedium.copy(fontFamily = Rubik),
        headlineSmall = t.headlineSmall.copy(fontFamily = Rubik),
        titleLarge = t.titleLarge.copy(fontFamily = Rubik),
        titleMedium = t.titleMedium.copy(fontFamily = Rubik),
        titleSmall = t.titleSmall.copy(fontFamily = Rubik),
        bodyLarge = t.bodyLarge.copy(fontFamily = Rubik),
        bodyMedium = t.bodyMedium.copy(fontFamily = Rubik),
        bodySmall = t.bodySmall.copy(fontFamily = Rubik),
        labelLarge = t.labelLarge.copy(fontFamily = Rubik),
        labelMedium = t.labelMedium.copy(fontFamily = Rubik),
        labelSmall = t.labelSmall.copy(fontFamily = Rubik)
    )
}

/**
 * Небо с облаками. [home] — главный экран: ещё солнце и зелёная лужайка внизу;
 * иначе небо плавно переходит в светло-зелёный.
 */
@Composable
fun SkyBackground(home: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val h = size.height
        val w = size.width
        fun at(y: Dp) = (y.toPx() / h).coerceIn(0f, 1f)
        if (home) {
            val grass = 0.66f
            drawRect(
                Brush.verticalGradient(
                    0f to Bright.SkyTop,
                    grass * 0.6f to Bright.SkyMid,
                    grass to Bright.HomeHaze,
                    endY = h * grass
                ),
                size = Size(w, h * grass)
            )
            drawRect(
                Brush.verticalGradient(0f to Bright.GrassLight, 1f to Bright.Grass, startY = h * grass, endY = h),
                topLeft = Offset(0f, h * grass),
                size = Size(w, h * (1 - grass))
            )
            val sun = Offset(w * 0.93f, h * 0.03f)
            drawCircle(Bright.Sun.copy(alpha = 0.35f), radius = 52.dp.toPx(), center = sun)
            drawCircle(Bright.Sun, radius = 30.dp.toPx(), center = sun)
            cloud(w * 0.14f, h * 0.07f, 110.dp.toPx(), 34.dp.toPx())
            cloud(w * 0.23f, h * 0.04f, 80.dp.toPx(), 26.dp.toPx())
            cloud(w * 0.84f, h * 0.11f, 120.dp.toPx(), 36.dp.toPx())
        } else {
            drawRect(
                Brush.verticalGradient(
                    0f to Bright.SkyTop,
                    at(240.dp) to Bright.SkyMid,
                    at(520.dp) to Bright.Meadow,
                    1f to Bright.Pale
                )
            )
            cloud(w * 0.12f, 60.dp.toPx(), 110.dp.toPx(), 34.dp.toPx())
            cloud(w * 0.86f, 110.dp.toPx(), 120.dp.toPx(), 36.dp.toPx())
        }
    }
}

/** Облако — белый овал с полуосями [rx] и [ry] (как radial-gradient … #fff 60% в CSS). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.cloud(cx: Float, cy: Float, rx: Float, ry: Float) {
    val sx = rx * 0.6f
    val sy = ry * 0.6f
    drawOval(Color.White, topLeft = Offset(cx - sx, cy - sy), size = Size(sx * 2, sy * 2))
}

/**
 * Игровой заголовок: толстые буквы с белой обводкой и тёмной «тенью» снизу, чуть наклонён.
 */
@Composable
fun FunTitle(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 32.sp,
    color: Color = Bright.Title,
    shadow: Color = Bright.TitleShadow,
    stroke: Dp = 2.dp,
    tilt: Float = -2f
) {
    val style = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.Black,
        fontSize = fontSize,
        lineHeight = fontSize * 1.05f,
        textAlign = TextAlign.Center
    )
    val strokePx = with(androidx.compose.ui.platform.LocalDensity.current) { (stroke * 2).toPx() }
    Box(modifier.rotate(tilt), contentAlignment = Alignment.Center) {
        // Обводка рисуется поверх «тени», сама буква — сверху. Все слои одного размера с основным текстом.
        Text(
            text,
            style = style.copy(color = shadow, drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round)),
            modifier = Modifier.matchParentSize().offset(y = 4.dp)
        )
        Text(text, style = style.copy(color = shadow), modifier = Modifier.matchParentSize().offset(y = 4.dp))
        Text(
            text,
            style = style.copy(color = Color.White, drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round)),
            modifier = Modifier.matchParentSize()
        )
        Text(text, style = style.copy(color = color))
    }
}

/**
 * Большая оранжевая кнопка с «объёмом»: снизу тёмная кромка, при нажатии кнопка опускается.
 */
@Composable
fun ChunkyButton(
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    height: Dp = 60.dp,
    fontSize: TextUnit = 20.sp,
    top: Color = Bright.Orange,
    bottom: Color = Bright.OrangeDeep,
    edge: Color = Bright.OrangeEdge,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(50)
    val lift = if (pressed && enabled) 2.dp else EDGE
    Box(modifier.height(height + EDGE).alpha(if (enabled) 1f else 0.45f)) {
        Box(Modifier.fillMaxWidth().height(height).offset(y = EDGE).background(edge, shape))
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = EDGE - lift)
                .background(Brush.verticalGradient(listOf(top, bottom)), shape)
                .clickable(interaction, LocalIndication.current, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

private val EDGE = 5.dp

/** Белая кнопка: «Мой альбом», «На главную», «Собрать свой маршрут». */
@Composable
fun WhiteButton(
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: Dp = 54.dp,
    fontSize: TextUnit = 18.sp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(height)
            .background(Color.White.copy(alpha = 0.85f), shape)
            .border(3.dp, Color.White, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Bright.Brown, fontSize = fontSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** Белая кнопка с оранжевой рамкой и текстом: «Изменить». */
@Composable
fun OutButton(text: String, modifier: Modifier = Modifier, height: Dp = 60.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(height)
            .background(Color.White, shape)
            .border(3.dp, Bright.OrangeSoft, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Bright.OrangeText, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

/** Белая «таблетка» с тенью: «← Назад», «Пропустить точку», «Показать находку». */
@Composable
fun PillLink(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Bright.Brown,
    fontSize: TextUnit = 16.sp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .shadow(4.dp, shape)
            .background(Color.White.copy(alpha = 0.92f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Голубая плашка «Точка 2 из 9», «Найдено 3 из 9». */
@Composable
fun StepPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier.background(Bright.Blue, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 5.dp)
    )
}

/** Ярлычки «👣 1,7 км · 📍 9 точек · ⏱ 1,5 часа». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanTags(walk: String?, stops: String, time: String, modifier: Modifier = Modifier, center: Boolean = false) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, if (center) Alignment.CenterHorizontally else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        walk?.let { Tag("👣 $it", Bright.TagWalk) }
        Tag("📍 $stops", Bright.TagStops)
        Tag("⏱ $time", Bright.Blue)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        modifier = Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 6.dp)
    )
}

enum class PanelStyle { Cream, Gold, Kraft }

/** Карточка: кремовая с мягкой тенью; «золотая» — с пунктирной рамкой (вопрос, грамота). */
@Composable
fun BrightPanel(
    style: PanelStyle = PanelStyle.Cream,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val base = when (style) {
        PanelStyle.Cream -> modifier.shadow(6.dp, shape, ambientColor = Bright.Brown, spotColor = Bright.Brown).background(Bright.Cream, shape)
        PanelStyle.Kraft -> modifier.background(Bright.KraftCream, shape)
        PanelStyle.Gold -> modifier.background(Bright.GoldCream, shape).dashedBorder(3.dp, Bright.GoldEdge, 24.dp)
    }
    Column(base.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Пунктирная рамка по краю закруглённой карточки. */
fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx() - w / 2),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 3, w * 2)))
    )
}
