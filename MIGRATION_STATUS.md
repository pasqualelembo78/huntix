# HUNTIX — STATO MIGRAZIONE UNITY

> Riepilogo del lavoro svolto e prossimi passi. Aggiornato: 05/08/2026.

## OBIETTIVO
Strategia **ibrida**: Android nativo (UI/menu/mini-giochi) + **Unity 2022.3 LTS**
(motore 3D/AR) per le sezioni outdoor, RealLife e indoor. Unity è integrato
come **libreria AAR** nel progetto Android (piano: `unity.txt`).

## SEZIONI E DOVE VANNO
| Sezione | Dove |
|---|---|
| Outdoor AR (caccia uova stile Pokémon GO) | **Unity** |
| RealLife (negozi, lavoro, social, NPC) | **Unity** |
| Indoor 3D | **Unity** |
| Mini-giochi 2D, termoculla, squadre, profilo, impostazioni, menu, login | **Android nativo** |

## STATO FASI
| Fase | Descrizione | Stato |
|---|---|---|
| 1 | Setup Unity + integrazione | ✅ Completata |
| 2 | Porting core systems | ✅ Completata |
| 3 | AR outdoor mode | ✅ Completata (scripts) |
| 4 | 3D RealLife + Indoor | ✅ Completata (scripts) |
| 5 | Polish & features | ✅ Completata (scripts) |
| 6 | Mini-giochi e sezioni native | ✅ Già presenti in app |
| 7 | Build & deploy | ✅ Build compilata senza errori |

## COSA E' STATO FATTO

### Unity project (`unity-project/`)
- Struttura progetto creata: `Assets/`, `Packages/`, `ProjectSettings/`
- `Packages/manifest.json`: URP 14.0.11, AR Foundation 5.1.3, ARCore 5.1.3,
  Firebase (firestore/auth/analytics/config), TextMeshPro, Cinemachine
- `ProjectSettings/ProjectSettings.asset`: minSdk 29, targetSdk 34, ARM64, IL2CPP
- **26 script C#** in `Assets/Scripts/`:
  - `Core/`: GameManager, ProfileManager, SaveManager, UnityBridge, PlayerProfileManager
  - `Outdoor/`: OutdoorManager, ARSessionManager, GeospatialAnchor, EggSpawner, EggIndicator, WorldEgg
  - `Indoor/`: IndoorManager
  - `AR/`: EggOpening
  - `Inventory/`: EggInventoryManager, BestiaryUI
  - `Gamification/`: LiveEventManager, ResearchTaskManager, SavedManager
  - `Weather/`: WeatherSystem + WeatherType
  - `UI/`: HUDController
  - ScriptableObject: EggRarity, EggElement, ZoneType, BuildingDef
- Scene segnaposto: `Assets/Scenes/Menu.unity`, `Outdoor.unity`, `Indoor.unity`
- `Assets/Plugins/Android/AndroidManifest.xml`

### Modulo `unityLibrary/` (AAR manuale)
- `build.gradle`: library Android, namespace `com.unity3d.player`, compileSdk 34,
  minSdk 29, targetSdk 34. **Senza `shrinkResources`** (errore "cannot be used for libraries").
- `src/main/AndroidManifest.xml`: UnityPlayerActivity con `android:exported="true"`,
  permissions (camera, posizione, notifiche, vibrazione) e `uses-feature` ARCore
  non duplicati.
- `src/main/java/com/unity3d/player/UnityPlayer.java`: stub runtime (UnitySendMessage,
  currentActivity, listener).
- `src/main/java/com/unity3d/player/UnityPlayerActivity.java`: stub Activity base.

### Bridge Android ↔ Unity
- `app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt`: statico `Bridge`
  (punto di ingresso Android→Unity via `UnitySendMessage`).
- `app/src/main/java/com/intelligame/huntix/bridge/BridgeActivity.java`:
  estende `UnityPlayerActivity`, riceve `EXTRA_MODE` (outdoor/reallife/indoor)
  e invia l'evento `setMode` a `GameManager`.
- `app/src/main/java/com/unity3d/player/UnityBridge.java`: bridge Unity → Android
  (spostato QUI dal modulo library per evitare dipendenza circolare).

### Integrazione Gradle
- `settings.gradle`: `include ':unityLibrary'`
- `app/build.gradle` (riga ~225): `implementation project(':unityLibrary')`
- `app/src/main/AndroidManifest.xml`: attività `.bridge.BridgeActivity` +
  meta-data `unityplayer` prima di `</application>`

## ERRORI RISOLTI
| Errore | Fix |
|---|---|
| `Resource shrinker cannot be used for libraries` | rimosso `shrinkResources` da unityLibrary/build.gradle |
| `Incorrect package=... found in source AndroidManifest.xml` | rimosso attributo `package`, namespace via gradle |
| `android:exported needs to be explicitly specified` | activity Unity rimossa dal manifest della library (le activity sono dichiarate solo nell'app) |
| `NoSuchFileException ... results.bin` | pulite cache gradle (`rm -rf /root/.gradle/caches/` e `.gradle`) |
| Duplicati `uses-feature android.hardware.camera.ar` | manifest library riscritto minimale (solo permission + uses-feature unico) |
| Mancanza di `UnityPlayerActivity`/`UnityPlayer` (app non compilava) | creati stub in unityLibrary |
| Dipendenza circolare `unityLibrary → app` | `UnityBridge.java` spostato nel modulo app |
| `minSdk 29` library > `minSdk 26` app | library allineata a minSdk 26 |
| Errori compile in mini-giochi (Game2048, MemoryCard, Pacman, Tetris, ARFlappyEgg) | file corretti (indentazione onDraw, `this@MemoryCardActivity`, `Float` bounds, variabile `c` in ombra, costante `GAP_H` mancante) |

**Build finale verificata (05/08/2026)**:
- `app:assembleDebug` → SUCCESS (APK 53 MB in `app/build/outputs/apk/debug/app-debug.apk`)
- `app:bundleRelease` → SUCCESS (`app/build/outputs/bundle/release/app-release.aab`, 28 MB)

## FILE CHIAVE
```
unity.txt                                                        # piano migrazione
unity-project/                                                    # progetto Unity
unity-project/Assets/Scripts/                                     # 26 script C#
unity-project/Packages/manifest.json                              # dipendenze Unity
unity-project/ProjectSettings/ProjectSettings.asset               # settaggi player
unityLibrary/build.gradle                                         # configurazione AAR
unityLibrary/src/main/AndroidManifest.xml                         # manifest library (minimale)
unityLibrary/src/main/java/com/unity3d/player/UnityPlayerActivity.java  # stub activity Unity
unityLibrary/src/main/java/com/unity3d/player/UnityPlayer.java          # stub runtime Unity
app/src/main/java/com/unity3d/player/UnityBridge.java              # bridge Unity→Android (modulo app)
app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt         # bridge Android→Unity
app/src/main/java/com/intelligame/huntix/bridge/BridgeActivity.java # activity che avvia Unity
app/build.gradle (riga ~225)                                      # implementation project(':unityLibrary')
settings.gradle                                                   # include ':unityLibrary'
build_release.sh                                                  # build release (firma keystore)
```

## LIMITI / BLOCCHI CONOSCIUTI
- **Nessuna licenza Unity valida** nel container: export headless/AAR reale
  da Unity Editor non possibile (`No ULF license found`). L'AAR è costruito a mano
  con stub di `UnityPlayerActivity`/`UnityPlayer`: la UI nativa (BridgeActivity)
  è collegata, ma il rendering 3D/AR richiede il runtime Unity reale su un PC con licenza.
- **Nessun display** per Unity GUI (`unityhub` come root richiede `--no-sandbox`).
- Le scene Unity sono segnaposto vuote: senza licenza/display non è possibile
  costruire la scena 3D/AR reale dal container.

## PROSSIMI PASSI
1. Build debug già verificata OK: `app/build/outputs/apk/debug/app-debug.apk`
2. Bundle release già verificato OK: `app/build/outputs/bundle/release/app-release.aab`
3. Test su dispositivo reale (`./gradlew app:installDebug` con device connesso).
4. Inserire modelli 3D in `unity-project/Assets/Models/` e aggiornare le scene.
5. Se disponibile licenza/display Unity, esportare AAR reale e sostituire
   gli stub di `unityLibrary/`.
6. Aggiornare questo file a fine sessione.

## COMANDI UTILI
```bash
cat /root/giochi/huntix/MIGRATION_STATUS.md   # riprendere da qui
cd /root/giochi/huntix && ./gradlew app:assembleDebug   # build debug
./build_release.sh                                        # build release firmata
```
