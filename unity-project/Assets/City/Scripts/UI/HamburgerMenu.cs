using UnityEngine;
using UnityEngine.UI;
using City.OSM;
using City.Vehicle;
using City.Economy;

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
        private GameObject skinPanel;
        private GameObject profilePanel;
        private GameObject skinSelRow;
        private Transform _canvas;

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
            _canvas = canvasGo.transform;
            var canvas = canvasGo.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            // sopra la HUD (10) e la minimappa (20), sotto la mappa (100)
            canvas.sortingOrder = 25;
            var scaler = canvasGo.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080, 1920);

            // bottone sandwich a meta' schermo, sul bordo destro, centrato verticalmente
            var btn = new GameObject("Btn_Menu", typeof(Image), typeof(Button));
            btn.transform.SetParent(canvasGo.transform, false);
            btn.GetComponent<Image>().color = BtnBg;
            var rt = btn.GetComponent<RectTransform>();
            rt.anchorMin = rt.anchorMax = new Vector2(1f, 0.5f);
            rt.pivot = new Vector2(1f, 0.5f);
            rt.anchoredPosition = new Vector2(-16f, 0f);
            rt.sizeDelta = new Vector2(88f, 72f);
            btn.GetComponent<Button>().onClick.AddListener(() => { Vibration.Vibrate(25); Toggle(); });

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
            prt.anchoredPosition = new Vector2(-16f, 0f);
            prt.sizeDelta = new Vector2(360f, 540f);
            panel.GetComponent<Image>().color = Bg;

            string[] labels =
            {
                "Lavoro (guadagna \u20ac)",
                "Mappa espansa",
                "Personalizza aspetto",
                "Profilo (Sim)",
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
                    OpenSkinPanel();
                },
                () =>
                {
                    SetVisible(false);
                    OpenProfilePanel();
                },
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

        private void Toggle()
        {
            bool open = panel == null || !panel.activeSelf;
            CloseAll();
            if (open) SetVisible(true);
        }

        private void SetVisible(bool v)
        {
            if (panel == null) return;
            panel.SetActive(v);
        }

        // ---- sotto-pannelli (personalizzazione / profilo Sim) ----

        private void CloseAll()
        {
            if (panel != null) panel.SetActive(false);
            if (skinPanel != null) skinPanel.SetActive(false);
            if (profilePanel != null) profilePanel.SetActive(false);
        }

        private static readonly string[] SkinNames =
        {
            "humanMaleA", "humanFemaleA", "zombieMaleA", "zombieFemaleA",
            "citizenM01", "citizenM02", "citizenM03", "citizenM04", "citizenM05",
            "citizenM06", "citizenM07", "citizenM08", "citizenM09", "citizenM10",
            "citizenM11", "citizenM12", "citizenM13", "citizenM14", "citizenM15",
            "citizenM16", "citizenM17", "citizenM18", "citizenM19", "citizenM20",
            "citizenM21", "citizenM22", "citizenM23", "citizenM24",
            "citizenF01", "citizenF02", "citizenF03", "citizenF04", "citizenF05",
            "citizenF06", "citizenF07", "citizenF08", "citizenF09", "citizenF10",
            "citizenF11", "citizenF12", "citizenF13", "citizenF14", "citizenF15",
            "citizenF16", "citizenF17", "citizenF18", "citizenF19", "citizenF20",
            "citizenF21", "citizenF22", "citizenF23", "citizenF24",
        };

        private static string SkinShort(string name)
        {
            if (name == "humanMaleA") return "Uomo";
            if (name == "humanFemaleA") return "Donna";
            if (name == "zombieMaleA") return "Zombie M";
            if (name == "zombieFemaleA") return "Zombie F";
            return name.Replace("citizen", "").ToUpperInvariant();
        }

        private void OpenSkinPanel()
        {
            if (skinPanel == null) BuildSkinPanel();
            skinSelRow = null;
            skinPanel.SetActive(true);
            RefreshSkinGrid();
        }

        private void BuildSkinPanel()
        {
            skinPanel = MakeSubPanel("SkinPanel", 860f, 800f);
            AddHeader(skinPanel, "ASPETTO (pettinatura + vestiti)");

            const int cols = 5;
            const float cellW = 150f, cellH = 54f, gap = 8f;
            float x0 = 30f, y0 = 130f;
            for (int i = 0; i < SkinNames.Length; i++)
            {
                int r = i / cols, c = i % cols;
                string sk = SkinNames[i];
                var cell = new GameObject("SK_" + i, typeof(Image), typeof(Button));
                cell.transform.SetParent(skinPanel.transform, false);
                var crt = cell.GetComponent<RectTransform>();
                crt.anchorMin = new Vector2(0f, 1f);
                crt.anchorMax = new Vector2(0f, 1f);
                crt.pivot = new Vector2(0f, 1f);
                crt.anchoredPosition = new Vector2(x0 + c * (cellW + gap),
                                                  -y0 - r * (cellH + gap));
                crt.sizeDelta = new Vector2(cellW, cellH);
                cell.GetComponent<Image>().color = RowBg;
                cell.GetComponent<Button>().onClick.AddListener(() => ApplySkin(sk));
                var lbl = NewLabel(cell.transform);
                lbl.text = SkinShort(sk);
                lbl.alignment = TextAnchor.MiddleCenter;
                lbl.rectTransform.anchorMin = Vector2.zero;
                lbl.rectTransform.anchorMax = Vector2.one;
                lbl.rectTransform.sizeDelta = Vector2.zero;
                lbl.rectTransform.offsetMin = Vector2.zero;
                lbl.rectTransform.offsetMax = Vector2.zero;
            }
        }

        private void RefreshSkinGrid()
        {
            string cur = City.Player.PlayerAppearance.SavedSkin;
            Transform t = skinPanel.transform;
            for (int i = 0; i < t.childCount; i++)
            {
                var child = t.GetChild(i);
                if (child == null || !child.name.StartsWith("SK_")) continue;
                TryMatchSkin(child, cur);
            }
        }

        private bool TryMatchSkin(Transform child, string cur)
        {
            // rileva l'indice dal nome della cella per evidenziarla
            string nm = child.name;
            if (!nm.StartsWith("SK_")) return false;
            int idx;
            if (!int.TryParse(nm.Substring(3), out idx)) return false;
            if (idx < 0 || idx >= SkinNames.Length) return false;
            var img = child.GetComponent<Image>();
            bool sel = SkinNames[idx] == cur;
            if (img != null)
                img.color = sel ? new Color(0.25f, 0.65f, 0.95f, 1f) : RowBg;
            return sel;
        }

        private void ApplySkin(string skin)
        {
            City.Game g = Game.Instance;
            var pc = g != null ? g.player : null;
            if (pc != null) City.Player.PlayerAppearance.ApplyTo(pc.gameObject, skin);
            PlayerPrefs.SetString(City.Player.PlayerAppearance.PrefKey, skin);
            PlayerPrefs.Save();
            Vibration.Vibrate(25);
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast("Aspetto aggiornato: " + SkinShort(skin));
            CloseAll();
        }

        private void OpenProfilePanel()
        {
            if (profilePanel == null) BuildProfilePanel();
            profilePanel.SetActive(true);
            RefreshProfile();
        }

        private void BuildProfilePanel()
        {
            profilePanel = MakeSubPanel("ProfilePanel", 860f, 1500f);
            AddHeader(profilePanel, "PROFILO (SIM-STYLE)");

            var avatar = new GameObject("Avatar", typeof(Image));
            avatar.transform.SetParent(profilePanel.transform, false);
            var art = avatar.GetComponent<RectTransform>();
            art.anchorMin = new Vector2(0.5f, 1f);
            art.anchorMax = new Vector2(0.5f, 1f);
            art.anchoredPosition = new Vector2(0f, -170f);
            art.sizeDelta = new Vector2(190f, 190f);
            avatar.GetComponent<Image>().color = new Color(0.32f, 0.50f, 0.92f, 1f);

            var nameTxt = NewLabel(profilePanel.transform);
            nameTxt.alignment = TextAnchor.MiddleCenter;
            nameTxt.rectTransform.anchorMin = new Vector2(0.5f, 1f);
            nameTxt.rectTransform.anchorMax = new Vector2(0.5f, 1f);
            nameTxt.rectTransform.pivot = new Vector2(0.5f, 1f);
            nameTxt.rectTransform.anchoredPosition = new Vector2(0f, -380f);
            nameTxt.rectTransform.sizeDelta = new Vector2(700f, 60f);
            nameTxt.fontSize = 34;
            nameTxt.name = "NameTxt";

            var stats = NewLabel(profilePanel.transform);
            stats.fontSize = 28;
            stats.alignment = TextAnchor.UpperLeft;
            stats.rectTransform.anchorMin = new Vector2(0f, 1f);
            stats.rectTransform.anchorMax = new Vector2(0f, 1f);
            stats.rectTransform.pivot = new Vector2(0f, 1f);
            stats.rectTransform.anchoredPosition = new Vector2(40f, -470f);
            stats.rectTransform.sizeDelta = new Vector2(780f, 900f);
            stats.name = "StatsTxt";
        }

        private void RefreshProfile()
        {
            Transform t = profilePanel.transform;
            Text nameTxt = null, stats = null;
            for (int i = 0; i < t.childCount; i++)
            {
                var c = t.GetChild(i);
                if (c.name == "NameTxt") nameTxt = c.GetComponent<Text>();
                else if (c.name == "StatsTxt") stats = c.GetComponent<Text>();
            }
            string name = "Giocatore";
            int level = 1;
            try
            {
                name = Huntix.Bridge.UnityBridge.GetPlayerName();
                level = Huntix.Bridge.UnityBridge.GetPlayerLevel();
            }
            catch (System.Exception) { }
            if (nameTxt != null) nameTxt.text = name + "  -  Lv " + level;
            if (stats != null)
            {
                int clean = PlayerPrefs.GetInt("city_clean_count", 0);
                int sus = City.Environment.ChaosTracker.Suspicion;
                int owned = City.Vehicle.VehicleOwnershipApi.Instance != null
                    ? City.Vehicle.VehicleOwnershipApi.Instance.OwnedCount : 0;
                string skin = City.Player.PlayerAppearance.SavedSkin;
                int age = City.Environment.AgeSystem.Value;
                int ageStage = City.Environment.AgeSystem.StageOf(age);
                stats.text =
                    "ENERGIA" + System.Environment.NewLine + System.Environment.NewLine +
                    "  " + City.Environment.EnergySystem.Value + "/" +
                    City.Environment.EnergySystem.MaxValue + System.Environment.NewLine +
                    System.Environment.NewLine +
                    "SONNO" + System.Environment.NewLine + System.Environment.NewLine +
                    "  " + City.Environment.SleepSystem.Value + "/" +
                    City.Environment.SleepSystem.MaxSleep + System.Environment.NewLine +
                    System.Environment.NewLine +
                    "CITTA PULITA" + System.Environment.NewLine + System.Environment.NewLine +
                    "  " + clean + " rifiuti smaltiti" + System.Environment.NewLine +
                    System.Environment.NewLine +
                    "SOSPETTO (CAOS)" + System.Environment.NewLine + System.Environment.NewLine +
                    "  " + sus + System.Environment.NewLine + System.Environment.NewLine +
                    "VEICOLI POSSEDUTI" + System.Environment.NewLine + System.Environment.NewLine +
                    "  " + owned + System.Environment.NewLine + System.Environment.NewLine +
                    "ASPETTO ATTUALE" + System.Environment.NewLine + System.Environment.NewLine +
                    "  " + skin + System.Environment.NewLine + System.Environment.NewLine +
                    "ETA / CRESCITA" + System.Environment.NewLine + System.Environment.NewLine +
                    "  " + age + " anni (" + City.Environment.AgeSystem.StageName(ageStage) + ")" +
                    System.Environment.NewLine + System.Environment.NewLine +
                    "CASA" + System.Environment.NewLine + System.Environment.NewLine +
                    "  " + (City.Environment.HomeSystem.OwnsHome
                        ? City.Environment.HomeSystem.HomeName +
                          (City.Environment.HomeSystem.HasCarParked
                              ? " (auto in garage)" : "")
                        : "nessuna casa");
            }
        }

        private GameObject MakeSubPanel(string nm, float w, float h)
        {
            var sp = new GameObject(nm, typeof(Image));
            sp.transform.SetParent(_canvas, false);
            var rt = sp.GetComponent<RectTransform>();
            rt.anchorMin = rt.anchorMax = new Vector2(0.5f, 0.5f);
            rt.pivot = new Vector2(0.5f, 0.5f);
            rt.anchoredPosition = Vector2.zero;
            rt.sizeDelta = new Vector2(w, h);
            sp.GetComponent<Image>().color = Bg;
            sp.SetActive(false);

            var close = new GameObject("Close", typeof(Image), typeof(Button));
            close.transform.SetParent(sp.transform, false);
            var crt = close.GetComponent<RectTransform>();
            crt.anchorMin = new Vector2(1f, 1f);
            crt.anchorMax = new Vector2(1f, 1f);
            crt.pivot = new Vector2(1f, 1f);
            crt.anchoredPosition = new Vector2(-16f, -16f);
            crt.sizeDelta = new Vector2(120f, 64f);
            close.GetComponent<Image>().color = BtnBg;
            close.GetComponent<Button>().onClick.AddListener(() => { CloseAll(); });
            var lbl = NewLabel(close.transform);
            lbl.text = "CHIUDI";
            lbl.alignment = TextAnchor.MiddleCenter;
            lbl.rectTransform.anchorMin = Vector2.zero;
            lbl.rectTransform.anchorMax = Vector2.one;
            lbl.rectTransform.sizeDelta = Vector2.zero;
            lbl.rectTransform.offsetMin = Vector2.zero;
            lbl.rectTransform.offsetMax = Vector2.zero;
            return sp;
        }

        private void AddHeader(GameObject sub, string title)
        {
            var h = NewLabel(sub.transform);
            h.alignment = TextAnchor.UpperLeft;
            h.rectTransform.anchorMin = new Vector2(0f, 1f);
            h.rectTransform.anchorMax = new Vector2(1f, 1f);
            h.rectTransform.pivot = new Vector2(0f, 1f);
            h.rectTransform.anchoredPosition = new Vector2(30f, -120f);
            h.rectTransform.sizeDelta = new Vector2(-60f, 60f);
            h.text = title;
        }
    }
}