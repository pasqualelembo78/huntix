using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using System.Collections.Generic;
using Huntix.Core;

namespace Huntix.EditorTools
{
    // Assembla una scena demo "quartiere" con gli asset Kenney City Kit (CC0):
    // strade, incroci, edifici commerciali, case, lampioni, alberi e fioriere.
    // I modelli del kit sono in scala "mini" (tile strada 1x1, case ~1.3m),
    // quindi il builder applica un worldScale configurabile (default 5x) per
    // portarli a scala umana (strada ~5m, case ~6m) come il resto del progetto.
    public static class CityKitNeighborhoodBuilder
    {
        public const float WorldScale = 5f;
        public const string ScenePath = "Assets/Scenes/CityKitNeighborhood.unity";

        [MenuItem("Huntix/City Kit/Build Neighborhood Demo Scene")]
        public static void BuildDemoScene()
        {
            CityKitAssetsSetup.Populate();
            // La Populate apre/salva scene di build: il riferimento all'asset può
            // risultare distrutto (unload). Ricarichiamo il registry da disco.
            var registry = AssetDatabase.LoadAssetAtPath<CityKitAssetRegistry>("Assets/CityKitAssetRegistry.asset");
            if (registry == null || registry.prefabs.Length == 0)
            {
                Debug.LogError("[CityKitNeighborhood] Registry vuoto: importa prima i modelli.");
                return;
            }

            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            var root = new GameObject("Neighborhood");
            root.transform.position = Vector3.zero;

            var roads = new List<string>
            {
                "road-straight", "road-straight", "road-straight",
                "road-intersection", "road-straight", "road-straight", "road-straight"
            };
            for (int i = 0; i < roads.Count; i++)
                Place(registry, roads[i], root.transform, new Vector3(i * 1f, 0, 0), Vector3.zero);

            var houses = new[] { "building-type-a", "building-type-b", "building-type-f", "building-type-h", "building-type-u", "building-type-c" };
            for (int i = 0; i < houses.Length; i++)
                Place(registry, houses[i], root.transform, new Vector3(i * 1.5f - 0.75f, 0, 1.6f), new Vector3(0, 180, 0));

            var shops = new[] { "building-a", "building-b", "building-c", "building-d" };
            for (int i = 0; i < shops.Length; i++)
                Place(registry, shops[i], root.transform, new Vector3(i * 1.5f - 0.75f, 0, -1.6f), Vector3.zero);

            for (int i = 0; i < 4; i++)
            {
                Place(registry, "light-square", root.transform, new Vector3(i * 2f - 1f, 0, 0.9f), Vector3.zero);
                Place(registry, "tree-small", root.transform, new Vector3(i * 2f - 1f, 0, -0.9f), Vector3.zero);
            }

            for (int i = 0; i < 3; i++)
                Place(registry, "planter", root.transform, new Vector3(0.5f + i * 1.4f, 0, 0f), Vector3.zero);

            // Camera
            var camGO = new GameObject("Main Camera");
            camGO.tag = "MainCamera";
            var cam = camGO.AddComponent<Camera>();
            camGO.AddComponent<AudioListener>();
            camGO.transform.position = new Vector3(3f, 4f, -6f);
            camGO.transform.LookAt(new Vector3(3f, 0, 0));

            // Luce direzionale
            var lightGO = new GameObject("Directional Light");
            var light = lightGO.AddComponent<Light>();
            light.type = LightType.Directional;
            light.intensity = 1f;
            lightGO.transform.rotation = Quaternion.Euler(50f, -30f, 0);

            EditorSceneManager.SaveScene(scene, ScenePath);
            Debug.Log($"[CityKitNeighborhood] Scena demo salvata in {ScenePath}");
        }

        private static GameObject Place(CityKitAssetRegistry registry, string name, Transform parent,
            Vector3 pos, Vector3 euler)
        {
            var prefab = registry.Get(name);
            if (prefab == null)
            {
                Debug.LogWarning($"[CityKitNeighborhood] Prefab non trovato: {name}");
                return null;
            }
            var go = (GameObject)PrefabUtility.InstantiatePrefab(prefab, parent);
            go.name = name;
            go.transform.localPosition = pos * WorldScale;
            go.transform.localRotation = Quaternion.Euler(euler);
            go.transform.localScale = Vector3.one * WorldScale;
            FixMaterials(go, registry);
            return go;
        }

        // I modelli Kenney importano materiali legacy (FbxSurfaceLambert) senza
        // shader validi sotto URP → invisibili. Ricollega URP/Lit + colormap del kit.
        private static void FixMaterials(GameObject go, CityKitAssetRegistry registry)
        {
            Shader targetShader = Shader.Find("Universal Render Pipeline/Lit");
            if (targetShader == null) targetShader = Shader.Find("Standard");
            bool isURP = targetShader != null && targetShader.name.Contains("Universal");
            string baseMapProp = isURP ? "_BaseMap" : "_MainTex";

            string n = go.name;
            Texture2D colormap =
                n.StartsWith("road") || n.StartsWith("light") || n.StartsWith("construction")
                    ? registry.colormapRoads
                    : n.StartsWith("building-type") || n.StartsWith("tree") || n.StartsWith("planter")
                    || n.StartsWith("fence") || n.StartsWith("driveway") || n.StartsWith("path")
                        ? registry.colormapSuburban
                        : registry.colormapCommercial;

            foreach (var r in go.GetComponentsInChildren<Renderer>(true))
            {
                var mats = r.sharedMaterials;
                for (int i = 0; i < mats.Length; i++)
                {
                    var m = mats[i];
                    if (m == null) continue;
                    if (targetShader != null &&
                        (m.shader == null || string.IsNullOrEmpty(m.shader.name) ||
                         m.shader.name.Contains("Standard") || m.shader.name.Contains("Diffuse") ||
                         (!m.shader.name.Contains("URP") && !m.shader.name.Contains("Universal"))))
                    {
                        m.shader = targetShader;
                    }
                    if (m.HasProperty(baseMapProp) && m.GetTexture(baseMapProp) == null && colormap != null)
                        m.SetTexture(baseMapProp, colormap);
                }
            }
        }
    }
}
