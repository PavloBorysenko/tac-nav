package com.tacnav.mortar.sensors

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import com.tacnav.mortar.domain.CalibrationMath
import com.tacnav.mortar.domain.Geodesy
import com.tacnav.mortar.security.CalibrationState

data class LivePose(
    val magneticHeading: Double? = null,
    val trueHeading: Double? = null,
    val elevation: Double? = null,
    val calibratedHeading: Double? = null,
    val calibratedElevation: Double? = null,
    val quality: String = "Unavailable",
    val compassReliable: Boolean = false,
)

class PhonePose(
    private val sensorManager: SensorManager,
) : SensorEventListener {
    @Volatile
    var pose: LivePose = LivePose()
        private set

    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)
    private var location: Location? = null
    private var calibration = CalibrationState()

    fun start() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun updateLocation(value: Location?) {
        location = value
    }

    fun updateCalibration(value: CalibrationState) {
        calibration = value
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        val magnetic = Geodesy.magneticHeadingFromRotation(rotation)
        val pitch = Math.toDegrees(orientation[1].toDouble())
        // Tube elevation from horizontal: flat is 0°, lifting the top raises the angle,
        // dipping the top below the horizon is negative. Do not clamp before offset.
        val elevation = CalibrationMath.elevationFromPitch(pitch)
        val accuracy = event.accuracy
        val quality = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
            else -> "Unreliable"
        }
        val current = location
        val declination = current?.let {
            GeomagneticField(
                it.latitude.toFloat(),
                it.longitude.toFloat(),
                it.altitude.toFloat(),
                it.time,
            ).declination.toDouble()
        }
        val trueHeading = Geodesy.trueHeading(magnetic, declination)
        pose = LivePose(
            magneticHeading = magnetic.takeIf { it.isFinite() },
            trueHeading = trueHeading,
            elevation = elevation,
            calibratedHeading = trueHeading?.let {
                CalibrationMath.applyHeadingOffset(it, calibration.directionOffsetDegrees)
            },
            calibratedElevation = CalibrationMath.applyElevationOffset(
                elevation,
                calibration.elevationOffsetDegrees,
            ),
            quality = quality,
            compassReliable = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_LOW,
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
