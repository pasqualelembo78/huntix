using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Piazza concessionarie, officine e garage estratti da OSM dentro il
    /// chunk. Ogni POI diventa una struttura riconoscibile: piazzale colorato,
    /// insegna su palo e zona interattiva (VehiclePoiZone). Nelle
    /// concessionarie vengono esposte 3 auto vetrina del catalogo.
    /// Gli edifici generici OSM troppo vicini a un POI veicolo vengono
    /// saltati da ChunkBuilder perche' qui li sostituiamo noi.
    /// </summary>
    public static class VehiclePoiPlacer
    {
        private const float PadHeight = 0.12f;
        // raggio (m) entro cui ChunkBuilder NON piazza edifici generici:
        // il lotto del POI e' nostro
        public const float BuildingClearRadius = 24f;

        private static readonly Color DealerColor = new Color(0.16f, 0.45f, 0.85f);
        private static readonly Color RepairColor = new Color(0.95f, 0.55f, 0.10f);
        private static readonly Color GarageColor = new Color(0.15f, 0.65f, 0.35f);
        private static readonly Color HospitalColor = new Color(0.88f, 0.90f, 0.92f);
        private static readonly Color SchoolColor = new Color(0.45f, 0.52f, 0.90f);
        private static readonly Color BarColor = new Color(0.92f, 0.40f, 0.60f);

        /// <summary>Posizioni locali dei POI veicoli del chunk (per il dedup edifici).
        /// Effetto collaterale voluto: registra nell'indice di navigazione TUTTI
        /// i POI della tile (la doc e' tile-wide), cosi' bussola/minimappa/
        /// cartelli funzionano dal primo chunk costruito senza dover visitare
        /// le singole zone.</summary>
        public static List<Vector3> CollectLocalPositions(TileGeoDoc geo,
            System.Func<GeoLL, Vector3> toLocal)
        {
            var outp = new List<Vector3>();
            if (geo?.pois == null) return outp;
            foreach (var poi in geo.pois)
            {
                if (poi?.p == null || poi.p.Length < 2) continue;
                VehiclePoiRegistry.Register(poi.t, poi.id.ToString(),
                    poi.nm ?? "", poi.p[0], poi.p[1], poi.ph, poi.web);
                outp.Add(toLocal(new GeoLL { a = poi.p[0], o = poi.p[1] }));
            }
            return outp;
        }

        public static int Populate(ChunkData chunk,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds)
        {
            if (chunk.geo?.pois == null || chunk.root == null) return 0;

            var root = new GameObject("PoiVeicoli");
            root.transform.SetParent(chunk.root.transform, false);
            root.transform.position = chunk.root.transform.position;

            int placed = 0;
            foreach (var poi in chunk.geo.pois)
            {
                if (poi?.p == null || poi.p.Length < 2) continue;
                try
                {
                    Vector3 local = toLocal(new GeoLL { a = poi.p[0], o = poi.p[1] });
                    if (!bounds.Contains(new Vector2(local.x, local.z))) continue;
                    Build(root.transform, poi, local);
                    placed++;
                }
                catch (System.Exception e)
                {
                    Debug.LogWarning("[PoiPlacer] " + chunk.key + " poi saltato id=" +
                        poi.id + ": " + e.Message);
                }
            }
            return placed;
        }

        private static void Build(Transform parent, TilePoiRec poi, Vector3 local)
        {
            var kind = ParseKind(poi.t);
            if (kind == VehiclePoiZone.PoiKind.Hospital ||
                kind == VehiclePoiZone.PoiKind.School ||
                kind == VehiclePoiZone.PoiKind.Bar ||
                kind == VehiclePoiZone.PoiKind.Bank)
            {
                BuildLandmark(parent, poi, local, kind);
                return;
            }
            if (kind == VehiclePoiZone.PoiKind.Ramp)
            {
                BuildRamp(parent, poi, local);
                return;
            }
            Color accent = kind == VehiclePoiZone.PoiKind.Dealer ? DealerColor
                : kind == VehiclePoiZone.PoiKind.Repair ? RepairColor : GarageColor;
            Vector2 padSize = kind == VehiclePoiZone.PoiKind.Garage
                ? new Vector2(14f, 12f)
                : new Vector2(18f, 14f);
            var go = new GameObject("Poi_" + poi.t + "_" + poi.id);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = new Vector3(local.x, 0f, local.z);

            // piazzale
            var pad = GameObject.CreatePrimitive(PrimitiveType.Cube);
            pad.name = "Piazzale";
            Object.Destroy(pad.GetComponent<Collider>());
            pad.transform.SetParent(go.transform, false);
            pad.transform.localScale = new Vector3(padSize.x, PadHeight, padSize.y);
            pad.transform.localPosition = new Vector3(0f, PadHeight * 0.5f - 0.01f, 0f);
            var mr = pad.GetComponent<MeshRenderer>();
            mr.sharedMaterial = new Material(Shader.Find("Universal Render Pipeline/Lit")
                ?? Shader.Find("Standard"));
            mr.material.color = new Color(accent.r, accent.g, accent.b, 0.9f);

            // insegna su palo (lato strada: verso il centro chunk come approssimazione)
            var sign = new GameObject("Insegna");
            sign.transform.SetParent(go.transform, false);
            float sx = -padSize.x * 0.5f + 1.2f;
            var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            Object.Destroy(pole.GetComponent<Collider>());
            pole.transform.SetParent(sign.transform, false);
            pole.transform.localScale = new Vector3(0.18f, 1.8f, 0.18f);
            pole.transform.localPosition = new Vector3(sx, 1.8f, -padSize.y * 0.5f + 1.0f);
            var panel = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(panel.GetComponent<Collider>());
            panel.transform.SetParent(sign.transform, false);
            panel.transform.localScale = new Vector3(2.6f, 1.1f, 0.15f);
            panel.transform.localPosition =
                new Vector3(sx, 3.6f, -padSize.y * 0.5f + 1.05f);
            var pmr = panel.GetComponent<MeshRenderer>();
            pmr.sharedMaterial = mr.sharedMaterial;
            pmr.material.color = accent;

            // cartello distanze: nome + POI piu' vicino delle altre categorie
            var post = panel.AddComponent<PoiSignpost>();
            post.Setup(VehiclePoiRegistry.KindString(kind),
                poi.id.ToString(), poi.nm);
            // CONCESSIONARIA / OFFICINA / GARAGE (case aperte): la zona
            // risulta ATTIVA su tutto il piazzale, si entra a piedi dalla
            // strada e il prompt apre il negozio. Nessun interno in prima
            // persona: la struttura resta sempre visibile da fuori.
            var zoneGo = new GameObject("Zona");
            zoneGo.transform.SetParent(go.transform, false);
            zoneGo.transform.localPosition = Vector3.zero;
            var col = zoneGo.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(padSize.x + 6f, 4f, padSize.y + 6f);
            col.center = new Vector3(0f, 2f, 0f);
            col.enabled = true;
            var zone = zoneGo.AddComponent<VehiclePoiZone>();
            zone.kind = kind;
            zone.poiId = poi.id.ToString();
            zone.poiName = string.IsNullOrEmpty(poi.nm) ? "" : poi.nm;

            // punto consegna davanti all'insegna
            var delivery = new GameObject("Consegna");
            delivery.transform.SetParent(go.transform, false);
            delivery.transform.localPosition =
                new Vector3(padSize.x * 0.5f - 3f, 0f, -padSize.y * 0.5f + 3f);
            zone.deliveryPoint = delivery.transform;

            // CONCESSIONARIA / OFFICINA / GARAGE = "case aperte": non si
            // entra verso un interno in prima persona. Si costruisce una
            // struttura a vetrina (pareti basse e tetto su pilastri, fronte
            // spalancato sulla strada) con le auto esposte: dal marciapiede
            // si vede dentro e ci si entra a piedi per fare acquisti.
            BuildShowroom(go.transform, padSize, accent, kind);

            // auto esposte nella struttura (non comprabili direttamente: si
            // compra dal catalogo e la vettura nasce al punto di consegna)
            if (VehicleSpawnManager.Instance == null)
            {
                var vsm = new GameObject("VehicleSpawnManager");
                vsm.AddComponent<VehicleSpawnManager>();
            }
            var defs = VehicleSpawnManager.Catalogue;
            int showCount = kind == VehiclePoiZone.PoiKind.Garage ? 4 : 5;
            showCount = Mathf.Min(showCount, defs.Length);
            for (int i = 0; i < showCount; i++)
            {
                var def = defs[(i * 5) % defs.Length];
                int r = i / 2;
                int c = i % 2;
                Vector3 pos = new Vector3(-3.4f + c * 3.6f, 0f, -1.5f - r * 4.4f);
                float yaw = kind == VehiclePoiZone.PoiKind.Garage ? 90f : 180f;
                var showCar = VehicleSpawnManager.BuildVehicle(go.transform, def,
                    pos, yaw, "SHOW_" + poi.id + "_" + i);
                // le vetrine non sono interagibili: via il trigger
                var trig = showCar != null ? showCar.transform.Find("Trigger") : null;
                if (trig != null) Object.Destroy(trig.gameObject);
            }
        }

        /// <summary>
        /// Struttura "aperta" (stile case aperte) per i POI veicolo: tetto su
        /// pilastri, pareti basse sui tre lati, fronte spalancato sulla strada
        /// e bancone sul fondo. Dal marciapiede si vede dentro (auto esposte)
        /// e ci si entra a piedi: niente cambio scena, niente prima persona.
        /// Gli acquisti partono dal prompt quando si e dentro la zona.
        /// </summary>
        private static void BuildShowroom(Transform parent, Vector2 padSize,
            Color accent, VehiclePoiZone.PoiKind kind)
        {
            var root = new GameObject("Showroom");
            root.transform.SetParent(parent, false);

            // tetto a sbalzo (4.6 m)
            var roof = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(roof.GetComponent<Collider>());
            roof.name = "Tetto";
            roof.transform.SetParent(root.transform, false);
            roof.transform.localScale =
                new Vector3(padSize.x + 2f, 0.35f, padSize.y + 3f);
            roof.transform.localPosition = new Vector3(0f, 4.6f, -0.5f);
            SetMrColor(roof, accent * 0.65f);

            // pilastri agli angoli del perimetro
            float hx = padSize.x * 0.5f - 0.4f;
            float hz = padSize.y * 0.5f - 0.4f;
            float ph = 2.3f;
            SpawnPillar(root.transform, new Vector3(-hx, ph, -hz), ph, accent);
            SpawnPillar(root.transform, new Vector3(hx, ph, -hz), ph, accent);
            SpawnPillar(root.transform, new Vector3(-hx, ph, hz), ph, accent);
            SpawnPillar(root.transform, new Vector3(hx, ph, hz), ph, accent);

            // pareti basse sul fondo e sui due lati (fronte strada aperto):
            // abbastanza basse da lasciar vedere le auto da fuori
            float wallH = 0.95f;
            float sideLen = padSize.y * 0.7f;
            SpawnWall(root.transform,
                new Vector3(0f, wallH * 0.5f, padSize.y * 0.5f),
                new Vector3(padSize.x + 0.4f, wallH, 0.25f), accent);
            SpawnWall(root.transform,
                new Vector3(-padSize.x * 0.5f, wallH * 0.5f, -sideLen * 0.15f),
                new Vector3(0.25f, wallH, sideLen), accent);
            SpawnWall(root.transform,
                new Vector3(padSize.x * 0.5f, wallH * 0.5f, -sideLen * 0.15f),
                new Vector3(0.25f, wallH, sideLen), accent);

            // bancone di vendita/assistenza sul fondo
            var counter = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(counter.GetComponent<Collider>());
            counter.name = "Bancone";
            counter.transform.SetParent(root.transform, false);
            counter.transform.localScale = new Vector3(4f, 0.85f, 1.2f);
            counter.transform.localPosition =
                new Vector3(0f, 0.5f, -padSize.y * 0.5f + 2.2f);
            SetMrColor(counter, accent * 0.4f);

            // insegna frontale appesa al tetto (fronte strada)
            var roofSign = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(roofSign.GetComponent<Collider>());
            roofSign.name = "InsegnaFrontale";
            roofSign.transform.SetParent(root.transform, false);
            roofSign.transform.localScale =
                new Vector3(padSize.x * 0.72f, 0.9f, 0.18f);
            roofSign.transform.localPosition =
                new Vector3(0f, 5.35f, -padSize.y * 0.5f + 1.6f);
            SetMrColor(roofSign, accent);
        }

        private static void SpawnPillar(Transform parent, Vector3 pos,
            float h, Color c)
        {
            var p = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            Object.Destroy(p.GetComponent<Collider>());
            p.transform.SetParent(parent, false);
            p.transform.localScale = new Vector3(0.22f, h, 0.22f);
            p.transform.localPosition = pos;
            SetMrColor(p, c);
        }

        private static void SpawnWall(Transform parent, Vector3 pos,
            Vector3 scale, Color c)
        {
            var w = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(w.GetComponent<Collider>());
            w.transform.SetParent(parent, false);
            w.transform.localScale = scale;
            w.transform.localPosition = pos;
            SetMrColor(w, c);
        }

        private static void SetMrColor(GameObject go, Color c)
        {
            var r = go.GetComponent<MeshRenderer>();
            if (r == null) return;
            var mat = new Material(
                Shader.Find("Universal Render Pipeline/Lit")
                    ?? Shader.Find("Standard"));
            mat.color = c;
            r.sharedMaterial = mat;
        }

        /// <summary>
        /// Punti di riferimento non interattivi (ospedale, scuola, bar):
        /// piazzale + palo con cartello. Nessuna zona interattiva: servono
        /// solo come destinazione di navigazione. Ospedale = croce rossa.
        /// </summary>
        private static void BuildLandmark(Transform parent, TilePoiRec poi,
            Vector3 local, VehiclePoiZone.PoiKind kind)
        {
            var go = new GameObject("Poi_" + poi.t + "_" + poi.id);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = new Vector3(local.x, 0f, local.z);

            Color accent = kind == VehiclePoiZone.PoiKind.Hospital
                ? HospitalColor
                : kind == VehiclePoiZone.PoiKind.School ? SchoolColor
                : kind == VehiclePoiZone.PoiKind.Bank ? BankColor
                : BarColor;

            var pad = GameObject.CreatePrimitive(PrimitiveType.Cube);
            pad.name = "Piazzale";
            Object.Destroy(pad.GetComponent<Collider>());
            pad.transform.SetParent(go.transform, false);
            pad.transform.localScale = new Vector3(16f, PadHeight, 12f);
            pad.transform.localPosition =
                new Vector3(0f, PadHeight * 0.5f - 0.01f, 0f);
            var mr = pad.GetComponent<MeshRenderer>();
            mr.sharedMaterial = new Material(
                Shader.Find("Universal Render Pipeline/Lit")
                    ?? Shader.Find("Standard"));
            mr.material.color = accent;

            // palo con cartello colorato sul bordo del piazzale
            var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            Object.Destroy(pole.GetComponent<Collider>());
            pole.transform.SetParent(go.transform, false);
            pole.transform.localScale = new Vector3(0.18f, 1.8f, 0.18f);
            pole.transform.localPosition = new Vector3(-6.2f, 1.8f, -5f);

            var board = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(board.GetComponent<Collider>());
            board.name = "Cartello_" + poi.t;
            board.transform.SetParent(go.transform, false);
            board.transform.localScale = new Vector3(2.8f, 1.3f, 0.14f);
            board.transform.localPosition = new Vector3(-6.2f, 3.8f, -5f);
            var bmr = board.GetComponent<MeshRenderer>();
            bmr.sharedMaterial = mr.sharedMaterial;
            bmr.material.color = accent;

            if (kind == VehiclePoiZone.PoiKind.Hospital)
            {
                // croce rossa al centro del cartello
                var barV = GameObject.CreatePrimitive(PrimitiveType.Cube);
                Object.Destroy(barV.GetComponent<Collider>());
                barV.name = "BarraV";
                barV.transform.SetParent(board.transform, false);
                barV.transform.localScale = new Vector3(0.30f, 1.05f, 0.05f);
                barV.transform.localPosition = Vector3.zero;
                var barH = GameObject.CreatePrimitive(PrimitiveType.Cube);
                Object.Destroy(barH.GetComponent<Collider>());
                barH.name = "BarraH";
                barH.transform.SetParent(board.transform, false);
                barH.transform.localScale = new Vector3(1.05f, 0.30f, 0.05f);
                barH.transform.localPosition = Vector3.zero;
                var rmr = barV.GetComponent<MeshRenderer>();
                rmr.sharedMaterial = mr.sharedMaterial;
                rmr.material.color = new Color(0.85f, 0.10f, 0.10f);
                barH.GetComponent<MeshRenderer>().sharedMaterial =
                    rmr.sharedMaterial;
            }

            var post = board.AddComponent<PoiSignpost>();
            post.Setup(VehiclePoiRegistry.KindString(kind),
                poi.id.ToString(), poi.nm);

            // ingresso fisico all'interno 3D (bordo del piazzale)
            string buildingName = string.IsNullOrEmpty(poi.nm)
                ? LandmarkDefaultName(kind) : poi.nm;
            AddEntrance(go.transform, new Vector2(16f, 12f),
                KindType(kind), buildingName, null);
        }

        private static string LandmarkDefaultName(VehiclePoiZone.PoiKind kind)
        {
            switch (kind)
            {
                case VehiclePoiZone.PoiKind.Hospital: return "Ospedale";
                case VehiclePoiZone.PoiKind.School: return "Scuola";
                case VehiclePoiZone.PoiKind.Bank: return "Banca / ATM";
                default: return "Bar";
            }
        }

        private static readonly Color RampColor = new Color(0.18f, 0.22f, 0.28f);
        private static readonly Color BankColor = new Color(1.00f, 0.84f, 0.30f);

        private static void BuildRamp(Transform parent, TilePoiRec poi,
            Vector3 local)
        {
            var go = new GameObject("Poi_rampa_" + poi.id);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = new Vector3(local.x, 0f, local.z);

            var pad = GameObject.CreatePrimitive(PrimitiveType.Cube);
            pad.name = "Piazzale";
            Object.Destroy(pad.GetComponent<Collider>());
            pad.transform.SetParent(go.transform, false);
            pad.transform.localScale = new Vector3(16f, PadHeight, 12f);
            pad.transform.localPosition =
                new Vector3(0f, PadHeight * 0.5f - 0.01f, 0f);
            var mr = pad.GetComponent<MeshRenderer>();
            mr.sharedMaterial = new Material(
                Shader.Find("Universal Render Pipeline/Lit")
                    ?? Shader.Find("Standard"));
            mr.material.color = RampColor;

            var tunnel = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(tunnel.GetComponent<Collider>());
            tunnel.name = "Discesa";
            tunnel.transform.SetParent(go.transform, false);
            tunnel.transform.localScale = new Vector3(5f, 0.6f, 8f);
            tunnel.transform.localPosition = new Vector3(0f, 0.15f, 3f);
            var tmr = tunnel.GetComponent<MeshRenderer>();
            tmr.sharedMaterial = mr.sharedMaterial;
            tmr.material.color = new Color(0.08f, 0.08f, 0.10f);

            var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            Object.Destroy(pole.GetComponent<Collider>());
            pole.transform.SetParent(go.transform, false);
            pole.transform.localScale = new Vector3(0.18f, 1.8f, 0.18f);
            pole.transform.localPosition = new Vector3(-5.5f, 1.8f, -5f);

            var sign = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(sign.GetComponent<Collider>());
            sign.name = "Cartello";
            sign.transform.SetParent(go.transform, false);
            sign.transform.localScale = new Vector3(2.4f, 1.4f, 0.12f);
            sign.transform.localPosition = new Vector3(-5.5f, 3.0f, -5f);
            var bmr = sign.GetComponent<MeshRenderer>();
            bmr.sharedMaterial = mr.sharedMaterial;
            bmr.material.color = RampColor;

            var post = sign.AddComponent<PoiSignpost>();
            post.Setup("rampa", poi.id.ToString(), poi.nm);

            var zoneGo = new GameObject("Zona");
            zoneGo.transform.SetParent(go.transform, false);
            zoneGo.transform.localPosition = Vector3.zero;
            var col = zoneGo.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(22f, 4f, 18f);
            col.center = new Vector3(0f, 2f, 0f);
            var zone = zoneGo.AddComponent<VehiclePoiZone>();
            zone.kind = VehiclePoiZone.PoiKind.Ramp;
            zone.poiId = poi.id.ToString();
            zone.poiName = string.IsNullOrEmpty(poi.nm) ? "" : poi.nm;

            var delivery = new GameObject("Consegna");
            delivery.transform.SetParent(go.transform, false);
            delivery.transform.localPosition = new Vector3(5f, 0f, -3f);
            zone.deliveryPoint = delivery.transform;
        }

        private static VehiclePoiZone.PoiKind ParseKind(string t)
        {
            switch (t)
            {
                case "dealer": return VehiclePoiZone.PoiKind.Dealer;
                case "repair": return VehiclePoiZone.PoiKind.Repair;
                case "hospital": return VehiclePoiZone.PoiKind.Hospital;
                case "rampa": return VehiclePoiZone.PoiKind.Ramp;
                case "school": return VehiclePoiZone.PoiKind.School;
                case "bar": return VehiclePoiZone.PoiKind.Bar;
                case "bank": return VehiclePoiZone.PoiKind.Bank;
                default: return VehiclePoiZone.PoiKind.Garage;
            }
        }

        /// <summary>Stringa buildingType per l'interno 3D (allineata ai casi di
        /// InteriorGenerator.BuildInterior).</summary>
        private static string KindType(VehiclePoiZone.PoiKind kind)
        {
            switch (kind)
            {
                case VehiclePoiZone.PoiKind.Dealer: return "dealer";
                case VehiclePoiZone.PoiKind.Repair: return "repair";
                case VehiclePoiZone.PoiKind.Hospital: return "hospital";
                case VehiclePoiZone.PoiKind.School: return "school";
                case VehiclePoiZone.PoiKind.Bar: return "bar";
                case VehiclePoiZone.PoiKind.Bank: return "bank";
                default: return "garage";
            }
        }

        /// <summary>
        /// Aggiunge l'ingresso fisico (BuildingEntrance) sul bordo del piazzale
        /// del POI. L'entrance fa partire l'auto-entry dentro l'interno 3D. La
        /// zona POI veicolo (se presente) viene associata per il bancone interno.
        /// </summary>
        private static void AddEntrance(Transform parent, Vector2 padSize,
            string buildingType, string buildingName, VehiclePoiZone zone)
        {
            var entGo = new GameObject("Ingresso");
            entGo.transform.SetParent(parent, false);
            float edgeZ = -padSize.y * 0.5f + 0.5f;
            entGo.transform.localPosition = new Vector3(0f, 0f, edgeZ);

            var col = entGo.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(4f, 4f, 2.5f);
            col.center = new Vector3(0f, 2f, 0f);

            var entrance = entGo.AddComponent<City.Interior.BuildingEntrance>();
            entrance.buildingName = buildingName;
            entrance.buildingType = buildingType;
            entrance.buildingWidth = Mathf.Max(4f, padSize.x);
            entrance.buildingDepth = Mathf.Max(4f, padSize.y);
            entrance.buildingHeight = 8f;
            entrance.floorCount = 1;
            entrance.poiZone = zone;
        }
    }
}
