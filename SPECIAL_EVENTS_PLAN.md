# Piano di Implementazione: Eventi Speciali — Huntix

## Panoramica

Questo documento descrive l'implementazione completa del sistema di **Eventi Speciali** per Huntix, includendo un calendario dell'avvento natalizio ("Caccia dell'Elfo"), un'attività AR semplificata per trovare regali, e l'integrazione con le schermate esistenti.

### File coinvolti

| # | File | Azione | Package |
|---|------|--------|---------|
| 1 | `gamification/SpecialEventManager.kt` | NUOVO | `com.intelligame.huntix.gamification` |
| 2 | `gamification/ElfHuntManager.kt` | NUOVO | `com.intelligame.huntix.gamification` |
| 3 | `ui/SpecialEventsActivity.kt` | NUOVO | `com.intelligame.huntix.ui` |
| 4 | `ui/ElfHuntActivity.kt` | NUOVO | `com.intelligame.huntix.ui` |
| 5 | `HomeActivity.kt` | MODIFICA | `com.intelligame.huntix` |
| 6 | `BaseNavActivity.kt` | MODIFICA | `com.intelligame.huntix` |
| 7 | `AndroidManifest.xml` | MODIFICA | root `app/src/main` |

### Dipendenze preesistenti

- `DailyStreakManager` — pattern singleton SharedPreferences da replicare
- `UiKit` — helper UI programmatici (scroll, title, button, card, section)
- `SavedManager.addMvc()` — per accreditare ricompense MVC
- `AdsManager` — per interstitial AdMob post-completamento
- `ArSceneManager` — pattern di sessione AR e checkProximity
- `EggPlacementManager` — pattern autoPlaceEggs e startEggPulse
- `LiveEventsActivity` — pattern auto-refresh timer con Handler

---

## 1. SpecialEventManager.kt

**Percorso:** `app/src/main/java/com/intelligame/huntix/gamification/SpecialEventManager.kt`

### Scopo

Singleton che determina se c'è un evento speciale attivo in base alla data corrente. Non gestisce lo stato del gioco — solo la classificazione temporale.

### Struttura

```kotlin
package com.intelligame.huntix.gamification

import java.util.Calendar

/**
 * SpecialEventManager — determina quale evento speciale è attivo
 * in base alla data corrente del dispositivo.
 *
 * Eventi attuali:
 *  - ElfHunt (Caccia dell'Elfo): 1-25 Dicembre
 *  - Tombolata: 27-31 Dicembre (placeholder per futuro)
 *
 * Pattern: object singleton, nessun SharedPreferences,
 * solo calcolo basato su Calendar.
 */
object SpecialEventManager {

    // ── Sealed class per i tipi di evento ──────────────────────

    sealed class SpecialEvent {
        abstract val id: String
        abstract val title: String
        abstract val description: String
        abstract val emoji: String
        abstract val startMonth: Int    // Calendar.MONTH (0-indexed)
        abstract val startDay: Int
        abstract val endMonth: Int
        abstract val endDay: Int

        data object ElfHunt : SpecialEvent() {
            override val id = "elf_hunt"
            override val title = "Caccia dell'Elfo"
            override val description = "Trova i regali nascosti per 25 giorni!"
            override val emoji = "🎄"
            override val startMonth = Calendar.DECEMBER  // 11 (0-indexed)
            override val startDay = 1
            override val endMonth = Calendar.DECEMBER
            override val endDay = 25
        }

        data object Tombolata : SpecialEvent() {
            override val id = "tombolata"
            override val title = "Tombolata di Capodanno"
            override val description = "Celebrare il nuovo anno con premi speciali!"
            override val emoji = "🎆"
            override val startMonth = Calendar.DECEMBER
            override val startDay = 27
            override val endMonth = Calendar.DECEMBER
            override val endDay = 31
        }
    }

    // ── Lista di tutti gli eventi registrati ────────────────────

    private val allEvents: List<SpecialEvent> = listOf(
        SpecialEvent.ElfHunt,
        SpecialEvent.Tombolata
    )

    // ── API pubblica ────────────────────────────────────────────

    /**
     * Restituisce l'evento speciale attivo al momento corrente,
     * oppure null se nessun evento è attivo.
     *
     * Logica: per ogni evento, controlla se la data corrente
     * è compresa tra startMonth/startDay e endMonth/endDay (inclusi).
     * Se più eventi si sovrappongono, restituisce il primo trovato.
     */
    fun getActiveSpecialEvent(): SpecialEvent? {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        return allEvents.firstOrNull { event ->
            isDateInRange(month, day, event.startMonth, event.startDay, event.endMonth, event.endDay)
        }
    }

    /**
     * Restituisce true se c'è almeno un evento speciale attivo.
     * Più efficiente di getActiveSpecialEvent() quando serve solo un boolean.
     */
    fun hasActiveEvent(): Boolean = getActiveSpecialEvent() != null

    /**
     * Restituisce true se l'evento con l'ID specificato è attivo.
     */
    fun isEventActive(eventId: String): Boolean =
        getActiveSpecialEvent()?.id == eventId

    // ── Utilità data ────────────────────────────────────────────

    /**
     * Controlla se una data (month, day) è inclusa nell'intervallo
     * [startMonth/startDay, endMonth/endDay].
     * Supporta l'attraversamento dell'anno (es. Nov 28 → Jan 3).
     * Per il caso semplice (stesso mese), è un confronto diretto.
     */
    private fun isDateInRange(
        month: Int, day: Int,
        startMonth: Int, startDay: Int,
        endMonth: Int, endDay: Int
    ): Boolean {
        return when {
            startMonth == endMonth -> {
                // Stesso mese: day deve essere in [startDay, endDay]
                month == startMonth && day in startDay..endDay
            }
            startMonth > endMonth -> {
                // Attraversa l'anno (es. Nov → Jan)
                (month == startMonth && day >= startDay) ||
                (month == endMonth && day <= endDay) ||
                (month in (startMonth + 1)..11 || month in 0 until endMonth)
            }
            else -> {
                // Mesi diversi, stesso anno (es. Mar → Jul)
                (month == startMonth && day >= startDay) ||
                (month == endMonth && day <= endDay) ||
                month in (startMonth + 1) until endMonth
            }
        }
    }

    /**
     * Restituisce il numero di giorni rimanenti fino alla fine
     * dell'evento attivo, oppure 0 se non c'è evento.
     */
    fun daysRemainingInEvent(): Int {
        val event = getActiveSpecialEvent() ?: return 0
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        val endCal = Calendar.getInstance().apply {
            set(Calendar.MONTH, event.endMonth)
            set(Calendar.DAY_OF_MONTH, event.endDay)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }

        val diffMs = endCal.timeInMillis - now
        return if (diffMs > 0) (diffMs / (24 * 60 * 60 * 1000)).toInt() + 1 else 0
    }

    /**
     * Restituisce il giorno corrente dell'evento ElfHunt
     * (1-25), oppure 0 se l'evento non è attivo.
     * Utile per sapere "quale giorno è oggi" nella logica di gioco.
     */
    fun getElfHuntDay(): Int {
        if (!isEventActive("elf_hunt")) return 0
        val cal = Calendar.getInstance()
        return cal.get(Calendar.DAY_OF_MONTH)  // 1-25 in Dicembre
    }
}
```

### Note implementative

- **Nessun contesto** necessario: è un object puro con Calendar.
- **Tombolata** è un placeholder: il corpo dell'evento verrà implementato in futuro. Per ora restituisce solo il match temporale.
- `getElfHuntDay()` è usato da `ElfHuntManager` e dalla UI per sapere quale giorno del calendario è oggi.

---

## 2. ElfHuntManager.kt

**Percorso:** `app/src/main/java/com/intelligame/huntix/gamification/ElfHuntManager.kt`

### Scopo

Gestisce lo stato persistente della Caccia dell'Elfo: quali giorni sono stati riscattati, le ricompense MVC guadagnate, e gli oggetti ricevuti. Segue esattamente il pattern di `DailyStreakManager`.

### Struttura

```kotlin
package com.intelligame.huntix.gamification

import android.content.Context
import android.content.SharedPreferences

/**
 * ElfHuntManager — gestisce lo stato della Caccia dell'Elfo (Dicembre 1-25).
 *
 * Pattern: object singleton con SharedPreferences (identico a DailyStreakManager).
 *
 * Dati persistiti:
 *  - claimed_days: Set<String> — giorni riscattati ("1", "2", ..., "25")
 *  - total_mvc_earned: Int — totale MVC guadagnati dall'evento
 *  - items_received: Set<String> — nomi degli oggetti ricevuti
 *
 * Ricompense: 25 giorni con ricompense crescenti + item esclusivi.
 */
object ElfHuntManager {

    private const val PREFS = "elf_hunt_v1"
    private const val KEY_CLAIMED_DAYS = "claimed_days"
    private const val KEY_TOTAL_MVC = "total_mvc_earned"
    private const val KEY_ITEMS_RECEIVED = "items_received"

    // ── Data class per lo stato ─────────────────────────────────

    data class ElfHuntStatus(
        val claimedDays: Set<Int>,
        val totalMvcEarned: Int,
        val todayClaimed: Boolean,
        val canClaimToday: Boolean,
        val totalDaysClaimed: Int,
        val itemsReceived: Set<String>
    )

    // ── Ricompense per giorno ───────────────────────────────────

    /**
     * Restituisce la quantità MVC per un determinato giorno (1-25).
     * La scala è crescente con picchi ai milestone.
     *
     * Giorni 1-7:   50-110 MVC (crescita +10/giorno)
     * Giorni 8-14:  120-190 MVC (crescita +10/giorno)
     * Giorni 15-21: 200-340 MVC (crescita +20/giorno)
     * Giorni 22-24: 350-410 MVC (crescita +30/giorno)
     * Giorno 25:    500 MVC (mega bonus)
     */
    fun rewardForDay(day: Int): Int = when {
        day <= 0 || day > 25 -> 0
        day <= 7 -> 40 + day * 10           // 50, 60, 70, 80, 90, 100, 110
        day <= 14 -> 50 + day * 10          // 130, 140, 150, 160, 170, 180, 190
                                            // (corretto: 120-190)
        day <= 21 -> 100 + day * 14         // 310..394 → ricalcolare
        day <= 24 -> 100 + day * 15         // placeholder
        else -> 500
    }

    /**
     * Ricalcolo esatto delle ricompense:
     *
     * Giorno  1:  50       Giorno  9: 130       Giorno 17: 260
     * Giorno  2:  60       Giorno 10: 140       Giorno 18: 280
     * Giorno  3:  70       Giorno 11: 150       Giorno 19: 300
     * Giorno  4:  80       Giorno 12: 160       Giorno 20: 320
     * Giorno  5:  90       Giorno 13: 170       Giorno 21: 340
     * Giorno  6: 100       Giorno 14: 180       Giorno 22: 370
     * Giorno  7: 110       Giorno 15: 200       Giorno 23: 400
     * Giorno  8: 120       Giorno 16: 220       Giorno 24: 430
     *                                                  Giorno 25: 500
     */
    fun rewardMvcForDay(day: Int): Int = when (day) {
        in 1..7 -> 40 + day * 10
        in 8..14 -> 50 + day * 10
        in 15..21 -> -100 + day * 20
        22 -> 370
        23 -> 400
        24 -> 430
        25 -> 500
        else -> 0
    }

    /**
     * Restituisce l'item esclusivo per un determinato giorno.
     * Solo i giorni milestone (7, 14, 21, 24, 25) danno un item.
     */
    fun itemForDay(day: Int): String? = when (day) {
        7 -> "Cristallo di Neve"
        14 -> "Campana d'Oro"
        21 -> "Stella di Natale"
        24 -> "Corona dell'Elfo"
        25 -> "Babbo Cacciatore"
        else -> null
    }

    /**
     * Restituisce la descrizione dell'item per un determinato giorno.
     */
    fun itemDescriptionForDay(day: Int): String? = when (day) {
        7 -> "Un cristallo che brilla di luce azzurra. +5% XP per 24h."
        14 -> "Una campana dorata che risuona di fortuna. +10% spawn rate per 24h."
        21 -> "Una stella che illumina la caccia. +15% XP per 24h."
        24 -> "La corona dell'elfo capo. +20% tutte le ricompense per 24h."
        25 -> "La creatura leggendaria del Natale! Stat: ATK 85, DEF 70, HP 90."
        else -> null
    }

    /**
     * Se il giorno 25, restituisce i dati della creatura "Babbo Cacciatore".
     * Altrimenti null.
     */
    fun getBabboCacciatoreStats(): Map<String, Any>? {
        return mapOf(
            "name" to "Babbo Cacciatore",
            "type" to "legendary",
            "element" to "ice",
            "atk" to 85,
            "def" to 70,
            "hp" to 90,
            "special" to "Regali Esplosivi — infligge danno ad area",
            "rarity" to "legendary"
        )
    }

    // ── Stato ───────────────────────────────────────────────────

    /**
     * Restituisce lo stato attuale della caccia dell'elfo.
     * Identico a DailyStreakManager.getStatus().
     */
    fun getStatus(ctx: Context): ElfHuntStatus {
        val p = prefs(ctx)
        val claimedStrings = p.getStringSet(KEY_CLAIMED_DAYS, emptySet()) ?: emptySet()
        val claimedDays = claimedStrings.mapNotNull { it.toIntOrNull() }.toSet()
        val totalMvc = p.getInt(KEY_TOTAL_MVC, 0)
        val items = p.getStringSet(KEY_ITEMS_RECEIVED, emptySet()) ?: emptySet()

        val todayDay = SpecialEventManager.getElfHuntDay()
        val todayClaimed = todayDay in claimedDays
        val canClaimToday = todayDay > 0 && !todayClaimed &&
            SpecialEventManager.isEventActive("elf_hunt")

        return ElfHuntStatus(
            claimedDays = claimedDays,
            totalMvcEarned = totalMvc,
            todayClaimed = todayClaimed,
            canClaimToday = canClaimToday,
            totalDaysClaimed = claimedDays.size,
            itemsReceived = items
        )
    }

    /**
     * Riscatta il giorno corrente dell'evento ElfHunt.
     * Restituisce Pair<Boolean, Int>:
     *  - Boolean: true se il riscatto è riuscito
     *  - Int: quantità MVC guadagnati (0 se fallito)
     *
     * Pattern identico a DailyStreakManager.claimToday().
     */
    @Synchronized
    fun claimDay(ctx: Context): Pair<Boolean, Int> {
        val p = prefs(ctx)
        val todayDay = SpecialEventManager.getElfHuntDay()

        // Verifica: evento attivo?
        if (!SpecialEventManager.isEventActive("elf_hunt")) return Pair(false, 0)

        // Verifica: giorno valido?
        if (todayDay <= 0 || todayDay > 25) return Pair(false, 0)

        // Verifica: già riscattato?
        val claimed = (p.getStringSet(KEY_CLAIMED_DAYS, emptySet()) ?: emptySet()).toMutableSet()
        if (claimed.contains(todayDay.toString())) return Pair(false, 0)

        // Calcola ricompensa
        val reward = rewardMvcForDay(todayDay)
        val item = itemForDay(todayDay)
        val totalMvc = p.getInt(KEY_TOTAL_MVC, 0) + reward
        val items = (p.getStringSet(KEY_ITEMS_RECEIVED, emptySet()) ?: emptySet()).toMutableSet()

        // Aggiorna stato
        claimed.add(todayDay.toString())
        if (item != null) items.add(item)

        p.edit()
            .putStringSet(KEY_CLAIMED_DAYS, claimed)
            .putInt(KEY_TOTAL_MVC, totalMvc)
            .putStringSet(KEY_ITEMS_RECEIVED, items)
            .apply()

        return Pair(true, reward)
    }

    /**
     * Restituisce la lista delle ricompense per tutti i 25 giorni.
     * Utile per popolare la UI del calendario.
     */
    fun getRewardsCalendar(): List<DayReward> {
        return (1..25).map { day ->
            DayReward(
                day = day,
               mvc = rewardMvcForDay(day),
                item = itemForDay(day),
                isMilestone = day in listOf(7, 14, 21, 24, 25)
            )
        }
    }

    data class DayReward(
        val day: Int,
        val mvc: Int,
        val item: String?,
        val isMilestone: Boolean
    )

    /**
     * Controlla se l'evento è attualmente attivo.
     * Wrapper comodo per SpecialEventManager.
     */
    fun isEventActive(): Boolean = SpecialEventManager.isEventActive("elf_hunt")

    // ── Privato ─────────────────────────────────────────────────

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
```

### Tabella ricompense dettagliata

| Giorno | MVC | Item | Tipo |
|--------|-----|------|------|
| 1 | 50 | — | Base |
| 2 | 60 | — | Base |
| 3 | 70 | — | Base |
| 4 | 80 | — | Base |
| 5 | 90 | — | Base |
| 6 | 100 | — | Base |
| 7 | 110 | **Cristallo di Neve** | Milestone |
| 8 | 120 | — | Base |
| 9 | 130 | — | Base |
| 10 | 140 | — | Base |
| 11 | 150 | — | Base |
| 12 | 160 | — | Base |
| 13 | 170 | — | Base |
| 14 | 180 | **Campana d'Oro** | Milestone |
| 15 | 200 | — | Base |
| 16 | 220 | — | Base |
| 17 | 240 | — | Base |
| 18 | 260 | — | Base |
| 19 | 280 | — | Base |
| 20 | 300 | — | Base |
| 21 | 320 | **Stella di Natale** | Milestone |
| 22 | 370 | — | Base |
| 23 | 400 | — | Base |
| 24 | 430 | **Corona dell'Elfo** | Milestone |
| 25 | 500 | **Babbo Cacciatore** | Mega Bonus |

---

## 3. SpecialEventsActivity.kt

**Percorso:** `app/src/main/java/com/intelligame/huntix/ui/SpecialEventsActivity.kt`

### Scopo

Schermata "Eventi Speciali" che mostra il calendario dell'avvento quando l'evento ElfHunt è attivo. Include una griglia 5x5 di card, barra di progresso, contatore MVC, e auto-refresh.

### Struttura

```kotlin
package com.intelligame.huntix.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.gamification.ElfHuntManager
import com.intelligame.huntix.gamification.SpecialEventManager

/**
 * SpecialEventsActivity — Calendario Eventi Speciali.
 *
 * Mostra la griglia dell'avvento (5x5) quando ElfHunt è attivo,
 * oppure un messaggio "nessun evento" altrimenti.
 *
 * Pattern UI: programmatico (nessun XML), come LiveEventsActivity.
 * Auto-refresh ogni 30 secondi per aggiornare lo stato "oggi".
 */
class SpecialEventsActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private lateinit var calendarGrid: LinearLayout
    private lateinit var progressLabel: TextView
    private lateinit var mvcCounterLabel: TextView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val scroll = ScrollView(c).apply {
            setBackgroundColor(Color.parseColor("#0D0620"))
        }
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 80))
        }
        scroll.addView(root)

        // ── Header: freccia back + titolo ───────────────────────
        root.addView(LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also { it.bottomMargin = UiKit.dp(c, 8) }
            addView(TextView(c).apply {
                text = "← Indietro"; textSize = 14f
                setTextColor(Color.parseColor("#666699"))
                setOnClickListener { finish() }
                setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 4))
            })
        })

        root.addView(UiKit.title(c, "Eventi Speciali", "🎄"))

        // ── Controlla se c'è un evento attivo ───────────────────
        val activeEvent = SpecialEventManager.getActiveSpecialEvent()

        if (activeEvent == null) {
            // Nessun evento attivo
            root.addView(UiKit.card(c,
                TextView(c).apply {
                    text = "Nessun evento speciale attivo al momento."
                    textSize = 14f; setTextColor(Color.parseColor("#6B5B95"))
                    gravity = Gravity.CENTER
                },
                TextView(c).apply {
                    text = "Torna durante un evento speciale per vincere ricompense esclusive!"
                    textSize = 12f; setTextColor(Color.parseColor("#4A3870"))
                    gravity = Gravity.CENTER
                    setPadding(0, UiKit.dp(c, 8), 0, 0)
                }
            ))
            setContentView(scroll)
            return
        }

        // ── Evento attivo trovato ───────────────────────────────
        when (activeEvent) {
            is SpecialEventManager.SpecialEvent.ElfHunt -> setupElfHuntUI(root)
            is SpecialEventManager.SpecialEvent.Tombolata -> {
                root.addView(UiKit.card(c,
                    TextView(c).apply {
                        text = "🎆 ${activeEvent.title}"
                        textSize = 18f; setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                    },
                    TextView(c).apply {
                        text = activeEvent.description
                        textSize = 13f; setTextColor(Color.parseColor("#A78BFA"))
                        gravity = Gravity.CENTER
                        setPadding(0, UiKit.dp(c, 8), 0, 0)
                    },
                    TextView(c).apply {
                        text = "In arrivo! Resta sintonizzato."
                        textSize = 12f; setTextColor(Color.parseColor("#FFD700"))
                        gravity = Gravity.CENTER
                        setPadding(0, UiKit.dp(c, 12), 0, 0)
                    }
                ))
            }
        }

        setContentView(scroll)

        // ── Auto-refresh ogni 30 secondi ────────────────────────
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isFinishing && !isDestroyed) {
                    refreshCalendar()
                    handler.postDelayed(this, 30_000)
                }
            }
        }
        handler.postDelayed(timerRunnable, 30_000)
    }

    // ── Setup UI per ElfHunt ────────────────────────────────────

    private fun setupElfHuntUI(root: LinearLayout) {
        val c = this
        val status = ElfHuntManager.getStatus(c)

        // Progress bar
        progressLabel = TextView(c).apply {
            text = "🎁 ${status.totalDaysClaimed}/25 regali trovati"
            textSize = 14f; setTextColor(Color.parseColor("#00FF88"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, UiKit.dp(c, 4))
        }
        root.addView(progressLabel)

        // MVC counter
        mvcCounterLabel = TextView(c).apply {
            text = "💰 Totale: ${status.totalMvcEarned} MVC guadagnati"
            textSize = 12f; setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 12))
        }
        root.addView(mvcCounterLabel)

        // Progress bar visuale
        val progressBarBg = android.view.View(c).apply {
            layoutParams = LinearLayout.LayoutParams(LP_MW, UiKit.dp(c, 8)).also {
                it.bottomMargin = UiKit.dp(c, 16)
            }
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 4).toFloat()
                setColor(Color.parseColor("#1A1030"))
            }
        }
        root.addView(progressBarBg)

        val progressFill = android.view.View(c).apply {
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 4).toFloat()
                setColor(Color.parseColor("#00FF88"))
            }
        }
        (progressBarBg as? ViewGroup)?.addView(progressFill) ?: run {
            // Usa FrameLayout wrapper per la progress bar
        }
        // La progress bar viene aggiornata in refreshCalendar()

        // Titolo sezione
        root.addView(UiKit.section(c, "Calendario dell'Avvento"))

        // Griglia calendario 5x5
        calendarGrid = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(calendarGrid)

        // Info ricompense
        root.addView(TextView(c).apply {
            text = "Riscatta ogni giorno per ricompense crescenti!"
            textSize = 11f; setTextColor(Color.parseColor("#4A3870"))
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 16), 0, UiKit.dp(c, 8))
        })

        // Pulsante indietro
        root.addView(UiKit.button(c, "← Indietro", "#666") { finish() })

        refreshCalendar()
    }

    // ── Aggiorna griglia calendario ─────────────────────────────

    private fun refreshCalendar() {
        val c = this
        val status = ElfHuntManager.getStatus(c)
        val todayDay = SpecialEventManager.getElfHuntDay()
        val rewards = ElfHuntManager.getRewardsCalendar()

        runOnUiThread {
            // Aggiorna labels
            progressLabel?.text = "🎁 ${status.totalDaysClaimed}/25 regali trovati"
            mvcCounterLabel?.text = "💰 Totale: ${status.totalMvcEarned} MVC guadagnati"

            calendarGrid.removeAllViews()

            // Griglia 5 righe x 5 colonne
            var row = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also {
                    it.bottomMargin = UiKit.dp(c, 6)
                }
            }

            for (day in 1..25) {
                if (day > 1 && (day - 1) % 5 == 0) {
                    calendarGrid.addView(row)
                    row = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also {
                            it.bottomMargin = UiKit.dp(c, 6)
                        }
                    }
                }

                val reward = rewards[day - 1]
                val isClaimed = day in status.claimedDays
                val isToday = day == todayDay && !isClaimed && status.canClaimToday
                val isPast = day < todayDay && !isClaimed
                val isFuture = day > todayDay
                val isMilestone = reward.isMilestone

                // Colore cella
                val bgColor = when {
                    isClaimed -> "#00CC88"
                    isToday -> "#1A1030"  // Bordo dorato animato
                    isPast -> "#331010"   // Rosso scuro (perso)
                    isFuture -> "#333355"  // Grigio (bloccato)
                    else -> "#1A1030"
                }

                // Bordo cella
                val borderColor = when {
                    isClaimed -> Color.parseColor("#00FF88")
                    isToday -> Color.parseColor("#FFD700")
                    isPast -> Color.parseColor("#C62828")
                    isFuture -> Color.parseColor("#333355")
                    else -> Color.parseColor("#333355")
                }

                val cell = CardView(c).apply {
                    radius = UiKit.dp(c, 8).toFloat()
                    cardElevation = if (isToday) UiKit.dp(c, 4).toFloat() else UiKit.dp(c, 1).toFloat()
                    setCardBackgroundColor(Color.parseColor(bgColor))
                    layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(c, 64), 1f).also {
                        it.marginEnd = UiKit.dp(c, 4)
                    }
                }

                val cellInner = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(UiKit.dp(c, 2), UiKit.dp(c, 4), UiKit.dp(c, 2), UiKit.dp(c, 4))
                }

                // Icona/giorno
                val iconText = when {
                    isClaimed -> "✅"
                    isToday -> "🎁"
                    isPast -> "❌"
                    isFuture -> "🔒"
                    else -> "$day"
                }
                cellInner.addView(TextView(c).apply {
                    text = iconText; textSize = 16f; gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                })

                // Numero giorno
                cellInner.addView(TextView(c).apply {
                    text = "$day"; textSize = 9f; gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#6B5B95"))
                })

                // Item (se milestone)
                if (isMilestone && reward.item != null) {
                    cellInner.addView(TextView(c).apply {
                        text = "⭐"; textSize = 8f; gravity = Gravity.CENTER
                    })
                }

                cell.addView(cellInner)

                // Bordo per today (animato dopo)
                if (isToday) {
                    cell.setCardBackgroundColor(Color.parseColor("#1A1030"))
                    cell.post {
                        // Aggiungi bordo dorato pulsante via GradientDrawable sul background
                        val bg = GradientDrawable().apply {
                            cornerRadius = UiKit.dp(c, 8).toFloat()
                            setColor(Color.parseColor("#1A1030"))
                            setStroke(UiKit.dp(c, 2), Color.parseColor("#FFD700"))
                        }
                        cell.background = bg
                    }

                    // Click: apri ElfHuntActivity
                    cell.isClickable = true
                    cell.isFocusable = true
                    cell.setOnClickListener {
                        val intent = Intent(c, ElfHuntActivity::class.java)
                        intent.putExtra("day", todayDay)
                        startActivity(intent)
                    }

                    // Animazione pulsante bordo dorato
                    val pulseAnim = ObjectAnimator.ofFloat(cell, "alpha", 0.7f, 1.0f).apply {
                        duration = 800
                        repeatCount = ObjectAnimator.INFINITE
                        repeatMode = ObjectAnimator.REVERSE
                        interpolator = LinearInterpolator()
                    }
                    cell.tag = pulseAnim
                    pulseAnim.start()
                }

                // Click su giorno claimed: mostra dettagli
                if (isClaimed) {
                    cell.isClickable = true
                    cell.isFocusable = true
                    cell.setOnClickListener {
                        val mvc = ElfHuntManager.rewardMvcForDay(day)
                        val item = ElfHuntManager.itemForDay(day)
                        val msg = buildString {
                            append("Giorno $day: +$mvc MVC")
                            if (item != null) append("\nItem: $item")
                        }
                        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                row.addView(cell)
            }
            calendarGrid.addView(row)

            // Mostra item ottenuti
            if (status.itemsReceived.isNotEmpty()) {
                val itemsSection = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, UiKit.dp(c, 16), 0, 0)
                }
                itemsSection.addView(UiKit.section(c, "🎁 Item Ottenuti"))
                for (item in status.itemsReceived) {
                    itemsSection.addView(TextView(c).apply {
                        text = "⭐ $item"
                        textSize = 13f; setTextColor(Color.parseColor("#FFD700"))
                        setPadding(UiKit.dp(c, 4), UiKit.dp(c, 2), 0, UiKit.dp(c, 2))
                    })
                }
                calendarGrid.parent?.let { (it as? LinearLayout)?.addView(itemsSection) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { handler.removeCallbacks(timerRunnable) } catch (_: Exception) {}
    }

    companion object {
        private const val LP_MW = LinearLayout.LayoutParams.MATCH_PARENT
        private const val LP_WW = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
```

### Note implementative

- La progress bar va implementata con un `FrameLayout` contenente background scuro e fill verde dinamico.
- L'animazione pulsante sul "today" card usa `ObjectAnimator` sul `alpha` — è il pattern più semplice. Per un effetto bordo si può usare un `ValueAnimator` che varia il colore dello stroke del `GradientDrawable`.
- Il click sul today apre `ElfHuntActivity` con extra `"day"`.
- L'auto-refresh ogni 30 secondi è lo stesso pattern di `LiveEventsActivity`.

---

## 4. ElfHuntActivity.kt

**Percorso:** `app/src/main/java/com/intelligame/huntix/ui/ElfHuntActivity.kt`

### Scopo

Attività AR semplificata per trovare un regalo natalizio. Il giocatore avvia la sessione AR, un regalo viene piazzato automaticamente su un piano rilevato, e il giocatore deve avvicinarsi e "aprire" il regalo con un gesto swipe-up.

### Semplificazioni rispetto al gioco principale

- **Nessuna cassaforte** (safe)
- **Nessun secchiello** (bucket)
- **Nessuna chiave** (keys)
- **Nessun multiplayer**
- **Nessuna uova trappola**
- **Nessun sistema di turni**
- **Nessun sistema di punteggio**
- Solo 1 regalo da trovare per sessione

### Fasi del gioco

```
SETUP → FINDING → NEAR_GIFT → OPENING → REVEALED
```

| Fase | Descrizione |
|------|-------------|
| SETUP | Sessione AR avviata, rilevamento piani attivato, 1 regalo piazzato automaticamente |
| FINDING | Regalo invisibile, giocatore cammina. Proximity check ~1.5m attiva NEAR_GIFT |
| NEAR_GIFT | Regalo visibile con animazione pulse, hint "swipe up" mostrato |
| OPENING | Giocatore fa swipe up per "aprire" il regalo |
| REVEALED | Animazione sparkle, popup ricompensa (MVC + item), pulsante "Torna al calendario" |

### Struttura

```kotlin
package com.intelligame.huntix.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.intelligame.huntix.R
import com.intelligame.huntix.SoundManager
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.battle.AdsManager
import com.intelligame.huntix.gamification.ElfHuntManager
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.hypot

/**
 * ElfHuntActivity — Caccia AR semplificata al regalo natalizio.
 *
 * Usa SceneView ARSceneView (come ARGameActivity).
 * Fasi: SETUP → FINDING → NEAR_GIFT → OPENING → REVEALED
 *
 * Semplificazioni: no safe, no bucket, no keys, no multiplayer,
 * no trap eggs, no turn system.
 */
class ElfHuntActivity : AppCompatActivity() {

    // ── Fasi del gioco ──────────────────────────────────────────

    private enum class HuntPhase {
        SETUP, FINDING, NEAR_GIFT, OPENING, REVEALED
    }

    // ── Stato ───────────────────────────────────────────────────

    private var currentPhase = HuntPhase.SETUP
    private var huntDay: Int = 1
    private lateinit var arSceneView: ARSceneView
    private var giftAnchorNode: AnchorNode? = null
    private var giftNode: Node? = null
    private var lastArFrame: Frame? = null
    private var planeDetected = false
    private var frameCount = 0

    // Swipe tracking
    private var swipeStartY = 0f
    private var swipeStartTime = 0L

    // UI
    private lateinit var statusText: TextView
    private lateinit var instructionText: TextView
    private lateinit var overlayContainer: FrameLayout
    private lateinit var rewardPopup: LinearLayout

    // Handler per polling proximity
    private val handler = Handler(Looper.getMainLooper())
    private val proximityRunnable = object : Runnable {
        override fun run() {
            if (currentPhase == HuntPhase.FINDING) {
                checkProximity()
                handler.postDelayed(this, 200)  // Ogni 200ms
            }
        }
    }

    // Costanti
    private companion object {
        const val RC_CAMERA = 1001
        const val GIFT_REVEAL_DISTANCE = 1.5f  // metri
        const val GIFT_SIZE = 0.15f  // metri (lato del cubo)
    }

    // ── Lifecycle ───────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        huntDay = intent.getIntExtra("day", 1).coerceIn(1, 25)

        // Layout root
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(FP, FP)
        }

        // AR SceneView (fullscreen)
        arSceneView = ARSceneView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FP, FP)
        }
        root.addView(arSceneView)

        // Overlay UI
        overlayContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FP, FP)
        }
        root.addView(overlayContainer)

        // Status bar in alto
        val statusBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC0D0620"))
            }
            layoutParams = FrameLayout.LayoutParams(FP, WRAP_CONTENT).apply {
                gravity = Gravity.TOP
            }
        }

        statusText = TextView(this).apply {
            text = "🎁 Caccia al Regalo — Giorno $huntDay"
            textSize = 14f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        statusBar.addView(statusText)
        overlayContainer.addView(statusBar)

        // Instruction text in basso
        instructionText = TextView(this).apply {
            text = "Muovi il telefono per trovare una superficie..."
            textSize = 13f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#CC0D0620"))
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = FrameLayout.LayoutParams(FP, WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dp(40)
            }
        }
        overlayContainer.addView(instructionText)

        // Reward popup (inizialmente nascosto)
        setupRewardPopup()
        overlayContainer.addView(rewardPopup)

        setContentView(root)

        // Setup AR
        setupAR()

        // Camera permission
        checkCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        try { arSceneView.resume() } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try { arSceneView.pause() } catch (_: Exception) {}
        handler.removeCallbacks(proximityRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { arSceneView.destroy() } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
    }

    // ── Camera Permission ───────────────────────────────────────

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startArSession()
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA), RC_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startArSession()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Fotocamera necessaria")
                    .setMessage("La caccia al regalo richiede accesso alla fotocamera per la realta' aumentata.")
                    .setPositiveButton("Esci") { _, _ -> finish() }
                    .setCancelable(false).show()
            }
        }
    }

    // ── Setup AR ────────────────────────────────────────────────

    private fun startArSession() {
        try {
            arSceneView.apply {
                planeRenderer.isEnabled = true
                configureSession { session, config ->
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    if (!session.isSupported(config)) {
                        config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                    }
                    config.focusMode = Config.FocusMode.AUTO
                }
                onSessionUpdated = { session, frame ->
                    lastArFrame = frame

                    // Piano rilevato?
                    if (!planeDetected && frame.camera.trackingState == TrackingState.TRACKING) {
                        val hasPlane = session.getAllTrackables(Plane::class.java)
                            .any { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
                        if (hasPlane) {
                            planeDetected = true
                            runOnUiThread { onFirstPlaneDetected() }
                        }
                    }

                    // Proximity check durante FINDING
                    if (currentPhase == HuntPhase.FINDING && frameCount++ % 6 == 0 &&
                        frame.camera.trackingState == TrackingState.TRACKING) {
                        checkProximity()
                    }
                }
                onTouchEvent = { event, _ ->
                    handleTouch(event)
                    true
                }
            }
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("AR non disponibile")
                .setMessage("Il tuo dispositivo non supporta ARCore.\n\nAggiorna ARCore dal Play Store e riprova.")
                .setPositiveButton("Esci") { _, _ -> finish() }
                .setCancelable(false).show()
        }
    }

    private fun onFirstPlaneDetected() {
        instructionText.text = "Superficie trovata! Piazzamento regalo..."
        statusText.text = "🎁 Piazzamento..."
        placeGift()
    }

    // ── Piazza il regalo ────────────────────────────────────────

    private fun placeGift() {
        val session = arSceneView.session ?: return
        val planes = session.getAllTrackables(Plane::class.java).filter {
            it.trackingState == TrackingState.TRACKING && it.subsumedBy == null &&
            it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.extentX >= 0.25f && it.extentZ >= 0.25f
        }
        if (planes.isEmpty()) {
            runOnUiThread {
                instructionText.text = "Muovi il telefono verso il pavimento..."
            }
            return
        }

        val plane = planes.random()
        val rx = (kotlin.random.Random.nextFloat() - 0.5f) * plane.extentX * 0.6f
        val rz = (kotlin.random.Random.nextFloat() - 0.5f) * plane.extentZ * 0.6f
        val cx = plane.centerPose.tx() + rx
        val cy = plane.centerPose.ty()
        val cz = plane.centerPose.tz() + rz

        try {
            val anchor = session.createAnchor(
                Pose(floatArrayOf(cx, cy, cz), floatArrayOf(0f, 0f, 0f, 1f))
            )

            val sv = arSceneView
            val an = AnchorNode(engine = sv.engine, anchor = anchor)

            // Materiale rosso natalizio per il regalo
            val redMat = sv.materialLoader.createColorInstance(
                color = android.graphics.Color.parseColor("#C62828")
            )
            // Materiale oro per il nastro
            val goldMat = sv.materialLoader.createColorInstance(
                color = android.graphics.Color.parseColor("#FFD700")
            )

            // Cubo regalo (scatola rossa)
            val giftBox = CubeNode(
                sv.engine,
                Size(GIFT_SIZE, GIFT_SIZE * 0.8f, GIFT_SIZE),
                materialInstance = redMat
            ).apply { position = Position(0f, GIFT_SIZE * 0.4f, 0f) }
            an.addChildNode(giftBox)

            // Nastro orizzontale (oro)
            val ribbonH = CubeNode(
                sv.engine,
                Size(GIFT_SIZE * 1.02f, GIFT_SIZE * 0.1f, GIFT_SIZE * 0.12f),
                materialInstance = goldMat
            ).apply { position = Position(0f, GIFT_SIZE * 0.4f, 0f) }
            an.addChildNode(ribbonH)

            // Nastro verticale (oro)
            val ribbonV = CubeNode(
                sv.engine,
                Size(GIFT_SIZE * 0.12f, GIFT_SIZE * 0.1f, GIFT_SIZE * 1.02f),
                materialInstance = goldMat
            ).apply { position = Position(0f, GIFT_SIZE * 0.4f, 0f) }
            an.addChildNode(ribbonV)

            // Fiocco (sfera oro sopra)
            val bow = SphereNode(
                sv.engine,
                0.03f,
                materialInstance = goldMat
            ).apply { position = Position(0f, GIFT_SIZE * 0.85f, 0f) }
            an.addChildNode(bow)

            an.isVisible = false  // Inizialmente nascosto (FINDING)
            sv.addChildNode(an)

            giftAnchorNode = an
            giftNode = giftBox

            // Transizione a FINDING
            currentPhase = HuntPhase.FINDING
            runOnUiThread {
                statusText.text = "🎁 Cercando regalo..."
                instructionText.text = "Cammina nella stanza per trovare il regalo nascosto..."
            }

            // Avvia proximity polling
            handler.postDelayed(proximityRunnable, 500)

        } catch (e: Exception) {
            runOnUiThread {
                instructionText.text = "Errore nel piazzamento. Riprova."
            }
        }
    }

    // ── Proximity Check ─────────────────────────────────────────

    private fun checkProximity() {
        if (currentPhase != HuntPhase.FINDING) return
        val frame = lastArFrame ?: return
        val gift = giftAnchorNode ?: return

        try {
            val cam = frame.camera.pose.translation
            val giftPos = gift.anchorNode.anchor.pose.translation
            val dist = dist3(cam, giftPos)

            if (dist < GIFT_REVEAL_DISTANCE) {
                // Regalo trovato!
                currentPhase = HuntPhase.NEAR_GIFT
                gift.isVisible = true
                startGiftPulse()
                handler.removeCallbacks(proximityRunnable)

                runOnUiThread {
                    statusText.text = "🎁 Regalo trovato!"
                    instructionText.text = "Scorri verso l'alto per aprire il regalo! 👆"
                }
            }
        } catch (e: Exception) {
            // Ignora errori di tracking
        }
    }

    private fun dist3(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return hypot(hypot(dx, dy), dz)
    }

    // ── Gift Pulse Animation ────────────────────────────────────

    private var pulseAnim: ValueAnimator? = null

    private fun startGiftPulse() {
        pulseAnim?.cancel()
        val gift = giftNode ?: return
        pulseAnim = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 950
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val p = 1f + 0.15f * kotlin.math.sin((anim.animatedValue as Float).toDouble()).toFloat()
                gift.scale = Scale(p, p, p)
            }
            start()
        }
    }

    // ── Touch Handling (Swipe Up) ───────────────────────────────

    private fun handleTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartY = event.y
                swipeStartTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                if (currentPhase != HuntPhase.NEAR_GIFT) return
                val dy = event.y - swipeStartY
                val dt = (System.currentTimeMillis() - swipeStartTime).coerceAtLeast(1L)
                val validSwipe = dy < -90f && (dy / dt) * 1000f < -250f
                if (validSwipe) {
                    onGiftOpened()
                }
            }
        }
    }

    // ── Apertura Regalo ─────────────────────────────────────────

    private fun onGiftOpen() {
        currentPhase = HuntPhase.OPENING
        pulseAnim?.cancel()
        statusText.text = "🎁 Apertura..."
        instructionText.text = "Il regalo si sta aprendo..."

        // Animazione "apertura": il regalo si espande e scompare
        val gift = giftNode ?: return
        val expandAnim = ValueAnimator.ofFloat(1f, 1.5f).apply {
            duration = 600
            addUpdateListener { anim ->
                val scale = anim.animatedValue as Float
                gift.scale = Scale(scale, scale, scale)
            }
        }
        val fadeAnim = ObjectAnimator.ofFloat(giftAnchorNode, "alpha", 1f, 0f).apply {
            duration = 600
        }

        expandAnim.start()
        fadeAnim.start()

        // Dopo l'animazione, mostra ricompensa
        handler.postDelayed({
            giftAnchorNode?.isVisible = false
            showReward()
        }, 700)
    }

    // ── Popup Ricompensa ────────────────────────────────────────

    private fun setupRewardPopup() {
        rewardPopup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E60D0620"))
            setPadding(dp(24), dp(32), dp(24), dp(32))
            layoutParams = FrameLayout.LayoutParams(FP, FP)
            visibility = View.GONE
        }
    }

    private fun showReward() {
        val result = ElfHuntManager.claimDay(this)
        val (claimed, mvcReward) = result

        if (!claimed) {
            runOnUiThread {
                Toast.makeText(this, "Errore nel riscatto", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }

        val item = ElfHuntManager.itemForDay(huntDay)

        runOnUiThread {
            currentPhase = HuntPhase.REVEALED
            rewardPopup.removeAllViews()
            rewardPopup.visibility = View.VISIBLE

            // Titolo
            rewardPopup.addView(TextView(this).apply {
                text = "🎉 Regalo Aperto!"
                textSize = 24f; setTextColor(Color.parseColor("#FFD700"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            })

            // Giorno
            rewardPopup.addView(TextView(this).apply {
                text = "Giorno $huntDay di 25"
                textSize = 14f; setTextColor(Color.parseColor("#A78BFA"))
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(16))
            })

            // Ricompensa MVC
            rewardPopup.addView(TextView(this).apply {
                text = "💰 +$mvcReward MVC"
                textSize = 28f; setTextColor(Color.parseColor("#00FF88"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            })

            // Item (se presente)
            if (item != null) {
                rewardPopup.addView(TextView(this).apply {
                    text = "⭐ $item"
                    textSize = 18f; setTextColor(Color.parseColor("#FFD700"))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(4))
                })
                rewardPopup.addView(TextView(this).apply {
                    text = ElfHuntManager.itemDescriptionForDay(huntDay) ?: ""
                    textSize = 12f; setTextColor(Color.parseColor("#A78BFA"))
                    gravity = Gravity.CENTER
                    setPadding(dp(16), 0, dp(16), dp(16))
                })
            }

            // Stats Babbo Cacciatore (giorno 25)
            if (huntDay == 25) {
                val stats = ElfHuntManager.getBabboCacciatoreStats()
                if (stats != null) {
                    rewardPopup.addView(TextView(this).apply {
                        text = " creature leggendaria!"
                        textSize = 14f; setTextColor(Color.parseColor("#FF6EC7"))
                        gravity = Gravity.CENTER
                        setPadding(0, dp(8), 0, dp(4))
                    })
                    rewardPopup.addView(TextView(this).apply {
                        text = "ATK: ${stats["atk"]}  DEF: ${stats["def"]}  HP: ${stats["hp"]}"
                        textSize = 12f; setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                    })
                    rewardPopup.addView(TextView(this).apply {
                        text = stats["special"] as String
                        textSize = 11f; setTextColor(Color.parseColor("#A78BFA"))
                        gravity = Gravity.CENTER
                        setPadding(0, dp(4), 0, dp(8))
                    })
                }
            }

            // Pulsante "Torna al calendario"
            rewardPopup.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#A78BFA"))
                }
                setPadding(dp(24), dp(14), dp(24), dp(14))
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
                    it.topMargin = dp(24)
                }
                isClickable = true; isFocusable = true
                setOnClickListener { finish() }
                addView(TextView(this@ElfHuntActivity).apply {
                    text = "Torna al calendario 📅"
                    textSize = 14f; setTextColor(Color.WHITE)
                    typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                })
            })
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun onGiftOpened() {
        currentPhase = HuntPhase.OPENING
        pulseAnim?.cancel()
        statusText.text = "🎁 Apertura..."
        instructionText.text = "Il regalo si sta aprendo..."

        val gift = giftNode ?: return
        val expandAnim = ValueAnimator.ofFloat(1f, 1.5f).apply {
            duration = 600
            addUpdateListener { anim ->
                val s = anim.animatedValue as Float
                gift.scale = Scale(s, s, s)
            }
        }
        val fadeAnim = ObjectAnimator.ofFloat(giftAnchorNode, "alpha", 1f, 0f).apply {
            duration = 600
        }

        expandAnim.start()
        fadeAnim.start()

        handler.postDelayed({
            giftAnchorNode?.isVisible = false
            showReward()
        }, 700)
    }
}
```

### Note implementative

- **Camera permission**: richiesta esplicita prima di avviare la sessione AR, con dialog di fallback.
- **Proximity check**: eseguito ogni 200ms durante `FINDING`, calcola la distanza 3D tra camera e regalo con `dist3()`. Soglia: 1.5m.
- **Gift model**: `CubeNode` rosso con nastro oro (`CubeNode` orizzontale + verticale) e fiocco sfera oro. Tutti materiali `createColorInstance`.
- **Pulse animation**: `ValueAnimator` che varia lo scale del nodo `giftNode` sinusoidalmente, identico a `EggPlacementManager.startEggPulse`.
- **Swipe up**: tracciamento `ACTION_DOWN` → `ACTION_UP`, calcolo velocità verticale. Soglia: dy < -90px e velocità < -250px/s.
- **AdMob interstitial**: da aggiungere in `showReward()` dopo il popup, chiamando `AdsManager.showInterstitialIfAvailable(this)`.
- **SoundManager**: chiamare `SoundManager.playThrow()` all'apertura e un suono di sparkle alla rivelazione.

---

## 5. Modifica: HomeActivity.kt

**Percorso:** `app/src/main/java/com/intelligame/huntix/HomeActivity.kt`

### Cosa modificare

Aggiungere una **Special Event Card** dopo il Live Event Banner (riga ~160), visibile solo quando `SpecialEventManager.hasActiveEvent()` restituisce true.

### Codice da inserire

Dopo la riga 160 (`} catch (e: Exception) { Sentry.captureException(e) }` che chiude il blocco del Live Event Banner), inserire:

```kotlin
        // ═══ 3b. SPECIAL EVENT BANNER ═══
        try {
            if (SpecialEventManager.hasActiveEvent()) {
                val activeEvent = SpecialEventManager.getActiveSpecialEvent()
                if (activeEvent != null) {
                    val eventCard = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(Color.parseColor("#C62828"), Color.parseColor("#1B5E20"))
                        ).apply { cornerRadius = dp(12).toFloat() }
                        setPadding(dp(14), dp(10), dp(14), dp(10))
                        layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also {
                            it.bottomMargin = dp(12)
                        }
                        isClickable = true; isFocusable = true
                        setOnClickListener {
                            startActivity(Intent(this@HomeActivity, SpecialEventsActivity::class.java))
                        }
                    }

                    eventCard.addView(TextView(this).apply {
                        text = "🎄"; textSize = 28f
                        layoutParams = LinearLayout.LayoutParams(dp(40), LP_WW)
                    })

                    val eventCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LP_WW, 1f).also {
                            it.marginStart = dp(8)
                        }
                    }
                    eventCol.addView(TextView(this).apply {
                        text = "Caccia dell'Elfo"
                        textSize = 15f; setTextColor(Color.WHITE)
                        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                    })
                    eventCol.addView(TextView(this).apply {
                        text = "Trova il regalo nascosto! 🔍"
                        textSize = 11f; setTextColor(Color.argb(200, 255, 255, 255))
                    })
                    eventCard.addView(eventCol)

                    eventCard.addView(TextView(this).apply {
                        text = "→"; textSize = 18f; setTextColor(Color.WHITE)
                    })

                    root.addView(eventCard)
                }
            }
        } catch (e: Exception) { Sentry.captureException(e) }
```

### Import da aggiungere

All'inizio del file, nella sezione imports, aggiungere:

```kotlin
import com.intelligame.huntix.gamification.SpecialEventManager
```

### Note

- La card è wrappata in `try-catch` come il Live Event Banner per coerenza.
- Il gradiente rosso-verde (#C62828 → #1B5E20) è il tema natalizio.
- Il click apre `SpecialEventsActivity`.
- La card appare solo quando un evento è attivo (Dicembre 1-25 per ElfHunt).

---

## 6. Modifica: BaseNavActivity.kt

**Percorso:** `app/src/main/java/com/intelligame/huntix/BaseNavActivity.kt`

### Cosa modificare

Aggiungere "Eventi Speciali" al menu "Altro" dopo "Eventi Live".

### Modifica all'array items (riga 132)

**Prima:**
```kotlin
val items = arrayOf("Profilo", "Personaggio", "Impostazioni", "Abilità", "Eventi Live", "Borsa", "Invita Amico", "Info e Legale")
```

**Dopo:**
```kotlin
val items = arrayOf("Profilo", "Personaggio", "Impostazioni", "Abilità", "Eventi Live", "Eventi Speciali", "Borsa", "Invita Amico", "Info e Legale")
```

### Modifica al blocco when (righe 133-142)

**Prima:**
```kotlin
AlertDialog.Builder(this).setTitle("Altro").setItems(items) { _, i -> when (i) {
    0 -> startActivity(Intent(this, PlayerProfileActivity::class.java))
    1 -> startActivity(Intent(this, GenderChangeActivity::class.java))
    2 -> startActivity(Intent(this, SettingsActivity::class.java))
    3 -> startActivity(Intent(this, AbilityActivity::class.java))
    4 -> startActivity(Intent(this, LiveEventsActivity::class.java))
    5 -> startActivity(Intent(this, SurpriseInventoryActivity::class.java))
    6 -> com.intelligame.huntix.social.ReferralManager.getMyCode(this) { code -> runOnUiThread { if (code.isNotBlank()) com.intelligame.huntix.social.ReferralManager.shareCode(this, code) } }
    7 -> startActivity(Intent(this, InfoLegalActivity::class.java))
}}.show()
```

**Dopo:**
```kotlin
AlertDialog.Builder(this).setTitle("Altro").setItems(items) { _, i -> when (i) {
    0 -> startActivity(Intent(this, PlayerProfileActivity::class.java))
    1 -> startActivity(Intent(this, GenderChangeActivity::class.java))
    2 -> startActivity(Intent(this, SettingsActivity::class.java))
    3 -> startActivity(Intent(this, AbilityActivity::class.java))
    4 -> startActivity(Intent(this, LiveEventsActivity::class.java))
    5 -> startActivity(Intent(this, SpecialEventsActivity::class.java))
    6 -> startActivity(Intent(this, SurpriseInventoryActivity::class.java))
    7 -> com.intelligame.huntix.social.ReferralManager.getMyCode(this) { code -> runOnUiThread { if (code.isNotBlank()) com.intelligame.huntix.social.ReferralManager.shareCode(this, code) } }
    8 -> startActivity(Intent(this, InfoLegalActivity::class.java))
}}.show()
```

### Note

- L'indice 5 è ora "Eventi Speciali" → `SpecialEventsActivity`.
- Tutti gli indici successivi sono incrementati di 1.
- L'import `com.intelligame.huntix.ui.*` è già presente nella riga 18 di BaseNavActivity, quindi `SpecialEventsActivity` è già importato (si trova nel package `com.intelligame.huntix.ui`).

---

## 7. Modifica: AndroidManifest.xml

**Percorso:** `app/src/main/AndroidManifest.xml`

### Cosa aggiungere

Inserire le dichiarazioni delle due nuove Activity prima del tag di chiusura `</application>` (riga 393).

### Codice da inserire

Prima di `</application>`, aggiungere:

```xml
        <!-- ═══ EVENTI SPECIALI — Caccia dell'Elfo ════════════ -->
        <activity android:name=".ui.SpecialEventsActivity"
            android:exported="false"
            android:screenOrientation="portrait"/>

        <activity android:name=".ui.ElfHuntActivity"
            android:exported="false"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.AppCompat.NoActionBar"/>
```

### Note

- **SpecialEventsActivity**: usa il tema di default (`Theme.ARProto`) che è lo stesso di tutte le Activity con bottom nav.
- **ElfHuntActivity**: usa `Theme.AppCompat.NoActionBar` per schermo intero AR, identico ai minigiochi AR (vedi righe 322-331 del manifest attuale).
- Entrambe `exported="false"` e `screenOrientation="portrait"` come tutte le altre Activity del progetto.

---

## Ordine di implementazione consigliato

1. **SpecialEventManager.kt** — Nessuna dipendenza, puro calcolo data. Creare per primo.
2. **ElfHuntManager.kt** — Dipende da SpecialEventManager. Creare per secondo.
3. **AndroidManifest.xml** — Registrare le nuove Activity prima di testare.
4. **SpecialEventsActivity.kt** — UI calendario. Dipende da ElfHuntManager e SpecialEventManager.
5. **ElfHuntActivity.kt** — Attività AR. Dipende da ElfHuntManager.
6. **HomeActivity.kt** — Modifica minima, aggiunta banner. Dipende da SpecialEventManager.
7. **BaseNavActivity.kt** — Modifica minima, aggiunta voce menu. Dipende da SpecialEventsActivity.

### Testing

- **SpecialEventManager**: testare con data simulata (modificare Calendar.getInstance() per mock).
- **ElfHuntManager**: testare SharedPreferences direttamente, verificare claimedDays persistono.
- **UI**: testare su dispositivo con ARCore, verificare che la griglia 5x5 si visualizza correttamente.
- **AR**: testare su dispositivo fisico (non emulatore) con superficie ben illuminata.

### Future espansioni

- **Tombolata**: implementare la logica di gioco per l'evento 27-31 Dicembre.
- **Notifiche push**: ricordare al giocatore di riscattare il giorno.
- **Item effetivi**: collegare gli item milestone agli effetti di gioco (XP boost, spawn rate, ecc.).
- **Creature Babbo Cacciatore**: implementare il modello 3D e le statistiche nel sistema di battaglia.
- **Condivisione social**: permettere di condividere il progresso del calendario.
