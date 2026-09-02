using System;
using UnityEngine;
using UnityEngine.UI;
using TMPro;

namespace City.Environment
{
    /// <summary>
    /// Statistica "energia" del player (0-100, persistita): si consuma con i
    /// lavori (-10 a passo) e si recupera sedendosi, bevendo alle fontanelle,
    /// al bar o in farmacia. Sotto la soglia minima i lavori sono bloccati.
    /// La barra vive in alto a sinistra sulla canvas principale.
    /// </summary>
    public static class EnergySystem
    {
        public const int MaxValue = 100;
        public const int JobStepCost = 10;
        public const int MinJobEnergy = 10;

        private const string Key = "city_energy";

        public static event Action<int> OnChanged;

        private static GameObject _bar;
        private static Image _fill;
        private static TMP_Text _text;

        public static int Value
        {
            get { return Mathf.Clamp(PlayerPrefs.GetInt(Key, MaxValue), 0, MaxValue); }
        }

        public static bool CanWork
        {
            get { return Value >= MinJobEnergy; }
        }

        public static void Consume(int amount)
        {
            Set(Value - Mathf.Max(0, amount));
        }

        public static void Restore(int amount)
        {
            Set(Value + Mathf.Max(0, amount));
        }

        public static void Set(int v)
        {
            v = Mathf.Clamp(v, 0, MaxValue);
            if (v == Value) { RefreshBar(); return; }
            PlayerPrefs.SetInt(Key, v);
            PlayerPrefs.Save();
            RefreshBar();
            var h = OnChanged;
            if (h != null) h(v);
            // Unico player: mantiene l'energia del profilo Huntix aggiornata.
            try { City.NPC.FamilyManager.SyncEnergyToHuntix(v); }
            catch (Exception) { }
        }

        // ── HUD ──────────────────────────────────────────────────

        public static void EnsureHud()
        {
            if (_bar != null) return;
            var canvas = GameObject.FindObjectOfType<Canvas>();
            if (canvas == null) return;

            _bar = new GameObject("EnergyBar");
            var rt = _bar.AddComponent<RectTransform>();
            rt.SetParent(canvas.transform, false);
            rt.anchorMin = rt.anchorMax = rt.pivot = new Vector2(0f, 1f);
            rt.anchoredPosition = new Vector2(14f, -46f);
            rt.sizeDelta = new Vector2(210f, 26f);
            var bg = _bar.AddComponent<Image>();
            bg.color = new Color(0f, 0f, 0f, 0.45f);
            bg.raycastTarget = false;

            var fillGo = new GameObject("Fill", typeof(RectTransform));
            var frt = fillGo.GetComponent<RectTransform>();
            frt.SetParent(_bar.transform, false);
            frt.anchorMin = frt.anchorMax = frt.pivot = new Vector2(0f, 0.5f);
            frt.anchoredPosition = new Vector2(2f, 0f);
            frt.sizeDelta = new Vector2(-4f, -4f);
            frt.offsetMin = new Vector2(2f, 2f);
            frt.offsetMax = new Vector2(-2f, -2f);
            frt.anchorMin = new Vector2(0f, 0f);
            frt.anchorMax = new Vector2(1f, 1f);
            _fill = fillGo.AddComponent<Image>();
            _fill.raycastTarget = false;
            _fill.type = Image.Type.Filled;
            _fill.fillMethod = Image.FillMethod.Horizontal;

            var txtGo = new GameObject("Txt", typeof(RectTransform));
            var trt = txtGo.GetComponent<RectTransform>();
            trt.SetParent(_bar.transform, false);
            trt.anchorMin = trt.anchorMax = Vector2.one * 0.5f;
            trt.sizeDelta = new Vector2(200f, 24f);
            _text = txtGo.AddComponent<TextMeshProUGUI>();
            _text.fontSize = 17f;
            _text.alignment = TextAlignmentOptions.Center;
            _text.raycastTarget = false;
            var font = TMP_Settings.defaultFontAsset;
            if (font == null) font = Resources.Load<TMP_FontAsset>(
                "Fonts & Materials/LiberationSans SDF");
            _text.font = font;

            RefreshBar();
        }

        private static void RefreshBar()
        {
            if (_bar == null) { EnsureHud(); if (_bar == null) return; }
            float f = Value / (float)MaxValue;
            _fill.fillAmount = f;
            _fill.color = f > 0.5f ? new Color(0.25f, 0.75f, 0.4f, 0.95f)
                : f > 0.25f ? new Color(0.9f, 0.65f, 0.15f, 0.95f)
                : new Color(0.85f, 0.25f, 0.2f, 0.95f);
            _text.text = "\u26a1 " + Value;
        }
    }
}
