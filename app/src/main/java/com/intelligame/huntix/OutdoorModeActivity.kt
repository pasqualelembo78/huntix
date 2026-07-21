package com.intelligame.huntix

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.intelligame.huntix.ui.OutdoorWorldActivity

/**
 * OutdoorModeActivity — redirect diretto alla mappa fullscreen.
 * La mappa e la schermata principale della modalita Outdoor.
 */
class OutdoorModeActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasGps = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

        if (!hasGps) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
        }

        com.intelligame.huntix.managers.ResearchTaskManager.trackProgress(this, "play_outdoor")
        startActivity(Intent(this, OutdoorWorldActivity::class.java))
        finish()
    }
}
