package com.intelligame.huntix.minigames

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.managers.MiniGameManager

/**
 * MiniGamePlugin — interfaccia per registrare nuovi minigiochi
 * nel sistema di Huntix.
 *
 * Ogni gioco implementa questa interfaccia e viene registrato
 * in [MiniGamePluginRegistry]. La registrazione avviene automaticamente
 * all'avvio dell'app tramite [MiniGamePluginRegistry.init].
 *
 * Uso:
 * ```
 * class TetrisPlugin : MiniGamePlugin {
 *     override fun gameId() = MiniGameManager.GAME_TETRIS
 *     override fun title() = "Tetris"
 *     override fun emoji() = "🧱"
 *     override fun category() = GameCategory.PUZZLE
 *     override fun createActivity(): Class<out AppCompatActivity> = TetrisActivity::class.java
 * }
 * ```
 *
 * Poi in [com.intelligame.huntix.MiniGamesHubActivity]:
 * ```
 * private val plugins = listOf(TetrisPlugin(), /* ... */)
 * ```
 */
interface MiniGamePlugin {

    /** ID univoco del gioco (deve corrispondere a una costante in [MiniGameManager]). */
    fun gameId(): String

    /** Nome visualizzato del gioco. */
    fun title(): String

    /** Emoji rappresentativa. */
    fun emoji(): String

    /** Categoria del gioco. */
    fun category(): GameCategory

    /** Activity che ospita il gioco. */
    fun createActivity(): Class<out AppCompatActivity>

    /**
     * true se il gioco ha una versione AR.
     * Se true, [createArActivity] deve restituire una classe non null.
     */
    fun hasAr(): Boolean = false

    /** Activity AR del gioco (solo se [hasAr] è true). */
    fun createArActivity(): Class<out AppCompatActivity>? = null
}

/** Categoria di un minigioco. */
enum class GameCategory {
    PUZZLE,
    ARCADE,
    MEMORY,
    ARCADE_CLASSIC,
    AR_EXCLUSIVE
}

/**
 * Registro centrale dei plugin di minigiochi.
 *
 * Inizializzare con [init] all'avvio dell'app, poi usare
 * [getPlugins] per ottenere la lista completa dei giochi
 * (nativi + plugin).
 */
object MiniGamePluginRegistry {

    private val plugins = mutableListOf<MiniGamePlugin>()

    /** Registra un plugin. Chiamare all'avvio dell'app. */
    fun init(vararg plugin: MiniGamePlugin) {
        plugins.clear()
        plugins.addAll(plugin)
    }

    /** Aggiunge un singolo plugin dinamicamente. */
    fun register(plugin: MiniGamePlugin) {
        plugins.add(plugin)
    }

    /** Rimuove un plugin. */
    fun unregister(gameId: String) {
        plugins.removeAll { it.gameId() == gameId }
    }

    /** Restituisce tutti i plugin registrati. */
    fun getPlugins(): List<MiniGamePlugin> = plugins.toList()

    /** Verifica che un gameId sia registrato. */
    fun isRegistered(gameId: String): Boolean = plugins.any { it.gameId() == gameId }

    /** Restituisce il plugin per un dato gameId, o null. */
    fun find(gameId: String): MiniGamePlugin? = plugins.find { it.gameId() == gameId }
}

/**
 * Helper per avviare un gioco tramite plugin.
 */
fun Context.launchMiniGame(gameId: String) {
    val plugin = MiniGamePluginRegistry.find(gameId) ?: return
    val intent = Intent(this, plugin.createActivity())
    startActivity(intent)
}

/**
 * Helper per avviare la versione AR di un gioco tramite plugin.
 */
fun Context.launchArMiniGame(gameId: String) {
    val plugin = MiniGamePluginRegistry.find(gameId) ?: return
    val arCls = plugin.createArActivity() ?: return
    val intent = Intent(this, arCls)
    startActivity(intent)
}