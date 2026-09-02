using System.Collections.Generic;
using UnityEngine;
using City.UI;
using City.World;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Corsa del pullman guidabile: quando il giocatore guida il Pullman
    /// parte un itinerario a fermate. Ad ogni fermata salgono dei passeggeri
    /// (visibili sul marciapiede) che vai a portare; a fine corsa ricevi una
    /// ricompensa per ogni passeggero consegnato.
    /// Non parte mai con altre auto: serve il modello "Pullman".
    /// </summary>
    public class BusMissionService : MonoBehaviour
    {
        public static BusMissionService Instance { get; private set; }

        public static BusMissionService Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("BusMissionService");
            DontDestroyOnLoad(go);
            return go.AddComponent<BusMissionService>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
        }

        // ── stato corsa ───────────────────────────────────────────
        private readonly List<VehiclePoiRegistry.PoiInfo> route =
            new List<VehiclePoiRegistry.PoiInfo>();
        private int stopIndex;
        private int onboard;          // passeggeri a bordo
        private int delivered;        // passeggeri consegnati
        private bool tripActive;
        private bool serving;         // animazione salita/scalata in corso
        private float serveTimer;
        private float nextOffer;      // offerta periodica quando si guida il bus
        private float tripTimer;
        private GameObject waitingGroup;  // passeggeri alla fermata corrente
        private int waitingCount;

        private const float ArriveDist = 26f;
        private const float SpawnInterval = 7f;
        private const float ServeTime = 2.4f;
        private const float MaxTrip = 420f;
        private const int RewardPerPassenger = 6;
        private const int MaxStops = 6;

        private void Update()
        {
            if (Game.Instance == null) return;
            var car = Game.Instance.CurrentVehicle;
            bool isBus = car != null && car.data != null
                && car.data.vehicleName == "Pullman";
            float now = Time.time;

            if (!isBus)
            {
                if (tripActive) Abort("Corsa annullata: hai lasciato il pullman.");
                return;
            }

            // corsa in corso
            if (tripActive)
            {
                RunTrip(car);
                return;
            }

            // offerta periodica della corsa mentre guidi il pullman
            if (now < nextOffer) return;
            nextOffer = now + SpawnInterval + Random.Range(0f, 3f);
            if (Random.Range(0f, 1f) > 0.75f) return;
            if (car.transform == null) return;
            if (OfferDialog.Instance == null) return;

            OfferDialog.Offer("CORSA BUS",
                "Sei sul pullman. Vuoi fare una corsa in citta'? Ti guido a "
                + "piu' fermate: raccogli i passeggeri fermandoti e ricevi \u20ac"
                + RewardPerPassenger + " a passeggero consegnato.",
                () => StartTrip(car));
        }

        private void StartTrip(VehicleController car)
        {
            var list = new List<VehiclePoiRegistry.PoiInfo>(
                VehiclePoiRegistry.All());
            if (list.Count < 2)
            {
                Toast("Nessuna destinazione conosciuta in citta'. Esplora per trovare i POI.");
                return;
            }
            // itinerario casuale: mescola e prendi i primi MaxStops
            var rng = new System.Random();
            for (int i = list.Count - 1; i > 0; i--)
            {
                int j = rng.Next(i + 1);
                var tmp = list[i]; list[i] = list[j]; list[j] = tmp;
            }
            int n = Mathf.Clamp(list.Count, 2, MaxStops);
            route.Clear();
            for (int i = 0; i < n; i++)
                route.Add(list[i]);

            tripActive = true;
            stopIndex = 0;
            onboard = 0;
            delivered = 0;
            tripTimer = 0f;
            serving = false;
            GoToStop(car);
            Toast("Corsa iniziata! Fermati: " + route[stopIndex].name
                + " (" + (stopIndex + 1) + "/" + route.Count + ")");
        }

        private void GoToStop(VehicleController car)
        {
            var stop = route[stopIndex];
            NavigationState.Set(stop.name, stop.kind, stop.lat, stop.lng);
            SpawnWaiting(stop);
        }

        private void RunTrip(VehicleController car)
        {
            tripTimer += Time.deltaTime;
            if (tripTimer > MaxTrip || car.transform == null)
            {
                Abort("Corsa terminata: tempo scaduto.");
                return;
            }

            var stop = route[stopIndex];
            Vector3 wp = WorldOrigin.ToWorld(stop.lat, stop.lng);
            wp.y = 0f;
            Vector3 pos = car.transform.position;
            pos.y = 0f;
            float dist = Vector3.Distance(pos, wp);

            // raggiunta la fermata e fermi: salita/scalata e poi via
            if (!serving && dist < ArriveDist
                && car.GetCurrentSpeedKmh() < 3f)
            {
                serving = true;
                serveTimer = 0f;
                Toast("Fermata " + stop.name + ": salita in corso...");
            }

            if (!serving) return;

            serveTimer += Time.deltaTime;
            if (serveTimer < ServeTime) return;
            serving = false;

            ServeStop(stop);
        }

        private void ServeStop(VehiclePoiRegistry.PoiInfo stop)
        {
            // i passeggeri alla fermata salgono a bordo
            int waiting = waitingCount;
            onboard += waiting;
            DestroyWaiting();
            if (waiting > 0)
                Toast("+" + waiting + " passeggeri saliti.");

            // ultima fermata: scarica tutti e paga la ricompensa
            if (stopIndex >= route.Count - 1)
            {
                TripComplete();
                return;
            }

            stopIndex++;
            var nxt = route[stopIndex];
            NavigationState.Set(nxt.name, nxt.kind, nxt.lat, nxt.lng);
            SpawnWaiting(nxt);
            Toast("Fermata: " + nxt.name + " (" + (stopIndex + 1) + "/"
                + route.Count + ")");
        }

        private void TripComplete()
        {
            delivered = onboard;
            onboard = 0;
            Wallet.Earn(delivered * RewardPerPassenger);
            Toast("Corsa completata! " + delivered + " passeggeri consegnati: +"
                + (delivered * RewardPerPassenger) + "€");
            tripActive = false;
            NavigationState.Clear();
        }

        private void Abort(string msg)
        {
            if (tripActive)
            {
                DestroyWaiting();
                NavigationState.Clear();
                Toast(msg);
            }
            tripActive = false;
            onboard = 0;
            delivered = 0;
        }

        // ── visivi passeggeri (capsule: coerenti col resto) ────────

        private void SpawnWaiting(VehiclePoiRegistry.PoiInfo stop)
        {
            DestroyWaiting();
            Vector3 stopWorld = WorldOrigin.ToWorld(stop.lat, stop.lng);
            int n = 1 + Random.Range(0, 3);   // 1-3 passeggeri in attesa

            var root = new GameObject("BusWaiting");
            root.transform.position = stopWorld + new Vector3(2f, 0f, 0f);
            for (int i = 0; i < n; i++)
            {
                var ped = MakePed(root.transform,
                    new Vector3(Random.Range(-1f, 1f), 0f, Random.Range(-1f, 1f)),
                    new Color(0.25f, 0.45f, 0.8f));
                float s = 0.9f + Random.Range(0f, 1f) * 0.2f;
                ped.transform.localScale *= s;
            }
            waitingCount = n;
            waitingGroup = root;
        }

        private static GameObject MakePed(Transform parent, Vector3 localPos,
            Color color)
        {
            var root = new GameObject("Ped");
            root.transform.SetParent(parent, false);
            root.transform.localPosition = localPos;

            var body = GameObject.CreatePrimitive(PrimitiveType.Capsule);
            Object.Destroy(body.GetComponent<Collider>());
            body.name = "Body";
            body.transform.SetParent(root.transform, false);
            body.transform.localPosition = new Vector3(0f, 0.85f, 0f);
            body.transform.localScale = new Vector3(0.5f, 1.1f, 0.5f);
            body.GetComponent<Renderer>().sharedMaterial = PedMat(color);

            var head = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            Object.Destroy(head.GetComponent<Collider>());
            head.name = "Head";
            head.transform.SetParent(root.transform, false);
            head.transform.localPosition = new Vector3(0f, 1.72f, 0f);
            head.transform.localScale = Vector3.one * 0.4f;
            head.GetComponent<Renderer>().sharedMaterial =
                PedMat(new Color(0.9f, 0.76f, 0.62f));

            return root;
        }

        private static readonly Dictionary<Color, Material> matCache =
            new Dictionary<Color, Material>();

        private static Material PedMat(Color c)
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

        private void DestroyWaiting()
        {
            if (waitingGroup != null) { Destroy(waitingGroup); waitingGroup = null; }
            waitingCount = 0;
        }

        private static void Toast(string msg)
        {
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast(msg);
        }
    }
}
