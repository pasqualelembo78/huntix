package com.intelligame.huntix

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// ── Permessi & caricamento indovinelli ─────────────────────────
internal fun MainActivity.checkCameraPermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) arSceneManager.setupAR()
    else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), MainActivity.CAMERA_PERMISSION_CODE)
}

internal fun MainActivity.loadRiddles(): List<String> {
    return try {
        assets.open("riddles.txt").bufferedReader().use { r ->
            r.readLines().filter { it.isNotBlank() }
        }
    } catch (_: Exception) {
        listOf(
            "Dove ogni mattina profuma di caldo - cerca vicino alla macchina del caffe'",
            "Dove la notte arrivano i sogni - guarda sotto il cuscino del letto",
            "Dove la natura entra in casa - cerca tra le piante del balcone",
            "Dove si conservano le cose preziose - guarda nell'armadio dei giochi"
        )
    }
}
