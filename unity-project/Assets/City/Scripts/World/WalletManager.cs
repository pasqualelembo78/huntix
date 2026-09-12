using System.Collections;
using UnityEngine;
using UnityEngine.Networking;
using City.OSM;

namespace City.World
{
    /// <summary>
    /// Motore di sincronizzazione del wallet con il server (FASE 6).
    ///
    /// - Al primo avvio, se c'e' un JWT valido, carica il saldo AUTHORITATIVE
    ///   da GET /api/city/wallet (cosi' il player trova i suoi soldi anche
    ///   dopo un cambio telefono).
    /// - Accumula i delta (netto) prodotti da Wallet.Spend/Earn e li invia al
    ///   server con un piccolo debounce (mai una richiesta ogni frame).
    ///   Il saldo tornato dal server e' la verita'; Wallet adotta il valore.
    /// - Il server e' authoritative. Se la rete/il token mancano, si resta
    ///   sulla cache locale PlayerPrefs (comportamento offline) senza errori.
    /// </summary>
    public class WalletManager : MonoBehaviour
    {
        private const string BaseUrl = TileClient.BaseUrl;
        private const string WalletUrl = BaseUrl + "/api/city/wallet";

        private const float FLUSH_AFTER_MUTATION = 0.20f; // debounce dopo Spend/Earn
        private const float FLUSH_DEFAULT = 1.0f;          // flush periodico di sicurezza

        private static WalletManager _instance;
        private static int _pendingDelta;    // delta netto NON ancora confermato
        private static bool _restoredServer; // il saldo e' stato caricato dal server

        private string _token = "";
        private bool _online;                // c'e' un JWT valido per il sync
        private float _nextFlush = -1f;
        private bool _syncing;               // c'e' gia' una richiesta in corso

        public static WalletManager Instance { get { return _instance; } }

        /// <summary>Auto-istanziato in Game.Start(); pulire sempre qui.</summary>
        public static void Ensure()
        {
            if (_instance == null)
            {
                var go = new GameObject("WalletManager");
                DontDestroyOnLoad(go);
                go.AddComponent<WalletManager>();
            }
        }

        public static void EnqueueDelta(int delta)
        {
            if (_instance == null) return; // offline: resta solo in PlayerPrefs
            _pendingDelta += delta;
            _instance.ScheduleFlush();
        }

        public static void ResetPendingDelta()
        {
            _pendingDelta = 0;
        }

        private void Awake()
        {
            if (_instance != null && _instance != this)
            {
                Destroy(gameObject);
                return;
            }
            _instance = this;
        }

        private void OnDestroy()
        {
            if (_instance == this) _instance = null;
        }

        private void Start()
        {
            _token = ReadToken();
            _online = !string.IsNullOrEmpty(_token);
            if (_online)
                StartCoroutine(LoadServerBalance());
        }

        private string ReadToken()
        {
            try { return Huntix.Bridge.UnityBridge.GetAccessToken() ?? ""; }
            catch (System.Exception e)
            {
                Debug.LogWarning("[Wallet] GetAccessToken: " + e.Message);
                return "";
            }
        }

        private void ScheduleFlush()
        {
            float t = Time.time;
            // flush subito dopo l'operazione (oppure il piu' presto gia' programmato)
            _nextFlush = (_nextFlush < 0f) ? t + FLUSH_AFTER_MUTATION
                                           : Mathf.Min(_nextFlush, t + FLUSH_AFTER_MUTATION);
        }

        private void Update()
        {
            if (!_online) return;
            if (_syncing) return;
            if (_pendingDelta == 0)
            {
                // nessun delta: niente spam. (poll di sicurezza saltato)
                _nextFlush = -1f;
                return;
            }
            if (Time.time < _nextFlush) return;
            StartCoroutine(SendDelta());
        }

        private IEnumerator LoadServerBalance()
        {
            string url = WalletUrl + "?token=" + _token;
            using (var web = UnityWebRequest.Get(url))
            {
                web.timeout = 15;
                yield return web.SendWebRequest();
                if (web.result != UnityWebRequest.Result.Success)
                    yield break; // offline per ora: resta sulla cache locale

                string text = web.downloadHandler != null ? web.downloadHandler.text : null;
                WalletResp resp = Parse(text);
                if (resp != null && resp.ok)
                {
                    _restoredServer = true;
                    Wallet.ApplyServerBalance(resp.money);
                }
            }
        }

        private IEnumerator SendDelta()
        {
            _syncing = true;
            int delta = _pendingDelta;
            _pendingDelta = 0; // verra' ripristinato in caso di fallimento
            try
            {
                if (delta == 0) yield break;

                string body = "{\"token\":\"" + _token + "\",\"amount\":" + Mathf.Abs(delta) + "}";
                string url = (delta > 0) ? WalletUrl + "/earn" : WalletUrl + "/spend";

                using (var web = new UnityWebRequest(url, "POST"))
                {
                    web.uploadHandler = new UploadHandlerRaw(
                        System.Text.Encoding.UTF8.GetBytes(body));
                    web.downloadHandler = new DownloadHandlerBuffer();
                    web.SetRequestHeader("Content-Type", "application/json");
                    web.timeout = 15;
                    yield return web.SendWebRequest();

                    if (web.result != UnityWebRequest.Result.Success)
                    {
                        // errore di rete: riaccoda il delta per il prossimo flush
                        _pendingDelta += delta;
                        _nextFlush = Time.time + FLUSH_DEFAULT;
                        yield break;
                    }

                    WalletResp resp = Parse(
                        web.downloadHandler != null ? web.downloadHandler.text : null);
                    if (resp == null || !resp.ok)
                    {
                        // server non riconosce il token: passa offline
                        _pendingDelta += delta;
                        _token = "";
                        _online = false;
                        yield break;
                    }

                    // saldo authoritative dal server
                    _restoredServer = true;
                    Wallet.ApplyServerBalance(resp.money);
                }
            }
            finally
            {
                _syncing = false;
            }
        }

        [System.Serializable]
        private class WalletResp
        {
            public bool ok;
            public int money;
            public string error;
        }

        private static WalletResp Parse(string json)
        {
            if (string.IsNullOrEmpty(json)) return null;
            try { return JsonUtility.FromJson<WalletResp>(json); }
            catch (System.Exception) { return null; }
        }
    }
}