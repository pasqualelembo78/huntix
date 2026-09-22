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
        private const string InboxUrl = BaseUrl + "/api/city/chat/inbox";
        private const string GiftUrl = BaseUrl + "/api/city/gift/inbox";
        private const string GiftClaimUrl = BaseUrl + "/api/city/gift/claim";
        private const string PresenceUrl = BaseUrl + "/api/city/presence";
        private const string PartyStateUrl = BaseUrl + "/api/city/party/state";
        private const string PartyInvitesUrl = BaseUrl + "/api/city/party/invites";
        private const string PartyCreateUrl = BaseUrl + "/api/city/party/create";
        private const string PartyInviteUrl = BaseUrl + "/api/city/party/invite";
        private const string PartyAcceptUrl = BaseUrl + "/api/city/party/accept";
        private const string PartyLeaveUrl = BaseUrl + "/api/city/party/leave";
        private const string PartyDeclineUrl = BaseUrl + "/api/city/party/decline";

        private const float POLL_INTERVAL = 1.0f;   // sec tra un ciclo e l'altro
        private const float CHAT_POLL_INTERVAL = 3.5f;
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

        // Notifiche chat P2P: numero di messaggi non letti gia' segnalati.
        private int _chatLastSeen;

        // B2/B3: HUD "vicini" (lista tappabile) ed emote locale inviata nel
        // heartbeat (anim_state) per la durata di una finestra di tempo.
        private bool _showPlayers;
        private string _localEmote = "";
        private float _emoteUntil;

        // Regali P2P (uova e gemme) in arrivo: notificati una sola volta.
        private int _giftLastSeen;

        // Party (gruppo di amici): membri + inviti in sospeso.
        private int _partyId;
        private string _partyLeader = "";
        private readonly List<PartyMember> _partyMembers = new List<PartyMember>();
        private readonly List<InviteMsg> _pendingInvites = new List<InviteMsg>();

        // "Vai da" (presenza): traccia un altro giocatore, o coordinate fisse
        // (es. casa di un amico condivisa via chat da Android).
        private string _presTarget = "";
        private string _presName = "";
        private bool _presOnline;
        private double _presLat;
        private double _presLon;
        private int _presSeen;
        private string _fixedCoords = "";   // "lat,lon" condivisi
        private string _fixedLabel = "";
        private double _fixedLat;
        private double _fixedLon;
        private float _fixedCoordsUntil;

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
            public string pet;
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
            public string pet;
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

        [Serializable]
        private class InboxMsg
        {
            public string from;
            public string from_name;
            public string text;
        }

        [Serializable]
        private class InboxResp
        {
            public bool ok;
            public int count;
            public InboxMsg[] messages;
        }

        [Serializable]
        private class GiftMsg
        {
            public int id;
            public string from_user;
            public string from_name;
            public string kind;
            public string rarity;
            public string egg_id;
            public string egg_name;
            public int amount;
            public string ts;
        }

        [Serializable]
        private class GiftResp
        {
            public bool ok;
            public int count;
            public GiftMsg[] gifts;
        }

        [Serializable]
        private class GiftClaimReq
        {
            public string token;
            public int gift_id;
        }

        [Serializable]
        private class GiftClaimResp
        {
            public bool ok;
        }

        [Serializable]
        private class CreditMsg
        {
            public string kind;
            public string rarity;
            public string egg_id;
            public string egg_name;
            public int amount;
            public string from_name;
        }

        [Serializable]
        private class PresenceResp
        {
            public bool ok;
            public bool online;
            public string id;
            public string username;
            public int level;
            public double lat;
            public double lon;
            public int last_seen_sec;
        }

        [Serializable]
        private class PartyMember
        {
            public string id;
            public string username;
            public int level;
            public string skin;
            public double lat;
            public double lon;
            public int last_seen_sec;
        }

        [Serializable]
        private class PartyResp
        {
            public bool ok;
            public int party_id;
            public string leader;
            public PartyMember[] members;
        }

        [Serializable]
        private class InviteMsg
        {
            public int party_id;
            public string from_user;
            public string from_name;
            public string ts;
        }

        [Serializable]
        private class InvitesResp
        {
            public bool ok;
            public int count;
            public InviteMsg[] invites;
        }

        [Serializable]
        private class SimpleReq
        {
            public string token;
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
            StartCoroutine(ChatLoop());
            StartCoroutine(GiftLoop());
            StartCoroutine(PartyLoop());
            StartCoroutine(PresenceLoop());
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

        // ── HUD social (IMGUI, minimale, senza dipendenze UI) ──
        // Bottone "👥 N giocatori" sopra la lista dei vicini, con azioni per
        // riga (💬 chat, 🧭 vai da, 🎮 invita in party), palette emote e,
        // sotto, il pannello party (membri + inviti in sospeso).
        private void OnGUI()
        {
            if (!_started) return;
            DrawPresenceIndicator();

            bool anyNeighbors = _remote.Count > 0;
            bool anyParty = _partyId > 0 || _pendingInvites.Count > 0;
            if (!anyNeighbors && !anyParty && !_showPlayers) return;

            string title = _remote.Count > 0
                ? "\uD83D\uDC65 " + _remote.Count + " giocatori"
                : "Nessun vicino";
            _showPlayers = GUI.Button(new Rect(10, 10, 190, 30),
                _showPlayers ? title + " \u25BC" : title);
            if (!_showPlayers) return;

            int y = 46;
            foreach (var kv in _remote)
            {
                RemotePlayer rp = kv.Value;
                string name = rp.Username + " (Lv." + rp.Level + ")";
                if (GUI.Button(new Rect(10, y, 118, 26), "\uD83D\uDCAC " + name))
                    OpenChatWith(rp.PlayerId, rp.Username);
                if (GUI.Button(new Rect(132, y, 52, 26), "\uD83E\uDDED"))
                    TrackPlayer(rp.PlayerId, rp.Username);
                if (GUI.Button(new Rect(188, y, 52, 26), "\uD83C\uDF9F\uFE0F"))
                    StartCoroutine(CreateThenInvite(rp.PlayerId));
                y += 30;
            }

            // Palette emote rapide (invio immediato del Trigger + heartbeat)
            if (anyNeighbors || anyParty || _remote.Count > 0)
            {
                GUI.Label(new Rect(10, y, 70, 26), "Emote:");
                string[] codes = { "yes", "no", "hit", "melee", "throw", "consume", "phone" };
                string[] icons = { "\uD83D\uDC4B", "\uD83D\uDC4E", "\uD83D\uDC4A",
                                   "\uD83E\uDD4A", "\uD83E\uDD1C", "\uD83C\uDF57", "\uD83D\uDCDE" };
                int ex = 84;
                for (int i = 0; i < codes.Length && i < icons.Length; i++)
                {
                    if (GUI.Button(new Rect(ex, y, 26, 26), icons[i]))
                        RequestEmote(codes[i]);
                    ex += 30;
                }
                y += 34;
            }

            // ── pannello party ──
            if (anyParty)
            {
                if (_partyId > 0)
                {
                    bool isLeader = _partyLeader == _myId;
                    GUI.Label(new Rect(10, y, 280, 22),
                        "\uD83C\uDF9F\uFE0F Gruppo" + (isLeader ? " (tuo)" : ""));
                    y += 24;
                    foreach (var m in _partyMembers)
                    {
                        bool online = m.last_seen_sec <= 5;
                        GUI.Label(new Rect(10, y, 220, 24),
                            (online ? "\uD83D\uDFE2 " : "\uD83D\uDD34 ") + m.username +
                            " (Lv." + m.level + ")" +
                            (m.id == _myId ? " · tu" : ""));
                        if (m.id != _myId && m.id.Length > 0 &&
                            GUI.Button(new Rect(240, y, 60, 24), "\uD83E\uDDED"))
                            TrackPlayer(m.id, m.username);
                        y += 28;
                    }
                    if (GUI.Button(new Rect(10, y, 140, 26), "Esci dal gruppo"))
                        StartCoroutine(PostPartyLeave());
                    y += 32;
                }
                foreach (var inv in _pendingInvites)
                {
                    if (GUI.Button(new Rect(10, y, 220, 26),
                        "\uD83D\uDCE8 " + inv.from_name + " ti invita"))
                        StartCoroutine(AcceptInvite(inv.party_id));
                    if (GUI.Button(new Rect(234, y, 40, 26), "\u2716"))
                        StartCoroutine(PostJson(PartyDeclineUrl, PartyReq(inv.party_id)));
                    y += 30;
                }
            }
        }

        /// JSON comune per le richieste "solo token" (body POST).
        private string PartyReq(int partyId)
        {
            if (partyId > 0)
                return "{\"token\":\"" + _accessToken + "\",\"party_id\":" + partyId + "}";
            return "{\"token\":\"" + _accessToken + "\"}";
        }

        /// B3: richiede un'emote locale + remota. Il trigger locale parte
        /// subito; l'anim_state va nel prossimo heartbeat agli altri player.
        private void RequestEmote(string code)
        {
            string clip = RemotePlayer.MapEmoteClip(code);
            if (clip.Length == 0) return;
            _localEmote = code;
            _emoteUntil = Time.time + 3f;
            try
            {
                if (Game.Instance != null && Game.Instance.player != null)
                    City.Player.PlayerActions.Trigger(Game.Instance.player.gameObject, clip);
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[Multiplayer] emote locale: " + e.Message);
            }
        }

        /// "Vai da": inizia a tracciare un altro giocatore (presenza + freccia).
        private void TrackPlayer(string id, string name)
        {
            _presTarget = id;
            _presName = name;
            _presOnline = false;
            _fixedCoords = "";
        }

        /// Coordinate fisse da Android (es. casa condivisa via "🧭 Vai"): il
        /// formato è "lat,lon" (label opzionale). Viene richiamata dal
        /// GameManager (pipe "setTargetCoord|lat,lon,label").
        public static void SetTargetCoords(string data)
        {
            if (Instance == null || data == null) return;
            string[] parts = data.Split(',');
            if (parts.Length < 2) return;
            if (double.TryParse(parts[0].Trim(), System.Globalization.NumberStyles.Float,
                    System.Globalization.CultureInfo.InvariantCulture, out double lt) &&
                double.TryParse(parts[1].Trim(), System.Globalization.NumberStyles.Float,
                    System.Globalization.CultureInfo.InvariantCulture, out double lo))
            {
                Instance._presTarget = "";
                Instance._fixedLat = lt;
                Instance._fixedLon = lo;
                Instance._fixedCoords = "set";
                Instance._fixedLabel = parts.Length > 2 ? parts[2].Trim() : "Destinazione";
                Instance._fixedCoordsUntil = Time.time + 300f;
            }
        }

        /// Indicatore "vai da" disegnato sopra la HUD: marker sullo schermo
        /// (direzione reale verso la destinazione) + nome e distanza.
        private void DrawPresenceIndicator()
        {
            bool fixedActive = Time.time < _fixedCoordsUntil && _fixedCoords.Length > 0;
            if (!fixedActive && string.IsNullOrEmpty(_presTarget)) return;
            try
            {
                var cam = Camera.main;
                string label;
                Vector3 wpos = Vector3.zero;
                bool hasPos = false;
                if (fixedActive)
                {
                    wpos = WorldOrigin.ToWorld(_fixedLat, _fixedLon);
                    label = "\uD83D\uDDFA\uFE0F " + _fixedLabel;
                    hasPos = true;
                }
                else if (_presOnline)
                {
                    wpos = WorldOrigin.ToWorld(_presLat, _presLon);
                    label = "\uD83D\uDDFA\uFE0F " + _presName;
                    hasPos = true;
                }
                else
                {
                    label = "\uD83D\uDDFA\uFE0F " + _presName + " · offline";
                }

                if (hasPos && _playerTransform != null)
                {
                    Vector3 flat = _playerTransform.position;
                    flat.y = 0f;
                    label += " · " + FormatDist(Vector3.Distance(flat, wpos));
                }

                if (!hasPos || cam == null)
                {
                    GUI.Label(new Rect(10, 82, 260, 24), label);
                    return;
                }

                Vector3 sp = cam.WorldToScreenPoint(wpos);
                bool behind = sp.z < 0f;
                if (behind) sp = -sp;
                float x = Mathf.Clamp(sp.x, 10f, Mathf.Max(12f, Screen.width - 10f));
                float y = Mathf.Clamp(Screen.height - sp.y, 70f, Mathf.Max(74f, Screen.height - 28f));
                GUI.Label(new Rect(x - 100f, y - 14f, 212f, 28f), label);
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[Multiplayer] indicatore: " + e.Message);
            }
        }

        private string FormatDist(float m)
        {
            if (m < 1000f) return Mathf.RoundToInt(m) + " m";
            return (m / 1000f).ToString("0.0") + " km";
        }

        private IEnumerator AcceptInvite(int partyId)
        {
            yield return PostJson(PartyAcceptUrl, PartyReq(partyId));
            yield return FetchPartyState();
            yield return FetchInvites();
        }

        private IEnumerator CreateThenInvite(string toUser)
        {
            if (_partyId <= 0)
            {
                yield return PostPartyCreate();
                if (_partyId <= 0) yield break;
            }
            yield return PostJson(PartyInviteUrl,
                "{\"token\":\"" + _accessToken + "\",\"to_user\":\"" + toUser + "\"}");
        }

        private IEnumerator PostPartyCreate()
        {
            string prev = JsonUtility.ToJson(new SimpleReq { token = _accessToken });
            yield return PostJson(PartyCreateUrl, prev);
            yield return FetchPartyState();
        }

        private IEnumerator PostPartyLeave()
        {
            yield return PostJson(PartyLeaveUrl, PartyReq(0));
            _partyId = 0;
            _partyLeader = "";
            _partyMembers.Clear();
        }

        /// POST JSON generico (body gia' serializzato). Per i servizi party
        /// non serve parseare risposta oltre lo stato (già pollato altrove).
        private IEnumerator PostJson(string url, string body)
        {
            using (var web = new UnityWebRequest(url, "POST"))
            {
                web.uploadHandler = new UploadHandlerRaw(System.Text.Encoding.UTF8.GetBytes(body));
                web.downloadHandler = new DownloadHandlerBuffer();
                web.SetRequestHeader("Content-Type", "application/json");
                web.timeout = 10;
                yield return web.SendWebRequest();
            }
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

        // ── notifiche chat P2P (interazione tra giocatori reali) ──
        // Verifica la casella messaggi non letti (peek=1: NON li consuma, la
        // chat Android resta padrona dell'inbox) e mostra un toast TOCCABILE:
        // tap → apri la chat con il mittente.
        private IEnumerator ChatLoop()
        {
            WaitForSeconds wait = new WaitForSeconds(CHAT_POLL_INTERVAL);
            while (true)
            {
                if (_started && !string.IsNullOrEmpty(_accessToken))
                    yield return FetchInbox();
                yield return wait;
            }
        }

        // ── regali P2P (uova e gemme) ───────────────────────────
        // Poll degli arrivi: mostra un toast (tap → chat col mittente).
        private IEnumerator GiftLoop()
        {
            WaitForSeconds wait = new WaitForSeconds(5f);
            while (true)
            {
                if (_started && !string.IsNullOrEmpty(_accessToken))
                    yield return FetchGifts();
                yield return wait;
            }
        }

        private IEnumerator FetchGifts()
        {
            string url = GiftUrl + "?token=" + Uri.EscapeDataString(_accessToken);
            using (var web = UnityWebRequest.Get(url))
            {
                web.timeout = 10;
                yield return web.SendWebRequest();
                if (web.result != UnityWebRequest.Result.Success) yield break;
                try
                {
                    GiftResp resp = JsonUtility.FromJson<GiftResp>(web.downloadHandler.text);
                    if (resp == null || resp.gifts == null) yield break;
                    int n = resp.gifts.Length;
                    if (n == 0) { _giftLastSeen = 0; yield break; }
                    if (n > _giftLastSeen)
                    {
                        for (int i = _giftLastSeen; i < n; i++)
                        {
                            GiftMsg g = resp.gifts[i];
                            if (g == null) continue;
                            string what = g.kind == "gem"
                                ? "\uD83D\uDC8E " + g.amount + " gemme"
                                : "\uD83E\uDD5A un uovo " + (string.IsNullOrEmpty(g.rarity) ? "" : g.rarity + " ");
                            NotifyGift(g, g.from_user ?? "", g.from_name ?? "Giocatore", what);
                        }
                        _giftLastSeen = n;
                    }
                    else if (n < _giftLastSeen)
                    {
                        _giftLastSeen = n;
                    }
                }
                catch (System.Exception e)
                {
                    Debug.LogWarning("[Multiplayer] regali: " + e.Message);
                }
            }
        }

        private void NotifyGift(GiftMsg g, string from, string name, string what)
        {
            try
            {
                if (City.Game.Instance != null && City.Game.Instance.ui != null)
                    City.Game.Instance.ui.ShowChatToast("\uD83C\uDF81 " + name + " ti ha regalato " + what,
                        () => StartCoroutine(ClaimAndCredit(g, from, name)));
                else
                    Debug.Log("[Multiplayer] Regalo da " + name + ": " + what);
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[Multiplayer] notifica regalo: " + e.Message);
                Debug.Log("[Multiplayer] Regalo da " + name + ": " + what);
            }
        }

        private IEnumerator ClaimAndCredit(GiftMsg g, string from, string name)
        {
            yield return ClaimGift(g);
        }

        /// POST /api/city/gift/claim (ledger): mark "claimed" server-side, poi
        /// comunica ad Android il contenuto per accreditare inventario/gemme.
        private IEnumerator ClaimGift(GiftMsg g)
        {
            var req = new GiftClaimReq { token = _accessToken, gift_id = g.id };
            string body = JsonUtility.ToJson(req);
            using (var web = new UnityWebRequest(GiftClaimUrl, "POST"))
            {
                web.timeout = 10;
                web.uploadHandler = new UploadHandlerRaw(System.Text.Encoding.UTF8.GetBytes(body));
                web.downloadHandler = new DownloadHandlerBuffer();
                web.SetRequestHeader("Content-Type", "application/json");
                yield return web.SendWebRequest();
                if (web.result != UnityWebRequest.Result.Success)
                {
                    Debug.LogWarning("[Multiplayer] claim regalo fallito: " + web.error +
                        " (lo si ritrova al prossimo giro)");
                    yield break;
                }
                try
                {
                    GiftClaimResp resp = JsonUtility.FromJson<GiftClaimResp>(web.downloadHandler.text);
                    if (resp != null && resp.ok)
                    {
                        var credit = new CreditMsg
                        {
                            kind = g.kind,
                            rarity = g.rarity ?? "",
                            egg_id = g.egg_id ?? "",
                            egg_name = g.egg_name ?? "",
                            amount = g.amount,
                            from_name = g.from_name ?? "Giocatore"
                        };
                        string json = JsonUtility.ToJson(credit);
                        try
                        {
                            Huntix.Bridge.UnityBridge.SendMessageToAndroid("GiftClaimed", json);
                        }
                        catch
                        {
                            // solo editor: accredito simulato, la notifica basta
                        }
                        if (City.Game.Instance != null && City.Game.Instance.ui != null)
                            City.Game.Instance.ui.ShowToast("\uD83C\uDF81 Regalo di " +
                                (g.from_name ?? "") + " riscattato!");
                    }
                    else
                    {
                        Debug.LogWarning("[Multiplayer] claim regalo rifiutato (gia' riscattato?)");
                    }
                }
                catch (System.Exception e)
                {
                    Debug.LogWarning("[Multiplayer] claim regalo: " + e.Message);
                }
            }
        }

        // ── party (gruppo di amici) ─────────────────────────────
        private IEnumerator PartyLoop()
        {
            WaitForSeconds wait = new WaitForSeconds(4f);
            while (true)
            {
                if (_started && !string.IsNullOrEmpty(_accessToken))
                {
                    yield return FetchPartyState();
                    yield return FetchInvites();
                }
                yield return wait;
            }
        }

        private IEnumerator FetchPartyState()
        {
            string url = PartyStateUrl + "?token=" + Uri.EscapeDataString(_accessToken);
            using (var web = UnityWebRequest.Get(url))
            {
                web.timeout = 10;
                yield return web.SendWebRequest();
                if (web.result != UnityWebRequest.Result.Success) yield break;
                try
                {
                    PartyResp r = JsonUtility.FromJson<PartyResp>(web.downloadHandler.text);
                    if (r == null) yield break;
                    _partyId = r.party_id;
                    _partyLeader = r.leader ?? "";
                    _partyMembers.Clear();
                    if (r.members != null) _partyMembers.AddRange(r.members);
                }
                catch (System.Exception e)
                {
                    Debug.LogWarning("[Multiplayer] party state: " + e.Message);
                }
            }
        }

        private IEnumerator FetchInvites()
        {
            string url = PartyInvitesUrl + "?token=" + Uri.EscapeDataString(_accessToken);
            using (var web = UnityWebRequest.Get(url))
            {
                web.timeout = 10;
                yield return web.SendWebRequest();
                if (web.result != UnityWebRequest.Result.Success) yield break;
                try
                {
                    InvitesResp r = JsonUtility.FromJson<InvitesResp>(web.downloadHandler.text);
                    if (r == null) yield break;
                    _pendingInvites.Clear();
                    if (r.invites != null) _pendingInvites.AddRange(r.invites);
                }
                catch (System.Exception e)
                {
                    Debug.LogWarning("[Multiplayer] inviti: " + e.Message);
                }
            }
        }

        // ── presenza "vai da" ───────────────────────────────────
        private IEnumerator PresenceLoop()
        {
            WaitForSeconds wait = new WaitForSeconds(2f);
            while (true)
            {
                if (_started && !string.IsNullOrEmpty(_presTarget) &&
                    !string.IsNullOrEmpty(_accessToken))
                    yield return FetchPresence();
                yield return wait;
            }
        }

        private IEnumerator FetchPresence()
        {
            string url = PresenceUrl + "?token=" + Uri.EscapeDataString(_accessToken) +
                         "&with_id=" + Uri.EscapeDataString(_presTarget);
            using (var web = UnityWebRequest.Get(url))
            {
                web.timeout = 10;
                yield return web.SendWebRequest();
                if (web.result != UnityWebRequest.Result.Success) yield break;
                try
                {
                    PresenceResp r = JsonUtility.FromJson<PresenceResp>(web.downloadHandler.text);
                    if (r == null) yield break;
                    _presOnline = r.online;
                    _presLat = r.lat;
                    _presLon = r.lon;
                    _presSeen = r.last_seen_sec;
                    if (!string.IsNullOrEmpty(r.username)) _presName = r.username;
                    if (_presOnline && _playerTransform != null)
                    {
                        Vector3 w = WorldOrigin.ToWorld(_presLat, _presLon);
                        Vector3 flat = _playerTransform.position;
                        flat.y = 0f;
                        // arrivati: spegni la modalita' tracciamento
                        if (Vector3.Distance(flat, w) < 6f) _presTarget = "";
                    }
                }
                catch (System.Exception e)
                {
                    Debug.LogWarning("[Multiplayer] presence: " + e.Message);
                }
            }
        }

        // ── F4: visita casa amico (teletrasporto) ──────────────
        /// <summary>True quando stiamo tracciando un amico (presenza) o
        /// coordinate condivise (es. casa di un amico via chat).</summary>
        public bool HasVisitTarget
        {
            get { return _presTarget != "" || _fixedCoords != ""; }
        }

        /// <summary>Nome mostrato per la visita (amico o etichetta fissa).</summary>
        public string VisitTargetName
        {
            get
            {
                if (!string.IsNullOrEmpty(_fixedLabel)) return _fixedLabel;
                return !string.IsNullOrEmpty(_presName) ? _presName : "amico";
            }
        }

        /// <summary>Teletrasporta il giocatore vicino all'amico tracciato
        /// (o alle coordinate condivise) con fade, per visitargli la casa.
        /// Come HomeSystem.TeleportHome: CC spento durante la mossa.</summary>
        public void VisitFriendTeleport()
        {
            double lat = 0, lon = 0;
            if (!string.IsNullOrEmpty(_fixedCoords))
            {
                string[] parts = _fixedCoords.Split(',');
                double a;
                double b;
                if (parts.Length >= 2 &&
                    double.TryParse(parts[0], out a) &&
                    double.TryParse(parts[1], out b))
                {
                    lat = a; lon = b;
                }
            }
            if (lat == 0 && lon == 0 && _presOnline)
            {
                lat = _presLat; lon = _presLon;
            }
            if (lat == 0 && lon == 0)
            {
                var g0 = Game.Instance;
                if (g0 != null && g0.ui != null)
                    g0.ui.ShowToast("Nessun amico da visitare in questo momento.");
                return;
            }
            Vector3 pos = WorldOrigin.ToWorld(lat, lon);
            var g = Game.Instance;
            if (g == null || g.player == null) return;
            StartCoroutine(VisitTeleportRoutine(g, pos));
        }

        private IEnumerator VisitTeleportRoutine(Game g, Vector3 pos)
        {
            var fader = g.fader;
            if (fader != null) fader.gameObject.SetActive(true);
            if (fader != null) fader.FadeToBlack(null);

            var player = g.player;
            var cc = player.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            player.transform.position = pos + Vector3.up * 0.3f;
            player.transform.rotation = Quaternion.Euler(0f, 0f, 0f);
            if (cc != null) cc.enabled = true;
            player.Stop();
            if (g.rig != null) g.rig.SetYaw(player.transform.rotation);

            if (g.ui != null) g.ui.ShowToast("Visitando " + VisitTargetName + ".");

            if (fader != null)
            {
                yield return new WaitForSeconds(fader.duration);
                fader.FadeFromBlack(null);
                yield return new WaitForSeconds(fader.duration);
                fader.gameObject.SetActive(false);
            }
        }

        /// <summary>Stanza condivisa (F4): nome del remote player più vicino
        /// entro `range` metri dalla posizione indicata (es. l'impronta
        /// dell'edificio in cui siamo) oppure null.</summary>
        public string SharedRoomMate(Vector3 pos, float range)
        {
            string best = null;
            float bestD = range * range;
            foreach (var kv in _remote)
            {
                RemotePlayer rp = kv.Value;
                if (rp == null || rp.transform == null) continue;
                float dx = rp.transform.position.x - pos.x;
                float dz = rp.transform.position.z - pos.z;
                float d2 = dx * dx + dz * dz;
                if (d2 < bestD)
                {
                    bestD = d2;
                    best = rp.Username;
                }
            }
            return best;
        }

        private IEnumerator FetchInbox()
        {
            string url = InboxUrl + "?token=" + Uri.EscapeDataString(_accessToken) + "&peek=1";
            using (UnityWebRequest web = UnityWebRequest.Get(url))
            {
                yield return web.SendWebRequest();
                if (web.result != UnityWebRequest.Result.Success) yield break;
                try
                {
                    InboxResp resp = JsonUtility.FromJson<InboxResp>(web.downloadHandler.text);
                    if (resp == null || resp.messages == null) yield break;
                    int n = resp.messages.Length;
                    if (n == 0) { _chatLastSeen = 0; yield break; }
                    if (n > _chatLastSeen)
                    {
                        for (int i = _chatLastSeen; i < n; i++)
                        {
                            InboxMsg m = resp.messages[i];
                            if (m == null) continue;
                            string name = string.IsNullOrEmpty(m.from_name) ? "Giocatore" : m.from_name;
                            string sender = m.from ?? "";
                            string txt = m.text ?? "";
                            if (txt.Length > 72) txt = txt.Substring(0, 72) + "\u2026";
                            NotifyChat(sender, name, txt);
                        }
                        _chatLastSeen = n;
                    }
                    else if (n < _chatLastSeen)
                    {
                        _chatLastSeen = n;  // l'activity di chat (pull-and-clear) li ha letti
                    }
                }
                catch (System.Exception e)
                {
                    Debug.LogWarning("[Multiplayer] inbox chat: " + e.Message);
                }
            }
        }

        private void NotifyChat(string senderId, string senderName, string text)
        {
            // Bolla di chat sopra la testa del mittente (se e' in vista).
            if (_remote.TryGetValue(senderId, out RemotePlayer rp) && rp != null)
                rp.ShowChatBubble(text);

            // Toast interattivo: tap → apre la chat P2P con il mittente
            // (stesso percorso del tap su un RemotePlayer).
            try
            {
                if (City.Game.Instance != null && City.Game.Instance.ui != null)
                    City.Game.Instance.ui.ShowChatToast("\uD83D\uDCAC " + senderName + ": " + text,
                        () => OpenChatWith(senderId, senderName));
                else
                    Debug.Log("[Multiplayer] Chat da " + senderName + ": " + text);
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[Multiplayer] notifica chat: " + e.Message);
                Debug.Log("[Multiplayer] Chat da " + senderName + ": " + text);
            }
        }

        private void OpenChatWith(string senderId, string senderName)
        {
            if (senderId == null || senderId.Length == 0) return;
            string name = senderName ?? "Giocatore";
            name = name.Replace("\\", "\\\\").Replace("\"", "\\\"");
            string json = "{\"toUserId\":\"" + senderId + "\",\"name\":\"" + name +
                          "\",\"level\":1,\"skin\":\"humanMaleA\"}";
            try
            {
                Huntix.Bridge.UnityBridge.SendMessageToAndroid("PlayerProfileRequest", json);
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[Multiplayer] apertura chat: " + e.Message);
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
            // B3: se e' in corso una emote locale, quella vince sul cammino.
            if (Time.time < _emoteUntil) anim = _localEmote;

            HeartbeatReq req = new HeartbeatReq
            {
                id = _myId,
                token = _accessToken,
                username = _playerName,
                level = _playerLevel,
                skin = PlayerAppearance.SavedSkin,
                pet = City.Player.PetController.SavedPet,
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
                        p.level, p.skin, p.pet, spawn, p.heading);
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