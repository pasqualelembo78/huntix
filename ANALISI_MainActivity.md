# 📊 Analisi MainActivity.kt — Huntix
## File: 153 KB | 3.124 righe | 111 funzioni

---

## 🔴 DIAGNOSI: God Class (Anti-Pattern)

MainActivity.kt contiene **TUTTA** la logica del gioco indoor AR in un singolo file:
- Setup AR (ARCore, SceneView, depth, room scan)
- Gestione touch (setup + playing)
- Piazzamento oggetti 3D (uova, cassaforte)
- Sistema di lancio basket
- Chiavi e cassaforte (apertura, animazioni)
- Indovinelli e biglietti
- Turni multiplayer
- Punteggi e statistiche finali
- Chat in-game
- Cloud Anchors (persist/restore)
- Sessioni locali
- AdMob (banner + rewarded)
- QR Code scanning
- Room scanning con progresso

### Responsabilità attuali (SRP violato):
| Responsabilità | Linee | % del file |
|---|---|---|
| AR Setup + SceneView | 243-705 | ~15% |
| QR Code detection | 706-882 | ~6% |
| Room Scanning | 883-1040 | ~5% |
| Touch handling | 1041-1175 | ~4% |
| Lancio basket + catch | 1175-1280 | ~3% |
| Cassaforte + chiavi | 1281-1420 | ~4% |
| Ticket/indovinelli + animazioni | 1418-1518 | ~3% |
| Multiplayer turni | 1518-1665 | ~5% |
| Statistiche finali | 1662-1775 | ~4% |
| Piazzamento 3D (safe + eggs) | 1776-2090 | ~10% |
| Cloud Anchors persist/restore | 2090-2425 | ~11% |
| Game flow (hunt, detect, tracking) | 2426-2580 | ~5% |
| UI overlay/menu/dialog | 2580-2735 | ~5% |
| UI updates (phase-aware) | 2734-2946 | ~7% |
| Chat in-game MP | 2946-3060 | ~4% |
| Utility (hit test, dist, dp) | 3060-3124 | ~2% |

---

## 🟢 PIANO REFACTORING CONSIGLIATO (MVVM)

### Architettura Target: MVVM + Fragment-per-Phase

```
MainActivity.kt (slim — solo hosting dei Fragment)
    │
    ├── IndoorGameViewModel.kt (stato condiviso, game logic)
    │   ├── GameState (LiveData: phase, playState, scores, turn)
    │   ├── ArState (anchors, eggs, safe)
    │   └── PlayerState (keys, found, time)
    │
    ├── fragments/
    │   ├── RoomScanFragment.kt       (scansione stanza ~150 righe)
    │   ├── SetupFragment.kt          (piazzamento safe + uova ~300 righe)
    │   ├── PlayingFragment.kt         (fase gioco, basket, proximity ~400 righe)
    │   ├── StatsFragment.kt           (statistiche finali ~100 righe)
    │   └── TurnSwitchFragment.kt      (overlay cambio turno ~50 righe)
    │
    ├── ar/
    │   ├── ArSceneManager.kt         (setup ARCore, SceneView, onFrame ~200 righe)
    │   ├── EggPlacementManager.kt    (piazzamento uova 3D ~250 righe)
    │   ├── SafeManager.kt            (cassaforte: apertura, chiavi, animaz ~200 righe)
    │   ├── BasketLauncher.kt         (lancio basket, hit/miss ~100 righe)
    │   └── CloudAnchorSync.kt        (persist/restore anchors ~300 righe)
    │
    ├── ui/
    │   ├── TicketOverlay.kt           (biglietto indovinello ~100 righe)
    │   ├── HintSystem.kt             (sistema suggerimenti ~80 righe)
    │   ├── InGameMenuManager.kt      (menu, dialoghi ~100 righe)
    │   └── InGameChatOverlay.kt      (chat multiplayer ~80 righe)
    │
    └── utils/
        ├── QrScanHelper.kt           (QR detection + dialog ~150 righe)
        └── ArHitTestUtils.kt         (hit test helpers ~30 righe)
```

### Risultato: ~15 file da ~100-400 righe ciascuno (vs 1 file da 3124 righe)

---

## 📋 PASSI CONCRETI (in ordine di priorità)

### STEP 1 — Estrai il ViewModel (senza rompere nulla)
1. Crea `IndoorGameViewModel.kt`
2. Sposta tutte le variabili di stato (phase, playState, scores, eggs, safe, ecc.)
3. Esponi come `StateFlow` / `LiveData`
4. MainActivity osserva e reagisce

### STEP 2 — Estrai i manager AR
1. `ArSceneManager.kt` — tutto il setup ARCore (L539-705)
2. `EggPlacementManager.kt` — placeEgg, autoPlaceEggs, buildEgg (L1904-2090)
3. `SafeManager.kt` — placeSafe, buildSafe, openDoor, closeDoor (L1776-1420)

### STEP 3 — Estrai i fragment per fase
1. `RoomScanFragment.kt` ← startRoomScan, updateScanProgress, onScanComplete
2. `SetupFragment.kt` ← handleSetupTouch, selectEgg, deleteEgg
3. `PlayingFragment.kt` ← handlePlayingTouch, launchBasket, onThrowHit
4. `StatsFragment.kt` ← showFinalStats, renderStats

### STEP 4 — Estrai utilities
1. `QrScanHelper.kt` ← onQrDetectedInAr, showQrCodeDialog, saveQrToGallery
2. `InGameChatOverlay.kt` ← toggleChatOverlay, sendInGameChat, addChatBubble
3. `TicketOverlay.kt` ← showTicket, wobbleCard, launchSparkles, onCloseTicket

---

## ⚠️ NOTE IMPORTANTI

1. **NON fare un refactoring big-bang** — rischio troppo alto. Estrai un pezzo alla volta.
2. **Inizia dal ViewModel** — è la base che abilita tutto il resto.
3. **Mantieni backward compatibility** — ogni step deve compilare e funzionare.
4. **Aggiungi test** man mano che estrai — il ViewModel è facilmente testabile.
5. **L'activity_main.xml è 53KB** — anche il layout va spezzato con `<include>` / `<fragment>`.
