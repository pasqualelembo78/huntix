using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Vehicle
{
    public class VehicleSpawnManager : MonoBehaviour
    {
        public static VehicleSpawnManager Instance;

        /// <summary>
        /// Veicoli posseduti gia' materializzati in scena fuori dal flusso a
        /// chunk (es. auto consegnata dal carro attrezzi all'officina): il
        /// popolatore li salta per non creare duplicati. Le voci orfane
        /// (GameObject distrutto) si ripuliscono da sole.
        /// </summary>
        private static readonly System.Collections.Generic.Dictionary<string, GameObject>
            activeOwned = new System.Collections.Generic.Dictionary<string, GameObject>();

        public static void RegisterActiveOwned(string code, GameObject go)
        {
            if (string.IsNullOrEmpty(code) || go == null) return;
            activeOwned[code] = go;
        }

        public static bool IsActiveOwned(string code)
        {
            if (string.IsNullOrEmpty(code)) return false;
            if (!activeOwned.TryGetValue(code, out var go)) return false;
            if (go != null) return true;
            activeOwned.Remove(code);
            return false;
        }

        private const int MAX_VEHICLES_PER_ROAD = 3;
        private const float MIN_ROAD_LENGTH = 15f;
        private const float SIDE_OFFSET = 2.5f;
        private const float SPACING = 10f;
        private const float KENNEY_SCALE = 1f;

        private readonly List<GameObject> spawned = new List<GameObject>();
        private int nextVehicleId = 1;

        // Prefab Kenney caricati da Resources/Vehicles/
        private static GameObject prefabSedan;
        private static GameObject prefabSUV;
        private static GameObject prefabVan;
        private static GameObject prefabTaxi;
        private static GameObject prefabTruck;
        private static GameObject prefabDelivery;
        private static GameObject prefabRace;
        private static GameObject prefabHatchback;
        private static GameObject prefabPolice;
        private static GameObject prefabAmbulance;
        private static GameObject prefabFiretruck;
        private static GameObject prefabGarbage;

        // Catalogo veicoli: nome, prezzo, vel max (m/s), accel, turn, prefab key.
        // Riferimento personaggio: camminata 4 m/s, corsa 7.5 m/s. Alcuni
        // veicoli devono restare PIU' LENTI della persona (bici, spazzaneve),
        // altri molto piu' veloci (sports, moto). NB: la velocita' NON entra
        // nel codice veicolo, cambiarla non invalida i codici gia' venduti.
        public static readonly VehicleDef[] Catalogue = new VehicleDef[]
        {
            new VehicleDef("Fiat 500",      50,  12f, 8f,  110f, "sedan",      1.8f, 3.8f),
            new VehicleDef("SUV",           80,  14f, 6f,  90f,  "suv",        2.1f, 4.6f),
            new VehicleDef("Van",           70,  11f, 5f,  80f,  "van",        2.2f, 5.5f),
            new VehicleDef("Taxi",          60,  13f, 7f,  100f, "taxi",       1.8f, 4.0f),
            new VehicleDef("Truck",         100, 9f,  4f,  70f,  "truck",      2.5f, 6.5f),
            new VehicleDef("Furgone",       65,  10f, 5f,  75f,  "delivery",   2.0f, 5.0f),
            new VehicleDef("Sports",        120, 22f, 10f, 130f, "race",       1.9f, 4.2f),
            new VehicleDef("Hatchback",     55,  12f, 8f,  105f, "hatchback",  1.7f, 3.8f),
            new VehicleDef("Polizia",       90,  16f, 9f,  120f, "police",     1.9f, 4.2f),
            new VehicleDef("Ambulanza",     95,  13f, 6f,  85f,  "ambulance",  2.2f, 5.5f),
            new VehicleDef("Vigili",        110, 10f, 5f,  75f,  "firetruck",  2.5f, 7.0f),
            new VehicleDef("Spazzaneve",    85,  6f,  4f,  60f,  "garbage",    2.5f, 6.0f),
            new VehicleDef("Moto",          25,  20f, 12f, 150f, null,         0.8f, 2.0f),
            new VehicleDef("Scooter",       20,  7f,  10f, 130f, null,         0.7f, 1.7f),
            new VehicleDef("Bici Elettrica",15,  3.5f, 10f, 140f, null,        0.5f, 1.6f),
            new VehicleDef("Pullman",       180, 16f, 5f,  48f,  null,         2.6f, 8.6f),
        };

        public struct VehicleDef
        {
            public string name;
            public int price;
            public float maxSpeed, accel, turn;
            public string prefabKey;
            public float w, l;
            public VehicleDef(string n, int p, float s, float a, float t, string pk, float w, float l)
            {
                name = n; price = p; maxSpeed = s; accel = a; turn = t; prefabKey = pk; this.w = w; this.l = l;
            }
        }

        private void Awake()
        {
            Instance = this;
            LoadPrefabs();
        }

        private void LoadPrefabs()
        {
            prefabSedan     = Resources.Load<GameObject>("Vehicles/sedan");
            prefabSUV       = Resources.Load<GameObject>("Vehicles/suv");
            prefabVan       = Resources.Load<GameObject>("Vehicles/van");
            prefabTaxi      = Resources.Load<GameObject>("Vehicles/taxi");
            prefabTruck     = Resources.Load<GameObject>("Vehicles/truck");
            prefabDelivery  = Resources.Load<GameObject>("Vehicles/delivery");
            prefabRace      = Resources.Load<GameObject>("Vehicles/race");
            prefabHatchback = Resources.Load<GameObject>("Vehicles/hatchback-sports");
            prefabPolice    = Resources.Load<GameObject>("Vehicles/police");
            prefabAmbulance = Resources.Load<GameObject>("Vehicles/ambulance");
            prefabFiretruck = Resources.Load<GameObject>("Vehicles/firetruck");
            prefabGarbage   = Resources.Load<GameObject>("Vehicles/garbage-truck");

            int loaded = 0;
            if (prefabSedan != null) loaded++;
            if (prefabSUV != null) loaded++;
            if (prefabVan != null) loaded++;
            if (prefabTaxi != null) loaded++;
            if (prefabTruck != null) loaded++;
            if (prefabDelivery != null) loaded++;
            if (prefabRace != null) loaded++;
            if (prefabHatchback != null) loaded++;
            if (prefabPolice != null) loaded++;
            if (prefabAmbulance != null) loaded++;
            if (prefabFiretruck != null) loaded++;
            Debug.Log("[VehicleSpawnManager] Prefab Kenney caricati: " + loaded + "/11");
        }

        private static GameObject GetPrefab(string key)
        {
            if (string.IsNullOrEmpty(key)) return null;
            switch (key)
            {
                case "sedan":      return prefabSedan;
                case "suv":        return prefabSUV;
                case "van":        return prefabVan;
                case "taxi":       return prefabTaxi;
                case "truck":      return prefabTruck;
                case "delivery":   return prefabDelivery;
                case "race":       return prefabRace;
                case "hatchback":  return prefabHatchback;
                case "police":     return prefabPolice;
                case "ambulance":  return prefabAmbulance;
                case "firetruck":  return prefabFiretruck;
                case "garbage":    return prefabGarbage;
                default:           return null;
            }
        }

        /// <summary>Cerca una definizione nel catalogo per nome esatto
        /// (usato da GarageUI per rispawnare l'auto uscita dal garage).</summary>
        public static bool TryGetDef(string name, out VehicleDef def)
        {
            def = default(VehicleDef);
            if (string.IsNullOrEmpty(name)) return false;
            foreach (var d in Catalogue)
            {
                if (d.name == name) { def = d; return true; }
            }
            return false;
        }

        /// <summary>True se l'elemento del catalogo all'indice dato e' il
        /// pullman (non parcheggiabile sul ciglio delle strade).</summary>
        public static bool IsStreetBus(int index)
        {
            if (index < 0 || index >= Catalogue.Length) return false;
            return Catalogue[index].name == "Pullman";
        }

        public void SpawnParkedVehicles(Transform root, OsmCityEnvelope env)
        {
            if (env.roads == null) return;
            var rng = new System.Random(42);

            foreach (var road in env.roads)
            {
                if (road.points == null || road.points.Length < 2) continue;
                string hw = road.highway ?? "";
                if (hw == "footway" || hw == "path" || hw == "cycleway" || hw == "steps") continue;

                float roadLen = CalcRoadLength(road);
                if (roadLen < MIN_ROAD_LENGTH) continue;

                for (int s = 0; s < road.points.Length - 1 && spawned.Count < 150; s++)
                {
                    Vector3 a = Local(road.points[s]);
                    Vector3 b = Local(road.points[s + 1]);
                    float segLen = (b - a).magnitude;
                    if (segLen < 2f) continue;

                    Vector3 dir = (b - a).normalized;
                    Vector3 right = Vector3.Cross(Vector3.up, dir).normalized;

                    int segCount = Mathf.Max(1, Mathf.FloorToInt(segLen / SPACING));
                    for (int k = 0; k < segCount && spawned.Count < 150; k++)
                    {
                        if (rng.NextDouble() > 0.4) continue;

                        float t = (k + 0.5f) / segCount;
                        Vector3 pos = a + (b - a) * t;
                        float side = (rng.Next(2) == 0) ? 1f : -1f;
                        pos += right * SIDE_OFFSET * side;

                        float angle = Mathf.Atan2(dir.z, dir.x) * Mathf.Rad2Deg;
                        if (side < 0) angle += 180f;

                        int idx = rng.Next(Catalogue.Length);
                        var def = Catalogue[idx];
                        SpawnOne(root, def, pos, angle, road.name, rng);
                    }
                }
            }

            Debug.Log("[VehicleSpawnManager] Veicoli parcheggiati: " + spawned.Count);
        }

        private void SpawnOne(Transform root, VehicleDef def, Vector3 pos, float angle, string streetName, System.Random rng)
        {
            int vehicleId = nextVehicleId++;
            var go = BuildVehicle(root, def, pos, angle, "V" + vehicleId);
            spawned.Add(go);
        }

        /// <summary>
        /// Crea un veicolo completo: modello Kenney (o fallback procedurale),
        /// BoxCollider solido, VehicleController e trigger di interazione per
        /// compra/entra. Usato dal legacy E dal popolatore a chunk: il codice
        /// veicolo passato qui deve essere DETERMINISTICO per avere la stessa
        /// identita' del veicolo su tutti i client.
        /// </summary>
        public static GameObject BuildVehicle(Transform parent, VehicleDef def,
            Vector3 pos, float angle, string code)
        {
            GameObject go;
            GameObject prefab = GetPrefab(def.prefabKey);

            if (prefab != null)
            {
                // Kenney prefab importato da FBX
                go = Instantiate(prefab, parent);
                go.name = code + "_" + def.name;
                go.transform.localPosition = pos;
                go.transform.localRotation = Quaternion.Euler(0f, angle, 0f);
                go.transform.localScale = Vector3.one * KENNEY_SCALE;

                // Disabilita tutti i collider figli (il prefab puo' averne):
                // il collider solido e' solo il nostro box, uniforme
                foreach (var col in go.GetComponentsInChildren<Collider>())
                    col.enabled = false;
            }
            else
            {
                // Fallback procedurale per moto/scooter/bici
                go = BuildProcedural(def, 0);
                go.transform.SetParent(parent, false);
                go.transform.localPosition = pos;
                go.transform.localRotation = Quaternion.Euler(0f, angle, 0f);
            }

            go.SetActive(true);

            // Collider solido per il veicolo
            float w = def.w;
            float l = def.l;
            var boxCol = go.AddComponent<BoxCollider>();
            boxCol.size = new Vector3(w, 1.2f, l);
            boxCol.center = new Vector3(0f, 0.6f, 0f);

            // Ruote anime + Controller (l'ordine conta: il VehicleController
            // legge il WheelSpinner nella sua Awake)
            go.AddComponent<WheelSpinner>();
            var vc = go.AddComponent<VehicleController>();
            vc.data = CreateVehicleData(def);

            // Trigger interazione (avvolge il veicolo)
            var triggerGo = new GameObject("Trigger");
            triggerGo.transform.SetParent(go.transform, false);
            triggerGo.transform.localPosition = new Vector3(0f, 0.8f, 0f);
            var triggerCol = triggerGo.AddComponent<BoxCollider>();
            triggerCol.isTrigger = true;
            triggerCol.size = new Vector3(w + 2f, 2.5f, l + 2f);
            var vi = triggerGo.AddComponent<VehicleInteract>();
            vi.controller = vc;
            vi.data = vc.data;
            vi.vehicleCode = code;

            return go;
        }

        // ── Fallback procedurale per veicoli senza prefab Kenney ──

        private static GameObject BuildProcedural(VehicleDef def, int colorSeed)
        {
            if (def.name == "Pullman")
                return BuildBusProcedural();

            var go = new GameObject("Parked_" + def.name);

            Color[] colors = new Color[]
            {
                new Color(0.8f, 0.15f, 0.15f), new Color(0.15f, 0.15f, 0.15f),
                new Color(0.9f, 0.9f, 0.9f), new Color(0.2f, 0.2f, 0.7f),
                new Color(0.1f, 0.6f, 0.2f), new Color(0.9f, 0.6f, 0.1f),
            };
            Color c = colors[colorSeed % colors.Length];

            // Corpo
            var body = GameObject.CreatePrimitive(PrimitiveType.Cube);
            body.name = "Body";
            body.transform.SetParent(go.transform, false);
            body.transform.localPosition = new Vector3(0f, def.w * 0.4f + 0.15f, 0f);
            body.transform.localScale = new Vector3(def.w, def.w * 0.6f, def.l);
            body.GetComponent<Renderer>().sharedMaterial = MakeMat(c);

            // Ruote
            Color wc = new Color(0.12f, 0.12f, 0.12f);
            float wr = 0.15f;
            PlaceWheel(go.transform, new Vector3(-def.w * 0.5f - 0.05f, wr + 0.02f, def.l * 0.25f), wr, wc);
            PlaceWheel(go.transform, new Vector3(def.w * 0.5f + 0.05f, wr + 0.02f, def.l * 0.25f), wr, wc);
            PlaceWheel(go.transform, new Vector3(-def.w * 0.5f - 0.05f, wr + 0.02f, -def.l * 0.25f), wr, wc);
            PlaceWheel(go.transform, new Vector3(def.w * 0.5f + 0.05f, wr + 0.02f, -def.l * 0.25f), wr, wc);

            return go;
        }

        private static void PlaceWheel(Transform parent, Vector3 pos, float r, Color c)
        {
            var w = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            w.name = "Wheel";
            w.transform.SetParent(parent, false);
            w.transform.localPosition = pos;
            w.transform.localScale = new Vector3(r * 2f, 0.06f, r * 2f);
            w.transform.localRotation = Quaternion.Euler(0f, 0f, 90f);
            w.GetComponent<Renderer>().sharedMaterial = MakeMat(c);
            var cld = w.GetComponent<Collider>();
            if (cld != null) cld.enabled = false;
        }

        // Pullman guidabile: carrozzeria + cabina finestrata + luci + 2 assi.
        // Il collider fisico lo aggiunge BuildVehicle (box sulle dimensioni def).
        private static GameObject BuildBusProcedural()
        {
            var go = new GameObject("Pullman");
            var blue = MakeMat(new Color(0.05f, 0.45f, 0.72f));
            var glass = MakeMat(new Color(0.4f, 0.62f, 0.85f));

            var body = GameObject.CreatePrimitive(PrimitiveType.Cube);
            body.name = "Body";
            body.transform.SetParent(go.transform, false);
            body.transform.localPosition = new Vector3(0f, 1.6f, 0f);
            body.transform.localScale = new Vector3(2.6f, 2.0f, 8.6f);
            body.GetComponent<Renderer>().sharedMaterial = blue;
            Object.Destroy(body.GetComponent<Collider>());

            var cabin = GameObject.CreatePrimitive(PrimitiveType.Cube);
            cabin.name = "Cabin";
            cabin.transform.SetParent(go.transform, false);
            cabin.transform.localPosition = new Vector3(0f, 2.7f, 0.4f);
            cabin.transform.localScale = new Vector3(2.34f, 0.5f, 6.6f);
            cabin.GetComponent<Renderer>().sharedMaterial = glass;
            Object.Destroy(cabin.GetComponent<Collider>());

            var dark = MakeMat(new Color(0.12f, 0.12f, 0.13f));
            var light = MakeMat(new Color(0.98f, 0.9f, 0.6f));
            var tail = MakeMat(new Color(0.8f, 0.12f, 0.12f));
            var head = GameObject.CreatePrimitive(PrimitiveType.Cube);
            head.name = "Headlight";
            head.transform.SetParent(go.transform, false);
            head.transform.localPosition = new Vector3(0f, 2.4f, 4.25f);
            head.transform.localScale = new Vector3(1.8f, 0.15f, 0.06f);
            head.GetComponent<Renderer>().sharedMaterial = light;
            Object.Destroy(head.GetComponent<Collider>());
            var tailObj = GameObject.CreatePrimitive(PrimitiveType.Cube);
            tailObj.name = "Taillight";
            tailObj.transform.SetParent(go.transform, false);
            tailObj.transform.localPosition = new Vector3(0f, 2.4f, -4.25f);
            tailObj.transform.localScale = new Vector3(1.8f, 0.15f, 0.06f);
            tailObj.GetComponent<Renderer>().sharedMaterial = tail;
            Object.Destroy(tailObj.GetComponent<Collider>());

            // ruote (4, cerchioni scuri) su 2 assi
            float[] zs = { 2.6f, -2.6f };
            foreach (float x in new float[] { -1.25f, 1.25f })
                foreach (float z in zs)
                {
                    var wObj = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                    wObj.name = "Wheel";
                    wObj.transform.SetParent(go.transform, false);
                    wObj.transform.localPosition = new Vector3(x, 0.36f, z);
                    wObj.transform.localRotation = Quaternion.Euler(0f, 0f, 90f);
                    wObj.transform.localScale = new Vector3(0.66f, 0.3f, 0.66f);
                    wObj.GetComponent<Renderer>().sharedMaterial = dark;
                    Object.Destroy(wObj.GetComponent<Collider>());
                }

            return go;
        }

        // ── Helpers ────────────────────────────────────────────────

        private static VehicleData CreateVehicleData(VehicleDef def)
        {
            var vd = ScriptableObject.CreateInstance<VehicleData>();
            vd.vehicleName = def.name;
            vd.price = def.price;
            vd.maxSpeed = def.maxSpeed;
            vd.acceleration = def.accel;
            vd.turnSpeed = def.turn;
            vd.brakeForce = def.accel * 2f;
            vd.drag = 0.8f;
            vd.bodyWidth = def.w;
            vd.bodyLength = def.l;
            vd.category = def.l < 2.5f ? VehicleCategory.Motorcycle : VehicleCategory.Car;
            return vd;
        }

        private static readonly Dictionary<Color, Material> matCache = new Dictionary<Color, Material>();
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

        private static Vector3 Local(GeoPoint p)
        {
            return new Vector3(CoordinateConverter.LonToX(p.lng), 0f, CoordinateConverter.LatToZ(p.lat));
        }

        private static float CalcRoadLength(OsmRoad road)
        {
            float len = 0f;
            for (int i = 0; i < road.points.Length - 1; i++)
            {
                Vector3 a = Local(road.points[i]);
                Vector3 b = Local(road.points[i + 1]);
                len += (b - a).magnitude;
            }
            return len;
        }

        public void DespawnAll()
        {
            foreach (var go in spawned)
                if (go != null) Destroy(go);
            spawned.Clear();
        }
    }
}
