using UnityEngine;
using UnityEngine.UI;

namespace City.UI
{
    /// <summary>
    /// Piccolo dialog modale SI/NO riutilizzabile per le offerte contestuali
    /// ("Vuoi le indicazioni per l'officina?"). Non tocca il dialog NPC.
    /// </summary>
    public class OfferDialog : MonoBehaviour
    {
        public static OfferDialog Instance { get; private set; }

        private GameObject panel;
        private Text titleText;
        private Text bodyText;
        private System.Action onYes;
        private System.Action onNo;

        // secondo pannello: "come la raggiungiamo?" (freccetta o teletrasporto)
        private GameObject travelPanel;
        private Text travelTitle;
        private Text travelBody;
        private System.Action onNavigate;
        private System.Action onTeleport;
        private System.Action onCancel;

        public static void Ensure()
        {
            if (Instance != null) return;
            var go = new GameObject("OfferDialog", typeof(OfferDialog));
            DontDestroyOnLoad(go);
        }

        private void Awake()
        {
            Instance = this;
            Build();
            panel.SetActive(false);
        }

        /// <summary>Mostra la domanda; onYes su "SI'", onNo su "NO".</summary>
        public static void Offer(string title, string body, System.Action yes,
            System.Action no = null)
        {
            if (Instance == null) Ensure();
            if (Instance == null) { yes?.Invoke(); return; }
            Instance.Show(title, body, yes, no);
        }

        /// <summary>
        /// Seconda domanda (dopo il SI'): come vuoi raggiungere la meta?
        /// onNavigate sulla freccetta, onTeleport sul teletrasporto. Entrambe
        /// chiudono SUBITO il dialogo.
        /// </summary>
        public static void OfferTravel(string title, string body,
            System.Action navigate, System.Action teleport,
            System.Action cancel = null)
        {
            if (Instance == null) Ensure();
            if (Instance == null) { navigate?.Invoke(); return; }
            Instance.ShowTravel(title, body, navigate, teleport, cancel);
        }

        private void Show(string title, string body, System.Action yes,
            System.Action no)
        {
            titleText.text = title;
            bodyText.text = body;
            onYes = yes;
            onNo = no;
            panel.SetActive(true);
        }

        private void ShowTravel(string title, string body,
            System.Action navigate, System.Action teleport,
            System.Action cancel)
        {
            travelTitle.text = title;
            var brt = travelBody.rectTransform;
            brt.anchoredPosition = new Vector2(0f, -84f);
            brt.sizeDelta = new Vector2(-96f, 86f);
            travelBody.text = body;
            onNavigate = navigate;
            onTeleport = teleport;
            onCancel = cancel;
            travelPanel.SetActive(true);
        }

        private void Build()
        {
            var canvasGo = new GameObject("Canvas", typeof(Canvas),
                typeof(GraphicRaycaster));
            canvasGo.transform.SetParent(transform, false);
            canvasGo.AddComponent<UnityEngine.UI.CanvasScaler>();
            canvasGo.GetComponent<Canvas>().renderMode =
                RenderMode.ScreenSpaceOverlay;
            var dlgScaler = canvasGo.GetComponent<UnityEngine.UI.CanvasScaler>();
            dlgScaler.uiScaleMode =
                UnityEngine.UI.CanvasScaler.ScaleMode.ScaleWithScreenSize;
            dlgScaler.referenceResolution = new Vector2(1080f, 1920f);
            // sopra HUD (10), minimappa (20) e mappa espansa (100), altrimenti
            // i bottoni SI/NO non ricevono il tocco (zone HUD raycastTarget=true).
            var dlgCanvas = canvasGo.GetComponent<Canvas>();
            dlgCanvas.sortingOrder = 999;

            panel = new GameObject("Pannello", typeof(Image));
            panel.transform.SetParent(canvasGo.transform, false);
            var pimg = panel.GetComponent<Image>();
            pimg.color = new Color(0.07f, 0.09f, 0.12f, 0.96f);

            var prt = panel.GetComponent<RectTransform>();
            prt.anchorMin = prt.anchorMax = prt.pivot =
                new Vector2(0.5f, 0.5f);
            prt.sizeDelta = new Vector2(640f, 300f);

            titleText = MakeTextOn(panel.transform, "Titolo", 34,
                new Color(0.95f, 0.78f, 0.30f), TextAnchor.UpperCenter,
                new Vector2(0f, -22f));
            var trt = titleText.rectTransform;
            trt.anchorMin = new Vector2(0f, 1f);
            trt.anchorMax = new Vector2(1f, 1f);
            trt.pivot = new Vector2(0.5f, 1f);
            trt.sizeDelta = new Vector2(-48f, 44f);

            bodyText = MakeTextOn(panel.transform, "Testo", 28,
                new Color(0.92f, 0.94f, 0.96f), TextAnchor.UpperCenter,
                new Vector2(0f, -76f));
            var brt = bodyText.rectTransform;
            brt.anchorMin = new Vector2(0f, 1f);
            brt.anchorMax = new Vector2(1f, 1f);
            brt.pivot = new Vector2(0.5f, 1f);
            brt.anchoredPosition = new Vector2(0f, -76f);
            brt.sizeDelta = new Vector2(-64f, 130f);

            MakeButtonOn(panel.transform, "SI'", new Vector2(-160f, 26f),
                new Color(0.20f, 0.55f, 0.25f), () =>
                {
                    panel.SetActive(false);
                    var cb = onYes; onYes = null;
                    cb?.Invoke();
                });
            MakeButtonOn(panel.transform, "NO", new Vector2(160f, 26f),
                new Color(0.55f, 0.22f, 0.20f), () =>
                {
                    panel.SetActive(false);
                    var cb = onNo; onNo = null;
                    onYes = null;
                    cb?.Invoke();
                });

            BuildTravelPanel();
        }

        private void BuildTravelPanel()
        {
            travelPanel = new GameObject("PannelloViaggio", typeof(Image));
            travelPanel.transform.SetParent(transform, false);
            var trt = travelPanel.GetComponent<RectTransform>();
            trt.anchorMin = trt.anchorMax = trt.pivot =
                new Vector2(0.5f, 0.5f);
            trt.sizeDelta = new Vector2(640f, 340f);
            if (panel != null)
                travelPanel.GetComponent<Image>().color =
                    panel.GetComponent<Image>().color;

            // canvas dedicato: deve stare SOPRA i pannelli offerta (già a 999).
            {
                var tgo = new GameObject("TravelCanvas", typeof(Canvas),
                    typeof(UnityEngine.UI.CanvasScaler));
                tgo.transform.SetParent(travelPanel.transform, false);
                var tc = tgo.GetComponent<Canvas>();
                tc.renderMode = RenderMode.ScreenSpaceOverlay;
                tc.sortingOrder = 1000;
                var sc = tgo.GetComponent<UnityEngine.UI.CanvasScaler>();
                sc.uiScaleMode =
                    UnityEngine.UI.CanvasScaler.ScaleMode.ScaleWithScreenSize;
                sc.referenceResolution = new Vector2(1080f, 1920f);
                tgo.AddComponent<GraphicRaycaster>();

                travelTitle = MakeTextOn(tgo.transform, "Titolo", 34,
                    new Color(0.95f, 0.78f, 0.30f), TextAnchor.UpperCenter,
                    new Vector2(-24f, -26f));
                travelBody = MakeTextOn(tgo.transform, "Testo", 28,
                    new Color(0.92f, 0.94f, 0.96f), TextAnchor.UpperCenter,
                    new Vector2(-16f, -92f));
                MakeButtonOn(tgo.transform, "FRECCETTA",
                    new Vector2(-160f, -170f),
                    new Color(0.85f, 0.62f, 0.10f), () =>
                    {
                        travelPanel.SetActive(false);
                        var cb = onNavigate; onNavigate = null;
                        cb?.Invoke();
                    });
                MakeButtonOn(tgo.transform, "TELEPORTA",
                    new Vector2(160f, -170f),
                    new Color(0.16f, 0.48f, 0.30f), () =>
                    {
                        travelPanel.SetActive(false);
                        var cb = onTeleport; onTeleport = null;
                        cb?.Invoke();
                    });
                MakeButtonOn(tgo.transform, "ESCI",
                    new Vector2(0f, -230f),
                    new Color(0.55f, 0.22f, 0.20f), () =>
                    {
                        travelPanel.SetActive(false);
                        var cb = onCancel; onCancel = null;
                        cb?.Invoke();
                    });
            }
            travelPanel.SetActive(false);
        }

        private Text MakeTextOn(Transform parent, string name, int size,
            Color col, TextAnchor align, Vector2 anchoredPos)
        {
            var go = new GameObject(name, typeof(Text));
            go.transform.SetParent(parent, false);
            var t = go.GetComponent<Text>();
            t.font = City.Vehicle.CompassUI.UiFont();
            t.fontSize = size;
            t.color = col;
            t.alignment = align;
            var rt = t.rectTransform;
            rt.anchorMin = rt.anchorMax = rt.pivot = new Vector2(0.5f, 0.5f);
            rt.anchoredPosition = anchoredPos;
            rt.sizeDelta = new Vector2(600f, 48f);
            return t;
        }

        private void MakeButtonOn(Transform parent, string caption,
            Vector2 pos, Color col, System.Action onClick)
        {
            var go = new GameObject("Btn_" + caption, typeof(Button));
            go.transform.SetParent(parent, false);
            var img = go.AddComponent<Image>();
            img.color = col;
            var rt = go.GetComponent<RectTransform>();
            rt.anchorMin = rt.anchorMax = rt.pivot =
                new Vector2(0.5f, 0.5f);
            rt.anchoredPosition = pos;
            rt.sizeDelta = new Vector2(260f, 62f);

            var lgo = new GameObject("T", typeof(Text));
            lgo.transform.SetParent(go.transform, false);
            var t = lgo.GetComponent<Text>();
            t.font = City.Vehicle.CompassUI.UiFont();
            t.fontSize = 30;
            t.text = caption;
            t.alignment = TextAnchor.MiddleCenter;
            t.color = Color.white;
            var trt = t.rectTransform;
            trt.anchorMin = Vector2.zero;
            trt.anchorMax = Vector2.one;
            trt.sizeDelta = Vector2.zero;

            go.GetComponent<Button>().onClick.AddListener(() => onClick());
        }
    }
}
