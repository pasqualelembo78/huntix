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

        /// <summary>Posizioni locali dei POI veicoli del chunk (per il dedup edifici).</summary>
        public static List<Vector3> CollectLocalPositions(TileGeoDoc geo,
            System.Func<GeoLL, Vector3> toLocal)
        {
            var outp = new List<Vector3>();
            if (geo?.pois == null) return outp;
            foreach (var poi in geo.pois)
            {
                if (poi?.p == null || poi.p.Length < 2) continue;
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

            // zona interattiva (trigger largo quanto il piazzale)
            var zoneGo = new GameObject("Zona");
            zoneGo.transform.SetParent(go.transform, false);
            zoneGo.transform.localPosition = Vector3.zero;
            var col = zoneGo.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(padSize.x + 6f, 4f, padSize.y + 6f);
            col.center = new Vector3(0f, 2f, 0f);
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

            // vetrina: 3 auto esposte nelle concessionarie (non comprabili
            // direttamente: si compra dal catalogo, l'auto nasce alla consegna)
            if (kind == VehiclePoiZone.PoiKind.Dealer)
            {
                if (VehicleSpawnManager.Instance == null)
                {
                    var vsm = new GameObject("VehicleSpawnManager");
                    vsm.AddComponent<VehicleSpawnManager>();
                }
                var defs = VehicleSpawnManager.Catalogue;
                for (int i = 0; i < 3 && i < defs.Length; i++)
                {
                    var def = defs[(i * 5) % defs.Length];
                    Vector3 pos = new Vector3(-3f + i * 3.2f, 0f, 1.5f);
                    VehicleSpawnManager.BuildVehicle(go.transform, def,
                        pos, 180f, "SHOW_" + poi.id + "_" + i);
                    // le vetrine non sono interagibili: via il trigger
                    var trig = go.transform.Find(
                        "SHOW_" + poi.id + "_" + i + "/Trigger");
                    if (trig != null) Object.Destroy(trig.gameObject);
                }
            }
        }

        private static VehiclePoiZone.PoiKind ParseKind(string t)
        {
            switch (t)
            {
                case "dealer": return VehiclePoiZone.PoiKind.Dealer;
                case "repair": return VehiclePoiZone.PoiKind.Repair;
                default: return VehiclePoiZone.PoiKind.Garage;
            }
        }
    }
}
