# Audit tecnico Huntix — 23/11/2026

Branca: `flat-world` · Commit iniziale: `2c7f3ef` · Commit dei fix terreno: `ca20fd4`

> Stato dell’audit: **eseguito su sorgenti, dati locali, log e documentazione reali**.
> Non sono stati usati produzione, dati utente reali, Firebase Emulator (non
> installato) o dispositivi Android (nessuno collegato). Ogni voce riporta uno
> stato esplicito: `CONFERMATO`, `CORRETTO`, `VERIFICATO`, `NON RIPRODUCIBILE`,
> `NON VERIFICATO`, `BLOCCATO`, `RICHIEDE INTERVENTO MANUALE`.

---

## 1. Executive summary

1. **Fix terreno/performance già applicati e committati** in `ca20fd4`: PhysX
   `autoSyncTransforms=false` con sincronizzazioni esplicite, collider terreno
   limitato a LOD0, sonde NPC sfalsate, filtro strade per bbox, pool mesh,
   `LogDebug` in release, `RaycastNonAlloc`, singolo `sharedMesh`.
   - `cscheck`: `Compilation succeeded - 189 warning(s)` / `CSCHECK OK`.
   - Build release reale: `EXIT=0`.
   - APK firmato: `app/build/outputs/apk/release/app-release-signed.apk`
   - SHA-256: `a4ec40f2a5592cca0102d7cb1adae6906970441d419e5f19a89b91787ad5fdbc`
   - **Nessuna nuova misurazione FPS/RAM**: serve sessione su device con viewer in-app.

2. **Correzioni dell’audit applicate in questa sessione** (da committare):
   - `AU-01` logout non revocava l’access token: blacklist scritta come stringa,
     letta come tupla `(jti, exp)`. **CORRETTO e VERIFICATO** con test che
     riproduce il bug prima del fix.
   - `CR-01` cifratura con fallback deterministico e plaintext quando manca
     `cryptography`. **CORRETTO e VERIFICATO** con 6 test locali;fail-closed
     all’avvio e migrazione esplicita dei dati storici.
   - `BR-01` preloader tile Android scriveva `.v4.*`, Unity legge `.v5.*`.
     **CORRETTO** (costante allineata) e compilazione Kotlin riuscita.

3. **Criticità Firestore**: diverse regole troppo larghe sono `CONFERMATO` dal
   sorgente, ma **NON CORRETTE** perché non esiste Firebase CLI/emulatore per
   provarle e un intervento alle regole attive senza test potrebbe interrompere
   flussi legittimi (team, amici, cancellazione dati). Sono documentate con
   patch proposta e test da eseguire.

4. **Cleartext Android**: `usesCleartextTraffic=true`, Network Security Config
   con eccezione per l’IP pubblico di produzione `82.165.218.56:5100`.
   **CONFERMATO** il traffico HTTP di auth/JWT, tile e multiplayer. **BLOCCATO**:
   rimuoverlo senza TLS su 5100 romperebbe il gioco.

5. **Backend lifecycle/auth**: blacklist in memoria, ban con timezone mismatch,
   ruolo admin letto dal JWT, token persistente non revocato, thread e cleanup
   incompleti, `_free_port` che può uccidere processi estranei. Parte è
   `CONFERMATO` dal codice, ma gli interventi strutturali richiedono decisioni
   su deployment/migrazione: documentati, non applicati alla cieca.

6. **Test eseguiti in questa sessione**:
   - 8 test `unittest` di regressione: **8/8 OK**;
   - validazione configurazione crittografica reale: **OK** (valore chiave non mostrato);
   - compilazione Kotlin release: **BUILD SUCCESSFUL**;
   - i 9 test JVM Android **NON sono stati eseguiti**: in modalità offline
     manca `junit:junit:4.13.2` nella cache Gradle (dettaglio §8).

---

## 2. Architettura esaminata

- **Unity** (`unity-project/Assets/City/Scripts`): mondo OSM a chunk, terreno,
  strade, edifici, veicoli, NPC, bridge `UnityBridge`, snapshot `CityWorldSync`,
  client tile `TileClient`.
- **Android** (`app/src/main`): profilo/SharedPreferences, `CityStateSync`,
  `CityTilePreloader`, `RealLifeAuth/Client`, AIDL `IUnityBridge`/`IUnityCallback`.
- **Backend Python** (`backend/`): FastAPI + Socket.IO, auth FastAPI attiva,
  blueprint Flask residuo non montato, cifratura `storage/crypto.py`, tile server,
  traffic simulation.
- **Firebase** (`firebase/firestore.rules`, master in
  `firebase/firebase_security_rules.txt`, `functions/index.js`): regole e poche
  Cloud Functions; il deploy regole rigenera `firestore.rules` dal master.
- **Dati/POI/tile**: `backend/preprocessing`, cache geo/graph, SRTM locale.

---

## 3. Metodo di audit

1. Verifica branch, HEAD, `git status`, commit e modifiche preesistenti.
2. Backup verificabile in `/tmp/opencode/huntix_audit_backup_20261123/`:
   `git_status_before.txt`, `staged_terrain_fixes.patch`,
   `unstaged_before.patch`, `head_before.txt`, `SHA256SUMS`, copie dei file
   modificati in `files_before_fix/`, hash APK.
3. Quattro analisi in sola lettura: Firestore; backend crypto/auth/lifecycle;
   Android cleartext/sync/bridge; Unity performance/test/repository.
4. Correzioni **solo additive/reversibili** e con test locale prima/dopo.
5. Nessun test distruttivo, nessun accesso a produzione, nessun dato utente reale.

**Ripristino backup**: le copie dei file prima delle correzioni sono in
`files_before_fix/`; per annullare una correzione copiare il file dal backup
sopra quello attuale e rieseguire i test. Lo stato Git pre-audit è ricostruibile
da `git_status_before.txt` e dalle patch salvate.

---

## 4. Criticità confermate

| ID | File / funzione | Evidenza | Stato |
|---|---|---|---|
| `AU-01` | `backend/app_routes/auth.py:394-407`, `backend/auth_fastapi.py:214-216` | logout aggiungeva una stringa `jti`; `_verify_jwt` confronta `t[0]`/`t[1]` come tupla | `CONFERMATO` → `CORRETTO` |
| `CR-01` | `backend/storage/crypto.py:11-49` (versione pre-fix) | fallback `sha256(b"huntix-insecure-fallback-key")`, plaintext su `ImportError`, `""` su errore | `CONFERMATO` → `CORRETTO` |
| `BR-01` | `CityTilePreloader.kt:36`, `TileClient.cs:89` | Android scriveva `.v4.*`, Unity cerca `.v5.*` | `CONFERMATO` → `CORRETTO` |
| `FS-01` | `firebase/firestore.rules:82-85` | membro può cambiare `leaderId`, `members`, `totalXp` | `CONFERMATO` (sorgente) |
| `FS-07` | `firebase/firestore.rules:119-130` | amici: scrittura da entrambi senza whitelist | `CONFERMATO` (sorgente) |
| `FS-05` | `firebase/firestore.rules:197-200,231-236` | stats con soli limiti superiori; delete molto probabilmente negata da `request.resource.data` | `CONFERMATO` (regole) |
| `FS-10` | `firebase/firestore.rules:81` | creazione team: solo `leaderId` controllato | `CONFERMATO` (sorgente) |
| `CL-01` | `AndroidManifest.xml:33`, `network_security_config.xml:16-21` | cleartext globale + eccezione IP produzione | `CONFERMATO` |
| `SY-01` | `CityStateSync.kt:34-47` | snapshot salvato prima della validazione | `CONFERMATO` |
| `SY-02` | `CityStateSync.kt:27-29`, `CityWorldSync.cs:37` | `ver` del JSON mai letto | `CONFERMATO` |
| `BP-01` | `backend/app.py:71-111` | `_free_port` fa `lsof` + `SIGTERM`/`SIGKILL` su PID estranei; default `PORT=5000` vs produzione `5100` | `CONFERMATO` (codice) |
| `AU-02` | `auth_fastapi.py:338-345` ecc. | `banned_until` naive vs confronto aware, eccezione inghiottita | `CONFERMATO` (codice) |
| `AU-03` | `auth_fastapi.py:398-408` | ruolo admin letto dal payload JWT, non dal DB | `CONFERMATO` |
| `AU-04` | `auth_fastapi.py:309-323`, `app_routes/auth.py:372-391` | `/auth/reauth` non controlla ban e persistent token non revocato dal logout | `CONFERMATO` |
| `P-00` | working tree pre-commit | 15 file fix terreno non ancora committati | `CORRETTO` con `ca20fd4` |

---

## 5. Criticità corrette

### AU-01 — Revoca access token
- **Prima** (test `test_vecchio_comportamento_non_revocava`): token restava valido dopo l’aggiunta della stringa `jti`.
- **Dopo**: `blacklist_access_token(payload)` inserisce `(jti, exp)`; `_verify_jwt` restituisce `None`.
- **File**: `backend/auth_fastapi.py:40`, `backend/app_routes/auth.py:18,403`.
- **Test**: `backend/test_audit_regressions.py` — 3 test specifici, tutti OK.
- **Limite**: la blacklist resta in memoria e non condivisa tra worker; oggi `start_backend.sh` avvia un solo processo, quindi il problema multi-worker resta `NON RIPRODUCIBILE` nella configurazione attuale ma va risolto prima di abilitare `--workers`.

### CR-01 — Cifratura fail-closed
- **Prima**: chiave Insecure fissa; senza `cryptography` il valore veniva salvato in chiaro.
- **Dopo**:
  - `MASTER_KEY` obbligatoria (`CryptoConfigurationError` se assente);
  - `cryptography` obbligatoria, più nessun ritorno di plaintext;
  - `validate_crypto_config()` chiamata all’avvio in `backend/app.py:25,120`;
  - `decrypt_with_legacy_key(ciphertext, chiave)` solo per migrazione offline esplicita.
- **Test**: 6 test — chiave assente, libreria assente, roundtrip, chiave diversa, migrazione legacy, input vuoto. Tutti OK.
- **Compatibilità**: la derivazione Fernet (`sha256(master)` → base64url) e il troncamento a 64 caratteri sono invariati, quindi i token cifrati con una `MASTER_KEY` reale restano leggibili. I token cifrati con la vecchia chiave Insecure **non** vengono più letti automaticamente: va eseguita la migrazione offline descritta in §19.

### BR-01 — Cache tile Android/Unity
- `CACHE_VERSION` portato da `4` a `5`, commento aggiornato.
- **Verifica**: `./gradlew :app:compileReleaseKotlin --offline` → `BUILD SUCCESSFUL`.
- **Effetto atteso**: il preloader scrive file che Unity legge davvero, eliminando un doppio download. **NON VERIFICATO su device**: serve controllo dei file `.v5.*` in `huntix_tiles` durante una sessione reale.

---

## 6. Criticità non corrette

| ID | Stato | Motivo |
|---|---|---|
| `FS-01`…`FS-10` | `BLOCCATO` | Nessun Firebase CLI/emulatore; regole attive non testabili. Patch proposta allegata, non applicata. |
| `CL-01` | `BLOCCATO` | Il backend usa HTTP su `82.165.218.56:5100`; senza TLS alternativo rimuovere l’eccezione rompe auth/tile/multiplayer. |
| `AU-02`, `AU-03`, `AU-04` | `RICHIEDE INTERVENTO MANUALE` | Richiedono una scelta progettuale su schema DB, durata token e revoca multi-dispositivo. |
| `BP-01` | `RICHIEDE INTERVENTO MANUALE` | `_free_port` va rimosso solo dopo aver confermato come viene avviato il backend in produzione; non eseguire test su porte reali. |
| `P-01`…`P-09` | `BLOCCATO` | Nessun dispositivo; i numeri del report sonostorici e non nuove misure. |
| `SY-01`…`SY-05` | `BLOCCATO` | Fix tocca profilo, Firestore e bridge: richiede test su Android/emulatore e conferma della semantica di `cityMoney`. |

### Proposta regole Firestore (NON applicata)

Per `teams` (create/update separati), `friends`, `mini_game_stats` (delete separata),
`world_eggs`, `gym_trainings` la direzione corretta è:

```text
create teams: hasOnly([...14 campi...]) + leaderId==uid + members==[uid] + memberCount==1
update teams:
  membro    -> affectedKeys().hasOnly(['members','memberCount'])
  leader    -> affectedKeys().hasOnly(['name','tag','description','isOpen','leaderId'])
  aggregati -> solo Cloud Function/transaction
friends/{userId}/list/{friendId}:
  write owner -> hasOnly(['name','addedAt'])
  create friend -> solo valori attesi; delete solo owner
mini_game_stats: keys().hasOnly([...]) + is number() + >=0 + updatedAt==request.time
  allow delete: if isOwner(userId)   // altrimenti la delete dati web fallisce
world_eggs update: resource.caught==false && request.caught==true && caughtBy==uid
gym_trainings update: resource.userId==uid + score 0..100 + completed bool + updatedAt==request.time
```

**Non applicata** perché qualsiasi refuso here negherebbe scritture reali senza
poter eseguire `firebase emulators:exec`. Le regole vanno modificate prima in
`firebase/firebase_security_rules.txt` (master) e poi rigenerate con
`scripts/deploy_rules.sh`, mai solo in `firestore.rules`.

---

## 7. Test effettuati

| Comando | Esito |
|---|---|
| `cd backend && ./venv/bin/python -m unittest -v test_audit_regressions` | `Ran 8 tests ... OK` |
| `./venv/bin/python -m py_compile auth_fastapi.py app.py storage/crypto.py app_routes/auth.py test_audit_regressions.py` | OK |
| validazione crypto con `.env` reale | `CONFIG CRIPTO REALE: OK` |
| `cd backend/preprocessing/cscheck && ./check.sh` | `Compilation succeeded - 189 warning(s)` / `CSCHECK OK` |
| `SKIP_INSTALL=1 SKIP_MAP_UPDATE=1 BUILD_UNITY_AAR=1 ./build_release.sh --auto` | `EXIT=0`, APK SHA-256 `a4ec40f2…` |
| `./gradlew :app:compileReleaseKotlin --offline` | `BUILD SUCCESSFUL in 1m 16s` |
| `git diff --cached --check` prima del commit terreno | exit 0 |

Nota: `cscheck` e build APK sono stati eseguiti **dopo** i fix terreno e **prima**
delle correzioni audit; quest’ultime non toccano codice C# Unity, quindi il
risultato resta valido per il codice C#. La modifica Kotlin è stata ricompilata
separatamente con successo.

---

## 8. Test non effettuati

| Test | Stato | Blocco |
|---|---|---|
| Suite autorizzazioni Firestore FS-01…FS-10 | `BLOCCATO` | `firebase` CLI assente, `firebase.json` senza blocco `emulators`, nessun `@firebase/rules-unit-testing` nel repo. Nessun test scritto ed eseguito. |
| `backend/test_security.py` | `NON ESEGUITO` | Richiede server vivo su `127.0.0.1:5000`; la fixture registra utente senza `birth_date`, campo ora obbligatorio → fallirebbe. |
| `backend/penetration_test_checklist.md` | `NON ESEGUITO` | 122 voci manuali, 0 spuntate; richiede Burp/ZAP e staging. |
| Test JVM Android (`EggRarityTest`, `WorldEggTest`) | `BLOCCATO` | `./gradlew :app:testReleaseUnitTest --offline` → `Could not resolve junit:junit:4.13.2` (non in cache offline). |
| Test Unity NUnit | `NON ESISTENTI` | Nessun `[Test]`, nessun `test-framework` nel manifest. |
| Profilazione device (FPS/RAM/GC/GPU/camera) | `BLOCCATO` | `adb devices` → nessun dispositivo. Serve nuova sessione con APK `a4ec40f2…` e viewer in-app. |
| Test produzione Firebase/backend | `NON ESEGUITI` | Vietato dal mandato dell’audit. |

---

## 9. Risultati di sicurezza

- **Auth**: revoca access token ripristinata (`AU-01`). Restano: blacklist non
  condivisa, ban timezone, ruolo da JWT, reauth persistent token, limiter non
  inizializzato in `app.py`, security header assenti, user enumeration parziale.
- **Cifratura**: più nessun fallback noto né plaintext automatico (`CR-01`).
  Restano: nessuna rotazione versionata nel token Fernet, backup di `.env` in
  chiaro in `/var/backups/huntix/env_*`, Android SharedPreferences plaintext per
  password/token.
- **Firestore**: regole troppo larghe su collezioni senza client reale (teoria)
  e su team/amici/stats (confermate dal sorgente). Nessuna exploit verificato.
- **Rete**: cleartext confermato verso IP pubblico di produzione; nessun client
  WebSocket/Socket.IO trovato (Unity usa polling HTTP).
- **Porte**: codice `_free_port` pericoloso; oggi configurazione single-process
  con porta 5100, quindi impatto concreto non riprodotto.

---

## 10. Risultati performance

- Correzioni statiche **verificate nel codice**: `LoadRadius=3` (49 chunk),
  `MaxConcurrentBuilds=1`, `BuildBudgetMs=12`, `MaxPlacedBuildingsPerChunk=320`,
  `MaxEnterablePerChunk=8`, `CarsPerTile=22`, `MaxCars=180`,
  `CityStyle.QuaterniusAllowed=false`, governor a secondi (`5695e45`), camera
  snap dinamico (`aa506d0`), URP render scale 0,75 / HDR off.
- Secondo intervento (commit `ca20fd4`): vedi §1.
- **Nessun nuovo dato FPS/frame/RAM/GC/GPU**: i valori
  `8–15 FPS`, `166→556 MB`, `13.614 edifici`, `3.054 veicoli`, `camSnap 11/17`
  sono **storici** e non possono essere usati come verifica di questi fix.

---

## 11. Risultati sincronizzazione

- `CityStateSync.onCitySnapshot()` salva JSON e timestamp prima di validare;
  `ver` del JSON è inerte; `cityMoney` è replacement mentre commenti e profilo
  dicono “max visto”; `cityPeopleKnown`/`cityEggDexCount` sono max monotonici;
  `foundContainerEggs` è unione; `jobs/family/home` sono replacement.
- `checkSync()` calcola `ok` solo dalla presenza di campi nello snapshot, non
  dal confronto con il profilo: può dire `ok:true` con profilo non allineato.
- `applyStoredToProfile()` non ha chiamanti; `startupSync()` persiste due volte.
- Firestore profilo usa `set(..., merge())` senza timestamp/versione → last-writer-wins.
- Nessuna correzione applicata: serve conferma della semantica attesa di
  `cityMoney` e test su Android con snapshot sintetici, non reali.

---

## 12. Risultati architetturali

- Due sistemi auth coesistono: FastAPI attivo e Flask blueprint non montato.
  Il Flask `/auth/local-login` accetta username senza password: **se venisse
  riattivato** sarebbe un accesso non autorizzato. `NON RIPRODUCIBILE` oggi
  perché non è montato.
- Bridge Android↔Unity usa AIDL `oneway` per eventi, senza envelope con
  `seq`/`ack` ni retry persistenti; `Bridge.saveData/loadData` sono stub.
- Snapshot Unity passa da `UnityBridge` a prefs e profilo; il ripristino avviene
  solo se `IsStateFresh()`.
- Mandato “universo unico” rispettato: nessuna funzionalità o asset rimosso.

---

## 13. Risultati repository

- `git ls-files`: 13.291 file tracciati; `.git` 1,4 GB; albero di lavoro ~17 GB
  durante la build (gran parte generato e ignorato).
- Directory generate/ignore: `unity-project/Library` 4,1 GB, `backend/preprocessing/data`
  3,4 GB, `app/build` 2,7 GB, `backend/venv` 627 MB, `huntix/build` 404 MB.
  **Nessuna eliminata**: sono ignorate e spesso costose da ricreare.
- 912 MiB di oggetti Git loose recuperabili con `git gc`, **non eseguito**
  (operazione che modifica il repository e vaAutorizzata).
- Working tree finale: solo le cancellazioni preesistenti dell’utente
  (5 log + `link.xml`/`.meta`, ricomparsi dalla build e riportati allo stato
  precedente).

---

## 14. Matrice di gravità

| Gravità | ID | Stato |
|---|---|---|
| Alta | `AU-01`, `CR-01`, `CL-01`, `BP-01`, `AU-04` | 2 corretti; 3 bloccati/manuali |
| Media | `FS-01`, `FS-05`, `FS-07`, `FS-10`, `SY-01`, `SY-02`, `AU-02`, `AU-03`, `BR-01` | 1 corretto; resto bloccato/manuale |
| Bassa / teorica | `FS-02`, `FS-03`, `FS-04`, `FS-06`, `FS-08`, `FS-09`, `P-01`…`P-09` | nessun client o nessuna misura |

---

## 15. Evidenze e riferimenti

- Regole: `firebase/firestore.rules`, `firebase/firebase_security_rules.txt`,
  `scripts/deploy_rules.sh`, `firebase.json`.
- Client Firestore reali: `TeamManager.kt`, `FriendsManager.kt`,
  `ChatModerationManager.kt`, `FirestoreGameSync.kt`.
- Auth/cifratura: `backend/auth_fastapi.py`, `backend/app_routes/auth.py`,
  `backend/storage/crypto.py`, `backend/app.py`, `backend/requirements.txt`.
- Sync/bridge: `CityStateSync.kt`, `CityWorldSync.cs`, `UnityIpc.kt`,
  `UnityAppCallback.kt`, `Bridge.kt`, `IUnityBridge.aidl`, `IUnityCallback.aidl`.
- Rete: `AndroidManifest.xml`, `res/xml/network_security_config.xml`,
  `RealLifeConfig.kt`, `TileClient.cs`, `MultiplayerManager.cs`.
- Performance: `docs/REPORT_OTTIMIZZAZIONE_FLUIDITA_20261123.md`,
  `ChunkManager.cs`, `ChunkData.cs`, `TerrainChunk.cs`, `PerfProbe.cs`,
  `PerformanceGovernor.cs`, `CameraRig.cs`, `CityStyle.cs`.
- Test: `backend/test_audit_regressions.py`, `backend/test_security.py`,
  `backend/penetration_test_checklist.md`, `app/src/test/**`.

---

## 16. Metriche prima/dopo

| Metrica | Prima | Dopo | Verifica |
|---|---|---|---|
| Revoca token dopo logout | token valido | token respinto | test 8/8 OK |
| `MASTER_KEY` assente | chiave Insecure fissa | `CryptoConfigurationError`, startup bloccato | test OK |
| `cryptography` assente | plaintext | eccezione, nessun plaintext | test OK |
| Cache tile Android | `.v4.*` | `.v5.*` | compile Kotlin OK; device da verificare |
| Collider terreno | LOD1 (49 chunk) | LOD0 (9 chunk) | codice `ca20fd4`; FPS non misurato |
| APK SHA-256 | `37045e53…` | `a4ec40f2…` | `sha256sum` |

---

## 17. Limitazioni

- Nessun Firebase Emulator/CLI → regole Firestore non eseguibili.
- Nessun dispositivo Android → nessuna profilazione, nessun test bridge end-to-end.
- Nessun accesso a produzione/dati utente → impossibile verificare impatto reale
  di blacklist multi-worker, ban, token persistenti e cleartext.
- `cscheck` usa stub MCS ed esclude 11 file: non è un test funzionale Unity.
- La build APK dimostra compilazione e packaging, non correttezza a runtime.

---

## 18. Rischi residui

1. Il cambio `autoSyncTransforms=false` può far attraversare il suolo se una
   sincronizzazione esplicita mancasse: serve sessione device con viewer.
2. Token cifrati con la vecchia chiave Insecure richiedono migrazione offline;
   finché non è fatta, `decrypt_value` ritorna "" per quei record.
3. Blacklist in memoria: se il backend passa a più worker, la revoca non è più
   condivisa.
4. Cleartext: JWT e dati di gioco viaggiano in HTTP verso IP pubblico.
5. Regole Firestore: finché non sono testate con emulator, le proposte in §6
   restano non applicate e le regole attive mantengono i permessi eccessivi.

---

## 19. Interventi manuali necessari

1. **Migrazione cifratura**: con backup del DB, esportare i
   `premium_users.purchase_token` cifrati con la vecchia chiave; per ciascuno
   chiamare `decrypt_with_legacy_key(token, '<vecchia chiave fornita dall'operatore>')`
   e riscrivere con `encrypt_value`. Non usare la costante Insecure nel codice:
   eventuale valore storico va passato solo come argomento esplicito in uno
   script one-shot fuori dall’app, poi eliminato. Verificare che la `MASTER_KEY`
   di produzione sia presente e stabile prima del deploy.
2. **Firestore**: installare Firebase CLI, aggiungere blocco `emulators` a
   `firebase.json`, scrivere test `@firebase/rules-unit-testing` per FS-01…FS-10,
   eseguirli, poi applicare la patch a `firebase_security_rules.txt` e rigenerare
   `firestore.rules` con `scripts/deploy_rules.sh`. Deploy solo dopo esito verde.
3. **TLS**: esporre HTTPS/WSS su 5100 (o reverse proxy), aggiornare le 5 costanti
   `BASE_URL`, poi restringere `network_security_config.xml` e verificare anche
   il manifest AAR `Assets/Plugins/Android/AndroidManifest.xml:7`.
4. **Backend**: passare blacklist a Redis/Store condiviso; correggere ban a
   `TIMESTAMPTZ`; leggere il ruolo dal DB; revocare persistent token al logout;
   sostituire `_free_port` con health-check; chiudere pool/task in shutdown.
5. **Device**: installare APK `a4ec40f2…`, usare il viewer in-app e raccogliere
   FPS/frame/RAM/ground/spawn per almeno 5 minuti in campagna aperta.
6. **Riflesso della caccia alle uova** — `RICHIEDE CONFERMA`: il gioco è una
   caccia alle uova… che dici se aggiungo un **“Uovo del Cofre”** che appare
   nel viewer quando una cattura viene rifiutata da una regola Firestore o un
   premio non viene erogato, registrandone posizione/timestamp nel profilo e
   sincronizzandolo Unity↔Android? Non l’ho implementato senza consenso.

---

## 20. Piano futuro

1. Completare e committare le correzioni di questa sessione.
2. Eseguire sessione device e aggiornare il report performance con numeri nuovi.
3. Portare su CI: `cscheck`, test `unittest` audit, test JVM Android (con junit
   in cache), build release; oggi non esiste alcun workflow.
4. Configurare emulator Firestore e testare/apply le regole proposte.
5. Piano TLS e migrazione credenziali Android a `EncryptedSharedPreferences`.
6. Revisione architetturale(auth duplicati, sync policies, porte) con
   approvazione del proprietario, nel rispetto del Mandato §7: conservare e
   integrare, non cancellare sistemi esistenti.
