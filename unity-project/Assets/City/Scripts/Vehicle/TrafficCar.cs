using UnityEngine;
using City.NPC;
using City.Player;

namespace City.Vehicle
{
    public class TrafficCar : MonoBehaviour
    {
        public float speed = 6f;
        private Vector3[] path;
        private int currentIdx;
        private WheelSpinner spinner;

        // ── vita vera: fermate, taxi, passeggeri ──────────────────
        private bool taxiCfg;
        private int stopIdx = -1;
        private bool stopDone;
        private bool paused;
        private float pause;
        private System.Random rr;
        private GameObject npc;
        private int npcMode;   // 0=nessuno, 1=sale, 2=scende
        private float npcT;

        // ── fermate del pullman e dei taxi (piu' fermate) ──
        private readonly System.Collections.Generic.List<int> busStops =
            new System.Collections.Generic.List<int>();
        private int busStopIdx;

        // ── flusso taxi: occupazione singola ──────────────────────
        private bool occupied;                  // passeggero/i AI a bordo

        // ── taxi noleggiato dal giocatore (gestito da TaxiService) ─
        public bool Hired { get; private set; }
        public bool PlayerOnBoard { get; private set; }

        /// <summary>Passeggero AI a bordo (taxi occupato).</summary>
        public bool HasPassenger { get { return occupied; } }

        /// <summary>Flag noleggio del giocatore (il servizio lo pilota).</summary>
        public void SetPlayerOnBoard(bool on)
        {
            PlayerOnBoard = on;
        }
        private Vector3? serviceTarget;
        private bool atHirePoint;               // arrivato a prendere il player
        private readonly System.Collections.Generic.List<CharacterWalker>
            npcWalkers = new System.Collections.Generic.List<CharacterWalker>();
        private readonly System.Collections.Generic.List<UnityEngine.Transform>
            npcSeats = new System.Collections.Generic.List<UnityEngine.Transform>();

        /// <summary>Taxi attualmente in scena, per il noleggio dal player.</summary>
        public static readonly System.Collections.Generic.List<TrafficCar>
            AllTaxis = new System.Collections.Generic.List<TrafficCar>();

        /// <summary>Punto in coordinate MONDO verso cui guidare (null = fermo).</summary>
        public Vector3? ServiceTarget
        {
            get { return serviceTarget; }
            set { serviceTarget = value; if (value.HasValue) atHirePoint = false; }
        }

        public bool IsWaitingPickup
        {
            get { return taxiCfg && Hired && !PlayerOnBoard && !serviceTarget.HasValue; }
        }

        public void Hire(Vector3 pickupWorld)
        {
            if (!taxiCfg || Hired || PlayerOnBoard) return;
            Hired = true;
            occupied = false;
            ServiceTarget = pickupWorld;
        }

        public void ResetHire()
        {
            Hired = false;
            PlayerOnBoard = false;
            serviceTarget = null;
            atHirePoint = false;
        }

        private static readonly string[] CarPrefabs = new string[]
        {
            "Vehicles/sedan", "Vehicles/suv", "Vehicles/van",
            "Vehicles/taxi", "Vehicles/hatchback-sports", "Vehicles/delivery",
            "Vehicles/truck",
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
            currentIdx = 0;

            // randomizzazione stabile (stesso seed => stessi stop, come le auto)
            rr = new System.Random(colorSeed * 31 + 17);

            // ~8% dei veicoli in movimento sono pullman: grandi, lenti
            isBus = rr.Next(12) == 0;
            speed = isBus ? Mathf.Min(spd, 5f) : spd;

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
            spinner = gameObject.GetComponent<WheelSpinner>();
            if (spinner == null) spinner = gameObject.AddComponent<WheelSpinner>();

            taxiCfg = taxiPrefabPicked;

            if (isBus) BuildBusStops();
            else if (taxiCfg) BuildTaxiStops();
            else if (path.Length >= 6 && rr.NextDouble() < 0.6)
                stopIdx = 2 + rr.Next(path.Length - 4);

            if (isBus) AddBusSign();
            else if (taxiCfg) AddTaxiSign();

            if (taxiCfg && !AllTaxis.Contains(this)) AllTaxis.Add(this);
        }

        private void OnDisable()
        {
            if (AllTaxis != null) AllTaxis.Remove(this);
        }

        /// <summary>I taxi si fermano a piu' stalli (scaricare/caricare).</summary>
        private void BuildTaxiStops()
        {
            if (path.Length < 8) return;
            int n = Mathf.Clamp(path.Length / 9, 2, 5);
            for (int k = 0; k < n; k++)
            {
                int idx = 2 + rr.Next(path.Length - 4);
                if (!busStops.Contains(idx))
                    busStops.Add(idx);
            }
            busStops.Sort();
        }

        private bool isBus;

        private bool taxiPrefabPicked;

        private void AddTaxiSign()
        {
            var sign = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Destroy(sign.GetComponent<Collider>());
            sign.name = "TaxiSign";
            sign.transform.SetParent(transform, false);
            sign.transform.localPosition = new Vector3(0f, 1.55f, -0.2f);
            sign.transform.localScale = new Vector3(0.5f, 0.16f, 0.8f);
            sign.GetComponent<Renderer>().sharedMaterial =
                MakeMat(new Color(1f, 0.82f, 0.1f));
        }

        private void AddBusSign()
        {
            var sign = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Destroy(sign.GetComponent<Collider>());
            sign.name = "BusSign";
            sign.transform.SetParent(transform, false);
            sign.transform.localPosition = new Vector3(0f, 2.3f, 0f);
            sign.transform.localScale = new Vector3(1.4f, 0.45f, 0.12f);
            sign.GetComponent<Renderer>().sharedMaterial =
                MakeMat(new Color(0.05f, 0.35f, 0.65f));
        }

        /// <summary>Distribuisce piu' fermate lungo il percorso del pullman
        /// (le stesse su ogni client: RNG con seed del chunk).</summary>
        private void BuildBusStops()
        {
            if (path.Length < 6) return;
            int n = Mathf.Clamp(path.Length / 6, 2, 6);
            for (int k = 0; k < n; k++)
            {
                int idx = 1 + rr.Next(path.Length - 2);
                if (!busStops.Contains(idx))
                    busStops.Add(idx);
            }
            busStops.Sort();
        }

        private int CurrentStopIdx() =>
            (isBus || taxiCfg)
                ? (busStopIdx < busStops.Count ? busStops[busStopIdx] : -1)
                : stopIdx;

        private void AdvanceStop()
        {
            if (isBus || taxiCfg)
            {
                busStopIdx++;
                if (busStopIdx >= busStops.Count) busStopIdx = 0;
            }
            else
            {
                stopDone = true;
            }
        }

        private void Update()
        {
            if (path == null || path.Length < 2) return;

            // taxi noleggiato dal giocatore (o in corsa con lui): la vita
            // normale (fermate/passeggeri AI) si sospende e il taxi guida
            // dritto verso il bersaglio pattuito
            if (taxiCfg && (Hired || PlayerOnBoard))
            {
                if (serviceTarget.HasValue) DriveHired();
                return;
            }

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

            // fermata programmata: raggiunto il waypoint si tira su/passa
            // il pedone qualche istante e poi si riparte. I pullman si
            // fermano a TUTTE le fermate; i taxi caricano se vuoti (2 PNG
            // insieme) e scaricano se occupati, e continuano il servizio.
            int cur = CurrentStopIdx();
            if (!paused && cur >= 0 && currentIdx == cur)
            {
                BeginStop();
                UpdateNpc(Time.deltaTime, true);
                return;
            }
            if (paused)
            {
                pause -= Time.deltaTime;
                if (pause <= 0f)
                {
                    paused = false;
                    AdvanceStop();
                    if (npc != null) { Destroy(npc); npc = null; }
                }
                else
                {
                    UpdateNpc(Time.deltaTime, false);
                }
                return;
            }

            // Cede il passo ai pedoni: le auto viaggiano ormai ovunque e il
            // collider e' solido. Se il giocatore (o un pedone) e' davanti
            // lungo la marcia frena gradualmente fino a fermarsi (niente
            // investimenti in permanenza); ripreso si rimette in moto.
            float brakeAhead = 8f;
            Vector3 fwd = dir.normalized;
            float brake = 1f;
            if (PlayerController.Instance != null)
            {
                Vector3 toPlayer = PlayerController.Instance.transform.position
                    - transform.position;
                toPlayer.y = 0f;
                float pd = toPlayer.magnitude;
                if (pd < brakeAhead
                    && Vector3.Dot(fwd, pd > 0.001f
                        ? toPlayer.normalized : fwd) > 0.3f)
                {
                    brake = Mathf.Clamp01((pd - 2.2f) / (brakeAhead - 2.2f));
                }
            }

            var npcs = City.NPC.NPCController.Active;
            for (int i = 0; i < npcs.Count; i++)
            {
                var n = npcs[i];
                if (n == null) continue;
                Vector3 toNpc = n.transform.position - transform.position;
                toNpc.y = 0f;
                float nd = toNpc.magnitude;
                if (nd >= brakeAhead) continue;
                if (Vector3.Dot(fwd, toNpc.normalized) <= 0.3f) continue;
                float b = Mathf.Clamp01((nd - 2.2f) / (brakeAhead - 2.2f));
                if (b < brake) brake = b;
            }

            Vector3 move = dir.normalized * speed * Time.deltaTime * brake;
            transform.localPosition += move;
            if (spinner != null) spinner.Spin(speed * brake);

            Quaternion look = Quaternion.LookRotation(dir.normalized, Vector3.up);
            transform.localRotation =
                Quaternion.Slerp(transform.localRotation, look, 5f * Time.deltaTime);
        }

        /// <summary>Guida in linea retta verso il bersaglio MONDO (convertito
        /// in locale del chunk). Trova il punto di raccolta o porta il
        /// giocatore a destinazione; arrivato si ferma e avvisa il servizio.</summary>
        private void DriveHired()
        {
            if (!serviceTarget.HasValue) return;
            Vector3 wanted = serviceTarget.Value;
            Vector3 parentWorld = transform.position - transform.localPosition;
            Vector3 targetLocal = wanted - parentWorld;
            Vector3 dir = targetLocal - transform.localPosition;
            dir.y = 0f;
            float dist = dir.magnitude;
            float arrive = PlayerOnBoard ? 14f : 6f;

            if (dist < arrive)
            {
                serviceTarget = null;
                if (PlayerOnBoard)
                {
                    if (TaxiService.Instance != null)
                        TaxiService.Instance.NotifyArrived(this);
                }
                else
                {
                    atHirePoint = true;
                }
                return;
            }

            Vector3 move = dir.normalized * speed * Time.deltaTime;
            transform.localPosition += move;
            if (spinner != null) spinner.Spin(speed);

            Quaternion look = Quaternion.LookRotation(dir.normalized, Vector3.up);
            transform.localRotation =
                Quaternion.Slerp(transform.localRotation, look, 5f * Time.deltaTime);
        }

        private void BeginStop()
        {
            paused = true;
            npcT = 0f;
            if (isBus)
            {
                // il pullman resta fermo piu' a lungo: qualcuno scende/sale
                pause = 3.5f + (float)rr.NextDouble() * 1.5f;
                npcMode = (busStopIdx % 2 == 0) ? 2 : 1;
            }
            else if (taxiCfg)
            {
                if (!occupied)
                {
                    // fermata di raccolta: fino a 2 persone vere salgono
                    // insieme e il taxi riparte appena entrate; finche' c'e'
                    // qualcuno a bordo nessun altro puo' salire
                    npcMode = 1;
                    pause = 2.0f + (float)rr.NextDouble() * 0.5f;
                    occupied = true;
                }
                else
                {
                    // fermata di discesa: i passeggeri (fino a 2) scendono e
                    // RESTANO per le strade (niente piu' sparizioni), il taxi
                    // si svuota e puo' ricaricare alla fermata successiva
                    npcMode = 2;
                    pause = 2.4f + (float)rr.NextDouble() * 0.9f;
                }
            }
            else
            {
                pause = 2.2f + (float)rr.NextDouble() * 2.4f;
                npcMode = 1;
            }
            SpawnNpc();
        }

        // posti disponibili per fermata di carico del taxi
        private const int TaxiSeats = 2;

        private void SpawnNpc()
        {
            var rootGo = new GameObject("Passenger");
            rootGo.transform.SetParent(transform, false);
            rootGo.transform.localPosition =
                npcMode == 1 ? new Vector3(3.2f, 0f, 0f)
                             : new Vector3(1.4f, 0f, 0f);
            rootGo.transform.localRotation = Quaternion.identity;

            npcWalkers.Clear();
            npcSeats.Clear();
            int seats = taxiCfg ? TaxiSeats : 1;
            for (int i = 0; i < seats; i++)
            {
                // persone vere (modello Kenney + skin PNG), niente piu' pedine
                var pm = CityCharacterFactory.SpawnPassengerModel(
                    rootGo.transform, true, rr);
                if (pm == null || pm.go == null)
                {
                    var seatGo = BuildFallbackPassenger2(rootGo.transform, i);
                    if (seatGo != null) npcSeats.Add(seatGo);
                }
                else
                {
                    if (pm.walker != null) npcWalkers.Add(pm.walker);
                    if (taxiCfg)
                        pm.go.transform.localPosition =
                            new Vector3(0f, 0f, (i == 0) ? -0.6f : 0.6f);
                    npcSeats.Add(pm.go.transform);
                }
            }
            npc = rootGo;
        }

        private Transform BuildFallbackPassenger2(Transform root, int seat)
        {
            // ogni sedile e' un Figlio coeso: Cosi' ReleasePassenger scarica
            // un solo oggetto per sedile anche quando il modello manca
            var seatGo = new GameObject("Seat");
            seatGo.transform.SetParent(root, false);
            seatGo.transform.localPosition = taxiCfg
                ? new Vector3(0f, 0f, (seat == 0) ? -0.6f : 0.6f)
                : Vector3.zero;

            var body = GameObject.CreatePrimitive(PrimitiveType.Capsule);
            Destroy(body.GetComponent<Collider>());
            body.name = "Body";
            body.transform.SetParent(seatGo.transform, false);
            body.transform.localPosition = new Vector3(0f, 0.85f, 0f);
            body.transform.localScale = new Vector3(0.55f, 1.15f, 0.55f);
            body.GetComponent<Renderer>().sharedMaterial = MakeMat(
                npcMode == 1 ? new Color(0.25f, 0.4f, 0.8f)
                             : new Color(0.7f, 0.35f, 0.25f));

            var head = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            Destroy(head.GetComponent<Collider>());
            head.name = "Head";
            head.transform.SetParent(seatGo.transform, false);
            head.transform.localPosition = new Vector3(0f, 1.75f, 0f);
            head.transform.localScale = Vector3.one * 0.42f;
            head.GetComponent<Renderer>().sharedMaterial =
                MakeMat(new Color(0.9f, 0.76f, 0.62f));

            return seatGo.transform;
        }

        // Pedina di riserva se il prefab del personaggio non e' caricabile.
        private void BuildFallbackPassenger(Transform root)
        {
            BuildFallbackPassenger2(root, 0);
        }


        private void UpdateNpc(float dt, bool skipIdle)
        {
            if (npc == null) return;
            npcT += dt;
            float walkSpd = 0f;

            if (npcMode == 1)
            {
                // sale: cammina dal marciapiede alla portiera e svanisce dentro
                float w = Mathf.Min(1f, npcT / 1.3f);
                float x = Mathf.Lerp(3.2f, 1.35f, w);
                if (npcT < 1.3f)
                {
                    npc.transform.localPosition = new Vector3(x, 0f, 0f);
                    npc.transform.localPosition =
                        new Vector3(x, Mathf.Abs(Mathf.Sin(npcT * 9f)) * 0.08f, 0f);
                    walkSpd = 1.3f;
                }
                else
                {
                    float s = 1f - Mathf.Clamp01((npcT - 1.3f) / 0.45f);
                    npc.transform.localScale = Vector3.one * s;
                    if (s <= 0.02f)
                    {
                        Destroy(npc);
                        npc = null;
                        npcSeats.Clear();
                    }
                }
            }
            else
            {
                // scende: appare alla portiera e si allontana; a fine corsa
                // NON sparisce: resta in strada come cittadino che cammina
                if (npcT < 0.4f)
                {
                    float a = Mathf.Clamp01(npcT / 0.25f);
                    npc.transform.localScale = Vector3.one * a;
                }
                else
                {
                    float w = Mathf.Min(1f, (npcT - 0.4f) / 1.1f);
                    npc.transform.localPosition =
                        new Vector3(Mathf.Lerp(1.4f, 3.6f, w), 0f, 0f);
                    walkSpd = 1.4f;
                    if (npcT > 1.5f) ReleasePassenger();
                }
            }
            for (int k = 0; k < npcWalkers.Count; k++)
                if (npcWalkers[k] != null) npcWalkers[k].SetSpeed(walkSpd);
        }

        // I passeggeri scesi restano nel mondo e camminano per le strade.
        private void ReleasePassenger()
        {
            if (npc == null) return;
            var n = npc;
            npc = null;
            for (int k = 0; k < npcWalkers.Count; k++)
                if (npcWalkers[k] != null) npcWalkers[k].SetSpeed(0f);
            npcWalkers.Clear();

            // taxi: ogni sedile scende e resta come cittadino vero (niente piu'
            // sparizioni); le altre auto scaricano un solo passeggero
            int capacity = taxiCfg ? TaxiSeats : 1;
            int released = 0;
            for (int k = 0; k < npcSeats.Count && released < capacity; k++)
            {
                var seat = npcSeats[k];
                if (seat != null)
                    City.NPC.CityCharacterFactory.ReleaseToStreet(seat);
                released++;
            }
            npcSeats.Clear();
            if (taxiCfg) occupied = false;    // il taxi ora e' libero
            Destroy(n);
        }


        private void BuildCarModel(int seed)
        {
            if (isBus) { BuildBus(); return; }

            EnsurePrefabs();

            // modulo sicuro anche per seed negativi (difensivo: il caller
            // normalizza gia' con & 0x7FFFFFFF)
            int len = loadedPrefabs.Length;
            int idx = ((seed % len) + len) % len;
            GameObject prefab = loadedPrefabs[idx];

            taxiPrefabPicked = prefab != null && prefab.name.IndexOf(
                "taxi", System.StringComparison.OrdinalIgnoreCase) >= 0;

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

            // Collider SOLIDO del veicolo AI (non piu' trigger): il giocatore
            // ora urta davvero le auto in movimento invece di attraversarle.
            // Rigidbody cinematico: il movimento resta via transform.
            var box = gameObject.AddComponent<BoxCollider>();
            box.isTrigger = false;
            box.size = new Vector3(2f, 1.3f, 4f);
            box.center = new Vector3(0f, 0.65f, 0f);
            EnsureSolidBody();
        }

        private void EnsureSolidBody()
        {
            var rb = GetComponent<Rigidbody>();
            if (rb == null) rb = gameObject.AddComponent<Rigidbody>();
            rb.isKinematic = true;
            rb.useGravity = false;
            rb.interpolation = RigidbodyInterpolation.None;
            rb.collisionDetectionMode = CollisionDetectionMode.Discrete;
        }

        // dimensioni pullman (condivise da BuildBus e aiuti)
        private const float BusW = 2.5f;
        private const float BusH = 2.0f;
        private const float BusL = 8.5f;

        private void BuildBus()
        {
            const float w = BusW, h = BusH, l = BusL;

            // carrozzeria
            var body = GameObject.CreatePrimitive(PrimitiveType.Cube);
            body.name = "BBody";
            body.transform.SetParent(transform, false);
            body.transform.localPosition = new Vector3(0f, h * 0.5f + 0.3f, 0f);
            body.transform.localScale = new Vector3(w, h, l);
            body.GetComponent<Renderer>().sharedMaterial =
                MakeMat(new Color(0.05f, 0.45f, 0.75f));
            body.GetComponent<Collider>().enabled = false;

            // cabina rialzata (finestre) in coda
            var cabin = GameObject.CreatePrimitive(PrimitiveType.Cube);
            cabin.name = "BCabin";
            cabin.transform.SetParent(transform, false);
            cabin.transform.localPosition = new Vector3(0f, h + 0.25f, 0f);
            cabin.transform.localScale = new Vector3(w * 0.9f, 0.5f, l * 0.8f);
            cabin.GetComponent<Renderer>().sharedMaterial =
                MakeMat(new Color(0.35f, 0.6f, 0.85f));
            cabin.GetComponent<Collider>().enabled = false;

            // luci anteriori/posteriori
            AddLightBar(w, l * 0.5f - 0.1f, new Color(0.98f, 0.9f, 0.6f));
            AddLightBar(w, -(l * 0.5f - 0.1f), new Color(0.8f, 0.1f, 0.1f));

            // ruote (3 assi)
            Color wc = new Color(0.1f, 0.1f, 0.1f);
            for (int axle = -1; axle <= 1; axle++)
            {
                float z = axle * l * 0.28f;
                PlaceBusWheel(w, z, wc);
                PlaceBusWheel(-w, z, wc);
            }

            // Collider SOLIDO del pullman (urta il giocatore, come le auto)
            var box = gameObject.AddComponent<BoxCollider>();
            box.isTrigger = false;
            box.size = new Vector3(w + 0.4f, h + 0.8f, l + 0.4f);
            box.center = new Vector3(0f, (h * 0.5f + 0.4f), 0f);
            EnsureSolidBody();
        }

        private void AddLightBar(float w, float z, Color c)
        {
            var bar = GameObject.CreatePrimitive(PrimitiveType.Cube);
            bar.name = "BLight";
            bar.transform.SetParent(transform, false);
            bar.transform.localPosition = new Vector3(0f, BusH + 0.8f, z);
            bar.transform.localScale = new Vector3(w * 0.7f, 0.12f, 0.05f);
            bar.GetComponent<Renderer>().sharedMaterial = MakeMat(c);
            bar.GetComponent<Collider>().enabled = false;
        }

        private void PlaceBusWheel(float x, float z, Color c)
        {
            var wObj = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            wObj.name = "BWheel";
            wObj.transform.SetParent(transform, false);
            wObj.transform.localPosition = new Vector3(x * 0.5f, 0.3f, z);
            wObj.transform.localRotation = Quaternion.Euler(0f, 0f, 90f);
            wObj.transform.localScale = new Vector3(0.5f, 0.12f, 0.5f);
            wObj.GetComponent<Renderer>().sharedMaterial = MakeMat(c);
            wObj.GetComponent<Collider>().enabled = false;
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
