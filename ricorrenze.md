# 🔁 RICORRENZE — Huntix

Cosa va fatto **a mano** e cosa **si risolve da solo** (`./ricorrenze.sh`).

```
./ricorrenze.sh          # backup + login + deploy regole (ogni volta che serve)
./ricorrenze.sh --apk    # idem + ricompila l'APK debug
```

---

## ✅ FATTO IN AUTONOMIA (dallo script)

| Cosa | Quando | Nota |
|---|---|---|
| Verifica file di backup | ogni esecuzione | ti dice se manca qualcosa |
| Login Firebase CLI | ogni esecuzione | se scaduto, stampa le istruzioni e si ferma |
| Deploy regole Firestore | ogni esecuzione | da `firebase_security_rules.txt` → `firestore.rules` |
| Deploy regole Realtime DB | ogni esecuzione | da `rtdb.rules` (indoor/outdoor/chat) |
| Rebuild APK debug | con `--apk` | dopo modifiche al codice |

> **Le regole**: il file `firebase_security_rules.txt` è la sorgente per Firestore.
> Per RTDB la sorgente è `rtdb.rules` (stato di produzione). Se modifichi le regole,
> aggiorna quei file e poi lancia `./ricorrenze.sh`.

---

## ✋ DA FARE A MANO (non automatizzabile)

### 1. OAuth client ID Android — ARCore keyless *(una tantum, blocca le stanze AR)*
Google Cloud Console → **APIs & Services → Credentials → Create Credentials → OAuth client ID → Android**:
- Package: `com.intelligame.huntix`
- SHA-1 firma:
  - **release**: `D3:53:83:7C:E8:CD:D4:B9:5C:C0:83:34:2E:CF:EE:67:9E:87:75:72`
  - **debug**:   `9F:C8:3A:9A:45:7E:41:6B:FA:EA:4C:A9:A0:51:B4:E0:76:22:CA:7F`

Senza questo client, il salvataggio delle stanze AR dà `ERROR_NOT_AUTHORIZED` nel Debug Log.

### 2. Rifare il login Firebase CLI *(solo se lo script te lo chiede)*
```bash
source ~/.nvm/nvm.sh && nvm use 20
firebase login --no-localhost
```
Apri l'URL nel browser del PC, accedi con `lembopasquale78@gmail.com`, incolla il codice nel terminale.

### 3. Test su telefono fisico
ARCore **non** gira su emulatore. Installa `app/build/outputs/apk/debug/app-debug.apk` e prova
stanze AR + stanze indoor con un secondo account.

### 4. Pubblicazione Google Play *(se prevista)*
Upload AAB firmato, Play App Signing, moduli commerciali.

---

## 💾 BACKUP — file da salvare (fuori da questa macchina)

```
huntix-release.keystore     ← firma APK (perdita = mai più update con stessa firma)
keystore.properties         ← password keystore + webClientId
app/google-services.json    ← config Firebase
.firebaserc                 ← progetto default
firebase.json               ← rules Firestore
backend/.env                ← chiavi AI + DATABASE_URL + JWT_SECRET + MASTER_KEY
```

**Oltre al backup**: la sessione CLI è in `~/.config/configstore/firebase-tools.json`
(rifai il login se la perdi, non è critica).

---

## 🗝️ Chiavi principali (dove stanno)

| Chiave | Valore / posizione |
|---|---|
| `webClientId` | `418980419674-mq5d7a5jmbpujj4gfpitngobjcg17km5.apps.googleusercontent.com` (`keystore.properties`) |
| AdMob App ID | `ca-app-pub-2572171530354182~3428923397` (hardcoded in `app/build.gradle` righe 50-55) |
| Progetto Firebase | `easter-egg-hunt-ar` (numero `418980419674`) |
| Account Firebase/Google | `lembopasquale78@gmail.com` |
| ENV non salvate | `OWM_API_KEY`, `SENTRY_DSN`, `ARCORE_API_KEY` (vuote di default — keyless attivo) |
