using System;
using System.Collections.Generic;
using UnityEngine;
using Huntix.Bridge;

namespace City.Interior.Visit
{
    /// <summary>
    /// Livello ADDITIVO che arreda la casa visitabile con il kit completo
    /// `Cozy Interiors + Builder` di SENDU (339 FBX + palette/pattern PNG,
    /// licenza commerciale gratuita che consente il shipping nel gioco).
    ///
    /// Si innesta ALLA FINE di VisitInteriorDecorator.Decorate: i 32 arredi CC0
    /// restano dov'erano, qui si aggiungono 31 oggetti texturizzati con le
    /// palette del kit. Nessun builder esistente viene sostituito, nessun
    /// collider sui mobili, nessun file geo/graph toccato.
    ///
    /// Compatibilità: i FBX sono engine-agnostic (Y-up, 1 unit = 1 m) e hanno
    /// due figli _LOD0/_LOD1. Il LOD1 viene disattivato per non disegnare due
    /// volte la stessa mesh. Gli shader custom del kit richiedono Unity 6000.6:
    /// qui si usano solo URP/Lit con la palette 8x8 (filtro Point, no mipmap).
    ///
    /// Riflesso della caccia alle uova (Mandato): un quarto nascondiglio reale
    /// nel cassetto SENDU della cucina, con luogo testuale e bridge Android.
    /// </summary>
    public static class SenduInteriorDecorator
    {
        private const string ModelFolder = "HouseInteriors/Sendu/FBX/";
        private const string Palette = "HouseInteriors/Sendu/Palettes/CI_Theme_Honey";

        private static Material _paletteMaterial;

        [Serializable]
        private sealed class Slot
        {
            public string folder;
            public string model;
            public float x;
            public float z;
            public float sx;
            public float sy;
            public float sz;
            public float yaw;
            public string room;
            public bool container;
            public string featureId;
            public string eggLabel;
            public string place;

            public Slot(string folder, string model, float x, float z,
                float sx, float sy, float sz, float yaw, string room,
                bool container = false, string featureId = "",
                string eggLabel = "", string place = "")
            {
                this.folder = folder;
                this.model = model;
                this.x = x;
                this.z = z;
                this.sx = sx;
                this.sy = sy;
                this.sz = sz;
                this.yaw = yaw;
                this.room = room;
                this.container = container;
                this.featureId = featureId;
                this.eggLabel = eggLabel;
                this.place = place;
            }
        }

        private static readonly Slot[] Layout =
        {
            // ── SOGGIORNO ────────────────────────────────────────────
            new Slot("Living", "CI_Living_Sideboard", 0.44f, 0.42f, 1.6f, 0.9f, 0.45f, 0f, "soggiorno"),
            new Slot("Living", "CI_Living_FloorCushion", 0.20f, 0.43f, 0.6f, 0.15f, 0.6f, 0f, "soggiorno"),
            new Slot("Living", "CI_Living_WallShelf", 0.47f, 0.30f, 0.9f, 0.06f, 0.25f, 0f, "soggiorno"),
            new Slot("Living", "CI_Living_Lamp_Table", 0.44f, 0.29f, 0.3f, 0.45f, 0.3f, 0f, "soggiorno"),
            new Slot("Living", "CI_Living_Pouf", 0.13f, 0.44f, 0.5f, 0.4f, 0.5f, 0f, "soggiorno"),
            new Slot("Decor", "CI_Decor_Plant_Monstera", 0.48f, 0.11f, 0.7f, 1.1f, 0.7f, 0f, "soggiorno"),
            new Slot("Decor", "CI_Decor_Rug_Round", 0.22f, 0.30f, 1.4f, 0.02f, 1.4f, 0f, "soggiorno"),
            new Slot("Decor", "CI_Decor_Frame_Cluster", 0.49f, 0.23f, 0.7f, 0.5f, 0.08f, 0f, "soggiorno"),
            new Slot("Decor", "CI_Decor_CeilingLight_ThreeArm", 0.30f, 0.30f, 0.8f, 0.4f, 0.8f, 0f, "soggiorno"),

            // ── CUCINA (quarto nascondiglio: cassetto SENDU) ─────────
            new Slot("Kitchen", "CI_Kitchen_Base_Drawer_0p6m", 0.44f, -0.40f, 0.6f, 0.9f, 0.6f, 0f, "cucina",
                true, "casa-cucina-cassetto-sendu", "Uovo nel cassetto SENDU della cucina",
                "casa→cucina→cassetto-sendu"),
            new Slot("Kitchen", "CI_Kitchen_WallShelf_Open_0p6m", 0.48f, -0.29f, 0.6f, 0.08f, 0.25f, 0f, "cucina"),
            new Slot("Kitchen", "CI_Kitchen_SpiceRack", 0.36f, -0.37f, 0.3f, 0.4f, 0.2f, 0f, "cucina"),
            new Slot("Kitchen", "CI_Kitchen_Kettle", 0.40f, -0.10f, 0.25f, 0.25f, 0.25f, 0f, "cucina"),
            new Slot("Kitchen", "CI_Kitchen_FruitBowl", 0.20f, -0.29f, 0.3f, 0.12f, 0.3f, 0f, "cucina"),
            new Slot("Kitchen", "CI_Kitchen_PedalBin", 0.48f, -0.46f, 0.25f, 0.5f, 0.25f, 0f, "cucina"),
            new Slot("Kitchen", "CI_Kitchen_TeaTowelRail", 0.46f, -0.18f, 0.25f, 0.35f, 0.1f, 0f, "cucina"),
            new Slot("Kitchen", "CI_Kitchen_WallClock", 0.48f, -0.05f, 0.3f, 0.3f, 0.08f, 0f, "cucina"),

            // ── CAMERA ──────────────────────────────────────────────
            new Slot("Bedroom", "CI_Bedroom_BedsideTable_Drawer", -0.47f, -0.16f, 0.45f, 0.6f, 0.4f, 0f, "camera"),
            new Slot("Bedroom", "CI_Bedroom_Lamp_Bedside", -0.31f, -0.16f, 0.2f, 0.35f, 0.2f, 0f, "camera"),
            new Slot("Bedroom", "CI_Bedroom_BlanketChest", -0.12f, -0.45f, 0.9f, 0.5f, 0.45f, 0f, "camera"),
            new Slot("Bedroom", "CI_Bedroom_PhotoFrame_Pair", -0.47f, -0.28f, 0.4f, 0.3f, 0.08f, 0f, "camera"),
            new Slot("Bedroom", "CI_Bedroom_Slippers", -0.20f, -0.08f, 0.3f, 0.1f, 0.2f, 0f, "camera"),
            new Slot("Bedroom", "CI_Bedroom_WindowSeat", -0.47f, -0.02f, 1.2f, 0.45f, 0.4f, 0f, "camera"),
            new Slot("Decor", "CI_Decor_Plant_SnakePlant", -0.47f, -0.11f, 0.5f, 1.0f, 0.5f, 0f, "camera"),

            // ── BAGNO ──────────────────────────────────────────────
            new Slot("Bathroom", "CI_Bathroom_Vanity_Basin", -0.45f, 0.22f, 0.8f, 0.9f, 0.5f, 0f, "bagno"),
            new Slot("Bathroom", "CI_Bathroom_MirrorCabinet", -0.48f, 0.30f, 0.6f, 0.7f, 0.15f, 0f, "bagno"),
            new Slot("Bathroom", "CI_Bathroom_TowelRadiator", -0.48f, 0.10f, 0.5f, 0.8f, 0.12f, 0f, "bagno"),
            new Slot("Bathroom", "CI_Bathroom_BathMat", -0.25f, 0.22f, 0.7f, 0.02f, 0.45f, 0f, "bagno"),
            new Slot("Bathroom", "CI_Bathroom_LinenCabinet_Tall", -0.47f, 0.05f, 0.5f, 1.8f, 0.4f, 0f, "bagno"),
            new Slot("Bathroom", "CI_Bathroom_Bin_Small", -0.30f, 0.46f, 0.2f, 0.3f, 0.2f, 0f, "bagno"),
            new Slot("Decor", "CI_Decor_Plant_FernStand", -0.46f, 0.45f, 0.5f, 1.0f, 0.5f, 0f, "bagno"),
            new Slot("Bathroom", "CI_Bathroom_Sconce_Wall", -0.49f, 0.40f, 0.2f, 0.3f, 0.15f, 0f, "bagno"),
        };

        /// <summary>Chiamato a fine VisitInteriorDecorator.Decorate. Ritorna
        /// il numero di arredi aggiunti (0 se il root non è una casa visitable).</summary>
        public static int Decorate(Transform root, float w, float d)
        {
            if (root == null) return 0;
            if (FindChild(root, "ArrediSENDU") != null) return 0; // idempotente

            float W = Mathf.Max(6f, w);
            float D = Mathf.Max(6f, d);
            var holder = new GameObject("ArrediSENDU");
            holder.transform.SetParent(root, false);
            holder.transform.localPosition = Vector3.zero;
            holder.transform.localRotation = Quaternion.identity;
            holder.transform.localScale = Vector3.one;

            int placed = 0;
            string key = VisitInteriorRegistry.All.Length > 0
                ? VisitInteriorRegistry.StableKey(VisitInteriorRegistry.All[0].osmId)
                : string.Empty;
            for (int i = 0; i < Layout.Length; i++)
            {
                Slot s = Layout[i];
                GameObject prefab = Resources.Load<GameObject>(ModelFolder + s.folder + "/" + s.model);
                if (prefab == null) continue;

                // Come per gli arredi CC0: l'holder porta la posizione
                // normalizzata sull'impronta OSM (x*W, z*D) e la rotazione,
                // il modello figlio viene solo riscalato e bottom-aligned.
                var slotRoot = new GameObject(s.model);
                slotRoot.transform.SetParent(holder.transform, false);
                slotRoot.transform.localPosition = new Vector3(s.x * W, 0f, s.z * D);
                slotRoot.transform.localRotation = Quaternion.Euler(0f, s.yaw, 0f);
                slotRoot.transform.localScale = Vector3.one;

                var item = (GameObject)UnityEngine.Object.Instantiate(prefab, slotRoot.transform, false);
                item.name = s.model;
                item.transform.localPosition = Vector3.zero;
                item.transform.localRotation = Quaternion.identity;
                item.transform.localScale = Vector3.one;

                DisableLod1(item.transform);
                StripCollidersAndPaint(item);
                Fit(item.transform, new Vector3(s.sx, s.sy, s.sz));
                placed++;

                if (!s.container) continue;
                var marker = slotRoot.AddComponent<UniversalContainerMarker>();
                marker.room = s.room;
                marker.interiorKey = key;
                var spot = slotRoot.AddComponent<HouseEggSpot>();
                spot.featureId = s.featureId;
                spot.label = s.eggLabel;
                spot.place = s.place;
                spot.room = s.room;
            }

            try
            {
                UnityBridge.LogToAndroid(VisitInteriorRegistry.Tag,
                    "kit SENDU installato: " + placed + " arredi texturizzati, " +
                    "4 uova totali nella casa visitabile");
            }
            catch (Exception) { }
            return placed;
        }

        private static void DisableLod1(Transform root)
        {
            Transform[] all = root.GetComponentsInChildren<Transform>(true);
            for (int i = 0; i < all.Length; i++)
            {
                if (all[i] != null && all[i].name.EndsWith("_LOD1", StringComparison.Ordinal))
                    all[i].gameObject.SetActive(false);
            }
        }

        private static void StripCollidersAndPaint(GameObject go)
        {
            Collider[] cols = go.GetComponentsInChildren<Collider>(true);
            for (int i = 0; i < cols.Length; i++) UnityEngine.Object.Destroy(cols[i]);

            Material mat = PaletteMaterial();
            Renderer[] rends = go.GetComponentsInChildren<Renderer>(true);
            for (int i = 0; i < rends.Length; i++)
            {
                if (rends[i] == null) continue;
                // Anche i figli _LOD0 devono stare sul layer edificio, non solo
                // la radice: GroundSnapper li esclude dalle sonde a terra.
                rends[i].gameObject.layer = 8;
                if (mat == null) continue;
                Material[] dst = new Material[rends[i].sharedMaterials.Length];
                for (int m = 0; m < dst.Length; m++) dst[m] = mat;
                rends[i].sharedMaterials = dst;
            }
        }

        private static Material PaletteMaterial()
        {
            if (_paletteMaterial != null) return _paletteMaterial;
            Shader shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) return null;
            _paletteMaterial = new Material(shader);
            _paletteMaterial.name = "CasaSENDU_PaletteHoney";
            Texture tex = Resources.Load<Texture>(Palette);
            if (tex != null)
            {
                if (_paletteMaterial.HasProperty("_BaseMap"))
                    _paletteMaterial.SetTexture("_BaseMap", tex);
                if (_paletteMaterial.HasProperty("_MainTex"))
                    _paletteMaterial.SetTexture("_MainTex", tex);
            }
            if (_paletteMaterial.HasProperty("_BaseColor"))
                _paletteMaterial.SetColor("_BaseColor", Color.white);
            if (_paletteMaterial.HasProperty("_Smoothness"))
                _paletteMaterial.SetFloat("_Smoothness", 0.25f);
            return _paletteMaterial;
        }

        /// <summary>Riscala sull'impronta e allinea alla base, come gli arredi CC0.
        /// Nessun collider: la navigazione resta guscio + paratie.</summary>
        private static void Fit(Transform inst, Vector3 target)
        {
            Renderer[] rends = inst.GetComponentsInChildren<Renderer>(true);
            Bounds b = new Bounds(Vector3.zero, Vector3.zero);
            bool first = true;
            Vector3 origin = inst.position;
            for (int i = 0; i < rends.Length; i++)
            {
                if (rends[i] == null) continue;
                Bounds rb = rends[i].bounds;
                rb.center -= origin;
                if (first) { b = rb; first = false; }
                else b.Encapsulate(rb);
            }
            Vector3 size = new Vector3(
                Mathf.Max(b.size.x, 0.03f), Mathf.Max(b.size.y, 0.03f),
                Mathf.Max(b.size.z, 0.03f));
            Vector3 scale = new Vector3(
                Mathf.Clamp(target.x / size.x, 0.02f, 40f),
                Mathf.Clamp(target.y / size.y, 0.02f, 40f),
                Mathf.Clamp(target.z / size.z, 0.02f, 40f));
            inst.localScale = scale;
            inst.localPosition = new Vector3(
                -b.center.x * scale.x, -b.min.y * scale.y, -b.center.z * scale.z);
        }

        private static Transform FindChild(Transform root, string name)
        {
            if (root == null) return null;
            Transform[] all = root.GetComponentsInChildren<Transform>(true);
            for (int i = 0; i < all.Length; i++)
                if (all[i] != null && all[i].name == name) return all[i];
            return null;
        }
    }
}
