using UnityEngine;
using System.Collections;
using System.Collections.Generic;
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
            public Vector3 position;
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

        private void RespawnEggs()
        {
            int activeCount = 0;
            foreach (var egg in _activeEggs)
            {
                if (egg.isActive) activeCount++;
            }

            int toSpawn = Mathf.Max(0, maxActiveEggs - activeCount);
            for (int i = 0; i < toSpawn; i++)
            {
                SpawnEgg();
            }
        }

        private void SpawnEgg()
        {
            var egg = new EggData
            {
                id = System.Guid.NewGuid().ToString(),
                position = GetRandomSpawnPosition(),
                rarity = UnityEngine.Random.Range(0f, 1f),
                captureRadius = 5f,
                isActive = true,
                spawnTime = System.DateTimeOffset.UtcNow.ToUnixTimeSeconds(),
                rarityData = GetRandomRarity()
            };

            _activeEggs.Add(egg);
            Debug.Log($"[OutdoorManager] Spawned egg {egg.id} at {egg.position} rarity={egg.rarityData.ToString()}");
        }

        private Vector3 GetRandomSpawnPosition()
        {
            float angle = Random.Range(0f, 360f);
            float distance = Random.Range(10f, spawnRadius);
            float x = Mathf.Cos(angle * Mathf.Deg2Rad) * distance;
            float z = Mathf.Sin(angle * Mathf.Deg2Rad) * distance;
            return new Vector3(x, 0f, z);
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

        public List<EggData> GetActiveEggs()
        {
            return _activeEggs.FindAll(e => e.isActive);
        }

        public void CaptureEgg(string eggId)
        {
            var egg = _activeEggs.Find(e => e.id == eggId);
            if (egg != null)
            {
                egg.isActive = false;
                Debug.Log($"[OutdoorManager] Egg {eggId} captured!");
            }
        }
    }
}