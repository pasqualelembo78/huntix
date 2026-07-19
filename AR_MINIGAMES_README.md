# 🔮 AR Minigames — Huntix  
**Versione 2.0 — Aggiornamento Immersivo Completo**

---

## 📊 Panoramica

| Tipo | Conteggio | Sensori | Immersione |
|------|-----------|---------|------------|
| Giochi AR (camera + UI) | 10 | Camera + Haptic | Media-Alta |
| Giochi AR Nativi | 3 | Camera + Gyro + Magneto + Haptic | **Massima** |
| Giochi Standard | 9 | — | Standard |

---

## 🔮 GIOCHI AR NATIVI (Nuovi — V2.0)

### 🎯 AREggShooterActivity — Egg Shooter AR
**Sensori:** Accelerometro/Gravity per mirare fisicamente  
**Meccanica:** Inclina il telefono → il mirino `⊕` si sposta sullo schermo. Tocca per sparare.  
**Target:**
- 🥚 Egg normale = +3 pts
- 🎯 Target facile = +1 pt  
- 🥇 Golden egg = +10 pts (raro 10%)
- 💣 Bomba = -5 pts (evitare!)

**AR Bonus:** +60 MVC | **Max giornaliero:** 3

---

### 💣 ARColorBombActivity — Color Bomb AR
**Sensori:** Touch gestures (swipe + tap) + Vibrazione differenziata  
**Meccanica:** Bombe colorate appaiono nell'aria con countdown (ring che si restringe).  
Swipa per disattivarle. Stessa sequenza colori = **COMBO!**  
**Bombe:**
- 🔴 Bomba Rossa = -10 pts se esplode
- 🟢 Bomba Verde = +5 pts
- 🔵 Bomba Blu = +3 pts
- 🟣 Bomba Viola = +7 pts
- ⭐ Golden Bomb = +20 pts (fuse velocissima!)

**AR Bonus:** +70 MVC | **Max giornaliero:** 3

---

### 🔮 AREggRadarActivity — Egg Radar AR
**Sensori:** Bussola magnetica (TYPE_MAGNETIC_FIELD + TYPE_ACCELEROMETER)  
**Meccanica:** Radar canvas 360° che mostra 5 uova nascoste in direzioni fisiche reali.  
Ruota fisicamente il telefono nella direzione del segnale.  
Lock-on progressbar: mantieni puntato 2 secondi = **uovo trovato!**  
**Feedback:** Haptic proximity — vibrazione aumenta avvicinandosi  
**Rarità uova:** Comune (+80), Raro (+200), Leggendario (+500)  
**Time bonus:** Uova tutte trovate entro 60s = bonus extra

**AR Bonus:** +80 MVC | **Max giornaliero:** 3

---

## 📷 GIOCHI AR STANDARD (Aggiornati — V2.0)

| File | Gioco | Miglioramenti V2.0 |
|------|-------|--------------------|
| `ARCatchEggActivity.kt` | Acchiappa Uova | + 3D tilt su gameArea, + 🥇 golden egg (+3pts), doppio burst particelle |
| `ARMemoryActivity.kt` | Memory Game | + Haptic tick su ogni carta, + glow verde sulle coppie trovate |
| `ARMatch3Activity.kt` | Match-3 | + **Shake-to-shuffle**: scuoti forte il telefono per mescolare la griglia |
| `ARThreeCardActivity.kt` | Tre Carte | Invariato (meccanica ottimale) |
| `ARHighCardActivity.kt` | Carta Alta | Invariato |
| `ARNumberPickActivity.kt` | Pesca Numeri | Invariato |

---

## 🏗️ ARGameBase — Nuovi Helper V2.0

| Metodo | Descrizione |
|--------|-------------|
| `apply3DTilt(view, tiltX, tiltY)` | Applica rotazione 3D prospettica guidata da sensore |
| `spawnParticles(parent, cx, cy, emojis, count)` | Burst di emoji-particelle multicolori |
| `addGlowPulse(view, colorHex)` | Animazione alpha pulsante per effetto glow |
| `hudPill(label, value, accentColor)` | Badge HUD olografico label + valore |

---

## 🎮 Hub Aggiornato (MiniGamesHubActivity)

**Struttura nuova:**
1. **🎮 GIOCHI STANDARD** — 9 giochi + toggle AR MODE per versione AR
2. **🔮 GIOCHI AR NATIVI** — 3 giochi sempre AR, badge "📷 NATIVO"

**Funzionamento:**
- Giochi standard: bottone `GIOCA` → standard, oppure `AR` se AR MODE attivo
- Giochi AR nativi: bottone `📷 AR` sempre disponibile (non richiedono toggle)

---

## 📱 Richieste Permessi

```xml
<uses-permission android:name="android.permission.CAMERA"/>
<uses-feature android:name="android.hardware.sensor.accelerometer" android:required="false"/>
<uses-feature android:name="android.hardware.sensor.compass" android:required="false"/>
<uses-feature android:name="android.hardware.vibrate" android:required="false"/>
```

---

## 🎁 Bonus AR Completi

| Gioco | AR Bonus | Sensore Principale |
|-------|----------|--------------------|
| Egg Shooter AR 🆕 | +60 MVC | Accelerometro (mira) |
| Color Bomb AR 🆕 | +70 MVC | Touch Swipe + Haptic |
| Egg Radar AR 🆕 | +80 MVC | Bussola magnetica |
| Acchiappa Uova | +50 MVC | Accelerometro (tilt) |
| Memory Game | +30 MVC | Camera |
| Match-3 | +50 MVC | Camera + Shake |
| Carta Alta | +10 MVC | Camera |
| Pesca Numeri | +15 MVC | Camera |
| Tre Carte | +25 MVC | Camera |

