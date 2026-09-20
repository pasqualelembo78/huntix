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
    /// AR Virtuale — pulsante "ATTIVA AR" contestuale.
    ///
    /// Quando il player (mosso con il joystick) raggiunge un POI/edificio della
    /// mappa viene mostrato il pulsante. Al tap viene inviata ad Android la
    /// POSIZIONE VIRTUALE del player (WorldOrigin, MAI il GPS reale del
    /// dispositivo) insieme ai dati del POI raggiunto: Android apre la
    /// fotocamera e ancora la scena AR a quella posizione virtuale, senza
    /// richiedere che l'utente sia fisicamente sul posto.
    ///
    /// Separazione delle posizioni:
    ///   - GPS reale      = Bridge.getCurrentLocation() (non toccato qui)
    ///   - posizione virtuale Huntix = WorldOrigin.ToGeo(player.position)
    ///   - posizione AR   = stessa posizione virtuale passata ad Android
    /// </summary>
    public class VirtualArController : MonoBehaviour
    {
        public static VirtualArController Instance { get; private set; }

        /// Raggio di ricerca del POI attorno al player (metri).
        public float detectRadius = 12f;
        /// Intervallo fra due scansioni dei POI (secondi).
        public float scanInterval = 0.4f;
        /// Secondi di pausa dopo l'invio (mentre Android apre la camera).
        public float reopenDelay = 2.5f;

        private GameObject buttonGo;
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
            var go = new GameObject("VirtualArController");
            DontDestroyOnLoad(go);
            go.AddComponent<VirtualArController>();
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
            // Ingresso diretto: il pulsante e' sempre disponibile in citta',
            // anche lontano da un POI (apre l'AR sulla posizione virtuale
            // corrente). Se c'e' un POI vicino, cambia solo l'etichetta.
            return true;
        }

        // -- HUD -------------------------------------------------------

        private void BuildButton()
        {
            var canvas = GameObject.FindObjectOfType<Canvas>();
            if (canvas == null) return;

            buttonGo = new GameObject("VirtualArButton", typeof(RectTransform));
            var rt = buttonGo.GetComponent<RectTransform>();
            rt.SetParent(canvas.transform, false);
            rt.anchorMin = rt.anchorMax = rt.pivot = new Vector2(0.5f, 0f);
            rt.anchoredPosition = new Vector2(0f, 210f);
            rt.sizeDelta = new Vector2(250f, 62f);

            buttonBg = buttonGo.AddComponent<Image>();
            buttonBg.color = new Color(0.36f, 0.16f, 0.72f, 0.94f);
            buttonBg.raycastTarget = true;

            var btn = buttonGo.AddComponent<Button>();
            btn.onClick.AddListener(OnArPressed);

            var txtGo = new GameObject("Txt", typeof(RectTransform));
            var trt = txtGo.GetComponent<RectTransform>();
            trt.SetParent(buttonGo.transform, false);
            trt.anchorMin = Vector2.zero;
            trt.anchorMax = Vector2.one;
            trt.offsetMin = Vector2.zero;
            trt.offsetMax = Vector2.zero;
            buttonText = txtGo.AddComponent<TextMeshProUGUI>();
            buttonText.text = "ATTIVA AR";
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
            // Aggiorna l'etichetta solo quando cambia (evita alloc ogni frame).
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
                    ? "Edificio" : nearEntrance.buildingName;
                return "\uD83C\uDF10  AR: " + n;
            }
            if (nearPoiZone != null)
            {
                string n = string.IsNullOrEmpty(nearPoiZone.poiName)
                    ? nearPoiZone.DefaultName() : nearPoiZone.poiName;
                return "\uD83C\uDF10  AR: " + n;
            }
            return "\uD83C\uDF10  AR Virtuale";
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
                "\"poiType\":\"" + Esc(poiType) + "\"}";

            UnityBridge.SendMessageToAndroid("VirtualArRequest", json);

            var ui = Game.Instance != null ? Game.Instance.ui : null;
            if (ui != null) ui.ShowToast("AR Virtuale: apro la fotocamera...");

            // Nasconde il pulsante mentre Android porta in primo piano la
            // camera; riappare al ritorno (il tempo di gioco riprende).
            hideUntil = Time.time + reopenDelay;
            visible = true;
            if (buttonGo != null) buttonGo.SetActive(false);
        }

        private static string F(double v)
        {
            return v.ToString("R", CultureInfo.InvariantCulture);
        }

        private static string Esc(string s)
        {
            if (string.IsNullOrEmpty(s)) return "";
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"");
        }
    }
}
