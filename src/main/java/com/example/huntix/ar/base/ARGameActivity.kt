package com.example.huntix.ar.base

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Scene
import com.example.huntix.audio.SpatialAudio

abstract class ARGameActivity : AppCompatActivity() {

    protected var arSceneView: ArSceneView? = null
    protected lateinit var scene: Scene
    protected var arSession: Session? = null
    protected var spatialAudio: SpatialAudio = SpatialAudio()
    private var isGameStarted = false
    protected val arHandler = Handler(Looper.getMainLooper())

    protected abstract fun setupGame()
    protected abstract fun handleFrame(frame: Frame)
    protected abstract fun onGameOver(score: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Controlla se ARCore è supportato
        if (Session.isSupported(this)) {
            try {
                setupARSession()
                arSceneView = ArSceneView(this)
                setContentView(arSceneView)

                scene = arSceneView!!.scene
                arSceneView!!.visibility = View.VISIBLE
                arSceneView!!.isEnabled = true

                setupGame()
                isGameStarted = true
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Errore AR: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            Toast.makeText(this, "AR non supportato su questo dispositivo", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupARSession() {
        arSession = Session(this)
        val config = Config(arSession)
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
        config.depthMode = Config.DepthMode.AUTOMATIC
        arSession?.configure(config)
    }

    protected fun tryAnchorToPlane(hitResult: HitResult): Anchor? {
        val hitTest = hitResult.hitTest(hitResult.x, hitResult.y)
        return hitTest.firstOrNull { hit ->
            hit.trackable is Plane && hit.isHitInPolygon
        }?.createAnchor()
    }

    protected fun resumeAR() {
        if (arSession != null && arSceneView != null) {
            try {
                arSession?.resume()
                arSceneView?.setupSession(arSession)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    protected fun pauseAR() {
        try {
            arSceneView?.pause()
            arSession?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        if (arSceneView != null && arSession != null) {
            resumeAR()
        }
    }

    override fun onPause() {
        super.onPause()
        pauseAR()
    }

    override fun onBackPressed() {
        // Puliamo risorse AR prima di tornare indietro
        pauseAR()

        // Attendi che Sceneform rilasci l'ambiente
        arSceneView?.let {
            try {
                it.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Chiudi l'activity
        finish()
        // Evita animazioni
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        pauseAR()

        // Rilascia risorse di Sceneform
        arSceneView?.let {
            try {
                it.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        spatialAudio.release()
    }

    protected fun isSafeToRender(): Boolean {
        return arSession != null && arSceneView != null && isGameStarted
    }
}