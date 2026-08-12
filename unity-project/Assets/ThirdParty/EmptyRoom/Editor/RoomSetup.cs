using UnityEngine;
using UnityEngine.SceneManagement;
using UnityEditor;
using UnityEditor.SceneManagement;

namespace EmptyRoom.Editor
{
    // Genera la scena "Room" completa di luce, player, stanza, HUD.
    // Eseguibile da menu (Room/Setup Scene) o in batchmode.
    public static class RoomSetup
    {
        [MenuItem("Room/Setup Scene")]
        public static void Setup()
        {
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            // ── Luce direzionale ──
            var light = new GameObject("DirectionalLight");
            var l = light.AddComponent<Light>();
            l.type = LightType.Directional;
            l.intensity = 1.2f;
            light.transform.rotation = Quaternion.Euler(50f, -30f, 0f);
            RenderSettings.ambientLight = new Color(0.35f, 0.35f, 0.38f);

            // ── Player (FPS) ──
            var player = new GameObject("Player");
            player.AddComponent<CharacterController>();
            player.AddComponent<PlayerController>();
            player.AddComponent<InteractionManager>();
            player.transform.position = new Vector3(0f, 1.6f, 4f);

            var cam = new GameObject("Camera");
            cam.transform.SetParent(player.transform);
            cam.transform.localPosition = new Vector3(0f, 1.6f, 0f);
            var camera = cam.AddComponent<Camera>();
            camera.tag = "MainCamera";
            camera.nearClipPlane = 0.05f;
            camera.farClipPlane = 100f;

            // ── Stanza (generata da RoomBuilder) ──
            var room = new GameObject("Room");
            room.AddComponent<RoomBuilder>();

            // ── HUD (IMGUI, built-in) ──
            var hudGO = new GameObject("HUD");
            hudGO.AddComponent<HUD>();

            // ── Salva ──
            System.IO.Directory.CreateDirectory("Assets/Scenes");
            EditorSceneManager.SaveScene(scene, "Assets/Scenes/Room.unity");
            AssetDatabase.SaveAssets();
            Debug.Log("[RoomSetup] Scena Room generata con successo.");
        }
    }
}
