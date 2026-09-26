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

/** Одна карточка коллажа: фото с точки, её название и наклейка найденной вещи. */
class CollageCard(val photo: Bitmap, val title: String, val sticker: Bitmap?)

/**
 * Коллаж «Мои находки с Трошей»: фото-карточки в два столбца на крафтовом фоне,
 * наверху заголовок, внизу значок «Юный караванщик», если он заработан.
 */
object Collage {
    private const val WIDTH = 1080
    private const val MARGIN = 40
    private const val GAP = 40
    private const val HEADER = 250
    private const val CARD_W = (WIDTH - 2 * MARGIN - GAP) / 2
    private const val PHOTO = CARD_W - 40
    private const val CARD_H = PHOTO + 150
    private const val FOOTER = 320

    private val KRAFT = Color.rgb(0xE8, 0xD3, 0xB0)
    private val RED = Color.rgb(0xD2, 0x46, 0x3C)
    private val INK = Color.rgb(0x3B, 0x24, 0x15)
    private val MUTED = Color.rgb(0x7A, 0x61, 0x50)

    fun render(cards: List<CollageCard>, subtitle: String, badge: Bitmap?, trosha: Bitmap?): Bitmap {
        val rows = (cards.size + 1) / 2
        val height = HEADER + rows * (CARD_H + GAP) + FOOTER
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(KRAFT)

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED
            textSize = 76f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Маленький караван", WIDTH / 2f, 120f, title)
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(subtitle, WIDTH / 2f, 190f, sub)

        cards.forEachIndexed { i, card ->
            val left = MARGIN + (i % 2) * (CARD_W + GAP)
            val top = HEADER + (i / 2) * (CARD_H + GAP)
            // Карточки чуть наклонены в разные стороны — как фото, приклеенные в альбом.
            val angle = if (i % 2 == 0) -2.5f else 2f
            canvas.save()
            canvas.rotate(angle, left + CARD_W / 2f, top + CARD_H / 2f)
            drawCard(canvas, card, left.toFloat(), top.toFloat())
            canvas.restore()
        }

        val footerTop = HEADER + rows * (CARD_H + GAP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        trosha?.let { drawFit(canvas, it, RectF(MARGIN.toFloat(), footerTop + 20f, MARGIN + 280f, footerTop + 290f), paint) }
        if (badge != null) {
            drawFit(canvas, badge, RectF(WIDTH - MARGIN - 270f, footerTop + 20f, WIDTH - MARGIN.toFloat(), footerTop + 290f), paint)
            val text = TextPaint(sub).apply { textSize = 44f; color = RED; typeface = Typeface.DEFAULT_BOLD }
            canvas.drawText("Юный караванщик!", WIDTH / 2f, footerTop + 170f, text)
        } else {
            canvas.drawText("Продолжение следует…", WIDTH / 2f, footerTop + 170f, TextPaint(sub).apply { color = MUTED })
        }
        return bitmap
    }

    private fun drawCard(canvas: Canvas, card: CollageCard, left: Float, top: Float) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 60, 30, 10) }
        canvas.drawRoundRect(RectF(left + 6, top + 10, left + CARD_W + 6, top + CARD_H + 10), 18f, 18f, shadow)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(left, top, left + CARD_W, top + CARD_H), 18f, 18f, white)

        // Фото обрезается до квадрата по центру.
        val photo = card.photo
        val side = min(photo.width, photo.height)
        val src = Rect((photo.width - side) / 2, (photo.height - side) / 2, (photo.width + side) / 2, (photo.height + side) / 2)
        val dst = RectF(left + 20, top + 20, left + 20 + PHOTO, top + 20 + PHOTO)
        canvas.drawBitmap(photo, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))

        val caption = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
        }
        val layout = StaticLayout.Builder.obtain(card.title, 0, card.title.length, caption, PHOTO)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(left + 20, top + 20 + PHOTO + 24)
        layout.draw(canvas)
        canvas.restore()

        card.sticker?.let {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            drawFit(canvas, it, RectF(left + CARD_W - 130, top - 30, left + CARD_W + 20, top + 120), paint)
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
