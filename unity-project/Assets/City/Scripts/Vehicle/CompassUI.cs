using UnityEngine;
using UnityEngine.UI;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Bussola contestuale in basso al centro dello schermo: le frecce per
    /// categoria (dealer/repair/garage) si mostrano SOLO durante la
    /// navigazione esplicita impostata dall.utente (NavigationState),
    /// puntate verso il POI piu.vicino di quella categoria. Filtro
    /// contestuale:
    ///  - concessionaria: sempre visibile (se c.e una destinazione)
    ///  - officina: solo se un mio veicolo e' sotto REPAIR_VIS_THRESHOLD
    ///  - garage: solo se possiedo almeno un veicolo
    /// Voci nascoste quando il target e' a meno di HideMeters (c'e' gia'
    /// il prompt di interazione) e la bussola intera dentro gli interni.
    /// </summary>
    public class CompassUI : MonoBehaviour
    {
        public static CompassUI Instance { get; private set; }

        private const float HideMeters = 25f;
        public const float RepairVisThreshold = 60f;

        private const float RingRadiusPx = 150f;
        private const float RefreshSec = 0.15f;

        // palette condivisa con i marker minimappa
        public static readonly Color ColDealer = new Color(0.31f, 0.76f, 0.97f);
        public static readonly Color ColRepair = new Color(1.00f, 0.72f, 0.30f);
        public static readonly Color ColGarage = new Color(0.51f, 0.78f, 0.52f);
        public static readonly Color ColHospital = new Color(0.95f, 0.30f, 0.30f);
        public static readonly Color ColSchool = new Color(0.62f, 0.50f, 0.94f);
        public static readonly Color ColBar = new Color(0.95f, 0.45f, 0.62f);
        public static readonly Color ColBank = new Color(1.00f, 0.84f, 0.30f);
        // freccia della destinazione scelta (rossa, "freccina rossa" della UX)
        public static readonly Color ColDest = new Color(0.95f, 0.13f, 0.10f);
        // freccia del lavoro attivo (ciano come il beacon dei lavori)
        public static readonly Color ColJob = new Color(0.20f, 0.85f, 0.95f);

        /// <summary>Target (posizione world) del lavoro attivo: lo imposta
        /// JobManager a ogni step, la bussola la punta con la freccia ciano.</summary>
        public static Vector3? JobTarget;

        /// <summary>Etichetta del lavoro mostrata sotto la freccia (es. TAXI).</summary>
        public static string JobLabel = "";

        /// <summary>Colore UI associato a un tipo POI (stringa wire).</summary>
        public static Color KindColor(string kindStr)
        {
            switch (kindStr)
            {
                case "dealer": return ColDealer;
                case "repair": return ColRepair;
                case "hospital": return ColHospital;
                case "school": return ColSchool;
                case "bar": return ColBar;
                case "bank": return ColBank;
                case "rampa": return new Color(0.18f, 0.22f, 0.28f);
                default: return ColGarage;
            }
        }

        /// <summary>Colore della freccia di destinazione: segue il colore della
        /// categoria POI selezionata (concessionaria azzurra, officina
        /// arancione, ...) oppure resta rosso (ColDest) per destinazioni
        /// generiche o da missione senza categoria.</summary>
        public static Color DestColor(string kind)
        {
            if (string.IsNullOrEmpty(kind)) return ColDest;
            switch (kind)
            {
                case "dealer": return ColDealer;
                case "repair": return ColRepair;
                case "hospital": return ColHospital;
                case "school": return ColSchool;
                case "bar": return ColBar;
                case "bank": return ColBank;
                case "rampa": return new Color(0.18f, 0.22f, 0.28f);
                case "garage": return ColGarage;
                default: return ColDest;
            }
        }

        private class Entry
        {
            public VehiclePoiZone.PoiKind kind;
            public string kindStr;
            public Image arrow;
            public Text label;
            public bool contextOk;
        }

        private Entry[] entries;
        private Image destArrow;
        private Text destLabel;
        private Image jobArrow;
        private Text jobLabel;
        private RectTransform ringRoot;
        private Canvas canvas;
        private float next;

        public static void Create()
        {
            if (GameObject.Find("CompassUI") != null) return;
            var go = new GameObject("CompassUI", typeof(CompassUI));
            DontDestroyOnLoad(go);
        }

        private void Awake()
        {
            Instance = this;
            var canvasGo = new GameObject("CompassCanvas", typeof(Canvas));
            canvasGo.transform.SetParent(transform, false);
            canvas = canvasGo.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;

            ringRoot = canvasGo.GetComponent<RectTransform>();
            ringRoot.anchorMin = new Vector2(0.5f, 0f);
            ringRoot.anchorMax = new Vector2(0.5f, 0f);
            ringRoot.pivot = new Vector2(0.5f, 0f);
            ringRoot.anchoredPosition = new Vector2(0f, 26f);
            ringRoot.sizeDelta = new Vector2(10f, 10f);

            Sprite tri = MakeTriangleSprite();
            entries = new Entry[]
            {
                MakeEntry(VehiclePoiZone.PoiKind.Dealer, "dealer",
                    ColDealer, tri, new Vector2(-64f, 0f)),
                MakeEntry(VehiclePoiZone.PoiKind.Repair, "repair",
                    ColRepair, tri, Vector2.zero),
                MakeEntry(VehiclePoiZone.PoiKind.Garage, "garage",
                    ColGarage, tri, new Vector2(64f, 0f)),
            };

            var dgo = new GameObject("Freccia_Dest", typeof(Image));
            dgo.transform.SetParent(ringRoot, false);
            var dimg = dgo.GetComponent<Image>();
            dimg.sprite = tri;
            dimg.color = ColDest;
            dimg.raycastTarget = false;
            var drt = dimg.rectTransform;
            drt.sizeDelta = new Vector2(44f, 44f);
            drt.anchoredPosition = new Vector2(0f, RingRadiusPx + 52f);

            var dl = new GameObject("Dist", typeof(Text));
            dl.transform.SetParent(dgo.transform, false);
            destLabel = dl.GetComponent<Text>();
            destLabel.font = UiFont();
            destLabel.fontSize = 24;
            destLabel.color = ColDest;
            destLabel.alignment = TextAnchor.LowerCenter;
            destLabel.raycastTarget = false;
            var dlrt = destLabel.rectTransform;
            dlrt.anchorMin = new Vector2(0f, 1f);
            dlrt.anchorMax = new Vector2(0f, 1f);
            dlrt.pivot = new Vector2(0.5f, 0f);
            dlrt.anchoredPosition = new Vector2(0f, 4f);
            dlrt.sizeDelta = new Vector2(260f, 30f);

            destArrow = dimg;

            // freccia del lavoro attivo (ciano): indica dove si trova il
            // punto obiettivo corrente quando un lavoro e in corso
            var jgo = new GameObject("Freccia_Lavoro", typeof(Image));
            jgo.transform.SetParent(ringRoot, false);
            var jimg = jgo.GetComponent<Image>();
            jimg.sprite = tri;
            jimg.color = ColJob;
            jimg.raycastTarget = false;
            var jrt = jimg.rectTransform;
            jrt.sizeDelta = new Vector2(42f, 42f);
            jrt.anchoredPosition = new Vector2(0f, RingRadiusPx + 52f);

            var jl = new GameObject("Dist", typeof(Text));
            jl.transform.SetParent(jgo.transform, false);
            jobLabel = jl.GetComponent<Text>();
            jobLabel.font = UiFont();
            jobLabel.fontSize = 24;
            jobLabel.color = ColJob;
            jobLabel.alignment = TextAnchor.LowerCenter;
            jobLabel.raycastTarget = false;
            var jlrt = jobLabel.rectTransform;
            jlrt.anchorMin = new Vector2(0f, 1f);
            jlrt.anchorMax = new Vector2(0f, 1f);
            jlrt.pivot = new Vector2(0.5f, 0f);
            jlrt.anchoredPosition = new Vector2(0f, 4f);
            jlrt.sizeDelta = new Vector2(380f, 30f);

            jobArrow = jimg;
        }

        private Entry MakeEntry(VehiclePoiZone.PoiKind kind, string kindStr,
            Color col, Sprite tri, Vector2 offset)
        {
            var go = new GameObject("Freccia_" + kindStr, typeof(Image));
            go.transform.SetParent(ringRoot, false);
            var img = go.GetComponent<Image>();
            img.sprite = tri;
            img.color = col;
            img.raycastTarget = false;
            var rt = img.rectTransform;
            rt.sizeDelta = new Vector2(34f, 34f);
            rt.anchoredPosition = offset;

            var lgo = new GameObject("Dist", typeof(Text));
            lgo.transform.SetParent(go.transform, false);
            var txt = lgo.GetComponent<Text>();
            txt.font = UiFont();
            txt.fontSize = 22;
            txt.color = new Color(0.92f, 0.94f, 0.96f);
            txt.alignment = TextAnchor.LowerCenter;
            txt.raycastTarget = false;
            var lrt = txt.rectTransform;
            lrt.anchorMin = new Vector2(0f, 1f);
            lrt.anchorMax = new Vector2(0f, 1f);
            lrt.pivot = new Vector2(0.5f, 0f);
            lrt.anchoredPosition = new Vector2(0f, 4f);
            lrt.sizeDelta = new Vector2(140f, 30f);

            return new Entry { kind = kind, kindStr = kindStr,
                arrow = img, label = txt };
        }

        private void Update()
        {
            if (Time.unscaledTime < next) return;
            next = Time.unscaledTime + RefreshSec;
            Refresh();
        }

        private void Refresh()
        {
            var game = Game.Instance;
            bool inInterior = game != null && game.IsInInterior;

            bool jobActive = JobTarget.HasValue;
            // Una sola freccia per volta: quella della destinazione scelta,
            // colorata in base alla categoria del POI selezionato (concessio-
            // naria azzurra, officina arancione, ...). Le frecce della cate-
            // goria piu. vicina non esistono piu.: per avere una freccia si
            // seleziona esplicitamente un POI (menu hamburger o mappa), che
            // attiva la sua freccia relativa con il colore apposito.
            // Durante un lavoro attivo la freccia ciano dedicata ha priorita.
            UpdateDest(inInterior, jobActive);
            UpdateJob(inInterior);
            ringRoot.gameObject.SetActive(
                destArrow != null && destArrow.gameObject.activeSelf);
        }

        /// <summary>
        /// Freccia dedicata alla destinazione scelta: resta sopra l'anello,
        /// punta sempre verso di essa, si spegne all'arrivo o negli interni.
        /// </summary>
        private void UpdateDest(bool inInterior, bool jobActive = false)
        {
            if (destArrow == null) return;
            var d = NavigationState.Current;
            Transform player = PlayerTransform();
            if (d == null || player == null || inInterior || jobActive)
            {
                destArrow.gameObject.SetActive(false);
                return;
            }
            Vector3 dw = d.WorldPos;
            Vector3 diff = dw - player.position;
            diff.y = 0f;
            float dist = diff.magnitude;
            if (dist < NavigationState.ArriveMeters)
            {
                NavigationState.Clear();
                destArrow.gameObject.SetActive(false);
                var g = Game.Instance;
                if (g != null && g.ui != null)
                    g.ui.ShowToast("Arrivato: " + d.name);
                return;
            }

            float camYaw = 0f;
            var cam = Camera.main;
            if (cam != null) camYaw = cam.transform.eulerAngles.y;
            float bearing = Mathf.Atan2(diff.x, diff.z) * Mathf.Rad2Deg;
            float rel = Mathf.DeltaAngle(camYaw, bearing);

            var art = destArrow.rectTransform;
            art.anchoredPosition = new Vector2(
                Mathf.Sin(rel * Mathf.Deg2Rad) * RingRadiusPx,
                Mathf.Cos(rel * Mathf.Deg2Rad) *
                    (RingRadiusPx + 52f));
            art.localRotation = Quaternion.Euler(0f, 0f, -rel);

            // colore apposito: la freccia della destinazione prende il colore
            // della categoria del POI selezionato (rosso per quelle generiche)
            Color destCol = DestColor(d.kind);
            destArrow.color = destCol;
            if (destLabel != null) destLabel.color = destCol;

            destLabel.text = FormatDist(dist) + " - " + d.name;
            destArrow.gameObject.SetActive(true);
        }

        /// <summary>
        /// Freccia del lavoro attivo (stessa meccanica della freccia POI):
        /// punta al punto obiettivo corrente del job e mostra la distanza.
        /// </summary>
        private void UpdateJob(bool inInterior)
        {
            if (jobArrow == null) return;
            if (jobArrow.gameObject.activeSelf) jobArrow.gameObject.SetActive(false);
            if (!JobTarget.HasValue) return;
            if (inInterior) return;
            Transform player = PlayerTransform();
            if (player == null) return;

            Vector3 tw = JobTarget.Value;
            Vector3 diff = tw - player.position;
            diff.y = 0f;
            float dist = diff.magnitude;

            float camYaw = 0f;
            var cam = Camera.main;
            if (cam != null) camYaw = cam.transform.eulerAngles.y;
            float bearing = Mathf.Atan2(diff.x, diff.z) * Mathf.Rad2Deg;
            float rel = Mathf.DeltaAngle(camYaw, bearing);

            var art = jobArrow.rectTransform;
            art.anchoredPosition = new Vector2(
                Mathf.Sin(rel * Mathf.Deg2Rad) * RingRadiusPx,
                Mathf.Cos(rel * Mathf.Deg2Rad) * (RingRadiusPx + 52f));
            art.localRotation = Quaternion.Euler(0f, 0f, -rel);

            jobLabel.text = FormatDist(dist) +
                (string.IsNullOrEmpty(JobLabel) ? "" : " - " + JobLabel);
            jobArrow.gameObject.SetActive(true);
        }

        /// <summary>Filtro contestuale per categoria.</summary>
        public static bool ContextAllows(VehiclePoiZone.PoiKind kind)
        {
            switch (kind)
            {
                case VehiclePoiZone.PoiKind.Garage:
                    return VehicleOwnershipApi.HasOwnedCached();
                case VehiclePoiZone.PoiKind.Repair:
                    return VehicleOwnershipApi.HasOwnedCached() &&
                        VehicleOwnershipApi.WorstConditionCached()
                            < RepairVisThreshold;
                default:
                    return true;
            }
        }

        private bool PointAtNearest(Entry e)
        {
            Transform player = PlayerTransform();
            if (player == null) { SetVisible(e, false); return false; }

            GeoCoord pg = WorldOrigin.ToGeo(player.position);
            var poi = VehiclePoiRegistry.Nearest(e.kindStr, pg.lat, pg.lng);
            if (poi == null) { SetVisible(e, false); return false; }

            Vector3 tp = WorldOrigin.ToWorld(poi.lat, poi.lng);
            Vector3 d = tp - player.position;
            d.y = 0f;
            float dist = d.magnitude;
            if (dist < HideMeters) { SetVisible(e, false); return false; }

            float camYaw = 0f;
            var cam = Camera.main;
            if (cam != null) camYaw = cam.transform.eulerAngles.y;

            float bearing = Mathf.Atan2(d.x, d.z) * Mathf.Rad2Deg;
            float rel = Mathf.DeltaAngle(camYaw, bearing);

            var art = e.arrow.rectTransform;
            art.anchoredPosition = new Vector2(
                Mathf.Sin(rel * Mathf.Deg2Rad) * RingRadiusPx,
                Mathf.Cos(rel * Mathf.Deg2Rad) * RingRadiusPx);
            art.localRotation = Quaternion.Euler(0f, 0f, -rel);

            e.label.text = FormatDist(dist);
            SetVisible(e, true);
            return true;
        }

        private static Transform PlayerTransform()
        {
            // priorita' a PlayerController (il transform del player):
            // Manager.target e' tipicamente la camera, non il player,
            // e la bussola deve puntare rispetto alla posizione del player
            var pc = City.Player.PlayerController.Instance;
            if (pc != null && pc.transform != null) return pc.transform;
            var mgr = City.OSM.CityChunkedWorld.Instance != null
                ? City.OSM.CityChunkedWorld.Instance.Manager : null;
            return mgr != null ? mgr.target : null;
        }

        internal static string FormatDist(float m)
        {
            if (m < 1000f) return Mathf.RoundToInt(m) + " m";
            return (m / 1000f).ToString("F1") + " km";
        }

        private static void SetVisible(Entry e, bool v)
        {
            if (e.arrow != null) e.arrow.gameObject.SetActive(v);
        }

        private static Sprite MakeTriangleSprite()
        {
            const int S = 32;
            var tex = new Texture2D(S, S, TextureFormat.RGBA32, false);
            tex.filterMode = FilterMode.Bilinear;
            var c = new Color32(255, 255, 255, 255);
            var z = new Color32(0, 0, 0, 0);
            var buf = new Color32[S * S];
            for (int y = 0; y < S; y++)
                for (int x = 0; x < S; x++)
                {
                    // triangolo isocele che punta in alto
                    float halfW = (float)(S - y) / S * (S * 0.5f);
                    float cx = S * 0.5f;
                    buf[y * S + x] =
                        (x >= cx - halfW && x <= cx + halfW && y >= 1) ? c : z;
                }
            tex.SetPixels32(buf);
            tex.Apply(false);
            return Sprite.Create(tex, new Rect(0f, 0f, S, S),
                new Vector2(0.5f, 0.5f), S);
        }

        internal static Font UiFont()
        {
            try { return Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf"); }
            catch { }
            try { return Resources.GetBuiltinResource<Font>("Arial.ttf"); }
            catch { }
            return null;
        }
    }
}
