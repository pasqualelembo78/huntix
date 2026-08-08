using UnityEngine;
using UnityEngine.AI;
using Huntix.Bridge;
using System.Collections.Generic;

namespace Huntix.Indoor
{
    /// <summary>
    /// Furniture placement system for indoor stores.
    /// Players can place unlocked furniture items in the store layout.
    /// Uses raycast for placement surface detection and NavMesh for validation.
    /// </summary>
    public class FurniturePlacer : MonoBehaviour
    {
        public static FurniturePlacer Instance { get; private set; }

        [Header("Placement")]
        public float placementHeight = 0f;
        public float gridSize = 0.5f;
        public float maxPlacementRange = 6f;
        public LayerMask placementLayer = ~0;

        [Header("Visual Feedback")]
        public Material previewValidMaterial;
        public Material previewInvalidMaterial;
        public Color validColor = new Color(0f, 1f, 0f, 0.5f);
        public Color invalidColor = new Color(1f, 0f, 0f, 0.5f);

        [Header("Unlocked Furniture")]
        public List<FurnitureItem> unlockedItems = new List<FurnitureItem>();

        private bool _isPlacing;
        private FurnitureItem _currentItem;
        private GameObject _previewObject;
        private Camera _cam;
        private Transform _playerTransform;
        private readonly List<GameObject> _placedFurniture = new List<GameObject>();

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
        }

        private void Start()
        {
            _cam = Camera.main;
            var player = GameObject.FindWithTag("Player");
            if (player != null) _playerTransform = player.transform;

            // Default unlocked items (can be extended via inventory system)
            if (unlockedItems.Count == 0)
                InitDefaultItems();
        }

        private void InitDefaultItems()
        {
            unlockedItems.Add(new FurnitureItem { id = "table_small", name = "Tavolo piccolo", emoji = "🪑",
                prefabName = "shelf-end", scale = new Vector3(0.6f, 0.35f, 0.6f), cost = 0 });
            unlockedItems.Add(new FurnitureItem { id = "plant", name = "Pianta", emoji = "🌿",
                prefabName = "display-fruit", scale = new Vector3(0.3f, 0.6f, 0.3f), cost = 0 });
            unlockedItems.Add(new FurnitureItem { id = "shelf_wall", name = "Scaffale a muro", emoji = "📚",
                prefabName = "shelf-boxes", scale = new Vector3(0.8f, 1f, 0.4f), cost = 0 });
        }

        private void Update()
        {
            if (!_isPlacing || _previewObject == null) return;

            // Raycast from screen center to find placement position
            var ray = _cam.ScreenPointToRay(new Vector3(Screen.width / 2f, Screen.height / 2f));
            if (Physics.Raycast(ray, out var hit, maxPlacementRange, placementLayer))
            {
                // Snap to grid
                var pos = hit.point;
                pos.x = Mathf.Round(pos.x / gridSize) * gridSize;
                pos.y = placementHeight;
                pos.z = Mathf.Round(pos.z / gridSize) * gridSize;

                // Check distance from player
                float dist = _playerTransform != null
                    ? Vector3.Distance(_playerTransform.position, pos)
                    : 0f;

                bool valid = dist <= maxPlacementRange && IsPositionClear(pos);

                _previewObject.transform.position = pos;
                SetPreviewColor(valid ? validColor : invalidColor);
            }
        }

        /// <summary>
        /// Start placing a furniture item. Called from Android UI.
        /// </summary>
        public void StartPlacement(string itemId)
        {
            var item = unlockedItems.Find(i => i.id == itemId);
            if (item == null)
            {
                Debug.LogWarning($"[FurniturePlacer] Item not found: {itemId}");
                return;
            }

            CancelPlacement();
            _currentItem = item;
            _isPlacing = true;

            // Create preview object
            var prefab = Resources.Load<GameObject>("KenneyMiniMarket/" + item.prefabName);
            if (prefab != null)
            {
                _previewObject = Instantiate(prefab);
                _previewObject.transform.localScale = item.scale;
                _previewObject.name = "FurniturePreview";

                // Make it semi-transparent
                foreach (var renderer in _previewObject.GetComponentsInChildren<Renderer>())
                {
                    foreach (var mat in renderer.materials)
                    {
                        mat.SetFloat("_Mode", 3); // Transparent mode
                        mat.SetInt("_SrcBlend", (int)UnityEngine.Rendering.BlendMode.SrcAlpha);
                        mat.SetInt("_DstBlend", (int)UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha);
                        mat.SetInt("_ZWrite", 0);
                        mat.DisableKeyword("_ALPHATEST_ON");
                        mat.EnableKeyword("_ALPHABLEND_ON");
                        mat.DisableKeyword("_ALPHAPREMULTIPLY_ON");
                        mat.renderQueue = 3000;
                    }
                }
            }

            UnityBridge.SendMessageToAndroid("FurniturePlacementStarted",
                $"{{\"id\":\"{item.id}\",\"name\":\"{item.name}\",\"emoji\":\"{item.emoji}\"}}");
            Debug.Log($"[FurniturePlacer] Starting placement: {item.name}");
        }

        /// <summary>
        /// Confirm current placement. Called from Android when player taps confirm.
        /// </summary>
        public void ConfirmPlacement()
        {
            if (!_isPlacing || _previewObject == null) return;

            var pos = _previewObject.transform.position;
            var rot = _previewObject.transform.rotation;

            // Make permanent
            _previewObject.transform.localScale = _currentItem.scale;

            // Remove preview materials
            foreach (var renderer in _previewObject.GetComponentsInChildren<Renderer>())
            {
                foreach (var mat in renderer.materials)
                {
                    mat.SetFloat("_Mode", 0); // Opaque mode
                    mat.SetInt("_SrcBlend", (int)UnityEngine.Rendering.BlendMode.One);
                    mat.SetInt("_DstBlend", (int)UnityEngine.Rendering.BlendMode.Zero);
                    mat.SetInt("_ZWrite", 1);
                    mat.DisableKeyword("_ALPHABLEND_ON");
                    mat.renderQueue = -1;
                }
            }

            _previewObject.name = $"Furniture_{_currentItem.id}_{_placedFurniture.Count}";
            _placedFurniture.Add(_previewObject);

            // Add collider for interaction
            if (_previewObject.GetComponent<Collider>() == null)
                _previewObject.AddComponent<BoxCollider>();

            UnityBridge.SendMessageToAndroid("FurniturePlacementConfirmed",
                $"{{\"id\":\"{_currentItem.id}\",\"pos\":\"{pos.x},{pos.y},{pos.z}\"}}");

            Debug.Log($"[FurniturePlacer] Placed: {_currentItem.name} at {pos}");
            _isPlacing = false;
            _previewObject = null;
            _currentItem = null;
        }

        /// <summary>
        /// Cancel current placement.
        /// </summary>
        public void CancelPlacement()
        {
            if (_previewObject != null) Destroy(_previewObject);
            _isPlacing = false;
            _previewObject = null;
            _currentItem = null;

            UnityBridge.SendMessageToAndroid("FurniturePlacementCancelled", "{}");
        }

        private bool IsPositionClear(Vector3 pos)
        {
            // Check for overlapping colliders
            return !Physics.CheckBox(pos, new Vector3(0.3f, 0.5f, 0.3f));
        }

        private void SetPreviewColor(Color color)
        {
            if (_previewObject == null) return;
            foreach (var renderer in _previewObject.GetComponentsInChildren<Renderer>())
            {
                foreach (var mat in renderer.materials)
                {
                    mat.color = color;
                }
            }
        }

        /// <summary>
        /// Returns list of placed furniture for saving.
        /// </summary>
        public List<PlacedFurniture> GetPlacedFurniture()
        {
            var result = new List<PlacedFurniture>();
            foreach (var obj in _placedFurniture)
            {
                if (obj != null)
                {
                    result.Add(new PlacedFurniture
                    {
                        id = obj.name,
                        position = $"{obj.transform.position.x},{obj.transform.position.y},{obj.transform.position.z}",
                        rotation = $"{obj.transform.rotation.x},{obj.transform.rotation.y},{obj.transform.rotation.z},{obj.transform.rotation.w}",
                        scale = $"{obj.transform.localScale.x},{obj.transform.localScale.y},{obj.transform.localScale.z}"
                    });
                }
            }
            return result;
        }

        [System.Serializable]
        public class FurnitureItem
        {
            public string id;
            public string name;
            public string emoji;
            public string prefabName;
            public Vector3 scale;
            public int cost;
        }

        [System.Serializable]
        public class PlacedFurniture
        {
            public string id;
            public string position;
            public string rotation;
            public string scale;
        }
    }
}
