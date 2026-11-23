// Adattato da Zone Zero — Copyright (c) 2026 Mishal Bhasim, MIT.
// Sorgenti: Assets/_Project/Scripts/UI/VirtualJoystick.cs e
// Assets/_Project/Scripts/Player/MobileInputHandler.cs del repository
// https://github.com/mishal-bhasim/Zone-Zero (vedi THIRD_PARTY_NOTICES.md).
// Rimosse le dipendenze da Unity.VisualScripting e il Debug.Log per-frame;
// aggiunto lo smoothing esponenziale e il bridge Android di Huntix.
using UnityEngine;
using UnityEngine.EventSystems;
using City.OSM;
using Huntix.Bridge;

namespace City.UI
{
    /// <summary>
    /// Joystick touch alternativo, attivabile dal menu, derivato dal pattern
    /// di Zone Zero (MIT). Non sostituisce DynamicJoystick: i due convivono e
    /// UIManager sceglie quale dei due alimenta PlayerController.SetMoveInput,
    /// così è possibile misurare latenza e fluidità con i probe già presenti.
    /// </summary>
    public class AlternateTouchInput : MonoBehaviour, IPointerDownHandler, IDragHandler, IPointerUpHandler
    {
        private const string PrefsKey = "huntix_alt_touch";
        private const float SmoothTime = 0.06f;

        public RectTransform canvasRoot;
        public RectTransform joystickBase;
        public RectTransform joystickHandle;
        public float radius = 120f;

        private Vector2 origin;
        private Vector2 target;
        private Vector2 smoothed;
        private bool active;

        public static bool Enabled { get; private set; }

        /// <summary>Chiamato una volta quando la UI viene costruita: legge la
        /// scelta salvata da Android senza inviare eventi e senza uova.</summary>
        public static void Initialize()
        {
            Enabled = PlayerPrefs.GetInt(PrefsKey, 0) == 1;
        }

        public static void SetEnabled(bool on, bool notify)
        {
            Enabled = on;
            PlayerPrefs.SetInt(PrefsKey, on ? 1 : 0);
            PlayerPrefs.Save();
            if (!notify) return;

            long ts = System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            string json = "{\"source\":\"" + (on ? "zone-zero" : "huntix") +
                "\",\"tsMt\":" + ts + "}";
            try { UnityBridge.SendMessageToAndroid("AltTouchChanged", json); }
            catch (System.Exception e)
            {
                OsmDiag.Log("[Touch] AltTouchChanged failed: " + e.Message);
            }
            if (City.Diagnostics.SessionTelemetry.Instance != null)
                City.Diagnostics.SessionTelemetry.Instance.Mark("touch=%s", on ? "zone-zero" : "huntix");
            // Riflesso della caccia alle uova: la prima attivazione del
            // joystick alternativo nasconde un uovo nel punto dov'è il player.
            if (on && Game.Instance != null) Game.Instance.OnAlternateTouchEnabled();
        }

        public Vector2 Value { get { return smoothed; } }

        private static bool MapOpen()
        {
            return MapSelectUI.Instance != null && MapSelectUI.Instance.IsOpen;
        }

        public void Configure(RectTransform root, RectTransform baseRt, RectTransform handleRt, float r)
        {
            canvasRoot = root;
            joystickBase = baseRt;
            joystickHandle = handleRt;
            radius = Mathf.Max(1f, r);
        }

        private void Update()
        {
            // Smoothing esponenziale indipendente dal frame rate: lauita' del
            // gesto resta la stessa a 30, 60 o 120 fps.
            float k = 1f - Mathf.Exp(-Time.unscaledDeltaTime / SmoothTime);
            smoothed = Vector2.Lerp(smoothed, active ? target : Vector2.zero, Mathf.Clamp01(k));
        }

        public void OnPointerDown(PointerEventData eventData)
        {
            if (!Enabled || MapOpen()) return;
            active = true;
            RectTransformUtility.ScreenPointToLocalPointInRectangle(
                canvasRoot, eventData.position, eventData.pressEventCamera, out origin);
            target = Vector2.zero;
            if (joystickBase != null)
            {
                joystickBase.anchoredPosition = origin;
                joystickBase.gameObject.SetActive(true);
            }
            if (joystickHandle != null) joystickHandle.localPosition = Vector3.zero;
        }

        public void OnDrag(PointerEventData eventData)
        {
            if (!active) return;
            if (MapOpen()) { Reset(); return; }
            RectTransformUtility.ScreenPointToLocalPointInRectangle(
                canvasRoot, eventData.position, eventData.pressEventCamera, out Vector2 pos);
            Vector2 delta = pos - origin;
            if (delta.magnitude > radius) delta = delta.normalized * radius;
            if (joystickHandle != null) joystickHandle.localPosition = delta;
            target = delta / radius;
        }

        public void OnPointerUp(PointerEventData eventData)
        {
            Reset();
        }

        private void Reset()
        {
            active = false;
            target = Vector2.zero;
            if (joystickBase != null) joystickBase.gameObject.SetActive(false);
            if (joystickHandle != null) joystickHandle.localPosition = Vector3.zero;
        }

        private void OnDisable()
        {
            Reset();
            smoothed = Vector2.zero;
        }
    }
}
