using System.Collections.Generic;
using UnityEngine;

namespace City.Player
{
    /// <summary>
    /// Misura la geometria VERA del modello (vertici skinnati), non i bounds
    /// degli SkinnedMeshRenderer. Sul prefab Remy i bounds della scarpa scendono
    /// 0,28 unita' modello SOTTO la suola reale: per questo [SoleCheck] diceva
    /// "delta=0" mentre il personaggio galleggiava.
    ///
    /// I due valori calcolati qui sono geometrici, non costanti numeriche:
    ///   SoleBelowAnkle = distanza verticale caviglia -> suola (spessore scarpa)
    ///   HipsToSole     = distanza verticale bacino -> suola a gamba distesa
    /// Vengono misurati una volta sull'hero appena instanziato (prima che
    /// l'Animator modifichi la posa) e riusati da walker, IK e diagnostica.
    /// </summary>
    public class SoleGeometry : MonoBehaviour
    {
        public float SoleBelowAnkle { get; private set; } = float.NaN;
        public float HipsToSole { get; private set; } = float.NaN;

        private Animator _anim;
        private Transform _rigRoot;

        /// <summary>Installa la sonda sull'hero rig. Idempotente.</summary>
        public static SoleGeometry Ensure(GameObject hero)
        {
            if (hero == null) return null;
            var probe = hero.GetComponent<SoleGeometry>();
            if (probe == null) probe = hero.AddComponent<SoleGeometry>();
            probe.Capture();
            return probe;
        }

        private void Awake()
        {
            _rigRoot = transform;
            _anim = GetComponentInChildren<Animator>(true);
        }

        /// <summary>Cattura la misura della posa corrente (da chiamare subito
        /// dopo l'instantiate, con l'hero ancora in bind pose).</summary>
        public void Capture()
        {
            if (_rigRoot == null) _rigRoot = transform;
            if (_anim == null) _anim = GetComponentInChildren<Animator>(true);

            float sole = float.MaxValue;
            if (!SoleY(_rigRoot, out sole)) return;

            Transform lf = _anim != null
                ? _anim.GetBoneTransform(HumanBodyBones.LeftFoot) : null;
            Transform rf = _anim != null
                ? _anim.GetBoneTransform(HumanBodyBones.RightFoot) : null;
            Transform hips = _anim != null
                ? _anim.GetBoneTransform(HumanBodyBones.Hips) : null;

            float ankle = float.MaxValue;
            if (lf != null) ankle = Mathf.Min(ankle, lf.position.y);
            if (rf != null) ankle = Mathf.Min(ankle, rf.position.y);

            if (ankle < float.MaxValue)
                SoleBelowAnkle = Mathf.Max(0f, ankle - sole);
            if (hips != null)
                HipsToSole = Mathf.Max(0f, hips.position.y - sole);

            City.OSM.OsmDiag.Log("[SoleGeometry] suola=" + sole.ToString("F3") +
                " caviglia=" + (ankle < float.MaxValue
                    ? ankle.ToString("F3") : "NA") +
                " spessoreScarpa=" + F(SoleBelowAnkle) +
                " bacinoSuola=" + F(HipsToSole));
        }

        /// <summary>Suola vera = vertice skinnato piu' basso fra tutti gli
        /// SkinnedMeshRenderer attivi dell'hero. Nessun bounds, nessuna
        /// approssimazione. Usata dalla diagnostica (throttled).</summary>
        public static bool SoleY(Transform root, out float minY)
        {
            minY = float.MaxValue;
            if (root == null) return false;
            var smrs = root.GetComponentsInChildren<SkinnedMeshRenderer>(true);
            bool any = false;
            for (int i = 0; i < smrs.Length; i++)
            {
                var smr = smrs[i];
                if (smr == null || !smr.gameObject.activeInHierarchy) continue;
                var mesh = smr.sharedMesh;
                if (mesh == null) continue;
                var verts = mesh.vertices;
                var weights = mesh.boneWeights;
                var bones = smr.bones;
                var bind = mesh.bindposes;
                for (int v = 0; v < verts.Length; v++)
                {
                    float y = SkinnedY(verts[v], weights[v], bones, bind);
                    if (y < minY) minY = y;
                    any = true;
                }
            }
            return any;
        }

        private static float SkinnedY(Vector3 vertex, BoneWeight w,
            Transform[] bones, Matrix4x4[] bind)
        {
            Vector3 p = Vector3.zero;
            float sum = 0f;
            Add(ref p, ref sum, vertex, w.weight0, w.boneIndex0, bones, bind);
            Add(ref p, ref sum, vertex, w.weight1, w.boneIndex1, bones, bind);
            Add(ref p, ref sum, vertex, w.weight2, w.boneIndex2, bones, bind);
            Add(ref p, ref sum, vertex, w.weight3, w.boneIndex3, bones, bind);
            return sum > 0.0001f ? p.y / sum : vertex.y;
        }

        private static void Add(ref Vector3 p, ref float sum, Vector3 vertex,
            float weight, int index, Transform[] bones, Matrix4x4[] bind)
        {
            if (weight <= 0.0001f || index < 0 || index >= bones.Length ||
                index >= bind.Length || bones[index] == null) return;
            p += bones[index].localToWorldMatrix.MultiplyPoint3x4(
                bind[index].MultiplyPoint3x4(vertex)) * weight;
            sum += weight;
        }

        private static string F(float v)
        {
            return float.IsNaN(v) ? "NA"
                : v.ToString("F3", System.Globalization.CultureInfo.InvariantCulture);
        }
    }
}
