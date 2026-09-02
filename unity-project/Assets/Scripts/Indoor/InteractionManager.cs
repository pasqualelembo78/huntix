using UnityEngine;
using UnityEngine.AI;
using Huntix.Bridge;
using System.Collections.Generic;

namespace Huntix.Indoor
{
    /// <summary>
    /// Manages interaction detection in indoor scenes.
    /// Casts a ray from the player's forward direction and finds InteractionComponents.
    /// Notifies Android when an interactable is in range or when the player triggers it.
    /// </summary>
    public class InteractionManager : MonoBehaviour
    {
        public static InteractionManager Instance { get; private set; }

        [Header("Detection")]
        public float detectionRange = 3f;
        public float raycastAngle = 45f;
        public LayerMask interactionLayer = ~0;

        [Header("References")]
        public Transform playerTransform;
        public Camera playerCamera;

        private InteractionComponent _currentTarget;
        private readonly List<InteractionComponent> _allInteractables = new List<InteractionComponent>();
        private bool _interactionPending;

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
        }

        private void Start()
        {
            if (playerTransform == null)
            {
                var player = GameObject.FindWithTag("Player");
                if (player != null) playerTransform = player.transform;
            }
            if (playerCamera == null)
                playerCamera = Camera.main;

            // Find all interactables in scene
            RefreshInteractables();
        }

        public void RefreshInteractables()
        {
            _allInteractables.Clear();
            _allInteractables.AddRange(FindObjectsOfType<InteractionComponent>());
            Debug.Log($"[InteractionManager] Found {_allInteractables.Count} interactables");
        }

        private void Update()
        {
            if (playerTransform == null) return;

            // Scan for nearest interactable in front of player
            InteractionComponent nearest = null;
            float nearestDist = detectionRange;

            foreach (var obj in _allInteractables)
            {
                if (obj == null) continue;

                float dist = Vector3.Distance(playerTransform.position, obj.transform.position);
                if (dist > detectionRange) continue;

                // Check angle (must be roughly in front of the player)
                Vector3 dir = (obj.transform.position - playerTransform.position).normalized;
                float angle = Vector3.Angle(playerTransform.forward, dir);
                if (angle > raycastAngle) continue;

                if (dist < nearestDist)
                {
                    nearestDist = dist;
                    nearest = obj;
                }
            }

            // Update highlight
            if (_currentTarget != nearest)
            {
                if (_currentTarget != null) _currentTarget.SetHighlight(false);
                _currentTarget = nearest;
                if (_currentTarget != null) _currentTarget.SetHighlight(true);

                // Notify Android
                if (_currentTarget != null)
                {
                    UnityBridge.SendMessageToAndroid("IndoorInteractable",
                        $"{{\"found\":true,\"data\":{_currentTarget.ToJson()}}}");
                }
                else
                {
                    UnityBridge.SendMessageToAndroid("IndoorInteractable",
                        "{\"found\":false}");
                }
            }
        }

        /// <summary>
        /// Called from Android when the player taps the interaction button.
        /// Triggers the action on the current target.
        /// </summary>
        public void TriggerInteraction()
        {
            if (_currentTarget == null)
            {
                Debug.Log("[InteractionManager] No interactable in range");
                return;
            }

            Debug.Log($"[InteractionManager] Interacting with: {_currentTarget.interactionName} ({_currentTarget.action})");

            // Send result to Android
            UnityBridge.SendMessageToAndroid("IndoorInteractionResult", _currentTarget.ToJson());

            // Apply need effect via LocalNeeds (Unity → Android → LocalNeeds.applyAction)
            if (!string.IsNullOrEmpty(_currentTarget.need) && _currentTarget.gain > 0)
            {
                Debug.Log($"[InteractionManager] Applying need: {_currentTarget.need} +{_currentTarget.gain}");
                var updatedNeeds = UnityBridge.ApplyNeedAction(_currentTarget.need, _currentTarget.gain);
                Debug.Log($"[InteractionManager] Needs updated: {updatedNeeds}");
                UnityBridge.SendMessageToAndroid("IndoorNeedsUpdated", updatedNeeds);
            }

            // Visual feedback: disable highlight temporarily
            _currentTarget.SetHighlight(false);

            // Only destroy consumable actions (collect, buy); talk/heal stay active
            bool consumable = _currentTarget.action == "collect" || _currentTarget.action == "buy";
            if (consumable)
            {
                Destroy(_currentTarget.gameObject, 0.3f);
                _allInteractables.Remove(_currentTarget);
            }

            _currentTarget = null;

            UnityBridge.SendMessageToAndroid("IndoorInteractable", "{\"found\":false}");
        }

        public InteractionComponent GetCurrentTarget() => _currentTarget;
    }
}
