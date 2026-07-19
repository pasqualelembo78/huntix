package com.intelligame.huntix.avatar

import android.content.Context
import java.io.File

/**
 * CreatureModelHelper — Wrapper semplificato per AssetRegistry.
 *
 * Tutte le chiamate passano da AssetRegistry (mapping centrale).
 * Nessun path hardcoded. Nessun download.
 */
object CreatureModelHelper {

    fun hasModel(ctx: Context, creatureId: String): Boolean =
        AssetRegistry.hasCreature(ctx, creatureId)

    fun getModelFile(ctx: Context, creatureId: String): File? =
        AssetRegistry.getCreatureFile(ctx, creatureId)

    fun getPlayerAvatarFile(ctx: Context): File? =
        AssetRegistry.getPlayerAvatarFile(ctx)

    fun getAvailableModels(ctx: Context): List<String> =
        AssetRegistry.availableCreatures(ctx)
}
