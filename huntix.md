# Huntix — Piano di Sviluppo

## Stato attuale (Baseline)
- **MainActivity.kt**: ~2700 righe, God Class, unico file sorgente esistente
- **Documentazione**: 20+ file .md con piani, analisi, guide
- **AndroidManifest.xml**: 40+ Activity dichiarate, ~37 senza implementazione
- **Security**: API key e token hardcoded in build.gradle, manifest, gradle.properties
- **Git**: keystore.properties già in .gitignore, ma molti segreti sono nei file tracciati

---

## Fase 1 — Security Hardening

### 1.1 API Key → keystore.properties ✅
- [x] Spostare ADMOB_BANNER_ID, ADMOB_INTERSTITIAL_ID, ADMOB_REWARDED_ID
- [x] Spostare ARCORE_API_KEY
- [x] Spostare MAPBOX_TOKEN (pubblico, ma non deve stare in build.gradle)
- [x] Spostare MAPBOX_DOWNLOADS_TOKEN (gradle.properties → keystore.properties)
- [x] Aggiornare build.gradle per leggere da keystore.properties
- [x] Aggiornare settings.gradle per leggere MAPBOX_DOWNLOADS_TOKEN da keystore
- [x] Usare manifestPlaceholders per ARCore API key in AndroidManifest.xml
- [x] Rimuovere MAPBOX_DOWNLOADS_TOKEN da gradle.properties

### 1.2 .gitignore
- [ ] Verificare che tutti i file sensibili siano esclusi

---

## Fase 2 — Refactoring Architetturale

### 2.1 MainActivity → MVVM ✅
- [x] Creato `model/GameModels.kt` (EggObject, SafeObject, IndoorGameUiState, GamePhase, PlayState)
- [x] Creato `viewmodel/IndoorGameViewModel.kt` (StateFlow, initialize, phase transitions, gameplay actions, timer)
- [x] Refactor MainActivity: delegation properties ↔ ViewModel state
- [x] Rimosse classi interne (EggObject, SafeObject, GamePhase, PlayState) → model package
- [x] Stato spostato in ViewModel (isMenuOpen, hintCooldown)
- [x] Timer Handler → ViewModel coroutine + uiHandler per delayed UI ops
- [x] ViewModel osservato via `lifecycleScope.repeatOnLifecycle`
- [ ] Estrarre ArSceneManager (setup ARCore, depth, room scan)
- [ ] Estrarre EggPlacementManager (placeEgg, autoPlaceEggs, buildEgg)
- [ ] Estrarre SafeManager (buildSafe, open/close door)
- [ ] Estrarre QrScanHelper
- [ ] Estrarre InGameChatOverlay

### 2.2 Fragment per Fase
- [ ] RoomScanFragment
- [ ] SetupFragment (safe + eggs placement)
- [ ] PlayingFragment (hunt, throw, proximity)
- [ ] StatsFragment

### 2.3 Handler/Thread → Coroutine
- [x] Sostituito timer Handler con ViewModel coroutine timer
- [ ] Sostituire i rimanenti `Thread { ... runOnUiThread { ... } }` con coroutine

---

## Fase 3 — Implementazione Activity Mancanti

### 3.1 Home & Navigation
- [ ] HomeActivity (menu principale)
- [ ] SplashActivity
- [ ] SettingsActivity
- [ ] HelpActivity
- [ ] LegalActivity / InfoLegalActivity

### 3.2 Profilo e Progressione
- [ ] PlayerProfileActivity
- [ ] StatsActivity
- [ ] LeaderboardActivity
- [ ] ShopActivity
- [ ] HatchingActivity
- [ ] EggInventoryActivity

### 3.3 Outdoor Mode
- [ ] OutdoorWorldActivity (mappa Mapbox, GPS, uova, palestre, POI)
- [ ] OutdoorModeActivity
- [ ] OutdoorSetupActivity
- [ ] OutdoorHuntActivity
- [ ] OutdoorGuestActivity
- [ ] OutdoorArCatchActivity
- [ ] ArNavigationActivity

### 3.4 Indoor Multiplayer
- [ ] IndoorModeSelectionActivity
- [ ] IndoorMultiplayerLobbyActivity
- [ ] MultiplayerLobbyActivity
- [ ] SaveSlotActivity

### 3.5 Gamification System
- [ ] QuestActivity (missioni)
- [ ] TeamActivity (squadre)
- [ ] GamifiedLeaderboardActivity
- [ ] AbilityActivity
- [ ] CustomizationActivity
- [ ] LiveEventsActivity
- [ ] EggOpeningAnimationActivity
- [ ] SurpriseRevealActivity
- [ ] SurpriseInventoryActivity

### 3.6 Battle System
- [ ] BattleActivity (Street Fighter style)
- [ ] BattleManager
- [ ] BattleEngine, ComboSystem, EnergySystem, EnemyAI, ecc.

### 3.7 Minigiochi
- [ ] MiniGamesHubActivity
- [ ] MemoryGameActivity
- [ ] NumberPickActivity
- [ ] HighCardActivity
- [ ] ThreeCardActivity
- [ ] CatchEggActivity
- [ ] Match3Activity
- [ ] AR versioni dei minigiochi

### 3.8 Social
- [ ] FriendsActivity
- [ ] ChatActivity / ChatListActivity
- [ ] TradeActivity

### 3.9 Avatar (Ready Player Me)
- [ ] ReadyPlayerMeActivity
- [ ] AvatarManager
- [ ] AvatarPersistenceManager
- [ ] AvatarSyncManager
- [ ] AccessoryManager
- [ ] AvatarMapRenderer

---

## Fase 4 — Backend & Firebase

### 4.1 Security Rules
- [ ] Validare Firestore rules
- [ ] Validare Realtime Database rules

### 4.2 Cloud Functions
- [ ] Review functions/index.js
- [ ] Test funzioni esistenti

---

## Fase 5 — QA & Polish

- [ ] Test su dispositivo fisico
- [ ] Performance profiling
- [ ] Crash reporting (Sentry già configurato)
- [ ] Adaptive layout (tablet, schermi piccoli)
- [ ] Localizzazione (i18n)
- [ ] Accessibilità

---

## Fase 1.3 — Risorse Android Mancanti ✅
- [x] `res/values/strings.xml` (app_name, facebook)
- [x] `res/values/colors.xml` (palette completa)
- [x] `res/values/dimens.xml` (dimensioni standard)
- [x] `res/values/themes.xml` (Theme.ARProto, Theme.ARProto.Fullscreen)
- [x] `res/drawable/circle_green.xml` (stato tracking OK)
- [x] `res/drawable/circle_red.xml` (stato tracking perso)
- [x] `res/drawable/bg_button_primary.xml` (riempito)
- [x] `res/drawable/ic_launcher_foreground/background.xml` (icona vettoriale)
- [x] `res/mipmap-anydpi-v26/ic_launcher.xml` (adaptive icon)
- [x] `res/xml/network_security_config.xml` (Firebase, Mapbox, ARCore)
- [x] `res/xml/file_paths.xml` (FileProvider)
- [x] **`res/layout/activity_main.xml`** (layout completo ~250 righe con tutti i View ID usati da MainActivity)

---

## Prossimo step 🎯
Dopo aver completato una fase, sposta il traguardo qui e aggiorna la data.

📅 **26/06/2026** — Fase 1 completata (Security + Resources).
📅 **26/06/2026** — Fase 2.1 parziale (ViewModel + delegazione stato + timer). MainActivity ancora God Class (~2722 righe) ma ora con ViewModel come SSoT.

Prossimo: **Fase 2.1 — Completare estrazione manager (ArScene, EggPlacement, Safe)**
- Compilare e verificare su dispositivo
- Estrarre ArSceneManager, EggPlacementManager, SafeManager
- Sostituire Thread rimanenti con coroutine lifecycleScope
