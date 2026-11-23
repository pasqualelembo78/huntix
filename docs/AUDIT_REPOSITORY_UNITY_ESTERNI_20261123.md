# Audit dei repository Unity esterni e mappa di integrazione per Miacittà

Data: 2026-11-23. Branch di lavoro: `audit-unity-esteri` (da `flat-world`).
Backup completo pre-inizio: `/tmp/opencode/backup` (21G, `rsync -a`, exit 0).
Repository clonati in: `/tmp/opencode/research/` (separati dal progetto Huntix).

Metodo: sola lettura del codice sorgente, manifest, ProjectSettings, LICENSE/NOTICE.
**Nessuno script, eseguibile o strumento proveniente dai repository scaricati è stato eseguito.**
Unity Editor non è stato avviato: apribilità/compilazione/build Android dei repository esterni
non sono verificati e sono indicati come `NON VERIFICATO`. I README non sono stati considerati
fonte di verità: dove il README dichiara una funzionalità ma il codice non la implementa, il
punteggio è basso e la nota lo segnala.

---

## 1. Repository scaricati ed esito dell'analisi

| # | Repository | Cartella | Unity | Licenza codice | Licenza asset | Esito per Huntix |
|---|---|---|---|---|---|---|
| 1 | 3ddriving | `3ddriving` | 6000.0.75f1 | **Nessuna** | CC0 dichiarato in THIRD_PARTY_NOTICES (Khronos/Quaternius/Kenney) | Solo idee: UX touch, salvataggio atomico. Sterzo rotto (`Mathf.Lerp(1,1,...)`). Non copiare codice. |
| 2 | CarGame | `CarGame` | 2019.4.16f1 | **Apache 2.0** | Non verificata (EasyRoads3D, Windridge, NatureManufacture, car package) | Progetto non apribile (ProjectSettings LFS pointer), errore compile in `pedestrianSpawner.cs:33`, zero touch. Non copiare asset. |
| 3 | CarRace | `CarRace` | 6000.3.17f1 | **Nessuna** | Non verificata (RCC/NWH attesi in `Assets/ThirdParty`) | Ricco di IA e setup Android, ma scene LFS pointer e build log con `return code 1`. Solo idee. |
| 4 | SanAndreasUnity | `sanandreasunity` | 2020.3.26f1 | **MIT**, Copyright (c) 2015 James King | Non verificata (carica dati GTA a runtime; nessun file GTA nel repo) | **Migliore base di pattern**: budget frame, spawn NPC limitato, enter/exit a stati, touch. Submodule vuoti. |
| 5 | Grand-Road-Auto | `Grand-Road-Auto` | 2020.3.26f1 | **Proprietaria BlackRoad** (fork MIT dichiarato in BLACKROAD.md) | Idem upstream | `diff -rq`: identico a SanAndreasUnity. Da **evitare**: prendere direttamente l'upstream MIT. |
| 6 | Traffic Rider Clone | `traffic-rider-clone` | 2022.3.62f3 | **Nessuna** | Primitivi runtime, nessun asset esterno evidente | Buone idee mobili (traffico a corsie, missioni, save, preset qualità), README non attendibile (prezzi diversi dal config). |
| 7 | RaceRider3D | `RaceRider3d` | 2019.2.2f1 | **MIT**, Copyright (c) 2019 Dillon Hemphill | CC0 solo dichiarato nel README, non verificabile | Endless runner: tilt, pool di 3 strade, shop `PlayerPrefs`. WheelCollider morti. Solo snippet MIT se necessari. |
| 8 | Unity AI Racing Cars with Waypoints | `Unity-AI-Racing-cars-with-waypoints` | 2017.3.1p4 | **Nessuna** (solo font OpenSans Apache) | Track pack commerciale Spyblood redistribuito | Solo idea: sterzo waypoint, sync mesh ruote. Codice acerbo (`update()` mai chiamato, sensori commentati). |
| 9 | Third Person Controller Android | `unity-Third-person-controller-android` | 2020.3.16f1 (nel RAR) | **GPLv3** | Non dichiarata, terze parti incluse | Non apribile senza estrazione RAR/unitypackage. Copyleft: **non copiare**. Solo idee (ScriptableObject, ground check, priorità camera). |
| 10 | Zone Zero | `Zone-Zero` | 2022.3.62f3 | **MIT**, Copyright (c) 2026 Mishal Bhasim | Unity Companion License + BigRookGames/Photon/PlayFab non verificati | Buon controller terza persona e FSM NavMesh (MIT), ma prefab non copribili e Photon AppId terzo. |
| 11 | Battle Island Mobile | `battle-island-mobile` | 2022.3.20f1 | **MIT**, Copyright (c) 2026 ebito-coder | Nessun asset binario | Scheletro non funzionante: touch mai settati, UI senza EventSystem, salvataggi mai chiamati. Nessun codice utile. |
| 12 | FuzzyTown | `FuzzyTown` | 2020.2.2f1 | **Apache 2.0** | 266 prefab "Simple People/Simple Town" acquistati, licenza non verificata | Traffico lane + incroci e pedoni crosswalk come idea; `using UnityEditor` in script runtime blocca build. |
| 13 | Smart City Digital Twin | `smart-city-digital-twin` | 6000.0.84f1 | **Nessuna** | N/A | Ottimo IDM (gap/spillback), semafori adattivi, A*, pooling, merge mesh: **sola idea da reimplementare**, non copiare. |

---

## 2. Tabella comparativa dei punteggi (1 = assente/inutilizzabile, 10 = eccellente)

I punteggi valutano il **codice letto**, non i diritti d'uso e non prestazioni misurate.
Dove i report hanno usato 0 per "assente", il valore è normalizzato a 1.

| Categoria | 3ddriving | CarGame | CarRace | SanAndreas | GrandRoad | TrafficRider | RaceRider3d | AI Waypoints | TPC Android | Zone Zero | Battle Is. | FuzzyTown | Smart City |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Guida | 8 | 7 | 8 | 8 | 8 | 7 | 4 | 7 | 1 | 1 | 1 | 2 | 2 |
| Fisica auto | 8 | 7 | 8 | 8 | 8 | 3 | 2 | 6 | 1 | 1 | 1 | 3 | 2 |
| WheelCollider | 9 | 7 | 9 | 9 | 9 | 1 | 2 | 7 | 1 | 1 | 1 | 1 | 1 |
| Accel/freno/sterzo | 7 | 7 | 7 | 8 | 8 | 6 | 3 | 6 | 1 | 1 | 1 | 2 | 6 |
| Collisioni | 7 | 6 | 7 | 7 | 7 | 4 | 4 | 3 | 3 | 6 | 4 | 5 | 3 |
| Camera dinamica | 8 | 6 | 8 | 8 | 8 | 5 | 2 | 7 | 7 | 7 | 3 | 6 | 2 |
| Personaggio 3ª persona | 1 | 6 | 1 | 8 | 8 | 1 | 1 | 1 | 8 | 9 | 4 | 1 | 1 |
| Animazioni | 2 | 4 | 2 | 8 | 8 | 2 | 1 | 1 | 8 | 8 | 1 | 5 | 2 |
| Movimento a piedi | 1 | 5 | 1 | 8 | 8 | 1 | 1 | 1 | 7 | 9 | 5 | 4 | 2 |
| Enter/exit veicoli | 1 | 3 | 1 | 9 | 9 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| Traffico AI | 2 | 4 | 8 | 2 | 2 | 7 | 1 | 7 | 1 | 1 | 1 | 7 | **9** |
| Pedoni AI | 1 | 2 | 2 | 7 | 7 | 1 | 1 | 1 | 1 | 2 | 1 | 6 | 3 |
| Navigazione NPC | 1 | 2 | 3 | 7 | 7 | 2 | 1 | 4 | 1 | 8 | 2 | 4 | 6 |
| Semafori/incroci | 1 | 5 | 7 | 4 | 4 | 1 | 1 | 2 | 1 | 1 | 1 | 5 | **9** |
| Edifici/ambiente urbano | 5 | 7 | 6 | 6 | 6 | 4 | 2 | 3 | 2 | 4 | 2 | 8 | 6 |
| Generazione strade | 4 | 6 | 3 | 2 | 2 | 6 | 5 | 2 | 1 | 1 | 1 | 6 | 6 |
| Ottimizzazione Android | 4 | 2 | 7 | 7 | 7 | 6 | 4 | 3 | 4 | 4 | 3 | 2 | 5 |
| Controlli touch | **9** | 1 | 8 | **9** | **9** | 7 | 2 | 7 | 8 | 5 | 2 | 1 | 1 |
| Multiplayer | 1 | 1 | 1 | 8 | 8 | 1 | 2 | 1 | 1 | **9** | 2 | 1 | 1 |
| Inventario | 3 | 1 | 2 | 2 | 2 | 2 | 3 | 1 | 6 | 1 | 3 | 1 | 1 |
| Missioni | 8 | 2 | 7 | 1 | 1 | 8 | 1 | 5 | 1 | 3 | 3 | 1 | 1 |
| Salvataggi | **9** | 1 | 5 | 2 | 2 | 8 | 5 | 5 | 1 | 3 | 2 | 1 | 1 |
| Memoria/prestazioni | 5 | 2 | 6 | 7 | 7 | 4 | 4 | 3 | 3 | 4 | 3 | 3 | 6 |

### Migliori per funzionalità

- **Guida/fisica/WheelCollider/enter-exit/touch**: SanAndreasUnity (MIT) e 3ddriving (senza licenza, solo idee).
- **Traffico AI e semafori**: Smart City Digital Twin (senza licenza → solo idea da reimplementare), CarRace (senza licenza).
- **Personaggio/NavMesh**: Zone Zero (MIT per il codice, prefab sotto Unity Companion License).
- **Salvataggi**: 3ddriving (senza licenza → solo il pattern atomico+backup).
- **Missioni**: 3ddriving e Traffic Rider Clone (entrambi senza licenza → solo idee).
- **Multiplayer**: SanAndreasUnity (MIT) e Zone Zero (MIT); Huntix ha già un sistema proprio funzionante.

---

## 3. Architettura Huntix / Miacittà — sistemi già presenti (da NON duplicare)

Mappa costruita leggendo `unity-project/Assets/City/**` e il bridge Android.

| Sistema | File chiave | Stato |
|---|---|---|
| Orchestrazione | `City/Scripts/Game.cs`, `Scripts/Core/GameManager.cs` | Completo |
| Mondo OSM e streaming | `OSM/ChunkManager.cs`, `CityChunkedWorld.cs`, `TileClient.cs` (CacheVersion 5), `ChunkBuilder.cs` | Completo (LoadRadius 3) |
| Player e movimento | `Player/PlayerController.cs`, `CharacterWalker.cs`, `FootIK.cs` | Completo |
| Camera | `Player/CameraRig.cs` (orbita, 1ª persona, guida, pinch, giro) | Completo |
| Touch | `UI/TouchInputHandler.cs`, `DynamicJoystick.cs`, `OrbitZone.cs`, `DrivePedal.cs` | Completo |
| Veicoli | `Vehicle/VehicleController.cs`, `VehicleSpawnManager.cs`, `VehicleOwnershipApi.cs`, `GarageUI.cs` | Completo; **nessun WheelCollider**, **freno a mano assente** |
| Traffico | `Vehicle/Traffic/CarAgent.cs`, `JunctionControl.cs`, `TileRoadNetwork.cs` | Completo; sonde sfalsate 1/6 |
| NPC/pedoni | `NPC/NPCPopulator.cs`, `NPCController.cs`, `NPCSpawnManager.cs` | Completo; waypoint, non NavMesh |
| Uova | `Economy/EggSpawnManager.cs`, `EggController.cs`, `EggCaptureMinigame.cs`, `EggRadar.cs` | Completo; bridge `EggCapturedInCity` in `Game.OnEggCollected` |
| Missioni/economia | `MissionManager.cs`, `JobManager.cs`, `Wallet.cs`, `Inventory.cs`, `Shop.cs` | Completo |
| Garage/interni | `Interior/InteriorManager.cs`, `InteriorGenerator.cs`, `HomeSystem.cs` | Completo |
| Multiplayer | `Multiplayer/MultiplayerManager.cs` | Completo |
| Bridge Android | `Scripts/Bridge/UnityBridge.cs` (`SendMessageToAndroid`, snapshot città, XP/MVC/gemme) | Completo |
| Qualità/telemetria | `Diagnostics/PerfProbe.cs`, `SessionTelemetry.cs`, `PerformanceGovernor.cs` | Completo nel decoding; **nessun set runtime di QualitySettings** |
| Salvataggi | `Scripts/Core/SavedManager.cs`, `SaveManager.cs` + PlayerPrefs sparsi + snapshot | Frammentato; **nessuna scrittura atomica/backup** |

Sistemi paralleli da **conservare** (Mandato §6–8, nessuna cancellazione autorizzata):
`Outdoor/EggSpawner`, `WorldEgg`, `Core/EggOpening`, `EggInventoryManager`, `EggRarity`,
`ArCityController` vs `VirtualArController`.

---

## 4. Mappa delle dipendenze: esistenti → nuove integrazioni

```
[Game.cs] ──► [VehicleController] ──(nuovo)──► [HandbrakeInput] ──► [TouchInputHandler/DrivePedal]
     │                                            │
     │                                            └──► [UnityBridge] ─► Android (evento + SharedPreferences)
     │
     ├──► [PerformanceGovernor] ──(nuovo)──► [QualityProfileSelector] ──► [HamburgerMenu]
     │                                            │
     │                                            └──► [UnityBridge] ─► Android (preferenza qualità)
     │
     └──► [EggSpawnManager] ◄──(riflesso uova)── [nuove feature]
                                  └──► [Game.OnEggCollected] ─► [UnityBridge] ─► Android
```

I salvataggi restano quelli attuali (`SavedManager`/`PlayerPrefs` + snapshot Android):
il writer atomico `.tmp → rename → .bak` è stato valutato ma **non implementato**.

Regole applicate:
1. **Nessuna riscrittura** di sistemi funzionanti: si aggiungono componenti laterali.
2. **Ogni nuova feature ha un uovo**: freno a mano e menu qualità, con posizione
   registrata nel profilo. Nessun uovo aggiuntivo per feature non implementate.
3. **Bridge obbligatorio**: ogni evento locale deve essere inviato ad Android e ripristinabile.
4. **Nessun codice copiato** da repository senza licenza o con licenza non permissiva.

---

## 5. Decisioni di riutilizzo

| Pattern | Fonte | Decisione |
|---|---|---|
| Budget tempo per frame + coda ordinata (`Cell.cs`) | SanAndreasUnity MIT | **Idea/ispirazione**: Huntix ha già `[Builder][Section]` e governor; nessuna copia. |
| Spawn NPC limitato con focus point (`NPCPedSpawner.cs`) | SanAndreasUnity MIT | **Idea**: già parzialmente attuato con `NPCPopulator` budgetato; nessuna copia. |
| Enter/exit a stati + camera collision | SanAndreasUnity MIT | Non serve: `Game.EnterVehicle/ExitVehicle` già completo. |
| Salvataggio atomico `.tmp → rename → .bak` | 3ddriving (no license) | Valutato ma **non implementato**: Huntix usa `PlayerPrefs` + bridge Android, senza guadagno dimostrato. |
| Freno a mano + pedal touch | idee da 3ddriving/CarRace (no license) | **Reimplementato da zero**, senza WheelCollider. |
| Profili qualità Low/Medium/High | Traffic Rider (no license) | **Reimplementato da zero** sopra `PerformanceGovernor`. |
| IDM gap/spillback + semafori adattivi | Smart City (no license) | Valutato: troppo rischioso da innestare su `CarAgent`/`JunctionControl` senza misure; **non implementato** in questa fase. |
| Controller terza persona/ground check | Zone Zero MIT / TPC Android GPLv3 | Non serve: `PlayerController`/`CameraRig` già completi e vincolati alle regole DEM del Mandato. |

---

## 6. Limiti di questa analisi

- Apribilità, compilazione e build Android dei 13 repository esterni: **NON VERIFICATE**.
- Performance runtime degli esterni: **NON VERIFICATE**.
- Licenza degli asset della maggior parte dei repo: **NON VERIFICATA**.
- Il codice Huntix è stato modificato solo dove indicato nei commit successivi a questo documento;
  ogni integrazione è verificata separatamente con `cscheck`, test audit e build Android.
