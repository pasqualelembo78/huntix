using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Networking;
using City.OSM;

namespace City.Vehicle.Traffic
{
    public class TrafficClient : MonoBehaviour
    {
        public static TrafficClient Instance;

        private const float POLL_INTERVAL = 0.15f;
        private const float RECONNECT_INTERVAL = 3f;
        private const float PLAYER_BROADCAST_INTERVAL = 1f;

        private string _serverUrl;
        private string _gameId;
        private bool _connected;
        private float _pollTimer;
        private float _broadcastTimer;
        private Transform _player;
        private float _playerX, _playerZ;
        private int _failCount;
        private const int MAX_FAILS_BEFORE_LOCAL = 3;
        private Bounds[] _buildingBounds;
        private RoadGraph _roadGraph;

        public event Action<List<CarUpdate>> OnCarsUpdated;
        public event Action<List<TrafficLightUpdate>> OnTrafficLightsUpdated;
        public event Action OnConnected;
        public event Action OnDisconnected;
        public event Action OnServerUnreachable;

        private float _reconnectTimer;
        private bool _joining;

        public struct CarUpdate
        {
            public string id;
            public float x, z, ry;
            public float speed;
            public string model;
            public int color;
        }

        public struct TrafficLightUpdate
        {
            public int id;
            public float x, z;
            public string state;
        }

        public struct SpeedZoneUpdate
        {
            public int id;
            public float x, z;
            public string type;
        }

        private void Awake()
        {
            Instance = this;
        }

        public void Connect(string serverUrl, string gameId)
        {
            _serverUrl = serverUrl.TrimEnd('/');
            _gameId = gameId;
            _connected = false;
            _joining = false;

            var game = City.Game.Instance;
            _player = game != null ? game.player?.transform : null;

            StartCoroutine(JoinGame());
        }

        public void SetBuildingBounds(Bounds[] bounds)
        {
            _buildingBounds = bounds;
        }

        public void SetRoadGraph(RoadGraph graph)
        {
            _roadGraph = graph;
        }

        public void Disconnect()
        {
            _connected = false;
            StopAllCoroutines();
        }

        private IEnumerator JoinGame()
        {
            _joining = true;
            string url = $"{_serverUrl}/api/traffic/join";

            string graphJson = _roadGraph != null ? SerializeGraph(_roadGraph) : null;

            var sb = new System.Text.StringBuilder();
            var ci = System.Globalization.CultureInfo.InvariantCulture;
            sb.Append("{");
            sb.AppendFormat("\"game_id\":\"{0}\",", _gameId);
            sb.AppendFormat("\"player_x\":{0},", _playerX.ToString("F2", ci));
            sb.AppendFormat("\"player_z\":{0}", _playerZ.ToString("F2", ci));

            if (graphJson != null)
                sb.AppendFormat(",\"road_graph\":{0}", graphJson);

            if (_buildingBounds != null && _buildingBounds.Length > 0)
            {
                sb.Append(",\"building_bounds\":[");
                for (int i = 0; i < _buildingBounds.Length; i++)
                {
                    if (i > 0) sb.Append(",");
                    var b = _buildingBounds[i];
                    sb.AppendFormat("{{\"cx\":{0},\"cz\":{1},\"hw\":{2},\"hd\":{3}}}",
                        b.center.x.ToString("F2", ci),
                        b.center.z.ToString("F2", ci),
                        b.extents.x.ToString("F2", ci),
                        b.extents.z.ToString("F2", ci));
                }
                sb.Append("]");
            }
            sb.Append("}");
            string body = sb.ToString();

            using (var req = new UnityWebRequest(url, "POST"))
            {
                byte[] bodyRaw = System.Text.Encoding.UTF8.GetBytes(body);
                req.uploadHandler = new UploadHandlerRaw(bodyRaw);
                req.downloadHandler = new DownloadHandlerBuffer();
                req.SetRequestHeader("Content-Type", "application/json");
                req.timeout = 5;

                yield return req.SendWebRequest();

                if (req.result == UnityWebRequest.Result.Success)
                {
                    var resp = JsonUtility.FromJson<JoinResponse>(req.downloadHandler.text);
                    if (resp != null && resp.cars != null)
                    {
                        _connected = true;
                        _failCount = 0;
                        _reconnectTimer = 0;
                        Debug.Log($"[TrafficClient] Connected: {resp.cars.Count} cars");
                        OnConnected?.Invoke();
                        OnCarsUpdated?.Invoke(resp.cars);
                    }
                }
                else
                {
                    _failCount++;
                    Debug.LogWarning($"[TrafficClient] Join failed ({_failCount}/{MAX_FAILS_BEFORE_LOCAL}): {req.error}");
                    if (_failCount >= MAX_FAILS_BEFORE_LOCAL)
                    {
                        Debug.LogWarning("[TrafficClient] Server unreachable, switching to local mode");
                        OnServerUnreachable?.Invoke();
                        yield break;
                    }
                    _reconnectTimer = RECONNECT_INTERVAL;
                }
            }
            _joining = false;
        }

        private void Update()
        {
            if (_player != null)
            {
                _playerX = _player.position.x;
                _playerZ = _player.position.z;
            }

            if (!_connected)
            {
                _reconnectTimer -= Time.deltaTime;
                if (_reconnectTimer <= 0 && !_joining)
                {
                    StartCoroutine(JoinGame());
                }
                return;
            }

            _pollTimer += Time.deltaTime;
            if (_pollTimer >= POLL_INTERVAL)
            {
                _pollTimer = 0;
                StartCoroutine(PollState());
            }

            _broadcastTimer += Time.deltaTime;
            if (_broadcastTimer >= PLAYER_BROADCAST_INTERVAL)
            {
                _broadcastTimer = 0;
                StartCoroutine(BroadcastPlayerPos());
            }
        }

        private IEnumerator PollState()
        {
            string url = $"{_serverUrl}/api/traffic/state?game_id={_gameId}";

            using (var req = UnityWebRequest.Get(url))
            {
                req.timeout = 5;
                yield return req.SendWebRequest();

                if (req.result == UnityWebRequest.Result.Success)
                {
                    var resp = JsonUtility.FromJson<StateResponse>(req.downloadHandler.text);
                    if (resp != null && resp.cars != null)
                    {
                        OnCarsUpdated?.Invoke(resp.cars);
                        if (resp.traffic_lights != null)
                            OnTrafficLightsUpdated?.Invoke(resp.traffic_lights);
                    }
                }
                else
                {
                    Debug.LogWarning($"[TrafficClient] Poll failed: {req.error}");
                    _connected = false;
                    _reconnectTimer = RECONNECT_INTERVAL;
                }
            }
        }

        private IEnumerator BroadcastPlayerPos()
        {
            string url = $"{_serverUrl}/api/traffic/player-pos";
            string body = JsonUtility.ToJson(new PlayerPosRequest
            {
                game_id = _gameId,
                x = _playerX,
                z = _playerZ,
            });

            using (var req = new UnityWebRequest(url, "POST"))
            {
                byte[] bodyRaw = System.Text.Encoding.UTF8.GetBytes(body);
                req.uploadHandler = new UploadHandlerRaw(bodyRaw);
                req.downloadHandler = new DownloadHandlerBuffer();
                req.SetRequestHeader("Content-Type", "application/json");
                req.timeout = 5;

                yield return req.SendWebRequest();
            }
        }

        [Serializable]
        private class JoinResponse
        {
            public string game_id;
            public int car_count;
            public List<CarUpdate> cars;
        }

        [Serializable]
        private class StateResponse
        {
            public List<CarUpdate> cars;
            public List<TrafficLightUpdate> traffic_lights;
            public List<SpeedZoneUpdate> speed_zones;
        }

        [Serializable]
        private class PlayerPosRequest
        {
            public string game_id;
            public float x;
            public float z;
        }

        private void OnDestroy()
        {
            if (Instance == this) Instance = null;
        }

        private static string SerializeGraph(RoadGraph graph)
        {
            var sb = new System.Text.StringBuilder();
            var ci = System.Globalization.CultureInfo.InvariantCulture;
            sb.Append("{");
            sb.Append("\"nodes\":[");
            for (int i = 0; i < graph.nodes.Count; i++)
            {
                if (i > 0) sb.Append(",");
                var n = graph.nodes[i];
                sb.AppendFormat("{{\"id\":{0},\"x\":{1},\"z\":{2},\"junction\":\"{3}\"}}",
                    n.id,
                    n.position.x.ToString("F2", ci),
                    n.position.z.ToString("F2", ci),
                    n.junction);
            }
            sb.Append("],\"arcs\":[");
            for (int i = 0; i < graph.arcs.Count; i++)
            {
                if (i > 0) sb.Append(",");
                var a = graph.arcs[i];
                sb.AppendFormat("{{\"id\":{0},\"from\":{1},\"to\":{2},\"road_name\":\"{3}\",\"highway\":\"{4}\",\"width\":{5},\"waypoints\":[",
                    a.id, a.fromNodeId, a.toNodeId,
                    a.roadName ?? "", a.highway ?? "",
                    a.width.ToString("F2", ci));
                for (int w = 0; w < a.waypoints.Length; w++)
                {
                    if (w > 0) sb.Append(",");
                    sb.AppendFormat("{{\"lng\":{0},\"lat\":{1}}}",
                        a.waypoints[w].x.ToString("F2", ci),
                        a.waypoints[w].z.ToString("F2", ci));
                }
                sb.Append("]}}");
            }
            sb.Append("]}");
            return sb.ToString();
        }
    }
}
