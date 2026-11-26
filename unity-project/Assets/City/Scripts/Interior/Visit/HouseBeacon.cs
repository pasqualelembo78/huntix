using UnityEngine;

namespace City.Interior.Visit
{
    /// <summary>
    /// Faro visivo delle case visitabili:alone + luce puntuale + fascio sottile
    /// sopra la porta, con pulsazione lenta come le uova (EggController). Si
    /// attacca SOLO alle case dichiarate in VisitInteriorRegistry (Roma e
    /// Foggia), non a tutti gli edifici: costo trascurabile e nessun collider.
    ///
    /// Nessun Material condiviso modificato a ogni frame: ogni faro ha il suo
    /// materiale, due case = due materiali. Nessun cambio di prefab o asset.
    /// </summary>
    public class HouseBeacon : MonoBehaviour
    {
        private const string RootName = "CasaBeacon";
        private static readonly Color BeaconColor =
            new Color(1f, 0.86f, 0.35f, 1f);
        private const float BaseIntensity = 1.9f;

        private Light _light;
        private Transform _orb;
        private float _phase;

        /// <summary>Crea il faro sotto un transform NON scalato (chunk root),
        /// nella posizione locale della porta. Idempotente.</summary>
        public static void Attach(Transform parent, Vector3 localDoor, string label)
        {
            if (parent == null) return;
            for (int i = 0; i < parent.childCount; i++)
            {
                if (parent.GetChild(i).name.StartsWith(RootName,
                        System.StringComparison.Ordinal)) return;
            }

            var go = new GameObject(RootName + "_" + (label ?? "casa"));
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localDoor + new Vector3(0f, 3.2f, 0f);
            var beacon = go.AddComponent<HouseBeacon>();
            beacon._phase = Mathf.Abs((label ?? "casa").GetHashCode()) * 0.013f;
            beacon.Build();
        }

        private void Build()
        {
            Material glow = MakeGlowMaterial();

            var orb = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            orb.name = "Alone";
            orb.transform.SetParent(transform, false);
            orb.transform.localPosition = Vector3.zero;
            orb.transform.localScale = Vector3.one * 1.35f;
            var orbR = orb.GetComponent<Renderer>();
            orbR.sharedMaterial = glow;
            Destroy(orb.GetComponent<Collider>());
            _orb = orb.transform;

            var beam = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            beam.name = "Fascio";
            beam.transform.SetParent(transform, false);
            beam.transform.localPosition = new Vector3(0f, -1.6f, 0f);
            beam.transform.localScale = new Vector3(0.22f, 1.6f, 0.22f);
            beam.GetComponent<Renderer>().sharedMaterial = glow;
            Destroy(beam.GetComponent<Collider>());

            var lightGo = new GameObject("LuceCasa");
            lightGo.transform.SetParent(transform, false);
            lightGo.transform.localPosition = Vector3.zero;
            _light = lightGo.AddComponent<Light>();
            _light.type = LightType.Point;
            _light.color = BeaconColor;
            _light.range = 12f;
            _light.intensity = BaseIntensity;
            _light.shadows = LightShadows.None;
        }

        private static Material MakeGlowMaterial()
        {
            Shader shader = Shader.Find("Universal Render Pipeline/Unlit");
            if (shader == null) shader = Shader.Find("Unlit/Color");
            if (shader == null) shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            Material m = shader != null ? new Material(shader) : null;
            if (m == null) return null;
            m.color = BeaconColor;
            m.EnableKeyword("_EMISSION");
            m.SetColor("_EmissionColor", BeaconColor * 0.8f);
            return m;
        }

        private void Update()
        {
            float pulse = (Mathf.Sin(Time.unscaledTime * 2f + _phase) + 1f) * 0.5f;
            if (_light != null) _light.intensity = BaseIntensity * (0.75f + pulse * 0.45f);
            if (_orb != null) _orb.localScale = Vector3.one * (1.2f + pulse * 0.3f);
        }
    }
}
