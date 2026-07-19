package com.intelligame.huntix.avatar

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * AccessoryManager — Sistema equipaggiamento accessori per avatar RPM.
 *
 * ═══ SLOT EQUIPAGGIAMENTO ═══
 *   HEAD   → cappelli, maschere, corna, orecchie, aureole
 *   BODY   → mantelli, ali, armature, zaini, scudi
 *   EFFECT → aure, particelle, scie, scintille
 *
 * ═══ OTTENIMENTO ACCESSORI ═══
 *   Gli accessori vengono ottenuti ESCLUSIVAMENTE dalle uova:
 *   - COMMON egg    → 0-1 accessori, solo HEAD common
 *   - UNCOMMON egg  → 1 accessorio, HEAD/BODY uncommon
 *   - RARE egg      → 1-2 accessori, qualsiasi slot rare
 *   - EPIC egg      → 2 accessori, qualsiasi slot epic
 *   - LEGENDARY egg → 2-3 accessori, tutti gli slot, legendary
 *
 * ═══ PERSISTENZA ═══
 *   Locale: JSON in SharedPreferences (via AvatarPersistenceManager)
 *   Cloud: Firestore (via AvatarSyncManager)
 *
 * ═══ VINCOLI ASSET ═══
 *   - SOLO asset gratuiti / open source (CC0, CC-BY, MIT)
 *   - Nessun asset a pagamento o con licenze restrittive
 *   - Low poly ottimizzato mobile (< 5k triangoli per accessorio)
 *   - Texture max 512px (accessori) per risparmiare memoria
 */
object AccessoryManager {

    private const val TAG = "AccessoryManager"

    // ─── Modelli ─────────────────────────────────────────────────

    enum class Slot(val displayName: String, val emoji: String) {
        HEAD("Testa", "🎩"),
        BODY("Corpo", "🛡️"),
        EFFECT("Effetto", "✨")
    }

    enum class AccessoryRarity(val displayName: String, val colorHex: String) {
        COMMON("Comune", "#00FF88"),
        UNCOMMON("Insolito", "#00B4FF"),
        RARE("Raro", "#A855F7"),
        EPIC("Epico", "#FF6B35"),
        LEGENDARY("Leggendario", "#FFD700")
    }

    /**
     * Definizione di un accessorio nel catalogo.
     * Gli accessori sono puramente cosmetici e non hanno effetti sul gameplay.
     */
    data class AccessoryDef(
        val id: String,
        val name: String,
        val emoji: String,
        val description: String,
        val slot: Slot,
        val rarity: AccessoryRarity,
        /** Path relativo all'asset (in assets/accessories/ o URL remoto) */
        val assetPath: String = "",
        /** Licenza dell'asset (es: "CC0", "CC-BY-4.0", "MIT") */
        val license: String = "CC0"
    )

    /**
     * Istanza di un accessorio posseduto dal giocatore.
     */
    data class OwnedAccessory(
        val accessoryId: String,
        val obtainedAt: Long = System.currentTimeMillis(),
        val sourceEggRarity: String = "common",
        val isEquipped: Boolean = false
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("accessoryId", accessoryId)
            put("obtainedAt", obtainedAt)
            put("sourceEggRarity", sourceEggRarity)
            put("isEquipped", isEquipped)
        }

        companion object {
            fun fromJson(j: JSONObject) = OwnedAccessory(
                accessoryId = j.optString("accessoryId", ""),
                obtainedAt = j.optLong("obtainedAt", System.currentTimeMillis()),
                sourceEggRarity = j.optString("sourceEggRarity", "common"),
                isEquipped = j.optBoolean("isEquipped", false)
            )
        }
    }

    /**
     * Configurazione equipaggiamento corrente.
     */
    data class EquipLoadout(
        val headId: String = "",
        val bodyId: String = "",
        val effectId: String = ""
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("headId", headId)
            put("bodyId", bodyId)
            put("effectId", effectId)
        }

        companion object {
            fun fromJson(j: JSONObject) = EquipLoadout(
                headId = j.optString("headId", ""),
                bodyId = j.optString("bodyId", ""),
                effectId = j.optString("effectId", "")
            )
        }
    }

    // ─── Catalogo completo accessori ─────────────────────────────
    // SOLO asset gratuiti / open source compatibili con uso commerciale

    val CATALOG: List<AccessoryDef> = listOf(
        // ── HEAD: Cappelli & copricapo ────────────────────────────
        AccessoryDef("head_beanie",     "Berretto Lana",     "🧶", "Un caldo berretto di lana",           Slot.HEAD, AccessoryRarity.COMMON, license = "CC0"),
        AccessoryDef("head_cap",        "Cappellino",        "🧢", "Cappellino sportivo",                 Slot.HEAD, AccessoryRarity.COMMON, license = "CC0"),
        AccessoryDef("head_bandana",    "Bandana",           "🎀", "Bandana colorata",                    Slot.HEAD, AccessoryRarity.COMMON, license = "CC0"),
        AccessoryDef("head_flower",     "Corona di Fiori",   "🌸", "Ghirlanda di fiori primaverili",      Slot.HEAD, AccessoryRarity.UNCOMMON, license = "CC0"),
        AccessoryDef("head_viking",     "Elmo Vichingo",     "⚔️",  "Elmo con corna vichinghe",            Slot.HEAD, AccessoryRarity.UNCOMMON, license = "CC0"),
        AccessoryDef("head_wizard",     "Cappello Mago",     "🧙", "Cappello da mago stellato",           Slot.HEAD, AccessoryRarity.RARE, license = "CC0"),
        AccessoryDef("head_crown",      "Corona Reale",      "👑", "Corona d'oro con gemme",              Slot.HEAD, AccessoryRarity.RARE, license = "CC0"),
        AccessoryDef("head_dragon_helm","Elmo del Drago",    "🐉", "Elmo forgiato nelle fiamme",          Slot.HEAD, AccessoryRarity.EPIC, license = "CC0"),
        AccessoryDef("head_halo",       "Aureola Divina",    "😇", "Aureola luminescente fluttuante",     Slot.HEAD, AccessoryRarity.EPIC, license = "CC0"),
        AccessoryDef("head_galaxy",     "Corona Cosmica",    "🌌", "Corona con nebulose vorticanti",      Slot.HEAD, AccessoryRarity.LEGENDARY, license = "CC0"),

        // ── BODY: Mantelli, ali, armature ────────────────────────
        AccessoryDef("body_scarf",      "Sciarpa",           "🧣", "Sciarpa colorata al vento",           Slot.BODY, AccessoryRarity.COMMON, license = "CC0"),
        AccessoryDef("body_backpack",   "Zaino Esploratore", "🎒", "Zaino da avventuriero",               Slot.BODY, AccessoryRarity.COMMON, license = "CC0"),
        AccessoryDef("body_cape_green", "Mantello Verde",    "🟢", "Mantello dell'esploratore",           Slot.BODY, AccessoryRarity.UNCOMMON, license = "CC0"),
        AccessoryDef("body_shield",     "Scudo di Legno",    "🛡️", "Scudo artigianale robusto",           Slot.BODY, AccessoryRarity.UNCOMMON, license = "CC0"),
        AccessoryDef("body_wings_bird", "Ali di Falco",      "🦅", "Ali piumate maestose",                Slot.BODY, AccessoryRarity.RARE, license = "CC0"),
        AccessoryDef("body_armor_iron", "Armatura di Ferro", "⚙️",  "Armatura forgiata a mano",            Slot.BODY, AccessoryRarity.RARE, license = "CC0"),
        AccessoryDef("body_cape_fire",  "Mantello Infuocato","🔥", "Mantello avvolto dalle fiamme",       Slot.BODY, AccessoryRarity.EPIC, license = "CC0"),
        AccessoryDef("body_wings_angel","Ali Angeliche",     "👼", "Ali bianche luminescenti",            Slot.BODY, AccessoryRarity.EPIC, license = "CC0"),
        AccessoryDef("body_wings_dragon","Ali del Drago",    "🐲", "Ali di drago leggendarie",            Slot.BODY, AccessoryRarity.LEGENDARY, license = "CC0"),
        AccessoryDef("body_armor_star", "Armatura Stellare", "⭐", "Armatura forgiata dalle stelle",      Slot.BODY, AccessoryRarity.LEGENDARY, license = "CC0"),

        // ── EFFECT: Aure, particelle, scie ───────────────────────
        AccessoryDef("fx_sparkle",      "Scintille",         "✨", "Piccole scintille attorno al corpo",  Slot.EFFECT, AccessoryRarity.COMMON, license = "CC0"),
        AccessoryDef("fx_leaves",       "Foglie Danzanti",   "🍃", "Foglie che volteggiano intorno",      Slot.EFFECT, AccessoryRarity.COMMON, license = "CC0"),
        AccessoryDef("fx_butterflies",  "Farfalle",          "🦋", "Farfalle colorate circostanti",       Slot.EFFECT, AccessoryRarity.UNCOMMON, license = "CC0"),
        AccessoryDef("fx_snowflakes",   "Fiocchi di Neve",   "❄️",  "Nevicata magica personale",           Slot.EFFECT, AccessoryRarity.UNCOMMON, license = "CC0"),
        AccessoryDef("fx_fire_aura",    "Aura di Fuoco",     "🔥", "Fiamme danzanti ai piedi",            Slot.EFFECT, AccessoryRarity.RARE, license = "CC0"),
        AccessoryDef("fx_lightning",    "Scariche Elettriche","⚡", "Fulmini crepitanti",                  Slot.EFFECT, AccessoryRarity.RARE, license = "CC0"),
        AccessoryDef("fx_dark_mist",    "Nebbia Oscura",     "🌫️", "Nebbia viola mistica",                Slot.EFFECT, AccessoryRarity.EPIC, license = "CC0"),
        AccessoryDef("fx_rainbow_trail","Scia Arcobaleno",   "🌈", "Scia luminosa arcobaleno",            Slot.EFFECT, AccessoryRarity.EPIC, license = "CC0"),
        AccessoryDef("fx_galaxy_aura",  "Aura Galattica",    "🌌", "Campo di stelle attorno al corpo",    Slot.EFFECT, AccessoryRarity.LEGENDARY, license = "CC0"),
        AccessoryDef("fx_phoenix_flame","Fiamma della Fenice","🔥","Fenice di fuoco che orbita",           Slot.EFFECT, AccessoryRarity.LEGENDARY, license = "CC0")
    )

    // ─── Lookup rapido ───────────────────────────────────────────

    private val catalogMap: Map<String, AccessoryDef> by lazy {
        CATALOG.associateBy { it.id }
    }

    fun getAccessoryDef(id: String): AccessoryDef? = catalogMap[id]

    fun getAccessoriesBySlot(slot: Slot): List<AccessoryDef> =
        CATALOG.filter { it.slot == slot }

    fun getAccessoriesByRarity(rarity: AccessoryRarity): List<AccessoryDef> =
        CATALOG.filter { it.rarity == rarity }

    // ─── Loot da uova ────────────────────────────────────────────

    /**
     * Genera accessori casuali ottenuti dalla schiusa di un uovo.
     * La rarità e il numero dipendono dalla rarità dell'uovo.
     */
    fun rollAccessoriesFromEgg(eggRarityId: String): List<AccessoryDef> {
        val random = java.util.Random()
        return when (eggRarityId) {
            "common" -> {
                // 40% chance di ottenere 1 accessorio COMMON
                if (random.nextFloat() < 0.4f) {
                    val pool = CATALOG.filter { it.rarity == AccessoryRarity.COMMON }
                    listOfNotNull(pool.randomOrNull())
                } else emptyList()
            }
            "uncommon" -> {
                // 1 accessorio COMMON o UNCOMMON
                val pool = CATALOG.filter {
                    it.rarity == AccessoryRarity.COMMON || it.rarity == AccessoryRarity.UNCOMMON
                }
                listOfNotNull(pool.randomOrNull())
            }
            "rare" -> {
                // 1-2 accessori fino a RARE
                val pool = CATALOG.filter {
                    it.rarity.ordinal <= AccessoryRarity.RARE.ordinal
                }
                val count = if (random.nextFloat() < 0.3f) 2 else 1
                pool.shuffled().take(count)
            }
            "epic" -> {
                // 2 accessori fino a EPIC
                val pool = CATALOG.filter {
                    it.rarity.ordinal <= AccessoryRarity.EPIC.ordinal
                }
                pool.shuffled().take(2)
            }
            "legendary" -> {
                // 2-3 accessori, tutti i livelli, almeno 1 EPIC+
                val count = if (random.nextFloat() < 0.4f) 3 else 2
                val premiumPool = CATALOG.filter {
                    it.rarity.ordinal >= AccessoryRarity.EPIC.ordinal
                }
                val generalPool = CATALOG.shuffled()
                val results = mutableListOf<AccessoryDef>()
                premiumPool.randomOrNull()?.let { results.add(it) }
                while (results.size < count) {
                    generalPool.randomOrNull()?.let {
                        if (it !in results) results.add(it)
                    }
                }
                results
            }
            else -> emptyList()
        }
    }

    // ─── Gestione inventario giocatore ───────────────────────────

    /** Carica l'inventario accessori dal JSON locale */
    fun loadInventory(context: Context): Pair<List<OwnedAccessory>, EquipLoadout> {
        val json = AvatarPersistenceManager.getLocalAccessories(context)
        return try {
            val root = JSONObject(json)
            val ownedArray = root.optJSONArray("owned") ?: JSONArray()
            val loadoutObj = root.optJSONObject("equipped") ?: JSONObject()

            val owned = (0 until ownedArray.length()).map {
                OwnedAccessory.fromJson(ownedArray.getJSONObject(it))
            }
            val loadout = EquipLoadout.fromJson(loadoutObj)

            Pair(owned, loadout)
        } catch (e: Exception) {
            Log.w(TAG, "Errore parsing inventario, reset", e)
            Pair(emptyList(), EquipLoadout())
        }
    }

    /** Salva l'inventario in locale e notifica sync */
    fun saveInventory(
        context: Context,
        owned: List<OwnedAccessory>,
        loadout: EquipLoadout
    ) {
        val root = JSONObject().apply {
            val ownedArray = JSONArray()
            owned.forEach { ownedArray.put(it.toJson()) }
            put("owned", ownedArray)
            put("equipped", loadout.toJson())
        }

        // Salva locale IMMEDIATAMENTE
        AvatarPersistenceManager.saveLocalAccessories(context, root.toString())

        // Sync cloud IN BACKGROUND
        AvatarSyncManager.notifyChange(context)

        Log.d(TAG, "Inventario salvato: ${owned.size} accessori, loadout=$loadout")
    }

    /** Aggiungi un accessorio all'inventario */
    fun addAccessory(context: Context, accessoryDef: AccessoryDef, sourceEggRarity: String) {
        val (owned, loadout) = loadInventory(context)
        val mutableOwned = owned.toMutableList()

        // Controlla se già posseduto (no duplicati)
        if (mutableOwned.any { it.accessoryId == accessoryDef.id }) {
            Log.d(TAG, "Accessorio ${accessoryDef.id} già posseduto, skip")
            return
        }

        mutableOwned.add(
            OwnedAccessory(
                accessoryId = accessoryDef.id,
                sourceEggRarity = sourceEggRarity
            )
        )

        saveInventory(context, mutableOwned, loadout)
        Log.d(TAG, "Aggiunto: ${accessoryDef.name} (${accessoryDef.slot.displayName})")
    }

    /** Equipa un accessorio nello slot corrispondente */
    fun equipAccessory(context: Context, accessoryId: String): Boolean {
        val def = getAccessoryDef(accessoryId) ?: return false
        val (owned, loadout) = loadInventory(context)

        // Verifica possesso
        if (owned.none { it.accessoryId == accessoryId }) {
            Log.w(TAG, "Non possiedi $accessoryId")
            return false
        }

        // Aggiorna loadout
        val newLoadout = when (def.slot) {
            Slot.HEAD   -> loadout.copy(headId = accessoryId)
            Slot.BODY   -> loadout.copy(bodyId = accessoryId)
            Slot.EFFECT -> loadout.copy(effectId = accessoryId)
        }

        // Aggiorna flag isEquipped
        val updatedOwned = owned.map { acc ->
            when {
                acc.accessoryId == accessoryId -> acc.copy(isEquipped = true)
                getAccessoryDef(acc.accessoryId)?.slot == def.slot -> acc.copy(isEquipped = false)
                else -> acc
            }
        }

        saveInventory(context, updatedOwned, newLoadout)
        Log.d(TAG, "Equipaggiato: ${def.name} in slot ${def.slot.displayName}")
        return true
    }

    /** Rimuovi un accessorio dallo slot */
    fun unequipSlot(context: Context, slot: Slot) {
        val (owned, loadout) = loadInventory(context)

        val newLoadout = when (slot) {
            Slot.HEAD   -> loadout.copy(headId = "")
            Slot.BODY   -> loadout.copy(bodyId = "")
            Slot.EFFECT -> loadout.copy(effectId = "")
        }

        val updatedOwned = owned.map { acc ->
            if (getAccessoryDef(acc.accessoryId)?.slot == slot)
                acc.copy(isEquipped = false)
            else acc
        }

        saveInventory(context, updatedOwned, newLoadout)
        Log.d(TAG, "Slot ${slot.displayName} svuotato")
    }

    /** Ritorna il loadout corrente */
    fun getCurrentLoadout(context: Context): EquipLoadout {
        val (_, loadout) = loadInventory(context)
        return loadout
    }
}
