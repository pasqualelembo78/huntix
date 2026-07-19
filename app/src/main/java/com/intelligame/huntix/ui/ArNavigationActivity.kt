package com.intelligame.huntix.ui

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.manager.OutdoorManager

class ArNavigationActivity : BaseNavActivity(), SensorEventListener {

    override fun activeTab() = ""

    private val mgr = OutdoorManager.get()
    private lateinit var arrow: TextView
    private lateinit var distText: TextView
    private lateinit var targetText: TextView
    private var deviceAzimuth = 0f
    private var sensorManager: SensorManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            mgr.start(this)
        }
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        arrow = TextView(this).apply {
            text = "➤"
            textSize = 120f
            setTextColor(0xFFFFEB3.toInt())
        }
        distText = TextView(this).apply { textSize = 28f; setTextColor(android.graphics.Color.WHITE) }
        targetText = TextView(this).apply { textSize = 18f }

        val content = UiKit.scroll(
            this,
            UiKit.title(this, "Navigazione AR", "🧭"),
            UiKit.card(this, arrow, distText, targetText),
            UiKit.button(this, "🎯 Vai a catturare", UiKit.ACCENT) {
                val egg = mgr.nearestUnfoundEgg()
                if (egg == null) {
                    Toast.makeText(this, "Nessuna uova da cercare", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(this, OutdoorHuntActivity::class.java).apply {
                        putExtra("eggId", egg.id)
                    })
                }
            },
            UiKit.button(this, "↩️ Mappa", UiKit.PURPLE) {
                startActivity(Intent(this, OutdoorWorldActivity::class.java))
                finish()
            }
        )
        setContentView(content)
        update()
    }

    override fun onResume() {
        super.onResume()
        sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        update()
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ORIENTATION) {
            deviceAzimuth = event.values[0]
            updateArrow()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun update() {
        val egg = mgr.nearestUnfoundEgg()
        if (egg == null) {
            distText.text = "Nessuna uova vicina"
            targetText.text = ""
            arrow.rotation = 0f
            return
        }
        val d = mgr.distanceMeters(egg)
        distText.text = "${egg.rarity.emoji} ${egg.displayLabel}\n${d.toInt()} m"
        targetText.text = "Punta il telefono verso l'uovo e cammina."
        updateArrow(egg)
    }

    private fun updateArrow(egg: com.intelligame.huntix.WorldEgg? = mgr.nearestUnfoundEgg()) {
        val e = egg ?: return
        val bearing = mgr.bearingTo(e)
        // "➤" points East (0deg) by default; rotate so North is up, then subtract device facing.
        arrow.rotation = bearing - deviceAzimuth - 90f
    }
}
