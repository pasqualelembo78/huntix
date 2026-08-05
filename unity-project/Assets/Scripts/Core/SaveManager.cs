using UnityEngine;
using System;
using System.Collections.Generic;
using Huntix.Bridge;

namespace Huntix.Core
{
    public class SaveManager : MonoBehaviour
    {
        public static SaveManager Instance { get; private set; }

        private const string SAVE_KEY = "huntix_save";
        private const string PREFS_KEY = "huntix_prefs";

        private Dictionary<string, object> _cache;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            _cache = new Dictionary<string, object>();
        }

        public void SaveLocal(string key, string value)
        {
            PlayerPrefs.SetString($"{PREFS_KEY}_{key}", value);
            PlayerPrefs.Save();
        }

        public string LoadLocal(string key, string defaultValue = "")
        {
            return PlayerPrefs.GetString($"{PREFS_KEY}_{key}", defaultValue);
        }

        public void SaveToFirebase(string jsonData)
        {
            UnityBridge.SaveData(jsonData);
        }

        public string LoadFromFirebase()
        {
            return UnityBridge.LoadData();
        }

        public void CacheData(string key, object value)
        {
            if (_cache.ContainsKey(key))
                _cache[key] = value;
            else
                _cache.Add(key, value);
        }

        public T GetCachedData<T>(string key)
        {
            if (_cache.ContainsKey(key) && _cache[key] is T)
                return (T)_cache[key];
            return default;
        }

        public void ClearCache()
        {
            _cache.Clear();
        }
    }
}