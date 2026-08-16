using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEditor.Animations;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.SceneManagement;
using City.Player;
using City.UI;
using City.World;

namespace City.Editor
{
    public static partial class CityBuilder
    {
        public const string ScenePath = "Assets/City/Scenes/City.unity";
        public const string AnimPath = "Assets/City/Anim/Player.controller";

        private const string RoadsDir = "Assets/Art/kenney_city-kit-roads/Models/FBX format/";
        private const string SubDir = "Assets/Art/kenney_city-kit-suburban/Models/FBX format/";
        private const string FurnDir = "Assets/Art/kenney_furniture-kit/Models/FBX format/";
        private const string CharDir = "Assets/Art/kenney_animated-characters-1/";
        private const string NatureDir = "Assets/Art/kenney_nature-kit/Models/FBX format/";

        private static float roadTile = 8f;
        private static float SP = 32f;
        private const int N = 2;

        [MenuItem("Citta/Genera Scena Citta")]
        public static void MenuBuildScene()
        {
            BuildScene();
        }

        public static void BuildScene()
        {
            AssetDatabase.Refresh();

            EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
            Scene scene = EditorSceneManager.GetActiveScene();
            EditorSceneManager.SetActiveScene(scene);

            GameObject root = new GameObject("Citta");

            BuildGround(root.transform);
            BuildRoads(root.transform);
            BuildBlocks(root.transform);
            BuildPlaza(root.transform);
            BuildShops(root.transform);
            BuildStreetProps(root.transform);

            GameObject player = BuildPlayer();
            BuildLighting();
            BuildGame(player);

            EditorSceneManager.SaveScene(scene, ScenePath);
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();

            Debug.Log("Scena città generata: " + ScenePath);
        }

        // ------------------------------------------------------------------ ground

        private static void BuildGround(Transform parent)
        {
            GameObject ground = CreateQuad("Terreno", 400f, 400f, new Vector3(0f, -0.05f, 0f), parent, Grass());
            MeshCollider mc = ground.AddComponent<MeshCollider>();
            mc.sharedMesh = ground.GetComponent<MeshFilter>().sharedMesh;
        }

        // ------------------------------------------------------------------ roads

        private static void BuildRoads(Transform parent)
        {
            Transform streets = new GameObject("Strade").transform;
            streets.SetParent(parent, false);

            GameObject crossGo = Inst(RoadsDir + "road_crossroad.fbx", Vector3.zero, Quaternion.identity, streets);
            Vector3 cs = Size(crossGo);
            roadTile = Mathf.Max(cs.x, cs.z);
            SP = roadTile * 4f;
            Debug.Log("roadTile=" + roadTile + " SP=" + SP);

            for (int i = -N; i <= N; i++)
            {
                for (int j = -N; j <= N; j++)
                {
                    InstOnGround(RoadsDir + "road_crossroad.fbx", new Vector3(i * SP, 0f, j * SP), Quaternion.identity, streets);
                }
            }

            for (int j = -N; j <= N; j++)
            {
                for (int i = -N; i < N; i++)
                {
                    for (int k = 1; k <= 3; k++)
                    {
                        InstOnGround(RoadsDir + "road_straight.fbx", new Vector3(i * SP + roadTile * k, 0f, j * SP), Quaternion.Euler(0f, 90f, 0f), streets);
                    }
                }
            }

            for (int i = -N; i <= N; i++)
            {
                for (int j = -N; j < N; j++)
                {
                    for (int k = 1; k <= 3; k++)
                    {
                        InstOnGround(RoadsDir + "road_straight.fbx", new Vector3(i * SP, 0f, j * SP + roadTile * k), Quaternion.identity, streets);
                    }
                }
            }
        }

        // ------------------------------------------------------------------ blocks & buildings

        private static bool IsShopBlock(int bi, int bj)
        {
            return (bi == 0 && bj == -1) || (bi == -1 && bj == 0) || (bi == 1 && bj == 0);
        }

        private static void BuildBlocks(Transform parent)
        {
            Transform buildings = new GameObject("Edifici").transform;
            buildings.SetParent(parent, false);

            for (int i = -N; i < N; i++)
            {
                for (int j = -N; j < N; j++)
                {
                    if (i == 0 && j == 0) continue;
                    if (IsShopBlock(i, j)) continue;
                    BuildHouseBlock(i, j, buildings);
                }
            }
        }

        private static void BuildHouseBlock(int bi, int bj, Transform parent)
        {
            float cx = (bi + 0.5f) * SP;
            float cz = (bj + 0.5f) * SP;
            float inner = SP / 2f - roadTile / 2f - 2f;

            int houseType = ((bi + 3) * 7 + (bj + 3) * 3) % 21 + 1;

            Vector3[] spots =
            {
                new Vector3(cx - inner * 0.5f, 0f, cz - inner * 0.5f),
                new Vector3(cx + inner * 0.5f, 0f, cz - inner * 0.5f),
                new Vector3(cx, 0f, cz + inner * 0.5f),
            };
            Quaternion[] streetDirs =
            {
                Quaternion.LookRotation(Vector3.left),
                Quaternion.LookRotation(Vector3.right),
                Quaternion.LookRotation(Vector3.forward),
            };

            for (int k = 0; k < spots.Length; k++)
            {
                BuildHouse("Casa " + bi + "," + bj + " #" + k, spots[k], streetDirs[k], parent, houseType + k);
            }
        }

        private static void BuildHouse(string name, Vector3 pos, Quaternion streetDir, Transform parent, int seed)
        {
            Transform root = BuildBuildingShell(name, pos, streetDir, parent, HouseColor(seed));

            CreateCubeRot(name + " - Finestra Sx", new Vector3(1.4f, 1.2f, 0.06f),
                root.TransformPoint(new Vector3(-2.65f, 1.6f, -3.61f)), streetDir, root, Window());
            CreateCubeRot(name + " - Finestra Dx", new Vector3(1.4f, 1.2f, 0.06f),
                root.TransformPoint(new Vector3(2.65f, 1.6f, -3.61f)), streetDir, root, Window());
            CreateCubeRot(name + " - Finestra Retro", new Vector3(2.6f, 1.2f, 0.06f),
                root.TransformPoint(new Vector3(0f, 1.6f, 3.61f)), Quaternion.LookRotation(-(streetDir * Vector3.forward)), root, Window());

            InstLocal(FurnDir + "loungeSofa.fbx", root, new Vector3(0f, 0f, 2.2f), Quaternion.Euler(0f, 180f, 0f));
            InstLocal(FurnDir + "tableCoffee.fbx", root, new Vector3(0f, 0f, 0.6f), Quaternion.identity);
            InstLocal(FurnDir + "table.fbx", root, new Vector3(2.4f, 0f, -2.2f), Quaternion.identity);
            InstLocal(FurnDir + "chair.fbx", root, new Vector3(1.6f, 0f, -1.4f), Quaternion.Euler(0f, 90f, 0f));
            InstLocal(FurnDir + "chair.fbx", root, new Vector3(3.2f, 0f, -1.4f), Quaternion.Euler(0f, -90f, 0f));
            InstLocal(FurnDir + "bedDouble.fbx", root, new Vector3(-2.4f, 0f, 2.4f), Quaternion.Euler(0f, 90f, 0f));
            InstLocal(FurnDir + "bookcaseOpen.fbx", root, new Vector3(-4.2f, 0f, -1.5f), Quaternion.identity);
            InstLocal(FurnDir + "plantSmall1.fbx", root, new Vector3(-3.9f, 0f, -2.8f), Quaternion.identity);
            InstLocal(FurnDir + "plantSmall2.fbx", root, new Vector3(3.9f, 0f, -2.8f), Quaternion.identity);
        }

        private static void InstLocal(string path, Transform root, Vector3 localPos, Quaternion localRot)
        {
            InstOnGround(path, root.TransformPoint(localPos), root.rotation * localRot, root);
        }

        private static Transform BuildBuildingShell(string name, Vector3 pos, Quaternion streetDir, Transform parent, Material exterior)
        {
            Transform root = new GameObject(name).transform;
            root.SetParent(parent, false);
            root.position = pos;
            root.rotation = streetDir * Quaternion.Euler(0f, 180f, 0f);

            CreateCube("Pavimento", new Vector3(9f, 0.1f, 7f), root.TransformPoint(new Vector3(0f, 0.05f, 0f)), root, Floor());
            CreateCube("Muro Retro", new Vector3(9f, 3.2f, 0.2f), root.TransformPoint(new Vector3(0f, 1.7f, 3.5f)), root, exterior);
            CreateCube("Muro Sx", new Vector3(0.2f, 3.2f, 7f), root.TransformPoint(new Vector3(-4.5f, 1.7f, 0f)), root, exterior);
            CreateCube("Muro Dx", new Vector3(0.2f, 3.2f, 7f), root.TransformPoint(new Vector3(4.5f, 1.7f, 0f)), root, exterior);
            CreateCube("Muro Front Sx", new Vector3(3.7f, 3.2f, 0.2f), root.TransformPoint(new Vector3(-2.65f, 1.7f, -3.5f)), root, exterior);
            CreateCube("Muro Front Dx", new Vector3(3.7f, 3.2f, 0.2f), root.TransformPoint(new Vector3(2.65f, 1.7f, -3.5f)), root, exterior);
            CreateCube("Architrave", new Vector3(1.6f, 1.0f, 0.2f), root.TransformPoint(new Vector3(0f, 2.7f, -3.5f)), root, exterior);
            CreateCube("Soffitto", new Vector3(9.2f, 0.2f, 7.2f), root.TransformPoint(new Vector3(0f, 3.3f, 0f)), root, Ceiling());

            BuildPeakedRoof(root);
            return root;
        }

        private static void BuildPeakedRoof(Transform root)
        {
            float eave = 4.15f;
            float slope = 0.5f;
            float slopeAngle = Mathf.Atan(slope) * Mathf.Rad2Deg;
            float panelLen = Mathf.Sqrt(eave * eave + (eave * slope) * (eave * slope));
            float midY = 3.2f + eave * slope * 0.5f;

            CreateCubeRot("Tetto S", new Vector3(9.6f, 0.15f, panelLen * 2f),
                root.TransformPoint(new Vector3(0f, midY, eave * 0.5f)), root.rotation * Quaternion.Euler(slopeAngle, 0f, 0f), root, RoofMat());
            CreateCubeRot("Tetto N", new Vector3(9.6f, 0.15f, panelLen * 2f),
                root.TransformPoint(new Vector3(0f, midY, -eave * 0.5f)), root.rotation * Quaternion.Euler(-slopeAngle, 0f, 0f), root, RoofMat());
        }

        // ------------------------------------------------------------------ plaza

        private static void BuildPlaza(Transform parent)
        {
            Transform plaza = new GameObject("Piazza").transform;
            plaza.SetParent(parent, false);

            Vector3 center = new Vector3(0.5f * SP, 0f, 0.5f * SP);
            GameObject floor = CreateQuad("Pavimento", SP, SP, center + new Vector3(0f, 0.02f, 0f), plaza, Plaza());

            string tree = SubDir + "tree_small.fbx";
            for (int k = 0; k < 8; k++)
            {
                float angle = k * 45f * Mathf.Deg2Rad;
                float r = SP * 0.34f;
                Vector3 pos = center + new Vector3(Mathf.Sin(angle) * r, 0f, Mathf.Cos(angle) * r);
                InstOnGround(tree, pos, Quaternion.Euler(0f, Random.Range(0f, 360f), 0f), plaza);
            }

            string bench = FurnDir + "bench.fbx";
            Vector3[] benchSpots =
            {
                center + new Vector3(-SP * 0.3f, 0f, 0f),
                center + new Vector3(SP * 0.3f, 0f, 0f),
                center + new Vector3(0f, 0f, -SP * 0.3f),
                center + new Vector3(0f, 0f, SP * 0.3f),
            };
            for (int k = 0; k < benchSpots.Length; k++)
            {
                InstOnGround(bench, benchSpots[k], Quaternion.Euler(0f, k * 90f, 0f), plaza);
            }
        }

        // ------------------------------------------------------------------ props

        private static void BuildStreetProps(Transform parent)
        {
            Transform props = new GameObject("Arredo").transform;
            props.SetParent(parent, false);

            string tree = SubDir + "tree_small.fbx";
            string lamp = RoadsDir + "light_curved.fbx";

            for (int i = -N; i <= N; i++)
            {
                for (int j = -N; j <= N; j++)
                {
                    if ((i + j) % 2 != 0) continue;
                    Vector3 p = new Vector3(i * SP + roadTile * 1.5f, 0f, j * SP + roadTile * 1.5f);
                    InstOnGround(lamp, p, Quaternion.identity, props);
                }
            }

            for (int i = -N; i <= N; i++)
            {
                Vector3 a = new Vector3(i * SP, 0f, -N * SP - roadTile * 1.5f);
                Vector3 b = new Vector3(i * SP, 0f, (N + 1) * SP + roadTile * 1.5f);
                Vector3 c = new Vector3(-N * SP - roadTile * 1.5f, 0f, i * SP);
                Vector3 d = new Vector3((N + 1) * SP + roadTile * 1.5f, 0f, i * SP);
                InstOnGround(tree, a, Quaternion.identity, props);
                InstOnGround(tree, b, Quaternion.identity, props);
                InstOnGround(tree, c, Quaternion.identity, props);
                InstOnGround(tree, d, Quaternion.identity, props);
            }
        }

        // ------------------------------------------------------------------ player

        private static GameObject BuildPlayer()
        {
            GameObject playerGo = new GameObject("Player");
            playerGo.tag = "Player";

            CharacterController cc = playerGo.AddComponent<CharacterController>();
            cc.height = 1.8f;
            cc.radius = 0.4f;
            cc.center = new Vector3(0f, 0.9f, 0f);

            PlayerController pc = playerGo.AddComponent<PlayerController>();

            GameObject model = Inst(CharDir + "Model/characterMedium.fbx", Vector3.zero, Quaternion.identity, playerGo.transform);
            if (model != null)
            {
                model.transform.localPosition = new Vector3(0f, -GetBounds(model).min.y, 0f);
                Animator anim = model.GetComponentInChildren<Animator>();
                if (anim != null)
                {
                    anim.runtimeAnimatorController = CreatePlayerAnimatorController();
                    anim.applyRootMotion = false;
                }
            }

            GameObject camGo = new GameObject("PlayerCamera");
            camGo.tag = "MainCamera";
            Camera cam = camGo.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.Skybox;
            cam.nearClipPlane = 0.3f;
            cam.farClipPlane = 500f;
            cam.fieldOfView = 60f;
            camGo.AddComponent<AudioListener>();

            CameraRig rig = camGo.AddComponent<CameraRig>();
            rig.target = playerGo.transform;

            playerGo.transform.position = new Vector3(0f, 0f, -1.5f * SP);
            playerGo.transform.rotation = Quaternion.LookRotation(Vector3.forward);

            return playerGo;
        }

        private static AnimatorController CreatePlayerAnimatorController()
        {
            if (AssetDatabase.LoadAssetAtPath<AnimatorController>(AnimPath) != null)
            {
                AssetDatabase.DeleteAsset(AnimPath);
            }
            AnimatorController controller = AnimatorController.CreateAnimatorControllerAtPath(AnimPath);
            AnimatorStateMachine sm = controller.layers[0].stateMachine;

            controller.AddParameter("Speed", AnimatorControllerParameterType.Float);

            AnimationClip idleClip = AssetDatabase.LoadAssetAtPath<AnimationClip>(CharDir + "Animations/idle.fbx");
            AnimationClip runClip = AssetDatabase.LoadAssetAtPath<AnimationClip>(CharDir + "Animations/run.fbx");

            AnimatorState idle = sm.AddState("Idle");
            AnimatorState run = sm.AddState("Run");
            if (idleClip != null) idle.motion = idleClip;
            if (runClip != null) run.motion = runClip;

            AnimatorStateTransition toRun = sm.AddAnyStateTransition(run);
            toRun.hasExitTime = false;
            toRun.duration = 0.15f;
            toRun.AddCondition(AnimatorConditionMode.Greater, 0.1f, "Speed");

            AnimatorStateTransition toIdle = sm.AddAnyStateTransition(idle);
            toIdle.hasExitTime = false;
            toIdle.duration = 0.15f;
            toIdle.AddCondition(AnimatorConditionMode.Less, 0.11f, "Speed");

            return controller;
        }

        // ------------------------------------------------------------------ lighting

        private static void BuildLighting()
        {
            GameObject sunGo = new GameObject("Sole");
            Light sun = sunGo.AddComponent<Light>();
            sun.type = LightType.Directional;
            sun.intensity = 1.2f;
            sun.color = new Color(1f, 0.96f, 0.88f);
            sun.shadows = LightShadows.Soft;
            sunGo.transform.rotation = Quaternion.Euler(55f, -35f, 0f);
            RenderSettings.sun = sun;

            RenderSettings.skybox = Resources.GetBuiltinResource<Material>("Skybox/Procedural.fbx");
            RenderSettings.ambientMode = AmbientMode.Skybox;
            RenderSettings.ambientIntensity = 1f;

            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.Linear;
            RenderSettings.fogColor = new Color(0.78f, 0.84f, 0.92f);
            RenderSettings.fogStartDistance = 140f;
            RenderSettings.fogEndDistance = 400f;
        }

        // ------------------------------------------------------------------ game bootstrap

        private static void BuildGame(GameObject player)
        {
            GameObject gameGo = new GameObject("Game");
            Game game = gameGo.AddComponent<Game>();
            gameGo.AddComponent<UIManager>();

            game.player = player.GetComponent<PlayerController>();
            game.rig = Object.FindObjectOfType<CameraRig>();
        }
    }
}
