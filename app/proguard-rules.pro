# ══════════════════════════════════════════════════════════════════
# Huntix ProGuard rules — ottimizzati per riduzione dimensione APK
#
# NOTA: le librerie (Firebase, GMS, ARCore, SceneView, Sentry, Filament)
# forniscono già le loro regole ProGuard via consumer-rules.pro.
# NON duplicare qui regole broad come "-keep class com.google.** { *; }"
# perché bloccano R8 e impediscono la rimozione del codice morto.
# ══════════════════════════════════════════════════════════════════

# ── JNI — regola generale: nessun metodo nativo può essere rinominato ─────────
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ── org.json — fornito dall'Android platform, non toccare ──────────────────────
-keep class org.json.** { *; }
-dontwarn org.json.**

# ── Gson / JSON data models — NON offuscare i nomi dei campi ──────────────────
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
-keep class com.intelligame.huntix.IndoorSessionManager$* { <fields>; <init>(...); }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ── Real Life models (Gson serializzati con @SerializedName) ────────────────────
-keep class com.intelligame.huntix.reallife.** { <fields>; <init>(...); }

# ── Firebase data models (Gson/Reflection) ─────────────────────────────────────
-keep class com.intelligame.huntix.managers.** { <fields>; <init>(...); }

# ── Multiplayer data classes (Firebase RTDB getValue) ──────────────────────────
-keep class com.intelligame.huntix.MultiplayerManager$ChatMessage { <fields>; <init>(...); }
-keep class com.intelligame.huntix.MultiplayerManager$PlayerScore { <fields>; <init>(...); }

# ── Ready Player Me Avatar System ──────────────────────────────────────────────
-keep class com.intelligame.huntix.avatar.** { *; }
-keepclassmembers class com.intelligame.huntix.avatar.ReadyPlayerMeActivity$RPMBridge {
    public *;
}
-keep class com.intelligame.huntix.social.** { <fields>; <init>(...); }

# ── Activities started via Intent (must keep class names from R8 obfuscation) ─
-keep class com.intelligame.huntix.ui.POICustomPageActivity { *; }
-keep class com.intelligame.huntix.ui.POIWebViewActivity { *; }
-keep class com.intelligame.huntix.ui.BuildingInteriorActivity { *; }
-keep class * extends android.app.Activity
-keep class * extends androidx.appcompat.app.AppCompatActivity
