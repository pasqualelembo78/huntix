using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using System.Collections.Generic;
using System.Linq;
using Huntix.Core;

namespace Huntix.EditorTools
{
    // Importa i modelli Kenney CC0 "grezzi" da assets/road nella cartella
    // Resources/Cinematic/RawCc0 e li collega al CinematicAssetRegistry.
    // I file sorgente vengono copiati una sola volta (mai sovrascritti se
    // l'utente li ha modificati): qui vivono gia' i .meta con GUID stabile.
    public static class CinematicRawAssetsSetup
    {
        const string DEST = "Assets/Resources/Cinematic/RawCc0";
        const string REGISTRY_PATH = "Assets/CinematicAssetRegistry.asset";
        static readonly string[] MODELS =
        {
            "construction-cone", "construction-barrier", "construction-light",
            "light-square", "sign-highway", "road-crossing"
        };

        [MenuItem("Huntix/Grafica/Importa asset CC0 grezzi")]
        public static void PopulateMenu()
        {
            Populate();
            AssetDatabase.SaveAssets();
        }

        public static CinematicAssetRegistry Populate()
        {
            var registry = AssetDatabase.LoadAssetAtPath<CinematicAssetRegistry>(REGISTRY_PATH);
            if (registry == null)
            {
                registry = ScriptableObject.CreateInstance<CinematicAssetRegistry>();
                AssetDatabase.CreateAsset(registry, REGISTRY_PATH);
            }

            var props = new List<GameObject>();
            foreach (var name in MODELS)
            {
                string path = DEST + "/" + name + ".fbx";
                var go = AssetDatabase.LoadAssetAtPath<GameObject>(path);
                if (go != null) props.Add(go);
            }
            registry.rawProps = props.ToArray();
            registry.rawPropsColormap = AssetDatabase.LoadAssetAtPath<Texture2D>(
                DEST + "/Textures/colormap.png");

            var importer = AssetImporter.GetAtPath(DEST + "/Textures/colormap.png") as TextureImporter;
            if (importer != null && (importer.maxTextureSize != 256 || importer.anisoLevel != 4))
            {
                importer.maxTextureSize = 256;
                importer.anisoLevel = 4;
                importer.wrapMode = TextureWrapMode.Repeat;
                importer.SaveAndReimport();
            }

            EditorUtility.SetDirty(registry);
            AssetDatabase.SaveAssets();

            foreach (var scenePath in EditorBuildSettings.scenes.Select(s => s.path))
            {
                if (string.IsNullOrEmpty(scenePath)) continue;
                var scene = EditorSceneManager.OpenScene(scenePath, OpenSceneMode.Single);
                bool changed = false;
                foreach (var gm in Object.FindObjectsOfType<GameManager>(true))
                {
                    if (gm.cinematicRegistry != registry)
                    {
                        gm.cinematicRegistry = registry;
                        EditorUtility.SetDirty(gm);
                        changed = true;
                    }
                }
                if (changed) EditorSceneManager.SaveScene(scene);
            }

            Debug.Log("[CinematicRawAssetsSetup] " + props.Count +
                      "/6 modelli CC0 grezzi collegati al registry.");
            return registry;
        }
    }
}
