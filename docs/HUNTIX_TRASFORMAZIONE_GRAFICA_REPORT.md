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
