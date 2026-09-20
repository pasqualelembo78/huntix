using UnityEditor;
using UnityEngine;

namespace Huntix.EditorTools
{
    /// <summary>
    /// Diagnostica dei prefab edifici Quaternius importati da FBX: per ogni
    /// prefab elenca renderer, materiali (nome, shader, proprieta' rilevanti,
    /// texture Base Map/Albedo) e segnala materiali NULL o shader mancanti.
    /// I materiali embedded sono sub-asset dell'FBX (non ci sono .mat esterni).
    /// Read-only: non modifica alcun asset.
    /// Eseguibile da CLI:
    ///   -executeMethod Huntix.EditorTools.QuaterniusDiag.Run
    /// </summary>
    public static class QuaterniusDiag
    {
        private static readonly string[] Prefabs =
        {
            "Buildings/Quaternius/Building_Large_2",
            "Buildings/Quaternius/Building_Medium_2_001",
            "Buildings/Quaternius/Building_Small_1",
        };

        public static void Run()
        {
            foreach (string path in Prefabs)
            {
                Debug.Log("[QuatDiag] === " + path + " ===");
                string fbxPath = "Assets/Resources/" + path + ".fbx";
                var sub = AssetDatabase.LoadAllAssetsAtPath(fbxPath);
                Debug.Log("[QuatDiag] FBX '" + fbxPath + "' sub-assets=" + sub.Length);
                foreach (var o in sub)
                {
                    if (o == null) continue;
                    var m = o as Material;
                    string desc = o.GetType().Name;
                    if (m != null)
                    {
                        string shader = m.shader != null ? m.shader.name : "NULL";
                        desc += " name='" + m.name + "' shader='" + shader + "' _BaseMap=" +
                            PropTex(m, "_BaseMap") + " _MainTex=" + PropTex(m, "_MainTex") +
                            " _BumpMap=" + PropTex(m, "_BumpMap") +
                            (m.HasProperty("_BaseColor")
                                ? " col=" + m.GetColor("_BaseColor")
                                : "");
                    }
                    Debug.Log("[QuatDiag]   sub: " + desc);
                }
                var prefab = Resources.Load<GameObject>(path);
                if (prefab == null)
                {
                    Debug.LogWarning("[QuatDiag] PREFAB NULL: " + path);
                    continue;
                }
                var probeRs = prefab.GetComponentsInChildren<Renderer>(true);
                for (int i = 0; i < probeRs.Length && i < 2; i++)
                {
                    var rr = probeRs[i];
                    if (rr == null) continue;
                    var mm = rr.sharedMaterials;
                    for (int j = 0; j < mm.Length && j < 3; j++)
                        if (mm[j] != null)
                            Debug.Log("[QuatDiag]   asset path mat='" + mm[j].name +
                                "' -> " + (string.IsNullOrEmpty(AssetDatabase.GetAssetPath(mm[j]))
                                    ? "RUNTIME-DEFAULT" : AssetDatabase.GetAssetPath(mm[j])));
                }
                var renderers = prefab.GetComponentsInChildren<Renderer>(true);
                Debug.Log("[QuatDiag] prefab=" + prefab.name + " renderers=" + renderers.Length);
                int total = 0;
                for (int i = 0; i < renderers.Length; i++)
                {
                    var rr = renderers[i];
                    if (rr == null) { Debug.Log("[QuatDiag]   renderer[" + i + "] NULL"); continue; }
                    var mats = rr.sharedMaterials;
                    for (int j = 0; j < mats.Length; j++)
                    {
                        var m = mats[j];
                        total++;
                        if (m == null)
                        {
                            Debug.Log("[QuatDiag]   r[" + i + "]=" + rr.name +
                                " sub[" + j + "] MATERIAL NULL");
                            continue;
                        }
                        string shader = m.shader != null ? m.shader.name : "NULL";
                        string baseMap = PropTex(m, "_BaseMap");
                        string mainTex = PropTex(m, "_MainTex");
                        string normalMap = PropTex(m, "_BumpMap");
                        bool hasEm = false;
                        if (m.HasProperty("_EmissionColor"))
                        {
                            Color ec = m.GetColor("_EmissionColor");
                            hasEm = ec.r > 0.01f || ec.g > 0.01f || ec.b > 0.01f;
                        }
                        Debug.Log("[QuatDiag]   r[" + i + "]=" + rr.name +
                            " sub[" + j + "] mat='" + m.name + "' shader='" + shader +
                            "' que=" + m.renderQueue +
                            " _MainTex=" + mainTex +
                            " _BaseMap=" + baseMap +
                            " _BumpMap=" + normalMap +
                            " emis=" + hasEm);
                    }
                }
                Debug.Log("[QuatDiag] " + path + ": materiali totali=" + total);
            }
            Debug.Log("[QuatDiag] DONE");
        }

        private static string PropTex(Material m, string prop)
        {
            if (m == null || !m.HasProperty(prop)) return "-";
            var t = m.GetTexture(prop);
            return t != null ? t.name : "NONE";
        }
    }
}