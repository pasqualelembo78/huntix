using UnityEngine;
using System;
using System.Collections.Generic;

namespace Huntix.Core
{
    public class SavedManager : MonoBehaviour
    {
        public static SavedManager Instance { get; private set; }

        private const string SAVE_VERSION = "1.0";
        private const string SAVE_FILE = "huntix_save.json";

        [System.Serializable]
        public class SaveData
        {
            public string version;
            public string playerId;
            public int level;
            public int xp;
            public int totalMVC;
            public int totalEggsCaught;
            public List<string> collectedEggIds;
            public List<string> completedTaskIds;
            public Dictionary<string, object> preferences;
            public long lastSaveTimestamp;
        }

        private SaveData _currentSave;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            _currentSave = new SaveData();
        }

        public void NewSave()
        {
            _currentSave = new SaveData
            {
                version = SAVE_VERSION,
                playerId = "",
                level = 1,
                xp = 0,
                totalMVC = 0,
                totalEggsCaught = 0,
                collectedEggIds = new List<string>(),
                completedTaskIds = new List<string>(),
                preferences = new Dictionary<string, object>(),
                lastSaveTimestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
            };
        }

        public void Save()
        {
            _currentSave.lastSaveTimestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            var json = JsonUtility.ToJson(_currentSave, true);
            SaveManager.Instance.SaveToFirebase(json);
            PlayerPrefs.SetString(SAVE_FILE, json);
            PlayerPrefs.Save();
            Debug.Log("[SavedManager] Save completed");
        }

        public bool Load()
        {
            var json = PlayerPrefs.GetString(SAVE_FILE, "");
            if (!string.IsNullOrEmpty(json))
            {
                try
                {
                    _currentSave = JsonUtility.FromJson<SaveData>(json);
                    Debug.Log($"[SavedManager] Save loaded (version {_currentSave.version})");
                    return true;
                }
                catch (Exception e)
                {
                    Debug.LogError($"[SavedManager] Failed to load save: {e.Message}");
                    return false;
                }
            }
            return false;
        }

        public SaveData GetCurrentSave()
        {
            return _currentSave;
        }

        public void SetPlayerId(string playerId)
        {
            _currentSave.playerId = playerId;
        }

        public void AddCollectedEgg(string eggId)
        {
            if (!_currentSave.collectedEggIds.Contains(eggId))
            {
                _currentSave.collectedEggIds.Add(eggId);
            }
        }

        public void AddCompletedTask(string taskId)
        {
            if (!_currentSave.completedTaskIds.Contains(taskId))
            {
                _currentSave.completedTaskIds.Add(taskId);
            }
        }
    }
}