package ru.cultureguide.kids.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val FESTIVE = listOf(
    Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
    Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFEC407A), Color(0xFF26C6DA)
)

private class Confetti(
    val x: Float, val delay: Float, val speed: Float, val sway: Float, val spin: Float,
    val color: Color, val w: Float, val h: Float, val round: Boolean
)

private class Balloon(val x: Float, val delay: Float, val speed: Float, val radius: Float, val color: Color, val phase: Float)

private class Burst(val x: Float, val y: Float, val at: Float, val color: Color)

/**
 * Праздничный показ находки: экран затемняется, за вещью вращаются золотые лучи, вещь
 * с пружинкой вырастает почти на весь экран, сверху сыплются конфетти, снизу летят шарики.
 * Затем вещь уменьшается и по дуге улетает в [flyTo] — рюкзак в углу экрана.
 * Без [flyTo] (финал прогулки) вещь остаётся по центру, а потом всё плавно гаснет.
 * Всё идёт само; нажатия во время праздника ни на что не влияют.
 *
 * @param flyTo центр рюкзака в координатах экрана
 * @param fireworks салют из нескольких вспышек — для значка за весь маршрут
 * @param onLanded вещь долетела до рюкзака
 * @param onDone праздник закончился, можно убирать
 */
@Composable
fun Celebration(
    sticker: String,
    title: String,
    subtitle: String,
    flyTo: Offset?,
    fireworks: Boolean = false,
    onLanded: () -> Unit = {},
    onDone: () -> Unit
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var time by remember { mutableFloatStateOf(0f) }
    val appear = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    val random = remember { Random(sticker.hashCode()) }
    val confetti = remember {
        List(if (fireworks) 140 else 110) {
            Confetti(
                x = random.nextFloat(), delay = random.nextFloat() * 1.6f, speed = 0.22f + random.nextFloat() * 0.25f,
                sway = 10f + random.nextFloat() * 24f, spin = 90f + random.nextFloat() * 360f,
                color = FESTIVE[random.nextInt(FESTIVE.size)], w = 7f + random.nextFloat() * 7f,
                h = 10f + random.nextFloat() * 10f, round = random.nextInt(4) == 0
            )
        }
    }
    val balloons = remember {
        List(if (fireworks) 14 else 10) {
            Balloon(
                x = 0.06f + random.nextFloat() * 0.88f, delay = random.nextFloat() * 1.2f,
                speed = 0.14f + random.nextFloat() * 0.12f, radius = 22f + random.nextFloat() * 14f,
                color = FESTIVE[random.nextInt(FESTIVE.size)], phase = random.nextFloat() * 6f
            )
        }
    }
    val bursts = remember {
        if (!fireworks) emptyList() else List(7) {
            Burst(0.15f + random.nextFloat() * 0.7f, 0.12f + random.nextFloat() * 0.35f, 0.2f + it * 0.75f, FESTIVE[random.nextInt(FESTIVE.size)])
        }
    }

    // Часы праздника: всё рисуется от времени с начала, без лишних перерисовок экрана.
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    LaunchedEffect(Unit) {
        vibrate(context)
        val grow = async { appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        delay(if (fireworks) FINALE_HOLD_MS else HOLD_MS)
        grow.await()
        leave.animateTo(1f, tween(if (flyTo != null) FLY_MS else FADE_MS, easing = FastOutSlowInEasing))
        if (flyTo != null) onLanded()
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        val fade = 1f - leave.value
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.55f * fade * appear.value.coerceIn(0f, 1f)))
            val center = Offset(this.size.width / 2f, this.size.height * CENTER_Y)
            drawRays(center, time, 0.38f * fade * appear.value.coerceIn(0f, 1f))
            bursts.forEach { drawBurst(it, time, fade) }
            balloons.forEach { drawBalloon(it, time, fade) }
            confetti.forEach { drawConfetti(it, time, fade) }
        }

        // Вещь: из центра растёт с пружинкой, потом по дуге улетает в рюкзак, уменьшаясь.
        val stickerPx = with(density) { STICKER_DP.dp.toPx() }
        val center = Offset(size.width / 2f, size.height * CENTER_Y)
        val target = flyTo ?: center
        val t = leave.value
        val arc = size.height * 0.18f * sin(PI.toFloat() * t)
        val pos = Offset(center.x + (target.x - center.x) * t, center.y + (target.y - center.y) * t - arc)
        val scale = appear.value * (if (flyTo != null) 1f - 0.88f * t else 1f)
        Box(
            Modifier
                .offset { IntOffset((pos.x - stickerPx / 2).roundToInt(), (pos.y - stickerPx / 2).roundToInt()) }
                .size(STICKER_DP.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = sin(time * 3f) * 6f * (1f - t)
                    alpha = if (flyTo != null) 1f else fade
                }
        ) {
            Sticker(sticker, Modifier.fillMaxSize())
        }

        Column(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (center.y + stickerPx / 2 + with(density) { 12.dp.toPx() }).roundToInt()) }
                .graphicsLayer { alpha = fade * appear.value.coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
            Text(subtitle, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Karavan.Gold, textAlign = TextAlign.Center)
        }
    }
}

private fun DrawScope.drawRays(center: Offset, time: Float, alpha: Float) {
    if (alpha <= 0f) return
    val length = size.maxDimension
    rotate(time * 18f, center) {
        for (i in 0 until RAYS) {
            val a1 = (i * 2 * PI / RAYS).toFloat()
            val a2 = a1 + (PI / RAYS).toFloat()
            val ray = Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + cos(a1) * length, center.y + sin(a1) * length)
                lineTo(center.x + cos(a2) * length, center.y + sin(a2) * length)
                close()
            }
            drawPath(ray, Karavan.Gold.copy(alpha = alpha))
        }
    }
}

private fun DrawScope.drawConfetti(p: Confetti, time: Float, fade: Float) {
    val t = time - p.delay
    if (t < 0f || fade <= 0f) return
    val y = -40f + p.speed * size.height * t
    if (y > size.height + 40f) return
    val x = p.x * size.width + sin(t * 3f + p.x * 10f) * p.sway * density
    val color = p.color.copy(alpha = fade)
    if (p.round) {
        drawCircle(color, radius = p.w * density * 0.5f, center = Offset(x, y))
    } else {
        rotate(p.spin * t, Offset(x, y)) {
            drawRect(color, topLeft = Offset(x - p.w * density / 2, y - p.h * density / 2), size = Size(p.w * density, p.h * density))
        }
    }
}

private fun DrawScope.drawBalloon(b: Balloon, time: Float, fade: Float) {
    val t = time - b.delay
    if (t < 0f || fade <= 0f) return
    val r = b.radius * density
    val y = size.height + r * 2 - b.speed * size.height * t
    if (y < -r * 4) return
    val x = b.x * size.width + sin(t * 1.6f + b.phase) * 14f * density
    val color = b.color.copy(alpha = 0.92f * fade)
    drawOval(color, topLeft = Offset(x - r, y - r * 1.2f), size = Size(r * 2, r * 2.4f))
    drawOval(Color.White.copy(alpha = 0.35f * fade), topLeft = Offset(x - r * 0.55f, y - r * 0.85f), size = Size(r * 0.5f, r * 0.7f))
    val knot = Path().apply {
        moveTo(x, y + r * 1.15f)
        lineTo(x - r * 0.18f, y + r * 1.4f)
        lineTo(x + r * 0.18f, y + r * 1.4f)
        close()
    }
    drawPath(knot, color)
    val string = Path().apply {
        moveTo(x, y + r * 1.4f)
        for (k in 1..6) lineTo(x + sin(k * 1.3f + t * 4f) * r * 0.15f, y + r * 1.4f + k * r * 0.35f)
    }
    drawPath(string, Color.White.copy(alpha = 0.7f * fade), style = Stroke(width = 1.5f * density))
}

private fun DrawScope.drawBurst(b: Burst, time: Float, fade: Float) {
    val t = time - b.at
    if (t < 0f || t > BURST_S || fade <= 0f) return
    val progress = t / BURST_S
    val reach = 130f * density * (1f - (1f - progress) * (1f - progress))
    val alpha = (1f - progress) * fade
    val center = Offset(b.x * size.width, b.y * size.height)
    for (k in 0 until SPARKS) {
        val a = (k * 2 * PI / SPARKS).toFloat()
        val p = Offset(center.x + cos(a) * reach, center.y + sin(a) * reach + 40f * density * progress * progress)
        drawCircle(b.color.copy(alpha = alpha), radius = 4f * density, center = p)
        drawCircle(Color.White.copy(alpha = alpha * 0.8f), radius = 1.8f * density, center = p)
    }
}

/** Короткая вибрация в момент находки. */
private fun vibrate(context: Context) {
    try {
        context.getSystemService(Vibrator::class.java)
            ?.takeIf { it.hasVibrator() }
            ?.vibrate(VibrationEffect.createOneShot(VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: SecurityException) {
    }
}

private const val CENTER_Y = 0.40f
private const val STICKER_DP = 260
private const val RAYS = 14
private const val SPARKS = 24
private const val BURST_S = 1.3f
private const val HOLD_MS = 2_600L
private const val FINALE_HOLD_MS = 5_200L
private const val FLY_MS = 900
private const val FADE_MS = 700
private const val VIBRATE_MS = 160L
