using System.Globalization;
using UnityEngine;
using UnityEngine.UI;
using TMPro;
using City.Interior;
using City.OSM;
using City.Player;
using City.Vehicle;
using Huntix.Bridge;

namespace City.AR
{
    /// <summary>
    /// Realtà Aumentata — pulsante "PASSA A AR" sempre visibile in città.
    ///
    /// Al tap invia ad Android un ArCityRequest con la posizione VIRTUALE
    /// corrente del player (WorldOrigin, mai il GPS reale) insieme ai dati
    /// del POI raggiunto con il joystick. Android apre la fotocamera reale,
    /// rileva una superficie piana (ARCore) e sopra la vita reale costruisce
    /// la mini-città OSM del punto: edifici, uova e personaggio.
    ///
    /// Non è una realtà virtuale: è una vera visualizzazione AR con la camera
    /// del telefono. Il pulsante sta in basso, sotto il menu azioni.
    /// </summary>
    public class ArCityController : MonoBehaviour
    {
        public static ArCityController Instance { get; private set; }

        /// Raggio di ricerca del POI attorno al player (metri).
        public float detectRadius = 12f;
        /// Intervallo fra due scansioni dei POI (secondi).
        public float scanInterval = 0.4f;
        /// Secondi di pausa dopo l'invio (mentre Android apre la camera).
        public float reopenDelay = 2.5f;
        /// Raggio (metri) entro cui spedire ad Android le uova presenti in
        /// citta': l'AR le mostrera' nella stessa posizione virtuale (scala
        /// mondo 1:100). Coerente con WORLD_RADIUS_M (220 m) di ArCityActivity.
        public float eggSendRadius = 220f;

        private GameObject buttonGo;
        private Button button;
        private Image buttonBg;
        private TextMeshProUGUI buttonText;
        private BuildingEntrance nearEntrance;
        private VehiclePoiZone nearPoiZone;
        private float lastScan = -10f;
        private float hideUntil = -1f;
        private bool visible;
        private string lastLabel = "";

        public static void Ensure()
        {
            if (Instance != null) return;
            var go = new GameObject("ArCityController");
            DontDestroyOnLoad(go);
            go.AddComponent<ArCityController>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
        }

        private void Start()
        {
            BuildButton();
        }

        private void OnDestroy()
        {
            if (Instance == this) Instance = null;
        }

        private void Update()
        {
            if (Time.time >= lastScan + scanInterval)
            {
                lastScan = Time.time;
                Scan();
            }
            if (hideUntil > 0f && Time.time >= hideUntil) hideUntil = -1f;
            RefreshVisibility();
        }

        // -- rilevamento POI -------------------------------------------

        private void Scan()
        {
            nearEntrance = null;
            nearPoiZone = null;

            var game = Game.Instance;
            var player = PlayerController.Instance;
            if (game == null || player == null) return;
            if (game.IsDriving || game.IsInInterior) return;

            // 1) focus corrente (soglia della porta / zona POI sotto i piedi).
            if (game.CurrentEntrance != null) nearEntrance = game.CurrentEntrance;
            if (game.CurrentPoiZone != null) nearPoiZone = game.CurrentPoiZone;
            if (nearEntrance != null || nearPoiZone != null) return;

            // 2) scansione fisica: i trigger delle porte sono stretti, quindi
            //    cerchiamo il POI piu' vicino attorno al player.
            Vector3 p = player.transform.position;
            Collider[] cols = Physics.OverlapSphere(p, detectRadius, ~0,
                QueryTriggerInteraction.Collide);
            float best = float.MaxValue;
            for (int i = 0; i < cols.Length; i++)
            {
                Collider col = cols[i];
                if (col == null) continue;
                float d = Vector3.Distance(p, col.transform.position);
                if (d >= best) continue;

                var e = col.GetComponent<BuildingEntrance>();
                if (e != null)
                {
                    best = d; nearEntrance = e; nearPoiZone = null;
                    continue;
                }
                var z = col.GetComponent<VehiclePoiZone>();
                if (z != null)
                {
                    best = d; nearEntrance = null; nearPoiZone = z;
                }
            }
        }

        private bool ShouldShow()
        {
            if (hideUntil > 0f) return false;
            var game = Game.Instance;
            if (game == null) return false;
            if (game.IsDriving || game.IsInInterior) return false;
            // Il pulsante è sempre disponibile in città, anche lontano da un
            // POI: apre l'AR sul punto virtuale in cui si trova il player.
            return true;
        }

        // -- HUD -------------------------------------------------------

        private void BuildButton()
        {
            var canvas = GameObject.FindObjectOfType<Canvas>();
            if (canvas == null) return;

            buttonGo = new GameObject("ArCityButton", typeof(RectTransform));
            var rt = buttonGo.GetComponent<RectTransform>();
            rt.SetParent(canvas.transform, false);
            rt.anchorMin = rt.anchorMax = rt.pivot = new Vector2(0.5f, 0f);
            rt.anchoredPosition = new Vector2(0f, 105f);
            rt.sizeDelta = new Vector2(250f, 54f);

            buttonBg = buttonGo.AddComponent<Image>();
            buttonBg.color = new Color(0.10f, 0.62f, 0.82f, 0.95f);
            buttonBg.raycastTarget = true;

            button = buttonGo.AddComponent<Button>();
            button.onClick.AddListener(OnArPressed);

            var txtGo = new GameObject("Txt", typeof(RectTransform));
            var trt = txtGo.GetComponent<RectTransform>();
            trt.SetParent(buttonGo.transform, false);
            trt.anchorMin = Vector2.zero;
            trt.anchorMax = Vector2.one;
            trt.offsetMin = Vector2.zero;
            trt.offsetMax = Vector2.zero;
            buttonText = txtGo.AddComponent<TextMeshProUGUI>();
            buttonText.text = "PASSA A AR";
            buttonText.fontSize = 22f;
            buttonText.alignment = TextAlignmentOptions.Center;
            buttonText.color = Color.white;
            buttonText.raycastTarget = false;
            var font = TMP_Settings.defaultFontAsset;
            if (font == null)
                font = Resources.Load<TMP_FontAsset>("Fonts & Materials/LiberationSans SDF");
            buttonText.font = font;

            buttonGo.SetActive(false);
        }

        private void RefreshVisibility()
        {
            if (buttonGo == null) return;
            bool show = ShouldShow();
            if (show != visible)
            {
                visible = show;
                buttonGo.SetActive(show);
            }
            if (!show) return;
            string label = Label();
            if (label != lastLabel)
            {
                lastLabel = label;
                buttonText.text = label;
            }
        }

        private string Label()
        {
            if (nearEntrance != null)
            {
                string n = string.IsNullOrEmpty(nearEntrance.buildingName)
                    ? "PASSA A AR" : "PASSA A AR: " + nearEntrance.buildingName;
                return n;
            }
            if (nearPoiZone != null)
            {
                string n = string.IsNullOrEmpty(nearPoiZone.poiName)
                    ? "PASSA A AR" : "PASSA A AR: " + nearPoiZone.poiName;
                return n;
            }
            return "PASSA A AR";
        }

        // -- attivazione ----------------------------------------------

        private void OnArPressed()
        {
            var player = PlayerController.Instance;
            if (player == null || !WorldOrigin.Initialized) return;

            // Posizione VIRTUALE corrente del player (mai il GPS reale).
            GeoCoord g = WorldOrigin.ToGeo(player.transform.position);

            string poiId = "";
            string poiName = "Luogo";
            string poiType = "luogo";
            double poiLat = g.lat;
            double poiLng = g.lng;

            if (nearEntrance != null)
            {
                poiName = string.IsNullOrEmpty(nearEntrance.buildingName)
                    ? "Edificio" : nearEntrance.buildingName;
                poiType = string.IsNullOrEmpty(nearEntrance.buildingType)
                    ? "house" : nearEntrance.buildingType;
                poiId = "entrance";
                GeoCoord eg = WorldOrigin.ToGeo(nearEntrance.transform.position);
                poiLat = eg.lat;
                poiLng = eg.lng;
            }
            else if (nearPoiZone != null)
            {
                poiName = string.IsNullOrEmpty(nearPoiZone.poiName)
                    ? nearPoiZone.DefaultName() : nearPoiZone.poiName;
                poiType = nearPoiZone.kind.ToString().ToLowerInvariant();
                poiId = nearPoiZone.poiId;
                GeoCoord zg = WorldOrigin.ToGeo(nearPoiZone.transform.position);
                poiLat = zg.lat;
                poiLng = zg.lng;
            }

            string json = "{" +
                "\"lat\":" + F(g.lat) + ",\"lng\":" + F(g.lng) + "," +
                "\"poiLat\":" + F(poiLat) + ",\"poiLng\":" + F(poiLng) + "," +
                "\"poiId\":\"" + Esc(poiId) + "\"," +
                "\"poiName\":\"" + Esc(poiName) + "\"," +
                "\"poiType\":\"" + Esc(poiType) + "\"," +
                "\"eggs\":" + EggJson(g) + "}";

            UnityBridge.SendMessageToAndroid("ArCityRequest", json);

            var ui = Game.Instance != null ? Game.Instance.ui : null;
            if (ui != null) ui.ShowToast("Apro la camera AR (inquadra un piano)...");

            hideUntil = Time.time + reopenDelay;
            visible = true;
            if (buttonGo != null) buttonGo.SetActive(false);
            if (button != null) button.interactable = false;
        }

        /// <summary>Uova presenti nel raggio eggSendRadius attorno al player,
        /// convertite in coordinate geografiche (WorldOrigin). Ad Android arriva
        /// l'array {lat,lng,rarity,type} cosi' l'AR le posiziona nello stesso
        /// punto virtuale della citta'.</summary>
        private string EggJson(GeoCoord playerGeo)
        {
            var city = City.Economy.EggSpawnManager.Instance;
            if (city == null) return "[]";
            var playerCtrl = PlayerController.Instance;
            if (playerCtrl == null) return "[]";

            var sb = new System.Text.StringBuilder();
            sb.Append("[");
            bool first = true;
            Vector3 p = playerCtrl.transform.position;
            var eggs = city.ActiveEggs;
            for (int i = 0; i < eggs.Count; i++)
            {
                GameObject e = eggs[i];
                if (e == null) continue;
                if (Vector3.Distance(p, e.transform.position) > eggSendRadius) continue;
                var ec = e.GetComponent<City.Economy.EggController>();
                if (ec == null) continue;
                GeoCoord eggGeo = WorldOrigin.ToGeo(e.transform.position);
                if (!first) sb.Append(",");
                first = false;
                sb.Append("{\"lat\":" + F(eggGeo.lat) + ",\"lng\":" + F(eggGeo.lng) +
                          ",\"rarity\":" + (int)ec.rarity +
                          ",\"type\":" + (int)ec.eggType + "}");
            }
            sb.Append("]");
            return sb.ToString();
        }

        private static string F(double v)
        {
            return v.ToString("0.0000000",
                System.Globalization.CultureInfo.InvariantCulture);
        }

        private static string Esc(string s)
        {
            if (string.IsNullOrEmpty(s)) return "";
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"");
        }
    }
}