package com.example.gpstracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class StepCounterManager(context: Context, private val onStepUpdate: (Int) -> Unit) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var startSteps = -1

    fun start() {
        startSteps = -1 // Reset για τη νέα διαδρομή
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val totalStepsSinceBoot = it.values[0].toInt()

            if (startSteps == -1) {
                startSteps = totalStepsSinceBoot // Κρατάμε την πρώτη μέτρηση ως σημείο μηδέν
            }

            val currentSessionSteps = totalStepsSinceBoot - startSteps
            onStepUpdate(currentSessionSteps)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}