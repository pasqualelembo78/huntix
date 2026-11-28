// Mandato §4 · Riflesso della caccia alle uova: ogni ambiente nuovo nasconde
// uova con luogo testuale, coordinate reali della porta e memoria Android.
// Mandato §3 · bridge bidirezionale: le scoperte viaggiano su CityGlobalActivity
// (diario + contatore "scoperte") e le chiavi uovo/zone tornano a Unity nello
// snapshot HouseVisitState già esistente. Nessun sistema preesistente rimosso.
using System.Collections.Generic;
using System.Globalization;
using TMPro;
using UnityEngine;
using City.Diagnostics;
using City.Interior.Visit;
using Huntix.Bridge;

namespace City.Interior.Victorian
{
    /// <summary>Tasca "TARDIS": la casa resta minuscola fuori, ma varcando la
    /// porta si entra in un salone vittoriano enorme con lampadari, biblioteca,
    /// sala da pranzo e una piazzetta che sembra una città. La tasca è figlia
    /// dell'edificio, sta 1000 m sotto il mondo (nessuna interferenza con DEM,
    /// chunk o strade) e viene costruita LAZY insieme all'interno ordinario.
    /// </summary>
    public sealed class VictorianPocket : MonoBehaviour
    {
        public InteriorGenerator owner;
        public string key = "";
        public Vector3 doorWorld;
        public Vector3 doorForward = Vector3.forward;
        public Vector3 entryLocal = new Vector3(0f, -999f, 42f);
        public Vector3 exitLocal = new Vector3(0f, -998.6f, 51f);
        // Case configurate: usano la villa da 300 mq / 3 piani; le altre
        // conservano la reggia TARDIS storica (nessun sistema rimosso).
        public bool grand = false;
        public long osmId = 0L;

        /// <summary>Interno dedicato dell'Asset Store (percorso Resources).
        /// Quando e' impostato la tasca NON usa la villa HUNTIX da 300 mq:
        /// apre il ramo `mainIndoor01` originale della casa Сountry House FREE.
        /// Le altre case configurate restano sulla villa 300 mq.</summary>
        public string interiorPrefab = "";

        public bool AssetInterior
        {
            get { return !string.IsNullOrEmpty(interiorPrefab); }
        }

        private void OnDisable()
        {
            // Chunk scaricato mentre il player è dentro: riporta il giocatore
            // sulla porta prima che la tasca sparisca, senza lasciarlo cadere.
            if (VictorianGrandInterior.ActivePocket != this) return;
            VictorianGrandInterior.EmergencyExit(this);
        }
    }

    /// <summary>Zona visitabile della casa vittoriana. Ogni zona ha un luogo
    /// testuale e un id stabile: la prima visita diventa una voce Android con
    /// coordinate (la porta della casa) e un indizio uovo una tantum.</summary>
    public sealed class VictorianZone : MonoBehaviour
    {
        public string featureId = "";
        public string label = "";
        public string place = "";
        public float radius = 10f;
    }

    /// <summary>Uscita dalla tasca: si torna davanti alla vera porta OSM.</summary>
    public sealed class VictorianExit : MonoBehaviour
    {
        // Il punto d'ingresso della casa country (z=5,15) sovrappone la capsula
        // del player al trigger d'uscita (z=5,85 ± 0,4): senza grazia si usciva
        // 48 ms dopo essere entrati (log 28/11/2026).
        private void OnTriggerEnter(Collider other)
        {
            if (other == null || !other.CompareTag("Player")) return;
            var mgr = InteriorManager.Instance;
            if (mgr != null && mgr.CurrentEntrance != null &&
                mgr.CurrentEntrance.NaturalEntryGraceActive)
            {
                // Sovrapposizione creata dal teleporto d'ingresso, non una
                // camminata verso la porta: resta dentro.
                return;
            }
            VictorianGrandInterior.Exit();
        }

        private void OnTriggerExit(Collider other)
        {
            if (other == null || !other.CompareTag("Player")) return;
            // Il player può essere già dentro il volume quando arriva dal
            // teleporto: in quel caso OnTriggerEnter non è un'uscita, ma
            // attraversare il volume verso l'ESTERNO (+Z locale) sì.
            Vector3 away = other.transform.position - transform.position;
            if (Vector3.Dot(away, transform.forward) > 0f)
                VictorianGrandInterior.Exit();
        }
    }

    public static class VictorianGrandInterior
    {
        public const string Tag = "CasaVittoriana";
        public const string CameraOwner = "vittoriano";
        private const float PocketDrop = 1000f;

        // Villa completa composta con i modelli SENDU già licenziati e
        // spediti nell'APK. Se il prefab non è presente resta il fallback
        // primitivo: nessuna casa resta vuota.
        private const string VillaPrefabResource = "Villas/VillaVittorianaCompleta";

        // Nuova villa da 300 mq: 3 piani x 100 mq (10 x 10 m), altezza 10,5 m.
        // Viene preferita SOLO per le case configurate; il prefab storico e il
        // fallback primitivo restano intatti per tutte le altre.
        private const string GrandVillaPrefabResource = "Villas/Villa300mqTreLivelli";
        public const int GrandFloors = 3;
        public const float GrandFloorHeight = 3.5f;
        public const float GrandSide = 10f;
        public const float GrandAreaPerFloor = 100f;
        public const float GrandTotalArea = 300f;

        private static readonly Dictionary<string, Material> Mats =
            new Dictionary<string, Material>();

        private static VictorianPocket _active;

        public static VictorianPocket ActivePocket { get { return _active; } }
        public static bool IsInsidePocket { get { return _active != null; } }
        public static Transform ActiveRoot { get { return _active != null ? _active.transform : null; } }

        /// <summary>Invocato in coda a InteriorGenerator.BuildInterior, solo per
        /// casa/appartamento. Non tocca i builder esistenti: aggiunge una tasca
        /// sorella del Floor_0, idempotente grazie al marker VictorianPocket.</summary>
        public static void Decorate(Transform parent, string type,
            float w, float d, float floorH, int floors, string buildingName)
        {
            if (parent == null) return;
            if (type != "house" && type != "apartment") return;
            VictorianPocket[] old = parent.GetComponentsInChildren<VictorianPocket>(true);
            if (old != null && old.Length > 0) return;

            var gen = parent.GetComponentInParent<InteriorGenerator>();
            if (gen == null) gen = parent.gameObject.GetComponent<InteriorGenerator>();
            BuildingEntrance entrance = gen != null ? gen.Entrance : null;
            if (entrance == null && gen != null)
                entrance = gen.GetComponentInChildren<BuildingEntrance>(true);

            Vector3 door = entrance != null ? entrance.transform.position : parent.position;
            Vector3 forward = entrance != null ? entrance.transform.forward : parent.forward;
            string key = StableKey(entrance, door, buildingName);
            string label = string.IsNullOrEmpty(buildingName) ? "Casa" : buildingName;
            long osmId = entrance != null ? entrance.osmId : 0L;
            bool grand = osmId != 0L &&
                City.Interior.Visit.VisitInteriorRegistry.IsConfigured(osmId);
            string assetInterior = "";
            if (osmId != 0L)
                City.Interior.Visit.VisitInteriorRegistry.TryGetInteriorPrefab(
                    osmId, out assetInterior);

            // Centro della tasca in world-space: sotto la casa, non dentro al
            // guscio. La radice è figlia del GENERATORE, non del Floor_0: con
            // gli esterni prefab InteriorGenerator disattiva "_interiorRoot"
            // dopo aver costruito l'interno. Se la tasca fosse sua figlia,
            // resterebbe invisibile anche dopo SetActive(true) sull'ingresso.
            // Così si scarica comunque col chunk, ma si accende da sola.
            Vector3 center = new Vector3(door.x, door.y - PocketDrop, door.z);
            var go = new GameObject("CasaVittoriana_TARDIS");
            go.transform.position = center;
            go.transform.rotation = Quaternion.identity;
            go.transform.localScale = Vector3.one;
            Transform anchor = gen != null ? gen.transform : parent;
            go.transform.SetParent(anchor, true);

            var pocket = go.AddComponent<VictorianPocket>();
            pocket.owner = gen;
            pocket.key = key;
            pocket.doorWorld = door;
            pocket.doorForward = forward;
            pocket.osmId = osmId;
            pocket.grand = grand;
            pocket.interiorPrefab = assetInterior;
            // La casa Asset Store e' un piano solo (~13,5 x 10,5 m): si entra
            // dall'atrio sul lato +Z, davanti alla porta d'ingresso dell'asset.
            // La villa HUNTIX da 300 mq resta compatta (10 x 10 m per piano)
            // per tutte le altre case configurate.
            bool useAssetInterior = !string.IsNullOrEmpty(assetInterior);
            pocket.entryLocal = useAssetInterior ? new Vector3(0f, 1.10f, 5.15f)
                : grand ? new Vector3(0f, 1.15f, 3.2f)
                        : new Vector3(0f, 1.1f, 42f);
            pocket.exitLocal = useAssetInterior ? new Vector3(0f, 1.10f, 5.85f)
                : grand ? new Vector3(0f, 1.15f, 4.5f)
                        : new Vector3(0f, 1.1f, 51f);

            BuildVilla(go.transform, pocket, label);
            go.SetActive(false); // nessun costo finché il player non entra

            try
            {
                UnityBridge.LogToAndroid(Tag,
                    "casa vittoriana pronta: " + label + " (" + key + ") — " +
                    (!string.IsNullOrEmpty(assetInterior)
                        ? "interno originale Asset Store (mainIndoor01, 9 uova)"
                        : grand ? "villa 300 mq in 3 piani" +
                                  " (fuori tre livelli, dentro 100 mq per piano)"
                                : "fuori piccola, dentro salone + città interna"));
            }
            catch (System.Exception) { }
        }

        /// <summary>Ingresso additivo chiamato da InteriorManager.MarkInside.
        /// Se la porta ha una tasca vittoriana, il player viene portato dentro
        /// con lo stesso teleporto helper usato dagli altri interni.</summary>
        public static void TryEnter(InteriorManager mgr, BuildingEntrance e)
        {
            if (_active != null || e == null) return;
            var gen = e.GetComponentInParent<InteriorGenerator>();
            if (gen == null) return;
            if (!gen.InteriorBuilt) gen.BuildInteriorNow();
            VictorianPocket pocket = FindPocket(gen);
            if (pocket == null) return;

            _active = pocket;
            // SetActive(true) sulla tasca non basta se un antenato è stato
            // disattivato (Floor_0 dei prefab): risaliamo la catena e lo
            // riaccendiamo, senza toccare gli altri interni del chunk.
            for (Transform t = pocket.transform.parent; t != null; t = t.parent)
                if (!t.gameObject.activeSelf) t.gameObject.SetActive(true);
            pocket.gameObject.SetActive(true);
            var g = Game.Instance;
            if (g != null)
            {
                Vector3 pos = pocket.transform.TransformPoint(pocket.entryLocal);
                InteriorTeleport.Move(pos, Quaternion.Euler(0f, 180f, 0f), g);
                // La casa Asset Store e' a piano unico: camera GTA piu' vicina,
                // stessa logica di override degli altri interni. La chiamata
                // storica (7f, 3.4f, 16f) resta il percorso predefinito.
                if (g.rig != null && !pocket.AssetInterior)
                    g.rig.SetIndoorOverride(true, CameraOwner, 7f, 3.4f, 16f);
                else if (g.rig != null)
                    g.rig.SetIndoorOverride(true, CameraOwner, 5f, 2.4f, 18f);
                if (g.ui != null)
                    g.ui.ShowToast(pocket.AssetInterior
                        ? "Sei dentro la casa dell'Asset Store: esterno e interni originali!"
                        : pocket.grand
                            ? "Sei nella villa da 300 mq: tre piani da 100 mq!"
                            : "Sei nella Casa Vittoriana: dentro è molto più grande di fuori!");
            }
            Discover(pocket,
                pocket.AssetInterior ? "atrio-country" :
                    pocket.grand ? "salotto-piano1" : "salone",
                pocket.AssetInterior ? "Atrio della casa country" :
                    pocket.grand ? "Salone del piano 1" : "Salone dei lampadari",
                "città→casa→" + pocket.key + "→" +
                (pocket.AssetInterior ? "atrio dell'interno Asset Store" :
                    pocket.grand ? "salone al piano 1" : "salone dei lampadari"));
            try
            {
                UnityBridge.LogToAndroid(Tag, "ingresso tasca vittoriana: " + pocket.key);
            }
            catch (System.Exception) { }
        }

        /// <summary>Uscita esplicita (trigger o pulsante): ritorno davanti alla
        /// porta reale, poi lo stato interno torna "fuori" col flusso normale.</summary>
        public static void Exit()
        {
            VictorianPocket pocket = _active;
            if (pocket == null) return;
            var g = Game.Instance;
            if (g != null)
            {
                Vector3 outside = pocket.doorWorld + pocket.doorForward * 2.4f;
                outside.y = pocket.doorWorld.y + 1f;
                InteriorTeleport.Move(outside,
                    Quaternion.LookRotation(pocket.doorForward, Vector3.up), g);
            }
            _active = null; // evita il OnDisable di richiamare l'uscita
            ClearCamera();
            pocket.gameObject.SetActive(false);
            var mgr = InteriorManager.Instance;
            if (mgr != null) mgr.MarkOutside(mgr.CurrentEntrance);
            try { UnityBridge.LogToAndroid(Tag, "uscita tasca vittoriana: " + pocket.key); }
            catch (System.Exception) { }
        }

        /// <summary>Chunk scaricato: stessa uscita, ma senza dipendere da un
        /// ingresso ancora valido (InteriorManager fa il reset di sicurezza).</summary>
        public static void EmergencyExit(VictorianPocket pocket)
        {
            if (pocket == null || _active != pocket) return;
            var g = Game.Instance;
            if (g != null)
            {
                Vector3 outside = pocket.doorWorld + pocket.doorForward * 2.4f;
                outside.y = pocket.doorWorld.y + 1f;
                InteriorTeleport.Move(outside,
                    Quaternion.LookRotation(pocket.doorForward, Vector3.up), g);
            }
            _active = null;
            ClearCamera();
            var mgr = InteriorManager.Instance;
            if (mgr != null && mgr.CurrentEntrance != null) mgr.MarkOutside(mgr.CurrentEntrance);
        }

        public static void ClearCamera()
        {
            var g = Game.Instance;
            if (g != null && g.rig != null) g.rig.SetIndoorOverride(false, CameraOwner);
        }

        /// <summary>Ticker unico (chiamato da InteriorManager): registra la
        /// prima visita di ogni zona con luogo, coordinate e indizio uovo.</summary>
        public static void Tick()
        {
            VictorianPocket pocket = _active;
            var g = Game.Instance;
            if (pocket == null || g == null || g.player == null) return;
            VictorianZone[] zones = pocket.GetComponentsInChildren<VictorianZone>(true);
            for (int i = 0; i < zones.Length; i++)
            {
                VictorianZone z = zones[i];
                if (z == null || string.IsNullOrEmpty(z.featureId)) continue;
                // La dedup usa la stessa chiave stabile scritta da Discover:
                // id breve + chiave casa, così il ticker non rifà il lavoro
                // ogni frame dopo la prima scoperta.
                if (HouseInteriorState.HasEgg(FeatureId(pocket.key, z.featureId))) continue;
                if (Vector3.Distance(g.player.transform.position, z.transform.position) > z.radius)
                    continue;
                Discover(pocket, z.featureId, z.label, z.place);
            }
        }

        private static void Discover(VictorianPocket pocket, string id,
            string label, string place)
        {
            if (pocket == null || string.IsNullOrEmpty(id)) return;
            string featureId = FeatureId(pocket.key, id);
            if (HouseInteriorState.HasEgg(featureId)) return;
            HouseInteriorState.MarkEgg(featureId);
            CityGlobalAreasBridge.Record("scoperte", "casa vittoriana→" + label,
                label, place, pocket.doorWorld);
            try { UnityBridge.LogToAndroid(Tag, "scoperto: " + place); }
            catch (System.Exception) { }
        }

        private static VictorianPocket FindPocket(InteriorGenerator gen)
        {
            if (gen == null) return null;
            VictorianPocket[] pockets = gen.GetComponentsInChildren<VictorianPocket>(true);
            return pockets != null && pockets.Length > 0 ? pockets[0] : null;
        }

        private static string FeatureId(string key, string room)
        {
            return "vittoriano-" + key + "->" + room;
        }

        private static string StableKey(BuildingEntrance e, Vector3 door, string name)
        {
            string lat = "0", lng = "0";
            if (City.OSM.WorldOrigin.Initialized)
            {
                City.OSM.GeoCoord g = City.OSM.WorldOrigin.ToGeo(door);
                lat = g.lat.ToString("F5", CultureInfo.InvariantCulture);
                lng = g.lng.ToString("F5", CultureInfo.InvariantCulture);
            }
            string stableName = e != null && !string.IsNullOrEmpty(e.buildingName)
                ? e.buildingName : (string.IsNullOrEmpty(name) ? "casa" : name);
            return stableName + "@" + lat + "," + lng;
        }

        // ── VILLA PREFAB + FALLBACK ──────────────────────────────────

        /// <summary>La villa completa è un prefab editoriale (SENDU). Viene
        /// caricato una sola volta per tasca; zone, etichette e uscita sono
        /// comuni ai due percorsi e non duplicano le uova del prefab.</summary>
        private static void BuildVilla(Transform root, VictorianPocket p, string label)
        {
            // Casa eroe Сountry House FREE: apre il VERO interno dell'Asset
            // Store, estratto dal ramo `mainIndoor01` (nessun guscio duplicato).
            // Se il prefab manca, si ripiega sulla villa 300 mq: mai vuota.
            if (p != null && p.AssetInterior)
            {
                GameObject assetInterior = null;
                try { assetInterior = Resources.Load<GameObject>(p.interiorPrefab); }
                catch (System.Exception) { assetInterior = null; }
                if (assetInterior != null)
                {
                    var casa = UnityEngine.Object.Instantiate(assetInterior, root, false) as GameObject;
                    if (casa != null)
                    {
                        casa.name = "CountryHouse_Interior_AssetStore";
                        casa.SetActive(true);
                        // I materiali dell'asset sono Standard: li cloniamo e
                        // convertiamo a URP/Lit sulla sola istanza, senza
                        // toccare i sorgenti dell'Asset Store.
                        City.OSM.BuildingPlacer.EnsureURPMaterialsOnInstance(casa);
                        FinalizeCountryInterior(root, p, label);
                        return;
                    }
                }
                Debug.LogWarning("[CasaVittoriana] interno Asset Store mancante: " +
                    p.interiorPrefab + " — fallback villa 300 mq");
            }

            GameObject prefab = null;
            bool grandWanted = p != null && p.grand;
            // Prima scelta per le case configurate: la villa da 300 mq.
            try { prefab = Resources.Load<GameObject>(
                grandWanted ? GrandVillaPrefabResource : VillaPrefabResource); }
            catch (System.Exception) { prefab = null; }
            if (grandWanted && prefab == null)
            {
                try { prefab = Resources.Load<GameObject>(VillaPrefabResource); }
                catch (System.Exception) { prefab = null; }
            }

            bool prefabUsed = prefab != null;
            bool grandUsed = grandWanted && prefabUsed &&
                prefab.name.IndexOf("300mqTreLivelli", System.StringComparison.Ordinal) >= 0;
            if (prefabUsed)
            {
                var villa = UnityEngine.Object.Instantiate(prefab, root, false) as GameObject;
                if (villa != null)
                {
                    villa.name = grandUsed ? "Villa300mqTreLivelli" : "VillaVittorianaCompleta";
                    villa.SetActive(true);
                }
                else prefabUsed = false;
            }
            if (!prefabUsed) BuildFallbackArchitecture(root, p, label);
            if (grandUsed) FinalizeGrandVilla(root, p, label);
            else FinalizePocket(root, p, label, prefabUsed);
        }

        /// <summary>Completamento dell'interno originale dell'Asset Store:
        /// suolo calpestabile, uscita davanti alla porta e 9 nascondigli con
        /// zone di scoperta nelle stanze reali del prefab (Room01/02/05/08/09
        /// e hallway). Nessun secondo edificio e nessuna villa HUNTIX: cambia
        /// solo l'arredazione visiva, uova/bridge/camera restano identici.
        /// </summary>
        private static void FinalizeCountryInterior(Transform root, VictorianPocket p,
            string label)
        {
            Material wood = Mat("country-wood", new Color(0.42f, 0.29f, 0.16f));
            Material gold = Mat("gold", new Color(0.72f, 0.55f, 0.20f));

            // L'asset ha collider su pareti/mobili (layer 8, non calpestabili).
            // Il suo pavimento e' decorative: aggiungiamo UNO suolo continuo
            // coperto da tutta l'impronta (~13,5 x 10,5 m) per GroundSnapper.
            Box(root, "PavimentoCountryAssetStore", new Vector3(0f, 0.12f, 0.9f),
                new Vector3(13.6f, 0.24f, 10.7f), wood, true, false);

            var exit = new GameObject("TriggerUscitaCountryAssetStore");
            exit.transform.SetParent(root, false);
            exit.transform.localPosition = p.exitLocal;
            var col = exit.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(3f, 2.2f, 0.8f);
            exit.AddComponent<VictorianExit>();

            // Nove nascondigli, uno per ambiente reale dell'asset. I numeri
            // sono coordinate LOCALI del prefab, misurate in batchmode sui
            // bounds dei figli `Room01`, `Room02`, `Room05`, `Room08`, `Room09`
            // e `hallway`; la porta dell'asset sta a +Z (Door04_pr, z~6,25).
            // Chiave uovo stabile e specifica dell'edificio: se in futuro un'altra
            // casa usera' lo stesso interno, le sue uova non collide con queste.
            string eggPrefix = "casa-osm-" +
                (p != null ? p.osmId.ToString(CultureInfo.InvariantCulture)
                           : "405221437") + ":";
            string[] ids =
            {
                eggPrefix + "country-soggiorno", eggPrefix + "country-cucina",
                eggPrefix + "country-sala", eggPrefix + "country-studio",
                eggPrefix + "country-biblioteca", eggPrefix + "country-camera",
                eggPrefix + "country-bagno", eggPrefix + "country-atrio1",
                eggPrefix + "country-atrio2"
            };
            string[] rooms =
            {
                "soggiorno (Room01)", "cucina (Room02)", "sala (Room05)",
                "studio (Room08)", "biblioteca (Room08)", "camera (Room09)",
                "bagno (hallway)", "atrio (hallway)", "atrio (hallway)"
            };
            Vector3[] spots =
            {
                new Vector3(-1.2f, 0.75f, -2.4f),
                new Vector3(0.4f, 0.75f, 1.6f),
                new Vector3(-4.3f, 0.75f, 1.1f),
                new Vector3(4.4f, 0.75f, -2.6f),
                new Vector3(4.4f, 0.75f, 0.8f),
                new Vector3(-5.3f, 0.75f, -2.7f),
                new Vector3(-1.4f, 0.75f, 4.6f),
                new Vector3(0f, 0.75f, 4.9f),
                new Vector3(1.5f, 0.75f, 5.2f)
            };
            for (int i = 0; i < ids.Length; i++)
            {
                Transform container = Box(root, "Contenitore_" + ids[i], spots[i],
                    new Vector3(0.55f, 0.45f, 0.38f), wood, false);
                var spot = container.gameObject.AddComponent<HouseEggSpot>();
                spot.featureId = ids[i];
                spot.label = rooms[i];
                spot.room = rooms[i];
                spot.place = "città→casa→Casa country Asset Store→" + rooms[i];
                var marker = container.gameObject.AddComponent<UniversalContainerMarker>();
                marker.room = rooms[i];
                marker.interiorKey = "vittoriano";

                AddCountryZone(root, ids[i], "Casa country — " + rooms[i],
                    spot.place, spots[i]);
            }

            try
            {
                UnityBridge.LogToAndroid(Tag,
                    "interno Asset Store: " + label + " — mainIndoor01, 9 uova, 9 zone");
            }
            catch (System.Exception) { }
        }

        /// <summary>Zona dell'interno Asset Store: helper dedicato per non
        /// alterare il conteggio storico delle 12 zone della villa.</summary>
        private static void AddCountryZone(Transform root,
            string id, string label, string place, Vector3 pos)
        {
            var go = new GameObject("ZonaCountry_" + id);
            go.transform.SetParent(root, false);
            go.transform.localPosition = pos;
            var zone = go.AddComponent<VictorianZone>();
            zone.featureId = id;
            zone.label = label;
            zone.place = place;
            zone.radius = 2.6f;
        }

        /// <summary>Completamento runtime della villa da 300 mq: marker dei
        /// contenitori, uscita, etichette e nove zone (tre per piano). Usa gli
        /// stessi eventi bridge di FinalizePocket, senza seconda economia.</summary>
        private static void FinalizeGrandVilla(Transform root, VictorianPocket p,
            string label)
        {
            HouseEggSpot[] spots = root.GetComponentsInChildren<HouseEggSpot>(true);
            for (int i = 0; i < spots.Length; i++)
            {
                HouseEggSpot spot = spots[i];
                if (spot == null || spot.GetComponent<UniversalContainerMarker>() != null) continue;
                var marker = spot.gameObject.AddComponent<UniversalContainerMarker>();
                marker.room = spot.room;
                marker.interiorKey = "vittoriano";
            }

            Material gold = Mat("gold", new Color(0.72f, 0.55f, 0.20f));
            var trigger = new GameObject("TriggerUscitaVittoriana");
            trigger.transform.SetParent(root, false);
            trigger.transform.localPosition = new Vector3(0f, 1.15f, 4.35f);
            var col = trigger.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(3f, 2.2f, 1f);
            trigger.AddComponent<VictorianExit>();

            Label(root, "PIANO 1 — SALONE E CUCINA", new Vector3(0f, 2.95f, 4.55f),
                new Vector3(20f, 1.4f, 1f), gold);
            Label(root, "PIANO 2 — CAMERA, BAGNO E STUDIO", new Vector3(0f, 6.45f, 4.55f),
                new Vector3(22f, 1.4f, 1f), gold);
            Label(root, "PIANO 3 — BIBLIOTECA, BAMBINI E TERRAZZA",
                new Vector3(0f, 9.95f, 4.55f), new Vector3(24f, 1.4f, 1f), gold);

            AddZonaVilla(root, p, "salotto-piano1", "Salone del piano 1",
                "città→casa→" + p.key + "→salone al piano 1",
                new Vector3(0f, 1.15f, 0.5f));
            AddZonaVilla(root, p, "cucina-piano1", "Cucina del piano 1",
                "città→casa→" + p.key + "→cucina al piano 1",
                new Vector3(2.6f, 1.15f, -1.5f));
            AddZonaVilla(root, p, "atrio-piano1", "Atrio del piano 1",
                "città→casa→" + p.key + "→atrio al piano 1",
                new Vector3(3.4f, 1.15f, 3f));
            AddZonaVilla(root, p, "camera-piano2", "Camera del piano 2",
                "città→casa→" + p.key + "→camera al piano 2",
                new Vector3(-2f, 4.65f, 1.8f));
            AddZonaVilla(root, p, "bagno-piano2", "Bagno del piano 2",
                "città→casa→" + p.key + "→bagno al piano 2",
                new Vector3(3.4f, 4.65f, -1f));
            AddZonaVilla(root, p, "studio-piano2", "Studio del piano 2",
                "città→casa→" + p.key + "→studio al piano 2",
                new Vector3(0.5f, 4.65f, -3f));
            AddZonaVilla(root, p, "biblioteca-piano3", "Biblioteca del piano 3",
                "città→casa→" + p.key + "→biblioteca al piano 3",
                new Vector3(-3f, 8.15f, 0f));
            AddZonaVilla(root, p, "bambini-piano3", "Stanza dei bambini al piano 3",
                "città→casa→" + p.key + "→stanza dei bambini al piano 3",
                new Vector3(3f, 8.15f, -2f));
            AddZonaVilla(root, p, "terrazza-piano3", "Terrazza del piano 3",
                "città→casa→" + p.key + "→terrazza al piano 3",
                new Vector3(2f, 8.15f, 2.4f));

            try
            {
                UnityBridge.LogToAndroid(Tag,
                    "villa 300 mq: " + label + " — 3 piani, 9 uova, 9 zone");
            }
            catch (System.Exception) { }
        }

        private static void AddZonaVilla(Transform root, VictorianPocket p,
            string id, string label, string place, Vector3 pos)
        {
            var go = new GameObject("ZonaVilla300_" + id);
            go.transform.SetParent(root, false);
            go.transform.localPosition = pos;
            var zone = go.AddComponent<VictorianZone>();
            zone.featureId = id;
            zone.label = label;
            zone.place = place;
            zone.radius = 3.5f;
        }

        /// <summary>Il prefab contiene già i cinque contenitori, ma il marker
        /// secondario di UniversalInteriorBuilder non si serializza nei prefab:
        /// lo ricrea solo se assente. Nessun uovo doppio, nessun sistema rimosso.
        /// </summary>
        private static void FinalizePocket(Transform root, VictorianPocket p,
            string label, bool prefabUsed)
        {
            HouseEggSpot[] spots = root.GetComponentsInChildren<HouseEggSpot>(true);
            for (int i = 0; i < spots.Length; i++)
            {
                HouseEggSpot spot = spots[i];
                if (spot == null || spot.GetComponent<UniversalContainerMarker>() != null) continue;
                var marker = spot.gameObject.AddComponent<UniversalContainerMarker>();
                marker.room = spot.room;
                marker.interiorKey = "vittoriano";
            }

            Material dark = Mat("dark", new Color(0.16f, 0.12f, 0.09f));
            Material gold = Mat("gold", new Color(0.72f, 0.55f, 0.20f));
            Material glow = Emissive("glow", new Color(1f, 0.94f, 0.76f),
                new Color(0.95f, 0.78f, 0.40f));
            // Col prefab la porta è già modellata: aggiungiamo solo il trigger.
            BuildExit(root, dark, gold, glow, !prefabUsed);

            Label(root, "CASA VITTORIANA — SALONE", new Vector3(0f, 3.4f, 49f),
                new Vector3(26f, 2.2f, 1f), gold);
            Label(root, "BIBLIOTECA", new Vector3(-48f, 3.4f, 20f),
                new Vector3(14f, 1.6f, 1f), gold);
            Label(root, "SALA DA PRANZO", new Vector3(48f, 3.4f, 20f),
                new Vector3(16f, 1.6f, 1f), gold);
            Label(root, "LA CITTÀ DENTRO CASA", new Vector3(0f, 3.4f, -32f),
                new Vector3(28f, 1.6f, 1f), gold);
            // Le comodità della villa vera: atrio, camera, bagno, cucina,
            // stanza dei bambini e studio privato. Etichette e zone usano gli
            // stessi eventi bridge delle zone storiche, senza nuova economia.
            Label(root, "ATRIO D'ONORE", new Vector3(0f, 4f, 36f),
                new Vector3(18f, 1.6f, 1f), gold);
            Label(root, "CAMERA PADRONALE", new Vector3(-58f, 4f, 22.5f),
                new Vector3(20f, 1.6f, 1f), gold);
            Label(root, "BAGNO E SPOGLIATOIO", new Vector3(-58f, 4f, -23.5f),
                new Vector3(20f, 1.6f, 1f), gold);
            Label(root, "CUCINA E DISPENSA", new Vector3(58f, 4f, 22.5f),
                new Vector3(20f, 1.6f, 1f), gold);
            Label(root, "STANZA DEI BAMBINI", new Vector3(52f, 4f, -23.5f),
                new Vector3(18f, 1.6f, 1f), gold);
            Label(root, "STUDIO PRIVATO", new Vector3(70f, 4f, -23.5f),
                new Vector3(16f, 1.6f, 1f), gold);

            Zone(root, p, "salone", "Salone dei lampadari",
                "città→casa→" + p.key + "→salone dei lampadari",
                new Vector3(0f, 1f, 18f), 16f);
            Zone(root, p, "biblioteca", "Biblioteca vittoriana",
                "città→casa→" + p.key + "→biblioteca vittoriana",
                new Vector3(-48f, 1f, 0f), 14f);
            Zone(root, p, "pranzo", "Sala da pranzo",
                "città→casa→" + p.key + "→sala da pranzo",
                new Vector3(48f, 1f, 0f), 14f);
            Zone(root, p, "citta", "Città dentro casa",
                "città→casa→" + p.key + "→città dentro casa",
                new Vector3(0f, 1f, -38f), 22f);
            Zone(root, p, "piazzetta", "Piazzetta della fontana",
                "città→casa→" + p.key + "→piazzetta della fontana",
                new Vector3(0f, 1f, -48f), 12f);
            Zone(root, p, "lampadari", "Lampadari del salone",
                "città→casa→" + p.key + "→lampadari del salone",
                new Vector3(0f, 1f, 30f), 12f);
            Zone(root, p, "atrio", "Atrio d'onore",
                "città→casa→" + p.key + "→atrio d'onore",
                new Vector3(0f, 1f, 48f), 12f);
            Zone(root, p, "camera", "Camera padronale",
                "città→casa→" + p.key + "→camera padronale",
                new Vector3(-58f, 1f, 42f), 18f);
            Zone(root, p, "bagno", "Bagno e spogliatoio",
                "città→casa→" + p.key + "→bagno e spogliatoio",
                new Vector3(-58f, 1f, -38f), 16f);
            Zone(root, p, "cucina", "Cucina e dispensa",
                "città→casa→" + p.key + "→cucina e dispensa",
                new Vector3(58f, 1f, 42f), 16f);
            Zone(root, p, "bambini", "Stanza dei bambini",
                "città→casa→" + p.key + "→stanza dei bambini",
                new Vector3(52f, 1f, -36f), 14f);
            Zone(root, p, "studio", "Studio privato",
                "città→casa→" + p.key + "→studio privato",
                new Vector3(70f, 1f, -30f), 12f);
        }

        // ── ARCHITETTURA DI FALLBACK ─────────────────────────────────

        private static void BuildFallbackArchitecture(Transform root, VictorianPocket p, string label)
        {
            Material stone = Mat("stone", new Color(0.42f, 0.33f, 0.25f));
            Material wall = Mat("wall", new Color(0.86f, 0.80f, 0.69f));
            Material dark = Mat("dark", new Color(0.16f, 0.12f, 0.09f));
            Material gold = Mat("gold", new Color(0.72f, 0.55f, 0.20f));
            Material red = Mat("red", new Color(0.42f, 0.08f, 0.11f));
            Material green = Mat("green", new Color(0.13f, 0.30f, 0.22f));
            Material glow = Emissive("glow", new Color(1f, 0.94f, 0.76f),
                new Color(0.95f, 0.78f, 0.40f));

            // Pavimento calpestabile: collider normale (layer 0), così il
            // GroundSnapper trova il suolo come in ogni altro interno.
            Box(root, "PavimentoVittoriano", new Vector3(0f, -0.6f, 0f),
                new Vector3(164f, 1.2f, 114f), stone, true, false);
            Box(root, "TappetoSalone", new Vector3(0f, 0.02f, 14f),
                new Vector3(38f, 0.08f, 46f), red);
            Box(root, "TappetoBiblioteca", new Vector3(-48f, 0.02f, 0f),
                new Vector3(24f, 0.08f, 42f), green);
            Box(root, "TappetoPranzo", new Vector3(48f, 0.02f, 0f),
                new Vector3(24f, 0.08f, 42f), red);

            // Guscio della tasca: muri con collider su layer Building (8),
            // come le paratie delle case visitabili, mai usati come "terreno".
            Box(root, "PareteFondo", new Vector3(0f, 6f, -57f),
                new Vector3(166f, 13f, 1f), wall, true, true);
            Box(root, "PareteIngresso", new Vector3(0f, 6f, 57f),
                new Vector3(166f, 13f, 1f), wall, true, true);
            Box(root, "PareteSinistra", new Vector3(-82f, 6f, 0f),
                new Vector3(1f, 13f, 116f), wall, true, true);
            Box(root, "PareteDestra", new Vector3(82f, 6f, 0f),
                new Vector3(1f, 13f, 116f), wall, true, true);
            Box(root, "SoffittoVittoriano", new Vector3(0f, 13f, 0f),
                new Vector3(166f, 1f, 116f), wall);
            for (int i = -4; i <= 4; i++)
                Box(root, "TraveSo" + i, new Vector3(i * 18f, 12.4f, 0f),
                    new Vector3(0.8f, 0.8f, 114f), dark);

            // Colonnato del salone: il senso di scala arriva qui e dai soffitti
            // alti, non dal numero di luci (due sole point light reali).
            for (int side = -1; side <= 1; side += 2)
            {
                for (int i = -3; i <= 3; i++)
                    Cylinder(root, "Colonna_" + side + "_" + i,
                        new Vector3(side * 23f, 6f, i * 13f + 12f),
                        new Vector3(1.1f, 6f, 1.1f), stone, true);
            }

            Chandelier(root, new Vector3(0f, 10.4f, 30f), gold, glow, true);
            Chandelier(root, new Vector3(0f, 10.4f, 4f), gold, glow, false);
            Chandelier(root, new Vector3(0f, 10.4f, -20f), gold, glow, true);

            BuildSalon(root, dark, gold, red, glow);
            BuildLibrary(root, dark, gold, green);
            BuildDining(root, dark, gold, red);
            BuildCityInside(root, wall, dark, gold, glow, red);
            // Uscita, etichette e zone li aggiunge FinalizePocket a entrambi
            // i percorsi (prefab e fallback), senza duplicarli.
        }

        private static void BuildSalon(Transform root, Material dark,
            Material gold, Material red, Material glow)
        {
            for (int i = 0; i < 4; i++)
            {
                float z = 6f + i * 11f;
                for (int side = -1; side <= 1; side += 2)
                {
                    Box(root, "PoltronaSalone_" + i + "_" + side,
                        new Vector3(side * 9f, 0.7f, z),
                        new Vector3(4.2f, 1.4f, 2.2f), red);
                    Box(root, "SchienaleSalone_" + i + "_" + side,
                        new Vector3(side * 9f, 1.7f, z - 0.9f),
                        new Vector3(4.2f, 2f, 0.4f), red);
                }
                Box(root, "TavolinoSalone_" + i, new Vector3(0f, 0.6f, z),
                    new Vector3(3.2f, 1.2f, 2f), dark);
            }
            // Camino monumentale e specchiere: la casa "vittoriana" si legge
            // anche senza texture, solo con volumi e palette.
            Box(root, "Camino", new Vector3(-34f, 2.4f, 34f),
                new Vector3(7f, 4.8f, 1.4f), dark);
            Box(root, "FuocoCamino", new Vector3(-34f, 1.2f, 34.8f),
                new Vector3(4f, 2f, 0.3f), glow);
            Box(root, "Specchiera", new Vector3(34f, 3f, 34f),
                new Vector3(6f, 6f, 0.4f), gold);
            // Uovo 1: cassetto sotto il lampadario centrale.
            EggSpot(root, "cassetto-lampadario", "salone", "cassetto sotto il lampadario",
                new Vector3(4f, 0.6f, 30f));
            // Comodità aggiuntive (prefab e fallback): anche l'armadio della
            // camera è un nascondiglio stabile del Riflesso uova.
            EggSpot(root, "armadio-camera", "camera da letto", "armadio della camera da letto",
                new Vector3(-36f, 0.6f, 50f));
        }

        private static void BuildLibrary(Transform root, Material dark,
            Material gold, Material green)
        {
            for (int i = -2; i <= 2; i++)
            {
                Box(root, "Libreria_" + i, new Vector3(-66f, 4f, i * 8f),
                    new Vector3(1.4f, 8f, 6f), dark);
                Box(root, "Libri_" + i, new Vector3(-65f, 4f, i * 8f),
                    new Vector3(0.5f, 7f, 5.4f), green);
            }
            Box(root, "ScrivaniaBiblioteca", new Vector3(-46f, 1f, -6f),
                new Vector3(6f, 2f, 3f), dark);
            Box(root, "GloboBiblioteca", new Vector3(-40f, 1.6f, -6f),
                new Vector3(2.2f, 3.2f, 2.2f), gold);
            // Uovo 2: cassetto della scrivania in biblioteca.
            EggSpot(root, "cassetto-biblioteca", "biblioteca", "cassetto della scrivania",
                new Vector3(-44f, 0.7f, -6f));
            EggSpot(root, "lavandino-bagno", "bagno", "lavandino del bagno",
                new Vector3(-36f, 0.7f, -18f));
        }

        private static void BuildDining(Transform root, Material dark,
            Material gold, Material red)
        {
            Box(root, "TavoloPranzo", new Vector3(48f, 1.2f, 0f),
                new Vector3(6f, 2.4f, 26f), dark);
            for (int i = -4; i <= 4; i++)
            {
                for (int side = -1; side <= 1; side += 2)
                    Box(root, "SediaPranzo_" + i + "_" + side,
                        new Vector3(48f + side * 5f, 0.7f, i * 2.8f),
                        new Vector3(1.8f, 1.4f, 1.8f), red);
            }
            Box(root, "CredenzaPranzo", new Vector3(69f, 2f, 0f),
                new Vector3(2f, 4f, 18f), gold);
            // Uovo 3: credenza della sala da pranzo.
            EggSpot(root, "credenza-pranzo", "sala da pranzo", "credenza della sala da pranzo",
                new Vector3(66f, 0.7f, 2f));
            EggSpot(root, "forno-cucina", "cucina", "forno della cucina",
                new Vector3(66f, 0.7f, 18f));
        }

        private static void BuildCityInside(Transform root, Material wall,
            Material dark, Material gold, Material glow, Material red)
        {
            // Due "strade" di facciate sotto il soffitto: da dentro sembra
            // una città vittoriana in miniatura, camminabile a grandezza reale.
            for (int side = -1; side <= 1; side += 2)
            {
                for (int i = 0; i < 6; i++)
                {
                    float z = -24f - i * 5.6f;
                    float x = side * (14f + (i % 2) * 2f);
                    Color c = i % 2 == 0
                        ? new Color(0.56f, 0.42f, 0.34f)
                        : new Color(0.42f, 0.48f, 0.50f);
                    Material facade = Mat("fac" + side + i, c);
                    Box(root, "Facciata_" + side + "_" + i,
                        new Vector3(x, 4.6f, z), new Vector3(7f, 9.2f, 5.2f), facade, true, true);
                    for (int row = 0; row < 2; row++)
                        Box(root, "Vetrina_" + side + "_" + i + "_" + row,
                            new Vector3(x - side * 3.6f, 1.5f + row * 3f, z),
                            new Vector3(0.3f, 1.8f, 2.6f), glow);
                    Box(root, "Cornice_" + side + "_" + i,
                        new Vector3(x, 9.3f, z), new Vector3(7.6f, 0.4f, 5.6f), gold);
                }
                for (int i = 0; i < 4; i++)
                {
                    float z = -22f - i * 8f;
                    Cylinder(root, "Lampione_" + side + "_" + i,
                        new Vector3(side * 7f, 2.4f, z), new Vector3(0.3f, 2.4f, 0.3f), dark);
                    Sphere(root, "LuceLampione_" + side + "_" + i,
                        new Vector3(side * 7f, 4.9f, z), new Vector3(1f, 1f, 1f), glow);
                    Box(root, "Panchina_" + side + "_" + i,
                        new Vector3(side * 5f, 0.6f, z - 2f), new Vector3(2.4f, 1.2f, 1f), dark);
                }
            }
            // Piazzetta con fontana: punto di arrivo della "città interna".
            Cylinder(root, "VascaFontana", new Vector3(0f, 0.6f, -48f),
                new Vector3(6f, 0.6f, 6f), wall, true);
            Cylinder(root, "ColonnaFontana", new Vector3(0f, 2.2f, -48f),
                new Vector3(1f, 2.2f, 1f), gold);
            Sphere(root, "AcquaFontana", new Vector3(0f, 3.8f, -48f),
                new Vector3(2.4f, 2.4f, 2.4f), glow);
            Box(root, "TappetoPiazzetta", new Vector3(0f, 0.03f, -48f),
                new Vector3(22f, 0.08f, 16f), red);
            // Uovo 4: vetrina di un negozio della città interna.
            EggSpot(root, "vetrina-citta", "città dentro casa", "vetrina del negozio",
                new Vector3(-9f, 0.7f, -35f));
            // Uovo 5: vasca della fontana nella piazzetta.
            EggSpot(root, "fontana-piazzetta", "piazzetta della fontana", "vasca della fontana",
                new Vector3(6f, 0.7f, -48f));
            EggSpot(root, "cassetto-bambini", "stanza dei bambini", "cassetto dei giocattoli",
                new Vector3(-22f, 0.7f, -18f));
            EggSpot(root, "scrivania-studio", "studio privato", "scrivania dello studio",
                new Vector3(22f, 0.7f, -18f));
        }

        private static void BuildExit(Transform root, Material dark,
            Material gold, Material glow, bool withDoor)
        {
            if (withDoor)
            {
                Box(root, "PortaUscitaVittoriana", new Vector3(0f, 2.6f, 55.6f),
                    new Vector3(6f, 5.2f, 0.6f), dark, true, true);
                Box(root, "ManigliaUscita", new Vector3(2f, 2.4f, 55.1f),
                    new Vector3(0.4f, 0.4f, 0.4f), gold);
                Sphere(root, "LuceUscita", new Vector3(0f, 5.4f, 55f),
                    new Vector3(1.2f, 1.2f, 1.2f), glow);
            }
            Label(root, "USCITA → CASA", new Vector3(0f, 6f, 55f),
                new Vector3(14f, 1.6f, 1f), gold);
            var trigger = new GameObject("TriggerUscitaVittoriana");
            trigger.transform.SetParent(root, false);
            trigger.transform.localPosition = new Vector3(0f, 1.1f, 52f);
            var col = trigger.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(8f, 3f, 1.2f);
            trigger.AddComponent<VictorianExit>();
        }

        private static void Chandelier(Transform root, Vector3 pos,
            Material gold, Material glow, bool withLight)
        {
            Transform stem = Cylinder(root, "LampadarioStelo", pos + new Vector3(0f, -1.4f, 0f),
                new Vector3(0.18f, 1.4f, 0.18f), gold);
            stem.name = "LampadarioStelo_" + pos.z.ToString("F0", CultureInfo.InvariantCulture);
            Transform ring = Cylinder(root, "LampadarioCerchio", pos,
                new Vector3(3.6f, 0.18f, 3.6f), gold);
            ring.name = "LampadarioCerchio_" + pos.z.ToString("F0", CultureInfo.InvariantCulture);
            for (int i = 0; i < 8; i++)
            {
                float a = i * Mathf.PI * 0.25f;
                Sphere(root, "Candela_" + pos.z.ToString("F0", CultureInfo.InvariantCulture) + "_" + i,
                    pos + new Vector3(Mathf.Cos(a) * 3.2f, 0.5f, Mathf.Sin(a) * 3.2f),
                    new Vector3(0.5f, 0.9f, 0.5f), glow);
            }
            if (!withLight) return;
            var lightGo = new GameObject("LuceLampadario_" + pos.z.ToString("F0", CultureInfo.InvariantCulture));
            lightGo.transform.SetParent(root, false);
            lightGo.transform.localPosition = pos + new Vector3(0f, -0.5f, 0f);
            var light = lightGo.AddComponent<Light>();
            light.type = LightType.Point;
            light.color = new Color(1f, 0.9f, 0.7f);
            light.intensity = 1.2f;
            light.range = 34f;
        }

        private static void EggSpot(Transform root, string id,
            string room, string thing, Vector3 pos)
        {
            var holder = new GameObject("Contenitore_" + id);
            holder.transform.SetParent(root, false);
            holder.transform.localPosition = pos;
            Box(holder.transform, "Mobile_" + id, new Vector3(0f, 0.5f, 0f),
                new Vector3(2.2f, 1f, 1.4f), Mat("wood", new Color(0.38f, 0.24f, 0.14f)));
            var marker = holder.AddComponent<UniversalContainerMarker>();
            marker.room = room;
            marker.interiorKey = "vittoriano";
            var spot = holder.AddComponent<HouseEggSpot>();
            spot.featureId = "uovo-" + id;
            spot.label = thing;
            spot.room = room;
            spot.place = "città→casa→Casa Vittoriana→" + room + "→" + thing;
        }

        private static void Zone(Transform root, VictorianPocket p,
            string id, string label, string place, Vector3 pos, float radius)
        {
            var go = new GameObject("ZonaVittoriana_" + id);
            go.transform.SetParent(root, false);
            go.transform.localPosition = pos;
            var zone = go.AddComponent<VictorianZone>();
            zone.featureId = id;
            zone.label = label;
            zone.place = place;
            zone.radius = radius;
        }

        // ── PRIMITIVE CONDIVISE ─────────────────────────────────────

        private static Transform Box(Transform root, string name, Vector3 pos,
            Vector3 size, Material mat, bool collider = false, bool buildingLayer = false)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(root, false);
            go.transform.localPosition = pos;
            go.transform.localScale = size;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            if (collider)
            {
                if (buildingLayer) go.layer = 8; // BuildingLayer: mai "terreno"
            }
            else
            {
                UnityEngine.Object.Destroy(go.GetComponent<Collider>());
            }
            return go.transform;
        }

        private static Transform Cylinder(Transform root, string name, Vector3 pos,
            Vector3 size, Material mat, bool collider = false)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            go.name = name;
            go.transform.SetParent(root, false);
            go.transform.localPosition = pos;
            go.transform.localScale = size;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            if (!collider) UnityEngine.Object.Destroy(go.GetComponent<Collider>());
            else go.layer = 8;
            return go.transform;
        }

        private static Transform Sphere(Transform root, string name, Vector3 pos,
            Vector3 size, Material mat)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            go.name = name;
            go.transform.SetParent(root, false);
            go.transform.localPosition = pos;
            go.transform.localScale = size;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            UnityEngine.Object.Destroy(go.GetComponent<Collider>());
            return go.transform;
        }

        private static void Label(Transform root, string text, Vector3 pos,
            Vector3 size, Material color)
        {
            var go = new GameObject("Label_" + text);
            go.transform.SetParent(root, false);
            go.transform.localPosition = pos;
            var tmp = go.AddComponent<TextMeshPro>();
            tmp.font = TMP_Settings.defaultFontAsset;
            tmp.text = text;
            tmp.fontSize = 2f;
            tmp.alignment = TextAlignmentOptions.Center;
            tmp.color = color != null ? color.color : Color.white;
            tmp.enableWordWrapping = false;
            tmp.overflowMode = TextOverflowModes.Overflow;
            tmp.raycastTarget = false;
            tmp.rectTransform.sizeDelta = new Vector2(size.x * 10f, size.y * 10f);
        }

        private static Material Mat(string id, Color c)
        {
            Material m;
            if (Mats.TryGetValue(id, out m) && m != null) return m;
            Shader shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) shader = Shader.Find("Hidden/InternalErrorShader");
            m = new Material(shader);
            m.name = "CasaVittoriana_" + id;
            string prop = shader != null && shader.name.StartsWith("Universal Render Pipeline",
                System.StringComparison.Ordinal) ? "_BaseColor" : "_Color";
            if (m.HasProperty(prop)) m.SetColor(prop, c);
            else if (m.HasProperty("_Color")) m.SetColor("_Color", c);
            if (m.HasProperty("_Smoothness")) m.SetFloat("_Smoothness", 0.28f);
            Mats[id] = m;
            return m;
        }

        private static Material Emissive(string id, Color baseColor, Color emission)
        {
            Material m = Mat(id, baseColor);
            m.EnableKeyword("_EMISSION");
            if (m.HasProperty("_EmissionColor")) m.SetColor("_EmissionColor", emission);
            return m;
        }
    }
}
