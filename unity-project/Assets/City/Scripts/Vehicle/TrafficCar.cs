using UnityEngine;

namespace City.Vehicle
{
    public class TrafficCar : MonoBehaviour
    {
        public float speed = 6f;
        private Vector3[] path;
        private int currentIdx;

        private static readonly string[] CarPrefabs = new string[]
        {
            "Vehicles/sedan", "Vehicles/suv", "Vehicles/van",
            "Vehicles/taxi", "Vehicles/hatchback-sports", "Vehicles/delivery",
        };

        private static GameObject[] loadedPrefabs;
        private static bool prefabsLoaded;

        private static void EnsurePrefabs()
        {
            if (prefabsLoaded) return;
            loadedPrefabs = new GameObject[CarPrefabs.Length];
            for (int i = 0; i < CarPrefabs.Length; i++)
                loadedPrefabs[i] = Resources.Load<GameObject>(CarPrefabs[i]);
            prefabsLoaded = true;
        }

        public void Init(Vector3[] roadPath, float spd, int colorSeed)
        {
            path = roadPath;
            speed = spd;
            currentIdx = 0;

            if (path.Length > 0)
            {
                // path e' in coordinate LOCALI del chunk root
                transform.localPosition = path[0];
                if (path.Length > 1)
                {
                    Vector3 dir = (path[1] - path[0]).normalized;
                    transform.rotation = Quaternion.LookRotation(dir, Vector3.up);
                }
            }

            BuildCarModel(colorSeed);
        }

        private void Update()
        {
            if (path == null || path.Length < 2) return;

            // movimento in spazio LOCALE del chunk root (il genitore)
            Vector3 target = path[currentIdx];
            Vector3 dir = target - transform.localPosition;
            dir.y = 0f;
            float dist = dir.magnitude;

            if (dist < 0.5f)
            {
                currentIdx++;
                if (currentIdx >= path.Length)
                    currentIdx = 0;
                return;
            }

            Vector3 move = dir.normalized * speed * Time.deltaTime;
            transform.localPosition += move;

            Quaternion look = Quaternion.LookRotation(dir.normalized, Vector3.up);
            transform.localRotation =
                Quaternion.Slerp(transform.localRotation, look, 5f * Time.deltaTime);
        }

        private void BuildCarModel(int seed)
        {
            EnsurePrefabs();

            // modulo sicuro anche per seed negativi (difensivo: il caller
            // normalizza gia' con & 0x7FFFFFFF)
            int len = loadedPrefabs.Length;
            int idx = ((seed % len) + len) % len;
            GameObject prefab = loadedPrefabs[idx];

            if (prefab != null)
            {
                var inst = Instantiate(prefab, transform);
                inst.transform.localPosition = Vector3.zero;
                inst.transform.localRotation = Quaternion.identity;
                inst.transform.localScale = Vector3.one;

                // Disabilita collider nei figli
                foreach (var col in inst.GetComponentsInChildren<Collider>())
                    col.enabled = false;
            }
            else
            {
                // Fallback box
                BuildFallback(seed);
            }

            // Collider trigger sul veicolo AI
            var box = gameObject.AddComponent<BoxCollider>();
            box.isTrigger = true;
            box.size = new Vector3(2f, 1.3f, 4f);
            box.center = new Vector3(0f, 0.65f, 0f);
        }

        private void BuildFallback(int seed)
        {
            Color[] colors = new Color[]
            {
                new Color(0.9f, 0.9f, 0.9f), new Color(0.2f, 0.2f, 0.2f),
                new Color(0.7f, 0.1f, 0.1f), new Color(0.1f, 0.3f, 0.7f),
                new Color(0.9f, 0.8f, 0.1f), new Color(0.4f, 0.4f, 0.4f),
            };
            Color c = colors[((seed % colors.Length) + colors.Length) % colors.Length];
            float w = 1.6f, h = 1.0f, l = 3.5f;

            var body = GameObject.CreatePrimitive(PrimitiveType.Cube);
            body.name = "TBody";
            body.transform.SetParent(transform, false);
            body.transform.localPosition = new Vector3(0f, h * 0.5f + 0.2f, 0f);
            body.transform.localScale = new Vector3(w, h, l);
            body.GetComponent<Renderer>().sharedMaterial = MakeMat(c);

            var roof = GameObject.CreatePrimitive(PrimitiveType.Cube);
            roof.name = "TRoof";
            roof.transform.SetParent(transform, false);
            roof.transform.localPosition = new Vector3(0f, h + 0.45f, -l * 0.03f);
            roof.transform.localScale = new Vector3(w * 0.85f, 0.4f, l * 0.45f);
            roof.GetComponent<Renderer>().sharedMaterial = MakeMat(new Color(0.5f, 0.7f, 0.9f, 0.6f));
        }

        private static readonly System.Collections.Generic.Dictionary<Color, Material> matCache
            = new System.Collections.Generic.Dictionary<Color, Material>();

        private static Material MakeMat(Color c)
        {
            if (matCache.TryGetValue(c, out var m)) return m;
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            m = new Material(shader);
            if (shader.name.StartsWith("Universal Render Pipeline/Lit"))
                m.SetColor("_BaseColor", c);
            else
                m.SetColor("_Color", c);
            matCache[c] = m;
            return m;
        }
    }
}
