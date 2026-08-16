using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using System.IO;
using System.Collections.Generic;
using System.Linq;
using Huntix.Core;

namespace Huntix.EditorTools
{
    // Popola il CityKitAssetRegistry con i modelli FBX di Kenney City Kit
    // (Commercial/Suburban/Roads) e lo collega al GameManager, così il build
    // Android include gli asset senza dipendere da Resources.Load.
    public static class CityKitAssetsSetup
    {
        const string REGISTRY_PATH = "Assets/CityKitAssetRegistry.asset";
        const string CITYKIT_ROOT = "Assets/Art/Models/Kenney/CityKit";

        [MenuItem("Huntix/City Kit/Populate City Kit Registry")]
        public static void PopulateMenu()
        {
            Populate();
            AssetDatabase.SaveAssets();
        }

        public static CityKitAssetRegistry Populate()
        {
            var registry = AssetDatabase.LoadAssetAtPath<CityKitAssetRegistry>(REGISTRY_PATH);
            if (registry == null)
            {
                registry = ScriptableObject.CreateInstance<CityKitAssetRegistry>();
                AssetDatabase.CreateAsset(registry, REGISTRY_PATH);
            }

            var prefabs = new List<GameObject>();
            Texture2D colormapCommercial = null, colormapSuburban = null, colormapRoads = null;
            string[] subDirs = { "Commercial", "Suburban", "Roads" };
            foreach (var sub in subDirs)
            {
                string dir = Path.Combine(CITYKIT_ROOT, sub);
                if (!Directory.Exists(dir)) continue;
                foreach (var fbx in Directory.GetFiles(dir, "*.fbx"))
                {
                    var go = AssetDatabase.LoadAssetAtPath<GameObject>(fbx);
                    if (go != null && !prefabs.Contains(go)) prefabs.Add(go);
                }
                var colormap = AssetDatabase.LoadAssetAtPath<Texture2D>(Path.Combine(dir, "colormap.png"));
                if (sub == "Commercial") colormapCommercial = colormap;
                else if (sub == "Suburban") colormapSuburban = colormap;
                else if (sub == "Roads") colormapRoads = colormap;
            }

            registry.prefabs = prefabs.ToArray();
            registry.colormapCommercial = colormapCommercial;
            registry.colormapSuburban = colormapSuburban;
            registry.colormapRoads = colormapRoads;
            EditorUtility.SetDirty(registry);
            AssetDatabase.SaveAssets();

            // Collega il registry al GameManager in ogni scena di build.
            foreach (var scenePath in EditorBuildSettings.scenes.Select(s => s.path))
            {
                if (string.IsNullOrEmpty(scenePath)) continue;
                var scene = EditorSceneManager.OpenScene(scenePath, OpenSceneMode.Single);
                bool changed = false;
                foreach (var gm in Object.FindObjectsOfType<GameManager>(true))
                {
                    if (gm.cityKitRegistry == null)
                    {
                        gm.cityKitRegistry = registry;
                        EditorUtility.SetDirty(gm);
                        changed = true;
                    }
                }
                if (changed) EditorSceneManager.SaveScene(scene);
            }

            UnityEngine.Debug.Log($"[CityKitAssetsSetup] Registry: {prefabs.Count} prefab City Kit.");
            return registry;
        }
    }
}
