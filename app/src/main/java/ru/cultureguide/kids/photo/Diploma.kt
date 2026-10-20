package ru.cultureguide.kids.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Грамота «Юный караванщик» на лист A4 — за прогулку, пройденную до конца: имя ребёнка,
 * маршрут, значок, найденные вещи, дата и подпись Троши. Тот же макет, что в веб-версии
 * (renderDiploma в app.js).
 */
object Diploma {
    private const val WIDTH = 1654
    private const val HEIGHT = 2339
    private const val MARGIN = 70f

    private val KRAFT = Color.rgb(0xE8, 0xD3, 0xB0)
    private val PAPER = Color.rgb(0xFD, 0xF6, 0xE7)
    private val RED = Color.rgb(0xD2, 0x46, 0x3C)
    private val GOLD = Color.rgb(0xE0, 0xA8, 0x3A)
    private val INK = Color.rgb(0x3B, 0x24, 0x15)
    private val MUTED = Color.rgb(0x7A, 0x61, 0x50)

    fun render(name: String, routeTitle: String, date: Date, badge: Bitmap?, trosha: Bitmap?, items: List<Bitmap>): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(KRAFT)

        // Крафтовый лист с двойной рамкой — как в альбоме.
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER }
        canvas.drawRoundRect(RectF(MARGIN, MARGIN, WIDTH - MARGIN, HEIGHT - MARGIN), 40f, 40f, fill)
        frame(canvas, MARGIN + 22, RED, 10f)
        frame(canvas, MARGIN + 46, GOLD, 4f)

        val cx = WIDTH / 2f
        canvas.drawText("ГРАМОТА", cx, 360f, text(170f, RED, bold = true))
        canvas.drawText("Маленький караван · прогулка по Троицку", cx, 450f, text(54f, MUTED))
        canvas.drawText("награждается", cx, 600f, text(60f, INK))
        // Длинное имя уменьшается, чтобы поместиться в строку.
        val namePaint = text(120f, INK, bold = true)
        val maxName = WIDTH - 2 * MARGIN - 220
        while (namePaint.measureText(name) > maxName && namePaint.textSize > 28f) namePaint.textSize -= 4f
        canvas.drawText(name, cx, 750f, namePaint)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; strokeWidth = 4f }
        canvas.drawLine(320f, 790f, WIDTH - 320f, 790f, line)

        val reason = "за прогулку «$routeTitle» по историческому центру Троицка и помощь верблюжонку Троше в поисках пропавших вещей"
        val reasonWidth = (WIDTH - 2 * MARGIN - 260).toInt()
        val layout = StaticLayout.Builder.obtain(reason, 0, reason.length, text(52f, INK, align = Paint.Align.LEFT), reasonWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(4)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate((WIDTH - reasonWidth) / 2f, 830f)
        layout.draw(canvas)
        canvas.restore()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        badge?.let { drawFit(canvas, it, RectF((WIDTH - 520) / 2f, 1110f, (WIDTH + 520) / 2f, 1630f), paint) }
        canvas.drawText("Юный караванщик", cx, 1750f, text(96f, RED, bold = true))

        // Найденные вещи — в ряд по центру.
        if (items.isNotEmpty()) {
            val size = min(120f, (WIDTH - 2 * MARGIN - 200) / items.size - 12)
            val rowWidth = items.size * (size + 12) - 12
            items.forEachIndexed { k, item ->
                val left = (WIDTH - rowWidth) / 2 + k * (size + 12)
                drawFit(canvas, item, RectF(left, 1790f, left + size, 1790f + size), paint)
            }
        }

        // Внизу: дата слева, Троша с подписью справа — внутри рамки.
        val footY = HEIGHT - MARGIN - 110
        val dateText = SimpleDateFormat("d MMMM yyyy 'г.'", Locale("ru")).format(date)
        canvas.drawText(dateText, MARGIN + 110, footY, text(46f, INK, align = Paint.Align.LEFT))
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; strokeWidth = 3f }
        canvas.drawLine(MARGIN + 110, footY + 20, MARGIN + 560, footY + 20, rule)
        trosha?.let { drawFit(canvas, it, RectF(WIDTH - MARGIN - 330, footY - 230, WIDTH - MARGIN - 140, footY - 40), paint) }
        canvas.drawText("Верблюжонок Троша", WIDTH - MARGIN - 110, footY + 10, text(42f, INK, bold = true, align = Paint.Align.RIGHT))
        return bitmap
    }

    private fun frame(canvas: Canvas, inset: Float, color: Int, width: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = width }
        canvas.drawRoundRect(RectF(inset, inset, WIDTH - inset, HEIGHT - inset), 40f, 40f, paint)
    }

    private fun text(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = align
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
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
