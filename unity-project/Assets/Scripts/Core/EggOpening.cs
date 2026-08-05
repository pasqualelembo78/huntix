using UnityEngine;
using UnityEngine.UI;
using TMPro;
using System.Collections;
using Huntix.Outdoor;
using Huntix.Bridge;

namespace Huntix.Core
{
    public class EggOpening : MonoBehaviour
    {
        [Header("UI References")]
        public GameObject openingPanel;
        public Slider crackProgressSlider;
        public Image eggImage;
        public TextMeshProUGUI eggIdText;
        public TextMeshProUGUI rarityText;
        public Button openButton;
        [Header("Settings")]
        public float crackDuration = 3f;
        public float revealDuration = 2f;
        public AudioClip crackSound;
        public AudioClip revealSound;

        private AudioSource _audioSource;
        private EggSpawner.EggData _currentEgg;
        private bool _isOpening = false;

        private void Awake()
        {
            _audioSource = GetComponent<AudioSource>();
            if (_audioSource == null)
            {
                _audioSource = gameObject.AddComponent<AudioSource>();
            }
        }

        private void Start()
        {
            if (openingPanel != null)
            {
                openingPanel.SetActive(false);
            }
            if (openButton != null)
            {
                openButton.onClick.AddListener(OnOpenButtonClicked);
            }
        }

        public void OpenEgg(EggSpawner.EggData egg)
        {
            _currentEgg = egg;
            _isOpening = false;

            if (openingPanel != null)
            {
                openingPanel.SetActive(true);
            }

            UpdateEggInfo(egg);
            StartCoroutine(EggOpeningSequence());
        }

        private void UpdateEggInfo(EggSpawner.EggData egg)
        {
            if (eggIdText != null)
                eggIdText.text = egg.id;
            if (rarityText != null)
                rarityText.text = egg.rarity.ToString();
            if (eggImage != null)
            {
                eggImage.color = GetRarityColor(egg.rarity);
            }
        }

        private Color GetRarityColor(EggRarity rarity)
        {
            switch (rarity)
            {
                case EggRarity.Common: return Color.gray;
                case EggRarity.Uncommon: return Color.green;
                case EggRarity.Rare: return Color.blue;
                case EggRarity.Epic: return Color.magenta;
                case EggRarity.Legendary: return Color.yellow;
                default: return Color.white;
            }
        }

        private IEnumerator EggOpeningSequence()
        {
            float elapsed = 0f;

            while (elapsed < crackDuration)
            {
                elapsed += Time.deltaTime;
                float progress = elapsed / crackDuration;
                if (crackProgressSlider != null)
                {
                    crackProgressSlider.value = progress;
                }

                if (progress >= 0.3f && !IsInvoking("CrackSound"))
                {
                    PlaySound(crackSound);
                }
                if (progress >= 0.7f && !IsInvoking("RevealSound"))
                {
                    PlaySound(revealSound);
                }

                yield return null;
            }

            _isOpening = true;
            if (openButton != null)
            {
                openButton.interactable = true;
            }
        }

        private void PlaySound(AudioClip clip)
        {
            if (clip != null && _audioSource != null)
            {
                _audioSource.PlayOneShot(clip);
            }
        }

        private bool IsInvoking(string methodName)
        {
            return false;
        }

        private void OnOpenButtonClicked()
        {
            if (!_isOpening) return;

            _isOpening = false;
            if (openButton != null)
            {
                openButton.interactable = false;
            }

            Debug.Log($"[EggOpening] Egg {_currentEgg.id} opened! Rarity: {_currentEgg.rarity}");
            UnityBridge.SendMessageToAndroid("EggOpened", $"{{\"eggId\":\"{_currentEgg.id}\",\"rarity\":\"{_currentEgg.rarity}\"}}");

            if (openingPanel != null)
            {
                openingPanel.SetActive(false);
            }
            _currentEgg = null;
        }
    }
}