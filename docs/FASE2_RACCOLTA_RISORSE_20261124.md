# Fase 2 · voce 14 — raccolta risorse (24/11/2026)

## Criterio e scelta

Dopo il slice 13 (esche) la regola di autonomia appena aggiunta al Mandato
(§11) impone di procedere senza domande su come continuare. La voce 14 era
l'unica delle restanti con una base reale già usata (`StoreBuilder.cs` ha cinque
oggetti `collect` indoor) e senza dipendenze obbligatorie da valute, shop,
Firestore o tabelle drop: è stata quindi scelta come secondo slice.

**Confini rigorosi**: nessuna modifica a `CatchEngine`/`CatchController`, al
`catchBonus` delle esche, a `CatchToolManager` (secchielli), a MVC/gemme/wallet,
alle tabelle drop, a Firestore o al backend. Non esiste un quarto inventario di
risorse: i contatori sono locali e le uova vanno nell'inventario uova esistente.

## Cosa è stato aggiunto

| File | Ruolo |
|---|---|
| `app/.../managers/EggResourceHarvestManager.kt` | Contatori locali per tipo, cooldown 15 s, uovo posizionale una tantum `harvest_<id>`, voce nel diario con coordinate |
| `app/.../ResourceHarvestActivity.kt` | Vista italiana in sola lettura: raccolte totali, tipi, uova corrispondenti, link al diario |
| `app/.../ui/PlayerProfileTabs.kt` | Ingresso “🧺 Raccolte nel mondo” nella scheda Statistiche |
| `app/src/main/AndroidManifest.xml` | Registrazione `.ResourceHarvestActivity` |
| `app/.../bridge/UnityIpc.kt` | Whitelist `ResourceHarvested` (Unity → Android) |
| `app/.../bridge/Bridge.kt` | Handler dedicato: non riusa `ConsumableUsed`, non scala esche |
| `app/.../bridge/BridgeActivity.java` | Specchio dei contatori in `setMode` (`resourceHarvestCount`, `resourceHarvestKinds`, `resourceHarvestDetail`) |
| `unity-project/Assets/Scripts/Indoor/InteractionManager.cs` | Invio `ResourceHarvested` solo per `action == "collect"`, con itemId, luogo e coordinate `WorldOrigin` |
| `unity-project/Assets/Scripts/Core/GameManager.cs` | Ripristino passivo dei contatori, `PlayerPrefs` e `SessionTelemetry` |
| `unity-project/Assets/City/Scripts/Diagnostics/PerfProbe.cs` | Slot `harvest` (17 slot), misura degli scambi |
| `backend/test_unity_audit_features.py` | Clausola `TestFase2RaccoltaRisorse` (4 test) e aggiornamento `SlotCount` |

## Flusso di gioco

1. In Miacittà il giocatore raccoglie uno scaffale, il pane o la frutta
   (`snack`, `water`, `can`, `bread`, `fruit`). `InteractionManager` invia
   `ResourceHarvested` con `itemId`, nome del luogo e lat/lng reali ricavati da
   `WorldOrigin`. L'evento è distinto da `ConsumableUsed` (che esclude
   `action == "collect"`), quindi la raccolta non consuma un'aroma, non entra
   nella catena di cattura e produce **una sola** voce nel diario.
2. Android registra la raccolta in `egg_resource_prefs` con cooldown di 15
   secondi per tipo, scrive una voce `KIND_EVENT` nello **stesso** `EggWhereLog`
   delle uova con luogo `raccolta→<luogo>` e coordinate (se Unity non le invia,
   si usa `lastKnownLocation`).
3. Alla **prima** raccolta di ogni tipo, il giocatore riceve un uovo posizionale
   idempotente `harvest_<id>` (`COMMON`, `xpReward = 0`) nel normale inventario
   uova e una voce `EggWhereLog.record()` con nome, rarità, luogo e coordinate.
   Le raccolte successive non danno altri uova: aggiornano solo il contatore e
   il diario.
4. All'apertura di Unity, `BridgeActivity` rimanda i contatori: il ripristino è
   passivo, non genera uova e non applica premi.
5. La scheda Statistiche mostra “🧺 Raccolte nel mondo” con totali, tipi e
   pulsante per aprire il diario delle uova posizionali.

## Riflesso della caccia alle uova

- Ogni risorsa è un **luogo dove nasce un uovo**, non un generico materiale:
  `Uovo della Raccolta · Pane fresco`, raccolto in `raccolta→Panificio` alle
  coordinate del mondo.
- Un solo inventario uova (`EggInventoryManager`) e un solo log
  (`EggWhereLog`): niente secondo diario, niente duplicazione di uova grazie
  all'`eggId` stabile.
- `SessionTelemetry` e `PerfProbe.Measure("harvest")` coprono invio e
  ripristino: nessun costo per frame e nessun nuovo loop.

## Bridge bidirezionale

| Direzione | Evento/campo | Dati | Effetto |
|---|---|---|---|
| Unity → Android | `ResourceHarvested` | `itemId`, `place`, `lat`, `lng` | contatore + uovo posizionale + diario |
| Android → Unity (`setMode`) | `resourceHarvestCount`, `resourceHarvestKinds`, `resourceHarvestDetail` | contatori | specchio passivo in `PlayerPrefs`, nessun uovo |

## Verifiche eseguite in questa sessione

- `python3 backend/test_unity_audit_features.py`: **61 test OK** (4 nuovi).
- `python3 backend/test_audit_regressions.py`: **8 test OK**.
- `python3 backend/test_city_recipe.py`: **3 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori
  (194 warning pre-esistenti degli stub Unity).
- `git diff --check`: pulito.
- Build release e verifica token: vedere sezione “Build” qui sotto.

## Limiti reali

- Nessun device/emulator: raccolta indoor, evento bridge, uovo posizionale e
  diario **non collaudati a runtime**. I test sono statici.
- I contatori sono locali Android (`egg_resource_prefs`): non esiste un ledger
  server-side authoritative per le risorse, come del resto per le esche.
- Non sono state create nuove resource node nel mondo aperto: il flusso usa
  solo gli oggetti `collect` indoor già presenti in `StoreBuilder.cs`.
- Le voci 11, 12, 15, 16, 17, 18, 19 e 20 della Fase 2 restano aperte; la 18
  (economia) e la 19 (scambi P2P) richiedono decisioni su valute e validazione
  server, fuori dal percorso additivo seguito finora.

## Build

- Comando: `SKIP_INSTALL=1 SKIP_MAP_UPDATE=1 BUILD_UNITY_AAR=1 FORCE_UNITY_AAR=1 LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 ./build_release.sh --auto`.
- Esito: `BUILD SUCCESSFUL`, `BUILD_EXIT=0`; AAR Unity ricreato dalle fonti modificate e APK firmato.
- APK: `app/build/outputs/apk/release/app-release-signed.apk`, 192.639.089 byte,
  SHA-256 `f76f2323751a45b96d143860a5ce657751a35803f230b470a10dcf82af1e0e75`.
- Copia dell'APK precedente conservata in
  `/tmp/opencode/app-release-signed.pre-harvest.apk` (SHA-256 `3d779ad8…`).
- Token verificati nell'APK: `global-metadata.dat` contiene `ResourceHarvested`,
  `huntix_resource_harvest_count`, `Raccolte:` e `resourceHarvestCount`;
  `classes2.dex` contiene `EggResourceHarvestManager` (5),
  `ResourceHarvestActivity` (1), `ResourceHarvested` (2) e `harvest_` (1).
- Effetti collaterali della build ripristinati (`link.xml.meta`,
  `PlayerLocomotion.controller`, `PlayerHero.prefab`).
- La presenza dei token non dimostra il funzionamento a runtime: vedi “Limiti reali”.
