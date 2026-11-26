using UnityEngine;
using UnityEngine.SceneManagement;
using System;
using Huntix.Bridge;
using Huntix.Outdoor;
using Huntix.UI;
using City.OSM;

namespace Huntix.Core
{
    public class GameManager : MonoBehaviour
    {
        public static GameManager Instance { get; private set; }

        // Registry asset Kenney (referenziata qui per essere inclusa nel build).
        // Risolve gli asset via riferimento diretto invece di Resources.Load.
        public KenneyAssetRegistry kenneyRegistry;

        // Registry asset Kenney City Kit (strade/edifici/arredo urbano, CC0).
        public CityKitAssetRegistry cityKitRegistry;

        // Registry delle 13 texture PBR CC0 della modalita' cinematografica.
        // Riferimento diretto: Resources.Load non risolve nel build Android.
        public CinematicAssetRegistry cinematicRegistry;

        public string CurrentMode { get; private set; }
        public bool IsInUnity { get; private set; }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            IsInUnity = true;
            // Legge subito la modalita' mondo: se l'evento Android arriva dopo
            // l'avvio della scena City, il flag e' gia' noto e non parte uno
            // streaming OSM inutile prima del ripristino del bridge.
            City.Fictional.FictionalCityWorld.Initialize();
            // Profilo prestazioni comune a TUTTE le modalita' (Menu, Esplora,
            // Citta', Interni): non dipende dalla scena City ne' dal chunking.
            City.Diagnostics.AdaptivePerformanceProfile.Ensure();
        }

        private void Start()
        {
            UnityBridge.Init();

            // Stato dei pacchetti offline (ponte Android <-> Unity): serve per
            // sapere se la citta' di spawn e' gia' sul dispositivo senza dover
            // interrogare il server. Non modifica la logica di TileClient.
            OfflinePackBridge.LogStatusOnce();

            // Se l'Activity Unity è stata lanciata con una modalità (es. Esplora),
            // carica subito la scena giusta (Esplora → Outdoor con personaggio + POI).
            string mode = UnityBridge.GetMode();
            if (!string.IsNullOrEmpty(mode))
            {
                LoadSceneForMode(mode);
            }
            else
            {
                // Nessun modo specifico: scena di menu, mostra l'UI di esplorazione.
                BootstrapExplore();
                NeedsHUD.EnsureInstance();
            }
        }

        // Ultimo modo effettivamente applicato: setMode arriva spesso DUE volte
        // (da Start via UnityBridge.GetMode e da BridgeActivity via
        // UnitySendMessage) nello stesso frame: senza guardia LoadScene verrebbe
        // accodato due volte e la scena ricaricata, distruggendo gli oggetti
        // root creati dal primo caricamento (ChunkManager incluso).
        private static string _appliedMode;

        private void LoadSceneForMode(string mode)
        {
            if (mode == _appliedMode)
            {
                Debug.Log($"[GameManager] LoadSceneForMode ignorato (gia' attivo): {mode}");
                return;
            }
            _appliedMode = mode;
            CurrentMode = mode;
            Debug.Log($"[GameManager] Mode set to: {mode}");
            string scene = mode switch
            {
                "outdoor" or "esplora" or "reallife" => "Outdoor",
                "indoor" => "Indoor",
                "room" => "Room",
                "game" or "sheep" => "Preload",
                "argame" or "ardice" => "MainScene",
                "miacitta" => "City",
                _ => null
            };
            if (scene != null && SceneManager.GetActiveScene().name != scene)
            {
                SceneManager.LoadScene(scene);
                if (scene == "Outdoor")
                {
                    NeedsHUD.EnsureInstance();
                    BootstrapExplore();
                }
                else
                {
                    NeedsHUD.DestroyInstance();
                    // Rimuove l'UI e i manager Esplora (POI) non pertineni in altre scene
                    if (ExploreManager.Instance != null) Destroy(ExploreManager.Instance.gameObject);
                    if (ExploreUIController.Instance != null) Destroy(ExploreUIController.Instance.gameObject);
                    if (ExplorePopup.Instance != null) Destroy(ExplorePopup.Instance.gameObject);
                    if (ExploreInputHandler.Instance != null) Destroy(ExploreInputHandler.Instance.gameObject);
                }
            }

            // MiAcitma: sostituisce il quartiere finto con la città OSM reale (streaming GPS).
            if (scene == "City")
            {
                // FASE 2+: citta' OSM streamata dal server (tile 10km / chunk 1km).
                // Sostituisce il vecchio quartiere Overpass via Android.
                City.OSM.CityChunkedWorld.EnsureInstance();
                if (City.Interior.InteriorManager.Instance == null)
                {
                    var im = new GameObject("InteriorManager");
                    UnityEngine.Object.DontDestroyOnLoad(im);
                    im.AddComponent<City.Interior.InteriorManager>();
                }
            }
        }

        // Crea il modulo Esplora (marker POI AR) e la sua UI se assenti in scena.
        private void BootstrapExplore()
        {
            if (ExploreManager.Instance == null)
            {
                var go = new GameObject("ExploreManager");
                go.AddComponent<ExploreManager>();
            }
            if (ExploreUIController.Instance == null)
            {
                var go = new GameObject("ExploreUIController");
                go.AddComponent<ExploreUIController>();
            }
        }

        public void SetMode(string mode)
        {
            CurrentMode = mode;
            Debug.Log($"[GameManager] Mode set to: {mode}");
        }

        public void ReturnToAndroid()
        {
            Debug.Log("[GameManager] Returning to Android native UI");
            UnityBridge.QuitToAndroid();
        }

        public void LoadScene(string sceneName)
        {
            SceneManager.LoadScene(sceneName);
        }

        /// <summary>
        /// Android -> Unity: un pacchetto offline e' stato installato. Lo stato
        /// e' gia' nelle prefs; qui aggiorniamo solo il log/UI di MiCittà.
        /// </summary>
        public void OnOfflinePackChanged(string json)
        {
            OfflinePackBridge.OnStatusFromAndroid(json);
        }

        /// <summary>Android -> Unity: stato dei settori visitati e richiesta di
        /// prefetch del World Streaming (nessun dato di profilo viene toccato).</summary>
        public void OnWorldStreamingStatus(string json)
        {
            WorldStreamingBridge.OnStatusFromAndroid(json);
        }

        // ── Ricevitori UnitySendMessage "diretti" ────────────────────────
        // Unity invoca il metodo col nome esatto passato da Android: questi
        // eventi non passano dal messenger PoiUnityBridge (che usava "OnEvent").
        // Senza questi metodi UnitySendMessage rispondeva "no receiver" e la
        // freccetta CASA/STADIO restava solo un toast. Li facciamo confluire
        // nello switch storico di OnEvent, senza duplicare la logica.

        public void navigateToHouse(string json)
        {
            OnEvent("navigateToHouse|" + (string.IsNullOrEmpty(json) ? "{}" : json));
        }

        public void navigateToVenue(string json)
        {
            OnEvent("navigateToVenue|" + (string.IsNullOrEmpty(json) ? "{}" : json));
        }

        public void openDestinationSearch(string json)
        {
            OnEvent("openDestinationSearch|" + (string.IsNullOrEmpty(json) ? "{}" : json));
        }

        public void OnGeocodeResult(string json)
        {
            OnEvent("OnGeocodeResult|" + (string.IsNullOrEmpty(json) ? "{}" : json));
        }

        public void TileCacheCleared(string json)
        {
            OnEvent("TileCacheCleared|" + (string.IsNullOrEmpty(json) ? "{}" : json));
        }

        /// <summary>Android → Unity: attiva/disattiva il profilo adattivo e
        /// passa il livello qualita' scelto nell'app, senza nuove preferenze.</summary>
        public void setAdaptivePerformance(string json)
        {
            OnEvent("setAdaptivePerformance|" + (string.IsNullOrEmpty(json) ? "{}" : json));
        }

        // UnitySendMessage passa sempre un unico parametro stringa.
        // Supporta due formati:
        //   1) JSON: {"action":"setMode","mode":"..."} (inviato da BridgeActivity)
        //   2) "eventName|data" (inviato da PoiUnityBridge.Messenger)
        public void OnEvent(string message)
        {
            if (string.IsNullOrEmpty(message)) return;

            // Toggle diagnostico del follower verticale: inviato dal viewer dei
            // log Android. Non e' un "action" di setMode, va intercettato qui.
            if (message.IndexOf("toggleGroundFollow", StringComparison.Ordinal) >= 0)
            {
                bool on = City.Player.PlayerController.ToggleGroundFollow();
                Debug.Log("[GameManager] GroundFollow -> " + (on ? "ON" : "OFF"));
                return;
            }

            // Doppia modalita' camera (prima/terza persona): comando dal
            // bottone in-game e dal viewer dei log Android, stesso canale del
            // follower. "setCameraMode" accetta true/false oppure prima/first.
            if (message.IndexOf("toggleCameraMode", StringComparison.Ordinal) >= 0)
            {
                var camRig = City.Player.CameraRig.Instance;
                if (camRig != null) camRig.ToggleFirstPerson();
                Debug.Log("[GameManager] CameraMode -> " +
                    (camRig != null && camRig.firstPerson ? "PRIMA" : "TERZA"));
                return;
            }
            if (message.IndexOf("setCameraMode", StringComparison.Ordinal) >= 0)
            {
                var camRig = City.Player.CameraRig.Instance;
                bool first = message.IndexOf("true", StringComparison.OrdinalIgnoreCase) >= 0 ||
                    message.IndexOf("prima", StringComparison.OrdinalIgnoreCase) >= 0 ||
                    message.IndexOf("first", StringComparison.OrdinalIgnoreCase) >= 0;
                if (camRig != null) camRig.SetFirstPerson(first);
                Debug.Log("[GameManager] CameraMode set -> " + (first ? "PRIMA" : "TERZA"));
                return;
            }

            // GIORNO 5: toggle del head bob dal viewer dei log Android (utile
            // per chi soffre il motion sickness). Stesso canale del follower.
            if (message.IndexOf("toggleHeadBob", StringComparison.Ordinal) >= 0)
            {
                var rig = City.Player.CameraRig.Instance;
                bool on = rig != null && rig.ToggleHeadBob();
                Debug.Log("[GameManager] HeadBob -> " + (on ? "ON" : "OFF"));
                return;
            }

            // Formato JSON (BridgeActivity)
            if (message.Contains("\"action\"", StringComparison.Ordinal) ||
                message.Contains("\"setMode\"", StringComparison.Ordinal))
            {
                HandleSetMode(message);
                return;
            }

            // Formato pipe (PoiUnityBridge)
            string eventName, jsonData;
            int pipeIndex = message.IndexOf('|');
            if (pipeIndex >= 0)
            {
                eventName = message.Substring(0, pipeIndex);
                jsonData = message.Substring(pipeIndex + 1);
            }
            else
            {
                eventName = message;
                jsonData = "";
            }

            Debug.Log($"[GameManager] Event received: {eventName} - {jsonData}");

            switch (eventName)
            {
                case "EggCaptured":
                    HandleEggCaptured(jsonData);
                    break;
                // Android → Unity: abilità mestieri aggiornate a caldo
                // (payload: "mastro_ladro:2;...|punti").
                case "CitySkillsUpdated":
                    ApplyCitySkills(jsonData);
                    break;
                case "MVCUpdated":
                    HandleMVCUpdated(jsonData);
                    break;
                // Fase 2 · consumabili: Android comunica l'aroma scelto (o lo
                // azzera dopo il lancio). Solo specchio + telemetria: nessun
                // uovo e nessun premio su questo evento.
                case "ActiveBaitChanged":
                    HandleActiveBaitChanged(jsonData);
                    break;
                // Fase 2 · crafting: Android conferma la fusione e rimanda
                // contatore/ricordo posizionale. Solo specchio + telemetria.
                case "FusionCrafted":
                    HandleFusionCrafted(jsonData);
                    break;
                // Fase 2 · inventario evoluto: Android manda solo contatori e
                // preferenza di vista, mai l'inventario uova completo.
                case "EggInventoryChanged":
                    HandleEggInventoryChanged(jsonData);
                    break;
                // Fase 2 · ricette: Android manda solo i contatori di scoperta;
                // il ricettario resta in sola lettura dentro l'app.
                case "RecipeDiscoveryChanged":
                    HandleRecipeDiscoveryChanged(jsonData);
                    break;
                case "setTargetCoord":
                    City.Multiplayer.MultiplayerManager.SetTargetCoords(jsonData);
                    break;
                // Android → Unity: "vai alla casa visitabile". La porta e'
                // nell'elenco configurato (o nel payload), mai inventata.
                case "navigateToHouse":
                    HandleNavigateToHouse(jsonData);
                    break;
                // Android → Unity: "vai allo stadio/ippodromo reale". Le
                // coordinate arrivano dal registro OSM nello snapshot.
                case "navigateToVenue":
                    HandleNavigateToVenue(jsonData);
                    break;
                case "ReturnToMenu":
                    ReturnToAndroid();
                    break;
                // Android → Unity: chip "CERCA DESTINAZIONE" nella mappa
                // Android. Apre la mappa espansa col campo già selezionato.
                case "openDestinationSearch":
                    // Android puo' passare {"query":"..."}: il pannello si apre
                    // gia' compilato ed esegue la ricerca senza digitazione.
                    var sm = System.Text.RegularExpressions.Regex.Match(
                        jsonData ?? "", "\"query\"\\s*:\\s*\"([^\"]*)\"");
                    if (sm.Success && sm.Groups[1].Value.Length > 0)
                        City.OSM.MapSelectUI.OpenSearch(sm.Groups[1].Value);
                    else
                        City.OSM.MapSelectUI.OpenSearch();
                    break;
                // Android → Unity: risposta del geocoding via + città.
                case "OnGeocodeResult":
                    HandleGeocodeResult(jsonData);
                    break;
                // Android → Unity: pulizia selettiva della cache tile
                // completata. Svuota il prefetch e registra l'uovo una volta.
                case "TileCacheCleared":
                    HandleTileCacheCleared(jsonData);
                    break;
                case "setAdaptivePerformance":
                    HandleSetAdaptivePerformance(jsonData);
                    break;
            }
        }

        /// <summary>Unisce gli indirizzi trovati da Nominatim ai POI già
        /// presenti nella ricerca della mappa espansa.</summary>
        private static void HandleGeocodeResult(string json)
        {
            if (string.IsNullOrEmpty(json)) return;
            var map = City.OSM.MapSelectUI.Instance;
            if (map != null) map.MergeSearchResults(json);
            Debug.Log("[GameManager] OnGeocodeResult: " +
                (json.Length > 220 ? json.Substring(0, 220) : json));
        }

        /// <summary>La cache su disco e' stata pulita lato Android: qui si
        /// libera la copia in memoria (Unity non deve riusare JSON vecchi) e
        /// scatta il riflesso uova una sola volta.</summary>
        private static void HandleTileCacheCleared(string json)
        {
            City.OSM.TileClient.TrimPrefetch(0);
            if (PlayerPrefs.GetInt("huntix_space_cleanup_egg", 0) == 0)
            {
                PlayerPrefs.SetInt("huntix_space_cleanup_egg", 1);
                PlayerPrefs.Save();
                var game = City.Game.Instance;
                if (game != null)
                    game.GrantAuditEgg("archivista-del-goblin",
                        "Archivista del Goblin", "Gestione spazio");
            }
            Debug.Log("[GameManager] TileCacheCleared: " +
                (string.IsNullOrEmpty(json) ? "{}" : json));
        }

        private static void HandleSetAdaptivePerformance(string json)
        {
            bool enabled = true;
            if (!string.IsNullOrEmpty(json))
            {
                var m = System.Text.RegularExpressions.Regex.Match(
                    json, "\"enabled\"\\s*:\\s*(true|false|1|0)",
                    System.Text.RegularExpressions.RegexOptions.IgnoreCase);
                if (m.Success)
                    enabled = m.Groups[1].Value.Equals("true", System.StringComparison.OrdinalIgnoreCase)
                              || m.Groups[1].Value == "1";
                var q = System.Text.RegularExpressions.Regex.Match(
                    json, "\"quality\"\\s*:\\s*\"(low|medium|high)\"",
                    System.Text.RegularExpressions.RegexOptions.IgnoreCase);
                if (q.Success)
                {
                    PlayerPrefs.SetString("city_quality", q.Groups[1].Value.ToLowerInvariant());
                    PlayerPrefs.Save();
                }
            }
            City.Diagnostics.AdaptivePerformanceProfile.Ensure().ApplyUserSetting(enabled);
            Debug.Log("[GameManager] setAdaptivePerformance enabled=" + enabled);
        }

        /// <summary>
        /// Android -> Unity: punta bussola e minimappa alla PORTA di una casa
        /// visitabile. Se il payload non porta coordinate valide si usa
        /// l'elenco configurato (id OSM): mai una destinazione inventata.
        /// Solo navigazione: nessun teletrasporto e nessun premio uovo.
        /// </summary>
        private static void HandleNavigateToHouse(string json)
        {
            if (string.IsNullOrEmpty(json)) return;
            string label = "Casa visitabile";
            var mLabel = System.Text.RegularExpressions.Regex.Match(
                json, "\"label\"\\s*:\\s*\"([^\"]+)\"");
            if (mLabel.Success) label = mLabel.Groups[1].Value;

            var mLat = System.Text.RegularExpressions.Regex.Match(
                json, "\"lat\"\\s*:\\s*(-?[0-9.]+)");
            var mLng = System.Text.RegularExpressions.Regex.Match(
                json, "\"lng\"\\s*:\\s*(-?[0-9.]+)");
            double lat = 0, lng = 0;
            bool ok = mLat.Success && mLng.Success &&
                double.TryParse(mLat.Groups[1].Value,
                    System.Globalization.NumberStyles.Float,
                    System.Globalization.CultureInfo.InvariantCulture, out lat) &&
                double.TryParse(mLng.Groups[1].Value,
                    System.Globalization.NumberStyles.Float,
                    System.Globalization.CultureInfo.InvariantCulture, out lng) &&
                lat > -90.0 && lat < 90.0 && lng > -180.0 && lng < 180.0;

            if (!ok)
            {
                var mOsm = System.Text.RegularExpressions.Regex.Match(
                    json, "\"osmId\"\\s*:\\s*(\\d+)");
                long id = mOsm.Success
                    ? long.Parse(mOsm.Groups[1].Value,
                        System.Globalization.CultureInfo.InvariantCulture)
                    : City.Interior.Visit.VisitInteriorRegistry.DefaultOsmId;
                City.Interior.Visit.VisitInteriorRegistry.Spec spec;
                if (!City.Interior.Visit.VisitInteriorRegistry.TryGet(id, out spec)) return;
                lat = spec.lat;
                lng = spec.lng;
                label = spec.label;
            }

            City.Vehicle.NavigationState.Set(label, "casa", lat, lng);
            Debug.Log("[GameManager] navigazione casa da Android: " + label + " (" +
                lat.ToString(System.Globalization.CultureInfo.InvariantCulture) + "," +
                lng.ToString(System.Globalization.CultureInfo.InvariantCulture) + ")");
        }

        /// <summary>
        /// Android -> Unity: punta bussola e minimappa a uno stadio/ippodromo
        /// REALE del registro OSM. Se il payload non ha coordinate valide si
        /// risolve l'id (o la categoria) dal registro locale: mai una
        /// destinazione inventata. Solo navigazione, nessun teletrasporto.
        /// </summary>
        private static void HandleNavigateToVenue(string json)
        {
            if (string.IsNullOrEmpty(json)) return;
            string label = "Impianto sportivo";
            string kind = "";
            var mLabel = System.Text.RegularExpressions.Regex.Match(
                json, "\"label\"\\s*:\\s*\"([^\"]+)\"");
            if (mLabel.Success) label = mLabel.Groups[1].Value;
            var mKind = System.Text.RegularExpressions.Regex.Match(
                json, "\"kind\"\\s*:\\s*\"([^\"]+)\"");
            if (mKind.Success) kind = mKind.Groups[1].Value;
            var mId = System.Text.RegularExpressions.Regex.Match(
                json, "\"id\"\\s*:\\s*\"([^\"]+)\"");

            double lat = 0, lng = 0;
            bool ok = TryCoord(json, "lat", out lat) &&
                      TryCoord(json, "lng", out lng) &&
                      lat > -90.0 && lat < 90.0 &&
                      lng > -180.0 && lng < 180.0;
            if (!ok)
            {
                City.Events.VenueRegistry.Spec spec = mId.Success
                    ? City.Events.VenueRegistry.Find(mId.Groups[1].Value) : null;
                if (spec == null && !string.IsNullOrEmpty(kind))
                    spec = City.Events.VenueRegistry.First(kind);
                if (spec == null) return;
                lat = spec.lat;
                lng = spec.lng;
                label = spec.name;
                kind = spec.kind;
            }
            if (string.IsNullOrEmpty(kind)) kind = City.Events.VenueRegistry.KindStadio;
            City.Vehicle.NavigationState.Set(label, kind, lat, lng);
            Debug.Log("[GameManager] navigazione impianto da Android: " + label +
                " (" + lat.ToString(System.Globalization.CultureInfo.InvariantCulture) +
                "," + lng.ToString(System.Globalization.CultureInfo.InvariantCulture) + ")");
        }

        private static bool TryCoord(string json, string field, out double value)
        {
            value = 0;
            var m = System.Text.RegularExpressions.Regex.Match(
                json, "\"" + field + "\"\\s*:\\s*(-?[0-9.]+)");
            return m.Success &&
                double.TryParse(m.Groups[1].Value,
                    System.Globalization.NumberStyles.Float,
                    System.Globalization.CultureInfo.InvariantCulture, out value);
        }

        /// <summary>Abilità mestieri da Android: evento caldo (dopo una spesa)
        /// o ripristino passivo in setMode. Nessun premio, solo gate dei furti.
        /// </summary>
        private static void ApplyCitySkills(string payload)
        {
            if (string.IsNullOrEmpty(payload)) return;
            string[] parts = payload.Split('|');
            string levels = parts.Length > 0 ? parts[0] : payload;
            int points = 0;
            if (parts.Length > 1) int.TryParse(parts[1], out points);
            City.Economy.CitySkills.Restore(levels, points);
            Debug.Log("[GameManager] Abilità mestieri: " + levels + " · punti " + points);
        }

        private void HandleSetMode(string json)
        {
            try
            {
                // ABILITÀ MESTIERI: fonte di verità Android, ripristinate prima
                // di qualunque pannello (concessionaria/garage) che ne ha bisogno.
                var citySkills = System.Text.RegularExpressions.Regex.Match(
                    json, "\"citySkills\"\\s*:\\s*\"([^\"]*)\"");
                if (citySkills.Success)
                {
                    var skillPoints = System.Text.RegularExpressions.Regex.Match(
                        json, "\"citySkillPoints\"\\s*:\\s*(\\d+)");
                    ApplyCitySkills(citySkills.Groups[1].Value +
                        "|" + (skillPoints.Success ? skillPoints.Groups[1].Value : "0"));
                }
                // CITTA' GIOCO: il ripristino da Android DEVE arrivare prima del
                // caricamento della scena City, cosi' CityChunkedWorld trova il
                // flag gia' impostato e non streama tile OSM. notify=false:
                // nessun evento di ritorno e nessun uovo su un ripristino.
                var worldMode = System.Text.RegularExpressions.Regex.Match(
                    json, "\"worldMode\"\\s*:\\s*\"([^\"]+)\"");
                if (worldMode.Success)
                {
                    string requested = worldMode.Groups[1].Value.ToLowerInvariant();
                    bool fictional = requested == "fictional" || requested == "citta-gioco";
                    City.Fictional.FictionalCityWorld.SetMode(fictional, false);
                    Debug.Log("[GameManager] mondo ripristinato da Android: " +
                        (fictional ? "Città Gioco" : "Reale"));
                }

                var match = System.Text.RegularExpressions.Regex.Match(json, "\"mode\"\\s*:\\s*\"([^\"]+)\"");
                if (match.Success)
                    LoadSceneForMode(match.Groups[1].Value);

                // Fase 6: skin personaggio scelta nel profilo Android
                var skin = System.Text.RegularExpressions.Regex.Match(json, "\"skin\"\\s*:\\s*\"([^\"]+)\"");
                if (skin.Success)
                    ApplyCitySkin(skin.Groups[1].Value);

                // Pet di compagnia scelto nel profilo Android
                var pet = System.Text.RegularExpressions.Regex.Match(json, "\"pet\"\\s*:\\s*\"([^\"]+)\"");
                if (pet.Success)
                    ApplyPet(pet.Groups[1].Value);

                // MiAcitma: punto di spawn DECISO dal selettore Android (citta'
                // o ultima posizione salvata su Google). Il GPS del dispositivo
                // NON viene mai piu' usato come posizione iniziale.
                var sLat = System.Text.RegularExpressions.Regex.Match(json, "\"spawnLat\"\\s*:\\s*\"?(-?[0-9.]+)\"?");
                var sLng = System.Text.RegularExpressions.Regex.Match(json, "\"spawnLng\"\\s*:\\s*\"?(-?[0-9.]+)\"?");
                if (sLat.Success && sLng.Success &&
                    double.TryParse(sLat.Groups[1].Value, System.Globalization.NumberStyles.Float,
                        System.Globalization.CultureInfo.InvariantCulture, out double lLat) &&
                    double.TryParse(sLng.Groups[1].Value, System.Globalization.NumberStyles.Float,
                        System.Globalization.CultureInfo.InvariantCulture, out double lLng))
                {
                    City.OSM.CityChunkedWorld.UseSpawnPoint(lLat, lLng);
                    Debug.Log("[GameManager] spawn scelto da Android: " +
                        lLat.ToString(System.Globalization.CultureInfo.InvariantCulture) + "," +
                        lLng.ToString(System.Globalization.CultureInfo.InvariantCulture));
                }

                // Profilo grafico salvato da Android: bridge Android -> Unity.
                // apply=false perche' e' un ripristino, non una scelta utente
                // (non deve nascondere un altro uovo ne' re-inviare l'evento).
                var quality = System.Text.RegularExpressions.Regex.Match(
                    json, "\"cityQuality\"\\s*:\\s*\"([^\"]+)\"");
                if (quality.Success)
                {
                    City.Diagnostics.QualityProfileSelector.Profile p;
                    switch (quality.Groups[1].Value.ToLowerInvariant())
                    {
                        case "low": p = City.Diagnostics.QualityProfileSelector.Profile.Low; break;
                        case "high": p = City.Diagnostics.QualityProfileSelector.Profile.High; break;
                        default: p = City.Diagnostics.QualityProfileSelector.Profile.Medium; break;
                    }
                    City.Diagnostics.QualityProfileSelector.Ensure().Apply(p, false);
                    Debug.Log("[GameManager] qualita' ripristinata da Android: " + p);
                }

                // Profilo prestazioni adattivo salvato da Android: ripristino
                // passivo (nessun uovo, nessun eco). Include il livello qualita'
                // perche' e' uno degli ingressi del tier.
                var adaptive = System.Text.RegularExpressions.Regex.Match(
                    json, "\"adaptivePerformance\"\\s*:\\s*\\{([^}]*)\\}");
                if (adaptive.Success)
                {
                    var enabled = System.Text.RegularExpressions.Regex.Match(
                        adaptive.Groups[1].Value, "\"enabled\"\\s*:\\s*true");
                    var apQuality = System.Text.RegularExpressions.Regex.Match(
                        adaptive.Groups[1].Value, "\"quality\"\\s*:\\s*\"(low|medium|high)\"",
                        System.Text.RegularExpressions.RegexOptions.IgnoreCase);
                    if (apQuality.Success)
                    {
                        PlayerPrefs.SetString("city_quality",
                            apQuality.Groups[1].Value.ToLowerInvariant());
                        PlayerPrefs.Save();
                    }
                    City.Diagnostics.AdaptivePerformanceProfile.Ensure()
                        .ApplyRestored(enabled.Success);
                    Debug.Log("[GameManager] prestazioni adattive ripristinate da Android: " +
                        enabled.Success);
                }

                // Sorgente input touch scelta in Miacitta e salvata da Android.
                // notify=false: e' un ripristino, non una scelta dell'utente.
                var altTouch = System.Text.RegularExpressions.Regex.Match(
                    json, "\"altTouch\"\\s*:\\s*true");
                if (altTouch.Success)
                {
                    City.UI.AlternateTouchInput.SetEnabled(true, false);
                    Debug.Log("[GameManager] input touch ripristinato da Android: zone-zero");
                }

                // Innesti MIT dell'audit: preferenze salvate da Android e
                // ripristinate passivamente (notify=false: niente uova, niente
                // eco verso Android, nessuna finestra che si apre da sola).
                var menuAnimation = System.Text.RegularExpressions.Regex.Match(
                    json, "\"menuAnimation\"\\s*:\\s*true");
                if (menuAnimation.Success)
                {
                    City.UI.MenuEntranceAnimator.SetEnabled(true, false);
                    Debug.Log("[GameManager] animazione menu ripristinata da Android: zone-zero");
                }
                var qaConsole = System.Text.RegularExpressions.Regex.Match(
                    json, "\"qaConsole\"\\s*:\\s*true");
                if (qaConsole.Success)
                {
                    City.Diagnostics.HuntixDebugConsole.SetEnabled(true, false);
                    City.Diagnostics.HuntixDebugConsole.Ensure();
                    Debug.Log("[GameManager] console QA ripristinata da Android: on");
                }

                // Compagno NPC: Android rimanda la preferenza e il nome dell'
                // ultimo pedone. Restore passivo: nessun compagno appare e
                // nessun uovo viene assegnato all'avvio.
                var npcCompanion = System.Text.RegularExpressions.Regex.Match(
                    json, "\"npcCompanion\"\\s*:\\s*true");
                if (npcCompanion.Success)
                {
                    var compName = System.Text.RegularExpressions.Regex.Match(
                        json, "\"npcCompanionName\"\\s*:\\s*\"([^\"]+)\"");
                    var compId = System.Text.RegularExpressions.Regex.Match(
                        json, "\"npcCompanionId\"\\s*:\\s*\"([^\"]+)\"");
                    City.NPC.NpcCompanion.Initialize();
                    City.NPC.NpcCompanion.Ensure();
                    City.NPC.NpcCompanion.RestoreFromAndroid(
                        compName.Success ? compName.Groups[1].Value : "",
                        compId.Success ? compId.Groups[1].Value : "");
                    Debug.Log("[GameManager] compagno NPC ripristinato da Android");
                }

                // Fase 1 · registro attività: Android comunica quante voci del
                // diario possiede (uova posizionali + missioni/check-in/badge).
                // Unity conserva il contatore e lo segna in telemetria: non
                // ricostruisce la cronologia (di competenza del profilo Android)
                // e non genera uova su un ripristino passivo.
                // Audit open source: ripristino passivo delle opzioni addittive.
                // Nessun evento, nessun uovo: Android e' il proprietario dello
                // stato, Unity si limita a sincronizzare i propri handler.
                var osFlags = System.Text.RegularExpressions.Regex.Match(
                    json, "\"openSourceFeatures\"\\s*:\\s*\"([^\"]*)\"");
                if (osFlags.Success)
                    City.Diagnostics.OpenSourceFeatureBridge.RestoreFromAndroid(
                        osFlags.Groups[1].Value);

                // Grafica cinematografica: secondo gruppo di preferenze,
                // ripristino passivo senza uova ne' notifiche.
                var gfxFlags = System.Text.RegularExpressions.Regex.Match(
                    json, "\"cinematicFeatures\"\\s*:\\s*\"([^\"]*)\"");
                if (gfxFlags.Success)
                    City.Diagnostics.CinematicFeatureBridge.RestoreFromAndroid(
                        gfxFlags.Groups[1].Value);

                // Dettagli naturali: terzo gruppo di preferenze visive.
                // Ripristino passivo, senza uova ne' notifiche: Android resta
                // il proprietario dello stato (huntix_veg_*).
                var vegFlags = System.Text.RegularExpressions.Regex.Match(
                    json, "\"vegetationFeatures\"\\s*:\\s*\"([^\"]*)\"");
                if (vegFlags.Success)
                    City.Diagnostics.VegetationFeatureBridge.RestoreFromAndroid(
                        vegFlags.Groups[1].Value);

                var activityLogCount = System.Text.RegularExpressions.Regex.Match(
                    json, "\"activityLogCount\"\\s*:\\s*(\\d+)");
                if (activityLogCount.Success)
                {
                    int entries;
                    if (int.TryParse(activityLogCount.Groups[1].Value, out entries))
                    {
                        using (City.Diagnostics.PerfProbe.Measure("diary"))
                        {
                            PlayerPrefs.SetInt("huntix_activity_log_count", entries);
                            if (City.Diagnostics.SessionTelemetry.Instance != null)
                                City.Diagnostics.SessionTelemetry.Instance.Mark(
                                    "Diario attività: {0} voci", entries);
                        }
                        Debug.Log("[GameManager] diario attività ripristinato da Android: " +
                            entries + " voci");
                    }
                }

                // Fase 2 · consumabili: ripristino passivo dell'esca scelta e
                // del numero di aromi in inventario. Non si applica alcun
                // bonus alla cattura e non viene generata nessun uovo: e' uno
                // specchio dello stato Android, non una nuova autorità.
                var activeBait = System.Text.RegularExpressions.Regex.Match(
                    json, "\"activeBaitId\"\\s*:\\s*\"([^\"]+)\"");
                var baitCount = System.Text.RegularExpressions.Regex.Match(
                    json, "\"baitCount\"\\s*:\\s*(\\d+)");
                var baitBonus = System.Text.RegularExpressions.Regex.Match(
                    json, "\"activeBaitBonus\"\\s*:\\s*([0-9.]+)");
                if (activeBait.Success || baitCount.Success)
                {
                    using (City.Diagnostics.PerfProbe.Measure("bait"))
                    {
                        string baitId = activeBait.Success ? activeBait.Groups[1].Value : string.Empty;
                        PlayerPrefs.SetString("huntix_active_bait", baitId);
                        int aromi = 0;
                        if (baitCount.Success) int.TryParse(baitCount.Groups[1].Value, out aromi);
                        PlayerPrefs.SetInt("huntix_bait_count", aromi);
                        float multiplier = 1f;
                        if (baitBonus.Success)
                            float.TryParse(baitBonus.Groups[1].Value,
                                System.Globalization.NumberStyles.Float,
                                System.Globalization.CultureInfo.InvariantCulture, out multiplier);
                        PlayerPrefs.SetFloat("huntix_active_bait_bonus", multiplier);
                        if (City.Diagnostics.SessionTelemetry.Instance != null)
                            City.Diagnostics.SessionTelemetry.Instance.Mark(
                                "Aromi disponibili: {0} · esca {1}", aromi,
                                string.IsNullOrEmpty(baitId) ? "nessuna" : baitId);
                    }
                    Debug.Log("[GameManager] esche ripristinate da Android");
                }

                // Fase 2 · raccolta risorse: Android rimanda solo i contatori.
                // Unity li conserva per HUD/telemetria; non genera uova e non
                // applica premi: la fonte di verita' resta il profilo Android.
                var harvestCount = System.Text.RegularExpressions.Regex.Match(
                    json, "\"resourceHarvestCount\"\\s*:\\s*(\\d+)");
                var harvestKinds = System.Text.RegularExpressions.Regex.Match(
                    json, "\"resourceHarvestKinds\"\\s*:\\s*(\\d+)");
                if (harvestCount.Success || harvestKinds.Success)
                {
                    using (City.Diagnostics.PerfProbe.Measure("harvest"))
                    {
                        int raccolte = 0;
                        int tipi = 0;
                        if (harvestCount.Success) int.TryParse(harvestCount.Groups[1].Value, out raccolte);
                        if (harvestKinds.Success) int.TryParse(harvestKinds.Groups[1].Value, out tipi);
                        PlayerPrefs.SetInt("huntix_resource_harvest_count", raccolte);
                        PlayerPrefs.SetInt("huntix_resource_harvest_kinds", tipi);
                        if (City.Diagnostics.SessionTelemetry.Instance != null)
                            City.Diagnostics.SessionTelemetry.Instance.Mark(
                                "Raccolte: {0} · tipi: {1}", raccolte, tipi);
                    }
                    Debug.Log("[GameManager] raccolte ripristinate da Android");
                }

                // Fase 2 · crafting: Android rimanda solo i contatori del
                // wrapper; nessuna fusione e nessun uovo su un ripristino.
                var craftCount = System.Text.RegularExpressions.Regex.Match(
                    json, "\"fusionCraftCount\"\\s*:\\s*(\\d+)");
                var craftLevel = System.Text.RegularExpressions.Regex.Match(
                    json, "\"fusionCraftLastLevel\"\\s*:\\s*(\\d+)");
                if (craftCount.Success || craftLevel.Success)
                {
                    using (City.Diagnostics.PerfProbe.Measure("craft"))
                    {
                        int fusioni = 0;
                        int livello = 0;
                        if (craftCount.Success) int.TryParse(craftCount.Groups[1].Value, out fusioni);
                        if (craftLevel.Success) int.TryParse(craftLevel.Groups[1].Value, out livello);
                        PlayerPrefs.SetInt("huntix_fusion_craft_count", fusioni);
                        PlayerPrefs.SetInt("huntix_fusion_craft_last_level", livello);
                        if (City.Diagnostics.SessionTelemetry.Instance != null)
                            City.Diagnostics.SessionTelemetry.Instance.Mark(
                                "Fusioni: {0} · ultimo livello: {1}", fusioni, livello);
                    }
                    Debug.Log("[GameManager] fusioni ripristinate da Android");
                }

                // Fase 2 · inventario evoluto: specchio passivo dei contatori.
                var invCount = System.Text.RegularExpressions.Regex.Match(
                    json, "\"inventoryCount\"\\s*:\\s*(\\d+)");
                var invTeam = System.Text.RegularExpressions.Regex.Match(
                    json, "\"inventoryTeamCount\"\\s*:\\s*(\\d+)");
                if (invCount.Success || invTeam.Success)
                {
                    using (City.Diagnostics.PerfProbe.Measure("inventory"))
                    {
                        int uova = invCount.Success ? int.Parse(invCount.Groups[1].Value) : 0;
                        int squadra = invTeam.Success ? int.Parse(invTeam.Groups[1].Value) : 0;
                        PlayerPrefs.SetInt("huntix_inventory_egg_count", uova);
                        PlayerPrefs.SetInt("huntix_inventory_team_count", squadra);
                        if (City.Diagnostics.SessionTelemetry.Instance != null)
                            City.Diagnostics.SessionTelemetry.Instance.Mark(
                                "Collezione: {0} uova · squadra {1}", uova, squadra);
                    }
                    Debug.Log("[GameManager] collezione uova ripristinata da Android");
                }

                // Fase 2 · ricette: specchio passivo di scoperte e utilizzi.
                var recipeCount = System.Text.RegularExpressions.Regex.Match(
                    json, "\"recipeDiscoveredCount\"\\s*:\\s*(\\d+)");
                var recipeUses = System.Text.RegularExpressions.Regex.Match(
                    json, "\"recipeUses\"\\s*:\\s*(\\d+)");
                if (recipeCount.Success || recipeUses.Success)
                {
                    using (City.Diagnostics.PerfProbe.Measure("recipe"))
                    {
                        int scoperte = recipeCount.Success ? int.Parse(recipeCount.Groups[1].Value) : 0;
                        int usi = recipeUses.Success ? int.Parse(recipeUses.Groups[1].Value) : 0;
                        PlayerPrefs.SetInt("huntix_recipe_discovered_count", scoperte);
                        PlayerPrefs.SetInt("huntix_recipe_uses", usi);
                        if (City.Diagnostics.SessionTelemetry.Instance != null)
                            City.Diagnostics.SessionTelemetry.Instance.Mark(
                                "Ricette: {0} scoperte · {1} usi", scoperte, usi);
                    }
                    Debug.Log("[GameManager] ricette ripristinate da Android");
                }

                // Ultimo mezzo a bordo salvato da Android (formato "tipo|posto"):
                // ripristino passivo, senza salire in auto e senza uova.
                var vehicleMount = System.Text.RegularExpressions.Regex.Match(
                    json, "\"vehicleMount\"\\s*:\\s*\"([^\"]+)\"");
                if (vehicleMount.Success)
                {
                    string[] parts = vehicleMount.Groups[1].Value.Split('|');
                    City.Vehicle.VehicleMountProfile.RestoreFromAndroid(
                        parts[0], parts.Length > 1 ? parts[1] : "inside");
                    Debug.Log("[GameManager] ultimo veicolo ripristinato da Android: " +
                        parts[0]);
                }
            }
            catch (System.Exception e)
            {
                Debug.LogWarning($"[GameManager] HandleSetMode: {e.Message}");
            }
        }

        private void ApplyCitySkin(string skinName)
        {
            PlayerPrefs.SetString(City.Player.PlayerAppearance.PrefKey, skinName);
            Debug.Log($"[GameManager] skin profilo: {skinName}");
            var pc = City.Player.PlayerController.Instance;
            if (pc != null) pc.ApplySkin(skinName);
        }

        private void ApplyPet(string petId)
        {
            if (string.IsNullOrEmpty(petId)) return;
            PlayerPrefs.SetString(City.Player.PetController.PrefKey, petId);
            Debug.Log($"[GameManager] pet profilo: {petId}");
            City.Player.PetController.Spawn(petId, null);
        }

        private void HandleEggCaptured(string jsonData)
        {
            Debug.Log($"[GameManager] Egg captured: {jsonData}");
        }

        private void HandleMVCUpdated(string jsonData)
        {
            Debug.Log($"[GameManager] MVC updated: {jsonData}");
        }

        /// <summary>
        /// Fase 2 · voce 13: riceve da Android l'esca attiva. Il bonus non
        /// viene applicato qui (la cattura resta di competenza Android): Unity
        /// conserva solo lo specchio per HUD/telemetria.
        /// </summary>
        private void HandleActiveBaitChanged(string jsonData)
        {
            var foodId = System.Text.RegularExpressions.Regex.Match(
                jsonData, "\"foodId\"\\s*:\\s*\"([^\"]+)\"");
            var bonus = System.Text.RegularExpressions.Regex.Match(
                jsonData, "\"bonus\"\\s*:\\s*([0-9.]+)");
            float multiplier = 1f;
            if (bonus.Success)
                float.TryParse(bonus.Groups[1].Value, System.Globalization.NumberStyles.Float,
                    System.Globalization.CultureInfo.InvariantCulture, out multiplier);
            string id = foodId.Success ? foodId.Groups[1].Value : string.Empty;
            using (City.Diagnostics.PerfProbe.Measure("bait"))
            {
                PlayerPrefs.SetString("huntix_active_bait", id);
                PlayerPrefs.SetFloat("huntix_active_bait_bonus", multiplier);
                if (City.Diagnostics.SessionTelemetry.Instance != null)
                    City.Diagnostics.SessionTelemetry.Instance.Mark(
                        "Esca {0} · bonus x{1}", string.IsNullOrEmpty(id) ? "nessuna" : id, multiplier);
            }
            Debug.Log("[GameManager] esca da Android: " +
                (string.IsNullOrEmpty(id) ? "nessuna" : id) + " x" + multiplier);
        }

        /// <summary>
        /// Fase 2 · voce 15: Android ha unito tre uova schiuse. Unity non
        /// scrive inventari (la fonte di verita' resta Android) ma conserva
        /// lo specchio del contatore e mostra il messaggio in telemetria.
        /// </summary>
        private void HandleFusionCrafted(string jsonData)
        {
            var name = System.Text.RegularExpressions.Regex.Match(
                jsonData, "\"name\"\\s*:\\s*\"([^\"]+)\"");
            var count = System.Text.RegularExpressions.Regex.Match(
                jsonData, "\"count\"\\s*:\\s*(\\d+)");
            var level = System.Text.RegularExpressions.Regex.Match(
                jsonData, "\"level\"\\s*:\\s*(\\d+)");
            string label = name.Success ? name.Groups[1].Value : "uovo fuso";
            int fusioni = count.Success ? int.Parse(count.Groups[1].Value) : 0;
            int livello = level.Success ? int.Parse(level.Groups[1].Value) : 0;
            using (City.Diagnostics.PerfProbe.Measure("craft"))
            {
                PlayerPrefs.SetInt("huntix_fusion_craft_count", fusioni);
                PlayerPrefs.SetInt("huntix_fusion_craft_last_level", livello);
                if (City.Diagnostics.SessionTelemetry.Instance != null)
                    City.Diagnostics.SessionTelemetry.Instance.Mark(
                        "Fusione {0} · livello {1} · totali {2}", label, livello, fusioni);
                if (City.UI.UIManager.Instance != null)
                    City.UI.UIManager.Instance.ShowToast("🥚 " + label + " creato!");
            }
            Debug.Log("[GameManager] fusione da Android: " + label + " Lv." + livello);
        }

        /// <summary>
        /// Fase 2 · voce 11: specchio della collezione uova Android. Unity non
        /// possiede l'inventario: conserva solo i contatori per HUD/telemetria.
        /// </summary>
        private void HandleEggInventoryChanged(string jsonData)
        {
            var count = System.Text.RegularExpressions.Regex.Match(
                jsonData, "\"inventoryCount\"\\s*:\\s*(\\d+)");
            var team = System.Text.RegularExpressions.Regex.Match(
                jsonData, "\"inventoryTeamCount\"\\s*:\\s*(\\d+)");
            var mode = System.Text.RegularExpressions.Regex.Match(
                jsonData, "\"inventorySortMode\"\\s*:\\s*\"([^\"]+)\"");
            int uova = count.Success ? int.Parse(count.Groups[1].Value) : 0;
            int squadra = team.Success ? int.Parse(team.Groups[1].Value) : 0;
            string ordine = mode.Success ? mode.Groups[1].Value : "recent";
            using (City.Diagnostics.PerfProbe.Measure("inventory"))
            {
                PlayerPrefs.SetInt("huntix_inventory_egg_count", uova);
                PlayerPrefs.SetInt("huntix_inventory_team_count", squadra);
                PlayerPrefs.SetString("huntix_inventory_sort_mode", ordine);
                if (City.Diagnostics.SessionTelemetry.Instance != null)
                    City.Diagnostics.SessionTelemetry.Instance.Mark(
                        "Collezione aggiornata: {0} uova · squadra {1} · ordine {2}",
                        uova, squadra, ordine);
            }
            Debug.Log("[GameManager] collezione da Android: " + uova + " uova, squadra " + squadra);
        }

        /// <summary>
        /// Fase 2 · voce 16: una ricetta è stata usata per la prima volta (o
        /// riutilizzata). Unity conserva solo lo specchio dei contatori; la
        /// scoperta e l'uovo posizionale restano di competenza Android.
        /// </summary>
        private void HandleRecipeDiscoveryChanged(string jsonData)
        {
            var count = System.Text.RegularExpressions.Regex.Match(
                jsonData, "\"recipeDiscoveredCount\"\\s*:\\s*(\\d+)");
            var uses = System.Text.RegularExpressions.Regex.Match(
                jsonData, "\"recipeUses\"\\s*:\\s*(\\d+)");
            var last = System.Text.RegularExpressions.Regex.Match(
                jsonData, "\"recipeLastId\"\\s*:\\s*\"([^\"]+)\"");
            int scoperte = count.Success ? int.Parse(count.Groups[1].Value) : 0;
            int usi = uses.Success ? int.Parse(uses.Groups[1].Value) : 0;
            string ultima = last.Success ? last.Groups[1].Value : "";
            using (City.Diagnostics.PerfProbe.Measure("recipe"))
            {
                PlayerPrefs.SetInt("huntix_recipe_discovered_count", scoperte);
                PlayerPrefs.SetInt("huntix_recipe_uses", usi);
                if (!string.IsNullOrEmpty(ultima))
                    PlayerPrefs.SetString("huntix_recipe_last_id", ultima);
                if (City.Diagnostics.SessionTelemetry.Instance != null)
                    City.Diagnostics.SessionTelemetry.Instance.Mark(
                        "Ricetta {0} · scoperte {1} · usi {2}", ultima, scoperte, usi);
            }
            Debug.Log("[GameManager] ricetta da Android: " + ultima + " (scoperte " + scoperte + ")");
        }

        // ── Esplora: ricezione POI da Android (Overpass) ─────────────
        public void OnPoisReceived(string jsonData)
        {
            Debug.Log($"[GameManager] PoisReceived: {jsonData}");
            var em = ExploreManager.Instance;
            if (em != null) em.OnPoisReceived(jsonData);
        }

        public void OnPoisFailed(string message)
        {
            Debug.LogWarning($"[GameManager] Pois fetch failed: {message}");
            var em = ExploreManager.Instance;
            if (em != null) em.OnPoisFailed(message);
        }

        /// <summary>Avanzamento del caricamento POI a fasi (barra in Esplora).</summary>
        public void OnPoisProgress(string jsonData)
        {
            var em = ExploreManager.Instance;
            if (em != null) em.OnPoisProgress(jsonData);
        }

        public void RequestPois(double lat, double lng, int radiusMeters)
        {
            Debug.Log($"[GameManager] RequestPois({lat},{lng},{radiusMeters}m)");
            UnityBridge.RequestPoisNearby(lat, lng, radiusMeters);
        }

        // ── MiAcitma: città OSM reale (scena City) ──────────────────

        public void RequestOsmCity(double lat, double lng, int radiusMeters)
        {
            if (City.OSM.CityChunkedWorld.Instance != null)
            {
                Debug.Log("[GameManager] mondo chunked attivo: Overpass legacy non richiesto");
                return;
            }
            Debug.Log($"[GameManager] RequestOsmCity({lat},{lng},{radiusMeters}m)");
            UnityBridge.LogToAndroid("GameManager", $"RequestOsmCity({lat},{lng},{radiusMeters}m)");
            UnityBridge.RequestOsmCity(lat, lng, radiusMeters);
        }

        public void OnOsmCityReceived(string json)
        {
            if (City.OSM.CityChunkedWorld.Instance != null) return; // mondo chunked attivo

            Debug.Log($"[GameManager] OsmCityReceived ({json?.Length ?? 0} chars)");
            UnityBridge.LogToAndroid("GameManager", $"OsmCityReceived ({json?.Length ?? 0} chars)");
            if (City.OSM.CityOSMWorld.Instance != null)
                City.OSM.CityOSMWorld.Instance.OnOsmCityReceived(json);
        }

        public void OnOsmCityFetchStarted(string data)
        {
            if (City.OSM.CityChunkedWorld.Instance != null) return; // mondo chunked attivo

            Debug.Log($"[GameManager] OsmCityFetchStarted ({data})");
            if (City.OSM.CityOSMWorld.Instance != null)
                City.OSM.CityOSMWorld.Instance.OnOsmCityFetchStarted(data);
        }

        public void OnOsmCityFailed(string message)
        {
            if (City.OSM.CityChunkedWorld.Instance != null) return; // mondo chunked attivo

            Debug.LogWarning($"[GameManager] OsmCity fetch failed: {message}");
            UnityBridge.LogToAndroid("GameManager", $"OsmCity fetch failed: {message}");
            if (City.OSM.CityOSMWorld.Instance != null)
                City.OSM.CityOSMWorld.Instance.OnOsmCityFailed(message);
        }
    }
}