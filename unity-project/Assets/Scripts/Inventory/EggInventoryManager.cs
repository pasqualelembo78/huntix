using UnityEngine;
using System.Collections.Generic;
using Huntix.Bridge;

namespace Huntix.Inventory
{
    public class EggInventoryManager : MonoBehaviour
    {
        public static EggInventoryManager Instance { get; private set; }

        [Header("Inventory Settings")]
        public int maxInventorySize = 50;

        private List<EggEntry> _eggs;
        private Dictionary<string, EggEntry> _eggLookup;

        [System.Serializable]
        public class EggEntry
        {
            public string eggId;
            public string rarityId;
            public string elementType;
            public bool isHatched;
            public long captureTimestamp;
            public Vector3 captureLocation;
            public float captureLatitude;
            public float captureLongitude;
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
            _eggs = new List<EggEntry>();
            _eggLookup = new Dictionary<string, EggEntry>();
        }

        public void AddEgg(EggEntry egg)
        {
            if (_eggs.Count >= maxInventorySize)
            {
                Debug.LogWarning("[EggInventory] Inventory full, cannot add more eggs");
                return;
            }

            if (!_eggLookup.ContainsKey(egg.eggId))
            {
                _eggs.Add(egg);
                _eggLookup.Add(egg.eggId, egg);
                Debug.Log($"[EggInventory] Added egg {egg.eggId} ({egg.rarityId})");
                UnityBridge.SendMessageToAndroid("EggAdded", $"{{\"eggId\":\"{egg.eggId}\",\"rarityId\":\"{egg.rarityId}\"}}");
            }
        }

        public bool RemoveEgg(string eggId)
        {
            var egg = _eggLookup.GetValueOrDefault(eggId);
            if (egg != null)
            {
                _eggs.Remove(egg);
                _eggLookup.Remove(eggId);
                return true;
            }
            return false;
        }

        public EggEntry GetEgg(string eggId)
        {
            _eggLookup.TryGetValue(eggId, out var egg);
            return egg;
        }

        public List<EggEntry> GetAllEggs()
        {
            return new List<EggEntry>(_eggs);
        }

        public List<EggEntry> GetEggsByRarity(string rarityId)
        {
            return _eggs.FindAll(e => e.rarityId == rarityId);
        }

        public List<EggEntry> GetUnhatchedEggs()
        {
            return _eggs.FindAll(e => !e.isHatched);
        }

        public int GetTotalEggCount()
        {
            return _eggs.Count;
        }

        public int GetUniqueRarityCount()
        {
            var rarities = new HashSet<string>();
            foreach (var egg in _eggs)
                rarities.Add(egg.rarityId);
            return rarities.Count;
        }

        public float GetCollectionProgress()
        {
            // Total egg types defined in the game
            int totalTypes = 5; // Common, Uncommon, Rare, Epic, Legendary
            return (float)GetUniqueRarityCount() / totalTypes;
        }

        public void HatEgg(string eggId)
        {
            var egg = _eggLookup.GetValueOrDefault(eggId);
            if (egg != null && !egg.isHatched)
            {
                egg.isHatched = true;
                Debug.Log($"[EggInventory] Egg {eggId} hatched!");
            }
        }

        public void ClearInventory()
        {
            _eggs.Clear();
            _eggLookup.Clear();
        }
    }
}