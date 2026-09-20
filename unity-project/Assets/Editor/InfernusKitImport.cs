using UnityEditor;
using UnityEngine;

namespace City.Editor
{
    /// <summary>
    /// Impostazioni di importazione per le texture del kit Inferno
    /// (Assets/Resources/InfernusKit): alpha trasparente, niente mipmap,
    /// bilineare, clamp, maxSize 1024 (le strisce fiamma arrivano a 288x640).
    /// Idempotente: tocca solo i file con impostazioni diverse.
    /// </summary>
    public static class InfernusKitImport
    {
        private static readonly string[] Roots =
        {
            "Assets/Resources/InfernusKit",
        };

        [MenuItem("Huntix/Import Infernus Kit Textures")]
        public static void ImportAll()
        {
            int n = EnsureAll();
            Debug.Log("[InfernusKit] impostate " + n + " texture.");
        }

        public static int EnsureAll()
        {
            int changed = 0;
            foreach (string root in Roots)
            {
                string[] guids = AssetDatabase.FindAssets("t:Texture2D", new[] { root });
                foreach (string g in guids)
                {
                    string path = AssetDatabase.GUIDToAssetPath(g);
                    var ti = AssetImporter.GetAtPath(path) as TextureImporter;
                    if (ti == null) continue;

                    bool dirty = false;
                    if (ti.textureType != TextureImporterType.Default) { ti.textureType = TextureImporterType.Default; dirty = true; }
                    if (!ti.alphaIsTransparency) { ti.alphaIsTransparency = true; dirty = true; }
                    if (ti.wrapMode != TextureWrapMode.Clamp) { ti.wrapMode = TextureWrapMode.Clamp; dirty = true; }
                    if (ti.mipmapEnabled) { ti.mipmapEnabled = false; dirty = true; }
                    if (ti.isReadable) { ti.isReadable = false; dirty = true; }
                    if (ti.filterMode != FilterMode.Bilinear) { ti.filterMode = FilterMode.Bilinear; dirty = true; }

                    TextureImporterSettings settings = new TextureImporterSettings();
                    ti.ReadTextureSettings(settings);
                    if (settings.maxTextureSize < 1024) { settings.maxTextureSize = 1024; dirty = true; }
                    if (dirty)
                    {
                        ti.SetTextureSettings(settings);
                        ti.SaveAndReimport();
                        changed++;
                    }
                }
            }
            return changed;
        }
    }
}