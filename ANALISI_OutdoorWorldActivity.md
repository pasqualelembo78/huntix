# 📊 Analisi OutdoorWorldActivity.kt — Huntix
## File: 80.5 KB | 1.308 righe | 82 funzioni | 37 import

---

## 🔍 OVERVIEW

OutdoorWorldActivity è il **cuore del mondo aperto** — la schermata stile "Pokémon GO" dove
il giocatore cammina nel mondo reale, vede uova sulla mappa Mapbox, le cattura, visita
palestre e punti di interesse.

Implementa **SensorEventListener** per bussola/orientamento personaggio sulla mappa.

---

## 📊 MAPPA RESPONSABILITÀ

| Responsabilità | Linee | Funzioni | % |
|---|---|---|---|
| **UI Construction** (buildUI, overlays) | 252-492 | 4 | 18% |
| **Character Sprite** (animazione, rendering mappa) | 493-557 | 4 | 5% |
| **GPS & Location** (primo fix, refresh, permission) | 558-615, 1231-1260 | 5 | 5% |
| **Egg System** (load, markers, proximity, navigation) | 586-727 | 11 | 11% |
| **Catch Minigame** (overlay, touch, basket, success) | 820-1041 | 14 | 17% |
| **Gym System** (markers, click, training minigame) | 786-1068 | 7 | 6% |
| **POI System** (markers, proximity, navigation) | 1079-1149 | 8 | 5% |
| **Marker Rendering** (3D markers bitmap, hexagon) | 1158-1220 | 6 | 5% |
| **Walking Rewards** | 1068-1078 | 1 | 1% |
| **Leaderboard** | 1149-1157 | 1 | <1% |
| **Lifecycle** (GPS, sensors, handlers, back) | 1222-1308 | 8 | 7% |
| **Player HUD** (profilo, XP bar, livello) | 231-251 | 2 | 2% |
| **State Variables** | 48-210 | — | 12% |

---

## 🏗️ PROBLEMI ARCHITETTURALI

### 1. 🔴 God Class (meno grave di MainActivity, ma comunque problematico)
1.308 righe con 82 funzioni che gestiscono:
- Mappa Mapbox + annotazioni
- GPS e location tracking
- Sensore bussola
- Sistema cattura uova (mini-game inline)
- Sistema allenamento palestre (mini-game inline)
- Sistema POI
- Rendering marker personalizzati
- Walking rewards
- HUD giocatore

### 2. 🟠 Mini-giochi inline
I mini-giochi **Catch** e **Train** sono embedded direttamente nell'Activity:
- `catchCurrentEgg`, `catchAttempts`, `catchThrowStartX/Y` — stato cattura nel Activity
- `trainCurrentGym`, `trainTapCount`, `trainMaxTaps` — stato allenamento nel Activity
- Meglio come Fragment o Dialog separati

### 3. 🟠 Tutta la UI costruita programmaticamente
`buildUI()` è un metodo di **176 righe** (L252-428) che costruisce TUTTA l'interfaccia
con codice Kotlin puro (nessun layout XML). Difficile da mantenere e modificare.

### 4. 🟡 Handler manuali per timer
Usa `Handler(Looper.getMainLooper())` con postDelayed per:
- Animazione personaggio (`charAnimHandler`)
- Refresh dati mondo (`refreshHandler`)  
- Timeout allenamento (`trainTimeoutHandler`)
Meglio usare `lifecycleScope.launch` con `delay()` per evitare memory leak.

---

## 🟢 PIANO REFACTORING CONSIGLIATO

### Architettura Target: MVVM + Fragment

```
OutdoorWorldActivity.kt (slim — ~300 righe)
    │   Solo hosting MapView + Fragment container
    │
    ├── OutdoorWorldViewModel.kt (~200 righe)
    │   ├── playerProfile: StateFlow<PlayerProfile?>
    │   ├── worldEggs: StateFlow<Map<String, WorldEgg>>
    │   ├── gymLocations: StateFlow<Map<String, GymLocation>>
    │   ├── pois: StateFlow<List<POI>>
    │   ├── currentLocation: StateFlow<Location?>
    │   └── walkingReward: SharedFlow<WalkingReward>
    │
    ├── map/
    │   ├── MapAnnotationManager.kt (~250 righe)
    │   │   └── Gestisce tutti i marker (uova, palestre, POI, personaggio)
    │   ├── MapMarkerFactory.kt (~100 righe)
    │   │   └── makeEggMarker3D, makeGymMarker3D, makePOIMarker, hexagonPath
    │   └── CharacterMapRenderer.kt (~80 righe)
    │       └── Sprite animato, rotazione bussola, cerchio azione
    │
    ├── catch/
    │   ├── CatchFragment.kt (~200 righe)
    │   │   └── Overlay cattura con animazione basket
    │   └── CatchViewModel.kt (~80 righe)
    │       └── Stato cattura, tentativi, successo
    │
    ├── gym/
    │   └── TrainFragment.kt (~100 righe)
    │       └── Mini-gioco allenamento tap
    │
    ├── poi/
    │   └── POIProximityManager.kt (~60 righe)
    │       └── Controllo prossimità, bottone POI
    │
    └── location/
        └── LocationTracker.kt (~80 righe)
            └── GPS permission, location updates, first fix
```

### Risultato: Activity da 1.308 → ~300 righe + 8 file da ~60-250 righe

---

## 📋 PASSI CONCRETI (in ordine di priorità)

### STEP 1 — Estrai ViewModel (~1 ora)
1. Crea `OutdoorWorldViewModel.kt`
2. Sposta tutte le variabili di stato (L48-210)
3. Esponi come `StateFlow`/`LiveData`
4. L'Activity osserva e reagisce

### STEP 2 — Estrai il sistema di cattura (~2 ore)
1. Crea `catch/CatchFragment.kt` con il suo layout overlay
2. Sposta: `startCatchMinigame`, `handleCatchTouch`, `launchBasket`, `onCatchSuccess/Miss`, `finalizeEggCatch`
3. Comunica con l'Activity via `FragmentResult`

### STEP 3 — Estrai il rendering marker (~1 ora)
1. Crea `map/MapMarkerFactory.kt`
2. Sposta: `makeEggMarker3D`, `makeGymMarker3D`, `makePOIMarker`, `hexagonPath`, `darken/lightenColor`
3. Sono funzioni pure — nessuna dipendenza dall'Activity

### STEP 4 — Estrai MapAnnotationManager (~1.5 ore)
1. Crea `map/MapAnnotationManager.kt`
2. Sposta: `addEggMarker`, `addGymMarker`, `addPOIMarkerToMap`, `ensureCharacterAnnotationExists`, `updateCharacterOnMap`
3. Riceve `MapboxMap` nel costruttore

### STEP 5 — Estrai LocationTracker (~1 ora)
1. Crea `location/LocationTracker.kt`
2. Sposta: `checkGpsPermission`, `startGps`, il `locationListener`
3. Usa `SharedFlow` per emettere location updates

### STEP 6 — Migra buildUI a XML/Compose (~2 ore)
1. Crea `activity_outdoor_world.xml` con il layout
2. Sostituisci il `buildUI()` da 176 righe con `setContentView(R.layout.activity_outdoor_world)`
3. Usa ViewBinding per i riferimenti

---

## 🔍 CONFRONTO CON MAINACTIVITY

| Metrica | MainActivity | OutdoorWorldActivity |
|---|---|---|
| Righe | **3.124** | **1.308** |
| Funzioni | 111 | 82 |
| Stato (variabili) | ~60+ | ~40 |
| Severità | 🔴 Critica | 🟠 Alta |
| Pattern | Nessuno | Nessuno |
| UI | XML (53KB) | Programmatica |
| Complessità principale | AR + multiplayer + turni | Mappa + GPS + mini-giochi |

### Nota positiva 🟢
OutdoorWorldActivity è meglio strutturato di MainActivity:
- Le responsabilità sono più raggruppate (cattura, palestre, POI)
- Non c'è multiplayer complesso
- Molte funzioni sono piccole e focalizzate
- La complessità è principalmente nel volume, non nella profondità

Il refactoring qui è più semplice e può essere fatto incrementalmente senza rischi elevati.

---

## ⚠️ STATO VARIABILI — ANALISI

**40 variabili di stato** nella stessa Activity:
- 🗺️ Mappa: mapReady, myCharAnnotation, actionCircleAnnot, eggAnnotations, gymAnnotations, poiAnnotations
- 📍 Posizione: lastLocation, prevLocation, firstFix, followPlayer, locationListener
- 🧭 Sensori: accV, magV, azimuth
- 🚶 Personaggio: walkTick, isMoving, facingDeg, charAnimHandler
- 🎯 Cattura: catchCurrentEgg, catchAttempts, catchMaxAttempts, catchThrowStartX/Y, catchTracking, catchBasketAnim, catchSucceeded
- 🏋️ Allenamento: trainCurrentGym, trainTapCount, trainMaxTaps, trainTimeoutHandler
- 🏷️ POI: poiBtn, currentNearPOI
- 📺 UI Views: ~15 view reference implicite

**Rischio**: Qualsiasi stato corrotto (es. `catchCurrentEgg != null` ma minigame non attivo) causa bug difficili da riprodurre.
