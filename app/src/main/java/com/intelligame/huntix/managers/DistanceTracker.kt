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
    private const val KEY_LAST_STEP = "last_step"
    private const val KEY_LISTENING = "is_listening"

    private const val STEP_LENGTH_M = 0.714f  // average step length in meters
    private const val KM_PER_STEP = STEP_LENGTH_M / 1000f

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var initialSteps = -1f
    private var onDistanceUpdate: ((Float) -> Unit)? = null

    fun startListening(ctx: Context, callback: (Float) -> Unit) {
        if (isListening(ctx)) return
        sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor == null) {
            Log.w("DistanceTracker", "Step counter sensor not available")
            return
        }
        onDistanceUpdate = callback
        initialSteps = -1f
        sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        prefs(ctx).edit().putBoolean(KEY_LISTENING, true).apply()
        Log.d("DistanceTracker", "Started listening for steps")
    }

    fun stopListening(ctx: Context) {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        stepSensor = null
        initialSteps = -1f
        prefs(ctx).edit().putBoolean(KEY_LISTENING, false).apply()
        Log.d("DistanceTracker", "Stopped listening")
    }

    fun isListening(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_LISTENING, false)

    fun getTotalKm(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_TOTAL_KM, 0f)

    fun getSessionSteps(ctx: Context): Int =
        prefs(ctx).getInt(KEY_SESSION_STEPS, 0)

    fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return

        val totalSteps = event.values[0]
        if (initialSteps < 0) {
            initialSteps = totalSteps
            return
        }

        val deltaSteps = totalSteps - initialSteps
        if (deltaSteps <= 0) return

        val deltaKm = deltaSteps * KM_PER_STEP

        // Save to SharedPreferences
        onDistanceUpdate?.invoke(deltaKm)

        // Update totals
        val ctx = sensorManager?.context ?: return
        val p = prefs(ctx)
        val prevTotal = p.getFloat(KEY_TOTAL_KM, 0f)
        p.edit()
            .putFloat(KEY_TOTAL_KM, prevTotal + deltaKm)
            .putInt(KEY_SESSION_STEPS, p.getInt(KEY_SESSION_STEPS, 0) + deltaSteps.toInt())
            .apply()

        // Reset initial so next call only counts delta
        initialSteps = totalSteps

        // Feed distance to incubators
        if (deltaKm > 0.001f) {
            val readyEggs = IncubatorManager.addDistanceToIncubators(ctx, deltaKm)
            if (readyEggs.isNotEmpty()) {
                Log.d("DistanceTracker", "Eggs ready to hatch: $readyEggs")
            }
            // Feed to buddy
            try {
                BuddyManager.addWalkingDistance(ctx, deltaKm)
            } catch (_: Exception) {}
            // Track walking research tasks
            val totalKm = prefs(ctx).getFloat(KEY_TOTAL_KM, 0f)
            if (totalKm >= 2f) ResearchTaskManager.trackProgress(ctx, "walk_2km")
            if (totalKm >= 10f) ResearchTaskManager.trackProgress(ctx, "walk_10km")
        }
    }

    fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun forceSyncSteps(ctx: Context): Float {
        val steps = prefs(ctx).getInt(KEY_SESSION_STEPS, 0)
        return steps * KM_PER_STEP
    }
}
