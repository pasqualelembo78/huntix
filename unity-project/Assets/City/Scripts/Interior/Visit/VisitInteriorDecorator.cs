using System;
using System.Collections.Generic;
using UnityEngine;
using City.World;
using Huntix.Bridge;

namespace City.Interior.Visit
{
    /// <summary>
    /// Arreda la casa OSM configurata con i modelli CC0 `Cozy Home Interiors`
    /// importati in Resources. Si innesta ALLA FINE di InteriorGenerator.BuildInterior,
    /// sopra il Floor_0 già costruito dal sistema universale: nessun builder
    /// esistente viene sostituito e nessun dato geo/graph cambia.
    ///
    /// Il prefab `CasaVisitabileCC0` contiene il layout (slot normalizzati
    /// sull'impronta OSM); i modelli FBX sono riscalati sullo slot, con
    /// materiali URP/Lit condivisi e ZERO collider sui mobili. Solo le paratie
    /// interne hanno collider, così soggiorno/cucina/camera/bagno sono
    /// navigabili in terza persona.
    /// </summary>
    public static class VisitInteriorDecorator
    {
        public const string RootName = "CasaVisitabileCC0";
        private const string ModelFolder = "HouseInteriors/CozyHome/";

        private static readonly Dictionary<int, Material> ConvertedMaterials =
            new Dictionary<int, Material>();
        private static readonly Dictionary<string, Material> SolidMaterials =
            new Dictionary<string, Material>();

        public static bool Decorate(Transform parent, long osmId, string type,
            Shop shop, float w, float d, float floorH, int floors, string buildingName)
        {
            VisitInteriorRegistry.Spec spec;
            if (parent == null || !VisitInteriorRegistry.TryGet(osmId, out spec))
                return false;

            Transform floor = FindChild(parent, "Floor_0");
            if (floor == null) return false;
            if (FindChild(floor, RootName) != null) return true; // idempotente

            float W = Mathf.Max(6f, w);
            float D = Mathf.Max(6f, d);
            GameObject asset = Resources.Load<GameObject>(VisitInteriorRegistry.PrefabResource);
            GameObject root;
            HouseSlots slots;
            if (asset != null)
            {
                root = (GameObject)UnityEngine.Object.Instantiate(asset, floor, false);
                root.name = RootName;
                slots = root.GetComponent<HouseSlots>();
            }
            else
            {
                // Build vecchie: il prefab non esiste ancora, ma il gioco resta
                // giocabile perché il layout è disponibile anche in codice.
                root = new GameObject(RootName);
                root.transform.SetParent(floor, false);
                slots = null;
            }
            root.transform.localPosition = Vector3.zero;
            root.transform.localRotation = Quaternion.identity;
            root.transform.localScale = Vector3.one;
            if (slots == null) slots = root.AddComponent<HouseSlots>();
            if (slots.slots == null || slots.slots.Count == 0)
                slots.slots = HouseSlots.DefaultLayout();

            // I divisori Wisuali del builder universale sarebbero doppi qui.
            RemoveVisualDividers(floor);

            BuildPartitions(root.transform, W, D);

            string key = VisitInteriorRegistry.StableKey(osmId);
            List<HouseSlot> list = slots.slots;
            for (int i = 0; i < list.Count; i++)
            {
                HouseSlot slot = list[i];
                if (slot == null || string.IsNullOrEmpty(slot.model)) continue;
                var holder = new GameObject(string.IsNullOrEmpty(slot.sceneName)
                    ? slot.model : slot.sceneName);
                holder.transform.SetParent(root.transform, false);
                holder.transform.localPosition = new Vector3(
                    slot.position.x * W, 0f, slot.position.y * D);
                holder.transform.localRotation = Quaternion.Euler(0f, slot.yaw, 0f);
                InstantiateModel(holder.transform, slot);

                if (slot.container)
                {
                    // ContainerEgg riconosce il nome e legge la stanza dal marker.
                    var cm = holder.AddComponent<UniversalContainerMarker>();
                    cm.room = slot.room;
                    cm.interiorKey = key;
                    var spot = holder.AddComponent<HouseEggSpot>();
                    spot.featureId = slot.featureId;
                    spot.label = slot.eggLabel;
                    spot.place = slot.place;
                    spot.room = slot.room;
                }
            }

            // Aggiorna l'etichetta del marker universale già presente: niente
            // secondo marker, InteriorManager continua a leggere lo stesso.
            UniversalInteriorMarker marker = UniversalInteriorBuilder.FindMarker(floor);
            if (marker != null)
            {
                marker.label = spec.label;
                marker.eggHint = "Uova in cucina, camera e bagno della casa visitabile.";
            }

            // Kit SENDU (licenza commerciale gratuita): si AGGIUNGE al livello
            // CC0, non lo sostituisce. 32 arredi con palette texturizzate e
            // un quarto nascondiglio nel cassetto della cucina.
            SenduInteriorDecorator.Decorate(root.transform, W, D);

            try
            {
                UnityBridge.LogToAndroid(VisitInteriorRegistry.Tag,
                    "casa visitabile pronta: " + spec.label + " (OSM " + osmId +
                    ") — " + list.Count + " arredi CC0 + 32 SENDU, 4 stanze, 4 uova");
            }
            catch (Exception) { }
            return true;
        }

        private static void InstantiateModel(Transform holder, HouseSlot slot)
        {
            GameObject prefab = Resources.Load<GameObject>(ModelFolder + slot.model);
            if (prefab == null)
            {
                // Fallback visivo: non lasciare buchi se un FBX non è nell'AAR.
                var cube = GameObject.CreatePrimitive(PrimitiveType.Cube);
                cube.name = slot.model + "_Fallback";
                cube.transform.SetParent(holder, false);
                cube.transform.localPosition = new Vector3(0f, slot.size.y * 0.5f, 0f);
                cube.transform.localScale = slot.size;
                cube.GetComponent<Renderer>().sharedMaterial =
                    SolidMaterial(new Color(0.72f, 0.62f, 0.50f));
                UnityEngine.Object.Destroy(cube.GetComponent<Collider>());
                return;
            }

            GameObject inst = (GameObject)UnityEngine.Object.Instantiate(prefab, holder, false);
            inst.name = slot.model;
            inst.transform.localPosition = Vector3.zero;
            inst.transform.localRotation = Quaternion.identity;
            inst.transform.localScale = Vector3.one;

            // I modelli importati non devono mai portare collider: la fisica è
            // solo guscio + paratie, come da vincoli sul frame rate.
            Collider[] cols = inst.GetComponentsInChildren<Collider>(true);
            for (int i = 0; i < cols.Length; i++) UnityEngine.Object.Destroy(cols[i]);
            ConvertMaterials(inst);

            Renderer[] rends = inst.GetComponentsInChildren<Renderer>(true);
            Bounds b = new Bounds(Vector3.zero, Vector3.zero);
            bool first = true;
            Vector3 origin = inst.transform.position;
            for (int i = 0; i < rends.Length; i++)
            {
                if (rends[i] == null) continue;
                Bounds rb = rends[i].bounds;
                rb.center -= origin;
                if (first) { b = rb; first = false; }
                else b.Encapsulate(rb);
            }
            Vector3 sx = new Vector3(
                Mathf.Max(b.size.x, 0.03f),
                Mathf.Max(b.size.y, 0.03f),
                Mathf.Max(b.size.z, 0.03f));
            Vector3 scale = new Vector3(
                Mathf.Clamp(slot.size.x / sx.x, 0.02f, 40f),
                Mathf.Clamp(slot.size.y / sx.y, 0.02f, 40f),
                Mathf.Clamp(slot.size.z / sx.z, 0.02f, 40f));
            inst.transform.localScale = scale;
            // Bottom-align: i parametri di grabbing dei modelli sono
            // eterogenei, qui si allinea sempre a pavimento.
            inst.transform.localPosition = new Vector3(
                -b.center.x * scale.x, -b.min.y * scale.y, -b.center.z * scale.z);
        }

        private static void ConvertMaterials(GameObject go)
        {
            Renderer[] rends = go.GetComponentsInChildren<Renderer>(true);
            for (int i = 0; i < rends.Length; i++)
            {
                if (rends[i] == null) continue;
                Material[] src = rends[i].sharedMaterials;
                if (src == null || src.Length == 0) continue;
                Material[] dst = new Material[src.Length];
                for (int m = 0; m < src.Length; m++) dst[m] = ConvertMaterial(src[m]);
                rends[i].sharedMaterials = dst;
            }
        }

        private static Material ConvertMaterial(Material src)
        {
            if (src == null) return null;
            if (src.shader != null &&
                src.shader.name != null &&
                src.shader.name.StartsWith("Universal Render Pipeline", StringComparison.Ordinal))
                return src;

            int id = src.GetInstanceID();
            Material cached;
            if (ConvertedMaterials.TryGetValue(id, out cached)) return cached;
            Shader shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) return src;
            var mat = new Material(shader);
            mat.name = "CasaCC0_" + src.name;
            string prop = mat.HasProperty("_BaseColor") ? "_BaseColor" : "_Color";
            if (src.HasProperty(prop)) mat.SetColor(prop, src.GetColor(prop));
            else if (src.HasProperty("_Color")) mat.SetColor(prop, src.GetColor("_Color"));
            if (mat.HasProperty("_Smoothness")) mat.SetFloat("_Smoothness", 0.22f);
            ConvertedMaterials[id] = mat;
            return mat;
        }

        private static Material SolidMaterial(Color c)
        {
            string key = c.r.ToString("F2") + "," + c.g.ToString("F2") + "," + c.b.ToString("F2");
            Material m;
            if (SolidMaterials.TryGetValue(key, out m)) return m;
            Shader shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            m = new Material(shader);
            string prop = m.HasProperty("_BaseColor") ? "_BaseColor" : "_Color";
            m.SetColor(prop, c);
            if (m.HasProperty("_Smoothness")) m.SetFloat("_Smoothness", 0.18f);
            SolidMaterials[key] = m;
            return m;
        }

        /// <summary>Quattro paratie con due varchi da ~2,2 m: si passa dal
        /// soggiorno alla cucina e dalla camera al bagno senza teletrasporti.
        /// Solo le paratie (e il pavimento del builder) hanno collider.</summary>
        private static void BuildPartitions(Transform root, float w, float d)
        {
            const float gap = 1.1f;
            float h = 2.5f;
            float y = h * 0.5f;
            float zw = d * 0.5f - gap;
            float xw = w * 0.5f - gap;

            if (zw > 0.4f)
            {
                Wall(root, "PareteCasaVerticaleRetro",
                    new Vector3(0f, y, (-d * 0.5f - gap) * 0.5f),
                    new Vector3(0.16f, h, zw));
                Wall(root, "PareteCasaVerticaleFronte",
                    new Vector3(0f, y, (d * 0.5f + gap) * 0.5f),
                    new Vector3(0.16f, h, zw));
            }
            if (xw > 0.4f)
            {
                Wall(root, "PareteCasaOrizzontaleSinistra",
                    new Vector3((-w * 0.5f - gap) * 0.5f, y, 0f),
                    new Vector3(xw, h, 0.16f));
                Wall(root, "PareteCasaOrizzontaleDestra",
                    new Vector3((w * 0.5f + gap) * 0.5f, y, 0f),
                    new Vector3(xw, h, 0.16f));
            }
            // Architravi decorativi sopra i due varchi (nessun collider).
            Frame(root, "PortaCasaCucinaSoggiorno",
                new Vector3(0f, 2.42f, 0f), new Vector3(0.24f, 0.16f, gap * 2f));
            Frame(root, "PortaCasaCameraBagno",
                new Vector3(0f, 2.42f, 0f), new Vector3(gap * 2f, 0.16f, 0.24f));
        }

        private static void Wall(Transform root, string name, Vector3 pos, Vector3 scale)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(root, false);
            go.transform.localPosition = pos;
            go.transform.localScale = scale;
            go.layer = 8; // BuildingLayer: escluso dalle sonde di GroundSnapper
            go.GetComponent<Renderer>().sharedMaterial =
                SolidMaterial(new Color(0.90f, 0.87f, 0.81f));
        }

        private static void Frame(Transform root, string name, Vector3 pos, Vector3 scale)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(root, false);
            go.transform.localPosition = pos;
            go.transform.localScale = scale;
            go.GetComponent<Renderer>().sharedMaterial =
                SolidMaterial(new Color(0.48f, 0.34f, 0.21f));
            UnityEngine.Object.Destroy(go.GetComponent<Collider>());
        }

        private static void RemoveVisualDividers(Transform floor)
        {
            Transform uni = FindChild(floor, "InternoUniversale");
            if (uni == null) return;
            string[] names = { "DivisorioSinistro", "DivisorioDestro", "ArchitraveVarco" };
            for (int i = 0; i < names.Length; i++)
            {
                Transform t = FindChild(uni, names[i]);
                if (t != null) UnityEngine.Object.Destroy(t.gameObject);
            }
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
