using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Networking;
using City.OSM;
using City.Player;

namespace City.Multiplayer
{
    /// <summary>
    /// Cliente multiplayer per MiaCitta' (Fase 2).
    ///
    /// Il server espone lo stato condiviso via HTTP (endpoint /api/city):
    ///   - POST /api/city/heartbeat : registra/aggiorna la MIA posizione
    ///   - GET  /api/city/players   : lista dei player attivi nelle zone vicine
    ///
    /// Ogni POLL_INTERVAL scorre entrambi gli endpoint; il filtro di vicinanza
    /// (MAX_REMOTE_PLAYERS + VISIBLE_RANGE) decide quali remote player far
    /// vedere sullo schermo.
    ///
    /// Fase 3/4: l'identità arriva dal ponte Android. Se l'utente è loggato
    /// (JWT valido ricevuto da StoreUnityBridge.getAccessToken) il server usa
    /// il reale user_id invece del GUID anonimo di device.
    /// </summary>
    public class MultiplayerManager : MonoBehaviour
    {
        public static MultiplayerManager Instance;

        // ── configurazione ──────────────────────────────────────
        private const string BaseUrl = TileClient.BaseUrl;
        private const string HeartbeatUrl = BaseUrl + "/api/city/heartbeat";
        private const string PlayersUrl = BaseUrl + "/api/city/players";

        private const float POLL_INTERVAL = 1.0f;   // sec tra un ciclo e l'altro
        private const float INTERPOLATION_SPEED = 8f;
        private const int MAX_REMOTE_PLAYERS = 20;
        private const float VISIBLE_RANGE = 200f;

        private const string PlayerIdKey = "huntix_player_id"; // condiviso con veicoli

        // ── stato ──────────────────────────────────────────────
        private readonly Dictionary<string, RemotePlayer> _remote =
            new Dictionary<string, RemotePlayer>();
        private readonly List<RemotePlayer> _orphans = new List<RemotePlayer>();

        private bool _started;
        private string _myId;

        // Identità autentica (Fase 3): JWT + profilo ricavati da Android.
        private string _accessToken = "";
        private string _playerName = "";
        private int _playerLevel = 1;

        // ── riferimenti ────────────────────────────────────────
        private Transform _playerTransform;

        // ── modelli JSON ────────────────────────────────────────
        [Serializable]
        private class HeartbeatReq
        {
            public string id;
            public string token;
            public string username;
            public int level;
            public string skin;
            public double lat;
            public double lon;
            public float heading;
            public string anim_state;
            public string vehicle_code;
        }

        [Serializable]
        private class PlayerState
        {
            public string id;
            public string username;
            public int level;
            public string skin;
            public double lat;
            public double lon;
            public float heading;
            public string anim_state;
            public string vehicle_code;
        }

        [Serializable]
        private class PlayersResp
        {
            public int count;
            public string center_zone;
            public PlayerState[] players;
        }

        [Serializable]
        private class HeartbeatResp
        {
            public bool ok;
            public string id;
            public string zone;
        }

        // API pubblica ------------------------------------------------
        public bool IsConnected { get { return _started; } }
        public int PlayerCount { get { return _remote.Count; } }
        public string MyId { get { return _myId; } }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
        }

        private void Start()
        {
            if (Game.Instance == null || Game.Instance.player == null)
                return;

            _playerTransform = Game.Instance.player.transform;
            _myId = LocalPlayerId();
            LoadIdentityFromAndroid();
            _started = true;
            StartCoroutine(PollLoop());
        }

        /// <summary>Recupera identità e profilo autentici dal ponte Android
        /// (Fase 3). Il token JWT, quando presente, sostituisce l'id anonimo
        /// sul server; NON viene mai persistito qui.</summary>
        private void LoadIdentityFromAndroid()
        {
            try
            {
                _accessToken = Huntix.Bridge.UnityBridge.GetAccessToken() ?? "";
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[Multiplayer] GetAccessToken: " + e.Message);
                _accessToken = "";
            }
            try { _playerName = Huntix.Bridge.UnityBridge.GetPlayerName(); }
            catch (System.Exception) { _playerName = ""; }
            try { _playerLevel = Huntix.Bridge.UnityBridge.GetPlayerLevel(); }
            catch (System.Exception) { _playerLevel = 1; }

            if (string.IsNullOrEmpty(_playerName)) _playerName = "Giocatore";
            if (_playerLevel < 1) _playerLevel = 1;
        }

        private void Update()
        {
            if (!_started) return;

            float spd = INTERPOLATION_SPEED;
            foreach (var rp in _remote.Values)
                rp.Interpolate(spd);

            // Pulisce eventuali RemotePlayer rimasti orfani di stato
            if (_orphans.Count > 0)
            {
                foreach (var r in _orphans) if (r != null) r.SafeDestroy();
                _orphans.Clear();
            }
        }

        public static string LocalPlayerId()
        {
            string id = PlayerPrefs.GetString(PlayerIdKey, "");
            if (string.IsNullOrEmpty(id))
            {
                id = Guid.NewGuid().ToString("N");
                PlayerPrefs.SetString(PlayerIdKey, id);
                PlayerPrefs.Save();
            }
            return id;
        }

        private void OnDestroy()
        {
            Instance = null;
        }

        // ── ciclo di polling ─────────────────────────────────────
        private IEnumerator PollLoop()
        {
            WaitForSeconds wait = new WaitForSeconds(POLL_INTERVAL);
            while (true)
            {
                if (_started && _playerTransform != null)
                {
                    yield return HeartbeatPos();
                    yield return FetchOthers();
                }
                yield return wait;
            }
        }

        private IEnumerator HeartbeatPos()
        {
            Vector3 pos = _playerTransform.position;
            GeoCoord geo = WorldOrigin.ToGeo(pos);
            float heading = _playerTransform.eulerAngles.y;
            string anim = "idle";
            if (Game.Instance != null && Game.Instance.player != null)
                anim = Game.Instance.player.IsMoving ? "walk" : "idle";

            HeartbeatReq req = new HeartbeatReq
            {
                id = _myId,
                token = _accessToken,
                username = _playerName,
                level = _playerLevel,
                skin = PlayerAppearance.SavedSkin,
                lat = geo.lat,
                lon = geo.lng,
                heading = heading,
                anim_state = anim,
                vehicle_code = "",
            };

            string json = JsonUtility.ToJson(req);
            using (var web = new UnityWebRequest(HeartbeatUrl, "POST"))
            {
                web.uploadHandler = new UploadHandlerRaw(System.Text.Encoding.UTF8.GetBytes(json));
                web.downloadHandler = new DownloadHandlerBuffer();
                web.SetRequestHeader("Content-Type", "application/json");
                yield return web.SendWebRequest();

                bool ok = web.result == UnityWebRequest.Result.Success;
                if (ok && !string.IsNullOrEmpty(_accessToken))
                {
                    // L'autenticazione JWT fa diventare l'id effettivo il reale
                    // user_id: memorizzalo per non mostrare se stesso come remote.
                    try
                    {
                        var text = web.downloadHandler != null ? web.downloadHandler.text : null;
                        if (!string.IsNullOrEmpty(text))
                        {
                            HeartbeatResp resp = JsonUtility.FromJson<HeartbeatResp>(text);
                            if (resp != null && !string.IsNullOrEmpty(resp.id))
                                _myId = resp.id;
                        }
                    }
                    catch (System.Exception e)
                    {
                        Debug.LogWarning("[Multiplayer] parse heartbeat id: " + e.Message);
                    }
                }
            }
        }

        private IEnumerator FetchOthers()
        {
            string url;
            if (_playerTransform != null)
            {
                GeoCoord geo = WorldOrigin.ToGeo(_playerTransform.position);
                url = PlayersUrl + "?lat=" + geo.lat.ToString("0.00000",
                    System.Globalization.CultureInfo.InvariantCulture) +
                      "&lon=" + geo.lng.ToString("0.00000",
                    System.Globalization.CultureInfo.InvariantCulture);
            }
            else
            {
                url = PlayersUrl;
            }

            using (var web = UnityWebRequest.Get(url))
            {
                web.timeout = 15;
                yield return web.SendWebRequest();

                bool ok = web.result == UnityWebRequest.Result.Success;
                if (!ok) yield break;

                string json = web.downloadHandler.text;
                PlayersResp resp = JsonUtility.FromJson<PlayersResp>(json);
                if (resp == null || resp.players == null) yield break;

                ApplyState(resp.players);
                UpdateVisibilityFilter();
            }
        }

        private void ApplyState(PlayerState[] players)
        {
            if (players == null) return;

            // Player presenti: mark per il diff join/remove
            Dictionary<string, PlayerState> current = new Dictionary<string, PlayerState>();
            foreach (var p in players)
                current[p.id] = p;

            // Nuovi o aggiornati
            foreach (var p in players)
            {
                if (string.IsNullOrEmpty(p.id)) continue;
                if (p.id == _myId) continue; // ignora se stesso

                if (_remote.TryGetValue(p.id, out RemotePlayer rp))
                {
                    if (_playerTransform != null)
                    {
                        Vector3 target = WorldOrigin.ToWorld(p.lat, p.lon);
                        rp.SetTarget(target, p.heading, p.anim_state);
                    }
                }
                else
                {
                    Vector3 spawn = Vector3.zero;
                    if (_playerTransform != null)
                        spawn = WorldOrigin.ToWorld(p.lat, p.lon);
                    RemotePlayer created = RemotePlayer.Spawn(
                        p.id, string.IsNullOrEmpty(p.username) ? "Giocatore" : p.username,
                        p.level, p.skin, spawn, p.heading);
                    if (created != null)
                        _remote[p.id] = created;
                }
            }

            // Rimossi (non piu' in lista)
            List<string> gone = new List<string>();
            foreach (var kv in _remote)
            {
                if (!current.ContainsKey(kv.Key))
                    gone.Add(kv.Key);
            }
            foreach (var id in gone)
            {
                if (_remote.TryGetValue(id, out RemotePlayer rp))
                {
                    _orphans.Add(rp);
                    _remote.Remove(id);
                }
            }
        }

        private void UpdateVisibilityFilter()
        {
            if (_playerTransform == null) return;

            Vector3 myPos = _playerTransform.position;
            List<RemotePlayer> sorted = new List<RemotePlayer>(_remote.Values);
            sorted.Sort(delegate (RemotePlayer a, RemotePlayer b)
            {
                float da = (a.transform.position - myPos).sqrMagnitude;
                float db = (b.transform.position - myPos).sqrMagnitude;
                return da.CompareTo(db);
            });

            int shown = 0;
            foreach (var rp in sorted)
            {
                float dist = Vector3.Distance(myPos, rp.transform.position);
                bool shouldBeVisible = shown < MAX_REMOTE_PLAYERS && dist <= VISIBLE_RANGE;
                rp.ApplyVisibility(shouldBeVisible);
                if (shouldBeVisible) shown++;
            }
        }
    }
}