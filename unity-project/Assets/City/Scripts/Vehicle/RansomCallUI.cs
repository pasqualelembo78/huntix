using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;
using TMPro;
using City.World;

namespace City.Vehicle
{
    /// <summary>
    /// Telefonata del ladro: il famoso "cavallo di ritorno". L'auto rubata
    /// viene restituita SOLO se paghi il riscatto richiesto entro la
    /// scadenza. Accetti -> l'auto compare davanti all'officina piu'
    /// vicina (con qualche danno in piu'). Rifiuti -> dopo qualche ora c'e'
    /// una chance che venga ritrovata abbandonata, altrimenti e' persa.
    /// </summary>
    public class RansomCallUI : MonoBehaviour
    {
        public static RansomCallUI Instance { get; private set; }

        private GameObject panel;
        private TMP_Text titleText;
        private TMP_Text bodyText;
        private Transform actionsRoot;
        private TMP_FontAsset font;

        private string pendingCode;
        private int pendingRansom;

        private static readonly Color OverlayBg = new Color(0f, 0f, 0f, 0.75f);
        private static readonly Color CardBg = new Color(0.06f, 0.08f, 0.10f, 0.99f);
        private static readonly Color AcceptColor = new Color(0.15f, 0.65f, 0.45f, 1f);
        private static readonly Color RefuseColor = new Color(0.80f, 0.25f, 0.20f, 1f);
        private static readonly Color RingColor = new Color(0.35f, 0.65f, 1f);

        public static void ShowTheft(string code, string model, int ransom,
            double deadlineEpoch)
        {
            if (Instance == null)
            {
                var go = new GameObject("RansomCallUI");
                DontDestroyOnLoad(go);
                Instance = go.AddComponent<RansomCallUI>();
            }
            Instance.pendingCode = code;
            Instance.pendingRansom = ransom;
            Instance.ShowCall(model, ransom, deadlineEpoch);
        }

        /// <summary>Notifica di auto ritrovata abbandonata (dopo rifiuto).</summary>
        public static void ShowRecovered(string model)
        {
            if (Instance == null) return;   // solo toast, senza pannello
            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast(
                    "\uD83D\uDC8C POLIZIA: la tua " + model +
                    " \u00e8 stata ritrovata abbandonata! Vai a riprenderla.");
        }

        public void Close()
        {
            if (panel != null) panel.SetActive(false);
            Time.timeScale = 1f;
        }

        private void ShowCall(string model, int ransom, double deadlineEpoch)
        {
            if (panel == null) BuildPanel();
            titleText.text = "\uD83D\uDCDE CHIAMATA IN ARRIVO";
            double hoursLeft = System.Math.Max(0.0,
                (deadlineEpoch - UtilityNow()) / 3600.0);
            bodyText.text =
                "Numero sconosciuto...\n\n" +
                "\"Abbiamo la tua " + (string.IsNullOrEmpty(model) ? "auto" : model) +
                ". Se vuoi il CAVALLO DI RITORNO portaci \u20ac" + ransom +
                ". Hai " + hoursLeft.ToString("F0") +
                " ore prima che sparisca per sempre...\"\n\n" +
                "Se accetti l'auto sar\u00e0 consegnata all'officina pi\u00f9 vicina.";
            BuildActions();
            panel.SetActive(true);
            Time.timeScale = 0f;
        }

        private static double UtilityNow()
        {
            return (System.DateTime.UtcNow -
                    new System.DateTime(1970, 1, 1)).TotalSeconds;
        }

        private void BuildActions()
        {
            foreach (Transform child in actionsRoot) Destroy(child.gameObject);

            AddButton(actionsRoot, "PAGA IL RISCATTO - \u20ac" + pendingRansom,
                AcceptColor, () =>
            {
                string code = pendingCode;
                int cost = pendingRansom;
                var api = VehicleOwnershipApi.Ensure();
                Close();
                if (!Wallet.CanAfford(cost))
                {
                    Toast("Non hai i soldi! Il ladro riattacca...");
                    api.RefuseRansom(code);
                    return;
                }
                // consegna presso l'officina piu' vicina all'auto rubata
                api.NearestRepairOf(code, (lat, lon, name) =>
                {
                    Wallet.Spend(cost);
                    api.RansomRespond(code, true, lat, lon, (ok, outc) =>
                    {
                        Toast(ok
                            ? "Affare fatto: la tua auto ti aspetta da " + name
                            : "Il ladro non si \u00e8 pi\u00f9 fatto sentire...");
                    });
                });
            });

            AddButton(actionsRoot, "RIFIUTA E DENUNCIA", RefuseColor, () =>
            {
                string code = pendingCode;
                Close();
                var api = VehicleOwnershipApi.Ensure();
                api.RefuseRansom(code);
                Toast("Hai rifiutato. L'auto sar\u00e0 cercata dalla polizia...");
            });
        }

        private void AddButton(Transform parent, string label, Color bg,
            System.Action onClick)
        {
            var rt = MakeRect("Act", parent, new Vector2(0f, 1f),
                new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            rt.sizeDelta = new Vector2(0f, 56f);
            rt.pivot = new Vector2(0.5f, 1f);
            var img = rt.gameObject.AddComponent<Image>();
            img.color = bg;
            var btn = rt.gameObject.AddComponent<Button>();
            btn.targetGraphic = img;
            btn.onClick.AddListener(new UnityEngine.Events.UnityAction(onClick));
            MakeText(rt, label, 26f, Color.white,
                TextAlignmentOptions.Center, Vector2.zero, Vector2.one,
                Vector2.zero, Vector2.zero);
        }

        private void Toast(string msg)
        {
            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast(msg);
        }

        // ── costruzione pannello ───────────────────────────────────

        private void BuildPanel()
        {
            font = TMP_Settings.defaultFontAsset;
            if (font == null) font = Resources.Load<TMP_FontAsset>(
                "Fonts & Materials/LiberationSans SDF");

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
                Debug.LogError("[RansomCallUI] nessuna Canvas nella scena");
                return;
            }
            if (EventSystem.current == null)
            {
                var esGo = new GameObject("EventSystem");
                esGo.AddComponent<EventSystem>();
                esGo.AddComponent<StandaloneInputModule>();
            }

            panel = new GameObject("RansomPanel");
            var prt = panel.AddComponent<RectTransform>();
            prt.SetParent(canvas.transform, false);
            prt.anchorMin = Vector2.zero;
            prt.anchorMax = Vector2.one;
            prt.offsetMin = Vector2.zero;
            prt.offsetMax = Vector2.zero;
            panel.AddComponent<Image>().color = OverlayBg;

            var card = MakeRect("Card", prt, new Vector2(0.5f, 0.5f),
                new Vector2(0.5f, 0.5f),
                new Vector2(-280f, -240f), new Vector2(280f, 240f));
            card.gameObject.AddComponent<Image>().color = CardBg;

            titleText = MakeText(card, "", 34f, RingColor,
                TextAlignmentOptions.Center, new Vector2(0f, 1f),
                new Vector2(1f, 1f), new Vector2(16f, -16f), new Vector2(-16f, -64f));

            bodyText = MakeText(card, "", 25f, Color.white,
                TextAlignmentOptions.TopLeft, new Vector2(0f, 1f),
                new Vector2(1f, 1f), new Vector2(22f, -70f), new Vector2(-22f, -260f));

            actionsRoot = MakeRect("Actions", card, new Vector2(0f, 0f),
                new Vector2(1f, 1f), new Vector2(24f, 24f), new Vector2(-24f, -120f));
            var vlg = actionsRoot.gameObject.AddComponent<VerticalLayoutGroup>();
            vlg.childControlWidth = true;
            vlg.childControlHeight = false;
            vlg.childForceExpandWidth = true;
            vlg.childForceExpandHeight = false;
            vlg.spacing = 10f;

            panel.SetActive(false);
        }

        private RectTransform MakeRect(string name, Transform parent,
            Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
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

        private TMP_Text MakeText(RectTransform parent, string content, float size,
            Color color, TextAlignmentOptions alignment, Vector2 anchorMin,
            Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
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
