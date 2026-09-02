using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Vehicle.Traffic
{
    /// <summary>
    /// Rete stradale UNIFICATA costruita dalle TileGraphDoc che si caricano
    /// dai chunk. I nodi viaggiano con gli ID OSM globali (nd.ref), quindi le
    /// tile confinanti si uniscono naturalmente: lo stesso nodo al confine di
    /// due tile ha lo stesso id e le archi delle due tile si saldano da sole.
    /// Gli archi sono dirigibili e rispettano il senso unico (oneway) durante
    /// il pathfinding, così le auto possono percorrere rotte che attraversano
    /// più strade (oggi Foggia, domani l'intero mondo) e tornare indietro.
    /// Le posizioni sono in coordinate MONDO (relative a WorldOrigin); al
    /// rebase si ricostruisce il grafo dalle tile raw (lat/lon) così le
    /// posizioni restano coerenti col resto del mondo.
    /// </summary>
    public class TileRoadNetwork : MonoBehaviour
    {
        public static TileRoadNetwork Instance { get; private set; }

        private readonly Dictionary<string, TileGraphDoc> _raw =
            new Dictionary<string, TileGraphDoc>();

        public RoadGraph Graph { get; private set; } = new RoadGraph();

        // adiacenza DIRIGIBILE (rispetta oneway): nodeId -> (neighbor, arcId)
        private readonly Dictionary<int, List<ArcEdge>> _out =
            new Dictionary<int, List<ArcEdge>>();

        private struct ArcEdge { public int to; public int arcId; }

        private List<RoadNode> _nodes = new List<RoadNode>();

        private bool _dirty = true;

        // ── traffico su rete: auto in movimento che seguono la rete ──
        private const int CarsPerTile = 10;
        private const int MaxCars = 150;
        private const float CullRadius = 500f;
        private const float CullInterval = 0.6f;
        private const float RouteMinLen = 400f;

        private readonly List<CarAgent> _cars = new List<CarAgent>();
        private Transform _root;
        private System.Random _rng = new System.Random();
        private float _nextCull;

        public static TileRoadNetwork Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("TileRoadNetwork");
            DontDestroyOnLoad(go);
            return go.AddComponent<TileRoadNetwork>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
            WorldOrigin.OnRebased += OnRebased;
            var rootGo = new GameObject("CarTraffic");
            rootGo.transform.SetParent(transform, false);
            _root = rootGo.transform;
        }

        private void OnDestroy()
        {
            if (Instance == this) Instance = null;
            WorldOrigin.OnRebased -= OnRebased;
        }

        private void OnRebased(Vector3 delta)
        {
            // il rebase sposta l'origine: la prossima Update ricostruisce il
            // grafo dalle tile raw (lat/lon) e ripianta le auto sulle nuove
            // coordinate mondo, così tutto torna coerente col resto del mondo.
            _dirty = true;
        }

        private void Update()
        {
            if (_dirty)
            {
                Rebuild();
                RespawnAll();
            }
            TickCars();
        }

        public void AddTile(string tileKey, TileGraphDoc doc)
        {
            if (doc == null || doc.nodes == null || doc.arcs == null) return;
            if (_raw.ContainsKey(tileKey)) return;
            _raw[tileKey] = doc;
            _dirty = true;
        }

        public void RemoveTile(string tileKey)
        {
            if (_raw.Remove(tileKey)) _dirty = true;
        }

        /// <summary>Numero di tile attualmente nel grafo unificato.</summary>
        public int TileCount => _raw.Count;

        public bool HasTiles => _raw.Count > 0;

        private void Rebuild()
        {
            _dirty = false;
            var graph = new RoadGraph();
            _out.Clear();

            foreach (var kv in _raw)
                MergeTile(graph, kv.Value);

            Graph = graph;
            _nodes = graph.nodes;
            OsmDiag.Log("[TileRoadNetwork] grafo unificato: " + _raw.Count +
                " tile, " + graph.nodes.Count + " nodi, " + graph.arcs.Count +
                " archi");
        }

        private void MergeTile(RoadGraph graph, TileGraphDoc doc)
        {
            // nodi: gli ID OSM globali uniscono le tile confinanti
            var nodeByTileId = new Dictionary<int, int>();   // tile node id -> graph node id
            for (int i = 0; i < doc.nodes.Length; i++)
            {
                var tn = doc.nodes[i];
                Vector3 pos = WorldOrigin.ToWorld(tn.lat, tn.lon);
                pos.y = 0f;
                JunctionType jt = JunctionType.Simple;
                var jstr = tn.junction;
                if (jstr == "Real") jt = JunctionType.Real;
                else if (jstr == "DeadEnd") jt = JunctionType.DeadEnd;
                var node = graph.AddNode((int)tn.id, pos, jt);
                nodeByTileId[(int)tn.id] = node.id;
            }

            for (int i = 0; i < doc.arcs.Length; i++)
            {
                var ta = doc.arcs[i];
                int from = (int)ta.from;
                int to = (int)ta.to;
                if (!nodeByTileId.ContainsKey(from) || !nodeByTileId.ContainsKey(to))
                    continue;

                if (ta.waypoints == null || ta.waypoints.Length < 2) continue;
                var wp = new Vector3[ta.waypoints.Length];
                for (int k = 0; k < ta.waypoints.Length; k++)
                {
                    wp[k] = WorldOrigin.ToWorld(ta.waypoints[k].a, ta.waypoints[k].o);
                    wp[k].y = 0f;
                }

                var arc = graph.AddArc(from, to, wp,
                    ta.road_name ?? "", ta.highway ?? "", ta.width, ta.oneway);

                // adiacenza diritta per il pathfinding: da from -> to è sempre
                // guidabile; da to -> from solo se non è oneway.
                AddOut(from, to, arc.id);
                if (!ta.oneway) AddOut(to, from, arc.id);
            }
        }

        private void AddOut(int from, int to, int arcId)
        {
            List<ArcEdge> list;
            if (!_out.TryGetValue(from, out list))
            {
                list = new List<ArcEdge>();
                _out[from] = list;
            }
            list.Add(new ArcEdge { to = to, arcId = arcId });
        }

        // ── pathfinding direzionale (rispetta oneway) ──────────────

        public List<int> FindDrivablePath(int startNodeId, int endNodeId)
        {
            if (startNodeId == endNodeId)
                return new List<int> { startNodeId };

            var gScore = new Dictionary<int, float> { [startNodeId] = 0f };
            var cameFrom = new Dictionary<int, int>();
            var closed = new HashSet<int>();
            var open = new List<int> { startNodeId };

            int safety = 0;
            while (open.Count > 0 && safety++ < 20000)
            {
                int current = -1;
                float bestF = float.MaxValue;
                for (int i = 0; i < open.Count; i++)
                {
                    float f = gScore[open[i]]
                        + Heuristic(open[i], endNodeId);
                    if (f < bestF) { bestF = f; current = open[i]; }
                }
                if (current < 0) return null;

                if (current == endNodeId)
                    return Reconstruct(cameFrom, current);

                open.Remove(current);
                closed.Add(current);

                List<ArcEdge> edges;
                if (!_out.TryGetValue(current, out edges)) continue;
                for (int e = 0; e < edges.Count; e++)
                {
                    int nb = edges[e].to;
                    if (closed.Contains(nb)) continue;
                    float tentative = gScore[current] + ArcLen(edges[e].arcId);
                    float gOld;
                    if (gScore.TryGetValue(nb, out gOld) && tentative >= gOld) continue;
                    gScore[nb] = tentative;
                    cameFrom[nb] = current;
                    if (!open.Contains(nb)) open.Add(nb);
                }
            }
            return null;
        }

        private float ArcLen(int arcId)
        {
            var arc = Graph.arcMap[arcId];
            if (arc.length > 0f) return arc.length;
            float len = 0f;
            for (int i = 1; i < arc.waypoints.Length; i++)
                len += (arc.waypoints[i] - arc.waypoints[i - 1]).magnitude;
            return len;
        }

        private float Heuristic(int nodeId, int endNodeId)
        {
            return Vector3.Distance(Graph.nodeMap[nodeId].position,
                Graph.nodeMap[endNodeId].position);
        }

        private static List<int> Reconstruct(Dictionary<int, int> cameFrom, int current)
        {
            var path = new List<int> { current };
            while (cameFrom.TryGetValue(current, out int prev))
            {
                current = prev;
                path.Add(current);
            }
            path.Reverse();
            return path;
        }

        // ── query nodi per lo spawn ───────────────────────────────

        /// <summary>Trova i nodi del grafo dentro un raggio (coordinate mondo).</summary>
        public void NodesNear(Vector3 worldPos, float radius, List<RoadNode> result)
        {
            result.Clear();
            float r2 = radius * radius;
            for (int i = 0; i < _nodes.Count; i++)
            {
                var n = _nodes[i];
                if ((n.position - worldPos).sqrMagnitude <= r2) result.Add(n);
            }
        }

        /// <summary>Nodo di partenza con outgoing valido più vicino a una posizione.</summary>
        public RoadNode NearestNodeWithOut(Vector3 worldPos)
        {
            RoadNode best = null;
            float bestD = float.MaxValue;
            for (int i = 0; i < _nodes.Count; i++)
            {
                List<ArcEdge> edges;
                if (!_out.TryGetValue(_nodes[i].id, out edges) || edges.Count == 0)
                    continue;
                float d = (_nodes[i].position - worldPos).sqrMagnitude;
                if (d < bestD) { bestD = d; best = _nodes[i]; }
            }
            return best;
        }

        // ── gestione auto in movimento (seguono la rete stradale) ──

        /// <summary>Distrugge tutte le auto in movimento e le ripianta sulla
        /// rete corrente (chiamato dopo un rebuild del grafo o un rebase).</summary>
        private void RespawnAll()
        {
            for (int i = 0; i < _cars.Count; i++)
                if (_cars[i] != null) Destroy(_cars[i].gameObject);
            _cars.Clear();

            int target = Mathf.Clamp(_raw.Count * CarsPerTile, 0, MaxCars);
            for (int i = 0; i < target && SpawnOne(); i++) { }
            if (target > 0)
                OsmDiag.Log("[TileRoadNetwork] traffico su rete: " + _cars.Count +
                    "/" + target + " auto (" + _raw.Count + " tile)");
        }

        private void TickCars()
        {
            _cars.RemoveAll(c => c == null);
            FillToTarget();

            if (Time.unscaledTime < _nextCull) return;
            _nextCull = Time.unscaledTime + CullInterval;

            Vector3 pp = PlayerPosition();
            if (pp == Vector3.zero) return;
            float r2 = CullRadius * CullRadius;
            float recycle2 = (CullRadius * 1.5f) * (CullRadius * 1.5f);
            for (int i = _cars.Count - 1; i >= 0; i--)
            {
                var c = _cars[i];
                float d2 = (c.transform.position - pp).sqrMagnitude;
                bool want = d2 < r2;
                if (c.gameObject.activeSelf != want) c.gameObject.SetActive(want);
                // riciclo: un'auto lontana ormai invisibile viene distrutta per
                // liberare lo slot; FillToTarget la ripianta attorno al player
                if (d2 > recycle2 && _cars.Count > 4)
                {
                    Destroy(c.gameObject);
                    _cars.RemoveAt(i);
                }
            }
        }

        private void FillToTarget()
        {
            int target = Mathf.Clamp(_raw.Count * CarsPerTile, 0, MaxCars);
            int guard = 0;
            while (_cars.Count < target && guard++ < 50)
            {
                if (!SpawnOne()) break;
            }
        }

        private Vector3 PlayerPosition()
        {
            var g = City.Game.Instance;
            if (g != null && g.player != null) return g.player.transform.position;
            if (Camera.main != null) return Camera.main.transform.position;
            return Vector3.zero;
        }

        private bool SpawnOne()
        {
            if (_out.Count == 0) return false;

            // partenza: nodo con una strada in uscita, preferendo quelli vicini
            // al player così il traffico visibile resta denso attorno a lui
            RoadNode start = PlayerBiasedOutNode();
            if (start == null) return false;

            // arrivo: il nodo raggiungibile più lontano tra un campione
            RoadNode end = PickDistantTarget(start);
            if (end == null || start.id == end.id) return false;

            var path = FindDrivablePath(start.id, end.id);
            if (path == null || path.Count < 2) return false;

            float[] segLimits;
            TrafficGate[] gates;
            Vector3[] wp = WaypointBuilder.Build(Graph, path, 4f, null,
                out segLimits, out gates);
            if (wp == null || wp.Length < 2) return false;

            float ratio = RouteLengthRatio(wp);
            if (ratio < RouteMinLen) return false;

            var go = new GameObject("Routed_" + _cars.Count);
            go.transform.SetParent(_root, false);
            go.transform.position = wp[0];
            var agent = go.AddComponent<CarAgent>();
            float speed = 7f + (float)_rng.NextDouble() * 6f;
            agent.Init(wp, speed, _rng.Next(100000), segLimits, gates);
            agent.looping = true;
            _cars.Add(agent);
            return true;
        }

        private float RouteLengthRatio(Vector3[] wp)
        {
            float len = 0f;
            for (int i = 1; i < wp.Length; i++)
                len += (wp[i] - wp[i - 1]).magnitude;
            return len;
        }

        /// <summary>Sceglie un nodo con strada in uscita vicino al player
        /// (entro il raggio di culling), così il traffico è visibile subito.
        /// Se non c'è nulla nei dintorni ricade su un nodo qualsiasi.</summary>
        private RoadNode PlayerBiasedOutNode()
        {
            Vector3 pp = PlayerPosition();
            float r = CullRadius;
            float r2 = r * r;
            RoadNode fallback = null;
            for (int i = 0; i < _nodes.Count; i++)
            {
                var n = _nodes[i];
                List<ArcEdge> edges;
                if (!_out.TryGetValue(n.id, out edges) || edges.Count == 0) continue;
                if (fallback == null) fallback = n;
                if ((n.position - pp).sqrMagnitude <= r2) return n;
            }
            return fallback;
        }

        private RoadNode PickDistantTarget(RoadNode start)
        {
            RoadNode best = null;
            float bestD = RouteMinLen;
            int samples = Mathf.Min(24, _nodes.Count);
            for (int i = 0; i < samples; i++)
            {
                var n = _nodes[_rng.Next(_nodes.Count)];
                List<ArcEdge> edges;
                if (!_out.TryGetValue(n.id, out edges) || edges.Count == 0) continue;
                float d = (n.position - start.position).sqrMagnitude;
                if (d > bestD) { bestD = d; best = n; }
            }
            return best;
        }
    }
}
