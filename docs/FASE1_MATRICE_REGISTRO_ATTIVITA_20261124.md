# Fase 1 dell'audit — matrice e primo intervento (24/11/2026)

## Criterio seguito

Le 10 funzionalità "a bassa difficoltà" dell'audit sono state cercate nel codice
esistente **prima** di scrivere qualsiasi riga. Nessun sistema è stato rimosso,
disattivato, sostituito o cambiato di default. Dove esisteva una base è stata
usata quella; dove esistevano sistemi paralleli sono stati lasciati intatti.

Legenda: **COPERTO** = equivalente funzionante · **PARZIALE** = base reale ma
incompleta · **MANCANTE** = solo scaffold/log tecnico.

| # | Funzionalità | Android | Unity | Backend/Bridge | Esito |
|---|---|---|---|---|---|
| 1 | Achievement e trofei | Trofei "Egg of Growth" da livello (`PlayerProfileTabs.kt:594-644`), nessun unlock condizionale | Nessun sistema | `badges` MVC (`store.py:108-157`), nessun `AchievementUnlocked` | PARZIALE |
| 2 | **Registro attività** | `EggWhereLog.kt` registra luogo/rarità/coordinate/timestamp (max 500) ma mostrava solo il contatore | solo log diagnostici | nessun evento dedicato | **PARZIALE → primo intervento** |
| 3 | Obiettivi giornalieri/settimanali | `ResearchTaskManager.kt`: 10 daily + 6 weekly, refresh, claim, reset lunedì, 6 chiamanti reali | enum/scaffold non istanziati | stato solo in SharedPreferences, nessun evento quest | COPERTO lato Android, PARZIALE altrove |
| 4 | Titoli e badge | `LocationBadge.kt` con `earn` mai chiamato; `equippedTitleId` senza catalogo | nessun titolo | `customization` read-only | PARZIALE |
| 5 | Profilo evoluto | `PlayerProfileTabs` + XP/livelli + Firestore | menu "SIM-STYLE" con statistiche parziali | snapshot selettivo | PARZIALE |
| 6 | Catalogo collezionabili | `CodexActivity.kt` completo (scoperti, rarità, percentuale) | `EggDex.cs` + `EggDexUI.cs` con sync | `content_unlocks` lato server | COPERTO |
| 7 | Notifiche in-game | FCM + canali, ma `scheduleDailyNotifications()` senza chiamanti | toast effimeri | nessun centro notifiche | PARZIALE |
| 8 | Ricompense login | `DailyStreakManager.kt` completo + UI 30 giorni; check-in parallelo in `SavedManager` | solo enum `DAILY_LOGIN` | tabelle `daily_checkins`, endpoint checkin | PARZIALE |
| 9 | Statistiche avanzate | statistiche profilo + 6 classifiche; `logGameEvent` senza chiamanti | `SessionTelemetry` è performance, non progressione | `analytics.py`, callable `logEvent` | PARZIALE |
| 10 | Progressione XP | `PlayerProfile.kt` (soglie, titoli da livello), bridge `CityXpEarned` | autorità Android, XP via `UnityBridge` | Firestore `players` | COPERTO (migliorabile) |

## Perché si parte dal registro attività

È l'unica voce con un archivio già pieno e **nessuna vista**: `EggWhereLog`
riceve già le uova catturate in città, outdoor, indoor, quelle del budget e
quelle del compagno NPC, con `place`, `lat`, `lng`, rarità e timestamp. Creare
un nuovo "achievement log" sarebbe stato un duplicato. Aggiungere la
consultazione e le altre categorie nello stesso array è l'intervento con minor
impatto e produce subito il menu "DOVE HO TROVATO QUESTO UOVO" previsto dalla
roadmap del Mandato (voce 7).

### Riflesso della caccia alle uova

Il gioco è una caccia alle uova: il diario non è un generico log di sistema, ma
**il luogo dove ogni uovo resta ricordato con il punto esatto in cui è stato
trovato**. Ogni voce uovo mostra emoji di rarità, luogo testuale e coordinate;
le attività non-uovo convivono nello stesso storico, con filtri, così il
giocatore può rileggere "uovo trovato a città→pedone→compagno→…" accanto
all'obiettivo che lo ha portato lì.

## Cosa è stato aggiunto

- `EggWhereLog.kt`: categorie `uovo`, `missione`, `login`, `badge`, `evento`
  nello **stesso** array (le voci vecchie senza `kind` restano uova);
  `recordActivity`, `getByKind`, `countByKind`. Nessuna nuova pref, nessuna
  migrazione, limite 500 invariato.
- `ActivityLogActivity.kt` (nuova): diario consultabile con riepilogo, filtri,
  cronologia ordinata, rarità e coordinate. Registrata nel manifest.
- `PlayerProfileTabs.kt`: la riga "📍 Dove le ho trovate" diventa l'ingresso al
  diario (prima era solo un numero).
- `ResearchTaskActivity.kt`, `DailyStreakActivity.kt`, `LocationBadge.kt`:
  riscatto obiettivo, check-in e badge scrivono una voce nel diario, senza
  modificare premi o comportamenti esistenti.
- `NPCMission.cs`: missione completata in MiCittà → evento bridge
  `ActivityLogEvent` con `label`, `place` e coordinate reali (`WorldOrigin`).
- `UnityIpc.kt` / `Bridge.kt`: whitelist e handler dell'evento; Android
  registra la voce senza versare ricompense.
- `BridgeActivity.java` → `GameManager.cs`: Android restituisce
  `activityLogCount`; Unity conserva il contatore in `PlayerPrefs` e lo segna
  in `SessionTelemetry`. Restore passivo: nessun uovo e nessun compagno
  generato all'avvio.
- `PerfProbe.cs`: slot `diary` per la misura del ripristino.

## Verifiche eseguite

- `cscheck`: compilazione Unity riuscita, 0 errori.
- Test statici: 53 + 8 + 3 OK; `git diff --check` pulito.
- Build release: `BUILD SUCCESSFUL`; APK firmato 192.626.801 byte,
  SHA-256 `178cca83f4cf1e2c3993c40c30babf86313e41b02bb53638c0ef26b5298ffa82`.
- Token verificati in `global-metadata.dat` (`ActivityLogEvent`,
  `activityLogCount`, `huntix_activity_log_count`, `SendDiaryEntry`) e in
  `classes2.dex` (`ActivityLogEvent`, `EggWhereLog`, `KIND_QUEST`).
- Effetti collaterali della build ripristinati (`link.xml.meta`,
  `PlayerLocomotion.controller`, `PlayerHero.prefab`).

## Limiti reali

- Nessun dispositivo/emulator disponibile: diario, filtri e click dalla
  scheda Statistiche **non sono stati collaudati a runtime**.
- Il contatore scritto in Unity è solo uno specchio del dato Android; la cronologia
  completa resta nel profilo Android, non viene duplicata su Firestore/PostgreSQL.
- Non sono state toccate le altre 9 voci: restano aperte le loro lacune
  (achievement condizionali, badge `earn` mai chiamato, notifiche non schedulate,
  XP dei riscatti missione non accreditato, doppio check-in).

## Prossimo slice candidato

Il meno invasivo dopo questo è il **collegamento dei badge di luogo** (voce 4):
`LocationBadge.earn` esiste ma non ha chiamanti e il pref `location_badges_v1`
usato nella pulizia non coincide con `location_badges_prefs`. Va fatto come fix
additivo, con conferma esplicita prima di qualsiasi unificazione dei due
check-in.
