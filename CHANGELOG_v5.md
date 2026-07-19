# 📋 CHANGELOG v5 — Huntix

## Riepilogo Modifiche

### 1. ⚔️ Combattimento spostato da Home → MiniGiochi
- **HomeActivity.kt**: Rimossa la slim card "Combatti!" dalla home page
- **MiniGamesHubActivity.kt**: Aggiunta card speciale "Combatti! 1v1" in cima alla lista minigiochi
  - Mostra emoji dell'uovo attuale + livello di forza
  - Stile rosso/scuro che risalta rispetto ai minigiochi standard

### 2. ⚔️ Combattimento 1v1 (riscrittura completa)
- **BattleManager.kt**: Riscrittura totale
  - NON più 3v3 a turni, ma **1v1 istantaneo**
  - Forza = livello del player (calcolato da XP)
  - Avversario random (livello ±5 dal player)
  - **Probabilità vittoria proporzionale alla forza** (es: Lv.10 vs Lv.8 = ~55% vittoria)
  - **Pareggio** se forze simili → premio di consolazione (+3 XP, +2 MVC)
  - **Vittoria** → +XP proporzionale al livello nemico, +MVC, possibile +1💎 se nemico più forte
  - **Sconfitta** → nessun premio

- **BattleActivity.kt**: UI completamente nuova
  - Schermata pre-battaglia con showcasing dell'uovo del player
  - **Sistema evoluzione aspetto uovo per livello:**
    - Lv.1-4: 🥚 Uovo Bianco (fragile)
    - Lv.5-9: 🔵 Uovo Azzurro (robusto)
    - Lv.10-14: 💜 Uovo Mistico (aura viola)
    - Lv.15-19: 🔥 Uovo Infuocato (potente)
    - Lv.20-29: 💎 Uovo Diamante
    - Lv.30-39: ⭐ Uovo Stellare
    - Lv.40-49: 🐉 Uovo Draconico
    - Lv.50+: 🌟 Uovo Cosmico
  - Preview dell'evoluzione con emoji
  - Log narrativo animato della battaglia
  - Bottone "Ricombatti!" per giocare di nuovo
  - **Rewarded Ad**: "Guarda un video per RADDOPPIARE il premio!"

### 3. 📺 Sistema Pubblicità (non-invasive)
- **AdHelper.kt**: Nuova classe centralizzata per AdMob
  - Usa gli ID AdMob già configurati in build.gradle
  - `BuildConfig.ADMOB_BANNER_ID`: Banner in fondo alle schermate
  - `BuildConfig.ADMOB_REWARDED_ID`: Video premiati per skip/raddoppio

- **Posizionamento ads:**
  1. 🥚 **Schiusura — "Salta 5 minuti"**: Bottone su ogni slot attivo
     - 📺 Video rewarded → -5 minuti dal timer
     - 💎 5 gemme → -10 minuti dal timer
  2. ⚔️ **Dopo battaglia**: "Guarda un video per RADDOPPIARE il premio!"
  3. 🎮 **MiniGames Hub**: Banner AdMob in fondo alla pagina
  4. 🐣 **Hatching Screen**: Banner AdMob in fondo alla pagina
  5. 🎮 **Dopo 3 minigiochi**: Interstitial automatico (AdHelper.onMiniGameCompleted)

### 4. File Modificati
| File | Tipo | Cosa cambia |
|------|------|-------------|
| HomeActivity.kt | ✏️ Modifica | Rimosso "Combatti!" dalla home |
| MiniGamesHubActivity.kt | ✏️ Modifica | Aggiunto "Combatti!" + banner ad + AdHelper init |
| BattleManager.kt | 🔄 Riscrittura | Sistema 1v1 XP-based con probabilità proporzionale |
| BattleActivity.kt | 🔄 Riscrittura | UI 1v1 con evoluzione uovo + rewarded ad raddoppio |
| HatchingActivity.kt | ✏️ Modifica | Salta minuti (ads + gemme) + banner ad |
| AdHelper.kt | ✨ Nuovo | Classe centralizzata gestione ads |

### 5. Note Tecniche
- AdMob già configurato nel build.gradle (nessuna modifica necessaria)
- L'interstitial usa temporaneamente il banner ID; va sostituito con ID dedicato
- Il sistema di skip usa `HatchingManager.accelerateSlot()` già esistente
- I premi battaglia vengono persistiti via `PlayerProfileManager.persistMyProfile()`
