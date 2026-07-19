# 🎮 Mini Giochi — Guida Completa di Integrazione
## Huntix — com.intelligame.huntix

---

## 📋 INDICE

1. [Panoramica Architettura](#1-panoramica-architettura)
2. [Lista Minigiochi](#2-lista-minigiochi)
3. [Schema Dati Firebase](#3-schema-dati-firebase)
4. [Piano di Integrazione](#4-piano-di-integrazione-passo-passo)
5. [Modifiche AndroidManifest](#5-modifiche-androidmanifest)
6. [Modifiche HomeActivity](#6-modifiche-homeactivity)
7. [Linee Guida UI/UX](#7-linee-guida-uiux)
8. [Sistema Premi e Economia](#8-sistema-premi-e-economia)
9. [Ottimizzazione Performance](#9-ottimizzazione-performance)
10. [Leaderboard & Analytics](#10-leaderboard--analytics)

---

## 1. Panoramica Architettura

```
HomeActivity
    └── 🎮 Mini Giochi → MiniGamesHubActivity
                              ├── MemoryGameActivity
                              ├── NumberPickActivity
                              ├── HighCardActivity
                              ├── ThreeCardActivity
                              ├── CatchEggActivity
                              └── Match3Activity

managers/
    └── MiniGameManager.kt  ← SINGLETON — gestisce tutto
```

### Flusso dei Premi
```
Giocatore completa minigioco
    ↓
MiniGameManager.applyReward(ctx, reward)
    ↓
Applica moltiplicatore streak (1x / 1.5x / 2x)
    ↓
HatchingManager.addMvc()          → MVC coins
PlayerProfileManager.profile.xp  → XP + level-up
PlayerProfileManager.profile.gems → Gemme
HatchingManager.giftEgg()         → Uova speciali
    ↓
Aggiorna SharedPreferences (tentativi, streak, MVC totale)
    ↓
Verifica bonus giornaliero (5 giochi → 500 MVC cassa)
```

### Struttura File Aggiunti
```
app/src/main/java/com/intelligame/huntix/
├── MiniGamesHubActivity.kt          ← Hub principale (NEW)
├── managers/
│   └── MiniGameManager.kt           ← Manager centrale (NEW)
└── minigames/
    ├── MemoryGameActivity.kt         ← Memory 4×4 (NEW)
    ├── NumberPickActivity.kt         ← Pesca Numeri (NEW)
    ├── HighCardActivity.kt           ← Carta Più Alta (NEW)
    ├── ThreeCardActivity.kt          ← Tre Carte (NEW)
    ├── CatchEggActivity.kt           ← Acchiappa Uova (NEW)
    └── Match3Activity.kt             ← Match-3 6×6 (NEW)
```

---

## 2. Lista Minigiochi

| # | Gioco | Tipo | Tentativi/giorno | Max Premi | Durata |
|---|-------|------|-----------------|-----------|--------|
| 4 | 🃏 Memory Game | Logica | 3 | 400 MVC | ~60s |
| 5 | 🎣 Pesca Numeri | Logica | 3 | 500 MVC + Gemma | ~30s |
| 6 | 🂡 Carta Più Alta | Carte | 5 | 200 MVC | ~30s |
| 7 | 🃑 Tre Carte | Carte | 5 | 250 MVC + Streak Bonus | ~60s |
| 8 | 🥚 Acchiappa Uova | Abilità | 3 | 300+ MVC | 30s |
| 9 | 🔮 Match-3 | Abilità | 3 | 400 MVC | 90s |

### Tabella Premi Dettagliata

```
🎰 SLOT MACHINE
  ├── JACKPOT (3x🥚):        1000 MVC + 500 XP + 1 Gemma
  ├── 3 uguali:               500 MVC + 200 XP
  ├── 2 uguali:               100 MVC + 50 XP
  └── Nessuna:                 20 MVC consolazione

🎟️ GRATTA & VINCI
  ├── 3x🥚:                   300 MVC + 150 XP
  ├── 3x💎:                   200 MVC + 100 XP + 1 Gemma
  ├── 3x🍀:                   100 MVC + 75 XP
  ├── 3x stesso numero:        numero × 3 MVC + 50 XP
  └── Nessuna terna:            15 MVC consolazione

📋 BINGO RAPIDO
  ├── BINGO completo:          500 MVC + 300 XP + Uovo Raro
  ├── Diagonale:               200 MVC + 150 XP
  ├── Riga/Colonna:            150 MVC + 100 XP
  └── Nessuna:                  20 MVC consolazione

🃏 MEMORY
  ├── Completato < 30s:        400 MVC + 200 XP
  ├── Completato 30–45s:       250 MVC + 100 XP
  ├── Completato 45–60s:       150 MVC + 50 XP
  └── Timeout:                  30 MVC consolazione

🎣 PESCA NUMERI
  ├── TRIPLE (3 uguali):       500 MVC + 250 XP + 1 Gemma
  ├── Somma > 45:              300 MVC + 150 XP
  ├── Somma 35–45:             150 MVC + 75 XP
  └── Somma < 35:               25 MVC consolazione

🂡 CARTA PIÙ ALTA
  ├── Vinci (diff ≥ 5):        200 MVC + 100 XP
  ├── Vinci (diff 1–4):        100 MVC + 50 XP
  ├── Pareggio:                 50 MVC + 20 XP
  └── Perdi:                    20 MVC consolazione

🃑 TRE CARTE
  ├── Trovato:                  250 MVC + 100 XP
  ├── Streak ×3:              + 500 MVC bonus
  └── Sbagliata:                30 MVC consolazione

🥚 ACCHIAPPA UOVA
  ├── Common 🥚:               +10 MVC per uovo
  ├── Rare 💜:                 +30 MVC per uovo
  ├── Legendary ⭐:            +100 MVC per uovo
  └── Rotten ❌:               -5 MVC (da evitare)

🔮 MATCH-3
  ├── Score ≥ 150:             400 MVC + 200 XP
  ├── Score 80–149:            200 MVC + 100 XP
  ├── Score 30–79:             100 MVC + 50 XP
  └── Score < 30:               30 MVC consolazione
```

---

## 3. Schema Dati Firebase

### Firestore Collections (Opzionale — per leaderboard online)

```
firestore/
├── mini_game_leaderboards/
│   └── {gameId}/
│       └── entries/ (subcollection)
│           └── {userId}/
│               ├── userId: String
│               ├── displayName: String
│               ├── score: Long
│               ├── mvcEarned: Long
│               ├── timestamp: Timestamp
│               └── gameId: String
│
├── mini_game_stats/
│   └── {userId}/
│       ├── totalMvcFromGames: Long
│       ├── totalGamesPlayed: Int
│       ├── maxStreak: Int
│       ├── lastPlayDate: String (yyyyMMdd)
│       ├── gamesWon: Map<String, Int>     // gameId → wins
│       ├── gamesPlayed: Map<String, Int>  // gameId → plays
│       └── achievements: List<String>
│
└── mini_game_events/
    └── {eventId}/
        ├── type: String  // "double_rewards", "bonus_game"
        ├── gameIds: List<String>
        ├── multiplier: Double
        ├── startTime: Timestamp
        ├── endTime: Timestamp
        └── isActive: Boolean
```

### SharedPreferences (Locale — già implementato)

```
"minigames_v1" SharedPreferences:
├── plays_{gameId}_{yyyyMMdd}: Int       // tentativi usati oggi
├── mvc_today_{yyyyMMdd}: Long           // MVC guadagnati oggi
├── total_games_{yyyyMMdd}: Int          // giochi completati oggi
├── daily_bonus_claimed_{yyyyMMdd}: Bool // bonus 5-giochi riscosso
├── streak_count: Int                    // streak corrente
├── streak_last_time: Long               // timestamp ultimo gioco
└── last_day: String                     // per rilevare nuovo giorno
```

### Firebase Realtime DB (Alternativa per leaderboard)

```json
{
  "mini_games": {
    "leaderboards": {
        "{userId}": {
          "score": 1000,
          "name": "Mario",
          "ts": 1712345678
        }
      }
    },
    "daily_rewards": {
      "{yyyyMMdd}": {
        "multiplier": 2.0,
        "active_games": ["memory", "match3"]
      }
    }
  }
}
```

### Security Rules (Firestore)

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /mini_game_leaderboards/{gameId}/entries/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    match /mini_game_stats/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    match /mini_game_events/{eventId} {
      allow read: if request.auth != null;
      allow write: if false; // Solo admin
    }
  }
}
```

---

## 4. Piano di Integrazione Passo-Passo

### STEP 1 — Copia i file sorgente (5 minuti)

```bash
# Copia nella struttura del progetto:
cp managers/MiniGameManager.kt  \
   app/src/main/java/com/intelligame/huntix/managers/

cp MiniGamesHubActivity.kt  \
   app/src/main/java/com/intelligame/huntix/

cp minigames/*.kt  \
   app/src/main/java/com/intelligame/huntix/minigames/
```

### STEP 2 — AndroidManifest.xml (3 minuti)

Aggiungi le seguenti `<activity>` dentro `<application>`:

```xml
<!-- Hub Minigiochi -->
<activity
    android:name=".MiniGamesHubActivity"
    android:theme="@style/Theme.AppCompat.Light.NoActionBar"
    android:screenOrientation="portrait"
    android:exported="false" />

<!-- Minigiochi singoli -->
    android:theme="@style/Theme.AppCompat.NoActionBar"
    android:screenOrientation="portrait" android:exported="false" />
    android:theme="@style/Theme.AppCompat.Light.NoActionBar"
    android:screenOrientation="portrait" android:exported="false" />
    android:theme="@style/Theme.AppCompat.Light.NoActionBar"
    android:screenOrientation="portrait" android:exported="false" />
<activity android:name=".minigames.MemoryGameActivity"
    android:theme="@style/Theme.AppCompat.Light.NoActionBar"
    android:screenOrientation="portrait" android:exported="false" />
<activity android:name=".minigames.NumberPickActivity"
    android:theme="@style/Theme.AppCompat.Light.NoActionBar"
    android:screenOrientation="portrait" android:exported="false" />
<activity android:name=".minigames.HighCardActivity"
    android:theme="@style/Theme.AppCompat.NoActionBar"
    android:screenOrientation="portrait" android:exported="false" />
<activity android:name=".minigames.ThreeCardActivity"
    android:theme="@style/Theme.AppCompat.NoActionBar"
    android:screenOrientation="portrait" android:exported="false" />
<activity android:name=".minigames.CatchEggActivity"
    android:theme="@style/Theme.AppCompat.NoActionBar"
    android:screenOrientation="portrait" android:exported="false" />
<activity android:name=".minigames.Match3Activity"
    android:theme="@style/Theme.AppCompat.NoActionBar"
    android:screenOrientation="portrait" android:exported="false" />
```

### STEP 3 — Aggiungi entry in HomeActivity (2 minuti)

Trova la sezione `slimCard` in `HomeActivity.kt` e aggiungi:

```kotlin
// ─── Slim extras ──────────────────────────────────
// ... (esistente) ...
root.addView(slimCard("🎮", "Mini Giochi",     "#E91E63") {
    startActivity(Intent(this@HomeActivity, MiniGamesHubActivity::class.java))
})
```

Oppure aggiungi nel CircleGrid come 7° elemento:

```kotlin
CircleItem("🎮", "Giochi", "#E91E63") {
    startActivity(Intent(this@HomeActivity, MiniGamesHubActivity::class.java))
}
```

### STEP 4 — Notifica Push (Opzionale, Firebase Cloud Messaging)

```kotlin
// In EggHuntApplication.kt, aggiungi daily reminder:
fun scheduleMiniGameReminder() {
    // Invia notifica ogni giorno alle 10:00
    // "🎮 I tuoi mini giochi sono pronti! Guadagna MVC gratis!"
    val workRequest = PeriodicWorkRequestBuilder<MiniGameReminderWorker>(
        1, TimeUnit.DAYS
    ).setInitialDelay(calculateDelayUntil(10, 0), TimeUnit.MILLISECONDS).build()
    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        "mini_game_reminder", ExistingPeriodicWorkPolicy.KEEP, workRequest
    )
}
```

### STEP 5 — Test & QA

```
✅ Testa ogni minigioco
✅ Verifica cooldown (3 tentativi poi bloccato fino a mezzanotte)
✅ Verifica streak multiplier (gioca 3+ giochi rapidamente)
✅ Verifica bonus giornaliero dopo 5 giochi
✅ Verifica MVC si accumula in HatchingManager
✅ Verifica XP in PlayerProfileManager
✅ Test orientation lock (portrait only)
✅ Test su dispositivi con schermi piccoli (360dp larghezza)
```

---

## 5. Modifiche AndroidManifest

File: `AndroidManifest_additions.xml` (allegato)

Nessuna nuova permission richiesta — tutte le funzionalità usano:
- `SharedPreferences` (locale)
- API esistenti (`HatchingManager`, `PlayerProfileManager`)
- Canvas standard Android (nessun hardware speciale)

---

## 6. Modifiche HomeActivity

Aggiungi il seguente slim card dopo la sezione "Slim extras" in `HomeActivity.kt`:

```kotlin
root.addView(slimCard("🎮", "Mini Giochi 🆕", "#E91E63") {
    startActivity(Intent(this@HomeActivity, MiniGamesHubActivity::class.java))
})
```

Per evidenziare che è una feature nuova, aggiungi il badge:

```kotlin
// Mostra badge "NUOVO" per i primi 7 giorni dall'installazione
val installTime = packageManager.getPackageInfo(packageName, 0).firstInstallTime
val isNew = System.currentTimeMillis() - installTime < 7 * 24 * 3600 * 1000L
val label = if (isNew) "Mini Giochi 🆕" else "Mini Giochi"
root.addView(slimCard("🎮", label, "#E91E63") {
    startActivity(Intent(this@HomeActivity, MiniGamesHubActivity::class.java))
})
```

---

## 7. Linee Guida UI/UX

### Palette Colori

| Contesto | Colore | Hex |
|----------|--------|-----|
| Sfondo principale | Verde menta | #C8E6C9 |
| Sfondo scuro (giochi notturni) | Dark navy | #0D1530 |
| Accent primario | Teal | #1A6B7C |
| Testo titoli | Dark navy | #1A3A4A |
| Testo secondario | Blue-gray | #607D8B |
| Premi/oro | Arancio | #FF6F00 |
| Vittoria | Verde | #2E7D32 |
| Perdita | Rosso | #B71C1C |
| Streak | Arancio caldo | #FF8F00 |

### Animazioni Standard

```kotlin
// Flip carta (rivelazione)
ObjectAnimator.ofFloat(view, "rotationY", 0f, 90f)  // flip out
ObjectAnimator.ofFloat(view, "rotationY", -90f, 0f) // flip in

// Scale bounce (vittoria)
PropertyValuesHolder.ofFloat("scaleX", 1f, 1.3f, 1f)
PropertyValuesHolder.ofFloat("scaleY", 1f, 1.3f, 1f)

// Fade in risultato
ObjectAnimator.ofFloat(resultCard, "alpha", 0f, 1f)

// Pulse continuo (icone disponibili)
ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.03f, 1f)
    .apply { repeatCount = ObjectAnimator.INFINITE; duration = 2000 }
```

### Haptic Feedback

```kotlin
// Tocco semplice
view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

// Evento importante (vincita)
view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
```

### Regola UX dei 3 Secondi
Ogni minigioco deve mostrare il risultato entro **3 secondi** dalla fine dell'azione dell'utente. Il feedback visivo immediato è fondamentale per l'engagement.

---

## 8. Sistema Premi e Economia

### Bilancio Giornaliero Massimo

```
3 giochi × 9 giochi disponibili = 27 tentativi totali
(5 carte × 2 giochi carta = 10; 3 × 7 altri = 21 → tot ~31)

Scenario "giocatore attivo" (tutti i tentativi):
  ≈ 2000–4000 MVC/giorno dai minigiochi
  
Confronto con mining:
  COMMON egg (Lv1):  ~0.001 MVC/ora
  → 4000 MVC dai giochi = 167 giorni di mining common
  
Questo è INTENZIONALE: i minigiochi sono il principale
onboarding di MVC per utenti senza uova rare.
```

### Streak Multiplier Economics

```
Streak 0–2: ×1.0  (baseline)
Streak 3–4: ×1.5  (incentiva sessioni da 3+ giochi)
Streak 5+:  ×2.0  (massimo — sessione completa)

Con streak ×2.0 e fortuna:
  → Non-inflazionario: richiede 30+ minuti di engagement
```

### Daily Bonus (5 Giochi)

```
5 giochi completati → Cassa 🎁:
  +500 MVC  +250 XP  +1 Gemma

Stimola il ritorno quotidiano senza forzare.
```

---

## 9. Ottimizzazione Performance

### Lazy Loading Activities

```kotlin
// In MiniGamesHubActivity, pre-caricare solo gli intent necessari
// Non creare le Activity finché l'utente non tocca
// Android gestisce il backstack automaticamente
```

### SharedPreferences Thread-Safe

```kotlin
// MiniGameManager usa Mode_PRIVATE — thread safe per letture
// Le scritture sono single-thread (UI thread) → nessun lock necessario
// Per accesso background: usare apply() non commit()
```

### Canvas CatchEggActivity

```kotlin
// EggCatchView usa postInvalidate() → ottimizzato per UI thread
// Handler a 16ms → ~60fps
// Lista eggs: mai più di 10 contemporaneamente (gestito da spawn rate)
// Usa drawText con emoji (bitmap cache automatica in Android 6+)
```

### Match3 Performance

```kotlin
// Griglia 6×6 = 36 Button views → accettabile su tutti i device Android 6+
// findMatches() è O(n) = O(36) → nessun problema di performance
// Animazioni: max 5 ObjectAnimator paralleli
```

---

## 10. Leaderboard & Analytics

### Leaderboard Locale (già incluso in MiniGameManager)

```kotlin
// Top 10 punteggi locali per gioco
// Memorizzato in SharedPreferences come JSON
// Visualizzabile in GamifiedLeaderboardActivity (già esistente)
```

### Integrazione Firebase Analytics (Opzionale)

```kotlin
// In ogni minigioco, dopo applyReward():
FirebaseAnalytics.getInstance(this).logEvent("minigame_completed") {
    param("reward_mvc", reward.mvcCoins)
    param("win", if (reward.mvcCoins > 30) 1L else 0L)
}
```

### Metriche da Monitorare

| Metrica | Target |
|---------|--------|
| Daily Active Users (DAU) mini games | > 40% degli utenti attivi |
| Media giochi/sessione | > 3 |
| Ritorno giornaliero (da notifica) | > 25% |
| Streak medio | > 2.5 |
| Tempo medio per sessione mini giochi | > 4 minuti |

---

## 🚀 QUICK START (TL;DR)

1. **Copia** tutti i `.kt` nelle cartelle corrette
2. **Aggiungi** le `<activity>` in `AndroidManifest.xml`
3. **Aggiungi** il `slimCard("🎮", "Mini Giochi", ...)` in `HomeActivity.kt`
4. **Build** e testa
5. **(Opzionale)** Configura Firebase Collections per leaderboard online

Tutto il resto è **zero-configuration** — `MiniGameManager` si auto-inizializza.

---

*Versione: 1.0.0 | Huntix Mini Games Integration*
*Generato per: com.intelligame.huntix*
