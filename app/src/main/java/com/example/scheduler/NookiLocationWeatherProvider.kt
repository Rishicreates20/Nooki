package com.example.scheduler

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.domain.model.LocalWeatherCalculator
import com.example.domain.model.LocalWeatherState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Queries the Android Location API ([LocationManager]) and optional ambient hardware sensors
 * ([SensorManager]) to compute a live [LocalWeatherState] that drives Nooki's dynamic weather
 * background and 3D desk-pet island environment.
 */
class NookiLocationWeatherProvider(
    private val context: Context
) {

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    /**
     * Fetches the user's current or last-known location from Android's [LocationManager]
     * and computes the resulting [LocalWeatherState].
     */
    @SuppressLint("MissingPermission")
    suspend fun fetchLocalWeatherState(
        nowMillis: Long = System.currentTimeMillis()
    ): LocalWeatherState {
        val permitted = hasLocationPermission()
        val (ambientTempC, humidityPct) = readOptionalEnvironmentSensors()

        if (!permitted) {
            NookiSchedulerTelemetry.record(
                source = "LocationWeatherAPI",
                message = "Location permission not yet granted; using local timezone solar weather fallback."
            )
            return LocalWeatherCalculator.computeFromLocation(
                latitude = 19.0760,
                longitude = 72.8777,
                altitudeMeters = 14.0,
                nowMillis = nowMillis,
                isFromLiveLocationApi = false,
                hasLocationPermission = false,
                ambientTempSensorC = ambientTempC,
                relativeHumiditySensorPct = humidityPct
            )
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val resolvedLocation = locationManager?.let { manager ->
            getBestAvailableLocation(manager)
        }

        if (resolvedLocation != null) {
            val geocodedLocality = resolveGeocoderLocality(
                latitude = resolvedLocation.latitude,
                longitude = resolvedLocation.longitude
            )
            val weatherState = LocalWeatherCalculator.computeFromLocation(
                latitude = resolvedLocation.latitude,
                longitude = resolvedLocation.longitude,
                altitudeMeters = if (resolvedLocation.hasAltitude()) resolvedLocation.altitude else 18.0,
                nowMillis = nowMillis,
                localityOverride = geocodedLocality,
                isFromLiveLocationApi = true,
                hasLocationPermission = true,
                ambientTempSensorC = ambientTempC,
                relativeHumiditySensorPct = humidityPct
            )
            NookiSchedulerTelemetry.record(
                source = "LocationWeatherAPI",
                message = "Location API fix (${weatherState.formattedCoordinates} • ${weatherState.localityName}) -> ${weatherState.condition.displayName} (${weatherState.temperatureCelsius}°C)"
            )
            return weatherState
        }

        // Permission is granted, but emulator/device GPS provider has no cached fix yet;
        // synthesize a location-permitted state using default device coordinates
        val fallbackState = LocalWeatherCalculator.computeFromLocation(
            latitude = 19.0760,
            longitude = 72.8777,
            altitudeMeters = 14.0,
            nowMillis = nowMillis,
            isFromLiveLocationApi = true,
            hasLocationPermission = true,
            ambientTempSensorC = ambientTempC,
            relativeHumiditySensorPct = humidityPct
        )
        NookiSchedulerTelemetry.record(
            source = "LocationWeatherAPI",
            message = "Location API permitted (passive/default coordinates ${fallbackState.formattedCoordinates}) -> ${fallbackState.condition.displayName} (${fallbackState.temperatureCelsius}°C)"
        )
        return fallbackState
    }

    @SuppressLint("MissingPermission")
    private suspend fun getBestAvailableLocation(locationManager: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        // 1. Check cached lastKnownLocation across all enabled providers first for instant response
        val cachedCandidates = providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        val freshestCached = cachedCandidates.maxByOrNull { it.time }
        if (freshestCached != null) {
            return freshestCached
        }

        // 2. Request a fresh current location fix with a 2.5s timeout so the UI never hangs
        val activeProvider = providers.firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        } ?: return null

        return withTimeoutOrNull(2_500L) {
            suspendCancellableCoroutine { continuation ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancellationSignal = CancellationSignal()
                    continuation.invokeOnCancellation { cancellationSignal.cancel() }
                    runCatching {
                        locationManager.getCurrentLocation(
                            activeProvider,
                            cancellationSignal,
                            context.mainExecutor
                        ) { location ->
                            if (continuation.isActive) {
                                continuation.resume(location)
                            }
                        }
                    }.onFailure {
                        if (continuation.isActive) continuation.resume(null)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            runCatching { locationManager.removeUpdates(this) }
                            if (continuation.isActive) {
                                continuation.resume(location)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    continuation.invokeOnCancellation {
                        runCatching { locationManager.removeUpdates(listener) }
                    }
                    runCatching {
                        @Suppress("DEPRECATION")
                        locationManager.requestSingleUpdate(
                            activeProvider,
                            listener,
                            Looper.getMainLooper()
                        )
                    }.onFailure {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        }
    }

    private fun resolveGeocoderLocality(latitude: Double, longitude: Double): String? {
        return runCatching {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            val first = addresses?.firstOrNull()
            first?.locality ?: first?.subAdminArea ?: first?.adminArea
        }.getOrNull()
    }

    private suspend fun readOptionalEnvironmentSensors(): Pair<Float?, Float?> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return null to null
        val tempSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        val humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        if (tempSensor == null && humiditySensor == null) {
            return null to null
        }

        val tempReading = tempSensor?.let { readSingleSensorValue(sensorManager, it) }
        val humidityReading = humiditySensor?.let { readSingleSensorValue(sensorManager, it) }
        return tempReading to humidityReading
    }

    private suspend fun readSingleSensorValue(
        sensorManager: SensorManager,
        sensor: Sensor
    ): Float? = withTimeoutOrNull(350L) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    sensorManager.unregisterListener(this)
                    val value = event?.values?.firstOrNull()
                    if (continuation.isActive) {
                        continuation.resume(value)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            continuation.invokeOnCancellation {
                sensorManager.unregisterListener(listener)
            }
            val registered = sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
            if (!registered && continuation.isActive) {
                continuation.resume(null)
            }
        }
    }
}
