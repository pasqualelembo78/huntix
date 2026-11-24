# Fase 2 · voce 16 — ricette di fusione (24/11/2026)

## Criterio e scelta

La matrice Fase 2 classificava la voce 16 come **MANCANTE come crafting**:
"ricetta" nel progetto significava solo la ricetta statica della città fittizia
(`preprocessing/city_recipe.py`, `FictionalCityWorld.cs`), non un ricettario di
fusione. Dopo la voce 15 il motore di fusione è però reale e chiamabile, e la
sua regola è già codificata in `HatchedEgg.kt` e `SavedManager.kt`.

Ho quindi scelto lo slice **additivo e in sola lettura**: un ricettario che
illustra la regola vera del motore, senza introdurre un nuovo sistema di craft,
senza imporre vincoli diversi e senza toccare la ricetta città esistente. La
regola di autonomia del Mandato §11 impone di procedere senza chiedere
indirizzi.

Il codice è originale Huntix: nessun clone esterno copiato, nessuna nuova voce
in `THIRD_PARTY_NOTICES.md`.

**Confini rigorosi**: `SavedManager.kt` non è stato modificato; a
`EggFusionCraftManager.kt` è stata aggiunta una sola chiamata di notifica dopo
una fusione già riuscita. Non sono state toccate valute, negozio, tabelle drop,
Firestore, backend, regole P2P o default. La ricetta città
(`Resources/CityRecipes`) resta intatta.

## Cosa è stato aggiunto

| File | Ruolo |
|---|---|
| `app/.../managers/EggFusionRecipeManager.kt` | Catalogo in sola lettura (3 voci), contatori di scoperta, uovo posizionale idempotente e risposta A→U |
| `app/.../managers/EggFusionCraftManager.kt` | Una sola chiamata aggiuntiva: `markUsed(...)` dopo l'esito positivo della fusione; snapshot del livello di partenza |
| `app/.../CraftActivity.kt` | Sezione “Ricette di fusione” in sola lettura: titolo, regola, gruppi pronti, stato di scoperta |
| `app/.../bridge/UnityIpc.kt` | Whitelist `RecipeRequest` (Unity → Android) |
| `app/.../bridge/Bridge.kt` | Handler: `open` apre `CraftActivity`, `snapshot` aggiorna solo lo specchio |
| `app/.../bridge/BridgeActivity.java` | Specchio passivo `recipeDiscoveredCount`, `recipeLastId`, `recipeUses` in `setMode` |
| `unity-project/Assets/City/Scripts/UI/HamburgerMenu.cs` | Voce “Ricette di fusione (guida)”, pannello allargato a 1220 px |
| `unity-project/Assets/Scripts/Core/GameManager.cs` | Ricezione `RecipeDiscoveryChanged`, ripristino contatori, `PlayerPrefs`, telemetria |
| `unity-project/Assets/City/Scripts/Diagnostics/PerfProbe.cs` | Slot `recipe` (20 slot), misura degli scambi |
| `backend/test_unity_audit_features.py` | Clausola `TestFase2Ricette` (4 test) e aggiornamento `SlotCount` |

## Regola mostrata (aderente al motore)

| Ricetta | Ingredienti | Risultato |
|---|---|---|
| Cristallo Potenziato | 3 uova Lv.1 della stessa creatura | 1 uovo Lv.2 (potenza ×2) |
| Cristallo Supremo | 3 uova Lv.2 della stessa creatura | 1 uovo Lv.3 (potenza ×4) |
| Cristallo MAXIMUM | 3 uova Lv.3 della stessa creatura | 1 uovo MAX (potenza ×8) |

Il pannello dice esplicitamente che **non occorre che i tre livelli siano
uguali**: `SavedManager.canFuse` richiede tre uova non MAX della stessa creatura
e `executeFusionSlots` usa il livello della prima uovo nello slot. Uno
"schema rigoroso" imporrebbe un comportamento diverso dal motore esistente e
quindi non è stato introdotto. I contatori “gruppi pronti” sono calcolati dalle
uova schiuse reali, in sola lettura.

## Flusso di gioco

1. Il giocatore apre **Profilo → Statistiche → 🥚 Fondi le uova** oppure la voce
   “Ricette di fusione (guida)” del menu in Miacittà (`RecipeRequest`).
2. La sezione ricette elenca le tre regole, quali sono già state scoperte e per
   quante creature ci sono uova adatte. Non ci sono pulsanti che avviano una
   fusione: si continua a usare gli slot qui sopra.
3. Quando una fusione reale riesce, `EggFusionCraftManager` chiama
   `EggFusionRecipeManager.markUsed` con il livello di partenza. La prima volta
   la ricetta viene **scoperta**: nasce un uovo posizionale idempotente
   `recipe_<da>_to_<a>` (`xpReward = 0`) con nome “Ricetta scoperta · …” e una
   sola voce nel `EggWhereLog` con luogo `ricetta→<dove>` e coordinate reali.
4. Le volte successive non creano un secondo uovo: restano una sola voce di
   evento nel diario e un contatore di utilizzi. L'`eggId` della scoperta è
   distinto da quello della fusione (`fusion_<ids ordinati>`), quindi le due
   memorie coesistono senza duplicazioni.
5. Android invia `RecipeDiscoveryChanged` a Unity con contatori e ultima
   ricetta; Unity aggiorna lo specchio in `PlayerPrefs` e la telemetria.
   `setMode` rimanda gli stessi campi in modo passivo, senza generare uova.

## Riflesso della caccia alle uova

La scoperta di una ricetta è un **luogo dove nasce un uovo**, esattamente come
esche, raccolte, crafting e collezione: stesso inventario, stesso diario,
stessa posizione GPS/ultima posizione nota. Non esiste un quinto archivio e
l'idempotenza è garantita dall'`eggId` stabile per coppia di livelli.

## Bridge bidirezionale

| Direzione | Evento/campo | Dati | Effetto |
|---|---|---|---|
| Unity → Android | `RecipeRequest` | `action=open` (o `snapshot`) | apre il ricettario / rimanda contatori |
| Android → Unity | `RecipeDiscoveryChanged` | `recipeDiscoveredCount`, `recipeLastId`, `recipeUses`, `ok` | specchio, telemetria |
| Android → Unity (`setMode`) | stessi campi | contatori | ripristino passivo |

## Verifiche eseguite in questa sessione

- `python3 backend/test_unity_audit_features.py`: **73 test OK** (4 nuovi).
- `python3 backend/test_audit_regressions.py`: **8 test OK**.
- `python3 backend/test_city_recipe.py`: **3 test OK** (la ricetta città resta intatta).
- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK**, 0 errori
  (194 warning pre-esistenti). `GameManager.cs` e `HamburgerMenu.cs` sono
  compilati dalla build Unity reale qui sotto.
- `git diff --check`: pulito.
- Build release e verifica token: vedere sezione “Build” qui sotto.

## Limiti reali

- Nessun device/emulator: apertura della sezione, scoperta delle ricette e
  contatori **non collaudati a runtime**. I test sono statici.
- La sezione non è un crafting alternativo: non crea uova dal nulla e non
  avvia fusioni. Chi si aspetta un sistema craft completo deve considerare la
  voce 16 ancora parziale sul piano delle meccaniche.
- I contatori sono locali Android (`egg_recipe_prefs`): nessun ledger server.
- Le voci 12, 17, 18, 19 e 20 restano aperte: sono documentate in
  `docs/FASE2_VOCI_RIMASTE_20261124.md` e non vengono toccate senza una
  decisione esplicita su drop, valute, server o possesso verificabile.

## Build

- Comando finale: `SKIP_INSTALL=1 SKIP_MAP_UPDATE=1 SKIP_UNITY_AAR=1 LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 ./build_release.sh --auto`.
- Esito: `BUILD SUCCESSFUL`, `BUILD_EXIT=0`; APK firmato prodotto.
- APK: `app/build/outputs/apk/release/app-release-signed.apk`, 192.675.953 byte,
  SHA-256 `fd788b2ab1db015e9a528ce0c191f3306eb4a980b9a9a07d5a5a9964264d3e51`.
- Copia dell'APK precedente conservata in
  `/tmp/opencode/app-release-signed.pre-recipes.apk` (SHA-256 `21675b30…`).
- Il primo tentativo (`BUILD_UNITY_AAR=1 FORCE_UNITY_AAR=1`) ha completato
  l'export/Il2Cpp Unity ma è fallito in `compileReleaseKotlin` per due errori
  propri del codice nuovo: un import di `EggWhereLog` nel pacchetto sbagliato e
  virgolette annidate in un template Kotlin. Corretti entrambi e verificati con
  `./gradlew compileReleaseKotlin` (`KOTLIN_EXIT=0`) prima del retry.
- L'AAR Unity prodotto dal primo tentativo è stato riutilizzato: contiene già
  `RecipeDiscoveryChanged` (2 occorrenze in `global-metadata.dat`), quindi la
  compilazione reale di `GameManager.cs` e `HamburgerMenu.cs` è avvenuta.
- Token verificati nell'APK finale: `global-metadata.dat` contiene
  `RecipeDiscoveryChanged` (2), `huntix_recipe_discovered_count`,
  `Ricette di fusione (guida)` e `Ricetta {0}`; `classes2.dex` contiene
  `EggFusionRecipeManager` (4), `RecipeRequest` (2),
  `RecipeDiscoveryChanged`, `recipeDiscoveredCount`, `recipe_`, `Ricetta scoperta`
  e `Ricetta riusata`.
- Effetti collaterali della build ripristinati (`link.xml.meta`,
  `PlayerLocomotion.controller`, `PlayerHero.prefab`).
- La presenza dei token dimostra che il codice è nella build, **non** che le
  interazioni funzionino sul device: vedi “Limiti reali”.
