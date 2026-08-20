using System;
using System.IO;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEngine;

namespace Huntix.EditorTools
{
    public static class HuntixBuild
    {
        public const string AndroidPackage = "com.intelligame.huntix";

        public static void ConfigureAndExport()
        {
            ConfigurePlayerSettings();
            ExportAndroidGradleProject();
        }

        public static void ConfigurePlayerSettings()
        {
            PlayerSettings.companyName = "IntelliGame";
            PlayerSettings.productName = "Huntix";
            PlayerSettings.SetApplicationIdentifier(NamedBuildTarget.Android, AndroidPackage);
            PlayerSettings.SetScriptingBackend(NamedBuildTarget.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel29;
            PlayerSettings.Android.targetSdkVersion = AndroidSdkVersions.AndroidApiLevel34;
            PlayerSettings.bundleVersion = "1.0.0";
            PlayerSettings.Android.bundleVersionCode = 1;
            PlayerSettings.Android.renderOutsideSafeArea = true;

            string[] scenes = {
                "Assets/Scenes/Menu.unity",
                "Assets/Scenes/Outdoor.unity",
                "Assets/ThirdParty/LaughLittleLamb/Scenes/Preload.unity",
                "Assets/ThirdParty/ARDice/Scenes/MainScene.unity",
                "Assets/City/Scenes/City.unity"
            };
            EditorBuildSettings.scenes = Array.ConvertAll(scenes, s => new EditorBuildSettingsScene(s, true));

            EditorUserBuildSettings.androidBuildSystem = AndroidBuildSystem.Gradle;
            EditorUserBuildSettings.exportAsGoogleAndroidProject = true;
            EditorUserBuildSettings.buildAppBundle = false;
            EditorUserBuildSettings.androidBuildSubtarget = MobileTextureSubtarget.ETC2;

            AssetDatabase.SaveAssets();
            Debug.Log("[HuntixBuild] PlayerSettings configurati: " + AndroidPackage);
        }

        public static void ExportAndroidGradleProject()
        {
            ConfigurePlayerSettings();

            // Path relativo al progetto: dobbiamo esportare in "<repo>/unity-export.gradle"
            // perché build_release.sh si aspetta proprio questo nome. Unity usa locationPathName
            // così comé (NON aggiunge .gradle), quindi lo passiamo esplicitamente.
            string rootDir = Directory.GetParent(Directory.GetParent(Application.dataPath).FullName).FullName;
            string exportBase = Path.Combine(rootDir, "unity-export");
            string exportDir = exportBase + ".gradle"; // dir reale che Unity crea
            if (Directory.Exists(exportDir))
            {
                Directory.Delete(exportDir, true);
            }

            string[] scenes = Array.ConvertAll(EditorBuildSettings.scenes, s => s.path);

            // ── Registry asset Kenney: collega i modelli FBX al GameManager ──
            // (caricamento runtime senza Resources.Load).
            KenneyAssetsSetup.Populate();

            // ── Registry asset Kenney City Kit: strade/edifici/arredo urbano ──
            CityKitAssetsSetup.Populate();

            // ── Addressables (Laugh Little Lamb) ──────────────────────────────
            // Builda il contenuto addressable (scene di menu/livello e i 6 prefab
            // dei livelli) PRIMA del BuildPlayer, così i bundle finiscono dentro
            // StreamingAssets del gradle project esportato (cartella Data/StreamingAssets).
            BuildAddressablesContent();

            BuildPlayerOptions options = new BuildPlayerOptions
            {
                scenes = scenes,
                locationPathName = exportDir,
                target = BuildTarget.Android,
                options = BuildOptions.AcceptExternalModificationsToPlayer
            };

             BuildReport report = BuildPipeline.BuildPlayer(options);
             if (report.summary.result != BuildResult.Succeeded)
             {
                 throw new InvalidOperationException("Export gradle project fallito: " + report.summary.result);
             }

             EnsureUnityLibraryStrings(exportDir);

             Debug.Log("[HuntixBuild] Gradle project esportato in: " + exportDir);
        }

        // ── Addressables (Laugh Little Lamb) ──────────────────────────────
        // Se il pacchetto Addressables è installato (e quindi il progetto importato
        // ha contenuti addressable), ne costruisce il contenuto prima del BuildPlayer.
        // Il check è a runtime sull'assembly (niente define custom necessaria).
        private static void BuildAddressablesContent()
        {
            if (System.Reflection.Assembly.Load("Unity.Addressables.Editor") == null)
            {
                Debug.Log("[HuntixBuild] Addressables non installato, salto content build.");
                return;
            }
            var settings = UnityEditor.AddressableAssets.AddressableAssetSettingsDefaultObject.Settings;
            if (settings == null)
            {
                Debug.LogWarning("[HuntixBuild] AddressableAssetSettings non trovato, salto content build.");
                return;
            }
            Debug.Log("[HuntixBuild] Avvio Addressables content build...");
            UnityEditor.AddressableAssets.Settings.AddressableAssetSettings.BuildPlayerContent(out var result);
            if (!string.IsNullOrEmpty(result.Error))
            {
                throw new InvalidOperationException("Addressables content build fallita: " + result.Error);
            }
            Debug.Log("[HuntixBuild] Addressables content build completata.");
        }

        // Unity 2022.3 non genera strings.xml in unityLibrary quando l'evento
        // androidProjectConfiguration fallisce. Lo creiamo noi per evitare
        // errori AAPT (resource string/app_name not found) durante la build.
        private static void EnsureUnityLibraryStrings(string exportDir)
        {
            string resValuesDir = Path.Combine(exportDir, "unityLibrary", "src", "main", "res", "values");
            string stringsXmlPath = Path.Combine(resValuesDir, "strings.xml");
            if (File.Exists(stringsXmlPath))
            {
                Debug.Log("[HuntixBuild] strings.xml già presente, salto.");
                return;
            }
            Directory.CreateDirectory(resValuesDir);
            string appName = PlayerSettings.productName;
            string content =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "  <string name=\"app_name\">" + appName + "</string>\n" +
                "</resources>\n";
            File.WriteAllText(stringsXmlPath, content);
            Debug.Log("[HuntixBuild] Generato strings.xml (app_name=" + appName + ") in: " + stringsXmlPath);
        }
    }
}
