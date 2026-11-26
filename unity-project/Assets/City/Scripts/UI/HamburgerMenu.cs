using System.Collections.Generic;
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
        private static GameObject _menuRoot;
        private GameObject panel;
        private GameObject skinPanel;
        private GameObject profilePanel;
        private GameObject cinematicPanel;
        // Tutti i sotto-pannelli creati a runtime: CloseAll li chiude anche se
        // non sono campi dedicati, così nessuna funzione resta aperta.
        private readonly List<GameObject> _subPanels = new List<GameObject>();
        private GameObject skinSelRow;
        private Text _styleLabel;
        private Text _shopLabel;
        private Text _venueLabel;
        private Text _qualityLabel;
        private Text _perfLabel;
        private Text _touchLabel;
        private Text _worldLabel;
        private Text _menuAnimLabel;
        private Text _qaConsoleLabel;
        private Text _openSourceLabel;
        private Text _cinematicLabel;
        private Text[] _cinematicDetailLabels;
        private Text _companionLabel;
        private Transform _canvas;

        private static readonly Color Bg = new Color(0.09f, 0.11f, 0.16f, 0.97f);
        private static readonly Color RowBg = new Color(0.20f, 0.22f, 0.26f, 1f);
        private static readonly Color BtnBg = new Color(0.28f, 0.30f, 0.34f, 1f);

        /// <summary>Crea (o riusa) il menu hamburger della HUD.</summary>
        public static void Ensure(UIManager ui)
        {
            if (_instance != null) return;
            var go = new GameObject("HamburgerMenu", typeof(HamburgerMenu));
            _menuRoot = go;
            DontDestroyOnLoad(go);
        }

        /// <summary>Ripristina il SOLO pulsante hamburger dopo un cambio di
        /// camera o un reload di scena. Il root e' DontDestroyOnLoad: puo'
        /// restare disattivato, o perdere il riferimento al Canvas, senza che
        /// nessun codice lo riattivi. NON apre il pannello e NON tocca gli
        /// altri HUD. Chiamato da CameraRig all'avvio e a ogni toggle 1A/3A.
        /// </summary>
        public static void EnsureVisible()
        {
            HamburgerMenu menu = _instance;
            // Il root viene tenuto a parte: GetComponent lo trova anche se
            // l'intero GameObject e' stato disattivato (e in quel caso Awake
            // non viene richiamato, quindi _instance non basta).
            if (menu == null && _menuRoot != null)
                menu = _menuRoot.GetComponent<HamburgerMenu>();
            if (menu == null)
            {
                if (UIManager.Instance != null) Ensure(UIManager.Instance);
                menu = _instance;
            }
            if (menu == null)
            {
                OsmDiag.Log("[Hamburger][Ensure] menu assente (UIManager non pronto)");
                return;
            }
            menu.RestoreButton();
        }

        private void RestoreButton()
        {
            bool changed = false;
            if (!gameObject.activeSelf)
            {
                gameObject.SetActive(true);
                changed = true;
            }
            if (_canvas == null)
            {
                Canvas found = GetComponentInChildren<Canvas>(true);
                if (found != null) _canvas = found.transform;
            }
            bool canvasOk = false;
            if (_canvas != null)
            {
                Canvas cv = _canvas.GetComponent<Canvas>();
                canvasOk = cv != null;
                if (cv != null && !cv.enabled)
                {
                    cv.enabled = true;
                    changed = true;
                }
                if (!_canvas.gameObject.activeSelf)
                {
                    _canvas.gameObject.SetActive(true);
                    changed = true;
                }
                Transform btn = _canvas.Find("Btn_Menu");
                if (btn != null && !btn.gameObject.activeSelf)
                {
                    btn.gameObject.SetActive(true);
                    changed = true;
                }
            }
            OsmDiag.Log("[Hamburger][Ensure] " +
                (changed ? "pulsante ripristinato" : "pulsante gia' attivo") +
                " root=" + gameObject.activeSelf + " canvas=" + canvasOk);
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
            // Altezza calcolata sulle voci del menu: l'aggiunta delle voci
            // Fase 2 non deve lasciare l'ultima riga fuori dal pannello.
            prt.sizeDelta = new Vector2(360f, 1670f);
            panel.GetComponent<Image>().color = Bg;

            // Primo livello: solo cio' che serve DURANTE il gioco. Le
            // impostazioni e le uova/ricette restano raggiungibili, ma in
            // sotto-pannelli dedicati (approvazione del proprietario 25/11):
            // nessuna funzione e' stata eliminata, cambia solo la collocazione.
            // LegacyIndex conserva l'indice storico delle etichette dinamiche:
            // i test e il refresh dei toggle restano validi senza duplicare
            // codice ne' cambiare i formati delle preferenze.
            var entries = new List<MenuEntry>
            {
                new MenuEntry("Lavoro (guadagna \u20ac)", 0, () =>
                {
                    SetVisible(false);
                    if (JobManager.Instance != null)
                        JobManager.Instance.OpenPanel();
                    else if (UIManager.Instance != null)
                        UIManager.Instance.ShowToast("Lavori non disponibili");
                }),
                new MenuEntry("Mappa espansa", 1, () =>
                {
                    SetVisible(false);
                    MapSelectUI.Open();
                }),
                new MenuEntry("\uD83D\uDD0D Cerca destinazione", -1, () =>
                {
                    SetVisible(false);
                    MapSelectUI.OpenSearch();
                }),
                new MenuEntry("Profilo (Sim)", 4, () =>
                {
                    SetVisible(false);
                    OpenProfilePanel();
                }),
                new MenuEntry("Stile edifici: Veloce", 3, () => ToggleBuildingStyle()),
                new MenuEntry("Negozio: Huntix", 19, () => ToggleShopStyle()),
                new MenuEntry("Eventi sportivi: Off", 20, () => ToggleVenueEvents()),
                new MenuEntry("Uova e ricette (3 in 1)", -1, () => OpenEggPanel()),
                new MenuEntry("Impostazioni e strumenti", -1, () => OpenSettingsPanel()),
                new MenuEntry("Esci dal gioco", 7, () =>
                {
                    SetVisible(false);
                    if (UIManager.Instance != null)
                        UIManager.Instance.OnExitPressedPublic();
                }),
                new MenuEntry("Chiudi menu", 21, () => CloseAll()),
            };

            for (int i = 0; i < entries.Count; i++)
            {
                AddMenuRow(panel.transform, entries[i], i, 74f, 6f);
            }

            AddPoiHelpBlock(panel.transform, 11);
            panel.SetActive(false);
        }

        /// <summary>Riga cliccabile del menu (o di un sotto-pannello). Il nome
        /// M_/S_ mantiene la convenzione usata dai test statici e dal debug.</summary>
        private void AddMenuRow(Transform parent, MenuEntry entry, int i,
            float itemH, float leftPad)
        {
            var row = new GameObject((parent == panel.transform ? "M_" : "S_") + i,
                typeof(Image), typeof(Button));
            row.transform.SetParent(parent, false);
            var rrt = row.GetComponent<RectTransform>();
            rrt.anchorMin = new Vector2(0f, 1f);
            rrt.anchorMax = new Vector2(1f, 1f);
            rrt.pivot = new Vector2(0f, 1f);
            rrt.anchoredPosition = new Vector2(leftPad, -8f - i * itemH);
            rrt.sizeDelta = new Vector2(-12f, itemH - 4f);
            row.GetComponent<Image>().color = RowBg;
            row.GetComponent<Button>().onClick.AddListener(() => entry.Action());

            var lbl = NewLabel(row.transform);
            lbl.text = entry.Label;
            BindLegacyLabel(entry.LegacyIndex, lbl);
            lbl.alignment = TextAnchor.MiddleLeft;
            lbl.rectTransform.anchorMin = Vector2.zero;
            lbl.rectTransform.anchorMax = Vector2.one;
            lbl.rectTransform.sizeDelta = Vector2.zero;
            lbl.rectTransform.offsetMin = new Vector2(16f, 0f);
            lbl.rectTransform.offsetMax = new Vector2(-16f, 0f);
        }

        /// <summary>Collega l'etichetta dinamica al vecchio indice: le righe
        /// ora vivono in sotto-pannelli diversi, ma i toggle e i test restano
        /// agganciati alla stessa chiave stabile.</summary>
        private void BindLegacyLabel(int idx, Text lbl)
        {
            if (idx == 3) _styleLabel = lbl;
            if (idx == 6) _qualityLabel = lbl;
            if (idx == 8) _touchLabel = lbl;
            if (idx == 9) _worldLabel = lbl;
            if (idx == 10) _menuAnimLabel = lbl;
            if (idx == 11) _qaConsoleLabel = lbl;
            if (idx == 12) _openSourceLabel = lbl;
            if (idx == 13) _companionLabel = lbl;
            if (idx == 17) _cinematicLabel = lbl;
            if (idx == 19) _shopLabel = lbl;
            if (idx == 20) _venueLabel = lbl;
            if (idx == 21) _perfLabel = lbl;
        }

        /// <summary>Spiegazione permanente nel pannello: l'utente non deve più
        /// chiedersi come si raggiungono stadi, ippodromi e case visitabili.
        /// Testi espliciti, in italiano, con il percorso operativo completo.</summary>
        private static void AddPoiHelpBlock(Transform parent, int firstRow)
        {
            float y = -8f - (firstRow + 0.5f) * 74f;
            string[] lines =
            {
                "COME RAGGIUNGERE I LUOGHI",
                "Mappa espansa → menu (☰) → scegli CASA, STADIO o IPPODROMO.",
                "Tocca il nome → TELEPORTA QUI (arrivi davvero) o FRECCETTA.",
                "Sottopassaggi = categoria SOTTERRANEO nella mappa espansa.",
                "🔍 Cerca destinazione trova un POI o una via + città.",
            };
            for (int i = 0; i < lines.Length; i++)
            {
                var t = NewLabel(parent);
                t.text = lines[i];
                t.fontSize = i == 0 ? 24 : 20;
                t.color = i == 0
                    ? new Color(1f, 0.85f, 0.30f)
                    : new Color(0.82f, 0.85f, 0.90f);
                t.alignment = TextAnchor.MiddleLeft;
                t.rectTransform.anchorMin = new Vector2(0f, 1f);
                t.rectTransform.anchorMax = new Vector2(1f, 1f);
                t.rectTransform.pivot = new Vector2(0f, 1f);
                t.rectTransform.anchoredPosition = new Vector2(16f, y - i * 34f);
                t.rectTransform.sizeDelta = new Vector2(-28f, 32f);
            }
        }

        // ── Uova e ricette: tre pannelli Android già esistenti, un'unica
        //    voce di primo livello. Nessun evento o schermata viene rimossa. ──

        private GameObject _eggPanel;

        private void OpenEggPanel()
        {
            SetVisible(false);
            if (_eggPanel == null) BuildEggPanel();
            RefreshAllDynamicLabels();
            _eggPanel.SetActive(true);
        }

        private void BuildEggPanel()
        {
            _eggPanel = MakeSubPanel("EggPanel", 620f, 410f);
            AddHeader(_eggPanel, "UOVA E RICETTE");
            var rows = new List<MenuEntry>
            {
                new MenuEntry("Fondi le uova (crafting)", 14, () =>
                {
                    // Fase 2 · la fusione vive nell'app Android, dove sono le
                    // uova schiuse reali. Unity apre la UI sua con un evento
                    // dedicato; Android risponde con FusionCrafted.
                    CloseAll();
                    try
                    {
                        Huntix.Bridge.UnityBridge.SendMessageToAndroid(
                            "CraftRequest", "{\"action\":\"open\"}");
                    }
                    catch (System.Exception e)
                    {
                        Debug.LogWarning("[HamburgerMenu] CraftRequest: " + e.Message);
                    }
                }),
                new MenuEntry("Le tue uova (collezione)", 15, () =>
                {
                    // Fase 2 · la collezione e' nell'app Android, dove vive
                    // l'inventario uova reale.
                    CloseAll();
                    try
                    {
                        Huntix.Bridge.UnityBridge.SendMessageToAndroid(
                            "InventoryViewRequest", "{\"action\":\"open\"}");
                    }
                    catch (System.Exception e)
                    {
                        Debug.LogWarning("[HamburgerMenu] InventoryViewRequest: " + e.Message);
                    }
                }),
                new MenuEntry("Ricette di fusione (guida)", 16, () =>
                {
                    // Fase 2 · il ricettario e' una sezione in sola lettura
                    // della UI crafting Android.
                    CloseAll();
                    try
                    {
                        Huntix.Bridge.UnityBridge.SendMessageToAndroid(
                            "RecipeRequest", "{\"action\":\"open\"}");
                    }
                    catch (System.Exception e)
                    {
                        Debug.LogWarning("[HamburgerMenu] RecipeRequest: " + e.Message);
                    }
                }),
            };
            for (int i = 0; i < rows.Count; i++)
                AddMenuRow(_eggPanel.transform, rows[i], i, 74f, 20f);
        }

        // ── Impostazioni e strumenti: tutte le vecchie voci restano
        //    funzionanti, raggruppate fuori dal primo livello del menu. ──

        private GameObject _settingsPanel;

        private void OpenSettingsPanel()
        {
            SetVisible(false);
            if (_settingsPanel == null) BuildSettingsPanel();
            RefreshAllDynamicLabels();
            _settingsPanel.SetActive(true);
        }

        private void BuildSettingsPanel()
        {
            _settingsPanel = MakeSubPanel("SettingsPanel", 700f, 1110f);
            AddHeader(_settingsPanel, "IMPOSTAZIONI E STRUMENTI");
            var rows = new List<MenuEntry>
            {
                new MenuEntry("Personalizza aspetto", 2, () =>
                {
                    CloseAll();
                    OpenSkinPanel();
                }),
                new MenuEntry("Note legali", 5, () =>
                {
                    CloseAll();
                    if (UIManager.Instance != null) UIManager.Instance.ShowLegal();
                }),
                new MenuEntry("Qualità grafica: Media", 6, () => ToggleQuality()),
                new MenuEntry("Prestazioni adattive: Attive", 21, () => ToggleAdaptivePerformance()),
                new MenuEntry("Input touch: Huntix", 8, () => ToggleTouch()),
                new MenuEntry("Mondo: Reale", 9, () => ToggleWorldMode()),
                new MenuEntry("Animazione menu: Huntix", 10, () => ToggleMenuAnimation()),
                new MenuEntry("Console QA: Off", 11, () => ToggleQaConsole()),
                new MenuEntry("Opzioni audit open source: Off", 12, () => ToggleOpenSourceAll()),
                new MenuEntry("Compagno NPC: Off", 13, () => ToggleNpcCompanion()),
                new MenuEntry("Modalità cinematografica: Off", 17, () => ToggleCinematicAll()),
                new MenuEntry("Dettagli grafica cinematografica", 18, () =>
                {
                    CloseAll();
                    OpenCinematicPanel();
                }),
                new MenuEntry("💾 Gestisci spazio", -1, () => OpenStorageSettings()),
            };
            for (int i = 0; i < rows.Count; i++)
                AddMenuRow(_settingsPanel.transform, rows[i], i, 74f, 20f);

            var note = NewLabel(_settingsPanel.transform);
            note.text = "La pulizia rimuove solo le tile di MiCittà: " +
                "profilo, uova e preferenze restano intatte.";
            note.fontSize = 19;
            note.color = new Color(0.80f, 0.84f, 0.90f);
            note.alignment = TextAnchor.MiddleLeft;
            note.rectTransform.anchorMin = new Vector2(0f, 1f);
            note.rectTransform.anchorMax = new Vector2(1f, 1f);
            note.rectTransform.pivot = new Vector2(0f, 1f);
            note.rectTransform.anchoredPosition = new Vector2(20f, -1040f);
            note.rectTransform.sizeDelta = new Vector2(-40f, 46f);
        }

        /// <summary>Apre la gestione spazio Android: la pulizia e' selettiva
        /// e confermata dall'utente. Unity svuota anche il prefetch in memoria
        /// quando Android segnala che i file sono stati rimossi.</summary>
        private void OpenStorageSettings()
        {
            CloseAll();
            try
            {
                Huntix.Bridge.UnityBridge.SendMessageToAndroid(
                    "StorageSettingsRequest", "{}");
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[HamburgerMenu] StorageSettingsRequest: " + e.Message);
            }
        }

        private sealed class MenuEntry
        {
            public readonly string Label;
            public readonly int LegacyIndex;
            public readonly System.Action Action;

            public MenuEntry(string label, int legacyIndex, System.Action action)
            {
                Label = label;
                LegacyIndex = legacyIndex;
                Action = action;
            }
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
            if (!open) return;
            SetVisible(true);
            // Animazione affiancata: resta inerte se l'utente non l'ha attivata.
            if (MenuEntranceAnimator.Enabled && panel != null)
            {
                var anim = panel.GetComponent<MenuEntranceAnimator>();
                if (anim == null) anim = panel.AddComponent<MenuEntranceAnimator>();
                anim.Play(panel.GetComponent<RectTransform>());
            }
        }

        private void SetVisible(bool v)
        {
            if (panel == null) return;
            if (v) RefreshAllDynamicLabels();
            panel.SetActive(v);
        }

        private void RefreshAllDynamicLabels()
        {
            RefreshStyleLabel();
            RefreshShopLabel();
            RefreshVenueLabel();
            RefreshQualityLabel();
            RefreshAdaptiveLabel();
            RefreshTouchLabel();
            RefreshWorldLabel();
            RefreshMenuAnimLabel();
            RefreshQaConsoleLabel();
            RefreshOpenSourceLabel();
            RefreshCinematicLabel();
            RefreshNpcCompanionLabel();
        }

        // ---- profilo grafico (Low / Medium / High) ----

        private void RefreshQualityLabel()
        {
            if (_qualityLabel == null) return;
            var sel = City.Diagnostics.QualityProfileSelector.Instance;
            string name = sel == null ? "Media" : sel.Current.ToString();
            _qualityLabel.text = "Qualità grafica: " +
                (name == "Low" ? "Bassa" : name == "High" ? "Alta" : "Media");
        }

        private void ToggleQuality()
        {
            var sel = City.Diagnostics.QualityProfileSelector.Instance;
            if (sel == null)
            {
                if (UIManager.Instance != null)
                    UIManager.Instance.ShowToast("Selettore qualità non disponibile");
                return;
            }
            sel.Cycle();
            RefreshQualityLabel();
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast("Qualità: " + sel.Current);
        }

        /// <summary>Riga unica del profilo adattivo: nessun sistema vecchio
        /// viene disattivato, cambia solo il budget/LOD in base a RAM e FPS.</summary>
        private void RefreshAdaptiveLabel()
        {
            if (_perfLabel == null) return;
            var p = City.Diagnostics.AdaptivePerformanceProfile.Instance;
            string state = p == null || !p.AutoEnabled ? "Disattive" : "Attive";
            string tier = p == null ? "Alte" : p.Current.ToString();
            _perfLabel.text = "Prestazioni adattive: " + state + " (" + tier + ")";
        }

        private void ToggleAdaptivePerformance()
        {
            var p = City.Diagnostics.AdaptivePerformanceProfile.Ensure();
            p.ApplyUserSetting(!p.AutoEnabled);
            RefreshAdaptiveLabel();
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast(p.AutoEnabled
                    ? "Prestazioni adattive attivate"
                    : "Prestazioni adattive disattivate");
        }

        // ---- input touch alternativo (Zone Zero, MIT) ----

        private void RefreshTouchLabel()
        {
            if (_touchLabel == null) return;
            _touchLabel.text = "Input touch: " +
                (AlternateTouchInput.Enabled ? "Zone Zero" : "Huntix");
        }

        private void ToggleTouch()
        {
            if (UIManager.Instance == null) return;
            UIManager.Instance.SetAlternateTouch(!AlternateTouchInput.Enabled);
            RefreshTouchLabel();
        }

        // ---- mondo: Reale (OSM) / Città Gioco (ricetta) ----

        private void RefreshWorldLabel()
        {
            if (_worldLabel == null) return;
            _worldLabel.text = "Mondo: " +
                (City.Fictional.FictionalCityWorld.Enabled
                    ? "Città Gioco (ricetta)" : "Reale (OSM)");
        }

        private void ToggleWorldMode()
        {
            if (UIManager.Instance == null) return;
            City.Fictional.FictionalCityWorld.SetMode(
                !City.Fictional.FictionalCityWorld.Enabled, true);
            RefreshWorldLabel();
        }

        // ---- animazione ingresso menu (Zone Zero, MIT) ----

        private void RefreshMenuAnimLabel()
        {
            if (_menuAnimLabel == null) return;
            _menuAnimLabel.text = "Animazione menu: " +
                (MenuEntranceAnimator.Enabled ? "Zone Zero" : "Huntix");
        }

        private void ToggleMenuAnimation()
        {
            MenuEntranceAnimator.SetEnabled(!MenuEntranceAnimator.Enabled, true);
            RefreshMenuAnimLabel();
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast(MenuEntranceAnimator.Enabled
                    ? "Animazione menu: Zone Zero" : "Animazione menu: Huntix");
        }

        // ---- console QA (SanAndreasUnity, MIT) ----

        private void RefreshQaConsoleLabel()
        {
            if (_qaConsoleLabel == null) return;
            _qaConsoleLabel.text = "Console QA: " +
                (City.Diagnostics.HuntixDebugConsole.Enabled ? "On" : "Off");
        }

        private void ToggleQaConsole()
        {
            City.Diagnostics.HuntixDebugConsole.SetEnabled(
                !City.Diagnostics.HuntixDebugConsole.Enabled, true);
            RefreshQaConsoleLabel();
        }

        // ---- interruttore unico delle migliorie open source ----

        private void RefreshOpenSourceLabel()
        {
            if (_openSourceLabel == null) return;
            _openSourceLabel.text = "Opzioni audit open source: " +
                (City.Diagnostics.OpenSourceFeatureBridge.AllEnabled ? "On" : "Off");
        }

        private void ToggleOpenSourceAll()
        {
            bool on = !City.Diagnostics.OpenSourceFeatureBridge.AllEnabled;
            City.Diagnostics.OpenSourceFeatureBridge.SetAll(on);
            RefreshOpenSourceLabel();
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast(on
                    ? "Opzioni audit open source attivate"
                    : "Opzioni audit open source disattivate");
        }

        // ---- modalita' grafica cinematografica (opt-in) ----

        private void RefreshCinematicLabel()
        {
            if (_cinematicLabel == null) return;
            _cinematicLabel.text = "Modalità cinematografica: " +
                (City.Diagnostics.CinematicFeatureBridge.AllEnabled ? "On" : "Off");
        }

        private void ToggleCinematicAll()
        {
            bool on = !City.Diagnostics.CinematicFeatureBridge.AllEnabled;
            City.Diagnostics.CinematicFeatureBridge.SetAll(on);
            RefreshCinematicLabel();
            RefreshCinematicDetailLabels();
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast(on
                    ? "Modalità cinematografica attivata"
                    : "Modalità cinematografica disattivata");
        }

        private void OpenCinematicPanel()
        {
            SetVisible(false);
            if (cinematicPanel == null) BuildCinematicPanel();
            RefreshCinematicDetailLabels();
            cinematicPanel.SetActive(true);
        }

        private void BuildCinematicPanel()
        {
            cinematicPanel = MakeSubPanel("CinematicPanel", 470f, 720f);
            AddHeader(cinematicPanel, "DETTAGLI GRAFICA CINEMATOGRAFICA");
            string[] ids = City.Diagnostics.CinematicFeatureBridge.AllIds;
            _cinematicDetailLabels = new Text[ids.Length];
            for (int i = 0; i < ids.Length; i++)
            {
                int idx = i;
                string id = ids[i];
                var row = new GameObject("G_" + i, typeof(Image), typeof(Button));
                row.transform.SetParent(cinematicPanel.transform, false);
                var rt = row.GetComponent<RectTransform>();
                rt.anchorMin = new Vector2(0f, 1f);
                rt.anchorMax = new Vector2(1f, 1f);
                rt.pivot = new Vector2(0f, 1f);
                rt.anchoredPosition = new Vector2(20f, -190f - i * 68f);
                rt.sizeDelta = new Vector2(-40f, 62f);
                row.GetComponent<Image>().color = RowBg;
                row.GetComponent<Button>().onClick.AddListener(() => ToggleCinematicDetail(idx));
                var lbl = NewLabel(row.transform);
                lbl.alignment = TextAnchor.MiddleLeft;
                lbl.rectTransform.anchorMin = Vector2.zero;
                lbl.rectTransform.anchorMax = Vector2.one;
                lbl.rectTransform.sizeDelta = Vector2.zero;
                lbl.rectTransform.offsetMin = new Vector2(14f, 0f);
                lbl.rectTransform.offsetMax = new Vector2(-14f, 0f);
                _cinematicDetailLabels[i] = lbl;
            }
        }

        private void RefreshCinematicDetailLabels()
        {
            if (_cinematicDetailLabels == null) return;
            string[] ids = City.Diagnostics.CinematicFeatureBridge.AllIds;
            for (int i = 0; i < ids.Length && i < _cinematicDetailLabels.Length; i++)
                _cinematicDetailLabels[i].text = City.Diagnostics.CinematicFeatureBridge.LabelFor(ids[i]) +
                    ": " + (City.Diagnostics.CinematicFeatureBridge.Enabled(ids[i]) ? "On" : "Off");
        }

        private void ToggleCinematicDetail(int idx)
        {
            string[] ids = City.Diagnostics.CinematicFeatureBridge.AllIds;
            if (idx < 0 || idx >= ids.Length) return;
            string id = ids[idx];
            bool on = !City.Diagnostics.CinematicFeatureBridge.Enabled(id);
            City.Diagnostics.CinematicFeatureBridge.SetToggle(id, on,
                City.Diagnostics.CinematicFeatureBridge.LabelFor(id),
                City.Diagnostics.CinematicFeatureBridge.AllPlace);
            RefreshCinematicLabel();
            RefreshCinematicDetailLabels();
        }

        // ---- compagno NPC (pattern MIT SanAndreasUnity, riscritto) ----

        private void RefreshNpcCompanionLabel()
        {
            if (_companionLabel == null) return;
            _companionLabel.text = "Compagno NPC: " +
                (City.NPC.NpcCompanion.Enabled ? "On" : "Off");
        }

        private void ToggleNpcCompanion()
        {
            City.NPC.NpcCompanion.SetEnabled(!City.NPC.NpcCompanion.Enabled, true);
            RefreshNpcCompanionLabel();
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast(City.NPC.NpcCompanion.Enabled
                    ? "Compagno NPC: avvicinati a un pedone e scegli 'PORTA CON TE'"
                    : "Compagno NPC disattivato");
        }

        // ---- stile edifici (fluidita' / qualita') ----

        private void RefreshStyleLabel()
        {
            if (_styleLabel == null) return;
            _styleLabel.text = "Stile edifici: " +
                (CityStyle.UseQuaternius
                    ? "Qualita' (Quaternius)"
                    : "Veloce (Kenney)" +
                      (CityStyle.CanUseQuaternius ? "" : " — Quaternius inibito"));
        }

        /// <summary>Un click commuta Kenney (fluido, default) <-> Quaternius
        /// (facciate texturizzate) e rigenera subito i chunk: la scelta viene
        /// letta da BuildingPlacer.PlaceLegacy al prossimo build.</summary>
        private void ToggleBuildingStyle()
        {
            // Quaternius riabilitato su richiesta del proprietario (24/11):
            // il toggle torna a funzionare. Il guard resta come rete di
            // sicurezza in caso di disattivazione futura.
            if (!CityStyle.CanUseQuaternius)
            {
                if (UIManager.Instance != null)
                    UIManager.Instance.ShowToast(
                        "Edifici Kenney: Quaternius inibito per la fluidita'");
                SetVisible(false);
                return;
            }

            // REVIEW: UnloadAll distrugge i collider e i riferimenti agli edifici.
            // Se siamo dentro un edificio o in auto, currentEntrance/CurrentVehicle
            // resterebbero appesi a oggetti distrutti: rifiuta il cambio con un
            // messaggio invece di lasciare uno stato inconsistente.
            var im = City.Interior.InteriorManager.Instance;
            if (im != null && im.IsInside)
            {
                if (UIManager.Instance != null)
                    UIManager.Instance.ShowToast("Esci dall'edificio prima di cambiare stile");
                SetVisible(false);
                return;
            }
            var game = Game.Instance;
            if (game != null && game.IsDriving)
            {
                if (UIManager.Instance != null)
                    UIManager.Instance.ShowToast("Scendi dall'auto prima di cambiare stile");
                SetVisible(false);
                return;
            }

            bool next = !CityStyle.UseQuaternius;
            CityStyle.Set(next);
            RefreshStyleLabel();
            Vibration.Vibrate(25);
            // Traccia il cambio stile anche sul lato Android (Mandato: ponte
            // bidirezionale) oltre che in PlayerPrefs.
            try { Huntix.Bridge.UnityBridge.LogToAndroid("CityStyle", next
                ? "Quaternius (qualita')" : "Kenney (veloce)"); }
            catch (System.Exception) { }
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast(next
                    ? "Stile edifici: Qualita' (Quaternius). Mappa in ricarica..."
                    : "Stile edifici: Veloce (Kenney). Mappa in ricarica...");

            // ReloadWorld() mette il SpawnBridge sotto il player PRIMA di
            // svuotare i chunk: senza ponte il player cadrebbe nel vuoto
            // durante il rebuild (stesso motivo di Teleport()).
            var cw = City.OSM.CityChunkedWorld.Instance;
            if (cw != null) cw.ReloadWorld();
            SetVisible(false);
        }

        // ---- switch A/B del negozio (Huntix / prefabbricato CC0) ----

        private void RefreshShopLabel()
        {
            if (_shopLabel == null) return;
            _shopLabel.text = "Negozio: " + City.Interior.ShopStyle.Label;
        }

        /// <summary>Commuta lo strato arredi del negozio senza eliminare nessun
        /// sistema: il prefab CC0 e il builder universale restano entrambi nel
        /// progetto. Cambia solo quale viene usato al prossimo rebuild dei chunk.
        /// Come per lo stile edifici, rifiuta il cambio dentro casa o in auto e
        /// ricarica il mondo con il ponte sotto il player.</summary>
        private void ToggleShopStyle()
        {
            var im = City.Interior.InteriorManager.Instance;
            if (im != null && im.IsInside)
            {
                if (UIManager.Instance != null)
                    UIManager.Instance.ShowToast("Esci dall'edificio prima di cambiare negozio");
                SetVisible(false);
                return;
            }
            var game = Game.Instance;
            if (game != null && game.IsDriving)
            {
                if (UIManager.Instance != null)
                    UIManager.Instance.ShowToast("Scendi dall'auto prima di cambiare negozio");
                SetVisible(false);
                return;
            }

            bool next = !City.Interior.ShopStyle.UsePrefab;
            City.Interior.ShopStyle.Set(next);
            RefreshShopLabel();
            Vibration.Vibrate(25);
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast(next
                    ? "Negozio prefabbricato CC0. Mappa in ricarica..."
                    : "Negozio Huntix (procedurale). Mappa in ricarica...");

            var cw = City.OSM.CityChunkedWorld.Instance;
            if (cw != null) cw.ReloadWorld();
            SetVisible(false);
        }

        // ---- eventi sportivi/ambientali (nuovo sistema additivo) ----

        private void RefreshVenueLabel()
        {
            if (_venueLabel == null) return;
            _venueLabel.text = "Eventi sportivi: " +
                (City.Events.VenueDirector.EventsEnabled ? "Attivi" : "Off");
        }

        /// <summary>Attiva/disattiva solo il nostro direttore: nessun altro
        /// sistema di eventi o di economia viene toccato. Gli impianti sono
        /// costruiti lazy, quindi il costo resta zero finche' sono disattivi.</summary>
        private void ToggleVenueEvents()
        {
            bool next = !City.Events.VenueDirector.EventsEnabled;
            City.Events.VenueDirector.EventsEnabled = next;
            RefreshVenueLabel();
            Vibration.Vibrate(25);
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast(next
                    ? "Eventi sportivi attivi: cerca uno stadio o un ippodromo"
                    : "Eventi sportivi disattivati");
            SetVisible(false);
        }

        // ---- sotto-pannelli (personalizzazione / profilo Sim) ----

        private void CloseAll()
        {
            if (panel != null) panel.SetActive(false);
            // Chiude ogni sotto-pannello, anche quelli creati in futuro senza
            // un campo dedicato: dopo "Chiudi menu" non resta nulla aperto.
            for (int i = 0; i < _subPanels.Count; i++)
                if (_subPanels[i] != null) _subPanels[i].SetActive(false);
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
            if (!_subPanels.Contains(sp)) _subPanels.Add(sp);

            var close = new GameObject("Close", typeof(Image), typeof(Button));
            close.transform.SetParent(sp.transform, false);
            var crt = close.GetComponent<RectTransform>();
            crt.anchorMin = new Vector2(1f, 1f);
            crt.anchorMax = new Vector2(1f, 1f);
            crt.pivot = new Vector2(1f, 1f);
            crt.anchoredPosition = new Vector2(-16f, -16f);
            crt.sizeDelta = new Vector2(160f, 72f);
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