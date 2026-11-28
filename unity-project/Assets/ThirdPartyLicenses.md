# Licenze di terze parti — asset degli eventi e delle case visitabili (25/11/2026)

Questo file accompagna i modelli importati in `Assets/Resources/EventVenues/`
(asset degli eventi) e in `Assets/Resources/HouseInteriors/` (case visitabili).
Ogni modello è stato scaricato da una fonte autorevole, verificato e convertito
da glTF(.glb) a FBX con Assimp 5.2. Le trasformazioni sono documentate qui e
nei report `docs/HUNTIX_EVENTI_SPORTIVI_REPORT.md` e
`docs/HUNTIX_CASE_VISITABILI_REPORT.md`.

## 1. Football Soccer Stadium and Club Grounds — CC0 1.0

- Fonte: https://3dassets.dev/packs/football-soccer-club-grounds
- URL API verificate: https://3dassets.dev/api/v1/packs/football-soccer-club-grounds
- Licenza: **CC0 1.0 Universal** — https://creativecommons.org/publicdomain/zero/1.0/
- Autore/publisher: 3dassets.dev; modelli generati con Claude Fable 5.1.
- Data acquisizione: 25/11/2026.
- File: `Assets/Resources/EventVenues/stadium/*.fbx` (33 modelli: campo,
  porte, tribune, panchine, spogliatoi, tabellone, fari, tornelli, ecc.).
- Modifiche: conversione GLB→FBX, nessuna alterazione della geometria;
  riutilizzo con scaling/rotazione in scena e collider propri di Huntix.
- Attribution non richiesta (CC0).

## 2. Racecourse and Parade Ring — CC0 1.0

- Fonte: https://3dassets.dev/packs/racecourse-and-parade-ring
- URL API verificate: https://3dassets.dev/api/v1/packs/racecourse-and-parade-ring
- Licenza: **CC0 1.0 Universal** — https://creativecommons.org/publicdomain/zero/1.0/
- Autore/publisher: 3dassets.dev; modelli generati con Claude Opus 5.
- Data acquisizione: 25/11/2026.
- File: `Assets/Resources/EventVenues/racecourse/*.fbx` (46 modelli: rettilineo
  e curva, ringhiere, ostacoli, gabbie di partenza, parade ring, pesa,
  tribune, tabellone, recinti, alberi, panchine).
- Modifiche: conversione GLB→FBX. Non sono stati importati gli asset
  relativi a scommesse (bookmakers/tote), perché Huntix non ha economie
  parallele né scommesse.
- Attribution non richiesta (CC0).

## 3. Horse Stables and Equestrian Yard — CC0 1.0

- Fonte: https://3dassets.dev/packs/equestrian-yard-and-stables
- URL API verificate: https://3dassets.dev/api/v1/packs/equestrian-yard-and-stables
- Licenza: **CC0 1.0 Universal** — https://creativecommons.org/publicdomain/zero/1.0/
- Autore/publisher: 3dassets.dev; modelli generati con Claude Fable 5.1.
- Data acquisizione: 25/11/2026.
- File: `Assets/Resources/EventVenues/stable/*.fbx` (27 modelli: cavalli in
  diverse pose e mantelli, box, sellerie, recinti, attrezzi, rimorchio).
- Modifiche: conversione GLB→FBX, selezione di un sottoinsieme dei 107 modelli
  originali per limitare peso e draw call su Android.
- Attribution non richiesta (CC0).

## 4. Race Horse (modello animato) — CC BY 4.0 — ATTRIBUZIONE OBBLIGATORIA

> "Race Horse" by vinish.dev (https://vinish.dev/free-3d-models/), CC BY 4.0

- Fonte: https://vinish.dev/free-3d-models/horses e
  https://vinish-models.pages.dev/horses/race-horse.glb
- Licenza: **Creative Commons Attribution 4.0 International** —
  https://creativecommons.org/licenses/by/4.0/
- Autore: vinish.dev. Il file GLB contiene il campo `asset.copyright` con la
  riga di credito qui riportata.
- Data acquisizione: 25/11/2026.
- File: `Assets/Resources/EventVenues/horses/RaceHorseAnimated.fbx`.
- Modifiche dichiarate: conversione GLB→FBX, decimazione della mesh da
  119.288 a circa 17.000 triangoli mantenendo skin weights e normali,
  conservazione delle sei clip originali (Idle, Walk, Trot, Gallop, Graze,
  Rear) e dei materiali. **Modificato: mesh semplificata e convertito in FBX.**
- Uso in Huntix: cavalli in corsa nell'ippodromo e nel parade ring, animati
  con le clip Gallop/Trot/Walk/Idle.
- L'attribuzione è riportata anche nei crediti dell'app e nel report.

## 5. Low Poly Character Figures — CC0 1.0

- Fonte: https://3dassets.dev/packs/low-poly-character-figures
- URL API verificate: https://3dassets.dev/api/v1/packs/low-poly-character-figures
- Licenza: **CC0 1.0 Universal** — https://creativecommons.org/publicdomain/zero/1.0/
- Autore/publisher: 3dassets.dev. Data acquisizione: 25/11/2026.
- File: `Assets/Resources/EventVenues/people/*.fbx` (5 figure: giocatore,
  corridore, spettatore seduto, spettatore anziano, figura in piedi).
- Modifiche: conversione GLB→FBX; il modello "Town plaza crowd" (10 MB) è
  stato scaricato per la valutazione ma **non importato**: si usano poche
  figure istanziate invece di un gruppo unico pesante.
- Attribution non richiesta (CC0).

## Asset valutati ma NON importati

- Animatics Asset Store — "3D Football Stadium – Low Poly"
  (https://www.animaticsassetstore.com/product/football-stadium/): il download
  avviene soltanto tramite carrello WooCommerce con account, non verificabile
  come gratuito e senza testo di licenza esplicito. **Scartato**, nessun file
  acquisito.
- Pacchetti 3dassets.dev con contenuti di scommesse, armi o temi non attinenti:
  **scartati** per coerenza con il Mandato e con l'assenza di economia parallela.

Assimp 5.2 (https://github.com/assimp/assimp) è stato usato solo come strumento
di conversione locale; nessun suo codice è stato copiato nel progetto.

---

## Case visitabili — Cozy Home Interiors (25/11/2026)

- **Fonte**: https://3dassets.dev/packs/cozy-home-interiors
- **API verificate**: https://3dassets.dev/api/v1/packs/cozy-home-interiors
- **Licenza**: **CC0 1.0 Universal** — https://creativecommons.org/publicdomain/zero/1.0/
- **Autore/contributore**: 3D Assets (3dassets.dev); contenuti generati con
  Claude Fable 5.1. CC0 non richiede attribuzione.
- **Data acquisizione**: 25/11/2026. Pack scaricato: 129 GLB, 3.616.296 byte,
  nessun Draco/KTX2, estensione `KHR_mesh_quantization`.
- **File importati**: `Assets/Resources/HouseInteriors/CozyHome/*.fbx` — 43 FBX
  (pareti, porte, finestre, pavimenti, soffitti, scale, soggiorno, cucina,
  camera, bagno, illuminazione), ognuno con `.meta`. Nessuna texture importata:
  i GLB contengono solo colori PBR di base, ricreati con materiali URP/Lit.
- **Modifiche dichiarate**: i GLB usano vertici quantizzati
  (`KHR_mesh_quantization`) che assimp interpreta male; lo script ripetibile
  `scripts/house_assets_import.py` dequantizza i vertici (SHORT normalizzato
  /32767), applica le matrici dei nodi glTF, esporta OBJ+MTL e converte in FBX
  con assimp. Nessuna geometria inventata; scalatura/rotazione applicate a
  runtime dagli slot del prefab `CasaVisitabileCC0`.
- **Uso in Huntix**: arredamento dell'unico edificio OSM `204023010` reso
  visitabile; i mobili non hanno collider (la navigazione usa solo paratie e
  guscio). Riflesso uova: cassetto cucina, armadio camera, lavandino bagno.

### Asset di interni valutati (importati o esclusi)

- **A — DOLLHOUSE - FREE** (FForgeWorks, https://assetstore.unity.com/packages/3d/props/interior/dollhouse-free-modular-low-poly-interiors-furniture-room-kit-411314):
  download solo con account Unity Asset Store, nessun URL anonimo; **non
  scaricato**, nessun bypass. Autore Unity 6000.4.2 > 2022.3, compatibilità
  non verificabile.
- **B — House Interior - Free** (nappin, https://assetstore.unity.com/packages/3d/props/interior/house-interior-free-258782):
  stesso blocco account; **non scaricato** (autore 2021.3.45, verosimilmente
  compatibile ma non verificato).
- **C — Cozy Interiors + Builder** (SENDU, https://sendudev.itch.io/cozy-interiors):
  **scaricato** (ZIP modelli FBX/GLB e unitypackage URP) con licenza gratuita
  personalizzata non CC0. I **339 FBX e le 20 PNG sono stati importati** (vedi
  sezione dedicata qui sotto); il solo **kit Unity** (shader/script/prefab)
  resta **escluso** perché richiede **Unity 6000.6+ / URP 17.6+**.
- **Quaternius Ultimate House Interior Pack**: download fallito per quota Google
  Drive; **non scaricato**. KayKit Furniture Bits: checkout con email e
  fatturazione obbligatorie; **non scaricato**. Poly Pizza / OpenGameArt:
  licenze non verificabili in forma autorevole; **nessun download**.

---

## Kit completo SENDU — licenza commerciale gratuita non CC0 (25/11/2026)

- **Prodotto**: Cozy Interiors + Builder 1.0.1 — 339 modelli FBX + palette e
  pattern PNG (10 stanze: Shell, Bedroom, Kitchen, Living, Bakery, Cafe,
  Office, Bathroom, Kids, Decor).
- **Fonte autorevole**: https://sendudev.itch.io/cozy-interiors — scaricato
  senza account né pagamento tramite il flusso itch.io pubblico.
- **Autore/titolare**: "the author of Cozy Interiors + Builder", contatto
  `cholak.apps@gmail.com`. **Data acquisizione**: 25/11/2026.
- **Licenza**: licenza gratuita **non esclusiva, mondiale e perpetua** per
  progetti commerciali e non commerciali finiti (giochi, app, film, render).
  **Il shipping dentro un gioco/app finito è esplicitamente consentito.**
  Testo integrale conservato in
  `unity-project/Assets/Resources/HouseInteriors/Sendu/README_models.txt`.
- **Attribuzione**: non richiesta ("Credit is not required but appreciated").
- **Restrizioni rispettate**: **divieto di rivendere, condividere o regalare i
  sorgenti** (modelli, palette, pattern, materiali, prefab, layout, scene,
  shader, script) e di includerli in un altro pack, template, marketplace o
  content library; **divieto di uso come training data ML**. In Huntix gli
  asset sono spediti solo dentro l'APK finito, mai come file separati.
- **File importati**: `Assets/Resources/HouseInteriors/Sendu/FBX/<Stanza>/*.fbx`
  (339), `.../Palettes/CI_Theme_*.png` (4), `.../Patterns/CI_Pattern_*.png`
  (16), `README_models.txt`. Ogni file ha il proprio `.meta`.
- **Modifiche dichiarate**: importati solo i FBX engine-agnostic e le PNG.
  **Non importati** shader, script, prefab, layout e scene del kit Unity
  (richiedono Unity 6000.6+/URP 17.6+, incompatibili con 2022.3/URP 14.0.11).
  I materiali sono ricreati a runtime con URP/Lit e la palette 8x8 a 32 px
  (`filterMode: Point`, mipmap disattivate, alpha = smoothness non opacità);
  ogni FBX conserva i figli `_LOD0`/`_LOD1`, di cui il secondo viene
  disattivato a runtime per evitare doppio disegno. 32 modelli sono usati
  come arredi aggiuntivi nella casa visitabile; gli altri restano disponibili
  in `Resources` per le prossime `Spec` (bar, caffetteria, ufficio, negozio).
- **Uso in Huntix e riflesso uova**: i mobili non hanno collider; la casa ha
  ora **4 nascondigli**, il quarto è `casa→cucina→cassetto-sendu` nel cassetto
  SENDU, con posizione registrata e bridge Android come gli altri tre.

### Villa vittoriana «TARDIS» — riutilizzo degli stessi modelli SENDU (27/11/2026)

- **Prefab**: `Assets/Resources/Villas/VillaVittorianaCompleta.prefab`, generato
  dal baker editoriale `Assets/City/Editor/VillaPrefabBaker.cs` (solo editor,
  non compilato nel player). Caricato a runtime con
  `Resources.Load<GameObject>("Villas/VillaVittorianaCompleta")`.
- **Modelli usati**: 119 reference a FBX SENDU già importati e licenziati sopra
  (moduli `Shell`, arredi `Bedroom`/`Bathroom`/`Kids`/`Living`/`Office`/
  `Kitchen`/`Cafe`/`Bakery`, decorazioni `Decor`), nessun nuovo download e
  nessun nuovo file sorgente del kit.
- **Materiali**: ricreati in `Assets/Resources/Villas/Materials/` con shader
  URP/Lit (crema, legno, rosso, verde, oro, pietra, vetro emissivo), coerentemente
  con la licenza: i materiali/sorgenti del kit restano esclusi.
- **Modifiche dichiarate**: i moduli architettonici 2 m sono scalati sugli assi
  per comporre pareti alte 12 m, pavimenti e soffitto a cassettoni su tutta la
  tasca 164 × 114 m; i mobili mantengono scala uniforme e materiali SENDU; i
  figli `_LOD1`/`_LOD2` vengono disattivati per evitare doppio disegno. Dieci
  `HouseEggSpot` sono aggiunti dal baker sui contenitori della villa: i cinque
  id storici (`cassetto-lampadario`, `cassetto-biblioteca`, `credenza-pranzo`,
  `vetrina-citta`, `fontana-piazzetta`) più i cinque delle nuove comodità
  (`armadio-camera`, `lavandino-bagno`, `forno-cucina`, `cassetto-bambini`,
  `scrivania-studio`).
- **Attribuzione**: non richiesta dalla licenza SENDU («Credit is not required
  but appreciated»); il credito è comunque riportato qui e nel testo integrale
  `Assets/Resources/HouseInteriors/Sendu/README_models.txt`.

### Villa da 300 mq in 3 livelli — stesso kit SENDU (27/11/2026)

- **Prefab aggiuntivo**: `Assets/Resources/Villas/Villa300mqTreLivelli.prefab`,
  generato da `VillaPrefabBaker.Bake300()` nello stesso baker editoriale.
  La reggia TARDIS da 164 × 114 m resta intatta come fallback per le case non
  configurate: nessun sistema, prefab o modello è stato rimosso.
- **Modelli usati**: 18 reference a FBX SENDU già importati e licenziati sopra
  (`Shell`, `Bedroom`, `Bathroom`, `Kids`, `Living`, `Office`, `Kitchen`,
  `Cafe`, `Decor`), fra cui letto/armadio, vasca/lavandino, librerie/scrivania,
  forno/tavolo/frigo, divano/tavolino, letti a castello/cassa giochi, tavolino
  bistrot e pianta. Nessun nuovo download, nessun nuovo file sorgente del kit.
- **Materiali**: gli stessi URP/Lit creati per la reggia (`Villa_Cream`,
  `Villa_Wood`, `Villa_Red`, `Villa_Green`, `Villa_Gold`, `Villa_Stone`,
  `Villa_Glow`) in `Assets/Resources/Villas/Materials/`.
- **Modifiche dichiarate**: i moduli architettonici 2 m sono scalati su tre
  piani da 10 × 10 m (100 mq ciascuno, 300 mq totali, 3,5 m di altezza per
  piano); i mobili mantengono scala uniforme e i figli `_LOD1`/`_LOD2` restano
  disattivati. Il baker aggiunge 9 `HouseEggSpot` — tre per piano — e quattro
  trigger scala (`GrandVillaStairs`) che riusano `InteriorTeleport`.
- **Attribuzione**: identica a quella della reggia; nessun credito obbligatorio,
  credito riportato qui e nel README del kit.

### Gusci esterni di Via Silvio Pellico — Grab3D e vinish.dev (27/11/2026, rimossi il 28/11/2026)

I cinque FBX qui sotto sono stati **rimossi il 28/11/2026** per decisione
esplicita del proprietario, che ha scelto il modello «una casa eroe reale + il
resto procedurale». I sei edifici OSM restano visitabili con lo stesso interno
da 300 mq, le stesse uova e lo stesso bridge Android: cambia solo l'esterno,
ora generato dal guscio procedurale condiviso (senza stiramento a 10,5 m e
senza vestizione fasce/finestre). I testi di licenza sono conservati come
provenienza storica; i file FBX non sono più nel progetto né nel player.

- **Suburban Family House** — Grab3D, CC0 1.0, senza attribuzione obbligatoria.
  Provenienza e licenza conservate in `LICENSE_grab3d.txt` e
  `manifest_grab3d.json`. File rimosso: `grab3d_suburban_family_house.fbx`.
- **Family House**, **Townhouses**, **Mediterranean Villa**, **Victorian House**
  — vinish.dev, CC BY 4.0. Crediti e licenza conservati in
  `CREDITS_vinish.txt`, `LICENSE_vinish.txt`, `README_vinish.txt`.
  Credito: “Family House”, “Townhouses”, “Mediterranean Villa” e “Victorian
  House” by vinish.dev (https://vinish.dev/free-3d-models/), CC BY 4.0.
  File rimossi: `vinish_*.fbx`.
- **Kenney City Kit Suburban (CC0)** — già presente nel progetto prima di
  questo intervento; veste gli edifici ordinari e i gusci procedurali.
- **Unity Asset Store — Сountry House FREE** (Objective Environment,
  https://assetstore.unity.com/packages/3d/environments/urban/country-house-free-125731):
  **scaricato e incluso** il 28/11/2026 con account Unity autorizzato, sotto
  **Standard Unity Asset Store EULA** (https://unity.com/legal/as-terms,
  Extension Asset). È una casa completa con interni, porte, materiali e
  texture (modello da 4.700 triangoli). File:
  `Assets/ALP_Assets/country house01/` e
  `Assets/Resources/Houses/ViaSilvioPellico/AssetStore/CountryHouse_Prefab.prefab`
  e `Assets/Resources/Houses/ViaSilvioPellico/AssetStore/CountryHouse_Interior.prefab`
  (ramo `mainIndoor01` estratto senza modifiche a geometria/materiali).
  Modifiche dichiarate: texture ridotte da 4096 a 2048 px, scena demo e
  post-processing esclusi; geometria e materiali invariati. Testo e crediti in
  `Assets/Resources/Houses/ViaSilvioPellico/LICENSE_assetstore_country_house.txt`.
  Usato come **unico guscio eroe dedicato** e come **suo interno giocabile**
  sull'edificio OSM `405221437`; le altre cinque case visitabili usano il
  guscio procedurale condiviso. I materiali vengono clonati a runtime per la
  conversione URP, senza alterare i file sorgente.
