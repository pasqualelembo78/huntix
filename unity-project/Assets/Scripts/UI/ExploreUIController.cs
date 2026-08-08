using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;
using System.Collections.Generic;
using Huntix.Outdoor;

namespace Huntix.UI
{
    /// <summary>
    /// ExploreUIController — barra "Esplora" in italiano: ricerca + tab categorie
    /// (Tutti / Ristoranti / Bar & Caffè / Negozi / Gym & Fitness / Musei & Cultura).
    /// La UI viene creata a runtime (nessun setup in scena necessario).
    /// </summary>
    public class ExploreUIController : MonoBehaviour
    {
        public static ExploreUIController Instance { get; private set; }

        [Header("Stile")]
        public Color accent = new Color(0.93f, 0.32f, 0.16f);   // arancione
        public Color bg = new Color(0.04f, 0.08f, 0.16f, 0.9f); // scuro semi-trasparente

        private ExploreManager _em;
        private GameObject _panel;
        private List<Button> _tabButtons = new List<Button>();
        private InputField _searchField;
        private Text _countText;
        private bool _visible = true;

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
            _em = ExploreManager.EnsureInstance();
            EnsureEventSystem();
            BuildUi();
        }

        /// <summary>Crea l'EventSystem a runtime se assente (necessario per i tap UI).</summary>
        public static void EnsureEventSystem()
        {
            if (EventSystem.current != null) return;
            var go = new GameObject("EventSystem", typeof(EventSystem), typeof(StandaloneInputModule));
        }

        private void BuildUi()
        {
            // Canvas
            var canvasGo = new GameObject("Esplora_Canvas", typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            canvasGo.transform.SetParent(transform);
            var canvas = canvasGo.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            var scaler = canvasGo.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080, 1920);

            _panel = new GameObject("Pannello", typeof(RectTransform), typeof(Image));
            _panel.transform.SetParent(canvasGo.transform, false);
            var prt = _panel.GetComponent<RectTransform>();
            prt.anchorMin = new Vector2(0f, 0.9f);
            prt.anchorMax = new Vector2(1f, 1f);
            prt.offsetMin = new Vector2(8f, 0f);
            prt.offsetMax = new Vector2(-8f, -8f);
            _panel.GetComponent<Image>().color = bg;

            var layout = _panel.AddComponent<VerticalLayoutGroup>();
            layout.padding = new RectOffset(10, 10, 10, 10);
            layout.spacing = 8;
            layout.childForceExpandWidth = true;
            layout.childForceExpandHeight = false;

            // Titolo + toggle nascondi
            var header = CreateText(_panel.transform, "🔍 Esplora — locali vicini", 30, FontStyle.Bold, TextAnchor.MiddleLeft);
            var toggleBtn = CreateButton(_panel.transform, "Nascondi", 24);
            toggleBtn.onClick.AddListener(() => TogglePanel());

            // Ricerca
            _searchField = CreateInputField(_panel.transform, "Cerca un locale…");
            _searchField.onValueChanged.AddListener(v => _em.Search(v));

            // Tab categorie
            var tabsRow = new GameObject("TabCategorie", typeof(RectTransform), typeof(HorizontalLayoutGroup));
            tabsRow.transform.SetParent(_panel.transform, false);
            var hlg = tabsRow.GetComponent<HorizontalLayoutGroup>();
            hlg.spacing = 6;
            hlg.childForceExpandWidth = false;
            hlg.childForceExpandHeight = false;

            var cats = _em.categories;
            for (int i = 0; i < cats.Length; i++)
            {
                int idx = i;
                var b = CreateButton(tabsRow.transform, $"{cats[i].emoji} {cats[i].label}", 22);
                b.onClick.AddListener(() =>
                {
                    _em.SetCategory(idx);
                    HighlightTab(idx);
                });
                _tabButtons.Add(b);
            }

            // Contatore
            _countText = CreateText(_panel.transform, "0 locali", 24, FontStyle.Normal, TextAnchor.MiddleLeft);

            HighlightTab(0);
        }

        private void TogglePanel()
        {
            _visible = !_visible;
            _panel.SetActive(_visible);
        }

        private void HighlightTab(int index)
        {
            for (int i = 0; i < _tabButtons.Count; i++)
            {
                var cb = _tabButtons[i].GetComponent<Image>();
                if (cb != null)
                    cb.color = (i == index) ? accent : new Color(0.15f, 0.2f, 0.35f);
            }
        }

        // ── Costruttori UI ─────────────────────────────────────

        private Text CreateText(Transform parent, string text, int size, FontStyle style, TextAnchor align)
        {
            var go = new GameObject("Testo", typeof(RectTransform), typeof(Text));
            go.transform.SetParent(parent, false);
            var t = go.GetComponent<Text>();
            t.text = text;
            t.font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            t.fontSize = size;
            t.fontStyle = style;
            t.alignment = align;
            t.color = Color.white;
            return t;
        }

        private Button CreateButton(Transform parent, string text, int size)
        {
            var go = new GameObject("Pulsante", typeof(RectTransform), typeof(Image), typeof(Button));
            go.transform.SetParent(parent, false);
            go.GetComponent<Image>().color = new Color(0.15f, 0.2f, 0.35f);
            var layout = go.AddComponent<LayoutElement>();
            layout.minWidth = size * text.Length * 0.55f;
            layout.minHeight = size + 14;

            var label = CreateText(go.transform, text, size, FontStyle.Normal, TextAnchor.MiddleCenter);
            var rt = label.GetComponent<RectTransform>();
            rt.anchorMin = Vector2.zero;
            rt.anchorMax = Vector2.one;
            rt.offsetMin = Vector2.zero;
            rt.offsetMax = Vector2.zero;

            return go.GetComponent<Button>();
        }

        private InputField CreateInputField(Transform parent, string placeholder)
        {
            var go = new GameObject("Ricerca", typeof(RectTransform), typeof(Image), typeof(InputField));
            go.transform.SetParent(parent, false);
            go.GetComponent<Image>().color = new Color(1f, 1f, 1f, 0.15f);
            var layout = go.AddComponent<LayoutElement>();
            layout.minHeight = 52;

            var textGo = new GameObject("Testo", typeof(RectTransform), typeof(Text));
            textGo.transform.SetParent(go.transform, false);
            var t = textGo.GetComponent<Text>();
            t.font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            t.fontSize = 26;
            t.color = Color.white;
            t.alignment = TextAnchor.MiddleLeft;
            t.supportRichText = false;
            var trt = textGo.GetComponent<RectTransform>();
            trt.anchorMin = Vector2.zero; trt.anchorMax = Vector2.one;
            trt.offsetMin = new Vector2(12, 0); trt.offsetMax = new Vector2(-12, 0);

            var field = go.GetComponent<InputField>();
            field.textComponent = t;
            field.placeholder = CreateText(go.transform, placeholder, 26, FontStyle.Italic, TextAnchor.MiddleLeft);
            field.placeholder.color = new Color(1, 1, 1, 0.5f);
            field.caretColor = Color.white;

            return field;
        }
    }
}