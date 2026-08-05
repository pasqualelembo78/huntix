using UnityEngine;
using UnityEngine.UI;
using TMPro;
using System.Collections.Generic;
using Huntix.Inventory;

namespace Huntix.UI
{
    public class BestiaryUI : MonoBehaviour
    {
        public static BestiaryUI Instance { get; private set; }

        [Header("UI References")]
        public Transform eggGridParent;
        public GameObject eggButtonPrefab;
        public TextMeshProUGUI collectionPercentText;
        public Button closeButton;

        private List<EggInventoryManager.EggEntry> _allEggs;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
        }

        private void Start()
        {
            if (closeButton != null)
                closeButton.onClick.AddListener(Hide);
        }

        public void Show()
        {
            gameObject.SetActive(true);
            RefreshUI();
        }

        public void Hide()
        {
            gameObject.SetActive(false);
        }

        public void RefreshUI()
        {
            _allEggs = EggInventoryManager.Instance.GetAllEggs();
            UpdateCollectionPercent();
            UpdateEggGrid();
        }

        private void UpdateCollectionPercent()
        {
            int totalTypes = 5;
            int foundTypes = EggInventoryManager.Instance.GetUniqueRarityCount();
            float percent = (float)foundTypes / totalTypes * 100f;
            if (collectionPercentText != null)
                collectionPercentText.text = $"{percent:F0}% Collected";
        }

        private void UpdateEggGrid()
        {
            foreach (Transform child in eggGridParent)
            {
                Destroy(child.gameObject);
            }

            foreach (var egg in _allEggs)
            {
                var buttonObj = Instantiate(eggButtonPrefab, eggGridParent);
                var button = buttonObj.GetComponent<BestiaryEggButton>();
                if (button != null)
                {
                    button.Setup(egg);
                }
            }
        }
    }

    public class BestiaryEggButton : MonoBehaviour
    {
        public TextMeshProUGUI eggNameText;
        public TextMeshProUGUI rarityText;
        public Image eggIcon;
        public Image rarityBackground;

        public void Setup(EggInventoryManager.EggEntry egg)
        {
            if (eggNameText != null)
                eggNameText.text = egg.eggId;
            if (rarityText != null)
                rarityText.text = egg.rarityId;
            if (eggIcon != null)
                eggIcon.color = GetRarityColor(egg.rarityId);
            if (rarityBackground != null)
                rarityBackground.color = GetRarityColor(egg.rarityId);
        }

        private Color GetRarityColor(string rarityId)
        {
            switch (rarityId.ToLower())
            {
                case "legendary": return Color.yellow;
                case "epic": return Color.magenta;
                case "rare": return Color.cyan;
                case "uncommon": return Color.green;
                default: return Color.white;
            }
        }
    }
}