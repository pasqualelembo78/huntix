using System.Collections.Generic;
using UnityEditor;
using UnityEditor.Animations;
using UnityEngine;

namespace City.Editor
{
    /// <summary>
    /// Integra la "Universal Animation Library 2 (Standard)" di Quaternius (CC0)
    /// come animazioni-azione del player. L'FBX viene importato come Humanoid,
    /// cosi' le clip vengono retargettate sull'avatar Mixamo di Remy; al
    /// controller PlayerLocomotion viene aggiunto uno stato per ogni clip,
    /// attivabile a runtime via PlayerActions.Trigger(gameObject, "NomeClip").
    ///
    /// Idempotente: se gli stati azione sono gia' presenti non fa nulla.
    /// Eseguito dal build hook PreBuildMixamoSetup, DOPO RemyKitSetup (che
    /// ricrea il controller da zero).
    /// </summary>
    public static class UAL2KitSetup
    {
        private const string Dir = "Assets/Art/UniversalAnimationLibrary";
        private const string FbxPath = Dir + "/UAL2_Standard.fbx";
        private const string CtrlPath = "Assets/Resources/Mixamo/PlayerLocomotion.controller";
        private const string StatePrefix = "UAL2 ";
        private const string ParamPrefix = "Act_";

        /// <summary>
        /// Sequenza completa usata dal build hook: prima RemyKitSetup ricrea il
        /// controller PlayerLocomotion da zero (e gli asset Remy), poi questo
        /// aggiunge gli stati azione UAL2. L'ordine e' critico.
        /// </summary>
        public static void PrepareAll()
        {
            RemyKitSetup.EnsureAll();
            EnsureAll();
        }

        public static void EnsureAll()
        {
            ConfigureImport();
            AssetDatabase.SaveAssets();
            // L'import appena fatto (SaveAndReimport) puo' non avere ancora
            // materializzato tutte le clip sub-asset: forza il refresh sincrono.
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
            AugmentController();
        }

        private static void ConfigureImport()
        {
            var importer = AssetImporter.GetAtPath(FbxPath) as ModelImporter;
            if (importer == null)
            {
                Debug.LogWarning("[UAL2] FBX assente: " + FbxPath);
                return;
            }

            bool dirty = false;
            if (importer.animationType != ModelImporterAnimationType.Human ||
                importer.avatarSetup != ModelImporterAvatarSetup.CreateFromThisModel)
            {
                importer.animationType = ModelImporterAnimationType.Human;
                importer.avatarSetup = ModelImporterAvatarSetup.CreateFromThisModel;
                dirty = true;
            }
            if (!importer.importAnimation)
            {
                importer.importAnimation = true;
                dirty = true;
            }
            if (importer.materialImportMode != ModelImporterMaterialImportMode.None)
            {
                importer.materialImportMode = ModelImporterMaterialImportMode.None;
                importer.materialLocation = ModelImporterMaterialLocation.External;
                dirty = true;
            }
            if (!importer.optimizeGameObjects)
            {
                importer.optimizeGameObjects = true;
                dirty = true;
            }
            if (Mathf.Abs(importer.globalScale - 1f) > 0.001f)
            {
                importer.globalScale = 1f;
                dirty = true;
            }

            // Clip: blocca root motion (il movimento lo fa il CharacterController)
            // e imposta il loop per le clip "_Loop".
            var src = importer.defaultClipAnimations ?? new ModelImporterClipAnimation[0];
            if (src.Length > 0)
            {
                var list = new List<ModelImporterClipAnimation>(src);
                bool clipsDirty = false;
                for (int i = 0; i < list.Count; i++)
                {
                    var c = list[i];
                    if (!c.lockRootRotation || !c.lockRootHeightY || !c.lockRootPositionXZ)
                    {
                        c.lockRootRotation = true;
                        c.lockRootHeightY = true;
                        c.lockRootPositionXZ = true;
                        clipsDirty = true;
                    }
                    bool loop = c.name.Contains("_Loop");
                    if (c.loopTime != loop || c.loopPose != loop)
                    {
                        c.loopTime = loop;
                        c.loopPose = loop;
                        clipsDirty = true;
                    }
                    list[i] = c;
                }
                if (clipsDirty)
                {
                    importer.clipAnimations = list.ToArray();
                    dirty = true;
                }
            }

            if (dirty)
            {
                importer.SaveAndReimport();
            }

            Avatar avatar = null;
            foreach (var o in AssetDatabase.LoadAllAssetsAtPath(FbxPath))
            {
                avatar = o as Avatar;
                if (avatar != null) break;
            }
            Debug.Log("[UAL2] Importato " + FbxPath + " clip=" + src.Length +
                " avatarHuman=" + (avatar != null && avatar.isHuman) +
                " avatarValid=" + (avatar != null && avatar.isValid));
        }

        private static void AugmentController()
        {
            var ctrl = AssetDatabase.LoadAssetAtPath<AnimatorController>(CtrlPath);
            if (ctrl == null)
            {
                Debug.LogWarning("[UAL2] Controller assente: " + CtrlPath);
                return;
            }

            AnimatorStateMachine sm = ctrl.layers[0].stateMachine;
            foreach (var child in sm.states)
            {
                if (child.state != null && child.state.name.StartsWith(StatePrefix))
                {
                    Debug.Log("[UAL2] Stati azione gia' presenti, nessuna modifica.");
                    return;
                }
            }

            // Le clip sub-asset dell'FBX possono richiedere qualche frame dopo
            // il reimport: retry finche' la conta e' stabile, cosi' non si
            // aggancia un'import parziale.
            var clips = new List<AnimationClip>();
            int previous = -1;
            for (int attempt = 0; attempt < 8; attempt++)
            {
                clips = LoadClipsFromFbx();
                if (clips.Count == previous && clips.Count > 0) break;
                previous = clips.Count;
                System.Threading.Thread.Sleep(400);
                AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
            }

            // L'FBX espone ogni take due volte ("X" e "X 0"): tieni la prima
            // occorrenza per nome corto.
            var unique = new Dictionary<string, AnimationClip>();
            foreach (var clip in clips)
            {
                string shortName = ClipShortName(clip.name);
                if (!unique.ContainsKey(shortName)) unique[shortName] = clip;
            }
            var sorted = new List<AnimationClip>(unique.Values);
            sorted.Sort((a, b) => string.CompareOrdinal(a.name, b.name));

            AnimatorState def = sm.defaultState;
            int index = 0;
            int added = 0;
            foreach (var clip in sorted)
            {
                string shortName = ClipShortName(clip.name);
                if (shortName == "A_TPose") continue;

                string pname = ParamPrefix + Sanitize(shortName);
                if (!HasParameter(ctrl, pname))
                    ctrl.AddParameter(pname, AnimatorControllerParameterType.Trigger);

                var state = sm.AddState(StatePrefix + shortName,
                    new Vector3(760f, -40f + index * 55f, 0f));
                state.motion = clip;
                state.writeDefaultValues = true;

                // Qualsiasi stato -> azione quando scatta il trigger.
                var enter = sm.AddAnyStateTransition(state);
                enter.hasExitTime = false;
                enter.duration = 0.08f;
                enter.canTransitionToSelf = false;
                enter.AddCondition(AnimatorConditionMode.If, 0f, pname);

                // A fine clip torna alla locomozione.
                if (def != null)
                {
                    var exit = state.AddTransition(def);
                    exit.hasExitTime = true;
                    exit.exitTime = 0.9f;
                    exit.duration = 0.15f;
                }
                index++;
                added++;
            }

            AssetDatabase.SaveAssets();
            Debug.Log("[UAL2] Aggiunti " + added + " stati azione (" + unique.Count +
                " clip uniche su " + clips.Count + ") al controller " + CtrlPath);
        }

        private static List<AnimationClip> LoadClipsFromFbx()
        {
            var list = new List<AnimationClip>();
            foreach (var o in AssetDatabase.LoadAllAssetsAtPath(FbxPath))
            {
                var clip = o as AnimationClip;
                if (clip != null) list.Add(clip);
            }
            return list;
        }

        /// <summary>Normalizza "Armature|OverhandThrow" in "OverhandThrow".</summary>
        private static string ClipShortName(string raw)
        {
            int bar = raw.LastIndexOf('|');
            return bar >= 0 && bar < raw.Length - 1
                ? raw.Substring(bar + 1)
                : raw;
        }

        private static bool HasParameter(AnimatorController ctrl, string name)
        {
            foreach (var p in ctrl.parameters)
            {
                if (p.name == name) return true;
            }
            return false;
        }

        private static string Sanitize(string raw)
        {
            var sb = new System.Text.StringBuilder(raw.Length);
            foreach (char ch in raw)
            {
                if (char.IsLetterOrDigit(ch)) sb.Append(ch);
                else sb.Append('_');
            }
            return sb.ToString();
        }
    }
}