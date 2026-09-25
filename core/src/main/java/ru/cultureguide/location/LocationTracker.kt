package ru.cultureguide.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

/**
 * Подписка на GPS и сетевой провайдер одновременно. Сетевой фикс принимается,
 * только если свежего GPS нет или он точнее, — так позиция не «прыгает».
 */
class LocationTracker(
    private val context: Context,
    private val onLocation: (Location) -> Unit
) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var running = false
    var lastLocation: Location? = null
        private set

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (isBetter(location, lastLocation)) {
                lastLocation = location
                onLocation(location)
            }
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Deprecated in Android API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isProviderEnabled(): Boolean =
        PROVIDERS.any { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    /** @return false, если нет разрешения или провайдеров. */
    fun start(): Boolean {
        if (running) return true
        if (!hasPermission()) return false
        var subscribed = false
        for (provider in PROVIDERS) {
            try {
                if (!manager.allProviders.contains(provider)) continue
                manager.requestLocationUpdates(provider, MIN_TIME_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper())
                subscribed = true
                manager.getLastKnownLocation(provider)?.let { listener.onLocationChanged(it) }
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }
        running = subscribed
        return subscribed
    }

    fun stop() {
        if (!running) return
        manager.removeUpdates(listener)
        running = false
    }

    private fun isBetter(candidate: Location, current: Location?): Boolean {
        if (current == null) return true
        val age = candidate.time - current.time
        if (age > STALE_MS) return true
        if (age < -STALE_MS) return false
        if (candidate.provider == current.provider) return true
        return candidate.accuracy <= current.accuracy
    }

    private companion object {
        val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        const val MIN_TIME_MS = 2_000L
        const val MIN_DISTANCE_M = 2f
        const val STALE_MS = 10_000L
    }
}
