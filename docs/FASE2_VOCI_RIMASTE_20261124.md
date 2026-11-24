# Fase 2 · voci rimaste e motivi del non-intervento (24/11/2026)

Questo documento chiude la Fase 2 dell'audit sul piano decisionale. Le voci
13, 14, 15, 11 e 16 sono state integrate con slice additivi e committate su
`integrazione-clone`. Le cinque qui sotto **non sono state modificate**: la
regola del Mandato è “in caso di dubbio, conservare”, e ciascuna richiederebbe
una scelta su sistemi pre-esistenti che altererebbe il comportamento del gioco.

## Voce 12 — Rarità degli oggetti · **SENSIBILE**

La rarità delle uova è già coperta (`EggRarity.kt`: 5 livelli con colore,
glow, potenza, XP, peso, raggio e TTL; `EggController.cs`). La rarità degli
**oggetti** non esiste: `ShopItem` e `Inventory` non la portano. Lato Unity
convivono **tre tabelle drop divergenti** (`EggSpawnManager.cs:172-191`,
`EggSpawner.cs:148-156`, `ContainerEgg.cs:187-193`) e il bridge converte 4↔5
livelli (`Bridge.kt:539-575`; `city_gifts.rarity` è testo libero senza `CHECK`).

Unificare la rarità richiederebbe di scegliere una tabella autorevole,
riscrivere le altre due e decidere la mapping 4↔5 lato server. Non è un
collegamento additivo: cambierebbe i drop di sistemi già attivi. **Non toccata.**

## Voce 17 — Shop migliorato · **SENSIBILE**

Esistono due negozi separati (MVC/Billing e RealLife cash), un catalogo
cosmetici acquistati ma non attribuiti (`ShopDefs.kt:33-40`) e il backend
`/store`, `/store/buy`, `/store/use`, `/transactions` (`store.py`,
`premium.py`). Manca qualsiasi evento `ShopOpen`/`ShopBuy` in whitelist.

Unificare gli shop o esporre gli acquisti al bridge significa toccare pagamenti,
idempotenza delle transazioni e tre valute non riconciliate. **Non toccata.**

## Voce 18 — Economia e log transazioni · **SENSIBILE**

Il ledger authoritative esiste per MVC (`mevacoins`, `mevacoins_transactions`,
`GET /transactions`), ma `city_wallet.spend/earn` (`city_realtime.py:1332-1385`)
scrive senza log e Android ha un saldo MVC locale in `SavedManager`. Le tre
valute (MVC, wallet città, gemme) non sono riconciliate.

Aggiungere un ledger o esporre il wallet città al bridge richiederebbe una
decisione su chi è autorevole e su come migrare i saldi esistenti. **Non toccata.**

## Voce 19 — Scambi P2P · **SENSIBILE**

La ricezione regali ha idempotenza lato client (`Bridge.kt:597-641`),
`MultiplayerManager.cs` fa polling di inbox/claim e `city_gifts`/`trade_offers`
esistono nel backend. Mancano addebito al mittente, verifica di possesso
dell'uovo e doppia conferma: oggi un regalo può essere rivendicato senza che il
mittente paghi.

Aggiungere validazione P2P richiederebbe regole server e firma delle operazioni:
un errore creerebbe o distruggerebbe uova altrui. **Non toccata.**

## Voce 20 — Personalizzazione (scopo stretto egg skin) · **MANCANTE**

`PlayerProfile.kt:50` ha solo `equippedEggSkinId = "skin_default"`: non esiste
un catalogo di skin possedute (`ownedEggSkins`) né un setter/getter chiamato, e
`customization/{itemId}` è read-only in `firestore.rules:67-71` senza dati. I
cosmetici di `ShopDefs.kt` non sono egg skin e l'acquisto applica solo un
bisogno (`ShopActivity.kt:185-194`). Lato Unity `PlayerAppearance.cs` e
`HamburgerMenu.cs` gestiscono personaggio/pet, non skin uovo.

Non c'è quindi nulla di posseduto da selezionare: creare un catalogo locale
significherebbe inventare un possesso non verificabile e rischiare una
scrittura Firestore implicita. **Non toccata.**

## Pre-esistenti segnalati e lasciati intatti

- `functions/index.js:420`: TypeScript in un file `.js` (blocca il deploy delle
  Cloud Functions); con Node 12 fallisce già prima per `?.` alla riga 243.
- `game_progress/{uid}` scritto da Android ma negato da `firestore.rules`.
- `AbilityManager` Speed Boost e la scala rarità 4↔5: API reali senza effetto,
  lasciate intatte perché unificarle cambierebbe due sistemi diversi.
- Blocco `20690,9ms` registrato in `docs/huntix_log_20261123_175436.txt`.

## Stato Fase 2

| Voce | Stato |
|---|---|
| 13 · Consumabili/esche | Integrata (`8215f7cc`) |
| 14 · Raccolta risorse | Integrata (`d96f62fb`) |
| 15 · Crafting/fusione | Integrata (`0aaa470e`) |
| 11 · Inventario evoluto | Integrata (`fc7c3b4a`) |
| 16 · Ricette di fusione | Integrata (`e9d2bc94`) |
| 12 · Rarità oggetti | Sensibile, non toccata |
| 17 · Shop migliorato | Sensibile, non toccata |
| 18 · Economia/ledger | Sensibile, non toccata |
| 19 · Scambi P2P | Sensibile, non toccata |
| 20 · Personalizzazione | Mancante, non toccata |

Nessun dispositivo/emulator è disponibile: tutte le integrazioni della Fase 2
sono verificate a livello statico e di build, non a runtime. Ogni nuova
funzionalità andrà collaudata su Android quando un device diventa disponibile.
