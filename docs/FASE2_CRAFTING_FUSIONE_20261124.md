# Fase 2 · voce 15 — crafting/fusione uova (24/11/2026)

## Criterio e scelta

La fusione uova esiste da tempo in `SavedManager.kt` (slot `fusion_slots_v2`,
`canFuse`, `executeFusionSlots`) ma **non ha nessun chiamante**: nessuna UI
Android, nessun ingresso Unity, nessun evento bridge. È il terzo caso di
"API morte" della Fase 2, dopo esche e raccolta risorse, quindi è stato scelto
come slice successivo senza richiedere decisioni all'utente (Mandato §11).

Il codice è originale Huntix: nessun clone esterno è stato copiato e non servono
nuove voci in `THIRD_PARTY_NOTICES.md`.

**Confini rigorosi**: `SavedManager.kt` non è stato modificato. Non sono state
toccate le tre valute, il negozio, le tabelle drop, Firestore, il backend, le
regole P2P o i default di alcun sistema. La vecchia via `fuseEggs` (legacy)
resta intatta e non viene richiamata.

## Cosa è stato aggiunto

| File | Ruolo |
|---|---|
| `app/.../managers/EggFusionCraftManager.kt` | Wrapper additivo: valida, chiama `executeFusionSlots`, verifica l'esito, uovo posizionale idempotente, contatori e risposta A→U |
| `app/.../CraftActivity.kt` | UI italiana: slot di fusione, uova schiuse disponibili, pulsante “Fondi le uova”, collegamento al diario |
| `app/.../ui/PlayerProfileTabs.kt` | Ingresso “🥚 Fondi le uova” nella scheda Statistiche |
| `app/src/main/AndroidManifest.xml` | Registrazione `.CraftActivity` |
| `app/.../bridge/UnityIpc.kt` | Whitelist `CraftRequest` (Unity → Android, processo app) |
| `app/.../bridge/Bridge.kt` | Handler: `action=open` apre la UI, `action=craft` fonde sugli slot pronti |
| `app/.../bridge/BridgeActivity.java` | Specchio passivo `fusionCraftCount`, `fusionCraftLastLevel`, `fusionCraftLastRarity` in `setMode` |
| `unity-project/Assets/City/Scripts/UI/HamburgerMenu.cs` | Voce “Fondi le uova (crafting)” che invia `CraftRequest` |
| `unity-project/Assets/Scripts/Core/GameManager.cs` | Ricezione `FusionCrafted`, ripristino contatori, `PlayerPrefs`, telemetria e toast |
| `unity-project/Assets/City/Scripts/Diagnostics/PerfProbe.cs` | Slot `craft` (18 slot), misura degli scambi |
| `backend/test_unity_audit_features.py` | Clausola `TestFase2Crafting` (4 test) e aggiornamento `SlotCount` |

## Flusso di gioco

1. Il giocatore apre **Profilo → Statistiche → 🥚 Fondi le uova**, oppure il
   menu di Miacittà **Fondi le uova (crafting)**. In quest'ultimo caso Unity
   invia `CraftRequest` con `action=open` e Android apre la stessa UI.
2. L'UI legge le **uova schiuse reali** di `SavedManager` e riempie i tre slot
   storici con `addToFusionSlot`; `removeFromFusionSlot` restituisce l'uovo alle
   uova schiuse, senza eliminarlo. La fusione è possibile solo con tre uova non
   MAX della stessa creatura: è la regola già presente in `canFuse`.
3. `EggFusionCraftManager.craft()` fa uno snapshot degli `istanzaId` attesi,
   chiama `executeFusionSlots` e verifica che `fusedFrom` coincida. La fusione
   storica non viene riscritta: se l'inventario è pieno e `addEgg` ha fallito,
   il wrapper **reinserisce** l'uovo fuso (senza rimuovere nulla) e segnala il
   limite nel messaggio.
4. Riflesso della caccia alle uova: ogni fusione lascia un uovo posizionale
   idempotente `fusion_<ids ordinati>` (`xpReward = 0`) nel normale inventario
   uova e **una sola** voce nello stesso `EggWhereLog`, con luogo
   `fusione→<dove>` e coordinate reali (GPS di Android o ultima posizione nota).
   Fusioni ripetute della stessa terna generano un evento idempotente, non un
   secondo uovo.
5. Android invia `FusionCrafted` a Unity con nome, livello, conta e `eggId`;
   Unity aggiorna lo specchio in `PlayerPrefs`, la telemetria e mostra un toast.
   All'apertura successiva `setMode` rimanda i contatori: ripristino passivo,
   nessuna fusione e nessun uovo generato all'avvio.

## Riflesso della caccia alle uova

- La fusione non è un'operazione contabile: è un **luogo dove nasce un uovo**
  (`Uovo della Fusione · ...`) con posizione e diario, come esche e raccolte.
- Un solo inventario uova e un solo diario: niente quarto archivio e niente
  duplicazione grazie all'`eggId` stabile derivato dalle tre uova consumate.
- Se l'inventario è pieno l'uovo posizionale non viene creato, ma il ricordo
  resta nel diario come evento: meglio un diario completo che un uovo perso.

## Bridge bidirezionale

| Direzione | Evento/campo | Dati | Effetto |
|---|---|---|---|
| Unity → Android | `CraftRequest` | `action=open` (o `craft`) | apre UI / fonde slot pronti |
| Android → Unity | `FusionCrafted` | `ok`, `name`, `level`, `rarity`, `eggId`, `count`, `message` | specchio, telemetria, toast |
| Android → Unity (`setMode`) | `fusionCraftCount`, `fusionCraftLastLevel`, `fusionCraftLastRarity` | contatori | ripristino passivo |

`CraftRequest` viaggia nel processo app perché legge/scrive le uova; gli invii
A→U passano dal `PoiUnityBridge` già esistente. Nessun nuovo servizio IPC.

## Verifiche eseguite in questa sessione

- `python3 backend/test_unity_audit_features.py`: **65 test OK** (4 nuovi).
- `python3 backend/test_audit_regressions.py`: **8 test OK**.
- `python3 backend/test_city_recipe.py`: **3 test OK**.
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori
  (194 warning pre-esistenti). `GameManager.cs` e `HamburgerMenu.cs` non sono
  coperti dallo stub check: la loro compilazione reale è nella build Unity qui
  sotto.
- `git diff --check`: pulito.
- Build release e verifica token: vedere sezione “Build” qui sotto.

## Limiti reali

- Nessun device/emulator: apertura della UI, scelta degli slot, fusione, uovo
  posizionale e toast **non collaudati a runtime**. I test sono statici.
- La fusione storica di `SavedManager` non è atomica: il wrapper verifica
  l'esito e conserva l'uovo creato, ma non può annullare una scrittura parziale
  in caso di interruzione di processo. Non è stato riscritto per non toccare
  un sistema esistente senza conferma esplicita.
- I contatori sono locali Android (`egg_fusion_craft_prefs`): non esiste un
  ledger authoritative lato server, come per esche e raccolte.
- Le uova della fusione e quelle posizionali restano distinte: la fusione
  consuma tre uova schiuse e produce l'uovo fuso; il ricordo posizionale è un
  uovo aggiuntivo con `xpReward = 0`, coerentemente con le slice precedenti.
- Le voci rimaste della Fase 2 (11, 12, 16, 17, 18, 19, 20) restano aperte.
  Le voci 18/19 (economia e scambi P2P) richiedono decisioni su valute e
  validazione server e non rientrano nel percorso additivo seguito finora.

## Build

- Comando: `SKIP_INSTALL=1 SKIP_MAP_UPDATE=1 BUILD_UNITY_AAR=1 FORCE_UNITY_AAR=1 LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 ./build_release.sh --auto`.
- Esito: `BUILD SUCCESSFUL`, `BUILD_EXIT=0`; AAR Unity ricreato dalle fonti modificate e APK firmato.
- APK: `app/build/outputs/apk/release/app-release-signed.apk`, 192.655.473 byte,
  SHA-256 `53edb98899480ae0c8e6c7157fc4ade0ea153c4972625dbd96ebf267c5cdd9c9`.
- Copia dell'APK precedente conservata in
  `/tmp/opencode/app-release-signed.pre-craft.apk` (SHA-256 `f76f2323…`).
- Token verificati nell'APK: `global-metadata.dat` contiene `FusionCrafted` (2),
  `huntix_fusion_craft_count`, `Fondi le uova (crafting)`, `Fondi le uova` e
  `Fusioni:`; `classes2.dex` contiene `EggFusionCraftManager`,
  `CraftActivity`, `CraftRequest` (2) e `fusionCraftCount`.
- La compilazione Unity reale ha coperto anche `GameManager.cs` e
  `HamburgerMenu.cs`, che lo stub check non compila.
- Effetti collaterali della build ripristinati (`link.xml.meta`,
  `PlayerLocomotion.controller`, `PlayerHero.prefab`).
- La presenza dei token dimostra che il codice è nella build, **non** che le
  interazioni funzionino sul device: vedi “Limiti reali”.
