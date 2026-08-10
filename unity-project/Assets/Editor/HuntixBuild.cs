using System;
using System.IO;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.AI;

namespace Huntix.EditorTools
{
    public static class HuntixBuild
    {
        public const string AndroidPackage = "com.intelligame.huntix";

        public static void ConfigureAndExport()
        {
            BakeIndoorNavMesh();
            ConfigurePlayerSettings();
            ExportAndroidGradleProject();
        }

        /// <summary>
        /// Bake NavMesh nella scena Indoor.unity (Editor-only).
        /// Segna il Floor come Navigation Static e bakes il NavMesh
        /// così gli NPC indoor possono pattugliare. A runtime IndoorManager.BuildRuntimeNavMesh()
        /// ricostruisce il NavMesh includendo le pareti create dinamicamente da StoreBuilder.
        /// </summary>
        private static void BakeIndoorNavMesh()
        {
            const string scenePath = "Assets/Scenes/Indoor.unity";
            var scene = EditorSceneManager.OpenScene(scenePath, OpenSceneMode.Single);
            if (!scene.isLoaded)
            {
                Debug.LogWarning("[HuntixBuild] Impossibile aprire Indoor.unity per NavMesh bake");
                return;
            }

            bool bakedAny = false;

            var floor = GameObject.Find("Floor");
            if (floor != null)
            {
                var flags = GameObjectUtility.GetStaticEditorFlags(floor);
                flags |= StaticEditorFlags.NavigationStatic;
                flags |= StaticEditorFlags.LightmapStatic;
                GameObjectUtility.SetStaticEditorFlags(floor, flags);
                bakedAny = true;
            }

            if (bakedAny)
            {
                UnityEditor.AI.NavMeshBuilder.BuildNavMesh();
                EditorSceneManager.SaveScene(scene, scenePath);
                Debug.Log("[HuntixBuild] NavMesh baked in Indoor.unity");
            }
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
                "Assets/Scenes/Indoor.unity"
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
