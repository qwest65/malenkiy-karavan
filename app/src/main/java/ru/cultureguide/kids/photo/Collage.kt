package ru.cultureguide.kids.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.min
import kotlin.math.roundToInt

/** Одна карточка коллажа: фото с точки, её название и наклейка найденной вещи. */
class CollageCard(val photo: Bitmap, val title: String, val sticker: Bitmap?)

/**
 * Коллаж «Мои находки с Трошей» на листе A4 (вертикальном): фото-карточки сеткой на крафтовом
 * фоне, наверху заголовок, внизу значок «Юный караванщик», если он заработан. Печатается на одну
 * страницу. Тот же макет — в веб-версии (renderCollage в app.js).
 */
object Collage {
    private const val WIDTH = 1654
    private const val HEIGHT = 2339
    private const val MARGIN = 70
    private const val HEADER = 300
    private const val FOOTER = 340
    private const val GAP = 40
    private const val PAD = 22

    private val KRAFT = Color.rgb(0xE8, 0xD3, 0xB0)
    private val RED = Color.rgb(0xD2, 0x46, 0x3C)
    private val INK = Color.rgb(0x3B, 0x24, 0x15)
    private val MUTED = Color.rgb(0x7A, 0x61, 0x50)

    private class Grid(val cols: Int, val rows: Int, val cellW: Float, val cellH: Float, val photo: Int)

    private fun captionSize(photo: Int) = (photo * 0.08f).roundToInt().coerceIn(28, 54)
    private fun captionHeight(photo: Int) = (captionSize(photo) * 2.7f).roundToInt()

    /** Из 1–4 столбцов — где фото крупнее всего, а из почти равных — с большим числом рядов. */
    private fun grid(n: Int): Grid {
        val gw = WIDTH - 2 * MARGIN
        val gh = HEIGHT - HEADER - FOOTER
        val options = (1..min(4, n)).map { cols ->
            val rows = (n + cols - 1) / cols
            val cellW = (gw - (cols - 1) * GAP) / cols.toFloat()
            val cellH = (gh - (rows - 1) * GAP) / rows.toFloat()
            var photo = (min(cellW, cellH) - 2 * PAD).toInt()
            while (photo > 0 && photo + 2 * PAD + captionHeight(photo) > cellH) photo -= 2
            Grid(cols, rows, cellW, cellH, photo)
        }
        val top = options.maxOf { it.photo }
        return options.filter { it.photo >= top * 0.9 }
            .sortedWith(compareByDescending<Grid> { it.rows }.thenByDescending { it.photo })
            .first()
    }

    fun render(cards: List<CollageCard>, subtitle: String, badge: Bitmap?, trosha: Bitmap?): Bitmap {
        val grid = grid(cards.size)
        val photo = grid.photo
        val cardW = photo + 2 * PAD
        val cardH = photo + 2 * PAD + captionHeight(photo)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(KRAFT)

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED
            textSize = 104f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Маленький караван", WIDTH / 2f, 150f, title)
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = 54f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(subtitle, WIDTH / 2f, 238f, sub)

        cards.forEachIndexed { i, card ->
            val row = i / grid.cols
            val col = i % grid.cols
            // Неполный последний ряд — по центру.
            val inRow = min(grid.cols, cards.size - row * grid.cols)
            val rowLeft = MARGIN + (grid.cols - inRow) * (grid.cellW + GAP) / 2
            val left = rowLeft + col * (grid.cellW + GAP) + (grid.cellW - cardW) / 2
            val top = HEADER + row * (grid.cellH + GAP) + (grid.cellH - cardH) / 2
            // Карточки чуть наклонены в разные стороны — как фото, приклеенные в альбом.
            val angle = if (i % 2 == 0) -2.5f else 2f
            canvas.save()
            canvas.rotate(angle, left + cardW / 2f, top + cardH / 2f)
            drawCard(canvas, card, left, top, photo)
            canvas.restore()
        }

        val footerTop = (HEIGHT - FOOTER).toFloat()
        val art = FOOTER - 40f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        trosha?.let { drawFit(canvas, it, RectF(MARGIN.toFloat(), footerTop + 20f, MARGIN + art, footerTop + 20f + art), paint) }
        val textY = footerTop + FOOTER / 2f + 20f
        if (badge != null) {
            drawFit(canvas, badge, RectF(WIDTH - MARGIN - art, footerTop + 20f, WIDTH - MARGIN.toFloat(), footerTop + 20f + art), paint)
            val text = TextPaint(sub).apply { textSize = 60f; color = RED; typeface = Typeface.DEFAULT_BOLD }
            canvas.drawText("Юный караванщик!", WIDTH / 2f, textY, text)
        } else {
            canvas.drawText("Продолжение следует…", WIDTH / 2f, textY, TextPaint(sub).apply { color = MUTED })
        }
        return bitmap
    }

    private fun drawCard(canvas: Canvas, card: CollageCard, left: Float, top: Float, photoSide: Int) {
        val cardW = photoSide + 2f * PAD
        val cardH = photoSide + 2f * PAD + captionHeight(photoSide)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 60, 30, 10) }
        canvas.drawRoundRect(RectF(left + 6, top + 10, left + cardW + 6, top + cardH + 10), 18f, 18f, shadow)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(left, top, left + cardW, top + cardH), 18f, 18f, white)

        // Фото обрезается до квадрата по центру.
        val photo = card.photo
        val side = min(photo.width, photo.height)
        val src = Rect((photo.width - side) / 2, (photo.height - side) / 2, (photo.width + side) / 2, (photo.height + side) / 2)
        val dst = RectF(left + PAD, top + PAD, left + PAD + photoSide, top + PAD + photoSide)
        canvas.drawBitmap(photo, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))

        val fontSize = captionSize(photoSide).toFloat()
        val caption = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = fontSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val layout = StaticLayout.Builder.obtain(card.title, 0, card.title.length, caption, photoSide)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(left + PAD, top + PAD + photoSide + fontSize * 0.35f)
        layout.draw(canvas)
        canvas.restore()

        card.sticker?.let {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val size = cardW * 0.32f
            val l = left + cardW - size * 0.85f
            val t = top - size * 0.2f
            drawFit(canvas, it, RectF(l, t, l + size, t + size), paint)
        }
    }

    /** Рисует картинку целиком внутри [box], сохраняя пропорции. */
    private fun drawFit(canvas: Canvas, image: Bitmap, box: RectF, paint: Paint) {
        val scale = min(box.width() / image.width, box.height() / image.height)
        val w = image.width * scale
        val h = image.height * scale
        val l = box.left + (box.width() - w) / 2
        val t = box.top + (box.height() - h) / 2
        canvas.drawBitmap(image, null, RectF(l, t, l + w, t + h), paint)
    }
}
