# Inventario delle integrazioni dai clone Unity — 24 novembre 2026

Analisi **sola lettura** dei 13 repository esterni clonati in
`/tmp/opencode/research/`. Nessun clone è stato eseguito, nessun asset binario
è stato importato. I repository sono descritti in
`docs/AUDIT_REPOSITORY_UNITY_ESTERNI_20261123.md`.

## Gate applicato

Un file passa solo se soddisfa **entrambi** i gate:

1. **Legale**: licenza permissiva verificabile (MIT/Apache 2.0), testo della
   licenza conservato, copyright conservato, modifiche dichiarate. Restano
   esclusi repo senza licenza, GPLv3, fork proprietari, asset Store/EULA e
   asset non verificati anche dentro repo permissivi.
2. **Tecnico**: codice self-contained o adattabile senza rete, senza GTA,
   senza Mirror/Photon/PlayFab/UnityEditor a runtime; non duplica un sistema
   Huntix già più completo; non cambia default, non rimuove nulla.

**Licenza permissiva non significa integrazione automatica**: la maggior parte
dei file è duplicativa, rotta o legata ad asset non coperti dalla licenza del
codice.

## File integrati (codice adattato, non preso tale quale)

| Repo / licenza | Sorgente | Destinazione Huntix | Adattamento |
|---|---|---|---|
| Zone Zero — MIT, `Copyright (c) 2026 Mishal Bhasim`, `/tmp/opencode/research/Zone-Zero/LICENSE` | `Assets/_Project/Scripts/UI/MainMenu/MainMenuAnimator.cs` (134 righe) | `unity-project/Assets/City/Scripts/UI/MenuEntranceAnimator.cs` | Rimosso TMPro inutilizzato e pulse infinito; target generico; flag opzionale; bridge, uovo, `PerfProbe`; non sostituisce il pannello esistente. |
| Zone Zero — MIT (sopra) | `Assets/Editor/BakeTransformIntoMesh.cs` (145 righe) | `unity-project/Assets/City/Editor/CityMeshTools.cs` | Voci menu italiane, `Undo`, nessuna esecuzione automatica. Strumento Editor, non componente runtime. |
| Zone Zero — MIT (sopra) | `Assets/Editor/EnableGPUInstancing.cs` (23 righe) | `unity-project/Assets/City/Editor/CityMeshTools.cs` | Voce italiana con avviso di misurare con `PerfProbe`: con URP l'SRP batcher può rendere inutile il flag. Strumento Editor, non componente runtime. |
| SanAndreasUnity — MIT, `Copyright (c) 2015 James King`, `/tmp/opencode/research/sanandreasunity/LICENSE` | `Assets/Scripts/Commands/CommandManager.cs` (271 righe) | `unity-project/Assets/City/Scripts/Diagnostics/HuntixCommandManager.cs` | Rimossi Player/Mirror/NetStatus e permessi server; registri locali e rate-limit per comando; messaggi italiani; comando `prof` collegato a `PerfProbe`. |
| SanAndreasUnity — MIT (sopra) | `Assets/Scripts/UI/Console.cs` (421 righe) + `ConsoleLogEventSubscriber.cs` (16 righe) | `unity-project/Assets/City/Scripts/Diagnostics/HuntixDebugConsole.cs` | Rimossi `PauseMenuWindow`, `UGameCore.Utilities`, IMGUI complesso e stack trace lunghe; coda con lock, viewer locale, filtri, comandi sicuri; flag opzionale, bridge, uovo, telemetria. Nessun dato GTA o di rete. |

Fonte per SanAndreasUnity è **solo l'upstream MIT**, non il fork
`Grand-Road-Auto`: quest'ultimo ha `LICENSE` proprietaria BlackRoad, anche se
i 293 file C# risultano byte-identici all'upstream.

## Perché gli strumenti Editor non hanno bridge

`CityMeshTools.cs` non è un componente di gioco: non viene istanziato in scena,
non scrive `PlayerPrefs`, non crea uova e non modifica lo stato del profilo.
Agisce solo su mesh/materiali selezionati esplicitamente in Editor, con `Undo`.
Non esiste quindi uno stato da sincronizzare con Android. Se in futuro verranno
usati per generare prefab che devono comparire nel profilo, andrà aggiunto un
componente runtime dedicato con bridge e uovo posizionale.

## Esclusi dopo il gate tecnico (anche se legalmente permissivi)

| Repo | Motivo |
|---|---|
| SanAndreasUnity | `Assets/RagdollBuilder.cs` ha *Unity Reference Only License*: **non integrabile** (non MIT); `VehiclePhysicsConstants`, `FocusPointManager`, `OutOfRangeDestroyer`, `GameModeManager` duplicano sistemi Huntix già presenti; `AudioStream` dipende da NVorbis con licenza non inclusa. |
| Grand-Road-Auto | Licenza proprietaria del fork; non usato come fonte. |
| Zone Zero | Joystick già adattato in `AlternateTouchInput.cs`; `ServiceLocator`/`GameEvent*` duplicano singleton ed `EventBus`; `ZoneVisual` dipende da Photon; `StarterAssets/**`, `Photon/**`, `PlayFabSDK/**`, `BigRookGames/**`, `ProceduralTerrainPainter/**` non coperti da MIT. |
| RaceRider3d | `AutoMoveMainMenuCameraScript.cs` è solo cosmetico e duplica la camera; il resto è endless runner rotto/duplicato o legato a GooglePlayGames e asset non verificati. |
| battle-island-mobile | Touch mai collegati, UI senza `EventSystem`, `Save()`/`TryCollectLoot()` mai chiamati: codice non funzionante. |
| CarGame | Apache 2.0 ma progetto non apribile/compilabile; `TrafficLightsSwitcher`/`DistanceChecker` sarebbero un downgrade rispetto a `JunctionControl` e `CarAgent`; asset EasyRoads3D/Windridge/non verificati esclusi. |
| FuzzyTown | Apache 2.0 ma `using UnityEditor` in script runtime, prefab `Simple People`/`SimpleTown` acquistati e licenza non verificata; `Crossing.cs` vale solo come spunto già coperto da `TrafficYieldMeter`. |
| 3ddriving, CarRace, Traffic Rider Clone, Unity AI Racing Cars, Smart City Digital Twin | Nessuna licenza per il codice sorgente: nessun permesso di riuso. |
| Third Person Controller Android | GPLv3 copyleft incompatibile con la distribuzione Huntix. |

## Interazione NPC: esito della ricerca (24/11, seconda passata)

Huntix copre già missioni NPC (`NPCMission`), pedoni con bisogni/relazioni
(`NPCController`), prompt di prossimità (`ContextActionController`), dialoghi
indoor/outdoor con tasto `E` (`OutdoorDialogueUI.cs:20`), tap-to-talk con bridge
(`TouchInputHandler.cs:223-237`), chat reale e job di consegna (`JobManager.cs`).

**Nessun clone contiene un sistema "talk to NPC / quest-giver / negoziante"
legalmente e tecnicamente superiore.** In dettaglio:

| Repo | Contenuto NPC | Esito |
|---|---|---|
| SanAndreasUnity (MIT) | Nessun dialogo, quest, bisogno, negozio o consegna. Solo `FollowState`/`PedAI.Recruit` (leader, segui, reazioni di gruppo) e percezione `UnderAimInfo` | **Unico spunto nuovo**: compagno/scorta. Riscritto da zero in `NpcCompanion.cs` |
| SanAndreasUnity | `RagdollBuilder.cs` (Unity Reference Only License), `DeadBody.cs`/`PedModel.cs` (Mirror + GTA) | **Non integrabile**: sottolicenza e asset GTA |
| FuzzyTown (Apache 2.0) | `Person.cs`/`Crossing.cs`: pedone che attende il traffico | Spunto per il "passaggio sicuro"; da riscrivere, prefab `Simple People` esclusi |
| Zone Zero (MIT) | FSM allerta→sparo con NavMesh | Tono incompatibile, dipende da Photon |
| battle-island-mobile (MIT) | Loot da casse, bot ostili | Non pertinente: nessun loot da NPC |
| CarGame, RaceRider3d | Spawner pedoni, semafori, camera follow | Duplicativi |
| 3ddriving, CarRace, traffic-rider-clone, smart-city-digital-twin | Missioni, selezione con raycast | **Senza licenza**: non integrabili |
| Third Person Controller Android | GPLv3, solo `.unitypackage`/`.rar` | Escluso |

Nessuno dei clone ha un bridge Android (zero `.java`/`.kt` tranne il plugin
Google Play Games). Il nuovo compagno ha quindi bridge dedicato
(`NpcCompanionChanged`, `CompanionRecruited`, `CompanionEggSpotted`) e uovo
posizionale con la cattura canonica `FeatureEggHidden`/`EggCapturedInCity`.

Le lacune ancora aperte e **non** coperte dai clone: dialogo per pedone Miacittà
senza missione (ora coperto solo nel caso compagno), tasto `E` in città,
consegna uova a NPC come quest, bridge dei contatori missioni in
`CityWorldSync`, quest-slot NPC ↔ Android, città fittizia senza abitanti.

## Verifica e limiti

- Ogni integrazione runtime è **affiancata e opzionale**: default invariato
  (`Animazione menu: Huntix`, `Console QA: Off`), nessun sistema rimosso.
- Ogni integrazione runtime ha bridge bidirezionale (`MenuAnimationChanged`,
  `QaConsoleChanged`), uovo con luogo/coordinate (`FeatureEggHidden`) e
  misure (`PerfProbe` slot `menu`/`console`, `SessionTelemetry.Mark`).
- Test statici: `backend/test_unity_audit_features.py`; compilazione:
  `backend/preprocessing/cscheck/check.sh`; build: `build_release.sh`.
- **Nessun claim di fluidità, giocabilità o resa visiva** senza collaudo su
  dispositivo: in questa sessione non c'è device/emulator disponibile.
