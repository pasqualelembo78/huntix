using UnityEngine;
using UnityEngine.UI;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Cartello stradale su un POI veicoli: mostra il nome della struttura e
    /// la distanza del POI piu' vicino delle ALTRE categorie (come i segnali
    /// reali lungo strada). Il testo si aggiorna poche volte dopo la
    /// creazione (il registro si riempie man mano che i chunk vicini vengono
    /// costruiti) poi si ferma: costo praticamente nullo.
    /// </summary>
    public class PoiSignpost : MonoBehaviour
    {
        private string kind;
        private string excludeId;
        private string title;
        private Text label;
        private readonly float[] refreshAt = { 5f, 20f, 60f };
        private int nextIdx;
        private float born;

        public void Setup(string kindStr, string id, string displayName)
        {
            kind = kindStr;
            excludeId = id;
            title = string.IsNullOrEmpty(displayName)
                ? Caption(kindStr) : displayName;
            born = Time.unscaledTime;
        }

        private void Start()
        {
            BuildCanvas();
            UpdateText();
        }

        private void Update()
        {
            if (nextIdx >= refreshAt.Length)
            {
                enabled = false;
                return;
            }
            if (Time.unscaledTime - born >= refreshAt[nextIdx])
            {
                nextIdx++;
                UpdateText();
            }
        }

        private void BuildCanvas()
        {
            var go = new GameObject("TestoCartello", typeof(Canvas));
            go.transform.SetParent(transform, false);
            var cv = go.GetComponent<Canvas>();
            cv.renderMode = RenderMode.WorldSpace;

            var rt = go.GetComponent<RectTransform>();
            rt.sizeDelta = new Vector2(2.5f, 1.05f);
            rt.localPosition = new Vector3(0f, 0f, -0.09f);

            var lgo = new GameObject("T", typeof(Text));
            lgo.transform.SetParent(go.transform, false);
            label = lgo.GetComponent<Text>();
            label.font = CompassUI.UiFont();
            label.fontSize = 34;
            label.alignment = TextAnchor.MiddleCenter;
            label.color = new Color(0.06f, 0.07f, 0.09f);
            label.text = "";
            var lrt = label.rectTransform;
            lrt.anchorMin = Vector2.zero;
            lrt.anchorMax = Vector2.one;
            lrt.sizeDelta = Vector2.zero;
        }

        private void UpdateText()
        {
            if (label == null || string.IsNullOrEmpty(kind)) return;

            var sb = new System.Text.StringBuilder();
            if (!string.IsNullOrEmpty(title)) sb.AppendLine(title);

            Append(sb, "dealer");
            Append(sb, "repair");
            Append(sb, "garage");
            Append(sb, "hospital");
            Append(sb, "school");
            Append(sb, "bar");

            label.text = sb.ToString().TrimEnd();
        }

        private void Append(System.Text.StringBuilder sb, string otherKind)
        {
            if (otherKind == kind) return;
            GeoCoord g = WorldOrigin.ToGeo(transform.position);
            var p = VehiclePoiRegistry.NearestExcept(otherKind, excludeId,
                g.lat, g.lng);
            if (p == null) return;
            sb.Append(Caption(otherKind)).Append(' ')
              .AppendLine(CompassUI.FormatDist(DistanceTo(p.lat, p.lng)));
        }

        private float DistanceTo(double lat, double lng)
        {
            Vector3 w = WorldOrigin.ToWorld(lat, lng);
            return Vector3.Distance(w, transform.position);
        }

        internal static string Caption(string kindStr)
        {
            switch (kindStr)
            {
                case "dealer": return "CONCESSIONARIA";
                case "repair": return "OFFICINA";
                case "hospital": return "OSPEDALE";
                case "rampa": return "SOTTERRANEO";
                case "school": return "SCUOLA";
                case "bar": return "BAR";
                case "bank": return "BANCA";
                default: return "GARAGE";
            }
        }
    }
}
