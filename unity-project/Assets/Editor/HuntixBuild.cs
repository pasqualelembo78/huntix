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

            // Path relativo al progetto: <root repo>/unity-export.gradle
            string rootDir = Directory.GetParent(Directory.GetParent(Application.dataPath).FullName).FullName;
            string exportBase = Path.Combine(rootDir, "unity-export");
            string exportPath = exportBase + ".gradle";
            if (Directory.Exists(exportPath))
            {
                Directory.Delete(exportPath, true);
            }

            string[] scenes = Array.ConvertAll(EditorBuildSettings.scenes, s => s.path);

            BuildPlayerOptions options = new BuildPlayerOptions
            {
                scenes = scenes,
                locationPathName = exportPath + ".gradle",
                target = BuildTarget.Android,
                options = BuildOptions.AcceptExternalModificationsToPlayer
            };

            BuildReport report = BuildPipeline.BuildPlayer(options);
            if (report.summary.result != BuildResult.Succeeded)
            {
                throw new InvalidOperationException("Export gradle project fallito: " + report.summary.result);
            }

            Debug.Log("[HuntixBuild] Gradle project esportato in: " + exportPath);
        }
    }
}
