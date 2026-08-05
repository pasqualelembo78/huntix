using UnityEngine;
using UnityEngine.AI;
using System.Collections.Generic;
using Huntix.Bridge;

namespace Huntix.Indoor
{
    public class IndoorManager : MonoBehaviour
    {
        public static IndoorManager Instance { get; private set; }

        [Header("Building Settings")]
        public float interactionRange = 3f;
        public LayerMask interactableLayer;

        private List<BuildingDef> _currentBuildings;
        private NavMeshAgent _playerAgent;
        private Transform _playerTransform;

        [System.Serializable]
        public class BuildingDef
        {
            public string buildingId;
            public BuildingType buildingType;
            public string name;
            public Vector3 position;
            public Quaternion rotation;
            public float scale;
            public bool isInteriorLoaded;
            public string interiorSceneName;
        }

        public enum BuildingType
        {
            HOUSE,
            SCHOOL,
            RESTAURANT,
            SUPERMARKET,
            HOSPITAL,
            GYM,
            LIBRARY,
            PARK,
            OTHER
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
            _currentBuildings = new List<BuildingDef>();
        }

        private void Start()
        {
            var playerObj = GameObject.FindWithTag("Player");
            if (playerObj != null)
            {
                _playerAgent = playerObj.GetComponent<NavMeshAgent>();
                _playerTransform = playerObj.transform;
            }
        }

        public void EnterBuilding(BuildingDef building)
        {
            Debug.Log($"[IndoorManager] Entering building: {building.name}");
            building.isInteriorLoaded = true;
            UnityEngine.SceneManagement.SceneManager.LoadScene(building.interiorSceneName);
        }

        public void ExitBuilding()
        {
            Debug.Log("[IndoorManager] Exiting building, returning to outdoor");
            UnityEngine.SceneManagement.SceneManager.LoadScene("Outdoor");
        }

        public void InteractWithBuilding(BuildingDef building)
        {
            if (Vector3.Distance(_playerTransform.position, building.position) <= interactionRange)
            {
                switch (building.buildingType)
                {
                    case BuildingType.RESTAURANT:
                        OpenShop(building);
                        break;
                    case BuildingType.SCHOOL:
                        OpenQuestGiver(building);
                        break;
                    case BuildingType.HOSPITAL:
                        OpenHealStation(building);
                        break;
                    default:
                        OpenDefaultInteraction(building);
                        break;
                }
            }
        }

        private void OpenShop(BuildingDef building)
        {
            Debug.Log($"[IndoorManager] Opening shop at {building.name}");
            UnityBridge.SendMessageToAndroid("OpenShop", $"{{\"buildingId\":\"{building.buildingId}\"}}");
        }

        private void OpenQuestGiver(BuildingDef building)
        {
            Debug.Log($"[IndoorManager] Opening quest giver at {building.name}");
        }

        private void OpenHealStation(BuildingDef building)
        {
            Debug.Log($"[IndoorManager] Opening heal station at {building.name}");
        }

        private void OpenDefaultInteraction(BuildingDef building)
        {
            Debug.Log($"[IndoorManager] Default interaction at {building.name}");
        }

        public void AddBuilding(BuildingDef building)
        {
            _currentBuildings.Add(building);
        }

        public List<BuildingDef> GetNearbyBuildings(Vector3 position, float range)
        {
            var nearby = new List<BuildingDef>();
            foreach (var b in _currentBuildings)
            {
                if (Vector3.Distance(b.position, position) <= range)
                    nearby.Add(b);
            }
            return nearby;
        }
    }
}