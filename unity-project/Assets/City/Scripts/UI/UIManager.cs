using System.Collections;
using System.Collections.Generic;
using TMPro;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.InputSystem.UI;
using UnityEngine.UI;
using City.Player;
using City.World;
using City.OSM;
using Huntix.Bridge;

namespace City.UI
{
    public class UIManager : MonoBehaviour
    {
        public ScreenFader fader;
        public DynamicJoystick joystick;
        public OrbitZone orbit;

        private static readonly Color PanelBg = new Color(0.11f, 0.12f, 0.14f, 0.97f);
        private static readonly Color Accent = new Color(0.20f, 0.75f, 0.55f, 1f);
        private static readonly Color RowBg = new Color(0.20f, 0.22f, 0.25f, 1f);
        private static readonly Color ButtonBg = new Color(0.28f, 0.30f, 0.34f, 1f);

        private TMP_FontAsset font;
        private Canvas canvas;
        private RectTransform root;

        private TMP_Text moneyText;
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

        private void Awake()
        {
            font = TMP_Settings.defaultFontAsset;
            if (font == null) font = Resources.Load<TMP_FontAsset>("Fonts & Materials/LiberationSans SDF");
            BuildCanvas();
        }

        private void Start()
        {
            Wallet.OnChanged += OnMoneyChanged;
            OnMoneyChanged(Wallet.Money);
        }

        private void Update()
        {
            if (joystick == null) return;
            PlayerController player = Game.Instance != null ? Game.Instance.player : null;
            if (player != null) player.SetMoveInput(joystick.Value);
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

        // Stato GPS/centro OSM mostrato in basso, aggiornato solo quando il testo
        // cambia (nessuna allocazione a ogni frame). Risponde alla domanda "dove
        // sono e il gioco sta davvero seguendo il GPS?".
        public void SetGpsStatus(string text)
        {
            if (gpsText == null || text == lastGpsStatus) return;
            lastGpsStatus = text;
            gpsText.text = text;
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
                CameraRig rig = CameraRig.Instance;
                if (rig != null) rig.Orbit(dx);
            };

            // --- money HUD
            moneyText = MakeText(root, "€ 0", 36f, new Color(1f, 1f, 1f, 1f), TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(24f, -28f), new Vector2(200f, -72f));
            MakeText(root, "Tocca e trascina per muoverti · scorri a destra per ruotare la telecamera", 18f, new Color(1f, 1f, 1f, 0.45f), TextAlignmentOptions.Center, new Vector2(0f, 1f), new Vector2(1f, 1f), new Vector2(0f, -96f), new Vector2(0f, -128f));

            // --- stato GPS/OSM (riga piccola semi-trasparente sotto le istruzioni)
            gpsText = MakeText(root, "", 15f, new Color(1f, 1f, 1f, 0.38f), TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(1f, 1f), new Vector2(24f, -136f), new Vector2(-24f, -160f));

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
        }

        private void OnInteractPressed()
        {
            Game.Instance.OnInteractPressed();
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
    }
}
