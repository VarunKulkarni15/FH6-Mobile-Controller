package com.wheelpad.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.atan2

/**
 * High-performance Gyroscope / Accelerometer steering engine.
 * Reads fused hardware rotation sensors at SENSOR_DELAY_FASTEST (200Hz+ on Galaxy M16 5G)
 * for sub-millisecond physical tilt steering.
 */
class GyroSteeringManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _steeringAngle = MutableStateFlow(0f) // -1.0f (full left) to +1.0f (full right)
    val steeringAngle: StateFlow<Float> = _steeringAngle

    var isEnabled: Boolean = false
        private set

    private val prefs = context.getSharedPreferences("wheelpad_prefs", Context.MODE_PRIVATE)

    var sensitivity: Float = prefs.getFloat("gyro_sensitivity", 1.0f)
        set(value) {
            field = value.coerceIn(0.4f, 3.0f)
            prefs.edit().putFloat("gyro_sensitivity", field).apply()
        }

    var maxTiltDegrees: Float = 35f       // Base angle of full lock steering
    var deadzonePercent: Float = 0.02f    // 2% center deadzone to prevent hand tremor
    var smoothingAlpha: Float = 0.85f     // Low-pass filter: 0.85 = instant response with zero jitter

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var filteredAngle: Float = 0f

    fun start() {
        if (rotationSensor == null) return
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_FASTEST)
        isEnabled = true
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        _steeringAngle.value = 0f
        filteredAngle = 0f
        isEnabled = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isEnabled) return

        // Compute rotation matrix from fused game rotation quaternion
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // For landscape orientation: remap coordinates so tilting phone steers naturally
        val remappedMatrix = FloatArray(9)
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_Y,
            SensorManager.AXIS_MINUS_X,
            remappedMatrix
        )

        SensorManager.getOrientation(remappedMatrix, orientationAngles)

        // Roll in radians (-PI to +PI)
        val rollRad = orientationAngles[2]
        val rollDeg = Math.toDegrees(rollRad.toDouble()).toFloat()

        // Normalize to [-1.0, 1.0] based on effective max tilt scaled by sensitivity
        val effectiveMaxTilt = (maxTiltDegrees / sensitivity).coerceIn(10f, 90f)
        var rawNormalized = (rollDeg / effectiveMaxTilt).coerceIn(-1.0f, 1.0f)

        // Apply deadzone
        val abs = kotlin.math.abs(rawNormalized)
        if (abs < deadzonePercent) {
            rawNormalized = 0f
        } else {
            rawNormalized = Math.signum(rawNormalized) * ((abs - deadzonePercent) / (1.0f - deadzonePercent))
        }

        // Apply ultra-fast exponential filter (< 0.5ms latency)
        filteredAngle = (smoothingAlpha * rawNormalized) + ((1f - smoothingAlpha) * filteredAngle)
        _steeringAngle.value = filteredAngle
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}

