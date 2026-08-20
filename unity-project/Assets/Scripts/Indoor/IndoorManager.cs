using UnityEngine;
using UnityEngine.AI;
using System.Collections.Generic;
using System.Linq;
using Huntix.Bridge;

namespace Huntix.Indoor
{
    public class IndoorManager : MonoBehaviour
    {
        public static IndoorManager Instance { get; private set; }

        [Header("Building Settings")]
        public float interactionRange = 3f;
        public LayerMask interactableLayer;

        [Header("Store Builder")]
        public StoreBuilder storeBuilder;

        // Riferimenti espliciti ai prefab "Cute Supermarket Lite" (cibo/prodotti),
        // popolati a build-time da CuteAssetsSetup. Evitano la dipendenza da
        // Resources.Load (che nel build AAR non risolve gli asset di Resources).
        public GameObject[] cuteDecorPrefabs;

        private List<BuildingDef> _currentBuildings;
        private NavMeshAgent _playerAgent;
        private Transform _playerTransform;
        private string _currentPoiType = "";

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

            // Try to find StoreBuilder in scene
            if (storeBuilder == null)
                storeBuilder = FindObjectOfType<StoreBuilder>();

            // Fallback: read POI data from Android intent if LoadStoreFromPOI wasn't called via UnitySendMessage
            if (storeBuilder == null)
            {
                var builderObj = new GameObject("StoreBuilder");
                storeBuilder = builderObj.AddComponent<StoreBuilder>();
            }

            // Ensure InteractionManager exists (needed for the interact button from IndoorActivity)
            if (FindObjectOfType<InteractionManager>() == null)
            {
                var imObj = new GameObject("InteractionManager");
                imObj.AddComponent<InteractionManager>();
                Debug.Log("[IndoorManager] InteractionManager auto-created");
            }

            // Ensure NPCDispatcher exists (GameObject named "NPC" per UnitySendMessage routing)
            var npcDispatcher = GameObject.Find("NPC");
            if (npcDispatcher == null)
            {
                npcDispatcher = new GameObject("NPC");
                npcDispatcher.AddComponent<NPCDispatcher>();
                Debug.Log("[IndoorManager] NPCDispatcher auto-created");
            }

            // Try to read POI data from the Android activity intent
            try
            {
                using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                {
                    var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
                    var intent = activity.Call<AndroidJavaObject>("getIntent");
                    var poiData = intent.Call<string>("getStringExtra", "POI_DATA");
                    if (!string.IsNullOrEmpty(poiData) && poiData != "{}")
                    {
                        LoadStoreFromPOI(poiData);
                    }
                }
            }
            catch (System.Exception e)
            {
                Debug.Log($"[IndoorManager] No POI_DATA in intent: {e.Message}");
            }
        }

        /// <summary>
        /// Called from Android to load a store from POI JSON data.
        /// Builds the interior using Kenney Mini Market assets.
        /// </summary>
        public void LoadStoreFromPOI(string poiJson)
        {
            Debug.Log($"[IndoorManager] LoadStoreFromPOI: {poiJson}");
            try { using (var log = new AndroidJavaClass("android.util.Log")) log.CallStatic<int>("d", "HuntixUnity", "[Indoor] LoadStoreFromPOI poiType=" + (_currentPoiType ?? "") + " json=" + poiJson); } catch {}

            var data = JsonUtility.FromJson<PoiData>(poiJson);
            _currentPoiType = data.type ?? "";
            if (string.IsNullOrEmpty(_currentPoiType))
                _currentPoiType = data.buildingType ?? "";

            if (storeBuilder == null)
            {
                var builderObj = new GameObject("StoreBuilder");
                storeBuilder = builderObj.AddComponent<StoreBuilder>();
            }

            storeBuilder.cuteDecorPrefabs = cuteDecorPrefabs;
            storeBuilder.BuildStore(_currentPoiType,
                System.StringComparer.OrdinalIgnoreCase.Equals(data.asset, "cute"));

            // Build NavMesh at runtime for NPC patrol (includes spawned walls as obstacles)
            BuildRuntimeNavMesh();

            // Notify AR manager to place store on AR plane if available
            var arManager = IndoorARManager.Instance;
            if (arManager != null && arManager.isARActive)
            {
                var storeRoot = GameObject.Find("StoreInterior");
                if (storeRoot != null)
                    arManager.OnStoreBuilt(storeRoot);
            }

            // Notify Android that the indoor scene is ready
            UnityBridge.SendMessageToAndroid("IndoorSceneReady",
                $"{{\"poiId\":\"{data.id}\",\"name\":\"{data.name}\",\"type\":\"{_currentPoiType}\"}}");
        }

        public void EnterBuilding(BuildingDef building)
        {
            Debug.Log($"[IndoorManager] Entering building: {building.name}");
            building.isInteriorLoaded = true;
            _currentPoiType = building.buildingType.ToString().ToLower();
            UnityEngine.SceneManagement.SceneManager.LoadScene(building.interiorSceneName);
        }

        /// <summary>
        /// Build NavMesh at runtime after store interior is constructed.
        /// Collects geometry from StoreInterior floor + walls and builds NavMesh
        /// so indoor NPCs can patrol using NavMeshAgent.
        /// </summary>
        private void BuildRuntimeNavMesh()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                var settings = new NavMeshBuildSettings
                {
                    agentTypeID = 0,
                    agentRadius = 0.4f,
                    agentHeight = 1.5f,
                    agentSlope = 45,
                    agentClimb = 0.4f,
                    minRegionArea = 2,
                    tileSize = 256,
                    buildHeightMesh = false
                };

                var sources = new List<NavMeshBuildSource>();
                var markups = new List<NavMeshBuildMarkup>();

                // Collect from StoreInterior (floor + walls + obstacles)
                var storeRoot = GameObject.Find("StoreInterior");
                if (storeRoot != null)
                {
                    NavMeshBuilder.CollectSources(storeRoot.transform, 0, NavMeshCollectGeometry.PhysicsColliders, 0, markups, sources);
                }
                else
                {
                    // Fallback: collect from scene's static Floor
                    var floorObj = GameObject.Find("Floor");
                    if (floorObj != null)
                    {
                        NavMeshBuilder.CollectSources(floorObj.transform, 0, NavMeshCollectGeometry.PhysicsColliders, 0, markups, sources);
                    }
                }

                if (sources.Count == 0)
                {
                    Debug.LogWarning("[IndoorManager] Nessuna geometria trovata per NavMesh build");
                }

                var data = NavMeshBuilder.BuildNavMeshData(settings, sources, CalculateNavMeshBounds(), Vector3.zero, Quaternion.identity);
                if (data != null)
                {
                    Debug.Log($"[IndoorManager] NavMesh build OK: {sources.Count} source, data={data.name}");

                    // Warp NPCs that aren't yet on NavMesh
                    foreach (var npcObj in FindObjectsOfType<GameObject>()
                        .Where(g => g.name.StartsWith("NPC_")))
                    {
                        if (npcObj.TryGetComponent<NavMeshAgent>(out var agent) && !agent.isOnNavMesh)
                        {
                            if (NavMesh.SamplePosition(npcObj.transform.position, out var hit, 2f, 0))
                                agent.Warp(hit.position);
                        }
                    }
                }
                else
                {
                    Debug.LogWarning("[IndoorManager] NavMesh.BuildNavMesh ritornato null");
                }
            }
            catch (System.Exception e)
            {
                Debug.LogWarning($"[IndoorManager] NavMesh build fallito (gli NPC resteranno feri): {e.Message}");
            }
            #else
            Debug.Log("[IndoorManager] BuildRuntimeNavMesh saltato (editor/non-android)");
            #endif
        }

        private Bounds CalculateNavMeshBounds()
        {
            var root = GameObject.Find("StoreInterior") ?? GameObject.Find("Floor");
            if (root != null)
            {
                var bounds = new Bounds(root.transform.position, Vector3.zero);
                foreach (var renderer in root.GetComponentsInChildren<Renderer>())
                {
                    bounds.Encapsulate(renderer.bounds);
                }
                if (bounds.size.magnitude > 0.01f)
                {
                    bounds.Expand(2f);
                    return bounds;
                }
            }
            return new Bounds(Vector3.zero, Vector3.one * 100f);
        }

        public void ExitBuilding()
        {
            Debug.Log("[IndoorManager] Exiting building, returning to outdoor");
            if (storeBuilder != null)
                storeBuilder.ClearStore();
            UnityBridge.SendMessageToAndroid("ExitIndoor", "{}");
        }

        public void InteractWithBuilding(BuildingDef building)
        {
            if (_playerTransform == null) return;
            if (Vector3.Distance(_playerTransform.position, building.position) <= interactionRange)
            {
                switch (building.buildingType)
                {
                    case BuildingType.RESTAURANT:
                    case BuildingType.SUPERMARKET:
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

         [System.Serializable]
         private class PoiData
         {
             public string id;
             public string name;
             public string type;
             public string buildingType;
             public string asset;
         }

        // ── Player movement ──
        [Header("Player Movement")]
        public float moveSpeed = 4f;

        /// <summary>
        /// Called from Android (UnitySendMessage) to move the player.
        /// Input format: "dx,dy" where values are -1..1 from the joystick.
        /// </summary>
        public void MovePlayer(string input)
        {
            try
            {
                var parts = input.Split(',');
                float dx = float.Parse(parts[0], System.Globalization.CultureInfo.InvariantCulture);
                float dy = float.Parse(parts[1], System.Globalization.CultureInfo.InvariantCulture);

                if (_playerAgent != null && _playerAgent.enabled && _playerAgent.isOnNavMesh)
                {
                    // Use NavMeshAgent for movement (respects obstacles)
                    var direction = new Vector3(dx, 0, dy);
                    _playerAgent.velocity = direction * moveSpeed;
                }
                else if (_playerTransform != null)
                {
                    // Fallback: direct transform movement
                    var move = new Vector3(dx, 0, dy) * moveSpeed * Time.deltaTime;
                    _playerTransform.Translate(move, Space.World);
                }
            }
            catch (System.Exception e)
            {
                Debug.LogWarning($"[IndoorManager] MovePlayer error: {e.Message}");
            }
        }
    }
}
