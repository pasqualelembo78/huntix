using System;
using System.Collections.Generic;
using UnityEngine;
using TMPro;
using City.World;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// Interni 3D universali e riutilizzabili, costruiti SOPRA il guscio gia'
    /// esistente di InteriorGenerator (nessun builder rimosso, nessun cambio
    /// geo/graph). Ogni edificio enterabile riceve:
    ///  - una pianta con due zone separate da un divisorio;
    ///  - arredi CC0 (Kenney Furniture / Mini Market / Vehicles / Pets) con
    ///    fallback procedurale se la risorsa non e' presente nell'AAR;
    ///  - contenitori con nomi riconosciuti da ContainerEgg (riflesso uova);
    ///  - due varchi collegati (pattern Gateways adattato ai chunk);
    ///  - un cartello in italiano con tipologia e indizio uovo.
    ///
    /// Tutto e' LAZY: il builder e' chiamato da InteriorGenerator.BuildInterior,
    /// che a sua volta gira solo entro 30 m dalla porta (InteriorManager).
    /// I mobili non hanno collider; solo i varchi hanno trigger dedicati.
    /// </summary>
    public static class UniversalInteriorBuilder
    {
        public const string Tag = UniversalInteriorRegistry.Tag;

        private static readonly Dictionary<string, Material> Materials =
            new Dictionary<string, Material>();

        public static void Build(Transform parent, string type, Shop shop,
            float w, float d, float floorH, int floors, string buildingName)
        {
            if (parent == null) return;
            Transform floor = FindChild(parent, "Floor_0");
            if (floor == null) return;
            if (FindChild(floor, "InternoUniversale") != null) return; // idempotente

            string kind = UniversalInteriorRegistry.Classify(type, shop, buildingName);
            string key = UniversalInteriorRegistry.StableKey(
                parent.position, kind, shop != null ? shop.shopName : buildingName);
            float width = Mathf.Max(4f, w);
            float depth = Mathf.Max(4f, d);

            var root = new GameObject("InternoUniversale");
            root.transform.SetParent(floor, false);
            root.transform.localPosition = Vector3.zero;

            var marker = root.AddComponent<UniversalInteriorMarker>();
            marker.key = key;
            marker.kind = kind;
            marker.label = UniversalInteriorRegistry.ItalianName(kind);
            marker.eggHint = UniversalInteriorRegistry.EggHint(kind);

            AddDividers(root.transform, width, depth);
            AddProps(root.transform, kind, width, depth);
            AddContainers(root.transform, kind, key, width, depth);
            AddGates(root.transform, key, width, depth);
            AddSign(root.transform, kind, width, depth);

            try
            {
                UnityBridge.LogToAndroid(Tag, "interno universale pronto: " +
                    UniversalInteriorRegistry.ItalianName(kind) + " (" + key + ") — " +
                    marker.eggHint);
            }
            catch (Exception) { }
        }

        /// <summary>Radice con marker, usata da InteriorManager per registrare la
        /// visita nello snapshot. Cerca anche nei figli per i prefab.</summary>
        public static UniversalInteriorMarker FindMarker(Transform root)
        {
            if (root == null) return null;
            var direct = root.GetComponent<UniversalInteriorMarker>();
            if (direct != null) return direct;
            var markers = root.GetComponentsInChildren<UniversalInteriorMarker>(true);
            return markers != null && markers.Length > 0 ? markers[0] : null;
        }

        // ── PIANTA ────────────────────────────────────────────────

        private static void AddDividers(Transform root, float w, float d)
        {
            // Divisorio SOLO VISIVO: nessun collider, la fisica resta quella del
            // guscio e i due varchi gestiscono il passaggio fra le zone.
            Box(root, "DivisorioSinistro", new Vector3(-w * 0.27f, 1.2f, 0f),
                new Vector3(w * 0.44f, 2.4f, 0.12f), Mat(new Color(0.86f, 0.84f, 0.79f)));
            Box(root, "DivisorioDestro", new Vector3(w * 0.27f, 1.2f, 0f),
                new Vector3(w * 0.44f, 2.4f, 0.12f), Mat(new Color(0.86f, 0.84f, 0.79f)));
            Box(root, "ArchitraveVarco", new Vector3(0f, 2.32f, 0f),
                new Vector3(w * 0.10f, 0.16f, 0.12f), Mat(new Color(0.52f, 0.38f, 0.24f)));
        }

        // ── ARREDI PER TIPOLOGIA ──────────────────────────────────

        private static void AddProps(Transform root, string kind, float w, float d)
        {
            switch (kind)
            {
                case "ristorante":
                    for (int i = 0; i < 3; i++)
                    {
                        float z = -d * 0.28f + i * d * 0.24f;
                        Prop(root, "Furniture/tableRound", "TavoloRistorante" + i,
                            new Vector3(-w * 0.22f, 0.42f, z), new Vector3(1.3f, 0.84f, 1.3f), 0f,
                            new Color(0.58f, 0.38f, 0.22f));
                        Prop(root, "Furniture/chair", "SediaRistorante" + i,
                            new Vector3(-w * 0.22f + 0.9f, 0.28f, z), new Vector3(0.5f, 0.56f, 0.5f), 180f,
                            new Color(0.42f, 0.28f, 0.18f));
                    }
                    Prop(root, "Furniture/kitchenStove", "CucinaRistorante",
                        new Vector3(w * 0.30f, 0.85f, -d * 0.30f), new Vector3(1.0f, 1.7f, 0.8f), 90f,
                        new Color(0.70f, 0.72f, 0.74f));
                    break;
                case "bar":
                    Prop(root, "Furniture/kitchenCoffeeMachine", "MacchinaCaffeUniversale",
                        new Vector3(w * 0.30f, 1.15f, -d * 0.30f), new Vector3(0.6f, 0.7f, 0.5f), 0f,
                        new Color(0.32f, 0.30f, 0.28f));
                    for (int i = 0; i < 2; i++)
                        Prop(root, "Furniture/stoolBar", "SgabelloUniversale" + i,
                            new Vector3(-w * 0.24f + i * 0.9f, 0.35f, d * 0.20f),
                            new Vector3(0.45f, 0.7f, 0.45f), 0f, new Color(0.34f, 0.24f, 0.18f));
                    break;
                case "farmacia":
                    for (int i = 0; i < 3; i++)
                        Prop(root, "KenneyMiniMarket/shelf-boxes", "ScaffaleFarmacia" + i,
                            new Vector3(-w * 0.32f + i * w * 0.20f, 0.9f, -d * 0.28f),
                            new Vector3(0.6f, 1.8f, 2.4f), 0f, new Color(0.92f, 0.94f, 0.96f));
                    Prop(root, "Furniture/bookcaseOpen", "ArmadiettoFarmacia",
                        new Vector3(w * 0.34f, 1.0f, -d * 0.28f), new Vector3(1.2f, 2.0f, 0.5f), 90f,
                        new Color(0.90f, 0.92f, 0.94f));
                    break;
                case "ufficio": case "banca":
                    for (int i = 0; i < 3; i++)
                    {
                        float x = -w * 0.28f + i * w * 0.26f;
                        Prop(root, "Furniture/desk", "ScrivaniaStudio" + i,
                            new Vector3(x, 0.42f, -d * 0.22f), new Vector3(1.5f, 0.84f, 0.8f), 0f,
                            new Color(0.50f, 0.36f, 0.22f));
                        Prop(root, "Furniture/chairDesk", "SediaStudio" + i,
                            new Vector3(x, 0.28f, -d * 0.22f + 0.85f), new Vector3(0.55f, 0.56f, 0.55f), 180f,
                            new Color(0.26f, 0.28f, 0.32f));
                        Prop(root, "Furniture/computerScreen", "MonitorStudio" + i,
                            new Vector3(x, 0.95f, -d * 0.24f), new Vector3(0.55f, 0.42f, 0.14f), 180f,
                            new Color(0.12f, 0.14f, 0.18f));
                    }
                    break;
                case "scuola":
                    for (int i = 0; i < 4; i++)
                        Prop(root, "Furniture/table", "BancoScuola" + i,
                            new Vector3(-w * 0.30f + (i % 2) * 1.6f, 0.40f, -d * 0.24f + (i / 2) * 1.5f),
                            new Vector3(1.4f, 0.8f, 0.7f), 0f, new Color(0.66f, 0.48f, 0.28f));
                    Box(root, "Lavagna", new Vector3(0f, 1.5f, -d * 0.42f),
                        new Vector3(Mathf.Min(3.6f, w * 0.6f), 1.6f, 0.08f), Mat(new Color(0.10f, 0.22f, 0.16f)));
                    break;
                case "ospedale":
                    for (int i = 0; i < 2; i++)
                    {
                        Prop(root, "Furniture/bedSingle", "LettoClinica" + i,
                            new Vector3(-w * 0.24f + i * 1.9f, 0.35f, -d * 0.20f),
                            new Vector3(1.0f, 0.7f, 2.0f), 0f, new Color(0.92f, 0.94f, 0.94f));
                        Prop(root, "Furniture/sideTable", "ComodinoClinica" + i,
                            new Vector3(-w * 0.24f + i * 1.9f + 0.8f, 0.32f, -d * 0.20f),
                            new Vector3(0.5f, 0.64f, 0.5f), 0f, new Color(0.80f, 0.82f, 0.84f));
                    }
                    break;
                case "hotel": case "appartamento": case "casa":
                    Prop(root, "Furniture/loungeSofa", "DivanoSoggiorno",
                        new Vector3(-w * 0.26f, 0.35f, d * 0.16f), new Vector3(2.0f, 0.9f, 0.9f), 0f,
                        new Color(0.30f, 0.42f, 0.52f));
                    Prop(root, "Furniture/tableCoffee", "TavolinoSoggiorno",
                        new Vector3(-w * 0.26f, 0.24f, d * 0.02f), new Vector3(1.0f, 0.48f, 0.6f), 0f,
                        new Color(0.56f, 0.38f, 0.22f));
                    Prop(root, "Furniture/televisionModern", "Televisore",
                        new Vector3(-w * 0.26f, 0.75f, -d * 0.34f), new Vector3(1.4f, 0.9f, 0.15f), 0f,
                        new Color(0.14f, 0.16f, 0.18f));
                    Prop(root, "Furniture/kitchenFridge", "FrigoCasa",
                        new Vector3(w * 0.34f, 1.0f, -d * 0.32f), new Vector3(0.8f, 2.0f, 0.8f), 90f,
                        new Color(0.88f, 0.90f, 0.92f));
                    break;
                case "concessionaria":
                    Prop(root, "Vehicles/sedan", "AutoShowroomUniversale",
                        new Vector3(0f, 0.18f, d * 0.10f), new Vector3(1.8f, 1.3f, 4.0f), 0f,
                        new Color(0.24f, 0.36f, 0.54f));
                    break;
                case "supermercato": case "negozio": case "abbigliamento":
                    for (int i = 0; i < 3; i++)
                        Prop(root, "KenneyMiniMarket/shelf-bags", "ScaffaleUniversale" + i,
                            new Vector3(-w * 0.30f + i * w * 0.22f, 0.9f, -d * 0.26f),
                            new Vector3(0.6f, 1.8f, 2.6f), 0f, new Color(0.66f, 0.46f, 0.28f));
                    break;
                case "lavaggiocani":
                    Prop(root, "Furniture/bathtub", "VascaUniversale",
                        new Vector3(-w * 0.22f, 0.45f, d * 0.08f), new Vector3(1.5f, 0.9f, 1.0f), 0f,
                        new Color(0.86f, 0.88f, 0.90f));
                    Prop(root, "Pets/animal-dog", "CaneUniversale",
                        new Vector3(-w * 0.22f, 0.55f, d * 0.08f), new Vector3(0.5f, 0.6f, 0.8f), 20f,
                        new Color(0.72f, 0.52f, 0.30f));
                    break;
                case "autolavaggio": case "officina":
                    Box(root, "PedanaLavaggio", new Vector3(0f, 0.06f, d * 0.08f),
                        new Vector3(Mathf.Min(4.6f, w * 0.7f), 0.12f, Mathf.Min(5f, d * 0.7f)),
                        Mat(new Color(0.44f, 0.48f, 0.52f)));
                    Prop(root, "Vehicles/sedan", "AutoServizio",
                        new Vector3(0f, 0.18f, d * 0.08f), new Vector3(1.8f, 1.3f, 4.0f), 0f,
                        new Color(0.30f, 0.44f, 0.60f));
                    break;
                case "parruccheria":
                    Prop(root, "Furniture/chairModernCushion", "PoltronaUniversale",
                        new Vector3(-w * 0.24f, 0.35f, d * 0.12f), new Vector3(0.9f, 0.7f, 0.9f), 180f,
                        new Color(0.38f, 0.24f, 0.42f));
                    Prop(root, "Furniture/bathroomMirror", "SpecchioUniversale",
                        new Vector3(-w * 0.24f, 1.3f, -d * 0.30f), new Vector3(1.0f, 1.9f, 0.12f), 0f,
                        new Color(0.76f, 0.86f, 0.90f));
                    break;
                case "biglietteria":
                    Prop(root, "KenneyMiniMarket/cash-register", "CassaUniversale",
                        new Vector3(0f, 1.05f, -d * 0.30f), new Vector3(0.7f, 0.5f, 0.6f), 0f,
                        new Color(0.28f, 0.34f, 0.42f));
                    Prop(root, "Furniture/computerScreen", "TotemUniversale",
                        new Vector3(-w * 0.34f, 0.85f, -d * 0.30f), new Vector3(0.7f, 1.3f, 0.15f), 0f,
                        new Color(0.10f, 0.18f, 0.28f));
                    break;
                default:
                    Prop(root, "Furniture/lampRoundFloor", "LampadaUniversale",
                        new Vector3(w * 0.34f, 0.8f, d * 0.24f), new Vector3(0.4f, 1.6f, 0.4f), 0f,
                        new Color(0.90f, 0.84f, 0.66f));
                    break;
            }
        }

        // ── CONTENITORI UOVA ─────────────────────────────────────

        private static void AddContainers(Transform root, string kind, string key,
            float w, float d)
        {
            // Nomi UNIVOCI per stanza: ContainerEgg calcola la chiave anche dal
            // nome del contenitore, quindi "CassettoFarmacia" e "CassettoUfficio"
            // non collidono nemmeno nello stesso palazzo.
            if (kind == "farmacia" || kind == "ufficio" || kind == "banca" ||
                kind == "biglietteria" || kind == "ristorante" || kind == "bar")
            {
                Container(root, "Furniture/cabinetBedDrawer", "Cassetto" + Capitalize(kind),
                    new Vector3(w * 0.34f, 0.42f, d * 0.30f), new Vector3(1.0f, 0.84f, 0.6f),
                    "banco " + UniversalInteriorRegistry.ItalianName(kind).ToLowerInvariant(),
                    key);
            }
            else if (kind == "ospedale" || kind == "hotel" || kind == "appartamento" ||
                     kind == "casa")
            {
                Container(root, "Furniture/bookcaseClosed", "Armadio" + Capitalize(kind),
                    new Vector3(w * 0.34f, 1.0f, d * 0.28f), new Vector3(1.2f, 2.0f, 0.5f),
                    "piano 1", key);
            }
            else
            {
                Container(root, "Furniture/cardboardBoxClosed", "Dispensa" + Capitalize(kind),
                    new Vector3(w * 0.36f, 0.32f, d * 0.30f), new Vector3(0.8f, 0.64f, 0.6f),
                    "magazzino", key);
            }

            // Secondo contenitore sempre presente, cosi' ogni interno universale
            // ha almeno due punti plausibili per il riflesso uova.
            Container(root, "Furniture/desk", "Scrivania" + Capitalize(kind),
                new Vector3(-w * 0.34f, 0.42f, d * 0.32f), new Vector3(1.3f, 0.84f, 0.7f),
                "angolo scrivania", key);
        }

        private static void Container(Transform root, string resource, string name,
            Vector3 pos, Vector3 size, string room, string key)
        {
            Prop(root, resource, name, pos, size, 0f, new Color(0.56f, 0.40f, 0.26f));
            Transform t = FindChild(root, name);
            if (t == null) return;
            var marker = t.gameObject.AddComponent<UniversalContainerMarker>();
            marker.room = room;
            marker.interiorKey = key;
        }

        // ── VARCHI (pattern Gateways adattato) ───────────────────

        private static void AddGates(Transform root, string key, float w, float d)
        {
            string idA = UniversalInteriorRegistry.GateId(key, "ingresso");
            string idB = UniversalInteriorRegistry.GateId(key, "retro");

            Gate(root, "VarcoIngressoUniversale", idA, idB, "VARCO INGRESSO",
                new Vector3(0f, 0f, d * 0.34f));
            Gate(root, "VarcoRetroUniversale", idB, idA, "VARCO RETRO",
                new Vector3(0f, 0f, -d * 0.34f));
        }

        private static void Gate(Transform root, string name, string id, string target,
            string label, Vector3 pos)
        {
            var go = new GameObject(name);
            go.transform.SetParent(root, false);
            go.transform.localPosition = pos;
            var col = go.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(1.8f, 2.2f, 0.35f);
            col.center = new Vector3(0f, 1.1f, 0f);
            var gate = go.AddComponent<InteriorGate>();
            gate.Configure(id, target, label);
            // La destinazione e' il centro del varco opposto, un metro davanti:
            // il controller del player non resta dentro il trigger di arrivo.
            gate.spawnOffset = new Vector3(0f, 1f, 0f);

            var frame = new GameObject("Telaio" + name);
            frame.transform.SetParent(root, false);
            frame.transform.localPosition = pos + new Vector3(0f, 1.2f, 0f);
            Box(frame.transform, "MontanteSinistro", new Vector3(-0.95f, 0f, 0f),
                new Vector3(0.12f, 2.4f, 0.12f), Mat(new Color(0.48f, 0.34f, 0.22f)));
            Box(frame.transform, "MontanteDestro", new Vector3(0.95f, 0f, 0f),
                new Vector3(0.12f, 2.4f, 0.12f), Mat(new Color(0.48f, 0.34f, 0.22f)));
        }

        private static void AddSign(Transform root, string kind, float w, float d)
        {
            var go = new GameObject("CartelloUniversale");
            go.transform.SetParent(root, false);
            go.transform.localPosition = new Vector3(0f, 2.5f, d * 0.36f);
            var tmp = go.AddComponent<TextMeshPro>();
            tmp.font = TMP_Settings.defaultFontAsset;
            tmp.text = UniversalInteriorRegistry.ItalianName(kind) + " — " +
                       UniversalInteriorRegistry.EggHint(kind);
            tmp.fontSize = 0.9f;
            tmp.alignment = TextAlignmentOptions.Center;
            tmp.color = Color.white;
            tmp.enableWordWrapping = false;
            tmp.overflowMode = TextOverflowModes.Overflow;
        }

        // ── HELPER GEOMETRICI (mobili senza collider) ────────────

        private static void Prop(Transform parent, string resourcePath, string name,
            Vector3 pos, Vector3 size, float yaw, Color fallback)
        {
            GameObject prefab = Resources.Load<GameObject>(resourcePath);
            if (prefab == null)
            {
                Box(parent, name, pos, size, Mat(fallback));
                return;
            }
            GameObject inst = (GameObject)UnityEngine.Object.Instantiate(prefab, parent, false);
            inst.name = name;
            inst.transform.SetParent(parent, false);
            inst.transform.localPosition = pos;
            inst.transform.localRotation = Quaternion.Euler(0f, yaw, 0f);
            inst.transform.localScale = Vector3.one;

            Renderer[] renderers = inst.GetComponentsInChildren<Renderer>();
            if (renderers != null && renderers.Length > 0)
            {
                Bounds b = renderers[0].bounds;
                for (int i = 1; i < renderers.Length; i++) b.Encapsulate(renderers[i].bounds);
                Vector3 s = b.size;
                if (s.x > 0.001f && s.y > 0.001f && s.z > 0.001f)
                    inst.transform.localScale = new Vector3(size.x / s.x, size.y / s.y, size.z / s.z);
            }
            else inst.transform.localScale = size;

            foreach (var col in inst.GetComponentsInChildren<Collider>())
                UnityEngine.Object.Destroy(col);
        }

        private static void Box(Transform parent, string name, Vector3 pos,
            Vector3 size, Material mat)
        {
            GameObject go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            go.transform.localScale = size;
            if (mat != null) go.GetComponent<Renderer>().sharedMaterial = mat;
            var col = go.GetComponent<Collider>();
            if (col != null) UnityEngine.Object.Destroy(col);
        }

        private static Material Mat(Color c)
        {
            string key = c.r.ToString("F2") + "_" + c.g.ToString("F2") + "_" + c.b.ToString("F2");
            Material m;
            if (Materials.TryGetValue(key, out m) && m != null) return m;
            Shader shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) return null;
            m = new Material(shader);
            if (shader.name.Contains("Universal")) m.SetColor("_BaseColor", c);
            else m.SetColor("_Color", c);
            m.SetFloat("_Smoothness", 0.25f);
            Materials[key] = m;
            return m;
        }

        private static string Capitalize(string s)
        {
            if (string.IsNullOrEmpty(s)) return "Generico";
            char[] chars = s.ToCharArray();
            if (chars.Length > 0) chars[0] = char.ToUpperInvariant(chars[0]);
            return new string(chars);
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

    /// <summary>Identita' di un interno universale: chiave geo-stabile, kind e
    /// indizio uovo. Consumata da InteriorManager (visite) e ContainerEgg.</summary>
    public sealed class UniversalInteriorMarker : MonoBehaviour
    {
        public string key = "";
        public string kind = "";
        public string label = "";
        public string eggHint = "";
    }

    /// <summary>Contenitore appartenente a una stanza dell'interno universale:
    /// ContainerEgg legge "room" per l'indizio testuale "città→edificio→stanza→oggetto".</summary>
    public sealed class UniversalContainerMarker : MonoBehaviour
    {
        public string room = "";
        public string interiorKey = "";
    }
}
