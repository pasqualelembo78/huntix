using UnityEngine;
using System.Collections;
using System.Collections.Generic;
using System.Globalization;
using System.Text.RegularExpressions;
using Huntix.Core;

namespace Huntix.Outdoor
{
    public class OutdoorManager : MonoBehaviour
    {
        public static OutdoorManager Instance { get; private set; }

        [Header("Spawn Settings")]
        public float spawnRadius = 100f;
        public float eggRespawnInterval = 300f;
        public int maxActiveEggs = 20;

        [Header("Location (reale/mock)")]
        public float locationRefreshSeconds = 5f;
        private double _playerLat = 41.902756;   // default Roma
        private double _playerLng = 12.493429;
        private bool _mockMode = false;
        private bool _locationReady = false;

        [Header("Weather")]
        public WeatherType currentWeather;
        public float weatherChangeInterval = 600f;

        private List<EggData> _activeEggs;
        private float _spawnTimer;
        private float _weatherTimer;

        [System.Serializable]
        public class EggData
        {
            public string id;
            public double latitude;
            public double longitude;
            public float rarity;
            public float captureRadius;
            public bool isActive;
            public long spawnTime;
            public EggRarity rarityData;
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            _activeEggs = new List<EggData>();
            _spawnTimer = 0f;
            _weatherTimer = 0f;
            StartCoroutine(RefreshLocationCoroutine());
        }

        private void Update()
        {
            _spawnTimer += Time.deltaTime;
            _weatherTimer += Time.deltaTime;

            if (_spawnTimer >= eggRespawnInterval)
            {
                _spawnTimer = 0f;
                RespawnEggs();
            }

            if (_weatherTimer >= weatherChangeInterval)
            {
                _weatherTimer = 0f;
                ChangeWeather();
            }

            CleanupExpiredEggs();
        }

        private IEnumerator RefreshLocationCoroutine()
        {
            var wait = new WaitForSeconds(locationRefreshSeconds);
            while (true)
            {
                RefreshLocation();
                yield return wait;
            }
        }

        private void RefreshLocation()
        {
            if (!Application.isMobilePlatform) return;
            string json = Huntix.Bridge.UnityBridge.GetCurrentLocation();
            ParseLocationJson(json);
            _locationReady = true;
        }

        private void ParseLocationJson(string json)
        {
            double lat = _playerLat, lng = _playerLng;
            bool mock = _mockMode;
            try
            {
                Match m;
                m = Regex.Match(json, "\"lat\"\\s*:\\s*(-?[0-9.]+)");
                if (m.Success) double.TryParse(m.Groups[1].Value, NumberStyles.Float, CultureInfo.InvariantCulture, out lat);
                m = Regex.Match(json, "\"lng\"\\s*:\\s*(-?[0-9.]+)");
                if (m.Success) double.TryParse(m.Groups[1].Value, NumberStyles.Float, CultureInfo.InvariantCulture, out lng);
                m = Regex.Match(json, "\"mock\"\\s*:\\s*(true|false)");
                if (m.Success) bool.TryParse(m.Groups[1].Value, out mock);
            }
            catch { /* usa valori precedenti */ }
            _playerLat = lat; _playerLng = lng; _mockMode = mock;
        }

        private void RespawnEggs()
        {
            int activeCount = 0;
            foreach (var egg in _activeEggs) if (egg.isActive) activeCount++;
            int toSpawn = Mathf.Max(0, maxActiveEggs - activeCount);
            for (int i = 0; i < toSpawn; i++) SpawnEgg();
        }

        private void SpawnEgg()
        {
            var egg = new EggData
            {
                id = System.Guid.NewGuid().ToString(),
                // Coordinate reali del giocatore + offset casuale (rispetto a GPS reale/mock)
                latitude = _playerLat + (UnityEngine.Random.Range(0f, 1f) - 0.5f) * (spawnRadius / 111000f),
                longitude = _playerLng + (UnityEngine.Random.Range(0f, 1f) - 0.5f) * (spawnRadius / 111000f),
                rarity = UnityEngine.Random.Range(0f, 1f),
                captureRadius = 5f,
                isActive = true,
                spawnTime = System.DateTimeOffset.UtcNow.ToUnixTimeSeconds(),
                rarityData = GetRandomRarity()
            };
            _activeEggs.Add(egg);
            Debug.Log($"[OutdoorManager] Spawned egg {egg.id} at ({egg.latitude},{egg.longitude}) rarity={egg.rarityData.ToString()} mock={_mockMode}");
        }

        private EggRarity GetRandomRarity()
        {
            float r = Random.Range(0f, 1f);
            if (r < 0.02f) return EggRarity.Legendary;
            if (r < 0.10f) return EggRarity.Epic;
            if (r < 0.30f) return EggRarity.Rare;
            if (r < 0.60f) return EggRarity.Uncommon;
            return EggRarity.Common;
        }

        private void ChangeWeather()
        {
            var types = System.Enum.GetValues(typeof(WeatherType));
            currentWeather = (WeatherType)types.GetValue(Random.Range(0, types.Length));
            Debug.Log($"[OutdoorManager] Weather changed to {currentWeather}");
        }

        private void CleanupExpiredEggs()
        {
            long now = System.DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            _activeEggs.RemoveAll(e => e.isActive && (now - e.spawnTime) > 3600);
        }

        public List<EggData> GetActiveEggs() => _activeEggs.FindAll(e => e.isActive);

        public void CaptureEgg(string eggId)
        {
            var egg = _activeEggs.Find(e => e.id == eggId);
            if (egg != null)
            {
                egg.isActive = false;
                Debug.Log($"[OutdoorManager] Egg {eggId} captured!");
                // Notifica Android: cattura sincronizzata con CatchEngine/Kotlin
                var json = $"{{\"id\":\"{eggId}\",\"lat\":{egg.latitude.ToString(CultureInfo.InvariantCulture)},\"lng\":{egg.longitude.ToString(CultureInfo.InvariantCulture)},\"rarity\":\"{egg.rarityData}\"}}";
                Huntix.Bridge.UnityBridge.SendMessageToAndroid("Catch", json);
            }
        }
    }
}
