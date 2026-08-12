using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using System.Collections.Generic;
using System.Linq;
using Huntix.Indoor;

namespace Huntix.EditorTools
{
    // Collega i prefab del pack "Cute Supermarket Lite" al campo
    // `cuteDecorPrefabs` dell'IndoorManager (scena Indoor), così il caricamento
    // a runtime non dipende da Resources.Load. Chiamato da HuntixBuild.
    public static class CuteAssetsSetup
    {
        const string SCENE_PATH = "Assets/Scenes/Indoor.unity";
        const string CUTE_PREFABS_DIR = "Assets/ThirdParty/CuteSupermarketLite/Resources/CuteStore/Prefabs";

        [MenuItem("Huntix/Supermarket/Populate Cute Decor (Indoor)")]
        public static void PopulateMenu()
        {
            Populate();
            AssetDatabase.SaveAssets();
        }

        public static void Populate()
        {
            var scene = EditorSceneManager.OpenScene(SCENE_PATH, OpenSceneMode.Single);
            var manager = Object.FindObjectOfType<IndoorManager>(true);
            if (manager == null)
            {
                UnityEngine.Debug.LogError("[CuteAssetsSetup] IndoorManager non trovato in " + SCENE_PATH);
                return;
            }

            var prefabs = new List<GameObject>();
            foreach (var guid in AssetDatabase.FindAssets("t:GameObject", new[] { CUTE_PREFABS_DIR }))
            {
                var p = AssetDatabase.LoadAssetAtPath<GameObject>(AssetDatabase.GUIDToAssetPath(guid));
                if (p != null) prefabs.Add(p);
            }

            manager.cuteDecorPrefabs = prefabs.ToArray();

            EditorUtility.SetDirty(manager);
            EditorSceneManager.SaveScene(scene);
            AssetDatabase.SaveAssets();

            UnityEngine.Debug.Log($"[CuteAssetsSetup] Indoor: {prefabs.Count} prefab Cute collegati a IndoorManager.cuteDecorPrefabs.");
        }
    }
}
