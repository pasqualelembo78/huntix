# 🔥 Schema Firebase — Mini Giochi
## Huntix

---

## ARCHITETTURA SCELTA: Ibrida (Locale + Firebase)

```
SharedPreferences (LOCALE)          Firebase Firestore (CLOUD)
━━━━━━━━━━━━━━━━━━━━━━━━━━━         ━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Tentativi giornalieri            ✅ Leaderboard globale
✅ Streak sessione                  ✅ Statistiche aggregate
✅ MVC guadagnati oggi              ✅ Daily Events server-side
✅ Bonus giornaliero claim          ✅ Anti-cheat validation
✅ Zero latency                     ✅ Backup cloud

Vantaggi:
• Funziona offline (SharedPrefs)
• Bassa latenza gameplay
• Costi Firebase ridotti (solo sync leaderboard)
• Anti-cheat: server verifica top scores
```

---

## Struttura Firestore Completa

### Collection: `mini_game_stats/{userId}`

```json
{
  "userId": "abc123",
  "displayName": "Mario",
  "totalMvcEarned": 15420,
  "totalGamesPlayed": 87,
  "longestStreak": 7,
  "lastActiveDate": "20260407",
  "consecutiveDays": 5,
  "gamesWon": {
    "memory": 15,
    "number_pick": 9,
    "high_card": 20,
    "three_card": 11,
    "catch_egg": 7,
    "match3": 10
  },
  "gamesPlayed": {
    "memory": 30,
    "number_pick": 22,
    "high_card": 35,
    "three_card": 28,
    "catch_egg": 21,
    "match3": 24
  },
  "highScores": {
    "catch_egg_mvc": 420,
    "match3_score": 210,
  },
  "achievements": [
    "streak_master",
    "daily_player_7"
  ],
  "updatedAt": "Timestamp"
}
```

### Collection: `mini_game_leaderboards/{gameId}/top100/{rank}`

```json
{
  "rank": 1,
  "userId": "abc123",
  "displayName": "Mario",
  "avatarUrl": "https://...",
  "score": 2500,
  "mvcEarned": 1500,
  "achievedAt": "Timestamp",
  "gameId": "memory",
  "weeklyScore": 800,
  "monthlyScore": 3200
}
```

### Collection: `mini_game_events/{eventId}`

```json
{
  "eventId": "easter_bonus_2026",
  "title": "🐰 Easter Special!",
  "description": "Premi raddoppiati su tutti i minigiochi!",
  "emoji": "🐣",
  "type": "double_rewards",
  "affectedGames": ["catch_egg", "memory", "match3"],
  "rewardMultiplier": 2.0,
  "bonusEggChance": 0.15,
  "startTime": "Timestamp",
  "endTime": "Timestamp",
  "isActive": true,
  "bannerColor": "#F06292",
  "priority": 1
}
```

### Collection: `mini_game_daily_rewards/{yyyyMMdd}`

```json
{
  "date": "20260407",
  "featuredGame": "memory",
  "globalMultiplier": 1.5,
  "specialReward": {
    "type": "egg",
    "rarityId": "rare",
    "condition": "play_3_games"
  },
  "communityGoal": {
    "target": 10000,
    "current": 6543,
    "reward": "legendary_egg_all"
  }
}
```

---

## Regole di Sicurezza Firestore

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Statistiche personali
    match /mini_game_stats/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null 
                   && request.auth.uid == userId
                   && validStatsUpdate(request.resource.data);
    }
    
    // Leaderboard - solo lettura per tutti, scrittura solo tramite Cloud Function
    match /mini_game_leaderboards/{gameId}/top100/{rank} {
      allow read: if request.auth != null;
      allow write: if false; // Solo Cloud Functions
    }
    
    // Events - solo lettura
    match /mini_game_events/{eventId} {
      allow read: if request.auth != null;
      allow write: if false; // Solo admin SDK
    }
    
    // Daily rewards - solo lettura
    match /mini_game_daily_rewards/{date} {
      allow read: if request.auth != null;
      allow write: if false;
    }
    
    function validStatsUpdate(data) {
      // Anti-cheat: valida che i punteggi siano ragionevoli
      return data.totalMvcEarned <= 50000  // max ragionevole/giorno × 30 giorni
          && data.gamesPlayed.values().hasAll([]) // struttura valida
          && data.longestStreak <= 50;       // streak massimo ragionevole
    }
  }
}
```

---

## Cloud Functions (Opzionale — Anti-Cheat + Leaderboard)

```javascript
// functions/index.js

const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// Aggiorna leaderboard dopo ogni partita completata
exports.updateLeaderboard = functions.firestore
  .document('mini_game_stats/{userId}')
  .onWrite(async (change, context) => {
    const userId = context.params.userId;
    const data = change.after.data();
    if (!data) return;
    
    // Calcola score composito
    const totalScore = data.totalMvcEarned + (data.totalGamesPlayed * 10);
    
    const db = admin.firestore();
    const leaderRef = db.collection('mini_game_leaderboards')
      .doc('overall').collection('top100');
    
    await leaderRef.doc(userId).set({
      userId,
      displayName: data.displayName,
      score: totalScore,
      mvcEarned: data.totalMvcEarned,
      gamesPlayed: data.totalGamesPlayed,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
  });

// Daily reset leaderboard settimanale
exports.weeklyLeaderboardReset = functions.pubsub
  .schedule('every monday 00:00')
  .timeZone('Europe/Rome')
  .onRun(async (context) => {
    // Archivia top 10 della settimana
    // Reset weekly scores
    console.log('Weekly mini-game leaderboard reset executed');
  });
```

---

## Integrazione Kotlin — Sync Firebase

```kotlin
// Da aggiungere in MiniGameManager (opzionale):
// Sincronizza le statistiche locali su Firebase ogni 5 giochi completati

fun syncToFirebase(ctx: Context) {
    val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val gamesToday = prefs.getInt("total_games_${todayKey()}", 0)
    
    // Sync ogni 5 giochi per ridurre costi Firestore
    if (gamesToday % 5 != 0) return
    
    val profile = PlayerProfileManager.myProfile ?: return
    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    
    db.collection("mini_game_stats")
      .document(profile.uid ?: return)
      .set(mapOf(
          "userId" to (profile.uid ?: ""),
          "displayName" to (profile.displayName ?: "Giocatore"),
          "totalMvcEarned" to totalMvcEarnedToday(ctx),
          "totalGamesPlayed" to gamesToday,
          "updatedAt" to com.google.firebase.Timestamp.now()
      ), com.google.firebase.firestore.SetOptions.merge())
      .addOnFailureListener { /* silent fail — offline ok */ }
}
```

---

## Indici Firestore Consigliati

```
Collection: mini_game_leaderboards/{gameId}/top100
  Composite index: score DESC, achievedAt DESC
  
Collection: mini_game_events
  Single index: isActive ASC, priority ASC

Collection: mini_game_stats
  Composite index: consecutiveDays DESC, totalMvcEarned DESC
```

---

## Stima Costi Firebase (Firestore)

```
Letture/giorno per utente attivo:
  - Load stats: 1 read
  - Load events: 1 read  
  - Load leaderboard: 1 read (paginato top 20)
  = 3 reads/utente/giorno

Scritture/giorno per utente attivo:
  - Update stats (ogni 5 giochi): 1 write
  = 1 write/utente/giorno

Per 10.000 utenti attivi/giorno:
  Reads: 30.000/giorno → $0.018 (free tier: 50.000/giorno)
  Writes: 10.000/giorno → $0.03 (free tier: 20.000/giorno)
  
Totale Firebase mini-games: ~GRATIS sotto i 10.000 DAU
```

---

*Schema v1.0 — Huntix Mini Games*
