using UnityEngine;

namespace City.Environment
{
    /// <summary>
    /// Vetrina di un POI: il primo impatto la crepa, il secondo la rompe con
    /// frammenti per terra. Ogni colpo alimenta il sospetto (ChaosTracker).
    /// </summary>
    public class BreakableWindow : MonoBehaviour
    {
        public bool IsCracked { get; private set; }
        public bool IsBroken { get; private set; }

        private static readonly Color IntactTint = new Color(0.45f, 0.75f, 0.95f, 0.5f);
        private static readonly Color CrackedTint = new Color(0.3f, 0.4f, 0.5f, 0.8f);

        /// <summary>true se il colpo e' stato assorbito (crepata o rotta).</summary>
        public bool Hit(Vector3 pushDir)
        {
            if (IsBroken) return false;
            if (!IsCracked)
            {
                IsCracked = true;
                Tint(CrackedTint);
                Toast("\ud83d\udca5 La vetrina si e' crepata! Meglio non insistere...");
            }
            else
            {
                IsBroken = true;
                Shatter(pushDir);
                gameObject.SetActive(false);
                Toast("\ud83d\udca5 VETRINA DISTRUTTA! Non e' mai una buona idea...");
            }
            ChaosTracker.AddChaos(2);
            return true;
        }

        private void Start()
        {
            Tint(IntactTint);
        }

        private void Tint(Color c)
        {
            var r = GetComponent<Renderer>();
            if (r == null) return;
            var mats = r.sharedMaterials;
            for (int i = 0; i < mats.Length; i++)
            {
                Material m = new Material(Shader.Find("Sprites/Default"));
                m.color = c;
                mats[i] = m;
            }
            r.sharedMaterials = mats;
        }

        private void Shatter(Vector3 dir)
        {
            Transform root = transform.parent != null ? transform.parent : transform;
            for (int i = 0; i < 6; i++)
            {
                var shard = GameObject.CreatePrimitive(PrimitiveType.Cube);
                UnityEngine.Object.Destroy(shard.GetComponent<Collider>());
                shard.transform.SetParent(root, true);
                Vector2 r = UnityEngine.Random.insideUnitCircle * 0.9f;
                shard.transform.position = transform.position +
                    new Vector3(r.x, 0.06f, r.y) +
                    new Vector3(dir.x, 0f, dir.z).normalized * 0.5f;
                shard.transform.rotation = UnityEngine.Random.rotation;
                shard.transform.localScale = new Vector3(0.16f, 0.02f, 0.12f);
                TintShard(shard);
            }
        }

        private static void TintShard(GameObject go)
        {
            var r = go.GetComponent<Renderer>();
            if (r == null) return;
            Material m = new Material(Shader.Find("Sprites/Default"));
            m.color = new Color(0.6f, 0.85f, 1f, 1f);
            r.sharedMaterial = m;
        }

        private void Toast(string msg)
        {
            if (City.Game.Instance != null && City.Game.Instance.ui != null)
                City.Game.Instance.ui.ShowToast(msg);
        }
    }
}
