# Report finale — audit repository Unity esterni e integrazione in Huntix/Miacittà

Data: 2026-11-23. Branch: `audit-unity-esteri` (base `flat-world`), non pushato.
Backup completo pre-modifica: `/tmp/opencode/backup` (21G, `rsync -a`, exit 0).

Regola seguita: **non dichiarare completata una funzionalità che non è stata realmente testata**.
Dove il collaudo su dispositivo non è stato possibile, lo stato è indicato esplicitamente.

---

## 1. Repository scaricati

13 repository clonati in `/tmp/opencode/research/`, separati dal progetto Huntix:

`3ddriving`, `CarGame`, `CarRace`, `SanAndreasUnity`, `Grand-Road-Auto`,
`Traffic Rider Clone`, `RaceRider3D`, `Unity AI Racing Cars with Waypoints`,
`Third Person Controller Android`, `Zone Zero`, `Battle Island Mobile`,
`FuzzyTown`, `Smart City Digital Twin`.

Analisi eseguita **sola lettura** di sorgenti, `ProjectSettings`, manifest, prefab/scene e
file di licenza. **Nessuno script, eseguibile o strumento proveniente dai repository
scaricati è stato eseguito** e nessun repository esterno è stato aperto con Unity.

## 2. Esito dell'analisi

Risultati per repository, versione Unity, licenza codice/asset e difetti verificati nel
sorgente: `docs/AUDIT_REPOSITORY_UNITY_ESTERNI_20261123.md`, sezione 1.

Sintesi:

- **Apribilità/compilazione/build dei 13 repo esterni: NON VERIFICATA** (Unity non avviato su di essi).
- README non considerati attendibili: dove dichiarano funzionalità assenti dal codice il punteggio è basso.
- Difetti concreti riscontrati: ProjectSettings/Sscene come puntatori Git LFS (CarGame, CarRace),
  errore di compilazione in `pedestrianSpawner.cs:33` (CarGame), sterzo rotto
  `Mathf.Lerp(1,1,...)` (3ddriving), `using UnityEditor` in script runtime (FuzzyTown),
  `update()` mai chiamato (AI Waypoints), touch mai collegati e UI senza EventSystem (Battle Island).
- `Grand-Road-Auto` è risultato identico (`diff -rq`) a `SanAndreasUnity`, ma è un fork
  proprietario BlackRoad: **da evitare**, meglio l'upstream MIT.

## 3. Tabella comparativa (1 = assente/inutilizzabile, 10 = eccellente)

Valuta il **codice letto**, non i diritti d'uso e non prestazioni misurate. I 0 sono normalizzati a 1.

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

## 4. Migliori sistemi individuati

- **SanAndreasUnity (MIT)** — migliore base di pattern: budget temporale per frame, spawn NPC
  limitato con focus point, enter/exit a stati, touch. Huntix ha già equivalenti propri.
- **Smart City Digital Twin (senza licenza)** — migliori IDM con gap/spillback e semafori
  adattivi: **sola idea**, non copiato e non integrato.
- **Zone Zero (MIT)** — controller terza persona/FSM NavMesh; non serve, Huntix ha già
  `PlayerController`/`CameraRig` vincolati alle regole DEM del Mandato.
- **3ddriving / Traffic Rider Clone (senza licenza)** — UX touch, missioni, save, preset
  qualità: solo idee.
- **Multiplayer**: SanAndreasUnity/Zone Zero, ma Huntix ha già `MultiplayerManager`.

## 5. Codice riutilizzato

Nella fase 1 **nessun file di codice o asset dei 13 repository è stato copiato**: le tre
integrazioni effettive sono state reimplementate da zero partendo solo dal pattern architetturale:

1. freno a mano touch/derapata (idea da 3ddriving/CarRace, senza licenza);
2. profili qualità Low/Medium/High sopra i `QualitySettings` già esistenti (idea da Traffic Rider, senza licenza);
3. riflesso uova + eventi bridge Android per ogni nuova feature (regola del Mandato HUNTIX).

Non sono stati importati modelli, texture, audio o file originali di GTA o di altri titoli commerciali.

## 6. Script nuovi

Unity (C#):

- `unity-project/Assets/City/Scripts/Diagnostics/QualityProfileSelector.cs` (+ relativo `.meta`
  generato da Unity): profili Low/Medium/High con persistenza `PlayerPrefs`, nessuna forzatura
  all'avvio se l'utente non ha scelto, emissione `CityQualityChanged`.

Modifiche sostanziali a script esistenti (nuovi metodi, nessuna riscrittura):

- `VehicleController.SetHandbrake(bool)`, fisica derapata senza `WheelCollider`;
- `Game.SetHandbrake`, `Game.OnQualityProfileApplied`, `Game.GrantFeatureEgg`;
- `EggSpawnManager.SpawnRewardEgg(...)`;
- `UIManager`: pulsante touch `FRENO A MANO` (PointerDown/Up);
- `HamburgerMenu`: voce `Qualità grafica` → `ToggleQuality()`;
- `GameManager` (Android): ripristino `cityQuality` ricevuto dal bridge;
- stub `cscheck` aggiornati per le nuove API.

Android (Kotlin/Java): nuovi eventi whitelistati `FeatureEggHidden`, `CityQualityChanged`
e persistenza `city_quality` in `SharedPreferences`.

Test: `backend/test_unity_audit_features.py` (17 test statici dei collegamenti dopo la fase 2).

## 7. File modificati/aggiunti

Commits: `7b9341c` (audit), `bb30124` (freno a mano + qualità), `47fd492` (fix avvio qualità),
`f6f32eb` (bridge bidirezionale). Diff totale: 14 file, +716/−4 righe, più i file di
verifica/report successivi.

| File | Tipo |
|---|---|
| `THIRD_PARTY_NOTICES.md` | aggiunto |
| `docs/AUDIT_REPOSITORY_UNITY_ESTERNI_20261123.md` | aggiunto |
| `docs/REPORT_FINALE_AUDIT_UNITY_ESTERNI_20261123.md` | aggiunto (questo file) |
| `backend/test_audit_regressions.py` | test esistente rieseguito |
| `backend/test_unity_audit_features.py` | aggiunto |
| `backend/preprocessing/cscheck/HuntixStubs.cs`, `UnityStubs.cs` | modificati |
| `unity-project/Assets/City/Scripts/Diagnostics/QualityProfileSelector.cs(.meta)` | aggiunti |
| `unity-project/Assets/City/Scripts/Economy/EggSpawnManager.cs` | modificato |
| `unity-project/Assets/City/Scripts/Game.cs` | modificato |
| `unity-project/Assets/City/Scripts/UI/HamburgerMenu.cs`, `UIManager.cs` | modificati |
| `unity-project/Assets/City/Scripts/Vehicle/VehicleController.cs` | modificato |
| `unity-project/Assets/Scripts/Core/GameManager.cs` | modificato |
| `app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt`, `BridgeActivity.java`, `UnityIpc.kt` | modificati |

Fase 2 (branch `integrazione-clone`):

| File | Tipo |
|---|---|
| `unity-project/Assets/City/Scripts/UI/AlternateTouchInput.cs(.meta)` | aggiunti (MIT Zone Zero adattato) |
| `unity-project/Assets/City/Scripts/Diagnostics/TrafficYieldMeter.cs(.meta)` | aggiunti (solo misura) |
| `unity-project/Assets/City/Scripts/Diagnostics/PerfProbe.cs` | modificato (slot `traffic`) |
| `unity-project/Assets/City/Scripts/Vehicle/Traffic/CarAgent.cs` | modificato (strumentazione, non guida) |
| `unity-project/Assets/City/Scripts/UI/UIManager.cs`, `HamburgerMenu.cs` | modificati (voce input touch) |
| `unity-project/Assets/City/Scripts/Game.cs` | modificato (uovo joystick) |
| `unity-project/Assets/Scripts/Core/GameManager.cs` | modificato (ripristino `altTouch`) |
| `app/src/main/java/com/intelligame/huntix/bridge/{Bridge.kt,BridgeActivity.java,UnityIpc.kt}` | modificati (`AltTouchChanged`) |
| `THIRD_PARTY_NOTICES.md`, report questo | aggiornati |

**Non toccati**: `main`, sistemi già funzionanti (player, camera, traffico, NPC, uova, missioni,
garage, multiplayer, bridge esistente), dipendenze principali, versione Unity. Le modifiche
preesistenti dell'utente (log `docs/huntix_log_*` cancellati, `link.xml`) sono state lasciate
intatte; gli effetti collaterali della build (`PlayerLocomotion.controller`, `PlayerHero.prefab`,
`link.xml.meta`) sono stati ripristinati e non fanno parte dei commit.

## 8. Funzionalità implementate / non implementate

**Implementate (verificate a livello sorgente/compilazione, non su device):**

- freno a mano touch con derapata controllata: nessun `WheelCollider` nuovo, clamp imbardata,
  freno 1,35×, boost yaw 1,55×, velocità max 0,65×, componente laterale 0,22×;
- profili qualità Low/Medium/High (livelli Unity 1/2/3) dal menu, senza sovrascrivere la
  qualità di progetto finché l'utente non sceglie; `PerformanceGovernor` resta attivo;
- riflesso uova: primo drift e primo cambio qualità generano un uovo raro tramite
  `EggSpawnManager`/`EggController`; la cattura segue il percorso canonico
  `Game.OnEggCollected` → `EggCapturedInCity`;
- bridge bidirezionale: Unity → Android (`FeatureEggHidden`, `CityQualityChanged`) e
  Android → Unity (`cityQuality` ripristinato in `GameManager`).

**Non implementate, con motivo:**

- IDM gap/spillback e semafori adattivi (Smart City): repo senza licenza, innesto rischioso su
  `CarAgent`/`JunctionControl`, nessuna misura preliminare;
- salvataggio atomico `.tmp → rename → .bak` (3ddriving): `PlayerPrefs` + snapshot Android
  già in uso, nessun beneficio dimostrato;
- riscrittura fisica con `WheelCollider`, NavMesh, controller terza persona, multiplayer,
  missioni, inventario: sistemi Huntix già completi o vincolati dal Mandato;
- preset qualità automatici all'avvio: rimossi dopo la correzione `47fd492` per evitare
  regressioni visive su dispositivi già configurati.

## 9. Problemi riscontrati

- **Licensing**: 5 repo senza licenza esplicita, 1 GPLv3 (evitato), 1 fork proprietario
  identico a un upstream MIT, asset di terze parti non verificabili in quasi tutti i repo.
- **Qualità dei repository esterni**: LFS pointer, errori di compilazione, script inutilizzati,
  README non attendibili (es. prezzi diversi dal config in Traffic Rider).
- **Build**: primo tentativo AAR Unity fallito per Burst 1.8.18
  (`Encoding.GetEncoding` → `NullReferenceException` nella cache `BurstCache`); cache generata
  spostata da parte e build ripetuta con locale esplicito. Vedi punto 12 per l'esito.
- **Device**: nessun dispositivo Android disponibile; touch, derapata, cambio qualità e
  cattura uova non collaudati in runtime.
- **Firebase/CLI**: emulator Firebase assente (FS-01…FS-10 non eseguiti);
  `gradlew :app:testReleaseUnitTest --offline` bloccato da `junit:junit:4.13.2` non risolto.
- **Rete**: `82.165.218.56:5100` senza TLS; non è stato rimosso il cleartext Android perché
  romperebbe auth/tile/multiplayer.
- **Performance**: lo stall di `20690,9 ms` nel log `docs/huntix_log_20261123_175436.txt`
  resta non diagnosticato: servono nuovi log con `[Builder][Section]`, `gc=`, `last=`, `focus=`, `ts=`.

## 10. Verifiche eseguite

- `cd backend/preprocessing/cscheck && ./check.sh` → **CSCHECK OK** (dopo le modifiche Unity e dopo il bridge).
- `python3 backend/test_audit_regressions.py` → **Ran 8 tests, OK** (blacklist logout, crypto fallback).
- `python3 backend/test_unity_audit_features.py` → **Ran 17 tests, OK** (collegamenti UI→Game→Vehicle,
  uova, bridge, nessuna forzatura all'avvio, joystick alternativo Zone Zero,
  strumentazione traffico).
- Build AAR Unity/Il2Cpp e APK: vedi punto 12.
- I file generati dalla build non sono stati inclusi nei commit; il branch è locale e non pushato.

## 11. Test Android

**Nessun test eseguito su dispositivo fisico o emulatore Android.** Non è quindi dichiarato
verificato il comportamento runtime di: pulsante touch freno a mano, derapata, cambio qualità,
spawn/raccolta uova-premio, persistenza bridge e assenza di regressioni FPS/RAM.
Sono stati verificati solo: compilazione C# via `cscheck`, 17 test statici sui collegamenti
e l'esito della build APK (punto 12). Il collaudo su device resta un passaggio obbligatorio prima del rilascio.

## 12. APK

Stato iniziale: `app/build/outputs/apk/release/app-release-signed.apk` preesistente,
SHA-256 `16dd2b4a5a4d5df3bb900e35dffbea726ec84474cb55bed8d527f4d1975cd82f`,
**precedente alle modifiche dell'audit**.

Build fase 1 **riuscita** (`/tmp/opencode/build_audit_unity_retry.log`):
SHA-256 `50561031bb1649e2fa34d3a5be238d3a7f072a7b4cc921396c8585c183c3eddc`.

Build intermedia della fase 2 (`/tmp/opencode/build_integrazione_clone.log`, `BUILD_EXIT=0`):
SHA-256 `e0ded29fad624458d99e819677a2de336018de0004b2476c61cd772acaa03eed`.
Controllo `global-metadata.dat`: presente `AlternateTouchInput`/`AltTouchChanged`,
assente `TrafficYieldMeter` (la strumentazione era stata aggiunta dopo l'export Unity).
Per questo motivo è stata eseguita una build finale.

Build finale **riuscita**: `LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 SKIP_MAP_UPDATE=1 FORCE_UNITY_AAR=1 ./build_release.sh --auto`
(`BUILD_EXIT=0`, AAR Unity/IL2CPP ricreato, `BUILD SUCCESSFUL`, APK firmato e verificato).
Log: `/tmp/opencode/build_integrazione_finale.log`.

- APK: `app/build/outputs/apk/release/app-release-signed.apk` (184 MiB, 192.524.307 byte).
- SHA-256: `8bd422c0ca32851e595c9e531ca7ea6fa21cfa47d14caeaf25d0fc0a98f2e3c6`.
- Verifica contenuto: `global-metadata.dat` dentro l'APK contiene `QualityProfileSelector`,
  `SetHandbrake`, `SpawnRewardEgg`, `FeatureEggHidden`, `CityQualityChanged`, `FRENO A MANO`,
  e — fase 2 — `AlternateTouchInput`, `AltTouchChanged`, `zone-zero`,
  `Uovo del joystick fluido`, `TrafficYieldMeter` e `crosswalk:`. Quindi l'APK contiene
  sia i sorgenti sia le nuove integrazioni compilate in IL2CPP.
- Primo tentativo della fase 1 fallito per Burst (cache corrotta): risolto spostando
  `unity-project/Library/BurstCache` e ripetendo con locale esplicito. Nessun file
  sorgente modificato per aggirare il guasto.
- Effetti collaterali della build finale su `link.xml.meta`, `PlayerLocomotion.controller` e
  `PlayerHero.prefab` ripristinati prima del commit; i log cancellati dall'utente e
  `docs/huntix_log_20261123_175436.txt` non sono stati toccati.

### Build finale della fase 3 — Città Gioco

Comando: `BUILD_UNITY_AAR=1 FORCE_UNITY_AAR=1 SKIP_MAP_UPDATE=1 LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 ./build_release.sh --auto`
(`EXIT=0`, AAR Unity/IL2CPP ricreato, `BUILD SUCCESSFUL`, APK firmato e verificato).
Log: `/tmp/opencode/build_citta_gioco.log`. Nessun errore Burst o `error CS` nel log.

- APK: `app/build/outputs/apk/release/app-release-signed.apk` — **192.532.593 byte** (184 MiB).
- SHA-256: `dab6856089ee1ec676489e0c65a41bf88f2edf6b97577f9e807dce35a7afcb3f`.
- `global-metadata.dat` contiene: `FictionalCityWorld` (3), `WorldModeChanged` (2),
  `Uovo della città di carta` (1), `citta-gioco` (1), `fictional` (2), oltre a tutti i token
  della fase 2 (`AlternateTouchInput`, `AltTouchChanged`, `zone-zero`, `TrafficYieldMeter`,
  `crosswalk:`). Quindi generatore, bridge e uovo sono effettivamente compilati in IL2CPP.
- I `classes*.dex` contengono la stringa `preload tile saltato`: anche la modifica Android
  che evita il download di tile in modalità fittizia è entrata nell'APK.
- Effetti collaterali della build su `link.xml.meta`, `PlayerLocomotion.controller` e
  `PlayerHero.prefab` di nuovo ripristinati; i `.meta` delle nuove cartelle
  (`City/Scripts/Game`, `Resources/CityRecipes`) invece sono stati inclusi nel commit,
  così gli GUID restano stabili su ogni macchina.

### Build del riabilitamento Quaternius (24/11)

Comando: `BUILD_UNITY_AAR=1 FORCE_UNITY_AAR=1 SKIP_MAP_UPDATE=1 LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 ./build_release.sh --auto`
(`EXIT=0`, `BUILD SUCCESSFUL` due volte, nessun `error CS` o errore Burst nel log).
Log: `/tmp/opencode/build_quaternius.log`.

- APK: **192.532.593 byte** (184 MiB), SHA-256
  `98d5be27bc3b898bb4c2a4f6214ec4db23213cfb91953b2ea6757996cbd5765d`.
- La dimensione è identica alla build precedente perché la modifica è una costante booleana
  (`CityStyle.QuaterniusAllowed` da `false` a `true`), non un nuovo asset.
- Il confronto dei token in `global-metadata.dat` **non dimostra da solo** che il pulsante
  funzioni: i letterali `Quaternius inibito` e `Edifici Kenney: Quaternius inibito` restano
  nel codice come testi dei guard/toast e compaiono identici (2 e 1) in entrambi gli APK.
  La prova effettiva è la lettura della costante nel sorgente (`CityStyle.cs`) e il collaudo
  su device del percorso Hamburger → `Stile edifici`. Nessun assert di "miglioramento" visivo
  o di fluidità è stato fatto senza collaudo.
- Effetti collaterali della build ripristinati prima del commit.

## 13. Licenze e attribuzioni

`THIRD_PARTY_NOTICES.md` raccoglie provenienza, licenza codice e licenza asset di tutti i 13 repo,
con testo MIT completo per SanAndreasUnity (`Copyright (c) 2015 James King`), Apache 2.0 per
CarGame/FuzzyTown, MIT per RaceRider3D/Zone Zero/Battle Island, GPLv3 per Third Person Controller
Android (evitato), e indicazione "nessuna licenza" per i rimanenti. Nella fase 2 il codice MIT
di Zone Zero è stato **adattato con attribuzione** in `AlternateTouchInput.cs`; nessun asset,
prefab, Photon/PlayFab o AppId copiato. Nessun asset GTA importato.

## 14. Ottimizzazioni Android e vincoli rispettati

- Nessun `WheelCollider` aggiunto: la guida resta `Rigidbody` + velocità scalare già collaudata.
- Nessun cambio di dipendenze, di versione Unity, di kit edifici predefinito o di numero di
  veicoli (le due leve principali sul frame rate secondo il Mandato). Il 24/11, su richiesta
  esplicita del proprietario, è stato **riabilitato il pulsante** `Stile edifici` del menu
  hamburger (`CityStyle.QuaterniusAllowed` da `false` a `true`) per provare Quaternius sul
  device: il default resta Kenney e nessun asset è stato rimosso.
- Profili qualità usano i 6 livelli già definiti nel progetto; nessun livello/dipendenza nuova.
- Riflesso uova applicato a ogni nuova feature, con posizione geografica inviata al bridge.
- Bridge richiesto dal Mandato esteso in entrambi i sensi senza toccare i canali esistenti.

## 15. Fase 2 — integrazione "affianca e misura" (branch `integrazione-clone`)

Dopo la richiesta di sostituire "tutti i sistemi", ho applicato la scelta confermata
**"affianca e misura"**: nessun sistema esistente è stato rimosso. Ho innestato prima il solo
sistema input, perché è il punto con minor rischio sull'Universo Unico (DEM, AR, uova, bridge).

- **Sorgente legale**: Zone Zero, MIT, `Copyright (c) 2026 Mishal Bhasim`. È il primo codice
  effettivamente adattato (non solo pattern): `AlternateTouchInput.cs` deriva da
  `VirtualJoystick.cs`/`MobileInputHandler.cs`. Ho rimosso dipendenze non permissive
  (`Unity.VisualScripting`) e il log per-frame, e ho aggiunto smoothing esponenziale, flag
  `Enabled`, bridge e uovo. Nessun asset, prefab, Photon/PlayFab o AppId copiato.
- **Affiancamento**: `UIManager` continua a costruire il `DynamicJoystick` storico; il nuovo
  componente usa la **stessa zona di tocco e le stesse visuali** ed è inerte finché non lo si
  attiva. `SetMoveInput` sceglie `altJoystick.Value` solo se `Enabled`; la latenza input→movimento
  resta misurabile con `PerfProbe.MarkInput`/`InputConsumed`, già presenti.
- **Attivazione e misura**: nuova voce `Input touch: Huntix/Zone Zero` nel menu hamburger.
  Ogni cambio emette `AltTouchChanged{source, tsMt}`, tracciato in `SessionTelemetry` con
  `touch=zone-zero|huntix`. Android salva `alt_touch` e lo restituisce a Unity in
  `BridgeActivity` → `GameManager` (notify=false: nessun evento/uovo doppio al ripristino).
- **Riflesso uova**: prima attivazione → `Uovo del joystick fluido` (Rare), percorso canonico
  `EggSpawnManager` → `EggCapturedInCity`.
- **Non aggiunto**: smoothing camera da SanAndreasUnity. `CameraRig.cs` ha già lo smoothing
  della distanza braccio con la stessa costante di tempo (righe 650-658); duplicarlo avrebbe
  solo introdotto un secondo filtro sullo stesso segnale.
- **Verifiche**: `cscheck` OK; `backend/test_unity_audit_features.py` → **17 test OK**;
  `backend/test_audit_regressions.py` → 8 test OK. APK finale e hash nella sezione 12.
- **Secondo slice — misura traffico (diagnostica, non policy)**: aggiunto
  `TrafficYieldMeter.cs` e uno slot `traffic` in `PerfProbe` (8→9). `CarAgent.CheckObstacle`
  conta le frenate già presenti davanti a player/pedoni e scrive
  `crosswalk: frenate/player/pedoni/dMin/vMedia` in `SessionTelemetry` ogni 25 eventi.
  **Nessuna velocità, corsia o semaforo modificato**: il codice FuzzyTown con
  `using UnityEditor` in runtime e le code `Intersection.cs` non sono stati copiati
  (sarebbero un downgrade di `JunctionControl`). Nessun tile rigenerato.
  Riflesso uova differito: se i numeri giustificheranno una policy attiva,
  l'uovo previsto è `Uovo del passaggio sicuro` con GPS all'incrocio osservato.
- **Non verificato su device**: fluidità percepita, latenza del nuovo joystick, frenate
  contate e persistenza Android→Unity runtime. Nessuna dichiarazione di "sostituzione riuscita".

## 16. Fase 3 — Città Gioco (ricetta OSM, mondo fittizio affiancato)

Dopo la conferma dell'utente ("percorriamo questa strada") ho aggiornato il Mandato con una
regola **additiva**: la città fittizia è un quartiere dello stesso universo Huntix, mai una
sostituzione della città AR/OSM. Il principio emerso dall'audit — i 13 clone funzionano perché
hanno un mondo piccolo e controllato, non per magie nella guida — è stato applicato come
scelta di prodotto, senza importare codice o asset.

- **Ricetta offline**: `backend/preprocessing/city_recipe.py` estrae SOLO statistiche dai PBF
  filtrati (con `pyosmium` nel venv e `locations=True`). Per il centro di Foggia
  (`41.44,15.51,41.49,15.58`): seed `3880082521`, passo strade `177,6 m`, larghezza `6,0 m`,
  isolato `171,7 m`, parchi `3,06%`, `268,8` edifici/km², mix POI reale (bar 65%, scuole 26%,
  ospedali 6%, riparazioni 2%, concessionari 0,5%). Nessuna geometria, DEM o tile nel file.
- **Generatore**: `FictionalCityWorld.cs` legge la ricetta da `Resources/CityRecipes/`, usa
  `System.Random(seed)`, costruisce un quartiere piatto di 5×5 isolati con un unico suolo
  collidibile, strade, parchi, edifici su layer 8 (esclusi dallo snap a terra), POI e confini.
  Il build è misurato dallo slot `fictional` di `PerfProbe` (9→10 slot).
- **Nessun costo di cartografia a runtime**: `ChunkManager` non streama mentre il flag è attivo;
  lo streaming resta sospeso solo in questa modalità e si riattiva tornando a Reale. Anche
  l'Android `HomeActivity` salta il preload di tile/graph/geo quando `world_mode=fictional`.
  Nessun sistema esistente è stato rimosso o disattivato in modo permanente.
- **Interruttore e bridge**: voce `Mondo: Reale (OSM)/Città Gioco (ricetta)` nel menu hamburger.
  Unity emette `WorldModeChanged{mode,recipe,tsMt}`; Android salva `world_mode` e lo restituisce
  in `BridgeActivity` → `GameManager` prima del caricamento della scena City (notify=false,
  quindi nessun evento o uovo duplicato al ripristino).
- **Riflesso uova**: prima attivazione → `Uovo della città di carta` (Epic), percorso canonico
  `GrantFeatureEgg` → `FeatureEggHidden` → `EggCapturedInCity`, con lat/lng e timestamp.
- **Verifiche**: `cscheck` OK; `backend/test_unity_audit_features.py` → **23 test OK**;
  `backend/test_audit_regressions.py` → 8 test OK; `backend/test_city_recipe.py` → 3 test OK.
- **Non verificato su device**: differenza percepita, collisioni, tempo di build del quartiere,
  toggle a caldo e persistenza Android→Unity. Nessuna dichiarazione di "più fluido" o
  "sostituzione riuscita" senza collaudo reale.

## 17. Conclusioni e prossimi passi

L'audit ha prodotto un quadro verificabile dei 13 repository; la fase 2 ha innestato un sistema
di input opzionale con attribuzione MIT e una misurazione diagnostica del traffico; la fase 3 ha
affiancato la Città Gioco generata da ricetta. Nessun sistema esistente è stato copiato, duplicato
o rimosso. Compilazione statica, test e build APK sono passati; **il giudizio finale
sull'esperienza di gioco resta sospeso fino al collaudo su dispositivo** e alla risoluzione dello
stall di build del terreno segnalato nel log.

Prossimi passi consigliati: (1) installare l'APK su device e verificare touch/derapata/qualità/uova;
(2) provare il toggle `Mondo` e leggere `world=`, `citta-gioco:` e `prof=fictional` insieme a
`touch=`/`crosswalk:`; (3) confrontare sullo stesso device la città OSM e il quartiere fittizio
prima di decidere cambi di predefinito; (4) raccogliere un nuovo log con i marker del builder per
diagnosticare lo stall; (5) solo dopo misure, valutare una reimplementazione locale dell'IDM con
semafori adattivi; (6) decidere se perseguire TLS sul server.
