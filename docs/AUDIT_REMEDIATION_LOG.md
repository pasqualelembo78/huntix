# Audit Huntix — log cronologico delle remediation

Data: 23/11/2026 · Branca: `flat-world` · HEAD iniziale: `2c7f3ef`

## Backup e protezione

- Backup verificabile: `/tmp/opencode/huntix_audit_backup_20261123/`
  - `git_status_before.txt`, `staged_terrain_fixes.patch`, `unstaged_before.patch`,
    `head_before.txt`, `SHA256SUMS`, `apk_sha256.txt`.
  - Copie dei file prima delle modifiche in `files_before_fix/` (auth, crypto,
    app.py, CityTilePreloader).
- Nessuna modifica ai 5 log cancellati dall’utente né a `link.xml`/`.meta`.
- Nessun accesso a produzione, nessun dato reale, nessun test distruttivo.

## 1. Fix terreno/performance (pre-audit, richiesta “applica tutti i fix e fai commit”)

- Commit: `ca20fd4` — `perf: riduce costo fisico, GC e log del terreno 3D`.
- 15 file, +301/-80. Staged esplicitamente; build artifacts e cancellazioni
  preesistenti esclusi.
- Verifiche: `cscheck` OK; build release `EXIT=0`; APK SHA-256
  `a4ec40f2a5592cca0102d7cb1adae6906970441d419e5f19a89b91787ad5fdbc`.
- Stato: `CORRETTO` (codice) / `NON VERIFICATO` (FPS, ground, spawn su device).

## 2. AU-01 — logout non revocava l’access token

- File: `backend/app_routes/auth.py`, `backend/auth_fastapi.py`.
- Prima: `_token_blacklist.add(jti)` (stringa), mentre `_verify_jwt` cerca
  `t[0] == jti and t[1] > now` su una tupla.
- Dopo: helper `blacklist_access_token(payload)` inserisce `(jti, exp)`;
  il logout lo chiama; import obsoleti rimossi.
- Test: `backend/test_audit_regressions.py`
  - `test_vecchio_comportamento_non_revocava` riproduce il bug;
  - `test_helper_revoca_access_token` verifica il fix;
  - `test_logout_usa_helper_e_non_scrive_piu_stringhe` controlla il sorgente.
- Esito: 3/3 OK. Stato: `CORRETTO`, `VERIFICATO`.
- Limite: blacklist ancora in memoria; multi-worker resta da risolvere.

## 3. CR-01 — cifratura fail-closed

- File: `backend/storage/crypto.py`, `backend/app.py`.
- Prima: fallback `sha256(b"huntix-insecure-fallback-key")`, plaintext senza
  `cryptography`, `""` silenzioso su errore.
- Dopo:
  - `MASTER_KEY` obbligatoria;
  - libreria `cryptography` obbligatoria;
  - `validate_crypto_config()` all’avvio del lifespan;
  - nessun plaintext in nessun ramo di errore;
  - `decrypt_with_legacy_key()` solo per migrazione offline esplicita.
- Test: 5 test specifici in `test_audit_regressions.py` + validazione con
  `.env` reale. Esito: tutti OK, chiave non mostrata.
- Stato: `CORRETTO`, `VERIFICATO`.
- Migrazione richiesta per vecchi token cifrati con chiave Insecure (§19 report).

## 4. BR-01 — cache tile Android/Unity disallineata

- File: `app/src/main/java/com/intelligame/huntix/bridge/CityTilePreloader.kt`.
- Prima: `CACHE_VERSION = 4`, Unity `TileClient.CacheVersion = 5`.
- Dopo: costante portata a 5, commento aggiornato.
- Verifica: `./gradlew :app:compileReleaseKotlin --offline` → `BUILD SUCCESSFUL`.
- Stato: `CORRETTO`; effetto runtime su device `NON VERIFICATO`.

## 5. Test e verifiche aggregate

- `backend/test_audit_regressions.py`: 8 test, **8/8 OK**.
- `py_compile` su 5 file Python: OK.
- `validate_crypto_config()` con `.env` reale: OK.
- `cscheck`: OK (189 warning noti).
- Build release: EXIT 0; hash APK registrato.
- `compileReleaseKotlin`: BUILD SUCCESSFUL.
- `testReleaseUnitTest --offline`: **BLOCCATO** (`junit:junit:4.13.2` assente
  nella cache offline). Non dichiarare i 9 test JVM come superati.

## 6. Non applicato (con motivazione)

- Regole Firestore FS-01…FS-10: `BLOCCATO`, nessun emulator/CLI; patch proposta
  in `docs/AUDIT_TECNICO_HUNTIX.md` §6, non applicata alle regole attive.
- Cleartext CL-01: `BLOCCATO`, serve TLS su 5100.
- Sync CityStateSync / bridge: `BLOCCATO`, serve scelta semantica su `cityMoney`
  e test Android con snapshot sintetici.
- Backend `AU-02`/`AU-03`/`AU-04`/`BP-01`: `RICHIEDE INTERVENTO MANUALE`
  (schema DB, revoca multi-dispositivo, gestione porte in produzione).
- Profilazione Unity: `BLOCCATO`, nessun device; vecchi numeri restano storici.

## 7. Ripristino

Per annullare una correzione: copiare il file corrispondente da
`/tmp/opencode/huntix_audit_backup_20261123/files_before_fix/` sopra il file
attuale, poi rieseguire i test. Lo stato Git pre-audit è nelle patch salvate.
