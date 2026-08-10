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
            BootstrapExplore();

            // Se l'Activity Unity è stata lanciata con una modalità (es. Esplora),
            // carica subito la scena giusta (Esplora → Outdoor con personaggio + POI).
            string mode = UnityBridge.GetMode();
            if (!string.IsNullOrEmpty(mode))
            {
                LoadSceneForMode(mode);
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
                _ => null
            };
            if (scene != null && SceneManager.GetActiveScene().name != scene)
            {
                SceneManager.LoadScene(scene);
                if (scene == "Outdoor")
                    NeedsHUD.EnsureInstance();
                else
                    NeedsHUD.DestroyInstance();
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

        public void OnEvent(string eventName, string jsonData)
        {
            Debug.Log($"[GameManager] Event received: {eventName} - {jsonData}");

            // BridgeActivity invia {"action":"setMode","mode":"..."} come eventName.
            if (!string.IsNullOrEmpty(eventName) && eventName.Contains("\"setMode\"", StringComparison.Ordinal))
            {
                HandleSetMode(eventName);
                return;
            }

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
    }
}