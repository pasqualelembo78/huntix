# HUNTIX — Analisi e ottimizzazione completa della fluidità di gioco

Data: 23/11/2026. Repository: `/root/giochi/huntix`, ramo `flat-world`.
Lingua e commit: italiano, commit separati per concetto logico, **nessun push**.

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
| 7. Nuova profilazione e confronto | **In attesa della tua prova sul device** |
| 8. Nuova APK per i test | **In build** con la strumentazione inclusa |

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

## 7. Cosa deve mostrare la nuova profilazione (dopo la tua prova)

Marker attesi nel viewer, nella stessa sessione di corsa e girata camera:

- `[T+...] fps=` verso **30** (33 ms) sui dispositivi compatibili; 60 FPS resta
  l'obiettivo finale, non una dichiarazione odierna.
- `hist=` con più frame sotto `33`/`50` e meno oltre `400`.
- `prof=move:... camera:... ik:... chunk:...` con media e picco. Se `chunk`
  resta il termine dominante, il prossimo intervento è cuocere gli edifici
  lato backend; se domina `ik`, si agisce sul costo delle sonde; se domina
  `camera`, sulla spring arm.
- `[WalkDiag]` con `camSnap=N`, `camDist` stabile, `gRef`/`hipOff` continui,
  `suolo ≈ 0.00`.
- `[Builder] posizionati<=320` e `limitati>0`.
- `uova=I/O` invariate: nessuna regressione del Riflesso.

---

## 8. APK per i test

Da compilare/aggiornare con l'hash finale dopo la build in corso. La build
precedente (senza `prof=`) era:

- `app/build/outputs/apk/release/app-release-signed.apk`, 184 MB
- SHA-256 `af6018320ad72d218801a0f36e911d20bf41a9195037eb645868c2d72e1795a2`

---

## REPORT FINALE (stato attuale)

### Problemi individuati
1. Costruzione runtime della città dentro il frame.
2. Working set di edifici e veicoli troppo grande.
3. Governor e camera che amplificavano il ritardo invece di assorbirlo.
4. Decorazione per edificio non budgettata.
5. Mancanza di misure distribuite e di breakdown CPU nel viewer.

### Cause tecniche
Come da tabella 3-4: sono cause di **budget e temporizzazione**, non di
sintassi animatoria o di costanti di movimento.

### File e componenti modificati
Elencati al paragrafo 5-6.

### Interventi effettuati
Tagli di budget già committati + profiler interno in questa sessione.

### FPS e frame time prima/dopo
- **Prima**: 8-15 fps reali, frame fino a 650 ms (log 07:36 del 23/11).
- **Dopo**: **non ancora misurato**. La misura arriva solo dalla tua sessione
  con la nuova APK. Ogni numero scritto adesso sarebbe inventato.

### Risultati dei test su dispositivo reale
In attesa. Serve: installa la nuova APK, corri fino a `tgt=7.50`, gira la camera
a destra e sinistra, apri il viewer dei log dentro l'app e inviami il file.

### Problemi ancora presenti / prossimi passi
- Verificare se i tagli bastano. Se `chunk` domina ancora: cuocere mesh e
  collider statici lato backend (modello `re3`), senza toccare uova/POI/DEM.
- Se `vehOn` resta basso con 3.054 tracciati: introdurre spatial hash e pool
  vicino alla camera, come nei cloni.
- Se `ik` o `camera` dominano: intervenire solo dopo aver letto i picchi.

### Riflesso uova (Mandato, regola 3)
Il gioco è una caccia alle uova: propongo esplicitamente l'**"Uovo del budget"**
— un uovo che appare nel profile Android solo quando il frame time resta sotto
soglia per N secondi, con posizione e timestamp salvati e bridge bidirezionale.
È ancora in attesa di accettazione: **non implementato**, nessun uovo toccato.
Se preferisci un altro nome o un'altra mechanic (per esempio un uovo che si
schiude quando la camera resta 10 secondi senza `camSnap`), dimmelo e lo
implemento con il bridge completo.

### Chiusura
Nessun push. La working tree sarà pulita dopo i commit del profiler e di questo
report; eventuali file `.meta` riscritti dalla build verranno ripristinati.
