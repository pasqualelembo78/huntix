using UnityEngine;
using UnityEngine.UI;
using TMPro;
using Huntix.Outdoor;

namespace Huntix.UI
{
    public class EggIndicator : MonoBehaviour
    {
        [Header("UI References")]
        public Image eggIcon;
        public TextMeshProUGUI distanceText;
        public Button captureButton;

        [Header("Settings")]
        public float maxDistance = 10f;
        public Color defaultColor = Color.white;
        public Color nearColor = Color.green;

        private EggSpawner.EggData _egg;
        private Transform _playerTransform;

        private void Start()
        {
            if (captureButton != null)
            {
                captureButton.onClick.AddListener(OnCaptureClicked);
            }
        }

        public void Setup(EggSpawner.EggData egg)
        {
            _egg = egg;
            _playerTransform = Camera.main?.transform;

            UpdateDistance();

            if (egg.visualObject != null)
            {
                Vector3 eggPos = egg.visualObject.transform.position;
                transform.position = eggPos + Vector3.up;
                LookAtPlayer();
            }
        }

        private void Update()
        {
            if (_egg == null) return;

            UpdateDistance();
            CheckCaptureDistance();
            LookAtPlayer();
        }

        private void UpdateDistance()
        {
            if (_playerTransform != null && distanceText != null)
            {
                float distance = Vector3.Distance(_playerTransform.position, _egg.position);
                distanceText.text = $"{distance:F1}m";

                if (distance <= maxDistance)
                {
                    if (distanceText.color != nearColor)
                    {
                        distanceText.color = nearColor;
                    }
                }
                else
                {
                    if (distanceText.color != defaultColor)
                    {
                        distanceText.color = defaultColor;
                    }
                }
            }
        }

        private void CheckCaptureDistance()
        {
            if (_playerTransform != null)
            {
                float distance = Vector3.Distance(_playerTransform.position, _egg.position);
                if (distance <= maxDistance)
                {
                    if (captureButton != null && !captureButton.interactable)
                    {
                        captureButton.interactable = true;
                        var cg = captureButton.GetComponent<CanvasGroup>();
                        if (cg != null) cg.alpha = 1f;
                    }
                }
                else
                {
                    if (captureButton != null && captureButton.interactable)
                    {
                        captureButton.interactable = false;
                        var cg = captureButton.GetComponent<CanvasGroup>();
                        if (cg != null) cg.alpha = 0.5f;
                    }
                }
            }
        }

        private void LookAtPlayer()
        {
            if (_playerTransform != null)
            {
                transform.LookAt(_playerTransform.position);
                transform.Rotate(0, 180, 0);
            }
        }

        private void OnCaptureClicked()
        {
            if (_egg != null && _egg.isActive)
            {
                _egg.isActive = false;
                Debug.Log($"[EggIndicator] Player captured egg {_egg.id}");
                EggSpawner.Instance.CaptureEgg(_egg.id);
                Destroy(gameObject);
            }
        }
    }
}