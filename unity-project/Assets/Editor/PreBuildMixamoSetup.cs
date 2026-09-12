using UnityEngine;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.Animations;

namespace City.Editor
{
    /// <summary>
    /// Build hook: prima di ogni build, importa le clip Mixamo GLB come
    /// Humanoid e crea il PlayerLocomotion.controller con BlendTree.
    /// Tutto automatico, nessuna interazione manuale con l'Editor.
    /// </summary>
    public class PreBuildMixamoSetup : IPreprocessBuildWithReport
    {
        public int callbackOrder => -100;

        private const string MixamoDir = "Assets/Art/Mixamo";
        private const string ResourcesDir = "Assets/Resources/Mixamo";
        private const string ControllerPath = ResourcesDir + "/PlayerLocomotion.controller";

        public void OnPreprocessBuild(BuildReport report)
        {
            EnsureMixamoAssets();
        }

        private static void EnsureMixamoAssets()
        {
            // 1. Assicura che le GLB Mixamo siano importate come Humanoid
            ConfigureMixamoGLB();

            // 2. Crea il controller con BlendTree se non esiste
            if (!AssetDatabase.IsValidFolder(ResourcesDir))
            {
                if (!AssetDatabase.IsValidFolder("Assets/Resources"))
                    AssetDatabase.CreateFolder("Assets", "Resources");
                AssetDatabase.CreateFolder("Assets/Resources", "Mixamo");
            }

            var existing = AssetDatabase.LoadAssetAtPath<RuntimeAnimatorController>(ControllerPath);
            if (existing != null)
            {
                AssetDatabase.DeleteAsset(ControllerPath);
            }
            CreateController();

            // 3. Ricarica le modifiche
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
        }

        private static void ConfigureMixamoGLB()
        {
            string[] guids = AssetDatabase.FindAssets("t:Model", new[] { MixamoDir });
            foreach (string guid in guids)
            {
                string path = AssetDatabase.GUIDToAssetPath(guid);
                if (!path.EndsWith(".glb") && !path.EndsWith(".fbx")) continue;

                var importer = AssetImporter.GetAtPath(path) as ModelImporter;
                if (importer == null) continue;

                bool dirty = false;
                if (importer.animationType != ModelImporterAnimationType.Human)
                {
                    importer.animationType = ModelImporterAnimationType.Human;
                    importer.avatarSetup = ModelImporterAvatarSetup.CreateFromThisModel;
                    importer.materialImportMode = ModelImporterMaterialImportMode.None;
                    importer.motionNodeName = "";
                    dirty = true;
                }

                // Loop time su tutte le clip (via importer: wrapMode è read-only
                // sulle clip non legacy). loopPose per evitare scatti al wrap.
                var clipSettings = importer.clipAnimations;
                bool loopDirty = false;
                if (clipSettings != null)
                {
                    for (int i = 0; i < clipSettings.Length; i++)
                    {
                        if (!clipSettings[i].loopTime)
                        {
                            clipSettings[i].loopTime = true;
                            clipSettings[i].loopPose = true;
                            loopDirty = true;
                        }
                    }
                    if (loopDirty) importer.clipAnimations = clipSettings;
                }
                if (dirty || loopDirty)
                {
                    importer.SaveAndReimport();
                    Debug.Log("[PreBuildMixamo] Configurato: " + path +
                        " human=" + (importer.animationType == ModelImporterAnimationType.Human) +
                        " loop=" + loopDirty);
                }
            }
        }

        private static void CreateController()
        {
            // Trova le clip Mixamo: Idle, Walk, Run
            AnimationClip idle = null, walk = null, run = null;
            string[] guids = AssetDatabase.FindAssets("t:AnimationClip", new[] { MixamoDir });

            foreach (string guid in guids)
            {
                string path = AssetDatabase.GUIDToAssetPath(guid);
                var clip = AssetDatabase.LoadAssetAtPath<AnimationClip>(path);
                if (clip == null) continue;
                string n = clip.name.ToLowerInvariant();
                if (n.Contains("idle") && idle == null) idle = clip;
                else if (n.Contains("walk") && walk == null) walk = clip;
                else if ((n.Contains("run") || n.Contains("running")) && run == null &&
                    !n.Contains("forward") && !n.Contains("backward") && !n.Contains("strafe"))
                    run = clip;
            }

            if (idle == null || walk == null || run == null)
            {
                Debug.LogWarning("[PreBuildMixamo] Clip mancanti: idle=" +
                    (idle != null) + " walk=" + (walk != null) + " run=" + (run != null) +
                    ". Il controller non verra' creato.");
                return;
            }

            // Crea l'AnimatorController (factory pubblica: crea layer + state machine)
            var ctrl = AnimatorController.CreateAnimatorControllerAtPath(ControllerPath);
            ctrl.name = "PlayerLocomotion";
            ctrl.AddParameter("Speed", AnimatorControllerParameterType.Float);

            // Layer con IK Pass abilitato
            AnimatorControllerLayer layer = ctrl.layers[0];
            layer.name = "Base Layer";
            layer.iKPass = true;
            ctrl.layers[0] = layer;

            AnimatorStateMachine sm = layer.stateMachine;

            // BlendTree Idle → Walk → Run (crea anche un AnimatorState che lo usa)
            BlendTree blendTree;
            AnimatorState blendState = ctrl.CreateBlendTreeInController("LocomotionBlend", out blendTree);
            blendTree.blendType = BlendTreeType.Simple1D;
            blendTree.blendParameter = "Speed";
            blendTree.AddChild(idle, 0f);
            blendTree.AddChild(walk, 3.2f);
            blendTree.AddChild(run, 7.5f);

            blendState.name = "LocomotionBlend";
            sm.defaultState = blendState;

            AssetDatabase.SaveAssets();
            Debug.Log("[PreBuildMixamo] Controller creato: " + ControllerPath +
                " (Idle=" + idle.name + " Walk=" + walk.name + " Run=" + run.name + ")");
        }
    }
}
