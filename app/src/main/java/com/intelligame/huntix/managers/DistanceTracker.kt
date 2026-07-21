package com.intelligame.huntix.managers

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

object DistanceTracker : SensorEventListener {

    private const val PREFS = "distance_tracker_v1"
    private const val KEY_TOTAL_KM = "total_km"
    private const val KEY_SESSION_STEPS = "session_steps"
    private const val KEY_LISTENING = "is_listening"

    private const val STEP_LENGTH_M = 0.714f
    private const val KM_PER_STEP = STEP_LENGTH_M / 1000f

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var initialSteps = -1f
    private var ctx: Context? = null

    fun startListening(context: Context, callback: (Float) -> Unit) {
        if (isListening(context)) return
        ctx = context.applicationContext
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor == null) {
            Log.w("DistanceTracker", "Step counter sensor not available")
            return
        }
        initialSteps = -1f
        sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        prefs(context).edit().putBoolean(KEY_LISTENING, true).apply()
        Log.d("DistanceTracker", "Started listening for steps")
    }

    fun stopListening(context: Context) {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        stepSensor = null
        initialSteps = -1f
        prefs(context).edit().putBoolean(KEY_LISTENING, false).apply()
        Log.d("DistanceTracker", "Stopped listening")
    }

    fun isListening(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LISTENING, false)

    fun getTotalKm(context: Context): Float =
        prefs(context).getFloat(KEY_TOTAL_KM, 0f)

    fun getSessionSteps(context: Context): Int =
        prefs(context).getInt(KEY_SESSION_STEPS, 0)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return

        val totalSteps = event.values[0]
        if (initialSteps < 0) {
            initialSteps = totalSteps
            return
        }

        val deltaSteps = totalSteps - initialSteps
        if (deltaSteps <= 0) return

        val deltaKm = deltaSteps * KM_PER_STEP

        val context = ctx ?: return
        val p = prefs(context)
        val prevTotal = p.getFloat(KEY_TOTAL_KM, 0f)
        p.edit()
            .putFloat(KEY_TOTAL_KM, prevTotal + deltaKm)
            .putInt(KEY_SESSION_STEPS, p.getInt(KEY_SESSION_STEPS, 0) + deltaSteps.toInt())
            .apply()

        initialSteps = totalSteps

        if (deltaKm > 0.001f) {
            val readyEggs = IncubatorManager.addDistanceToIncubators(context, deltaKm)
            if (readyEggs.isNotEmpty()) {
                Log.d("DistanceTracker", "Eggs ready to hatch: $readyEggs")
            }
            try {
                BuddyManager.addWalkingDistance(context, deltaKm)
            } catch (_: Exception) {}
            val totalKm = p.getFloat(KEY_TOTAL_KM, 0f)
            if (totalKm >= 2f) ResearchTaskManager.trackProgress(context, "walk_2km")
            if (totalKm >= 10f) ResearchTaskManager.trackProgress(context, "walk_10km")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun forceSyncSteps(context: Context): Float {
        val steps = prefs(context).getInt(KEY_SESSION_STEPS, 0)
        return steps * KM_PER_STEP
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
