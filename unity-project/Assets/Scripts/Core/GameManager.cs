using UnityEngine;
using UnityEngine.SceneManagement;
using System;
using Huntix.Bridge;
using Huntix.Outdoor;
using Huntix.UI;

namespace Huntix.Core
{
    public class GameManager : MonoBehaviour
    {
        public static GameManager Instance { get; private set; }

        // Registry asset Kenney (referenziata qui per essere inclusa nel build).
        // Risolve gli asset via riferimento diretto invece di Resources.Load.
        public KenneyAssetRegistry kenneyRegistry;

        // Registry asset Kenney City Kit (strade/edifici/arredo urbano, CC0).
        public CityKitAssetRegistry cityKitRegistry;

        public string CurrentMode { get; private set; }
        public bool IsInUnity { get; private set; }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            IsInUnity = true;
        }

        private void Start()
        {
            UnityBridge.Init();

            // Se l'Activity Unity è stata lanciata con una modalità (es. Esplora),
            // carica subito la scena giusta (Esplora → Outdoor con personaggio + POI).
            string mode = UnityBridge.GetMode();
            if (!string.IsNullOrEmpty(mode))
            {
                LoadSceneForMode(mode);
            }
            else
            {
                // Nessun modo specifico: scena di menu, mostra l'UI di esplorazione.
                BootstrapExplore();
                NeedsHUD.EnsureInstance();
            }
        }

        private void LoadSceneForMode(string mode)
        {
            CurrentMode = mode;
            Debug.Log($"[GameManager] Mode set to: {mode}");
            string scene = mode switch
            {
                "outdoor" or "esplora" or "reallife" => "Outdoor",
                "indoor" => "Indoor",
                "game" or "sheep" => "Preload",
                "argame" or "ardice" => "MainScene",
                "supermarket_proto" => "MainGame",
                "room" => "Room",
                "miacitta" => "City",
                _ => null
            };
            if (scene != null && SceneManager.GetActiveScene().name != scene)
            {
                SceneManager.LoadScene(scene);
                if (scene == "Outdoor")
                {
                    NeedsHUD.EnsureInstance();
                    BootstrapExplore();
                }
                else
                {
                    NeedsHUD.DestroyInstance();
                    // Rimuove l'UI e i manager Esplora (POI) non pertineni in altre scene
                    if (ExploreManager.Instance != null) Destroy(ExploreManager.Instance.gameObject);
                    if (ExploreUIController.Instance != null) Destroy(ExploreUIController.Instance.gameObject);
                    if (ExplorePopup.Instance != null) Destroy(ExplorePopup.Instance.gameObject);
                    if (ExploreInputHandler.Instance != null) Destroy(ExploreInputHandler.Instance.gameObject);
                }
            }

            // MiAcitma: sostituisce il quartiere finto con la città OSM reale (streaming GPS).
            if (scene == "City")
            {
                City.OSM.CityOSMWorld.EnsureInstance();
            }
        }

        // Crea il modulo Esplora (marker POI AR) e la sua UI se assenti in scena.
        private void BootstrapExplore()
        {
            if (ExploreManager.Instance == null)
            {
                var go = new GameObject("ExploreManager");
                go.AddComponent<ExploreManager>();
            }
            if (ExploreUIController.Instance == null)
            {
                var go = new GameObject("ExploreUIController");
                go.AddComponent<ExploreUIController>();
            }
        }

        public void SetMode(string mode)
        {
            CurrentMode = mode;
            Debug.Log($"[GameManager] Mode set to: {mode}");
        }

        public void ReturnToAndroid()
        {
            Debug.Log("[GameManager] Returning to Android native UI");
            UnityBridge.QuitToAndroid();
        }

        public void LoadScene(string sceneName)
        {
            SceneManager.LoadScene(sceneName);
        }

        // UnitySendMessage passa sempre un unico parametro stringa.
        // Supporta due formati:
        //   1) JSON: {"action":"setMode","mode":"..."} (inviato da BridgeActivity)
        //   2) "eventName|data" (inviato da PoiUnityBridge.Messenger)
        public void OnEvent(string message)
        {
            if (string.IsNullOrEmpty(message)) return;

            // Formato JSON (BridgeActivity)
            if (message.Contains("\"action\"", StringComparison.Ordinal) ||
                message.Contains("\"setMode\"", StringComparison.Ordinal))
            {
                HandleSetMode(message);
                return;
            }

            // Formato pipe (PoiUnityBridge)
            string eventName, jsonData;
            int pipeIndex = message.IndexOf('|');
            if (pipeIndex >= 0)
            {
                eventName = message.Substring(0, pipeIndex);
                jsonData = message.Substring(pipeIndex + 1);
            }
            else
            {
                eventName = message;
                jsonData = "";
            }

            Debug.Log($"[GameManager] Event received: {eventName} - {jsonData}");

            switch (eventName)
            {
                case "EggCaptured":
                    HandleEggCaptured(jsonData);
                    break;
                case "MVCUpdated":
                    HandleMVCUpdated(jsonData);
                    break;
                case "ReturnToMenu":
                    ReturnToAndroid();
                    break;
            }
        }

        private void HandleSetMode(string json)
        {
            try
            {
                var match = System.Text.RegularExpressions.Regex.Match(json, "\"mode\"\\s*:\\s*\"([^\"]+)\"");
                if (match.Success)
                    LoadSceneForMode(match.Groups[1].Value);
            }
            catch (System.Exception e)
            {
                Debug.LogWarning($"[GameManager] HandleSetMode: {e.Message}");
            }
        }

        private void HandleEggCaptured(string jsonData)
        {
            Debug.Log($"[GameManager] Egg captured: {jsonData}");
        }

        private void HandleMVCUpdated(string jsonData)
        {
            Debug.Log($"[GameManager] MVC updated: {jsonData}");
        }

        // ── Esplora: ricezione POI da Android (Overpass) ─────────────
        public void OnPoisReceived(string jsonData)
        {
            Debug.Log($"[GameManager] PoisReceived: {jsonData}");
            var em = ExploreManager.Instance;
            if (em != null) em.OnPoisReceived(jsonData);
        }

        public void OnPoisFailed(string message)
        {
            Debug.LogWarning($"[GameManager] Pois fetch failed: {message}");
            var em = ExploreManager.Instance;
            if (em != null) em.OnPoisFailed(message);
        }

        /// <summary>Avanzamento del caricamento POI a fasi (barra in Esplora).</summary>
        public void OnPoisProgress(string jsonData)
        {
            var em = ExploreManager.Instance;
            if (em != null) em.OnPoisProgress(jsonData);
        }

        public void RequestPois(double lat, double lng, int radiusMeters)
        {
            Debug.Log($"[GameManager] RequestPois({lat},{lng},{radiusMeters}m)");
            UnityBridge.RequestPoisNearby(lat, lng, radiusMeters);
        }

        // ── MiAcitma: città OSM reale (scena City) ──────────────────

        public void RequestOsmCity(double lat, double lng, int radiusMeters)
        {
            Debug.Log($"[GameManager] RequestOsmCity({lat},{lng},{radiusMeters}m)");
            UnityBridge.LogToAndroid("GameManager", $"RequestOsmCity({lat},{lng},{radiusMeters}m)");
            UnityBridge.RequestOsmCity(lat, lng, radiusMeters);
        }

        public void OnOsmCityReceived(string json)
        {
            Debug.Log($"[GameManager] OsmCityReceived ({json?.Length ?? 0} chars)");
            UnityBridge.LogToAndroid("GameManager", $"OsmCityReceived ({json?.Length ?? 0} chars)");
            if (City.OSM.CityOSMWorld.Instance != null)
                City.OSM.CityOSMWorld.Instance.OnOsmCityReceived(json);
        }

        public void OnOsmCityFetchStarted(string data)
        {
            Debug.Log($"[GameManager] OsmCityFetchStarted ({data})");
            if (City.OSM.CityOSMWorld.Instance != null)
                City.OSM.CityOSMWorld.Instance.OnOsmCityFetchStarted(data);
        }

        public void OnOsmCityFailed(string message)
        {
            Debug.LogWarning($"[GameManager] OsmCity fetch failed: {message}");
            UnityBridge.LogToAndroid("GameManager", $"OsmCity fetch failed: {message}");
            if (City.OSM.CityOSMWorld.Instance != null)
                City.OSM.CityOSMWorld.Instance.OnOsmCityFailed(message);
        }
    }
}