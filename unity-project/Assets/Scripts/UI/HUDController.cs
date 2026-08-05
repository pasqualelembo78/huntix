using UnityEngine;
using TMPro;
using UnityEngine.UI;
using Huntix.Bridge;

namespace Huntix.UI
{
    public class HUDController : MonoBehaviour
    {
        public static HUDController Instance { get; private set; }

        [Header("Level & XP")]
        public TextMeshProUGUI levelText;
        public TextMeshProUGUI xpText;
        public Slider xpSlider;

        [Header("MVC")]
        public TextMeshProUGUI mvcText;

        [Header("Egg Count")]
        public TextMeshProUGUI eggCountText;

        [Header("Weather")]
        public TextMeshProUGUI weatherText;

        [Header("Buttons")]
        public Button arButton;
        public Button mapButton;
        public Button menuButton;

        private int _currentLevel;
        private int _currentXP;
        private int _currentMVC;
        private int _eggCount;

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
            if (arButton != null)
                arButton.onClick.AddListener(OnARButtonClicked);
            if (mapButton != null)
                mapButton.onClick.AddListener(OnMapButtonClicked);
            if (menuButton != null)
                menuButton.onClick.AddListener(OnMenuButtonClicked);
        }

        public void UpdateLevel(int level, int xp, int xpForNext)
        {
            _currentLevel = level;
            _currentXP = xp;
            if (levelText != null) levelText.text = $"Lv.{level}";
            if (xpText != null) xpText.text = $"{xp}/{xpForNext}";
            if (xpSlider != null) xpSlider.value = (float)xp / xpForNext;
        }

        public void UpdateMVC(int mvc)
        {
            _currentMVC = mvc;
            if (mvcText != null) mvcText.text = mvc.ToString();
        }

        public void UpdateEggCount(int count)
        {
            _eggCount = count;
            if (eggCountText != null) eggCountText.text = count.ToString();
        }

        public void UpdateWeather(string weatherType)
        {
            if (weatherText != null) weatherText.text = weatherType;
        }

        private void OnARButtonClicked()
        {
            Debug.Log("[HUD] AR button clicked");
            UnityBridge.SendMessageToAndroid("ARButtonClicked", "{}");
        }

        private void OnMapButtonClicked()
        {
            Debug.Log("[HUD] Map button clicked");
            UnityBridge.SendMessageToAndroid("MapButtonClicked", "{}");
            UnityBridge.QuitToAndroid();
        }

        private void OnMenuButtonClicked()
        {
            Debug.Log("[HUD] Menu button clicked");
            UnityBridge.SendMessageToAndroid("MenuButtonClicked", "{}");
        }
    }
}