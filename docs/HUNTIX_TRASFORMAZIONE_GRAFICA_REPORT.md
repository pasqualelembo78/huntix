# Trasformazione grafica di Huntix — report finale (2026-11-24)

Obiettivo: migliorare radicalmente e **realmente** la qualità visiva di Huntix
con asset CC0 importati e integrati, senza migrazione di engine e senza
toccare il bridge Android. Tutto il nuovo è **opt-in** dal menu hamburger;
il renderer storico resta il default.

---

## 1. Asset individuati

- **Poly Haven** (API pubblica, CC0): 6 materiali PBR con mappe 1K JPG —
  `asphalt_01`, `asphalt_03`, `brick_pavement_02`, `brushed_concrete`,
  `brick_wall_001`, `sparse_grass` — più 18 candidati aggiuntivi valutati.
- **Kenney** (CC0): 10 pacchetti già presenti in `kit/` (characters, roads,
  nature, skyboxes, car kit, city kit commercial/suburban/industrial,
  furniture, cube-pets).
- **Quaternius** (CC0 solo per il pack Downtown Free, verificato dal
  `License_Standard.txt`): mega-kit città e Universal Animation Library.
- **AmbientCG / OpenGameArt / Sketchfab / Mixamo / Unity Asset Store**:
  valutati, non importati (motivazioni nella shortlist, §21).
- **character1.unitypackage**: "Stylized Player Character (Free)", licenza
  **non dichiarata** → escluso.

## 2. Asset scaricati

- 13 texture PBR da Poly Haven, **10.723.909 byte**, MD5 verificato contro
  `https://api.polyhaven.com/files/<id>` per ogni file.
- 11 zip CC0 già presenti in `kit/` estratti in `assets/` (7.215 file,
  368 MB, SHA-256 dei sorgenti in `assets/manifest.json`).
- Nessun download da fonti con licenza non verificabile.

## 3. Asset importati

- `unity-project/Assets/Resources/Cinematic/Textures/`: 13 JPG + 13 `.meta`
  (normal map come `NormalMap`, sRGB 0, max 1024, aniso 4, repeat, mipmap).
- `unity-project/Assets/Resources/Cinematic/RawCc0/`: 6 FBX Kenney (coni,
  barriere, lampioni, cartelli, attraversamento) + colormap + `LICENSE.txt`,
  con `.meta` e GUID stabili.
- Script/registry: `CinematicAssetRegistry.cs`, `CinematicAssetsSetup.cs`,
  `CinematicRawAssetsSetup.cs`, hook in `HuntixBuild.cs` e `GameManager.cs`.

## 4. Asset effettivamente utilizzati

| Asset | Sistema | Switch |
|---|---|---|
| Asfalto `asphalt_01` (diffuse/normal/AO/rough) | `TerrainChunk.RoadMaterial()` → materiale strada condiviso; la roughness è legata allo slot MetallicGloss con metallic 0 | `asphalt-pbr` |
| Asfalto usurato `asphalt_03` (diffuse/normal) | `TerrainChunk.WornRoadMaterial()`, scelto per ~1/4 dei chunk da `ChunkManager.RoadMaterialFor()` | `asphalt-pbr` |
| Pavimentazione `brick_pavement_02` | `TerrainChunk.SidewalkMaterial()` | `pavement-pbr` |
| Erba `sparse_grass` | `TerrainChunk.ParkMaterial()` → aree verdi dei parchi | `pavement-pbr` |
| Diffuse + normale mattone `brick_wall_001` | `BuildingPlacer.EnsureURPMaterials()` solo su facciate senza texture | `facade-pbr` |
| Diffuse cemento `brushed_concrete` | materiale condiviso dei pali lampione | `street-lamps` |
| Normale cemento + smoothness | `VehicleMaterialFix` / `VehicleSpawnManager` | `vehicle-detail` |
| Lampioni procedurali + PointLight (1 ogni 4) | `PropSpawner.SpawnStreetProps` → `CinematicGraphics.PlaceLamps` | `street-lamps` |
| Nebbia/ambiente notturno | `DayNightManager.ApplyLighting` → `CinematicGraphics.SetAtmosphere` | `night-atmosphere` |
| 6 FBX Kenney grezzi | `PropSpawner` → `CinematicGraphics.PlaceRawProps` (collider disattivati) | `raw-cc0-assets` |

## 5. Link originali

Poly Haven: https://polyhaven.com/ e https://api.polyhaven.com/files/<id>.
Kenney: https://kenney.nl/assets (singoli pacchetti in `assets/INVENTORY.md`).
Quaternius: https://quaternius.com/packs/downtowncitymegakit.html.
Elenco completo con URL per pacchetto: `assets/INVENTORY.md`,
`THIRD_PARTY_NOTICES.md` e §21.

## 6. Licenze

CC0 1.0 Universal per tutti gli asset importati
(https://creativecommons.org/publicdomain/zero/1.0/). Testo e attribuzioni in
`THIRD_PARTY_NOTICES.md`, sezione "Trasformazione grafica cinematografica".
Ogni cartella in `assets/` conserva il `License.txt` originale.

## 7. Directory di destinazione

- Grezzi: `/root/giochi/huntix/assets/` (inventario + `restore.sh` idempotente;
  le cartelle estratte sono derivate dai pacchetti tracciati in `kit/`).
- Unity: `unity-project/Assets/Resources/Cinematic/{Textures,RawCc0}/`.
- Registry: `unity-project/Assets/CinematicAssetRegistry.asset` (popolato in build).

## 8. Modifiche apportate

Nuovo gruppo di preferenze `huntix_gfx_*`, evento Android
`CinematicFeatureChanged`, chiave JSON `cinematicFeatures`, bridge
`CinematicFeatureBridge`, menu master + sottopannello, 7 handler opt-in,
uova-indizio posizionali tramite il solo canale `FeatureEggHidden`.

## 9. Sistemi grafici implementati

Texture PBR su surface già esistenti (UVpresenti, nessun collider toccato),
facciate selettive, materiali veicolo, lampioni con emissivo e luce punto
budgetata, atmosfera notturna, props CC0 reali distribuiti proceduralmente.

## 10. Scene modificate

Nessuna scena `.unity` modificata a mano: i riferimenti ai registry sono
impostati a build-time da `CinematicAssetsSetup` / `CinematicRawAssetsSetup`
in tutte le scene di `EditorBuildSettings`, lo stesso pattern di
`KenneyAssetsSetup` (evita `Resources.Load` non risolto su Android).

## 11. Materiali creati

Materiali condivisi esistenti arricchiti a runtime (`_BaseMap`, `_MainTex`,
`_BumpMap`, `_OcclusionMap`, `_Smoothness`) più due materiali condivisi per
palo lampione e vetro emissivo. Nessun materiale per istanza.

## 12. Shader utilizzati

Solo shader già presenti e compatibili Android:
`Universal Render Pipeline/Lit`, fallback `Standard`, `Universal Render
Pipeline/Unlit`, fallback `Unlit/Color`. Nessun nuovo shader scritto.

## 13. Modifiche alle impostazioni

Nessuna modifica a `ProjectSettings` o `GraphicsSettings`: pipeline e
qualità restano quelle di Huntix. `TileClient.CacheVersion` resta 5: il
formato geo/graph non è cambiato (restyling solo locale).

## 14. Ottimizzazioni Android

Texture 1K con mipmap/aniso 4, normal map lineari, materiali condivisi, una
luce ogni 4 lampioni (`shadows=None`), props istanziati senza collider,
distribuzione con stride (non un oggetto per nodo stradale), toggle per
disattivare ogni singolo costo. I kit ad alto costo (Quaternius, 29 MB di
texture) restano dietro lo switch preesistente `Stile edifici`.

## 15. Errori riscontrati

- `urllib` riceveva HTTP 403 dalle API Poly Haven senza `User-Agent`: risolto
  con header esplicito; MD5 verificato su tutti i file.
- `cscheck` non conosce i nuovi registry (fuori dallo scope `Assets/City`):
  aggiunti stub dedicati in `HuntixStubs.cs`/`UnityStubs.cs`, senza toccare
  gli stub del progetto Unity.
- Due test statici dell'audit si aspettavano l'altezza vecchia del pannello
  (1300): aggiornati a 1470.
- `character1.unitypackage` ha licenza non verificabile: non importato.
- Primo tentativo di build: `error CS0103: The name 'RefreshFacades' does not
  exist in the current context` — Unity aveva compilato una versione
  intermedia di `CinematicGraphics.cs` mentre il metodo veniva aggiunto.
  Risolto completando il metodo e rilanciando la build (vedi §19).
- Errori a runtime segnalati dall'utente all'attivazione della modalità:
  `Material 'Sprites/Default' ... doesn't have a texture property
  '_OcclusionMap'` e `'_MetallicGlossMap'`. Causa: `Save()` leggeva le texture
  senza verificare `HasProperty`, e `RefreshFacades()` considerava anche
  materiali Sprite/UI. Risolto con getter protetti da `HasProperty` e filtro
  esplicito degli shader non 3D (`_BumpMap` richiesto).
- Menu hamburger senza chiusura esplicita: aggiunta voce "Chiudi menu",
  pulsante CHIUDI più grande nei sub-panel e `CloseAll()` che chiude tutti i
  sub-panel registrati, anche futuri.
- Nuovo log `docs/huntix_log_20261125_073357.txt`: gli errori
  `_OcclusionMap`/`_MetallicGlossMap` **non compaiono più** e tutti e 7 gli
  switch cinematografici risultano `on=true`. Il successivo `player=(NaN,NaN)`
  è stato analizzato separatamente (§22): non è causato dalla grafica CC0.

## 16. Problemi risolti

Evento Android e pref separati dal gruppo audit, per non alterare i 17 token
già verificati; cartella `assets/` inizialmente vuota popolata con sorgenti
verificabili; hiccup di importazione Unity evitato con `.meta` e GUID espliciti.

## 17. Problemi ancora presenti

- Nessun device/emulatore disponibile: **verifica visiva a runtime non
  effettuata**. L'aspetto cinematografico va collaudato su telefono.
- L'impatto FPS del set completo non è misurato su hardware reale: usare
  `PerfProbe`/`SessionTelemetry` e il menu per accendere un effetto alla volta.
- I 6 FBX Kenney importati sono low-poly: il risultato è "coherentemente
  stilizzato", non fotorealistico come Poly Haven; per pacchetti più dettagliati
  servirebbe un passo di LOD dedicato.

## 18. Verifiche effettuate

- `cscheck`: **OK**.
- `backend/test_unity_audit_features.py`: **99/99 OK**.
- `backend/test_city_recipe.py`: **3/3 OK**.
- `backend/test_unity_cinematic_features.py`: **9/9 OK**.
- `backend/test_unity_nan_guards.py`: **8/8 OK**.
- Kotlin release: **compilazione OK**.
- MD5 delle 13 texture e SHA-256 dei 11 pacchetti: verificati.
- `assets/restore.sh` eseguito da una directory diversa: rigenerazione
  idempotente dei 7.215 file confermata.
- Build Android: vedi §19.

## 19. Risultato della build

### Build della grafica CC0 (prima build)

`LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 BUILD_UNITY_AAR=1 FORCE_UNITY_AAR=1
SKIP_MAP_UPDATE=1 SKIP_INSTALL=1 ./build_release.sh --auto`

**Esito: riuscita.** `BUILD SUCCESSFUL` sia per l'AAR Unity (4m 30s) sia per
l'APK (1m 40s); `:app:compileReleaseKotlin` e `:huntix:compileReleaseKotlin`
OK, firma e zipalign verificati. Log: `/tmp/opencode/build_cinematic_fix.log`.

APK firmato: `app/build/outputs/apk/release/app-release-signed.apk`

- Dimensione: 199.459.830 byte (191 MB)
- SHA-256: `e4dc235846bf6f40e485d0597c10d7d7693023d6bf5f2bc7a2bd9036eca9aa3a`
  (superato dalla build finale qui sotto)

L'hash comprendeva anche le correzioni runtime segnalate dall'utente (§15):
getter `HasProperty`-safe e chiusura esplicita del menu.

### Build finale con i guard anti-NaN (§22)

`LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 SKIP_MAP_UPDATE=1 SKIP_INSTALL=1
FORCE_UNITY_AAR=1 ./build_release.sh --auto`

**Esito: riuscita.** `BUILD SUCCESSFUL` in 1m 56s; AAR Unity ricompilato,
Kotlin Android e firma APK OK. Log: `/tmp/opencode/build_nan_guard.log`.

APK firmato: `app/build/outputs/apk/release/app-release-signed.apk`

- Dimensione: 199.463.926 byte (191 MB)
- SHA-256: `eed382149db28482173afbf59dd5bd80dfbce22d42a3f5a00689816a82977ded`
  (superato dalla build finale qui sotto)

`TileClient.CacheVersion` resta 5: il formato geo/graph non è cambiato e i
guard non alterano interpolation, collider o fisica dei veicoli.

### Build finale con i negozi walk-in (§23)

`LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 SKIP_MAP_UPDATE=1 SKIP_INSTALL=1
FORCE_UNITY_AAR=1 ./build_release.sh --auto`

**Esito: riuscita.** `BUILD SUCCESSFUL` per l'AAR Unity (4m 28s) e per l'APK
(1m 47s); compilazione Kotlin, zipalign e verifica firma OK.
Log: `/tmp/opencode/build_shops_final.log`.

APK firmato: `app/build/outputs/apk/release/app-release-signed.apk`

- Dimensione: 199.488.502 byte (191 MB)
- SHA-256: `717d1209d170bd4e179b01aec7551fa7b6930c60f0f7a19174b02c4e060fdd6b`
  (superato dalla build finale qui sotto)

`TileClient.CacheVersion` resta 5: negozi, contatori e uova non toccano il
formato geo/graph; mappe, POI e grafi stradali non vanno rigenerati.

### Build finale con il fix dei chunk scaricati durante la guida (§24)

`LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 SKIP_MAP_UPDATE=1 SKIP_INSTALL=1
FORCE_UNITY_AAR=1 ./build_release.sh --auto`

**Esito: riuscita.** `BUILD SUCCESSFUL` per l'AAR Unity (4m 31s) e per l'APK
(1m 40s); Kotlin, zipalign e verifica firma OK. Log:
`/tmp/opencode/build_chunk_guards.log`.

APK firmato: `app/build/outputs/apk/release/app-release-signed.apk`

- Dimensione: **199.488.502 byte** (191 MB)
- SHA-256: `dfe998f4f6ef0eab3f5663a37c72f98d99b859cf87a9fb5b95722b089d85b526`

`TileClient.CacheVersion` resta 5: le guardie non cambiano il formato geo/graph,
quindi mappe, POI e grafi stradali non vanno rigenerati. Artefatti generati
dalla build (`link.xml.meta`, `PlayerLocomotion.controller`, `PlayerHero.prefab`)
ripristinati con `git checkout --`.

Effetti collaterali noti della build Unity (`link.xml.meta`,
`PlayerLocomotion.controller`, `PlayerHero.prefab`) ripristinati con
`git checkout --` dopo la compilazione; le uniche modifiche alle scene sono i
riferimenti `cinematicRegistry` impostati a build-time.

## 20. Backup creato

- Tag git `pre-cinematic-backup` su `46a67938`.
- Copia dei 12 file critici in `/tmp/opencode/backup_pre_cinematic/` con
  `SHA256SUMS.txt` verificato (`sha256sum -c` → OK).
- Ogni fase è stata committata separatamente; `main` non toccato, nulla pushato.

## 21. Shortlist — 40 pacchetti/fonti valutati

Verificati via API/HTTP il 2026-11-24. "Importato" = file realmente nel
progetto; "Già integrato" = presente da commit precedenti; "Scartato" =
licenza non verificabile, piattaforma a pagamento o non innestabile senza
riscrittura.

| # | Fonte / pacchetto | Licenza | Esito |
|---:|---|---|---|
| 1 | Kenney Animated Characters 1 | CC0 | Già integrato (characters.zip) |
| 2 | Kenney City Kit Roads 2.0 | CC0 | **Importato** (6 FBX) |
| 3 | Kenney City Kit Commercial | CC0 | Già integrato |
| 4 | Kenney City Kit Suburban | CC0 | Già integrato |
| 5 | Kenney City Kit Industrial | CC0 | Già integrato |
| 6 | Kenney Car Kit | CC0 | Già integrato |
| 7 | Kenney Toy Car Kit | CC0 | Scartato (duplicato del car kit) |
| 8 | Kenney Nature Kit | CC0 | Già integrato |
| 9 | Kenney Skyboxes | CC0 | Già integrato |
| 10 | Kenney Furniture Kit | CC0 | Già integrato |
| 11 | Kenney Cube Pets | CC0 | Già integrato |
| 12 | Quaternius Downtown City MegaKit Free | CC0 | **Importato** come grezzo + già integrato |
| 13 | Quaternius Universal Animation Library | CC0 | Già integrato |
| 14 | Quaternius Simple Nature | CC0 | Scartato (duplicato nature kit) |
| 15 | Quaternius Characters Fantasy Races | QAL | Scartato (non CC0, non urbano) |
| 16 | Poly Haven `asphalt_01` | CC0 | **Importato** |
| 17 | Poly Haven `asphalt_03` | CC0 | **Importato** |
| 18 | Poly Haven `brick_pavement_02` | CC0 | **Importato** |
| 19 | Poly Haven `brushed_concrete` | CC0 | **Importato** |
| 20 | Poly Haven `brick_wall_001` | CC0 | **Importato** |
| 21 | Poly Haven `sparse_grass` | CC0 | **Importato** |
| 22 | Poly Haven `asphalt_02` | CC0 | Scartato (variante di #16) |
| 23 | Poly Haven `asphalt_04` | CC0 | Scartato (variante) |
| 24 | Poly Haven `asphalt_05` | CC0 | Scartato (variante) |
| 25 | Poly Haven `asphalt_06` | CC0 | Scartato (variante) |
| 26 | Poly Haven `asphalt_07` | CC0 | Scartato (variante) |
| 27 | Poly Haven `asphalt_floor` | CC0 | Scartato (parcheggio non usato) |
| 28 | Poly Haven `clean_asphalt` | CC0 | Scartato (troppo pulito) |
| 29 | Poly Haven `cobblestone_01` | CC0 | Scartato (estetica non coerente) |
| 30 | Poly Haven `cobblestone_02` | CC0 | Scartato (estetica non coerente) |
| 31 | Poly Haven `brick_pavement_03` | CC0 | Scartato (duplicato #18) |
| 32 | Poly Haven `brushed_concrete_2` | CC0 | Scartato (duplicato #19) |
| 33 | Poly Haven `grass_ground` | CC0 | Scartato (duplicato #21) |
| 34 | Poly Haven `leafy_grass` | CC0 | Scartato (non urbano) |
| 35 | Poly Haven `withered_grass` | CC0 | Scartato (non urbano) |
| 36 | Poly Haven `brick_wall_005` | CC0 | Scartato (duplicato #20) |
| 37 | Poly Haven HDRI | CC0 | Scartato (skybox Kenney già usata, costo GPU) |
| 38 | ambientCG (2046 materiali via API) | CC0 | Valutato, non importato (mappe 1K già coperte) |
| 39 | OpenGameArt / Sketchfab | Per-asset | Scartato (licenza e qualità non uniformi) |
| 40 | Mixamo / Unity Asset Store / character1.unitypackage | Account/Store / non verificata | Scartato (accesso o licenza non verificabile) |

Fonte comune licenze: https://creativecommons.org/publicdomain/zero/1.0/.
I pacchetti GTA/Mafia e i repo che richiedono il gioco originale restano
esclusi per vincolo di proprietà intellettuale.

## 22. Log 2026-11-25: NaN del player e guard anti-propagazione

Sequenza ricostruita da `docs/huntix_log_20261125_073357.txt`:

- `07:32:54-55`: i 7 effetti cinematografici vengono attivati (`on=true`);
  il giocatore è ancora fermo e valido.
- `07:33:09.645`: teleport valido a `(196,2,225,5) → (2687,9,-1719,9)`,
  `y=2,40`, con snap su `SHOW_405220197_1_Furgone` (auto da salone).
- `07:33:21.506`: ultima telemetria valida, `player=(2688,-1720)`, 13 fps,
  carico chunk in corso (`C_0825_0871`).
- `07:33:22.347`: primo NaN osservabile, `CityGrid` riceve
  `lat=NaN lon=NaN`; quindi `WorldOrigin.TryRebase(NaN)`, chunk root e mesh
  `TerrenoChunk` con bounds NaN, HUD/GPS `player=(NaN,NaN)`.

Il NaN era **già presente nel transform del player** quando `ChunkManager.Tick()`
lo ha letto. La causa scatenante resta non dimostrata (finestra non strumentata
di ~840 ms; candidato più plausibile l'accoppiamento fra `CharacterController`
e il Rigidbody cinematico del furgone durante `Physics.SyncTransforms()`), ma
la catena di propagazione è dimostrata: `NaN < RebaseDistanceM` è falso, quindi
il vecchio `TryRebase` accettava il NaN e lo scriveva in origine, chunk, mesh e
GPS. **`CityConfig.WorldHeights = Flat` e la modalità cinematografica non sono
la sorgente**: il DEM ritorna sempre 0 e i 7 switch erano già attivi da 27 s
senza effetti sulla posizione.

Barriere aggiunte (nessuna modifica ai valori finiti, nessun cambio di
interpolazione/collider dei veicoli):

- `WorldOrigin.cs`: `IsFinite(float/double/Vector3)`, guard su `Init()` e
  `TryRebase()` (blocco prima del confronto di distanza e prima di invocare
  `OnRebased`).
- `ChunkManager.cs`: ultima posizione valida del target, `RescueInvalidTarget()`
  in `Tick()`/`Update()`, `OnRebased()` ignora delta non finiti e riabilita
  sempre il `CharacterController` con `try/finally`.
- `GroundSnapper.cs`/`CityChunkedWorld.cs`: sonde e snap rifiutano origini, hit
  e quote non finite; `+Infinity` resta ammesso come `maxSurfaceY` dello spawn.
- `PlayerController.cs`: stato finito di posizione/rotazione/velocità, controllo
  subito dopo ogni `controller.Move()` e `ApplyFlight()`, ripristino
  dell'ultima posizione valida con log throttled.

`TileClient.CacheVersion` resta 5: formato geo/graph, bridge Android, collider
e fisica dei veicoli non sono cambiati.

## 23. Negozi walk-in 3D: analisi, scelta e implementazione

### 23.1 Perché si vedevano "quattro mura"

Il teleport e l'entrata funzionavano (`InteriorManager` + `BuildingEntrance`),
ma il contenuto era volutamente minimale: `InteriorGenerator.BuildShop`
(` InteriorGenerator.cs:907-1031`) creava bancone, tre scaffali di cubetti,
vetrina, etichetta e un trigger cassa; niente cassiera, niente clienti, niente
attività, niente differenze tra un negozio e l'altro. Esistevano due sistemi
paralleli da **conservare** (Mandato §7): gli interni OSM in-place e il negozio
Indoor/AR (`StoreBuilder.cs`). Quest'ultimo ha un difetto noto e lasciato
intatto: carica `CuteStore/<nome>` mentre i prefab sono in
`CuteStore/Prefabs/`, e i nomi `floor/wall/cash-register/character-employee`
non esistono nel set Cute, quindi la cassiera cade su una capsula. Il prototipo
tycoon `Assets/ThirdParty/SupermarketPrototype/` è isolato dal bridge.

### 23.2 Gli asset già scaricati bastano

Inventario verificato nei Resources del progetto (tutti CC0, sorgente in
`assets/INVENTORY.md`):

| Kit | File | Stato prima | Uso nuovo |
|---|---:|---|---|
| `Resources/Furniture` | 140 FBX | ~20 referenziati | ~120 già importati e non usati: appendini, specchi, poltrone, vasche, macchina caffè, scrivanie |
| `Resources/KenneyMiniMarket` | 20 FBX | usato solo in Indoor | scaffali, freezer, cassa, display, carrello, colonne |
| `Resources/Characters` | 4 FBX | non collegati | `characterMedium` + clip `idle` per cassiera e clienti |
| `Resources/Vehicles` | 14 FBX | solo traffico | 3 auto in showroom con pedane e cartellini |
| `Resources/Pets` | 24 FBX | solo casa/AR | `animal-dog` nel lavaggio cani |
| `CuteStore/Prefabs` | 22 prefab | percorso sbagliato in `StoreBuilder` | prodotti sugli scaffali col percorso corretto |

**Conclusione:** non servivano nuovi download per cominciare. Mancava il
*sistema* che composesse gli asset e desse stato al negozio, non la geometria.

### 23.3 Ricerca internet (fatta, non ancora importata)

Fonti CC0 verificate il 2026-11-25: Kenney **Mini Shop** (30 file, animazioni),
Quaternius **Sushi Restaurant Kit** (108 modelli animati/textured),
**Ultimate Furniture Pack** (20), **Ultimate House Interior Pack** (123);
Kenney **Furniture Kit** è la sorgente già importata. Escluso il "Coffee Shop"
di poly.pizza perché è **CC BY 3.0**, non CC0. Decisione: non importare ora
pack aggiuntivi: i kit già presenti coprono le 7 tipologie con fallback
procedurale e evitano altro peso e nuova revisione licenze. I quattro pack CC0
restano opzione documentata per un futuro passo di qualità (camerini e
showroom più dettagliati).

### 23.4 Cosa è stato aggiunto (tutto additivo)

- `ShopInteriorBuilder.cs`: le sette attività richieste (`supermercato`, `bar`,
  `abbigliamento`, `concessionaria`, `parruccheria`, `lavaggiocani`,
  `biglietteria`) più l'`autolavaggio` dei tag OSM, ognuna con layout dedicato,
  cassiera animata (clip `idle`), 2-3 clienti che passeggiano senza
  NavMesh/Fisica, cartelli in italiano e incasso aggiornato in tempo reale. I
  mobili non hanno collider: la fisica resta quella dei gusci e i trigger
  separati già esistenti. La classificazione controlla i casi specifici prima
  di `bar` (così un `barber` non diventa una caffetteria).
- `InteriorGenerator.BuildInterior`: nuovi casi OSM (`clothes`,
  `hairdresser`, `dogwash`, `carwash`, `ticket`) che riutilizzano `BuildShop`
  e una sola chiamata a `ShopInteriorBuilder.Decorate` a fine costruzione
  (idempotente). `BuildShop/BuildBar/BuildDealer/BuildWorkshop/BuildHouse`
  intatti.
- `BuildingPlacer`: mapping dei tag specifici prima del negozio generico e
  bancone acquistabile per tutte le attività commerciali (prima solo `shop`).
- `ShopState.cs`: contatori stile tycoon (clienti serviti, incasso, livello,
  scorte) con chiave stabile per edificio/coordinate/tipologia. Non accredita
  Wallet: è riepilogo di gestione, non stampella di valuta.
- Ponte bidirezionale: `CityWorldSync` esporta/importa `shopStates` nello
  **stesso snapshot** usato per uova, casa e wallet; evento `ShopActivity`
  whitelistato in `UnityIpc` e ricevuto da `Bridge.kt` → `CityStateSync`
  (mirror in SharedPreferences + campo nel report `checkSync`). Nessun nuovo
  sistema di salvataggio parallelo.
- `InteriorManager.Update` avanza l'attività solo quando `IsInside` è true.

### 23.5 Riflesso della caccia alle uova (proposto e implementato)

Il gioco è una caccia alle uova… **che dici se**, dentro questi negozi, nascondiamo
uova nel cassetto sotto la cassa, nella dispensa e nella credenza, con indizio
testuale tipo *"negozio → parruccheria → credenza asciugamani"*? La risposta è
stata applicata in ottica uova: i nuovi arredi portano nomi riconosciuti da
`ContainerEgg` (`CassettoCassa`, `Dispensa`, `Credenza`,
`CassettoBiglietteria`, `DispensaCani`, `DispensaAutolavaggio`). La chiave è
`negozio@lat,lng#tipologia#nome`, calcolata con `WorldOrigin.ToGeo` e cultura
invariante esattamente come `ContainerEgg.BuildingSeed`: resta stabile anche
dopo un rebase del mondo, non solo dopo il rebuild dei chunk. `ShopState.EggHint()`
fornisce l'indizio in italiano. Le chiavi raccolte
viaggiano già in `foundContainerEggs` verso Android e tornano col restore,
quindi le uova non rinascono. La rarità resta l'unica COMMON→LEGENDARY.

### 23.6 Verifiche

- `cscheck` completo: **OK**.
- `backend/test_unity_shop_interiors.py`: **20 test**, tutti passati (8
  tipologie, layout, innesto additivo, mapping OSM, cassiera animata, niente
  collider sui mobili, uova con chiave geografica stabile, snapshot/ponte
  Android, asset CC0 presenti, rarità unica, fisica e `TileClient.CacheVersion
  = 5` invariati).
- Le 124 verifiche preesistenti (audit 99, cinematografici 9, sicurezza 8,
  guard NaN 8) restano passate.
- Build Android finale con AAR Unity ricompilato: **riuscita** (§19), APK
  `717d1209d170bd4e179b01aec7551fa7b6930c60f0f7a19174b02c4e060fdd6b`,
  log `/tmp/opencode/build_shops_final.log`.
- Nessun tag/POI/geo/graph toccato: i nuovi tipi derivano dai tag OSM già
  presenti nel tile stream; non serve rigenerare la mappa.

## 24. Guida: NRE del `ChunkBuilder` su chunk scaricati durante la build

### 24.1 Evidenza

Errore segnalato durante la guida:

```
[ChunkBuilder] C-0827-0868 edificio salita id=405234122 Types:
System.NullReferenceException: Object reference not set to an instance of an object.
  at City.OSM.ChunkBuilder+<Build>d__8.MoveNext()
  at City.OSM.ChunkManager+<BuildChunkCoroutine>d__64.MoveNext()
```

Lo stesso schema è nel log storico `docs/huntix_log_20261125_073357.txt:14203`:
subito dopo `ERRORE sezione UOVA: System.NullReferenceException` il rigo 14210
dice `chunk 825,871 build interrotta (chunk scaricato)`. Durante lo streaming ad
alta velocità `UnloadFar()` (o il rebase GPS) rimuove il chunk **mentre la
coroutine di build è sospesa su uno `yield`**. La build riprendeva con
`chunk.root`/`buildingsGo` ormai distrutti; `Object.Destroy` è differito a fine
frame, quindi il vecchio riferimento C# sembrava ancora valido abbastanza per
produrre un NRE dentro `MoveNext()`, catturato dalla sezione sbagliata.

### 24.2 Fix (nessun cambiamento di contenuto del mondo)

- `ChunkBuilder.cs`: helper `ChunkAlive(chunk)` (chunk, `root`, `transform`),
  chiamato dopo **ogni** `yield return null;` — strade, edifici, aeroporti,
  natura, veicoli, pompa NPC, uova, POI veicoli, segnali e prima di chiudere la
  build. Anche il ciclo degli NPC ricontrolla il chunk fra due `MoveNext()`.
- Spawn uova: non più `Instance?.SpawnEggsInChunk(chunk.root.transform, ...)`
  (il `?.` proteggeva il Manager, non la root); ora `eggMgr != null &&
  ChunkAlive(chunk)`. Se il chunk muore, **nessun uovo orfano** viene creato
  (Riflesso del Mandato §4: l'uovo resta il fulcro, ma non può esistere senza
  il suo punto nel mondo).
- Log degli edifici null-safe (`b == null`, `cand.rec.t ?? "null"`) e componenti
  del terreno (`MeshFilter`/`MeshRenderer`) letti tramite variabile con guardia.
  Il messaggio "edificio saltato id=…" non può più essere esso stesso la causa
  di un NRE.
- `ChunkData.Destroy()` azzera subito `root` e tutti i GameObject figli:
  le guardie diventano deterministiche anche nello stesso frame della distruzione.
- Un chunk scaricato non viene marcato `built=true` e non riceve LOD/fisica.

Il bridge Android↔Unity, `TileClient.CacheVersion = 5`, le uova già raccolte,
la fisica dei veicoli e i tile/POI/graph restano invariati: non serve
rigenerare mappe né aggiornare i cache dei device.

### 24.3 Verifiche

- `cscheck` completo (`-define:HUNTIX_FULL`): **OK**.
- `backend/test_unity_chunk_builder_guards.py`: 8 test statici che controllano
  che ogni yield abbia una guardia prima del successivo, lo spawn uova protetto,
  i log null-safe e l'azzeramento dei riferimenti in `Destroy()`.
- Tutti i test statici del progetto: **144 test, OK** (audit, cinematografici,
  negozi, NaN e nuovo fix).
- Build Android finale: vedere §19. Riproduzione visiva e FPS restano sul device
  dell'utente: in assenza di emulatore locale non possiamo guidare la città.

## 25. Interni 3D universali riutilizzabili (2026-11-25)

### 25.1 Analisi e scelta architetturale

Dopo i negozi walk-in (§23) restava un problema di *copertura*: solo 8
tipologie commerciali avevano un layout dedicato, mentre case, appartamenti,
uffici, farmacie, ristoranti, scuole, ospedali, banche e hotel ricadevano sul
generico `BuildHouse`/`BuildShop`. La domanda non era "serve un altro kit di
modelli?", ma "esiste un sistema che riutilizzi gli asset già CC0 per qualsiasi
attività e colleghi gli interni senza duplicare salvataggi o economie?".

Ho valutato il progetto **Gateways** (https://github.com/nmacadam/Gateways,
MIT, con dipendenza https://github.com/nmacadam/Scene-Reference): usa GUID di
porta stabili e un `ReturnGate` per il ritorno, ma collega **scene Unity**
tramite `SceneManager` ed è una preview Unity 2019.4 con header misti Unity
Companion License. Importarlo come sistema parallelo avrebbe rotto il mondo
chunked: caricare una scena smonta i chunk e perde il rebase GPS di
`WorldOrigin`. **Decisione:** adattare solo il *pattern* (id deterministici,
porta di ritorno, registro con pulizia) dentro il codice Huntix esistente,
senza scene, senza `DontDestroyOnLoad`, senza codice di Gateways copiato.

Nessun nuovo download: i kit CC0 già importati (§23.2) bastano. I pack
Dollhouse Free, Cozy Cartoon Pack/Cafe, CozyShops, i tre Konbini, gli asset
Asset Store a pagamento e Mixamo restano esclusi per EULA/licenza non CC0
verificabile; GTA/Mafia restano fuori per vincolo IP.

### 25.2 Script aggiunti e punti di innesto

| File | Ruolo |
|---|---|
| `Interior/UniversalInteriorRegistry.cs` | 19 tipologie, classificazione da tag OSM + nome attività, etichette e indizi in italiano, chiave geo-stabile `interno@lat,lng#tipo#nome`, `GateId()` deterministici |
| `Interior/UniversalInteriorBuilder.cs` | pianta a due zone con divisori, arredi per tipologia, contenitori uova, due varchi collegati, cartello; idempotente su `InternoUniversale`; fallback a cubo se la risorsa manca |
| `Interior/InteriorGate.cs` | `InteriorGate`, `InteriorGateRegistry` (register/unregister su `Awake`/`OnDestroy`, più register in `Configure` perché `AddComponent` esegue `Awake` prima dell'id) e `InteriorTeleport.Move` unico per porte, scale e varchi |
| `Interior/UniversalInteriorState.cs` | locali visitati (conteggio + timestamp), `Export`/`Import` con merge non distruttivo, evento `UniversalInteriorVisited` |
| `Interior/InteriorGenerator.cs` | una chiamata a `UniversalInteriorBuilder.Build(...)` a fine costruzione, dopo i builder storici e dopo `ShopInteriorBuilder.Decorate` |
| `Interior/InteriorManager.cs` | registra la visita all'ingresso; teleporto unificato; `SetPlayerPos` rimosso |
| `Interior/BuildingEntrance.cs` | teleporto unificato; `SetPlayerPos` rimosso |
| `Interior/ContainerEgg.cs` | legge `UniversalContainerMarker.room` per l'indizio "città→edificio→stanza→oggetto" |
| `Sync/CityWorldSync.cs` | campo `universalInteriors` nello snapshot esistente, export/import |
| `bridge/UnityIpc.kt`, `Bridge.kt`, `CityStateSync.kt` | whitelist evento, router, mirror SharedPreferences e voci in `checkSync` |
| `backend/test_unity_universal_interiors.py` | 24 verifiche statiche dedicate |

Quattro nuovi file hanno `.meta` con GUID generati una volta e stabili. Nessuna
scena `.unity` è stata modificata a mano: tutto è costruito a runtime sotto il
`Floor_0` già generato. Nessun builder esistente è stato rimosso e nessun
sistema economico duplicato (`Wallet` e `ShopState` restano gli unici).

### 25.3 Interni disponibili e edifici collegati

Tipologie universali: **casa, appartamento, negozio, supermercato, bar,
ristorante, abbigliamento, concessionaria, farmacia, ufficio, parruccheria,
lavaggiocani, autolavaggio, biglietteria, officina, banca, scuola, ospedale,
hotel**. La classificazione privilegia il nome dell'attività (farmacia,
ristorante/pizzeria, ufficio/studio) sui tag generici OSM; per i negozi
riutilizza `ShopInteriorBuilder.Classify`, così caffetterie, concessionarie e
parruccherie restano coerenti col sistema §23.

Gli interni universali non creano nuovi edifici: si innestano sugli edifici
**già enterabili** generati da `BuildingPlacer`, con il budget invariato di
**8 edifici per chunk** e il caricamento lazy entro **30 m** dalla porta
(`InteriorManager`). Funzionano sia nei gusci in-place sia nei prefab con
fade, perché il builder lavora solo sul pavimento prodotto da
`InteriorGenerator`. I piani superiori restano gestiti da `StairTrigger`; i
due varchi universali collegano due zone dello stesso piano, non due scene.

### 25.4 Asset usati, licenze e URL

| Fonte | Licenza / URL | Uso in questa fase |
|---|---|---|
| Kenney Furniture Kit | CC0 1.0 — https://kenney.nl/assets/furniture-kit , testo in `assets/furniture/License.txt` | scrivanie, letti, armadi, frigo, lavagne, poltrone, lampade, cassetti |
| Kenney Mini Market | CC0 — https://kenney.nl/assets (file già presenti in `Assets/Resources/KenneyMiniMarket`) | scaffali e cassa di farmacie, negozi, supermarket, biglietterie |
| Kenney Car Kit | CC0 — https://kenney.nl/assets/car-kit , testo in `assets/car-kit/License.txt` | auto in showroom e officine/autolavaggi |
| Kenney Cube Pets | CC0 — https://kenney.nl/assets/cube-pets , testo in `assets/cube-pets/License.txt` | cane nel lavaggio cani |
| Kenney Characters | CC0 — https://kenney.nl/assets , testo in `assets/characters/License.txt` | già usati da `ShopInteriorBuilder`; non duplicati |
| CuteStore prefab | già importati da pacchetto precedente, nessun file scaricato in questa fase | corretto solo il percorso `CuteStore/Prefabs/` in `StoreBuilder`; licenza preesistente non riesaminata |
| Quaternius packs già presenti | CC0 per i pack verificati, QAL v1.0 come licenza sito — https://quaternius.com/license.html | nessun nuovo pack, nessuna modifica ai modelli |
| Gateways / Scene-Reference | MIT — https://github.com/nmacadam/Gateways , https://github.com/nmacadam/Scene-Reference | solo pattern adattato; nessuna riga o asset copiato |
| Poly Pizza | CC0 solo per asset selezionati — https://poly.pizza/ | valutati, **non importati**; il "Coffee Shop" è CC BY 3.0 quindi scartato |

Se una risorsa `Resources.Load` non è presente nell'AAR, il builder genera un
cubo con materiale condiviso: nessun modulo mancante può bloccare la build o
la visita. Nessun collider sui mobili, nessun `Rigidbody`, `NavMeshAgent` o
`CharacterController` aggiunto dagli arredi.

### 25.5 Interazioni, uova e bridge

- **Varchi**: trigger `BoxCollider` con tag `Player`, cooldown di 2 s anti-loop,
  fade con `Game.fader`, arrivo davanti al varco opposto e opposizione automatica
  (`target.targetGateId = gateId`) come `ReturnGate`. Se il chunk di arrivo è
  stato scaricato il registro non trova la porta e il giocatore resta dove è.
- **Cartelli** in italiano con tipologia e indizio; nessun nuovo pulsante UI.
- **Riflesso della caccia alle uova**: ogni interno riceve contenitori con
  nomi riconosciuti da `ContainerEgg` (`Cassetto`, `Armadio`, `Dispensa`,
  `Scrivania`, con suffisso per tipologia). `UniversalContainerMarker.room`
  rende l'indizio specifico (per esempio *banco farmacia* invece di *piano1*).
  La chiave usa `WorldOrigin.ToGeo` con cultura invariante, quindi sopravvive
  a rebase e rebuild; le uova raccolte restano in `foundContainerEggs` e la
  rarità resta l'unica `EggController.Rarity`.
- **Visite**: `UniversalInteriorState.MarkVisited` all'ingresso aggiorna
  conteggio/timestamp, invia `UniversalInteriorVisited` e confluisce nello
  snapshot `CityWorldSync`. Android unisce per massimo in
  `city_universal_interiors_v1` e il report `checkSync` mostra mirror e
  presenza nello snapshot (ponte bidirezionale, nessun secondo salvataggio).

### 25.6 Verifiche eseguite

- `cscheck` completo: **OK**, 0 errori e 196 warning preesistenti. Nota di
  precisione: il comando corretto è `cd backend/preprocessing/cscheck &&
  ./check.sh` senza argomenti; la dicitura `-define:HUNTIX_FULL` al §24.3 era
  imprecisa (attiva la modalità mirata con stub).
- `python3 -m unittest discover -s backend -p 'test_unity_*.py'`: **183 test,
  OK** (24 nuovi per gli interni universali: catalogo, innesto additivo,
  budget/lazy/`CacheVersion`, asset CC0 presenti, niente collider/Rigidbody nei
  mobili, varchi senza cambi scena, teleporto unico con guard anti-NaN, uova,
  snapshot e router Android; più i test dello switch del §26).
- Corretti due difetti preesistenti emersi dai test: percorso
  `Furniture/credenza` → `Furniture/cabinetBedDrawer` in `ShopInteriorBuilder`
  e `CuteStore/` → `CuteStore/Prefabs/` in `StoreBuilder`.
- `TileClient.CacheVersion` resta **5**: nessun tag, POI, geo o graph è
  cambiato; non serve `aggiorna mappa` né svuotare cache tile.
- Build Android finale con `SKIP_MAP_UPDATE=1`: risultato e hash al §25.8.

### 25.7 Verifiche non eseguite, limitazioni e prossimi miglioramenti

Non c'è device o emulatore locale: **non sono stati verificati** l'aspetto
visivo dei 19 layout, l'attraversamento reale dei varchi in guidata, la cattura
delle uova nei nuovi contenitori, gli FPS con 49 chunk e il restore Android su
installazione reale. La causa scatenante del primo NaN del 2026-11-25 resta non
dimostrata (§22); il teleporto unificato aggiunge la guard anti-NaN, non una
diagnosi della sorgente.

Prossimi passi consigliati: prova su device delle tipologie più frequenti
(casa, bar, ristorante, farmacia, ufficio) e misurazione FPS prima di ampliare
il budget 8/chunk; refactoring dei nomi dei prefab CuteStore non trovati
(`floor`, `wall`, `cash-register`, `character-employee`) senza toccare il
sistema Indoor/AR; valutazione dei pack CC0 Poly Pizza solo dopo nuova verifica
di licenza per file; eventuali varchi fra piani collegati a `StairTrigger`
mantenendo id geostabili. In sintesi: il sistema è additivo, reversibile e
pronto per la verifica sul device; nulla è stato pushato.

### 25.8 Build finale degli interni universali e dello switch negozio

- Comando: `LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 SKIP_MAP_UPDATE=1
  SKIP_INSTALL=1 FORCE_UNITY_AAR=1 ./build_release.sh --auto`.
- Log: `/tmp/opencode/build_shop_switch_final.log`; export Unity:
  `unitylic/unity-export.log`. **Build riuscita**, APK firmato
  `app/build/outputs/apk/release/app-release-signed.apk` (199.521.364 byte).
- **SHA-256**:
  `f482d327a6ca3cca1c2b48d42068bc04c310daecd39d6f0a27f115c602cec456`.
- Il prefabbricato CC0 `Assets/Resources/ShopPrefabs/NegozioProntoCC0.prefab`
  è stato generato in editor da `ShopPrefabSetup` (hook in
  `ExportAndroidGradleProject`, il metodo usato da `build_release.sh`) ed è
  entrato nell'APK. Artefatti collaterali della build (`link.xml.meta`,
  `PlayerLocomotion.controller`, `PlayerHero.prefab`) ripristinati da Git.
- Non è stato eseguito alcun aggiornamento mappe/POI/tile: `CacheVersion`
  resta 5. Nessun push.

## 26. Switch A/B del negozio: sistema Huntix vs prefabbricato CC0 (2026-11-25)

### 26.1 Richiesta e scelta

L'utente ha chiesto uno switch per scegliere il tipo di negozio «senza
eliminare il nostro sistema, ma aggiungendo uno dei negozi già pronti,
così vediamo chi è migliore», precisando di volere **un prefabbricato**.
La soluzione è additiva e locale: un vero prefab Unity
`Assets/Resources/ShopPrefabs/NegozioProntoCC0.prefab`, generato **una volta**
in editor (`Assets/Editor/ShopPrefabSetup.cs`) con i kit **già importati e
verificati CC0** del progetto (Kenney Mini Market / Furniture e CuteStore).

Non è stato importato alcun negozio terzo: CuteStore ha licenza locale non
chiaramente documentata come CC0 e `SupermarketPrototype` porta un proprio
sistema economico/`Player` che duplicherebbe Wallet e uova. Nessun download,
nessun Asset Store, nessun cambio di scena, nessun `CacheVersion` diverso da 5.

### 26.2 Architettura (cosa cambia e cosa resta)

- `ShopStyle.cs` (nuovo): stato `Huntix` (default) o `Prefabbricato` in
  `PlayerPrefs`, con timestamp UTC e merge non regressivo. Non contiene
  valute, rarità o salvataggi paralleli.
- `ShopPrefabInstaller.cs` (nuovo): istanzia il prefab dentro il `Floor_0`
  già generato, adatta l'impronta 6×6 m all'edificio, è idempotente, rimuove
  i collider sull'**istanza** (mai sui prefab sorgente) e, se il prefab non
  c'è (build vecchia), ricade sul builder universale invece di lasciare la
  stanza vuota.
- `ShopPrefabSetup.cs` (nuovo, Editor): crea il prefab con banco, pedana,
  frigo, cassa, scaffali, carrello, prodotti e due contenitori uova. Chiamato
  da `HuntixBuild.ConfigureAndExport()` prima dell'export AAR, così il prefab
  entra nell'APK ed è versionato nel repository.
- `InteriorGenerator.BuildInterior`: sceglie `ShopStyle.UsePrefab` fra
  `ShopPrefabInstaller` e `UniversalInteriorBuilder`. **Resta intatto** il
  livello attività (`ShopInteriorBuilder.Decorate`, cassiera, clienti,
  `ShopState`): cambia solo lo strato arredi del piano 0.
- `HamburgerMenu`: nuova voce «Negozio: Huntix/Prefabbricato», dopo i dettagli
  cinematografici. Come lo stile edifici, rifiuta il cambio dentro un edificio
  o in auto e ricarica il mondo con `ReloadWorld()` (ponte sotto il player).
- Ponte bidirezionale: `ShopStyleChanged` in whitelist `UnityIpc` e router
  `Bridge`; `shopStyle` nello snapshot `CityWorldSync`; mirror Android
  `city_shop_style_v1` con merge per timestamp e campo `shopStyle` in
  `checkSync`. Nessun evento o schermata nuova lato app.

### 26.3 Riflesso della caccia alle uova

Il prefab include `CassettoPronto` e `DispensaPronto` con
`UniversalContainerMarker`: `ContainerEgg` li usa come contenitori con indizi
«città → edificio → banco/dispensa prefabbricato → cassetto». Il marker
d'interno riceve la chiave geostabile `WorldOrigin.ToGeo` e l'`eggHint` della
tipologia. Le uova restano quelle del sistema esistente: nessuna seconda
rarità, nessuna seconda economia.

### 26.4 Verifiche eseguite

- `python3 -m unittest discover -s backend -p 'test_unity_*.py'`:
  **183 test, OK**. I 15 nuovi test verificano default Huntix, timestamp e
  import non regressivo, prefab salvato in `Resources`, asset CC0 presenti,
  editor che non distrugge i prefab sorgente, installer idempotente e senza
  collider, marker uova, fallback, coesistenza dei due builder, voce menu,
  invarianti `CacheVersion = 5`/`MaxEnterablePerChunk = 8` e ponte Android.
  Aggiornati tre test preesistenti che fissavano l'altezza del pannello a
  1540 px: ora accettano 1620 px (nuova voce) senza rimpicciolirlo.
- `cd backend/preprocessing/cscheck && ./check.sh`: **OK**, 0 errori e
  196 warning preesistenti (dopo l'aggiunta di `using City.World;` al nuovo
  installer).
- Build Android finale con prefab generato in editor: vedi §25.8.

### 26.5 Limiti e confronto A/B

Serve un device per dire «chi è migliore»: il prefabbricato è un banco
una-tantum con scaffali fissi, il sistema Huntix adatta 19 tipologie e due
zone con varchi. Lo switch serve a confrontarli nello stesso edificio, non a
sostituire gli interni universali. Senza device restano da verificare
visivamente arredi, uova nei contenitori, FPS e restore dello switch. Nulla è
stato pushato.

## 27. Dettagli naturali di tutto il mondo — 26/11/2026

Obiettivo: rendere più ricco e naturale **ogni** mondo Huntix (MiCittà/OSM,
Città Gioco, mondo legacy e AR Android) senza sostituire la vegetazione
esistente e senza nuovi download. Nessuna funzione preesistente è stata
rimossa o disattivata.

### 27.1 Asset e licenze

Riusati i **118 FBX già importati** da Kenney Nature Kit in
`unity-project/Assets/Resources/Nature/` (CC0 1.0, sorgente
`kit/kenney_nature-kit.zip`, licenza in `assets/nature/License.txt`,
inventario in `assets/INVENTORY.md`). Non è stato importato QuaterniusNature
né alcun duplicato: il report precedente (§21) resta invariato. Nuovo utilizzo
dei modelli: erba, fiori, cespugli, sassi e alberi di parco; la luce calda è
procedurale, al massimo una per chunk e solo a LOD0.

### 27.2 Sistema additivo

| File | Ruolo |
|---|---|
| `Environment/NatureDetailPlacer.cs` | Lotti di istanze GPU (`Graphics.DrawMeshInstanced`), mesh del prefab combinate una volta, materiali condivisi con `enableInstancing`, distanza di streaming, nessun collider e distanza di 4 m dalle porte |
| `Diagnostics/VegetationFeatureBridge.cs` | Quattro preferenze (`prati-fioriti`, `cespugli-rocce`, `alberi-parco`, `luci-naturali`), pref `huntix_veg_*`, evento `VegetationFeatureChanged`, ripristino passivo e riflesso uova |
| `OSM/ChunkBuilder.cs` | Nuova sezione isolata `Dettagli naturali`, dopo la sezione Natura storica (che resta intatta) |
| `OSM/ChunkData.cs` | `detailsGo`, LOD dedicato e preferenze applicate a caldo senza riaccendere un LOD spento |
| `Game/FictionalCityWorld.cs`, `OSM/CityOSMWorld.cs` | Stessi lotti GPU nei mondi piatti e legacy |
| `reallife/OsmCityBuilder.kt` | Ciuffi d'erba e sassi additivi nella AR Android, dentro il budget `MAX_NODES` già esistente |
| `UI/HamburgerMenu.cs` | Voci «Dettagli naturali: On» e «Dettagli vegetazione» (pannello con 4 toggle) |
| `Bridge.kt`, `UnityIpc.kt`, `BridgeActivity.java`, `GameManager.cs` | Ponte bidirezionale: Android salva le preferenze e le rimanda in `setMode` come `vegetationFeatures=id=1,id=0` |

Le opzioni sono **attive di default**: Android diventa proprietario della
preferenza solo dopo il primo toggle, esattamente come i gruppi `huntix_gfx_*`
e `huntix_os_*`, ma con prefisso dedicato e senza collisioni. Il reset di
`hasChanged` evita lavoro ridondante; la sezione 31 corregge il meccanismo
originale (matrici modificate in place e flag condiviso fra i lotti), che dopo
il rebase GPS lasciava i dettagli alle vecchie coordinate.

### 27.3 Riflesso della caccia alle uova

Uova nascoste **tra cespugli, sassi e fiori** delle nuove zone verdi, non
accanto al player. Chiavi `vegetazione->parco->fioriture`,
`vegetazione->tutto` e `vegetazione-><id>`; luogo testuale
`città→parco→vegetazione` (varianti `città→città-gioco/…` e
`città→mondo-osm/…`). Posizione e coordinate GPS viaggiano sul canale
canonico `FeatureEggHidden` tramite `Game.GrantVegetationEgg` →
`SpawnFeatureEggAt` → `EggCapturedInCity`: nessun premio duplicato, nessuna
seconda economia e dedup Android per chiave esatta.

### 27.4 Invarianti rispettati

`LoadRadius=3`, `UnloadRadius=4`, `TerrainColliderLod=0`, step DEM/mesh 15/45 m,
kit edifici e `CarsPerTile=22`/`MaxCars=180` invariati,
`TileClient.CacheVersion=5` (nessun cambio di formato geo/graph),
`VegetationPlacer.MaxTreesPerChunk=400`. Il campionamento altimetrico usa
`TerrainHeightField.HeightNear` con fallback `TileElevation.TryHeightAtWorld`,
quindi i dettagli seguono il DEM senza creare proxy dalle altezze degli edifici.

### 27.5 Verifiche

- `python3 -m unittest discover -p 'test_*.py'` in `backend/`: **316 test OK**
  (9 nuovi guardia statica degli asset CC0, ponte, menu, uova, LOD,
  instancing e invarianti; aggiornato il conteggio voci menu a 29).
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori,
  215 warning preesistenti. Stub estesi (solo nel checker, mai nel progetto
  Unity) per `CombineInstance`, `Mesh.CombineMeshes`, `Material.enableInstancing`,
  copy constructor, `Transform.hasChanged`, `Matrix4x4.TRS/inverse/*`,
  `LightRenderMode` e `Graphics.DrawMeshInstanced`.
- `./gradlew :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --offline -q`:
  **BUILD SUCCESSFUL** (solo il solito avviso di deprecated API in
  `BridgeActivity.java`).
- Build release con AAR Unity ricreato e APK firmato: **BUILD SUCCESSFUL in 5m 49s**
  (`SKIP_INSTALL=1 SKIP_MAP_UPDATE=1 SKIP_SRTM_DOWNLOAD=1 ./build_release.sh`,
  log `/tmp/opencode/build_veg.log`). APK `app/build/outputs/apk/release/app-release-signed.apk`,
  204.882.545 byte, firma v3 verificata (1 signer). **SHA-256**:
  `0af97a2179fc7bf566e865400bd725cd1cc938fa47532d1ffe843ca510af82c9`.
  Nel `global-metadata.dat` dell'AAR e dell'APK sono presenti i token
  `VegetationFeatureChanged`, `vegetationFeatures`, `Dettagli vegetazione` e
  il prefisso `huntix_veg_`: il codice nuovo è entrato davvero nell'artefatto.
  Artefatti collaterali della build (`link.xml.meta`, `PlayerLocomotion.controller`,
  `PlayerHero.prefab`) sono stati ripristinati e non fanno parte del commit.
- Collaudo su `emulator-5556`: APK installato e permessi concessi, avvio di
  MiCittà in modalità «Città Gioco» tramite `BridgeActivity`. Il `setModeJson`
  iniziale contiene `vegetationFeatures=""`; menu ☰ → «Impostazioni e
  strumenti» → «Dettagli vegetazione» mostra le quattro opzioni attive
  («Prati e fiori: On», «Cespugli e rocce: On», «Alberi nei parchi: On»,
  «Luci dei parchi: On»). Disattivando «Luci dei parchi» il log Android registra
  `I/HuntixSync: Vegetazione: luci-naturali on=false` e la pref diventa
  `huntix_veg_luci-naturali=false`. Dopo force-stop e riavvio il
  `setModeJson` contiene `vegetationFeatures=luci-naturali=0` e il pannello
  ripropone «Luci dei parchi: Off» (con «Dettagli naturali: Off»): ritorno
  Android→Unity e persistenza confermati. Nel diary `feature_eggs` sono
  comparse le chiavi con coordinate
  `vegetazione->parco->fioriture|41.113083|16.866607` e
  `vegetazione->luci-naturali|41.1171|16.871931`: uova nel verde con posizione
  testuale e GPS, senza duplicati. Ripristinando il toggle a On la pref torna
  `true` e le opzioni tornano tutte On; lo stato di prova dell'emulatore è
  stato ripulito. Nessun crash/ANR dell'app: solo rumore di piattaforma
  (`StrictMode`, `ChimeraSrvcProxy`) non legato a queste modifiche.

---

## 28. QA attivo su emulatore, loop anti-NAN trovato e corretto (26/11/2026)

### 28.1 Il guasto riproducibile

Durante la prova di forza su `emulator-5556` in MiCittà, dopo il ciclo di
toggle del menu (eventi sportivi, stile edifici, negozio, impostazioni) il log
mostrava ripetutamente:

`[PlayerController] SALVATAGGIO NaN #73 pos=(0.95, 1.05, 0.00) -> ultima posizione valida`

Il rescue scattava decine di volte di fila mentre la telemetria segnalava
`fps=2 frame=800.0ms ... LAG`. Non era un crash Java ma un **blocco
funzionale**: il guard rilevava un campo non finito ma il salvataggio
ripristinava solo la posizione. Se il velo era `moveInput` o la rotazione, il
guard scattava di nuovo al frame successivo. Un drag con coordinate non finite
poteva inoltre avvelenare l'input alla sorgente.

### 28.2 Fix additivo

| File | Modifica |
|---|---|
| `Player/PlayerController.cs` | `RememberGoodPlayer()` memorizza anche l'ultima rotazione finita; `RescueInvalidPlayer()` ripristina la rotazione, azzera `moveInput`/velocità/direzione e scrive `causa=[...]` nel log per diagnosticare il campo incriminato |
| `UI/DynamicJoystick.cs` | raggio minimo 1 e filtro su coordinate screen/local non finite prima di calcolare il delta |
| `UI/AlternateTouchInput.cs` | stesso filtro sul drag prima di calcolare il delta |
| `backend/test_unity_nan_guards.py` | guardia statica sul loop, sul log con `causa=[`, sul ripristino della rotazione e sui filtri dei due stick |

Nessuna funzione esistente è stata rimossa o disattivata. Il percorso con
valori finiti resta invariato. Il riflesso uova non introduce nuove chiavi: i
canali `VegetationFeatureChanged`/`FeatureEggHidden` e le uova con coordinate
restano quelli della sezione 27, compresi i nuovi eventi generati durante il
QA (`vegetazione->parco->fioriture|41.113083|16.866607` e
`vegetazione->tutto|41.1171|16.871942`).

### 28.3 Verifiche e nuova build

- `python3 -m unittest discover -p 'test_*.py'` in `backend/`: **317 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori,
  215 warning preesistenti. `git diff --check` pulito.
- `SKIP_INSTALL=1 SKIP_MAP_UPDATE=1 SKIP_SRTM_DOWNLOAD=1 ./build_release.sh`:
  **BUILD SUCCESSFUL in 4m 46s** e **BUILD SUCCESSFUL in 1m 54s**. APK firmato
  con schema v3 (1 signer). **SHA-256**:
  `8fedfa9e9a4314dc8121db33b7d4a01926dc280bcd750dd1ef8aa9cc97d56bc1`.
  I token `causa=[` e `moveInput` sono presenti nel `global-metadata.dat` di
  AAR e APK: il fix è entrato davvero nell'artefatto. Il churn della build
  (`link.xml.meta`, `PlayerLocomotion.controller`, `PlayerHero.prefab`) è stato
  ripristinato e non fa parte del commit.

### 28.4 Collaudo sul dispositivo

APK reinstallato su `emulator-5556`, pref di prova `world_mode=fictional`
ripristinate e MiCittà avviata via `BridgeActivity`. Ripetendo la sequenza che
innescava il guasto: **0** occorrenze di `SALVATAGGIO NaN`, **0** `causa=[`,
nessun `FATAL EXCEPTION`, `ANR in`, `NullReferenceException`,
`IndexOutOfRangeException`, `ArgumentException` o `InvalidOperationException`,
app ancora in primo piano. Le uova con posizione testuale e coordinate GPS
sono state generate regolarmente anche dopo il fix.

Stress successivo con `monkey -p com.intelligame.huntix --pct-syskeys 0
--throttle 80 500`: **500 eventi iniettati**, nessun crash/ANR dell'app e
ancora **0** rescue NaN. I valori `fps` bassi restano quelli dell'emulatore
software (SwiftShader, senza GPU reale), non una regressione del fix; durante
la build l'emulatore era andato in segfault per pressione RAM/CPU della
macchina, quindi è stato riavviato a build terminata e non è un guasto del
gioco. Le preferenze di prova dell'emulatore sono state ripristinate dal backup.

---

## 29. Ricerca senza risultati visibili e casa illuminata senza porta (26/11/2026)

### 29.1 Sintomi riportati dal QA

1. Dal chip Android **CERCA DESTINAZIONE** il pannello Unity si apriva, ma
   lo stato diceva «trovati N luoghi» senza mostrare righe tappabili; il campo
   restava vuoto e la tastiera non si apriva in modo affidabile.
2. La casa di Foggia era illuminata e riconoscibile, ma non si trovava la
   porta girandoci attorno: non compariva il prompt **ENTRA IN CASA** e
   l'ingresso restava irraggiungibile.

### 29.2 Cause

- La lista usava `Mask` su un'immagine trasparente: la maschera dipendeva
  dall'alpha della Graphic e poteva ritagliare tutte le righe. Le righe
  venivano scritte solo nel contenitore della vista attiva, quindi un
  geocoding arrivato durante un cambio vista le faceva sparire. Su Android
  mancava inoltre `EventSystem.current.SetSelectedGameObject`: il campo
  veniva selezionato ma non era il GameObject attivo dell'EventSystem.
- Il chip Android inviava sempre `{}`: nessuna query precompilata.
- Il segnaposto porta veniva creato dal lazy dell'interno (fino a 30 m dal
  player), quindi non era visibile appena il chunk caricava. La posizione era
  sempre il `+Z` locale del prefab, mentre la porta vera della casa di Foggia
  è vicino all'angolo ovest di Via Silvio Pellico: lato e punto lungo il muro
  erano entrambi sbagliati. Anche soglie di ingresso/uscita usavano il `+Z`
  del prefab, coerentemente con una porta che non esisteva.

### 29.3 Fix additivi

| File | Modifica |
|---|---|
| `OSM/MapSelectUI.cs` | `RectMask2D` invece di `Mask`; righe dedicated (`AddSearchResultRow`/`AddSearchRowTo`) scritte su **entrambe** le viste, testo bianco su `ColResultHi`, log `[MapSelect][Search] righe=`; focus esplicito dell'EventSystem; `OpenSearch(string query)` precompila e lancia subito la ricerca |
| `Scripts/Core/GameManager.cs` | `openDestinationSearch` legge `{"query":"..."}` con regex e chiama `OpenSearch(query)`; senza query resta il percorso storico |
| `ui/CityMapActivity.kt` | nuovo `EXTRA_SEARCH_QUERY`, escape della stringa e payload JSON verso Unity; il percorso `{}` resta invariato |
| `Interior/Visit/VisitInteriorRegistry.cs` | `TryDoorLocalOffset` converte la coppia porta/centro del registro nelle coordinate locali del prefab (yaw OSM), senza inventare il lato |
| `OSM/BuildingPlacer.cs` | lato scelto per distanza normalizzata sui semilati e punto lungo il muro con `Mathf.Clamp`; materiale porta oro emissivo a doppia faccia solo per le due case; `forceDoorMarker` crea il portale subito, con tre stipiti luminosi |
| `Interior/BuildingEntrance.cs` | portale grande/emissivo immediato per le case configurate, soglie di ingresso/uscita dal trigger reale; gli altri edifici conservano il segnaposto lazy |
| `Interior/InteriorGenerator.cs`, `Interior/InteriorManager.cs` | `DoorAnchorWorld` legge il trigger reale; nuove soglie `PrefabEnterWorld/ExitWorld` con rotazione dell'ingresso; fallback identico al vecchio comportamento quando non c'è `Entrance` |
| `backend/test_unity_visit_houses.py`, `backend/test_unity_menu_search_storage.py`, `cscheck/UnityStubs.cs` | guardie statiche su lato/porta (Foggia `+Z` con `along < -6`, Roma `+Z` centrale), visibilità righe e query del ponte; stub solo nel checker |

Nessuna funzione esistente è stata rimossa o disattivata: le porte degli altri
edifici, il lazy dell'interno e la ricerca da tastiera restano identici.

### 29.4 Ponte bidirezionale e riflesso uova

Il chip Android → `openDestinationSearch` con payload JSON → `MapSelectUI` →
geocoding → `OnGeocodeResult` → righe visibili. La scelta di un risultato e
**TELEPORTA** generano l'uovo canonico con luogo testuale e coordinate
(`FeatureEggHidden` → `EggCapturedInCity`), senza nuova economia. Le case
visitabili usano le chiavi stabili `casa-osm-<id>` / `casa→stanza→oggetto` e
lo snapshot `HouseInteriorState` per la dedup: l'ingresso crea le quattro uova
nascoste nei contenitori già presenti, la cattura passa dallo stesso
`EggController` di sempre. Nessun nuovo evento di ponte è stato introdotto.

### 29.5 Verifiche e build

- `python3 -m unittest discover -p 'test_*.py'` in `backend/`: **319 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori,
  214 warning preesistenti. `git diff --check` pulito.
- `./gradlew :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --offline -q`:
  **BUILD SUCCESSFUL**.
- `SKIP_INSTALL=1 SKIP_MAP_UPDATE=1 SKIP_SRTM_DOWNLOAD=1 ./build_release.sh`:
  **BUILD SUCCESSFUL in 7m 29s** e **BUILD SUCCESSFUL in 7m 1s**. APK firmato
  v3 (1 signer), SHA-256
  `5bbe4b06b3029535b10258d4951ac78d82934c38228dcb22f93b2a2ff25bfdb7`.
  I token `rectMask2D`, `TryDoorLocalOffset`, `forceDoorMarker`, `StipiteSx`
  e `PrefabEnterWorld` sono presenti nel `global-metadata.dat` dell'APK e
  `EXTRA_SEARCH_QUERY` nei DEX: i due fix sono entrati davvero nell'artefatto.
  Il churn della build (`link.xml.meta`, `PlayerLocomotion.controller`,
  `PlayerHero.prefab`) è stato ripristinato.

### 29.6 Collaudo su emulatore

Sessione pulita su `emulator-5556`, server tile `localhost:5100` attivo.

**Ricerca:** tap sul chip `[892,460][1080,549]` con `EXTRA_SEARCH_QUERY=bar`;
log `[GameManager] Event received: openDestinationSearch - {"query":"bar"}`,
`[MapSelect][Search] righe=14` e poi `righe=17` dopo il geocoding. Lo
screenshot mostra 17 risultati leggibili; toccando la prima riga e
**TELEPORTA QUI** il diario registra
`ricerca->stadio->Sannicandro di Bari · Sannicandro di Bari` con
`lat=41.002419 lng=16.792016`. Nessun errore Huntix.

**Casa:** modalità Reale/OSM e spawn forzato sulla porta (tile `IT_082_087`,
OSM `405228771`). Il log conferma il caricamento del tile, l'interno lazy
costruito sull'ancora porta (`porta=(12.0, 1.0, 1.3)`), il trigger
`[BuildingEntrance] OnTriggerEnter: Casa di Foggia (house)` e il prompt
**ENTRA IN CASA**. Il tap deve cadere nel bordo alto del pulsante
(y≈1998): più in basso il pulsante `PASSA A AR` sovrappone la stessa area,
questione di HUD già esistente e non legata a questo fix. Con il tap corretto
il log registra `Entrato (in-place, 3a persona): Casa di Foggia`,
`visita casa: Casa di Foggia (OSM 405228771)` e
`uova casa nascoste: 4 contenitori (cucina, camera, bagno)`; la telemetria
passa a `ground=ColliderShort/Pavimento`, confermando che il player è dentro.
Nessun `FATAL EXCEPTION`, `NullReferenceException`, `ArgumentException` o ANR
dell'app. La cattura delle uova resta il mini-gioco preesistente già coperto
dalle sezioni 27-28; in questa prova non è stato forzato.

Le preferenze modificate per il collaudo sono state ripristinate dai backup.

## 30. Carriera, abilità dei mestieri e furto auto (26/11/2026)

### 30.1 Richiesta e diagnosi

L'utente ha segnalato che, completando un lavoro in MiCittà, il livello visibile
restava `1`, e ha chiesto che salire di livello faccia anche guadagnare più
soldi. Ha inoltre chiesto un furto auto **permanente** (niente officina e
concessionaria obbligatorie) sbloccato da un'abilità "master nei furti", con
tipologie diverse per livello (bici/auto piccole → auto grandi → appartamenti),
nuovi utenti da abilità zero e altre abilità "carine".

La diagnosi ha confermato tre livelli distinti e non collegati: `JobManager.Level`
(locale del lavoro, cap 10, +15% di paga), `PlayerProfile.level` (quello che
vede l'utente, soglia `lv*150`) e i livelli separati di `MiniGameManager`.
Un lavoro dava 16-22 XP contro soglie da 150, quindi il livello del profilo non
si muoveva. Il server `vehicle_services.py` ha già un motore furti/riscatti/
abbandoni: è stato lasciato intatto e il furto è un **gate lato client**.

### 30.2 Scelta delle abilità

Oltre a **Mastro Ladro** (che apre i furti) sono state proposte e introdotte:
**Pilota**, **Meccanico**, **Attore**, **Assassino** e **Contabile**. La scala è
coerente con i mestieri già presenti (Tassista/Corriere/Ronda/Cineasta) e con
quelli citati come futuri (attore, cassiere, banchiere, parcheggiatore
abusivo), senza implementarli ora. Le vecchie sei abilità caccia-uova di
`AbilityManager` restano invariate: il nuovo sistema è separato.

### 30.3 Implementazione additiva

- **Lavoro → +1 livello e più paga.** `JobManager` invia `CityJobCompleted` con
  luogo testuale e coordinate; Android accredita esattamente
  `xpForNextLevel - xp` sul profilo, regala 1 punto abilità e mostra il toast
  `livello N → N+1`. La paga usa `PayMult` storico (+15%) moltiplicato per il
  nuovo `CareerPayMult` (+10% per livello di carriera, fino al livello 50).
- **Abilità.** Nuovo `CitySkillManager.kt` (fonte di verità Android,
  SharedPreferences, livelli 0..10, punti e usi) e specchio Unity
  `CitySkills.cs`. Nuovi utenti partono da 0; ogni salto di livello regala un
  punto; l'utente lo spende dalla sezione "Mestieri di MiCittà" in `Abilità`.
- **Furto.** Nuovo pannello `TheftUI.cs` raggiungibile da concessionaria e
  officina con il pulsante "🚗 RUB AUTO (Mastro Ladro Lv.N)". Il gate segue il
  prezzo: Lv1 bici/scooter/moto, Lv2 utilitarie, Lv4 SUV/furgoni, Lv6 sportive,
  Lv8 auto di lusso, Lv10 appartamenti (futuro). L'auto presa entra nel registro
  con codice `T...`, diventa inventario/possesso permanente e viene consegnata
  e montata subito: nessun `Wallet.Spend`, nessun timer e nessun riscatto.
- **Ponte bidirezionale.** Unity→Android: `CityJobCompleted`,
  `CityVehicleStolen` aggiunti alla whitelist `UnityIpc.APP_EVENTS`; Android→
  Unity: `citySkills`/`citySkillPoints` in `setMode` ed evento caldo
  `CitySkillsUpdated`.

### 30.4 Riflesso uova

Ogni lavoro registrato e ogni auto rubata scrivono su `EggWhereLog` con
**luogo testuale + lat/lng**: `MiCittà→lavori→<mestiere>` e
`MiCittà→garage→furto→<modello>`. Se il lavoro lascia davvero un uovo nel mondo
viene registrata anche la rarità; il furto lascia "uovo nel bagagliaio". Le uova
già presenti alle stazioni LAVORI e alla consegna non sono state modificate.

### 30.5 Verifiche

- `python3 backend/test_unity_city_skills.py`: **12 test OK**; tutti i 14
  `backend/test_unity*.py` passano.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori.
- `SKIP_MAP_UPDATE=1 ./gradlew :app:compileDebugKotlin
  :app:compileDebugJavaWithJavac --no-daemon`: **BUILD SUCCESSFUL**.
- `GameManager.cs` (fuori dalla copertura mcs dello stub) non è compilabile con
  `check.sh` a causa di sintassi C# moderna preesistente: le aggiunte sono
  state verificate per confronto con il parser dell'evento pipe e dal build
  Gradle lato Android.
- QA su device/emulatore: **non eseguito**, il tentativo di avvio di
  `huntix_field` è terminato con `Segmentation fault (core dumped)` prima del
  boot. Restano da verificare su dispositivo: lavoro → livello 2 e paga più
  alta, spesa di 1 punto abilità, furto bici al Lv1 e rifiuto di un'auto di
  lusso senza livello.

---

## 31. Erba, giardini e lotti GPU: rebase GPS e toggle dei mondi piatti (26/11/2026)

### 31.1 Richiesta e diagnosi

L'utente ha segnalato che erba, giardini e dettagli naturali «non funzionano
ancora». I quattro toggle del pannello «Dettagli vegetazione» e il bridge
Android risultavano corretti, quindi il difetto era nel **disegno a runtime**.
La revisione statica della catena `ChunkBuilder → NatureDetailPlacer →
NatureInstancedBatch` ha individuato tre cause concrete:

1. **Matrici GPU modificate in place.** `NatureInstancedBatch` salvava le
   matrici locali in `_blocks` e many le sostituiva con `rootMatrix * local`.
   Al primo rebase GPS le matrici erano già world: venivano moltiplicate una
   seconda volta per la nuova radice e i ciuffi sparivano dal chunk.
2. **`hasChanged` condiviso.** tutti i lotti di un gruppo (erba, fiori, sassi)
   leggono lo stesso `Transform.hasChanged`; il primo batch lo azzerava, quindi
   gli altri non ricevevano più il segnale di rebase e restavano fermi alle
   coordinate vecchie.
3. **Mondi piatti incompleti.** `BuildFlat` (Città Gioco e CityOSMWorld) creava
   i gruppi `AlberiParco` e `LuciNature`, ma non generava alberi né luci: le
   voci «Alberi nei parchi» e «Luci dei parchi» non avevano effetto.

Due difetti minori concorrevano: le porte venivano confrontate in coordinate
**mondo** contro l'erba in coordinate **locali** (la distanza di 4 m dalle
porte non era affidabile fuori dall'origine), e l'indice di riga di
`ScatterPark` non si azzerava a ogni colonna, saltando righe del prato.

### 31.2 Fix additivi

| File | Modifica |
|---|---|
| `Environment/NatureDetailPlacer.cs` | `_localBlocks` immutabili + `_worldBlocks` ricalcolati da `root * local`; `SameMatrix` confronta la radice per ogni batch, `hasChanged` resta solo un indizio e viene azzerato dopo l'uso. Nessuna matrice viene più riscritta in place: ogni rebase riparte dal valore locale originale |
| `Environment/NatureDetailPlacer.cs` | `CollectDoors` converte le porte con `InverseTransformPoint`; l'indice `iz` riparte da ogni colonna; `BuildFlat` genera anche alberi e una lampada con la stessa logica dei chunk, senza collider |
| `Diagnostics/VegetationFeatureBridge.cs` | Nuova costante `GardenPlace = "città→giardino→cespugli"` per i giardini OSM (`leisure=garden`) |
| `backend/test_unity_vegetation_features.py` | 2 nuove guardie: rebase/distanza porte e quattro toggle nei mondi piatti (11 test nel file) |
| `backend/preprocessing/cscheck/UnityStubs.cs` | Indexer `Matrix4x4` nel solo checker, come gli altri stub già presenti |

Nessun sistema, prefab, toggle o evento esistente è stato rimosso o
disattivato. La sezione «Natura» storica di `VegetationPlacer` resta intatta.

### 31.3 Riflesso della caccia alle uova

Il gioco è una caccia alle uova: l'uovo resta nascosto **tra cespugli, sassi e
fiori dei prati e dei giardini**, non accanto al player. Ora l'uovo del parco
viene generato se è attivo almeno uno fra prati, cespugli o alberi (prima
bastavano i soli prati), e i giardini OSM usano il luogo testuale dedicato
`città→giardino→cespugli`. Posizione e coordinate GPS continuano a viaggiare
via `Game.GrantVegetationEgg → FeatureEggHidden → EggCapturedInCity`, con la
solita dedup per chiave; Android riceve anche `VegetationFeatureChanged` con
`lat`/`lng` e timestamp, e rimanda lo stato in `setMode` come
`vegetationFeatures=id=1,id=0`. Nessuna seconda economia: nessun premio in valuta, solo il riflesso uova.

### 31.4 Verifiche

- `python3 backend/test_unity_vegetation_features.py`: **11 test OK**.
- `python3 -m unittest discover -p 'test_*.py'` in `backend/`: **333 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori,
  215 warning preesistenti. `git diff --check` pulito.
- `./gradlew :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --offline -q`:
  **BUILD SUCCESSFUL**.
- Build release: AAR Unity ricreato in **4m 42s** (prima passata,
  `SKIP_UNITY_AAR=0`) e APK assemblato/firmato in **1m 33s** (seconda passata,
  `SKIP_UNITY_AAR=1`). `BUILD SUCCESSFUL` in entrambi i passi; il primo
  `build_release.sh` era stato interrotto dal timeout durante il packaging,
  non da un errore di compilazione. APK firmato v3 (1 signer, verifica
  `apksigner verify`), SHA-256
  `2179de9614bcbe37a525b68fbfe100d5e26cc8fc5ebf43a2df6cbcd7d8485543`.
- Nel `global-metadata.dat` dell'AAR sono presenti `GardenPlace`, `SameMatrix`
  e `_localBlocks`: il fix è entrato davvero nell'artefatto. Il churn della
  build (`link.xml(.meta)`, `PlayerLocomotion.controller`,
  `PlayerHero.prefab`, `.meta` generati) è stato ripristinato.
- QA su device/emulatore: **non eseguito**. `adb devices` riporta
  `emulator-5554 offline` e il tentativo precedente con `huntix_field` era
  terminato con `Segmentation fault` prima del boot. Restano da verificare su
  dispositivo la persistenza visiva dei ciuffi dopo uno spostamento GPS e
  l'accensione di alberi/luci in Città Gioco e nel mondo legacy.

## 32. Aree globali e MiCittà parte del tutto (26/11/2026)

### 32.1 Richiesta e diagnosi

L'utente ha chiesto **Area Inventario**, **menu Inventario**, **Area Abilità** e
menu relativo raggiungibili da tutta l'app, «non solo da MiCittà ma anche e
soprattutto dalla home». Inoltre tutta l'attività di MiCittà — abilità,
inventario, scoperte, acquisti, vendite — deve essere visibile e conosciuta nel
resto del gioco: MiCittà non è un universo a sé, ma una parte del tutto.

Il problema non era la mancanza delle schermate (`EggCollectionActivity` e
`AbilityActivity` esistevano già), ma la **visibilità e la connessione**: le due
aree si aprivano solo da percorsi interni e gli eventi di MiCittà restavano nel
processo Unity, senza confluire nel profilo Android e nel diario posizionale
usato dal resto dell'app.

### 32.2 Principio aggiunto al Mandato

`HUNTIX_MANDATO.md` ora contiene il §13 **MiCittà è una parte del tutto**, con
cinque regole obbligatorie: aree globali raggiungibili da Home, menu `Altro`,
partita AR e menu Unity; stato condiviso non duplicato; memoria del «dove» in
un solo diario; riflesso uova delle aperture e delle azioni; conservazione
integrale dei sistemi esistenti (§7 e §11). Criterio di verifica: se un evento
di MiCittà non è visibile dalla Home o dal profilo Android, il lavoro non è
finito.

### 32.3 Fix additivi

| File | Modifica |
|---|---|
| `managers/GlobalAreas.kt` | Nuovo unico punto di ingresso: apre le due Activity esistenti, contatori `inv/skill/disc/buy/sell`, ultimo luogo/azione, `snapshot`/`summary`, import/export flag, scrittura in `EggWhereLog` |
| `HomeActivity.kt` | Due card evidenti «AREA INVENTARIO» e «AREA ABILITÀ» con riepilogo condiviso |
| `BaseNavActivity.kt` | Voci appendute 8 e 9 nel menu `Altro`: indici storici 0..7 invariati |
| `bridge/Bridge.kt`, `UnityIpc.kt` | Nuovi eventi `AreaInventarioOpen`, `AreaAbilitaOpen`, `CityGlobalActivity`, router e whitelist APP |
| `bridge/BridgeActivity.java` | `setMode` rimanda `globalAreas` come ripristino passivo |
| `bridge/CityStateSync.kt` | Lo snapshot importa i flag globali prendendo il massimo mai visto |
| `EggCollectionActivity.kt`, `ui/AbilityActivity.kt`, `ui/PlayerProfileTabs.kt`, `MainActivityUi.kt` | Riepiloghi e pulsanti nelle due aree, nel profilo e nel menu in-game AR |
| `Diagnostics/CityGlobalAreasBridge.cs` | Nuovo ponte Unity→Android con coordinate, luogo, timestamp, contatori, restore e uovo-indizio una tantum |
| `UI/HamburgerMenu.cs` | Due righe additive in `EggPanel` (`LegacyIndex -1`); altezza 410→590 px, menu 29→31 voci |
| `Scripts/Core/GameManager.cs`, `Sync/CityWorldSync.cs` | Restore da `setMode` e snapshot `globalAreas` |
| `UI/UIManager.cs`, `Vehicle/VehicleInteract.cs`, `Economy/EggDex.cs` | Acquisti, vendite e scoperte segnalati al mondo Huntix |
| `backend/test_unity_global_areas.py` | 11 guardie statiche per Mandato, ingressi Android/Unity, bridge bidirezionale e riflesso uova |

Nessun menu, evento, indice, preferenza o sistema preesistente è stato rimosso:
il vecchio `InventoryViewRequest` e la collezione Android restano intatti e
vengono riutilizzati dalle nuove voci.

### 32.4 Riflesso della caccia alle uova

L'apertura delle aree e ogni azione di MiCittà passano dallo stesso canale delle
uova posizionali: `CityGlobalAreasBridge` invia `area/action/detail/place/lat/
lng/tsMt`, Android aggiorna i contatori globali e scrive la voce in
`EggWhereLog` con `kind=evento`. Unity lascia **un solo uovo-indizio per coppia
area→azione** via `FeatureEggHidden → GrantAuditEgg`; non c'è seconda economia,
non c'è premio in valuta e un ripristino passivo da `setMode` non genera uova.

### 32.5 Verifiche

- `python3 backend/test_unity_global_areas.py`: **11 test OK**.
- `python3 -m unittest discover -p 'test_*.py'` in `backend/`: **344 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori.
- `./gradlew :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --offline -q`:
  **BUILD SUCCESSFUL** (solo note di API deprecate preesistenti).
- `git diff --check`: pulito.

## 33. Due bug dal log dell'APK: ricerca non scrivibile e gambe piegate all'avvio (26/11/2026)

### 33.1 Segnalazione e analisi del log

Dopo l'APK con le aree globali l'utente ha segnalato: «se vado in cerca luogo,
non riesco a scrivere» e «quando si avvia il gioco il personaggio piega le gambe
fino al bacino per 2 o 3 volte prima di stabilizzarsi». Il log allegato è
`docs/huntix_log_20261126_222417.txt` (sessione iniziata alle 22:16:57, APP
v1.16 build 26).

Per le gambe la catena è inequivocabile:

- `22:17:35.126` · `[CharacterWalker][GroundRay][L]` · `hits=8 bestY=0,98`
  `nY=0,97` `groundRef=0,00` `gap=0,98` `w=0,02`;
- `22:17:49.371` · `hits=5 bestY=0,99`;
- `22:18:15.900` · `hits=7 bestY=0,98`.

Negli stessi istanti `[CharacterWalker][Ground][IK]` riporta `footLift` pari a
`0,92`, `1,01` e `1,10` m, mentre il valore stabile è `0,08..0,21` m. Tre picchi
isolati, esattamente il numero di «piegate» descritte. La quota 0,98 m con
normale quasi verticale è il tetto di un'auto parcheggiata: il ray IK partiva da
`groundRef + 1,1` e, con `groundMask = ~0` (tutti i layer), colpiva l'auto prima
dell'asfalto, alzando la caviglia IK fino a poco sotto il bacino.

Per la ricerca non c'è una riga di errore nel log: il percorso è la UI runtime
della mappa (`MapSelectUI`, voce «🔍 Cerca destinazione» e chip Android
`CERCA DESTINAZIONE`) e la barra «Cerca un locale…» dell'Esplora. Entrambi gli
`InputField` venivano creati a runtime senza impostare `shouldHideMobileInput`,
che in uGUI 1.0 vale **false**: su Android la tastiera nativa diventa l'editor e
il testo digitato non appare nel campo disegnato da Unity. Il campo sembra quindi
«non scrivibile» anche se l'IME si apre. Ho verificato il sorgente uGUI della
Unity 2022.3.52f1 installata (`InputField.ActivateInputField` non fa early-return
se il campo è già selezionato), quindi l'ordine di `Select`/`Activate` non era
la causa: ho mantenuto l'attivazione esplicita come rete di sicurezza e corretto
il flag mobile.

### 33.2 Fix additivi

| File | Modifica |
|---|---|
| `Player/CharacterWalker.cs` | Il ray IK interseca `groundMask` con `GroundSnapper.WalkableMask` e scarta ogni hit più alto di `groundRef + ShortMaxRise` (0,35 m): tetti di auto, marciapiedi alti e impalcati non diventano target della caviglia. `using City.OSM;` aggiunto |
| `OSM/MapSelectUI.cs` | `shouldHideMobileInput = true` sui campi della mappa e della ricerca a schermo intero; `EnsureMapEventSystem()` riutilizza l'EventSystem esistente e aggiunge un modulo Legacy solo se manca; `FocusSearch()` chiama `ActivateInputField()`, seleziona il campo e logs `[MapSelect][Focus]` |
| `Scripts/UI/ExploreUIController.cs` | `shouldHideMobileInput = true` anche nel campo «Cerca un locale…» dell'outdoor |
| `backend/preprocessing/cscheck/UnityStubs.cs` | Stub `BaseInputModule` e `InputField.shouldHideMobileInput` per il checker C# |
| `backend/test_unity_menu_search_storage.py` | Guardie sui due flag mobili, sull'ordine attivazione/selezione, sull'EventSystem e sul campo Esplora |
| `backend/test_unity_nan_guards.py` | Guardia statica sul filtro della mask e del dislivello nel ray IK |

Non è stato rimosso nessun sistema: il `groundMask` pubblico resta configurabile,
la `WalkableMask` è già la regola del `GroundSnapper`, e l'IK continua a usare la
superficie reale sotto ogni piede. Nessuna nuova superficie o luogo è stata
aggiunta, quindi non serve un nuovo riflesso uova.

### 33.3 Verifiche

- `python3 backend/test_unity_menu_search_storage.py`: **16 test OK**.
- `python3 backend/test_unity_nan_guards.py`: **10 test OK**.
- `python3 -m unittest discover -s backend -p 'test_*.py'`: **345 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori.
- `git diff --check`: pulito.
- APK ricompilato con AAR Unity aggiornato e firmato:
  `app/build/outputs/apk/release/app-release-signed.apk` (196 MB), SHA-256
  `635e0ff2b1e08cdb76289600b1df7f72eab0690d36fa5f3a9c5982386afbe327`.
  Firma verificata con `apksigner` (CN=Huntix).

### 33.4 Limiti di verifica

Il log disponibile termina prima di qualsiasi tap sulla ricerca: il fix della
tastiera è quindi basato sul comportamento noto di uGUI/Android, non su una
riga di errore. La correzione delle gambe è invece ricavata direttamente dai tre
picchi `bestY≈0,98` nel log. Il QA su device resta il passo finale per confermare
che la tastiera accetti i caratteri e che all'avvio non compaiano più le piegate.

## 34. Prima casa gratuita per tutti, successive a pagamento (26/11/2026)

### 34.1 Regola di prodotto

Avvicinandosi a una porta residenziale prima dell'aggiornamento compariva
`COMPRA CASA` e, senza 900 €, il giocatore restava senza casa. Ora la **prima
casa di ogni profilo è gratuita**: l'azione diventa `PRIMA CASA GRATUITA` e non
toca il portafoglio. Dopo quella casa - anche se venduta - le successive tornano
a costare 900 € come prima. La casa gratuita venduta **non rimborsa 540 €**,
perché non è mai stata pagata: nessuna valuta viene creata dal nulla.

Per i profili già esistenti che avevano comprato una casa a pagamento prima di
questa feature, la vendita segna il beneficio come usato: non ricevono una
seconda casa gratis. Chi invece non ha mai preso casa lo riceve comunque.

### 34.2 Stato una tantum nel ponte esistente

Il flag `freehome` non è una nuova economia né un nuovo salvataggio: è un nuovo
valore nei flag globali già usati da `GlobalAreas` (`inv/skill/disc/buy/sell`).

| Direzione | Canale | Contenuto |
|---|---|---|
| Unity → Android | `CityGlobalActivity` (già in whitelist) | `area=acquisti`, `action=prima casa gratis`, luogo `città→casa→prima casa gratis→<nome>`, lat/lng della porta, timestamp |
| Android → Unity | `globalAreas` in `setMode` e nello snapshot `CityStateSync` | `freehome=1` dopo il primo utilizzo, ripristino passivo con presa del massimo |
| Unity → Android | `FeatureEggHidden` | uovo-indizio una tantum `casa->prima-gratis`, piazzato sulla porta reale |
| Android → diario | `EggWhereLog.recordActivity` | voce evento con luogo, coordinate e timestamp |

Lo specchio Android è visibile nel riepilogo `GlobalAreas.summary`, quindi
nell'Area Inventario, nell'Area Abilità, nel profilo e nel menu in-game:
`prima casa gratis: disponibile/usata`. Nessun evento nuovo è stato aggiunto alla
whitelist `UnityIpc.APP_EVENTS`: il beneficio riutilizza `CityGlobalActivity`.

### 34.3 Modifiche ai file

| File | Modifica |
|---|---|
| `Environment/HomeSystem.cs` | `KeyFree`, `CurrentHomeIsFree`, `BuyLabel`; `Buy` salta `TrySpend` solo se il beneficio è disponibile e lo consuma; `Sell` azzera il rimborso per la casa gratuita, cancella `city_home_free` e segna il flag come usato per le case pagate pre-esistenti |
| `Diagnostics/CityGlobalAreasBridge.cs` | `KeyFirstHomeFree`, `FirstHomeFreeAvailable`, `RecordFirstHomeFree`, `MarkFirstHomeFreeUsed`; `Record` con coordinate reali; `MarkEggAt` piazza l'uovo nel punto dell'azione; `ExportFlags` include `freehome` |
| `Scripts/Game.cs` | `GrantAuditEggAt`: stesso canale `FeatureEggHidden`, ma con posizione esplicita invece del punto accanto al player |
| `UI/ContextActionController.cs` | Etichetta e voce menu lette da `HomeSystem.BuyLabel` |
| `Sync/CityWorldSync.cs` | `home` acquista un settimo campo `0/1` per il rimborso; il restore lo riapplica |
| `managers/GlobalAreas.kt` | `KEY_FREEHOME`, import/export flag, voce `primaCasaGratis` in `snapshot` e `summary` |
| `backend/test_unity_first_home_free.py` | 8 guardie statiche su prezzi, benefit una tantum, migrazione, bridge, uovo e specchio Android |

Nessun sistema preesistente è stato rimosso o disattivato: il costo 900 €,
il rimborso 60%, il garage, il rientro a casa e tutte le altre azioni restano
identici; cambia solo il primo acquisto e il suo rimborso.

### 34.4 Verifiche

- `python3 backend/test_unity_first_home_free.py`: **8 test OK**.
- `python3 backend/test_unity_global_areas.py`: **11 test OK**.
- `python3 -m unittest discover -s backend -p 'test_*.py'`: **353 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori.
- `./gradlew :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --offline -q`:
  **BUILD SUCCESSFUL** (solo note di API deprecate preesistenti).
- `git diff --check`: pulito.
- APK ricompilato con nuovo AAR Unity e firmato:
  `app/build/outputs/apk/release/app-release-signed.apk` (196 MB), SHA-256
  `8abd454f7abe909c16d121d63fb957af3fa849e10e8b3e5c53d1519ee7db0e65`.
  Firma verificata con `apksigner` (schema v3, 1 firmatario).

### 34.5 Limiti di verifica

Il flusso è coperto da guardie statiche e dalla compilazione: il QA su device
deve confermare il primo acquisto a 0 €, il secondo a 900 € e la voce
`prima casa gratis: usata` nelle aree globali dopo la vendita.

## 35. Casa vittoriana «TARDIS»: piccola fuori, enorme dentro (27/11/2026)

### 35.1 Richiesta e scelta di prodotto

Entrando in casa il giocatore vedeva ancora l'arredo in-place ordinario. La
richiesta era di una casa vittoriana con lampadari, salone e una dimensione
interna molto maggiore di quella esterna: «da fuori minuscola, da dentro
enorme, una specie di città».

La soluzione è **additiva**: il guscio OSM, l'interno universale, i prefab
Quaternius/SENDU e le case visitabili restano identici. Solo per gli edifici
`house`/`apartment` viene costruita, in coda a `BuildInterior`, una tasca figlia
del generatore 1000 m sotto il mondo: fuori la porta è sempre la stessa, dentro
si apre un salone di 164 × 114 m con soffitti alti, colonnato, tre lampadari,
biblioteca, sala da pranzo, vetrine, lampioni e una piazzetta con fontana che
sembra una città vittoriana in miniatura. La tasca è disattivata finché non si
entra, quindi non aggiunge costi di rendering alle città già popolose.

Dopo la prima versione in primitive, su richiesta dell'utente («importante è
che si tratta di una bella villa… un bel prefab completo») l'interno è stato
sostituito da una **villa prefabbricata completa** composta con modelli SENDU
già importati e licenziati. Nella seconda iterazione («lo spazio sta… occupiamolo
con una villa vera», «manca il soffitto», «mancano tutte le comodità di una
casa») la villa è diventata una **reggia di 164 × 114 m con pareti alte 12 m**
(circa sette volte il giocatore di 1,7 m) e soffitto a cassettoni che copre
l'intera superficie, non più solo il salone. Sono state aggiunte l'atrio
d'onore, la camera padronale, il bagno e spogliatoio, la cucina con dispensa,
la stanza dei bambini e lo studio privato, oltre ai complementi mancanti in
salone/biblioteca/pranzo/città interna (119 reference FBX SENDU di nove kit:
`Shell`, `Bedroom`, `Bathroom`, `Kids`, `Living`, `Office`, `Kitchen`, `Cafe`,
`Bakery`, `Decor`). Il baker editoriale `Assets/City/Editor/VillaPrefabBaker.cs`
crea il prefab in `Assets/Resources/Villas/VillaVittorianaCompleta.prefab`
(2,2 MB, 10 uova, 6 collider, 10 luci point, materiali URP dedicati); non fa
parte del player. A runtime il prefab viene caricato una sola volta per tasca;
se non è presente resta attivo il fallback primitivo, senza case vuote né uova
duplicate.

### 35.2 Riflesso della caccia alle uova e ponte bidirezionale

Come da Mandato §4, ogni superficie nuova è anche un nascondiglio: dieci
`HouseEggSpot` stabili — i cinque storici (cassetto del salone, scrivania della
biblioteca, credenza del pranzo, vetrina della città, vasca della fontana) e i
cinque delle comodità nuove (armadio in camera, lavandino in bagno, forno in
cucina, cassa dei giocattoli, scrivania dello studio). Le dodici zone (sei
storiche più atrio, camera, bagno, cucina, bambini e studio) registrano la
prima visita con luogo testuale e coordinate della porta. Le uova usano il
normale `EggController` (tipo `Edificio`), la
rarità calcolata da seed stabile e il riempimento per prossimità di
`ContainerEgg`: non esiste una seconda economia né un secondo inventario.

| Direzione | Canale | Contenuto |
|---|---|---|
| Unity → Android | `CityGlobalActivity` (già in whitelist) | `area=scoperte`, azione e luogo testuale `città→casa→<chiave>→<zona>`, lat/lng della porta di casa, timestamp |
| Unity → Android | `FeatureEggHidden` (già in whitelist) | uovo-indizio una tantum per ogni zona, emesso dal percorso canonico `GrantAuditEgg` |
| Unity → Android | `HouseVisitChanged` / `foundContainerEggs` | chiavi di zone e uova già viste, nello snapshot `CityWorldSync` |
| Android → Unity | `houseVisitState` e flag globali | dedup non distruttiva: dopo rebuild dei chunk o restore le uova non rinascono |

Nessun nuovo evento bridge è stato aggiunto: la tasca riutilizza solo eventi già
presenti in `UnityIpc.APP_EVENTS`.

### 35.3 Modifiche ai file

| File | Modifica |
|---|---|
| `Interior/Victorian/VictorianGrandInterior.cs` | Nuovo builder additivo: `VictorianPocket`, `VictorianZone`, `VictorianExit`, ingresso/uscita con `InteriorTeleport`, camera indoor dedicata, ticker zone, dieci uova e dodici zone con chiavi stabili. Secondo passo: la tasca è agganciata al generatore (non al `Floor_0` disattivato) e l'ingresso riattiva gli antenati |
| `Interior/Victorian.meta`, `VictorianGrandInterior.cs.meta` | GUID Unity delle nuove asset |
| `Interior/InteriorGenerator.cs` | Chiamata in coda a `VisitInteriorDecorator.Decorate`, limitata a casa/appartamento e idempotente |
| `Interior/InteriorManager.cs` | Ingresso all'attraversamento della porta, uscita dal trigger e dal pulsante `ESCI`, sospensione del controllo geometrico dentro la tasca, `ActiveInteriorRoot` puntato alla tasca per le uova |
| `City/Editor/VillaPrefabBaker.cs` | Baker solo editor: compone la reggia con 119 modelli SENDU, materiali URP, 6 collider, 10 luci, 10 contenitori uova e salva il prefab in `Resources` |
| `Resources/Villas/VillaVittorianaCompleta.prefab` (+ `.meta`, materiali) | Reggia spedita nell'APK: guscio, nove ambienti, pavimenti e soffitto continui, arredi, illuminazione, porta e nascondigli; 2,2 MB |
| `VictorianGrandInterior.cs` (secondo passo) | `BuildVilla` carica il prefab e mantiene `BuildFallbackArchitecture`; `FinalizePocket` aggiunge a runtime il `UniversalContainerMarker` che Unity non serializza nei prefab, le 12 zone, le etichette e il trigger d'uscita (senza duplicare la porta del prefab) |
| `backend/test_unity_victorian_interior.py` | 13 guardie statiche su dimensioni, arredi, innesto additivo, ingresso/uscita, uova con luogo, specchio Android, prefab SENDU, marker runtime e tasca non sepolta dall'interno disattivato |
| `Assets/ThirdPartyLicenses.md` | Attribuzione/credito SENDU per la villa prefabbricata (licenza gratuita non CC0, credito non obbligatorio ma riportato) |

Nessun builder, collider o sistema esistente è stato rimosso o disattivato.
L'unico comportamento nuovo è che varcare la porta di una casa/apartamento
apre anche la tasca; l'uscita riporta sempre davanti alla porta OSM reale.

### 35.4 Verifiche

- `python3 backend/test_unity_victorian_interior.py`: **13 test OK** (le 7
  guardie della tasca più 6 sul prefab: presenza/caricamento, collider+luci+10
  uova, reggia con soffitto intero e stanze nuove, marker runtime,
  mutual esclusività prefab/fallback e dedup delle uova).
- `python3 -m unittest discover -s backend -p 'test_*.py'`: **366 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori, 214 warning preesistenti.
- Baker eseguito con Unity 2022.3.52f1 in batchmode: `EXIT=0`, log
  `[VillaPrefabBaker] prefab completo creato`, nessun modello mancante.
- `./gradlew :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --offline -q`: OK.
- Build firmata finale: `SKIP_MAP_UPDATE=1 SKIP_SRTM_DOWNLOAD=1 AUTH_MODE=api
  SKIP_INSTALL=1 ./build_release.sh` → **BUILD SUCCESSFUL**, AAR Unity ricreato
  con il prefab, APK `app/build/outputs/apk/release/app-release-signed.apk`;
  SHA-256 `a5f22dbe5af9c48985619a5d82e20b05d417c59e1fd2dbab3fce00204d1da604`.
  Firma verificata con `apksigner` (schema v3, 1 firmatario, certificato Huntix).
  Il nome del prefab è presente nei dati e nei metadati IL2CPP dell'AAR spedito.
- `git diff --check`: pulito su sorgenti, test e documentazione; i file YAML
  generati da Unity (prefab, `.mat`, `.meta`) contengono i consueti spazi finali
  serializzati dall'editor. Il churn noto di `link.xml.meta`,
  `PlayerLocomotion.controller` e `PlayerHero.prefab` è stato ripristinato e non
  viene committato.

### 35.5 Limiti di verifica

Le guardie statiche e la compilazione coprono struttura, ponte e dedup, ma non
sostituiscono il QA su device: resta da confermare che l'ingresso non faccia
sbucare la camera dal salone (ora arredato con la villa SENDU), che il trigger
di uscita riporti davanti alla porta e che le dieci uova del prefab compaiano
nel diario «DOVE HO TROVATO» con le coordinate della casa. La resa estetica
della villa (arredi, luci, fontana, città interna) va giudicata su device: le
guardie statiche ne verificano la presenza, non la bellezza percepita.

## 36. Erba davvero visibile e emulatore offline (27/11/2026)

### 36.1 Richiesta e diagnosi

L'utente ha chiesto: «appena finisce apk controlla perché dispositivo emulato
è offline. Inoltre l'erba non è ancora presente. La vegetazione è completamente
assente».

Il dispositivo emulato non era guasto: `adb devices` riportava
`emulator-5554 offline` perché la porta ADB 5555 era già occupata dal processo
`node /opt/mevapool/init.js` (pid 1353). L'emulatore con console 5554 tentava
quindi di usare 5555 e restava offline. Il vecchio emulatore è stato fermato e
riavviato su `-port 5580`; `emulator-5580` risulta ora `device` e l'APK firmato
è stato installato con successo.

Il QA nell'APK ha poi mostrato la causa dell'erba mancante nella Città Gioco:

```
[Dettagli] mondo piatto: erba/fiori=6 cespugli/rocce=1 alberi=1 lotti=5
```

Sei ciuffi in un quartiere di circa 1 km². `FictionalCityWorld` passava a
`NatureDetailPlacer.BuildFlat` solo i centri dei parchi e la ricetta di Foggia
ne genera uno solo. Nei chunk OSM il passo dei prati era di 11 m con tetto a
420 istanze e le aiuole stradali erano campionate ogni 46 m: erba tecnicamente
presente ma invisibile da altezza d'uomo. Nel mondo AR i ciuffi erano limitati
a 2-8 per parco. C'era anche un difetto di culling nei mondi piatti: il batch
aveva come unico anchor il centro del quartiere, quindi con distanza 320 m
l'erba sotto i piedi ai bordi veniva saltata.

### 36.2 Fix additivi

| File | Modifica |
|---|---|
| `Environment/NatureDetailPlacer.cs` | `BuildFlat` accetta raggio, numero minimo/massimo di ciuffi per punto e modalità `vergeMode` (solo erba/fiori, niente alberi o luci sull'asfalto); i chiamanti storici restano invariati per i valori predefiniti |
| `Environment/NatureDetailPlacer.cs` | Prati OSM a passo 9 m con tetto a 900 istanze per chunk, aiuole ogni 24 m con 3-4 ciuffi, cespugli ogni 20 m, fino a 16 alberi di parco per chunk |
| `Environment/NatureDetailPlacer.cs` | I tre lotti dei mondi piatti non usano più il culling dal centro: restano disegnati e la visibilità è gestita dai quattro toggle e dal LOD |
| `Game/FictionalCityWorld.cs` | Nuovi punti d'ancora sui due lati di tutte le strade, ordinati a stride così il budget copre uniformemente il quartiere; 900 ciuffi nei parchi e 2600 sui bordi stradali |
| `reallife/OsmCityBuilder.kt` | Ciuffi d'erba nei parchi AR da 2-8 a 6-24, sempre dentro il budget `MAX_NODES` e senza collider |

Nessun sistema, toggle, evento o prefab esistente è stato rimosso o
disattivato. La sezione «Natura» storica, i prefab Kenney e il riflesso uova
restano identici.

### 36.3 Riflesso della caccia alle uova e ponte bidirezionale

L'uovo del verde continua a essere piazzato una sola volta da
`VegetationFeatureBridge.GrantParkEgg`, con luogo testuale
`città→città-gioco→parco→vegetazione` e coordinate reali del parco, tramite
`FeatureEggHidden` e il diario Android. La nuova erba lungo le strade non crea
uova duplicate né seconde economie: è solo superficie visitabile già coperta
dallo stesso indizio. I quattro toggle `huntix_veg_*` e il ripristino da
`vegetationFeatures` restano invariati.

### 36.4 Verifiche

- `python3 backend/test_unity_vegetation_features.py`: **12 test OK** (una
  nuova guardia su densità, bordi stradali e assenza di culling nel mondo
  piatto).
- `python3 backend/test_unity_victorian_interior.py`: **13 test OK**.
- `python3 -m unittest discover -s backend -p 'test_*.py'`: **367 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori,
  214 warning preesistenti.
- `./gradlew :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --offline -q`: **BUILD SUCCESSFUL**.
- `SKIP_MAP_UPDATE=1 SKIP_SRTM_DOWNLOAD=1 AUTH_MODE=api SKIP_INSTALL=1 ./build_release.sh`:
  **BUILD SUCCESSFUL** (7 min 30 s), APK firmato e verificato, SHA-256
  `525fe0803e140f73058ee9c21cd66cfbbe6d71b582234b4fe7b5cb44f421123b`.

### 36.5 QA su emulatore

Emulatore `emulator-5580` (il vecchio `emulator-5554` resta offline per il
conflitto ADB descritto in §36.1). APK firmato installato, `world_mode=fictional`
impostato nelle preferenze Android e MiCittà avviata con spawn su Bari
(41.1171 / 16.8719). Il log conferma il passaggio da 6 ciuffi a due lotti
distinti, uno per i parchi e uno per i marciapiedi:

```
[Dettagli] mondo piatto: erba/fiori=11 cespugli/rocce=1 alberi=1 lotti=7
[Dettagli] mondo piatto: erba/fiori=2600 cespugli/rocce=0 alberi=0 lotti=11
[HuntixSync] Uovo nascosto: Uovo tra cespugli e fiori
  (vegetazione->parco->fioriture) place=città→città-gioco→parco→vegetazione
  lat=41.113083 lng=16.866607
[CittaGioco] costruita: seed=3880082521 passo=177.6 strada=6.0 isolati=25
  parchi=1 edifici=192 poi=10 (nessun tile/DEM a runtime)
[CityChunkedWorld] === MIACITTA === CityReady (chunk pronti, splash chiusa)
```

Nessuna eccezione, `FATAL EXCEPTION`, `ANR` o errore della sezione dettagli
naturali nel log. Il conteggio del lotto stradale è al tetto (2600) e quello del parco è
salito a 11: l'erba non è più una manciata di ciuffi invisibili. L'uovo del
verde è stato nascosto una sola volta con luogo testuale e coordinate reali,
a conferma che il riflesso uova e il bridge bidirezionale non sono cambiati.
Il QA visivo sul dispositivo resta limitato all'emulazione software
(swiftshader, 6-7 fps anche prima del fix): la copertura dell'erba è misurabile
nei lotti ma la resa estetica va giudicata su hardware reale.
