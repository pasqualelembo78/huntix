using System.Globalization;
using System.Text.RegularExpressions;
using UnityEngine;
using UnityEngine.UI;

namespace City.OSM
{
    /// <summary>
    /// HUD diagnostico GPS/mondo per la verifica sul campo. Mostra in tempo
    /// reale: coordinate GPS del device (con accuratezza), la coordinata
    /// geografica derivata dalla posizione mondo del player e lo yaw camera.
    /// Come usarlo: in piedi davanti a casa, confronta "GPS device" con le
    /// coordinate del tuo navigatore.
    ///   - GPS sbagliato vs realta'  -> problema pipeline GPS (seed stantio,
    ///     mock mode, precisione)
    ///   - GPS giusto ma "Player->geo" diverso -> bug di posizionamento player
    ///   - entrambi giusti ma strade diverse -> problema rendering/dati tile
    /// </summary>
    public class GpsDebugHud : MonoBehaviour
    {
        private const float PollSeconds = 0.5f;

        private Text _label;
        private float _nextPoll;
        private double _gpsLat, _gpsLng;
        private float _gpsAcc = -1f;
        private bool _gpsOk;

        public static void Create()
        {
            var go = new GameObject("GpsDebugHud", typeof(GpsDebugHud));
            DontDestroyOnLoad(go);
        }

        private void Awake()
        {
            var canvasGo = new GameObject("GpsHudCanvas", typeof(Canvas));
            canvasGo.transform.SetParent(transform, false);
            canvasGo.GetComponent<Canvas>().renderMode =
                RenderMode.ScreenSpaceOverlay;

            var textGo = new GameObject("Testo", typeof(Text));
            textGo.transform.SetParent(canvasGo.transform, false);
            _label = textGo.GetComponent<Text>();
            _label.font = UiFont();
            if (_label.font == null) return;
            _label.fontSize = 30;
            _label.color = Color.white;
            _label.alignment = TextAnchor.UpperLeft;

            RectTransform rt = _label.rectTransform;
            rt.anchorMin = new Vector2(0f, 1f);
            rt.anchorMax = new Vector2(0f, 1f);
            rt.pivot = new Vector2(0f, 1f);
            rt.anchoredPosition = new Vector2(12f, -8f);
            rt.sizeDelta = new Vector2(1100f, 260f);
        }

        private void Update()
        {
            if (Time.unscaledTime >= _nextPoll)
            {
                _nextPoll = Time.unscaledTime + PollSeconds;
                PollGps();
            }
            Refresh();
        }

        private static Font UiFont()
        {
            try { return Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf"); }
            catch { }
            try { return Resources.GetBuiltinResource<Font>("Arial.ttf"); }
            catch { }
            return null;
        }

        private void PollGps()
        {
            _gpsOk = false;
            try
            {
                string json = Huntix.Bridge.UnityBridge.GetCurrentLocation();
                if (string.IsNullOrEmpty(json)) return;
                bool latOk = false, lngOk = false;
                Match m = Regex.Match(json, "\"lat\"\\s*:\\s*(-?[0-9.eE+-]+)");
                if (m.Success)
                    latOk = double.TryParse(m.Groups[1].Value, NumberStyles.Float,
                        CultureInfo.InvariantCulture, out _gpsLat);
                m = Regex.Match(json, "\"lng\"\\s*:\\s*(-?[0-9.eE+-]+)");
                if (m.Success)
                    lngOk = double.TryParse(m.Groups[1].Value, NumberStyles.Float,
                        CultureInfo.InvariantCulture, out _gpsLng);
                m = Regex.Match(json, "\"acc\"\\s*:\\s*(-?[0-9.eE+-]+)");
                float acc;
                if (m.Success && float.TryParse(m.Groups[1].Value,
                        NumberStyles.Float, CultureInfo.InvariantCulture, out acc))
                    _gpsAcc = acc;
                _gpsOk = latOk && lngOk &&
                    System.Math.Abs(_gpsLat) > 0.001 &&
                    System.Math.Abs(_gpsLng) > 0.001;
            }
            catch { }
        }

        private void Refresh()
        {
            if (_label == null || _label.font == null) return;
            var ci = CultureInfo.InvariantCulture;
            string s = _gpsOk
                ? "GPS device : " + _gpsLat.ToString("F6", ci) + ", " +
                  _gpsLng.ToString("F6", ci) + "  (acc " +
                  (_gpsAcc < 0 ? "?" : _gpsAcc.ToString("F0")) + " m)"
                : "GPS device : nessun fix";

            var world = CityChunkedWorld.Instance != null
                ? CityChunkedWorld.Instance.Manager : null;
            Transform t = world != null ? world.target : null;
            if (t != null)
            {
                GeoCoord g = WorldOrigin.ToGeo(t.position);
                s += "\nPlayer->geo: " + g.lat.ToString("F6", ci) + ", " +
                     g.lng.ToString("F6", ci);
                s += "\nWorld      : x=" + t.position.x.ToString("F1", ci) +
                     " z=" + t.position.z.ToString("F1", ci);
                double dLat = (g.lat - _gpsLat) * GeoCoord.MetersPerDegLat;
                double dLng = (g.lng - _gpsLng) *
                    GeoCoord.MetersPerDegLon((g.lat + _gpsLat) * 0.5);
                double dev = System.Math.Sqrt(dLat * dLat + dLng * dLng);
                s += "\nScarto     : " + (_gpsOk
                    ? dev.ToString("F0", ci) + " m"
                    : "n.d.");
            }
            var cam = Camera.main;
            if (cam != null)
                s += "\nYaw camera : " +
                     cam.transform.eulerAngles.y.ToString("F0", ci) + "\u00b0";
            _label.text = s;
        }
    }
}
