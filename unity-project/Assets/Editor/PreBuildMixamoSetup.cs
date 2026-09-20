using UnityEditor.Build;
using UnityEditor.Build.Reporting;

namespace City.Editor
{
    /// <summary>
    /// Build hook: prima di ogni build assicura l'integrazione del player "Remy"
    /// (import FBX/Mixamo, texture, materiali, controller PlayerLocomotion con Jump,
    /// prefab PlayerHero). Tutta la logica vive in RemyKitSetup/UAL2KitSetup; qui
    /// restano solo l'ordine di callback e la delega.
    ///
    /// RemyKitSetup ricrea il controller PlayerLocomotion da zero; UAL2KitSetup
    /// aggiunge poi le animazioni-azione della Universal Animation Library 2.
    /// </summary>
    public class PreBuildMixamoSetup : IPreprocessBuildWithReport
    {
        public int callbackOrder => -100;

        public void OnPreprocessBuild(BuildReport report)
        {
            UAL2KitSetup.PrepareAll();
            InfernusKitImport.EnsureAll();
        }
    }
}