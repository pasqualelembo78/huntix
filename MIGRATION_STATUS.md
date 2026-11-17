# HUNTIX — STATO MIGRAZIONE UNITY

> Riepilogo del lavoro svolto e prossimi passi. Aggiornato: **17/11/2026**.
> Stato letto dal branch corrente `flat-world` (**16 commit locali non ancora pushati**).
> Regola sovraordinata: `HUNTIX_MANDATO.md` (Costituzione del gioco). Ogni nuova
> feature va proposta con il suo **Riflesso della caccia alle uova** e con il
> **bridge bidirezionale obbligatorio** da/per Android.

## OBIETTIVO
Strategia **ibrida**: Android nativo (UI/menu/mini-giochi/profilo) + **Unity 2022.3 LTS**
(motore 3D/AR) per outdoor, RealLife e indoor. Unity è integrato come
**libreria AAR** nel progetto Android. HUNTIX è un **universo unico** di caccia
alle uova AR: niente sistemi "a parte", tutto converge nel profilo e nel bridge.

## SEZIONI E DOVE VANNO
| Sezione | Dove |
|---|---|
| Outdoor AR (caccia uova stile Pokémon GO) | **Unity** |
| RealLife (negozi, lavoro, social, NPC, città OSM 3D) | **Unity** |
| Indoor 3D (case/negozi enterabili, uova nei contenitori) | **Unity** |
| Mini-giochi 2D, termoculla, squadre, profilo, impostazioni, menu, login | **Android nativo** |

---

## STATO ATTUALE (17/11/2026)

### Città OSM 3D (Unity) — operativa
- Tile IIS 1×1 on-demand dal tile server (`backend/traffic/tile_server.py`), grafi
  stradali e POI coti nei file geo (`_geo.json.gz`, versione `ver` con firma `FILTERS`).
- Interni enterabili generati da `InteriorGenerator` (case, appartamenti, negozi,
  POI), con piani, arredi Kenney/Quaternius, luci e azioni contestuali.
- Traffico NPC e mini-gioco di cattura uova (`EggCaptureMinigame`) attivi.
- Costi di rendering/fisica sotto controllo (vedi `AGENTS.md`): kit edifici e
  numero auto sono le due leve principali sul frame rate; misurare con `tools/`.

### Altitudine / DEM — interruttore unico
- `CityConfig.WorldHeights` è l'unico interruttore (default **`Flat`**, comportamento
  del ramo `unity`). I chunk senza griglia `ele` restano piatti in ogni modalità.
- DEM SRTM cotto nei geo a 250 m allineato ai chunk, void riempiti col valore
  valido più vicino (`AGENTS.md` → "Vincoli del mondo OSM lato client").
- **Decisione di prodotto ancora aperta**: restare in `Flat` o riattivare DEM su
  device. I vincoli da rispettare sono documentati in `AGENTS.md` (hit più vicino,
  nessun proxy dalle altezze edifici, mesh 45 m ≤ DEM 250 m, collider solo LOD0,
  `ChunkManager.LoadRadius = 3`, `TryHeightAtWorld` ai bordi del nastro stradale).

### 🥚 Uovo nel cassetto della cucina — Riflesso del Mandato (NUOVO, 17/11/2026)
Integrazione completa del "DOVE HO TROVATO L'UOVO", **estendendo il sistema
esistente `ContainerEgg`** (nessun sistema parallelo, nessuna rimozione):

| File | Modifica |
|---|---|
| `InteriorGenerator.cs` | nuovo arredo `Cassetto2` → `kitchenCabinetDrawer` in cucina; nome con "cassetto" riconosciuto da `ContainerEgg` |
| `ContainerEgg.cs` | preferisce il cassetto; seed **stabile** da edificio+coordinate+piano (non più `GetInstanceID`); chiave stabile `_eggKey`; salta le uova già raccolte; registro `PlayerPrefs["huntix_container_eggs"]` con `MarkFound`/`ExportFound`/`ImportFound` |
| `EggController.cs` | campi `placeHint` (es. `città→<Edificio>→cucina→cassetto2`) e `containerEggKey` |
| `Game.cs` | `OnEggCollected` → `ContainerEgg.MarkFound`; `SendEggToHuntix` usa `placeHint` nel campo `place` con `JsonEscape` |
| `InteriorManager.cs` | espone `CurrentEntrance` per la chiave stabile dell'edificio |
| `CityWorldSync.cs` | snapshot `foundContainerEggs` ← `ExportFound()`, restore → `ImportFound()` (unione non distruttiva) |
| `PlayerProfile.kt` | `cityContainerEggs` in `toFirestore()`/`fromMap` |
| `CityStateSync.kt` | merge di `foundContainerEggs` nel profilo Android + conteggi in `checkSync()` |
| `HuntixStubsGame.cs` | stub cscheck aggiornati con i nuovi campi |

**Flusso completo (bridge bidirezionale):**
```
InteriorManager.MarkInside → ContainerEgg (cassetto, uovo celato)
  → tap → EggCaptureMinigame → EggController.OnCaptured
  → Game.OnEggCollected → EggDex.Record + ContainerEgg.MarkFound
  → UnityBridge.SendMessageToAndroid("EggCapturedInCity", JSON con place/lat/lng)
  → Bridge.handleCityEggCaptured → EggInventoryManager + PlayerProfileManager
     + EggWhereLog.record(luogo, coordinate, timestamp) + gemme/XP
  → CityWorldSync snapshot (foundContainerEggs) → CityStateSync → profilo Firestore
  → restore Android→Unity: ImportFound → il cassetto non fa rinascere l'uovo
```
- Nessun nuovo evento bridge: si riusa `EggCapturedInCity` arricchendo `place`.
- `EggWhereLog` è il "DOVE HO TROVATO L'UOVO" del Mandato (già esistente, non toccato).
- Scala di rarità già unificata a 5 livelli (`Common..Legendary`) su Unity e Android.

### Bridge Android ↔ Unity — bidirezionale
- **Unity → Android**: `UnityBridge.SendMessageToAndroid(eventName, json)` →
  `Bridge.onUnityMessage` → handler (`EggCapturedInCity`, `CityStateSync`, XP, gems, …).
- **Android → Unity**: `UnityPlayer.UnitySendMessage("GameManager","OnEvent", json)`
  e snapshot `CityWorldSync.RequestRestore()` → `ApplySnapshot()`.
- `SplashActivity` allinea il profilo Android allo snapshot all'avvio.

---

## STATO FASI
| Fase | Descrizione | Stato |
|---|---|---|
| 1 | Setup Unity + integrazione | ✅ Completata |
| 2 | Porting core systems | ✅ Completata |
| 3 | AR outdoor mode | ✅ Completata (scripts) |
| 4 | 3D RealLife + Indoor | ✅ Completata (scripts) |
| 5 | Polish & features | ✅ Completata (scripts) |
| 6 | Mini-giochi e sezioni native | ✅ Già presenti in app |
| 7 | Build & deploy | ✅ Build verificata; deploy su device da fare |

---

## COSA E' STATO FATTO

### Unity project (`unity-project/`)
- Struttura progetto creata: `Assets/`, `Packages/`, `ProjectSettings/`.
- `Packages/manifest.json`: URP 14.0.11, AR Foundation 5.1.3, ARCore 5.1.3,
  Firebase (firestore/auth/analytics/config), TextMeshPro, Cinemachine.
- `ProjectSettings/ProjectSettings.asset`: minSdk 29, targetSdk 34, ARM64, IL2CPP.
- Script C# in `Assets/Scripts/` (Core, Outdoor, Indoor, AR, Inventory,
  Gamification, Weather, UI) e in `Assets/City/Scripts/` (città OSM: chunk,
  strade, edifici, interni, economia uova, sync, veicoli, NPC).
- Scene segnaposto `Menu.unity`, `Outdoor.unity`, `Indoor.unity`.

### Modulo `unityLibrary/` (AAR manuale)
- `build.gradle`: library Android, namespace `com.unity3d.player`, compileSdk 34,
  minSdk 29, targetSdk 34. **Senza `shrinkResources`**.
- Manifest minimale e stub di `UnityPlayerActivity`/`UnityPlayer`.

### Integrazione Gradle
- `settings.gradle`: `include ':unityLibrary'`; `app/build.gradle`:
  `implementation project(':unityLibrary')`.
- `app/AndroidManifest.xml`: `.bridge.BridgeActivity` + meta-data `unityplayer`.

### Lavoro recente sul ramo `flat-world` (ultimi 16 commit)
- `9bcfafd` uovo nel cassetto con bridge e posizione nel profilo (vedi sopra).
- `324fb08` asset Unity rigenerati da export (controller, prefab, `link.xml`).
- `bc346ba` chunk build più rapidi e traffico ridotto come nel ramo `unity`.
- `ac0a7f0` edifici OSM non abitabili e scala dei prefab sotto controllo.
- `90013f9` nomi NPC univoci e waypoint dentro il chunk.
- `330b952` / `fa9e267` log Android: sessioni distinte, niente doppioni, crash leggibili.
- `5fa659a` cscheck copre l'API `ErrorSurface` usata dai log City.
- `b9bee18` / `814891f` documentazione costi di rendering e vincoli altitudine.
- `2675642` / `7b3f8d5` / `9b9d153` / `923810c` / `a496ff8` / `fe5dc59` fix
  altitudine, DEM 250 m allineato, snap al terreno, viadotti, follower verticale.

## ERRORI RISOLTI (estratto)
| Errore | Fix |
|---|---|
| `Resource shrinker cannot be used for libraries` | rimosso `shrinkResources` da unityLibrary/build.gradle |
| `Incorrect package=... found in source AndroidManifest.xml` | rimosso attributo `package`, namespace via gradle |
| `android:exported` non specificato | activity Unity dichiarate solo nell'app |
| `NoSuchFileException ... results.bin` | pulite cache gradle |
| Duplicati `uses-feature android.hardware.camera.ar` | manifest library minimale |
| Mancanza di `UnityPlayerActivity`/`UnityPlayer` | stub in unityLibrary |
| Dipendenza circolare `unityLibrary → app` | `UnityBridge.java` spostato nell'app |
| `minSdk 29` library > `minSdk 26` app | library allineata a minSdk 26 |
| Errori compile mini-giochi | file corretti (indentazione, `this@`, bounds Float, `GAP_H`) |
| Player "sospeso/sprofondato" col DEM | hit più vicino (non il più alto), no proxy dagli edifici, DEM 250 m allineato |
| Lag di rendering/fisica | due allocazioni per frame rimosse, traffico ridotto, LOD chunk |
| `cscheck` non copriva `ErrorSurface`/campi uova | stub aggiornati |

## VERIFICHE (17/11/2026)
- `backend/preprocessing/cscheck/./check.sh` → **CSCHECK OK** (compilazione C# City).
- `./gradlew app:compileDebugKotlin` → **BUILD SUCCESSFUL**.
- Build storiche verificate: `app:assembleDebug` (APK ~53 MB) e `app:bundleRelease` (~28 MB).

## FILE CHIAVE
```
HUNTIX_MANDATO.md                                               # Costituzione del gioco
AGENTS.md                                                       # comandi convenzionali + vincoli OSM/DEM
MIGRATION_STATUS.md                                             # questo file
unity.txt                                                       # piano migrazione
unity-project/Assets/Scripts/                                   # script base (core/outdoor/inventory/UI)
unity-project/Assets/City/Scripts/                              # città OSM (chunk, interni, uova, sync)
unity-project/Assets/City/Scripts/Interior/ContainerEgg.cs      # uovo nel cassetto + registro chiavi
unity-project/Assets/City/Scripts/Interior/InteriorGenerator.cs # arredo Cassetto2 in cucina
unity-project/Assets/City/Scripts/Sync/CityWorldSync.cs         # snapshot foundContainerEggs
unity-project/Packages/manifest.json                            # dipendenze Unity
unityLibrary/build.gradle                                       # configurazione AAR
app/src/main/java/com/unity3d/player/UnityBridge.java           # bridge Unity→Android
app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt       # bridge Android→Unity (handler uova)
app/src/main/java/com/intelligame/huntix/bridge/CityStateSync.kt# mirror snapshot + cityContainerEggs
app/src/main/java/com/intelligame/huntix/managers/EggWhereLog.kt# "DOVE HO TROVATO L'UOVO"
app/src/main/java/com/intelligame/huntix/PlayerProfile.kt       # profilo + cityContainerEggs
build_release.sh                                                # build release firmata
```

## LIMITI / BLOCCHI CONOSCIUTI
- **Nessun dispositivo Android connesso** (`adb devices` vuoto): test su device e
  prova A/B del follower verticale non eseguibili in questa sessione.
- **Decisione `Flat` vs DEM** su device ancora da prendere (default resta `Flat`).
- **Nessuna licenza Unity valida** nel container: export headless/AAR reale non
  possibile (`No ULF license found`); l'AAR è costruito a mano con stub.
- **Nessun display** per Unity GUI (`unityhub` come root richiede `--no-sandbox`).
- Le scene Unity restano segnaposto senza licenza/display.

## PROSSIMI PASSI
1. Push del branch `flat-world` (16 commit locali non pushati) previa conferma.
2. Test su dispositivo reale (`./gradlew app:installDebug`) del flusso
   cassetto → cattura → `EggWhereLog` → profilo → restore.
3. Decidere `CityConfig.WorldHeights` (Flat/DEM) con prova su device.
4. Rigenerare/build release (`./build_release.sh`) quando serve il pacchetto firmato.

## COMANDI UTILI
```bash
cat /root/giochi/huntix/MIGRATION_STATUS.md                    # riprendere da qui
cd /root/giochi/huntix/backend/preprocessing/cscheck && ./check.sh   # check C# City
cd /root/giochi/huntix && ./gradlew app:compileDebugKotlin     # check Kotlin Android
cd /root/giochi/huntix && ./gradlew app:assembleDebug          # build debug
./build_release.sh                                             # build release firmata
```
