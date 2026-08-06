# 🦅 Huntix

**Huntix** — un gioco originale di caccia e raccolta creature nel mondo reale,
basato su MapLibre 3D e GPS per esplorare la città alla ricerca di uova rare,
catturare creature, visitare punti di interesse e competere con altri giocatori.

---

## 🎮 Come funziona

Huntix trasforma il mondo reale in un campo di gioco. Il giocatore esplora la
città su una mappa 3D stile real-time, scoprendo uova rare (con diversi elementi
e rarità), combattendo negli allenamenti, visitando edifici e punti di interesse,
e crescendo il proprio team di creature.

### Meccaniche principali

| Meccanica | Descrizione |
|---|---|
| 🥚 Uova rare | Uova con elementi (Acqua, Terra, Aria, Fuoco, Normale) e rarità crescenti |
| 🪣 Secchielli | Strumenti di cattura con rate diversi (Base, Super, Ultra) |
| 🏟 Palestre | Allenamento e sfida tra giocatori |
| 🏛 Edifici | Visita luoghi con bisogni da soddisfare e ricompense |
| 🌐 Mappa 3D | MapLibre 3D con edifici realistici, cielo dinamico, tilt 60° |
| 📡 Proximity | Rilevamento uova vicine (<50m) con vibrazione e notifiche |
| 🔄 Sistema Social | Amici online, scambi, raid cooperativi |
| 🧬 Metamorfosi | Evoluzione delle creature con linee evolutive uniche |

---

## ⚙️ Stack tecnologico

- **Android Kotlin** — piattaforma nativa
- **MapLibre GL SDK 11.8.0** — renderizzazione mappa 3D con tilt, edifici, tile server
- **Firebase** — Firestore (dati), Auth, Analytics, Cloud Functions, Messaging
- **CameraX + ML Kit** — barcode scanning per interazioni AR
- **Google Play Billing** — acquisti in-app, pass stagionali
- **SceneView** — rendering 3D creature per minigiochi
- **SensorManager** — bussola e orientamento per navigazione AR
- **OkHttp + Gson** — networking e serializzazione JSON
- **Coroutine Scope** — gestione asincrona

---

## 📱 Setup locale

### Requisiti
- **Android Studio** Hedgehog o più recente
- **JDK 17** (incluso con Android Studio)
- **Dispositivo fisico** con GPS attivo

### Come aprire il progetto
1. Apri Android Studio
2. `File → Open` → seleziona la cartella `huntix`
3. Attendi la sincronizzazione Gradle (~3-5 minuti)
4. Collega il telefono via USB con **debug USB abilitato**
5. Premi ▶️ **Run**

### Prima build
Android Studio scaricherà automaticamente tutte le dipendenze.
Non è necessaria alcuna API key per la build di debug.

---

## 🗺️ Struttura del progetto

```
huntix/
├── app/
│   ├── src/main/
│   │   ├── java/com/intelligame/huntix/
│   │   │   ├── ui/           — Activity, Fragment, View custom
│   │   │   ├── managers/     — logica di gioco, dati, rete
│   │   │   ├── avatar/        — rendering avatar 3D
│   │   │   └── reallife/      — mappa cittadina, edifici, POI
│   │   ├── res/               — layout, drawable, stringhe
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── backend/                    — backend Python (AI, chat, auth)
├── functions/                  — Firebase Cloud Functions
├── scripts/                    — script utili (deploy regole, ricorrenze)
├── docs/                       — documentazione (README, CREDITS, ricorrenze)
└── firebase/                   — regole di sicurezza (Firestore + RTDB)
```

---

## 📋 Documentazione

| File | Contenuto |
|---|---|
| `docs/README.md` | Questo file |
| `docs/CREDITS.md` | Crediti e paternità |
| `docs/ricorrenze.md` | Attività manuali + backup + esecutore `scripts/ricorrenze.sh` |
| `docs/FIREBASE_SCHEMA.md` | Schema dati Firestore |
| `docs/giochi.txt` | Mappa dei sistemi di gioco |
| `docs/brookhaven.txt` / `docs/narrazione.txt` | Design mondo 3D e RealLife |
| `firebase/firebase_security_rules.txt` | Regole Firestore + Realtime DB (sorgente) |
| `firebase/firestore.rules` / `firebase/rtdb.rules` | Regole deployate |
| `scripts/deploy_rules.sh` | Deploy regole Firestore + RTDB |
| `scripts/ricorrenze.sh` | Verifica backup + login + deploy + (opzionale) build APK |

---

## 🔒 Licenza

Huntix è software proprietario. Tutti i diritti riservati.
Vedi il file `LICENSE` per i termini.

---

## 📧 Contatti

Per collaborazioni, partnership o domande:
- Repository: `https://github.com/pasqualelembo78/huntix`
- Sviluppatore: Pasquale Lembo
