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
    // Popola il KenneyAssetRegistry con i modelli FBX di KenneyMiniMarket e lo
    // collega al GameManager (così il build Android include gli asset senza
    // dipendere da Resources.Load, che non risolve in questo progetto).
    public static class KenneyAssetsSetup
    {
        const string REGISTRY_PATH = "Assets/KenneyAssetRegistry.asset";
        const string KENNEY_DIR = "Assets/Resources/KenneyMiniMarket";

        [MenuItem("Huntix/Supermarket/Populate Kenney Registry")]
        public static void PopulateMenu()
        {
            Populate();
            AssetDatabase.SaveAssets();
        }

        public static KenneyAssetRegistry Populate()
        {
            var registry = AssetDatabase.LoadAssetAtPath<KenneyAssetRegistry>(REGISTRY_PATH);
            if (registry == null)
            {
                registry = ScriptableObject.CreateInstance<KenneyAssetRegistry>();
                AssetDatabase.CreateAsset(registry, REGISTRY_PATH);
            }

            var prefabs = new List<GameObject>();
            foreach (var fbx in Directory.GetFiles(KENNEY_DIR, "*.fbx"))
            {
                var go = AssetDatabase.LoadAssetAtPath<GameObject>(fbx);
                if (go != null) prefabs.Add(go);
            }
            var colormap = AssetDatabase.LoadAssetAtPath<Texture2D>(Path.Combine(KENNEY_DIR, "colormap.png"));
            registry.prefabs = prefabs.ToArray();
            registry.colormap = colormap;
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
                    if (gm.kenneyRegistry == null)
                    {
                        gm.kenneyRegistry = registry;
                        EditorUtility.SetDirty(gm);
                        changed = true;
                    }
                }
                if (changed) EditorSceneManager.SaveScene(scene);
            }

            UnityEngine.Debug.Log($"[KenneyAssetsSetup] Registry: {prefabs.Count} prefab Kenney, colormap={(colormap != null ? "ok" : "null")}.");
            return registry;
        }
    }
}
