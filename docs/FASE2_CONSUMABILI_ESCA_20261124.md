# Fase 2 · voce 13 — consumabili/esca collegati alla cattura (24/11/2026)

## Criterio e scelta

La matrice `FASE2_MATRICE_SISTEMI_GIOCO_20261124.md` ha mostrato il buco più
netto e meno duplicativo: `EggNutrimentManager.kt` definisce da tempo sei esche
con quantità, bonus per elemento e starter kit, ma `applyFood`, `consume`,
`getAvailableFoods` e `currentFoodBonus` **non avevano chiamanti reali**, e
`OutdoorManager.tryCatch(ctx, eggId, foodBonus)` riceveva sempre `1f`.

Questo slice collega quella via esistente alla cattura e all'interazione indoor.
Non è stato copiato codice dai clone esterni: tutto il codice seguente è
originale Huntix, con lo stesso header di attribuzione del progetto. Non servono
nuove voci in `THIRD_PARTY_NOTICES.md`.

**Nessun sistema pre-esistente è stato rimosso, disattivato, sostituito o
cambiato di default.** `AbilityManager` (Speed Boost), le tre valute, le scale
di rarità 4↔5 e le tabelle drop restano intatte.

## Cosa è stato aggiunto

| File | Ruolo |
|---|---|
| `app/.../managers/EggBaitManager.kt` | Gestore additivo: sceglie un aroma, lo conserva 5 minuti, cooldown 30 s, un solo lancio per esca; usa **solo** `EggNutrimentManager.consume()` come fonte delle quantità |
| `app/.../BaitActivity.kt` | UI italiana di scelta aroma, con quantità reali, bonus e annullamento |
| `app/.../ui/PlayerProfileTabs.kt` | Ingresso “🍎 Esche per le uova” nella scheda Statistiche |
| `app/src/main/AndroidManifest.xml` | Registrazione `.BaitActivity` |
| `app/.../bridge/UnityIpc.kt` | Whitelist dell'evento `ConsumableUsed` (Unity → Android) |
| `app/.../bridge/Bridge.kt` | Handler `ConsumableUsed` e passaggio di `catchBonus` alla cattura |
| `app/.../bridge/BridgeActivity.java` | Ripristino passivo di `activeBaitId`, `activeBaitBonus`, `baitCount` in `setMode` |
| `huntix/.../legacy/poi/game/CatchController.kt` | `attemptCatch(..., catchBonus: Float = 1f)` |
| `huntix/.../legacy/poi/creature/CatchEngine.kt` | Moltiplicatore sulla probabilità, clamp `0.98`; default `1f` |
| `unity-project/Assets/Scripts/Core/GameManager.cs` | Ricezione `ActiveBaitChanged`, specchio in `PlayerPrefs`, telemetria |
| `unity-project/Assets/Scripts/Indoor/InteractionManager.cs` | Invio `ConsumableUsed` con `itemId`, azione e coordinate da `WorldOrigin` |
| `unity-project/Assets/City/Scripts/Diagnostics/PerfProbe.cs` | Slot `bait` (16 slot), misura degli scambi e non del frame |
| `backend/test_unity_audit_features.py` | Clausola `TestFase2ConsumabiliEsca` (4 test) e aggiornamento `SlotCount` |

## Flusso di gioco

1. Il giocatore apre **Profilo → Statistiche → 🍎 Esche per le uova** e sceglie
   un aroma. `EggBaitManager.select()` scala **una** unità con
   `EggNutrimentManager.consume()`, aggiorna la quest `spend_food` e invia
   `ActiveBaitChanged` a Unity.
2. L'esca resta attiva 5 minuti e vale per il **primo lancio**; se scade o viene
   annullata non dà alcun bonus. Il cooldown di 30 secondi decorre dal lancio,
   non dalla scelta, così non si perde tempo se non si trova subito un uovo.
3. Alla cattura, `Bridge.kt` chiama `EggBaitManager.consumeForCatch()`: restituisce
   `1f` se non c'è un'esca attiva (comportamento storico identico), altrimenti
   il bonus base o quello calcolato da `calculateCatchBonus()` se l'elemento è
   noto. Il valore passa a `CatchController` → `CatchEngine`, che lo applica alla
   probabilità con tetto `0.98`. L'esca si consuma al primo tentativo di cattura,
   anche se il lancio fallisce per distanza: è una scelta esplicita del giocatore,
   non un premio automatico.
4. In Miacittà, quando il giocatore usa un oggetto indoor, `InteractionManager`
   invia `ConsumableUsed` ad Android con posizione geografica reale; il bridge
   scala la quantità solo se l'`itemId` corrisponde a un aroma noto e scrive la
   voce nel diario. Nessun premio, nessuna valuta toccata.
5. All'apertura di Unity, `BridgeActivity` rimanda lo specchio dell'esca attiva:
   il ripristino è passivo, non genera uova e non applica bonus alla cattura.

## Riflesso della caccia alle uova

- **Uovo posizionale una tantum per aroma**: `bait_<foodId>`, idempotente via
  `eggId` e aggiunto al normale `EggInventoryManager`. Non è un nuovo inventario.
- **Stesso diario della Fase 1**: il primo uso di ogni aroma registra
  `EggWhereLog.record()` con rarità, luogo `esca→<aroma>` e coordinate GPS (o
  `lastKnownLocation`); ogni uso registrato via UI o bridge scrive una voce
  `KIND_EVENT` con luogo e coordinate. Non esiste un secondo log.
- Rarità scelta `UNCOMMON` senza XP aggiuntivo (`xpReward = 0`): è un ricordo del
  ritrovamento, non una scorciatoia di progressione.
- `SessionTelemetry` registra l'aroma scelto/ripristinato; `PerfProbe.Measure("bait")`
  copre solo gli scambi, non aggiunge lavoro per frame.

## Bridge bidirezionale

| Direzione | Evento | Dati | Effetto |
|---|---|---|---|
| Android → Unity | `ActiveBaitChanged` | `foodId`, `bonus`, `expiresMs`, `label` | specchio HUD + telemetria |
| Android → Unity (`setMode`) | JSON `activeBaitId`, `activeBaitBonus`, `baitCount` | stato passivo | ripristino senza uova |
| Unity → Android | `ConsumableUsed` | `itemId`, `action`, `place`, `lat`, `lng` | quantità + voce nel diario |

I messaggi Unity → Android passano dal processo `:unity` grazie alla whitelist
`UnityIpc.APP_EVENTS`; Android → Unity usa il canale esistente
`PoiUnityBridge`/`UnitySendMessage`. Nessun nuovo servizio IPC.

## Verifiche eseguite in questa sessione

- `python3 backend/test_unity_audit_features.py`: **57 test OK** (inclusi i 4
  della nuova clausola `TestFase2ConsumabiliEsca`).
- `python3 backend/test_audit_regressions.py`: **8 test OK**.
- `python3 backend/test_city_recipe.py`: **3 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori
  (warning pre-esistenti degli stub Unity).
- `git diff --check`: pulito.
- Build release e verifica token: vedere sezione “Build” qui sotto.
- `backend/test_security.py`: **non eseguibile in questo ambiente** richiede il
  backend Huntix su `127.0.0.1:5000`; il servizio attivo su quella porta è un
  altro servizio (espone `/characters`, `/chat`, `/providers`), quindi i test
  d'integrazione danno 429/404 indipendentemente da questo slice.

## Limiti reali

- Nessun dispositivo o emulator Android: scelta esca, lancio con bonus, evento
  indoor e visualizzazione nel diario **non sono stati collaudati a runtime**.
  I test statici controllano i collegamenti nei sorgenti, non il comportamento
  sul telefono.
- L'elemento dell'uovo non è noto al momento del lancio outdoor, quindi viene
  usato il bonus base dell'aroma; il bonus elemento/amore-odio resta disponibile
  al vecchio percorso `applyFood`, intatto.
- Le esche sono consumabili locali Android (`egg_food_prefs`), non sincronizzate
  con il backend: questo slice non introduce un ledger o un acquisto server-side.
  Le tre valute parallele restano non riconciliate, come da matrice Fase 2.
- `AbilityManager` “Speed Boost” è sorella di questa voce ma è stata lasciata
  intatta: unificarla alle esche cambierebbe due sistemi e richiede conferma.

## Build

- Comando: `SKIP_INSTALL=1 SKIP_MAP_UPDATE=1 BUILD_UNITY_AAR=1 FORCE_UNITY_AAR=1 LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 ./build_release.sh --auto`.
- Esito: `BUILD SUCCESSFUL`, `BUILD_EXIT=0`; AAR Unity ricreato dalle fonti modificate e APK firmato.
- APK: `app/build/outputs/apk/release/app-release-signed.apk`, 192.639.089 byte,
  SHA-256 `3d779ad8a759cfe07033f0648bc057ea4383beb9c417758170b5dde516430080`.
- Copia dell'APK precedente conservata in
  `/tmp/opencode/app-release-signed.pre-bait.apk` (SHA-256 `178cca83…`).
- Token verificati nell'APK: `global-metadata.dat` contiene `ActiveBaitChanged`
  (2), `huntix_active_bait` (1), `huntix_bait_count` (1), `ConsumableUsed` (1) e
  `Aromi disponibili` (1); `classes2.dex` contiene `EggBaitManager` (2),
  `BaitActivity` (1), `ActiveBaitChanged` (1), `ConsumableUsed` (2),
  `mela_dolce` (1) e `bait_` (1).
- Effetti collaterali della build ripristinati (`link.xml.meta`,
  `PlayerLocomotion.controller`, `PlayerHero.prefab`): nessuna loro modifica
  entra nel commit.
- La presenza dei token dimostra che il codice è nella build, **non** che le
  interazioni funzionino sul device: vedi “Limiti reali”.
