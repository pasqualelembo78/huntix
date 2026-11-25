# Terze parti — provenienza, licenze e attribuzioni

Documento relativo all'audit dei repository Unity esterni eseguito il 2026-11-23
(branch `audit-unity-esteri`). I repository sono stati clonati in `/tmp/opencode/research/`
e **non sono stati eseguiti**. Analisi dettagliata: `docs/AUDIT_REPOSITORY_UNITY_ESTERNI_20261123.md`.

## Regola applicata

**Nessun file di codice o asset dei repository sotto elencati è stato copiato tale quale.**
Dove una soluzione è stata riutilizzata, è stata **reimplementata da zero** partendo dal solo
pattern architetturale, perché la licenza del progetto d'origine non lo consentiva oppure
perché l'implementazione Huntix esistente era già più completa. Le eccezioni sono il codice
MIT di Zone Zero indicato al punto 10 e gli innesti del 24/11 elencati nella sezione
"Integrazioni con codice adattato": in entrambi i casi il codice è stato **adattato**, non
copiato tale quale, mantenendo copyright, licenza e dichiarazione delle modifiche.
Nessun asset binario di alcun repository è stato importato. Le attribuzioni sotto servono a
documentare la provenienza delle idee e a non perdere il collegamento con le licenze originali.

I marchi e i nomi dei progetti restano dei rispettivi proprietari. Huntix non incorpora modelli,
texture, audio o file originali di GTA San Andreas né di altri titoli commerciali.

---

## 1. SanAndreasUnity

- Provenienza: https://github.com/in0finite/SanAndreasUnity
- Licenza codice: **MIT**, `Copyright (c) 2015 James King`.
- Testo licenza (dal file `LICENSE` del repository):

```
MIT License

Copyright (c) 2015 James King

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- Licenza asset: **non verificata**. Il repository non contiene file GTA proprietari, ma carica
  dati di gioco a runtime da una directory configurabile; Huntix **non** utilizza quel meccanismo.
- Uso in Huntix: **sola ispirazione** per budget temporale per frame, limitazione spawn NPC e
  state machine di ingresso/uscita veicoli (già presenti in Huntix con implementazione propria).
  Nessuna riga di codice copiata.

## 2. 3ddriving

- Provenienza: https://github.com/Tritone1/3ddriving
- Licenza codice: **nessuna licenza esplicita** (`license: null`).
- Licenza asset: `THIRD_PARTY_NOTICES.md` interno cita Khronos (CC BY 4.0), Quaternius/Kenney/
  3DAssetsDev (CC0). Non è una licenza per il codice sorgente.
- Uso in Huntix: **sola idea** per il pattern di salvataggio atomico (`.tmp` → rename → `.bak`)
  e per la stratificazione degli input touch. Il salvataggio atomico è stato **valutato e non
  implementato** (Huntix usa `PlayerPrefs` + bridge Android). Nessun codice copiato.

## 3. CarGame

- Provenienza: https://github.com/TeodossiyNushev/CarGame
- Licenza codice: **Apache License 2.0** (file `LICENSE`; `Assets/LICENSE.txt` contiene invece i
  termini Unity Monetization, non una licenza asset).
- Licenza asset: **non verificata** (EasyRoads3D, Windridge, NatureManufacture, pacchetti auto).
- Uso in Huntix: **nessuno**. Progetto non apribile nella copia clonata (ProjectSettings come
  puntatore Git LFS) e con un errore di compilazione verificato.

## 4. CarRace (Overtake)

- Provenienza: https://github.com/Yogesh283/CarRace
- Licenza codice: **nessuna licenza esplicita**.
- Licenza asset: **non verificata** (`Assets/ThirdParty/README.md` si aspetta RCC/NWH esterni).
- Uso in Huntix: **nessuno**. Solo osservazione architetturale sul setup Android centralizzato.

## 5. Grand Road Auto

- Provenienza: https://github.com/BlackRoad-Forge/Grand-Road-Auto
- Licenza codice: **proprietaria BlackRoad**; `BLACKROAD.md` dichiara il fork MIT di
  SanAndreasUnity e la `LICENSE` precisa che il codice upstream resta MIT.
- Uso in Huntix: **nessuno**. cloned risulta identico (`diff -rq`) all'upstream; si fa riferimento
  diretto all'upstream MIT, non a questo fork.

## 6. Traffic Rider Clone

- Provenienza: https://github.com/montetitan/traffic-rider-clone
- Licenza codice: **nessuna licenza esplicita**.
- Licenza asset: primitivi generati a runtime, nessun asset esterno evidente.
- Uso in Huntix: **sola idea** (non implementata in questa fase) per preset qualità mobili e
  missioni con progressione. Nessun codice copiato.

## 7. RaceRider3D

- Provenienza: https://github.com/Boxfort/RaceRider3d
- Licenza codice: **MIT**, `Copyright (c) 2019 Dillon Hemphill`.
- Licenza asset: README dichiara "CCO" (presumibilmente CC0), ma non esiste un file di licenza
  asset né attribuzioni per modelli/audio: **non verificata**.
- Uso in Huntix: **nessuno**. Nessun codice copiato.

## 8. Unity AI Racing Cars with Waypoints

- Provenienza: https://github.com/Frost199/Unity-AI-Racing-cars-with-waypoints
- Licenza codice: **nessuna licenza del repository**; l'unico file è la licenza Apache 2.0 del
  font OpenSans.
- Licenza asset: `Environmental Race Track Pack` di Spyblood Productions (2016), pacchetto
  commerciale redistribuito senza licenza: **non riutilizzabile**.
- Uso in Huntix: **nessuno**.

## 9. Third Person Controller Android

- Provenienza: https://github.com/Jay22K/unity-Third-person-controller-android
- Licenza codice: **GNU GPL v3** (testo integrale standard, senza campi di copyright compilati).
- Licenza asset: **non dichiarata**; il package include Unity Standard Assets, Joystick Pack,
  FBX e animazioni di terze parti.
- Uso in Huntix: **nessuno**. Licenza copyleft incompatibile con la distribuzione di Huntix.

## 10. Zone Zero

- Provenienza: https://github.com/Mishalbhasim/Zone-Zero
- Licenza codice: **MIT**, `Copyright (c) 2026 Mishal Bhasim`.
- Licenza asset: `Assets/StarterAssets/license.txt` = Unity Companion License; BigRookGames
  ("All rights reserved", divieto di redistribuzione di Amplify Color), Photon/PlayFab vendored:
  **non verificati / non copiabili**.
- Uso in Huntix: **codice MIT adattato**, non preso tale quale. Il file
  `unity-project/Assets/City/Scripts/UI/AlternateTouchInput.cs` deriva da
  `Assets/_Project/Scripts/UI/VirtualJoystick.cs` e `.../Player/MobileInputHandler.cs`:
  mantenuta l'attribuzione MIT nel commento in testa, rimossi `using Unity.VisualScripting`
  e il `Debug.Log` per-frame, aggiunti smoothing esponenziale, flag di attivazione e bridge
  Android. È un sistema **affiancato** a `DynamicJoystick`, non una sostituzione.
  **Non copiati**: `Assets/StarterAssets/**` (Unity Companion License), Photon/PlayFab,
  prefab, AppId Photon e ogni asset di terze parti.

## 11. Battle Island Mobile

- Provenienza: https://github.com/ebito-coder/battle-island-mobile
- Licenza codice: **MIT**, `Copyright (c) 2026 ebito-coder`.
- Licenza asset: nessun asset binario nel repository.
- Uso in Huntix: **nessuno** (codice non funzionante: input touch mai collegato, UI senza EventSystem).

## 12. FuzzyTown

- Provenienza: https://github.com/p-v-z/FuzzyTown
- Licenza codice: **Apache License 2.0**.
- Licenza asset: i 266 prefab `Simple People`/`SimpleTown` risultano acquistati
  (`Assets/SimplePeople/Changelog.txt`), licenza **non verificata**.
- Uso in Huntix: **nessuno**. Nessun codice copiato.

## 13. Smart City Digital Twin

- Provenienza: https://github.com/shivesh900/smart-city-digital-twin
- Licenza codice: **nessun file LICENSE** → diritti non concessi.
- Licenza asset: N/A (scene e prefab generati in editor, non presenti nel repository).
- Uso in Huntix: **nessuno**. Solo osservazione architetturale (IDM, semafori adattivi), non implementata.

---

## Città Gioco (2026-11-24)

Generatore `unity-project/Assets/City/Scripts/Game/FictionalCityWorld.cs` ed estrattore
`backend/preprocessing/city_recipe.py`: **codice originale Huntix**, scritto da zero a partire
dalle statistiche estratte offline dai nostri PBF OSM (griglia stradale, isolati, parchi, mix
POI). Non contiene codice, prefab, texture né idee implementative copiate dai 13 repository
auditati: la loro lezione (mondo piccolo e controllato) è stata applicata come scelta di
prodotto, non come import. Nessuna attribuzione aggiuntiva è quindi dovuta.

---

## Kenney Car Kit (asset già presente nel progetto, 2026-11-24)

- Provenienza: Kenney Car Kit, https://kenney.nl/assets/car-kit — **CC0 1.0**.
- File usati: `unity-project/Assets/Resources/Vehicles/*.fbx` e
  `Resources/Vehicles/Textures/colormap.png` (atlas colori). Nessun file scaricato dai 13
  repository auditati e nessun modello prelevato da essi.
- `VehicleMaterialFix.cs` applica a quei prefab lo shader URP/Lit e l'atlas CC0 già presente:
  è codice originale Huntix, necessario perché gli FBX sono importati senza materiali.
- **Esclusi** dall'uso: `ConceptCar006.fbx` (29 MB) e `Resources/Vehicles/Sketchfab/cars kit
  pack.obj`, la cui licenza non è verificata. Restano nel progetto ma non vengono usati dalle
  nuove integrazioni.

---

## Integrazioni con codice adattato (2026-11-24)

Inventario completo e gate legale/tecnico: `docs/INVENTARIO_INTEGRAZIONI_CLONE_20261124.md`.
I file sotto sono stati **adattati**, non copiati tale quale; ogni file Huntix conserva
l'intestazione con copyright e licenza. Nessun asset dei repository è stato importato.

### Zone Zero — MIT, Copyright (c) 2026 Mishal Bhasim

Testo licenza integrale dal file `/tmp/opencode/research/Zone-Zero/LICENSE`:

```
MIT License

Copyright (c) 2026 Mishal Bhasim

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- `Assets/_Project/Scripts/UI/MainMenu/MainMenuAnimator.cs` →
  `unity-project/Assets/City/Scripts/UI/MenuEntranceAnimator.cs`.
  **Modifiche**: rimosso `using TMPro` (inutilizzato) e il pulse infinito del pulsante;
  tre riferimenti fissi sostituiti da una raccolta generica di righe `M_*`; aggiunti
  flag opzionale con `PlayerPrefs`, bridge Android `MenuAnimationChanged`, riflesso uova
  con posizione, `SessionTelemetry.Mark` e `PerfProbe.Measure("menu")`; ripristino degli
  alpha alla chiusura. Il pannello esistente resta invariato quando il flag è disattivato.
- `Assets/Editor/BakeTransformIntoMesh.cs` e `Assets/Editor/EnableGPUInstancing.cs` →
  `unity-project/Assets/City/Editor/CityMeshTools.cs`.
  **Modifiche**: voci menu italiane sotto `Huntix/Strumenti (MIT Zone Zero)`, messaggi e
  `Undo` espliciti, nessuna esecuzione automatica, avviso di misurare con `PerfProbe`
  prima di tenere il flag di instancing. Sono strumenti Editor, non componenti runtime:
  non toccano profilo, uova, `PlayerPrefs` o Android e quindi non hanno bridge.

**Non copiati**: `Assets/StarterAssets/**` (Unity Companion License), `Photon/**`,
`PlayFabSDK/**`, `BigRookGames/**`, `ProceduralTerrainPainter/**` (Asset Store EULA),
prefab e AppId. Il joystick di Zone Zero era già stato adattato in
`AlternateTouchInput.cs` (punto 10) e non è stato reimportato.

### SanAndreasUnity — MIT, Copyright (c) 2015 James King

Il testo licenza integrale è già riportato alla sezione 1, dal file
`/tmp/opencode/research/sanandreasunity/LICENSE`. Fonte usata: **solo upstream MIT**,
non il fork `Grand-Road-Auto` (la cui `LICENSE` è proprietaria BlackRoad).

- `Assets/Scripts/Commands/CommandManager.cs` →
  `unity-project/Assets/City/Scripts/Diagnostics/HuntixCommandManager.cs`.
  **Modifiche**: rimossi `SanAndreasUnity.Net.Player`, `NetStatus`, `Player.onDisable` e
  i permessi server; registri locali, rate-limit per comando, risposte in italiano,
  comando `prof` collegato a `PerfProbe`. Nessun comando può modificare il mondo.
- `Assets/Scripts/UI/Console.cs` e `Assets/Scripts/UI/ConsoleLogEventSubscriber.cs` →
  `unity-project/Assets/City/Scripts/Diagnostics/HuntixDebugConsole.cs`.
  **Modifiche**: rimossi `PauseMenuWindow`, `UGameCore.Utilities.ConcurrentQueue`,
  `GUIUtils` e le stack trace lunghe; coda con `lock`, viewer IMGUI locale con filtri e
  raggruppamento, flag opzionale, bridge `QaConsoleChanged`, riflesso uova con posizione,
  `SessionTelemetry.Mark` e `PerfProbe.Measure("console")`. Nessun dato lascia il device.

**Non copiati**: `Assets/RagdollBuilder.cs` (Unity Reference Only License), qualsiasi
dato/asset GTA, `NVorbis` (licenza non inclusa), i file duplicativi legati a LOD, rete
e costanti veicolo già coperte da sistemi Huntix.

### Pattern NPC riscritto da zero (2026-11-24, seconda passata)

Dallo stesso upstream MIT di SanAndreasUnity è stato riutilizzato **solo il
pattern**, non il codice: il meccanismo di `FollowState.cs` (leader, distanza di
scorta, reazioni al gruppo) e di `PedAI.Recruit` ha ispirato
`unity-project/Assets/City/Scripts/NPC/NpcCompanion.cs`.

**Modifiche sostanziali**: riscrittura integrale con tipi e sistemi Huntix;
nessun dato GTA, `Mirror`, `UGameCore`, `NodeReader`, arma o NavMesh; aggiunti
preferenza opzionale con `PlayerPrefs`, azione `PORTA CON TE`, bridge Android
bidirezionale (`NpcCompanionChanged`, `CompanionRecruited`,
`CompanionEggSpotted`), uovo posizionale con cattura canonica, cooldown e limite
di avvistamenti, slot `PerfProbe` e marker `SessionTelemetry`. Il comportamento
originario di `NPCController` resta invariato finché `FollowMode` non viene
attivato.

**Esclusi esplicitamente**: `Assets/RagdollBuilder.cs` (Unity Reference Only
License), `DeadBody.cs`/`PedModel.cs` (Mirror + dati GTA), `PedestrianDef.cs`
(modello dati GTA), FSM armate di Zone Zero (Photon), AI ostile di
battle-island-mobile e tutti i repo senza licenza. Nessun asset dei repo è
stato importato.

---

## Audit open source 2026-11-24 — 12 progetti (branch `integrazione-open-source`)

Analisi completa: `docs/HUNTIX_OPEN_SOURCE_IMPROVEMENTS_AUDIT.md`. I repository sono stati
clonati in `/tmp/opencode/open-source-audit/` e **non eseguiti**. Qui è una regola assoluta:
**nessun file di codice o asset dei 12 progetti è stato copiato tale quale**. Dove la licenza
lo consente è stato adattato il solo pattern, riscrivendo ogni riga con tipi Huntix e conservando
copyright/licenza/dichiarazione delle modifiche nell'intestazione del file. Dove la licenza non
è verificabile è stata adottata solo l'idea generale. Nessuna mesh, texture, audio o prefab dei
progetti è stato importato.

### Licenze con codice adattato

I cinque progetti sotto dichiarano MIT. Testo comune della licenza (dal file `LICENSE` /
`LICENSE.md` di ciascun repository, verificato durante l'audit):

```
MIT License

Copyright (c) 2015 Denis Rizov
Copyright (c) 2023 Robel Getnet Geremew
Copyright (c) 2025 Elitania Ceciliano
Copyright (c) 2026 Harun Doğacan Kutlu
Copyright (c) 2021 Timothy O'Barr

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- **dbrizov/Unity-CharacterController** — https://github.com/dbrizov/Unity-CharacterController,
  MIT, `Copyright (c) 2015 Denis Rizov`. Fonte `Assets/NaughtyCharacter/_Scripts/SpringArm.cs`.
  File Huntix: `unity-project/Assets/City/Scripts/Player/CameraCollisionRadius.cs`.
  **Modifiche**: formula del raggio isolata in metodo puro `FromCamera(fov, aspect, nearClip)`,
  clamp 0,12–0,60 m, opt-in disattivato, nessuna dipendenza dal `Character` del repository,
  raggio fisso 0,22 m di `CameraRig` conservato.
- **rob1997/Controller** — https://github.com/rob1997/Controller, MIT,
  `Copyright (c) 2023 Robel Getnet Geremew`. Fonte
  `Packages/com.ekaka.core/Runtime/Common/EventBus.cs`. File Huntix:
  `unity-project/Assets/City/Scripts/Environment/EventBus.cs`.
  **Modifiche**: solo il concetto one-shot, adattato all'EventBus struct-based esistente come
  `SubscribeOnce<T>` che si auto-disiscrive; API esistenti invariate; nessun pacchetto
  `com.ekaka.*` importato.
- **unity-ai-npc** — https://github.com/dogacankutlu/unity-ai-npc, MIT,
  `Copyright (c) 2026 Harun Doğacan Kutlu`. Fonte
  `Assets/AIConversationSystem/Runtime/NPC/NPCPersona.cs`. File Huntix:
  `unity-project/Assets/City/Scripts/NPC/CityNpcPersonaDefinition.cs`.
  **Modifiche**: mantenuta solo la separazione dati/contenuto in ScriptableObject; rimossi
  rete, provider OpenAI, chiavi API, CancellationToken e UI accoppiata; aggiunti indizio uovo
  una volta, opt-in e ponte Android esistente.
- **ai-npc-quest-framework** — https://github.com/bceciliano/ai-npc-quest-framework, MIT,
  `Copyright (c) 2025 Elitania Ceciliano`. Fonte
  `QuestSystem/QuestsObjects/QuestData.cs`. File Huntix:
  `unity-project/Assets/City/Scripts/Economy/NpcMissionDefinition.cs`.
  **Modifiche**: solo definizione opzionale applicata con `TryApply(ref MissionData)`; rimossi
  SQLite, OpenAI/APIManager e UI coupling; generatore procedurale `NPCMission` invariato.
- **Unity-Rpg** — https://github.com/weirdbeardgame/Unity-Rpg, MIT,
  `Copyright (c) 2021 Timothy O'Barr`. Fonte `Assets/Game/GameAssetManager.cs`.
  File Huntix: `unity-project/Assets/City/Scripts/Diagnostics/OpenSourceAudit.cs` (sorgente
  `asset-handles`) e `unity-project/Assets/Editor/OpenSourceAuditMenu.cs`.
  **Modifiche**: il concetto di ID stabile/provenienza diventa un controllo di sola lettura sui
  registry Huntix già esistenti; **non** usati `Guid`, `TypeNameHandling.All` di Json.NET né
  `Resources.Load`; nessun JSON di quest importato.

### Apache-2.0 con attribuzione prudenziale

- **MafiaUnity** — https://github.com/MafiaHub/MafiaUnity, Apache License 2.0
  (https://www.apache.org/licenses/LICENSE-2.0), `NOTICE`: Copyright (c) 2018 Dominik Madarász,
  Dávid Svitana, Roman Dobiáš. Fonte `Assets/Scripts/Game/Archetypes/Door.cs` (`isMoving` come
  latch temporale). File Huntix: `unity-project/Assets/City/Scripts/World/InteractDoor.cs`.
  **Modifiche**: latch di 1 s con `Time.unscaledTime` solo dopo teleport riuscito, opt-in
  `useDoorLatch=false`; `BUSSA` e porte negozio mai bloccate; nessun asset Mafia, nessuna
  state machine di animazione copiata. Il codice è riscritto da zero: l'attribuzione è
  prudenziale perché è stato adottato il pattern.

### Solo idea generale, zero codice (licenza non concessa o non verificabile)

- **SanAndreasUnity** — https://github.com/in0finite/SanAndreasUnity, MIT,
  `Copyright (c) 2015 James King` (testo integrale nella sezione 1). Fonte
  `OutOfRangeDestroyer.cs`/`FocusPointManager.cs`. File Huntix:
  `unity-project/Assets/City/Scripts/Economy/EggCullGrace.cs`. **Modifiche**: nessuna
  distruzione di oggetti, culling del solo render a 1 Hz con grazia 3 s e isteresi 60/70 m,
  opt-in. Nessun dato/asset GTA.
- **Android_Unity3D_OpenWorld** — https://github.com/nikunjsingh93/Android_Unity3D_OpenWorld,
  **nessuna licenza per il codice** (solo `README.md`; evidenza da assembly decompilato in
  `/tmp/opencode/ao_cs.il`). File Huntix:
  `unity-project/Assets/City/Scripts/Player/EggCompassAssist.cs`. Solo il principio della soglia
  di velocità per la rotazione della camera, riscritto da zero: opt-in, <2 m/s, max 15°/s.
- **3ddriving** — https://github.com/Tritone1/3ddriving, **nessuna licenza per i C#**
  (il `THIRD_PARTY_NOTICES.md` interno copre solo asset). File Huntix:
  `unity-project/Assets/City/Scripts/Economy/EggJournal.cs`. Solo il pattern atomico
  `.tmp` → `.backup.json` → file e la validazione, riscritti da zero; cache non autoritativa.
- **OpenStash** — https://github.com/unity3d-open-tools/openstash, MIT dichiarato al
  `README.md:18-19` ma **file LICENSE assente e titolare non verificabile**. File Huntix:
  `unity-project/Assets/City/Scripts/Diagnostics/OpenSourceAudit.cs` (sorgente
  `resource-defs`). Solo il controllo editor dei path `Resources`; non importati la griglia
  multi-slot, `prefabPath`, `canDrop` né `SignalNeighbors`.
- **Grand Road Auto** — https://github.com/BlackRoad-Forge/Grand-Road-Auto, **licenza
  proprietaria BlackRoad OS, Inc.** File Huntix:
  `unity-project/Assets/City/Scripts/Diagnostics/OpenSourceAudit.cs` (sorgente
  `load-lifecycle`). Solo il principio generale dei guard-rail sui budget, applicato come
  verifica in sola lettura dei valori Huntix già presenti. Nessuna riga, asset o pattern coperto
  dalla licenza proprietaria è stato riprodotto.

### Quaternius — CC0 / QAL per singolo pack

https://quaternius.com/ — licenza sito **QAL v1.0** (aggiornata 28/08/2026),
https://quaternius.com/license.html. Pagine verificate: `packs/simplenature.html` (Simple Nature
Pack, 13 modelli, **CC0**), `packs/downtowncitymegakit.html` (Downtown City MegaKit, 315 modelli,
**CC0**, parzialmente gratuito), `packs/stylizednaturemegakit.html`, `packs/ultimatenature.html`,
`packs/ultimatestylizednature.html`, `packs/texturedfantasynature.html` (tutti **CC0**) e
`packs/charactersfantasyraces.html` (Characters – Fantasy Races, **QAL**, non CC0).
CC0: https://creativecommons.org/publicdomain/zero/1.0/legalcode.it — rinuncia irrevocabile,
nessun credito obbligatorio, "così com'è", nessuna garanzia su marchi/brevetti. QAL v1.0:
commerciale gratuito, nessun credito richiesto, divieto di rivendere/redistribuire gli asset
come asset e di falsa attribuzione.

File Huntix: `unity-project/Assets/City/Scripts/Diagnostics/OpenSourceAudit.cs` (sorgente
`asset-packs`). È stato aggiunto **solo il manifesto** con URL, licenza e URL licenza; nessuna
mesh o texture è stata scaricata o importata in questa sessione. I modelli Quaternius già
presenti nel progetto (Downtown via `BuildingPlacer`, UAL2 CC0) restano regolati dalle
attribuzioni esistenti.

### Aggiornamenti futuri

Se in seguito verrà importato del codice reale da uno di questi progetti, prima del commit
occorre: (1) verificare la licenza specifica del file, (2) aggiungere il testo della licenza e
l'attribuzione in questo documento, (3) conservare gli avvisi di copyright nei file sorgente.

---

## Trasformazione grafica cinematografica — asset CC0 importati (2026-11-24)

A differenza dell'audit dei repository Unity (dove nessun binario è stato
importato), per la modalità cinematografica sono stati **effettivamente
scaricati, verificati e importati** solo asset con licenza CC0 esplicita.
Tutti i file sorgente grezzi sono in `assets/` (inventario:
`assets/INVENTORY.md`, rigenerazione: `assets/restore.sh`).

### Poly Haven — texture PBR CC0

- Sito: https://polyhaven.com/ — licenza **CC0 1.0**,
  https://polyhaven.com/license. API pubbliche `https://api.polyhaven.com/files/<id>`.
- Texture scaricate in 1K JPG, ognuna verificata per **MD5** contro il valore
  restituito dall'API: `asphalt_01`, `asphalt_03`, `brick_pavement_02`,
  `brushed_concrete`, `brick_wall_001`, `sparse_grass`. Mappe: Diffuse,
  nor_gl (Normal), AO, Rough.
- Destinazione: `unity-project/Assets/Resources/Cinematic/Textures/` (13 file
  + 13 `.meta`). Normal map importate come `NormalMap`, sRGB disattivato,
  aniso 4, mipmap attive. Nessuna texture supera 1024 px.
- Utilizzo: modalità cinematografica opt-in, asfalto/marciapiede/facciate/
  veicoli. I materiali del renderer originale restano invarianti a switch spento.

### Kenney — CC0 1.0

- Sito: https://kenney.nl/assets — licenza **CC0 1.0**,
  https://creativecommons.org/publicdomain/zero/1.0/. Ogni zip conserva il
  proprio `License.txt` originale in `assets/<pack>/`.
- Pacchetti grezzi in `assets/`: `characters.zip` (Animated Characters Retro),
  `road.zip` (City Kit Roads 2.0), `kenney_nature-kit.zip`,
  `kenney_skyboxes.zip`, `kenney_car-kit.zip`, city kit commercial/suburban/
  industrial, `furniture.zip`, `cube-pets.zip`.
- **Importati davvero in Unity** da `assets/road`: 6 FBX (coni, barriere,
  lampioni, cartelli, attraversamento) in
  `unity-project/Assets/Resources/Cinematic/RawCc0/` con colormap e licenza.
  Usati a runtime da `CinematicGraphics.PlaceRawProps` solo con lo switch
  "Asset CC0 grezzi" attivo; i collider delle istanze vengono disattivati per
  non alterare la fisica di guida. Gli altri pack sono già integrati nel
  progetto da commit precedenti (vedi `assets/INVENTORY.md`).

### Quaternius — CC0 1.0 (pack specifico)

- `kit/quaternius.zip` (`assets/quaternius-downtown/`): Downtown City MegaKit
  **FREE**, il cui `License_Standard.txt` dichiara esplicitamente
  "CC0 1.0 Universal (CC0 1.0) Public Domain Dedication".
  https://quaternius.com/packs/downtowncitymegakit.html
- I modelli Quaternius già presenti in `Assets/Resources/Quaternius` restano
  regolati dalle attribuzioni esistenti; la copia grezza in `assets/` è solo
  archivio di sorgente.

### Non importato: licenza non verificabile

- `character/character1.unitypackage` ("Stylized Player Character (Free)"):
  il README interno non dichiara alcuna licenza. Pur essendo tecnicamente
  compatibile (Unity 6, URP, 30.682 triangoli), **non è stato importato né
  usato**: i personaggi di Huntix restano Kenney CC0.

---

## Asset degli eventi sportivi — 25/11/2026

Come da Mandato §12 non ci si è limitati al CC0: sono stati acquisiti asset
gratuiti con licenze compatibili e attribuzione. Elenco completo, URL, date,
modifiche e motivi degli scarti in `unity-project/Assets/ThirdPartyLicenses.md`.

- **CC0 1.0**, 3dassets.dev: `Football Soccer Stadium and Club Grounds`,
  `Racecourse and Parade Ring`, `Horse Stables and Equestrian Yard` (106 modelli
  GLB verificati via API, 105 convertiti in FBX; esclusi gli asset di scommesse).
- **CC BY 4.0**, "Race Horse" by vinish.dev
  (https://vinish.dev/free-3d-models/): modello animato con sei clip, mesh
  decimata e convertita in FBX. Attribuzione obbligatoria riportata anche nei
  crediti dell'app e nel report `docs/HUNTIX_EVENTI_SPORTIVI_REPORT.md`.
- Non importato lo stadio Animatics: download solo tramite carrello/account e
  licenza non verificabile in forma autorevole.
