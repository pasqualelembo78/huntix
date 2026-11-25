# Report — Case visitabili collegate a edifici OSM reali (25/11/2026)

Obiettivo: almeno **una casa realmente visitabile** in Huntix, associata a un
edificio OSM reale, con ingresso/uscita, interni arredati con asset gratuiti
verificati, collisioni, navigazione e uova nascoste, più un sistema riutilizzabile.

Branch `integrazione-open-source`; Unity **2022.3.52f1**, URP **14.0.11**.
Nessun aggiornamento di Unity, nessuna modifica al wire geo/graph,
`TileClient.CacheVersion` resta **5**.

---

## 1. Asset trovati

Sono state verificate le quattro fonti richieste e alcune alternative:

| # | Asset | Fonte | Esito |
|---|---|---|---|
| A | DOLLHOUSE - FREE - Modular Low Poly Interiors | Unity Asset Store (FForgeWorks) | trovato, **non scaricato** |
| B | House Interior - Free | Unity Asset Store (nappin) | trovato, **non scaricato** |
| C | Cozy Interiors + Builder: 339 Models (URP) | SENDU, itch.io | **FBX+PNG importati**; kit Unity escluso |
| D | Cozy Home Interiors | 3dassets.dev | **scaricato e usato** |
| D1 | Bedroom and Living Room Furniture | 3dassets.dev | scaricato, non usato |
| D2 | Fitted Kitchen and Bathroom Builder | 3dassets.dev | scaricato, non usato |
| E | Kenney Furniture Kit 2.0 | kenney.nl | scaricato, non usato |
| — | Quaternius Ultimate House Interior, KayKit Furniture Bits, Poly Pizza, OpenGameArt | varie | scartati (vedi punto 3) |

Dettagli con URL, licenze e date in `/tmp/opencode/research/interiors/RISULTATI.md`
(ricerca isolata in `/tmp`, prima di qualsiasi modifica al repository).

## 2. Asset scaricati e importati

- Pack **D — Cozy Home Interiors**: 129 GLB, 3.616.296 byte, scaricato via API
  `https://3dassets.dev/api/v1/packs/cozy-home-interiors`.
- **43 modelli** selezionati e convertiti in FBX in
  `unity-project/Assets/Resources/HouseInteriors/CozyHome/` (pareti, porte,
  finestre, pavimenti, soffitti, scale, divano, poltrona, tavoli, letto,
  armadio, frigo, forno, lavello, vasca, doccia, WC, lampade, tende, piante).
- Ogni FBX ha il proprio `.meta`; **nessuna texture PNG** è stata importata:
  i GLB contengono solo colori PBR di base, ricreati con materiali URP/Lit.
- Conversione ripetibile con `scripts/house_assets_import.py`: i GLB usano
  `KHR_mesh_quantization`, che assimp interpreta male (vertici/bounds enormi);
  lo script dequantizza i vertici (SHORT normalizzato /32767), applica le
  matrici dei nodi glTF, scrive OBJ+MTL e poi converte con assimp 5.2.

### Contestazione sull'APK e import del kit completo (25/11/2026)

L'utente ha contestato — correttamente — che l'APK fosse cresciuto di soli
**327.720 byte** (201.473.302 → 201.801.022 byte) dopo il primo import: i 43 FBX
CC0 pesano 3,50 MiB ma **non importano texture**, quindi una volta compressi da
Unity e condivisi a runtime l'impatto sul pacchetto è minore del previsto. Il
sospetto era fondato: i progetti completi con materiali, atlas e prefab non
erano entrati.

Dopo la scelta esplicita dell'utente (**"Importa kit SENDU FBX+PNG"**) il kit
**C** è stato importato davvero:

- ZIP sorgente `cozy_interiors_models_fbx_glb.zip` (8.850.757 byte), scaricato
  legalmente senza account né pagamento da https://sendudev.itch.io/cozy-interiors.
- **339 FBX** engine-agnostic in
  `unity-project/Assets/Resources/HouseInteriors/Sendu/FBX/<Stanza>/` (10 stanze:
  Shell, Bedroom, Kitchen, Living, Bakery, Cafe, Office, Bathroom, Kids, Decor).
- **20 PNG** (4 palette `CI_Theme_*.png` + 16 pattern `CI_Pattern_*.png`) in
  `.../Palettes/` e `.../Patterns/`; la cartella completa pesa **~13 MiB**.
- `README_models.txt` originale conservato accanto agli asset come prova della
  licenza. Ogni file ha il proprio `.meta`.
- Import ripetibile con `scripts/sendu_assets_import.py` (riusa il template
  `ModelImporter` di `house_assets_import.py`; per le palette imposta filtro
  Point, niente mipmap, `alphaIsTransparency: 0` perché l'alpha è smoothness).
- Il kit non è solo "sul disco": **32 modelli reali** sono istanziati a runtime
  nella casa (verificato che i 32 file esistano, non solo che il codice compili).
- **Esclusi** solo shader, script, prefab, layout e scene del kit Unity:
  richiedono Unity 6000.6+ / URP 17.6+, incompatibili con 2022.3 / URP 14.0.11.
  I materiali sono ricreati con URP/Lit e la palette 8x8 celle da 32 px.

## 3. Asset scartati (e perché)

- **A DOLLHOUSE** e **B House Interior**: download solo tramite account Unity
  Asset Store, nessun URL anonimo. **Non scaricati**, nessun tentativo di bypass.
  A dichiara Unity 6000.4.2 (> 2022.3); B dichiara 2021.3.45 ma non è verificabile
  senza il file.
- **Solo il kit Unity di C SENDU**: i suoi shader custom (`CozyInteriors/Soft`,
  `/Emissive`, `/Glass`, `/Grid`), script, prefab, layout e scene richiedono
  **Unity 6000.6+ / URP 17.6+**, incompatibili con la 2022.3, quindi **non
  importati**. I FBX engine-agnostic e le PNG, invece, **sono importati** (punto 2).
  La licenza è gratuita commerciale **personalizzata, non CC0**: consente il
  shipping nel gioco finito ma vieta la rivendita/condivisione dei sorgenti.
- **Quaternius Ultimate House Interior**: download fallito per quota Google
  Drive esaurita. **KayKit Furniture Bits**: checkout con email e indirizzo di
  fatturazione obbligatori. **Poly Pizza / OpenGameArt**: licenze non
  verificabili in modo autorevole (404 / 403). Nessun download, nessun account,
  nessun pagamento, nessun asset piratato.

## 4. Licenze

- **CC0 1.0 Universal** per i 43 modelli D:
  https://creativecommons.org/publicdomain/zero/1.0/. Attribution non richiesta.
  Originale dichiarato: 3D Assets / 3dassets.dev, generato con Claude Fable 5.1.
- **Licenza commerciale gratuita non CC0** per il kit C SENDU: permette
  esplicitamente il shipping dentro un gioco/app finito, **non richiede
  attribuzione** e vieta solo la rivendita/condivisione dei sorgenti e l'uso
  come training data ML. Testo integrale in
  `unity-project/Assets/Resources/HouseInteriors/Sendu/README_models.txt`.
  In Huntix gli asset viaggiano solo dentro l'APK, mai come file separati.
- Assimp 5.2 usato solo come strumento locale di conversione, nessun suo codice
  copiato. Documentazione aggiornata in:

- `unity-project/Assets/ThirdPartyLicenses.md` (nuova sezione "Case visitabili");
- `THIRD_PARTY_NOTICES.md` (nuova sezione "Case visitabili OSM");
- questo report.

## 5. File creati

**Script Unity** in `unity-project/Assets/City/Scripts/Interior/Visit/`:

- `VisitInteriorRegistry.cs` — catalogo configurabile (id OSM → casa), con
  tipologie riusate da `UniversalInteriorRegistry.Kinds`;
- `HouseInteriorState.cs` — visite/uova persistenti, evento bridge ed export;
- `HouseSlot.cs` / `HouseSlot` — slot serializzati normalizzati sull'impronta;
- `HouseEggSpot.cs` — nascondiglio uovo con luogo testuale;
- `VisitInteriorDecorator.cs` — arredamento CC0, paratie, luci, collider, innesto SENDU;
- `SenduInteriorDecorator.cs` — 32 arredi SENDU con palette, LOD1 disattivato,
  zero collider e quarto nascondiglio;
- `VisitInteriorManager.cs` — hook ingresso/uscita, indizio italiano, dedup uova.

**Editor**: `unity-project/Assets/Editor/HousePrefabSetup.cs` (genera
`Resources/HouseInteriors/CasaVisitabileCC0.prefab`).

**Altro**: `scripts/house_assets_import.py`, `scripts/sendu_assets_import.py`,
`backend/test_unity_visit_houses.py`, tutti i `.meta` dei nuovi file, 43 FBX CC0,
339 FBX e 20 PNG SENDU.

## 6. File modificati (innesti additivi)

- `InteriorGenerator.cs`: chiamata a `VisitInteriorDecorator.Decorate(...)` a
  **fine** `BuildInterior`, dopo i tre builder esistenti; `SetOsmId`.
- `BuildingEntrance.cs`: campo `osmId`.
- `BuildingPlacer.cs`: id OSM sull'ingresso, nome casa, bypass budget 8 ingressi.
- `ChunkBuilder.cs`: priorità della casa configurata prima del taglio a 320
  edifici e della rimozione vicino ai POI.
- `InteriorManager.cs`: hook `OnEntered` / `OnExited` (due punti di uscita).
- `Game.cs`: prompt `ENTRA IN CASA`; le altre porte restano `NEGOZIO`/`APRI`.
- `ContainerEgg.cs`: uova esplicite sui tre `HouseEggSpot`, dedup all raccolta.
- `CityWorldSync.cs`: campo `houseVisitState` in export/import.
- `app/.../UnityIpc.kt`, `Bridge.kt`, `CityStateSync.kt`: evento e mirror Android.
- `unity-project/Assets/Editor/HuntixBuild.cs`: genera il prefab in build.

**Conservati**: `InteriorGenerator`, `ShopInteriorBuilder`,
`ShopPrefabInstaller`, `UniversalInteriorBuilder`, `UniversalInteriorState`,
prefab `NegozioProntoCC0`, budget 8 ingressi per chunk, `CacheVersion = 5`.
Nessun sistema funzionante sostituito o disattivato.

## 7. La casa

Prefab `CasaVisitabileCC0` con **4 stanze**: soggiorno (divano, poltrona,
tavolino, TV, libreria, tappeto, lampada, pianta), cucina (piano cottura, forno,
frigo, lavello, cassetti, tavolo da pranzo, isola), camera (letto matrimoniale,
comodini, armadio, cassettiera, tappeto), bagno (vasca, doccia, WC, lavandino,
portasciugamani). Pareti, porte, finestre, tende, pavimenti legno/piastrelle,
soffitti e illuminazione (lampade e stringhe luminose) sono modelli CC0 reali.

Sopra questo livello si AGGIUNGONO **32 arredi SENDU reali** texturizzati con la
palette `CI_Theme_Honey` (soggiorno, cucina, camera, bagno): sideboard, pouf,
tappeto rotondo, lampade, piante, cassetti da cucina, bollitore, specchio,
comodini, cesto, asciugamani e altri oggetti dei kit FBX. I FBX conservano i
figli `_LOD0`/`_LOD1`: il secondo è disattivato a runtime per non disegnare due
volte la stessa mesh. I materiali URP/Lit sono condivisi e creati a runtime.

Le paratie interne hanno **due varchi da ~2,2 m** (`gap = 1.1f` per lato) e i
mobili **non hanno collider**: la navigazione in terza persona usa solo guscio,
pavimento e paratie (layer 8 `BuildingLayer`, escluso da `GroundSnapper`, per
non interferire con lo snap del player). Luci e materiali URP/Lit condivisi.

## 8. Edificio OSM associato

- **id OSM**: `204023010` (`building=yes`), dichiarato solo in
  `VisitInteriorRegistry`; tutti gli altri edifici conservano il comportamento
  precedente.
- **centro**: `[41.90601, 12.49579]`; **impronta**: `[21.3, 13.5]` m;
  **rotazione**: 318°.
- **chunk** `(878, 619)`, **tile** `IT_087_061`, circa **360 m** dallo spawn Roma.
- Nessuna modifica a geo/graph o al formato tile: la casa è un livello sopra
  l'interno universale già generato dal sistema esistente.

## 9. Ingresso e uscita

Flusso invariato: trigger porta → tap su `ENTRA IN CASA` →
`BuildingEntrance.Interact()` → fade → `BuildInteriorNow()` →
`InteriorTeleport.Move(...)` (unico teletrasporto consentito, con guardie NaN)
→ `InteriorManager.MarkInside` → hook `VisitInteriorManager.OnEntered`.
Uscita: `ESCI` → `InteriorManager.ExitInterior()` → `ExitPrefabRoutine` →
`PrefabExitLocal`, ripristino fuori soglia senza sovrapposizioni. Il singolo id
configurato non consuma il budget di 8 porte per chunk e non viene tagliato dal
limite 320 edifici, senza togliere porte agli altri edifici.

## 10. Riflesso della caccia alle uova (Mandato)

**Quattro** nascondigli reali, con posizione testuale stabile: tre sui
contenitori CC0 e il quarto sul cassetto SENDU della cucina:

| feature id | luogo |
|---|---|
| `casa-cucina-cassetto2` | `casa→cucina→cassetto2` |
| `casa-camera-armadio` | `casa→camera→armadio` |
| `casa-bagno-lavandino` | `casa→bagno→lavandino` |
| `casa-cucina-cassetto-sendu` | `casa→cucina→cassetto-sendu` |

Le uova sono create da `ContainerEgg` sui `HouseEggSpot`, tipo `EggType.Edificio`,
rivelate per prossimità e raccolte con il normale mini-gioco `EggController`
(ricompensa, Bestiario e profilo). `EggCapturedInCity` porta lat/lng e
`containerEggKey` stabile. `HouseInteriorState` traccia solo le uova **raccolte**
(mai quelle nascoste), così un rebuild o un restore non impedisce la raccolta.

## 11. Ponte Android ↔ Unity

- Nuovo evento whitelistato `HouseVisitChanged` in `UnityIpc.APP_EVENTS`;
  payload `{key, osmId, kind, label, visits, lastTs}`.
- Router `Bridge.kt` → `CityStateSync.onHouseVisit()`; mirror Salvato in
  `SharedPreferences` chiave `city_house_visits_v1`.
- Snapshot `CityWorldSync.houseVisitState` con `{houses:[...],eggs:[...]}`,
  unito in Android con merge **non distruttivo** (conteggio/timestamp più
  recenti vincono; le uova raccolte non rinascono).
- Report Android: `houseVisits`, `houseEggs`, `houseVisitSnapshot`.
- Stesso snapshot bidirezionale già usato dai sistemi esistenti: nessun canale
  di salvataggio parallelo.

## 12. Sistema riutilizzabile ed estensibile

Aggiungere una casa, un bar, un ristorante, un ufficio, un hotel, una palestra o
una concessionaria richiede **una sola `Spec`** in `VisitInteriorRegistry`,
senza toccare geo/graph, player o builder. Le categorie riusano
`UniversalInteriorRegistry.Kinds`. Il decorator carica il prefab da `Resources`,
quindi non serve rete a runtime.

## 13. Backup e sicurezza

Prima di ogni modifica è stato creato il tag **`pre-visible-houses`** su HEAD
`9874d15b`. Nessun push. Le 6 cancellazioni preesistenti di `docs/huntix_log_*`
e i 2 log untracked sono stati lasciati intatti e **non** inclusi nel commit.

## 14. Errori riscontrati e corretti

- **Quantizzazione GLB**: assimp produceva bounds errati; risolto con
  dequantizzazione esplicita nello script di importazione.
- **Incompatibilità kit Unity SENDU**: shader/script/prefab esclusi per
  Unity/URP troppo nuovi, senza forzare l'import. I FBX/PNG engine-agnostic
  invece sono stati importati e usati con materiali URP/Lit ricreati in codice.
- **APK cresciuto troppo poco dopo il primo import**: non era un errore di
  build ma una scelta incompleta di asset (43 FBX senza texture); corretta
  importando il kit SENDU completo e documentando i numeri reali nel punto 2.
- **Conteggio slot SENDU**: il test ne attendeva 31, il decorator ne ha 32;
  verificato che tutti i 32 FBX esistono su disco e allineato test e docs.
- **Due asserzioni di test troppo rigide** (metodi su due righe): corrette per
  verificare la presenza del comportamento, non la formattazione del sorgente.
- Nessun errore di compilazione C# riportato da `cscheck`.

## 15. Test eseguiti e superati

- `python3 -m unittest discover -s backend -p 'test_unity_*.py'` →
  **Ran 218 tests — OK** (4 nuovi test SENDU in `test_unity_visit_houses.py`).
- `cd backend/preprocessing/cscheck && ./check.sh` → **CSCHECK OK**, solo
  warning preesistenti (196-198 a seconda della passata), nessun errore nuovo.
- Build release completa avviata con `SKIP_MAP_UPDATE=1 SKIP_INSTALL=1
  FORCE_UNITY_AAR=1 ./build_release.sh --auto`; esito in appendice.

I test statici verificano: 43 FBX CC0 con `.meta` e nessuna texture, **339 FBX e
20 PNG SENDU con `.meta`**, palette senza mipmap, 32 slot SENDU con i relativi
FBX realmente presenti su disco, licenza non CC0 documentata, id OSM e
etichetta, 4 stanze, **4 nascondigli**, decorator additivo e collider solo sulle
paratie, innesto dopo i builder esistenti, prompt e hook ingresso/uscita, dedup
uova, evento e snapshot bridge, prefab generato in build, conservazione dei
sistemi esistenti e `CacheVersion = 5`.

## 16. Test NON eseguibili in questo ambiente

- **Nessun device o emulatore Android locale**: non sono stati eseguiti test
  visivi, percezione audio, FPS/RAM reali né restore su device.
- **Nessuna sessione Unity Editor interattiva**: il posizionamento dei 43 slot
  CC0 e dei 32 slot SENDU è verificato staticamente (test e prefab), non a occhio
  in scena.
- Nessun test di build su dispositivo fisico o di prestazioni di rendering.
  Questi verdict non sono dichiarati come superati.

## 17. Android e performance

- 43 mesh FBX CC0 senza texture; in più 339 FBX e 20 PNG SENDU in `Resources`
  (di cui 32 modelli e una palette 256x256 usati nella casa), materiali URP/Lit
  condivisi e texture con mipmap disattivate. Per semplicità lampade/fuoco e
  vetro usano lo stesso materiale opaco della palette: non sono stati creati
  materiali emissivi/trasparenti dedicati.
- Nessun collider sui mobili: il costo fisico resta quello del guscio esistente.
- I `_LOD1` duplicati dei FBX SENDU vengono disattivati a runtime. Nessun test
  FPS/RAM su device è stato eseguito: il costo reale non è dichiarato.
- Arredamento costruito solo per l'edificio configurato, non per tutti; il
  decorator è un no-op sugli altri id OSM.
- Nessuna rete a runtime (`Resources.Load`), nessun Json.NET, nessun Guid.
- Lo stato viaggia nello snapshot esistente: nessun payload aggiuntivo per
  frame, solo un campo in più nel salvataggio.

## 18. Istruzioni di test (in italiano)

1. Avviare l'app e aspettare la generazione della città di Roma.
2. Andare vicino all'edificio con id OSM `204023010` (circa 360 m dallo spawn);
   comparirà il prompt **ENTRA IN CASA** invece di **APRI**.
3. Toccare **ENTRA IN CASA** e attendere il fade: si entra in soggiorno.
4. Camminare nei due varchi: cucina, camera e bagno sono navigabili; i mobili
   non bloccano il passaggio.
5. Avvicinarsi a cassetto della cucina, cassetto SENDU della cucina, armadio
   della camera e lavandino del bagno: appare un uovo; raccoglierlo con il
   normale mini-gioco.
6. Toccare **ESCI**: si torna fuori dalla porta, senza sovrapposizioni.
7. Riavviare l'app: visite e uova raccolte devono essere conservate nello
   snapshot Android; i nascondigli già svuotati non devono rinascere.
8. Verificare nel report bridge i campi `houseVisits`, `houseEggs` e
   `houseVisitSnapshot`.

## 19. Prossimi passi

- Verifica visiva su device Android reale (layout, luci, navigazione, audio).
- Misurazione FPS/RAM con `PerfProbe` confrontando casa dentro/fuori.
- Aggiungere una seconda `Spec` (per esempio un bar o una concessionaria) per
  validare l'extendibilità del registro senza nuove modifiche ai builder.
- Valutare LOD/occlusion culling per le case future se il numero di `Spec`
  dovesse crescere.
- Test di restore su device con cache fredda e chunk rigenerati.

## 20. Stato del repository

Tag `pre-visible-houses` preservato; modifiche divise in commit logici in
italiano: `907b548d`, `bbfda017`, `8d0296d0`, `14c1dfdd`, `743cde3c`,
`c0e26eb0`, `75dc889f` (asset/licenze, sistema casa, bridge Android, kit
SENDU, arredi e uova, test/report). Nessun push. Le cancellazioni preesistenti
dei log e i file untracked estranei non sono stati toccati. Vedi l'appendice
per l'esito finale della build.

---

## Appendice — esito build

Build release completa eseguita con
`LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 SKIP_MAP_UPDATE=1 SKIP_INSTALL=1
FORCE_UNITY_AAR=1 ./build_release.sh --auto`:

- **`BUILD SUCCESSFUL in 1m 42s`** (89 task: 24 eseguite, 1 da cache, 64 up-to-date).
- AAR Unity: `unity-export.gradle/unityLibrary/build/outputs/aar/unityLibrary-release.aar`
  (105.471.349 byte, 14:34) — **+2.730.691 byte** rispetto alla build delle 13:12.
- APK firmato e verificato: `app/build/outputs/apk/release/app-release-signed.apk`
  (**204.570.990 byte**, 14:36), allineato con zipalign e firmato con apksigner.
- **Crescita APK totale della casa visitabile**: da 201.473.302 byte (baseline
  12:11, prima di qualsiasi asset) a **204.570.990 byte**, cioè **+3.097.688 byte
  (~2,95 MiB)**: +327.720 byte per i 43 FBX CC0 e **+2.769.968 byte (~2,64 MiB)**
  per il kit SENDU completo.
- I sorgenti SENDU occupano 11.567.828 byte su disco (zippati 6.907.274 byte),
  ma in APK entrano compressi nel blob Unity `assets/bin/Data`: mesh FBX
  low-poly e una palette PNG 256x256, senza gli shader/texture PBR del kit
  Unity escluso per incompatibilità. **Non è una crescita di 60-70 MB**: per
  ottenerla sarebbero serviti i prefab completi e le texture del kit Unity,
  che richiedono Unity 6000.6/URP 17.6 e non sono stati importati.
- Debug symbols Sentry gestiti dalla build (upload task eseguito/da cache).
- Prefab `unity-project/Assets/Resources/HouseInteriors/CasaVisitabileCC0.prefab`
  (7.750 byte) generato in fase di export Unity da `HousePrefabSetup.EnsurePrefab()`.
- Backend riavviato su http://localhost:5100 (tile server OK).
- Artefatti collaterali riscritti da Unity (`link.xml.meta`, `PlayerHero.prefab`,
  `PlayerLocomotion.controller`) ripristinati a `HEAD` dopo la build.

La build non ha introdotto errori di compilazione C# nuovi. La verifica su
device resta non eseguita (punto 16).
