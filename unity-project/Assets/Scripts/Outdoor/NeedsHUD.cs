using UnityEngine;
using UnityEngine.UI;
using System;
using Huntix.Bridge;

namespace Huntix.Outdoor
{
    /// <summary>
    /// NeedsHUD — HUD dei bisogni (stile Sims) nella scena Esplora.
    /// Barre: Fame, Sonno, Igiene, Divertimento, Sete.
    ///
    /// Fonte dati: LocalNeeds (Kotlin) via UnityBridge quando si gira su Android;
    /// in editor usa un fallback PlayerPrefs così l'HUD funziona anche senza device.
    /// La UI viene creata a runtime (nessun setup in scena necessario).
    /// </summary>
    public class NeedsHUD : MonoBehaviour
    {
        public static NeedsHUD Instance { get; private set; }

        [Serializable]
        public class NeedsData
        {
            public float hunger = 60f;
            public float sleep = 60f;
            public float hygiene = 60f;
            public float fun = 60f;
            public float thirst = 60f;
        }

        private class NeedDef
        {
            public string key;
            public string label;
            public string emoji;
            public Color color;
        }

        private static readonly NeedDef[] NEEDS = new[]
        {
            new NeedDef { key = "hunger",  label = "Fame",        emoji = "\U0001F354", color = FromHex("FF8A3D") },
            new NeedDef { key = "sleep",   label = "Sonno",       emoji = "\U0001F634", color = FromHex("4FA3FF") },
            new NeedDef { key = "hygiene", label = "Igiene",      emoji = "\U0001F6BF", color = FromHex("3DE0E0") },
            new NeedDef { key = "fun",     label = "Divertimento", emoji = "\U0001F389", color = FromHex("B96BFF") },
            new NeedDef { key = "thirst",  label = "Sete",        emoji = "\U0001F4A7", color = FromHex("4FC3F7") }
        };

        private static readonly Color BG = new Color(0.04f, 0.08f, 0.16f, 0.88f);
        private static readonly Color TRACK = new Color(0.10f, 0.12f, 0.22f, 1f);

        private GameObject _panel;
        private Image[] _fills = new Image[NEEDS.Length];
        private Text[] _values = new Text[NEEDS.Length];
        private bool _visible = true;
        private float _timer;
        private const float REFRESH_SECONDS = 1f;

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
            BuildUi();
            RefreshAll();
        }

        private void Update()
        {
            _timer += Time.deltaTime;
            if (_timer >= REFRESH_SECONDS)
            {
                _timer = 0f;
                RefreshAll();
            }
        }

        private void OnDestroy()
        {
            if (Instance == this) Instance = null;
        }

        public static NeedsHUD EnsureInstance()
        {
            if (Instance == null)
            {
                var go = new GameObject("NeedsHUD");
                go.AddComponent<NeedsHUD>();
            }
            return Instance;
        }

        public static void DestroyInstance()
        {
            if (Instance != null)
                Destroy(Instance.gameObject);
        }

        /// <summary>Legge i bisogni (LocalNeeds su Android, PlayerPrefs in editor).</summary>
        public NeedsData ReadNeeds()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                string json = UnityBridge.GetNeedsJson();
                if (!string.IsNullOrEmpty(json) && json != "{}")
                    return JsonUtility.FromJson<NeedsData>(json);
            }
            catch (Exception e)
            {
                Debug.LogWarning("[NeedsHUD] ReadNeeds: " + e.Message);
            }
            #endif
            return new NeedsData
            {
                hunger = PlayerPrefs.GetFloat("huntix_needs_hunger", 60f),
                sleep = PlayerPrefs.GetFloat("huntix_needs_sleep", 60f),
                hygiene = PlayerPrefs.GetFloat("huntix_needs_hygiene", 60f),
                fun = PlayerPrefs.GetFloat("huntix_needs_fun", 60f),
                thirst = PlayerPrefs.GetFloat("huntix_needs_thirst", 60f)
            };
        }

        /// <summary>Applica un'azione a un bisogno e aggiorna l'HUD.</summary>
        public void ApplyAction(string needKey, float gain)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try { UnityBridge.ApplyNeedAction(needKey, gain); } catch (Exception e) { Debug.LogWarning("[NeedsHUD] ApplyAction: " + e.Message); }
            #else
            PlayerPrefs.SetFloat("huntix_needs_" + needKey,
                Mathf.Clamp(PlayerPrefs.GetFloat("huntix_needs_" + needKey, 60f) + gain, 0f, 100f));
            #endif
            RefreshAll();
        }

        private void RefreshAll()
        {
            var needs = ReadNeeds();
            float[] values =
            {
                needs.hunger, needs.sleep, needs.hygiene, needs.fun, needs.thirst
            };
            for (int i = 0; i < NEEDS.Length; i++)
            {
                float v = Mathf.Clamp(values[i], 0f, 100f);
                if (_fills[i] != null) _fills[i].fillAmount = v / 100f;
                if (_values[i] != null) _values[i].text = Mathf.RoundToInt(v) + "%";
            }
        }

        // ── UI ─────────────────────────────────────────────────────

        private void BuildUi()
        {
            Huntix.UI.ExploreUIController.EnsureEventSystem();

            var canvasGo = new GameObject("Needs_Canvas", typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            canvasGo.transform.SetParent(transform);
            var canvas = canvasGo.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            var scaler = canvasGo.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080, 1920);

            _panel = new GameObject("Pannello", typeof(RectTransform), typeof(Image));
            _panel.transform.SetParent(canvasGo.transform, false);
            var prt = _panel.GetComponent<RectTransform>();
            prt.anchorMin = new Vector2(0f, 1f);
            prt.anchorMax = new Vector2(0f, 1f);
            prt.pivot = new Vector2(0f, 1f);
            prt.anchoredPosition = new Vector2(12f, -12f);
            prt.sizeDelta = new Vector2(340f, 0f);
            _panel.GetComponent<Image>().color = BG;

            var layout = _panel.AddComponent<VerticalLayoutGroup>();
            layout.padding = new RectOffset(10, 10, 10, 10);
            layout.spacing = 6;
            layout.childForceExpandWidth = true;
            layout.childForceExpandHeight = false;

            var header = new GameObject("Header", typeof(RectTransform), typeof(HorizontalLayoutGroup));
            header.transform.SetParent(_panel.transform, false);

            var title = MakeText(header.transform, "\U0001F3AC  Bisogni", 24, FontStyle.Bold, TextAnchor.MiddleLeft);
            title.GetComponent<RectTransform>().sizeDelta = new Vector2(200f, 0f);

            var toggle = MakeButton(header.transform, "Nascondi", 20);
            toggle.onClick.AddListener(TogglePanel);

            for (int i = 0; i < NEEDS.Length; i++)
                CreateNeedRow(i);
        }

        private void CreateNeedRow(int index)
        {
            var def = NEEDS[index];

            var row = new GameObject("Bisogno_" + def.key, typeof(RectTransform), typeof(HorizontalLayoutGroup));
            row.transform.SetParent(_panel.transform, false);
            var hlg = row.GetComponent<HorizontalLayoutGroup>();
            hlg.spacing = 8;
            hlg.childForceExpandWidth = false;
            hlg.childForceExpandHeight = true;
            var rowLayout = row.AddComponent<LayoutElement>();
            rowLayout.minHeight = 34;
            rowLayout.preferredHeight = 34;

            var emoji = MakeText(row.transform, def.emoji, 22, FontStyle.Normal, TextAnchor.MiddleLeft);
            emoji.GetComponent<RectTransform>().sizeDelta = new Vector2(36f, 0f);

            var bar = MakeBar(row.transform, def.color);
            var barLayout = bar.GetComponent<LayoutElement>();
            barLayout.minWidth = 130f;
            barLayout.flexibleWidth = 1f;

            var value = MakeText(row.transform, "60%", 20, FontStyle.Normal, TextAnchor.MiddleRight);
            value.GetComponent<RectTransform>().sizeDelta = new Vector2(52f, 0f);
            value.color = def.color;
            value.alignment = TextAnchor.MiddleRight;

            _fills[index] = bar.transform.GetChild(0).GetComponent<Image>();
            _values[index] = value;
        }

        private GameObject MakeBar(Transform parent, Color color)
        {
            var go = new GameObject("Barra", typeof(RectTransform), typeof(Image));
            go.transform.SetParent(parent, false);
            go.GetComponent<Image>().color = TRACK;
            go.AddComponent<LayoutElement>();
            go.GetComponent<Image>().raycastTarget = false;

            var fill = new GameObject("Fill", typeof(RectTransform), typeof(Image));
            fill.transform.SetParent(go.transform, false);
            var frt = fill.GetComponent<RectTransform>();
            frt.anchorMin = Vector2.zero;
            frt.anchorMax = Vector2.one;
            frt.offsetMin = Vector2.zero;
            frt.offsetMax = Vector2.zero;
            var img = fill.GetComponent<Image>();
            img.type = Image.Type.Filled;
            img.fillMethod = Image.FillMethod.Horizontal;
            img.fillOrigin = 0;
            img.fillAmount = 0.6f;
            img.color = color;
            img.raycastTarget = false;
            return go;
        }

        private void TogglePanel()
        {
            _visible = !_visible;
            if (_panel != null) _panel.SetActive(_visible);
        }

        // ── Costruttori ────────────────────────────────────────────

        private static Text MakeText(Transform parent, string text, int size, FontStyle style, TextAnchor align)
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
            t.raycastTarget = false;
            return t;
        }

        private static Button MakeButton(Transform parent, string text, int size)
        {
            var go = new GameObject("Pulsante", typeof(RectTransform), typeof(Image), typeof(Button));
            go.transform.SetParent(parent, false);
            go.GetComponent<Image>().color = new Color(0.15f, 0.2f, 0.35f);
            var le = go.AddComponent<LayoutElement>();
            le.minWidth = size * text.Length * 0.6f + 16;
            le.minHeight = size + 10;

            var label = MakeText(go.transform, text, size, FontStyle.Normal, TextAnchor.MiddleCenter);
            var rt = label.GetComponent<RectTransform>();
            rt.anchorMin = Vector2.zero;
            rt.anchorMax = Vector2.one;
            rt.offsetMin = Vector2.zero;
            rt.offsetMax = Vector2.zero;
            return go.GetComponent<Button>();
        }

        private static Color FromHex(string hex)
        {
            Color c;
            return ColorUtility.TryParseHtmlString("#" + hex, out c) ? c : Color.white;
        }
    }
}
