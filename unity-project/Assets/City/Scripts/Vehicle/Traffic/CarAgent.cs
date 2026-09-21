using System.Collections.Generic;
using UnityEngine;
using City.Player;
using City.NPC;
using City.OSM;

namespace City.Vehicle.Traffic
{
    public enum CarAgentState
    {
        Idle,
        Driving,
        Parked,
        OffMap,
        Punctured   // gomma a terra contro un cordolo: ferma sul posto
    }

    public class CarAgent : MonoBehaviour
    {
        public CarAgentState state = CarAgentState.Idle;
        public float maxSpeed = 10f;
        public float currentSpeed;
        public float acceleration = 6f;
        public float deceleration = 8f;
        public float turnSpeed = 120f;

        private Vector3[] _waypoints;
        private int _wpIndex;
        private float _stopTimer;
        private CarAgent _leader;
        private float _leaderDist;
        public bool looping;

        // ── Cordoli (Fase 7): il marciapiede rialzato e' un ostacolo reale ──
        // Sonda frontale contro le mesh dei marciapiedi ("Marciapiedi",
        // create da ChunkBuilder con MeshCollider): se l'auto taglia la
        // curva sopra il cordolo mentre avanza, buca una gomma e resta
        // ferma dove si e' fermata (come richiesto: mai sui marciapiedi).
        public bool FlatTire { get; private set; }
        private const float CurbProbeDist = 2.1f;    // asse anteriore (~metà auto)
        private const float CurbProbeRadius = 0.35f;
        private const float CurbProbeY = 0.17f;      // piano marciapiede = 0.12
        private const float PunctureMinSpeed = 0.8f; // sotto questa velocita' si ferma senza bucare
        private static readonly Collider[] _curbBuf = new Collider[8];

        // ── Regole stradali (Fase 8): semafori, precedenze, limiti ──
        private float[] _segLimits;                       // limite m/s per waypoint
        private TrafficGate[] _gates;     // incroci sul percorso
        private int _gateScan;
        private int _grantedNode = -1;                    // incrocio concesso
        private bool _waiting;                            // fermo alla linea
        private Vector3 _stopTarget;
        private float _waitStart;                         // quando ho iniziato ad attendere
        private bool _stuckLogged;                        // gia' segnalato fermo all'incrocio
        private float _nextOffRoadLog;                    // soglia audit fuori corsia

        private static readonly string[] CarPrefabs = new string[]
        {
            "Vehicles/sedan", "Vehicles/suv", "Vehicles/van",
            "Vehicles/taxi", "Vehicles/hatchback-sports", "Vehicles/delivery",
        };
        private static GameObject[] _prefabs;
        private static bool _prefabsLoaded;

        public static void PreloadPrefabs()
        {
            if (_prefabsLoaded) return;
            _prefabs = new GameObject[CarPrefabs.Length];
            for (int i = 0; i < CarPrefabs.Length; i++)
                _prefabs[i] = Resources.Load<GameObject>(CarPrefabs[i]);
            _prefabsLoaded = true;
        }

        public void Init(Vector3[] waypoints, float speed, int colorSeed,
            float[] segLimits = null, TrafficGate[] gates = null)
        {
            _waypoints = waypoints;
            _wpIndex = 0;
            maxSpeed = speed;
            currentSpeed = speed * 0.5f;
            state = CarAgentState.Driving;
            _leader = null;
            _segLimits = segLimits;
            _gates = gates;
            _gateScan = 0;
            _grantedNode = -1;
            _waiting = false;

            if (waypoints.Length > 0)
            {
                transform.position = waypoints[0];
                if (waypoints.Length > 1)
                {
                    Vector3 dir = (waypoints[1] - waypoints[0]).normalized;
                    if (dir.sqrMagnitude > 0.01f)
                        transform.rotation = Quaternion.LookRotation(dir, Vector3.up);
                }
            }

            BuildModel(colorSeed);
        }

        public void SetLeader(CarAgent leader, float distance)
        {
            _leader = leader;
            _leaderDist = distance;
        }

        public void ClearLeader()
        {
            _leader = null;
        }

        public Vector3 GetPosition() => transform.position;

        public float GetLeaderDistance() => _leaderDist;

        private void Update()
        {
            if (state == CarAgentState.Punctured)
            {
                // ferma definitivamente: ostacolo naturale per chi segue
                currentSpeed = 0f;
                return;
            }
            if (state != CarAgentState.Driving) return;
            if (_waypoints == null || _waypoints.Length < 2) return;

            AdvanceWaypoint();
            FollowLeader();
            EvaluateGates();
            CheckCurb();
            CheckObstacle();
            Drive();
            AuditGateStall();
            AuditRoadBounds();
        }

        // ── audit ────────────────────────────────────────────────

        // Auto ferma al gate per piu' di 15s senza permesso: possibile
        // incrocio bloccato (JunctionControl mai rilasciato). Normale per un
        // semaforo rosso lungo, quindi soglia alta + throttle di 60s.
        private void AuditGateStall()
        {
            if (!_waiting || _stuckLogged || _grantedNode >= 0) return;
            if (Time.time - _waitStart < 15f) return;
            _stuckLogged = true;
            if (_gates == null || _gateScan >= _gates.Length) return;
            OsmDiag.Log("[Audit] Auto ferma all'incrocio " + _gates[_gateScan].nodeId +
                " da " + (Time.time - _waitStart).ToString("F0") + "s " + name +
                " @" + transform.position.x.ToString("F1") + "," +
                transform.position.z.ToString("F1"));
        }

        // Auto oltre ~8 m dalla linea di waypoint corrente: il percorso sta
        // "tagliando" fuori strada (marciapiede/erba/attraverso i blocchi).
        // La stringa si costruisce solo quando scatta la soglia (15s).
        private void AuditRoadBounds()
        {
            if (state != CarAgentState.Driving) return;
            if (Time.time < _nextOffRoadLog) return;
            if (_waypoints == null || _wpIndex < 1 || _wpIndex >= _waypoints.Length)
                return;
            Vector3 a = _waypoints[_wpIndex - 1]; a.y = 0f;
            Vector3 b = _waypoints[_wpIndex]; b.y = 0f;
            Vector3 ab = b - a;
            float len = ab.magnitude;
            if (len < 0.001f) return;
            Vector3 dir = ab / len;
            Vector3 p = transform.position; p.y = 0f;
            float t = Mathf.Clamp01(Vector3.Dot(p - a, dir) / len);
            float dist = Vector3.Distance(p, a + dir * (t * len));
            if (dist <= 8f) return;
            if (_waiting) return;
            _nextOffRoadLog = Time.time + 15f;
            OsmDiag.Log("[Audit] Auto fuori corsia: " + name +
                " dist=" + dist.ToString("F1") + "m wp=" + _wpIndex +
                " @" + transform.position.x.ToString("F1") + "," +
                transform.position.z.ToString("F1"));
        }

        // Limite effettivo: min tra cap dell'agente e limite della strada
        // del segmento che sto per percorrere.
        private float EffectiveMax()
        {
            if (_segLimits == null || _segLimits.Length == 0) return maxSpeed;
            int idx = Mathf.Clamp(_wpIndex < _waypoints.Length ? _wpIndex : _waypoints.Length - 1,
                0, _segLimits.Length - 1);
            return Mathf.Min(maxSpeed, _segLimits[idx]);
        }

        // ── Semafori e precedenza: gestione dei gate d'incrocio ──
        private void EvaluateGates()
        {
            if (_gates == null || _gates.Length == 0 || _waypoints == null) return;

            // rilascio del permesso appena ho lasciato l'incrocio
            if (_grantedNode >= 0)
                ReleaseIfPastGate();

            while (_gateScan < _gates.Length && _gates[_gateScan].wpIndex < _wpIndex)
                _gateScan++;
            if (_gateScan >= _gates.Length) { _waiting = false; return; }

            var g = _gates[_gateScan];
            Vector3 gatePos = _waypoints[g.wpIndex];
            Vector3 toGate = gatePos - transform.position;
            toGate.y = 0f;
            if (toGate.magnitude > JunctionControl.ApproachDist)
            {
                _waiting = false;
                return;
            }

            bool go = JunctionControl.RequestGo(g.nodeId, this, g.approachDir);
            if (go)
            {
                _grantedNode = g.nodeId;
                _waiting = false;
            }
            else
            {
                if (!_waiting)
                {
                    _waitStart = Time.time;
                    _stuckLogged = false;
                }
                _waiting = true;
                Vector3 dir = g.approachDir;
                if (dir.sqrMagnitude < 0.0001f) dir = transform.forward;
                _stopTarget = gatePos - dir.normalized * JunctionControl.StopLineOffset;
            }
        }

        private void ReleaseIfPastGate()
        {
            for (int i = 0; i < _gates.Length; i++)
            {
                if (_gates[i].nodeId != _grantedNode) continue;
                int idx = _gates[i].wpIndex;
                bool pastWaypoint = _wpIndex > idx;
                bool farEnough =
                    (transform.position - _waypoints[Mathf.Clamp(idx, 0, _waypoints.Length - 1)])
                        .magnitude > JunctionControl.JunctionRadius + 3f;
                if (pastWaypoint && farEnough)
                {
                    JunctionControl.Release(_grantedNode, this);
                    _grantedNode = -1;
                    _waiting = false;
                }
                return;
            }
            _grantedNode = -1;
        }

        // Rileva il marciapiede rialzato davanti all'auto. Contatto a velocita'
        // utile -> gomma a terra e stop sull'istante; contatto quasi fermo ->
        // solo frenata (manovra, non incidente).
        private void CheckCurb()
        {
            if (FlatTire || currentSpeed <= 0.01f) return;
            Vector3 probe = transform.position + transform.forward * CurbProbeDist;
            probe.y = CurbProbeY;
            int n = Physics.OverlapSphereNonAlloc(probe, CurbProbeRadius, _curbBuf,
                ~0, QueryTriggerInteraction.Ignore);
            bool curb = false;
            for (int i = 0; i < n; i++)
            {
                var c = _curbBuf[i];
                if (c == null || !(c is MeshCollider) || !c.enabled) continue;
                if (!c.gameObject.name.StartsWith("Marciapiedi")) continue;
                curb = true;
                break;
            }
            if (!curb) return;

            if (currentSpeed >= PunctureMinSpeed)
            {
                FlatTire = true;
                state = CarAgentState.Punctured;
                currentSpeed = 0f;
                if (_grantedNode >= 0)
                {
                    JunctionControl.Release(_grantedNode, this);
                    _grantedNode = -1;
                }
                _waiting = false;
                // piccola inclinazione: l'anteriore si abbassa sulla gomma bucata
                transform.Rotate(0f, 0f, 2.5f);
                Debug.Log("[CarAgent] gomma a terra contro il cordolo: fermata su " +
                          name);
                Huntix.Bridge.UnityBridge.LogToAndroid("Traffic",
                    "Auto con gomma a terra sul cordolo");
            }
            else
            {
                currentSpeed = 0f; // frenata: riparte col prossimo ciclo leader/waypoint
            }
        }

        // Frena se il giocatore o un pedone e' davanti sulla traiettoria di
        // marcia: l'auto rallenta fino a fermarsi per non investirli, poi
        // riparte (FollowLeader) appena la via e' libera.
        private void CheckObstacle()
        {
            if (state != CarAgentState.Driving || currentSpeed <= 0.01f) return;
            Vector3 fwd = transform.forward;
            fwd.y = 0f;
            float probe = 10f;

            Transform target = null;
            if (PlayerController.Instance != null)
                target = PlayerController.Instance.transform;

            float best = probe;
            var npcs = NPCController.Active;
            for (int i = 0; i < npcs.Count; i++)
            {
                var n = npcs[i];
                if (n == null) continue;
                Vector3 toN = n.transform.position - transform.position;
                toN.y = 0f;
                float d = toN.magnitude;
                if (d > best) continue;
                if (Vector3.Dot(fwd, d > 0.001f ? toN.normalized : fwd) <= 0.3f)
                    continue;
                best = d;
                target = n.transform;
            }

            if (target == null) return;

            Vector3 toT = target.position - transform.position;
            toT.y = 0f;
            float dist = toT.magnitude;
            if (Vector3.Dot(fwd, dist > 0.001f ? toT.normalized : fwd) <= 0.3f)
                return;

            float stopAt = 2.2f;
            if (dist < probe)
            {
                float want = Mathf.Lerp(0f, currentSpeed,
                    Mathf.Clamp01((dist - stopAt) / (probe - stopAt)));
                currentSpeed = Mathf.MoveTowards(currentSpeed, want,
                    deceleration * Time.deltaTime);
            }
        }

        private void AdvanceWaypoint()
        {
            if (_wpIndex >= _waypoints.Length) return;
            Vector3 target = _waypoints[_wpIndex];
            Vector3 toTarget = target - transform.position;
            toTarget.y = 0f;

            if (toTarget.sqrMagnitude < 1f)
                _wpIndex++;
        }

        private void FollowLeader()
        {
            if (_leader == null || !_leader.gameObject.activeInHierarchy)
            {
                currentSpeed = Mathf.MoveTowards(currentSpeed, maxSpeed, acceleration * Time.deltaTime);
                return;
            }

            float dist = Vector3.Distance(transform.position, _leader.transform.position);
            _leaderDist = dist;

            float effMax = EffectiveMax();
            float safeFollow = 4f + currentSpeed * 0.3f;
            if (dist < safeFollow)
            {
                float brakeTarget = Mathf.Min(_leader.currentSpeed, effMax * 0.5f);
                currentSpeed = Mathf.MoveTowards(currentSpeed, brakeTarget, deceleration * 2f * Time.deltaTime);
            }
            else if (dist < safeFollow * 2f)
            {
                currentSpeed = Mathf.MoveTowards(currentSpeed, _leader.currentSpeed, acceleration * 0.5f * Time.deltaTime);
            }
            else
            {
                currentSpeed = Mathf.MoveTowards(currentSpeed, effMax, acceleration * Time.deltaTime);
            }
        }

        private void Drive()
        {
            if (_waiting && _grantedNode < 0)
            {
                // fermata obbligatoria alla linea (rosso/giallo o precedenza)
                Vector3 stop = _stopTarget - transform.position;
                stop.y = 0f;
                if (stop.magnitude < 0.45f)
                {
                    currentSpeed = Mathf.MoveTowards(currentSpeed, 0f, deceleration * 3f * Time.deltaTime);
                    return;
                }
                StepTowards(stop.normalized, stop.magnitude);
                return;
            }

            if (_wpIndex >= _waypoints.Length)
            {
                if (looping)
                {
                    _wpIndex = 0;
                    // nuovo giro: i gate ripartono dall'inizio del percorso
                    if (_grantedNode >= 0)
                    {
                        JunctionControl.Release(_grantedNode, this);
                        _grantedNode = -1;
                    }
                    _waiting = false;
                    _gateScan = 0;
                    return;
                }
                state = CarAgentState.Idle;
                currentSpeed = 0f;
                return;
            }

            Vector3 target = _waypoints[_wpIndex];
            Vector3 toTarget = target - transform.position;
            toTarget.y = 0f;

            if (toTarget.sqrMagnitude < 0.01f)
            {
                _wpIndex++;
                return;
            }

            Vector3 moveDir = toTarget.normalized;
            StepTowards(moveDir, toTarget.magnitude);
        }

        // Rotazione + avanzamento condiviso (waypoint o linea di arresto).
        private void StepTowards(Vector3 moveDir, float remaining)
        {
            Quaternion targetRot = Quaternion.LookRotation(moveDir, Vector3.up);
            transform.rotation = Quaternion.Slerp(transform.rotation, targetRot,
                turnSpeed * Time.deltaTime / Mathf.Max(1f, Vector3.Angle(transform.forward, moveDir)));

            float step = currentSpeed * Time.deltaTime;
            transform.position += moveDir * Mathf.Min(step, remaining);
            SnapToElevation();
        }

        // Aggancia la base alla quota DEM del terreno: le auto seguono i
        // dislivelli delle strade invece di restare a quota fissa. Sui
        // viadotti/gallerie l'aggancio DEM farebbe sprofondare l'auto nella
        // valle SOTTO il ponte: se il binario corrente viaggia "a ponte" (la
        // quota bake dei waypoint si stacca dal terreno) si segue quella retta
        // di impalcato, identica alla mesh del deck.
        private void SnapToElevation()
        {
            Vector3 p = transform.position;
            float deck = DeckHeightAt(p);
            float h = deck > float.MinValue
                ? deck : TileElevation.HeightAtWorld(p);
            transform.position = new Vector3(p.x, h, p.z);
        }

        private const float DeckOffTerrainM = 1.5f;   // oltre questo scarto dal DEM = sono sul deck
        private const float DeckLateralM = 10f;

        // Proietta la posizione sui segmenti di path intorno a _wpIndex: se i
        // due waypoint bake si discostano dal terreno di almeno
        // DeckOffTerrainM e il punto cade vicino al binario, e' un tratto di
        // impalcato -> quota = lerp dei waypoint. Niente risultato = il DEM fa
        // da piano (strade normali, anche sotto un ponte).
        private float DeckHeightAt(Vector3 p)
        {
            if (_waypoints == null || _waypoints.Length < 2) return float.MinValue;
            int lo = Mathf.Max(0, _wpIndex - 1);
            int hi = Mathf.Min(_waypoints.Length - 2, _wpIndex + 4);
            float dem = TileElevation.HeightAtWorld(p);
            for (int i = lo; i <= hi; i++)
            {
                Vector3 a = _waypoints[i]; a.y = 0f;
                Vector3 b = _waypoints[i + 1]; b.y = 0f;
                Vector3 ab = b - a;
                float len2 = ab.sqrMagnitude;
                if (len2 < 0.0001f) continue;
                Vector3 pa = p; pa.y = 0f;
                pa -= a;
                float t = Mathf.Clamp01(Vector3.Dot(pa, ab) / len2);
                Vector3 near = a + ab * t;
                Vector3 lat = p - near; lat.y = 0f;
                if (lat.sqrMagnitude > DeckLateralM * DeckLateralM) continue;
                float deckY = Mathf.Lerp(_waypoints[i].y, _waypoints[i + 1].y, t);
                if (Mathf.Abs(deckY - dem) < DeckOffTerrainM) continue;
                return deckY;
            }
            return float.MinValue;
        }

        private void BuildModel(int seed)
        {
            PreloadPrefabs();

            if (_prefabs != null && _prefabs.Length > 0)
            {
                int idx = seed % _prefabs.Length;
                if (_prefabs[idx] != null)
                {
                    var inst = Instantiate(_prefabs[idx], transform);
                    inst.transform.localPosition = Vector3.zero;
                    inst.transform.localRotation = Quaternion.identity;
                    inst.transform.localScale = Vector3.one;

                    foreach (var col in inst.GetComponentsInChildren<Collider>())
                        col.enabled = false;
                    return;
                }
            }

            BuildFallback(seed);
        }

        private void BuildFallback(int seed)
        {
            Color[] colors = new Color[]
            {
                new Color(0.9f, 0.9f, 0.9f), new Color(0.2f, 0.2f, 0.2f),
                new Color(0.7f, 0.1f, 0.1f), new Color(0.1f, 0.3f, 0.7f),
                new Color(0.9f, 0.8f, 0.1f), new Color(0.4f, 0.4f, 0.4f),
            };
            Color c = colors[seed % colors.Length];

            var body = GameObject.CreatePrimitive(PrimitiveType.Cube);
            body.name = "Body";
            body.transform.SetParent(transform, false);
            body.transform.localPosition = new Vector3(0f, 0.7f, 0f);
            body.transform.localScale = new Vector3(1.6f, 1f, 3.5f);
            body.GetComponent<Renderer>().sharedMaterial = MakeMat(c);

            var roof = GameObject.CreatePrimitive(PrimitiveType.Cube);
            roof.name = "Roof";
            roof.transform.SetParent(transform, false);
            roof.transform.localPosition = new Vector3(0f, 1.2f, -0.1f);
            roof.transform.localScale = new Vector3(1.3f, 0.4f, 1.6f);
            roof.GetComponent<Renderer>().sharedMaterial = MakeMat(new Color(0.5f, 0.7f, 0.9f, 0.6f));
        }

        private static readonly Dictionary<Color, Material> _matCache = new Dictionary<Color, Material>();
        private static Material MakeMat(Color c)
        {
            if (_matCache.TryGetValue(c, out var m)) return m;
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            m = new Material(shader);
            if (shader.name.StartsWith("Universal Render Pipeline/Lit"))
                m.SetColor("_BaseColor", c);
            else
                m.SetColor("_Color", c);
            _matCache[c] = m;
            return m;
        }
    }
}
