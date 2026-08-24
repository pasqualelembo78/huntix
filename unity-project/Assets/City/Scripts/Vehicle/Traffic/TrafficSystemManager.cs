using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Vehicle.Traffic
{
    public class TrafficSystemManager : MonoBehaviour
    {
        public static TrafficSystemManager Instance;

        private const float DESPAWN_DIST = 120f;
        private const float NEARBY_RADIUS = 100f;
        private const float MIN_SPEED = 4f;
        private const float MAX_SPEED = 14f;
        private const int MIN_AGENTS = 1;
        private const int BUILDINGS_PER_CAR = 3;

        private RoadGraph _graph;
        private readonly List<CarAgent> _localAgents = new List<CarAgent>();
        private readonly Dictionary<string, CarRenderer> _serverCars = new Dictionary<string, CarRenderer>();
        private Transform _player;
        private System.Random _rng;
        private Bounds[] _buildingBounds;
        private float _densityCheckTimer;

        private bool _serverMode;
        private bool _initialized;
        private TrafficLightRenderer _lights;
        private float _lightPushTimer;

        // classi stradali "maggiori": incroci importanti -> semaforo
        private static readonly HashSet<string> MajorRoads = new HashSet<string>
        { "motorway", "trunk", "primary", "secondary" };

        private void Awake()
        {
            Instance = this;
        }

        public void InitLocal(RoadGraph graph, Transform root, Bounds[] buildingBounds,
            TrafficLightRenderer lights = null)
        {
            _graph = graph;
            _rng = new System.Random(12345);
            _buildingBounds = buildingBounds;
            _serverMode = false;
            _lights = lights;

            // ── registro incroci regolati: semafori dove servono ──
            // 4+ rami sempre; 3 rami se toccano strade maggiori.
            JunctionControl.Clear();
            foreach (var n in graph.nodes)
            {
                int arcs = n.arcIds.Count;
                if (arcs < 3) continue;
                bool hasLight = arcs >= 4;
                if (!hasLight)
                {
                    foreach (int arcId in n.arcIds)
                    {
                        var a = graph.arcMap[arcId];
                        if (a != null && MajorRoads.Contains(a.highway ?? ""))
                        {
                            hasLight = true;
                            break;
                        }
                    }
                }
                JunctionControl.RegisterJunction(n.id, n.position, hasLight);
            }

            var game = City.Game.Instance;
            _player = game != null && game.player != null ? game.player.transform : null;

            if (_graph == null || _graph.nodes.Count < 2)
            {
                Debug.LogWarning("[TrafficSystemManager] Graph too small");
                return;
            }

            CarAgent.PreloadPrefabs();
            int target = CalcTargetCount();
            SpawnInitialLocal(target);
            _initialized = true;

            Debug.Log($"[TrafficSystemManager] Local mode: {_localAgents.Count} agents");
        }

        public void InitServer(Transform root)
        {
            _serverMode = true;
            _initialized = true;

            var game = City.Game.Instance;
            _player = game != null && game.player != null ? game.player.transform : null;

            Debug.Log("[TrafficSystemManager] Server mode: waiting for positions");
        }

        private void Update()
        {
            if (!_initialized) return;

            if (_serverMode)
            {
                CleanupDistantServerCars();
            }
            else
            {
                if (_graph == null) return;
                CleanupFinishedLocal();
                _densityCheckTimer += Time.deltaTime;
                if (_densityCheckTimer > 1f)
                {
                    _densityCheckTimer = 0f;
                    EnforceDensityLocal();
                }
                AssignLeadersLocal();

                // visualizzazione dei semafori generati localmente
                _lightPushTimer += Time.deltaTime;
                if (_lightPushTimer > 0.5f)
                {
                    _lightPushTimer = 0f;
                    PushLocalLights();
                }
            }
        }

        // Due teste per incrocio (id*2 = asse NS, id*2+1 = asse EW) riutilizzano
        // il renderer pensato per il server: zero cambi lato visual.
        private void PushLocalLights()
        {
            if (_lights == null) return;
            Vector3 pp = _player != null ? _player.position : Vector3.zero;
            var updates = new List<TrafficClient.TrafficLightUpdate>();
            float now = Time.time;
            foreach (var j in JunctionControl.AllJunctions())
            {
                if (!j.hasLight) continue;
                if ((j.pos - pp).sqrMagnitude > 170f * 170f) continue;
                var ns = new TrafficClient.TrafficLightUpdate
                {
                    id = j.nodeId * 2,
                    x = j.pos.x - 2.6f,
                    z = j.pos.z - 2.6f,
                    state = JunctionControl.StateName(JunctionControl.StateFor(j.nodeId, 0, now)),
                };
                var ew = new TrafficClient.TrafficLightUpdate
                {
                    id = j.nodeId * 2 + 1,
                    x = j.pos.x + 2.6f,
                    z = j.pos.z + 2.6f,
                    state = JunctionControl.StateName(JunctionControl.StateFor(j.nodeId, 1, now)),
                };
                updates.Add(ns);
                updates.Add(ew);
            }
            if (updates.Count > 0) _lights.UpdateLights(updates);
        }

        // ── Server mode: receive car positions ───────────────────

        public void UpdateServerCars(List<TrafficClient.CarUpdate> updates)
        {
            if (!_serverMode || !_initialized) return;

            var seen = new HashSet<string>();

            foreach (var cu in updates)
            {
                seen.Add(cu.id);

                if (_serverCars.TryGetValue(cu.id, out var renderer))
                {
                    renderer.UpdatePosition(cu);
                }
                else
                {
                    var go = new GameObject("SCar_" + cu.id);
                    go.transform.SetParent(transform, false);
                    var cr = go.AddComponent<CarRenderer>();
                    cr.Init(cu);
                    _serverCars[cu.id] = cr;
                }
            }

            var toRemove = new List<string>();
            foreach (var kvp in _serverCars)
            {
                if (!seen.Contains(kvp.Key))
                    toRemove.Add(kvp.Key);
            }
            foreach (var id in toRemove)
            {
                if (_serverCars.TryGetValue(id, out var r))
                    Destroy(r.gameObject);
                _serverCars.Remove(id);
            }
        }

        private void CleanupDistantServerCars()
        {
            if (_player == null) return;
            Vector3 pp = _player.position;

            foreach (var kvp in _serverCars)
            {
                if (kvp.Value == null) continue;
                float d = Vector3.Distance(kvp.Value.transform.position, pp);
                kvp.Value.gameObject.SetActive(d < DESPAWN_DIST);
            }
        }

        // ── Local mode: existing simulation ──────────────────────

        private int CalcTargetCount()
        {
            int nearby = CountNearbyBuildings();
            int byDensity = Mathf.Max(MIN_AGENTS, Mathf.CeilToInt((float)nearby / BUILDINGS_PER_CAR));
            return Mathf.Min(byDensity, _graph.nodes.Count);
        }

        private int CountNearbyBuildings()
        {
            if (_buildingBounds == null || _buildingBounds.Length == 0 || _player == null)
                return 0;
            Vector3 pp = _player.position;
            int count = 0;
            for (int i = 0; i < _buildingBounds.Length; i++)
            {
                if (Vector3.Distance(pp, _buildingBounds[i].center) < NEARBY_RADIUS)
                    count++;
            }
            return count;
        }

        private List<RoadNode> GetReachableNodes()
        {
            var result = new List<RoadNode>();
            foreach (var node in _graph.nodes)
            {
                if (node.junction != JunctionType.DeadEnd && node.arcIds.Count > 0)
                    result.Add(node);
            }
            return result;
        }

        private void SpawnInitialLocal(int count)
        {
            var nodes = GetReachableNodes();
            if (nodes.Count < 2) return;
            for (int i = 0; i < count; i++)
                SpawnRandomLocal(nodes);
        }

        private void EnforceDensityLocal()
        {
            int target = CalcTargetCount();
            var nodes = GetReachableNodes();
            if (nodes.Count < 2) return;

            int safety = 0;
            while (_localAgents.Count < target && safety++ < 50)
            {
                int before = _localAgents.Count;
                SpawnNearPlayerLocal(nodes);
                if (_localAgents.Count == before) break;
            }
        }

        private void SpawnNearPlayerLocal(List<RoadNode> nodes)
        {
            if (nodes.Count < 2 || _player == null) return;

            int startId = FindClosestNodeLocal(nodes);
            int endId = PickRandomFarNodeLocal(nodes, startId);

            var path = RoadPathfinder.FindPath(_graph, startId, endId);
            if (path == null || path.Count < 2) return;

            var wp = WaypointBuilder.Build(_graph, path, 4f, _buildingBounds,
                out var segLimits, out var gates);
            if (wp.Length < 2) return;

            float speed = MIN_SPEED + (float)(_rng.NextDouble() * (MAX_SPEED - MIN_SPEED));
            var go = new GameObject("LAgent_" + _localAgents.Count);
            go.transform.SetParent(transform, false);
            go.transform.position = wp[0];

            var agent = go.AddComponent<CarAgent>();
            agent.Init(wp, speed, _rng.Next(1000), segLimits, gates);
            agent.looping = true;
            _localAgents.Add(agent);
        }

        private int FindClosestNodeLocal(List<RoadNode> nodes)
        {
            Vector3 pp = _player.position;
            int best = nodes[0].id;
            float bestD = float.MaxValue;
            foreach (var n in nodes)
            {
                float d = (n.position - pp).sqrMagnitude;
                if (d < bestD) { bestD = d; best = n.id; }
            }
            return best;
        }

        private int PickRandomFarNodeLocal(List<RoadNode> nodes, int excludeId)
        {
            Vector3 pp = _player != null ? _player.position : Vector3.zero;
            var cands = new List<RoadNode>();
            foreach (var n in nodes)
            {
                if (n.id == excludeId) continue;
                float d = Vector3.Distance(n.position, pp);
                if (d > 30 && d < 250) cands.Add(n);
            }
            if (cands.Count == 0)
                foreach (var n in nodes)
                    if (n.id != excludeId) cands.Add(n);
            if (cands.Count == 0) return excludeId;
            return cands[_rng.Next(cands.Count)].id;
        }

        private void SpawnRandomLocal(List<RoadNode> nodes)
        {
            if (nodes.Count < 2) return;
            int si = _rng.Next(nodes.Count);
            int ei = _rng.Next(nodes.Count);
            int t = 0;
            while (ei == si && t++ < 10) ei = _rng.Next(nodes.Count);

            var path = RoadPathfinder.FindPath(_graph, nodes[si].id, nodes[ei].id);
            if (path == null || path.Count < 2) return;

            var wp = WaypointBuilder.Build(_graph, path, 4f, _buildingBounds,
                out var segLimits, out var gates);
            if (wp.Length < 2) return;

            float speed = MIN_SPEED + (float)(_rng.NextDouble() * (MAX_SPEED - MIN_SPEED));
            var go = new GameObject("LAgent_" + _localAgents.Count);
            go.transform.SetParent(transform, false);
            go.transform.position = wp[0];

            var agent = go.AddComponent<CarAgent>();
            agent.Init(wp, speed, _rng.Next(1000), segLimits, gates);
            agent.looping = true;
            _localAgents.Add(agent);
        }

        private void CleanupFinishedLocal()
        {
            if (_player == null) return;
            for (int i = _localAgents.Count - 1; i >= 0; i--)
            {
                var a = _localAgents[i];
                if (a == null || a.state == CarAgentState.Idle)
                {
                    if (a != null) Destroy(a.gameObject);
                    _localAgents.RemoveAt(i);
                    continue;
                }
                float d = Vector3.Distance(a.transform.position, _player.position);
                if (d > DESPAWN_DIST)
                {
                    Destroy(a.gameObject);
                    _localAgents.RemoveAt(i);
                }
            }
        }

        private void AssignLeadersLocal()
        {
            foreach (var a in _localAgents) a.ClearLeader();
            for (int i = 0; i < _localAgents.Count; i++)
            {
                var a = _localAgents[i];
                if (a == null || a.state != CarAgentState.Driving) continue;
                float bestD = float.MaxValue;
                CarAgent bestL = null;
                for (int j = 0; j < _localAgents.Count; j++)
                {
                    if (i == j) continue;
                    var b = _localAgents[j];
                    // anche le auto ferme (gomma a terra) sono ostacoli:
                    // chi arriva dietro frena invece di attraversarle
                    if (b == null || (b.state != CarAgentState.Driving &&
                                      b.state != CarAgentState.Punctured)) continue;
                    float d = Vector3.Distance(a.transform.position, b.transform.position);
                    if (d > 20) continue;
                    Vector3 toB = (b.transform.position - a.transform.position).normalized;
                    float dot = Vector3.Dot(a.transform.forward, toB);
                    if (dot > 0.5f && d < bestD) { bestD = d; bestL = b; }
                }
                if (bestL != null) a.SetLeader(bestL, bestD);
            }
        }

        public void DespawnAll()
        {
            foreach (var a in _localAgents)
                if (a != null) Destroy(a.gameObject);
            _localAgents.Clear();

            foreach (var kvp in _serverCars)
                if (kvp.Value != null) Destroy(kvp.Value.gameObject);
            _serverCars.Clear();
        }

        private void OnDestroy()
        {
            if (Instance == this) Instance = null;
            JunctionControl.Clear();
        }
    }
}
