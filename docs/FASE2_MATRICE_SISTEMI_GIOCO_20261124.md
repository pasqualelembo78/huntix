# Fase 2 dell'audit — matrice sistemi di gioco (24/11/2026)

> **Stato finale aggregato (sempre 24/11/2026):** la tabella qui sotto è la
> ricognizione iniziale, precedente agli interventi. Le voci 13, 14, 15, 11 e 16
> sono state integrate rispettivamente con `8215f7cc`, `d96f62fb`, `0aaa470e`,
> `fc7c3b4a` ed `eb806994`; le voci 12, 17, 18, 19 e 20 sono state lasciate
> intatte perché sensibili o mancanti. Il dettaglio è in
> `docs/FASE2_VOCI_RIMASTE_20261124.md` e nei documenti delle singole slice.

## Criterio seguito

Le 10 funzionalità della Fase 2 sono state cercate nel codice esistente **prima**
di scrivere qualsiasi riga, su Android, Unity e backend/bridge. Nessun sistema è
stato rimosso, disattivato, sostituito o cambiato di default. Dove esisteva una
base è stata usata quella; dove esistevano sistemi paralleli sono stati lasciati
intatti.

Legenda: **COPERTO** = equivalente funzionante · **PARZIALE** = base reale ma
incompleta · **MANCANTE** = solo scaffold/log tecnico.

| # | Funzionalità | Android | Unity | Backend/Bridge | Esito |
|---|---|---|---|---|---|
| 11 | Inventario evoluto | `EggInventoryManager.kt:13-89` (300 uova, team 3, ordinamenti); `SavedManager.kt:263-345,375-461` (schiusa/fusione); `SurpriseInventoryActivity.kt:113-192` è bestiario, non inventario item | `Inventory.cs:6-73` (`Dictionary<string,int>` + `inv_` prefs); `EggInventoryManager.cs:7-129` con `AddEgg` senza chiamanti; `EggDex.cs:16` | `user_consumables` (`schema.py:240-247`); nessun `user_items`/CRUD; uova sincronizzate via `EggCapturedInCity` → `Bridge.kt:535-589` | PARZIALE |
| 12 | Rarità degli oggetti | `EggRarity.kt:9-23,134-165`: 5 livelli con colore, glow, potenza, XP, peso, raggio, TTL e roll pesato; `OutdoorManager.kt:521-531,784-797`; `WeatherZoneManager.kt:93,105` | `EggController.cs:9,401-410` (5 livelli); **tre tabelle drop divergenti**: `EggSpawnManager.cs:172-191`, `EggSpawner.cs:148-156`, `ContainerEgg.cs:187-193`; `ShopItem` e `Inventory` senza rarità | `city_gifts.rarity TEXT` libera (`city_realtime.py:725,1686`), nessun `CHECK`; `Bridge.kt:539-575` converte 4↔5 livelli | PARZIALE (uova coperte, item no) |
| 13 | Consumabili | `EggNutrimentManager.kt:29-205`: 6 esche, bonus elemento/amore-odio, quantità, starter kit, `applyFood`/`currentFoodBonus`; **nessun chiamante** e `tryCatch(..., foodBonus)` mai alimentato | `InteractionComponent.cs:11-20`; `InteractionManager.cs:109-145` applica il bisogno ma non consuma `itemId`; `StoreBuilder.cs:219-460` catalogo cibi/bevende | `CONSUMABLES` e `/api/user/mevacoins/store/{buy,use}` (`store.py:17-94`, `premium.py:86-136`), `streak_shield` (`streak.py:291-333`) ma nessun bridge né UI Android | PARZIALE → **primo intervento** |
| 14 | Raccolta risorse | `CatchToolManager.kt:27-232`: secchielli con capacità/prezzo/scadenza; `catchMultiplier` e `consume()` non usati; `SavedManager.kt:507-532` mining MVC passivo | nessun nodo risorsa: solo `collect` indoor (`StoreBuilder.cs:223-460`), `JobManager.cs:14-53`, `Game.OnEggCollected :644-668` | nessuna tabella `resource_nodes`/`harvest_log`, nessun endpoint authoritative; solo uova con posizione in `EggWhereLog` | PARZIALE |
| 15 | Crafting | solo fusione uova schiuse 3→1 (`SavedManager.kt:375-505`, `HatchedEgg.kt:9-20`); `craft_beer` è un nome oggetto, non un'azione | nessun `CraftingManager`, stazioni o output; unico falso positivo `StoreBuilder.cs:444` | nessuna tabella/endpoint/Callable `craft` | MANCANTE |
| 16 | Ricette (craft) | “ricetta” = generazione città fittizia (`Bridge.kt:383`, `HomeActivity.kt:617`), non crafting | `FictionalCityWorld.cs:14,90-133` carica `Resources/CityRecipes/foggia_city_recipe`; uovo `fictional-city` in `Game.cs:246-248` | `preprocessing/city_recipe.py:164-261` produce la ricetta OSM statica; nessuna tabella `recipes` di crafting | MANCANTE come crafting (COPERTO solo come ricetta città) |
| 17 | Shop migliorato | due negozi separati: Billing/MVC (`ShopActivity.kt:16-112`) e cash RealLife (`ui/ShopActivity.kt:17-196`); `ShopDefs.kt:33-40` cosmetici acquistati ma non attribuiti; nessuna vendita | `Shop.cs:7-22`, `UIManager.cs:354-369,925-959` (compra con `Wallet`/`Inventory`); negozio indoor `IndoorManager.cs:277-296` | `get_shop_payload()` (`store.py:180-223`), endpoint `/store`, `/store/buy`, `/store/use`, `/transactions` (`premium.py:86-153`); nessun evento `ShopOpen`/`ShopBuy` in whitelist | PARZIALE |
| 18 | Economia e log transazioni | `SavedManager.kt:23-102` (saldo MVC + totali, nessun ledger); `MoneyManager.kt:11-42`; idempotenza solo Billing (`BillingManager.kt:206-275`) | `Wallet.cs:23-93`, `WalletManager.cs:20-186` (server-authoritative), ATM MVC↔€ `InteractableProp.cs:211-296`; `MVCUpdated` A→U è log-only (`GameManager.cs:422-425`) | `mevacoins` + `mevacoins_transactions` (`schema.py:185-210`, `mevacoins.py:15-123`), `GET /transactions` (`premium.py:151-153`); `city_wallet.spend/earn` (`city_realtime.py:1332-1385`) **senza log**; tre valute non riconciliate | PARZIALE |
| 19 | Scambi P2P | ricezione regali con idempotenza `eggId` (`Bridge.kt:597-641`); nessun invio, doppia conferma o storico donazioni | `MultiplayerManager.cs:642-770` polling `/api/city/gift/{inbox,claim}`, `GiftClaimed` U→A; nessun trade/baratto | `city_gifts` (`city_realtime.py:710-831,1651-1736`) e `trade_offers` (`firestore.rules:214-228`); **nessun addebito del mittente, nessuna verifica ownership uovo**, idempotenza lato client | PARZIALE |
| 20 | Personalizzazione | `PlayerProfile.kt:48-75,200-281` (skin, pet, frame, `equippedEggSkinId`); `PlayerProfileTabs.kt:71-220` seleziona personaggio/pet; frame/tema/skin uovo solo letti | `PlayerAppearance.cs:12-75` + `HamburgerMenu.cs:505-609`; `PetController.cs:13-28,167-203`; `CityStateSync.cs:164-165,417-430`; nessun `avatarFrame`/`costume` | `customization/{itemId}` read-only (`firestore.rules:67-71`); nessuna tabella `user_cosmetics`, nessun evento `CosmeticChanged` | PARZIALE |

## Constatazioni trasversali (pre-esistenti, non toccate)

- **Nessuna tabella** per `items`, `inventory` generico, `recipes` di crafting,
  `transactions` dedicate, `trades` o `cosmetics`. Gli unici `CREATE TABLE`
  economici sono `mevacoins`, `mevacoins_transactions`, `content_unlocks`,
  `user_consumables`, `user_mission_rewards` (`backend/storage/schema.py:185-265`),
  più `city_wallet`, `city_gifts`, `city_msgs` creati a caldo in
  `backend/city_realtime.py:110-204,719-735`.
- **Tre valute parallele non riconciliate**: MVC backend, wallet città ("soldi")
  e gemme Android. Il bridge espone MVC e gemme ma non il wallet città
  (`UnityIpc.kt:33-43`; `WalletManager.cs:23` è solo HTTP lato Unity).
- `functions/index.js:420` contiene TypeScript (`const updateData: { [key: string]: any }`)
  in un file `.js`: impedisce il deploy di tutte le Cloud Functions. Con Node 12
  fallisce già prima per `?.` alla riga 243. **Non modificato**: serve una
  decisione esplicita prima di toccare una funzione che altri flussi usano.
- `GameProgressSync.kt:29,123-124` scrive `game_progress/{uid}` su Firestore, ma
  `firebase/firestore.rules` non autorizza quel match (catch-all `if false`).
  Anche questo è pre-esistente e lasciato intatto.
- `PerfProbe.cs:28-29` ha gli slot `move,camera,ik,chunk,anim,input,spawn,roads,
  traffic,fictional,mount,menu,console,companion,diary`; `SessionTelemetry.cs`
  misura frame e uova, non shop/inventario/craft/economia/personalizzazione.

## Perché si parte dai consumabili (voce 13)

È il buco più netto e il meno duplicativo: **i dati ci sono già tutti** (6 esche
con bonus, reazioni elemento, quantità, starter kit, hook `foodBonus` nella
cattura, quest `spend_food`, catalogo cibi indoor lato Unity) ma **nessun
chiamante reale**: `applyFood`, `consume`, `getAvailableFoods` e
`currentFoodBonus` sono morti, e `tryCatch(ctx, eggId, foodBonus)` riceve sempre
il default `1f`. Collegare questa via non crea un secondo inventario, non
unifica rarità, non tocca valute né drop esistenti e non cambia alcun default:
l'esca agisce solo se il giocatore la sceglie esplicitamente.

Lo stesso vale per la "Speed Boost" di `AbilityManager.kt:59-65,166-168`
(raggio di cattura temporaneo con cooldown, UI in `AbilityActivity.kt`), anch'essa
senza effetto applicato: viene **lasciata intatta** e segnalata come sorella
della voce 13, da trattare in un slice separato e solo con conferma, perché
unificarla alle esche cambierebbe due sistemi diversi.

### Riflesso della caccia alle uova

L'esca non è un consumabile generico: è il modo per addolcire un uovo prima di
catturarlo. Il primo utilizzo regala **un uovo posizionale dell'esca**
(idempotente, con aroma legato al cibo scelto) e lo registra nello **stesso**
`EggWhereLog` con luogo e coordinate del punto di cattura. Così il diario della
Fase 1 sale di livello: accanto a "uovo trovato a città/outdoor" compare
"uovo trovato grazie a una mela dolce a 41.4xx, 16.1xx".

### Bridge bidirezionale previsto

- **Android → Unity**: `ActiveBaitChanged` con `foodId`, `bonus`, `expiresMs`;
  ripristino passivo in `setMode` con `activeBaitId` e contatore esche.
- **Unity → Android**: `ConsumableUsed` spedito da `InteractionManager` quando
  il giocatore consuma un oggetto indoor, con `itemId`, `action` e coordinate
  reali (`WorldOrigin`), in modo che Android consumi la quantità e registri
  l'attività nel diario.
- Nessun premio o penalità aggiuntiva: l'unico effetto è il bonus di cattura
  già previsto da `EggNutrimentManager`, più la telemetria.

## Limiti

- Le altre voci di Fase 2 restano aperte: inventario item, rarità item, raccolta
  risorse, crafting, ricette craft, shop unificato, ledger, trade validato,
  cosmetici indossabili.
- Nessun dispositivo/emulator disponibile: il collasso dei consumabili non può
  essere collaudato a runtime in questa sessione.
- I problemi backend qui segnalati non sono stati risolti: richiedono
  decisioni esplicite perché toccano flussi di gioco e pagamenti esistenti.
