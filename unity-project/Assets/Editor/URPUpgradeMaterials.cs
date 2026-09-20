using System.Collections.Generic;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;

namespace Huntix.EditorTools
{
    /// <summary>
    /// Converte i materiali che usano ancora shader built-in (Standard / Legacy
    /// Shaders) in Universal Render Pipeline/Lit, rimappando le proprieta.
    /// Senza questa conversione i materiali third-party rendono rosa/magenta.
    /// Eseguibile da CLI: -executeMethod Huntix.EditorTools.URPUpgradeMaterials.ConvertToURP
    /// </summary>
    public static class URPUpgradeMaterials
    {
        private const string LitShader = "Universal Render Pipeline/Lit";

        private static bool ShouldConvert(Material mat)
        {
            Shader s = mat.shader;
            if (s == null) return false;
            string n = s.name;
            if (string.IsNullOrEmpty(n)) return false;
            if (n.StartsWith("Universal Render Pipeline", System.StringComparison.Ordinal)) return false;
            if (n.StartsWith("HDRP", System.StringComparison.Ordinal)) return false;
            if (n.StartsWith("Sprites/", System.StringComparison.Ordinal)) return false;
            if (n.StartsWith("UI/", System.StringComparison.Ordinal)) return false;
            if (n.StartsWith("Skybox/", System.StringComparison.Ordinal)) return false;
            if (n.StartsWith("Particles/", System.StringComparison.Ordinal)) return false;
            return n == "Standard"
                || n.StartsWith("Standard ", System.StringComparison.Ordinal)
                || n.StartsWith("Legacy Shaders/", System.StringComparison.Ordinal)
                || n == "Diffuse"
                || n == "Bumped Diffuse";
        }

        private static float Float(Material m, string prop, float fallback)
        {
            return m.HasProperty(prop) ? m.GetFloat(prop) : fallback;
        }

        private static Texture Tex(Material m, string prop)
        {
            return m.HasProperty(prop) ? m.GetTexture(prop) : null;
        }

        public static void ConvertToURP()
        {
            // Persisti anche la lista scenne (incluse le 3 dei regni afterlife)
            // dentro EditorBuildSettings.asset.
            try { HuntixBuild.ConfigurePlayerSettings(); }
            catch (System.Exception e) { Debug.LogWarning("[URPUpgradeMaterials] ConfigurePlayerSettings: " + e.Message); }

            Shader lit = Shader.Find(LitShader);
            if (lit == null)
            {
                Debug.LogError("[URPUpgradeMaterials] Shader  + LitShader +  non trovato. Abort.");
                EditorApplication.Exit(1);
                return;
            }

            List<string> paths = new List<string>();
            string[] guids = AssetDatabase.FindAssets("t:Material", new[] { "Assets" });
            int converted = 0, skipped = 0;
            foreach (string guid in guids)
            {
                string path = AssetDatabase.GUIDToAssetPath(guid);
                Material mat = AssetDatabase.LoadAssetAtPath<Material>(path);
                if (mat == null) { skipped++; continue; }
                if (!ShouldConvert(mat)) { skipped++; continue; }

                bool emissionEnabled = mat.IsKeywordEnabled("_EMISSION");
                bool alphaTest = mat.renderQueue >= 2450 && mat.renderQueue < 2500;
                bool transparent = mat.renderQueue >= 3000;

                Texture albedo = Tex(mat, "_MainTex");
                Color color = mat.HasProperty("_Color") ? mat.GetColor("_Color") : Color.white;
                float metallic = Float(mat, "_Metallic", 0f);
                float glossiness = Float(mat, "_Glossiness", 0f);
                float smoothness = Float(mat, "_Smoothness", glossiness);
                Texture normals = Tex(mat, "_BumpMap");
                float bumpScale = Float(mat, "_BumpScale", 1f);
                Texture emissionMap = Tex(mat, "_EmissionMap");
                Color emissionColor = mat.HasProperty("_EmissionColor") ? mat.GetColor("_EmissionColor") : Color.black;
                Texture ao = Tex(mat, "_OcclusionMap");
                Texture detail = Tex(mat, "_DetailAlbedoMap");
                float cutoff = Float(mat, "_Cutoff", 0.5f);
                float specular = Float(mat, "_Specular", 0f);

                mat.shader = lit;

                mat.SetColor("_BaseColor", color);
                if (albedo != null) mat.SetTexture("_BaseMap", albedo);
                mat.SetFloat("_Metallic", metallic);
                mat.SetFloat("_Smoothness", smoothness);
                if (normals != null) { mat.SetTexture("_BumpMap", normals); mat.SetFloat("_BumpScale", bumpScale); mat.EnableKeyword("_NORMALMAP"); }
                if (ao != null) mat.SetTexture("_OcclusionMap", ao);
                if (detail != null) mat.SetTexture("_DetailAlbedoMap", detail);
                if (emissionMap != null) mat.SetTexture("_EmissionMap", emissionMap);
                mat.SetColor("_EmissionColor", emissionColor);
                mat.SetColor("_EmissiveColor", emissionColor);
                if (emissionEnabled || emissionColor.maxColorComponent > 0.5f)
                {
                    mat.EnableKeyword("_EMISSION");
                }
                else
                {
                    mat.DisableKeyword("_EMISSION");
                }

                if (transparent)
                {
                    mat.SetFloat("_Surface", 1f);
                    mat.SetFloat("_AlphaClip", 0f);
                    mat.SetFloat("_Blend", 0f);
                    mat.SetFloat("_SrcBlend", (float)BlendMode.SrcAlpha);
                    mat.SetFloat("_DstBlend", (float)BlendMode.OneMinusSrcAlpha);
                    mat.SetFloat("_ZWrite", 0f);
                    mat.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
                    mat.DisableKeyword("_ALPHATEST_ON");
                    mat.renderQueue = (int)RenderQueue.Transparent;
                }
                else if (alphaTest)
                {
                    mat.SetFloat("_Surface", 0f);
                    mat.SetFloat("_AlphaClip", 1f);
                    mat.SetFloat("_Cutoff", cutoff);
                    mat.EnableKeyword("_ALPHATEST_ON");
                    mat.DisableKeyword("_SURFACE_TYPE_TRANSPARENT");
                    mat.renderQueue = (int)RenderQueue.AlphaTest;
                }
                else
                {
                    mat.SetFloat("_Surface", 0f);
                    mat.SetFloat("_AlphaClip", 0f);
                    mat.DisableKeyword("_ALPHATEST_ON");
                    mat.DisableKeyword("_SURFACE_TYPE_TRANSPARENT");
                    mat.SetFloat("_SrcBlend", (float)BlendMode.One);
                    mat.SetFloat("_DstBlend", (float)BlendMode.Zero);
                    mat.SetFloat("_ZWrite", 1f);
                    mat.renderQueue = (int)RenderQueue.Geometry;
                }

                EditorUtility.SetDirty(mat);
                converted++;
                paths.Add(path);
            }

            AssetDatabase.SaveAssets();
            Debug.Log("[URPUpgradeMaterials] Convertiti " + converted + " materiali a URP/Lit; saltati " + skipped);
            foreach (string p in paths) Debug.Log("[URPUpgradeMaterials] -> " + p);
        }
    }
}
