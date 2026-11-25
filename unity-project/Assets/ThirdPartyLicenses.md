# Licenze di terze parti — asset degli eventi (25/11/2026)

Questo file accompagna i modelli importati in `Assets/Resources/EventVenues/`.
Ogni modello è stato scaricato da una fonte autorevole, verificato e convertito
da glTF(.glb) a FBX con Assimp 5.2. Le trasformazioni sono documentate qui e
nel report `docs/HUNTIX_EVENTI_SPORTIVI_REPORT.md`.

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
