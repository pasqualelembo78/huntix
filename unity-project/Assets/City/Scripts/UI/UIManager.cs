using System.Collections;
using System.Collections.Generic;
using TMPro;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.InputSystem.UI;
using UnityEngine.UI;
using City.Player;
using City.World;
using City.Vehicle;
using City.Economy;
using City.OSM;
using Huntix.Bridge;

namespace City.UI
{
    public class UIManager : MonoBehaviour
    {
        public static UIManager Instance;

        public ScreenFader fader;
        public DynamicJoystick joystick;
        public OrbitZone orbit;

        private LegalManager _legal;

        private LegalManager legal
        {
            get
            {
                if (_legal == null) _legal = GetComponentInChildren<LegalManager>();
                return _legal;
            }
        }

        private static readonly Color PanelBg = new Color(0.11f, 0.12f, 0.14f, 0.97f);
        private static readonly Color Accent = new Color(0.20f, 0.75f, 0.55f, 1f);
        private static readonly Color RowBg = new Color(0.20f, 0.22f, 0.25f, 1f);
        private static readonly Color ButtonBg = new Color(0.28f, 0.30f, 0.34f, 1f);

        private TMP_FontAsset font;
        private Canvas canvas;
        private RectTransform root;

        private TMP_Text moneyText;
        private TMP_Text eggCountText;
        private TMP_Text missionText;
        private GameObject interactButton;
        private TMP_Text interactLabel;

        private GameObject shopPanel;
        private TMP_Text shopTitle;
        private TMP_Text shopMoney;
        private RectTransform shopListContent;

        private TMP_Text toast;
        private Coroutine toastRoutine;

        private TMP_Text gpsText;
        private string lastGpsStatus;

        private GameObject exitButton;
        private GameObject exitPanel;

        private Shop currentShop;

        // ── Dialogo WoW-style ──────────────────────────────────
        private GameObject dialogPanel;
        private RectTransform dialogPortraitRect;
        private TMP_Text dialogNameText;
        private TMP_Text dialogText;
        private Button dialogContinueBtn;
        private Button dialogEnterBtn;
        private Button dialogCloseBtn;
        private string[] dialogLines;
        private int dialogIndex;
        private System.Action<int> dialogCallback;
        private bool dialogActive;

        private void Awake()
        {
            Instance = this;
            font = TMP_Settings.defaultFontAsset;
            if (font == null) font = Resources.Load<TMP_FontAsset>("Fonts & Materials/LiberationSans SDF");
            BuildCanvas();
        }

        private void Start()
        {
            Wallet.OnChanged += OnMoneyChanged;
            OnMoneyChanged(Wallet.Money);
        }

        private void OnDestroy()
        {
            Wallet.OnChanged -= OnMoneyChanged;
        }

        // ---------------------------------------------------------------- public API

        public void ShowInteract(string label)
        {
            interactButton.SetActive(true);
            interactLabel.text = label;
        }

        public void HideInteract()
        {
            interactButton.SetActive(false);
        }

        public void OpenShop(Shop shop)
        {
            currentShop = shop;
            shopTitle.text = shop.shopName;
            RebuildShopList();
            shopPanel.SetActive(true);
            UpdateMoney(Wallet.Money);
            Time.timeScale = 0f;
        }

        public void CloseShop()
        {
            currentShop = null;
            shopPanel.SetActive(false);
            Time.timeScale = 1f;
        }

        public void ShowToast(string message)
        {
            if (toast == null) return;
            if (toastRoutine != null) StopCoroutine(toastRoutine);
            toastRoutine = StartCoroutine(ToastRoutine(message));
        }

        public void ShowLegal()
        {
            if (legal != null) legal.Show();
        }

        public void HideLegal()
        {
            if (legal != null) legal.Hide();
        }

        // Stato GPS/centro OSM mostrato in basso, aggiornato solo quando il testo
        // cambia (nessuna allocazione a ogni frame). Risponde alla domanda "dove
        // sono e il gioco sta davvero seguendo il GPS?".
        public void SetGpsStatus(string text)
        {
            // La barra in basso mostra SOLO l'attribuzione OSM permanente.
            // Il testo GPS aggiornato (posizione, centro) viene loggato ma non mostrato
            // all'utente per non confonderlo con la nota legale fissa.
            // Se si vuole debug, decommentare la riga sotto:
            // if (gpsText == null || text == lastGpsStatus) return;
            // lastGpsStatus = text;
            // gpsText.text = text;
        }

        // ── Veicoli ───────────────────────────────────────────────

        private GameObject drivingPanel;
        private TMP_Text speedText;

        public void ShowVehicleShop(VehicleInteract vi)
        {
            if (VehicleShopUI.Instance != null)
                VehicleShopUI.Instance.ShowPurchaseDialog(vi);
        }

        public void ShowDrivingUI(bool show)
        {
            if (drivingPanel == null) BuildDrivingUI();

            if (show)
            {
                drivingPanel.SetActive(true);
                if (joystick != null) joystick.gameObject.SetActive(false);
            }
            else
            {
                drivingPanel.SetActive(false);
                if (joystick != null) joystick.gameObject.SetActive(true);
            }
        }

        private void BuildDrivingUI()
        {
            drivingPanel = new GameObject("DrivingPanel");
            var prt = drivingPanel.AddComponent<RectTransform>();
            prt.SetParent(root, false);
            prt.anchorMin = Vector2.zero;
            prt.anchorMax = Vector2.one;
            prt.offsetMin = Vector2.zero;
            prt.offsetMax = Vector2.zero;

            // Pulsante ESCI in alto a destra
            var exitRt = MakeRect("ExitVehicle", prt, new Vector2(1f, 1f), new Vector2(1f, 1f),
                new Vector2(-160f, -24f), new Vector2(-24f, -72f));
            Image eb = exitRt.gameObject.AddComponent<Image>();
            eb.color = new Color(0.8f, 0.2f, 0.2f, 0.85f);
            eb.raycastTarget = true;
            Button exitBtn = exitRt.gameObject.AddComponent<Button>();
            exitBtn.targetGraphic = eb;
            exitBtn.onClick.AddListener(() =>
            {
                if (Game.Instance != null) Game.Instance.ExitVehicle();
            });
            MakeText(exitRt, "ESCI", 22f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            // Indicatore velocita' in basso al centro
            speedText = MakeText(prt, "0 km/h", 28f, new Color(1f, 1f, 1f, 0.8f),
                TextAlignmentOptions.Center,
                new Vector2(0.5f, 0f), new Vector2(0.5f, 0f),
                new Vector2(-100f, 90f), new Vector2(100f, 125f));

            drivingPanel.SetActive(false);
        }

        private float uiRefreshTimer;

        private void Update()
        {
            if (joystick == null) return;
            PlayerController player = Game.Instance != null ? Game.Instance.player : null;

            // Blocca input quando il pannello legale e' aperto
            bool legalOpen = legal != null && legal.IsVisible;

            if (player != null && !Game.Instance.IsDriving && !Game.Instance.IsInInterior && !legalOpen)
                player.SetMoveInput(joystick.Value);

            // Aggiorna velocita' se in guida
            if (Game.Instance != null && Game.Instance.IsDriving && speedText != null && Game.Instance.CurrentVehicle != null)
            {
                speedText.text = Mathf.RoundToInt(Game.Instance.CurrentVehicle.GetCurrentSpeedKmh()) + " km/h";
            }

            // Aggiorna UI economia ogni 0.5s
            uiRefreshTimer += Time.unscaledDeltaTime;
            if (uiRefreshTimer > 0.5f)
            {
                uiRefreshTimer = 0f;
                RefreshEconomyUI();
            }
        }

        private void RefreshEconomyUI()
        {
            if (MissionManager.Instance != null)
            {
                var active = MissionManager.Instance.GetActiveMissions();
                if (active.Count > 0)
                {
                    var m = active[0];
                    string progress = "";
                    if (m.type == NPCMission.MissionType.CollectEggs)
                        progress = m.currentCount + "/" + m.targetCount;
                    else
                        progress = m.currentCount + "m/" + m.targetCount + "m";
                    UpdateMissionText(m.description + " " + progress);
                }
                else
                {
                    UpdateMissionText("");
                }
            }
        }

        private IEnumerator ToastRoutine(string message)
        {
            toast.text = message;
            Color c = toast.color;
            c.a = 1f;
            toast.color = c;
            yield return new WaitForSecondsRealtime(1.6f);
            float t = 0f;
            while (t < 0.4f)
            {
                t += Time.deltaTime;
                c.a = Mathf.Lerp(1f, 0f, t / 0.4f);
                toast.color = c;
                yield return null;
            }
        }

        // ---------------------------------------------------------------- internals

        private void OnMoneyChanged(int money)
        {
            UpdateMoney(money);
        }

        private void UpdateMoney(int money)
        {
            if (moneyText != null) moneyText.text = "€ " + money;
            if (shopMoney != null) shopMoney.text = "Soldi: € " + money;
        }

        private void RebuildShopList()
        {
            foreach (Transform child in shopListContent) Destroy(child.gameObject);

            if (currentShop == null) return;
            for (int i = 0; i < currentShop.items.Count; i++)
            {
                ShopItem item = currentShop.items[i];
                RectTransform row = MakeRect("Item" + i, shopListContent, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
                row.sizeDelta = new Vector2(0f, 64f);
                row.anchorMin = new Vector2(0f, 1f);
                row.anchorMax = new Vector2(1f, 1f);
                row.pivot = new Vector2(0.5f, 1f);

                Image rowImg = MakeImage(row, RowBg, new Vector2(0f, 0f), new Vector2(1f, 1f), new Vector2(0f, 0f), new Vector2(0f, -2f));

                TMP_Text nameText = MakeText(row, item.name, 30f, Color.white, TextAlignmentOptions.Left, new Vector2(0f, 0f), new Vector2(0.62f, 1f), new Vector2(16f, 0f), new Vector2(0f, 0f));
                TMP_Text priceText = MakeText(row, item.price + " €", 30f, Accent, TextAlignmentOptions.Right, new Vector2(0.62f, 0f), new Vector2(0.8f, 1f), new Vector2(0f, 0f), new Vector2(0f, 0f));

                int owned = Inventory.Count(item.name);
                TMP_Text ownedText = MakeText(row, "x" + owned, 22f, new Color(0.8f, 0.8f, 0.8f, 1f), TextAlignmentOptions.Center, new Vector2(0.8f, 0f), new Vector2(0.9f, 1f), new Vector2(0f, 0f), new Vector2(0f, 0f));

                ShopItem captured = item;
                Button buy = MakeButton(row, "Compra", () => Buy(captured), new Vector2(0.9f, 0f), new Vector2(1f, 1f), new Vector2(4f, 10f), new Vector2(4f, 10f));
            }
        }

        private void Buy(ShopItem item)
        {
            if (!Wallet.CanAfford(item.price))
            {
                ShowToast("Soldi insufficienti!");
                return;
            }
            Wallet.Spend(item.price);
            Inventory.Add(item.name);
            ShowToast("Acquistato: " + item.name);
            RebuildShopList();
            UpdateMoney(Wallet.Money);
        }

        // ---------------------------------------------------------------- builders

        private void BuildCanvas()
        {
            if (EventSystem.current == null)
            {
                GameObject esGo = new GameObject("EventSystem");
                EventSystem es = esGo.AddComponent<EventSystem>();
                InputSystemUIInputModule module = esGo.AddComponent<InputSystemUIInputModule>();
                module.AssignDefaultActions();
            }

            GameObject canvasGo = new GameObject("UI");
            canvas = canvasGo.AddComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            canvas.sortingOrder = 10;
            canvasGo.AddComponent<GraphicRaycaster>();

            CanvasScaler scaler = canvasGo.AddComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1280f, 720f);
            scaler.screenMatchMode = CanvasScaler.ScreenMatchMode.MatchWidthOrHeight;
            scaler.matchWidthOrHeight = 0.5f;

            root = canvasGo.GetComponent<RectTransform>();
            canvasGo.transform.SetParent(transform, false);

            // --- joystick zone (left 55%)
            Image moveZone = MakeImage(root, new Color(0f, 0f, 0f, 0.001f), new Vector2(0f, 0f), new Vector2(0.55f, 1f), Vector2.zero, Vector2.zero);
            moveZone.raycastTarget = true;
            joystick = moveZone.gameObject.AddComponent<DynamicJoystick>();

            RectTransform baseRt = MakeCircle(root, "JoystickBase", 150f, new Color(1f, 1f, 1f, 0.20f));
            RectTransform handleRt = MakeCircle(baseRt, "JoystickHandle", 70f, new Color(1f, 1f, 1f, 0.65f));
            baseRt.gameObject.SetActive(false);
            joystick.Configure(root, baseRt, handleRt);

            // --- orbit zone (right 45%)
            Image orbitImg = MakeImage(root, new Color(0f, 0f, 0f, 0.001f), new Vector2(0.55f, 0f), new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            orbitImg.raycastTarget = true;
            orbit = orbitImg.gameObject.AddComponent<OrbitZone>();
            orbit.OnDragDelta += dx =>
            {
                if (Game.Instance != null) Game.Instance.OnOrbitDelta(dx);
            };

            // --- money HUD
            moneyText = MakeText(root, "€ 0", 36f, new Color(1f, 1f, 1f, 1f), TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(24f, -28f), new Vector2(200f, -72f));

            // --- egg counter (under money)
            eggCountText = MakeText(root, "", 26f, new Color(1f, 0.95f, 0.5f, 1f), TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(24f, -74f), new Vector2(200f, -100f));
            eggCountText.gameObject.SetActive(false);

            // --- active mission (under egg counter)
            missionText = MakeText(root, "", 22f, new Color(0.4f, 0.9f, 0.4f, 0.9f), TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(0.6f, 1f), new Vector2(24f, -102f), new Vector2(24f, -128f));
            missionText.gameObject.SetActive(false);

            // --- rewarded ad button (top-right, under ESCI)
            var rewardRt = MakeRect("RewardButton", root, new Vector2(1f, 1f), new Vector2(1f, 1f), new Vector2(-160f, -80f), new Vector2(-24f, -118f));
            Image rwBg = rewardRt.gameObject.AddComponent<Image>();
            rwBg.color = new Color(0.9f, 0.7f, 0.1f, 0.85f);
            rwBg.raycastTarget = true;
            Button rwBtn = rewardRt.gameObject.AddComponent<Button>();
            rwBtn.targetGraphic = rwBg;
            rwBtn.onClick.AddListener(OnRewardedAdPressed);
            MakeText(rewardRt, "GUARDA VIDEO +€25", 18f, Color.white, TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            MakeText(root, "Tocca e trascina per muoverti · scorri a destra per ruotare la telecamera", 18f, new Color(1f, 1f, 1f, 0.45f), TextAlignmentOptions.Center, new Vector2(0f, 1f), new Vector2(1f, 1f), new Vector2(0f, -96f), new Vector2(0f, -128f));

            // --- stato GPS/OSM (riga piccola semi-trasparente con attribuzione permanente)
            gpsText = MakeText(root, "© OpenStreetMap contributors · ODbL", 15f, new Color(1f, 1f, 1f, 0.38f), TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(1f, 1f), new Vector2(24f, -136f), new Vector2(-24f, -160f));

            // --- pulsante Note Legali (piccolo, sotto l'attribuzione OSM)
            var legalRt = MakeRect("LegalButton", root, new Vector2(0f, 1f), new Vector2(0f, 1f),
                new Vector2(24f, -166f), new Vector2(160f, -186f));
            Image legalBg = legalRt.gameObject.AddComponent<Image>();
            legalBg.color = new Color(0.20f, 0.22f, 0.25f, 0.7f);
            legalBg.raycastTarget = true;
            Button legalBtn = legalRt.gameObject.AddComponent<Button>();
            legalBtn.targetGraphic = legalBg;
            legalBtn.onClick.AddListener(ShowLegal);
            TMP_Text legalTxt = MakeText(legalRt, "Note legali", 16f, new Color(0.8f, 0.8f, 0.8f, 0.8f), TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            // --- LegalManager (pannello a schede per i documenti legali)
            var legalMgrGo = new GameObject("LegalManager");
            legalMgrGo.transform.SetParent(transform, false);
            _legal = legalMgrGo.AddComponent<LegalManager>();
            _legal.Init(canvas, root);

            // --- exit button (angolo in alto a destra: torna alla Home)
            exitButton = MakeRect("ExitButton", root, new Vector2(1f, 1f), new Vector2(1f, 1f), new Vector2(-104f, -24f), new Vector2(-24f, -72f)).gameObject;
            Image eb = exitButton.AddComponent<Image>();
            eb.color = new Color(0f, 0f, 0f, 0.45f);
            eb.raycastTarget = true;
            exitButton.AddComponent<Button>().onClick.AddListener(OnExitPressed);
            MakeText(exitButton.GetComponent<RectTransform>(), "ESCI", 22f, new Color(1f, 1f, 1f, 0.9f), TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            // --- pannello conferma uscita (evita tap accidentali sull'ESCI)
            exitPanel = MakeRect("ExitPanel", root, new Vector2(0.5f, 0.5f), new Vector2(0.5f, 0.5f), new Vector2(-300f, -130f), new Vector2(300f, 130f)).gameObject;
            Image epBg = exitPanel.AddComponent<Image>();
            epBg.color = PanelBg;
            epBg.raycastTarget = true;
            MakeText(exitPanel.GetComponent<RectTransform>(), "Tornare alla Home?", 34f, Color.white, TextAlignmentOptions.Center, new Vector2(0f, 1f), new Vector2(1f, 1f), new Vector2(20f, -28f), new Vector2(-20f, -92f));
            MakeText(exitPanel.GetComponent<RectTransform>(), "Il gioco verrà chiuso e tornerai alla schermata principale.", 20f, new Color(1f, 1f, 1f, 0.65f), TextAlignmentOptions.Center, new Vector2(0f, 1f), new Vector2(1f, 1f), new Vector2(24f, -92f), new Vector2(-24f, -126f));
            MakeButton(exitPanel.GetComponent<RectTransform>(), "Sì, esci", ConfirmExit, new Vector2(0f, 0f), new Vector2(0.5f, 0f), new Vector2(24f, 22f), new Vector2(-12f, 70f));
            MakeButton(exitPanel.GetComponent<RectTransform>(), "Annulla", CloseExitPanel, new Vector2(0.5f, 0f), new Vector2(1f, 0f), new Vector2(12f, 22f), new Vector2(-24f, 70f));
            exitPanel.SetActive(false);

            // --- interact button
            interactButton = MakeRect("InteractButton", root, new Vector2(0.5f, 0f), new Vector2(0.5f, 0f), new Vector2(-150f, 90f), new Vector2(150f, 170f)).gameObject;
            Image ib = interactButton.AddComponent<Image>();
            ib.color = Accent;
            ib.raycastTarget = true;
            interactButton.AddComponent<Button>().onClick.AddListener(OnInteractPressed);
            interactLabel = MakeText(interactButton.GetComponent<RectTransform>(), "", 30f, Color.white, TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            interactButton.SetActive(false);

            // --- shop panel
            shopPanel = MakeRect("ShopPanel", root, new Vector2(0.5f, 0.5f), new Vector2(0.5f, 0.5f), new Vector2(-320f, -300f), new Vector2(320f, 300f)).gameObject;
            Image spBg = shopPanel.AddComponent<Image>();
            spBg.color = PanelBg;
            spBg.raycastTarget = true;

            shopTitle = MakeText(shopPanel.GetComponent<RectTransform>(), "", 40f, Color.white, TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(1f, 1f), new Vector2(24f, -18f), new Vector2(-24f, -66f));

            Button closeBtn = MakeButton(shopPanel.GetComponent<RectTransform>(), "X", CloseShop, new Vector2(1f, 1f), new Vector2(1f, 1f), new Vector2(-70f, -18f), new Vector2(-18f, -66f));

            RectTransform listScroll = MakeRect("ShopList", shopPanel.GetComponent<RectTransform>(), new Vector2(0f, 0f), new Vector2(1f, 1f), new Vector2(20f, 80f), new Vector2(-20f, -140f));
            ScrollRect scroll = listScroll.gameObject.AddComponent<ScrollRect>();
            Image scrollBg = listScroll.gameObject.AddComponent<Image>();
            scrollBg.color = new Color(0f, 0f, 0f, 0.25f);
            listScroll.gameObject.AddComponent<Mask>();

            shopListContent = MakeRect("ShopListContent", listScroll, new Vector2(0f, 1f), new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            shopListContent.pivot = new Vector2(0.5f, 1f);
            VerticalLayoutGroup vlg = shopListContent.gameObject.AddComponent<VerticalLayoutGroup>();
            vlg.childControlWidth = true;
            vlg.childControlHeight = false;
            vlg.childForceExpandWidth = true;
            vlg.childForceExpandHeight = false;
            vlg.spacing = 4f;
            vlg.padding = new RectOffset(0, 0, 4, 4);
            shopListContent.gameObject.AddComponent<ContentSizeFitter>().verticalFit = ContentSizeFitter.FitMode.PreferredSize;
            scroll.content = shopListContent;
            scroll.vertical = true;
            scroll.horizontal = false;
            scroll.movementType = ScrollRect.MovementType.Clamped;
            scroll.viewport = listScroll;

            shopMoney = MakeText(shopPanel.GetComponent<RectTransform>(), "Soldi: € 0", 28f, Accent, TextAlignmentOptions.Left, new Vector2(0f, 0f), new Vector2(1f, 0f), new Vector2(24f, 14f), new Vector2(-100f, 48f));
            shopPanel.SetActive(false);

            // --- toast
            toast = MakeText(root, "", 28f, new Color(1f, 1f, 1f, 1f), TextAlignmentOptions.Center, new Vector2(0.5f, 0f), new Vector2(0.5f, 0f), new Vector2(-400f, 200f), new Vector2(400f, 242f));

            // --- fader
            GameObject faderGo = new GameObject("Fader");
            faderGo.transform.SetParent(root, false);
            RectTransform faderRt = faderGo.AddComponent<RectTransform>();
            faderRt.anchorMin = Vector2.zero;
            faderRt.anchorMax = Vector2.one;
            faderRt.offsetMin = Vector2.zero;
            faderRt.offsetMax = Vector2.zero;
            Image faderImg = faderGo.AddComponent<Image>();
            faderImg.color = new Color(0f, 0f, 0f, 0f);
            faderImg.raycastTarget = false;
            fader = faderGo.AddComponent<ScreenFader>();
            fader.image = faderImg;
            faderGo.SetActive(false);

            // --- dialogo WoW-style (barra in basso) ---
            BuildDialog();
        }

        private void OnInteractPressed()
        {
            Game.Instance.OnInteractPressed();
        }

        private void OnRewardedAdPressed()
        {
            if (RewardedAdHelper.Instance != null)
            {
                ShowToast("Caricamento video...");
                RewardedAdHelper.Instance.ShowRewardedAd(success => { });
            }
            else
            {
                ShowToast("Video non disponibile");
            }
        }

        public void UpdateEggCount(int count)
        {
            if (eggCountText != null)
            {
                eggCountText.gameObject.SetActive(count > 0);
                eggCountText.text = "Uova: " + count;
            }
        }

        public void UpdateMissionText(string text)
        {
            if (missionText != null)
            {
                missionText.gameObject.SetActive(!string.IsNullOrEmpty(text));
                missionText.text = text;
            }
        }

        private void OnExitPressed()
        {
            if (exitPanel == null) return;
            exitPanel.SetActive(true);
            Time.timeScale = 0f;
        }

        private void CloseExitPanel()
        {
            if (exitPanel == null) return;
            exitPanel.SetActive(false);
            Time.timeScale = 1f;
        }

        private void ConfirmExit()
        {
            if (exitPanel != null) exitPanel.SetActive(false);
            Time.timeScale = 1f;
            // Ferma subito il polling OSM/JNI prima di chiudere l'Activity Unity:
            // in gara con il teardown dell'engine su Android un accesso al bridge
            // può provocare un crash nativo del processo.
            if (CityOSMWorld.Instance != null) CityOSMWorld.Instance.PrepareExit();
            // Su Android chiude l'Activity Unity (BridgeActivity) e torna alla
            // Home nativa; in editor il metodo è un no-op (si logga soltanto).
            Debug.Log("[UIManager] Exit richiesta: ritorno alla Home");
            UnityBridge.ExitCityToHome();
        }

        // ---------------------------------------------------------------- helpers

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

        private Image MakeImage(RectTransform parent, Color color, Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            RectTransform rt = MakeRect("Image", parent, anchorMin, anchorMax, offsetMin, offsetMax);
            Image img = rt.gameObject.AddComponent<Image>();
            img.color = color;
            img.raycastTarget = false;
            return img;
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

        private Button MakeButton(RectTransform parent, string label, UnityEngine.Events.UnityAction onClick, Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            RectTransform rt = MakeRect("Button", parent, anchorMin, anchorMax, offsetMin, offsetMax);
            Image img = rt.gameObject.AddComponent<Image>();
            img.color = ButtonBg;
            img.raycastTarget = true;
            Button btn = rt.gameObject.AddComponent<Button>();
            btn.targetGraphic = img;
            btn.onClick.AddListener(onClick);

            TMP_Text txt = MakeText(rt, label, 30f, Color.white, TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            txt.raycastTarget = false;
            return btn;
        }

        private RectTransform MakeCircle(RectTransform parent, string name, float size, Color color)
        {
            RectTransform rt = MakeRect(name, parent, new Vector2(0.5f, 0.5f), new Vector2(0.5f, 0.5f), Vector2.zero, Vector2.zero);
            rt.sizeDelta = new Vector2(size, size);
            Image img = rt.gameObject.AddComponent<Image>();
            img.color = color;
            img.raycastTarget = false;
            return rt;
        }

        // ── Dialogo WoW-style ──────────────────────────────────

        private void BuildDialog()
        {
            dialogPanel = MakeRect("DialogPanel", root,
                new Vector2(0f, 0f), new Vector2(1f, 0f),
                new Vector2(0f, 0f), new Vector2(0f, 210f)).gameObject;
            Image dpBg = dialogPanel.AddComponent<Image>();
            dpBg.color = new Color(0.04f, 0.03f, 0.06f, 0.92f);
            dpBg.raycastTarget = true;

            // Bordo dorato superiore
            var borderRt = MakeRect("Border", dialogPanel.GetComponent<RectTransform>(),
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(0f, -4f), new Vector2(0f, 0f));
            Image borderImg = borderRt.gameObject.AddComponent<Image>();
            borderImg.color = new Color(0.75f, 0.60f, 0.15f, 1f);

            // Portrait frame (left side)
            var portraitFrame = MakeRect("PortraitFrame", dialogPanel.GetComponent<RectTransform>(),
                new Vector2(0f, 0f), new Vector2(0f, 1f),
                new Vector2(20f, 10f), new Vector2(130f, -10f));
            Image pfBg = portraitFrame.gameObject.AddComponent<Image>();
            pfBg.color = new Color(0.15f, 0.12f, 0.08f, 1f);

            // Portrait inner
            dialogPortraitRect = MakeRect("Portrait", portraitFrame,
                new Vector2(0f, 0f), new Vector2(1f, 1f),
                new Vector2(6f, 6f), new Vector2(-6f, -6f));
            Image pImg = dialogPortraitRect.gameObject.AddComponent<Image>();
            pImg.color = new Color(0.35f, 0.25f, 0.15f, 1f);

            // NPC name (gold, above text)
            dialogNameText = MakeText(dialogPanel.GetComponent<RectTransform>(), "",
                30f, new Color(1f, 0.84f, 0.0f, 1f), TextAlignmentOptions.Left,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(148f, -16f), new Vector2(-200f, -52f));

            // Dialog text (white, main area)
            dialogText = MakeText(dialogPanel.GetComponent<RectTransform>(), "",
                26f, new Color(0.92f, 0.90f, 0.85f, 1f), TextAlignmentOptions.Left,
                new Vector2(0f, 0f), new Vector2(1f, 1f),
                new Vector2(148f, 52f), new Vector2(-200f, -52f));
            dialogText.enableWordWrapping = true;

            // Continue button (tap to advance)
            dialogContinueBtn = MakeButton(dialogPanel.GetComponent<RectTransform>(),
                "Continua >>", OnDialogContinue,
                new Vector2(1f, 0f), new Vector2(1f, 0f),
                new Vector2(-180f, 14f), new Vector2(-20f, 56f));

            // Enter button
            dialogEnterBtn = MakeButton(dialogPanel.GetComponent<RectTransform>(),
                "Entri", OnDialogEnter,
                new Vector2(1f, 0f), new Vector2(1f, 0f),
                new Vector2(-180f, 14f), new Vector2(-20f, 56f));

            // Close button
            dialogCloseBtn = MakeButton(dialogPanel.GetComponent<RectTransform>(),
                "Chiudi", OnDialogClose,
                new Vector2(1f, 0f), new Vector2(1f, 0f),
                new Vector2(-340f, 14f), new Vector2(-190f, 56f));

            dialogPanel.SetActive(false);
        }

        public void ShowDialog(string npcName, string[] lines, System.Action<int> callback)
        {
            if (dialogPanel == null) return;
            dialogLines = lines;
            dialogIndex = 0;
            dialogCallback = callback;
            dialogActive = true;
            dialogNameText.text = npcName;
            dialogText.text = lines[0];
            dialogEnterBtn.gameObject.SetActive(false);
            dialogCloseBtn.gameObject.SetActive(false);
            dialogContinueBtn.gameObject.SetActive(true);
            dialogPanel.SetActive(true);
            HideInteract();
        }

        public void HideDialog()
        {
            if (dialogPanel == null) return;
            dialogPanel.SetActive(false);
            dialogActive = false;
            dialogLines = null;
            dialogCallback = null;
        }

        private void OnDialogContinue()
        {
            dialogIndex++;
            if (dialogIndex < dialogLines.Length)
            {
                dialogText.text = dialogLines[dialogIndex];
                bool isLast = dialogIndex >= dialogLines.Length - 1;
                dialogContinueBtn.gameObject.SetActive(!isLast);
                dialogEnterBtn.gameObject.SetActive(isLast);
                dialogCloseBtn.gameObject.SetActive(isLast);
            }
        }

        private void OnDialogEnter()
        {
            int choice = 0;
            var cb = dialogCallback;
            HideDialog();
            cb?.Invoke(choice);
        }

        private void OnDialogClose()
        {
            int choice = -1;
            var cb = dialogCallback;
            HideDialog();
            cb?.Invoke(choice);
        }
    }
}
