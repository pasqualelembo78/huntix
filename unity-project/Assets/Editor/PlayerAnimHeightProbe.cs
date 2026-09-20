using UnityEditor;
using UnityEditor.Animations;
using UnityEngine;

namespace Huntix.EditorTools
{
    /// <summary>
    /// Misura l'altezza del root/hips del PlayerHero con il controller
    /// PlayerLocomotion a Speed=0 (idle), 3.2 (walk) e 7.5 (run) per
    /// individuare un sink del corpo (~1m) tra le clip Mixamo.
    /// Read-only: non modifica asset.
    /// Eseguibile da CLI:
    ///   -executeMethod Huntix.EditorTools.PlayerAnimHeightProbe.Run
    /// </summary>
    public static class PlayerAnimHeightProbe
    {
        public static void Run()
        {
            var prefab = Resources.Load<GameObject>("PlayerHero");
            if (prefab == null)
            {
                Debug.LogError("[AnimProbe] prefab PlayerHero NULL");
                return;
            }
            var ctrl = Resources.Load<RuntimeAnimatorController>("Mixamo/PlayerLocomotion");
            if (ctrl == null)
            {
                Debug.LogError("[AnimProbe] controller PlayerLocomotion NULL");
                return;
            }
            Debug.Log("[AnimProbe] prefab=" + prefab.name +
                " ctrl=" + ctrl.name + " ctrlType=" + ctrl.GetType().Name);

            var animator = prefab.GetComponent<Animator>();
            Debug.Log("[AnimProbe] animatorOnPrefab=" + (animator != null) +
                " avatar=" + (animator != null && animator.avatar != null ? animator.avatar.name : "NULL"));

            var go = Object.Instantiate(prefab);
            go.name = "ProbeRig";
            var anim = go.GetComponent<Animator>();
            if (anim == null) anim = go.AddComponent<Animator>();
            anim.avatar = animator != null ? animator.avatar : null;
            anim.runtimeAnimatorController = ctrl;
            anim.applyRootMotion = false;

            float[] speeds = { 0f, 0.5f, 3.2f, 7.5f };
            foreach (float s in speeds)
            {
                anim.Play("LocomotionBlend", 0, 0f);
                anim.SetFloat("Speed", s);
                // step tante volte per stabilizzare il blend + pose
                for (int i = 0; i < 90; i++)
                {
                    anim.Update(1f / 30f);
                    if (i == 88) Sample(anim, "pre-final");
                    if (i < 30) continue;
                }
                Sample(anim, "step90");
            }

            Debug.Log("[AnimProbe] --- singole clip col LORO avatar sorgente ---");
            var sourceAvatars = new System.Collections.Generic.Dictionary<string, Avatar>();
            string[] fbxPaths = {
                "Assets/Art/Mixamo/Idle.fbx",
                "Assets/Art/Mixamo/Walking.fbx",
                "Assets/Art/Mixamo/Run.fbx",
                "Assets/Art/Mixamo/Jump.fbx",
            };
            foreach (string p in fbxPaths)
            {
                foreach (Object o in AssetDatabase.LoadAllAssetsAtPath(p))
                    if (o is Avatar) sourceAvatars[o.name] = o as Avatar;
            }
            foreach (var clip in EditorUtility.CollectDependencies(new[] { ctrl as Object }))
            {
                var ac = clip as AnimationClip;
                if (ac == null) continue;
                foreach (var kv in sourceAvatars)
                {
                    Debug.Log("[AnimProbe] avatarCandidates[" + ac.name + "] -> " + kv.Key);
                }
                SampleClipOnAvatar(anim, ac, sourceAvatars, "ownAvatar clip=" + ac.name);
            }

            Debug.Log("[AnimProbe] --- singole clip dirette (sample a t=0.5s) ---");
            foreach (var clip in EditorUtility.CollectDependencies(new[] { ctrl as Object }))
            {
                var ac = clip as AnimationClip;
                if (ac == null) continue;
                SampleClip(anim, ac, "clip=" + ac.name);
            }

            Debug.Log("[AnimProbe] --- clip: sample multi-time con bounds SMR ---");
            foreach (var clip in EditorUtility.CollectDependencies(new[] { ctrl as Object }))
            {
                var ac = clip as AnimationClip;
                if (ac == null) continue;
                SampleClipMulti(anim, ac);
            }

            Object.DestroyImmediate(go);
            Debug.Log("[AnimProbe] FINE");
        }

        private static void Sample(Animator anim, string tag)
        {
            Transform hips = anim.GetBoneTransform(HumanBodyBones.Hips);
            Transform lFoot = anim.GetBoneTransform(HumanBodyBones.LeftFoot);
            Transform rFoot = anim.GetBoneTransform(HumanBodyBones.RightFoot);
            float feetY = 0f;
            if (lFoot != null && rFoot != null)
                feetY = Mathf.Min(lFoot.position.y, rFoot.position.y);
            Debug.Log("[AnimProbe] " + tag + " speed=" + anim.GetFloat("Speed") +
                " rootY=" + goRootY(anim).ToString("F3") +
                " hipsY=" + (hips != null ? hips.position.y.ToString("F3") : "NULL") +
                " feetY=" + feetY.ToString("F3") +
                " hipsMinusFeet=" + (hips != null ? (hips.position.y - feetY).ToString("F3") : "NULL"));
        }

        private static float goRootY(Animator anim)
        {
            return anim.transform.position.y;
        }

        private static void SampleClipOnAvatar(Animator anim, AnimationClip clip,
            System.Collections.Generic.Dictionary<string, Avatar> avatars, string tag)
        {
            Avatar avatar = null;
            foreach (var kv in avatars)
            {
                if (clip.name == kv.Key || clip.name.StartsWith(kv.Key))
                {
                    avatar = kv.Value;
                    break;
                }
            }
            if (avatar == null)
            {
                Debug.Log("[AnimProbe] " + tag + " avatar non trovato, skip");
                return;
            }
            var oldAvatar = anim.avatar;
            anim.avatar = avatar;

            var tmpName = "ProbeCtrl_TmpA";
            var existing = AssetDatabase.LoadAssetAtPath<AnimatorController>("Assets/Editor/" + tmpName + ".controller");
            if (existing != null)
            {
                AssetDatabase.DeleteAsset("Assets/Editor/" + tmpName + ".controller");
                AssetDatabase.Refresh();
            }
            var c = AnimatorController.CreateAnimatorControllerAtPath("Assets/Editor/" + tmpName + ".controller");
            var layer = c.layers[0];
            var sm = layer.stateMachine;
            var st = sm.AddState("Solo", new Vector3(0, 0, 0));
            st.motion = clip;
            anim.runtimeAnimatorController = c;
            anim.Play("Solo", 0, 0f);
            anim.Update(0f);
            for (int i = 0; i <= 90; i++)
            {
                anim.Update(1f / 30f);
                if (i == 30 || i == 90)
                {
                    Sample(anim, tag + " t=" + (i / 30f).ToString("F2") +
                        " avatar=" + (avatar != null ? avatar.name : "?"));
                }
            }
            Object.DestroyImmediate(c);
            AssetDatabase.DeleteAsset("Assets/Editor/" + tmpName + ".controller");
            anim.avatar = oldAvatar;
        }

        private static void SampleClip(Animator anim, AnimationClip clip, string tag)
        {
            var tmpName = "ProbeCtrl_Tmp";
            var existing = AssetDatabase.LoadAssetAtPath<AnimatorController>("Assets/Editor/" + tmpName + ".controller");
            if (existing != null)
            {
                AssetDatabase.DeleteAsset("Assets/Editor/" + tmpName + ".controller");
                AssetDatabase.Refresh();
            }
            var c = AnimatorController.CreateAnimatorControllerAtPath("Assets/Editor/" + tmpName + ".controller");
            var layer = c.layers[0];
            var sm = layer.stateMachine;
            var st = sm.AddState("Solo", new Vector3(0, 0, 0));
            st.motion = clip;
            c.AddParameter("Speed", AnimatorControllerParameterType.Float);
            anim.runtimeAnimatorController = c;
            anim.Play("Solo", 0, 0f);
            anim.Update(clip.frameRate > 0 ? 0.5f * (30f / clip.frameRate) : 0.5f);
            anim.Update(1f / 30f);
            Sample(anim, tag);
            AssetDatabase.DeleteAsset("Assets/Editor/" + tmpName + ".controller");
        }

        private static void SampleClipMulti(Animator anim, AnimationClip clip)
        {
            var tmpName = "ProbeCtrl_Tmp2";
            var existing = AssetDatabase.LoadAssetAtPath<AnimatorController>("Assets/Editor/" + tmpName + ".controller");
            if (existing != null)
            {
                AssetDatabase.DeleteAsset("Assets/Editor/" + tmpName + ".controller");
                AssetDatabase.Refresh();
            }
            var c = AnimatorController.CreateAnimatorControllerAtPath("Assets/Editor/" + tmpName + ".controller");
            var layer = c.layers[0];
            var sm = layer.stateMachine;
            var st = sm.AddState("Solo", new Vector3(0, 0, 0));
            st.motion = clip;
            anim.runtimeAnimatorController = c;
            anim.Play("Solo", 0, 0f);
            anim.Update(0f);
            for (int i = 0; i <= 100; i++)
            {
                anim.Update(1f / 30f);
                if (i == 0 || i == 5 || i == 30 || i == 60 || i == 100)
                {
                    var smrs = anim.GetComponentsInChildren<SkinnedMeshRenderer>(true);
                    Bounds b = new Bounds();
                    bool any = false;
                    for (int k = 0; k < smrs.Length; k++)
                    {
                        if (smrs[k] == null || !smrs[k].gameObject.activeInHierarchy) continue;
                        if (!any) { b = smrs[k].bounds; any = true; }
                        else b.Encapsulate(smrs[k].bounds);
                    }
                    Sample(anim, "mv t=" + (i / 30f).ToString("F2") +
                        " boundsY=" + (any ? b.min.y.ToString("F3") : "?") +
                        " boundsH=" + (any ? b.size.y.ToString("F3") : "?"));
                }
            }
            Object.DestroyImmediate(c);
            AssetDatabase.DeleteAsset("Assets/Editor/" + tmpName + ".controller");
        }
    }
}