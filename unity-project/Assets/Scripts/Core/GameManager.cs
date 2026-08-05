using UnityEngine;
using UnityEngine.SceneManagement;
using System;
using Huntix.Bridge;

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

        private void HandleEggCaptured(string jsonData)
        {
            Debug.Log($"[GameManager] Egg captured: {jsonData}");
        }

        private void HandleMVCUpdated(string jsonData)
        {
            Debug.Log($"[GameManager] MVC updated: {jsonData}");
        }
    }
}