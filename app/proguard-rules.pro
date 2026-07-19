# ARCore / SceneView - non offuscare le classi native
-keep class com.google.ar.** { *; }
-keep class io.github.sceneview.** { *; }
-dontwarn com.google.ar.**

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# ARCore / SceneView (extended)
-keep class com.google.ar.core.** { *; }
-dontwarn com.google.ar.**

# Sentry
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

# Modelli dati Kotlin usati con Firebase
-keepclassmembers class com.intelligame.huntix.IndoorSessionManager$* {
    <fields>;
    <init>(...);
}

# Kotlin serialization
-keep @kotlinx.serialization.Serializable class * { *; }

# org.json - NON offuscare mai: è fornito dall'Android platform.
# Se R8 rinomina i campi (es. char -> 'a'), causa NoSuchFieldError su Android 16+
# (firebase-database usa org.json.JSONStringer internamente via JsonMapper)
-keep class org.json.** { *; }
-dontwarn org.json.**

# ── Filament / ARSceneView — JNI bridge (libfilament-jni.so et al.) ──────────
# R8 NON deve rinominare queste classi: i nomi sono hardcoded nelle .so native
-keep class com.google.android.filament.** { *; }
-keep class com.google.android.filament.gltfio.** { *; }
-keep class com.google.android.filament.utils.** { *; }
-dontwarn com.google.android.filament.**

# ── ARCore (libarcore_sdk_jni.so / libarcore_sdk_c.so) ───────────────────────
-keep class com.google.ar.core.** { *; }
-keep class com.google.arcore.** { *; }

# ── SceneView / ARSceneView ──────────────────────────────────────────────────
-keep class io.github.sceneview.** { *; }
-dontwarn io.github.sceneview.**

# ── JNI — regola generale: nessun metodo nativo può essere rinominato ─────────
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ── Sentry native (libsentry-android.so / libsentry.so) ─────────────────────
# Le classi JNI di Sentry usano nomi precisi — non offuscare
-keep class io.sentry.android.ndk.** { *; }
-dontwarn io.sentry.android.ndk.**

# ── CameraX — ListenableFuture interface ─────────────────────────────────────
-keep interface com.google.common.util.concurrent.ListenableFuture { *; }
-keep class androidx.concurrent.futures.** { *; }
-dontwarn com.google.common.util.concurrent.**

# ── Ready Player Me Avatar System ──────────────────────────────
-keep class com.intelligame.huntix.avatar.** { *; }
-keepclassmembers class com.intelligame.huntix.avatar.ReadyPlayerMeActivity$RPMBridge {
    public *;
}


# ══════════════════════════════════════════════════════════════════
# Gson / JSON data models — NON offuscare i nomi dei campi
# Senza queste regole, R8 rinomina i campi e Gson non riesce
# a deserializzare i dati salvati → la borsa appare vuota!
# ══════════════════════════════════════════════════════════════════
-keep class com.intelligame.huntix.OwnedSurprise { *; }
-keep class com.intelligame.huntix.SurpriseCreature { *; }
-keep class com.intelligame.huntix.SurpriseCreature$Companion { *; }
-keep class com.intelligame.huntix.HatchedEgg { *; }
-keep class com.intelligame.huntix.HatchedEgg$Companion { *; }
-keep class com.intelligame.huntix.EggInventoryItem { *; }
-keep class com.intelligame.huntix.EggInventoryItem$Companion { *; }
-keep class com.intelligame.huntix.PlayerProfile { *; }
-keep class com.intelligame.huntix.PlayerProfile$Companion { *; }
-keep class com.intelligame.huntix.EggRarity { *; }
-keep class com.intelligame.huntix.ZoneType { *; }
-keep class com.intelligame.huntix.WeatherType { *; }
-keep class com.intelligame.huntix.WorldEgg { *; }
-keep class com.intelligame.huntix.LocationBadge { *; }

# Managers data models
-keep class com.intelligame.huntix.managers.** { *; }

# Gson TypeToken (necessario per deserializzazione generics)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Regola generica: mantieni tutti i data class del package principale
-keepclassmembers class com.intelligame.huntix.** {
    <fields>;
    <init>(...);
}

-keep class com.intelligame.huntix.social.** { *; }
