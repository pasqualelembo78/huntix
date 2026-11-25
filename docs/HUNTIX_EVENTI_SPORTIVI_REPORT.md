# Huntix — integrazione stadio, ippodromo, cavalli ed eventi sportivi/ambientali

Data: 25/11/2026 — branch `integrazione-open-source` — backup `pre-stadium-events` su `6b1a21fb`.

## 1. Obiettivo e vincoli

Integrare **realmente** (non solo come documento di analisi) uno stadio di calcio
visitabile con partita simulata, un ippodromo con corse e classifica, cavalli e
giocatori animati, audio ed eventi ambientali, mantenendo:

- universo Unity + Android unico e **ponte bidirezionale obbligatorio**;
- **Riflesso della caccia alle uova**: ogni nuova funzionalità ha uova
  posizionali, indizio testuale, coordinate e mirror Android;
- UI/messaggi in italiano;
- nessun cambio ai wire geo/graph, a `TileClient.CacheVersion` (resta **5**),
  a `ChunkManager.LoadRadius` (resta **3**), alle regole DEM/fisica e ai due
  `LiveEventManager` già presenti;
- nessun sistema economico parallelo e **nessuna scommessa**;
- licenze verificate su fonti autorevoli, con attribuzione quando richiesta
  (Mandato §12).

Il sistema è **additivo e disattivato di default** (`huntix_venue_events_enabled =
0`): la voce del menu “Eventi sportivi: Off/Attivi” lo attiva senza alterare
comportamento e costi del mondo esistente.

## 2. Asset cercati, acquisiti e scartati

Tutti i download sono stati fatti da URL pubblici il 25/11/2026 e verificati
tramite API o pagina ufficiale. Ogni GLB è stato convertito in FBX con Assimp
5.2 (solo come strumento locale: nessun codice Assimp copiato).

### Acquisiti

| Pacchetto | Fonte | Licenza | File importati | Note |
|---|---|---|---|---|
| Football Soccer Stadium and Club Grounds | https://3dassets.dev/packs/football-soccer-club-grounds (API: `/api/v1/packs/football-soccer-club-grounds`) | CC0 1.0 | 33 FBX in `Assets/Resources/EventVenues/stadium/` | campo 105×68, porte, tribune, panchine, spogliatoi, tabellone, fari, tornelli |
| Racecourse and Parade Ring | https://3dassets.dev/packs/racecourse-and-parade-ring | CC0 1.0 | 46 FBX in `.../racecourse/` | rettilineo/curva, ringhiere, ostacoli, gabbie, parade ring, pesa, tribune, tabellone |
| Horse Stables and Equestrian Yard | https://3dassets.dev/packs/equestrian-yard-and-stables | CC0 1.0 | 27 FBX in `.../stable/` | box, sellerie, recinti, attrezzi, cavalli statici in varie pose |
| Low Poly Character Figures | https://3dassets.dev/packs/low-poly-character-figures | CC0 1.0 | 5 FBX in `.../people/` | figure per pubblico/spectatori; non usate come giocatori animati |
| **Race Horse** di vinish.dev | https://vinish.dev/free-3d-models/horses — GLB `https://vinish-models.pages.dev/horses/race-horse.glb` | **CC BY 4.0** | 1 FBX in `.../horses/RaceHorseAnimated.fbx` | 6 clip (Idle, Walk, Trot, Gallop, Graze, Rear) |

Attribuzione obbligatoria, riportata in `unity-project/Assets/ThirdPartyLicenses.md`,
`THIRD_PARTY_NOTICES.md`, nei crediti del gioco e in questo report:

> "Race Horse" by vinish.dev (https://vinish.dev/free-3d-models/), CC BY 4.0

Modifica dichiarata: conversione GLB→FBX e **decimazione della mesh** da
119.288 a circa 17.000 triangoli, mantenendo skin weights, normali, materiali e
le sei clip. Il GLB originale contiene il campo `asset.copyright` con la stessa
riga di credito.

Il set totale è di **111 FBX (~27 MB)**, ognuno con `.meta` generato da Unity
durante la build. Le texture del cavallo (14 immagini, 256/512 px) restano
originali; non sono state scaricate texture aggiuntive.

### Scartati (nessun file importato)

- **Animatics Asset Store**, “3D Football Stadium – Low Poly”
  (https://www.animaticsassetstore.com/product/football-stadium/): download
  solo tramite carrello/account WooCommerce, licenza non verificabile in forma
  autorevole.
- **Kenney API** (https://kenney.nl/api/assets): risposta 403, nessun asset
  acquisito.
- **“Town plaza crowd”** di 3dassets.dev (10 MB): scaricato solo per
  valutazione, **non importato**: si usano poche figure istanziate invece di un
  gruppo unico pesante.
- Asset dei pack 3dassets.dev relativi a **scommesse/bookmaker/tote**, armi o
  temi non attinenti: esclusi per coerenza con il Mandato e con l'assenza di
  economia parallela.

Assimp 5.2 è stato usato anche per provare glTFast 6.6.0 (Unity ≥2020.3) come
alternativa ai FBX: la via FBX è stata scelta per non aggiungere dipendenze al
manifest e per mantenere il caricamento via `Resources.Load`.

## 3. Script, hook e struttura

Nuova cartella `unity-project/Assets/City/Scripts/Events/`:

- `VenueEventState.cs`: enum `VenuePhase` (Inattivo, Programmato, InCorso,
  Intervallo, Terminato) e `VenueKind`; salvataggio `PlayerPrefs`, pubblicazione
  su `EventBus`, invio `VenueEventChanged` al bridge, export/import nello
  snapshot con merge per timestamp.
- `VenueDirector.cs`: singleton `Ensure()`, registro leggero per chunk
  (`OnChunkBuilt`), selezione deterministica
  (`h = x*73856093 ^ y*19349663`, `pick = h % 19`; 0 = stadio, 5 = ippodromo),
  costruzione lazy in coroutine entro 115 m, disattivazione oltre 220 m,
  un solo impianto attivo (`MAX_ACTIVE = 1`), prompt contestuale e uova.
- `VenueSimulation.cs`: ciclo partita/corsa, classifica, meteo, audio e
  movimento deterministico degli attori; aggiornamento entro 210 m, animazione
  entro 70 m.
- `VenueAudio.cs`: clip generate a runtime (folla, zoccoli, fischio), sorgenti
  3D con distanza massima, un solo `AudioSource` per evento.
- `VenueMaterials.cs`: conversione una volta sola dei materiali importati a
  `Universal Render Pipeline/Lit`, condivisi per renderer (SRP Batcher).

Innesti (nessun sistema esistente rimosso):

- `OSM/ChunkBuilder.cs`: nuova sezione “Eventi sportivi” dopo i PROP e prima di
  `chunk.built = true`; solo registro, nessuna geometria pesante nel build.
- `Game.cs`: `VenueDirector.Ensure()` accanto a `SimulationManager.Ensure()` e
  `VenueDirector.TryInteractNearby()` come **ultima** priorità dopo porta,
  veicolo, POI, missione e ingresso edificio.
- `UI/HamburgerMenu.cs`: voce “Eventi sportivi” con toggle e riepilogo; pannello
  da 1620 a 1670 px; test preesistenti aggiornati per accettare la nuova
  altezza.
- `Sync/CityWorldSync.cs`: campo `venueEventState` nello snapshot con import
  non distruttivo per timestamp.
- `City/Scripts/Events/` e `Resources/EventVenues/`: cartelle e `.meta`
  generate dalla build Unity.

Nessuna scena modificata, nessun nuovo `DontDestroyOnLoad`, nessun nuovo campo
nel wire, nessun cambio di `CacheVersion`.

### Animazione reale

- **Giocatori**: si istanzia `Resources/PlayerHero` (prefab già rigged con
  Animator e controller Mixamo `PlayerLocomotion`, senza CharacterController,
  collider o script di gameplay). `VenueSimulation` muove l'Animator con
  `SetFloat("Speed", ...)`; fallback alle figure CC0 se il prefab manca.
- **Cavalli**: le sei clip dell'FBX CC BY sono lette con
  `Resources.LoadAll<AnimationClip>("EventVenues/horses")`, copiate a runtime,
  marcate `legacy` e suonate con un componente `Animation`; `CrossFade`
  avviene solo al cambio di clip (Gallop in corsa, Idle a riposo). Nessun
  AnimatorController generato a runtime, nessuna fisica.

## 4. Simulazioni ed eventi

- **Stadio**: Programmato → InCorso (primo tempo, 0-0) → Intervallo →
  InCorso (secondo tempo) → Terminato → nuovo Programmato. Gol deterministici,
  pallone che segue due attori, pubblico seduto, fischio e folla, tabellone.
- **Ippodromo**: Programmato → InCorso → Terminato con classifica dei primi 3
  (`Cavallo 1…4`), quattro cavalli animati in pista, gabbie di partenza,
  winning post, parade ring, box 3, sellerie, tribune e big screen.
- **Eventi ambientali**: sereno / pioggia leggera / vento cambiano
  `SpeedFactor` (0,82 / 1,08 / 1,00) e pubblicano
  `EnvironmentalEventAppliedEvent` + evento `EnvironmentalEventApplied` sul
  bridge.
- Stato e dettagli italiani sono visibili nel toast e nel mirror Android.

### Riflesso uova

| Chiave | Luogo | Posizione |
|---|---|---|
| `stadio-spogliatoio` | `stadio→spogliatoio` | vicino armadio/panchina/doccia |
| `ippodromo-box3` | `ippodromo→box 3` | box 3 delle scuderie |
| `ippodromo-parade` | `ippodromo→parade ring` | parade ring |

Le uova usano `Game.GrantCompanionEgg` → `SpawnFeatureEggAt` →
`FeatureEggHidden` → `EggJournal.Record` → `EggCapturedInCity` con lat/lng e
timestamp; la concessione è una sola per impianto (`huntix_venue_eggs_<id>`).
Nessun premio economico: sono uova-indizio come le altre feature del Mandato.

### Ponte Android↔Unity

- `UnityIpc.kt`: nuovi eventi in whitelist `VenueEventChanged`,
  `EnvironmentalEventApplied`.
- `Bridge.kt`: router verso `CityStateSync`.
- `CityStateSync.kt`: chiavi `city_venue_event_v1` / `city_env_event_v1`,
  `onVenueEvent`, `onEnvironmentalEvent`, `mergeVenueSnapshot` nello snapshot,
  campi `venueEvent` e `venueEventSnapshot` in `checkSync`.
- Il merge applica solo uno stato con `tsMt` più recente: restore e sync non
  possono riportare indietro la simulazione.

## 5. Ottimizzazioni

- Feature **OFF di default**: costo zero finché non viene attivata dal menu.
- Un solo impianto costruito alla volta; costruzione in coroutine con yield;
  ancoraggi dei chunk scaricati rimossi.
- Culling: costruzione <115 m, root attiva <220 m, simulazione <210 m,
  movimento/animazione <130/70 m; tick del direttore ogni 0,5 s.
- Nessun `CharacterController`, `Rigidbody` o collider nuovo; attori mossi con
  trasformazioni deterministiche, niente `Physics.Raycast` per loro.
- Materiali URP condivisi, nessuna luce realtime aggiunta, nessun post-process.
- Un solo `AudioSource` di folla e uno FX per impianto, clip generate una volta.
- Mesh del cavallo decimata ~7×; sottoinsiemi dei pack scelti per limitare
  peso e draw call; nessun asset di scommesse.
- Nessun accesso rete a runtime: tutto è in `Resources`.

## 6. Test eseguiti

| Verifica | Comando | Esito |
|---|---|---|
| Test statici della nuova integrazione | `python3 -m unittest discover -s backend -p 'test_unity_sport_events.py' -v` | 19 test OK |
| Tutti i test Unity statici | `python3 -m unittest discover -s backend -p 'test_unity_*.py'` | **202 test OK** |
| Compilazione C# con stub | `cd backend/preprocessing/cscheck && ./check.sh` | `CSCHECK OK` (196 warning preesistenti, 0 errori) |
| Build Android release | `LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 SKIP_MAP_UPDATE=1 SKIP_INSTALL=1 FORCE_UNITY_AAR=1 ./build_release.sh --auto` | `BUILD SUCCESSFUL`, export Unity + Il2Cpp + APK firmato |
| Firma APK | integrata nello script (`apksigner verify`) | OK |
| `.meta` | conteggio su `Events/` e `EventVenues/` | 5 script + 111 FBX con `.meta` |

Artefatto finale:

- `app/build/outputs/apk/release/app-release-signed.apk`
- dimensione **201.473.302 byte**
- SHA-256 `0c269c0afb19a9fa29806fba8a727a54e0e4b7d132b419937bd180dfd6890dc6`

Gli artefatti collaterali noti della build (`link.xml.meta`,
`PlayerLocomotion.controller`, `PlayerHero.prefab`) sono stati ripristinati da
Git dopo la build, come nella fase precedente. La build ha inoltre riavviato il
backend HUNTIX sulla porta 5100 (comportamento normale dello script).

### Test NON eseguiti (dichiarazione esplicita)

In questa macchina **non c'è un device o emulatore Android**: non sono stati
verificati aspetto visivo di stadio/ippodromo, resa dei cavalli animati,
varchi/uova in gioco, audio percepito, FPS/RAM reali e restore su device. Lo
stato compilato e firmato è pronto per quella verifica, che resta un passo
obbligatorio prima di considerare chiusa la QA visiva.

## 7. Problemi riscontrati e soluzioni

1. **Prima passata Unity con 8 errori `CS0234` su `City.Events`**: i nuovi
   script non avevano ancora `.meta` quando Unity ha compilato la prima volta.
   La build fa già una passata preliminare di import: dopo la generazione dei
   `.meta` la compilazione finale e l'export Il2Cpp sono riusciti con **0 errori
   C#**. La seconda build (con animazione reale) non ha più avuto errori.
2. **`cscheck` inizialmente in errore** su API assenti negli stub
   (`AudioSource.minDistance`, `rolloffMode`, `isPlaying`,
   `PlayOneShot(clip)`, `Material.mainTexture`): gli stub del check sono stati
   allineati alle API reali di Unity 2022.3, senza toccare il codice di gioco.
3. **Due test statici inizialmente falliti**: uno confrontava una frase
   diversa da quella della licenza, l'altro trovava “CharacterController” in un
   commento XML; il test ora controlla il codice eseguibile (commenti rimossi).
4. **Cavalli inizialmente senza animazione**: il solo `Animator` senza
   controller lasciava il modello in bind pose; è stato aggiunto il playback
   legacy delle clip FBX e l'uso del prefab eroe per i giocatori.
5. **Causa del primo NaN del 25/11/2026 non dimostrata**: candidato
   `CharacterController` + Rigidbody cinematico del furgone
   `SHOW_405220197_1_Furgone`. Il nuovo sistema non introduce fisica e non
   tocca lo snap a terra, quindi non aggrava quel problema; resta da indagare
   separatamente con telemetria su device.

## 8. Prossimi sviluppi

1. QA su device reale: ingresso agli impianti, visibilità delle uova, audio,
   FPS/RAM prima/dopo con confronto `prof=...` di `SessionTelemetry`.
2. Verificare in game che `Resources.LoadAll<AnimationClip>` restituisca le sei
   clip su Android Il2Cpp; se necessario, generare in editor un
   `RuntimeAnimatorController` dedicato e sostituire il playback legacy.
3. Ampliare le varianti (più squadre, più corse, fantini se si trova un modello
   CC0/CC BY verificato) mantenendo un solo impianto attivo e il default OFF.
4. Aggiungere un pannello HUD dedicato al punteggio/classifica e all'indizio
   delle uova, sempre senza valute o scommesse.
5. Misurare il costo dei 10 eroi animati in stadio: se supera il budget,
   passare a 6-8 attori o a LOD più aggressivi, con dati alla mano.
