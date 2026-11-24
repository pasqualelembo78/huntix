# HUNTIX — Sintesi integrazione migliorie da 12 progetti Unity open source

Branch: `integrazione-open-source` (da `integrazione-clone` @ `b158e45a`). `main` intoccato,
nulla pushato. Analisi estesa: `docs/HUNTIX_OPEN_SOURCE_IMPROVEMENTS_AUDIT.md`.
Attribuzioni: `THIRD_PARTY_NOTICES.md` (nuova sezione "Audit open source 2026-11-24").

**Regola applicata**: da ogni repository è stata ricavata almeno una miglioria concreta.
Dove il codice non era riutilizzabile è stata adattata una tecnica, un'architettura o un
comportamento. Nessun default cambiato, nessun sistema rimosso/disattivato/sostituito,
nessun tocco a valute, Firestore, drop o P2P. Tutte le opzioni sono **opt-in**.

## I 12 contributi

| # | Progetto | Licenza | Contributo concreto | File principale |
|---|---|---|---|---|
| 1 | SanAndreasUnity | MIT | Culling uova a 1 Hz con grazia 3 s e isteresi 60/70 m | `City/Scripts/Economy/EggCullGrace.cs` |
| 2 | MafiaUnity | Apache-2.0 | Latch 1 s dopo teleport riuscito, solo se attivo; `BUSSA` intatto | `City/Scripts/World/InteractDoor.cs` |
| 3 | Android_Unity3D_OpenWorld | nessuna | Bussola uova in guida, <2 m/s, max 15°/s, uovo entro 120 m | `City/Scripts/Player/EggCompassAssist.cs` |
| 4 | 3ddriving | nessuna (solo idea) | Diario uova locale atomico `.tmp`/`.backup`, versione 1, max 200 voci | `City/Scripts/Economy/EggJournal.cs` |
| 5 | dbrizov/Unity-CharacterController | MIT | Raggio SphereCast camera calcolato dal frustum, opt-in | `City/Scripts/Player/CameraCollisionRadius.cs` |
| 6 | rob1997/Controller | MIT | `EventBus.SubscribeOnce<T>` e prima voce uovo nel diario Android | `City/Scripts/Environment/EventBus.cs` |
| 7 | unity-ai-npc | MIT | Persona NPC offline con indizio uovo, zero rete/LLM | `City/Scripts/NPC/CityNpcPersonaDefinition.cs` |
| 8 | ai-npc-quest-framework | MIT | Definizione missione opzionale data-driven, generatore intatto | `City/Scripts/Economy/NpcMissionDefinition.cs` |
| 9 | Unity-Rpg | MIT | Audit `asset-handles`: ID/provenienza dei registry esistenti | `City/Scripts/Diagnostics/OpenSourceAudit.cs` |
| 10 | OpenStash | MIT dichiarato, LICENSE assente | Audit `resource-defs`: path `Resources` e ID unici | `City/Scripts/Diagnostics/OpenSourceAudit.cs` |
| 11 | Grand Road Auto | proprietaria | Audit `load-lifecycle`: 6 invarianti in sola lettura | `City/Scripts/Diagnostics/OpenSourceAudit.cs` |
| 12 | Quaternius | CC0 / QAL | Manifesto per-pack con URL e licenza, senza import mesh | `City/Scripts/Diagnostics/OpenSourceAudit.cs` |

## Infrastruttura condivisa

- `City/Scripts/Diagnostics/OpenSourceFeatureBridge.cs`: pref `huntix_os_<id>`, Unity→Android
  `OpenSourceFeatureChanged`, Android→Unity `openSourceFeatures` nel JSON `setMode`, uovo
  posizionale via `FeatureEggHidden`, `PerfProbe`/`SessionTelemetry`.
- `City/Scripts/Diagnostics/OpenSourceFeatureCommands.cs`: comandi QA `osfeature` / `osaudit`.
- `Assets/Editor/OpenSourceAuditMenu.cs`: quattro voci menu `Huntix/Audit …`.
- Android: `UnityIpc.kt` (whitelist evento), `Bridge.kt` (pref e indizio nel diario),
  `BridgeActivity.java` (rimando flags), `Scripts/Core/GameManager.cs` (ripristino passivo).
- `PerfProbe.SlotCount` 20 → **21** (nuovo slot in coda `egg-hint`; indici esistenti invariati).

## File toccati (principali)

- **Nuovi script Unity (+ `.meta`)**: `OpenSourceFeatureBridge.cs`, `OpenSourceFeatureCommands.cs`,
  `OpenSourceAudit.cs`, `EggCullGrace.cs`, `EggJournal.cs`, `EggCompassAssist.cs`,
  `CameraCollisionRadius.cs`, `CityNpcPersonaDefinition.cs`, `NpcMissionDefinition.cs`.
- **Modificati**: `Game.cs` (inizializzazioni e journal), `GameManager.cs`, `EventBus.cs`,
  `PerfProbe.cs`, `EggSpawnManager.cs`, `InteractDoor.cs`, `CameraRig.cs`, `UIManager.cs`,
  `NPCMission.cs`, `UnityIpc.kt`, `Bridge.kt`, `BridgeActivity.java`, stubs `cscheck`.
- **Documenti**: audit completo e questa sintesi, `THIRD_PARTY_NOTICES.md`.

## Test eseguiti

- `python3 backend/test_unity_audit_features.py` — **96 OK** (73 baseline + 23 nuovi).
- `python3 backend/test_city_recipe.py` — **3 OK**.
- `cd backend && ./venv/bin/python -m unittest -q test_audit_regressions` — **8 OK**.
- `cd backend/preprocessing/cscheck && ./check.sh` — **OK**, 0 errori, 194 warning noti.
- `./gradlew :app:compileReleaseKotlin --offline -q` — **OK**.

## Non eseguiti / rischi residui

- Nessun device o emulatore: touch, camera in guida, porte e dialoghi non collaudati a runtime.
- La build Unity AAR/APK non è ancora stata ricostruita in questa sessione: `cscheck` non copre
  `GameManager.cs`, `HamburgerMenu.cs` e i file esclusi dal suo elenco.
- Nessun risultato di performance inventato: i target `PerfProbe` sono solo strumentati.
- Rischi principali: bussola auto-rotante (mitigata da soglia/limite angolare), journal locale
  non autoritativo, latch porte 1 s, raggio camera in interni stretti, audit via reflection
  in sola lettura. Tutti gli innesti restano disattivabili senza toccare i percorsi storici.
