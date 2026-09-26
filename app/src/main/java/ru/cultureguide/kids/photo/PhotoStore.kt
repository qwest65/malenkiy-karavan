package ru.cultureguide.kids.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Фото на память с точек маршрута: по одному на точку, в `files/photos/stop_<n>.jpg`.
 * Снимок уменьшается до [MAX_SIDE_PX] и поворачивается по EXIF, чтобы не занимать лишнего места.
 */
class PhotoStore(context: Context) {
    private val resolver = context.contentResolver
    private val dir = File(context.filesDir, "photos").apply { mkdirs() }
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    val collageFile = File(dir, "collage.jpg")

    /** Растёт при каждом изменении фото или коллажа — экраны перечитывают картинки. */
    var version by mutableIntStateOf(0)
        private set

    fun file(stop: Int) = File(dir, "stop_$stop.jpg")

    fun has(stop: Int): Boolean = file(stop).exists()

    /** Сохраняет фото с камеры или из галереи для точки [stop]; [onDone] — в главном потоке. */
    fun import(stop: Int, source: Uri, onDone: (Boolean) -> Unit) {
        executor.execute {
            val ok = try {
                val bitmap = decode(source) ?: throw IOException("не удалось прочитать фото")
                val target = File(dir, "stop_$stop.tmp")
                target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                bitmap.recycle()
                target.renameTo(file(stop))
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
            main.post {
                if (ok) version++
                onDone(ok)
            }
        }
    }

    /** Фото точки, уменьшенное так, чтобы большая сторона была не больше [maxSide]. */
    fun load(stop: Int, maxSide: Int): Bitmap? = loadFile(file(stop), maxSide)

    fun loadCollage(maxSide: Int): Bitmap? = loadFile(collageFile, maxSide)

    /** Собирает коллаж в фоне и сохраняет его в [collageFile]. */
    fun saveCollage(render: () -> Bitmap?) {
        executor.execute {
            val bitmap = render()
            val ok = bitmap != null && runCatching {
                collageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            }.isSuccess
            bitmap?.recycle()
            if (ok) main.post { version++ }
        }
    }

    fun deleteCollage() {
        if (collageFile.delete()) version++
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        version++
    }

    fun shutdown() {
        executor.shutdown()
    }

    private fun loadFile(file: File, maxSide: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
        return BitmapFactory.decodeFile(file.path, options)
    }

    private fun decode(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE_PX)
        }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = MAX_SIDE_PX.toFloat() / max(raw.width, raw.height)
        if (rotation == 0f && scale >= 1f) return raw
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            postRotate(rotation)
        }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { if (it !== raw) raw.recycle() }
    }

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    companion object {
        const val MAX_SIDE_PX = 1600
        private const val JPEG_QUALITY = 88

        /** Сторона превью в пикселях для экрана телефона. */
        fun previewSide(density: Float, dp: Int): Int = (dp * density).roundToInt()
    }
}
