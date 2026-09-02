using System;
using UnityEngine;
using UnityEngine.UI;
using TMPro;
using City.Player;

namespace City.Economy
{
    /// <summary>
    /// Mini-gioco di cattura uova (premium): un dial rotante con un ago che
    /// gira a velocita' crescente. Il player tappa per fermarlo; se l'ago si
    /// ferma nella zona di cattura (grande per le uova comuni, minuscola per
    /// le leggendarie) l'uovo viene preso con un bonus moltiplicatore legato
    /// alla precisione del colpo (BULLSEYE).
    /// </summary>
    public class EggCaptureMinigame : MonoBehaviour
    {
        public static EggCaptureMinigame Instance;

        /// <summary>Crea l'istanza persistente del mini-gioco (piazzata sotto
        /// Game).</summary>
        public static EggCaptureMinigame Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("EggCaptureMinigame");
            if (Game.Instance != null) go.transform.SetParent(Game.Instance.transform, false);
            UnityEngine.Object.DontDestroyOnLoad(go);
            return go.AddComponent<EggCaptureMinigame>();
        }

        private EggController target;
        private Canvas canvas;
        private RectTransform dialRoot;      // ruota l'ago
        private Image needle;
        private Image hitZone;
        private TextMeshProUGUI statusText;
        private TextMeshProUGUI valueText;
        private Action<EggCaptureResult> onDone;

        private bool active;
        private bool spinning;
        private float angle;
        private float spinSpeed;
        private float _successWait;
        private Vector2 hitAngles;           // [start, end] dell'arco di cattura

        private const int SUCCESS_ZONE_WIDE = 90;   // gradi di hit zone
        private const int SUCCESS_ZONE_TINY = 20;

        public struct EggCaptureResult
        {
            public bool success;
            public float multiplier;      // 1.0 normale, >1 bullseye
        }

        private static readonly Color HitColor = new Color(0.2f, 1f, 0.3f, 1f);
        private static readonly Color ZoneColor = new Color(1f, 0.9f, 0.2f, 1f);

        private void Awake()
        {
            Instance = this;
        }

        public bool IsActive { get { return active; } }

        /// <summary>Avvia il mini-gioco per l'uovo indicato.</summary>
        public void Begin(EggController egg, Action<EggCaptureResult> done)
        {
            if (active) return;
            target = egg;
            onDone = done;
            BuildCanvas(egg);
            StartSpin();
        }

        private void BuildCanvas(EggController egg)
        {
            var canvasGo = new GameObject("EggCaptureCanvas");
            canvasGo.transform.SetParent(transform, false);
            canvas = canvasGo.AddComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            canvas.sortingOrder = 900;
            var scaler = canvasGo.AddComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080, 1920);

            // sfondo scuro semi-trasparente
            var bg = CreateImage("Backdrop", canvas.transform, "Overlay");
            Stretch(bg.rectTransform);
            bg.color = new Color(0f, 0f, 0f, 0.55f);

            // titolo
            statusText = CreateText("Status", canvas.transform, new Vector2(0, 220));
            statusText.text = "CATTURA L'UOVO!";
            statusText.fontSize = 56;
            statusText.color = Color.white;

            // dial
            dialRoot = CreateRect("Dial", canvas.transform, new Vector2(0, -120));
            SetSize(dialRoot, 620, 620);

            // zona di cattura (arco) sul dial
            float zoneHalf = ZoneWidth(egg.rarity) * 0.5f;
            hitZone = CreateImage("HitZone", dialRoot, "Overlay");
            SetSize(hitZone.rectTransform, 620, 620);
            hitZone.color = ZoneColor;
            hitZone.gameObject.AddComponent<CanvasGroup>().alpha = 0.45f;
            // rappresentata come un settore: uso un'immagine quadrata ruotata
            hitZone.rectTransform.localRotation = Quaternion.Euler(0f, 0f, -zoneHalf);
            hitZone.rectTransform.sizeDelta = new Vector2(620, 620 * (zoneHalf / 180f) * 1.2f);
            hitZone.rectTransform.pivot = new Vector2(0.5f, 0.02f);
            hitZone.rectTransform.anchoredPosition = Vector2.zero;

            // ago
            needle = CreateImage("Needle", dialRoot, "Overlay");
            needle.color = HitColor;
            needle.rectTransform.sizeDelta = new Vector2(6, 260);
            needle.rectTransform.pivot = new Vector2(0.5f, 0.02f);
            needle.rectTransform.anchoredPosition = Vector2.zero;
            needle.rectTransform.localRotation = Quaternion.Euler(0f, 0f, 0f);

            // valore + istruzioni
            valueText = CreateText("Value", canvas.transform, new Vector2(0, -420));
            valueText.text = "Uovo " + FancyName(egg.rarity) + " — valore " + egg.value + " EUR";
            valueText.fontSize = 40;
            valueText.color = GetRarityColor(egg.rarity);

            var hint = CreateText("Hint", canvas.transform, new Vector2(0, -500));
            hint.text = "TOCCA per fermare l'ago nella zona verde!";
            hint.fontSize = 34;
            hint.color = new Color(1f, 1f, 1f, 0.9f);

            // rettangolo invisibile full-screen che cattura il tap
            var catcher = CreateImage("TapCatcher", canvas.transform, "Overlay");
            Stretch(catcher.rectTransform);
            var cb = catcher.gameObject.AddComponent<Button>();
            catcher.color = new Color(0f, 0f, 0f, 0f);
            cb.onClick.AddListener(() => OnTap());
        }

        private void StartSpin()
        {
            spinning = true;
            active = true;
            _successWait = 0f;
            spinSpeed = UnityEngine.Random.Range(540f, 720f);   // gradi/sec
            angle = 0f;
            // zona di cattura centrata verso l'alto (angolo -half..+half attorno a 0)
            float half = ZoneWidth(target.rarity) * 0.5f;
            hitAngles = new Vector2(-half, half);
        }

        private void Update()
        {
            if (_successWait > 0f)
            {
                _successWait -= Time.deltaTime;
                if (_successWait <= 0f)
                    FinishSuccess();
                return;
            }
            if (!active || !spinning || needle == null) return;
            angle += spinSpeed * Time.deltaTime;
            if (angle > 360f) angle -= 360f;
            needle.rectTransform.localRotation = Quaternion.Euler(0f, 0f, -angle);
        }

        private void OnTap()
        {
            if (!active || !spinning) return;
            spinning = false;

            // angolo normalizzato -180..180
            float a = angle;
            if (a > 180f) a -= 360f;

            bool hit = a >= hitAngles.x && a <= hitAngles.y;
            if (hit)
            {
                // precisione: quanto lontano dal centro (0) dell'arco
                float half = hitAngles.y;
                float dist = Mathf.Abs(a);
                float mult = 1f + (1f - Mathf.Clamp01(dist / Mathf.Max(1f, half))) * 2f;
                Success(mult);
            }
            else
            {
                Fail();
            }
        }

        private void Success(float mult)
        {
            active = false;
            ShowMomentStatus("PRESO! +" + Mathf.RoundToInt(target.value * mult) + " EUR", true);
            _resultMultiplier = mult;
            _successWait = 0.9f;   // breve pausa per far leggere il risultato

            if (target != null)
                target.OnCaptured();
        }

        private void Fail()
        {
            // l'uovo resta: l'ago ricomincia
            statusText.text = "MANCATO! RIPROVA!";
            statusText.color = new Color(1f, 0.3f, 0.3f, 1f);
            StartSpin();
        }

        private void ShowMomentStatus(string msg, bool good)
        {
            statusText.text = msg;
            statusText.color = good ? new Color(0.3f, 1f, 0.4f, 1f) : new Color(1f, 0.3f, 0.3f, 1f);
        }

        private float _resultMultiplier;

        private void FinishSuccess()
        {
            if (onDone != null)
            {
                onDone(new EggCaptureResult { success = true, multiplier = _resultMultiplier });
                onDone = null;
            }
            Close();
        }

        public void Close()
        {
            if (canvas != null) Destroy(canvas.gameObject);
            canvas = null;
            dialRoot = null;
            needle = null;
            hitZone = null;
            target = null;
            active = false;
            spinning = false;
            _successWait = 0f;
        }

        private float ZoneWidth(EggController.Rarity r)
        {
            switch (r)
            {
                case EggController.Rarity.Common: return SUCCESS_ZONE_WIDE;      // 90°
                case EggController.Rarity.Uncommon: return 60f;
                case EggController.Rarity.Rare: return 38f;
                case EggController.Rarity.Legendary: return SUCCESS_ZONE_TINY;   // 20°
                default: return SUCCESS_ZONE_WIDE;
            }
        }

        private static string FancyName(EggController.Rarity r)
        {
            switch (r)
            {
                case EggController.Rarity.Common: return "COMUNE";
                case EggController.Rarity.Uncommon: return "NON COMUNE";
                case EggController.Rarity.Rare: return "RARO";
                case EggController.Rarity.Legendary: return "LEGGENDARIO";
                default: return "COMUNE";
            }
        }

        private static Color GetRarityColor(EggController.Rarity r)
        {
            switch (r)
            {
                case EggController.Rarity.Common: return new Color(1f, 0.95f, 0.7f);
                case EggController.Rarity.Uncommon: return new Color(0.4f, 0.9f, 0.4f);
                case EggController.Rarity.Rare: return new Color(0.3f, 0.5f, 1f);
                case EggController.Rarity.Legendary: return new Color(1f, 0.6f, 0.1f);
                default: return new Color(1f, 0.95f, 0.7f);
            }
        }

        // ── Pesanti helpers UI (tutto costruito a runtime, niente prefab) ──

        public static Image CreateImage(string name, Transform parent, string layer)
        {
            var go = new GameObject(name);
            if (parent != null) go.transform.SetParent(parent, false);
            var img = go.AddComponent<Image>();
            img.raycastTarget = layer == "Overlay";
            return img;
        }

        public static RectTransform CreateRect(string name, Transform parent, Vector2 pos)
        {
            var go = new GameObject(name);
            if (parent != null) go.transform.SetParent(parent, false);
            var rt = go.AddComponent<RectTransform>();
            rt.anchoredPosition = pos;
            return rt;
        }

        public static TextMeshProUGUI CreateText(string name, Transform parent, Vector2 pos)
        {
            var go = new GameObject(name);
            if (parent != null) go.transform.SetParent(parent, false);
            var txt = go.AddComponent<TextMeshProUGUI>();
            txt.alignment = TextAlignmentOptions.Center;
            txt.fontSize = 40;
            var rt = txt.rectTransform;
            rt.anchoredPosition = pos;
            SetSize(rt, 700, 80);
            return txt;
        }

        public static void SetSize(RectTransform rt, float w, float h)
        {
            rt.sizeDelta = new Vector2(w, h);
        }

        public static void Stretch(RectTransform rt)
        {
            rt.anchorMin = Vector2.zero;
            rt.anchorMax = Vector2.one;
            rt.offsetMin = Vector2.zero;
            rt.offsetMax = Vector2.zero;
        }
    }
}
