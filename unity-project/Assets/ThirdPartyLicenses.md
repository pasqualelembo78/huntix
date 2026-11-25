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

### Asset di interni valutati ma NON usati

- **A — DOLLHOUSE - FREE** (FForgeWorks, https://assetstore.unity.com/packages/3d/props/interior/dollhouse-free-modular-low-poly-interiors-furniture-room-kit-411314):
  download solo con account Unity Asset Store, nessun URL anonimo; **non
  scaricato**, nessun bypass. Autore Unity 6000.4.2 > 2022.3, compatibilità
  non verificabile.
- **B — House Interior - Free** (nappin, https://assetstore.unity.com/packages/3d/props/interior/house-interior-free-258782):
  stesso blocco account; **non scaricato** (autore 2021.3.45, verosimilmente
  compatibile ma non verificato).
- **C — Cozy Interiors + Builder** (SENDU, https://sendudev.itch.io/cozy-interiors):
  **scaricato** (ZIP modelli FBX/GLB e unitypackage URP) con licenza gratuita
  personalizzata non CC0, che consente il shipping nel gioco ma vieta la
  rivendita dei sorgenti. Il kit Unity richiede **Unity 6000.6+ / URP 17.6+** e
  shader custom incompatibili con Unity 2022.3: **non importato**. Nessun file
  di quel pack è presente nel repository.
- **Quaternius Ultimate House Interior Pack**: download fallito per quota Google
  Drive; **non scaricato**. KayKit Furniture Bits: checkout con email e
  fatturazione obbligatorie; **non scaricato**. Poly Pizza / OpenGameArt:
  licenze non verificabili in forma autorevole; **nessun download**.
