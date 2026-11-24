using UnityEngine;
using UnityEngine.SceneManagement;
using System;
using Huntix.Bridge;
using Huntix.Outdoor;
using Huntix.UI;

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
        }

        private void Start()
        {
            UnityBridge.Init();

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
                case "MVCUpdated":
                    HandleMVCUpdated(jsonData);
                    break;
                case "setTargetCoord":
                    City.Multiplayer.MultiplayerManager.SetTargetCoords(jsonData);
                    break;
                case "ReturnToMenu":
                    ReturnToAndroid();
                    break;
            }
        }

        private void HandleSetMode(string json)
        {
            try
            {
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