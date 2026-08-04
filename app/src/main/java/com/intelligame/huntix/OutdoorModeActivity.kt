package com.intelligame.huntix

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.intelligame.huntix.ui.ArNavigationActivity
import com.intelligame.huntix.ui.OutdoorTutorialActivity
import com.intelligame.huntix.ui.OutdoorWorldActivity

class OutdoorModeActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasGps = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCam = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

        if (!hasGps || !hasCam) {
            ActivityCompat.requestPermissions(this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA
                ), 100)
        }

        com.intelligame.huntix.managers.ResearchTaskManager.trackProgress(this, "play_outdoor")

        val prefs = getSharedPreferences("outdoor_prefs", MODE_PRIVATE)
        val hasSeenTutorial = prefs.getBoolean("tutorial_seen", false)

        if (!hasSeenTutorial) {
            startActivity(Intent(this, OutdoorTutorialActivity::class.java))
        } else {
            startActivity(Intent(this, ArNavigationActivity::class.java))
        }
        finish()
    }
}