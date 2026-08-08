using UnityEngine;
using System.Collections.Generic;
using System.Globalization;
using System.Text.RegularExpressions;
using System.Linq;
using Huntix.Bridge;
using Huntix.AR;
using Huntix.Core;

namespace Huntix.Outdoor
{
    /// <summary>
    /// ExploreManager — modulo Esplora (mappa pokemon-go in AR).
    /// Riceve da Android (Overpass) i POI OSM vicino al giocatore (fino a 10 km),
    /// li colloca nel mondo 3D con GeospatialAnchor.GeoWorldOffset e li filtra per
    /// categoria (Tutti / Ristoranti / Bar & Caffè / Supermercati / Negozi & Tabacchi /
    /// Gym & Fitness / Musei & Cultura / Parchi & Natura / Scuole & Istruzione).
    /// Ogni marker tappabile apre la pagina del POI (custom JSON / web / JSON
    /// sintetico OSM costruito da Android).
    /// </summary>
    public class ExploreManager : MonoBehaviour
    {
        public static ExploreManager Instance { get; private set; }

        [Header("POI Settings")]
        public float searchRadiusMeters = 10000f;
        public float reloadDistanceThreshold = 500f;
        public int maxVisiblePois = 400;

        [Header("Prefabs")]
        public GameObject poiMarkerPrefab;
        public Transform markersRoot;

        [System.Serializable]
        public class PoiData
        {
            public string id;
            public string name;
            public double lat;
            public double lng;
            public string buildingType;
            public string poiType;
            public string category;
            public string emoji;
            public string pageType;
            public string url;
            public bool hasCustom;

            [System.NonSerialized] public Vector3 worldPosition;
            [System.NonSerialized] public GameObject markerObject;
        }

        [System.Serializable]
        private class PoiEnvelope
        {
            public PoiData[] pois;
        }

        [System.Serializable]
        public class StoreCategory
        {
            public string label;
            public string emoji;
            public string[] keywords;

            public bool IsAll => keywords == null || keywords.Length == 0;

            public bool Matches(PoiData poi)
            {
                if (IsAll) return true;
                if (string.IsNullOrEmpty(poi.poiType)) return false;
                var v = poi.poiType.ToLowerInvariant();
                var k = poi.buildingType ?? "";
                return keywords.Contains(v) || keywords.Contains(k.ToLowerInvariant());
            }
        }

        public StoreCategory[] categories = new StoreCategory[]
        {
            new StoreCategory { label = "Tutti i locali", emoji = "🏪", keywords = null },
            new StoreCategory { label = "Ristoranti", emoji = "🍝", keywords = new[] {
                "restaurant","ristorante","pub","bistro","fast_food","pizzeria",
                "trattoria","osteria","kebab","doner" }},
            new StoreCategory { label = "Bar & Caffè", emoji = "☕", keywords = new[] {
                "cafe","caffè","coffee","bar","bar_cafe","coffee_house","juice_bar" }},
            new StoreCategory { label = "Supermercati", emoji = "🛒", keywords = new[] {
                "supermarket","supermercato","hypermarket","ipermercato","hyper","mall",
                "wholesale","minimarket","convenience","convenience_store","grocery",
                "general","variety_store","department_store","food_shop","market" }},
            new StoreCategory { label = "Negozi & Tabacchi", emoji = "🛍️", keywords = new[] {
                "tobacco","tabacchi","tabaccheria","kiosk","edicola","newsagent",
                "clothing","apparel","fashion","electronics","computer","books","bookstore",
                "florist","pharmacy","chemist","perfume","alcohol","wine","beer","bakery",
                "panetteria","butcher","greengrocer","confectionery","gelato","gelateria",
                "gift","stationery","tea","jewellery","jewelry","pet",
                "beauty","cosmetics","furniture","hardware","mobile_phone","mobile","bicycle",
                "car","car_repair","laundry","dry_cleaning","tailor","optician","optometrist",
                "money","lottery","video_games","music","photo","sports","outdoors",
                "negozio","shop","barber","shoemaker",
                "cleaner","photographer","electronics_repair","jewellery_repair" }},
            new StoreCategory { label = "Gym & Fitness", emoji = "💪", keywords = new[] {
                "gym","palestra","palestre","fitness_centre","fitness","yoga","sports_centre" }},
            new StoreCategory { label = "Musei & Cultura", emoji = "🏛️", keywords = new[] {
                "museum","museo","gallery","monument","monumento","library","biblioteca",
                "church","cathedral","cattedrale","monastery","castle","castello","ruin",
                "memorial","statue","viewpoint","tourist_information","theme_park","zoo",
                "attraction","planetarium","theatre","opera" }},
            new StoreCategory { label = "Parchi & Natura", emoji = "🌳", keywords = new[] {
                "park","parco","garden","giardino","playground","nature_reserve","forest",
                "fountain","fontana","spring","drinking_water","hot_spring" }},
            new StoreCategory { label = "Scuole & Istruzione", emoji = "🎓", keywords = new[] {
                "school","scuola","kindergarten","college","university","università",
                "music_school","language_school","driving_school" }}
        };

        private List<PoiData> _allPois = new List<PoiData>();
        private List<PoiData> _filtered = new List<PoiData>();
        private Dictionary<string, GameObject> _markers = new Dictionary<string, GameObject>();
        private StoreCategory _current;
        private double _originLat, _originLng;
        private bool _hasOrigin = false;
        private bool _isLoading = false;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);

            if (GetComponent<ExploreInputHandler>() == null)
                gameObject.AddComponent<ExploreInputHandler>();

            if (ExplorePopup.Instance == null)
            {
                var go = new GameObject("ExplorePopup");
                go.AddComponent<ExplorePopup>();
            }

            if (markersRoot == null)
            {
                var root = new GameObject("POI_Markers");
                root.transform.SetParent(transform);
                markersRoot = root.transform;
            }

            _current = categories.Length > 0 ? categories[0] : null;
        }

        private void Start()
        {
            // Bootstrap: prefab di default se non impostato (funziona senza scene setup)
            if (poiMarkerPrefab == null)
                poiMarkerPrefab = CreateDefaultMarkerPrefab();

            // Carica i POI subito appena pronto
            InvokeRepeating("CheckReload", 1f, 5f);
            RequestPoisAtCurrentLocation();
        }

        /// <summary>Creazione automatica se manca nella scena (GameManager la chiama).</summary>
        public static ExploreManager EnsureInstance()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("ExploreManager");
            return go.AddComponent<ExploreManager>();
        }

        private void CheckReload()
        {
            if (_isLoading || !_hasOrigin) return;
            var (lat, lng) = ReadPlayerGeo();
            double d = HaversineM(_originLat, _originLng, lat, lng);
            if (d > reloadDistanceThreshold)
            {
                // Re-origin: sposta i marker per la nuova posizione
                ReOrigin(lat, lng);
                RequestPoisAtCurrentLocation();
            }
        }

        /// <summary>Richiede i POI alla posizione attuale ad Android (Overpass).</summary>
        public void RequestPoisAtCurrentLocation()
        {
            if (_isLoading) return;
            var (lat, lng) = ReadPlayerGeo();
            if (!_hasOrigin) { _originLat = lat; _originLng = lng; _hasOrigin = true; }
            else if (HaversineM(_originLat, _originLng, lat, lng) > reloadDistanceThreshold)
            {
                ReOrigin(lat, lng);
            }

            _isLoading = true;
            Debug.Log($"[Esplora] Richiesti POI a ({lat},{lng}) raggio={searchRadiusMeters}m");
            GameManager.Instance.RequestPois(lat, lng, (int)searchRadiusMeters);
        }

        private void ReOrigin(double lat, double lng)
        {
            var delta = GeospatialAnchor.GeoWorldOffset(lat, lng, 0, _originLat, _originLng, 0);
            foreach (var kvp in _markers)
            {
                if (kvp.Value != null)
                    kvp.Value.transform.position -= delta;
            }
            foreach (var p in _allPois)
                p.worldPosition -= delta;
            _originLat = lat;
            _originLng = lng;
        }

        /// <summary>Callback da Android (GameManager.OnPoisReceived).</summary>
        public void OnPoisReceived(string json)
        {
            _isLoading = false;
            try
            {
                var env = JsonUtility.FromJson<PoiEnvelope>(json);
                if (env == null || env.pois == null)
                {
                    Debug.LogWarning("[Esplora] Nessun POI nella risposta");
                    return;
                }

                ClearMarkers();
                _allPois.Clear();

                foreach (var p in env.pois)
                {
                    if (string.IsNullOrEmpty(p.id)) continue;
                    p.worldPosition = GeospatialAnchor.GeoWorldOffset(
                        p.lat, p.lng, 0, _originLat, _originLng, 0);
                    _allPois.Add(p);
                }

                Debug.Log($"[Esplora] Caricati {_allPois.Count} POI");
                ApplyFilter(_current);
            }
            catch (System.Exception e)
            {
                Debug.LogError($"[Esplora] Errore parsing POI: {e.Message}");
            }
        }

        /// <summary>Filtra per categoria (indice delle tab).</summary>
        public void SetCategory(int index)
        {
            if (index < 0 || index >= categories.Length) return;
            _current = categories[index];
            ApplyFilter(_current);
        }

        private void ApplyFilter(StoreCategory cat)
        {
            if (cat == null) return;

            if (cat.IsAll)
            {
                _filtered = _allPois
                    .Where(p => categories.Skip(1).Any(c => c.Matches(p)))
                    .ToList();
            }
            else
            {
                _filtered = _allPois.Where(p => cat.Matches(p)).ToList();
            }

            // Limita ai più vicini
            if (_filtered.Count > maxVisiblePois)
            {
                _filtered = _filtered
                    .OrderBy(p => p.worldPosition.sqrMagnitude)
                    .Take(maxVisiblePois)
                    .ToList();
            }

            RefreshMarkers();
        }

        /// <summary>Ricerca per testo (nome/tipo).</summary>
        public void Search(string query)
        {
            if (string.IsNullOrWhiteSpace(query) || query.Trim().Length < 2)
            {
                ApplyFilter(_current);
                return;
            }
            var tokens = query.ToLower().Split(' ')
                .Where(t => t.Length > 0).ToArray();
            var cat = _current;
            var pool = (cat != null && !cat.IsAll)
                ? _allPois.Where(p => cat.Matches(p)).ToList()
                : _allPois;
            _filtered = pool.Where(p =>
            {
                var text = $"{p.name} {p.buildingType} {p.poiType}".ToLower();
                return tokens.All(t => text.Contains(t));
            }).ToList();
            RefreshMarkers();
        }

        private void RefreshMarkers()
        {
            var wanted = new HashSet<string>(_filtered.Select(p => p.id));

            // Nasconde i non inclusi
            var toHide = _markers.Keys.Where(id => !wanted.Contains(id)).ToList();
            foreach (var id in toHide)
                if (_markers.TryGetValue(id, out var m)) m.SetActive(false);

            // Mostra/crea i marker inclusi
            foreach (var poi in _filtered)
            {
                if (_markers.TryGetValue(poi.id, out var existing))
                {
                    existing.SetActive(true);
                    poi.markerObject = existing;
                }
                else
                {
                    poi.markerObject = CreateMarker(poi);
                    _markers[poi.id] = poi.markerObject;
                }
            }
            Debug.Log($"[Esplora] Marker visibili: {_filtered.Count} ({(_current != null ? _current.label : "?")})");
        }

        private GameObject CreateMarker(PoiData poi)
        {
            var marker = Instantiate(poiMarkerPrefab, markersRoot);
            marker.name = "POI_" + poi.id;
            marker.transform.position = poi.worldPosition;

            var pm = marker.GetComponent<POIMarker>();
            if (pm == null) pm = marker.AddComponent<POIMarker>();
            pm.Setup(poi);

            // Imposta il testo emoji in un eventuale TextMesh/UI dentro il prefab
            var label = marker.GetComponentInChildren<TextMesh>();
            if (label != null) label.text = string.IsNullOrEmpty(poi.emoji) ? EmojiFor(poi) : poi.emoji;

            return marker;
        }

        /// <summary>Apre la pagina del locale (via Android: custom JSON / web / JSON sintetico OSM).</summary>
        public void OpenPoi(PoiData poi)
        {
            if (poi == null) return;
            Debug.Log($"[Esplora] Apri locale: {poi.name} ({poi.id}) pageType={poi.pageType}");
            UnityBridge.OpenPoiPage(poi.id, poi.name, poi.buildingType, poi.poiType,
                                    poi.pageType, poi.url, poi.lat, poi.lng, poi.category);
        }

        // ── Interazione tap (InputHandler) ──────────────────────────
        // 1) marker POI → pagina del POI (custom JSON / web / JSON sintetico OSM);
        // 2) punto vuoto → ricerca POI vicini (0 / 1 / molti) e popup.

        /// <summary>Tap su un marker POI: apre sempre la pagina del POI.</summary>
        public void OnPoiTapped(PoiData poi)
        {
            if (poi == null) return;
            OpenPoi(poi);
        }

        /// <summary>Tap su un punto vuoto del terreno.</summary>
        public void OnGroundTapped(Vector3 worldPoint)
        {
            if (ExplorePopup.IsOpen) return;

            const float nearbyRadiusM = 60f;
            var nearby = _allPois
                .Where(p => DistanceOnPlane(p.worldPosition, worldPoint) <= nearbyRadiusM)
                .OrderBy(p => DistanceOnPlane(p.worldPosition, worldPoint))
                .ToList();

            var (lat, lng) = WorldToLatLng(worldPoint);
            Debug.Log($"[Esplora] Tap su terreno vuoto ({lat:F6},{lng:F6}): {nearby.Count} POI vicini");

            if (nearby.Count == 0)
            {
                ExplorePopup.Show(
                    "Nessun POI registrato",
                    "In questo punto non risulta alcun punto di interesse registrato.\n\nVuoi segnalarlo per creare un nuovo POI?",
                    null, null,
                    ("Segnala / Crea nuovo", () => ShowReportForm(lat, lng)),
                    ("Annulla", null));
            }
            else if (nearby.Count == 1)
            {
                var p = nearby[0];
                int meters = Mathf.Max(1, Mathf.RoundToInt(DistanceOnPlane(p.worldPosition, worldPoint)));
                ExplorePopup.Show(
                    "POI già registrato",
                    $"A circa {meters} m da qui c'è già un punto di interesse registrato:\n\n'{p.name}'.\n\nCosa vuoi fare?",
                    null, null,
                    ("Accedi a questo POI", () => OnPoiTapped(p)),
                    ("Crea nuovo", () => ShowReportForm(lat, lng)),
                    ("Annulla", null));
            }
            else
            {
                var names = nearby.Select(p => p.name).ToList();
                ExplorePopup.Show(
                    "Punti di interesse vicini",
                    $"Ci sono {nearby.Count} punti di interesse nelle vicinanze.\n\nSelezionane uno oppure creane uno nuovo:",
                    names, idx => OnPoiTapped(nearby[idx]),
                    ("Crea nuovo", () => ShowReportForm(lat, lng)),
                    ("Annulla", null));
            }
        }

        /// <summary>Form di segnalazione → email a lembopasquale78@gmail.com.</summary>
        private void ShowReportForm(double lat, double lng)
        {
            ExplorePopup.ShowReportForm(lat, lng, (name, category, note) =>
                SendSegnalazioneEmail(lat, lng, name, category, note));
        }

        /// <summary>Compone e lancia l'email di segnalazione via client mail.</summary>
        private void SendSegnalazioneEmail(double lat, double lng, string name, string category, string note)
        {
            const string to = "lembopasquale78@gmail.com";
            string subject = "Huntix - Segnalazione nuovo POI";
            string body = string.Format(
                "Segnalazione nuovo punto di interesse (Huntix Esplora)\n\n" +
                "Nome: {0}\nCategoria: {1}\nLatitudine: {2:F6}\nLongitudine: {3:F6}\nNota: {4}",
                name, category, lat, lng, note);

            string mailto = "mailto:" + to +
                "?subject=" + System.Uri.EscapeDataString(subject) +
                "&body=" + System.Uri.EscapeDataString(body);

            Debug.Log("[Esplora] Email segnalazione: " + mailto);

            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                using (var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
                using (var intent = new AndroidJavaObject("android.content.Intent", "android.intent.action.SENDTO"))
                using (var uri = new AndroidJavaObject("android.net.Uri"))
                {
                    var parsed = uri.CallStatic<AndroidJavaObject>("parse", mailto);
                    intent.Call<AndroidJavaObject>("setData", parsed);
                    activity.Call("startActivity", intent);
                }
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[Esplora] Invio email fallito: " + e.Message);
                UnityBridge.ShowToast("Nessuna app email disponibile");
            }
            #endif
        }

        /// <summary>Distanza in metri sul piano dei marker (x=east, y=north, z=alt).</summary>
        private static float DistanceOnPlane(Vector3 a, Vector3 b)
        {
            float dx = a.x - b.x;
            float dy = a.y - b.y;
            return Mathf.Sqrt(dx * dx + dy * dy);
        }

        /// <summary>Converte un punto del mondo nel piano dei marker in coordinate reali.</summary>
        private (double lat, double lng) WorldToLatLng(Vector3 world)
        {
            double dNorth = world.y;                 // nord = +Y (metri)
            double dEast = world.x;                  // est = +X (metri)
            double lat = _originLat + dNorth / 111320.0;
            double lng = _originLng + dEast / (111320.0 * System.Math.Cos(_originLat * System.Math.PI / 180.0));
            return (lat, lng);
        }

        private string EmojiFor(PoiData poi)
        {
            foreach (var c in categories.Skip(1))
                if (c.Matches(poi)) return c.emoji;
            return poi.hasCustom ? "⭐" : "📍";
        }

        private void ClearMarkers()
        {
            foreach (var m in _markers.Values)
                if (m != null) Destroy(m);
            _markers.Clear();
        }

        private (double lat, double lng) ReadPlayerGeo()
        {
            // GPS disattivato/assente: città di default (il personaggio resta al centro).
            const double defaultLat = 41.9028, defaultLng = 12.4964; // Roma
            string json = UnityBridge.GetCurrentLocation();
            double lat = defaultLat, lng = defaultLng;
            try
            {
                var m = Regex.Match(json, "\"lat\"\\s*:\\s*(-?[0-9.]+)");
                if (m.Success) double.TryParse(m.Groups[1].Value, NumberStyles.Float, CultureInfo.InvariantCulture, out lat);
                m = Regex.Match(json, "\"lng\"\\s*:\\s*(-?[0-9.]+)");
                if (m.Success) double.TryParse(m.Groups[1].Value, NumberStyles.Float, CultureInfo.InvariantCulture, out lng);
            }
            catch { }
            // Coordinate non valide (0,0 = nessun fix): ripiega sulla città di default.
            if (lat == 0d && lng == 0d) { lat = defaultLat; lng = defaultLng; }
            return (lat, lng);
        }

        private static double HaversineM(double lat1, double lon1, double lat2, double lon2)
        {
            const double R = 6371000.0;
            double dLat = (lat2 - lat1) * System.Math.PI / 180.0;
            double dLon = (lon2 - lon1) * System.Math.PI / 180.0;
            double a = System.Math.Sin(dLat / 2) * System.Math.Sin(dLat / 2) +
                       System.Math.Cos(lat1 * System.Math.PI / 180.0) * System.Math.Cos(lat2 * System.Math.PI / 180.0) *
                       System.Math.Sin(dLon / 2) * System.Math.Sin(dLon / 2);
            return 2 * R * System.Math.Atan2(System.Math.Sqrt(a), System.Math.Sqrt(1 - a));
        }

        /// <summary>Prefab di default: cilindro colorato (base di prova).</summary>
        private static GameObject CreateDefaultMarkerPrefab()
        {
            var marker = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            marker.transform.localScale = new Vector3(0.6f, 2f, 0.6f);
            marker.GetComponent<Renderer>().material.color = new Color(1f, 0.6f, 0.1f);
            return marker;
        }
    }
}