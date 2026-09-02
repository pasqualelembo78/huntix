using UnityEngine;
using UnityEngine.UI;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Segnali lungo le strade principali del chunk: palino con pannello che
    /// indica il POI piu' vicino di una categoria (assegnata a rotazione).
    /// Testo aggiornato poche volte dopo la creazione, come i cartelli POI.
    /// </summary>
    public static class RoadSignSpawner
    {
        private const float SampleEveryMeters = 140f;
        private const int MaxPerChunk = 4;
        private static readonly string[] SignKinds =
            { "dealer", "repair", "garage", "hospital" };

        public static void Populate(ChunkData chunk,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds)
        {
            if (chunk?.geo?.roads == null || chunk.root == null) return;

            var rng = new System.Random(
                (chunk.key ?? "c").GetHashCode());
            int placed = 0;
            int kindIdx = Mathf.Abs(rng.Next()) % SignKinds.Length;

            foreach (var r in chunk.geo.roads)
            {
                if (placed >= MaxPerChunk) break;
                if (r?.pts == null || r.pts.Length < 2) continue;
                bool major = r.hw == "primary" || r.hw == "secondary" ||
                    r.hw == "tertiary";
                if (!major) continue;

                Vector3 prev = toLocal(r.pts[0]);
                float carry = SampleEveryMeters * 0.5f;
                for (int i = 1; i < r.pts.Length && placed < MaxPerChunk;
                    i++)
                {
                    Vector3 cur = toLocal(r.pts[i]);
                    float seg = Vector3.Distance(prev, cur);
                    while (carry <= seg && placed < MaxPerChunk)
                    {
                        Vector3 pos = prev +
                            (cur - prev).normalized * carry;
                        Build(chunk.root.transform, pos,
                            SignKinds[kindIdx % SignKinds.Length],
                            r.hw);
                        kindIdx++;
                        placed++;
                        carry += SampleEveryMeters;
                    }
                    carry -= seg;
                    prev = cur;
                }
            }
        }

        private static void Build(Transform parent, Vector3 local,
            string kindStr, string roadClass)
        {
            bool big = roadClass == "primary";
            var go = new GameObject("Segnale_" + kindStr);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = new Vector3(local.x, 0f, local.z);

            var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            Object.Destroy(pole.GetComponent<Collider>());
            pole.transform.SetParent(go.transform, false);
            pole.transform.localScale = new Vector3(0.09f,
                big ? 1.5f : 1.2f, 0.09f);
            pole.transform.localPosition = new Vector3(0f,
                big ? 1.5f : 1.2f, 0f);

            var board = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(board.GetComponent<Collider>());
            board.transform.SetParent(go.transform, false);
            board.transform.localScale = new Vector3(1.7f, 0.85f, 0.08f);
            board.transform.localPosition = new Vector3(0f,
                big ? 3.15f : 2.55f, 0f);

            var mr = board.GetComponent<MeshRenderer>();
            mr.sharedMaterial = new Material(
                Shader.Find("Universal Render Pipeline/Lit")
                    ?? Shader.Find("Standard"));
            mr.material.color = new Color(0.94f, 0.95f, 0.96f);

            var sign = board.AddComponent<RoadSign>();
            sign.Setup(kindStr);
        }
    }

    /// <summary>Pannello del segnale: testo world-space con distanza.</summary>
    public class RoadSign : MonoBehaviour
    {
        private string kindStr;
        private Text label;
        private readonly float[] refreshAt = { 5f, 20f, 60f };
        private int nextIdx;
        private float born;

        public void Setup(string kind)
        {
            kindStr = kind;
            born = Time.unscaledTime;
        }

        private void Start()
        {
            BuildCanvas();
            UpdateText();
        }

        private void Update()
        {
            if (nextIdx >= refreshAt.Length)
            {
                enabled = false;
                return;
            }
            if (Time.unscaledTime - born >= refreshAt[nextIdx])
            {
                nextIdx++;
                UpdateText();
            }
        }

        private void BuildCanvas()
        {
            var go = new GameObject("Testo", typeof(Canvas));
            go.transform.SetParent(transform, false);
            var cv = go.GetComponent<Canvas>();
            cv.renderMode = RenderMode.WorldSpace;

            var rt = go.GetComponent<RectTransform>();
            rt.sizeDelta = new Vector2(1.6f, 0.8f);
            rt.localPosition = new Vector3(0f, 0f, -0.06f);

            var lgo = new GameObject("T", typeof(Text));
            lgo.transform.SetParent(go.transform, false);
            label = lgo.GetComponent<Text>();
            label.font = CompassUI.UiFont();
            label.fontSize = 40;
            label.alignment = TextAnchor.MiddleCenter;
            label.color = new Color(0.05f, 0.30f, 0.10f);
            label.text = "";
            var lrt = label.rectTransform;
            lrt.anchorMin = Vector2.zero;
            lrt.anchorMax = Vector2.one;
            lrt.sizeDelta = Vector2.zero;
        }

        private void UpdateText()
        {
            if (label == null || string.IsNullOrEmpty(kindStr)) return;
            GeoCoord g = WorldOrigin.ToGeo(transform.position);
            var p = VehiclePoiRegistry.Nearest(kindStr, g.lat, g.lng);
            if (p == null) { label.text = ""; return; }
            Vector3 w = WorldOrigin.ToWorld(p.lat, p.lng);
            float d = Vector3.Distance(w, transform.position);
            label.text = PoiSignpost.Caption(kindStr) + "\n" +
                CompassUI.FormatDist(d);
        }
    }
}
