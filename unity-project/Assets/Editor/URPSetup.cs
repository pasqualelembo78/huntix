using System.IO;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;

namespace Huntix.EditorTools
{
    /// <summary>
    /// Genera e assegna la pipeline di rendering URP (UniversalRenderPipelineAsset +
    /// UniversalRendererData) a GraphicsSettings e a tutti i QualitySettings.
    /// Il progetto usa materiali URP/Lit ovunque, ma GraphicsSettings.m_CustomRenderPipeline
    /// era {fileID: 0} (pipeline built-in): a runtime i materiali URP non vengono renderizzati
    /// → schermo nero. Questo script ripristina l'assegnazione URP.
    /// Eseguibile da CLI: -executeMethod Huntix.EditorTools.URPSetup.Setup
    /// </summary>
    public static class URPSetup
    {
        const string SettingsDir = "Assets/Settings";
        const string PipelinePath = SettingsDir + "/URP_Pipeline.asset";
        const string RendererPath = SettingsDir + "/URP_Renderer.asset";

        public static void Setup()
        {
            if (!Directory.Exists(SettingsDir))
            {
                AssetDatabase.CreateFolder("Assets", "Settings");
            }

            var rendererData = AssetDatabase.LoadAssetAtPath<UniversalRendererData>(RendererPath);
            if (rendererData == null)
            {
                rendererData = ScriptableObject.CreateInstance<UniversalRendererData>();
                AssetDatabase.CreateAsset(rendererData, RendererPath);
                Debug.Log("[URPSetup] Creato UniversalRendererData: " + RendererPath);
            }

            var pipeline = AssetDatabase.LoadAssetAtPath<UniversalRenderPipelineAsset>(PipelinePath);
            if (pipeline == null)
            {
                pipeline = ScriptableObject.CreateInstance<UniversalRenderPipelineAsset>();
                AssetDatabase.CreateAsset(pipeline, PipelinePath);
                Debug.Log("[URPSetup] Creato UniversalRenderPipelineAsset: " + PipelinePath);
            }

            // Collega il renderer data alla pipeline (campi serializzati privati).
            var so = new SerializedObject(pipeline);
            var rendererList = so.FindProperty("m_RendererDataList");
            rendererList.arraySize = 1;
            rendererList.GetArrayElementAtIndex(0).objectReferenceValue = rendererData;
            var defaultIndex = so.FindProperty("m_DefaultRendererIndex");
            if (defaultIndex != null) defaultIndex.intValue = 0;
            so.ApplyModifiedProperties();

            // Assegna la pipeline come default globale e su ogni quality level.
            GraphicsSettings.defaultRenderPipeline = pipeline;
            for (int i = 0; i < QualitySettings.names.Length; i++)
            {
                QualitySettings.SetQualityLevel(i, false);
                QualitySettings.renderPipeline = pipeline;
            }
            QualitySettings.SetQualityLevel(Mathf.Clamp(QualitySettings.GetQualityLevel(), 0, QualitySettings.names.Length - 1), true);

            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();

            var assigned = GraphicsSettings.defaultRenderPipeline;
            string path = assigned != null ? AssetDatabase.GetAssetPath(assigned) : "NULL";
            Debug.Log("[URPSetup] defaultRenderPipeline = " + path);
            Debug.Log("[URPSetup] Quality levels assegnati: " + QualitySettings.names.Length);
        }
    }
}
