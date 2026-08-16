using System;
using System.IO;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEngine;

namespace City.Editor
{
    public static class AndroidBuilder
    {
        public static void Build()
        {
            string projectRoot = Path.GetFullPath(Path.Combine(Application.dataPath, ".."));
            string apkPath = Path.Combine(projectRoot, "Build", "LaMiaCitta.apk");
            Directory.CreateDirectory(Path.GetDirectoryName(apkPath));

            PlayerSettings.companyName = "Miacitta";
            PlayerSettings.productName = "La Mia Citta";
            PlayerSettings.bundleVersion = "1.0.0";
            PlayerSettings.SetApplicationIdentifier(BuildTargetGroup.Android, "com.miacitta.citta");
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel22;
            PlayerSettings.Android.targetSdkVersion = AndroidSdkVersions.AndroidApiLevel34;
            PlayerSettings.SetScriptingBackend(BuildTargetGroup.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.SetIl2CppCompilerConfiguration(BuildTargetGroup.Android, Il2CppCompilerConfiguration.Release);
            PlayerSettings.SetManagedStrippingLevel(BuildTargetGroup.Android, ManagedStrippingLevel.Minimal);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARMv7 | AndroidArchitecture.ARM64;
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.LandscapeLeft;
            PlayerSettings.Android.androidIsGame = true;

            string[] scenes = GetEnabledScenePaths();
            if (scenes.Length == 0)
            {
                throw new Exception("Nessuna scena abilitata in EditorBuildSettings.");
            }

            BuildPlayerOptions options = new BuildPlayerOptions();
            options.scenes = scenes;
            options.locationPathName = apkPath;
            options.target = BuildTarget.Android;
            options.options = BuildOptions.None;

            BuildReport report = BuildPipeline.BuildPlayer(options);
            if (report.summary.result != BuildResult.Succeeded)
            {
                throw new Exception("Build fallita: " + report.summary.result + " - " + report.summary.ToString());
            }

            Debug.Log("APK creato: " + apkPath);
        }

        private static string[] GetEnabledScenePaths()
        {
            var paths = new System.Collections.Generic.List<string>();
            foreach (EditorBuildSettingsScene scene in EditorBuildSettings.scenes)
            {
                if (scene.enabled) paths.Add(scene.path);
            }
            return paths.ToArray();
        }
    }
}
