# Fase 2 · voce 11 — inventario evoluto (24/11/2026)

## Criterio e scelta

Dopo le voci 13 (esche), 14 (raccolta) e 15 (crafting), la voce 11 è il quarto
caso di API reale senza chiamanti: `EggInventoryManager.kt` ha ordinamenti per
rarità/potenza e una squadra uova (`toggleBattleTeam`), usati solo nelle loro
definizioni, e non esiste **nessuna** schermata Android che mostri l'inventario
uova (quella presente mostra le creature). La regola di autonomia del Mandato
§11 impone di procedere senza chiedere indirizzi.

Il codice è originale Huntix, senza copia dai clone esterni: non servono nuove
voci in `THIRD_PARTY_NOTICES.md`.

**Confini rigorosi**: `EggInventoryManager.kt` non è stato modificato. Non sono
stati toccati valute, negozio, tabelle drop, Firestore, backend, i tre inventari
paralleli (Android/Unity `Inventory`/Unity `EggInventoryManager`) né la squadra
creature dei raid. L'ordinamento resta una preferenza di vista e non riscrive
mai l'inventario.

## Cosa è stato aggiunto

| File | Ruolo |
|---|---|
| `app/.../managers/EggInventoryViewManager.kt` | Wrapper additivo: ordinamenti in sola lettura, squadra uova delegata all'API storica, uovo posizionale per rarità, contatori e risposta A→U |
| `app/.../EggCollectionActivity.kt` | UI italiana “Le tue uova”: chip Rarità/Potenza/Recenti, elenco, squadra uova, link al diario |
| `app/.../ui/PlayerProfileTabs.kt` | Ingresso “🥚 Le tue uova” nella scheda Statistiche |
| `app/src/main/AndroidManifest.xml` | Registrazione `.EggCollectionActivity` |
| `app/.../bridge/UnityIpc.kt` | Whitelist `InventoryViewRequest` (Unity → Android) |
| `app/.../bridge/Bridge.kt` | Handler: `open` apre la collezione, `snapshot` rimanda solo i contatori |
| `app/.../bridge/BridgeActivity.java` | Specchio passivo `inventoryCount`, `inventoryTeamCount`, `inventorySortMode` in `setMode` |
| `unity-project/Assets/City/Scripts/UI/HamburgerMenu.cs` | Voce “Le tue uova (collezione)” e altezza pannello adattata al numero di righe |
| `unity-project/Assets/Scripts/Core/GameManager.cs` | Ricezione `EggInventoryChanged`, ripristino contatori, `PlayerPrefs`, telemetria |
| `unity-project/Assets/City/Scripts/Diagnostics/PerfProbe.cs` | Slot `inventory` (19 slot), misura degli scambi |
| `backend/test_unity_audit_features.py` | Clausola `TestFase2InventarioEvoluto` (4 test) e aggiornamento `SlotCount` |

## Flusso di gioco

1. Il giocatore apre **Profilo → Statistiche → 🥚 Le tue uova**, oppure la voce
   “Le tue uova (collezione)” del menu in Miacittà. Unity invia
   `InventoryViewRequest` e Android apre la schermata.
2. I chip **Rarità / Potenza / Recenti** cambiano solo la preferenza di vista
   (`egg_inventory_view_prefs`). Le API `getSortedByRarity`/`getSortedByPower`
   restituiscono nuove liste: l'ordine storico “più recente prima” salvato da
   `EggInventoryManager` non viene mai riscritto.
3. Il pulsante **Metti in squadra / Togli dalla squadra** chiama
   `EggInventoryManager.toggleBattleTeam`, con il limite esistente di 3 uova.
   La squadra uova è presentata esplicitamente come distinta da quella creature
   usata nei raid: `RaidBattleActivity` e `SurpriseManager` non sono toccati.
4. Riflesso della caccia alle uova: la **prima** volta che entra in squadra un
   uovo di una rarità nasce un uovo posizionale idempotente
   `inventory_team_<rarityId>` (`xpReward = 0`) nel normale inventario uova,
   con luogo `inventario→squadra uova` e coordinate GPS/ultima posizione nota.
   Ogni azione produce **una sola** voce nel `EggWhereLog`: l'uovo trovato per
   la prima rarità, oppure un evento `KIND_EVENT` per le azioni successive.
5. Android invia `EggInventoryChanged` a Unity con contatori e modo di ordine;
   Unity aggiorna lo specchio in `PlayerPrefs`, la telemetria e non riceve mai
   l'elenco completo delle uova. In `setMode` i contatori vengono rimandati in
   modo passivo, senza generare uova all'avvio.

## Riflesso della caccia alle uova

- La collezione non è una lista contabile: ogni prima squadra di una rarità è
  un **luogo dove nasce un uovo**, con posizione e diario condivisi.
- Un solo inventario uova e un solo diario: nessun quinto archivio, nessuna
  duplicazione grazie all'`eggId` stabile per rarità.
- Se l'inventario è pieno il ricordo resta come evento nel diario, senza
  uovo aggiuntivo: meglio la cronologia completa che un premio perso.

## Bridge bidirezionale

| Direzione | Evento/campo | Dati | Effetto |
|---|---|---|---|
| Unity → Android | `InventoryViewRequest` | `action=open` (o `snapshot`) | apre la collezione / rimanda contatori |
| Android → Unity | `EggInventoryChanged` | `inventoryCount`, `inventoryTeamCount`, `inventorySortMode`, `ok` | specchio, telemetria |
| Android → Unity (`setMode`) | stessi campi | contatori | ripristino passivo |

## Verifiche eseguite in questa sessione

- `python3 backend/test_unity_audit_features.py`: **69 test OK** (4 nuovi).
- `python3 backend/test_audit_regressions.py`: **8 test OK**.
- `python3 backend/test_city_recipe.py`: **3 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori
  (194 warning pre-esistenti). `GameManager.cs` e `HamburgerMenu.cs` sono
  compilati dalla build Unity reale qui sotto.
- `git diff --check`: pulito.
- Build release e verifica token: vedere sezione “Build” qui sotto.

## Limiti reali

- Nessun device/emulator: chip di ordinamento, squadra uova, uovo posizionale e
  toast **non collaudati a runtime**. I test sono statici.
- La lista mostra al massimo 20 uova per schermata: l'ordinamento completo
  resta nei dati, ma non esiste ancora una paginazione nell'UI.
- La squadra uova non influenza i raid: collegarla richiederebbe una decisione
  su un sistema esistente e non è stata presa.
- I contatori sono locali Android (`egg_inventory_view_prefs`): non esiste un
  ledger server-side dell'inventario, come per le tre slice precedenti.
- Restano aperte le voci 12, 16, 17, 18, 19 e 20 della Fase 2; le voci 18/19
  (economia e scambi P2P) richiedono decisioni su valute e validazione server.

## Build

- Comando: `SKIP_INSTALL=1 SKIP_MAP_UPDATE=1 BUILD_UNITY_AAR=1 FORCE_UNITY_AAR=1 LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 ./build_release.sh --auto`.
- Esito: `BUILD SUCCESSFUL`, `BUILD_EXIT=0`; AAR Unity ricreato dalle fonti modificate e APK firmato.
- APK: `app/build/outputs/apk/release/app-release-signed.apk`, 192.663.665 byte,
  SHA-256 `21675b306e86e9098409fb5b668c1ed3ff83b69f2900bd1fe04c164c168d5e9d`.
- Copia dell'APK precedente conservata in
  `/tmp/opencode/app-release-signed.pre-inventory.apk` (SHA-256 `53edb988…`).
- Token verificati nell'APK: `global-metadata.dat` contiene
  `EggInventoryChanged` (2), `huntix_inventory_egg_count`,
  `Le tue uova (collezione)`, `Collezione:` e `InventoryViewRequest` (2);
  `classes2.dex` contiene `EggInventoryViewManager` (5),
  `EggCollectionActivity` e `InventoryViewRequest` (2); `classes.dex` contiene
  `inventory_team`, `Uovo della Collezione` ed `EggInventoryViewManager`.
- La compilazione Unity reale ha coperto anche `GameManager.cs` e
  `HamburgerMenu.cs`, che lo stub check non compila.
- Effetti collaterali della build ripristinati (`link.xml.meta`,
  `PlayerLocomotion.controller`, `PlayerHero.prefab`).
- La presenza dei token dimostra che il codice è nella build, **non** che le
  interazioni funzionino sul device: vedi “Limiti reali”.
