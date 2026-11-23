# HUNTIX — Analisi e ottimizzazione completa della fluidità di gioco

Data: 23/11/2026. Repository: `/root/giochi/huntix`, ramo `flat-world`.
Lingua e commit: italiano, commit separati per concetto logico, push su
`origin/flat-world` eseguito (prima i commit del profiler, poi quelli dell'uovo).

> Regola richiamata dal Mandato: non dichiarare risolto nulla perché compila
> o perché l'APK si avvia. La verifica finale è solo quella sul device, con
> il viewer dei log dentro l'app (mai `adb logcat`).

---

## 0. Stato del metodo richiesto

| Passo richiesto | Stato |
|---|---|
| 1. Analisi dello stato attuale | **Fatto** (statica + log reale già disponibile) |
| 2. Profilazione della build installata su dispositivo reale | **Parziale**: usato il log reale del 23/11; il device non è collegato a questa postazione |
| 3. Individuazione delle cause | **Fatto** |
| 4. Report tecnico delle criticità | **Fatto** (questo documento) |
| 5. Definizione degli interventi | **Fatto** |
| 6. Implementazione delle correzioni | **Fatto**: tagli di budget + profiler interno |
| 7. Nuova profilazione e confronto | **Fatto**: log `docs/huntix_log_20261123_122151.txt`, sessione 12:21 (vedi paragrafo 7) |
| 8. Nuova APK per i test | **Fatto**: hash `37045e53…`, vedi paragrafo 8 |

Il passo 2 non può essere completato da qui: non c'è un telefono collegato e
il Mandato vieta `adb logcat`. Ho quindi reso il viewer in-app più informativo
invece di inventare numeri.

---

## 1. Analisi dello stato attuale

### Movimento
- `PlayerController.cs`: `walkSpeed=3.2`, `runSpeed=7.5`, `turnSpeed=12`,
  `gravity=-25`, `jumpForce=9`; `AccelSmoothS=0.08`, `BrakeSmoothS=0.12`,
  substep fisico `MaxFrameStepS=0.05`, clamp `MaxFrameDtS=0.25`.
- Input: joystick sinistro in `UIManager.cs` → `SetMoveInput()`; rotazione vista
  in `OrbitZone.cs` → `CameraRig.Orbit()`; salto in `TouchInputHandler.cs`.
- La velocità consegnata all'Animator è **quella reale misurata** dallo
  spostamento (`PlayerController.cs:455-470`), non quella richiesta dall'input.
  È una scelta corretta: evita che l'animazione "corra" mentre il player è
  bloccato da un muro.

### Camera
- `CameraRig.cs`: follow con `SmoothDamp`, spring arm con una sola
  `SphereCastNonAlloc` (hit più vicino, mask che esclude il layer edifici 8),
  snap dinamico `gapLimit = 0.35 + v*tau`, rotazione esponenziale
  `RotationFollow=6.5`, switch 1A/3A con ponte Android↔Unity.
- `MovementBasis` usa solo lo yaw del drag, mai il giroscopio: muoversi non
  cambia direzione quando si muove solo il telefono.

### Animazioni
- `PlayerLocomotion.controller`: BlendTree 1D su `Speed` con soglie
  `0 / 3.2 / 7.5`, parametro `IsGrounded`, transizioni Jump 0.08 s / 0.15 s,
  IK Pass attivo.
- `CharacterWalker.cs`: setta solo `Speed` e `IsGrounded`; il Foot IK usa due
  `RaycastNonAlloc`, prende l'hit più vicino, `groundRef` continuo
  (`feetY - skinWidth`) e compensa il bacino. Le vecchie costanti di
  smoothing >1 erano state corrette in `05e50e2`.

### Fisica e suolo
- `CharacterController` con `slopeLimit=55`, `stepOffset=0.4`, `skinWidth=0.05`.
- `GroundSnapper.cs`: `WalkableMask = ~(1<<8)`, sonda corta → lunga → DEM,
  `PickBest` prende l'hit più vicino (mai il tetto della colonna), regola
  NON negoziabile del Mandato.
- `[SoleCheck]` / `[GroundDiag]` misurano caviglia, suola, bacino, sorgente.

### Carico del mondo
- `ChunkManager.cs`: `LoadRadius=3` (7×7 = 49 chunk da 1 km²), `BuildBudgetMs=12`,
  `MaxConcurrentBuilds=1`.
- `ChunkBuilder.cs`: `MaxPlacedBuildingsPerChunk=320`, `BuildingsPerStep=12`,
  log `[Builder]` con `edificiTotali / candidati / posizionati / limitati`.
- `BuildingPlacer.cs`: `MaxEnterablePerChunk=8`.
- `URP_Pipeline.asset`: `m_RenderScale=0.75`, HDR disattivato, ombre conservate.

---

## 2. Profilazione reale disponibile (prima)

Unica sessione con misure sul device: `docs/huntix_log_20261123_073631.txt`
(Samsung SM-A356B, Android API 36, 1080×2340, quality=Medium, vsync=1).
L'utente ha corso e girato la camera.

| Misura | Valore |
|---|---|
| FPS | 8,0 / 8,8 / 9,0 / 10,0 / 10,5 / 11,0 / 11,5 / 12,5 / 15,0 |
| Frame peggiore | fino a 650 ms |
| Istogramma | quasi nessun frame sotto 16/33 ms; maggioranza 50-400 ms |
| `camSnap=Y` | 11 campioni su 17 |
| `camDist` | 5,95 - 7,45 m (oscillazione a ogni hit) |
| `GOVERNOR tier=4` | raggiunto in 0,8 s |
| `BUILD START` | 56 in ~50 s, 2 build concorrenti da 24 ms |
| Edifici in un chunk | 1.792 / 9.457 / 13.614 |
| RAM | 166 MB → 556 MB in 45 s |
| `vehOn` | 2 attivi su 3.054 tracciati |

---

## 3-4. Cause e criticità individuate

| # | Criticità | Causa tecnica (con evidenza) | Gravità |
|---|---|---|---|
| 1 | Frame da 400-650 ms | Costruzione di mesh/collider OSM **dentro il frame**: 13.614 edifici in un chunk, 2 build da 24 ms | Alta |
| 2 | Governor al massimo in 0,8 s | `UpConfirmSeconds=3` confrontato con un contatore per frame (3 frame a 8 fps = 0,4 s) | Alta (risolto) |
| 3 | Camera che scatta | `camSnap=Y` frequente: la spring arm inseguiva un mondo che non arrivava; `camGap=0,35` fisso | Alta (mitigato) |
| 4 | IK tremolante | `dt=0,05` bigotto e `Lerp` con fattore >1 a basso fps; `groundRef` a salti | Alta (risolto in `05e50e2`) |
| 5 | Culling inefficace | 3.054 veicoli tracciati, 2 attivi, lista scorsa tutta ogni frame | Media |
| 6 | Costo per edificio | Ogni edificio = Renderer + Collider + Porta + Interno + Luce; a Roma ~63.000 GameObject in più | Alta (mitigato) |
| 7 | Carico GPU | 1080×2340, URP + HDR + ombre, 29 MB texture Quaternius (default Kenney ripristinato) | Media (mitigato) |
| 8 | Osservabilità insufficiente | Nessun marker `Profiler.BeginSample`; il beacon aveva solo last-value e istogramma dei frame | Media (risolto con profiler interno) |

Nota: la causa 1 non è un "valore di smoothing sbagliato". È un problema di
**architettura temporale**: il telefono fa da build server e da render engine
nello stesso frame. Per questo il Mandato vieta le correzioni numeriche a caso.

---

## 5-6. Interventi effettuati e file modificati

### Tagli già applicati (commit precedenti, verificati nel codice)

| Commit | Intervento |
|---|---|
| `aa506d0` | Snap camera dinamico `0,35 + v*tau`; braccio smorzato con la stessa costante di tempo |
| `5695e45` | Governor conta secondi, non frame |
| `598b5b4` | `MaxPlacedBuildingsPerChunk = 320` |
| `e981470` | `MaxConcurrentBuilds = 1`, `BuildBudgetMs = 12` (LoadRadius resta 3) |
| `73217d9` | `MaxEnterablePerChunk = 8` |
| `27113ae` | URP render scale 0,75, HDR off, ombre conservate |

### Strumentazione aggiunta in questa sessione

Nuovo file `unity-project/Assets/City/Scripts/Diagnostics/PerfProbe.cs` (+ `.meta`):
profiler interno che misura, con scope senza allocazioni per frame:

- `move` = tempo di `PlayerController.Update()`;
- `camera` = tempo di `CameraRig.LateUpdate()`;
- `ik` = tempo di `CharacterWalker.RunFootIK()`;
- `chunk` = tempo della pompa di costruzione in `ChunkManager.Update()`.

`SessionTelemetry.cs` aggiunge al beacon `[T+...]` il campo `prof=...` con
media/picco dei 2 secondi precedenti. Tutto passa da `UnityBridge.LogToAndroid`,
quindi finisce nel viewer in-app. Non sono stati aggiunti marker Unity Profiler:
nella build non sarebbero leggibili dal viewer e richiederebbero il PC collegato.

File toccati:
- `Assets/City/Scripts/Diagnostics/PerfProbe.cs` (nuovo)
- `Assets/City/Scripts/Diagnostics/PerfProbe.cs.meta` (nuovo)
- `Assets/City/Scripts/Diagnostics/SessionTelemetry.cs`
- `Assets/City/Scripts/Player/PlayerController.cs`
- `Assets/City/Scripts/Player/CameraRig.cs`
- `Assets/City/Scripts/Player/CharacterWalker.cs`
- `Assets/City/Scripts/OSM/ChunkManager.cs`

**Non toccati di proposito**: costanti di movimento, BlendTree, parametri IK,
soglie di pendenza, `LoadRadius`, spawn uova, bridge, DEM, POI.

Verifica: `cd backend/preprocessing/cscheck && ./check.sh` →
`Compilation succeeded - 185 warning(s)` / `CSCHECK OK`.

---

## 6. Confronto con i giochi di riferimento

Non ho i sorgenti di PK XD, Play Together, Brookhaven o GTA V e quindi non
invento numeri loro. Il confronto è sulle **scelte architetturali** che questi
titoli applicano e che sono verificabili anche nei cloni open source esaminati
(`crime-city.html` e il port `re3` di GTA III su Raspberry Pi 5):

| Pattern | Huntix prima | Giochi di riferimento |
|---|---|---|
| Mondo | Ricostruito da OSM a runtime | Precotto / streammato da archivio (`re3` richiede i file originali `models`, `data`, `audio`) |
| Working set | 49 chunk densissimi, fino a 13.614 edifici/chunk | Celle/isolati visibili, LOD e culling stringenti |
| Attori | 3.054 veicoli tracciati | Pool chiusi vicini alla camera (es. clone: 24 auto, 130 pedoni) |
| Collisioni | Renderer/Collider per edificio | Proxy semplici, box/cerchi, maschere di layer |
| Tick | Passi fissi bigotti + frame erratico | `DT=1/60` con accumulator e massimo 5 step |
| Camera | Snap quando il mondo è in ritardo | Smoothing esponenziale continuo della vista e della distanza |
| Animazioni | BlendTree + IK corretti, ma alimentati da frame instabili | Stessi concetti, ma con 30/60 fps costanti che li rendono invisibili |
| GPU | Render scale pieno, HDR, ombre, texture pesanti | Qualità tarata per il dispositivo, riflessi/alpha semplificati |

La differenza qualitativa che vedi in GTA V o Brookhaven non è che abbiano
"un motore migliore" in senso astratto: è che **il frame è prevedibile**.
Con 16,7 o 33,3 ms sempre disponibili, anche un'animazione semplice sembra
naturale; con frame da 400 ms, anche un IK perfetto sembra un tremito.

---

## 7. Risultati della prova reale (sessione 12:21)

File: `docs/huntix_log_20261123_122151.txt` (contiene più sessioni; quella
nuova è il blocco con timestamp `2026-11-23 12:21`). Stesso dispositivo
Samsung SM-A356B, Medium, vsync=1, 1080×2340.

| Misura | Prima (07:36) | Dopo (12:21) | Esito |
|---|---|---|---|
| FPS (beacon stabili) | 8-15 | **15-19** | Migliorato, non ancora sufficiente |
| Frame peggiore | fino a 650 ms | 233-400 ms | Ridotto |
| `camSnap=Y` | 11 campioni su 17 | **0** (`camSnap=N` ovunque) | Risolto |
| `camDist` | 5,95-7,45 m oscillante | 7,1-8,4 m, continuo | Migliorato |
| `prof=move` media/picco | non misurato | 0,14-0,24 / 1,2-1,6 ms | Trascurabile |
| `prof=camera` | non misurato | 0,01-0,02 / 0,0 ms | Trascurabile |
| `prof=ik` | non misurato | 0,08-0,11 / 2,0-2,3 ms | Trascurabile |
| `prof=chunk` | 2 build da 24 ms | 0,30-0,94 / 2,2-4,1 ms | Risolto il picco |
| `prof=input` | non misurato | 1,62 / 1,6 ms | Reattività buona |
| `GOVERNOR` | tier 4 in 0,8 s | tier 3, ema 86 ms a T+38 | Meno aggressivo |
| RAM | 166→556 MB in 45 s | 423→471 MB in 10 s | Crescita ancora presente |
| `[Builder] posizionati` | fino a 13.614 edifici | 193 / 235 / 348 / 221 | Cap rispettato |
| `limitati` | - | 0 (i candidati già filtrati sono <320) | Coerente |
| `uova=I/O` | I0/O0 | I0/O0 | Nessuna regressione |

**Interpretazione:** i interventi su camera, governor e pompa chunk hanno
funzionato: lo scatto della camera è sparito e il costo CPU dei nostri scope è
ora di frazioni di millisecondo. Il frame time resta però 33-100 ms con picchi
fino a 400 ms. Poiché `move`, `camera`, `ik` e `chunk` sono tutti <1 ms di
media, il costo rimanente **non è in quei quattro scope**: sta in rendering /
GPU, fisica PhysX, GC o `Instantiate` non strumentati. Cuocere le mesh lato
backend a questo punto NON è più la prima leva (il `chunk` non domina più).

**Prossima misura da aggiungere**, prima di toccare altro: uno scope `render`
attorno al submit e un contatore di allocation/`Instantiate` per beacon, così
si distingue GPU da GC. Poi, solo se i dati lo mostrano, si interviene su
pool veicoli/decorazioni o su URP.

---

## 8. APK per i test

APK finale con profiler interno, latenza input→movimento, **Uovo del budget** e
blocco del caricamento Quaternius (`STILE edifici=Kenney quaterniusInibito=True`):

- `app/build/outputs/apk/release/app-release-signed.apk`, 184 MB
- SHA-256 `37045e532016aaaa46e2019e29b09e385f9514903f67169db665107667bb9223`
- Build: AAR Unity `BUILD SUCCESSFUL in 4m 46s`, APK `BUILD SUCCESSFUL in 1m 38s`.

APK intermediate superate (non usare):
- `af6018320ad72d218801a0f36e911d20bf41a9195037eb645868c2d72e1795a2` — tagli senza `prof=`
- `979eb2d893820be44dea7ee138f25ae6233c0de795b4212b592dd690f862e987` — `prof=` senza latenza input
- `52f516d06e7adb4e277c7e7534ab51b688dd6295895282a650185fb576220c07` — profiler completo, senza uovo budget
- `e66ae7bd16d50440224390eca9a84b44f2d8bd568d9eb7fde5798787fcce3f1b` — uovo budget, senza blocco Quaternius e senza marker `STILE`

---

## 9. Uovo del budget (Riflesso del Mandato)

Accettato e implementato con il flusso canonico, senza sistemi paralleli.

**Meccanica.** `BudgetEggWatcher` (nuovo, in `Assets/City/Scripts/Economy/`)
si arma quando: mondo pronto, player a piedi, minigioco uova non attivo, e
l'EMA del `PerformanceGovernor` resta sotto **33 ms per 5 secondi**. Allora
spawna un uovo **Epic** 3 m davanti al player, agganciato al suolo con
`CityChunkedWorld.TryGroundColumn` (mai al tetto di un edificio), con
`placeHint = "città→budget→frame<33ms→5s"`.

**Cattura.** Non c'è un bypass: `EggController.OnCaptured → Game.OnEggCollected →
SendEggToHuntix → Bridge.handleCityEggCaptured → EggInventoryManager /
EggWhereLog`. Il payload ora include `eggId` stabile, `kind=budget`, `tsMt`,
oltre a `rarityId`, `fantasyName="Uovo del budget"`, `power`, `xpReward`,
`gems`, `lat`, `lng`, `place`. `Game.OnEggCollected` chiama
`BudgetEggWatcher.MarkCaptured` e `ContainerEgg.MarkFound` con la stessa chiave.

**Bridge bidirezionale.**
- Unity → Android: nuovo evento whitelistato `BudgetEggSpawned` (eggId, place,
  lat, lng, timestamp, soglia, EMA); Android lo registra in `AppLog` e mostra
  un toast. Nessuna SharedPreferences nuova.
- Persistenza: lo snapshot `CityWorldSync` porta `budgetEggSpawned`,
  `budgetEggFound`, `budgetEggId`, `budgetEggLat`, `budgetEggLng`,
  `budgetEggTs`. Android li conserva nel già esistente `city_sync_prefs` e li
  restituisce col restore: l'uovo non rinasce dopo un rebuild del chunk.

**Nota sul dispositivo attuale:** con EMA 86 ms l'uovo NON compare ancora — è
il trofeo del traguardo dei 30 fps, non uno sfogo del log. Quando il frame
scende sotto 33 ms il viewer mostra `UOVO budget: frame sotto soglia ...` e
poi `UOVO budget comparso: ...`.

---

## 10. Verifica kit edifici (Kenney vs Quaternius)

Ipotesi sollevata dall'utente (e da ChatGPT): i modelli Quaternius con 29 MB di
texture potrebbero essere la causa dei 15-19 fps. **I dati della prova reale
smentiscono l'ipotesi per quel dispositivo:** nel log delle 12:21 ci sono
**8010** occorrenze di `useQuaternius=False` e **8010** di `via=Kenney`, zero
caricamenti Quaternius. Il default del codice è Kenney dal commit `376fbc9`
(19/11); l'indicazione "default 1" in `AGENTS.md` è superata.

Ho comunque applicato la richiesta come **salvagardia**: `CityStyle.QuaterniusAllowed
= false` impedisce il caricamento Quaternius anche se un vecchio `PlayerPrefs`
vale 1. Prefab, FBX e texture Quaternius restano integri nel progetto; il codice
di `BuildingPlacer.LoadQuaternius` resta intatto e si riattiva portando la
costante a `true`. L'hamburger menu mostra "Veloce (Kenney) — Quaternius
inibito" e spiega il perché invece di un toggle muto. `SessionTelemetry`
scrive nel viewer e nel bridge Android il marker `STILE edifici=...`.

**Conclusione onesta:** questo intervento non spiega i 15-19 fps e non dovrebbe
cambiarli sul dispositivo già in Kenney. La causa rimasta fuori dagli scope
misurati (GPU/fisica/GC/instantiate) resta la prossima da misurare.

---

## REPORT FINALE (stato attuale)

### Problemi individuati
1. Costruzione runtime della città dentro il frame (risolto il picco).
2. Camera che amplificava il ritardo (risolto: `camSnap=N`).
3. Governor che contava i frame invece dei secondi (risolto).
4. Working set di edifici/veicoli e decorazione per edificio (mitigato).
5. Osservabilità insufficiente (risolto con `prof=`).

### Cause tecniche
Cause di **budget e temporizzazione**. La misura successiva ha spostato il
sospetto fuori dagli scope misurati: rendering/GPU, fisica, GC o instantiate.

### File e componenti modificati
Paragrafi 5-6 (tagli e profiler) e paragrafo 9 (uovo del budget).

### Interventi effettuati
Tagli di budget, profiler interno, misura latenza input, uovo del budget con
bridge e persistenza.

### FPS e frame time prima/dopo
- **Prima**: 8-15 fps reali, frame fino a 650 ms (log 07:36 del 23/11).
- **Dopo**: 15-19 fps reali, frame 33-100 ms, picchi 233-400 ms (log 12:21).
- **Obiettivo dichiarato**: 30 fps stabili (33 ms); 60 fps resta il traguardo
  finale. Non dichiaro il problema risolto.

### Risultati dei test su dispositivo reale
Prova eseguita e misurata (12:21). Camera e chunk non sono più la causa
dominante. Restano da misurare GPU/fisica/GC.

### Problemi ancora presenti / prossimi passi
1. Aggiungere uno scope `render` e un contatore allocate/instantiate al beacon.
2. Con quei dati, decidere tra URP, pool veicoli o decorazione edifici.
3. Rifare la prova e verificare che l'Uovo del budget compaia.

### Chiusura
Profiler, report, uovo del budget e blocco Quaternius sono committati e pushati
su `origin/flat-world`. I file `.meta`/prefab riscritti dalla build sono stati
ripristinati prima del commit. La verifica finale resta sul dispositivo: serve
una nuova sessione con questa APK e il log del viewer in-app, perché la causa
dei 15-19 fps non è ancora misurata.

---

## ANALISI E FIX DEL TERRENO 3D (23/11, secondo intervento)

Analisi statica approfondita del terreno (sola lettura) e successiva
applicazione dei fix. Valori verificati nel codice: DEM server 250 m
(`backend/preprocessing/dem.py:72`), mesh terreno 45 m
(`TerrainChunk.cs:89`), `LoadRadius=3` → 49 chunk, collider del terreno
inizialmente attivo fino a LOD1 (tutti i 49 chunk). In build corrente
`CityConfig.WorldHeights=Flat`: la mesh DEM è compilata ma spenta e ogni chunk
ha soltanto **4 vertici / 2 triangoli** di terreno (`terrenoVerts=4` nel log).
I 510.000 triangoli storici erano 20.402 tri/chunk × 25 chunk della mesh a 10 m.

### Fix applicati (nessun cambio di gameplay)

1. **`Physics.autoSyncTransforms = false`** in `ChunkManager`, `CityChunkedWorld`
   e `CityOSMWorld`: prima era forzato a `true` in tre punti, con sync PhysX a
   ogni spostamento di transform. La registrazione dei collider runtime resta
   garantita dai sync espliciti esistenti (fine build, spawn bridge, rebase,
   `GroundSnapper.SyncOnce` una volta per frame).
2. **`GroundDiag` solo in editor/development build**: in release non viene più
   attaccato al player, eliminando il secondo `SampleGround` (5 SphereCast) per
   frame. La telemetria `prof=`/`ground=` resta attiva.
3. **Sonde NPC sfalsate 1/3 oltre 40 m** con dt accumulato nel follower: stesse
   quote finali, ~3× meno SphereCast per frame. Auto `TrafficCar`: confronto
   `sqrMagnitude` prima della radice su tutti gli NPC attivi.
4. **Filtro geografico delle strade prima di `CollectJunctions`**
   (`RoadRenderer.FilterByGeoBounds`): i chunk di campagna non ricalcolano più
   giunzioni su 1724 strade/1816 nodi della tile quando l'asfalto finale è 0.
5. **Pool degli accumulatori mesh** (~96 KB ×3 per chunk) e `LogDebug` per le
   righe per-edificio in release: meno GC e meno chiamate JNI durante il build.
6. **Un solo `sharedMesh`** nel MeshCollider del terreno (prima mesh→null→mesh,
   due cook PhysX per chunk).
7. **`TerrainColliderLod = 0`**: collider del terreno solo nei 3×3 chunk attorno
   al giocatore (~9.500 tri con DEM invece di ~51.800), non più in tutti i 49.
8. **`RaycastAll` → `RaycastNonAlloc`** con buffer condivisi in `GroundSnapper`
   e `CityChunkedWorld`; commenti e numeri aggiornati in `ChunkManager`.

### Rischi e verifica

- Il rischio maggiore è il passaggio ad `autoSyncTransforms=false`: se una
  sincronizzazione esplicita mancasse, il player potrebbe attraversare il suolo
  appena costruito. Le guardie esistenti (build, spawn gate con retry 15 s,
  rebase, `SyncOnce`) sono state mantenute e rafforzate dai commenti.
- `TerrainColliderLod=0` è coperto dal fatto che il centro LOD è il chunk del
  player e si aggiorna ogni 0,25 s; NPC/veicoli lontani usano `TileElevation`,
  non i collider.
- **Non dichiaro il problema risolto**: serve una nuova sessione su device con
  l'APK di questa build e il log del viewer in-app per verificare ground, spawn
  e frame time in campagna aperta.
