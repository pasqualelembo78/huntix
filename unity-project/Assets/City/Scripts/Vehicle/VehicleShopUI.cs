using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;
using TMPro;
using City.World;

namespace City.Vehicle
{
    public class VehicleShopUI : MonoBehaviour
    {
        public static VehicleShopUI Instance;

        private GameObject panel;
        private TMP_Text titleText;
        private RectTransform listContent;
        private GameObject listPanel;
        private VehicleInteract currentInteract;

        private static readonly Color PanelBg = new Color(0.11f, 0.12f, 0.14f, 0.97f);
        private static readonly Color Accent = new Color(0.20f, 0.75f, 0.55f, 1f);
        private static readonly Color RowBg = new Color(0.20f, 0.22f, 0.25f, 1f);
        private static readonly Color ButtonBg = new Color(0.28f, 0.30f, 0.34f, 1f);
        private static readonly Color BuyColor = new Color(0.15f, 0.65f, 0.45f, 1f);
        private static readonly Color SellColor = new Color(0.85f, 0.45f, 0.15f, 1f);
        private static readonly Color OwnedColor = new Color(0.3f, 0.3f, 0.3f, 0.8f);

        private TMP_FontAsset font;

        private void Awake()
        {
            Instance = this;
            font = TMP_Settings.defaultFontAsset;
            if (font == null) font = Resources.Load<TMP_FontAsset>("Fonts & Materials/LiberationSans SDF");
        }

        public void ShowPurchaseDialog(VehicleInteract vi)
        {
            if (vi == null) return;
            if (vi.data == null)
            {
                Debug.LogWarning("[VehicleShopUI] dialogo acquisto richiesto per veicolo senza VehicleData");
                return;
            }
            currentInteract = vi;
            ShowPanel(vi.data, vi.IsOwned());
        }

        public void HideDialog()
        {
            if (panel != null) panel.SetActive(false);
            currentInteract = null;
            Time.timeScale = 1f;
        }

        private void ShowPanel(VehicleData data, bool owned)
        {
            if (panel == null) BuildPanel();

            titleText.text = data.vehicleName;
            foreach (Transform child in listContent) Destroy(child.gameObject);

            string category = data.category.ToString();
            MakeRow("Tipo", category);
            MakeRow("Velocita max", data.maxSpeed.ToString("F0") + " km/h");
            MakeRow("Prezzo", "\u20ac" + data.price);

            if (owned)
            {
                MakeButton("GUIDA", () =>
                {
                    var vi = currentInteract;
                    HideDialog();
                    if (vi != null) Game.Instance.EnterVehicle(vi.controller);
                }, BuyColor);

                int resale = data.price * 6 / 10;
                MakeButton("VENDI - \u20ac" + resale, () =>
                {
                    var vi = currentInteract;
                    if (vi == null) return;
                    vi.TrySell(ok =>
                    {
                        if (ok) HideDialog();
                        // se il server rifiuta il pannello resta aperto
                    });
                }, SellColor);
            }
            else
            {
                bool canBuy = Wallet.CanAfford(data.price);
                MakeButton(canBuy ? "COMPRA - \u20ac" + data.price : "SOLDI INSUFFICIENTI", () =>
                {
                    var vi = currentInteract;
                    if (vi == null) return;
                    vi.TryBuy(ok =>
                    {
                        if (!ok) return;
                        HideDialog();
                        Game.Instance.EnterVehicle(vi.controller);
                    });
                }, canBuy ? BuyColor : OwnedColor);
            }

            MakeButton("Chiudi", () => HideDialog(), ButtonBg);

            panel.SetActive(true);
            Time.timeScale = 0f;
        }

        private void BuildPanel()
        {
            // scegli una canvas cliccabile: overlay + GraphicRaycaster,
            // preferendo il sorting order piu' alto (la UI principale).
            // FindObjectOfType<Canvas> poteva restituire un HUD senza
            // raycaster: il pannello si vedeva ma i tap non arrivavano.
            Canvas canvas = null;
            foreach (var c in FindObjectsOfType<Canvas>())
            {
                if (c.renderMode != RenderMode.ScreenSpaceOverlay) continue;
                if (c.GetComponent<GraphicRaycaster>() == null) continue;
                if (canvas == null || c.sortingOrder > canvas.sortingOrder)
                    canvas = c;
            }
            if (canvas == null)
            {
                canvas = FindObjectOfType<Canvas>();
                if (canvas != null)
                    canvas.gameObject.AddComponent<GraphicRaycaster>();
            }
            if (canvas == null)
            {
                Debug.LogError("[VehicleShopUI] BuildPanel: nessuna Canvas nella scena");
                return;
            }
            if (EventSystem.current == null)
            {
                var esGo = new GameObject("EventSystem");
                esGo.AddComponent<EventSystem>();
                esGo.AddComponent<StandaloneInputModule>();
            }
            Debug.Log("[VehicleShopUI] BuildPanel su canvas=" + canvas.name +
                      " order=" + canvas.sortingOrder);

            panel = new GameObject("VehicleShopPanel");
            var prt = panel.AddComponent<RectTransform>();
            prt.SetParent(canvas.transform, false);
            prt.anchorMin = new Vector2(0.5f, 0.5f);
            prt.anchorMax = new Vector2(0.5f, 0.5f);
            prt.offsetMin = new Vector2(-250f, -200f);
            prt.offsetMax = new Vector2(250f, 200f);

            var bg = panel.AddComponent<UnityEngine.UI.Image>();
            bg.color = PanelBg;

            titleText = MakeText(prt, "", 36f, Color.white, TextAlignmentOptions.Left,
                new Vector2(0f, 1f), new Vector2(1f, 1f), new Vector2(20f, -15f), new Vector2(-20f, -60f));

            var scrollRt = MakeRect("Scroll", prt, new Vector2(0f, 0f), new Vector2(1f, 1f),
                new Vector2(15f, 70f), new Vector2(-15f, -10f));
            var sr = scrollRt.gameObject.AddComponent<UnityEngine.UI.ScrollRect>();
            var mask = scrollRt.gameObject.AddComponent<UnityEngine.UI.Mask>();
            mask.showMaskGraphic = true;
            var scrollBg = scrollRt.gameObject.AddComponent<UnityEngine.UI.Image>();
            scrollBg.color = new Color(0f, 0f, 0f, 0.2f);

            listContent = MakeRect("Content", scrollRt, new Vector2(0f, 1f), new Vector2(1f, 1f),
                Vector2.zero, Vector2.zero);
            listContent.pivot = new Vector2(0.5f, 1f);
            var vlg = listContent.gameObject.AddComponent<UnityEngine.UI.VerticalLayoutGroup>();
            vlg.childControlWidth = true;
            vlg.childControlHeight = false;
            vlg.childForceExpandWidth = true;
            vlg.childForceExpandHeight = false;
            vlg.spacing = 4f;
            vlg.padding = new RectOffset(4, 4, 4, 4);
            listContent.gameObject.AddComponent<ContentSizeFitter>().verticalFit = ContentSizeFitter.FitMode.PreferredSize;
            sr.content = listContent;
            sr.viewport = scrollRt;
            sr.vertical = true;
            sr.horizontal = false;

            panel.SetActive(false);
        }

        private void MakeRow(string label, string value)
        {
            var row = MakeRect("Row", listContent, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            row.sizeDelta = new Vector2(0f, 40f);
            row.anchorMin = new Vector2(0f, 1f);
            row.anchorMax = new Vector2(1f, 1f);
            row.pivot = new Vector2(0.5f, 1f);
            var img = row.gameObject.AddComponent<UnityEngine.UI.Image>();
            img.color = RowBg;

            MakeText(row, label, 22f, new Color(0.7f, 0.7f, 0.7f), TextAlignmentOptions.Left,
                new Vector2(0f, 0f), new Vector2(0.5f, 1f), new Vector2(12f, 0f), new Vector2(0f, 0f));
            MakeText(row, value, 22f, Color.white, TextAlignmentOptions.Right,
                new Vector2(0.5f, 0f), new Vector2(0.95f, 1f), new Vector2(0f, 0f), new Vector2(-12f, 0f));
        }

        private void MakeButton(string label, UnityEngine.Events.UnityAction onClick, Color bgColor)
        {
            var rt = MakeRect("Btn", listContent, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            rt.sizeDelta = new Vector2(0f, 50f);
            rt.anchorMin = new Vector2(0f, 1f);
            rt.anchorMax = new Vector2(1f, 1f);
            rt.pivot = new Vector2(0.5f, 1f);
            var img = rt.gameObject.AddComponent<UnityEngine.UI.Image>();
            img.color = bgColor;
            var btn = rt.gameObject.AddComponent<Button>();
            btn.targetGraphic = img;
            btn.onClick.AddListener(onClick);
            MakeText(rt, label, 26f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
        }

        private RectTransform MakeRect(string name, Transform parent, Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            var go = new GameObject(name, typeof(RectTransform));
            var rt = go.GetComponent<RectTransform>();
            rt.SetParent(parent, false);
            rt.anchorMin = anchorMin;
            rt.anchorMax = anchorMax;
            rt.offsetMin = offsetMin;
            rt.offsetMax = offsetMax;
            return rt;
        }

        private TMP_Text MakeText(RectTransform parent, string content, float size, Color color, TextAlignmentOptions alignment, Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            var rt = MakeRect("T", parent, anchorMin, anchorMax, offsetMin, offsetMax);
            var text = rt.gameObject.AddComponent<TextMeshProUGUI>();
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
