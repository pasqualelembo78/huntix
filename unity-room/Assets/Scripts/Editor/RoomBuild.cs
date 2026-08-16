using UnityEngine;
using UnityEditor;
using UnityEditor.Build.Reporting;

namespace EmptyRoom.Editor
{
    public static class RoomBuild
    {
    [MenuItem("Room/Build Linux")]
    public static void BuildLinux()
    {
        Build(BuildTarget.StandaloneLinux64, "/tmp/opencode/room-build/Room.x86_64");
    }

    [MenuItem("Room/Build Android APK")]
    public static void BuildAndroid()
    {
        RoomSetup.Setup();

        PlayerSettings.Android.keystoreName = "/tmp/opencode/market-build/debug.keystore";
        PlayerSettings.Android.keystorePass = "android";
        PlayerSettings.Android.keyaliasName = "androiddebugkey";
        PlayerSettings.Android.keyaliasPass = "android";
        Build(BuildTarget.Android, "/tmp/opencode/room-build/EmptyRoom.apk");
    }

        public static void Build(BuildTarget target, string path)
        {
            var opts = new BuildPlayerOptions
            {
                scenes = new[] { "Assets/Scenes/Room.unity" },
                locationPathName = path,
                target = target,
                options = BuildOptions.None
            };
            var report = BuildPipeline.BuildPlayer(opts);
            Debug.Log($"[RoomBuild] risultato: {report.summary.result} -> {path}");
        }
    }
}
