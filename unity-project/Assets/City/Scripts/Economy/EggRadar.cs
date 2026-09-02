using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;
using TMPro;

namespace City.Economy
{
    /// <summary>
    /// Radar di caccia uova: mostra la direzione e la distanza dell'uovo
    /// (non ancora catturato) piu' vicino entro un raggio massimo, in stile
    /// cacciatore Huntix. La freccia punta sempre verso la preda e cambia
    /// colore in base alla rarita'. Costruito 100% a runtime.
    /// </summary>
    public class EggRadar : MonoBehaviour
    {
        public static EggRadar Instance;

        public float maxRange = 45f;

        private RectTransform arrow;
        private Image arrowImg;
        private TMP_Text distText;
        private Canvas canvas;

        private static readonly Color CommonColor = new Color(1f, 0.95f, 0.7f);
        private static readonly Color UncommonColor = new Color(0.4f, 0.9f, 0.4f);
        private static readonly Color RareColor = new Color(0.3f, 0.5f, 1f);
        private static readonly Color LegendaryColor = new Color(1f, 0.6f, 0.1f);

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
            BuildHud();
        }

        private void BuildHud()
        {
            canvas = GameObject.FindObjectOfType<Canvas>();
            if (canvas == null) return;

            // Root del radar (cerchio in basso a sinistra)
            var root = new GameObject("EggRadar", typeof(RectTransform));
            var rt = root.GetComponent<RectTransform>();
            rt.SetParent(canvas.transform, false);
            rt.anchorMin = rt.anchorMax = rt.pivot = new Vector2(0f, 0f);
            rt.anchoredPosition = new Vector2(24f, 24f);
            rt.sizeDelta = new Vector2(150f, 150f);

            var bg = root.AddComponent<Image>();
            bg.color = new Color(0f, 0f, 0f, 0.45f);
            bg.raycastTarget = false;

            // Freccia (direzionale)
            var arrowGo = new GameObject("Needle", typeof(RectTransform));
            var art = arrowGo.GetComponent<RectTransform>();
            art.SetParent(rt, false);
            art.anchorMin = art.anchorMax = new Vector2(0.5f, 0.5f);
            art.sizeDelta = new Vector2(6f, 64f);
            arrowImg = arrowGo.AddComponent<Image>();
            arrowImg.color = CommonColor;
            arrowImg.raycastTarget = false;
            art.pivot = new Vector2(0.5f, 0.12f);
            arrow = art;

            // Punto centrale
            var dot = new GameObject("Dot", typeof(RectTransform));
            var drt = dot.GetComponent<RectTransform>();
            drt.SetParent(rt, false);
            drt.anchorMin = drt.anchorMax = new Vector2(0.5f, 0.5f);
            drt.sizeDelta = new Vector2(10f, 10f);
            var dotImg = dot.AddComponent<Image>();
            dotImg.color = new Color(1f, 1f, 1f, 0.9f);
            dotImg.raycastTarget = false;

            // Distanza sotto il radar
            var txtGo = new GameObject("Dist", typeof(RectTransform));
            var trt = txtGo.GetComponent<RectTransform>();
            trt.SetParent(canvas.transform, false);
            trt.anchorMin = trt.anchorMax = new Vector2(0f, 0f);
            trt.anchoredPosition = new Vector2(99f, 28f);
            trt.sizeDelta = new Vector2(120f, 28f);
            distText = txtGo.AddComponent<TextMeshProUGUI>();
            distText.fontSize = 18f;
            distText.alignment = TextAlignmentOptions.Center;
            distText.raycastTarget = false;
            distText.text = "";
            var font = TMP_Settings.defaultFontAsset;
            if (font == null) font = Resources.Load<TMP_FontAsset>("Fonts & Materials/LiberationSans SDF");
            distText.font = font;
        }

        private void Update()
        {
            Transform target = NearestEgg();
            if (target == null)
            {
                if (arrow != null) arrow.gameObject.SetActive(false);
                if (distText != null) distText.text = "";
                return;
            }

            Transform player = Game.Instance != null ? Game.Instance.player.transform : null;
            if (player == null) return;

            if (arrow != null) arrow.gameObject.SetActive(true);
            Vector3 toEgg = target.position - player.position;
            toEgg.y = 0f;

            float dist = toEgg.magnitude;
            if (distText != null)
                distText.text = dist < 1f ? "QUI!" : ((int)dist).ToString() + " m";

            // Angolo nel piano orizzontale (guardando lungo +Z del player)
            float angle = Mathf.Atan2(toEgg.x, toEgg.z) * Mathf.Rad2Deg;
            if (arrow != null)
                arrow.localRotation = Quaternion.Euler(0f, 0f, -angle);

            // Colore in base alla rarita'
            var egg = target.GetComponent<EggController>();
            if (arrowImg != null && egg != null)
                arrowImg.color = RarityColor(egg.rarity);
        }

        private Transform NearestEgg()
        {
            var manager = EggSpawnManager.Instance;
            if (manager == null) return null;
            Transform player = Game.Instance != null ? Game.Instance.player.transform : null;
            if (player == null) return null;

            Transform best = null;
            float bestDist = maxRange + 1f;
            List<GameObject> eggs = manager.ActiveEggs;
            for (int i = 0; i < eggs.Count; i++)
            {
                var go = eggs[i];
                if (go == null) continue;
                var egg = go.GetComponent<EggController>();
                if (egg == null || egg.Captured || !egg.PlayerNearCanRadar)
                    continue;
                float d = Vector3.Distance(go.transform.position, player.position);
                if (d < bestDist) { bestDist = d; best = go.transform; }
            }
            return best;
        }

        private static Color RarityColor(EggController.Rarity r)
        {
            switch (r)
            {
                case EggController.Rarity.Common: return CommonColor;
                case EggController.Rarity.Uncommon: return UncommonColor;
                case EggController.Rarity.Rare: return RareColor;
                case EggController.Rarity.Legendary: return LegendaryColor;
                default: return CommonColor;
            }
        }

        public static void EnsureHud()
        {
            if (Instance != null) return;
            var go = new GameObject("EggRadar");
            go.AddComponent<EggRadar>();
        }
    }
}
