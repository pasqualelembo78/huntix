using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using City.OSM;
using City.UI;

namespace City.Events
{
    /// <summary>
    /// Direttore degli impianti sportivi. Aggiunto ai chunk dal ChunkBuilder:
    /// registra solo un'ancora leggera per chunk; la geometria viene costruita
    /// in coroutine SOLO quando il player si avvicina, con un solo impianto
    /// attivo alla volta. Le ancore vengono dal registro OSM reale
    /// (VenueRegistry): niente piu' stadi a caso per hash di chunk. Nessun
    /// campo nuovo nel wire geo/graph, nessun cambio di CacheVersion/scena.
    /// </summary>
    public class VenueDirector : MonoBehaviour
    {
        private class Site
        {
            public ChunkData Chunk;
            public VenueRegistry.Spec Spec;
            public string Id;
            public VenueKind Kind;
            public double Lat;
            public double Lng;
            // Offset geografico rispetto al centro del chunk: resta valido
            // dopo il rebase perche' chunk root e coordinate world si
            // spostano insieme.
            public Vector3 LocalOffset;
            public Vector3 Center;
            public bool Built;
            public bool Building;
            public bool EggsGranted;
            public GameObject Root;
            public VenueSimulation Sim;
        }

        private const float BUILD_RADIUS = 115f;
        private const float SIM_RADIUS = 220f;
        private const float PROMPT_RADIUS = 17f;
        private const int MAX_ACTIVE = 1;

        public static VenueDirector Instance { get; private set; }

        /// <summary>Nuova funzionalità: default OFF per non cambiare il
        /// comportamento e i costi del mondo esistente. Il menu la attiva.</summary>
        public static bool EventsEnabled
        {
            get { return PlayerPrefs.GetInt("huntix_venue_events_enabled", 0) == 1; }
            set { PlayerPrefs.SetInt("huntix_venue_events_enabled", value ? 1 : 0); PlayerPrefs.Save(); }
        }

        private readonly List<Site> _sites = new List<Site>();
        private float _tick;
        private bool _promptShown;
        private Site _near;

        public static VenueDirector Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("VenueDirector");
            Instance = go.AddComponent<VenueDirector>();
            return Instance;
        }

        private void Awake()
        {
            Instance = this;
        }

        /// <summary>Chiamato dal ChunkBuilder a chunk costruito: registra gli
        /// impianti il cui baricentro OSM cade DENTRO questo chunk. La citta'
        /// ha un solo stadio/ippodromo principale perche' cosi' e' generato
        /// il registro; qui non si inventa piu' nulla per hash del chunk.</summary>
        public void OnChunkBuilt(ChunkData chunk)
        {
            if (chunk == null || chunk.root == null || chunk.geo == null) return;
            List<VenueRegistry.Spec> specs = VenueRegistry.InChunk(chunk.index);
            if (specs == null || specs.Count == 0) return;

            // Niente filtro "hasLand": l'impronta OSM e' gia' la prova che
            // li' c'e' un impianto, anche se il wire filtrato non trasporta
            // il tag leisure=stadium (che infatti non e' in FILTERS).
            for (int i = 0; i < specs.Count; i++)
            {
                VenueRegistry.Spec spec = specs[i];
                VenueKind kind = KindOf(spec.kind);
                if (kind == VenueKind.Stadio && spec.kind != VenueRegistry.KindStadio)
                    continue;
                if (kind == VenueKind.Ippodromo && spec.kind != VenueRegistry.KindIppodromo)
                    continue;

                lock (_sites)
                {
                    bool exists = false;
                    for (int j = 0; j < _sites.Count; j++)
                        if (_sites[j].Id == spec.id) { exists = true; break; }
                    if (exists) continue;
                }

                Vector3 world = WorldOrigin.ToWorld(spec.lat, spec.lng);
                Vector3 local = world - chunk.root.transform.position;
                local.y = 0f;
                if (!WorldOrigin.IsFinite(local)) continue;
                world.y = TileElevation.HeightAtWorld(world);
                _sites.Add(new Site
                {
                    Chunk = chunk,
                    Spec = spec,
                    Id = spec.id,
                    Kind = kind,
                    Lat = spec.lat,
                    Lng = spec.lng,
                    LocalOffset = local,
                    Center = world
                });
            }
        }

        private static VenueKind KindOf(string kind)
        {
            return kind == VenueRegistry.KindIppodromo
                ? VenueKind.Ippodromo : VenueKind.Stadio;
        }

        private void Update()
        {
            _tick -= Time.deltaTime;
            if (_tick > 0f) return;
            _tick = 0.5f;

            var game = Game.Instance;
            if (game == null || game.player == null) return;
            if (!EventsEnabled)
            {
                // I sistemi esistenti restano intatti: nessun motivo di
                // aggiornare o mantenere attivi gli impianti se disattivati.
                for (int i = 0; i < _sites.Count; i++)
                    if (_sites[i].Root != null) _sites[i].Root.SetActive(false);
                if (_promptShown && UIManager.Instance != null) UIManager.Instance.HideInteract();
                _promptShown = false;
                return;
            }
            Vector3 player = game.player.transform.position;

            // Rimuove le ancore dei chunk scaricati (chunk.root distrutto).
            for (int i = _sites.Count - 1; i >= 0; i--)
            {
                if (_sites[i].Chunk == null || _sites[i].Chunk.root == null)
                    _sites.RemoveAt(i);
            }

            Site nearest = null;
            float best = float.MaxValue;
            int active = 0;
            for (int i = 0; i < _sites.Count; i++)
            {
                Site s = _sites[i];
                // Ricalcola dal georeferimento: dopo un rebase l'origine si
                // sposta e il vecchio Center world non sarebbe piu' valido.
                Vector3 world = WorldOrigin.ToWorld(s.Lat, s.Lng);
                world.y = TileElevation.HeightAtWorld(world);
                s.Center = world;
                float d = Vector3.Distance(player, s.Center);
                // Conta solo gli impianti realmente in gioco vicino al
                // player: contare tutti quelli mai costruiti in qualsiasi
                // citta' bloccherebbe per sempre la costruzione del primo
                // impianto visitato (MAX_ACTIVE = 1).
                if (s.Built && d < SIM_RADIUS) active++;
                if (s.Root != null) s.Root.SetActive(d < SIM_RADIUS);
                if (d < best) { best = d; nearest = s; }
                if (!s.Built && !s.Building && d < BUILD_RADIUS && active < MAX_ACTIVE)
                {
                    s.Building = true;
                    active++;
                    StartCoroutine(BuildRoutine(s));
                }
            }

            _near = nearest != null && best < PROMPT_RADIUS ? nearest : null;
            UpdatePrompt(game);
        }

        private void UpdatePrompt(Game game)
        {
            if (_near == null || _near.Sim == null)
            {
                if (_promptShown && UIManager.Instance != null) UIManager.Instance.HideInteract();
                _promptShown = false;
                return;
            }
            // Non sovrascrive mai un prompt più importante (porta, veicolo, POI).
            if (game.CurrentDoor != null || game.CurrentVehicleFocus != null ||
                game.CurrentMissionNPC != null || game.CurrentPoiZone != null ||
                game.CurrentEntrance != null)
            {
                if (_promptShown && UIManager.Instance != null) UIManager.Instance.HideInteract();
                _promptShown = false;
                return;
            }
            string label = _near.Sim.Phase == VenuePhase.InCorso
                ? "Ferma " + (_near.Kind == VenueKind.Stadio ? "la partita" : "la corsa")
                : "Avvia " + (_near.Kind == VenueKind.Stadio ? "la partita" : "la corsa");
            if (UIManager.Instance != null) UIManager.Instance.ShowInteract(label);
            _promptShown = true;
        }

        /// <summary>Punto d'ingresso del pulsante contestuale, aggiunto a
        /// Game.OnInteractPressed con priorità più bassa.</summary>
        public static bool TryInteractNearby()
        {
            var d = Instance;
            if (d == null || d._near == null || d._near.Sim == null) return false;
            var sim = d._near.Sim;
            bool stop = sim.Phase == VenuePhase.InCorso || sim.Phase == VenuePhase.Intervallo;
            sim.Toggle();
            if (UIManager.Instance != null)
                UIManager.Instance.ShowToast(stop ? "Evento sospeso" : "Evento avviato");
            return true;
        }

        private IEnumerator BuildRoutine(Site s)
        {
            // Un yield per groups: il build non blocca mai un frame intero.
            yield return null;
            if (s.Chunk == null || s.Chunk.root == null) { s.Building = false; yield break; }

            var root = new GameObject("Venue_" + s.Id);
            root.transform.SetParent(s.Chunk.root.transform, false);
            float baseY = s.Center.y - s.Chunk.root.transform.position.y;
            // Il modello va sul baricentro OSM, non al centro del chunk:
            // e' la differenza fra "uno stadio da qualche parte" e lo
            // stadio reale all'incrocio Via Gioberti/Viale Ofanto.
            root.transform.localPosition = new Vector3(
                s.LocalOffset.x, baseY, s.LocalOffset.z);
            s.Root = root;

            var sim = root.AddComponent<VenueSimulation>();
            sim.Configure(s.Kind, s.Center, s.Id);
            s.Sim = sim;

            // Un solo Audio per impianto, non uno per spettatore/cavallo.
            var audioGo = new GameObject("Audio");
            audioGo.transform.SetParent(root.transform, false);
            var audio = audioGo.AddComponent<VenueAudio>();
            audio.Setup(audioGo.transform);
            sim.Audio = audio;

            if (s.Kind == VenueKind.Stadio) BuildStadium(root, sim);
            else BuildRacecourse(root, sim);

            s.Built = true;
            s.Building = false;
            sim.Begin();
            GrantEggs(s);
            if (s.Spec != null && UIManager.Instance != null)
                UIManager.Instance.ShowToast(s.Spec.name + " — " + s.Spec.city);
        }

        private static GameObject Place(GameObject root, string folder, string name,
            Vector3 local, float yaw, float scale)
        {
            string path = "EventVenues/" + folder + "/" + name;
            var prefab = Resources.Load<GameObject>(path);
            if (prefab == null)
            {
                OsmDiag.Log("[VenueDirector] modello mancante: " + path);
                return null;
            }
            var go = Instantiate(prefab, root.transform);
            go.name = name;
            go.transform.localPosition = local;
            go.transform.localRotation = Quaternion.Euler(0f, yaw, 0f);
            go.transform.localScale = Vector3.one * scale;
            VenueMaterials.Apply(go);
            return go;
        }

        /// <summary>Giocatori ANIMATI: si riutilizza l'eroe già rigged del
        /// progetto (Animator con controller Mixamo), non una figurina statica.
        /// Il prefab contiene solo Animator e modello: nessun CharacterController,
        /// nessun collider e nessuno script di gameplay. Fallback CC0 se assente.</summary>
        private static GameObject SpawnPlayer(GameObject root, Vector3 local, float yaw)
        {
            var hero = Resources.Load<GameObject>("PlayerHero");
            if (hero == null)
                return Place(root, "people", "PlayerFigure", local, yaw, 1f);
            var go = Instantiate(hero, root.transform);
            go.name = "GiocatoreAnimato";
            go.transform.localPosition = local;
            go.transform.localRotation = Quaternion.Euler(0f, yaw, 0f);
            go.transform.localScale = Vector3.one;
            return go;
        }

        /// <summary>Le sei clip del cavallo CC BY vengono caricate come
        /// under-asset dell'FBX e suonate con il componente legacy Animation:
        /// nessun AnimatorController generato a runtime, nessuna fisica.</summary>
        private static void AttachHorseClips(GameObject go)
        {
            if (go == null) return;
            var clips = Resources.LoadAll<AnimationClip>("EventVenues/horses");
            if (clips == null || clips.Length == 0) return;
            var anim = go.GetComponent<Animation>();
            if (anim == null) anim = go.AddComponent<Animation>();
            anim.playAutomatically = false;
            anim.wrapMode = WrapMode.Loop;
            for (int i = 0; i < clips.Length; i++)
            {
                if (clips[i] == null) continue;
                // Copia: non si modifica l'asset importato condiviso.
                var clip = Instantiate(clips[i]);
                clip.name = clips[i].name;
                clip.legacy = true;
                clip.wrapMode = WrapMode.Loop;
                anim.AddClip(clip, clip.name);
            }
            anim.Play("Idle");
        }

        private void BuildStadium(GameObject root, VenueSimulation sim)
        {
            Place(root, "stadium", "Pitch", new Vector3(0, 0.02f, 0), 0f, 1f);
            Place(root, "stadium", "FullSizeOpenNetSoccerGoal", new Vector3(52.5f, 0, 0), 90f, 1f);
            Place(root, "stadium", "FullSizeOpenNetSoccerGoal", new Vector3(-52.5f, 0, 0), -90f, 1f);
            for (int i = -3; i <= 3; i++)
            {
                float x = i * 16f;
                Place(root, "stadium", "ThreeRowSeatedStandBay", new Vector3(x, 0, 40f), 180f, 1f);
                Place(root, "stadium", "CantileverStandRoofBay", new Vector3(x, 6.4f, 40f), 180f, 1f);
                Place(root, "stadium", "ThreeRowSeatedStandBay", new Vector3(x, 0, -40f), 0f, 1f);
                Place(root, "stadium", "CantileverStandRoofBay", new Vector3(x, 6.4f, -40f), 0f, 1f);
            }
            for (int i = 0; i < 4; i++)
            {
                float a = i * Mathf.PI * 0.5f + Mathf.PI * 0.25f;
                Place(root, "stadium", "FourLampFloodlightMast",
                    new Vector3(Mathf.Cos(a) * 82f, 0, Mathf.Sin(a) * 58f), 0f, 1f);
            }
            Place(root, "stadium", "ClubNeutralElevatedScoreboard", new Vector3(0, 0, 52f), 180f, 1f);
            Place(root, "stadium", "SixSeatTeamDugout", new Vector3(0, 0, 36f), 180f, 1f);
            Place(root, "stadium", "GroundEntranceTicketBooth", new Vector3(-92f, 0, 0), 90f, 1f);
            Place(root, "stadium", "TripodEntranceTurnstile", new Vector3(-86f, 0, 0), 90f, 1f);
            // Spogliatoi: gli stessi oggetti del vecchio internale, ora con uova.
            Place(root, "stadium", "OpenVentilatedKitLocker", new Vector3(70f, 0, -42f), 0f, 1f);
            Place(root, "stadium", "ThreePlaceTeamBench", new Vector3(73f, 0, -42f), 0f, 1f);
            Place(root, "stadium", "FreestandingShowerColumn", new Vector3(76f, 0, -42f), 0f, 1f);
            var ball = Place(root, "stadium", "FacetedPanelSoccerBall", new Vector3(0, 0.12f, 0), 0f, 1f);
            sim.Ball = ball != null ? ball.transform : null;

            int players = 10;
            for (int i = 0; i < players; i++)
            {
                var go = SpawnPlayer(root, new Vector3(24f, 0, 0), 0f);
                sim.AddActor(go, false, i, players);
            }
            for (int i = 0; i < 8; i++)
            {
                string prefab = i % 2 == 0 ? "SeatedFigure" : "SeatedOldFigure";
                Place(root, "people", prefab,
                    new Vector3(-48f + i * 14f, 1.2f, 41f), 180f, 1f);
            }
        }

        private void BuildRacecourse(GameObject root, VenueSimulation sim)
        {
            for (int i = 0; i < 6; i++)
            {
                Place(root, "racecourse", "TurfTrackStraight", new Vector3(i * 12f - 30f, 0, 0), 0f, 1f);
                Place(root, "racecourse", "RunningRailRun", new Vector3(i * 12f - 30f, 0, 9f), 0f, 1f);
            }
            Place(root, "racecourse", "StartingStallsSixBay", new Vector3(-160f, 0, 0), 0f, 1f);
            Place(root, "racecourse", "TapeStartBarrier", new Vector3(-150f, 0, 0), 0f, 1f);
            Place(root, "racecourse", "WinningPost", new Vector3(95f, 0, 0), 0f, 1f);
            Place(root, "racecourse", "ResultsBoard", new Vector3(0, 0, 70f), 180f, 1f);
            Place(root, "racecourse", "BigScreenFrame", new Vector3(30f, 0, 82f), 180f, 1f);
            Place(root, "racecourse", "GrandstandTierLow", new Vector3(0, 0, 58f), 180f, 1f);
            Place(root, "racecourse", "GrandstandTierHigh", new Vector3(0, 3.2f, 68f), 180f, 1f);
            Place(root, "racecourse", "GrandstandRoofCanopy", new Vector3(0, 9f, 64f), 180f, 1f);
            // Parade ring / box / sellerie: luoghi delle uova posizionali.
            Place(root, "racecourse", "ParadeRingRail", new Vector3(-60f, 0, -65f), 0f, 1f);
            Place(root, "racecourse", "ParadeRingLawn", new Vector3(-60f, 0, -65f), 0f, 1f);
            Place(root, "racecourse", "SaddlingBoxRun", new Vector3(-25f, 0, -72f), 0f, 1f);
            Place(root, "racecourse", "WeighingRoomFront", new Vector3(10f, 0, -78f), 0f, 1f);
            Place(root, "racecourse", "JockeysBench", new Vector3(14f, 0, -76f), 0f, 1f);
            Place(root, "stable", "StallBayWithDoor", new Vector3(45f, 0, -82f), 0f, 1f);
            Place(root, "stable", "SaddleOnRack", new Vector3(49f, 0, -82f), 0f, 1f);
            for (int i = 0; i < 4; i++)
            {
                Place(root, "racecourse", "Hurdle", new Vector3(i * 34f - 30f, 0, -28f), 0f, 1f);
                Place(root, "people", "StandFigure",
                    new Vector3(-12f + i * 8f, 2f, 59f), 180f, 1f);
            }

            int horses = 4;
            for (int i = 0; i < horses; i++)
            {
                var go = Place(root, "horses", "RaceHorseAnimated",
                    new Vector3(-150f + i * 4f, 0, i * 3f - 4f), 90f, 1f);
                AttachHorseClips(go);
                sim.AddActor(go, true, i, horses);
            }
            Place(root, "stable", "RidingHorseStanding", new Vector3(-58f, 0, -64f), 0f, 1f);
            Place(root, "stable", "RidingHorseGrazing", new Vector3(-62f, 0, -68f), 0f, 1f);
        }

        private static void GrantEggs(Site s)
        {
            if (s.EggsGranted || Game.Instance == null) return;
            s.EggsGranted = true;
            string prefs = "huntix_venue_eggs_" + s.Id;
            if (PlayerPrefs.GetInt(prefs, 0) == 1) return;
            PlayerPrefs.SetInt(prefs, 1);
            PlayerPrefs.Save();

            Game g = Game.Instance;
            if (s.Kind == VenueKind.Stadio)
            {
                g.GrantCompanionEgg("stadio-spogliatoio", "Uovo nello spogliatoio",
                    s.Center + new Vector3(73f, 0.6f, -42f), "stadio→spogliatoio");
            }
            else
            {
                g.GrantCompanionEgg("ippodromo-box3", "Uovo nel box 3",
                    s.Center + new Vector3(45f, 0.6f, -82f), "ippodromo→box 3");
                g.GrantCompanionEgg("ippodromo-parade", "Uovo nel parade ring",
                    s.Center + new Vector3(-60f, 0.6f, -65f), "ippodromo→parade ring");
            }
        }
    }
}
