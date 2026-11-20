using UnityEngine;
using City.OSM;

namespace City.Player
{
    /// <summary>
    /// "Anti-T-pose" per i rig Mixamo (PlayerHero/Remy). Il modello Mixamo è
    /// esportato con la bind pose a T (braccia orizzontali): se l'Animator non
    /// riesce a valutare il rig (avatar Humanoid assente/non valido, controller
    /// non assegnato, clip mancanti) la SkinnedMeshRenderer mostra proprio la
    /// bind pose = T-pose. Questo componente ruota le spalle verso un'A-pose
    /// (braccia lungo i fianchi con leggera apertura) finché l'Animator non
    /// prende davvero il controllo; appena l'animazione comanda smette di
    /// toccare le ossa. Idempotente e innocuo: se le ossa non si trovano o il
    /// rig è già animato non fa nulla.
    /// </summary>
    public class ArmsPoseFallback : MonoBehaviour
    {
        [Tooltip("Apertura laterale dell'A-pose: 0 = braccia giù lungo i fianchi, 1 = braccia orizzontali (T).")]
        public float armOpen = 0.28f;

        [Tooltip("Velocità di transizione verso l'A-pose (0..1 per frame).")]
        public float lerpSpeed = 0.35f;

        private Animator animator;
        private Transform lShoulder;
        private Transform rShoulder;
        private Transform lArm;
        private Transform rArm;
        private Quaternion lRestRot = Quaternion.identity;
        private Quaternion rRestRot = Quaternion.identity;
        private Quaternion lDelta = Quaternion.identity;
        private Quaternion rDelta = Quaternion.identity;
        private bool ready;

        /// <summary>Attacca il fallback alla root del rig (se non già presente).</summary>
        public static void Ensure(GameObject root)
        {
            if (root == null) return;
            if (root.GetComponent<ArmsPoseFallback>() == null)
                root.AddComponent<ArmsPoseFallback>();
        }

        private void Start()
        {
            animator = GetComponentInChildren<Animator>(true);
            if (animator != null)
            {
                // Con avatar Humanoid i bone Transform sono raggiungibili anche
                // con "Optimize Game Objects" (GetBoneTransform li espone).
                lShoulder = animator.GetBoneTransform(HumanBodyBones.LeftShoulder);
                rShoulder = animator.GetBoneTransform(HumanBodyBones.RightShoulder);
                lArm = animator.GetBoneTransform(HumanBodyBones.LeftUpperArm);
                rArm = animator.GetBoneTransform(HumanBodyBones.RightUpperArm);
            }
            // Avatar non (ancora) Humanoid: cerca nella gerarchia per nome.
            if (lShoulder == null) lShoulder = FindBone(transform, "LeftShoulder");
            if (rShoulder == null) rShoulder = FindBone(transform, "RightShoulder");
            if (lArm == null && lShoulder != null) lArm = FindBone(lShoulder, "LeftArm");
            if (rArm == null && rShoulder != null) rArm = FindBone(rShoulder, "RightArm");

            if (lShoulder == null || rShoulder == null || lArm == null || rArm == null)
            {
                ready = false;
                OsmDiag.Log("[ArmsPoseFallback] ossa non trovate su " + gameObject.name +
                    ": fallback disattivato (rig animato o scheletro diverso).");
                return;
            }

            lRestRot = lShoulder.rotation;
            rRestRot = rShoulder.rotation;

            Vector3 lDir = lArm.position - lShoulder.position;
            Vector3 rDir = rArm.position - rShoulder.position;
            if (lDir.sqrMagnitude < 0.0001f || rDir.sqrMagnitude < 0.0001f)
            {
                ready = false;
                OsmDiag.Log("[ArmsPoseFallback] braccia collassate su " + gameObject.name +
                    ": fallback disattivato.");
                return;
            }

            lDelta = Quaternion.FromToRotation(lDir.normalized, FoldTarget(-transform.right));
            rDelta = Quaternion.FromToRotation(rDir.normalized, FoldTarget(transform.right));
            ready = true;
        }

        /// <summary>Direzione dell'A-pose: giù lungo il corpo con apertura laterale.</summary>
        private Vector3 FoldTarget(Vector3 lateral)
        {
            lateral.y = 0f;
            if (lateral.sqrMagnitude < 0.0001f) lateral = Vector3.right;
            lateral.Normalize();
            return (Vector3.down * (1f - armOpen) + lateral * armOpen).normalized;
        }

        /// <summary>True se l'Animator sta davvero muovendo il rig (niente fold).</summary>
        private bool AnimatorDrives()
        {
            if (animator == null || !animator.enabled) return false;
            if (animator.runtimeAnimatorController == null) return false;
            if (animator.avatar == null || !animator.avatar.isValid) return false;
            if (!animator.gameObject.activeInHierarchy) return false;
            // Rig Humanoid ottimizzato: le ossa non sono scrivibili, e se
            // l'animazione gira il fallback non serve. Con "Optimize Game
            // Objects" i bone Transform non hanno gerarchia e sono read-only.
            if (!animator.hasTransformHierarchy) return false;
            try
            {
                if (animator.GetCurrentAnimatorStateInfo(0).fullPathHash == 0) return false;
                return animator.GetCurrentAnimatorClipInfo(0).Length > 0;
            }
            catch (System.Exception) { return false; }
        }

        private void LateUpdate()
        {
            if (!ready) return;
            if (AnimatorDrives()) return;

            Quaternion lTarget = lDelta * lRestRot;
            Quaternion rTarget = rDelta * rRestRot;
            lShoulder.rotation = Quaternion.Slerp(lShoulder.rotation, lTarget, lerpSpeed);
            rShoulder.rotation = Quaternion.Slerp(rShoulder.rotation, rTarget, lerpSpeed);
        }

        private static Transform FindBone(Transform root, string boneName)
        {
            if (root == null) return null;
            if (root.name == boneName) return root;
            for (int i = 0; i < root.childCount; i++)
            {
                Transform hit = FindBone(root.GetChild(i), boneName);
                if (hit != null) return hit;
            }
            return null;
        }
    }
}