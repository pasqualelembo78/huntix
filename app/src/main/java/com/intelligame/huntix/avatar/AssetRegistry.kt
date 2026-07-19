package com.intelligame.huntix.avatar

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * AssetRegistry — Registro CENTRALE di tutti gli asset 3D del gioco.
 *
 * ═══ ARCHITETTURA ═══
 *
 *  TUTTI gli asset sono locali, nella cartella assets/ del progetto.
 *  Nessun download da internet. Mai. Zero task Gradle esterni.
 *
 *  Struttura cartelle (OBBLIGATORIA):
 *    assets/
 *    ├── Creatures/                    ← GLB animaletti
 *    │   ├── pulcino.glb
 *    │   ├── coniglietto.glb
 *    │   └── ...
 *    ├── Avatars/
 *    │   └── Humanoid/                ← GLB avatar RPM maschio/femmina
 *    │       ├── player_male.glb
 *    │       └── player_female.glb
 *    └── Accessories/
 *        ├── Animals/                 ← GLB accessori creature
 *        │   ├── head_beanie.glb
 *        │   └── ...
 *        └── Humanoid/               ← GLB accessori avatar umanoidi
 *            ├── hat_wizard.glb
 *            └── ...
 *
 * ═══ REGOLA FONDAMENTALE ═══
 *  Il gioco carica asset SOLO tramite ID → nome file (questo registro).
 *  Mai path hardcoded sparsi nel codice.
 *  Per aggiungere un asset: metti il GLB nella cartella + aggiungi entry qui.
 *
 * ═══ PER LO SVILUPPATORE ═══
 *  1. Procurati il file GLB (low-poly, <1MB, CC0/CC-BY)
 *  2. Copialo nella cartella corretta sotto assets/
 *  3. Aggiungi l'entry nella mappa corrispondente qui sotto
 *  4. Compila. Fatto.
 */
object AssetRegistry {

    private const val TAG = "AssetRegistry"

    // ═══════════════════════════════════════════════════════════
    // CARTELLE BASE
    // ═══════════════════════════════════════════════════════════

    private const val DIR_CREATURES    = "Creatures"
    private const val DIR_AVATARS      = "Avatars/Humanoid"
    private const val DIR_ACC_ANIMALS  = "Accessories/Animals"
    private const val DIR_ACC_HUMANOID = "Accessories/Humanoid"

    // ═══════════════════════════════════════════════════════════
    // CREATURE: ID → nome file GLB
    // ═══════════════════════════════════════════════════════════

    val CREATURES: Map<String, String> = mapOf(
        // COMMON
        "pulcino"        to "pulcino.glb",
        "coniglietto"    to "coniglietto.glb",
        "agnellino"      to "agnellino.glb",
        "farfalla"       to "farfalla.glb",
        // UNCOMMON
        "volpe_luna"     to "volpe_luna.glb",
        "cerbiatto"      to "cerbiatto.glb",
        "gufo_stellato"  to "gufo_stellato.glb",
        // RARE
        "drago_pasquale" to "drago_pasquale.glb",
        "fenice_rosa"    to "fenice_rosa.glb",
        "unicorno"       to "unicorno.glb",
        // EPIC
        "behemoth"       to "behemoth.glb",
        "leviatan"       to "leviatan.glb",
        // LEGENDARY
        "grande_coniglio" to "grande_coniglio.glb",
        "uovo_creatore"  to "uovo_creatore.glb"
    )

    // ═══════════════════════════════════════════════════════════
    // AVATAR UMANOIDI: ID → nome file GLB
    // ═══════════════════════════════════════════════════════════

    val AVATARS: Map<String, String> = mapOf(
        "player_male"    to "player_male.glb",
        "player_female"  to "player_female.glb"
    )

    // ═══════════════════════════════════════════════════════════
    // ACCESSORI CREATURE: ID → nome file GLB
    // ═══════════════════════════════════════════════════════════

    val ACCESSORIES_ANIMALS: Map<String, String> = mapOf(
        // HEAD
        "head_beanie"      to "head_beanie.glb",
        "head_cap"         to "head_cap.glb",
        "head_bandana"     to "head_bandana.glb",
        "head_flower"      to "head_flower.glb",
        "head_viking"      to "head_viking.glb",
        "head_wizard"      to "head_wizard.glb",
        "head_crown"       to "head_crown.glb",
        "head_dragon_helm" to "head_dragon_helm.glb",
        "head_halo"        to "head_halo.glb",
        "head_galaxy"      to "head_galaxy.glb",
        // BODY
        "body_scarf"       to "body_scarf.glb",
        "body_backpack"    to "body_backpack.glb",
        "body_cape_green"  to "body_cape_green.glb",
        "body_shield"      to "body_shield.glb",
        "body_wings_bird"  to "body_wings_bird.glb",
        "body_armor_iron"  to "body_armor_iron.glb",
        "body_cape_fire"   to "body_cape_fire.glb",
        "body_wings_angel" to "body_wings_angel.glb",
        "body_wings_dragon" to "body_wings_dragon.glb",
        "body_armor_star"  to "body_armor_star.glb",
        // EFFECT
        "fx_sparkle"       to "fx_sparkle.glb",
        "fx_leaves"        to "fx_leaves.glb",
        "fx_butterflies"   to "fx_butterflies.glb",
        "fx_snowflakes"    to "fx_snowflakes.glb",
        "fx_fire_aura"     to "fx_fire_aura.glb",
        "fx_lightning"     to "fx_lightning.glb",
        "fx_dark_mist"     to "fx_dark_mist.glb",
        "fx_rainbow_trail" to "fx_rainbow_trail.glb",
        "fx_galaxy_aura"   to "fx_galaxy_aura.glb",
        "fx_phoenix_flame" to "fx_phoenix_flame.glb"
    )

    // ═══════════════════════════════════════════════════════════
    // ACCESSORI AVATAR UMANOIDI: ID → nome file GLB
    // ═══════════════════════════════════════════════════════════

    val ACCESSORIES_HUMANOID: Map<String, String> = mapOf(
        "hat_wizard"       to "hat_wizard.glb",
        "hat_crown"        to "hat_crown.glb",
        "hat_cap"          to "hat_cap.glb",
        "cape_hero"        to "cape_hero.glb",
        "cape_fire"        to "cape_fire.glb",
        "armor_knight"     to "armor_knight.glb",
        "wings_angel"      to "wings_angel.glb",
        "shield_wooden"    to "shield_wooden.glb",
        "backpack_explorer" to "backpack_explorer.glb",
        "aura_rainbow"     to "aura_rainbow.glb"
    )

    // ═══════════════════════════════════════════════════════════
    // API DI ACCESSO (unico punto di caricamento)
    // ═══════════════════════════════════════════════════════════

    /** Apre lo stream di un asset creatura per ID */
    fun openCreature(ctx: Context, creatureId: String): InputStream? =
        openAsset(ctx, DIR_CREATURES, CREATURES[creatureId])

    /** Apre lo stream di un avatar per ID */
    fun openAvatar(ctx: Context, avatarId: String): InputStream? =
        openAsset(ctx, DIR_AVATARS, AVATARS[avatarId])

    /** Apre lo stream di un accessorio animale per ID */
    fun openAnimalAccessory(ctx: Context, accId: String): InputStream? =
        openAsset(ctx, DIR_ACC_ANIMALS, ACCESSORIES_ANIMALS[accId])

    /** Apre lo stream di un accessorio umanoide per ID */
    fun openHumanoidAccessory(ctx: Context, accId: String): InputStream? =
        openAsset(ctx, DIR_ACC_HUMANOID, ACCESSORIES_HUMANOID[accId])

    /**
     * Copia un asset in un file temporaneo per SceneView/Filament.
     * @return File locale leggibile, o null se non disponibile
     */
    fun getCreatureFile(ctx: Context, creatureId: String): File? =
        extractToCache(ctx, DIR_CREATURES, CREATURES[creatureId], "creatures/$creatureId.glb")

    fun getAvatarFile(ctx: Context, avatarId: String): File? =
        extractToCache(ctx, DIR_AVATARS, AVATARS[avatarId], "avatars/$avatarId.glb")

    fun getAnimalAccessoryFile(ctx: Context, accId: String): File? =
        extractToCache(ctx, DIR_ACC_ANIMALS, ACCESSORIES_ANIMALS[accId], "acc_animals/$accId.glb")

    fun getHumanoidAccessoryFile(ctx: Context, accId: String): File? =
        extractToCache(ctx, DIR_ACC_HUMANOID, ACCESSORIES_HUMANOID[accId], "acc_humanoid/$accId.glb")

    /** Ritorna l'avatar del giocatore in base al genere scelto */
    fun getPlayerAvatarFile(ctx: Context): File? {
        val gender = GenderSelectionActivity.getGender(ctx)
        return getAvatarFile(ctx, "player_$gender")
    }

    // ═══════════════════════════════════════════════════════════
    // VERIFICA DISPONIBILITÀ
    // ═══════════════════════════════════════════════════════════

    fun hasCreature(ctx: Context, creatureId: String): Boolean =
        assetExists(ctx, DIR_CREATURES, CREATURES[creatureId])

    fun hasAvatar(ctx: Context, avatarId: String): Boolean =
        assetExists(ctx, DIR_AVATARS, AVATARS[avatarId])

    /** Lista creature con modello 3D disponibile */
    fun availableCreatures(ctx: Context): List<String> =
        CREATURES.keys.filter { hasCreature(ctx, it) }

    /** Lista tutti i file RICHIESTI dal sistema (per verifica) */
    fun requiredFilesList(): String {
        val sb = StringBuilder()
        sb.appendLine("═══ FILE RICHIESTI DAL SISTEMA ═══\n")

        sb.appendLine("── Creatures/ (${CREATURES.size} file) ──")
        CREATURES.forEach { (id, file) -> sb.appendLine("  $file  ← $id") }

        sb.appendLine("\n── Avatars/Humanoid/ (${AVATARS.size} file) ──")
        AVATARS.forEach { (id, file) -> sb.appendLine("  $file  ← $id") }

        sb.appendLine("\n── Accessories/Animals/ (${ACCESSORIES_ANIMALS.size} file) ──")
        ACCESSORIES_ANIMALS.forEach { (id, file) -> sb.appendLine("  $file  ← $id") }

        sb.appendLine("\n── Accessories/Humanoid/ (${ACCESSORIES_HUMANOID.size} file) ──")
        ACCESSORIES_HUMANOID.forEach { (id, file) -> sb.appendLine("  $file  ← $id") }

        val total = CREATURES.size + AVATARS.size + ACCESSORIES_ANIMALS.size + ACCESSORIES_HUMANOID.size
        sb.appendLine("\n═══ TOTALE: $total file GLB richiesti ═══")
        return sb.toString()
    }

    // ═══════════════════════════════════════════════════════════
    // INTERNALS
    // ═══════════════════════════════════════════════════════════

    private fun openAsset(ctx: Context, dir: String, filename: String?): InputStream? {
        if (filename == null) return null
        return try {
            ctx.assets.open("$dir/$filename")
        } catch (e: Exception) {
            Log.w(TAG, "Asset non trovato: $dir/$filename")
            null
        }
    }

    private fun assetExists(ctx: Context, dir: String, filename: String?): Boolean {
        if (filename == null) return false
        return try {
            ctx.assets.open("$dir/$filename").use { true }
        } catch (e: Exception) { false }
    }

    private fun extractToCache(ctx: Context, dir: String, filename: String?, cachePath: String): File? {
        if (filename == null) return null
        val cacheFile = File(ctx.cacheDir, cachePath)
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile
        return try {
            cacheFile.parentFile?.mkdirs()
            ctx.assets.open("$dir/$filename").use { input ->
                FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
            }
            if (cacheFile.length() > 0) cacheFile else null
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile estrarre $dir/$filename: ${e.message}")
            null
        }
    }
}
