# Debug script for outdoor map freezing issue

This script analyzes the potential causes of the outdoor map freezing in Huntix.

## Issues Found:

### 1. Handler Thread Conflicts (Critical)
```kotlin
// OutdoorWorldActivity.kt:76-79
private val refresh = Handler(Looper.getMainLooper())
private val tick = object : Runnable {
    override fun run() {
        refreshUi(); refresh.postDelayed(this, 3000) // Every 3 seconds!
    }
}
// This posts to UI thread repeatedly WITHOUT locking
```

### 2. MapRedraw Loop (Critical)
```kotlin
// OutdoorWorldActivity.kt:400-503
mapLibre?.let { map ->
    map.clear() // Clears ALL markers
    mgr.getEggs().forEach { ... }     // Redraws ALL eggs
    mgr.getPois().forEach { ... }     // Redraws ALL pois  
    // ALL this on every map movement! (every 3 seconds)
}
```

### 3. Sensor Interference (High)
```kotlin
// OutdoorWorldActivity.kt:289-294
sensorManager?.registerListener(sensorListener, rotationVector, SensorManager.SENSOR_DELAY_UI)
// Continuous sensor updates during UI interactions
```

### 4. Concurrency Issues (High)
```kotlin
// OutdoorManager.kt:335-365
private fun fetchOnlinePoisAsync(...)
    scope?.launch { // Multiple background coroutines
        mgr.fetchPoiForLocation(...)
        // Network + UI updates
    }
```

## Immediate Fixes Needed:

1. **Fix the 3-second redraw loop**: This is destroying performance during map movement
2. **Add map movement throttling**: Only redraw when map stops moving
3. **Separate sensor and map logic**: Prevent sensor updates from blocking map rendering
4. **Fix handler cleanup**: Ensure all callbacks are properly removed

## Reproduction Steps:

1. Open outdoor mode: `OutdoorWorldActivity.kt`
2. Allow GPS location to initialize
3. Start moving the map (pan/rotate)
4. Notice UI freezing every 3 seconds when the timer fires
5. Observer app freezes and server disconnects

The root cause is the unoptimized map redraw loop combined with continuous sensor updates and network requests.