package ru.cultureguide.kids.map

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.abs

/**
 * Компас: куда направлен верх телефона (или, если телефон держат вертикально, его камера),
 * в градусах от географического севера. Нужен, пока стоим: при ходьбе направление даёт GPS.
 */
class HeadingSensor(context: Context, private val onHeading: (Double) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)
    private var declination = 0f
    private var declinationSet = false
    private var lastAtMs = 0L

    fun start() {
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    /** Магнитный север отличается от географического; поправку считаем один раз по месту. */
    fun setLocation(lat: Double, lon: Double) {
        if (declinationSet) return
        declination = GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
        declinationSet = true
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAtMs < MIN_INTERVAL_MS) return
        lastAtMs = now
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        // Телефон в руке почти вертикально — направление считаем по оси «из экрана назад».
        val matrix = if (abs(orientation[1]) > PI / 4) {
            SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
            remapped
        } else {
            rotation
        }
        SensorManager.getOrientation(matrix, orientation)
        onHeading(Math.toDegrees(orientation[0].toDouble()) + declination)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val MIN_INTERVAL_MS = 80L
    }
}
