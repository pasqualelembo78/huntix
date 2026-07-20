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
import androidx.core.app.ActivityCompat
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
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var haveAccel = false
    private var haveMag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            mgr.start(this)
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 2002
            )
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
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        update()
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3); haveAccel = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3); haveMag = true
            }
        }
        if (haveAccel && haveMag) {
            val r = FloatArray(9); val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                deviceAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (deviceAzimuth < 0) deviceAzimuth += 360f
                updateArrow()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2002 &&
            grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            mgr.start(this)
        }
    }

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
