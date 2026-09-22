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

        /// <summary>Canvas root della HUD di gioco (unico attivo e visibile
        /// sempre). I pannelli modali costruiti a runtime (es. i LAVORI) devono
        /// appendere qui, non a un generico FindObjectOfType che puo' ritrovare
        /// un canvas inattivo o del menu.</summary>
        public Canvas HUD { get { return canvas; } }

        public ScreenFader fader;
        public DynamicJoystick joystick;
        public OrbitZone orbit;

        private LegalManager _legal;
        private RectTransform actionCenterButton;
        private GameObject actionMenuRoot;
        private TMP_Text actionLabel;
        private RectTransform actionMenuContent;

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
        private static readonly Color ActionBg = new Color(0.95f, 0.75f, 0.20f, 0.95f);

        private TMP_FontAsset font;
        private Canvas canvas;
        private RectTransform root;

        private TMP_Text moneyText;
        private TMP_Text camModeText;
        private TMP_Text eggCountText;
        private TMP_Text missionText;
        private TMP_Text playerText;
        private GameObject interactButton;
        private TMP_Text interactLabel;
        private GameObject interiorExitButton;
        private Button interiorExitBt;

        private GameObject shopPanel;
        private TMP_Text shopTitle;
        private TMP_Text shopMoney;
        private RectTransform shopListContent;

        // pulsante GUARDA VIDEO +€25: teniamo riferimenti a Button + Image
        // per disabilitarlo/anelocare quando l'ad non e' pronta e mostrare
        // sempre uno stato visivo onesto al giocatore.
        private Button rewardButton;
        private Image rewardButtonImage;
        private TMP_Text rewardButtonLabel;

        // pulsante flottante LAVORI accanto al joystick: nascosto durante la
        // guida per non sovrapporsi ai controlli del veicolo. Salto/Sprint
        // non hanno piu' un tasto dedicato: vivono nel menu AZIONI.
        private GameObject jobsButtonGo;

        // pulsante flottante GIRO (giroscopio): gira la visuale ruotando il
        // telefono. Stato acceso/spento persistito in PlayerPrefs, il colore
        // riflette lo stato vero della camera.
        private Image gyroButtonImage;
        private TMP_Text gyroButtonLabel;

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

        // auto-chiusura dialog se il player si allontana dall'NPC
        private Vector3 dialogOriginPos;
        private bool dialogHasOrigin;
        private const float DialogAutoCloseDist = 12f;

        // ── Morte: menu scelta modalita' + schermata post-morte ────
        private GameObject deathMenuRoot;
        private GameObject postDeathRoot;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            font = TMP_Settings.defaultFontAsset;
            if (font == null) font = Resources.Load<TMP_FontAsset>("Fonts & Materials/LiberationSans SDF");
            BuildCanvas();
        }

        private void Start()
        {
            Wallet.OnChanged += OnMoneyChanged;
            OnMoneyChanged(Wallet.Money);
            HamburgerMenu.Ensure(this);
            ContextActionController.Ensure();
            City.Death.DeathDirector.Ensure();
            City.NPC.FamilyManager.Ensure();
            City.NPC.FamilyKidHost.Ensure();
            ApplyProfileAgeAndGender();
        }

        // ── Profilo Huntix: età e sesso scelti in registrazione ─────
        // Sesso ed età NON vengono più chiesti dentro Miacittà: la fonte di
        // verità è il profilo Huntix (PlayerProfile). Qui li leggiamo all'avvio
        // e applichiamo l'età alla vita del player; la scelta interna viene
        // forzata come già fatta così il dialogo non compare mai.
        [System.Serializable]
        private class ProfileSnapshot
        {
            public string gender = "";
            public int birthYear = 0;
            public bool isMinor = false;
        }

        private static readonly int ThisYear = System.DateTime.UtcNow.Year;

        private void ApplyProfileAgeAndGender()
        {
            try
            {
                string json = Huntix.Bridge.UnityBridge.GetPlayerProfileJson();
                ProfileSnapshot snap = JsonUtility.FromJson<ProfileSnapshot>(json);
                int age = 0;
                if (snap != null && snap.birthYear > 0)
                    age = System.Math.Max(0, ThisYear - snap.birthYear);

                if (age > 0)
                {
                    City.NPC.FamilyManager.SetInitialAge(age);
                    City.NPC.FamilyManager.MarkAgeChosen();
                    // Sesso dal profilo: usato come base per "Marco"/"Giulia" e
                    // per il modello del personaggio, coerente con la scelta in
                    // registrazione.
                    City.NPC.FamilyManager.SetProfileGender(
                        string.Equals(snap.gender, "female", System.StringComparison.OrdinalIgnoreCase));
                }
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("[UIManager] ApplyProfileAgeAndGender: " + e.Message);
            }
            // Fallback: se non siamo riusciti a leggere l'età dal profilo, ma
            // nemmeno l'utente l'ha ancora scelta, chiediamogliela una volta.
            if (!City.NPC.FamilyManager.HasChosenAge)
                ShowAgeChoice();
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

        // ── Morte: menu scelta modalita' + schermata post-morte ────

        public void ShowDeathMenu()
        {
            if (deathMenuRoot == null) BuildDeathMenu();
            deathMenuRoot.SetActive(true);
        }

        public void CloseDeathMenu()
        {
            if (deathMenuRoot != null) deathMenuRoot.SetActive(false);
        }

        public void ShowPostDeathChoice()
        {
            if (postDeathRoot == null) BuildPostDeathMenu();
            postDeathRoot.SetActive(true);
        }

        public void ClosePostDeathChoice()
        {
            if (postDeathRoot != null) postDeathRoot.SetActive(false);
        }

        private void BuildDeathMenu()
        {
            deathMenuRoot = MakeRect("DeathMenu", root,
                new Vector2(0f, 0f), new Vector2(1f, 1f), Vector2.zero, Vector2.zero).gameObject;
            Image bg = deathMenuRoot.AddComponent<Image>();
            bg.color = new Color(0f, 0f, 0f, 0.6f);
            bg.raycastTarget = true;
            var closeBtn = deathMenuRoot.AddComponent<Button>();
            closeBtn.targetGraphic = bg;
            closeBtn.onClick.AddListener(() => City.Death.DeathDirector.Instance.Cancel());

            RectTransform panel = MakeRect("Panel", deathMenuRoot.GetComponent<RectTransform>(),
                new Vector2(0.5f, 0.5f), new Vector2(0.5f, 0.5f),
                new Vector2(-340f, -330f), new Vector2(340f, 330f));
            Image panelBg = panel.gameObject.AddComponent<Image>();
            panelBg.color = PanelBg;
            panelBg.raycastTarget = true;

            MakeText(panel, "SCEGLI LA MODALITA' DI MORTE", 28f, Color.white,
                TextAlignmentOptions.Center,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(20f, -18f), new Vector2(-20f, -58f));

            string[] labels =
            {
                "MORTO INVESTITO", "MORTO SPARATO", "MORTO PESTATO", "CADUTA DAL PALAZZO"
            };
            City.Death.DeathMode[] modes =
            {
                City.Death.DeathMode.INVESTIMENTO, City.Death.DeathMode.SPARI,
                City.Death.DeathMode.PESTAGGIO, City.Death.DeathMode.CADUTA
            };
            float startY = 40f;
            float rowH = 64f;
            for (int i = 0; i < modes.Length; i++)
            {
                int idx = i;
                RectTransform row = MakeRect("Row" + i, panel,
                    new Vector2(0.5f, 0f), new Vector2(0.5f, 0f),
                    new Vector2(-280f, startY + i * rowH), new Vector2(280f, startY + i * rowH + rowH));
                Image rowImg = row.gameObject.AddComponent<Image>();
                rowImg.color = ButtonBg;
                rowImg.raycastTarget = true;
                var btn = row.gameObject.AddComponent<Button>();
                btn.targetGraphic = rowImg;
                btn.onClick.AddListener(() => City.Death.DeathDirector.Instance.ChooseDeath(modes[idx]));
                MakeText(row, labels[idx], 24f, Color.white, TextAlignmentOptions.Center,
                    Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            }

            RectTransform cancel = MakeRect("Cancel", panel,
                new Vector2(0.5f, 0f), new Vector2(0.5f, 0f),
                new Vector2(-160f, -84f), new Vector2(160f, -22f));
            Image cancelImg = cancel.gameObject.AddComponent<Image>();
            cancelImg.color = new Color(0.55f, 0.2f, 0.2f, 1f);
            cancelImg.raycastTarget = true;
            var cb = cancel.gameObject.AddComponent<Button>();
            cb.targetGraphic = cancelImg;
            cb.onClick.AddListener(() => City.Death.DeathDirector.Instance.Cancel());
            MakeText(cancel, "ANNULLA", 24f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
        }

        private void BuildPostDeathMenu()
        {
            postDeathRoot = MakeRect("PostDeath", root,
                new Vector2(0f, 0f), new Vector2(1f, 1f), Vector2.zero, Vector2.zero).gameObject;
            Image bg = postDeathRoot.AddComponent<Image>();
            bg.color = new Color(0f, 0f, 0f, 0.75f);
            bg.raycastTarget = true;

            RectTransform panel = MakeRect("Panel", postDeathRoot.GetComponent<RectTransform>(),
                new Vector2(0.5f, 0.5f), new Vector2(0.5f, 0.5f),
                new Vector2(-380f, -280f), new Vector2(380f, 280f));
            Image panelBg = panel.gameObject.AddComponent<Image>();
            panelBg.color = PanelBg;
            panelBg.raycastTarget = true;

            MakeText(panel, "SEI MORTO", 34f, new Color(1f, 0.4f, 0.3f, 1f),
                TextAlignmentOptions.Center,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(20f, -16f), new Vector2(-20f, -56f));

            var body = MakeText(panel,
                "Vuoi reincarnarti in qualcun altro oppure iniziare la tua nuova strada verso l'Inferno, il Purgatorio e il Paradiso? (l'Afterlife che abbiamo creato)",
                24f, Color.white, TextAlignmentOptions.Center,
                new Vector2(0f, 0.45f), new Vector2(1f, 1f),
                new Vector2(40f, -70f), new Vector2(-40f, -130f));
            body.enableWordWrapping = true;

            RectTransform rein = MakeRect("Reincarna", panel,
                new Vector2(0.5f, 0f), new Vector2(0.5f, 0f),
                new Vector2(-330f, 28f), new Vector2(-12f, 108f));
            Image reinImg = rein.gameObject.AddComponent<Image>();
            reinImg.color = Accent;
            reinImg.raycastTarget = true;
            var rb = rein.gameObject.AddComponent<Button>();
            rb.targetGraphic = reinImg;
            rb.onClick.AddListener(() => City.Death.DeathDirector.Instance.ReincarnateNow());
            MakeText(rein, "REINCARNA ORA", 24f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            RectTransform after = MakeRect("Afterlife", panel,
                new Vector2(0.5f, 0f), new Vector2(0.5f, 0f),
                new Vector2(12f, 28f), new Vector2(330f, 108f));
            Image afterImg = after.gameObject.AddComponent<Image>();
            afterImg.color = new Color(0.75f, 0.55f, 0.15f, 1f);
            afterImg.raycastTarget = true;
            var ab = after.gameObject.AddComponent<Button>();
            ab.targetGraphic = afterImg;
            ab.onClick.AddListener(() => City.Death.DeathDirector.Instance.GoToAfterlife());
            MakeText(after, "INIZIA AFTERLIFE", 24f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
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

        /// Toast interattivo per la chat P2P: resta un avviso temporaneo ma il
        /// TAP su di esso esegue onTap (es. aprire la chat con il mittente).
        public void ShowChatToast(string message, UnityEngine.Events.UnityAction onTap)
        {
            if (toast == null) { onTap?.Invoke(); return; }
            if (toastRoutine != null) StopCoroutine(toastRoutine);
            toastRoutine = StartCoroutine(ToastRoutine(message, onTap));
        }

        public void ShowLegal()
        {
            if (legal != null) legal.Show();
        }

        public void HideLegal()
        {
            if (legal != null) legal.Hide();
        }

        // ---- overlay sonno (letto) ----

        private GameObject sleepOverlay;
        private TMP_Text sleepOverlayText;

        public void SetSleepOverlay(bool on, string msg)
        {
            if (sleepOverlay == null)
            {
                sleepOverlay = new GameObject("SleepOverlay");
                var rt = sleepOverlay.AddComponent<RectTransform>();
                rt.SetParent(root, false);
                rt.anchorMin = Vector2.zero;
                rt.anchorMax = Vector2.one;
                rt.offsetMin = Vector2.zero;
                rt.offsetMax = Vector2.zero;
                var img = sleepOverlay.AddComponent<Image>();
                img.color = new Color(0f, 0f, 0f, 0.62f);
                img.raycastTarget = false;

                var txtGo = new GameObject("Txt", typeof(RectTransform));
                var trt = txtGo.GetComponent<RectTransform>();
                trt.SetParent(rt, false);
                trt.anchorMin = trt.anchorMax = new Vector2(0.5f, 0.5f);
                trt.sizeDelta = new Vector2(620f, 60f);
                sleepOverlayText = txtGo.AddComponent<TextMeshProUGUI>();
                sleepOverlayText.fontSize = 30f;
                sleepOverlayText.alignment = TextAlignmentOptions.Center;
                sleepOverlayText.color = Color.white;
                sleepOverlayText.raycastTarget = false;
                var font = TMP_Settings.defaultFontAsset;
                if (font == null)
                    font = Resources.Load<TMP_FontAsset>(
                        "Fonts & Materials/LiberationSans SDF");
                sleepOverlayText.font = font;
                sleepOverlayText.text = "";
            }
            sleepOverlay.SetActive(on);
            if (sleepOverlayText != null) sleepOverlayText.text = msg;
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
        private Image speedGaugeImage;
        private TMP_Text distanceText;
        private Image damageBarImage;
        private TMP_Text damageBarText;
        private Image fuelBarImage;
        private TMP_Text fuelBarText;

        public void ShowDrivingUI(bool show)
        {
            if (drivingPanel == null) BuildDrivingUI();

            // Il joystick resta SEMPRE attivo: in guida fa da gas/sterzo,
            // stesso schema della camminata (sinistra=muovi, destra=camera)
            drivingPanel.SetActive(show);
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

            // Pulsanti veicolo in basso a destra: LUCI e CLACSON (Brooke-)
            // le luci si accendono la sera e il clacson richiama l'attenzione.
            var lightBt = MakeRect("VehicleLightsBtn", prt, new Vector2(1f, 0f), new Vector2(1f, 0f), new Vector2(-300f, 150f), new Vector2(-180f, 210f));
            Image lightB = lightBt.gameObject.AddComponent<Image>();
            lightB.color = new Color(1f, 0.85f, 0.3f, 0.9f);
            lightB.raycastTarget = true;
            Button lightBtn = lightBt.gameObject.AddComponent<Button>();
            lightBtn.targetGraphic = lightB;
            lightBtn.onClick.AddListener(() =>
            {
                Vibration.Vibrate(25);
                var v = Game.Instance != null ? Game.Instance.CurrentVehicle : null;
                if (v == null) return;
                v.ToggleHeadlights();
                ShowToast(v.HeadlightsOn ? "Luci accese" : "Luci spente");
            });
            MakeText(lightBt, "LUCI", 20f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            var hornBt = MakeRect("VehicleHornBtn", prt, new Vector2(1f, 0f), new Vector2(1f, 0f), new Vector2(-300f, 90f), new Vector2(-180f, 146f));
            Image hornB = hornBt.gameObject.AddComponent<Image>();
            hornB.color = new Color(0.45f, 0.68f, 1f, 0.9f);
            hornB.raycastTarget = true;
            Button hornBtn = hornBt.gameObject.AddComponent<Button>();
            hornBtn.targetGraphic = hornB;
            hornBtn.onClick.AddListener(() =>
            {
                Vibration.Vibrate(25);
                var v = Game.Instance != null ? Game.Instance.CurrentVehicle : null;
                if (v != null) v.Honk();
            });
            MakeText(hornBt, "CLACSON", 16f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            // Indicatore velocita' in basso al centro
            speedText = MakeText(prt, "0 km/h", 28f, new Color(1f, 1f, 1f, 0.8f),
                TextAlignmentOptions.Center,
                new Vector2(0.5f, 0f), new Vector2(0.5f, 0f),
                new Vector2(-100f, 90f), new Vector2(100f, 125f));

            // Tacchimetro: barra rettangolare sopra la velocita' che si
            // riempie in proporzione alla velocita' massima del mezzo e
            // cambia colore da verde (lento) a rosso (veloce/troppo).
            var gaugeBg = MakeRect("SpeedGaugeBg", prt,
                new Vector2(0.5f, 0f), new Vector2(0.5f, 0f),
                new Vector2(-140f, 140f), new Vector2(140f, 152f));
            var gaugeBgImg = gaugeBg.gameObject.AddComponent<Image>();
            gaugeBgImg.color = new Color(0f, 0f, 0f, 0.55f);
            var gaugeFillRect = MakeRect("SpeedGaugeFill", gaugeBg,
                new Vector2(0f, 0f), new Vector2(1f, 1f),
                Vector2.zero, Vector2.zero);
            speedGaugeImage = gaugeFillRect.gameObject.AddComponent<Image>();
            speedGaugeImage.type = Image.Type.Filled;
            speedGaugeImage.fillMethod = Image.FillMethod.Horizontal;
            speedGaugeImage.fillAmount = 0f;
            speedGaugeImage.color = new Color(0.3f, 0.9f, 0.35f, 0.95f);

            // Distanza percorsa (viaggio attuale / totale del mezzo)
            distanceText = MakeText(prt, "0 m", 18f, new Color(1f, 1f, 1f, 0.7f),
                TextAlignmentOptions.Center,
                new Vector2(0.5f, 0f), new Vector2(0.5f, 0f),
                new Vector2(-150f, 50f), new Vector2(150f, 72f));

            // Barra dell'integrita' (HP) in alto al centro: mostra la vita
            // residua del veicolo e passa da verde a rosso man mano che scende.
            var damageBarBg = MakeRect("DamageBarBg", prt,
                new Vector2(0.5f, 1f), new Vector2(0.5f, 1f),
                new Vector2(-150f, -36f), new Vector2(150f, -20f));
            var bgImg = damageBarBg.gameObject.AddComponent<Image>();
            bgImg.color = new Color(0f, 0f, 0f, 0.55f);
            var fillRect = MakeRect("DamageFill", damageBarBg,
                new Vector2(0f, 0f), new Vector2(1f, 1f),
                Vector2.zero, Vector2.zero);
            damageBarImage = fillRect.gameObject.AddComponent<Image>();
            damageBarImage.type = Image.Type.Filled;
            damageBarImage.fillMethod = Image.FillMethod.Horizontal;
            damageBarImage.fillAmount = 1f;
            damageBarImage.color = new Color(0.2f, 0.85f, 0.35f, 0.95f);
            damageBarText = MakeText(damageBarBg, "HP 100%",
                20f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            // Barra carburante (sotto la barra HP)
            var fuelBarBg = MakeRect("FuelBarBg", prt,
                new Vector2(0.5f, 1f), new Vector2(0.5f, 1f),
                new Vector2(-150f, -58f), new Vector2(150f, -42f));
            var fuelBgImg = fuelBarBg.gameObject.AddComponent<Image>();
            fuelBgImg.color = new Color(0f, 0f, 0f, 0.55f);
            var fuelFillRect = MakeRect("FuelFill", fuelBarBg,
                new Vector2(0f, 0f), new Vector2(1f, 1f),
                Vector2.zero, Vector2.zero);
            fuelBarImage = fuelFillRect.gameObject.AddComponent<Image>();
            fuelBarImage.type = Image.Type.Filled;
            fuelBarImage.fillMethod = Image.FillMethod.Horizontal;
            fuelBarImage.fillAmount = 1f;
            fuelBarImage.color = new Color(0.2f, 0.7f, 1f, 0.95f);
            fuelBarText = MakeText(fuelBarBg, "BENZINA 100%",
                18f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            // Niente piu' pedali: gas/sterzo dal joystick sinistro (come
            // la camminata), camera ruotabile dalla zona destra

            drivingPanel.SetActive(false);
        }

        private float uiRefreshTimer;

        private void Update()
        {
            // ── Back hardware Android / Escape in editor ─────────────────
            // Senza questo hook, il percorso di default di UnityPlayerActivity
            // chiude direttamente l'Activity (super.onBackPressed → finish()) e
            // il teardown dell'engine col mondo montato crasha in SIGSEGV dopo
            // ~10s (signal=11, trace nullo).  Riusiamo lo stesso flusso del
            // bottone "Esci" in-app: pannello di conferma → ConfirmExit che
            // scarica il mondo PRIMA del teardown.
#if UNITY_ANDROID || UNITY_EDITOR
            if (Input.GetKeyDown(KeyCode.Escape))
            {
                if (exitPanel != null && exitPanel.activeSelf)
                    ConfirmExit();
                else if (dialogActive)
                    HideDialog();
                else if (shopPanel != null && shopPanel.activeSelf)
                    CloseShop();
                else if (actionMenuRoot != null && actionMenuRoot.activeSelf)
                    CloseActionRadial();
                else if (_legal != null && _legal.IsVisible)
                    _legal.Hide();
                else if (MapSelectUI.Instance != null && MapSelectUI.Instance.IsOpen)
                    MapSelectUI.Close();
                else
                    OnExitPressed();
            }
#endif

            UpdateActionLabel();

            // Il dialog di un NPC si chiude se il player si allontana.
            if (dialogActive && dialogHasOrigin)
            {
                PlayerController dp = Game.Instance != null ? Game.Instance.player : null;
                if (dp != null)
                {
                    Vector3 dd = dp.transform.position - dialogOriginPos;
                    dd.y = 0f;
                    if (dd.magnitude > DialogAutoCloseDist) HideDialog();
                }
            }

            if (joystick == null) return;
            PlayerController player = Game.Instance != null ? Game.Instance.player : null;

            // ESCI dall'interno: pulsante fisso sempre visibile dentro un edificio.
            bool inInterior = Game.Instance != null && Game.Instance.IsInInterior;
            if (interiorExitButton != null && interiorExitButton.activeSelf != inInterior)
                interiorExitButton.SetActive(inInterior);

            // LAVORI: in primo piano solo a piedi; durante la guida si nasconde
            // per non coprire i controlli del veicolo.
            bool driving = Game.Instance != null && Game.Instance.IsDriving;
            if (jobsButtonGo != null && jobsButtonGo.activeSelf == driving)
                jobsButtonGo.SetActive(!driving);

            // Blocca input quando il pannello legale e' aperto
            bool legalOpen = legal != null && legal.IsVisible;
            // il menu delle azioni (radiale in basso) blocca il movimento:
            // toccando le voci il personaggio non deve scivolare sotto le
            // dita mentre si sceglie l'azione.
            bool menuOpen = actionMenuRoot != null && actionMenuRoot.activeSelf;

            if (player != null && !Game.Instance.IsDriving && !Game.Instance.IsInInterior && !legalOpen && !menuOpen)
                player.SetMoveInput(joystick.Value);

            // Aggiorna velocita' se in guida
            if (Game.Instance != null && Game.Instance.IsDriving && speedText != null && Game.Instance.CurrentVehicle != null)
            {
                speedText.text = Mathf.RoundToInt(Game.Instance.CurrentVehicle.GetCurrentSpeedKmh()) + " km/h";
                if (speedGaugeImage != null)
                {
                    float ratio = Mathf.Clamp01(
                        Game.Instance.CurrentVehicle.GetCurrentSpeedKmh() /
                        Mathf.Max(1f, Game.Instance.CurrentVehicle.MaxSpeedMs * 3.6f));
                    speedGaugeImage.fillAmount = ratio;
                    speedGaugeImage.color = ratio < 0.5f
                        ? new Color(0.3f, 0.9f, 0.35f, 0.95f)
                        : ratio < 0.8f
                        ? new Color(0.95f, 0.7f, 0.15f, 0.95f)
                        : new Color(0.9f, 0.2f, 0.2f, 0.95f);
                }
                if (distanceText != null)
                {
                    float tripKm = Game.Instance.CurrentVehicle.TripMeters / 1000f;
                    float totalKm = Game.Instance.CurrentVehicle.TotalOdometerM / 1000f;
                    string tripStr = tripKm >= 1f ? tripKm.ToString("0.0") + " km"
                                                  : Mathf.RoundToInt(Game.Instance.CurrentVehicle.TripMeters) + " m";
                    distanceText.text = "Viaggio " + tripStr + "  ·  Totale " + totalKm.ToString("0.0") + " km";
                }
                if (damageBarImage != null)
                {
                    float hp = Mathf.Clamp(
                        Game.Instance.CurrentVehicle.Integrity, 0f, 100f);
                    damageBarImage.fillAmount = hp / 100f;
                    damageBarImage.color = hp > 60f
                        ? new Color(0.2f, 0.85f, 0.35f, 0.95f)
                        : hp > 25f
                        ? new Color(0.95f, 0.7f, 0.15f, 0.95f)
                        : new Color(0.9f, 0.2f, 0.2f, 0.95f);
                    if (damageBarText != null)
                        damageBarText.text = "HP " + Mathf.RoundToInt(hp) + "%";
                }
                // barra carburante
                if (fuelBarImage != null)
                {
                    float fuel = Mathf.Clamp(
                        Game.Instance.CurrentVehicle.FuelPercent, 0f, 100f);
                    fuelBarImage.fillAmount = fuel / 100f;
                    fuelBarImage.color = fuel > 50f
                        ? new Color(0.2f, 0.7f, 1f, 0.95f)
                        : fuel > 20f
                        ? new Color(0.95f, 0.7f, 0.15f, 0.95f)
                        : new Color(0.9f, 0.2f, 0.2f, 0.95f);
                    if (fuelBarText != null)
                        fuelBarText.text = "BENZINA " + Mathf.RoundToInt(fuel) + "%";
                }
            }

            // Aggiorna UI economia ogni 0.5s
            uiRefreshTimer += Time.unscaledDeltaTime;
            if (uiRefreshTimer > 0.5f)
            {
                uiRefreshTimer = 0f;
                RefreshEconomyUI();
                RefreshRewardButton();
            }
        }

        /// <summary>
        /// Stato del pulsante GUARDA VIDEO +€25: abilitato quando la
        /// rewarded ad e' pronta (Android), semi-trasparente e non cliccabile
        /// quando non lo e'. In editor resta sempre abilitato (fallback).
        /// </summary>
        private void RefreshRewardButton()
        {
            if (rewardButton == null || rewardButtonImage == null) return;
            if (rewardButtonLabel == null) return;
            bool ready = RewardedAdHelper.Instance == null ||
                RewardedAdHelper.Instance.IsAvailable();
            rewardButtonImage.raycastTarget = ready;
            Color col = rewardButtonImage.color;
            col.a = ready ? 0.85f : 0.45f;
            rewardButtonImage.color = col;
            rewardButtonLabel.color = ready
                ? Color.white
                : new Color(1f, 1f, 1f, 0.5f);
            if (!ready) rewardButtonLabel.text = "VIDEO NON DISPONIBILE";
            else rewardButtonLabel.text = "GUARDA VIDEO +€25";
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

        private IEnumerator ToastRoutine(string message, UnityEngine.Events.UnityAction onTap)
        {
            toast.text = message;
            Color c = toast.color;
            c.a = 1f;
            toast.color = c;
            AttachToastTap(onTap);
            yield return new WaitForSecondsRealtime(2.2f);
            float t = 0f;
            while (t < 0.4f)
            {
                t += Time.deltaTime;
                c.a = Mathf.Lerp(1f, 0f, t / 0.4f);
                toast.color = c;
                yield return null;
            }
            DetachToastTap();
        }

        private UnityEngine.UI.Button _toastButton;

        private void AttachToastTap(UnityEngine.Events.UnityAction onTap)
        {
            try
            {
                if (toast == null || onTap == null) return;
                if (_toastButton == null)
                    _toastButton = toast.gameObject.AddComponent<UnityEngine.UI.Button>();
                _toastButton.targetGraphic = toast;
                toast.raycastTarget = true;
                _toastButton.onClick.RemoveAllListeners();
                _toastButton.onClick.AddListener(onTap);
            }
            catch (System.Exception e)
            {
                OsmDiag.Log("[UIManager] toast tap non attivabile: " + e.Message);
            }
        }

        private void DetachToastTap()
        {
            try
            {
                if (_toastButton != null)
                    _toastButton.onClick.RemoveAllListeners();
            }
            catch (System.Exception) { }
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

            // --- pulsante LAVORI: accesso diretto al pannello dei lavori
            // (Tassista/Corriere/Ronda), una delle funzioni core del gioco.
            var jobsRt = MakeRect("JobsButton", root, new Vector2(0.5f, 0f), new Vector2(0.5f, 0f), new Vector2(0f, 0f), new Vector2(0f, 0f));
            jobsRt.pivot = new Vector2(0.5f, 0.5f);
            jobsRt.anchoredPosition = new Vector2(-230f, 130f);
            jobsRt.sizeDelta = new Vector2(110f, 84f);
            Image jobsBg = jobsRt.gameObject.AddComponent<Image>();
            jobsBg.color = new Color(0.15f, 0.65f, 0.45f, 0.85f);
            jobsBg.raycastTarget = true;
            Button jobsBtn = jobsRt.gameObject.AddComponent<Button>();
            jobsBtn.targetGraphic = jobsBg;
            jobsBtn.onClick.AddListener(OnJobsButtonPressed);
            jobsButtonGo = jobsRt.gameObject;
            MakeText(jobsRt, "LAVORI", 26f, Color.white, TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            // --- orbit zone (right 45%)
            Image orbitImg = MakeImage(root, new Color(0f, 0f, 0f, 0.001f), new Vector2(0.55f, 0f), new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            orbitImg.raycastTarget = true;
            orbit = orbitImg.gameObject.AddComponent<OrbitZone>();
            orbit.OnDragDelta += dx =>
            {
                if (Game.Instance != null) Game.Instance.OnOrbitDelta(dx);
            };

            // --- pulsante SALTO in basso a destra (stile Brookhaven mobile):
            // il salto e' sempre disponibile con un tap, come in Brookhaven.
            var jumpRt = MakeRect("JumpButton", root, new Vector2(1f, 0f), new Vector2(1f, 0f), new Vector2(0f, 0f), new Vector2(0f, 0f));
            jumpRt.pivot = new Vector2(0.5f, 0.5f);
            jumpRt.anchoredPosition = new Vector2(-110f, 180f);
            jumpRt.sizeDelta = new Vector2(128f, 128f);
            Image jumpBg = jumpRt.gameObject.AddComponent<Image>();
            jumpBg.color = new Color(1f, 1f, 1f, 0.22f);
            jumpBg.raycastTarget = true;
            Button jumpBtn = jumpRt.gameObject.AddComponent<Button>();
            jumpBtn.targetGraphic = jumpBg;
            jumpBtn.onClick.AddListener(() =>
            {
                Vibration.Vibrate(25);
                // Regno Inferno esterno (gioco FloorIsLava): la HUD citta'
                // persiste ma il player citta' e' nascosto, l'anima del gioco
                // ha registrato il suo hook -> il pulsante salta davvero.
                var infernoJump = City.Afterlife.RealmSceneManager.InfernoJumpInput;
                if (infernoJump != null)
                {
                    City.OSM.OsmDiag.Log("[Brookhaven][UI] Jump -> anima Inferno");
                    infernoJump.BeginJump();
                    return;
                }
                var pc = City.Player.PlayerController.Instance;
                if (pc == null)
                {
                    City.OSM.OsmDiag.Log("[Brookhaven][UI] Jump button: PlayerController.Instance == null!");
                    return;
                }
                City.OSM.OsmDiag.Log("[Brookhaven][UI] Jump button premuto");
                pc.DoJump();
            });
            MakeText(jumpRt, "SALTA", 30f, Color.white, TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            // --- pulsante GIRO (giroscopio): sotto SALTA, stesso stile.
            var gyroRt = MakeRect("GyroButton", root, new Vector2(1f, 0f), new Vector2(1f, 0f), Vector2.zero, Vector2.zero);
            gyroRt.pivot = new Vector2(0.5f, 0.5f);
            gyroRt.anchoredPosition = new Vector2(-110f, 60f);
            gyroRt.sizeDelta = new Vector2(110f, 84f);
            Image gyroBg = gyroRt.gameObject.AddComponent<Image>();
            gyroBg.raycastTarget = true;
            gyroButtonImage = gyroBg;
            Button gyroBtn = gyroRt.gameObject.AddComponent<Button>();
            gyroBtn.targetGraphic = gyroBg;
            gyroBtn.onClick.AddListener(OnGyroTogglePressed);
            gyroButtonLabel = MakeText(gyroRt, "GIRO", 26f, Color.white, TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            gyroButtonLabel.raycastTarget = false;
            {
                var rig = Game.Instance != null ? Game.Instance.rig : City.Player.CameraRig.Instance;
                bool on = rig != null ? rig.GyroEnabled : PlayerPrefs.GetInt("gyroCam", 1) == 1;
                DrawGyroButton(on);
            }

            // --- pulsante modalita camera (terza/prima persona), stile
            // Brookhaven mobile: sotto il pulsante menu in alto a sinistra.
            var camRt = MakeRect("CamButton", root, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(16f, -172f), new Vector2(104f, -100f));
            City.OSM.OsmDiag.Log("[Brookhaven][UI] CamButton creata, anchor=(0,1) offsetMin=(16,-172) offsetMax=(104,-100)");
            Image camBg = camRt.gameObject.AddComponent<Image>();
            camBg.color = new Color(0f, 0f, 0f, 0.5f);
            camBg.raycastTarget = true;
            Button camBtn = camRt.gameObject.AddComponent<Button>();
            camBtn.targetGraphic = camBg;
            camModeText = MakeText(camRt, "1A", 30f, Color.white, TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            camModeText.raycastTarget = false;
            camBtn.onClick.AddListener(() =>
            {
                Vibration.Vibrate(25);
                var rig = City.Player.CameraRig.Instance;
                if (rig == null)
                {
                    City.OSM.OsmDiag.Log("[Brookhaven][Camera] CameraRig.Instance == null!");
                    return;
                }
                rig.ToggleFirstPerson();
                camModeText.text = rig.firstPerson ? "3A" : "1A";
                City.OSM.OsmDiag.Log("[Brookhaven][Camera] Toggle camera -> " +
                    (rig.firstPerson ? "PRIMA persona" : "TERZA persona"));
                ShowToast(rig.firstPerson
                    ? "Camera: prima persona"
                    : "Camera: terza persona");
            });

            // --- money HUD
            moneyText = MakeText(root, "€ 0", 42f, new Color(1f, 0.95f, 0.3f, 1f), TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(122f, -24f), new Vector2(330f, -56f));

            // --- unified player identity (nome + livello dal profilo Huntix)
            playerText = MakeText(root, "", 24f, new Color(0.55f, 0.85f, 1f, 1f), TextAlignmentOptions.Right, new Vector2(1f, 1f), new Vector2(1f, 1f), new Vector2(-260f, -28f), new Vector2(-24f, -72f));
            playerText.gameObject.SetActive(false);

            // --- egg counter (under money)
            eggCountText = MakeText(root, "", 26f, new Color(1f, 0.95f, 0.5f, 1f), TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(122f, -116f), new Vector2(330f, -140f));
            eggCountText.gameObject.SetActive(false);

            // --- active mission (under egg counter)
            missionText = MakeText(root, "", 22f, new Color(0.4f, 0.9f, 0.4f, 0.9f), TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(0.6f, 1f), new Vector2(24f, -102f), new Vector2(24f, -128f));
            missionText.gameObject.SetActive(false);

            // --- rewarded ad button (top-right, under ESCI)
            var rewardRt = MakeRect("RewardButton", root, new Vector2(1f, 1f), new Vector2(1f, 1f), new Vector2(-160f, -80f), new Vector2(-24f, -118f));
            Image rwBg = rewardRt.gameObject.AddComponent<Image>();
            rwBg.color = new Color(0.9f, 0.7f, 0.1f, 0.85f);
            rwBg.raycastTarget = true;
            rewardButtonImage = rwBg;
            rewardButton = rewardRt.gameObject.AddComponent<Button>();
            rewardButton.targetGraphic = rwBg;
            rewardButton.onClick.AddListener(OnRewardedAdPressed);
            rewardButtonLabel = MakeText(rewardRt, "GUARDA VIDEO +€25", 22f, Color.white, TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            // --- Bestiario uova button (top-right, a sinistra del video)
            var dexRt = MakeRect("DexButton", root, new Vector2(1f, 1f), new Vector2(1f, 1f), new Vector2(-300f, -80f), new Vector2(-168f, -118f));
            Image dexBg = dexRt.gameObject.AddComponent<Image>();
            dexBg.color = new Color(0.40f, 0.55f, 0.85f, 0.85f);
            dexBg.raycastTarget = true;
            Button dexBtn = dexRt.gameObject.AddComponent<Button>();
            dexBtn.targetGraphic = dexBg;
            dexBtn.onClick.AddListener(() => City.Economy.EggDexUI.Toggle());
            MakeText(dexRt, "BESTIARIO", 18f, Color.white, TextAlignmentOptions.Center, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);

            // --- stato GPS/OSM (riga piccola semi-trasparente con attribuzione permanente)
            gpsText = MakeText(root, "© OpenStreetMap contributors · ODbL", 15f, new Color(1f, 1f, 1f, 0.38f), TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(1f, 1f), new Vector2(24f, -136f), new Vector2(-24f, -160f));


            // --- LegalManager (pannello a schede per i documenti legali)
            var legalMgrGo = new GameObject("LegalManager");
            legalMgrGo.transform.SetParent(transform, false);
            _legal = legalMgrGo.AddComponent<LegalManager>();
            _legal.Init(canvas, root);


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

            // --- ESCI dall'interno: sempre visibile mentre sei dentro un
            // edificio (terza persona), così non resti mai bloccato.
            var interiorExitRt = MakeRect("InteriorExitButton", root,
                new Vector2(1f, 1f), new Vector2(1f, 1f),
                new Vector2(-170f, -74f), new Vector2(-40f, -130f));
            Image ieBg = interiorExitRt.gameObject.AddComponent<Image>();
            ieBg.color = new Color(0.8f, 0.25f, 0.2f, 0.95f);
            ieBg.raycastTarget = true;
            interiorExitBt = interiorExitRt.gameObject.AddComponent<Button>();
            interiorExitBt.targetGraphic = ieBg;
            interiorExitBt.onClick.AddListener(OnInteriorExitPressed);
            MakeText(interiorExitRt, "ESCI", 26f, Color.white,
                TextAlignmentOptions.Center, Vector2.zero, Vector2.one,
                Vector2.zero, Vector2.zero);
            interiorExitButton = interiorExitRt.gameObject;
            interiorExitButton.SetActive(false);

            BuildActionRadial();

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
            if (Game.Instance == null) return;
            Game.Instance.OnInteractPressed();
        }

        /// <summary>Apre direttamente il pannello dei lavori dal pulsante.</summary>
        private void OnGyroTogglePressed()
        {
            Vibration.Vibrate(25);
            bool now;
            var rig = Game.Instance != null ? Game.Instance.rig : City.Player.CameraRig.Instance;
            if (rig != null)
            {
                now = !rig.GyroEnabled;
                rig.SetGyroEnabled(now);
                PlayerPrefs.SetInt("gyroCam", now ? 1 : 0);
            }
            else
            {
                now = PlayerPrefs.GetInt("gyroCam", 1) != 1;
                PlayerPrefs.SetInt("gyroCam", now ? 1 : 0);
            }
            DrawGyroButton(now);
            City.OSM.OsmDiag.Log("[Camera][Gyro] toggle -> " + now);
        }

        private void DrawGyroButton(bool on)
        {
            if (gyroButtonImage == null) return;
            gyroButtonImage.color = on
                ? new Color(0.15f, 0.65f, 0.95f, 0.85f)
                : new Color(0.5f, 0.5f, 0.5f, 0.6f);
            if (gyroButtonLabel != null)
                gyroButtonLabel.text = on ? "GIRO" : "GIRO OFF";
        }

        private void OnJobsButtonPressed()
        {
            if (JobManager.Instance != null)
                JobManager.Instance.OpenPanel();
            else
                ShowToast("Lavori non disponibili");
        }

        private void OnInteriorExitPressed()
        {
            if (City.Interior.InteriorManager.Instance != null)
                City.Interior.InteriorManager.Instance.ExitInterior();
        }

        // ── menu' radiale Fai Azione ───────────────────────────

        private void BuildActionRadial()
        {
            // pulsante azioni contestuali in basso al centro: mostra l azione
            // principale corrente e, al tocco, apre il menu con le azioni
            // del momento (salto/fischio/sprint, auto, pacchi, porte, persone).
            var actionBg = MakeCircle(root, "ActionCenterBg",
                92f, new Color(0f, 0f, 0f, 0.35f));
            actionBg.anchorMin = new Vector2(0.5f, 0f);
            actionBg.anchorMax = new Vector2(0.5f, 0f);
            actionBg.pivot = new Vector2(0.5f, 0.5f);
            actionBg.anchoredPosition = new Vector2(0f, 190f);

            actionCenterButton = MakeCircle(actionBg, "ActionCenter",
                74f, ActionBg);
            var cb = actionCenterButton.GetComponent<Image>();
            cb.raycastTarget = true;
            var btn = actionCenterButton.gameObject.AddComponent<Button>();
            btn.targetGraphic = cb;
            btn.onClick.AddListener(ToggleActionRadial);
            actionLabel = MakeText(actionCenterButton, "AZIONI", 20f, Color.black,
                TextAlignmentOptions.Center, Vector2.zero, Vector2.one,
                Vector2.zero, Vector2.zero);
            
            // menu a tendina con le azioni del contesto corrente
            actionMenuRoot = MakeRect("ActionMenu", root,
                new Vector2(0.5f, 0f), new Vector2(0.5f, 0f),
                new Vector2(-190f, 370f), new Vector2(190f, 600f)).gameObject;
            Image backdrop = actionMenuRoot.AddComponent<Image>();
            backdrop.color = new Color(0.11f, 0.12f, 0.14f, 0.95f);
            backdrop.raycastTarget = true;
            var bgBtn = actionMenuRoot.AddComponent<Button>();
            bgBtn.targetGraphic = backdrop;
            bgBtn.onClick.AddListener(CloseActionRadial);

            actionMenuContent = MakeRect("ActionMenuContent",
                actionMenuRoot.GetComponent<RectTransform>(),
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                Vector2.zero, Vector2.zero);
            actionMenuContent.pivot = new Vector2(0.5f, 1f);
            var vlg = actionMenuContent.gameObject.AddComponent<VerticalLayoutGroup>();
            vlg.childControlWidth = true;
            vlg.childControlHeight = false;
            vlg.childForceExpandWidth = true;
            vlg.childForceExpandHeight = false;
            vlg.spacing = 6f;
            vlg.padding = new RectOffset(8, 8, 8, 8);
            actionMenuContent.gameObject.AddComponent<ContentSizeFitter>()
                .verticalFit = ContentSizeFitter.FitMode.PreferredSize;

            actionMenuRoot.SetActive(false);
        }

        private void UpdateActionLabel()
        {
            if (actionCenterButton == null || actionLabel == null) return;
            string lbl = ContextActionController.Instance != null
                ? ContextActionController.Instance.PrimaryLabel() : "AZIONI";
            actionLabel.text = lbl;
        }

        private void ToggleActionRadial()
        {
            if (actionMenuRoot == null) return;
            if (actionMenuRoot.activeSelf) CloseActionRadial();
            else RebuildActionMenu();
        }

        private void RebuildActionMenu()
        {
            if (actionMenuContent == null) return;
            foreach (Transform child in actionMenuContent)
                Destroy(child.gameObject);

            var list = new List<ContextActionController.Action>();
            if (ContextActionController.Instance != null)
                ContextActionController.Instance.BuildActions(list);

            for (int i = 0; i < list.Count; i++)
            {
                var act = list[i];
                RectTransform row = MakeActionRow("A" + i, actionMenuContent);
                Image rowImg = row.gameObject.AddComponent<Image>();
                rowImg.color = new Color(0.25f, 0.55f, 0.45f, 1f);
                rowImg.raycastTarget = true;
                var captured = act;
                row.gameObject.AddComponent<Button>().onClick.AddListener(
                    () => RunContext(captured));
                MakeText(row, act.label, 24f, Color.white,
                    TextAlignmentOptions.Center, Vector2.zero, Vector2.one,
                    Vector2.zero, Vector2.zero);
            }

            // accessi sempre presenti: LAVORI e FISCHIO
            RectTransform jobRow = MakeActionRow("AJobs", actionMenuContent);
            Image jobImg = jobRow.gameObject.AddComponent<Image>();
            jobImg.color = new Color(0.25f, 0.55f, 0.85f, 1f);
            jobImg.raycastTarget = true;
            jobRow.gameObject.AddComponent<Button>().onClick.AddListener(
                OnJobsPressed);
            MakeText(jobRow, "LAVORI", 24f, Color.white,
                TextAlignmentOptions.Center, Vector2.zero, Vector2.one,
                Vector2.zero, Vector2.zero);

            actionMenuRoot.SetActive(true);
            actionCenterButton.GetComponent<Image>().color =
                new Color(0.75f, 0.55f, 0.10f, 0.95f);
        }

        private RectTransform MakeActionRow(string name, RectTransform parent)
        {
            RectTransform row = MakeRect(name, parent,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                Vector2.zero, Vector2.zero);
            row.sizeDelta = new Vector2(0f, 52f);
            row.anchorMin = new Vector2(0f, 1f);
            row.anchorMax = new Vector2(1f, 1f);
            row.pivot = new Vector2(0.5f, 1f);
            return row;
        }

        private void RunContext(ContextActionController.Action act)
        {
            CloseActionRadial();
            if (act != null && act.run != null) act.run();
        }

        private void CloseActionRadial()
        {
            if (actionMenuRoot == null) return;
            actionMenuRoot.SetActive(false);
            if (actionCenterButton != null)
                actionCenterButton.GetComponent<Image>().color = ActionBg;
        }

        private void OnWhistlePressed()
        {
            CloseActionRadial();
            if (City.Vehicle.TaxiService.Instance != null)
                City.Vehicle.TaxiService.Instance.Whistle(Game.Instance);
            else
                ShowToast("TaxiService non disponibile");
        }

        private void OnJobsPressed()
        {
            CloseActionRadial();
            if (JobManager.Instance != null)
                JobManager.Instance.OpenPanel();
            else
                ShowToast("Lavori non disponibili");
        }


        private void OnRewardedAdPressed()
        {
            var helper = RewardedAdHelper.Instance;
            if (helper == null)
            {
                ShowToast("Video non disponibile");
                return;
            }
            if (!helper.IsAvailable())
            {
                ShowToast("Video non disponibile in questo momento");
                RefreshRewardButton();
                return;
            }
            ShowToast("Caricamento video...");
            helper.ShowRewardedAd(success => { RefreshRewardButton(); });
        }

        public void UpdateEggCount(int count)
        {
            if (eggCountText != null)
            {
                eggCountText.gameObject.SetActive(count > 0);
                eggCountText.text = "Uova: " + count;
            }
        }

        /// <summary>Mostra l'identita' del player unico Huntix (nome + livello),
        /// letta dal profilo condiviso, così Miacitta mostra lo stesso player
        /// degli altri moduli (stessa XP, stesso livello).</summary>
        public void UpdatePlayerProfile(string name, int level)
        {
            if (playerText != null)
            {
                string label = name.Trim();
                if (string.IsNullOrEmpty(label) || label == "Giocatore") label = "Giocatore";
                playerText.gameObject.SetActive(true);
                playerText.text = label + "  ·  Lv." + level;
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

        public void OnExitPressedPublic() { OnExitPressed(); }

        private void OnExitPressed()
        {
            if (exitPanel == null) return;
            // Difensivo: se una mappa espansa o i documenti legali sono ancora
            // aperti (canvas overlay 100/10), chiudili PRIMA di mostrare la
            // conferma: l'ExitPanel dovrebbe sempre risultare visibile in cima.
            if (MapSelectUI.Instance != null && MapSelectUI.Instance.IsOpen)
                MapSelectUI.Close();
            if (_legal != null && _legal.IsVisible) _legal.Hide();
            exitPanel.transform.SetAsLastSibling();
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
            // Sync Finale Unity→Android: esporta lo snapshot dello stato città
            // PRIMA che l'Activity venga chiusa (il bridge è ancora vivo).
            try { City.Sync.CityWorldSync.FlushNow(); } catch (System.Exception) { }
            // Ferma subito il polling OSM/JNI prima di chiudere l'Activity Unity:
            // in gara con il teardown dell'engine su Android un accesso al bridge
            // può provocare un crash nativo del processo.
            if (CityOSMWorld.Instance != null) CityOSMWorld.Instance.PrepareExit();
            // Ferma anche lo streaming dei chunk (tick loop + build in volo +
            // fetch tile) PRIMA di chiudere l'Activity Unity: in gara con il
            // teardown dell'engine un thread di build che tocca Unity può
            // segfaultare il processo (che resta vivo sulla Home nativa).
            if (CityChunkedWorld.Instance != null &&
                CityChunkedWorld.Instance.Manager != null)
            {
                CityChunkedWorld.Instance.Manager.StopStreaming();
                // Il teardown dell'engine su Android (mUnityPlayer.destroy con la
                // scena ancora gigante: 15 chunk, 1200+ veicoli, 5800 edifici/chunk)
                // ci mette ~10s e crashe in SIGSEGV durante lo smontaggio (log:
                // Exit -> ~10s -> SIGNALED signal=11). Scarichiamo TUTTO il mondo
                // qui, PRIMA di chiudere l'Activity: l'engine smonta quasi nulla.
                // In editor non svuotiamo (il no-op non chiude nulla e si perderebbe
                // la città visibile): il crash esiste solo su Android.
                if (!Application.isEditor)
                    CityChunkedWorld.Instance.Manager.UnloadWorldForExit();
            }            // Su Android chiude l'Activity Unity (BridgeActivity) e torna alla
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
            var dorigin = Game.Instance != null ? Game.Instance.player : null;
            dialogHasOrigin = dorigin != null;
            if (dorigin != null) dialogOriginPos = dorigin.transform.position;
        }

        /// <summary>Chiede al player con quanti anni iniziare la partita
        /// (preset: 0, 13, 25, 70). Retrodatta il giorno di nascita.</summary>
        public void ShowAgeChoice()
        {
            ShowDialog("Benvenuto a Hutix",
                new string[] {
                    "Da che eta inizierai la tua vita?",
                    "1. Bambino/a (0 anni)",
                    "2. Adolescente (13 anni)",
                    "3. Adulto/a (25 anni)",
                    "4. Anziano/a (70 anni)"
                },
                (int idx) =>
                {
                    int age = 0;
                    switch (idx)
                    {
                        case 1: age = 0; break;
                        case 2: age = 13; break;
                        case 3: age = 25; break;
                        case 4: age = 70; break;
                        default: age = 0; break;
                    }
                    City.NPC.FamilyManager.SetInitialAge(age);
                    City.NPC.FamilyManager.MarkAgeChosen();
                    ShowToast("Inizi come " + AgeLabel(age) + " (" + age + " anni).");
                });
        }

        private static string AgeLabel(int age)
        {
            if (age <= 0) return "Bambino/a";
            if (age < 18) return "Adolescente";
            if (age < 60) return "Adulto/a";
            return "Anziano/a";
        }

        public void HideDialog()
        {
            if (dialogPanel == null) return;
            dialogPanel.SetActive(false);
            dialogActive = false;
            dialogHasOrigin = false;
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
            var cb = dialogCallback;
            int choice = dialogIndex;
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