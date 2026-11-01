package com.tacnav.beacon.session

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import com.tacnav.beacon.domain.Geodesy

data class CompassPose(
    val heading: Double? = null,
    val reliable: Boolean = false,
)

/** True bearing of the phone top, read once when deployment starts. */
class Compass(context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val rotation = FloatArray(9)

    @Volatile
    private var location: Location? = null

    @Volatile
    var pose: CompassPose = CompassPose()
        private set

    fun start() {
        sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensors.unregisterListener(this)
    }

    fun updateLocation(value: Location) {
        location = value
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val declination = location?.let {
            GeomagneticField(
                it.latitude.toFloat(),
                it.longitude.toFloat(),
                it.altitude.toFloat(),
                it.time,
            ).declination.toDouble()
        } ?: 0.0
        pose = CompassPose(
            heading = Geodesy.normalizeBearing(Geodesy.headingFromRotation(rotation) + declination),
            reliable = event.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
