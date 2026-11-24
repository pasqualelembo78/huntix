using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using System.Collections.Generic;
using System.Linq;
using Huntix.Core;

namespace Huntix.EditorTools
{
    // Popola il CinematicAssetRegistry con le texture PBR CC0 scaricate da
    // Poly Haven (Asphalt 01/03, Pavement, Concrete, Brick Wall, Grass) e lo
    // collega al GameManager in ogni scena di build. Le normal map vengono
    // importate come Normal Map (linear) per URP; le altre come sRGB.
    public static class CinematicAssetsSetup
    {
        const string REGISTRY_PATH = "Assets/CinematicAssetRegistry.asset";
        const string TEXTURE_DIR = "Assets/Resources/Cinematic/Textures";

        [MenuItem("Huntix/Grafica/Popola registry cinematografico")]
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

            ConfigureImport("Asphalt_Normal", true);
            ConfigureImport("AsphaltWorn_Normal", true);
            ConfigureImport("Pavement_Normal", true);
            ConfigureImport("Concrete_Normal", true);
            ConfigureImport("BrickWall_Normal", true);
            ConfigureImport("Asphalt_AO", false);
            ConfigureImport("Asphalt_Rough", false);

            registry.asphaltDiffuse = Load("Asphalt_Diff");
            registry.asphaltNormal = Load("Asphalt_Normal");
            registry.asphaltAO = Load("Asphalt_AO");
            registry.asphaltRoughness = Load("Asphalt_Rough");
            registry.asphaltWornDiffuse = Load("AsphaltWorn_Diff");
            registry.asphaltWornNormal = Load("AsphaltWorn_Normal");
            registry.pavementDiffuse = Load("Pavement_Diff");
            registry.pavementNormal = Load("Pavement_Normal");
            registry.concreteDiffuse = Load("Concrete_Diff");
            registry.concreteNormal = Load("Concrete_Normal");
            registry.brickWallDiffuse = Load("BrickWall_Diff");
            registry.brickWallNormal = Load("BrickWall_Normal");
            registry.grassDiffuse = Load("Grass_Diff");

            EditorUtility.SetDirty(registry);
            AssetDatabase.SaveAssets();

            foreach (var scenePath in EditorBuildSettings.scenes.Select(s => s.path))
            {
                if (string.IsNullOrEmpty(scenePath)) continue;
                var scene = EditorSceneManager.OpenScene(scenePath, OpenSceneMode.Single);
                bool changed = false;
                foreach (var gm in Object.FindObjectsOfType<GameManager>(true))
                {
                    if (gm.cinematicRegistry == null)
                    {
                        gm.cinematicRegistry = registry;
                        EditorUtility.SetDirty(gm);
                        changed = true;
                    }
                }
                if (changed) EditorSceneManager.SaveScene(scene);
            }

            int count = new[]
            {
                registry.asphaltDiffuse, registry.asphaltNormal, registry.asphaltAO,
                registry.asphaltRoughness, registry.asphaltWornDiffuse,
                registry.asphaltWornNormal, registry.pavementDiffuse,
                registry.pavementNormal, registry.concreteDiffuse,
                registry.concreteNormal, registry.brickWallDiffuse,
                registry.brickWallNormal, registry.grassDiffuse
            }.Count(t => t != null);
            Debug.Log("[CinematicAssetsSetup] Registry: " + count +
                      "/13 texture PBR CC0 collegate al GameManager.");
            return registry;
        }

        private static Texture2D Load(string name)
        {
            return AssetDatabase.LoadAssetAtPath<Texture2D>(
                TEXTURE_DIR + "/" + name + ".jpg");
        }

        private static void ConfigureImport(string name, bool normalMap)
        {
            string path = TEXTURE_DIR + "/" + name + ".jpg";
            var importer = AssetImporter.GetAtPath(path) as TextureImporter;
            if (importer == null) return;
            var type = normalMap ? TextureImporterType.NormalMap : TextureImporterType.Default;
            if (importer.textureType == type && importer.sRGBTexture == !normalMap &&
                importer.maxTextureSize == 1024 && importer.anisoLevel == 4) return;
            importer.textureType = type;
            importer.sRGBTexture = !normalMap;
            importer.maxTextureSize = 1024;
            importer.anisoLevel = 4;
            importer.wrapMode = TextureWrapMode.Repeat;
            importer.filterMode = FilterMode.Trilinear;
            importer.SaveAndReimport();
        }
    }
}
