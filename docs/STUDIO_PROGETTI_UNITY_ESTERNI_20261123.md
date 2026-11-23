# Studio di progetti Unity esterni e applicazione a Miacitta (23/11/2026)

## Obiettivo

Capire cosa possiamo imparare da progetti Unity maturi per sbloccare i problemi
di fluidità della città OSM di Huntix, senza copiare codice non licenziabile e
senza dichiarare miglioramenti non misurati.

I due repository sono stati clonati **fuori dal repo** (`/tmp/opencode/research/`),
quindi la working tree di Huntix non è stata toccata dall'analisi.

## I progetti esaminati

| Progetto | Licenza | Stato | Cosa possiamo fare |
|---|---|---|---|
| [SanAndreasUnity](https://github.com/in0finite/SanAndreasUnity) | **MIT** (confermata da `LICENSE` e GitHub API) | 2711★, 479 fork, dal 2018, Unity 2020.3.26f1 | Riutilizzare codice con attribuzione. Nessun asset GTA nel repo |
| [3ddriving](https://github.com/Tritone1/3ddriving) | **nessuna** (`license: null`) | 0★, creato il 2/10/2026, Unity 6 LTS / URP 17 | Solo **idee**: il codice C# è "all rights reserved". Gli asset sono CC0/CC BY, il codice no |

SanAndreasUnity è il progetto "serio" da cui imparare; 3ddriving è uno starter
recente e legalmente non copiabile. Entrambi sono comunque stati utili.

Nota metodologica: in SanAndreasUnity la cartella `Assets/UGameCoreUtilities` è
un submodule dichiarato ma non presente nel clone. Per i meccanismi interni
(`BackgroundJobRunner`, `AsyncLoader`, `ComponentPoolList`) è stato consultato
solo in lettura il sorgente upstream al commit pinnato `f2ed4500`.

## Pattern di SanAndreasUnity rilevanti per i nostri problemi

- **`Assets/Scripts/Behaviours/World/Cell.cs`**: budget esplicito di **10 ms/frame**
  con `SortedSet` ordinato per distanza e buffer preallocato da 64 voci.
- **`Assets/Scripts/Behaviours/FocusPointManager.cs`**: **isteresi di 3 s** prima
  di scaricare, per evitare il thrashing al bordo del raggio di carico.
- **`Assets/Scripts/Behaviours/NPCPedSpawner.cs`**: al massimo **1 pedone ogni
  0,33 s**, un destroy ogni 0,65 s, **2 frame di pausa** prima di inizializzare
  armi/AI, cap globali (`totalMaxNumSpawnedPeds = 40`).
- **`Assets/Scripts/Importing/LoadingThread.cs`**: caricamento su thread di
  background con jobe leti via `BackgroundJobRunner`.

Il nostro `ChunkManager.cs` **aveva già** l'ordinamento per distanza
(`_pending.Sort` rieseguito a ogni frame in `Update`) e l'isteresi spaziale
(`UnloadRadius = 4`, un anello oltre `LoadRadius = 3`). Non li abbiamo quindi
riscritti: il commit 2 si è concentrato su ciò che mancava davvero.

## Cosa NON abbiamo preso

- Da 3ddriving: il WheelCollider completo (le nostre auto sono cinetiche e
  legate a `GroundSnapper`/DEM), i presunti hook di pooling (`ComponentPool` non
  è istanziato in nessuna scena, `ITrafficAgent` non ha implementazioni) e il
  "save atomico" (è un `File.Copy`, non un rename). Il cambio qualità da UI non
  richiama nemmeno `Apply`: non importiamo i suoi bug.
- Da SanAndreasUnity: la priorità dichiarata dei job ma coda effettivamente FIFO,
  l'`ArchiveManager` che copia file in `byte[]`, e il parsing/costruzione dei
  ped sincroni. Il suo `Docs/TODO.md` segnala ancora FPS e GC come TODO aperti:
  non è una bacchetta magica.

Idee riutilizzabili da 3ddriving (solo come concetti): `destinationArmed` (una
missione si completa solo dopo che il giocatore si è allontanato dal bersaglio —
utile per le uova AR che spawnano sotto il player), chiavi di cooldown stabili,
`Sanitize()` difensivo del profilo, disciplina zero-alloc nell'input.

## Evidenze dal log del dispositivo (`docs/huntix_log_20261123_175436.txt`)

Dispositivo `samsung SM-A356B`, `quality=Medium`, `1080x2340`, mondo `FLAT`,
kit Kenney. A regime il gioco gira a **30 fps stabili**: il problema è durante
lo streaming, non il frame rate costante.

| Misura | Valore |
|---|---|
| Picchi durante il caricamento | 266–300 ms |
| 30 NPC costruiti | 355 ms (12:20:50.650 → 12:20:51.005) |
| Frame lungo | **20690,9 ms** (beacon 12:21:10.886) |
| `BUILD DONE` del chunk | `totalMs=21204ms` |
| Profilo del beacon | `prof=... chunk:0,21/1,8` |
| Stato governor dopo lo stall | `ema=2108ms`, tier 1 |

Correzioni di diagnosi rispetto alle ipotesi iniziali:

1. I 30 NPC **non** impiegano 20 secondi: li costruiscono in 355 ms. La
   rate-limitazione aiuta i picchi da 266–300 ms, ma non spiega il frame da
   20,7 s.
2. Il profiler `chunk` misura solo la pompa di `ChunkManager.Update`, **non** la
   coroutine di build: per questo riportava 0,21 ms mentre la build richiedeva
   21,2 s.
3. L'ultimo evento prima del blocco è `[NPCPopulator] C_0791_0982: 30/30` alle
   12:20:51.005; il primo dopo è l'health check alle 12:21:10.851, poi
   `poiVeicoli=2` alle 12:21:11.020. I 20 secondi stanno quindi fra la sezione
   **Pedoni** e la fine di **Concessionarie**, cioè in **Uova** (`SpawnEggsInChunk`)
   o nella prima parte di `VehiclePoiPlacer.Populate`. Nessuna delle due sezioni
   scriveva un log: era impossibile distinguerle.
4. Il log **non** prova il lampeggio delle strade: nessun `roads-only`,
   riallineamento DEM o toggle di LOD/materials. `stradeMesh=47/49` era dovuto
   a due chunk con "0 geometria nei bounds", stabili per tutta la sessione.
   `PerformanceGovernor` tocca solo i raggi di cull di veicoli/NPC, non le
   strade; `ChunkData.SetLod` non spegne `roadsGo`.

## Interventi applicati (verificati)

1. **`2f1eaf6` — `perf`**: popolamento NPC budgetato (2 NPC e 8 ms per frame,
   stessi seed/id/skin, guard sul chunk scaricato), pompa manuale della
   coroutine dentro il build, nuovi scope `spawn`/`roads`, contatori GC e
   ultimo evento OSM (`last=`) nei beacon con frame >1 s, scope `spawn` sulla
   costruzione dei veicoli.
2. **`32837fc` — `perf`**: cronometro per sezione in `ChunkBuilder` (log solo
   oltre 100 ms), clamp del campione e del tempo integrato in
   `PerformanceGovernor` (un frame da 20 s non deve essere letto come 20 s di
   carico sostenuto), flag `focus=`/`ts=` nel beacon dei frame lunghi.

Verifiche: `cscheck` completo OK, `backend/test_audit_regressions.py` 8/8,
build Android OK. APK firmato `16dd2b4a5a4d5df3bb900e35dffbea726ec84474cb55bed8d527f4d1975cd82f`.

## Riflesso delle uova (Mandato §4)

Non abbiamo aggiunto un nuovo sistema di uova: esiste già e è già collegato al
bridge bidirezionale.

- `EggSpawnManager.SpawnEggsInChunk` genera ~23 uova/km² con seed deterministico
  e **`SpawnPoiEggs`** piazza fino a 1 uovo **Rare/Legendary ancorato a POI reali**
  (chiese, scuole, bar, musei, ...) in ogni chunk, con relativo timestamp.
- `Game.OnEggCollected` → `SendEggToHuntix` → `UnityBridge.SendMessageToAndroid(
  "EggCapturedInCity", json)` passa l'uovo all'inventario Android; il percorso
  inverso evita che l'uovo raccolto rinascia dopo un rebuild del chunk.
- `BudgetEggWatcher`, `EggDex`, `ContainerEgg.MarkFound` e
  `SessionTelemetry.EggCollected` completano il riflesso (trofei, bestiario,
  indoor/outdoor).

Aggiungere un "uovo per chunk POI" separato avrebbe duplicato questa logica e
violato la regola di conservazione dei sistemi esistenti (Mandato §7).

## Prossimo passo (bloccato dal dispositivo)

Serve un **nuovo log** dalla stessa app con la strumentazione appena committa:
cercare `[Builder][Section]` per vedere la sezione colpevole, e nei beacon con
frame >1 s leggere `gc=`, `last=`, `focus=`, `ts=`. Con quei dati si potrà
budgetare la sezione esatta invece di continuare a ipotizzare.

Il vecchio log non contiene profilazione GPU, fisica, GC o compilazione shader,
quindi non permette di escludere una causa esterna al main thread (es. pausa del
ciclo di vita Android): è proprio quello che `focus=`/`ts=` chiariranno.

## Altri blocchi (invariati)

- Firebase Emulator/CLI assenti → test FS-01…FS-10 non eseguiti.
- Nessun TLS su `82.165.218.56:5100` → il cleartext Android non può essere
  rimosso senza rompere auth, tile e multiplayer.
- `./gradlew :app:testReleaseUnitTest --offline` bloccato da
  `Could not resolve junit:junit:4.13.2`.

## Licenze

Pattern di SanAndreasUnity riutilizzati come **idee e documentazione**, non come
codice copiato. SanAndreasUnity è distribuito con MIT License, Copyright (c)
2015 James King; se in futuro verrà importato codice sorgente, il testo della
licenza e l'attribuzione dovranno essere inclusi. Nessun file di 3ddriving è
stato importato.
