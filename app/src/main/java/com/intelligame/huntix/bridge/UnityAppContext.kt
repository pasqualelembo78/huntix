package com.intelligame.huntix.bridge

import android.content.Context

/**
 * Contesto applicativo condiviso fra i due processi.
 *
 * In :unity serve perche' `UnityPlayer.currentActivity` e' valido solo mentre
 * gira l'Activity Unity; nel processo app e' l'unico modo per leggere le prefs
 * e lo stato del profilo quando il runtime Unity non c'e'.
 */
object UnityAppContext {
    @Volatile
    var context: Context? = null
}
