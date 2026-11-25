using System.Collections.Generic;
using UnityEngine;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// RIFLESSO UVOVA (Mandato): ogni nuova feature propone la sua
    /// integrazione uova. Qui gli edifici enterabili nascondono un uovo
    /// (tipo Edificio) DENTRO un contenitore della cucina/armadio
    /// (frigo, forno, armadio, credenza, lavello). L'uovo resta celato
    /// fino a quando il giocatore non si avvicina al contenitore: a quel
    /// punto appare (gli renderizzatori si riattivano) e si può raccogliere
    /// con il normale mini-gioco (ricompensa + Bestiario + profilo Huntix).
    ///
    /// Scelta del contenitore DETERMINISTICA per interni (seed dall'istanza
    /// della radice interna): stesso edificio rigenerato → stesso contenitore.
    /// Un solo gestore DontDestroyOnLoad che si aggancia all'ingresso.
    /// </summary>
    public class ContainerEgg : MonoBehaviour
    {
        private static ContainerEgg _instance;

        public static ContainerEgg Instance { get { return _instance; } }

        public static bool Active
        {
            get { return _instance != null; }
        }

        public static void Ensure()
        {
            if (_instance != null) return;
            var go = new GameObject("ContainerEgg");
            DontDestroyOnLoad(go);
            _instance = go.AddComponent<ContainerEgg>();
        }

        /// <summary>GIORNO 8 - riflesso uova: distanza dal contenitore scelto
        /// per l'intero corrente. Sola lettura: NON rivela ne' sposta l'uovo e
        /// NON tocca il mini-gioco. Ritorna MaxValue fuori dagli interni o se
        /// l'edificio non ha un contenitore, cosi' il caller puo' usare la
        /// distanza senza conoscere lo stato interno di ContainerEgg.</summary>
        public static float DistanceToChosen(Vector3 point)
        {
            var inst = _instance;
            if (inst == null || inst._chosen == null) return float.MaxValue;
            return Vector3.Distance(point, inst._chosen.position);
        }

        // Probabilita' che l'edificio abbia l'uovo in un contenitore (50%).
        private const float EggProb = 0.5f;
        private const float RevealDist = 2.4f;

        // Nomi dei contenitori riconosciuti (lowercase, prefix-match).
        private static readonly string[] ContainerNames =
        {
            "frigo", "forno", "armadio", "cassetto", "credenza",
            "lavello", "dispensa", "scrivania",
        };

        private Transform _root;
        private Transform _chosen;
        private GameObject _activeEgg;
        private bool _busy;
        private string _placeHint = "";
        private string _eggKey = "";

        // ── CASE VISITABILI (riflesso uova) ───────────────────────────
        // A differenza degli edifici ordinari (un solo contenitore casuale,
        // 50% di probabilità), una casa configurata ha UNA uovo per ogni
        // nascondiglio dichiarato nel prefab: cassetto cucina, armadio camera,
        // lavandino bagno. Stesso EggController e stesso bridge di cattura.
        private sealed class HouseEggEntry
        {
            public Transform container;
            public GameObject egg;
            public string place = "";
            public bool revealed;
        }
        private readonly List<HouseEggEntry> _houseEggs = new List<HouseEggEntry>();

        private void Update()
        {
            var g = Game.Instance;
            if (g == null || g.player == null) return;
            var mgr = InteriorManager.Instance;
            bool inside = mgr != null && mgr.IsInside;
            Transform root = inside && mgr != null ? mgr.ActiveInteriorRoot : null;

            if (!inside || root == null)
            {
                if (_root != null) ResetState();
                return;
            }
            if (root != _root) BeginBuilding(root);

            // Casa visitabile: più uova, una per contenitore dichiarato. La
            // rivelazione resta proximity-based come nel cassetto ordinario.
            if (_houseEggs.Count > 0)
            {
                for (int i = 0; i < _houseEggs.Count; i++)
                {
                    HouseEggEntry e = _houseEggs[i];
                    if (e == null || e.revealed || e.egg == null ||
                        e.container == null) continue;
                    if (Vector3.Distance(g.player.transform.position,
                            e.container.position) <= RevealDist)
                        RevealHouseEgg(e, g);
                }
                return;
            }

            if (_busy || _chosen == null || _activeEgg== null) return;

            float d = Vector3.Distance(g.player.transform.position, _chosen.position);
            if (d <= RevealDist) Reveal(_chosen, g);
        }

        private void BeginBuilding(Transform root)
        {
            ResetState();
            _root = root;

            // Case visitabili: i nascondigli sono ESPLICITI (tre), non casuali.
            // Il vecchio percorso resta identico per tutti gli altri interni.
            Visit.HouseEggSpot[] houseSpots =
                root.GetComponentsInChildren<Visit.HouseEggSpot>(true);
            if (houseSpots.Length > 0)
            {
                BeginHouseEggs(houseSpots);
                return;
            }

            var containers = new List<Transform>();
            Transform[] all = root.GetComponentsInChildren<Transform>(true);
            for (int i = 0; i < all.Length; i++)
            {
                Transform t = all[i];
                if (t == null) continue;
                string n = t.name.ToLowerInvariant();
                if (n.IndexOf("(clone)") >= 0) n = n.Replace("(clone)", "");
                for (int k = 0; k < ContainerNames.Length; k++)
                {
                    if (n.IndexOf(ContainerNames[k], System.StringComparison.Ordinal) >= 0)
                    {
                        containers.Add(t);
                        break;
                    }
                }
            }
            if (containers.Count == 0)
            {
                _busy = true;
                return;
            }

            var mgr = InteriorManager.Instance;
            BuildingEntrance entrance = mgr != null ? mgr.CurrentEntrance : null;
            int floor = mgr != null ? mgr.GetCurrentFloor() : 0;
            // Seed STABILE: edificio + coordinate + piano. root.GetInstanceID()
            // cambiava a ogni ricostruzione del chunk, quindi lo stesso
            // cassetto poteva ospitare uova diverse in sessioni diverse.
            string buildingSeed = BuildingSeed(entrance, floor, root);
            System.Random rng = new System.Random(StableHash(buildingSeed));

            // Riflesso uova: il cassetto della cucina e' il contenitore
            // preferito. Se l'edificio non ce l'ha (shop/apartment) resta il
            // vecchio pool, senza rimuovere nulla.
            List<Transform> pool = containers;
            for (int i = 0; i < containers.Count; i++)
            {
                if (CleanName(containers[i].name).IndexOf("cassetto", System.StringComparison.Ordinal) < 0) continue;
                pool = new List<Transform>();
                for (int k = 0; k < containers.Count; k++)
                    if (CleanName(containers[k].name).IndexOf("cassetto", System.StringComparison.Ordinal) >= 0)
                        pool.Add(containers[k]);
                break;
            }
            _chosen = pool[rng.Next(pool.Count)];
            _eggKey = BuildingKey(entrance, floor, root, _chosen);
            _placeHint = PlaceHint(entrance, floor, _chosen);

            // Uovo gia' raccolto in questo contenitore (chiave ripristinata da
            // Android via snapshot): non deve rinascere dopo un rebuild.
            if (IsFound(_eggKey))
            {
                _busy = true;
                return;
            }

            if (rng.NextDouble() >= EggProb)
            {
                _busy = true;
                return;
            }

            // F4 (Riflesso uova): in stanza condivisa con un amico la rarita'
            // viene lanciata due volte e si tiene la migliore (fortuna di squadra).
            City.Economy.EggController.Rarity r = RollRarity(rng);
            var mp = City.Multiplayer.MultiplayerManager.Instance;
            if (mp != null && Game.Instance != null && Game.Instance.player != null)
            {
                if (mp.SharedRoomMate(Game.Instance.player.transform.position, 6f) != null)
                {
                    City.Economy.EggController.Rarity r2 = RollRarity(rng);
                    if (r2 > r) r = r2;
                }
            }

            Vector3 pos = _chosen.position + Vector3.up * 0.55f +
                _chosen.forward * 0.35f;
            var go = new GameObject("Egg_Container_" + _chosen.name);
            go.transform.SetParent(_chosen, true);
            var col = go.AddComponent<SphereCollider>();
            col.radius = 0.5f;
            col.isTrigger = true;
            var egg = go.AddComponent<City.Economy.EggController>();
            pos.y = Mathf.Max(pos.y, go.transform.position.y + 0.05f);
            egg.Init(pos, r, City.Economy.EggController.EggType.Edificio);
            egg.placeHint = _placeHint;
            egg.containerEggKey = _eggKey;
            go.SetActive(false); // nascosto fino all'avvicinamento
            _activeEgg = go;
            _busy = false;
        }

        /// <summary>Uno uovo per nascondiglio della casa visitabile. La chiave
        /// featureId è stabile (id OSM + stanza + oggetto), quindi Android la
        /// riconosce nello snapshot dopo un rebuild dei chunk.</summary>
        private void BeginHouseEggs(Visit.HouseEggSpot[] spots)
        {
            for (int i = 0; i < spots.Length; i++)
            {
                var spot = spots[i];
                if (spot == null || string.IsNullOrEmpty(spot.featureId)) continue;
                if (Visit.HouseInteriorState.HasEgg(spot.featureId) ||
                    IsFound(spot.featureId)) continue; // già raccolto, non rinace

                System.Random rng = new System.Random(StableHash(spot.featureId));
                City.Economy.EggController.Rarity rarity = RollRarity(rng);
                Vector3 pos = spot.EggPosition;
                var go = new GameObject("Egg_Casa_" + spot.featureId);
                go.transform.SetParent(spot.transform, true);
                var col = go.AddComponent<SphereCollider>();
                col.radius = 0.5f;
                col.isTrigger = true;
                var egg = go.AddComponent<City.Economy.EggController>();
                egg.Init(pos, rarity, City.Economy.EggController.EggType.Edificio);
                // Luogo testuale canonicale per il profilo Android:
                // "casa→cucina→cassetto2" (e analoghi per camera/bagno).
                egg.placeHint = string.IsNullOrEmpty(spot.place)
                    ? "casa→" + spot.room + "→" + spot.featureId : spot.place;
                egg.containerEggKey = spot.featureId;
                go.SetActive(false); // nascosto finché il player si avvicina

                _houseEggs.Add(new HouseEggEntry
                {
                    container = spot.transform,
                    egg = go,
                    place = egg.placeHint
                });
            }
            // Nessun uovo rimasto: evita di riscrivere lo stato ogni frame.
            if (_houseEggs.Count == 0) _busy = true;
            else
            {
                _busy = false;
                UnityBridge.LogToAndroid(Visit.VisitInteriorRegistry.Tag,
                    "uova casa nascoste: " + _houseEggs.Count +
                    " contenitori (cucina, camera, bagno)");
            }
        }

        private static void RevealHouseEgg(HouseEggEntry entry, Game g)
        {
            entry.revealed = true;
            if (entry.egg == null) return;
            entry.egg.SetActive(true);
            if (g != null && g.ui != null)
                g.ui.ShowToast("In " + entry.place + " c'e' un uovo che luccica!");
            UnityBridge.LogToAndroid(Visit.VisitInteriorRegistry.Tag,
                "uovo rivelato in " + entry.place);
        }

        private static City.Economy.EggController.Rarity RollRarity(System.Random rng)
        {
            double rr = rng.NextDouble();
            if (rr < 0.10) return City.Economy.EggController.Rarity.Legendary;
            if (rr < 0.30) return City.Economy.EggController.Rarity.Epic;
            if (rr < 0.75) return City.Economy.EggController.Rarity.Rare;
            return City.Economy.EggController.Rarity.Uncommon;
        }

        // ── chiave stabile e ponte Android ↔ Unity ──────────────────────

        private const string FoundPrefKey = "huntix_container_eggs";
        private static List<string> _found;

        private static List<string> Found
        {
            get
            {
                if (_found == null)
                {
                    _found = new List<string>();
                    string raw = PlayerPrefs.GetString(FoundPrefKey, "");
                    if (!string.IsNullOrEmpty(raw))
                    {
                        string[] parts = raw.Split(';');
                        for (int i = 0; i < parts.Length; i++)
                            if (!string.IsNullOrEmpty(parts[i]) && !_found.Contains(parts[i]))
                                _found.Add(parts[i]);
                    }
                }
                return _found;
            }
        }

        private static void PersistFound()
        {
            PlayerPrefs.SetString(FoundPrefKey, string.Join(";", Found.ToArray()));
        }

        private static bool IsFound(string key)
        {
            return !string.IsNullOrEmpty(key) && Found.Contains(key);
        }

        /// <summary>Chiamato da Game.OnEggCollected: l'uovo del cassetto e' stato
        /// raccolto. La chiave viaggia nello snapshot verso Android e torna con
        /// il restore, cosi' il cassetto non ne genera un altro.</summary>
        public static void MarkFound(City.Economy.EggController egg)
        {
            if (egg == null || string.IsNullOrEmpty(egg.containerEggKey)) return;
            if (Found.Contains(egg.containerEggKey)) return;
            Found.Add(egg.containerEggKey);
            PersistFound();
            // Case visitabili: la chiave casa-* entra nello snapshot dedicato,
            // oltre al found generico già esportato da CityWorldSync.
            Visit.VisitInteriorManager.OnEggCollected(egg.containerEggKey);
        }

        /// <summary>Elenco delle chiavi gia' raccolte, esportato in CityWorldSync.</summary>
        public static string ExportFound()
        {
            return string.Join(";", Found.ToArray());
        }

        /// <summary>Unione non distruttiva delle chiavi arrivate da Android.</summary>
        public static void ImportFound(string keys)
        {
            if (string.IsNullOrEmpty(keys)) return;
            string[] parts = keys.Split(';');
            bool added = false;
            for (int i = 0; i < parts.Length; i++)
            {
                if (string.IsNullOrEmpty(parts[i]) || Found.Contains(parts[i])) continue;
                Found.Add(parts[i]);
                added = true;
            }
            if (added) PersistFound();
        }

        private static string CleanName(string n)
        {
            if (string.IsNullOrEmpty(n)) return "contenitore";
            string s = n;
            int clone = s.IndexOf("(Clone)", System.StringComparison.OrdinalIgnoreCase);
            if (clone >= 0) s = s.Substring(0, clone);
            return s.Trim();
        }

        private static int StableHash(string s)
        {
            unchecked
            {
                // FNV-1a: uint per poter partire dal primo primo maggiore di
                // Int32.MaxValue senza errori di conversione in mcs/Unity.
                uint h = 2166136261;
                for (int i = 0; i < s.Length; i++)
                {
                    h ^= s[i];
                    h *= 16777619;
                }
                return (int)h;
            }
        }

        private static string BuildingSeed(BuildingEntrance e, int floor, Transform root)
        {
            string name = e != null && !string.IsNullOrEmpty(e.buildingName)
                ? e.buildingName : (root != null ? root.name : "edificio");
            string lat = "0", lng = "0";
            if (e != null && City.OSM.WorldOrigin.Initialized)
            {
                var g = City.OSM.WorldOrigin.ToGeo(e.transform.position);
                lat = g.lat.ToString("F5", System.Globalization.CultureInfo.InvariantCulture);
                lng = g.lng.ToString("F5", System.Globalization.CultureInfo.InvariantCulture);
            }
            return name + "@" + lat + "," + lng + "#f" + floor;
        }

        private static string BuildingKey(BuildingEntrance e, int floor, Transform root, Transform container)
        {
            return BuildingSeed(e, floor, root) + "#" + CleanName(container.name);
        }

        private static string PlaceHint(BuildingEntrance e, int floor, Transform container)
        {
            string name = e != null && !string.IsNullOrEmpty(e.buildingName) ? e.buildingName : "Edificio";
            string c = CleanName(container.name).ToLowerInvariant();
            string thing = c.IndexOf("cassetto", System.StringComparison.Ordinal) >= 0 ? "cassetto2" : c;
            bool kitchen = floor == 0 &&
                (c.Contains("cassetto") || c.Contains("frigo") || c.Contains("forno") || c.Contains("lavello"));
            string room = kitchen ? "cucina" : "piano" + (floor + 1);
            // Interni universali: il contenitore dichiara la stanza reale, così
            // l'indizio e' "città→edificio→banco farmacia→cassetto" invece di
            // un generico "piano1". Nessun cambiamento per i negozi esistenti.
            var marker = container.GetComponent<UniversalContainerMarker>();
            if (marker != null && !string.IsNullOrEmpty(marker.room)) room = marker.room;
            return "città→" + name + "→" + room + "→" + thing;
        }

        private void Reveal(Transform container, Game g)
        {
            if (_activeEgg == null) return;
            _activeEgg.SetActive(true);
            if (g.ui != null)
            {
                string nm = System.Threading.Thread.CurrentThread.CurrentCulture.TextInfo
                    .ToTitleCase(container.name.ToLowerInvariant().Replace("_", " "));
                g.ui.ShowToast("In " + nm + " c'e' qualcosa che luccica...");
            }
            // Log + evento uovo: il luogo testuale arriva ad Android con
            // EggCapturedInCity (place) e finisce in EggWhereLog/profilo.
            UnityBridge.LogToAndroid("ContainerEgg",
                "uovo rivelato in " + (string.IsNullOrEmpty(_placeHint) ? container.name : _placeHint));
            _activeEgg = null; // una sola rivelazione: se raccolto, distrutto dal capture
            _busy = true;
        }

        private void ResetState()
        {
            _root = null;
            _chosen = null;
            _activeEgg = null;
            _busy = false;
            _placeHint = "";
            _eggKey = "";
            // Uova casa non raccolte: distrutte allo stato "fuori" per non
            // duplicarle quando il player rientra nello stesso interno.
            for (int i = 0; i < _houseEggs.Count; i++)
                if (_houseEggs[i] != null && _houseEggs[i].egg != null)
                    UnityEngine.Object.Destroy(_houseEggs[i].egg);
            _houseEggs.Clear();
        }
    }
}
