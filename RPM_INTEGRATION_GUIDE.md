# 🎮 Integrazione Ready Player Me — Guida Completa

## 📁 File Generati

Tutti i file vanno in: `app/src/main/java/com/intelligame/huntix/avatar/`

| File | Responsabilità |
|------|---------------|
| `ReadyPlayerMeActivity.kt` | WebView RPM per creazione avatar |
| `AvatarManager.kt` | Download, cache, thumbnail GLB |
| `AvatarPersistenceManager.kt` | Doppia persistenza locale + cloud |
| `AvatarSyncManager.kt` | Sincronizzazione intelligente |
| `AccessoryManager.kt` | Sistema equip testa/corpo/effetti |
| `AvatarMapRenderer.kt` | Rendering avatar su mappa Mapbox |

---

## 1️⃣ Aggiornare `app/build.gradle`

Aggiungi nelle `dependencies`:

```groovy
dependencies {
    // ... dipendenze esistenti ...

    // ── Ready Player Me Avatar System ─────────────────────────
    // OkHttp per download avatar (già usato da Sentry, non aggiunge peso)
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    // WebView helper (per RPM avatar creator)
    implementation 'androidx.webkit:webkit:1.10.0'
}
```

---

## 2️⃣ Aggiornare `AndroidManifest.xml`

Aggiungi la nuova Activity e i permessi necessari:

```xml
<!-- Dentro <manifest>, verifica che questi permessi esistano: -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Dentro <application>: -->
<activity
    android:name=".avatar.ReadyPlayerMeActivity"
    android:label="Crea Avatar"
    android:theme="@style/Theme.AppCompat.NoActionBar"
    android:screenOrientation="portrait"
    android:exported="false" />
```

---

## 3️⃣ Aggiornare `proguard-rules.pro`

Aggiungi:

```proguard
# ── Ready Player Me Avatar System ──────────────────────────────
-keep class com.intelligame.huntix.avatar.** { *; }
-keepclassmembers class com.intelligame.huntix.avatar.ReadyPlayerMeActivity$RPMBridge {
    public *;
}
```

---

## 4️⃣ Aggiornare `PlayerProfile.kt`

Aggiungi questi campi al data class:

```kotlin
data class PlayerProfile(
    // ... campi esistenti ...

    // ── Ready Player Me Avatar ────────────────────────────────
    var rpmAvatarUrl:       String = "",    // URL modello GLB RPM
    var rpmAvatarId:        String = "",    // ID avatar RPM
    var rpmAvatarVersion:   Int    = 0,     // Versione avatar (incrementale)
    var equippedHeadId:     String = "",    // Accessorio testa equipaggiato
    var equippedBodyId:     String = "",    // Accessorio corpo equipaggiato
    var equippedEffectId:   String = "",    // Accessorio effetto equipaggiato
)
```

Aggiorna anche `toMap()` e `fromMap()` in PlayerProfile per includere i nuovi campi.

---

## 5️⃣ Integrazione in `HomeActivity.kt`

### Aggiungi pulsante "Crea Avatar"

```kotlin
class HomeActivity : AppCompatActivity() {
    companion object {
        private const val RC_RPM_AVATAR = 900
    }

    // Nel metodo dove costruisci il layout/menu, aggiungi:
    private fun setupAvatarButton() {
        val avatarBtn = Button(this).apply {
            text = if (AvatarPersistenceManager.hasLocalAvatar(this@HomeActivity))
                "✏️ Modifica Avatar" else "🧑 Crea Avatar"
            setOnClickListener {
                ReadyPlayerMeActivity.launch(this@HomeActivity, RC_RPM_AVATAR)
            }
        }
        // Aggiungilo al tuo layout
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_RPM_AVATAR && resultCode == Activity.RESULT_OK) {
            val avatarUrl = data?.getStringExtra(ReadyPlayerMeActivity.EXTRA_AVATAR_URL) ?: return
            val avatarId = data.getStringExtra(ReadyPlayerMeActivity.EXTRA_AVATAR_ID) ?: ""

            // Salva ID avatar
            AvatarPersistenceManager.saveAvatarId(this, avatarId)

            // Scarica avatar in background
            lifecycleScope.launch {
                val success = AvatarManager.ensureAvatarDownloaded(this@HomeActivity, avatarUrl)
                if (success) {
                    // Scarica thumbnail per la mappa
                    AvatarManager.downloadAvatarThumbnail(this@HomeActivity, avatarId)
                    // Sync verso cloud
                    AvatarSyncManager.pushLocalToCloud(this@HomeActivity)
                    Toast.makeText(this@HomeActivity, "✅ Avatar salvato!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@HomeActivity, "❌ Errore download", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
```

---

## 6️⃣ Integrazione in `OutdoorWorldActivity.kt`

### Sostituisci il rendering del personaggio

Dove attualmente usi `CharacterSpriteManager.makeCharacterDrawable(...)`,
sostituisci con:

```kotlin
// PRIMA (vecchio):
val charDrawable = CharacterSpriteManager.makeCharacterDrawable(
    resources, CHAR_SIZE_DP, walkTick, level, facing
)

// DOPO (nuovo — con fallback automatico):
val charDrawable = AvatarMapRenderer.makeAvatarMarkerDrawable(
    resources = resources,
    sizeDp = CHAR_SIZE_DP,
    walkTick = walkTick,
    level = level,
    facing = facing,
    context = this
)
```

Il renderer gestisce automaticamente il fallback: se l'utente non ha un avatar RPM,
usa il vecchio `CharacterSpriteManager`.

---

## 7️⃣ Integrazione in `EggHuntApplication.kt` (Application class)

### Sync all'avvio

```kotlin
class EggHuntApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // ... init esistente ...

        // ── Avatar Sync all'avvio ────────────────────────────────
        AvatarSyncManager.syncOnStartup(this) { result ->
            Log.d("App", "Avatar sync: $result")
        }
    }
}
```

---

## 8️⃣ Integrazione in `HatchingActivity.kt`

### Award accessori alla schiusa uovo

Dove gestisci la schiusa di un uovo, aggiungi:

```kotlin
// Dopo che l'uovo è schiuso con successo:
val accessories = AccessoryManager.rollAccessoriesFromEgg(egg.sourceRarityId)
for (acc in accessories) {
    AccessoryManager.addAccessory(this, acc, egg.sourceRarityId)
    // Mostra notifica/toast per il nuovo accessorio
    Toast.makeText(this,
        "${acc.emoji} Nuovo accessorio: ${acc.name}!",
        Toast.LENGTH_SHORT
    ).show()
}
```

---

## 9️⃣ Firestore Rules

Aggiungi alle Firestore Security Rules:

```
match /players/{uid}/avatar/{doc} {
    allow read:  if request.auth != null && request.auth.uid == uid;
    allow write: if request.auth != null && request.auth.uid == uid;
}
```

---

## 🌐 Configurazione RPM

1. Vai su [readyplayer.me](https://readyplayer.me) → Developer Dashboard
2. Crea un'applicazione (gratuita)
3. Nota il tuo subdomain (es: `intelligame`)
4. In `ReadyPlayerMeActivity.kt`, aggiorna `RPM_SUBDOMAIN` col tuo subdomain

Ready Player Me è **GRATUITO** per uso base (creazione avatar + download GLB).

---

## 📊 Schema Firestore Finale

```
players/{uid}/
├── (profilo giocatore esistente)
├── avatar/
│   └── config
│       ├── rpmAvatarUrl: string
│       ├── rpmAvatarId: string
│       ├── avatarHash: string
│       ├── avatarVersion: int
│       ├── accessories: string (JSON)
│       ├── lastModifiedMs: long
│       └── userId: string
├── customization/
│   └── equipped (esistente)
```

---

## ⚠️ Note Importanti

- **Licenze**: Tutti gli accessori usano CC0 (dominio pubblico). Nessun asset a pagamento.
- **Offline**: Il sistema funziona completamente offline dopo il primo download.
- **Rete**: Avatar scaricato UNA sola volta, poi solo da cache locale.
- **Sync**: Modifiche locali immediate + cloud in background.
- **Fallback**: Se RPM non disponibile, il gioco funziona con gli sprite classici.
- **Texture**: Limitate a 1024px via parametri RPM API.
- **Peso**: L'avatar ottimizzato RPM pesa ~1-3MB (con meshLod=1 + textureAtlas=1024).
