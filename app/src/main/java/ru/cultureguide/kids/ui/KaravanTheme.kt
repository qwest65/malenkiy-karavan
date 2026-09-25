package ru.cultureguide.kids.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Karavan {
    val Sand = Color(0xFFF6EBD6)
    val Kraft = Color(0xFFE8D3B0)
    val Card = Color(0xFFFFFBF3)
    val Red = Color(0xFFD2463C)
    val Blue = Color(0xFF2F6FB5)
    val Sky = Color(0xFF4FA3C7)
    val Green = Color(0xFF3E9C6A)
    val Gold = Color(0xFFF5C542)
    val Ink = Color(0xFF3B2415)
    val Muted = Color(0xFF7A6150)
}

@Composable
fun KaravanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Karavan.Red,
            secondary = Karavan.Sky,
            background = Karavan.Sand,
            surface = Karavan.Card,
            onSurface = Karavan.Ink
        ),
        content = content
    )
}

@Composable
private fun rememberSticker(name: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(name) {
        runCatching {
            context.assets.open("kids/stickers/$name.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull()
    }
}

/**
 * Наклейка из `assets/kids/stickers`. Ненайденная вещь показывается бледной
 * серой тенью со знаком вопроса — сюрприз сохраняется, но видно, что её ещё предстоит найти.
 */
@Composable
fun Sticker(name: String, modifier: Modifier = Modifier, found: Boolean = true) {
    val bitmap = rememberSticker(name)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = if (found) Modifier else Modifier.alpha(0.25f),
                colorFilter = if (found) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            )
        }
        if (!found) {
            Text("?", color = Karavan.Red, fontSize = 40.sp, fontWeight = FontWeight.Black)
        }
    }
}

/** Круглый знак вопроса на месте ещё не найденной вещи. */
@Composable
fun Mystery(modifier: Modifier = Modifier, fontSize: Int = 28) {
    Box(modifier.background(Karavan.Red, CircleShape), contentAlignment = Alignment.Center) {
        Text("?", color = Color.White, fontSize = fontSize.sp, fontWeight = FontWeight.Black)
    }
}
