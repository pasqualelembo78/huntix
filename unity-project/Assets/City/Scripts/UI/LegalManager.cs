using UnityEngine;
using TMPro;
using UnityEngine.UI;

namespace City.UI
{
    public class LegalManager : MonoBehaviour
    {
        public static LegalManager Instance { get; private set; }

        private GameObject panel;
        private TMP_Text titleText;
        private TMP_Text contentText;
        private ScrollRect scroll;
        private TMP_Text[] tabTexts;

        private static readonly Color PanelBg = new Color(0.11f, 0.12f, 0.14f, 0.98f);
        private static readonly Color TabBg = new Color(0.20f, 0.22f, 0.25f, 1f);
        private static readonly Color TabActive = new Color(0.20f, 0.75f, 0.55f, 1f);
        private static readonly Color Accent = new Color(0.20f, 0.75f, 0.55f, 1f);

        private TMP_FontAsset font;

        private readonly string[] docKeys = { "terms", "privacy", "osm", "disclaimer", "copyright" };
        private readonly string[] docTitles = {
            "Termini e Condizioni",
            "Privacy Policy",
            "Dati Cartografici (OSM)",
            "Disclaimer",
            "Copyright"
        };

        private int activeTab = -1;

        public void Init(Canvas parentCanvas, RectTransform parentRoot)
        {
            Instance = this;
            font = TMP_Settings.defaultFontAsset;
            if (font == null) font = Resources.Load<TMP_FontAsset>("Fonts & Materials/LiberationSans SDF");
            Build(parentRoot);
        }

        public void Show()
        {
            if (panel == null) return;
            panel.SetActive(true);
            ShowTab(0);
        }

        public void Hide()
        {
            if (panel == null) return;
            panel.SetActive(false);
        }

        public bool IsVisible => panel != null && panel.activeSelf;

        private void Build(RectTransform root)
        {
            // Full-screen overlay panel
            panel = MakeRect("LegalPanel", root,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero).gameObject;
            Image bg = panel.AddComponent<Image>();
            bg.color = PanelBg;
            bg.raycastTarget = true;
            panel.SetActive(false);

            RectTransform prt = panel.GetComponent<RectTransform>();

            // Title
            titleText = MakeText(prt, "Note Legali", 36f, Color.white, TextAlignmentOptions.Left,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(24f, -16f), new Vector2(-80f, -60f));

            // Close button
            var closeRt = MakeRect("CloseBtn", prt,
                new Vector2(1f, 1f), new Vector2(1f, 1f),
                new Vector2(-72f, -16f), new Vector2(-16f, -60f));
            Image closeBg = closeRt.gameObject.AddComponent<Image>();
            closeBg.color = new Color(0.8f, 0.2f, 0.2f, 0.85f);
            closeBg.raycastTarget = true;
            Button closeBtn = closeRt.gameObject.AddComponent<Button>();
            closeBtn.targetGraphic = closeBg;
            closeBtn.onClick.AddListener(Hide);
            MakeText(closeRt, "X", 28f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            // Tab bar
            float tabW = (Screen.width > 800) ? 200f : 140f;
            float tabH = 44f;
            float startX = 24f;
            tabTexts = new TMP_Text[docKeys.Length];

            for (int i = 0; i < docKeys.Length; i++)
            {
                float x = startX + i * (tabW + 6f);
                var tabRt = MakeRect("Tab_" + docKeys[i], prt,
                    new Vector2(0f, 1f), new Vector2(0f, 1f),
                    new Vector2(x, -108f), new Vector2(x + tabW, -108f + tabH));
                Image tabBg = tabRt.gameObject.AddComponent<Image>();
                tabBg.color = TabBg;
                tabBg.raycastTarget = true;
                Button tabBtn = tabRt.gameObject.AddComponent<Button>();
                tabBtn.targetGraphic = tabBg;
                int captured = i;
                tabBtn.onClick.AddListener(() => ShowTab(captured));
                tabTexts[i] = MakeText(tabRt, docTitles[i], 18f, Color.white, TextAlignmentOptions.Center,
                    Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            }

            // Separator line
            var lineRt = MakeRect("Line", prt,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(24f, -114f), new Vector2(-24f, -112f));
            Image lineImg = lineRt.gameObject.AddComponent<Image>();
            lineImg.color = Accent;
            lineImg.raycastTarget = false;

            // Content area with scroll
            var scrollRt = MakeRect("ScrollArea", prt,
                new Vector2(0f, 0f), new Vector2(1f, 1f),
                new Vector2(24f, 24f), new Vector2(-24f, -118f));
            scroll = scrollRt.gameObject.AddComponent<ScrollRect>();
            Image scrollBg = scrollRt.gameObject.AddComponent<Image>();
            scrollBg.color = new Color(0f, 0f, 0f, 0.2f);
            scrollRt.gameObject.AddComponent<Mask>();

            // Content inside scroll
            var contentRt = MakeRect("Content", scrollRt,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                Vector2.zero, Vector2.zero);
            contentRt.pivot = new Vector2(0.5f, 1f);
            contentText = MakeText(contentRt, "", 22f, new Color(0.9f, 0.9f, 0.9f, 1f), TextAlignmentOptions.TopLeft,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(16f, -16f), new Vector2(-16f, 0f));
            contentText.enableWordWrapping = true;
            contentText.overflowMode = TextOverflowModes.Overflow;
            contentText.raycastTarget = false;

            // Force text height for scroll
            ContentSizeFitter fitter = contentRt.gameObject.AddComponent<ContentSizeFitter>();
            fitter.verticalFit = ContentSizeFitter.FitMode.PreferredSize;

            scroll.content = contentRt;
            scroll.vertical = true;
            scroll.horizontal = false;
            scroll.movementType = ScrollRect.MovementType.Clamped;
            scroll.viewport = scrollRt;
        }

        private void ShowTab(int index)
        {
            if (index < 0 || index >= docKeys.Length) return;
            activeTab = index;

            // Load text from Resources
            TextAsset file = Resources.Load<TextAsset>("Legal/" + docKeys[index]);
            string text = file != null ? file.text : "Documento non disponibile.";

            contentText.text = text;
            titleText.text = docTitles[index];

            // Reset scroll
            Canvas.ForceUpdateCanvases();
            if (scroll != null) scroll.verticalNormalizedPosition = 1f;

            // Update tab colors
            for (int i = 0; i < tabTexts.Length; i++)
            {
                Transform tabBg = tabTexts[i].transform.parent;
                Image img = tabBg.GetComponent<Image>();
                if (img != null) img.color = (i == index) ? TabActive : TabBg;
            }
        }

        // ── UI Helpers (same pattern as UIManager) ──

        private RectTransform MakeRect(string name, Transform parent, Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            GameObject go = new GameObject(name, typeof(RectTransform));
            RectTransform rt = go.GetComponent<RectTransform>();
            rt.SetParent(parent, false);
            rt.anchorMin = anchorMin;
            rt.anchorMax = anchorMax;
            rt.offsetMin = offsetMin;
            rt.offsetMax = offsetMax;
            return rt;
        }

        private TMP_Text MakeText(RectTransform parent, string content, float size, Color color, TextAlignmentOptions alignment, Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            RectTransform rt = MakeRect("Text", parent, anchorMin, anchorMax, offsetMin, offsetMax);
            TMP_Text text = rt.gameObject.AddComponent<TextMeshProUGUI>();
            text.text = content;
            text.fontSize = size;
            text.color = color;
            text.alignment = alignment;
            text.font = font;
            text.raycastTarget = false;
            return text;
        }
    }
}
