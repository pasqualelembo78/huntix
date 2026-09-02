using UnityEngine;
using UnityEngine.UI;
using TMPro;

namespace City.Economy
{
    /// <summary>
    /// EggDexUI — pannello Bestiario delle uova: griglia tipo × rarita' che
    /// mostra quali combinazioni il cacciatore ha gia' scoperto (accese) e
    /// quali mancano (spente), con contatore totale. Costruito 100% a runtime.
    /// </summary>
    public static class EggDexUI
    {
        private static GameObject _panel;

        private static readonly Color HeaderBg = new Color(0.10f, 0.11f, 0.13f, 0.97f);
        private static readonly Color FoundColor = new Color(0.35f, 0.9f, 0.55f);
        private static readonly Color MissingColor = new Color(0.32f, 0.34f, 0.38f);

        private static readonly System.Collections.Generic.Dictionary<string, Color> RarityColors =
            new System.Collections.Generic.Dictionary<string, Color>
            {
                { "Common", new Color(1f, 0.95f, 0.7f) },
                { "Uncommon", new Color(0.4f, 0.9f, 0.4f) },
                { "Rare", new Color(0.3f, 0.5f, 1f) },
                { "Legendary", new Color(1f, 0.6f, 0.1f) },
            };

        public static void Toggle()
        {
            if (_panel != null) { Hide(); return; }
            Show();
        }

        public static void Show()
        {
            if (_panel != null) return;
            var canvas = GameObject.FindObjectOfType<Canvas>();
            if (canvas == null) return;

            _panel = new GameObject("EggDexPanel", typeof(RectTransform));
            var rt = _panel.GetComponent<RectTransform>();
            rt.SetParent(canvas.transform, false);
            rt.anchorMin = rt.anchorMax = new Vector2(0.5f, 0.5f);
            rt.sizeDelta = new Vector2(640f, 540f);
            var bg = _panel.AddComponent<Image>();
            bg.color = HeaderBg;

            // Intestazione
            MakeText(rt, "BESTIARIO UOVA — " + EggDex.TotalFound + "/" + EggDex.TotalEntries,
                30f, Color.white, TextAlignmentOptions.Center,
                new Vector2(0f, 1f), new Vector2(1f, 1f), new Vector2(0f, -30f), new Vector2(0f, 40f));

            // Griglia: per ogni tipo, una riga con una cella per rarita'
            var types = System.Enum.GetValues(typeof(EggController.EggType));
            var rarities = System.Enum.GetValues(typeof(EggController.Rarity));
            int nTypes = types.Length;
            int nRar = rarities.Length;

            float rowH = 30f;
            float colW = 118f;
            float gridTop = 540f - 90f;        // sotto intestazione
            float gridBottom = 70f;            // sopra il pulsante
            float gridH = gridTop - gridBottom;

            // Alloca tutte le righe nella zona visibile: se troppe, scrolla via
            // dimensione contenitore (semplicemente overflow, cosmetico).
            for (int r = 0; r < nTypes; r++)
            {
                var t = (EggController.EggType)types.GetValue(r);
                float y = gridTop - (r + 1) * rowH;

                MakeText(rt, t.ToString(), 18f, new Color(0.85f, 0.85f, 0.85f),
                    TextAlignmentOptions.Left,
                    new Vector2(0.5f, 0.5f), new Vector2(0.5f, 0.5f),
                    new Vector2(-(colW * (nRar - 1)) / 2f - 70f, y - gridH * 0.5f),
                    new Vector2(120f, rowH));

                for (int c = 0; c < nRar; c++)
                {
                    var rar = (EggController.Rarity)rarities.GetValue(c);
                    bool has = EggDex.Has(t, rar);
                    float cellX = -(colW * (nRar - 1)) / 2f + c * colW;
                    var cell = new GameObject("Cell", typeof(RectTransform));
                    var crt = cell.GetComponent<RectTransform>();
                    crt.SetParent(rt, false);
                    crt.anchorMin = crt.anchorMax = new Vector2(0.5f, 0.5f);
                    crt.pivot = new Vector2(0.5f, 0.5f);
                    crt.anchoredPosition = new Vector2(cellX, y - gridH * 0.5f);
                    crt.sizeDelta = new Vector2(colW - 6f, rowH - 4f);
                    var cellImg = cell.AddComponent<Image>();
                    cellImg.color = has ? FoundColor : MissingColor;
                    cellImg.raycastTarget = false;
                }
            }

            // Intestazione colonne rarita' (sopra la griglia)
            for (int c = 0; c < nRar; c++)
            {
                string rn = System.Enum.GetName(typeof(EggController.Rarity), rarities.GetValue(c));
                Color rc = RarityColors.TryGetValue(rn, out var col) ? col : Color.white;
                float cellX = -(colW * (nRar - 1)) / 2f + c * colW;
                MakeText(rt, rn, 18f, rc, TextAlignmentOptions.Center,
                    new Vector2(0.5f, 0.5f), new Vector2(0.5f, 0.5f),
                    new Vector2(cellX, gridTop - 20f - gridH * 0.5f), new Vector2(colW, 24f));
            }

            // Pulsante chiudi (sfondo + label)
            var closeGo = new GameObject("Close", typeof(RectTransform));
            var crt2 = closeGo.GetComponent<RectTransform>();
            crt2.SetParent(rt, false);
            crt2.anchorMin = crt2.anchorMax = new Vector2(0.5f, 0f);
            crt2.anchoredPosition = new Vector2(0f, 20f);
            crt2.sizeDelta = new Vector2(160f, 44f);
            var cBg = closeGo.AddComponent<Image>();
            cBg.color = new Color(0.28f, 0.30f, 0.34f);
            var btn = closeGo.AddComponent<Button>();
            btn.targetGraphic = cBg;
            btn.onClick.AddListener(Hide);
            MakeText(crt2, "CHIUDI", 20f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
        }

        public static void Hide()
        {
            if (_panel != null) { Object.Destroy(_panel); _panel = null; }
        }

        private static TMP_Text MakeText(RectTransform parent, string text, float size, Color color,
            TextAlignmentOptions align, Vector2 anchorMin, Vector2 anchorMax, Vector2 pos, Vector2 sizeDelta)
        {
            var go = new GameObject("Text", typeof(RectTransform));
            var rt = go.GetComponent<RectTransform>();
            rt.SetParent(parent, false);
            rt.anchorMin = anchorMin;
            rt.anchorMax = anchorMax;
            rt.pivot = new Vector2(0.5f, 0.5f);
            rt.anchoredPosition = pos;
            rt.sizeDelta = sizeDelta;
            var txt = go.AddComponent<TextMeshProUGUI>();
            txt.text = text;
            txt.fontSize = size;
            txt.color = color;
            txt.alignment = align;
            txt.raycastTarget = false;
            var font = TMP_Settings.defaultFontAsset;
            if (font == null) font = Resources.Load<TMP_FontAsset>("Fonts & Materials/LiberationSans SDF");
            txt.font = font;
            return txt;
        }
    }
}
