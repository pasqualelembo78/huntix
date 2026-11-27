// Mandato §3 · ADDITIVO: la vestizione storica (GrandHouseExterior.Dress)
// resta intatta; questo livello le sovrappone solo geometria e materiali.
// Mandato §4 · Riflesso della caccia alle uova: il giardino e la terrazza
// sono luoghi candidati per nascondigli, ma qui NON nasce alcun nuovo uovo
// ne' una seconda economia (nessun HouseEggSpot: i 9 nascondigli storici
// restano invariati finche' l'utente non approva l'aggiunta).
using UnityEngine;

namespace City.Interior.Visit
{
    /// <summary>
    /// Finitura moderna della villa da 300 mq vista da fuori. Copre i pannelli
    /// emissivi piatti creati da GrandHouseExterior con veri vani finestra
    /// (cassa, vetro, telaio, mullioni, davanzale), aggiunge lamelle di
    /// facciata, tetto a due falde con terrazza, ringhiere dei balconi,
    /// portico/step e un giardino d'ingresso con piante SENDU gia' licenziate.
    ///
    /// Solo visuale: nessun collider, nessuna Light, nessuno spostamento della
    /// porta o del trigger di BuildingEntrance (coordinate Android intatte).
    /// </summary>
    public static class GrandHouseSkin
    {
        public const string RootName = "Villa300mq_SkinModerna";

        private const string PaletteFolder = "HouseInteriors/Sendu/Palettes/";
        private const string PatternFolder = "HouseInteriors/Sendu/Patterns/";
        private const string PlantFolder = "HouseInteriors/Sendu/FBX/Decor/";

        private const float Out = 0.17f;   // distanza della pelle dal muro
        private const int BuildingLayer = 8;

        /// <summary>Chiamato alla fine di GrandHouseExterior.Dress, dopo che
        /// il root e' gia' orientato (yaw OSM) e posizionato a quota terreno.
        /// Idempotente: una seconda chiamata non duplica nulla.</summary>
        public static void Apply(Transform houseRoot, float w, float d)
        {
            if (houseRoot == null) return;
            if (FindChild(houseRoot, RootName) != null) return;

            float width = Mathf.Max(w, 8f);
            float depth = Mathf.Max(d, 8f);
            float hw = width * 0.5f;
            float hd = depth * 0.5f;

            Transform skin = NewGroup(houseRoot, RootName, Vector3.zero, 0f);

            Material plaster = Surface("mod_intonaco", new Color(0.91f, 0.89f, 0.84f),
                0.16f, 0f, PatternFolder + "CI_Pattern_03", 3f);
            Material fin = Surface("mod_lamella", new Color(0.55f, 0.58f, 0.55f),
                0.35f, 0.05f, PatternFolder + "CI_Pattern_03", 2f);
            Material stone = Surface("mod_pietra", new Color(0.62f, 0.60f, 0.55f),
                0.22f, 0f, PatternFolder + "CI_Pattern_08", 2f);
            Material dark = Surface("mod_ombra", new Color(0.10f, 0.11f, 0.12f), 0.1f, 0f);
            Material frame = Surface("mod_infissi", new Color(0.18f, 0.20f, 0.21f), 0.55f, 0.35f);
            Material glass = Surface("mod_vetro", new Color(0.14f, 0.24f, 0.29f), 0.92f, 0.18f,
                null, 1f, new Color(0.16f, 0.13f, 0.09f));
            Material roof = Surface("mod_copertura", new Color(0.22f, 0.24f, 0.26f), 0.42f, 0.1f,
                PatternFolder + "CI_Pattern_03", 4f);
            Material copper = Surface("mod_rame", new Color(0.42f, 0.27f, 0.17f), 0.5f, 0.45f);
            Material soil = Surface("mod_terriccio", new Color(0.25f, 0.18f, 0.12f), 0.05f, 0f);
            Material lawn = Surface("mod_prato", new Color(0.25f, 0.40f, 0.20f), 0.05f, 0f,
                PatternFolder + "CI_Pattern_05", 5f);

            Facade(skin, width, depth, plaster, fin, dark);
            Windows(skin, width, depth, dark, frame, glass, stone, copper);
            RoofAndTerrace(skin, width, depth, roof, stone, frame, copper);
            Balconies(skin, width, depth, frame, stone);
            EntranceAndGarden(skin, width, depth, stone, dark, frame, lawn, soil);
        }

        // ── Facciata: lamelle fra una finestra e l'altra, senza coprire i vani ──
        private static void Facade(Transform skin, float w, float d,
            Material plaster, Material fin, Material dark)
        {
            float hw = w * 0.5f;
            float hd = d * 0.5f;
            for (int side = 0; side < 4; side++)
            {
                float span = side < 2 ? w : d;
                Vector3 pos = FaceOrigin(side, hw, hd);
                float yaw = FaceYaw(side);
                Transform face = NewGroup(skin, "Facciata_" + FaceName(side), pos, yaw);

                // Intonaco leggermente in rilievo: copre le vecchie fasce piatte
                // senza nascondere le finestre (che stanno piu' avanti).
                Box(face, "Rivestimento", new Vector3(0f, GrandHouseExterior.TotalHeight * 0.5f,
                    Out * 0.45f), new Vector3(span - 0.3f,
                    GrandHouseExterior.TotalHeight - 0.5f, 0.08f), plaster);

                float guard = Mathf.Min(1.9f, span * 0.16f);
                int gaps = side < 2 ? 5 : 3;
                for (int i = 0; i < gaps; i++)
                {
                    // Punti intermedi fra le finestre esistenti (il vecchio
                    // Dress mette i vani a i*w/6 oppure i*d/4).
                    float t = side < 2
                        ? (i - 2) * (w / 6f) + w / 12f
                        : (i - 1) * (d / 4f) + d / 8f;
                    if (Mathf.Abs(t) > hw - guard && side >= 2) continue;
                    Box(face, "Lamella_" + i, new Vector3(t,
                        GrandHouseExterior.TotalHeight * 0.5f, Out + 0.04f),
                        new Vector3(0.16f, GrandHouseExterior.TotalHeight - 0.6f, 0.14f), fin);
                }

                // Pilastri d'angolo: danno profondita' e chiudono le lamelle.
                for (int s = -1; s <= 1; s += 2)
                {
                    Box(face, "Pilastro_" + s, new Vector3(s * (span * 0.5f - 0.16f),
                        GrandHouseExterior.TotalHeight * 0.5f, Out + 0.02f),
                        new Vector3(0.34f, GrandHouseExterior.TotalHeight - 0.3f, 0.22f), plaster);
                }

                // Ombre dei solai: solco scuro continuo sotto ogni fascia.
                for (int floor = 1; floor < GrandHouseExterior.Floors; floor++)
                {
                    Box(face, "SolcoPiano" + floor,
                        new Vector3(0f, floor * GrandHouseExterior.FloorHeight - 0.26f,
                            Out + 0.05f),
                        new Vector3(span - 0.45f, 0.09f, 0.10f), dark);
                }
            }
        }

        // ── Finestre: coprono i vecchi pannelli emissivi con un vano reale ──
        private static void Windows(Transform skin, float w, float d,
            Material reveal, Material frame, Material glass, Material stone, Material copper)
        {
            for (int floor = 1; floor < GrandHouseExterior.Floors; floor++)
            {
                float wy = floor * GrandHouseExterior.FloorHeight + 1.65f;
                float winW = Mathf.Min(1.6f, w / 8f);
                float winH = 1.9f;
                for (int side = 0; side < 4; side++)
                {
                    bool longSide = side < 2;
                    int count = longSide ? 5 : 3;
                    float step = longSide ? w / 6f : d / 4f;
                    Vector3 pos = FaceOrigin(side, w * 0.5f, d * 0.5f);
                    Transform face = NewGroup(skin,
                        "VaniPiano" + floor + "_" + FaceName(side), pos, FaceYaw(side));
                    for (int i = 0; i < count; i++)
                    {
                        float t = (i - (count - 1) * 0.5f) * step;
                        Window(face, "Finestra" + i, t, wy, winW, winH,
                            reveal, frame, glass, stone, copper);
                    }
                }
            }
        }

        private static void Window(Transform face, string id, float x, float y,
            float w, float h, Material reveal, Material frame, Material glass,
            Material stone, Material copper)
        {
            Transform vano = NewGroup(face, id, new Vector3(x, y, Out + 0.12f), 0f);
            // Cassa in ombra: e' piu' grande del vecchio pannello e lo copre.
            Box(vano, "Cassa", Vector3.zero, new Vector3(w + 0.24f, h + 0.24f, 0.16f), reveal);
            Box(vano, "Vetro", new Vector3(0f, 0f, 0.10f),
                new Vector3(w - 0.20f, h - 0.20f, 0.03f), glass);
            // Telaio perimetrale + mullione + traversa: la finestra non e' piu'
            // un rettangolo luminoso ma un infisso leggibile da vicino.
            float xEdge = w * 0.5f - 0.05f;
            float yEdge = h * 0.5f - 0.05f;
            Box(vano, "TelaioSX", new Vector3(-xEdge, 0f, 0.13f),
                new Vector3(0.10f, h, 0.09f), frame);
            Box(vano, "TelaioDX", new Vector3(xEdge, 0f, 0.13f),
                new Vector3(0.10f, h, 0.09f), frame);
            Box(vano, "TelaioAlto", new Vector3(0f, yEdge, 0.13f),
                new Vector3(w, 0.10f, 0.09f), frame);
            Box(vano, "TelaioBasso", new Vector3(0f, -yEdge, 0.13f),
                new Vector3(w, 0.10f, 0.09f), frame);
            Box(vano, "Mullione", new Vector3(0f, 0f, 0.14f),
                new Vector3(0.07f, h - 0.22f, 0.07f), frame);
            Box(vano, "Traversa", new Vector3(0f, h * 0.12f, 0.14f),
                new Vector3(w - 0.22f, 0.07f, 0.07f), frame);
            Box(vano, "Davanzale", new Vector3(0f, -h * 0.5f - 0.10f, 0.20f),
                new Vector3(w + 0.42f, 0.12f, 0.44f), stone);
            Box(vano, "Architrave", new Vector3(0f, h * 0.5f + 0.09f, 0.12f),
                new Vector3(w + 0.30f, 0.11f, 0.26f), stone);
            Box(vano, "Maniglia", new Vector3(w * 0.28f, -0.12f, 0.19f),
                new Vector3(0.05f, 0.22f, 0.05f), copper);
        }

        // ── Tetto a due falde + terrazza sul lato ingresso ──────────────────
        private static void RoofAndTerrace(Transform skin, float w, float d,
            Material roof, Material stone, Material frame, Material copper)
        {
            float hw = w * 0.5f;
            float hd = d * 0.5f;
            float y = GrandHouseExterior.TotalHeight;

            Box(skin, "SolaioCopertura", new Vector3(0f, y + 0.10f, 0f),
                new Vector3(w + 1.2f, 0.32f, d + 1.2f), stone);

            // Falde solo sulla meta' posteriore: la meta' anteriore diventa
            // terrazza, cosi' la villa ha una sagoma articolata (non un scatolone).
            float slopeDepth = hd * 1.15f + 0.6f;
            float slopeZ = -hd * 0.425f - 0.3f;
            for (int s = -1; s <= 1; s += 2)
            {
                Transform falda = NewGroup(skin, "Falda_" + s,
                    new Vector3(s * (w * 0.25f + 0.15f), y + 0.52f, slopeZ), 0f);
                falda.localRotation = Quaternion.Euler(0f, 0f, s * 9f);
                Box(falda, "Pannello", Vector3.zero,
                    new Vector3(w * 0.54f, 0.18f, slopeDepth), roof);
            }
            Box(skin, "Colmo", new Vector3(0f, y + 0.86f, slopeZ),
                new Vector3(0.46f, 0.18f, slopeDepth), copper);

            float terraceZ = hd * 0.575f + 0.3f;
            float terraceDepth = hd * 0.85f + 0.6f;
            Box(skin, "PavimentoTerrazza", new Vector3(0f, y + 0.30f, terraceZ),
                new Vector3(w + 0.7f, 0.06f, terraceDepth), stone);
            // Parapetto in tre lati: appoggia sul solaio, nessun elemento sospeso.
            for (int s = -1; s <= 1; s += 2)
            {
                Box(skin, "ParapettoTerrazza_" + s,
                    new Vector3(s * (w * 0.5f + 0.45f), y + 0.85f, terraceZ),
                    new Vector3(0.20f, 1.05f, terraceDepth), stone);
            }
            Box(skin, "ParapettoTerrazza_Frontale",
                new Vector3(0f, y + 0.85f, hd + 0.55f),
                new Vector3(w + 1.0f, 1.05f, 0.20f), stone);
            Box(skin, "CorrimanoTerrazza",
                new Vector3(0f, y + 1.40f, hd + 0.55f),
                new Vector3(w + 1.1f, 0.09f, 0.28f), copper);
            for (int i = -3; i <= 3; i++)
            {
                Box(skin, "MontanteTerrazza_" + i,
                    new Vector3(i * (w / 7f), y + 0.90f, hd + 0.55f),
                    new Vector3(0.05f, 0.95f, 0.05f), frame);
            }
        }

        // ── Balconi: ringhiera piena in basso e montanti, non solo un rail ──
        private static void Balconies(Transform skin, float w, float d,
            Material frame, Material stone)
        {
            float hd = d * 0.5f;
            // Stessa larghezza della lastra di pietra creata da
            // GrandHouseExterior: i montanti non sporgono dal balcone.
            float bw = Mathf.Min(w * 0.45f, 6.5f);
            for (int floor = 1; floor < GrandHouseExterior.Floors; floor++)
            {
                float y = floor * GrandHouseExterior.FloorHeight;
                Box(skin, "RinghieraBassa_" + floor,
                    new Vector3(0f, y + 0.34f, hd + 1.65f),
                    new Vector3(bw, 0.07f, 0.09f), frame);
                int posts = Mathf.Max(4, Mathf.RoundToInt(bw / 0.8f));
                for (int i = 0; i <= posts; i++)
                {
                    float t = -bw * 0.5f + bw * i / posts;
                    Box(skin, "MontanteBalcone_" + floor + "_" + i,
                        new Vector3(t, y + 0.55f, hd + 1.65f),
                        new Vector3(0.06f, 0.66f, 0.06f), frame);
                }
                Box(skin, "FiorieraBalcone_" + floor,
                    new Vector3(bw * 0.34f, y + 0.35f, hd + 1.10f),
                    new Vector3(1.1f, 0.26f, 0.55f), stone);
            }
        }

        // ── Ingresso, pavimentazione e giardino ─────────────────────────────
        private static void EntranceAndGarden(Transform skin, float w, float d,
            Material stone, Material dark, Material frame, Material lawn, Material soil)
        {
            float hw = w * 0.5f;
            float hd = d * 0.5f;

            // Cornice della porta: la porta storica e il suo trigger restano
            // dov'erano; qui si aggiunge solo il contorno in pietra.
            for (int s = -1; s <= 1; s += 2)
            {
                Box(skin, "StipitePorta_" + s, new Vector3(s * 0.86f, 1.25f, hd + 0.22f),
                    new Vector3(0.18f, 2.5f, 0.24f), stone);
            }
            Box(skin, "ArchitravePorta", new Vector3(0f, 2.56f, hd + 0.22f),
                new Vector3(1.92f, 0.18f, 0.24f), stone);
            Box(skin, "SogliaPorta", new Vector3(0f, 0.05f, hd + 0.30f),
                new Vector3(1.95f, 0.10f, 0.42f), dark);

            // Tre gradini che salgono verso la porta (il piu' alto e' il piu'
            // vicino): niente scalini sospesi, ognuno poggia sul precedente.
            for (int i = 0; i < 3; i++)
            {
                Box(skin, "Gradino_" + i,
                    new Vector3(0f, 0.09f * (3 - i), hd + 1.15f + i * 0.60f),
                    new Vector3(3.4f + i * 0.4f, 0.18f * (3 - i), 0.62f), stone);
            }

            // Vialetto in lastre con giunti, davanti all'ingresso.
            for (int i = 0; i < 9; i++)
            {
                Box(skin, "LastraVialetto_" + i,
                    new Vector3(0f, 0.035f, hd + 3.05f + i * 0.62f),
                    new Vector3(2.5f, 0.07f, 0.52f), stone);
                if (i % 2 == 0)
                {
                    for (int s = -1; s <= 1; s += 2)
                    {
                        Box(skin, "BordinoVialetto_" + i + "_" + s,
                            new Vector3(s * 1.38f, 0.06f, hd + 3.05f + i * 0.62f),
                            new Vector3(0.12f, 0.12f, 0.54f), frame);
                    }
                }
            }

            // Aiuole rialzate: non z-fightano con il TerrainChunk e danno un
            // giardino credibile anche dove il terreno e' leggermente in pendenza.
            for (int s = -1; s <= 1; s += 2)
            {
                float px = s * Mathf.Min(hw * 0.74f, hw - 1.2f);
                Box(skin, "Prato_" + s, new Vector3(px, 0.02f, hd + 3.4f),
                    new Vector3(Mathf.Max(2.4f, w * 0.23f), 0.04f, 4.2f), lawn);
                Box(skin, "Aiuola_" + s, new Vector3(px, 0.28f, hd + 1.95f),
                    new Vector3(1.5f, 0.56f, 1.5f), stone);
                Box(skin, "Terriccio_" + s, new Vector3(px, 0.57f, hd + 1.95f),
                    new Vector3(1.30f, 0.06f, 1.30f), soil);
                Plant(skin, "CI_Decor_Plant_OliveTree", "OlivoGiardino_" + s,
                    new Vector3(px, 0.60f, hd + 1.95f), new Vector3(1.7f, 2.4f, 1.7f));
                Plant(skin, "CI_Decor_Plant_Monstera", "MonsteraIngresso_" + s,
                    new Vector3(s * 2.05f, 0.62f, hd + 0.95f),
                    new Vector3(0.85f, 1.05f, 0.85f));
            }
        }

        private static void Plant(Transform skin, string model, string name,
            Vector3 pos, Vector3 size)
        {
            GameObject prefab = Resources.Load<GameObject>(PlantFolder + model);
            if (prefab == null) return;
            GameObject inst = (GameObject)UnityEngine.Object.Instantiate(prefab, skin, false);
            inst.name = name;
            inst.transform.localPosition = pos;
            inst.transform.localRotation = Quaternion.identity;
            inst.transform.localScale = Vector3.one;
            DisableLod1(inst.transform);
            Collider[] cols = inst.GetComponentsInChildren<Collider>(true);
            for (int i = 0; i < cols.Length; i++) UnityEngine.Object.Destroy(cols[i]);
            Renderer[] rends = inst.GetComponentsInChildren<Renderer>(true);
            for (int i = 0; i < rends.Length; i++)
                if (rends[i] != null) rends[i].gameObject.layer = BuildingLayer;
            Fit(inst.transform, size);
        }

        /// <summary>Riscala il modello SENDU senza deformare i mobili e lo
        /// appoggia alla base (stessa logica di SenduInteriorDecorator).</summary>
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
            if (first) return;
            Vector3 span = new Vector3(Mathf.Max(b.size.x, 0.03f),
                Mathf.Max(b.size.y, 0.03f), Mathf.Max(b.size.z, 0.03f));
            Vector3 scale = new Vector3(
                Mathf.Clamp(target.x / span.x, 0.02f, 40f),
                Mathf.Clamp(target.y / span.y, 0.02f, 40f),
                Mathf.Clamp(target.z / span.z, 0.02f, 40f));
            inst.localScale = scale;
            inst.localPosition += new Vector3(-b.center.x * scale.x,
                -b.min.y * scale.y, -b.center.z * scale.z);
        }

        private static void DisableLod1(Transform root)
        {
            Transform[] all = root.GetComponentsInChildren<Transform>(true);
            for (int i = 0; i < all.Length; i++)
                if (all[i] != null &&
                    all[i].name.EndsWith("_LOD1", System.StringComparison.Ordinal))
                    all[i].gameObject.SetActive(false);
        }

        private static string FaceName(int side)
        {
            return side == 0 ? "S" : side == 1 ? "N" : side == 2 ? "E" : "O";
        }

        private static float FaceYaw(int side)
        {
            return side == 0 ? 0f : side == 1 ? 180f : side == 2 ? 90f : -90f;
        }

        private static Vector3 FaceOrigin(int side, float hw, float hd)
        {
            return side == 0 ? new Vector3(0f, 0f, hd) :
                   side == 1 ? new Vector3(0f, 0f, -hd) :
                   side == 2 ? new Vector3(hw, 0f, 0f) :
                              new Vector3(-hw, 0f, 0f);
        }

        private static Transform NewGroup(Transform parent, string name,
            Vector3 localPos, float yaw)
        {
            GameObject go = new GameObject(name);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            go.transform.localRotation = Quaternion.Euler(0f, yaw, 0f);
            go.transform.localScale = Vector3.one;
            go.layer = BuildingLayer;
            return go.transform;
        }

        /// <summary>Primitiva solo visiva: niente collider (la cammina-su-
        /// edifici resta quella del guscio OSM).</summary>
        private static Transform Box(Transform parent, string name, Vector3 pos,
            Vector3 size, Material mat)
        {
            GameObject go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            go.transform.localScale = size;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            go.layer = BuildingLayer;
            UnityEngine.Object.Destroy(go.GetComponent<Collider>());
            return go.transform;
        }

        private static Material Surface(string id, Color color, float smoothness,
            float metallic, string texturePath = null, float tile = 1f,
            Color emission = default(Color))
        {
            Shader shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) shader = Shader.Find("Hidden/InternalErrorShader");
            Material m = new Material(shader);
            m.name = "Villa300mq_" + id;
            string prop = shader != null && shader.name.StartsWith("Universal Render Pipeline",
                System.StringComparison.Ordinal) ? "_BaseColor" : "_Color";
            if (m.HasProperty(prop)) m.SetColor(prop, color);
            else if (m.HasProperty("_Color")) m.SetColor("_Color", color);
            if (m.HasProperty("_Smoothness")) m.SetFloat("_Smoothness", smoothness);
            if (m.HasProperty("_Metallic")) m.SetFloat("_Metallic", metallic);
            if (!string.IsNullOrEmpty(texturePath))
            {
                Texture tex = Resources.Load<Texture>(texturePath);
                if (tex != null)
                {
                    // Le texture SENDU sono importate con wrap Repeat: qui si
                    // cambia solo il tiling del materiale, non l'asset condiviso.
                    if (m.HasProperty("_BaseMap")) m.SetTexture("_BaseMap", tex);
                    if (m.HasProperty("_MainTex")) m.SetTexture("_MainTex", tex);
                    if (m.HasProperty("_BaseMap")) m.SetTextureScale("_BaseMap",
                        new Vector2(tile, tile));
                    if (m.HasProperty("_MainTex")) m.SetTextureScale("_MainTex",
                        new Vector2(tile, tile));
                }
            }
            if (emission.r + emission.g + emission.b > 0f)
            {
                m.EnableKeyword("_EMISSION");
                if (m.HasProperty("_EmissionColor")) m.SetColor("_EmissionColor", emission);
            }
            return m;
        }

        private static Transform FindChild(Transform root, string name)
        {
            Transform[] all = root.GetComponentsInChildren<Transform>(true);
            for (int i = 0; i < all.Length; i++)
                if (all[i] != null && all[i].name == name) return all[i];
            return null;
        }
    }
}
