const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// Notifica uovo pronto
exports.notifyEggReady = functions.firestore
  .document('players/{uid}')
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after  = change.after.data();
    if (after.fcmToken && after.hatchReady && !before.hatchReady) {
      try {
        await admin.messaging().send({
          token: after.fcmToken,
          notification: { title: 'Uovo pronto!', body: 'Il tuo uovo si e schiuso!' },
          data: { type: 'egg_ready', title: 'Uovo pronto!', body: 'Il tuo uovo si e schiuso!' }
        });
      } catch (e) { console.error('FCM error:', e); }
    }
  });

// Notifica nuovo evento live
exports.notifyLiveEvent = functions.firestore
  .document('live_events/{eventId}')
  .onCreate(async (snap) => {
    const event = snap.data();
    const users = await admin.firestore().collection('players').where('fcmToken', '!=', '').get();
    const tokens = users.docs.map(d => d.data().fcmToken).filter(Boolean);
    if (tokens.length === 0) return;
    const title = event.title || 'Nuovo evento!';
    const body = event.description || '';
    await admin.messaging().sendEachForMulticast({
      tokens,
      notification: { title, body },
      data: { type: 'live_event', title, body }
    });
  });

// Notifica richiesta amicizia
exports.notifyFriendRequest = functions.firestore
  .document('friend_requests/{targetUid}/incoming/{senderUid}')
  .onCreate(async (snap, context) => {
    const target = await admin.firestore().collection('players').doc(context.params.targetUid).get();
    const token = target.data()?.fcmToken;
    if (!token) return;
    const body = (snap.data().senderName || 'Qualcuno') + ' vuole essere tuo amico!';
    await admin.messaging().send({
      token,
      notification: { title: 'Nuova richiesta!', body },
      data: { type: 'friend_request', title: 'Nuova richiesta!', body }
    });
  });

// Notifica offerta scambio
exports.notifyTradeOffer = functions.firestore
  .document('trade_offers/{offerId}')
  .onCreate(async (snap) => {
    const offer = snap.data();
    const target = await admin.firestore().collection('players').doc(offer.toUid).get();
    const token = target.data()?.fcmToken;
    if (!token) return;
    const body = (offer.fromName || 'Qualcuno') + ' ti offre ' + (offer.offeredCreatureName || '');
    await admin.messaging().send({
      token,
      notification: { title: 'Offerta di scambio!', body },
      data: { type: 'trade_offer', title: 'Offerta di scambio!', body }
    });
  });


// ═══════════════════════════════════════════════════════════════
// ✅ FIX v7.2.1: Filtro contenuti chat SERVER-SIDE
// Play Store richiede moderazione attiva per UGC con minori
// ═══════════════════════════════════════════════════════════════

const BLOCKED_WORDS = [
  // Italiano
  "cazzo", "merda", "vaffanculo", "stronzo", "stronza",
  "coglione", "puttana", "minchia", "troia", "figa",
  "negro", "frocio", "ricchione", "handicappato",
  "ammazzati", "suicidati", "muori", "crepa",
  // English
  "fuck", "shit", "bitch", "asshole", "nigger", "faggot",
  "retard", "whore", "dick", "pussy", "cock", "cum",
  "kill yourself", "kys"
];

const URL_REGEX = /(https?:\/\/|www\.)[\w.-]+\.[a-z]{2,}/i;
const EMAIL_REGEX = /[\w.+-]+@[\w.-]+\.[a-z]{2,}/i;

// Realtime Database: filtra chat_global
exports.filterChatGlobal = functions.database
  .ref("/chat_global/{messageId}")
  .onCreate(async (snapshot, context) => {
    const msg = snapshot.val();
    if (!msg || !msg.text) return null;
    
    const text = msg.text.toLowerCase();
    const isBlocked = BLOCKED_WORDS.some(w => text.includes(w))
      || URL_REGEX.test(msg.text)
      || EMAIL_REGEX.test(msg.text);
    
    if (isBlocked) {
      console.log(`Blocked message from ${msg.uid}: ${msg.text.substring(0, 50)}`);
      return snapshot.ref.remove();
    }
    return null;
  });

// Realtime Database: filtra chat_private
exports.filterChatPrivate = functions.database
  .ref("/chat_private/{chatId}/{messageId}")
  .onCreate(async (snapshot, context) => {
    const msg = snapshot.val();
    if (!msg || !msg.text) return null;
    
    const text = msg.text.toLowerCase();
    const isBlocked = BLOCKED_WORDS.some(w => text.includes(w))
      || URL_REGEX.test(msg.text)
      || EMAIL_REGEX.test(msg.text);
    
    if (isBlocked) {
      console.log(`Blocked private message from ${msg.uid}`);
      return snapshot.ref.remove();
    }
    return null;
  });

// Realtime Database: filtra chat indoor rooms
exports.filterIndoorChat = functions.database
  .ref("/indoor_rooms/{roomCode}/chat/{messageId}")
  .onCreate(async (snapshot, context) => {
    const msg = snapshot.val();
    if (!msg || !msg.text) return null;
    
    const text = msg.text.toLowerCase();
    const isBlocked = BLOCKED_WORDS.some(w => text.includes(w))
      || URL_REGEX.test(msg.text)
      || EMAIL_REGEX.test(msg.text);
    
    if (isBlocked) {
      return snapshot.ref.remove();
    }
    return null;
  });
