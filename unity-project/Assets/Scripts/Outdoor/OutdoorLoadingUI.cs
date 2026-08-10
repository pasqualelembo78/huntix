using UnityEngine;
using UnityEngine.UI;

namespace Huntix.Outdoor
{
    /// <summary>
    /// OutdoorLoadingUI — barra di caricamento compatta (in alto, NON bloccante)
    /// per la scena Esplora. Canvas procedurale, nessuna dipendenza esterna.
    /// Non copre la scena: i negozi restano visibili e tappabili mentre le fasi
    /// di caricamento arrivano da Android. Supporta due modalità: indeterminata
    /// (pulse) e determinata (SetProgress) con la percentuale delle fasi;
    /// si nasconde da sola in caso di timeout.
    /// </summary>
    public class OutdoorLoadingUI : MonoBehaviour
    {
        public static OutdoorLoadingUI Instance { get; private set; }

        private const float SAFETY_TIMEOUT = 240f;

        private Canvas _canvas;
        private GameObject _panel;
        private Image _fill;
        private Text _label;
        private float _pulse = 0f;
        private float _safetyTimer = 0f;
        private float _target = -1f;
        private bool _visible = false;

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
            BuildUI();
            Hide();
        }

        private void Update()
        {
            if (!_visible) return;

            if (_target >= 0f)
            {
                // Determinata: la barra scorre dolcemente verso la % della fase corrente
                if (_fill != null)
                    _fill.fillAmount = Mathf.MoveTowards(_fill.fillAmount, _target, Time.deltaTime * 0.8f);
            }
            else
            {
                // Indeterminata: va avanti e indietro finché non c'è un progresso noto
                _pulse += Time.deltaTime * 0.8f;
                if (_fill != null)
                    _fill.fillAmount = Mathf.PingPong(_pulse, 1f);
            }

            _safetyTimer += Time.deltaTime;
            if (_safetyTimer >= SAFETY_TIMEOUT)
            {
                Debug.LogWarning("[OutdoorLoadingUI] Timeout caricamento, nascondo la barra");
                Hide();
            }
        }

        public void Show(string message = "Caricamento negozi…")
        {
            _visible = true;
            _target = -1f;
            _safetyTimer = 0f;
            if (_panel != null) _panel.SetActive(true);
            if (_label != null) _label.text = message;
            if (_fill != null) _fill.fillAmount = 0f;
        }

        /// <summary>Barra determinata: target 0..1 + messaggio della fase corrente.</summary>
        public void SetProgress(float fraction, string message)
        {
            _visible = true;
            _target = Mathf.Clamp01(fraction);
            _safetyTimer = 0f;
            if (_panel != null) _panel.SetActive(true);
            if (_label != null) _label.text = message;
            if (_fill != null && _target <= 0f) _fill.fillAmount = 0f;
        }

        public void Hide()
        {
            _visible = false;
            _target = -1f;
            if (_panel != null) _panel.SetActive(false);
        }

        private void BuildUI()
        {
            var go = new GameObject("Canvas");
            go.transform.SetParent(transform);
            _canvas = go.AddComponent<Canvas>();
            _canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            _canvas.sortingOrder = 200;
            var scaler = _canvas.gameObject.AddComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1920, 1080);
            _canvas.gameObject.AddComponent<GraphicRaycaster>();

            // Pannello compatto in alto (non copre la scena, non intercetta i tap)
            _panel = new GameObject("LoadingPanel");
            _panel.transform.SetParent(_canvas.transform);
            var pr = _panel.AddComponent<RectTransform>();
            pr.anchorMin = new Vector2(0.5f, 1f);
            pr.anchorMax = new Vector2(0.5f, 1f);
            pr.pivot = new Vector2(0.5f, 1f);
            pr.anchoredPosition = new Vector2(0, -48);
            pr.sizeDelta = new Vector2(640, 96);
            var panelImg = _panel.AddComponent<Image>();
            panelImg.color = new Color(0.04f, 0.08f, 0.16f, 0.85f);
            panelImg.raycastTarget = false;

            // Contorno della barra
            var bar = new GameObject("ProgressBar");
            bar.transform.SetParent(_panel.transform);
            var br = bar.AddComponent<RectTransform>();
            br.anchorMin = new Vector2(0.5f, 0f);
            br.anchorMax = new Vector2(0.5f, 0f);
            br.pivot = new Vector2(0.5f, 0f);
            br.anchoredPosition = new Vector2(0, 14);
            br.sizeDelta = new Vector2(600, 22);
            var barImg = bar.AddComponent<Image>();
            barImg.color = new Color(0f, 0f, 0f, 0.85f);
            barImg.raycastTarget = false;

            // Riempimento animato
            _fill = new GameObject("Fill").AddComponent<Image>();
            _fill.transform.SetParent(bar.transform);
            var fr = _fill.GetComponent<RectTransform>();
            fr.anchorMin = Vector2.zero;
            fr.anchorMax = Vector2.one;
            fr.offsetMin = new Vector2(3, 3);
            fr.offsetMax = new Vector2(-3, -3);
            _fill.type = Image.Type.Filled;
            _fill.fillMethod = Image.FillMethod.Horizontal;
            _fill.fillAmount = 0f;
            _fill.color = new Color(0.2f, 0.8f, 0.4f, 1f);
            _fill.raycastTarget = false;

            // Testo
            _label = new GameObject("Label").AddComponent<Text>();
            _label.transform.SetParent(_panel.transform);
            var lr = _label.GetComponent<RectTransform>();
            lr.anchorMin = new Vector2(0.5f, 1f);
            lr.anchorMax = new Vector2(0.5f, 1f);
            lr.pivot = new Vector2(0.5f, 1f);
            lr.anchoredPosition = new Vector2(0, -6);
            lr.sizeDelta = new Vector2(600, 44);
            _label.alignment = TextAnchor.MiddleCenter;
            _label.fontSize = 26;
            _label.color = Color.white;
            _label.raycastTarget = false;
            _label.horizontalOverflow = HorizontalWrapMode.Wrap;
        }
    }
}
