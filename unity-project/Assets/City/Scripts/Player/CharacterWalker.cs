using System.Collections;
using System.Collections.Generic;
using UnityEngine;

namespace City.Player
{
    /// <summary>
    /// Animatore del personaggio Kenney characterMedium con doppia modalita':
    ///
    /// 1) ANIMATOR MODE (player): se esiste un Animator CON controller,
    ///    costruisce a runtime un AnimatorController con BlendTree Idle→Walk→Run
    ///    guidato dal parametro float "Speed" + Foot IK per piedi sul terreno DEM.
    ///
    /// 2) PROCEDURAL MODE (NPC): se l'Animator manca o non ha controller,
    ///    usa Animation legacy con curve procedurali generate a runtime
    ///    (stesso codice di prima per retrocompatibilita' NPC).
    ///
    /// PlayerController e NPCController chiamano SetSpeed ogni frame.
    /// </summary>
    public class CharacterWalker : MonoBehaviour
    {
        // ── Interfaccia comune ──
        private float speed;
        private bool ready;
        private bool useAnimator;
        private Animator animator;

        // ── Parametri Animator mode ──
        [Header("Animator Mode (player)")]
        public float walkSpeed = 3.2f;
        public float runSpeed = 7.5f;

        // ── Foot IK ──
        [Header("Foot IK")]
        public bool footIKEnabled = true;
        public float ikRayDistance = 1.5f;
        public float ikSmoothSpeed = 12f;
        public float ikMaxOffset = 0.3f;
        public LayerMask groundMask = ~0;
        private float _ikWeightL, _ikWeightR;
        private Vector3 _ikPosL, _ikPosR;
        private Quaternion _ikRotL, _ikRotR;
        private float _hipOffsetY;
        private float _hipOffsetTarget;
        private float _hipsBaseY = float.NaN;

        // ── Parametri procedural mode (NPC) ──
        private const string IdleState = "Idle";
        private const string WalkState = "Walk";
        private const string RunState = "Run";
        private const string HipsPath = "Root/HipsCtrl/Hips";
        private const string SpinePath = HipsPath + "/Spine";
        private const string ChestPath = SpinePath + "/Chest";
        private const string UpChestPath = ChestPath + "/UpperChest";
        private const string LUpLegPath = HipsPath + "/LeftUpLeg";
        private const string LLegPath = LUpLegPath + "/LeftLeg";
        private const string RUpLegPath = HipsPath + "/RightUpLeg";
        private const string RLegPath = RUpLegPath + "/RightLeg";
        private const string LShoulderPath = UpChestPath + "/LeftShoulder";
        private const string RShoulderPath = UpChestPath + "/RightShoulder";
        private const string LArmPath = LShoulderPath + "/LeftArm";
        private const string RArmPath = RShoulderPath + "/RightArm";
        private const string LForeArmPath = LArmPath + "/LeftForeArm";
        private const string RForeArmPath = RArmPath + "/RightForeArm";

        [Header("Procedural Mode (NPC)")]
        public float legSwingDeg = 30f;
        public float kneeBendDeg = 34f;
        public float armSwingDeg = 22f;
        public float elbowBendDeg = 26f;
        public float runThreshold = 4.2f;
        public float walkCadence = 1.7f;
        public float runCadence = 2.6f;
        public float walkStrideM = 1.1f;
        public float runStrideM = 2.0f;
        public float forwardLeanDeg = 7f;
        public float walkBounceM = 0f;
        public float runBounceM = 0f;
        public float swingSign = 1f;

        private Quaternion _foldL = Quaternion.identity;
        private Quaternion _foldR = Quaternion.identity;
        private bool _foldReady;
        private Animation anim;
        private string current;
        private bool _swingReady;
        private Vector3 _swingAxisL = Vector3.right;
        private Vector3 _swingAxisR = Vector3.right;
        private bool _legAxisReady;
        private Vector3 _hipAxisL = Vector3.right;
        private Vector3 _hipAxisR = Vector3.right;
        private Vector3 _kneeAxisL = Vector3.right;
        private Vector3 _kneeAxisR = Vector3.right;
        private Vector3 _elbowAxisL = Vector3.right;
        private Vector3 _elbowAxisR = Vector3.right;

        public bool IsReady { get { return ready; } }

        public static CharacterWalker AttachIfNeeded(GameObject ownerRoot)
        {
            if (ownerRoot == null) return null;
            CharacterWalker existing = ownerRoot.GetComponentInChildren<CharacterWalker>();
            if (existing != null) return existing;
            SkinnedMeshRenderer smr = ownerRoot.GetComponentInChildren<SkinnedMeshRenderer>();
            if (smr == null) return null;

            Transform target = null;
            for (Transform t = smr.transform; t != null; t = t.parent)
            {
                if (t.Find("Root") != null) { target = t; break; }
            }
            if (target == null) target = smr.transform;

            var w = target.GetComponent<CharacterWalker>();
            if (w != null) return w;
            return target.gameObject.AddComponent<CharacterWalker>();
        }

        private void Start()
        {
            animator = GetComponentInChildren<Animator>(true);

            // Prova a caricare il controller Mixamo da Resources
            var mixamoCtrl = Resources.Load<RuntimeAnimatorController>(
                "Mixamo/PlayerLocomotion");

            if (animator != null)
            {
                if (mixamoCtrl != null)
                {
                    // Player: usa il controller Mixamo con BlendTree + Foot IK
                    useAnimator = true;
                    SetupAnimatorMode(mixamoCtrl);
                }
                else if (animator.runtimeAnimatorController != null)
                {
                    // Fallback: usa il controller esistente (es. PlayerAnim Kenney)
                    useAnimator = true;
                    SetupAnimatorMode(animator.runtimeAnimatorController);
                }
                else
                {
                    // NPC senza controller: destruilo e usa procedurale
                    DestroyImmediate(animator);
                    useAnimator = false;
                    BuildProcedural();
                }
            }
            else
            {
                useAnimator = false;
                BuildProcedural();
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // ANIMATOR MODE - BlendTree + Foot IK
        // ═══════════════════════════════════════════════════════════════

        private void SetupAnimatorMode(RuntimeAnimatorController controller)
        {
            animator.applyRootMotion = false;
            animator.runtimeAnimatorController = controller;
            ready = true;
            City.OSM.OsmDiag.Log("[CharacterWalker][Animator] Controller assegnato: " +
                controller.name + " footIK=" + footIKEnabled);
        }

        private void UpdateAnimatorMode()
        {
            if (animator == null) return;
            // Il controller Mixamo usa "Speed"; il fallback Kenney usa "IsMoving".
            // SetFloat/SetBool su parametri inesistenti sono no-op (sicuri).
            animator.SetFloat("Speed", speed);
            animator.SetBool("IsMoving", speed > 0.15f);
        }

        // ── Foot IK ──

        private void OnAnimatorIK(int layerIndex)
        {
            if (!useAnimator || !footIKEnabled || animator == null) return;

            FootIKStep(AvatarIKGoal.LeftFoot, ref _ikWeightL, ref _ikPosL, ref _ikRotL);
            FootIKStep(AvatarIKGoal.RightFoot, ref _ikWeightR, ref _ikPosR, ref _ikRotR);

            _hipOffsetTarget = Mathf.Min(_ikPosL.y, _ikPosR.y);
            float groundRef = (transform.position.y);
            float offset = Mathf.Clamp(_hipOffsetTarget - groundRef, -ikMaxOffset, 0f);
            _hipOffsetY = Mathf.Lerp(_hipOffsetY, offset, ikSmoothSpeed * Time.deltaTime);

            Transform hipsBone = animator.GetBoneTransform(HumanBodyBones.Hips);
            if (hipsBone != null)
            {
                if (float.IsNaN(_hipsBaseY)) _hipsBaseY = hipsBone.localPosition.y;
                Vector3 hp = hipsBone.localPosition;
                hp.y = _hipsBaseY + _hipOffsetY;
                hipsBone.localPosition = hp;
            }
        }

        private void FootIKStep(AvatarIKGoal goal,
            ref float weight, ref Vector3 pos, ref Quaternion rot)
        {
            Transform footBone = animator.GetBoneTransform(
                goal == AvatarIKGoal.LeftFoot
                    ? HumanBodyBones.LeftFoot
                    : HumanBodyBones.RightFoot);
            Transform hipsBone = animator.GetBoneTransform(HumanBodyBones.Hips);
            if (footBone == null || hipsBone == null)
            {
                weight = Mathf.MoveTowards(weight, 0f, ikSmoothSpeed * Time.deltaTime);
                return;
            }

            Vector3 footWorld = footBone.position;
            Vector3 hipWorld = hipsBone.position;
            Vector3 origin = footWorld + Vector3.up * 0.3f;
            float maxDist = Mathf.Abs(footWorld.y - hipWorld.y) + ikRayDistance;

            Ray ray = new Ray(origin, Vector3.down);
            RaycastHit hit;
            float targetWeight = 0f;
            Vector3 targetPos = footWorld;
            Quaternion targetRot = footBone.rotation;

            if (Physics.Raycast(ray, out hit, maxDist, groundMask))
            {
                targetPos = hit.point + Vector3.up * 0.02f;
                targetRot = Quaternion.FromToRotation(Vector3.up, hit.normal) *
                    Quaternion.LookRotation(transform.forward, Vector3.up);
                targetWeight = Mathf.Clamp01(1f - hit.distance / maxDist);
            }

            weight = Mathf.MoveTowards(weight, targetWeight, ikSmoothSpeed * Time.deltaTime);
            pos = Vector3.Lerp(pos, targetPos, ikSmoothSpeed * Time.deltaTime);
            rot = Quaternion.Slerp(rot, targetRot, ikSmoothSpeed * Time.deltaTime);

            animator.SetIKPositionWeight(goal, weight);
            animator.SetIKPosition(goal, pos);
            animator.SetIKRotationWeight(goal, weight * 0.8f);
            animator.SetIKRotation(goal, rot);
        }

        // ═══════════════════════════════════════════════════════════════
        // PROCEDURAL MODE - curve legacy per NPC
        // ═══════════════════════════════════════════════════════════════

        private void CalibrateFold()
        {
            Transform lSh = transform.Find(LShoulderPath);
            Transform rSh = transform.Find(RShoulderPath);
            Transform lArm = transform.Find(LArmPath);
            Transform rArm = transform.Find(RArmPath);
            if (lSh == null || rSh == null || lArm == null || rArm == null) return;
            try
            {
                Quaternion lShWorld = lSh.parent != null
                    ? lSh.parent.rotation * lSh.localRotation : lSh.localRotation;
                Quaternion rShWorld = rSh.parent != null
                    ? rSh.parent.rotation * rSh.localRotation : rSh.localRotation;
                Vector3 lDir = (lArm.position - lSh.position).normalized;
                Vector3 rDir = (rArm.position - rSh.position).normalized;
                if (lDir.sqrMagnitude < 0.0001f || rDir.sqrMagnitude < 0.0001f) return;
                Vector3 lTgt = FoldTarget(transform.right * -1f);
                Vector3 rTgt = FoldTarget(transform.right * 1f);
                _foldL = SideFold(lShWorld, lDir, lTgt);
                _foldR = SideFold(rShWorld, rDir, rTgt);
                _foldReady = true;
                _swingAxisL = Quaternion.Inverse(lShWorld) * (transform.right * -1f);
                _swingAxisR = Quaternion.Inverse(rShWorld) * (transform.right * 1f);
                _swingReady = true;
            }
            catch (System.Exception ex)
            {
                Debug.LogWarning("[CharacterWalker] calibrazione braccia: " + ex);
            }
        }

        private static Quaternion SideFold(Quaternion shWorld, Vector3 dirW, Vector3 tgtW)
        {
            Vector3 dirL = Quaternion.Inverse(shWorld) * dirW;
            Vector3 tgtL = Quaternion.Inverse(shWorld) * tgtW;
            if (dirL.sqrMagnitude < 0.0001f || tgtL.sqrMagnitude < 0.0001f)
                return Quaternion.identity;
            return Quaternion.FromToRotation(dirL.normalized, tgtL.normalized);
        }

        private static Vector3 FoldTarget(Vector3 lateral)
        {
            lateral.y = 0f;
            if (lateral.sqrMagnitude < 0.001f) lateral = Vector3.right;
            lateral.Normalize();
            return (Vector3.down * 0.955f + lateral * 0.30f).normalized;
        }

        private void CalibrateLimbAxes()
        {
            Vector3 lateral = transform.right;
            try
            {
                _hipAxisL = LocalFlexAxis(LUpLegPath, lateral);
                _hipAxisR = LocalFlexAxis(RUpLegPath, lateral);
                _kneeAxisL = LocalFlexAxis(LLegPath, lateral);
                _kneeAxisR = LocalFlexAxis(RLegPath, lateral);
                _elbowAxisL = LocalFlexAxis(LForeArmPath, lateral);
                _elbowAxisR = LocalFlexAxis(RForeArmPath, lateral);
                _legAxisReady = true;
            }
            catch (System.Exception ex)
            {
                Debug.LogWarning("[CharacterWalker] calibrazione arti: " + ex);
            }
        }

        private Vector3 LocalFlexAxis(string bonePath, Vector3 worldLateral)
        {
            Transform bone = transform.Find(bonePath);
            if (bone == null) return worldLateral;
            Quaternion world = bone.rotation;
            return Quaternion.Inverse(world) * worldLateral;
        }

        private Quaternion HipFlex(bool right, float angle)
        {
            if (!_legAxisReady) return Quaternion.AngleAxis(angle, right ? Vector3.right : -Vector3.right);
            return Quaternion.AngleAxis(angle, right ? _hipAxisR : _hipAxisL);
        }
        private Quaternion KneeFlex(bool right, float angle)
        {
            if (!_legAxisReady) return Quaternion.AngleAxis(angle, right ? Vector3.right : -Vector3.right);
            return Quaternion.AngleAxis(angle, right ? _kneeAxisR : _kneeAxisL);
        }
        private Quaternion ElbowFlex(bool right, float angle)
        {
            if (!_legAxisReady) return Quaternion.AngleAxis(angle, right ? Vector3.right : -Vector3.right);
            return Quaternion.AngleAxis(angle, right ? _elbowAxisR : _elbowAxisL);
        }
        private Quaternion FoldDelta(bool right)
        {
            if (_foldReady) return right ? _foldR : _foldL;
            return Quaternion.Euler(right ? 80f : -80f, 0f, 0f);
        }
        private Quaternion SwingDelta(bool right, float angle)
        {
            if (!_swingReady) return Quaternion.identity;
            return Quaternion.AngleAxis(angle, right ? _swingAxisR : _swingAxisL);
        }

        private void BuildProcedural()
        {
            CalibrateFold();
            CalibrateLimbAxes();
            anim = gameObject.GetComponent<Animation>();
            if (anim == null) anim = gameObject.AddComponent<Animation>();
            anim.AddClip(BuildLocomotion("walk", legSwingDeg, kneeBendDeg,
                armSwingDeg, walkCadence, walkBounceM, forwardLeanDeg * 0.4f, 3f), WalkState);
            anim.AddClip(BuildLocomotion("run", legSwingDeg * 1.5f,
                kneeBendDeg * 1.35f, armSwingDeg * 1.5f, runCadence, runBounceM,
                forwardLeanDeg, 8f), RunState);
            anim.AddClip(BuildIdle(), IdleState);
            current = IdleState;
            anim.Play(IdleState);

            if (transform.Find(HipsPath) == null ||
                transform.Find(LUpLegPath) == null)
            {
                Debug.LogWarning("[CharacterWalker] ossa non raggiunte su " +
                    gameObject.name + " hips=" +
                    (transform.Find(HipsPath) != null) + " lLeg=" +
                    (transform.Find(LUpLegPath) != null) + " animator=" +
                    (GetComponentInChildren<Animator>(true) != null));
            }
            ready = true;
        }

        private AnimationClip BuildLocomotion(string tag, float legSwing,
            float kneeBend, float armSwing, float cadence, float bounce,
            float leanDeg, float thighBiasDeg)
        {
            float dur = 1f / Mathf.Max(0.1f, cadence);
            const int steps = 28;
            Transform hips = transform.Find(HipsPath);
            float hipY = hips != null ? hips.localPosition.y : 0f;

            RotBaker rot = new RotBaker(BoneRest);
            PosBaker pos = new PosBaker();
            for (int i = 0; i <= steps; i++)
            {
                float t = dur * i / steps;
                float ph = t / dur * Mathf.PI * 2f;
                float sinL = Mathf.Sin(ph);
                float sinR = Mathf.Sin(ph + Mathf.PI);

                rot.RotQ(t, LUpLegPath,
                    HipFlex(false, swingSign * (sinL * legSwing + thighBiasDeg)));
                rot.RotQ(t, RUpLegPath,
                    HipFlex(true, swingSign * (sinR * legSwing + thighBiasDeg)));
                float kneeL = Mathf.Clamp01(-sinL) * kneeBend;
                float kneeR = Mathf.Clamp01(-sinR) * kneeBend;
                rot.RotQ(t, LLegPath, KneeFlex(false, swingSign * kneeL));
                rot.RotQ(t, RLegPath, KneeFlex(true, swingSign * kneeR));
                rot.RotQ(t, LShoulderPath,
                    SwingDelta(false, swingSign * (sinR * armSwing)) * FoldDelta(false));
                rot.RotQ(t, RShoulderPath,
                    SwingDelta(true, swingSign * (sinL * armSwing)) * FoldDelta(true));
                float elbowL = Mathf.Clamp01(Mathf.Abs(sinL)) * elbowBendDeg;
                float elbowR = Mathf.Clamp01(Mathf.Abs(sinR)) * elbowBendDeg;
                rot.RotQ(t, LForeArmPath, ElbowFlex(false, swingSign * elbowL));
                rot.RotQ(t, RForeArmPath, ElbowFlex(true, swingSign * elbowR));
                rot.Rot(t, SpinePath, leanDeg, sinR * 6f, sinR * 3f);
                rot.Rot(t, UpChestPath, leanDeg * 0.45f, sinL * 5f, 0f);
                pos.Pos(t, HipsPath, hipY + Mathf.Sin(ph * 2f) * bounce);
            }
            AnimationClip clip = new AnimationClip { wrapMode = WrapMode.Loop, legacy = true };
            rot.Flush(clip);
            pos.Flush(clip);
            return clip;
        }

        private AnimationClip BuildIdle()
        {
            const float dur = 3.4f;
            const int steps = 20;
            Transform hips = transform.Find(HipsPath);
            float hipY = hips != null ? hips.localPosition.y : 0f;

            RotBaker rot = new RotBaker(BoneRest);
            PosBaker pos = new PosBaker();
            for (int i = 0; i <= steps; i++)
            {
                float t = dur * i / steps;
                float ph = t / dur * Mathf.PI * 2f;
                float breathe = Mathf.Sin(ph);
                rot.Rot(t, SpinePath, 1.2f * breathe, 0f, 0f);
                rot.RotQ(t, LShoulderPath,
                    SwingDelta(false, 3f * breathe) * FoldDelta(false));
                rot.RotQ(t, RShoulderPath,
                    SwingDelta(true, 3f * breathe) * FoldDelta(true));
                pos.Pos(t, HipsPath, hipY + breathe * 0.004f);
            }
            AnimationClip clip = new AnimationClip { wrapMode = WrapMode.Loop, legacy = true };
            rot.Flush(clip);
            pos.Flush(clip);
            return clip;
        }

        private Quaternion? BoneRest(string path)
        {
            Transform t = transform.Find(path);
            return t != null ? (Quaternion?)t.localRotation : null;
        }

        // ═══════════════════════════════════════════════════════════════
        // UPDATE COMUNE
        // ═══════════════════════════════════════════════════════════════

        public void SetSpeed(float metersPerSecond)
        {
            speed = metersPerSecond;
        }

        private void Update()
        {
            if (!ready) return;

            if (useAnimator)
            {
                UpdateAnimatorMode();
            }
            else
            {
                UpdateProceduralMode();
            }
        }

        private void UpdateProceduralMode()
        {
            if (anim == null) return;
            string want = IdleState;
            if (speed > 0.15f)
                want = speed > runThreshold ? RunState : WalkState;
            if (want != current)
            {
                current = want;
                anim.CrossFade(want, 0.18f);
            }
            AnimationState st = anim[current];
            if (st != null)
            {
                bool running = current == RunState;
                float baseCad = running ? runCadence : walkCadence;
                float stride = running ? runStrideM : walkStrideM;
                float cad = speed / Mathf.Max(0.1f, stride);
                st.speed = Mathf.Clamp(cad / Mathf.Max(0.1f, baseCad), 0.4f, 2.1f);
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // ROTBAKER / POSBAKER - curve procedurali legacy
        // ═══════════════════════════════════════════════════════════════

        private sealed class RotBaker
        {
            private readonly Dictionary<string, List<float[]>> keys =
                new Dictionary<string, List<float[]>>();
            private readonly System.Func<string, Quaternion?> restLookup;

            public RotBaker(System.Func<string, Quaternion?> restLookup)
            {
                this.restLookup = restLookup;
            }

            public void Rot(float time, string path, float x, float y, float z)
            {
                Quaternion? rest = restLookup != null ? restLookup(path) : null;
                if (rest == null) return;
                Quaternion q = rest.Value * Quaternion.Euler(x, y, z);
                List<float[]> list;
                if (!keys.TryGetValue(path, out list))
                {
                    list = new List<float[]>();
                    keys[path] = list;
                }
                list.Add(new[] { time, q.x, q.y, q.z, q.w });
            }

            public void RotQ(float time, string path, Quaternion delta)
            {
                Quaternion? rest = restLookup != null ? restLookup(path) : null;
                if (rest == null) return;
                Quaternion q = rest.Value * delta;
                List<float[]> list;
                if (!keys.TryGetValue(path, out list))
                {
                    list = new List<float[]>();
                    keys[path] = list;
                }
                list.Add(new[] { time, q.x, q.y, q.z, q.w });
            }

            public void Flush(AnimationClip clip)
            {
                if (clip == null || !clip.legacy) return;
                foreach (KeyValuePair<string, List<float[]>> kv in keys)
                {
                    List<float[]> list = kv.Value;
                    const string comps = "xyzw";
                    for (int c = 0; c < 4; c++)
                    {
                        AnimationCurve curve = new AnimationCurve();
                        foreach (float[] k in list)
                            curve.AddKey(k[0], k[1 + c]);
                        clip.SetCurve(kv.Key, typeof(Transform),
                            "m_LocalRotation." + comps[c], curve);
                    }
                }
            }
        }

        private sealed class PosBaker
        {
            private readonly Dictionary<string, List<float[]>> keys =
                new Dictionary<string, List<float[]>>();

            public void Pos(float time, string path, float localY)
            {
                List<float[]> list;
                if (!keys.TryGetValue(path, out list))
                {
                    list = new List<float[]>();
                    keys[path] = list;
                }
                list.Add(new[] { time, localY });
            }

            public void Flush(AnimationClip clip)
            {
                if (clip == null || !clip.legacy) return;
                foreach (KeyValuePair<string, List<float[]>> kv in keys)
                {
                    AnimationCurve curve = new AnimationCurve();
                    foreach (float[] k in kv.Value)
                        curve.AddKey(k[0], k[1]);
                    clip.SetCurve(kv.Key, typeof(Transform), "m_LocalPosition.y", curve);
                }
            }
        }
    }
}
