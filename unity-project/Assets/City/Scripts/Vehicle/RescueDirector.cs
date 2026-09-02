using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using City.OSM;
using City.UI;
using City.World;

namespace City.Vehicle
{
    /// <summary>
    /// Carro attrezzi + vigili del fuoco. Le offerte compaiono quando l'auto
    /// e' incidentata/in fiamme oppure quando il giocatore, con la gomma a
    /// terra, preferisce non zoppicare fino in officina.
    ///
    /// Sequenza del carro attrezzi, tutta visibile e seguibile:
    ///   1) dopo qualche secondo il truck (FBX Kenney reale) appare davanti
    ///      e ARRIVA IN RETROMARCIA con il lampeggiante;
    ///   2) il gancio si aggancia al muso dell'auto e un cavo la collega;
    ///   3) il truck PARTE DAVVERO e guida fino all'officina: il giocatore
    ///      puo' salire su un'altra macchina e seguirlo con l'auto a rimorchio.
    ///   Se l'officina e' oltre MaxFollowDistance il truck percorre comunque
    ///   alcuni metri e poi completa il trasferimento sotto fade.
    /// Costo a carico del wallet locale (15 euro).
    /// </summary>
    public class RescueDirector : MonoBehaviour
    {
        public static RescueDirector Instance { get; private set; }

        public const int TowCost = 15;
        public const int FireCost = 15;

        /// <summary>Massima distanza (metri) in cui il truck percorre TUTTA
        /// la strada fino all'officina con il giocatore che puo' seguire.
        /// Oltre va solo a fare il trasferimento (normale, se l'officina e'
        /// dall'altra parte della citta' non puo' guidare metri reali).</summary>
        public const float MaxFollowDistance = 550f;

        private bool busy;
        private GameObject holder;
        private readonly List<GameObject> rescued =
            new List<GameObject>();

        /// <summary>Crea l'oggetto di servizio (sopravvive ai change di scena).</summary>
        public static RescueDirector Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("RescueDirector");
            DontDestroyOnLoad(go);
            return go.AddComponent<RescueDirector>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            holder = new GameObject("RescuedVehicles");
            DontDestroyOnLoad(holder);
        }

        // ── offerte ───────────────────────────────────────────────

        public void OfferTow(VehicleController vc)
        {
            if (vc == null) return;
            string reason = vc.Damage == VehicleDamage.Wrecked
                ? "L'auto e' incidentata: il motore e' incrinato e non parte. Il carro attrezzi arriva, aggancia l'auto e la consegna all'officina piu' vicina (costo " + TowCost + "€)."
                : vc.Damage == VehicleDamage.Flat
                ? "Gomma a terra: puoi zoppicare fino all'officina, oppure il carro attrezzi viene a prendere l'auto e la porta dritta in officina (costo " + TowCost + "€)."
                : "L'auto non parte: il carro attrezzi arriva, aggancia e porta l'auto all'officina piu' vicina (costo " + TowCost + "€).";
            OfferDialog.Offer("CARRO ATTREZZI", reason, () => StartTow(vc));
        }

        public void OfferFlatChoice(VehicleController vc,
            System.Action enterAnyway)
        {
            if (vc == null) return;
            OfferDialog.Offer("GOMMA A TERRA",
                "Hai bucato una gomma: l'auto zoppica e va piano. Puoi chiamare il carro attrezzi che arriva, aggancia e porta l'auto in officina subito (costo " + TowCost + "€), oppure guidare fino li' e risparmiare.",
                () => StartTow(vc),
                enterAnyway);
        }

        public void OfferFireThenTow(VehicleController vc)
        {
            if (vc == null) return;
            OfferDialog.Offer("VIGILI DEL FUOCO",
                "L'auto e' in fiamme! Prima i pompieri arrivano e la spengono (costo " + FireCost + "€), poi il carro attrezzi la porta in officina.",
                () => StartFire(vc));
        }

        public static void SetLocalDamage(string code, VehicleDamage damage)
        {
            if (Instance == null || string.IsNullOrEmpty(code)) return;
            foreach (var go in Instance.rescued)
                if (go != null && go.name == code)
                {
                    var vc = go.GetComponent<VehicleController>();
                    if (vc != null) vc.SetDamage(damage);
                    return;
                }
        }

        // ── avvio operazioni ──────────────────────────────────────

        private void StartFire(VehicleController vc)
        {
            if (vc == null || busy) return;
            if (!Wallet.CanAfford(FireCost))
            {
                Toast("Servizio pompieri non disponibile: servono " + FireCost + "€");
                return;
            }
            busy = true;
            StartCoroutine(FireRoutine(vc));
        }

        private void StartTow(VehicleController vc)
        {
            if (vc == null || busy) return;
            var api = VehicleOwnershipApi.Ensure();
            var vi = vc.GetComponentInChildren<VehicleInteract>();
            string code = vi != null ? vi.vehicleCode : "";
            if (string.IsNullOrEmpty(code) || api == null)
            {
                Toast("Soccorso non disponibile: veicolo non riconosciuto.");
                return;
            }
            var off = NearestRepair(vc.transform.position);
            if (off == null)
            {
                Toast("Nessuna officina conosciuta in zona. Esplora per trovarne una.");
                return;
            }
            if (!Wallet.CanAfford(TowCost))
            {
                Toast("Servizio carro attrezzi non disponibile: servono " + TowCost + "€");
                return;
            }

            busy = true;
            api.Tow(code, off.lat, off.lng, (ok, err) =>
            {
                if (!ok)
                {
                    busy = false;
                    Toast("Carro attrezzi non riuscito: " + (err ?? "riprova"));
                    return;
                }
                if (!Wallet.TrySpend(TowCost))
                {
                    busy = false;
                    Toast("Servizio pagabile solo in contanti: servono " + TowCost + "€");
                    return;
                }
                NavigationState.Set(
                    string.IsNullOrEmpty(off.name) ? "OFFICINA" : off.name,
                    "repair", off.lat, off.lng);
                StartCoroutine(TowRoutine(vc, code, off));
            });
        }

        // ── cinematiche ───────────────────────────────────────────

        private IEnumerator FireRoutine(VehicleController vc)
        {
            var api = VehicleOwnershipApi.Ensure();
            var vi = vc.GetComponentInChildren<VehicleInteract>();
            string code = vi != null ? vi.vehicleCode : "";

            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast("Pompieri in arrivo...");
            float wait = 0f;
            while (wait < 2.0f)
            {
                wait += Time.deltaTime;
                yield return null;
            }

            var rig = BuildFireEngine();
            Vector3 start = vc.transform.position
                + vc.transform.forward * 14f + Vector3.up * 0.05f;
            Vector3 target = vc.transform.position
                + vc.transform.forward * 5.5f;
            rig.root.position = start;
            rig.root.rotation = vc.transform.rotation;

            // 1) arrivo col lampeggiante rosso
            while (Vector3.Distance(rig.root.position, target) > 1.5f)
            {
                rig.root.position = Vector3.MoveTowards(
                    rig.root.position, target, 7f * Time.deltaTime);
                if (rig.spinner != null) rig.spinner.Spin(6f);
                rig.Blink();
                yield return null;
            }
            yield return new WaitForSeconds(0.5f);

            // 2) acqua sui fiamme: spegnimento progressivo
            GameObject beam = CreateWaterBeam(rig.root, vc.transform);
            int quenchTicks = 60;
            for (int i = 0; i <= quenchTicks && vc != null; i++)
            {
                vc.QuenchFire((float)i / quenchTicks);
                rig.Blink();
                if (i % 3 == 0) yield return null;
            }
            if (beam != null) Destroy(beam);

            // 3) conferma server + spegnimento definitivo (resta incidentata)
            bool doneOk = false; string doneErr = null;
            api.ExtinguishFire(code, (ok, err) => { doneOk = ok; doneErr = err; });
            float waited = 0f;
            while (!doneOk && waited < 6f)
            {
                waited += Time.deltaTime;
                yield return null;
            }
            TrashRig(rig);
            if (!doneOk)
            {
                busy = false;
                Toast("Spegnimento non riuscito: " + (doneErr ?? "riprova"));
                yield break;
            }
            Wallet.TrySpend(FireCost);
            if (vc != null)
            {
                vc.SetDamage(VehicleDamage.Wrecked);
                Toast("Incendio spento! L'auto e' incidentata: ora tocca al carro attrezzi.");
            }
            busy = false;
            if (vc != null) OfferTow(vc);
        }

        private IEnumerator TowRoutine(VehicleController vc, string code,
            VehiclePoiRegistry.PoiInfo off)
        {
            Vector3 offWorld = WorldOrigin.ToWorld(off.lat, off.lng);
            var rb = vc != null ? vc.GetComponent<Rigidbody>() : null;
            if (rb != null) rb.isKinematic = true;

            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast("Carro attrezzi in arrivo...");
            float wait = 0f;
            while (wait < 3.5f)
            {
                wait += Time.deltaTime;
                yield return null;
            }
            if (vc == null)
            {
                busy = false;
                yield break;
            }

            var truck = BuildTowTruck();
            if (truck == null || truck.root == null)
            {
                busy = false;
                Toast("Carro attrezzi non disponibile ora.");
                yield break;
            }

            Vector3 carPos = vc.transform.position;
            Vector3 carFwd = vc.transform.forward;
            Vector3 spawnPos = carPos + carFwd * 26f + Vector3.up * 0.05f;
            Vector3 hookPos = carPos + carFwd * 6.8f;
            truck.root.position = spawnPos;
            truck.root.rotation = Quaternion.LookRotation(carFwd, Vector3.up);

            // 1) il truck ARRIVA IN RETROMARCIA (si avvicina all'indietro)
            float bt = 0f;
            while (bt < 3.2f)
            {
                bt += Time.deltaTime;
                float k = Mathf.SmoothStep(0f, 1f, bt / 3.2f);
                truck.root.position = Vector3.Lerp(spawnPos, hookPos, k);
                truck.root.position = new Vector3(
                    truck.root.position.x, 0.05f, truck.root.position.z);
                truck.root.rotation = Quaternion.LookRotation(carFwd, Vector3.up);
                if (truck.spinner != null) truck.spinner.Spin(2.2f);
                truck.Blink();
                yield return null;
            }
            yield return new WaitForSeconds(0.4f);

            // 2) gancio sul muso + cavo fino al tail del truck
            GameObject hook = BuildHook(vc.transform);
            GameObject cable = BuildCable(truck.root, vc.transform);
            yield return new WaitForSeconds(0.9f);

            // 3) il truck guida DAVVERO verso l'officina (si puo' seguire)
            bool followable = HorizontalDist(vc.transform.position, offWorld)
                <= MaxFollowDistance;
            Vector3 target = followable ? offWorld
                : vc.transform.position + carFwd * 320f;
            target.y = 0.05f;

            float speed = 12f;
            float arriveDist = followable ? 9f : 3f;
            float maxSeconds = followable ? 115f : 40f;
            float elapsed = 0f;

            while (HorizontalDist(truck.root.position, target) > arriveDist
                && elapsed < maxSeconds)
            {
                elapsed += Time.deltaTime;

                Vector3 toward = target - truck.root.position;
                toward.y = 0f;
                if (toward.sqrMagnitude > 0.01f)
                {
                    Quaternion want = Quaternion.LookRotation(
                        toward.normalized, Vector3.up);
                    truck.root.rotation = Quaternion.Slerp(
                        truck.root.rotation, want, 2.4f * Time.deltaTime);
                }
                truck.root.position += truck.root.forward * speed * Time.deltaTime;
                truck.root.position = new Vector3(
                    truck.root.position.x, 0.05f, truck.root.position.z);

                if (truck.spinner != null) truck.spinner.Spin(speed);
                truck.Blink();

                // l'auto segue agganciata al cavo (ruote che girano)
                if (vc != null)
                {
                    vc.transform.position = truck.root.position
                        - truck.root.forward * 8.6f;
                    vc.transform.position = new Vector3(
                        vc.transform.position.x, 0.05f,
                        vc.transform.position.z);
                    vc.transform.rotation = Quaternion.Slerp(
                        vc.transform.rotation, truck.root.rotation,
                        4f * Time.deltaTime);
                    var carSpin = vc.GetComponent<WheelSpinner>();
                    if (carSpin != null) carSpin.Spin(speed);
                }
                UpdateCable(cable, truck.root, vc != null ? vc.transform : null);

                yield return null;
            }

            // 4) consegna all'officina sotto il nero
            var fader = Game.Instance != null ? Game.Instance.fader : null;
            if (fader != null)
            {
                fader.gameObject.SetActive(true);
                fader.FadeToBlack(null);
            }
            if (fader != null) yield return new WaitForSeconds(fader.duration);

            if (vc != null)
            {
                vc.transform.SetParent(holder.transform, false);
                vc.transform.position = offWorld;
                vc.transform.rotation = Quaternion.identity;
                if (rb != null) rb.isKinematic = true;
                VehicleSpawnManager.RegisterActiveOwned(code, vc.gameObject);
                vc.name = code;
                if (!rescued.Contains(vc.gameObject))
                    rescued.Add(vc.gameObject);
            }
            if (cable != null) Destroy(cable);
            if (hook != null) Destroy(hook);
            TrashRig(truck);

            if (fader != null)
            {
                fader.FadeFromBlack(null);
                yield return new WaitForSeconds(fader.duration);
                fader.gameObject.SetActive(false);
            }
            busy = false;
            Toast("Auto consegnata a " + off.name + "! Va' in officina per la riparazione.");
            Debug.Log("[RescueDirector] rimorchiato " + code + " verso officina " + off.name);
        }

        // ── utilita' ──────────────────────────────────────────────

        public static VehiclePoiRegistry.PoiInfo NearestRepair(Vector3 worldPos)
        {
            GeoCoord g = WorldOrigin.ToGeo(worldPos);
            var p = VehiclePoiRegistry.NearestRepair(g.lat, g.lng);
            return p ?? VehiclePoiRegistry.Nearest("repair", g.lat, g.lng);
        }

        private static float HorizontalDist(Vector3 a, Vector3 b)
        {
            a.y = 0f; b.y = 0f;
            return Vector3.Distance(a, b);
        }

        private static void Toast(string msg)
        {
            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast(msg);
        }

        // ── gancio e cavo ─────────────────────────────────────────

        private GameObject BuildHook(Transform car)
        {
            var h = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(h.GetComponent<Collider>());
            h.name = "TowHook";
            h.transform.SetParent(car, false);
            h.transform.localPosition = new Vector3(0f, 0.4f, 2.15f);
            h.transform.localRotation = Quaternion.identity;
            h.transform.localScale = new Vector3(0.10f, 0.10f, 0.10f);
            var r = h.GetComponent<Renderer>();
            if (r != null) r.sharedMaterial =
                SimpleMat(new Color(1f, 0.75f, 0.15f));
            return h;
        }

        private GameObject BuildCable(Transform truckRoot, Transform car)
        {
            var c = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(c.GetComponent<Collider>());
            c.name = "TowCable";
            var r = c.GetComponent<Renderer>();
            if (r != null) r.sharedMaterial =
                SimpleMat(new Color(0.22f, 0.22f, 0.25f));
            UpdateCable(c, truckRoot, car);
            return c;
        }

        private void UpdateCable(GameObject cable, Transform truckRoot,
            Transform car)
        {
            if (cable == null) return;
            Vector3 a = truckRoot.position
                - truckRoot.forward * 4.5f + Vector3.up * 0.55f;
            Vector3 b = car != null
                ? car.position + car.forward * 2.15f + Vector3.up * 0.4f
                : a + truckRoot.forward * 6f;
            Vector3 dir = b - a;
            Vector3 mid = (a + b) * 0.5f;
            cable.transform.position = mid;
            if (dir.sqrMagnitude > 0.001f)
                cable.transform.rotation =
                    Quaternion.LookRotation(dir.normalized, Vector3.up);
            cable.transform.localScale = new Vector3(
                0.05f, 0.05f, dir.magnitude);
        }

        // ── costruzione mezzi (FBX Kenney reali, fallback procedurali) ──

        private sealed class RescueRig
        {
            public Transform root;
            public Light beacon;
            public WheelSpinner spinner;
            public Color flashColor = Color.yellow;
            public bool alive = true;

            public void Blink()
            {
                if (beacon == null) return;
                float k = 0.5f + 0.5f * Mathf.Sin(Time.time * 16f);
                beacon.intensity = 0.35f + k * 4.2f;
                beacon.color = Color.Lerp(flashColor, Color.white, k * 0.55f);
            }
        }

        private void TrashRig(RescueRig rig)
        {
            if (rig == null) return;
            rig.alive = false;
            if (rig.root != null) Destroy(rig.root.gameObject);
        }

        private RescueRig BuildTowTruck()
        {
            return BuildRescueVehicle("Vehicles/truck",
                "CarroAttrezzi", new Color(1f, 0.6f, 0.05f), false);
        }

        private RescueRig BuildFireEngine()
        {
            return BuildRescueVehicle("Vehicles/firetruck",
                "VigiliDelFuoco", new Color(1f, 0.15f, 0.1f), false);
        }

        private RescueRig BuildRescueVehicle(string prefabKey, string name,
            Color flashColor, bool fallbackFire)
        {
            var rig = new RescueRig();
            rig.flashColor = flashColor;
            GameObject prefab = Resources.Load<GameObject>(prefabKey);
            if (prefab != null)
            {
                var inst = Instantiate(prefab);
                inst.name = name;
                rig.root = inst.transform;
                foreach (var col in inst.GetComponentsInChildren<Collider>())
                    col.enabled = false;
            }
            else if (fallbackFire)
            {
                rig = BuildFireEngineProcedural();
            }
            else
            {
                rig = BuildTowTruckProcedural();
            }
            if (rig.root == null) return rig;

            rig.spinner = rig.root.GetComponent<WheelSpinner>();
            if (rig.spinner == null)
                rig.spinner = rig.root.gameObject.AddComponent<WheelSpinner>();

            var bgo = new GameObject("Beacon");
            bgo.transform.SetParent(rig.root, false);
            bgo.transform.localPosition = new Vector3(0f, 2.3f, -1.2f);
            rig.beacon = bgo.AddComponent<Light>();
            rig.beacon.type = LightType.Point;
            rig.beacon.range = 14f;
            rig.beacon.intensity = 0f;
            return rig;
        }

        private RescueRig BuildTowTruckProcedural()
        {
            var rig = new RescueRig();
            var root = new GameObject("CarroAttrezzi").transform;
            var metal = SimpleMat(new Color(0.32f, 0.34f, 0.38f));
            var dark = SimpleMat(new Color(0.13f, 0.13f, 0.14f));

            MakeBox(root, new Vector3(0f, 1.15f, -0.55f),
                new Vector3(2.15f, 1.1f, 4.9f), metal);
            MakeBox(root, new Vector3(0f, 0.78f, 2.9f),
                new Vector3(2.25f, 1.45f, 1.5f), metal);
            MakeBox(root, new Vector3(0f, 1.05f, 2.9f),
                new Vector3(1.8f, 0.55f, 1.15f), dark);
            MakeBox(root, new Vector3(0f, 0.5f, -0.55f),
                new Vector3(2.35f, 0.28f, 5.0f), dark);
            MakeWheels(root, new Color(0.09f, 0.09f, 0.1f));
            rig.root = root;
            return rig;
        }

        private RescueRig BuildFireEngineProcedural()
        {
            var rig = new RescueRig();
            var root = new GameObject("VigiliDelFuoco").transform;
            var red = SimpleMat(new Color(0.72f, 0.16f, 0.14f));
            var metal = SimpleMat(new Color(0.85f, 0.85f, 0.88f));
            var dark = SimpleMat(new Color(0.14f, 0.14f, 0.16f));

            MakeBox(root, new Vector3(0f, 1.25f, -0.5f),
                new Vector3(2.3f, 1.35f, 4.4f), red);
            MakeBox(root, new Vector3(0f, 0.85f, 2.75f),
                new Vector3(2.3f, 1.5f, 1.3f), red);
            MakeBox(root, new Vector3(0f, 1.15f, 2.75f),
                new Vector3(1.7f, 0.6f, 0.95f), dark);
            MakeBox(root, new Vector3(0f, 1.65f, -0.4f),
                new Vector3(2.05f, 0.7f, 3.9f), metal);
            MakeBox(root, new Vector3(0f, 0.32f, -0.2f),
                new Vector3(2.2f, 0.25f, 5.4f), dark);
            MakeWheels(root, new Color(0.08f, 0.08f, 0.09f));
            rig.root = root;
            return rig;
        }

        private void MakeWheels(Transform parent, Color color)
        {
            var mat = SimpleMat(color);
            float[] xs = { -1.05f, 1.05f };
            float[] zs = { -1.85f, 1.85f };
            foreach (float x in xs)
                foreach (float z in zs)
                {
                    var c = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                    Object.Destroy(c.GetComponent<Collider>());
                    c.name = "wheel";
                    c.transform.SetParent(parent, false);
                    c.transform.localPosition = new Vector3(x, 0.35f, z);
                    c.transform.localRotation = Quaternion.Euler(0f, 0f, 90f);
                    c.transform.localScale = new Vector3(0.72f, 0.32f, 0.72f);
                    var r = c.GetComponent<Renderer>();
                    if (r != null) r.sharedMaterial = mat;
                }
        }

        private Transform MakeBox(Transform parent, Vector3 localPos,
            Vector3 size, Material mat)
        {
            var c = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(c.GetComponent<Collider>());
            c.name = "box";
            c.transform.SetParent(parent, false);
            c.transform.localPosition = localPos;
            c.transform.localScale = size;
            var r = c.GetComponent<Renderer>();
            if (r != null) r.sharedMaterial = mat;
            return c.transform;
        }

        private static Material SimpleMat(Color c)
        {
            var shader = Shader.Find("Standard");
            if (shader == null) shader = Shader.Find("Universal Render Pipeline/Unlit");
            if (shader == null) shader = Shader.Find("Sprites/Default");
            var m = new Material(shader);
            m.color = c;
            return m;
        }

        private GameObject CreateWaterBeam(Transform from, Transform to)
        {
            var beam = GameObject.CreatePrimitive(PrimitiveType.Quad);
            Object.Destroy(beam.GetComponent<Collider>());
            beam.name = "WaterBeam";
            var mat = SimpleMat(new Color(0.45f, 0.82f, 0.96f));
            var c = mat.color;
            c.a = 0.8f;
            mat.color = c;
            var r = beam.GetComponent<Renderer>();
            if (r != null) r.sharedMaterial = mat;

            Vector3 fromPos = from.position;
            fromPos.y = 1.4f;
            Vector3 toPos = to.position;
            toPos.y = 1.0f;
            Vector3 dir = toPos - fromPos;
            dir.y = 0f;
            float len = Mathf.Max(0.6f, dir.magnitude);
            float ang = Mathf.Atan2(dir.z, dir.x) * 57.29578f;
            beam.transform.position = fromPos
                + (len > 0.01f ? dir.normalized * (len * 0.5f) : Vector3.zero);
            beam.transform.rotation = Quaternion.Euler(0f, ang, 0f);
            beam.transform.localScale = new Vector3(len, 0.18f, 1f);
            return beam;
        }
    }
}
