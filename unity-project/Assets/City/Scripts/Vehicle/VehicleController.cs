using UnityEngine;
using City.Vehicle.Mechanics;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>Stato di danno di un veicolo dopo un incidente.</summary>
    public enum VehicleDamage
    {
        /// <summary>In ordine.</summary>
        None = 0,
        /// <summary>Gomma a terra: guida molto piano e tira, ma si arriva in officina.</summary>
        Flat = 1,
        /// <summary>Incidentata (fiancata incrinata): NON parte, serve il carro attrezzi.</summary>
        Wrecked = 2,
        /// <summary>In fiamme: prima i vigili del fuoco, poi il carro attrezzi.</summary>
        Fire = 3,
    }

    /// <summary>
    /// Controller di guida con servizi estesi:
    ///   • condizione (0-100): scende coi chilometri percorsi (mirror del
    ///     backend: 150 km per arrivare a 0), riduce velocita' e accelerazione,
    ///     sotto lo 0.5% l'auto non parte piu' (panne: serve l'officina)
    ///   • danni da incidente (flat/wrecked/fire): persistiti sul server. Un
    ///     urto forte buca la gomma (si guida piano), piu' forte incrina la
    ///     fiancata (non si guida: carro attrezzi), violentissimo incendia
    ///     l'auto (vigili del fuoco prima di tutto)
    ///   • odometro persistente per veicolo (PlayerPrefs + sync server con
    ///     drive-ping periodici mentre si guida)
    /// </summary>
    public class VehicleController : MonoBehaviour
    {
        public VehicleData data;

        private Rigidbody rb;
        private float currentSpeed, steerInput, throttleInput;
        private bool braking;

        public bool IsDriving { get; private set; }
        public VehicleDamage Damage { get; private set; }

        /// <summary>HP del veicolo 0-100 (integrita'): scende coi danni da
        /// impatto (per zona). A 0% l'auto e' devastata e non parte piu',
        /// serve il carro attrezzi (officina o recupero).</summary>
        public float Integrity { get; private set; } = 100f;

        /// <summary>Danno percentuale per zona della carrozzeria/meccanica
        /// (sospensioni/carrozzeria/paraurti). Sorgente della riparazione
        /// per zona in officina.</summary>
        public readonly System.Collections.Generic.Dictionary<string, float>
            ZoneDamage = new System.Collections.Generic.Dictionary<string, float>
            { { "suspension", 0f }, { "bodywork", 0f }, { "bumper", 0f } };

        /// <summary>
        /// Strato FINE di danno: singole parti meccaniche (motore, cambio,
        /// giunto/cardano, differenziale, sospensioni, ammortizzatori, freni,
        /// gomme, sterzo, serbatoio, radiatori, olio, telaio, carrozzeria,
        /// paraurti, batteria, elettrico, scarico). Integra il danno per zona:
        /// un urto colpisce LA parte esatta nel punto d'impatto oltre alla zona.
        /// Se null, il veicolo è trattato col vecchio sistema a sole zone.
        /// </summary>
        [Tooltip("Sistema di danno per singola parte meccanica")]
        public VehiclePartDamageSystem damageSystem;

        public bool HasPartDamage { get { return damageSystem != null; } }

        /// <summary>Effetto (0..1) di una parte sul comportamento di guida.</summary>
        public float PartEffect(VehiclePartType type)
        {
            return damageSystem != null ? damageSystem.GetEffectFactor(type) : 1f;
        }

        /// <summary>Inizializza il sistema di parti con lo schema standard auto.</summary>
        public void SetupStandardPartDamage(string label = "Auto")
        {
            if (damageSystem == null) damageSystem = new VehiclePartDamageSystem();
            damageSystem.SetupStandardCar(label);
        }

        /// <summary>Auto temporanea di un lavoro (es. taxi del Tassista):
        /// non e di proprieta, ma si puo salire e guidare per il lavoro.</summary>
        public bool IsJobVehicle { get; private set; }

        public void SetJobVehicle()
        {
            IsJobVehicle = true;
        }

        /// <summary>Comodita' per il codice esistente: gomma a terra.</summary>
        public bool FlatTire { get { return Damage == VehicleDamage.Flat; } }

        // ── guidabilita' dello sterzo ──────────────────────────────
        // Velocita' minima (m/s) perché l'auto cominci a girare: a ferma
        // non sterza in place, ha bisogno di un po' di avanzamento.
        private const float SteeringMinSpeed = 0.8f;
        // Riduzione dell'angolo di sterzo con la velocita': a velocita'
        // elevata le ruote sterzano meno, cosi' l'auto resta stabile in
        // curva invece di strapparsi. Valore testato sull'asse XZ amatoriale.
        private const float SteeringHighSpeedDamp = 0.045f;
        // Limite assoluto della velocita' di imbardata (deg/s) per tutti i
        // mezzi: evita che un turnSpeed alto (70-130) trasformi la guida
        // in una trottola. ~55 deg/s = curva stretta ma controllabile.
        private const float MaxYawPerSec = 55f;
        // ── stabilita' / anti-ribaltamento ─────────────────────────
        // Sospensione virtuale degli ammortizzatori: l'auto NON deve
        // accappottarsi nemmeno su un marciapiede a bassa velocita'.
        // StabilizeStrengthSoft agisce quando l'auto e' quasi dritta
        // (effetto ammortizzatore morbido: piccoli su/giu' lisci), mentre
        // StabilizeStrengthStrong si attiva quando il telaio si inclina
        // parecchio per riportarlo su con decisione senza ribaltarsi.
        private const float StabilizeStrengthSoft = 4f;
        private const float StabilizeStrengthStrong = 14f;
        // smorzamento del rollio/pitch fisico residuo (1/s)
        private const float SuspensionDamping = 1.8f;
        // Inclinazione massima (gradi) del telaio per effetto della pendenza
        // DEM (sospensioni su un dosso/collina): le ruote seguono il terreno,
        // il corpo si sbilancia ma non accappotta.
        private const float MaxInclineDeg = 20f;

        /// <summary>Sospensioni completamente a terra: l'auto zoppica anche
        /// se le altre zone sono a posto.</summary>
        public bool SuspensionDead
        {
            get { return ZoneDamage != null &&
                        ZoneDamage.ContainsKey("suspension") &&
                        ZoneDamage["suspension"] >= 100f; }
        }

        // ── danni da impatto: velocita' relativa (m/s) → danno per zona ──
        // Il marciapiede/erba e' un rialzo leggero: consuma solo le
        // sospensioni con un danno piccolissimo e cumulativo, NON buca
        // subito la gomma (come prima rimediava il modello discreto).
        private const float CurbDamagePerMs = 0.25f;    // marciapiede/erba
        private const float BodyDamagePerMs = 0.7f;     // urto laterale (muro)
        private const float FrontDamagePerMs = 1.4f;    // frontale: alto = 100%
        private const float MinImpactSpeed = 1.5f;      // sotto: trascurabile
        // incendio solo su impatti davvero violenti (speed m/s) e non scontati
        private const float FireImpactSpeed = 18f;
        private const float FireChance = 0.35f;
        private const float LimpSpeed = 2.5f;           // con sospensioni a terra

        // ── condizione / odometro ──────────────────────────────────
        // mirror di vehicle_services.py: CONDITION_PER_KM = 100/150
        public const float WearPerKm = 100f / 150f;
        private const float PingIntervalSec = 30f;
        private const string OdoKeyPrefix = "vodo_";

        private float baseCondition = 100f;
        private long baseOdometer;
        private long storedOdometer;
        private float sessionMeters;
        private float nextPing;
        private float _nextGroundAudit;
        private string vehicleCode = "";

        // ── carburante (Brookhaven-style) ──
        // Livello benzina 0-100: scende quando si guida, si ricarica ai
        // distributori (POI tipo "fuel"). A 0 il motore si spegne.
        public const float FuelMax = 100f;
        private const float FuelConsumptionPerKm = 0.8f;  // ~125 km con un pieno
        private const string FuelKeyPrefix = "vfuel_";
        private float _fuel = FuelMax;
        private float _fuelSessionMeters;

        // ── clacson / luci / sirene ──────────────────────────────
        private static AudioClip honkClip;
        private GameObject beaconRoot;
        private Light beacon1, beacon2;
        private bool beaconPhase;
        private float beaconTimer;
        private const float BeaconBlinkInterval = 0.28f;
        public bool HeadlightsOn { get; private set; }

        // ── ruote ─────────────────────────────────────────────────
        private WheelSpinner spinner;

        // ── fx danni ──────────────────────────────────────────────
        private GameObject fxRoot;
        private Material damageMat;
        private Light flameLight;
        private float fxTime;

        public float ConditionPercent
        {
            get
            {
                float total = storedOdometer + (long)sessionMeters;
                float worn = (total - baseOdometer) / 1000f * WearPerKm;
                return Mathf.Clamp(baseCondition - worn, 0f, 100f);
            }
        }

        public long TotalOdometerM
        {
            get { return storedOdometer + (long)sessionMeters; }
        }

        /// <summary>Metri percorsi in QUESTO viaggio (dall'ingresso in guida).</summary>
        public float TripMeters
        {
            get { return sessionMeters; }
        }

        /// <summary>Velocita' massima del mezzo (m/s), per la tacchimetro.</summary>
        public float MaxSpeedMs
        {
            get { return data != null ? data.maxSpeed : 14f; }
        }

        public float FuelPercent
        {
            get { return Mathf.Clamp01(_fuel / FuelMax) * 100f; }
        }

        /// <summary>Salva il livello carburante su PlayerPrefs.</summary>
        private void SaveFuel()
        {
            if (!string.IsNullOrEmpty(vehicleCode))
                PlayerPrefs.SetFloat(FuelKeyPrefix + vehicleCode, _fuel);
        }

        /// <summary>Carica il livello carburante da PlayerPrefs.</summary>
        public static float StoredFuel(string code)
        {
            if (string.IsNullOrEmpty(code)) return FuelMax;
            return PlayerPrefs.GetFloat(FuelKeyPrefix + code, FuelMax);
        }

        /// <summary>Riempie il serbatoio (ai distributori o con toolkit).</summary>
        public void Refuel(float amount)
        {
            _fuel = Mathf.Min(_fuel + amount, FuelMax);
            SaveFuel();
            OsmDiag.Log("[Vehicle] Refuel +" + amount.ToString("F0") +
                "L -> " + _fuel.ToString("F0") + "/" + FuelMax);
        }

        /// <summary>Il serbatoio e' vuoto.</summary>
        public bool IsOutOfFuel { get { return _fuel <= 0f; } }

        /// <summary>
        /// L'auto parte solo con un filo di vita, SENZA danno grave e CON
        /// almeno un filo di benzina.
        /// </summary>
        public bool CanStart()
        {
            if (Damage == VehicleDamage.Wrecked) return false;
            if (Damage == VehicleDamage.Fire) return false;
            if (Integrity <= 0f) return false;
            if (_fuel <= 0f) return false;
            return ConditionPercent > 0.5f;
        }

        /// <summary>Chiamato da VehicleOwnershipApi quando spawn/stato nuovo.</summary>
        public void SetServiceState(float condition, long odometerM,
            VehicleDamage damage = VehicleDamage.None)
        {
            baseCondition = Mathf.Clamp(condition, 0f, 100f);
            storedOdometer = System.Math.Max(odometerM, StoredOdometer(vehicleCode));
            baseOdometer = storedOdometer;
            if (vehicleCode.Length > 0)
                PlayerPrefs.SetString(OdoKeyPrefix + vehicleCode,
                    storedOdometer.ToString());
            if (Damage != damage)
            {
                Damage = damage;
                ApplyDamageVisual();
            }
        }

        /// <summary>Odometro totale salvato per un codice veicolo.</summary>
        public static long StoredOdometer(string code)
        {
            if (string.IsNullOrEmpty(code)) return 0L;
            long v = 0L;
            long.TryParse(PlayerPrefs.GetString(OdoKeyPrefix + code, ""), out v);
            return v;
        }

        /// <summary>
        /// Applica HP + danno per zona (dal server o dopo una riparazione).
        /// </summary>
        public void SetDamageState(float integrity,
            System.Collections.Generic.IEnumerable<System.Collections.Generic.
                KeyValuePair<string, float>> zones)
        {
            Integrity = Mathf.Clamp(integrity, 0f, 100f);
            var keys = new System.Collections.Generic.List<string>(ZoneDamage.Keys);
            foreach (var k in keys) ZoneDamage[k] = 0f;
            if (zones != null)
                foreach (var kv in zones)
                    if (ZoneDamage.ContainsKey(kv.Key))
                        ZoneDamage[kv.Key] = Mathf.Clamp(kv.Value, 0f, 100f);
            if (Integrity <= 0f && Damage != VehicleDamage.Fire &&
                Damage != VehicleDamage.Wrecked)
                Damage = VehicleDamage.Wrecked;
            ApplyDamageVisual();
        }

        private void Awake()
        {
            rb = GetComponent<Rigidbody>();
            if (rb == null)
            {
                rb = gameObject.AddComponent<Rigidbody>();
                rb.mass = 1200f;
                rb.drag = 0f;
                rb.angularDrag = 3f;
                rb.interpolation = RigidbodyInterpolation.Interpolate;
                rb.collisionDetectionMode = CollisionDetectionMode.ContinuousDynamic;
            }
            rb.isKinematic = true;
            spinner = gameObject.GetComponent<WheelSpinner>();
        }

        private void OnDestroy()
        {
            if (damageMat != null) Destroy(damageMat);
        }

        public bool StartDriving()
        {
            // niente partenza da incidentata/in fiamme: ragionaci prima
            if (Damage == VehicleDamage.Wrecked || Damage == VehicleDamage.Fire)
                return false;

            IsDriving = true;
            rb.isKinematic = false;
            currentSpeed = 0f;
            sessionMeters = 0f;
            _fuelSessionMeters = 0f;
            nextPing = PingIntervalSec;
            City.Environment.EventBus.Publish(new City.Environment.VehicleEnteredEvent(true));


            var vi = GetComponentInChildren<VehicleInteract>();
            vehicleCode = vi != null ? vi.vehicleCode : "";
            // carburante persistito per veicolo
            _fuel = StoredFuel(vehicleCode);
            // carburante persistito per veicolo
            storedOdometer = StoredOdometer(vehicleCode);
            baseOdometer = storedOdometer;
            if (myStateApi != null)
                myStateApi.SyncBaseState(vehicleCode, this);
            OsmDiag.Log("[Audit] Inizio guida: " + (data != null ? data.vehicleName : name) +
                " @" + transform.position.x.ToString("F1") + "," +
                transform.position.z.ToString("F1") + " y=" +
                transform.position.y.ToString("F2") + " dem=" +
                TileElevation.HeightAtWorld(transform.position).ToString("F2"));

            if (IsEmergency) EnsureBeacons();
            return true;
        }

        public void StopDriving()
        {
            FlushOdometer();
            IsDriving = false;
            SaveFuel();
            rb.velocity = Vector3.zero;
            rb.angularVelocity = Vector3.zero;
            rb.isKinematic = true;
            currentSpeed = 0f;
            throttleInput = 0f;
            steerInput = 0f;
            DestroyBeacons();
            City.Environment.EventBus.Publish(new City.Environment.VehicleEnteredEvent(false));
            OsmDiag.Log("[Audit] Fine guida: " + (data != null ? data.vehicleName : name) +
                " carburante=" + _fuel.ToString("F0") + "/" + FuelMax +
                " @" + transform.position.x.ToString("F1") + "," +
                transform.position.z.ToString("F1") + " y=" +
                transform.position.y.ToString("F2") + " dem=" +
                TileElevation.HeightAtWorld(transform.position).ToString("F2"));
        }

        // ── clacson, luci e lampeggianti emergenza ────────────────

        /// <summary>Veicolo di servizio (polizia/ambulanza/vigili del fuoco):
        /// attiva i lampeggianti durante la guida (soprattutto nei lavori).</summary>
        public bool IsEmergency
        {
            get
            {
                return vehicleCode == "police" || vehicleCode == "ambulance" ||
                       vehicleCode == "firetruck";
            }
        }

        public void ToggleHeadlights()
        {
            HeadlightsOn = !HeadlightsOn;
            ApplyHeadlights();
        }

        public void Honk()
        {
            var src = GetComponent<AudioSource>();
            if (src == null)
            {
                src = gameObject.AddComponent<AudioSource>();
                src.playOnAwake = false;
                src.spatialBlend = 1f;
                src.volume = 0.4f;
                src.maxDistance = 40f;
            }
            if (honkClip == null) honkClip = BuildHonkClip();
            src.PlayOneShot(honkClip, 1f);
        }

        private static AudioClip BuildHonkClip()
        {
            const int sr = 22050;
            const float dur = 0.28f;
            int n = Mathf.Max(1, Mathf.RoundToInt(sr * dur));
            var samples = new float[n];
            for (int i = 0; i < n; i++)
            {
                float t = i / (float)sr;
                float env = Mathf.Clamp01(1f - Mathf.Abs(t - dur * 0.5f) * (4f / dur));
                float sig = Mathf.Sign(Mathf.Sin(Mathf.PI * 2f * 430f * t));
                samples[i] = sig * env * 0.5f;
            }
            var clip = AudioClip.Create("Honk", n, 1, sr, false);
            clip.SetData(samples, 0);
            return clip;
        }

        private void ApplyHeadlights()
        {
            var head = FindChildByName(transform, "headlight");
            var tail = FindChildByName(transform, "taillight");
            if (head != null) head.gameObject.SetActive(HeadlightsOn);
            if (tail != null) tail.gameObject.SetActive(HeadlightsOn);
        }

        private static Transform FindChildByName(Transform root, string namePart)
        {
            for (int i = 0; i < root.childCount; i++)
            {
                Transform c = root.GetChild(i);
                if (c.name.IndexOf(namePart, System.StringComparison.OrdinalIgnoreCase) >= 0)
                    return c;
                Transform deep = FindChildByName(c, namePart);
                if (deep != null) return deep;
            }
            return null;
        }

        private void EnsureBeacons()
        {
            if (beaconRoot != null) return;
            beaconRoot = new GameObject("EmergencyBeacons");
            beaconRoot.transform.SetParent(transform, false);
            beacon1 = MakeBeacon("BeaconRosso",
                new Vector3(-0.9f, 3.2f, 0.5f), new Color(1f, 0.1f, 0.1f));
            beacon2 = MakeBeacon("BeaconBlu",
                new Vector3(0.9f, 3.2f, 0.5f), new Color(0.15f, 0.35f, 1f));
        }

        private Light MakeBeacon(string name, Vector3 localPos, Color color)
        {
            var o = new GameObject(name);
            o.transform.SetParent(beaconRoot.transform, false);
            o.transform.localPosition = localPos;
            var l = o.AddComponent<Light>();
            l.type = LightType.Point;
            l.color = color;
            l.range = 14f;
            l.intensity = 0f;
            return l;
        }

        private void DestroyBeacons()
        {
            if (beaconRoot != null)
            {
                Destroy(beaconRoot);
                beaconRoot = null;
                beacon1 = beacon2 = null;
            }
        }

        private void UpdateBeacons()
        {
            if (beaconRoot == null || !IsDriving) return;
            beaconTimer += Time.fixedDeltaTime;
            if (beaconTimer < BeaconBlinkInterval) return;
            beaconTimer = 0f;
            beaconPhase = !beaconPhase;
            if (beacon1 != null) beacon1.intensity = beaconPhase ? 5f : 0f;
            if (beacon2 != null) beacon2.intensity = beaconPhase ? 0f : 5f;
        }

        private VehicleOwnershipApi myStateApi
        {
            get { return VehicleOwnershipApi.Instance; }
        }

        /// <summary>Sincronizza il totale col server e salva in locale.</summary>
        private void FlushOdometer()
        {
            long total = TotalOdometerM;
            if (vehicleCode.Length > 0)
            {
                PlayerPrefs.SetString(OdoKeyPrefix + vehicleCode, total.ToString());
                PlayerPrefs.Save();
            }
            if (!IsDriving || total <= baseOdometer) { return; }
            if (myStateApi == null || vehicleCode.Length == 0) return;
            myStateApi.DrivePing(vehicleCode, total, ConditionPercent);
            storedOdometer = total;
            sessionMeters = 0f;
            baseOdometer = total;
        }

        public void SetInput(float throttle, float steer, bool brake)
        {
            throttleInput = throttle;
            steerInput = steer;
            braking = brake;
        }

        private void FixedUpdate()
        {
            if (!IsDriving || data == null) return;
            UpdateBeacons();

            float condFactor = 0.55f + 0.45f * (ConditionPercent / 100f);

            // ── effetti meccanici per singola parte ──
            // motore rotto → potenza quasi nulla; cambio rotto → niente marcia;
            // giunto/diff rotti → perdita trazione; gomma scoppiata → limpa.
            bool engineBroken = damageSystem != null &&
                (damageSystem.HasBrokenPart(VehiclePartType.Engine) ||
                 damageSystem.HasBrokenPart(VehiclePartType.Gearbox));
            float engEff = PartEffect(VehiclePartType.Engine)
                         * PartEffect(VehiclePartType.Gearbox)
                         * PartEffect(VehiclePartType.Driveshaft)
                         * PartEffect(VehiclePartType.Differential);
            if (damageSystem != null && damageSystem.HasBrokenPart(VehiclePartType.Gearbox))
                engEff = 0f;
            float tireFactor = 0.5f * (EffectiveGripFront() + EffectiveGripRear());

            float accel = data.acceleration * condFactor * engEff;
            float baseMax = data.maxSpeed * condFactor * Mathf.Clamp01(engEff + 0.2f);
            // il tenore di velocita' massima scala anche con l'aderenza (gonfia)
            baseMax *= Mathf.Clamp01(tireFactor * 0.9f + 0.3f);
            bool limpByPart = damageSystem != null &&
                (damageSystem.HasBrokenPart(VehiclePartType.Tire) ||
                 damageSystem.HasBrokenPart(VehiclePartType.Suspension));
            float maxSpd = (FlatTire || SuspensionDead || limpByPart)
                            ? Mathf.Min(LimpSpeed, baseMax)
                            : baseMax;

            // Incidentata / in fiamme: motore spento, auto ferma (di norma
            // siamo gia' stati staccati dall'urto con un prompt)
            if (Damage == VehicleDamage.Wrecked || Damage == VehicleDamage.Fire)
            {
                currentSpeed = 0f;
                rb.velocity = new Vector3(0f, rb.velocity.y, 0f);
                return;
            }

            if (braking)
            {
                // frenata scalata dagli eventuali freni rotti (asiimmetrica → sbanda)
                float brakeEff = PartEffect(VehiclePartType.Brake);
                float brakeVal = data.brakeForce * brakeEff * Time.fixedDeltaTime;
                if (brakeEff < 1f && currentSpeed > 0.3f)
                {
                    // freni asimmetrici: tende a sbandare sul lato con freno rotto
                    float pull = (1f - brakeEff) * 10f * Time.fixedDeltaTime;
                    if (HasBrokenBrakeLeft()) pull = -pull; // raw
                    transform.Rotate(0f, pull * 30f, 0f);
                }
                currentSpeed = Mathf.MoveTowards(currentSpeed, 0f, brakeVal);
            }
            else
            {
                float target = throttleInput * maxSpd;
                currentSpeed = Mathf.MoveTowards(currentSpeed, target, accel * Time.fixedDeltaTime);
            }

            // ── sterzo sensibile alla velocita' (guidabilita') ──
            // Prima l'angolo raggiungeva il massimo a soli 3 m/s e usava il
            // valore pieno di turnSpeed (70-130 deg/s): a velocita' l'auto
            // si strappava e diventava difficilissima da tenere in strada.
            // Ora:
            //   • serve un po' di velocita' per iniziare a girare (parking)
            //   • piu' si va forte, piu' l'angolo di sterzo si restringe
            //   • il tutto e' comunque limitato (MaxYawPerSec) per stabilita'
            float speedAbs = Mathf.Abs(currentSpeed);
            float highSpeedFactor = 1f /
                (1f + speedAbs * SteeringHighSpeedDamp);
            float speedFactor = Mathf.Clamp01(speedAbs / SteeringMinSpeed)
                                * highSpeedFactor;
            float yawRate = steerInput * data.turnSpeed * speedFactor
                            * PartEffect(VehiclePartType.Steering);
            // sospensioni/ammortizzatori a terra → scarsa tenuta in curva
            float gripEff = 0.5f * (PartEffect(VehiclePartType.Suspension) +
                                    PartEffect(VehiclePartType.ShockAbsorber));
            yawRate *= Mathf.Clamp01(gripEff * 0.8f + 0.2f);
            yawRate = Mathf.Clamp(yawRate, -MaxYawPerSec, MaxYawPerSec);
            float turn = yawRate * Time.fixedDeltaTime;
            if (FlatTire && speedAbs > 0.5f)
                turn += 12f * Time.fixedDeltaTime;
            // gomma scoppiata per parte: trazione in un verso
            if (HasWeakTire() && speedAbs > 0.5f)
                turn += 6f * Time.fixedDeltaTime;
            // Specchia lo sterzo in retromarcia: andando all'indietro il
            // muso ruota in un verso ma chi guida guarda dove va la coda.
            // Senza il segno inverso il joystick a destra = curva a sinistra
            // quando si va in R (come trattare lo sterzo avanti all'indietro).
            if (currentSpeed < 0f) turn = -turn;
            transform.Rotate(0f, turn, 0f);

            Vector3 vel = transform.forward * currentSpeed;
            vel.y = rb.velocity.y;
            rb.velocity = vel;
            rb.velocity *= (1f - data.drag * Time.fixedDeltaTime);

            // mantiene l'auto allineata all'up (niente accappottamento)
            StabilizeAgainstFlip();

            if (spinner != null) spinner.Spin(currentSpeed);

            sessionMeters += Mathf.Abs(currentSpeed) * Time.fixedDeltaTime;
            _fuelSessionMeters += Mathf.Abs(currentSpeed) * Time.fixedDeltaTime;

            // ── consumo carburante ──
            // Ogni km percorso consuma FuelConsumptionPerKm litri.
            // A 0 il motore si spegne (velocita -> 0).
            if (_fuel > 0f && _fuelSessionMeters >= 1000f)
            {
                float km = _fuelSessionMeters / 1000f;
                _fuel -= km * FuelConsumptionPerKm;
                _fuelSessionMeters %= 1000f;
                if (_fuel <= 0f)
                {
                    _fuel = 0f;
                    currentSpeed = 0f;
                    rb.velocity = new Vector3(0f, rb.velocity.y, 0f);
                    OsmDiag.Log("[Vehicle] SERBATOIO VUOTO: " +
                        (data != null ? data.vehicleName : name));
                    City.Environment.EventBus.Publish(new City.Environment.FuelLowEvent(0f));
                    if (Game.Instance != null && Game.Instance.ui != null)
                        Game.Instance.ui.ShowToast("Serbatoio vuoto! Vai al distributore.");
                }
                SaveFuel();
            }
            if (Time.time >= nextPing)
            {
                nextPing = Time.time + PingIntervalSec;
                FlushOdometer();
            }

            // Audit quota: un veicolo in guida NON deve affondare sotto la
            // superficie reale. La mesh stradale sta a DEM+0.03/+0.12, quindi
            // y < DEM - 0.6 e' un'anomalia (asfalto sparito o auto sotto la
            // collina). I viadotti sono SOPRA il DEM: solo la sottoquota conta.
            if (Time.time >= _nextGroundAudit)
            {
                _nextGroundAudit = Time.time + 15f;
                float h = TileElevation.HeightAtWorld(transform.position);
                if (transform.position.y < h - 0.6f)
                    OsmDiag.Log("[Audit] Veicolo sotto la quota DEM: " +
                        (data != null ? data.vehicleName : name) +
                        " y=" + transform.position.y.ToString("F2") +
                        " dem=" + h.ToString("F2") + " @" +
                        transform.position.x.ToString("F1") + "," +
                        transform.position.z.ToString("F1"));
            }
        }

        private void OnCollisionEnter(Collision collision)
        {
            if (!IsDriving) return;
            // gli urti fra auto del traffico non danneggiano: solo cordoli,
            // muri ed edifici (e a velocita' significativa); ora le auto del
            // traffico hanno il collider SOLIDO, quindi anche il taxi/pullman
            // che sbatte sul giocatore non deve causare danni
            if (collision.collider.GetComponent<VehicleController>() != null) return;
            if (collision.collider.GetComponent<TrafficCar>() != null) return;
            if (!collision.collider.enabled) return;

            float speed = collision.relativeVelocity.magnitude;
            if (speed < MinImpactSpeed) return;   // niente danno per i contatti
            if (Damage == VehicleDamage.Wrecked) return;
            if (Damage == VehicleDamage.Fire) return;

            string zone = PickDamageZone(collision);
            float damage = ZoneDamagePerMs(zone) * speed;
            if (damage <= 0f) return;

            // incendio solo su impatti violentissimi e non scontati
            if (speed >= FireImpactSpeed &&
                UnityEngine.Random.Range(0f, 1f) < FireChance)
            {
                SetDamage(VehicleDamage.Fire);
                StopCarHard();
                if (City.UI.UIManager.Instance != null)
                    City.UI.UIManager.Instance.ShowToast(
                        "INCENDIO! Scendi subito e chiama i vigili del fuoco!");
                if (City.Game.Instance != null)
                    City.Game.Instance.OnVehicleCaughtFire(this);
                ReportDamageState();
                return;
            }

            // danno per singola PARTE meccanica nel punto d'impatto:
            // un urto al paraurti anteriore danneggia paraurti→radiatore→motore,
            // NON il serbatoio posteriore. Fornisce anche il messaggio "parte rotta".
            string brokenPartMsg = "";
            if (damageSystem != null)
            {
                Vector3 hitLocal = transform.InverseTransformPoint(
                    collision.contacts.Length > 0
                        ? collision.contacts[0].point
                        : transform.position);
                damageSystem.DamageAtLocalPoint(hitLocal, speed, 1.4f, 100f);
                var broken = damageSystem.GetBrokenPartNearest(hitLocal);
                if (broken != null && Damage == VehicleDamage.None &&
                    Integrity > 0f && speed >= 3f)
                    brokenPartMsg = broken.partName;
            }

            // danno graduale per zona + calo HP
            ZoneDamage[zone] = Mathf.Clamp(ZoneDamage[zone] + damage, 0f, 100f);
            float missing = 0f;
            foreach (var kv in ZoneDamage) missing += kv.Value;
            Integrity = Mathf.Clamp(100f - missing, 0f, 100f);

            if (Integrity <= 0f)
            {
                // devastata: non partira' piu', serve il carro attrezzi
                SetDamage(VehicleDamage.Wrecked);
                StopCarHard();
                if (City.UI.UIManager.Instance != null)
                    City.UI.UIManager.Instance.ShowToast(
                        "Botta violenta! L'auto e' devastata: non parte piu'. Serve il carro attrezzi.");
            }
            else if (brokenPartMsg.Length > 0)
            {
                if (City.UI.UIManager.Instance != null)
                    City.UI.UIManager.Instance.ShowToast(
                        "Danno meccanico: si e' rotto il " + brokenPartMsg +
                        " (HP " + Mathf.RoundToInt(Integrity) + "%). Fatto controllare in officina.");
            }
            else if (InspireLimp(zone))
            {
                if (City.UI.UIManager.Instance != null)
                    City.UI.UIManager.Instance.ShowToast(
                        "Sospensioni a terra: l'auto zoppica. Vai piano fino all'officina, o chiama il carro attrezzi.");
            }
            else if (damage >= 8f)
            {
                if (City.UI.UIManager.Instance != null)
                    City.UI.UIManager.Instance.ShowToast(
                        "Impatto: danno alla " + ZoneLabel(zone) + " (HP " +
                        Mathf.RoundToInt(Integrity) + "%).");
            }
            // rende visibile il danno graduale (alone sui pannelli)
            if (Damage == VehicleDamage.None && fxRoot == null)
                ApplyDamageVisual();
            ReportDamageState();
        }

        private static string ZoneLabel(string zone)
        {
            if (zone == "suspension") return "sospensioni";
            if (zone == "bodywork") return "carrozzeria";
            if (zone == "bumper") return "fascia/paraurti";
            return "carrozzeria";
        }

        private bool InspireLimp(string zone)
        {
            return zone == "suspension" && SuspensionDead &&
                   Damage != VehicleDamage.Flat;
        }

        /// <summary>Determina la zona di danno in base all'oggetto colpito e
        /// alla direzione dell'urto. Marciapiede/erba = sospensioni (danno
        /// minimo ma cumulativo); frontale ad alta velocita' = paraurti;
        /// qualsiasi altro impatto laterale = carrozzeria.</summary>
        private string PickDamageZone(Collision collision)
        {
            GameObject go = collision.collider.gameObject;
            string name = go != null ? go.name : "";
            // superfici morbide/rialzi del suolo → sospensioni
            if (name.IndexOf("Terreno", System.StringComparison.OrdinalIgnoreCase) >= 0 ||
                name.IndexOf("Marciapiedi", System.StringComparison.OrdinalIgnoreCase) >= 0 ||
                name.IndexOf("Natura", System.StringComparison.OrdinalIgnoreCase) >= 0 ||
                name.IndexOf("Erba", System.StringComparison.OrdinalIgnoreCase) >= 0 ||
                name.IndexOf("Pavim", System.StringComparison.OrdinalIgnoreCase) >= 0 ||
                name.IndexOf("Parcheggio", System.StringComparison.OrdinalIgnoreCase) >= 0)
                return "suspension";
            // urto frontale se la velocita' relativa e' proiettata in avanti
            Vector3 rel = collision.relativeVelocity;
            if (rel.sqrMagnitude > 0.01f &&
                Vector3.Dot(rel.normalized, transform.forward) >= 0.5f)
                return "bumper";
            return "bodywork";
        }

        private static float ZoneDamagePerMs(string zone)
        {
            if (zone == "suspension") return CurbDamagePerMs;
            if (zone == "bumper") return FrontDamagePerMs;
            return BodyDamagePerMs;
        }

        private void ReportDamageState()
        {
            if (vehicleCode.Length > 0 && myStateApi != null)
                myStateApi.ReportDamage(vehicleCode, Damage, Integrity, ZoneDamage, null);
        }

        /// <summary>
        /// Riequilibra il telaio verso la verticale conservando la
        /// direzione di marcia (yaw). Un ammortizzatore virtuale che:
        ///   • a telaio quasi dritto interviene dolcemente (effetto
        ///     ammortizzatori reali: si sente, non ribalta);
        ///   • a telaio molto inclinato riporta su con decisione, così
        ///     urtare un marciapiede NON accappotta l'auto.
        /// Smorza inoltre il rollio/pitch fisico residuo lasciato dal
        /// Rigidbody nelle collisioni.
        /// </summary>
        private void StabilizeAgainstFlip()
        {
            if (rb == null) return;
            float dot = Vector3.Dot(transform.up, Vector3.up);
            // forza piu' decisa quanto piu' l'auto e' inclinata
            float t = Mathf.Lerp(1f, 0f, Mathf.Clamp01((dot + 1f) * 0.5f));
            float strength = Mathf.Lerp(StabilizeStrengthSoft,
                                        StabilizeStrengthStrong, t);
            Vector3 forward = transform.forward;
            if (forward.sqrMagnitude < 0.001f) forward = Vector3.forward;
            // Up di appoggio = pendenza DEM limitata (sospensioni reali):
            // l'auto si inclina seguendo il dosso, ma al massimo di MaxInclineDeg.
            Vector3 groundUp = TileElevation.SlopeUpAtWorld(
                transform.position, MaxInclineDeg);
            Quaternion target = Quaternion.LookRotation(forward, groundUp);
            transform.rotation = Quaternion.Slerp(transform.rotation, target,
                Mathf.Clamp01(strength * Time.fixedDeltaTime));

            // smorza roll/pitch residuo lasciato dalle collisioni fisiche
            Vector3 av = rb.angularVelocity;
            if (av.sqrMagnitude > 0.0001f)
            {
                av.x *= 1f - SuspensionDamping * Time.fixedDeltaTime;
                av.z *= 1f - SuspensionDamping * Time.fixedDeltaTime;
                rb.angularVelocity = av;
            }
        }

        private void StopCarHard()
        {
            currentSpeed = 0f;
            throttleInput = 0f;
            steerInput = 0f;
            rb.velocity = Vector3.zero;
            rb.angularVelocity = Vector3.zero;
        }

        /// <summary>
        /// Imposta il danno (da urto o dall'officina) e ridisegna gli FX.
        /// </summary>
        public void SetDamage(VehicleDamage d)
        {
            if (Damage == d) return;
            Damage = d;
            ApplyDamageVisual();
        }

        /// <summary>Acqua dei pompieri: spegne gradualmente i lampi
        /// (amount 1 = totalmente spenta). La rimozione degli FX avviene
        /// poi con SetDamage(Wrecked).</summary>
        public void QuenchFire(float amount)
        {
            amount = Mathf.Clamp01(amount);
            if (flameLight != null)
                flameLight.intensity = Mathf.Lerp(2.2f, 0f, amount);
            if (flameMat != null)
            {
                Color c = flameMat.color;
                c.a = c.a * (1f - amount);
                flameMat.color = c;
            }
        }

        public float GetCurrentSpeedKmh()
        {
            return Mathf.Abs(currentSpeed) * 3.6f;
        }

        // ── helper per gli effetti delle parti meccaniche ─────────

        /// <summary>Aderenza media delle 2 gomme anteriori (0..1).</summary>
        private float EffectiveGripFront()
        {
            if (damageSystem == null) return 1f;
            var tires = damageSystem.GetParts(VehiclePartType.Tire);
            int n = 0; float sum = 0f;
            for (int i = 0; i < tires.Count && i < 2; i++) { sum += tires[i].IntegrityFactor; n++; }
            return n > 0 ? sum / n : 1f;
        }

        /// <summary>Aderenza media delle gomme posteriori (0..1).</summary>
        private float EffectiveGripRear()
        {
            if (damageSystem == null) return 1f;
            var tires = damageSystem.GetParts(VehiclePartType.Tire);
            int n = 0; float sum = 0f;
            for (int i = 2; i < tires.Count; i++) { sum += tires[i].IntegrityFactor; n++; }
            return n > 0 ? sum / n : 1f;
        }

        /// <summary>True se una gomma posteriore sinistra è rotta (per il
        /// tiro del freno asimmetrico semplificato).</summary>
        private bool HasBrokenBrakeLeft()
        {
            if (damageSystem == null) return false;
            var brakes = damageSystem.GetParts(VehiclePartType.Brake);
            return brakes.Count >= 2 && brakes[1].IsBroken;
        }

        /// <summary>True se almeno una gomma è rotta (scoppio per parte).</summary>
        private bool HasWeakTire()
        {
            if (damageSystem == null) return false;
            return damageSystem.HasBrokenPart(VehiclePartType.Tire);
        }

        /// <summary>La parte meccanica rotta più vicina a un punto (per
        /// messaggi d'urto tipo "S'è rotto il motore!").</summary>
        public string NearestBrokenPartName(Vector3 hitLocal)
        {
            if (damageSystem == null) return "";
            var p = damageSystem.GetBrokenPartNearest(hitLocal);
            return p != null ? p.partName : "";
        }

        /// <summary>Ripara completamente la parte di un dato tipo e indice.</summary>
        public void RepairPartAt(VehiclePartType type, int index)
        {
            if (damageSystem != null) damageSystem.RepairPart(type, index, 100f);
        }

        /// <summary>Ripara tutte le parti meccaniche locali.</summary>
        public void RepairAllParts()
        {
            if (damageSystem != null) damageSystem.RepairAll();
        }

        /// <summary>Ripara le parti meccaniche corrispondenti a una zona
        /// riparata dall'officina (mantiene coerente lo strato fine).</summary>
        public void RepairPartsForZone(string zone)
        {
            if (damageSystem == null) return;
            // mappa zona ufficina → gruppi di parti: ripara SOLO questi tipi
            if (zone == "suspension")
                RepairTypes(new[] { VehiclePartType.Suspension,
                    VehiclePartType.ShockAbsorber, VehiclePartType.Tire });
            else if (zone == "bumper")
                RepairTypes(new[] { VehiclePartType.Bumper,
                    VehiclePartType.Radiator, VehiclePartType.Engine,
                    VehiclePartType.Fuel });
            else if (zone == "bodywork")
                RepairTypes(new[] { VehiclePartType.Bodywork,
                    VehiclePartType.Chassis });
        }

        private void RepairTypes(VehiclePartType[] types)
        {
            for (int t = 0; t < types.Length; t++)
            {
                var list = damageSystem.GetParts(types[t]);
                for (int i = 0; i < list.Count; i++)
                    damageSystem.RepairPart(types[t], i, 100f);
            }
        }


        // ── effetti visivi danni ───────────────────────────────────

        private void ApplyDamageVisual()
        {
            RemoveDamageFx();
            bool anyDamage = Damage != VehicleDamage.None || Integrity < 100f;
            if (!anyDamage) return;

            fxRoot = new GameObject("DamageFx");
            fxRoot.transform.SetParent(transform, false);
            fxRoot.transform.localPosition = Damage == VehicleDamage.Fire
                ? new Vector3(0f, 0.9f, 0.2f)
                : new Vector3(0f, 1.0f, 0.8f);
            damageMat = CreateFxMat(Damage == VehicleDamage.Fire
                ? new Color(0.18f, 0.18f, 0.18f, 0.6f)
                : new Color(0.24f, 0.24f, 0.24f, 0.55f));
            MakeQuad(new Vector3(0f, 1.2f, 0.7f), damageMat);
            if (Damage != VehicleDamage.Flat)
            {
                MakeQuad(new Vector3(0.4f, 1.4f, 0.5f), damageMat);
                MakeQuad(new Vector3(1.0f, 1.5f, 0.8f), damageMat);
            }

            if (Damage == VehicleDamage.Fire)
            {
                var flameMat = CreateFxMat(new Color(1f, 0.45f, 0.05f, 0.95f));
                MakeQuad(new Vector3(0f, 0.55f, 0f), flameMat);
                MakeQuad(new Vector3(0.35f, 0.85f, 0.3f), flameMat);
                MakeQuad(new Vector3(-0.35f, 0.8f, 0.1f), flameMat);
                AddFlameQuadsRegister(flameMat);

                var lgo = new GameObject("FireLight");
                lgo.transform.SetParent(fxRoot.transform, false);
                lgo.transform.localPosition = new Vector3(0f, 1.4f, 0.4f);
                flameLight = lgo.AddComponent<Light>();
                flameLight.type = LightType.Point;
                flameLight.color = new Color(1f, 0.5f, 0.1f);
                flameLight.intensity = 2.2f;
                flameLight.range = 10f;
            }
        }

        private Material flameMat;
        private readonly System.Collections.Generic.List<Renderer>
            damageRenderers = new System.Collections.Generic.List<Renderer>();

        private void MakeQuad(Vector3 localPos, Material mat)
        {
            var q = GameObject.CreatePrimitive(PrimitiveType.Quad);
            Object.Destroy(q.GetComponent<Collider>());
            q.name = "fx";
            q.transform.SetParent(fxRoot.transform, false);
            q.transform.localPosition = localPos;
            q.transform.localScale = new Vector3(0.42f, 0.42f, 1f);
            q.transform.localRotation = Damage == VehicleDamage.Fire
                ? Quaternion.Euler(UnityEngine.Random.Range(-25f, 25f), 0f, 0f)
                : Quaternion.identity;
            var r = q.GetComponent<Renderer>();
            if (r != null)
            {
                r.sharedMaterial = mat;
                damageRenderers.Add(r);
            }
        }

        private void AddFlameQuadsRegister(Material flameMat)
        {
            this.flameMat = flameMat;
        }

        private static Material CreateFxMat(Color c)
        {
            var shader = Shader.Find("Sprites/Default");
            if (shader == null) shader = Shader.Find("Universal Render Pipeline/Unlit");
            if (shader == null) shader = Shader.Find("Standard");
            var mat = new Material(shader);
            mat.color = c;
            return mat;
        }

        private void RemoveDamageFx()
        {
            if (fxRoot != null) Destroy(fxRoot);
            fxRoot = null;
            flameLight = null;
            damageMat = null;
            flameMat = null;
            damageRenderers.Clear();
        }

        private void Update()
        {
            if (fxRoot == null) return;
            fxTime += Time.deltaTime;
            float flick = Mathf.Sin(fxTime * 11f) * 0.5f + 0.5f;

            if (damageMat != null)
            {
                Color c = damageMat.color;
                if (Damage == VehicleDamage.Fire)
                {
                    c.a = 0.55f + flick * 0.2f;
                }
                else
                {
                    // l'alone scuro scala col danno subito (HP mancante),
                    // cosi' il danno graduale si vede senza stati discreti
                    float missing = Mathf.Clamp01((100f - Integrity) / 100f);
                    float baseA = Damage == VehicleDamage.None ? 0.16f : 0.30f;
                    c.a = Mathf.Clamp01(baseA * missing + flick * 0.12f);
                }
                damageMat.color = c;
            }
            if (flameMat != null)
            {
                Color c = flameMat.color;
                c.a = 0.75f + flick * 0.25f;
                flameMat.color = c;
            }
            if (flameLight != null)
                flameLight.intensity = 1.8f + flick * 1.2f;
        }
    }
}
