using UnityEngine;
using UnityEngine.UI;
using City.OSM;
using City.Vehicle;
using City.Economy;
using City.Environment;

namespace City.UI
{
    /// <summary>
    /// Menu hamburger della schermata principale (HUD di gioco): bottone
    /// "sandwich" a meta' schermo sul lato destro (per non interferire con
    /// la minimappa in alto a destra) che apre un pannello con le azioni
    /// rapide (mappa espansa, note legali, uscita). Costruito a runtime
    /// come bussola/minimappa/mappa; la voce "Esci" usa la stessa
    /// conferma anti-tap-accidentale della HUD.
    /// </summary>
    public class HamburgerMenu : MonoBehaviour
    {
        private static HamburgerMenu _instance;
        private GameObject panel;

        private static readonly Color Bg = new Color(0.09f, 0.11f, 0.16f, 0.97f);
        private static readonly Color RowBg = new Color(0.20f, 0.22f, 0.26f, 1f);
        private static readonly Color BtnBg = new Color(0.28f, 0.30f, 0.34f, 1f);

        /// <summary>Crea (o riusa) il menu hamburger della HUD.</summary>
        public static void Ensure(UIManager ui)
        {
            if (_instance != null) return;
            var go = new GameObject("HamburgerMenu", typeof(HamburgerMenu));
            DontDestroyOnLoad(go);
        }

        private void Awake()
        {
            _instance = this;
            Build();
        }

        private void Build()
        {
            var canvasGo = new GameObject("HamburgerCanvas",
                typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            canvasGo.transform.SetParent(transform, false);
            var canvas = canvasGo.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            // sopra la HUD (10) e la minimappa (20), sotto la mappa (100)
            canvas.sortingOrder = 25;
            var scaler = canvasGo.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080, 1920);

            // bottone sandwich a meta' schermo, sempre sul bordo destro
            var btn = new GameObject("Btn_Menu", typeof(Image), typeof(Button));
            btn.transform.SetParent(canvasGo.transform, false);
            btn.GetComponent<Image>().color = BtnBg;
            var rt = btn.GetComponent<RectTransform>();
            rt.anchorMin = rt.anchorMax = new Vector2(1f, 0.5f);
            rt.pivot = new Vector2(1f, 0.5f);
            rt.anchoredPosition = new Vector2(-64f, 0f);
            rt.sizeDelta = new Vector2(88f, 72f);
            btn.GetComponent<Button>().onClick.AddListener(Toggle);

            // tre barrette disegnate (il font UI potrebbe non avere il
            // glifo "☰")
            for (int i = 0; i < 3; i++)
            {
                var bar = new GameObject("Bar", typeof(Image));
                bar.transform.SetParent(btn.transform, false);
                var brt = bar.GetComponent<RectTransform>();
                brt.anchorMin = brt.anchorMax = new Vector2(0.5f, 0.5f);
                brt.anchoredPosition = new Vector2(0f, 14f - i * 15f);
                brt.sizeDelta = new Vector2(40f, 7f);
                bar.GetComponent<Image>().color = Color.white;
            }

            BuildPanel(canvasGo.transform);
        }

        private void BuildPanel(Transform parent)
        {
            panel = new GameObject("MenuPanel", typeof(Image));
            panel.transform.SetParent(parent, false);
            var prt = panel.GetComponent<RectTransform>();
            prt.anchorMin = prt.anchorMax = new Vector2(1f, 0.5f);
            prt.pivot = new Vector2(1f, 0.5f);
            prt.anchoredPosition = new Vector2(-180f, 0f);
            prt.sizeDelta = new Vector2(360f, 880f);
            panel.GetComponent<Image>().color = Bg;

            string[] labels =
            {
                "Lavoro (guadagna \u20ac)",
                "Mappa espansa",
                "ATM / Banca (freccia)",
                "Bar (freccia)",
                "Officina (freccia)",
                "Concessionaria (freccia)",
                "Garage (freccia)",
                "Ospedale (freccia)",
                "Scuola (freccia)",
                "Note legali",
                "Esci dal gioco",
            };
            System.Action[] actions =
            {
                () =>
                {
                    SetVisible(false);
                    if (JobManager.Instance != null)
                        JobManager.Instance.OpenPanel();
                    else if (UIManager.Instance != null)
                        UIManager.Instance.ShowToast("Lavori non disponibili");
                },
                () =>
                {
                    SetVisible(false);
                    MapSelectUI.Open();
                },
                () =>
                {
                    SetVisible(false);
                    Transform player = null;
                    var pc = City.Player.PlayerController.Instance;
                    if (pc != null && pc.transform != null) player = pc.transform;
                    if (player == null)
                    {
                        var mgr = City.OSM.CityChunkedWorld.Instance != null
                            ? City.OSM.CityChunkedWorld.Instance.Manager : null;
                        if (mgr != null) player = mgr.target;
                    }
                    InteractableProp best = null;
                    float bestD = float.MaxValue;
                    foreach (var pr in InteractableProp.All)
                    {
                        if (pr == null || pr.kind != InteractableProp.Kind.Atm)
                            continue;
                        if (player == null)
                        {
                            if (best == null) best = pr;
                            continue;
                        }
                        float d = Vector3.Distance(
                            pr.transform.position, player.position);
                        if (d < bestD) { bestD = d; best = pr; }
                    }
                    if (best == null && player != null)
                    {
                        var geo = WorldOrigin.ToGeo(player.position);
                        var bank = VehiclePoiRegistry.Nearest("bank", geo.lat, geo.lng);
                        if (bank != null)
                        {
                            NavigationState.Set(
                                string.IsNullOrEmpty(bank.name) ? "Banca / ATM" : bank.name,
                                "bank", bank.lat, bank.lng);
                            if (UIManager.Instance != null)
                                UIManager.Instance.ShowToast("Banca impostata come destinazione");
                            return;
                        }
                    }
                    if (best == null)
                    {
                        if (UIManager.Instance != null)
                            UIManager.Instance.ShowToast("Nessuna banca/ATM nelle vicinanze");
                        return;
                    }
                    var g = WorldOrigin.ToGeo(best.transform.position);
                    NavigationState.Set("ATM / Banca", "bank", g.lat, g.lng);
                    if (UIManager.Instance != null)
                        UIManager.Instance.ShowToast("ATM impostato come destinazione");
                },
                () => NavigateTo("bar", "Bar"),
                () => NavigateTo("repair", "Officina"),
                () => NavigateTo("dealer", "Concessionaria"),
                () => NavigateTo("garage", "Garage"),
                () => NavigateTo("hospital", "Ospedale"),
                () => NavigateTo("school", "Scuola"),
                () =>
                {
                    SetVisible(false);
                    if (UIManager.Instance != null)
                        UIManager.Instance.ShowLegal();
                },
                () =>
                {
                    SetVisible(false);
                    if (UIManager.Instance != null)
                        UIManager.Instance.OnExitPressedPublic();
                },
            };

            for (int i = 0; i < labels.Length; i++)
            {
                int idx = i;
                var row = new GameObject("M_" + i, typeof(Image), typeof(Button));
                row.transform.SetParent(panel.transform, false);
                var rrt = row.GetComponent<RectTransform>();
                rrt.anchorMin = new Vector2(0f, 1f);
                rrt.anchorMax = new Vector2(1f, 1f);
                rrt.pivot = new Vector2(0f, 1f);
                rrt.anchoredPosition = new Vector2(6f, -8f - i * 74f);
                rrt.sizeDelta = new Vector2(-12f, 70f);
                row.GetComponent<Image>().color = RowBg;
                row.GetComponent<Button>().onClick.AddListener(() => actions[idx]());

                var lbl = NewLabel(row.transform);
                lbl.text = labels[idx];
                lbl.alignment = TextAnchor.MiddleLeft;
                lbl.rectTransform.anchorMin = Vector2.zero;
                lbl.rectTransform.anchorMax = Vector2.one;
                lbl.rectTransform.sizeDelta = Vector2.zero;
                lbl.rectTransform.offsetMin = new Vector2(16f, 0f);
                lbl.rectTransform.offsetMax = new Vector2(-16f, 0f);
            }

            panel.SetActive(false);
        }

        private static Text NewLabel(Transform parent)
        {
            var go = new GameObject("L", typeof(Text));
            go.transform.SetParent(parent, false);
            var t = go.GetComponent<Text>();
            t.font = CompassUI.UiFont();
            if (t.font != null) t.fontSize = 28;
            t.color = Color.white;
            t.raycastTarget = false;
            return t;
        }

        private static void NavigateTo(string kind, string display)
        {
            var player = Player();
            if (player == null)
            {
                if (UIManager.Instance != null)
                    UIManager.Instance.ShowToast(display + " non disponibile");
                return;
            }
            var g = WorldOrigin.ToGeo(player.position);
            var poi = VehiclePoiRegistry.Nearest(kind, g.lat, g.lng);
            if (poi == null)
            {
                if (UIManager.Instance != null)
                    UIManager.Instance.ShowToast("Nessun " + display + " nelle vicinanze");
                return;
            }
            NavigationState.Set(
                string.IsNullOrEmpty(poi.name) ? display : poi.name,
                kind, poi.lat, poi.lng);
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast(display + " impostato come destinazione");
        }

        private static Transform Player()
        {
            var pc = City.Player.PlayerController.Instance;
            if (pc != null && pc.transform != null) return pc.transform;
            var mgr = City.OSM.CityChunkedWorld.Instance != null
                ? City.OSM.CityChunkedWorld.Instance.Manager : null;
            return mgr != null ? mgr.target : null;
        }

        private void Toggle()
        {
            SetVisible(panel != null && !panel.activeSelf);
        }

        private void SetVisible(bool v)
        {
            if (panel == null) return;
            panel.SetActive(v);
        }
    }
}