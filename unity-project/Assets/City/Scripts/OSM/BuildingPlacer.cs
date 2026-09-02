using System;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Piazza gli edifici del chunk partendo dai placement record della tile
    /// (centro, impronta w/d, rotazione, tipo OSM) e istanzia i prefab Kenney
    /// con la stessa logica di CityOSMWorld.TryPlaceKenneyBuilding:
    /// misura i bounds PRIMA della rotazione, scala sull'impronta, appoggia a
    /// terra. Un solo BoxCollider per edificio (layer 8 Buildings).
    /// </summary>
    public static class BuildingPlacer
    {
        private static readonly string[] SuburbanHouses =
        {
            "building-type-a", "building-type-b", "building-type-c", "building-type-d",
            "building-type-e", "building-type-f", "building-type-g", "building-type-h",
            "building-type-i", "building-type-j", "building-type-k", "building-type-l",
            "building-type-m", "building-type-n", "building-type-o", "building-type-p",
            "building-type-q", "building-type-r", "building-type-s", "building-type-t",
        };

        private static readonly string[] CommercialBuildings =
        {
            "building-a", "building-b", "building-c", "building-d", "building-e",
            "building-f", "building-g", "building-h", "building-i", "building-j",
            "building-k", "building-l", "building-m", "building-n",
        };

        private static readonly string[] IndustrialBuildings =
        {
            "building-a", "building-b", "building-c", "building-d", "building-e",
            "building-f", "building-g", "building-h", "building-i", "building-j",
            "building-k", "building-l", "building-m", "building-n", "building-o",
            "building-p", "building-q", "building-r", "building-s", "building-t",
        };

        private static readonly string[] Skyscrapers =
        {
            "building-skyscraper-a", "building-skyscraper-b", "building-skyscraper-c",
            "building-skyscraper-d", "building-skyscraper-e",
        };

        private const int BuildingLayer = 8;

        // ── Entrate interni (Fase 4) ─────────────────────────────────────
        // Budget per chunk: non piu' di MaxEnterablePerChunk edifici
        // visitabili, cosi' i quartieri restano misti e la fisica leggera.
        private const int MaxEnterablePerChunk = 8;
        private static int enterableBudget;
        public static void ResetChunkBudget() { enterableBudget = MaxEnterablePerChunk; }

        public static bool IsCommercial(string t)
        {
            return ContainsAny(t, "commercial", "retail", "office", "civic",
                "hotel", "supermarket");
        }

        public static bool IsIndustrial(string t)
        {
            return ContainsAny(t, "industrial", "warehouse", "farm_auxiliary",
                "barn", "shed", "hangar");
        }

        private static bool ContainsAny(string value, params string[] keys)
        {
            if (string.IsNullOrEmpty(value)) return false;
            for (int i = 0; i < keys.Length; i++)
                if (value.IndexOf(keys[i], StringComparison.OrdinalIgnoreCase) >= 0)
                    return true;
            return false;
        }

        /// <summary>
        /// Prefab + altezza target deterministici da tipo e id OSM.
        /// blockSeed (hash dell'isolato ~120 m) guida la scelta dentro una
        /// famiglia piccola di prefab: gli edifici dello stesso isolato
        /// condividono lo stile, con variazione individuale limitata.
        /// </summary>
        public static string PickPrefabName(TileBuildingRec b, out float height, int blockSeed = 0)
        {
            bool commercial = IsCommercial(b.t);
            bool industrial = IsIndustrial(b.t);
            bool civicBig = IsCivicBig(b.t);
            float area = b.d != null && b.d.Length >= 2 ? b.d[0] * b.d[1] : 25f;
            int h = Hash(b.id);

            // Ospedali/scuole/chiese: il kit suburbano non li rappresenta;
            // usano gli edifici commerciali grandi cosi' sembrano pubblici.
            if (civicBig && area > 300f)
            {
                height = 16f + (blockSeed % 6);
                return Skyscrapers[(blockSeed >> 2) % Skyscrapers.Length];
            }
            if (civicBig)
            {
                height = 10f + (blockSeed % 4);
                return CommercialBuildings[(blockSeed >> 2) % CommercialBuildings.Length];
            }

            if (commercial && area > 600f)
            {
                height = 22f + (h % 18);
                return Skyscrapers[h % Skyscrapers.Length];
            }
            if (commercial)
            {
                // famiglia coerente per isolato: 4 gruppi da 3-4 prefab
                int family = ((blockSeed >> 4) & 3) * 4;
                height = 9f + (blockSeed % 6) + (h % 2);
                return CommercialBuildings[(family + h % 4) % CommercialBuildings.Length];
            }
            if (industrial)
            {
                height = 6f + (h % 5);
                return IndustrialBuildings[h % IndustrialBuildings.Length];
            }

            // Case: 5 famiglie di 4; l'isolato sceglie la famiglia,
            // l'edificio solo una variante dentro la famiglia.
            int fam = ((blockSeed >> 3) % 5) * 4;
            height = 4.5f + (blockSeed % 3) * 0.8f + (h % 2) * 0.4f;
            return SuburbanHouses[(fam + h % 4) % SuburbanHouses.Length];
        }

        // hospital / scuola / culto: volumi pubblici riconoscibili
        public static bool IsCivicBig(string t)
        {
            return ContainsAny(t, "hospital", "school", "university", "college",
                "kindergarten", "church", "place_of_worship", "museum",
                "theatre", "library");
        }

        private static int Hash(long id)
        {
            uint x = (uint)id;
            x ^= x >> 16; x *= 0x7feb352d; x ^= x >> 15; x *= 0x846ca68b; x ^= x >> 16;
            return (int)(x & 0x7fffffff);
        }

        /// <summary>
        /// Istanzia il prefab per il record b. centerLocal = posizione dell'
        /// impronta in coordinate locali al chunk. Ritorna false se manca il
        /// prefab o il record e' degenere.
        /// </summary>
        public static bool Place(Huntix.Core.CityKitAssetRegistry registry,
            Transform parent, TileBuildingRec b, Vector3 centerLocal)
        {
            if (b.c == null || b.c.Length < 2 || b.d == null || b.d.Length < 2)
                return false;
            float w = Mathf.Max(b.d[0], 1.5f);
            float d = Mathf.Max(b.d[1], 1.5f);

            float h;
            // isolato ~120 m: gli edifici vicini condividono stile e fascia
            // d'altezza (quartieri omogenei, niente patchwork casuale)
            int blockSeed = Hash(
                ((long)Mathf.FloorToInt(centerLocal.x / 120f) << 16) ^
                (long)Mathf.FloorToInt(centerLocal.z / 120f));
            string prefabName = PickPrefabName(b, out h, blockSeed);
            GameObject prefab = registry != null ? registry.Get(prefabName) : null;
            if (prefab == null)
            {
                string folder = IsIndustrial(b.t) ? "Industrial"
                    : IsCommercial(b.t) ? "Commercial" : "Suburban";
                prefab = Resources.Load<GameObject>("Buildings/" + folder + "/" + prefabName);
            }
            if (prefab == null) return false;

            var inst = UnityEngine.Object.Instantiate(prefab, parent);
            inst.name = "Edificio " + b.id;

            foreach (var col in inst.GetComponentsInChildren<Collider>(true))
                UnityEngine.Object.Destroy(col);

            // Misura PRIMA della rotazione: bounds axis-aligned su oggetto ruotato gonfia la scala.
            Bounds baseB = UnionBounds(inst);
            float sx = w / Mathf.Max(baseB.size.x, 0.1f);
            float sz = d / Mathf.Max(baseB.size.z, 0.1f);
            float s = Mathf.Max(sx, sz);
            float sy = Mathf.Clamp(h / Mathf.Max(baseB.size.y, 0.01f), s * 0.6f, s * 1.5f);
            inst.transform.localScale = new Vector3(sx, sy, sz);
            inst.transform.localRotation = Quaternion.Euler(0f, b.r, 0f);
            inst.transform.localPosition = Vector3.zero;

            Bounds wb = UnionBounds(inst);
            var pos = new Vector3(
                centerLocal.x - wb.center.x,
                -wb.min.y,
                centerLocal.z - wb.center.z);
            inst.transform.localPosition = pos;

            // Un collider solo, dimensionato sui bounds reali del modello.
            var box = inst.AddComponent<BoxCollider>();
            box.size = baseB.size;
            box.center = baseB.center;
            inst.layer = BuildingLayer;

            MaybeAddEntrance(inst, b, baseB, w, d, h, sx, sy, sz);
            return true;
        }

        // Alcuni edifici piccoli diventano visitabili: un PORTALE stretto sul
        // lato della facciata (dove l'interno mette la PortaIngresso, +Z
        // locale), largo quanto una porta. L'ingresso scatta SOLO attraversando
        // la porta (OnTriggerEnter del BuildingEntrance -> auto-entry): passare
        // semplicemente accanto al muro o in mezzo alla strada NON attiva nulla.
        // NOTA: gli edifici fusi (blocco > 350 sqm) vengono saltati dalla soglia.
        private static void MaybeAddEntrance(GameObject inst, TileBuildingRec b,
            Bounds baseB, float w, float d, float h,
            float sx, float sy, float sz)
        {
            if (enterableBudget <= 0) return;
            float area = w * d;
            if (area < 40f || area > 350f) return;
            // ~1 edificio su 3 idoneo: deterministico per id OSM
            if (Hash(b.id) % 3 != 0) return;

            bool shop = IsCommercial(b.t);

            // Lato porta = +Z locale (facciata), coerente con la PortaIngresso
            // interna di InteriorGenerator (a +d*0.5). Il trigger-porta sporge
            // appena oltre il muro frontale e resta stretto in larghezza, cosi'
            // il giocatore deve passare ATTRAVERSO la porta, non solo accanto.
            const float doorW = 2.4f;     // larghezza porta (mondo)
            const float doorT = 0.4f;     // spessore fascia porta (mondo)
            const float protr = 0.15f;    // sporgenza oltre il muro (mondo)
            const float doorCenterY = 1.0f; // altezza centro porta (mondo)

            // offset del muro frontale rispetto al box center (+ baseB.size.z/2)
            float frontOffsetZ = baseB.size.z * 0.5f + protr;
            float doorCenterZ = baseB.center.z + frontOffsetZ;

            var trig = new GameObject("Porta");
            trig.transform.SetParent(inst.transform, false);
            trig.layer = BuildingLayer;
            // posiziono il GAMEOBJECT al centro del portale e tengo il collider
            // centrato sull'origine: cosi' il segnaposto visivo (figlio) sta
            // al centro. Le coordinate locali sono sottoposte alla scala NON
            // uniforme del genitore -> divido per (sx,sy,sz) per i metri mondo.
            trig.transform.localPosition =
                new Vector3(baseB.center.x, doorCenterY / sy, doorCenterZ / sz);
            var tc = trig.AddComponent<BoxCollider>();
            tc.isTrigger = true;
            tc.center = Vector3.zero;
            tc.size = new Vector3(doorW / sx, 2.2f / sy, doorT / sz);

            // segnaposto VISIVO della porta: una piastra piana verticale sul
            // muro frontale, cosi' il giocatore sa dove passare
            var doorMark = GameObject.CreatePrimitive(PrimitiveType.Quad);
            UnityEngine.Object.Destroy(doorMark.GetComponent<Collider>());
            doorMark.name = "PortaSegnaposto";
            doorMark.transform.SetParent(trig.transform, false);
            doorMark.transform.localPosition = Vector3.zero;
            doorMark.transform.localRotation = Quaternion.identity;
            doorMark.transform.localScale = new Vector3(2.3f / sx, 2.1f / sy, 1f);
            var r = doorMark.GetComponent<Renderer>();
            var dm = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            if (dm.shader == null) dm = new Material(Shader.Find("Standard"));
            dm.SetColor("_BaseColor", new Color(0.35f, 0.28f, 0.2f));
            r.sharedMaterial = dm;

            var entrance = trig.AddComponent<City.Interior.BuildingEntrance>();
            entrance.buildingType = shop ? "shop" : "house";
            entrance.buildingName = shop ? "Negozio " + b.id : "Casa " + b.id;
            entrance.buildingWidth = w;
            entrance.buildingDepth = d;
            entrance.buildingHeight = h;
            entrance.floorCount = h > 7f ? 2 : 1;
            enterableBudget--;

            if (shop)
            {
                // Negozi nel mondo chunked: il bancone di acquisto dentro
                // l'interno viene creato da InteriorGenerator SOLO se
                // entrance.shop != null. Prima lo Shop era attachato solo nel
                // percorso legacy (disattivato) percio' il commercio era morto.
                var shopComp = inst.AddComponent<City.World.Shop>();
                shopComp.shopName = string.IsNullOrEmpty(b.nm)
                    ? "Negozio " + b.id : b.nm;
                ShopItemsFor(shopComp, b.id);
                entrance.shop = shopComp;
            }
        }

        /// <summary>Catalogo deterministico per id OSM. I placement della tile
        /// espongono solo il tipo building (senza amenity/shop), quindi non si
        /// puo' distinguere supermercato/pizzeria: prodotti "da quartiere"
        /// variati dall'id.</summary>
        private static void ShopItemsFor(City.World.Shop shop, long bId)
        {
            string[][] cats =
            {
                new[] { "Pane", "Latte", "Acqua", "Mele" },
                new[] { "Caffe", "Cornetto", "Cappuccino" },
                new[] { "Maglietta", "Cappellino", "Jeans" },
                new[] { "Cuffie", "Cavo USB", "Powerbank" },
                new[] { "Libro", "Quaderno", "Penna" },
                new[] { "Cerotti", "Vitamine", "Nastro adesivo" },
            };
            int h = Hash(bId);
            var cat = cats[h % cats.Length];
            for (int i = 0; i < cat.Length; i++)
            {
                int price = 1 + (h >> (2 + i * 2)) % (4 + i * 2);
                shop.items.Add(new City.World.ShopItem(cat[i], price));
            }
        }

        public static Bounds UnionBounds(GameObject go)
        {
            var bounds = new Bounds();
            bool first = true;
            var renderers = go.GetComponentsInChildren<Renderer>();
            for (int i = 0; i < renderers.Length; i++)
            {
                if (renderers[i] is ParticleSystemRenderer) continue;
                if (first) { bounds = renderers[i].bounds; first = false; }
                else bounds.Encapsulate(renderers[i].bounds);
            }
            if (first) bounds = new Bounds(Vector3.zero, Vector3.one);
            // riporta in spazio locale dell'istanza (pivot non centrato)
            var t = go.transform;
            var localCenter = t.worldToLocalMatrix.MultiplyPoint3x4(bounds.center);
            var localSize = Vector3.Scale(bounds.size, InverseScale(t.lossyScale));
            return new Bounds(localCenter, localSize);
        }

        private static Vector3 InverseScale(Vector3 s)
        {
            return new Vector3(
                s.x != 0 ? 1f / s.x : 0f,
                s.y != 0 ? 1f / s.y : 0f,
                s.z != 0 ? 1f / s.z : 0f);
        }
    }
}
