using UnityEngine;
using System.Collections;
using Huntix.Core;
using Huntix.UI;
using Huntix.Bridge;

namespace Huntix.Outdoor
{
    public class EggSpawner : MonoBehaviour
    {
        public static EggSpawner Instance { get; private set; }

        [Header("Spawn Settings")]
        public float spawnInterval = 300f;
        public int maxActiveEggs = 20;
        public Transform spawnPoint;

        [Header("Prefabs")]
        public GameObject eggPrefab;
        public GameObject eggIndicatorPrefab;

        private EggData[] _activeEggs;
        private int _eggCount;

        [System.Serializable]
        public class EggData
        {
            public string id;
            public Vector3 position;
            public GameObject visualObject;
            public GameObject indicatorObject;
            public bool isActive;
            public long spawnTime;
            public EggRarity rarity;
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
            _activeEggs = new EggData[maxActiveEggs];
            _eggCount = 0;
        }

        private void Start()
        {
            StartCoroutine(SpawnEggsRoutine());
        }

        private IEnumerator SpawnEggsRoutine()
        {
            while (true)
            {
                if (_eggCount < maxActiveEggs)
                {
                    SpawnEgg();
                }
                yield return new WaitForSeconds(spawnInterval);
            }
        }

        private void SpawnEgg()
        {
            if (eggPrefab == null) return;

            var eggObj = Instantiate(eggPrefab, spawnPoint.position, Quaternion.identity, transform);
            var egg = new EggData
            {
                id = System.Guid.NewGuid().ToString(),
                position = eggObj.transform.position,
                visualObject = eggObj,
                isActive = true,
                spawnTime = System.DateTimeOffset.UtcNow.ToUnixTimeSeconds(),
                rarity = GetRandomRarity()
            };

            _activeEggs[_eggCount] = egg;
            _eggCount++;

            CreateIndicator(egg);
            Debug.Log($"[EggSpawner] Spawned egg {egg.id} at {egg.position} rarity={egg.rarity}");
        }

        private void CreateIndicator(EggData egg)
        {
            if (eggIndicatorPrefab == null) return;

            var indicator = Instantiate(eggIndicatorPrefab, egg.position + Vector3.up, Quaternion.identity);
            indicator.name = $"Indicator_{egg.id}";
            egg.indicatorObject = indicator;

            var indicatorScript = indicator.GetComponent<EggIndicator>();
            if (indicatorScript != null)
            {
                indicatorScript.Setup(egg);
            }
        }

        public EggData[] GetActiveEggs()
        {
            var active = new EggData[_eggCount];
            for (int i = 0; i < _eggCount; i++)
            {
                if (_activeEggs[i].isActive)
                {
                    active[i] = _activeEggs[i];
                }
            }
            return active;
        }

        public EggData GetEgg(string eggId)
        {
            for (int i = 0; i < _eggCount; i++)
            {
                if (_activeEggs[i].id == eggId && _activeEggs[i].isActive)
                {
                    return _activeEggs[i];
                }
            }
            return null;
        }

        public void CaptureEgg(string eggId)
        {
            var egg = GetEgg(eggId);
            if (egg != null)
            {
                egg.isActive = false;
                if (egg.visualObject != null)
                {
                    egg.visualObject.SetActive(false);
                }
                if (egg.indicatorObject != null)
                {
                    Destroy(egg.indicatorObject);
                }
                Debug.Log($"[EggSpawner] Egg {eggId} captured!");
                UnityBridge.SendMessageToAndroid("EggCaptured", $"{{\"eggId\":\"{eggId}\",\"rarity\":\"{egg.rarity}\"}}");
            }
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

        private void OnDestroy()
        {
            foreach (var egg in _activeEggs)
            {
                if (egg != null && egg.indicatorObject != null)
                {
                    Destroy(egg.indicatorObject);
                }
            }
        }
    }
}