using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;
using System;
using System.Collections.Generic;

namespace Huntix.Outdoor
{
    /// <summary>
    /// ExplorePopup — popup modale costruito a runtime (nessun setup in scena).
    /// Supporta: titolo, messaggio, lista a scorrimento verticale di POI e
    /// form di segnalazione (nome/categoria/nota → email).
    /// </summary>
    public class ExplorePopup : MonoBehaviour
    {
        public static ExplorePopup Instance { get; private set; }

        public static bool IsOpen => Instance != null && Instance.gameObject.activeSelf;

        [Header("Stile")]
        public Color accent = new Color(0.93f, 0.32f, 0.16f);   // arancione
        public Color bg = new Color(0.05f, 0.07f, 0.13f, 0.97f); // scuro

        private Canvas _canvas;
        private GameObject _panel;
        private RectTransform _content;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            EnsureEventSystem();
            BuildCanvas();
            Hide();
        }

        private void EnsureEventSystem()
        {
            if (EventSystem.current != null) return;
            new GameObject("EventSystem", typeof(EventSystem), typeof(StandaloneInputModule));
        }

        private void BuildCanvas()
        {
            var canvasGo = new GameObject("Explore_Popup_Canvas", typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            canvasGo.transform.SetParent(transform);
            var canvas = canvasGo.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            canvas.sortingOrder = 200;
            var scaler = canvasGo.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080, 1920);
            _canvas = canvas;

            // Sfondo scuro a tutto schermo (intercetta i tap dietro)
            var dim = new GameObject("Dim", typeof(RectTransform), typeof(Image), typeof(Button));
            dim.transform.SetParent(canvasGo.transform, false);
            var drt = dim.GetComponent<RectTransform>();
            drt.anchorMin = Vector2.zero;
            drt.anchorMax = Vector2.one;
            drt.offsetMin = Vector2.zero;
            drt.offsetMax = Vector2.zero;
            dim.GetComponent<Image>().color = new Color(0f, 0f, 0f, 0.65f);
            dim.GetComponent<Button>().onClick.AddListener(() => { });

            // Pannello centrale
            _panel = new GameObject("Popup_Pannello", typeof(RectTransform), typeof(Image));
            _panel.transform.SetParent(canvasGo.transform, false);
            var prt = _panel.GetComponent<RectTransform>();
            prt.anchorMin = new Vector2(0.05f, 0.18f);
            prt.anchorMax = new Vector2(0.95f, 0.82f);
            prt.offsetMin = Vector2.zero;
            prt.offsetMax = Vector2.zero;
            _panel.GetComponent<Image>().color = bg;

            _content = new GameObject("Contenuto", typeof(RectTransform)).GetComponent<RectTransform>();
            _content.SetParent(_panel.transform, false);
            _content.anchorMin = Vector2.zero;
            _content.anchorMax = Vector2.one;
            _content.offsetMin = new Vector2(18f, 14f);
            _content.offsetMax = new Vector2(-18f, -14f);

            var layout = _content.gameObject.AddComponent<VerticalLayoutGroup>();
            layout.padding = new RectOffset(6, 6, 6, 6);
            layout.spacing = 12;
            layout.childForceExpandWidth = true;
            layout.childForceExpandHeight = false;
        }

        /// <summary>Mostra un popup modale con messaggio, lista opzionale e bottoni.</summary>
        public static void Show(
            string title, string message,
            IList<string> items, Action<int> onItemSelected,
            params (string label, Action action)[] buttons)
        {
            if (Instance == null) return;
            Instance.ShowInternal(title, message, items, onItemSelected, buttons);
        }

        /// <summary>Mostra il form di segnalazione (nome/categoria/nota → email).</summary>
        public static void ShowReportForm(double lat, double lng, Action<string, string, string> onSubmit)
        {
            if (Instance == null) return;
            Instance.ShowReportFormInternal(lat, lng, onSubmit);
        }

        // ── Costruzione contenuto ──────────────────────────────────

        private void ShowInternal(string title, string message,
            IList<string> items, Action<int> onItemSelected,
            (string label, Action action)[] buttons)
        {
            ClearContent();
            gameObject.SetActive(true);

            CreateText(_content, title, 34, FontStyle.Bold, TextAnchor.MiddleCenter);
            CreateText(_content, message, 26, FontStyle.Normal, TextAnchor.MiddleLeft);

            if (items != null && items.Count > 0)
            {
                var scroll = CreateScrollList(_content, Mathf.Min(items.Count, 6));
                for (int i = 0; i < items.Count; i++)
                {
                    int idx = i;
                    var b = CreateButton(scroll, items[i], 24);
                    b.onClick.AddListener(() => onItemSelected?.Invoke(idx));
                }
            }

            if (buttons != null && buttons.Length > 0)
            {
                var row = CreateHorizontalRow(_content);
                foreach (var (label, action) in buttons)
                    CreateButton(row, label, 26).onClick.AddListener(() => { Hide(); action?.Invoke(); });
            }
        }

        private void ShowReportFormInternal(double lat, double lng, Action<string, string, string> onSubmit)
        {
            ClearContent();
            gameObject.SetActive(true);

            CreateText(_content, "Segnalazione nuovo POI", 32, FontStyle.Bold, TextAnchor.MiddleCenter);
            CreateText(_content,
                $"Coordinate: {lat.ToString("F6", System.Globalization.CultureInfo.InvariantCulture)}, " +
                $"{lng.ToString("F6", System.Globalization.CultureInfo.InvariantCulture)}",
                24, FontStyle.Normal, TextAnchor.MiddleLeft);

            var nameField = CreateInputField(_content, "Nome del locale / POI");
            var catField = CreateInputField(_content, "Categoria (es. Ristorante, Bar, Negozio)");
            var noteField = CreateInputField(_content, "Nota (facoltativa)");

            var row = CreateHorizontalRow(_content);
            CreateButton(row, "Annulla", 26).onClick.AddListener(Hide);
            CreateButton(row, "Invia segnalazione", 26).onClick.AddListener(() =>
            {
                Hide();
                onSubmit?.Invoke(nameField.text.Trim(), catField.text.Trim(), noteField.text.Trim());
            });
        }

        private void Hide() => gameObject.SetActive(false);

        private void ClearContent()
        {
            foreach (Transform child in _content)
                Destroy(child.gameObject);
        }

        // ── Costruttori UI ─────────────────────────────────────────

        private RectTransform CreateScrollList(RectTransform parent, int visibleRows)
        {
            var go = new GameObject("Lista_POI", typeof(RectTransform), typeof(Image), typeof(ScrollRect));
            go.transform.SetParent(parent, false);
            var rt = go.GetComponent<RectTransform>();
            rt.sizeDelta = new Vector2(0f, visibleRows * 72f);
            go.GetComponent<Image>().color = new Color(1f, 1f, 1f, 0.08f);

            var viewport = new GameObject("Viewport", typeof(RectTransform), typeof(Mask), typeof(Image));
            viewport.transform.SetParent(go.transform, false);
            var vrt = viewport.GetComponent<RectTransform>();
            vrt.anchorMin = Vector2.zero;
            vrt.anchorMax = Vector2.one;
            vrt.offsetMin = new Vector2(0f, 0f);
            vrt.offsetMax = new Vector2(-40f, 0f);
            viewport.GetComponent<Image>().color = new Color(0f, 0f, 0f, 0.2f);
            viewport.GetComponent<Mask>().showMaskGraphic = false;

            var content = new GameObject("Content", typeof(RectTransform), typeof(VerticalLayoutGroup), typeof(ContentSizeFitter));
            content.transform.SetParent(viewport.transform, false);
            var crt = content.GetComponent<RectTransform>();
            crt.anchorMin = new Vector2(0f, 1f);
            crt.anchorMax = new Vector2(1f, 1f);
            crt.pivot = new Vector2(0.5f, 1f);
            crt.sizeDelta = new Vector2(0f, 0f);
            var vlg = content.GetComponent<VerticalLayoutGroup>();
            vlg.padding = new RectOffset(6, 6, 6, 6);
            vlg.spacing = 6;
            vlg.childForceExpandWidth = true;
            vlg.childForceExpandHeight = false;
            var fitter = content.GetComponent<ContentSizeFitter>();
            fitter.verticalFit = ContentSizeFitter.FitMode.PreferredSize;

            var scroll = go.GetComponent<ScrollRect>();
            scroll.viewport = vrt;
            scroll.content = crt;
            scroll.vertical = true;
            scroll.horizontal = false;
            scroll.movementType = ScrollRect.MovementType.Clamped;

            // Scrollbar verticale
            var sb = new GameObject("Scrollbar", typeof(RectTransform), typeof(Scrollbar));
            sb.transform.SetParent(go.transform, false);
            var sbrt = sb.GetComponent<RectTransform>();
            sbrt.anchorMin = new Vector2(1f, 0f);
            sbrt.anchorMax = new Vector2(1f, 1f);
            sbrt.offsetMin = new Vector2(-30f, 0f);
            sbrt.offsetMax = new Vector2(-4f, 0f);
            var scrollbar = sb.GetComponent<Scrollbar>();
            var barImage = new GameObject("Barra", typeof(RectTransform), typeof(Image)).GetComponent<Image>();
            barImage.transform.SetParent(sb.transform, false);
            barImage.color = Color.white;
            var brt = barImage.GetComponent<RectTransform>();
            brt.anchorMin = Vector2.zero;
            brt.anchorMax = Vector2.one;
            scrollbar.handleRect = brt;
            scrollbar.direction = Scrollbar.Direction.BottomToTop;
            scroll.verticalScrollbar = scrollbar;
            scroll.verticalScrollbarVisibility = ScrollRect.ScrollbarVisibility.AutoHide;

            return content.GetComponent<RectTransform>();
        }

        private RectTransform CreateHorizontalRow(RectTransform parent)
        {
            var row = new GameObject("Bottoni", typeof(RectTransform), typeof(HorizontalLayoutGroup));
            row.transform.SetParent(parent, false);
            var rt = row.GetComponent<RectTransform>();
            rt.sizeDelta = new Vector2(0f, 60f);
            var hlg = row.GetComponent<HorizontalLayoutGroup>();
            hlg.spacing = 10;
            hlg.childForceExpandWidth = true;
            hlg.childForceExpandHeight = true;
            hlg.childAlignment = TextAnchor.MiddleCenter;
            return rt;
        }

        private Button CreateButton(RectTransform parent, string text, int size)
        {
            var go = new GameObject("Pulsante", typeof(RectTransform), typeof(Image), typeof(Button));
            go.transform.SetParent(parent, false);
            go.GetComponent<Image>().color = accent;
            var layout = go.AddComponent<LayoutElement>();
            layout.minWidth = 120;
            layout.minHeight = size + 16;
            layout.preferredHeight = size + 16;

            var label = CreateText(go.transform as RectTransform, text, size, FontStyle.Normal, TextAnchor.MiddleCenter);
            var rt = label.GetComponent<RectTransform>();
            rt.anchorMin = Vector2.zero;
            rt.anchorMax = Vector2.one;
            rt.offsetMin = Vector2.zero;
            rt.offsetMax = Vector2.zero;
            return go.GetComponent<Button>();
        }

        private InputField CreateInputField(RectTransform parent, string placeholder)
        {
            var go = new GameObject("Campo", typeof(RectTransform), typeof(Image), typeof(InputField));
            go.transform.SetParent(parent, false);
            go.GetComponent<Image>().color = new Color(1f, 1f, 1f, 0.12f);
            var layout = go.AddComponent<LayoutElement>();
            layout.minHeight = 56;

            var t = CreateText(go.transform as RectTransform, "", 26, FontStyle.Normal, TextAnchor.MiddleLeft);
            t.supportRichText = false;
            var trt = t.GetComponent<RectTransform>();
            trt.anchorMin = Vector2.zero; trt.anchorMax = Vector2.one;
            trt.offsetMin = new Vector2(12, 0); trt.offsetMax = new Vector2(-12, 0);

            var field = go.GetComponent<InputField>();
            field.textComponent = t;
            field.placeholder = CreateText(go.transform as RectTransform, placeholder, 24, FontStyle.Italic, TextAnchor.MiddleLeft);
            field.placeholder.color = new Color(1, 1, 1, 0.5f);
            field.caretColor = Color.white;
            field.lineType = InputField.LineType.SingleLine;
            return field;
        }

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
            t.horizontalOverflow = HorizontalWrapMode.Wrap;
            return t;
        }
    }
}
