using UnityEngine;

namespace City.Economy
{
    /// <summary>
    /// Uovo del budget — Riflesso della caccia alle uova applicato alla
    /// fluidita'. Si arma SOLO quando il mondo e' pronto e il frame time
    /// mediato (lo stesso EMA del PerformanceGovernor) resta sotto 33 ms per
    /// 5 secondi consecutivi. Spawna un uovo Epic vicino al player; la cattura
    /// passa dal mini-gioco canonico e dal flusso EggCapturedInCity, senza
    /// sistemi paralleli. Stato e coordinate viaggiano nello snapshot
    /// CityWorldSync verso Android e tornano col restore.
    /// </summary>
    public class BudgetEggWatcher : MonoBehaviour
    {
        private const float BudgetMs = 33f;
        private const float HoldSeconds = 5f;
        private const float CooldownSeconds = 120f;

        public static BudgetEggWatcher Instance { get; private set; }

        // Stato persistente nello snapshot (un solo trofeo per profilo).
        private static bool _spawned;
        private static bool _found;
        private static string _eggId = "";
        private static string _lat = "0";
        private static string _lng = "0";
        private static long _spawnTs;

        private float _goodSeconds;
        private float _cooldown;
        private bool _trackingLogged;
        private GameObject _egg;

        public static bool Spawned { get { return _spawned; } }
        public static bool Found { get { return _found; } }
        public static string EggId { get { return _eggId; } }
        public static string Lat { get { return _lat; } }
        public static string Lng { get { return _lng; } }
        public static long SpawnTs { get { return _spawnTs; } }

        public static BudgetEggWatcher Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("BudgetEggWatcher");
            DontDestroyOnLoad(go);
            return go.AddComponent<BudgetEggWatcher>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
        }

        private void Update()
        {
            if (_cooldown > 0f) _cooldown -= Time.unscaledDeltaTime;

            // Uovo gia' in campo: il player deve catturarlo col mini-gioco.
            if (_egg != null) return;
            // Trofeo gia' assegnato o in attesa: non ne nasce un secondo.
            if (_spawned || _found) return;
            if (!City.Diagnostics.SessionTelemetry.WorldIsReady) return;

            var game = Game.Instance;
            if (game == null || game.player == null || game.IsDriving) return;
            if (EggCaptureMinigame.Instance != null &&
                EggCaptureMinigame.Instance.IsActive) return;

            var gov = City.Diagnostics.PerformanceGovernor.Instance;
            if (gov == null) return;

            float ema = gov.EmaMs;
            if (ema > 0f && ema < BudgetMs)
            {
                if (_goodSeconds <= 0f && !_trackingLogged)
                {
                    _trackingLogged = true;
                    var t0 = City.Diagnostics.SessionTelemetry.Instance;
                    if (t0 != null)
                        t0.Mark("UOVO budget: frame sotto soglia ({0:F1}<33ms), accumulo {1:F0}s",
                            ema, HoldSeconds);
                }
                _goodSeconds += Time.unscaledDeltaTime;
                if (_goodSeconds >= HoldSeconds) Spawn(game, ema);
            }
            else
            {
                _goodSeconds = 0f;
                _trackingLogged = false;
            }
        }

        private void Spawn(Game game, float ema)
        {
            _goodSeconds = 0f;
            _trackingLogged = false;
            _cooldown = CooldownSeconds;

            Vector3 pp = game.player.transform.position;
            Vector3 spot = pp + game.player.transform.forward * 3f +
                           game.player.transform.right * 1.2f;
            Vector3 snapped;
            string surface;
            // Unico snap del progetto, con il layer edifici escluso: l'uovo
            // finisce per strada/suolo, mai sul tetto di un palazzo.
            if (!City.OSM.CityChunkedWorld.TryGroundColumn(spot,
                    game.player.transform, 0.05f, 4f, out snapped, out surface))
            {
                snapped = new Vector3(spot.x,
                    City.OSM.TileElevation.HeightAtWorld(spot) + 0.05f, spot.z);
                surface = "DEM";
            }

            _spawnTs = System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            _eggId = "budget-" + _spawnTs.ToString(
                System.Globalization.CultureInfo.InvariantCulture);
            if (City.OSM.WorldOrigin.Initialized)
            {
                var g = City.OSM.WorldOrigin.ToGeo(snapped);
                _lat = g.lat.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
                _lng = g.lng.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
            }
            _spawned = true;

            var go = new GameObject("Egg_Budget");
            var col = go.AddComponent<SphereCollider>();
            col.radius = 0.5f;
            col.isTrigger = true;
            var egg = go.AddComponent<EggController>();
            egg.Init(snapped, EggController.Rarity.Epic, EggController.EggType.Budget);
            egg.placeHint = "città→budget→frame<33ms→5s";
            // Stessa chiave usata da ContainerEgg.MarkFound: viaggia nello
            // snapshot e impedisce che l'uovo rinasca dopo un rebuild.
            egg.containerEggKey = _eggId;
            _egg = go;

            var tel = City.Diagnostics.SessionTelemetry.Instance;
            if (tel != null)
                tel.Mark("UOVO budget comparso: id={0} lat={1} lng={2} ema={3:F1}ms suolo={4}",
                    _eggId, _lat, _lng, ema, surface);

            SendSpawnEvent(ema, surface);
        }

        /// <summary>Evento Unity → Android: notifica la comparsa del trofeo.
        /// La persistenza resta quella canonica dello snapshot CityStateSync.</summary>
        private static void SendSpawnEvent(float ema, string surface)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            string json = "{\"eggId\":\"" + _eggId +
                "\",\"rarityId\":\"epic\"" +
                ",\"lat\":\"" + _lat + "\",\"lng\":\"" + _lng + "\"" +
                ",\"place\":\"città→budget→frame<33ms→5s\"" +
                ",\"tsMt\":" + _spawnTs +
                ",\"thresholdMs\":33,\"holdSeconds\":5" +
                ",\"emaMs\":" + ema.ToString("F1", System.Globalization.CultureInfo.InvariantCulture) +
                ",\"surface\":\"" + surface + "\"}";
            try { Huntix.Bridge.UnityBridge.SendMessageToAndroid("BudgetEggSpawned", json); }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("BudgetEggSpawned failed: " + e.Message);
            }
#endif
        }

        /// <summary>Chiamato da Game.OnEggCollected: segna il trofeo come trovato.
        /// Il payload di cattura resta quello canonico (EggCapturedInCity).</summary>
        public static void MarkCaptured(EggController egg)
        {
            if (egg == null || egg.eggType != EggController.EggType.Budget) return;
            _found = true;
            _spawned = true;
            if (!string.IsNullOrEmpty(egg.containerEggKey))
                _eggId = egg.containerEggKey;
        }

        /// <summary>Ripristino Android → Unity: se il trofeo era gia' stato
        /// assegnato non deve comparire di nuovo in una citta' rigenerata.</summary>
        public static void ImportState(bool spawned, bool found, string id,
            string lat, string lng, long ts)
        {
            if (!spawned && !found) return;
            _spawned = spawned || found;
            _found = found;
            if (!string.IsNullOrEmpty(id)) _eggId = id;
            if (!string.IsNullOrEmpty(lat)) _lat = lat;
            if (!string.IsNullOrEmpty(lng)) _lng = lng;
            if (ts > 0L) _spawnTs = ts;
        }
    }
}
