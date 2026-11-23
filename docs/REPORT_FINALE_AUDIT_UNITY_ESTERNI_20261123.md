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

**Nessun file di codice o asset dei 13 repository è stato copiato.** Le tre integrazioni
effettive sono state reimplementate da zero partendo solo dal pattern architetturale:

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

Test: `backend/test_unity_audit_features.py` (11 test statici dei collegamenti).

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
- `python3 backend/test_unity_audit_features.py` → **Ran 11 tests, OK** (collegamenti UI→Game→Vehicle,
  uova, bridge, nessuna forzatura all'avvio).
- Build AAR Unity/Il2Cpp e APK: vedi punto 12.
- I file generati dalla build non sono stati inclusi nei commit; il branch è locale e non pushato.

## 11. Test Android

**Nessun test eseguito su dispositivo fisico o emulatore Android.** Non è quindi dichiarato
verificato il comportamento runtime di: pulsante touch freno a mano, derapata, cambio qualità,
spawn/raccolta uova-premio, persistenza bridge e assenza di regressioni FPS/RAM.
Sono stati verificati solo: compilazione C# via `cscheck`, 11 test statici sui collegamenti
e l'esito della build APK (punto 12). Il collaudo su device resta un passaggio obbligatorio prima del rilascio.

## 12. APK

Stato iniziale: `app/build/outputs/apk/release/app-release-signed.apk` preesistente,
SHA-256 `16dd2b4a5a4d5df3bb900e35dffbea726ec84474cb55bed8d527f4d1975cd82f`,
**precedente alle modifiche dell'audit**.

Build finale **riuscita**: `SKIP_MAP_UPDATE=1 FORCE_UNITY_AAR=1 ./build_release.sh --auto`
(`BUILD_EXIT=0`, AAR Unity/IL2CPP ricreato, `BUILD SUCCESSFUL`, APK firmato e verificato).
Log: `/tmp/opencode/build_audit_unity_retry.log`.

- APK: `app/build/outputs/apk/release/app-release-signed.apk` (184 MiB, 192.512.019 byte).
- SHA-256: `50561031bb1649e2fa34d3a5be238d3a7f072a7b4cc921396c8585c183c3eddc`.
- Verifica contenuto: `global-metadata.dat` dentro l'APK contiene `QualityProfileSelector`,
  `SetHandbrake`, `SpawnRewardEgg`, `FeatureEggHidden`, `CityQualityChanged`, la stringa
  `FRENO A MANO` e i log `[Quality] profilo=`/`[GameManager] qualita' ripristinata da Android`:
  quindi l'APK contiene le nuove integrazioni, non solo i sorgenti.
- Primo tentativo fallito per Burst (cache corrotta): risolto spostando
  `unity-project/Library/BurstCache` e ripetendo con `LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8`.
  Nessun file sorgente modificato per aggirare il guasto.
- Effetti collaterali della build su `link.xml.meta`, `PlayerLocomotion.controller` e
  `PlayerHero.prefab` ripristinati prima del commit.

## 13. Licenze e attribuzioni

`THIRD_PARTY_NOTICES.md` raccoglie provenienza, licenza codice e licenza asset di tutti i 13 repo,
con testo MIT completo per SanAndreasUnity (`Copyright (c) 2015 James King`), Apache 2.0 per
CarGame/FuzzyTown, MIT per RaceRider3D/Zone Zero/Battle Island, GPLv3 per Third Person Controller
Android (evitato), e indicazione "nessuna licenza" per i rimanenti. Attribution mantenuta anche
per le sole idee riutilizzate. Nessun asset GTA importato.

## 14. Ottimizzazioni Android e vincoli rispettati

- Nessun `WheelCollider` aggiunto: la guida resta `Rigidbody` + velocità scalare già collaudata.
- Nessun cambio di dipendenze, di versione Unity, di kit edifici o di numero di veicoli
  (le due leve principali sul frame rate secondo il Mandato).
- Profili qualità usano i 6 livelli già definiti nel progetto; nessun livello/dipendenza nuova.
- Riflesso uova applicato a ogni nuova feature, con posizione geografica inviata al bridge.
- Bridge richiesto dal Mandato esteso in entrambi i sensi senza toccare i canali esistenti.

## 15. Conclusioni e prossimi passi

L'audit ha prodotto un quadro verificabile dei 13 repository e ha innestato in Huntix solo tre
meccaniche a basso rischio, senza copiare codice terzo e senza duplicare sistemi esistenti.
La compilazione statica e i test sono passati; **il giudizio finale sull'esperienza di gioco
resta sospeso fino al collaudo su dispositivo** e alla risoluzione dello stall di build del
terreno segnalato nel log. Prossimi passi consigliati: (1) installare l'APK su device e
verificare touch/derapata/qualità/uova; (2) raccogliere un nuovo log con i marker del builder
per diagnosticare lo stall; (3) solo dopo misure, valutare una reimplementazione locale
dell'IDM con semafori adattivi; (4) decidere se perseguire TLS sul server di gioco.
