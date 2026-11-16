package com.example.presentation.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat

enum class StepSensorStatus { COUNTING, NEEDS_PERMISSION, NO_SENSOR }

object StepSensor {
    fun hasSensor(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    fun status(context: Context): StepSensorStatus = when {
        !hasSensor(context) -> StepSensorStatus.NO_SENSOR
        !hasPermission(context) -> StepSensorStatus.NEEDS_PERMISSION
        else -> StepSensorStatus.COUNTING
    }
}

/**
 * Listens to the phone's built-in step counter while the app is open and passes each cumulative
 * reading on. Works offline; no Google Fit or internet needed.
 */
@Composable
fun StepCounterEffect(context: Context, enabled: Boolean, onReading: (Long) -> Unit) {
    val latest = rememberUpdatedState(onReading)
    DisposableEffect(enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (!enabled || sm == null || sensor == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    event.values.firstOrNull()?.let { latest.value(it.toLong()) }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            onDispose { sm.unregisterListener(listener) }
        }
    }
}
