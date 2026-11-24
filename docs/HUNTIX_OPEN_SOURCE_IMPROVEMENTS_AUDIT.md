# HUNTIX — Audit, acquisizione e integrazione di migliorie da progetti Unity open source

Documento di lavoro del branch `integrazione-open-source` (creato da `integrazione-clone`
all'head `b158e45a`). Scan complete dell'argomento: `HUNTIX_MANDATO.md` (Costituzione del
gioco, in particolare §7 conservazione, §10 commit, §11 autonomia).

**Regola centrale della sessione**: da ogni repository deve essere ricavata almeno una
miglioria concreta per Huntix, anche quando non è possibile riutilizzare direttamente il
codice; in quel caso viene adattata una tecnica, un'architettura, un comportamento o una
soluzione. Nessun repository viene dato per completato solo perché analizzato.

---

## Fase 1 — Audit preliminare di Huntix (stato iniziale, prima di ogni modifica)

### Versione, pipeline, target

| Voce | Valore riscontrato | Evidenza |
|---|---|---|
| Unity | `2022.3.52f1 (1120fcb54228)` | `unity-project/ProjectSettings/ProjectVersion.txt:1-2` |
| Pipeline | URP `14.0.11`, SRP attivo | `unity-project/Packages/manifest.json:36`, `ProjectSettings/GraphicsSettings.asset:43,67` |
| Asset pipeline | `URP_Pipeline.asset`: render scale 0.75, HDR off, MSAA 1, shadow distance 50 | `unity-project/Assets/Settings/URP_Pipeline.asset:13,26,28,29,37,48`; renderer senza feature/post-processing (`URP_Renderer.asset:18,21,53`) |
| Qualità | 6 livelli, indice 5 = Ultra (non modificato) | `ProjectSettings/QualitySettings.asset:7,255` |
| Android | package `com.intelligame.huntix`, minSdk 29, targetSdk 34, IL2CPP, ARM64, landscape 1920×1080 | `ProjectSettings/ProjectSettings.asset:11,45-46,143,166-167,174-176,265,666-667` |
| Pacchetti | Cinemachine 2.9.7, TMP 3.0.6, ARFoundation/ARCore 5.1.3, Addressables 1.21.19, Input System 1.11.2, uGUI | `unity-project/Packages/manifest.json:38-44` |
| Build | `Assets/Editor/HuntixBuild.cs:63-90` (IL2CPP/ARM64/ETC2/Gradle/AAR), integrazione AAR in `build_release.sh:387-400,515-544` | — |
| Scene in build | 10: Menu, Outdoor, Indoor, Preload, ARDice MainScene, Room, City, Inferno, Purgatorio, Paradiso | `ProjectSettings/EditorBuildSettings.asset:9-36`; `HuntixBuild.cs:73-85` |

### Inventario degli asset e degli script

- `unity-project/Assets` ≈ 889 MB: `Art` 390 MB, `ThirdParty` 354 MB, `Resources` 128 MB, `City` 13 MB.
- 754 script `.cs`: 225 runtime first-party (`Scripts` 48 + `City/Scripts` 177), 40 editor
  first-party (`Editor` 34 + `City/Editor` 6), 481 third-party, 8 plugin.
- 47 scene (9 first-party + 38 third-party), 201 prefab (16 first-party + 185 third-party),
  1475 mesh FBX/OBJ, 1768 immagini PNG/JPG, 3 clip `.anim` (tutti third-party), 11 Animator
  controller (4 first-party).
- Cartelle first-party: `Assets/Scripts/{Core,Outdoor,Indoor,Bridge,UI,AR,Gamification,Inventory,Weather}`,
  `Assets/City/Scripts/{Player,OSM,Vehicle,NPC,Economy,Interior,Environment,UI,Diagnostics,World,Sync,Game,AR,Death,Afterlife,Multiplayer}`,
  `Assets/Resources/{Buildings,Characters,CityRecipes,Eggs,Furniture,Mixamo,Morphs,Nature,Pets,Quaternius,Roads,Vehicles,VehicleData}`.

### Architettura dei sistemi (verificata sul codice, non sui README)

- **Player**: `Game` singleton orchestratore (`City/Scripts/Game.cs:14,59-72`); `PlayerController`
  con `CharacterController` (`Player/PlayerController.cs:8-9,222,244,298,401-443`), substep
  max 50 ms e clamp 250 ms (`:71-74`), SmoothDamp accel/freno (`:83-84,365-374`), salto/sprint/
  dash/volo, pendenza ripida → knockdown (`:39-46,445-456,552`), switch follower A/B via
  `PlayerPrefs` (`:128-146`). Animazione: `CharacterWalker` Animator/procedurale
  (`Player/CharacterWalker.cs:20,367,406,813,883`), `FootIK` 2 raycast/frame con `WalkableMask`
  (`Player/FootIK.cs:27-38,193`), `PlayerHeroRig`, `SoleGeometry`, `GrowthMorphController`.
- **Ground snap**: `GroundSnapper` statico, `WalkableMask` esclude layer 8 edifici
  (`OSM/GroundSnapper.cs:70,73,179,208,263,294,318`), buffer `RaycastNonAlloc` (`:191,233`);
  snap authoritativo `CityChunkedWorld.TryGroundColumn` (`OSM/CityChunkedWorld.cs:1121`).
  Regole vincolanti del Mandato: mai prendere l'hit più alto, nessun proxy altimetrico dagli
  edifici, `TerrainChunk.DemSampleStep` coerente con il passo mesh.
- **Camera**: `CameraRig` (`Player/CameraRig.cs:8`) con orbit/zoom/giroscopio/pinch/prima persona/
  guida/indoor, deadzone gyro (`:37-42`), collisione braccio `SphereCastNonAlloc` (`:635`).
- **Input touch**: `TouchInputHandler` legacy tap/doppio tap/long press (`:35-122`), teleport
  (`:134`), interazione (`:158`), veicoli (`:216-219`); joystick doppio `DynamicJoystick` e
  `AlternateTouchInput` (`:20,39,44,89`), selezione sorgente in `UIManager.cs:727-731`, pulsanti
  runtime (`:988-1034,1190`).
- **Interazione/NPC**: `Game.OnInteractPressed` priorità POI→veicolo→porta→missione→ingresso
  (`Game.cs:412-480`), `ContextActionController` probe 0,15 s (`:25,43,50,86,177`),
  `InteractableProp` (`:16-18,151`), `BuildingEntrance` (`Interior/BuildingEntrance.cs:158,169`);
  `NPCController` con culling/bisogni/quotidiano/knockdown (`NPC/NPCController.cs:15,225,312`),
  `MaxActiveNpc=180` (`:80`), popolazione per chunk con budget (`NPCPopulator.cs:20-24,66`),
  famiglia/relazioni/compagno, NPC indoor paralleli.
- **Missioni**: `MissionManager` (`Economy/MissionManager.cs:6,28,48,62`), `NPCMission`
  tipi `CollectEggs/WalkDistance` (`NPCMission.cs:12-14,88,162,321`), lavori
  (`JobManager.cs:31,383,517`), ricerche Android scaffold (`Core/ResearchTaskManager.cs:7,34,103`).
- **Uova/inventario**: cattura canonica `EggController` (`Economy/EggController.cs:9-12,99,145-170`),
  spawn chunk/envelope/reward (`EggSpawnManager.cs:86,236,553`), `EggDex` (`:16-73`),
  `ContainerEgg` (`:52,56,234,243,249`), `BudgetEggWatcher` (`:14,95,170`). Collezione uova
  authoritative lato Android (`EggWhereLog`). **Duplicati/incompleti, da conservare**:
  `EggInventoryManager`, `BestiaryUI.RefreshUI`, `EggOpening.OpenEgg`, `HUDController.UpdateEggCount`,
  `UIManager.UpdateEggCount`, `OutdoorManager`/`EggSpawner` (due `EggData`, due `GetRandomRarity`).
- **Salvataggi**: `SaveManager`, `SavedManager`, `ProfileManager`, `PlayerProfileManager`
  coesistono (quadruplicazione del livello profilo); ponte effettivo `CityWorldSync`
  (`City/Scripts/Sync/CityWorldSync.cs:33,111,234,253,314`), usato da `Game.cs:105`.
- **Veicoli/traffico**: guida/danni/carburante (`VehicleController.cs:32,81,158,173`), UI
  concessionaria/garage/officina/parcheggio, taxi/bus/passeggeri. Traffico triplice conservato:
  legacy `TrafficManager` max 25, locale `TileRoadNetwork`/`CarAgent` (`CarsPerTile=22`,
  `MaxCars=180`, `:48-49`), server `TrafficClient`/`TrafficSystemManager`.
- **HUD/UI**: `UIManager` 1804 righe (`City/Scripts/UI/UIManager.cs:17`), HUD legacy,
  `HamburgerMenu` (`:17,213-236,490-531`), minimappa/GPS/radar, console QA `HuntixDebugConsole`.
- **Mondo**: tre mondi affiancati e non sostituibili — envelope Android `CityOSMWorld`
  (`OSM/CityOSMWorld.cs:28,250,292,325`), streaming tile/chunk `CityChunkedWorld` +
  `ChunkManager` + `TileClient`, quartiere fittizio da ricetta `FictionalCityWorld`
  (`Game/FictionalCityWorld.cs:18-23,140-182,230,354`, flag `huntix_world_mode`).

### Criticità e tecniche già presenti

- Costi noti: kit Quaternius FBX ~11× e 29 MB texture ma **default Kenney** (`City/Scripts/UI/CityStyle.cs:5-14,25-32`);
  `GetComponentsInChildren` su alberi grandi (`ChunkData` ha cache `:30-33,105-110`);
  `NPCController.cs:1115-1116` fa due scansioni `SkinnedMeshRenderer` nella stessa stringa di log;
  `Physics.RaycastAll`/`OverlapSphere` allocanti in diversi punti; budget chunk 12 ms e 1 build
  concorrente (`ChunkManager.cs:35,45`); mesh terreno 45 m contro `DemSampleStep=15f`
  (`TerrainChunk.cs:20,82`) — disallineamento documentato, **non da cambiare senza misure**.
- Tecniche già presenti: `PerfProbe` scope struct 20 slot (`:15-33,45-52,83-99`);
  `SessionTelemetry` beacon 2 s con istogramma frame/GC/RAM/`prof=` (`:19,24,58,137,160,182-285`);
  `PerformanceGovernor` tier 0-4; profili qualità Low/Medium/High senza sovrascrivere Ultra;
  LOD/culling chunk (`LoadRadius=3`, `UnloadRadius=4`, tick 0,25 s), `TerrainColliderLod=0`,
  simulazione a 20 Hz; buffer no-alloc e cache prefab/material/tile; `Physics.autoSyncTransforms=false`.

### Baseline di verifica (prima delle modifiche)

- `python3 backend/test_unity_audit_features.py`: **73 test OK**.
- `python3 backend/test_city_recipe.py`: **3 test OK**.
- `cd backend && ./venv/bin/python -m unittest -q test_audit_regressions`: **8 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori, 194 warning pre-esistenti.
- Working tree: solo i noti log pre-esistenti (sei `docs/huntix_log_20261122_*`/`_122151.txt`
  cancellati e `docs/huntix_log_20261123_175436.txt` untracked), da non toccare.
- Backup completo di `HEAD`: `/tmp/opencode/huntix-open-source-backup` (13.346 file, 1,2 GB).

---

## Fase 2 — Download dei repository

Tutti gli 11 repository GitHub sono stati clonati con `git clone --depth 1` in
`/tmp/opencode/open-source-audit/`; nessun download è fallito. Quaternius è stato analizzato
tramite il sito ufficiale e le pagine dei singoli pack.

| Progetto | URL / fonte | Commit shallow | Esito |
|---|---|---|---|
| SanAndreasUnity | https://github.com/in0finite/SanAndreasUnity | `f685c43` | clonato |
| MafiaUnity | https://github.com/MafiaHub/MafiaUnity | `0d7a12f` | clonato |
| Android_Unity3D_OpenWorld | https://github.com/nikunjsingh93/Android_Unity3D_OpenWorld | `7bf02b7` | clonato |
| 3ddriving | https://github.com/Tritone1/3ddriving | `cd85b89` | clonato |
| Unity-CharacterController | https://github.com/dbrizov/Unity-CharacterController | `8dff862` | clonato |
| Controller | https://github.com/rob1997/Controller | `32b8702` | clonato |
| unity-ai-npc | https://github.com/dogacankutlu/unity-ai-npc | `a6505ea` | clonato |
| ai-npc-quest-framework | https://github.com/bceciliano/ai-npc-quest-framework | `c5505c1` | clonato |
| Unity-Rpg | https://github.com/weirdbeardgame/Unity-Rpg | `9a9b3fc` | clonato |
| OpenStash | https://github.com/unity3d-open-tools/openstash | `3e121fd` | clonato |
| Grand Road Auto | https://github.com/BlackRoad-Forge/Grand-Road-Auto | `c7d93d7` | clonato |
| Quaternius | https://quaternius.com/ (+ pagine pack, FAQ, `/license.html`) | — | sito consultato |

### Licenze verificate

| Progetto | Licenza | Obblighi / limitazioni |
|---|---|---|
| SanAndreasUnity | MIT, `Copyright (c) 2015 James King` (`LICENSE:1-13`) | conservare copyright e notice; asset GTA richiesti dal README **non** usati |
| MafiaUnity | Apache-2.0 (`LICENSE:1-5`), `NOTICE` con copyright 2018 Madarász/Svitana/Dobiáš | se si copia: licenza + NOTICE + notice modifiche; **nessun asset Mafia** |
| Android_Unity3D_OpenWorld | **nessuna licenza** (solo `README.md`) | solo analisi tecnica, zero copia |
| 3ddriving | **nessuna licenza per i C#**; `THIRD_PARTY_NOTICES.md` copre solo asset (Khronos CC BY 4.0, Quaternius/Kenney/3DAssetsDev CC0, glTFast) | solo idea, zero copia |
| dbrizov/Unity-CharacterController | MIT, `Copyright (c) 2015 Denis Rizov` (`LICENSE.md:1-3`) | conservare notice |
| rob1997/Controller | MIT (`LICENSE:1`) | conservare notice |
| dogacankutlu/unity-ai-npc | MIT (`LICENSE:1`) | conservare notice |
| bceciliano/ai-npc-quest-framework | MIT, `Copyright (c) 2025 Elitania Ceciliano` (`LICENSE:1-3`) | conservare notice |
| weirdbeardgame/Unity-Rpg | MIT, `Copyright (c) 2021 Timothy O'Barr` (`LICENSE:1-21`) | conservare notice |
| unity3d-open-tools/openstash | MIT **dichiarato al README:18-19**, ma file LICENSE assente e titolare non verificabile | solo adattamento tecnico, zero copia |
| BlackRoad-Forge/Grand-Road-Auto | **proprietaria** BlackRoad OS, Inc. (`LICENSE:1-59`) | nessuna copia di codice, asset o pattern protetti; solo principio generale documentato |
| Quaternius | CC0 per i pack verificati (Simple Nature, Downtown City MegaKit, Stylized Nature MegaKit, Ultimate Nature, Ultimate Stylized Nature, Textured Fantasy Nature); QAL v1.0 per `Characters – Fantasy Races` e per il sito | nessun credito obbligatorio; QAL vieta rivendita degli asset come asset |

---

## Fase 3 — Matrice comparativa (12 progetti → 12 migliorie)

Ogni riga riporta l'evidenza nel repository analizzato, il verdetto legale/tecnico e il
contributo **effettivamente implementato** in Huntix. Nessun progetto è stato archiviato
solo perché analizzato.

| # | Progetto | Evidenza nel sorgente esterno | Licenza | Verdetto | Miglioria implementata in Huntix |
|---|---|---|---|---|---|
| 1 | SanAndreasUnity | `Assets/Scripts/Behaviours/OutOfRangeDestroyer.cs:8-49` (tick 1 s, grazia 5 s, reset se rientra); `.../World/FocusPointManager.cs:8-22,57-92,95-138` | MIT, James King 2015 | IMPLEMENTABILE | `Economy/EggCullGrace.cs`: culling uova a 1 Hz, grazia 3 s, isteresi 60/70 m, `SetActive` solo su cambio di stato |
| 2 | MafiaUnity | `Assets/Scripts/Game/Archetypes/Door.cs:14,59-83,85-112` (`isMoving` blocca la seconda `Use()` fino a fine animazione) | Apache-2.0, NOTICE 2018 Madarász/Svitana/Dobiáš | IMPLEMENTABILE | `World/InteractDoor.cs`: `useDoorLatch=false` (default), lock 1 s con `Time.unscaledTime` **solo dopo teleport riuscito**; `BUSSA` e negozi intatti |
| 3 | Android_Unity3D_OpenWorld | `README.md:2`; `obj/Debug/Assembly-CSharp.dll` decompilato in `/tmp/opencode/ao_cs.il:39-72` (`CarCam.rotationThreshold`, `cameraRotationSpeed`) | **nessuna licenza per il codice** | SENSIBILE (sola idea) | `Player/EggCompassAssist.cs`: opt-in, solo in guida e sotto 2 m/s, max 15°/s verso l'uovo attivo entro 120 m; zero correzioni sopra soglia |
| 4 | 3ddriving | `Assets/DrivingSim/Scripts/Save/SaveService.cs:7-55,64-84` (`.tmp`→`.backup`→file); `PlayerProfile.cs:10,75-89` (`version=3`, `Sanitize()`) | **nessuna licenza per i C#** | IMPLEMENTABILE come idea indipendente | `Economy/EggJournal.cs`: cache locale `egg-journal.json` + `.backup.json` + `.tmp`, versione 1, `Sanitize()` dedup/clamp/max 200, **non autoritativa** (Android resta fonte di verità) |
| 5 | dbrizov/Unity-CharacterController | `Assets/NaughtyCharacter/_Scripts/SpringArm.cs:17-36,39-60` (`GetCollisionRadiusForCamera` da near clip/FOV/aspect, `SmoothDamp`) | MIT, Denis Rizov 2015 | IMPLEMENTABILE | `Player/CameraCollisionRadius.cs` + hook opt-in in `CameraRig`: `FromCamera(fov, aspect, nearClip)`, clamp 0,12–0,60 m; `ArmRadius=0.22f` e SphereCast esistenti invariati |
| 6 | rob1997/Controller | `Packages/com.ekaka.core/Runtime/Common/EventBus.cs:64-102,151-211,213-247` (`SubscribeOneTime`, unsubscribe tollerante) | MIT, Robel Getnet Geremew 2023 | IMPLEMENTABILE | `Environment/EventBus.cs`: `SubscribeOne<T>` che si auto-disiscrive; `EggHintObservedEvent`; prima voce del diario `ActivityLogEvent` in `Game.cs`; slot `egg-hint` in `PerfProbe` |
| 7 | dogacankutlu/unity-ai-npc | `Assets/AIConversationSystem/Runtime/NPC/NPCPersona.cs:7-37` (persona come ScriptableObject); `Conversation/ConversationSession.cs:9-17` | MIT, Harun Doğacan Kutlu 2026 | SENSIBILE (solo slice offline) | `NPC/CityNpcPersonaDefinition.cs`: persona offline con `eggHintLine`/`eggPlaceHint`, `Decorate()` aggiunge una riga una volta; `UIManager.ShowDialog` la chiama; **zero rete/LLM/API key** |
| 8 | bceciliano/ai-npc-quest-framework | `QuestSystem/QuestsObjects/QuestData.cs:6-15`; `Interactable.cs:51-67`; `QuestSystemManager.cs:92-137` (obiettivi event-driven da definizioni) | MIT, Elitania Ceciliano 2025 | IMPLEMENTABILE (solo slice dati) | `Economy/NpcMissionDefinition.cs`: `TryApply(ref NPCMission.MissionData)` opzionale; generatore procedurale di `NPCMission` resta identico; **zero SQLite/OpenAI** |
| 9 | weirdbeardgame/Unity-Rpg | `Assets/Game/GameAssetManager.cs:15-30,86-167` (handle con ID stabile e provenienza); `Assets/Editor/QuestEditor.cs:35-80` | MIT, Timothy O'Barr 2021 | IMPLEMENTABILE | `Diagnostics/OpenSourceAudit.cs` + `Assets/Editor/OpenSourceAuditMenu.cs`: audit `asset-handles` (registry Kenney/CityKit collegati, nessun `Resources.Load`, nessun `TypeNameHandling.All`) |
| 10 | unity3d-open-tools/openstash | `OpenStash/Assets/Editor/OpenStash/OSDefinitionsInspector.cs:21-45` (accetta solo path sotto `Assets/Resources/`); `OSItem.cs:51-65` | MIT **dichiarato al README:18-19**, LICENSE assente | MANCANTE (legale) / adattamento tecnico | audit `resource-defs`: verifica i path `Resources` dichiarati (Nature, Buildings, Legal/copyright.txt) e ID unici; menu Editor + comando `osaudit`; **nessuna griglia multi-slot né `OverlapSphere` per suono** |
| 11 | BlackRoad-Forge/Grand-Road-Auto | `Assets/Scripts/Behaviours/OutOfRangeDestroyer.cs:8-49`; `NPCPedSpawner.cs:16-41` (cap separati); `Loader.cs:90-127,205-281` (step pesati) | **proprietaria** BlackRoad OS, Inc. | SENSIBILE (solo principio generale, zero copia) | audit `load-lifecycle`: 6 invarianti letti con reflection (`UnloadRadius>LoadRadius`, `BuildBudgetMs<=16`, `MaxConcurrentBuilds==1`, `CarsPerTile<=25`, `MaxCars<=200`, `ProgressIntervalS>0`) e uovo solo se 6/6 passano; zero valori modificati |
| 12 | Quaternius | `https://quaternius.com/license.html` (QAL v1.0); `/packs/simplenature.html` CC0; `/packs/downtowncitymegakit.html` CC0; `/packs/charactersfantasyraces.html` QAL | CC0 per i pack verificati, QAL per Fantasy Races | IMPLEMENTABILE (solo manifest) | audit `asset-packs`: manifest per-pack con URL, licenza, URL licenza e stato import; Downtown/UAL2 CC0 importati, Simple Nature CC0 candidato non importato, Fantasy Races QAL documentato; **nessuna mesh/texture nuova** |

---

## Fase 4 — Contributi implementati (uno per progetto)

Infrastruttura comune (non un "dodicesimo progetto", ma il ponte che rende ogni contributo
verificabile): `Diagnostics/OpenSourceFeatureBridge.cs` — pref `huntix_os_<id>`, evento
Unity→Android `OpenSourceFeatureChanged` (whitelistato in `UnityIpc.kt`), ritorno Android→Unity
nel JSON `setMode` come `openSourceFeatures=id=1,id=0`, ripple uova via `FeatureEggHidden`
e `Game.GrantAuditEgg`, telemetria `PerfProbe`/`SessionTelemetry`. Comandi QA `osfeature` /
`osaudit` nella console chiusa di default. Tutte le opzioni sono **opt-in con default
invariato**; i percorsi storici restano attivi quando l'opzione è spenta.

| Progetto | File Huntix | Default / comportamento spento | Riflesso uova e misura |
|---|---|---|---|
| SanAndreasUnity | `Economy/EggCullGrace.cs`, hook in `EggSpawnManager.Update()` | `Enabled=false` → scansione per frame originale a 60 m | `egg-cull` al toggle; log throttle 10 s; 1 Hz invece di ~30 Hz |
| MafiaUnity | `World/InteractDoor.cs`, `Initialize()` in `Game.Start()` | `useDoorLatch=false` → nessun lock, `BUSSA` sempre disponibile | `door-latch` → "Uovo dietro la porta" una sola volta |
| Android_Unity3D_OpenWorld | `Player/EggCompassAssist.cs`, hook in `CameraRig.Update()` | `Enabled=false` → nessuna query nuova; sopra 2 m/s la camera non viene toccata | `egg-compass`; misura nello slot `camera` già esistente |
| 3ddriving | `Economy/EggJournal.cs`, `Record()` da `Game.SpawnFeatureEggAt()` | `Enabled=false` → nessun file scritto; `SaveManager`/Android invariati | `egg-journal`; `PerfProbe.Measure("diary")` e `SessionTelemetry.Mark` |
| dbrizov | `Player/CameraCollisionRadius.cs`, `CameraRig.useFrustumCollisionRadius` | `false` → raggio fisso 0,22 m e SphereCast identico | `camera-radius`; calcolo solo su cambio camera/FOV |
| rob1997 | `Environment/EventBus.cs` (`SubscribeOnce<T>`, `EggHintObservedEvent`), `Game.cs`, `PerfProbe.cs` | API `Subscribe`/`Unsubscribe`/`Publish`/`Clear` invariate | prima voce del diario con lat/lng, una sola per sessione; slot 21 `egg-hint` |
| unity-ai-npc | `NPC/CityNpcPersonaDefinition.cs`, `UIManager.ShowDialog()` | nessuna persona assegnata → array di righe identico, callback invariato | `npc-persona` → indizio una sola volta, `place=città→indizio→…` |
| ai-npc-quest | `Economy/NpcMissionDefinition.cs`, `NPCMission.GenerateMissionData()` | nessuna definizione assegnata → missioni generate come prima | `quest-def` → indizio testuale nel diario, nessun premio nuovo |
| Unity-Rpg | `Diagnostics/OpenSourceAudit.cs` (`asset-handles`), `Assets/Editor/OpenSourceAuditMenu.cs` | audit su richiesta, zero costo runtime | uovo dei crediti solo se 2/2 registry collegati |
| OpenStash | `Diagnostics/OpenSourceAudit.cs` (`resource-defs`), menu Editor | audit su richiesta, nessun `Resources.Load` nuovo | uovo delle risorse solo se tutti i path esistono |
| Grand Road Auto | `Diagnostics/OpenSourceAudit.cs` (`load-lifecycle`) | audit su richiesta, reflection in sola lettura (`GetRawConstantValue`, mai `SetValue`) | uovo del caricamento solo se 6/6 invarianti rispettati |
| Quaternius | `Diagnostics/OpenSourceAudit.cs` (`asset-packs`), manifest CC0/QAL | audit su richiesta, nessuna mesh importata | uovo dei crediti pack solo se manifest coerente (3 CC0 + 1 QAL) |

### Commit della sessione

- `a199afb9` — SubscribeOnce e primo indizio uovo (rob1997).
- `382b0df2` — ponte bidirezionale condiviso (Unity↔Android) e test.
- `48db67b7` — completa il ripristino delle preferenze in `GameManager.cs`.
- `e16667c9` — culling con grazia e quattro audit locali (5 repository).
- `e19a0d36` — latch porte e diario uova atomico (MafiaUnity, 3ddriving).
- `aaa5e1f0` — bussola uova e raggio camera dal frustum (android_openworld, dbrizov).
- `c06dce1b` — persona NPC offline e definizioni missioni (unity-ai-npc, ai-npc-quest) + test statici.
- `a7be02c0` — report finale, matrice comparativa e attribuzioni aggiornate.
- `82de6e8b` — `.meta` mancante del ponte open source.
- `687b188b` — grazia culling azzerata al rientro e firma `Report` allineata nel menu Editor.

---

## Fase 5 — Soluzioni scartate (e perché)

- **Import diretto dei pacchetti rob1997** (`com.ekaka.*`, 179 script): riscritto solo il
  pattern one-shot dentro l'EventBus esistente; sostituire l'Bus avrebbe toccato tutti i sistemi
  (§7 Mandato) senza guadagno misurato.
- **Layer LLM/OpenAI di unity-ai-npc e APIManager di ai-npc-quest**: rete, chiavi, latenza,
  privacy e batteria su Android; mantenuta solo la separazione dati/contenuto offline.
- **SQLite e UI coupling di ai-npc-quest**: già coperte da `MissionManager`/`PlayerPrefs`;
  importarle avrebbe creato una quarta fonte di verità.
- **Griglia multi-slot di OpenStash e `SignalNeighbors` con `OverlapSphere` per suono**:
  costo per-frame su Android e licenza non verificabile; mantenuto solo il controllo editor
  dei path `Resources`.
- **Copia di codice/asset da Grand Road Auto (proprietaria) e dai repo senza licenza**
  (android_openworld, 3ddriving): usate solo le idee generali, riscritte da zero.
- **Importazione delle mesh Quaternius (Downtown 315 modelli, Simple Nature, Fantasy Races)**:
  il manifesto documenta le licenze, ma nuovi FBX/texture aumenterebbero il costo di rendering
  già critico (29 MB di texture del kit attuale, nota AGENTS.md). Nessun asset binario aggiunto.
- **Modifica di `CityStyle.UseQuaternius`, `LoadRadius`, `CarsPerTile`, raggi uova 60 m,
  valute, Firestore, drop, P2P**: tutto intenzionalmente non toccato.

---

## Fase 6 — Test eseguiti e non eseguiti

### Eseguiti (ultima verifica dopo tutte le modifiche)

| Comando | Risultato |
|---|---|
| `python3 backend/test_unity_audit_features.py` | **96 test OK** (73 baseline + 23 nuovi: ponte, SubscribeOnce, culling, 4 audit, latch, journal, bussola, raggio camera, persona, missioni, `.meta`) |
| `python3 backend/test_city_recipe.py` | **3 test OK** |
| `cd backend && ./venv/bin/python -m unittest -q test_audit_regressions` | **8 test OK** |
| `cd backend/preprocessing/cscheck && ./check.sh` | **CSCHECK OK**, 0 errori, 194 warning pre-esistenti. Stubs allineati alle nuove API (`MissionData`, `City.World.InteractDoor`, `Mathf.Tan`, `Camera.aspect`, `Quaternion.RotateTowards`) |
| `./gradlew :app:compileReleaseKotlin --offline -q` | **OK** (handler `OpenSourceFeatureChanged`, pref e `openSourceFeatures`) |
| `LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 BUILD_UNITY_AAR=1 FORCE_UNITY_AAR=1 SKIP_MAP_UPDATE=1 SKIP_INSTALL=1 ./build_release.sh --auto` | **BUILD SUCCESSFUL in 5m 1s** (89 task, 682 nodi IL2CPP, 0 falliti); export Unity, `:unityLibrary:assembleRelease` Il2Cpp e APK firmato da 184 MB. Log: `/tmp/opencode/build_release_open_source.log` |
| `apksigner verify --print-certs app/build/outputs/apk/release/app-release-signed.apk` | **firma valida**, certificato `CN=Huntix, OU=Huntix, O=Huntix, L=IT, ST=IT, C=IT`, SHA-256 `7089056d70807fb282a9d997f78c4f1b037c885b862f79c6531b8a8c5dd2239c` |
| `sha256sum app/build/outputs/apk/release/app-release-signed.apk` | `6ea4e40d2993690885972591ed02809cd9697df02a97a5091ab5c5ae2772aeb8` |
| Verifica token nell'APK (`classes*.dex` + `assets/bin/Data/Managed/Metadata/global-metadata.dat`) | **14/14 presenti**: `OpenSourceFeatureChanged`, `openSourceFeatures`, `huntix_os_`, `egg-cull`, `egg-compass`, `egg-journal`, `door-latch`, `npc-persona`, `quest-def`, `camera-radius`, `egg-hint`, `resource-defs`, `load-lifecycle`, `asset-packs`. I bridge sono nei dex, gli ID Unity nella metadata IL2CPP |
| `md5sum -c /tmp/opencode/pre_build_hashes.txt` | **OK su 5/5** dopo il ripristino dei noti effetti collaterali della build (`link.xml.meta`, `PlayerHero.prefab`, `PlayerLocomotion.controller`) |

La build Unity reale ha individuato un errore sfuggito a `cscheck` (cartella `Assets/Editor`
non inclusa): `OpenSourceAuditMenu.cs` passava 6 argomenti a `OpenSourceFeatureBridge.Report`
che ne accetta 5. Corretto in `687b188b`; la successiva compilazione batchmode non ha riportato
errori C#.

### Non eseguiti (dichiarazione esplicita)

- **Collaudo su dispositivo/emulatore**: non disponibile in questa sessione. Touch, camera in
  guida, rotazione bussola, latch porte e dialoghi NPC non sono verificati a runtime.
- **MisurazioniPerfProbe/SessionTelemetry su device**: gli slot sono presenti e collegati,
  ma i target (distanze/s uova, p95 scrittura journal, 0 correzioni sopra soglia) restano
  da validare sul campo. Nessun risultato numerico è stato inventato.
- **Test di installazione/avvio APK su device**: l'APK è firmato e verificato, ma non installato
  (nessun device/emulatore disponibile).

---

## Fase 7 — Rischi residui

1. **Bussola in guida**: l'auto-rotazione potrebbe disorientare. Mitigazioni: opt-in, solo
   <2 m/s, max 15°/s, bersaglio attivo entro 120 m, gyro e drag utente con priorità.
   Richiede collaudo touch.
2. **Journal locale**: `persistentDataPath` su Android ha semantica diversa da `PlayerPrefs`;
   il file è **cache non autoritativa** e la dimensione è limitata a 200 voci. Scrittura solo
   su evento uovo, mai per frame.
3. **Latch porte**: se l'utente apre e bussa nello stesso secondo il secondo gesto viene
   ignorato per 1 s. È voluto e limitato al solo ramo teleport; tempo non scalato evita lock
   infinito in pausa.
4. **Raggio camera frustum**: in interni stretti il pull-in può cambiare; l'eccezione
   `EggArmClearance` e il default 0,22 m restano invariati. Da misurare prima di attivare.
5. **Audit con reflection**: se una costante viene rinominata, l'audit riporta "skip" invece
   di rompere la build; non modifica mai i valori (`SetValue` assente, verificato da test).
6. **Stubs cscheck**: i nuovi componenti sono verificati contro stub allineati, non contro
   l'API Unity reale; la build AAR è stata eseguita con successo ed è l'autorità finale, ma non
   copre il comportamento a runtime su device.

---

## Fase 8 — Impatto Android e conformità Mandato

- **Bridge bidirezionale**: Unity→Android `OpenSourceFeatureChanged` (in `APP_EVENTS`) e
  Android→Unity `openSourceFeatures` nel JSON `setMode`; Android è proprietario della
  preferenza (`huntix_prefs`, pref `huntix_os_<id>`) e la rispedisce a ogni nuova Activity.
  Il ripristino è passivo: nessun uovo, nessun premio, nessun evento duplicato.
- **Riflesso uova**: ogni contributo ha un indizio posizionale nel solo diario
  (`FeatureEggHidden` → `handleFeatureEggHidden`), con `place` specifico. La cattura canonica
  resta `EggCapturedInCity`; nessuna valuta, Firestore, drop o P2P toccato.
- **UI in italiano**: etichette, comandi e messaggi new sono in italiano; nessuna stringa
  visibile al giocatore in inglese.
- **Conservazione (§7)**: nessun sistema esistente rimosso, disattivato o sostituito; tutti i
  vecchi percorsi restano attivi con le opzioni spente. Nessun default cambiato.
- **Licenze**: attribuzioni e testi in `THIRD_PARTY_NOTICES.md` (nuova sezione
  "Audit open source 2026-11-24"); zero asset binari importati dai 12 progetti.

---

## Fase 9 — Stato finale

- Branch `integrazione-open-source`, 10 commit della sessione, `main` intoccato, **nulla pushato**.
- Build release completata: AAR Unity IL2CPP ricostruito e APK firmato
  `app/build/outputs/apk/release/app-release-signed.apk` (184 MB, SHA-256
  `6ea4e40d2993690885972591ed02809cd9697df02a97a5091ab5c5ae2772aeb8`). Tutti i 14 token
  delle integrazioni sono presenti nell'APK; effetti collaterali noti della build Unity
  ripristinati e verificati con `md5sum -c`.
- Working tree: solo i noti log `docs/huntix_log_*` pre-esistenti da non toccare; i documenti
  finali di questo audit sono committati nella sessione.
- Prossimo passo operativo (fuori sessione): collaudo su dispositivo delle opzioni opt-in e
  delle misure `PerfProbe`/`SessionTelemetry`.
- Report di sintesi: `docs/HUNTIX_OPEN_SOURCE_IMPROVEMENTS_SUMMARY.md`.
