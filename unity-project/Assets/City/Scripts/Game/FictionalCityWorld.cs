// Generatore originale Huntix (NESSUN codice dei 13 clone auditati).
// La citta' fittizia e' un quartiere dello stesso universo: nasce da una
// ricetta di statistiche OSM estratte OFFLINE (nessuna geometria reale, nessun
// DEM, nessun tile a runtime). Vedi HUNTIX_MANDATO.md §8 "Citta' fittizia".
using System;
using UnityEngine;
using City.Diagnostics;
using City.Environment;
using City.OSM;

namespace City.Fictional
{
    /// <summary>
    /// Modalita' Gioco: quartiere piatto generato da
    /// Resources/CityRecipes/foggia_city_recipe.json con seed deterministico.
    /// NON sostituisce la citta' AR/OSM: e' affiancata, disattivabile e usa il
    /// solo flag di streaming gia' presente (ChunkManager.StreamingEnabled).
    /// </summary>
    public static class FictionalCityWorld
    {
        public const string PrefKey = "huntix_world_mode";
        public const string RecipePath = "CityRecipes/foggia_city_recipe";
        private const string RootName = "CittaGioco";
        private const int BlocksPerSide = 5;

        [Serializable]
        private class GridDoc
        {
            public float road_spacing_m;
            public float road_width_m;
            public float block_m;
            public float park_ratio;
            public float buildings_per_km2;
        }

        [Serializable]
        private class MixDoc
        {
            public float bar;
            public float school;
            public float hospital;
            public float repair;
            public float dealer;
            public float garage;
            public float rampa;
        }

        [Serializable]
        private class SourceDoc
        {
            public double[] bbox;
        }

        [Serializable]
        private class RecipeDoc
        {
            public int version;
            public long seed;
            public GridDoc grid;
            public MixDoc poi_mix;
            public SourceDoc source;
        }

        private static GameObject _root;
        private static RecipeDoc _recipe;
        private static bool _initialized;

        /// <summary>Modalita' scelta (persistita). False = Reale/OSM, default.</summary>
        public static bool Enabled { get; private set; }

        /// <summary>True solo se il quartiere fittizio e' effettivamente in scena.</summary>
        public static bool Active { get { return Enabled && _root != null; } }

        public static string RecipeLabel
        {
            get { return _recipe != null && _recipe.source != null &&
                    _recipe.source.bbox != null && _recipe.source.bbox.Length >= 4
                ? "Foggia (ricetta OSM)"
                : "Foggia (default)"; }
        }

        /// <summary>Lettura senza effetti collaterali: nessun evento, nessun uovo.</summary>
        public static void Initialize()
        {
            if (_initialized) return;
            _initialized = true;
            Enabled = PlayerPrefs.GetInt(PrefKey, 0) == 1;
            _recipe = LoadRecipe();
        }

        private static RecipeDoc LoadRecipe()
        {
            try
            {
                TextAsset asset = Resources.Load<TextAsset>(RecipePath);
                if (asset == null)
                {
                    Debug.LogWarning("[CittaGioco] ricetta non trovata: " + RecipePath);
                    return null;
                }
                RecipeDoc doc = JsonUtility.FromJson<RecipeDoc>(asset.text);
                if (doc == null || doc.grid == null)
                {
                    Debug.LogWarning("[CittaGioco] ricetta non valida: " + RecipePath);
                    return null;
                }
                return doc;
            }
            catch (Exception e)
            {
                Debug.LogWarning("[CittaGioco] lettura ricetta: " + e.Message);
                return null;
            }
        }

        private static RecipeDoc RecipeOrFallback()
        {
            if (_recipe != null) return _recipe;
            var doc = new RecipeDoc();
            doc.seed = 3880082521L;
            doc.grid = new GridDoc();
            doc.grid.road_spacing_m = 177.6f;
            doc.grid.road_width_m = 6f;
            doc.grid.block_m = 171.7f;
            doc.grid.park_ratio = 0.03f;
            doc.grid.buildings_per_km2 = 270f;
            doc.poi_mix = new MixDoc();
            doc.poi_mix.bar = 0.65f;
            doc.poi_mix.school = 0.26f;
            doc.poi_mix.hospital = 0.06f;
            doc.poi_mix.repair = 0.02f;
            doc.poi_mix.dealer = 0.01f;
            return doc;
        }

        /// <summary>
        /// Cambia modalita'. notify=false e' il ripristino da Android: non
        /// nasconde uova e non rimanda l'evento. notify=true e' una scelta del
        /// giocatore: bridge, telemetria, uovo e applicazione immediata.
        /// </summary>
        public static void SetMode(bool on, bool notify)
        {
            Initialize();
            bool changed = Enabled != on;
            Enabled = on;
            PlayerPrefs.SetInt(PrefKey, on ? 1 : 0);
            PlayerPrefs.Save();

            if (notify)
            {
                long ts = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                string mode = on ? "fictional" : "real";
                string json = "{\"mode\":\"" + mode +
                    "\",\"recipe\":\"" + (on ? "foggia" : "osm") +
                    "\",\"tsMt\":" + ts + "}";
                try { Huntix.Bridge.UnityBridge.SendMessageToAndroid("WorldModeChanged", json); }
                catch (Exception e)
                {
                    OsmDiag.Log("[CittaGioco] WorldModeChanged failed: " + e.Message);
                }
                if (SessionTelemetry.Instance != null)
                    SessionTelemetry.Instance.Mark("world=%s", mode);
                // Applica PRIMA di nascondere l'uovo: GrantFeatureEgg legge la
                // posizione del player, che viene riportata all'origine qui.
                bool applied = ApplyNow(on);
                if (on && changed && applied && Game.Instance != null)
                    Game.Instance.OnFictionalCityEntered();
            }
            else if (Game.Instance != null && Game.Instance.player != null &&
                     CityChunkedWorld.Instance != null &&
                     CityChunkedWorld.Instance.PlayerSpawned)
            {
                // Ripristino arrivato a scena gia' attiva: applica senza uova.
                ApplyNow(on);
            }
        }

        /// <summary>
        /// Applica la modalita' a caldo: scarica i chunk OSM (senza distruggerli
        /// definitivamente) e mette/ toglie il quartiere fittizio. I sistemi
        /// restano tutti presenti: cambia solo cosa viene streamato.
        /// </summary>
        private static bool ApplyNow(bool on)
        {
            var game = Game.Instance;
            var cw = CityChunkedWorld.Instance;
            if (game == null || game.player == null || cw == null ||
                !cw.PlayerSpawned) return false;

            if (on)
            {
                if (game.IsDriving ||
                    (Interior.InteriorManager.Instance != null &&
                     Interior.InteriorManager.Instance.IsInside))
                {
                    game.ui.ShowToast("Esci dall'auto o dall'edificio prima di cambiare mondo");
                    return false;
                }
                if (_root != null) return true;
                if (cw.Manager != null)
                {
                    cw.Manager.UnloadAll();
                    cw.Manager.StreamingEnabled = false;
                    cw.Manager.StopProgressReporting();
                }
                BuildWorld();
            }
            else
            {
                DestroyWorld();
                if (cw.Manager != null) cw.Manager.StreamingEnabled = true;
            }

            // L'origine geografica non cambia: la ricetta descrive solo la
            // forma del quartiere, non una teletrasporto verso un'altra citta'.
            var cc = game.player.GetComponent<CharacterController>();
            float feet = cc != null ? cc.height * 0.5f - cc.center.y : 1f;
            game.player.transform.position = new Vector3(0f, feet, 0f);
            Physics.SyncTransforms();
            if (game.ui != null)
                game.ui.ShowToast(on
                    ? "Mondo: Città Gioco — quartiere fittizio da ricetta OSM"
                    : "Mondo: Reale — torno alla città OSM");
            return true;
        }

        /// <summary>
        /// Costruisce il quartiere. Chiamato anche all'ingresso in Miacitta,
        /// prima del gate di spawn, quando il player e' ancora congelato.
        /// </summary>
        public static void BuildWorld()
        {
            Initialize();
            if (_root != null) return;
            using (PerfProbe.Measure("fictional"))
            {
                RecipeDoc r = RecipeOrFallback();
                GridDoc g = r.grid;
                float spacing = Mathf.Clamp(g.road_spacing_m, 70f, 400f);
                float width = Mathf.Clamp(g.road_width_m, 4f, 14f);
                float block = Mathf.Clamp(g.block_m, 30f, 360f);
                if (block >= spacing) block = spacing - width;
                float parkRatio = Mathf.Clamp(g.park_ratio, 0f, 0.5f);
                float density = Mathf.Clamp(g.buildings_per_km2, 0f, 2000f);

                var rng = new System.Random((int)(r.seed & 0x7fffffff));
                float extent = spacing * (BlocksPerSide + 1);
                float half = extent * 0.5f;
                _root = new GameObject(RootName);

                Material groundMat = Mat(new Color(0.20f, 0.23f, 0.25f));
                Material roadMat = Mat(new Color(0.29f, 0.31f, 0.34f));
                Material parkMat = Mat(new Color(0.22f, 0.40f, 0.24f));
                Material[] houseMats =
                {
                    Mat(new Color(0.62f, 0.57f, 0.49f)),
                    Mat(new Color(0.54f, 0.49f, 0.52f)),
                    Mat(new Color(0.68f, 0.63f, 0.56f)),
                    Mat(new Color(0.47f, 0.51f, 0.56f)),
                };
                Material poiMat = Mat(new Color(0.95f, 0.72f, 0.22f));

                // Suolo UNICO, piano, con collider: niente DEM e nessuna mesh
                // di terreno. La faccia superiore deve stare esattamente a
                // y=0 (centro -0.25, spessore 0.5): e' la quota dello
                // SpawnBridge e dello snap del player. Se la faccia stesse a
                // +0.25 strade (top +0.06) e parchi (top +0.10) resterebbero
                // sepolti dentro il suolo.
                Box("Suolo", new Vector3(0f, -0.25f, 0f),
                    new Vector3(extent + 80f, 0.5f, extent + 80f),
                    groundMat, _root.transform, false, true);

                // Punti d'erba sui due lati di ogni strada: nelle prime build
                // i dettagli naturali ricevevano SOLO i centri dei parchi
                // (1-2 nel quartiere), quindi l'erba era praticamente invisibile.
                var vergePoints = new System.Collections.Generic.List<Vector2>();
                for (int i = 0; i <= BlocksPerSide; i++)
                {
                    float p = -half + i * spacing;
                    Box("StradaNS_" + i, new Vector3(p, 0.03f, 0f),
                        new Vector3(width, 0.06f, extent), roadMat, _root.transform);
                    Box("StradaEO_" + i, new Vector3(0f, 0.04f, p),
                        new Vector3(extent, 0.06f, width), roadMat, _root.transform);

                    float verge = width * 0.5f + 1.4f;
                    for (float t = -half + 8f; t < half - 8f; t += 13f)
                    {
                        vergePoints.Add(new Vector2(p + verge, t));
                        vergePoints.Add(new Vector2(p - verge, t));
                        vergePoints.Add(new Vector2(t, p + verge));
                        vergePoints.Add(new Vector2(t, p - verge));
                    }
                }
                // Ordine a stride: se il budget di istanze viene raggiunto, i
                // ciuffi coprono uniformemente tutte le strade invece di
                // esaurirsi sulle prime due.
                var orderedVerge = new System.Collections.Generic.List<Vector2>(vergePoints.Count);
                const int stride = 11;
                for (int s = 0; s < stride && s < vergePoints.Count; s++)
                    for (int i = s; i < vergePoints.Count; i += stride)
                        orderedVerge.Add(vergePoints[i]);

                // Confinamento morale del quartiere: muri bassi, non cambiano
                // la fisica del mondo reale perche' vivono sotto questo root.
                float edge = half + width * 0.5f;
                Box("BordoN", new Vector3(0f, 1.5f, edge),
                    new Vector3(extent, 3f, 1f), groundMat, _root.transform, true);
                Box("BordoS", new Vector3(0f, 1.5f, -edge),
                    new Vector3(extent, 3f, 1f), groundMat, _root.transform, true);
                Box("BordoE", new Vector3(edge, 1.5f, 0f),
                    new Vector3(1f, 3f, extent), groundMat, _root.transform, true);
                Box("BordoO", new Vector3(-edge, 1.5f, 0f),
                    new Vector3(1f, 3f, extent), groundMat, _root.transform, true);

                int parks = 0, buildings = 0;
                var parkCenters = new System.Collections.Generic.List<Vector2>();
                float cellAreaKm2 = block * block / 1000000f;
                int targetBuildings = Mathf.Clamp(
                    Mathf.RoundToInt(density * cellAreaKm2), 1, 9);

                for (int ix = 0; ix < BlocksPerSide; ix++)
                {
                    for (int iz = 0; iz < BlocksPerSide; iz++)
                    {
                        float cx = -half + (ix + 0.5f) * spacing;
                        float cz = -half + (iz + 0.5f) * spacing;
                        bool park = (double)rng.NextDouble() < parkRatio;
                        if (ix == 0 && iz == 0) park = true; // almeno un parco

                        if (park)
                        {
                            parks++;
                            Box("Parco_" + ix + "_" + iz,
                                new Vector3(cx, 0.05f, cz),
                                new Vector3(block * 0.88f, 0.1f, block * 0.88f),
                                parkMat, _root.transform);
                            parkCenters.Add(new Vector2(cx, cz));
                            continue;
                        }

                        int slots = Math.Max(2, targetBuildings);
                        for (int s = 0; s < slots; s++)
                        {
                            int col = s % 3;
                            int row = s / 3;
                            float slot = block / 3f;
                            float bx = cx - block * 0.33f + col * slot +
                                ((float)rng.NextDouble() - 0.5f) * 3f;
                            float bz = cz - block * 0.33f + row * slot +
                                ((float)rng.NextDouble() - 0.5f) * 3f;
                            float h = 5f + (float)rng.NextDouble() * 17f;
                            float sx = slot * (0.55f + (float)rng.NextDouble() * 0.30f);
                            float sz = slot * (0.55f + (float)rng.NextDouble() * 0.30f);
                            Box("Casa_" + ix + "_" + iz + "_" + s,
                                new Vector3(bx, h * 0.5f, bz),
                                new Vector3(sx, h, sz),
                                houseMats[rng.Next(houseMats.Length)],
                                _root.transform, true);
                            buildings++;
                        }
                    }
                }

                // Stessi dettagli naturali dei chunk OSM, adattati al suolo
                // piatto della Citta' Gioco (nessun tile/DEM a runtime).
                try
                {
                    if (parkCenters.Count > 0 && _root != null)
                    {
                        int vegSeed = (int)(r.seed & 0x7fffffff);
                        // I parchi restano densi; le strade ricevono ciuffi
                        // piccoli e distribuiti, senza alberi sull'asfalto.
                        NatureDetailPlacer.BuildFlat(_root.transform, parkCenters,
                            0.12f, vegSeed, 900f, 8f, 8, 14);
                        if (orderedVerge.Count > 0)
                            NatureDetailPlacer.BuildFlat(_root.transform, orderedVerge,
                                0.12f, vegSeed ^ 0x5eed, 2600f, 2.4f, 2, 4, true);
                        var c = parkCenters[0];
                        VegetationFeatureBridge.GrantParkEgg("citta-gioco",
                            _root.transform.position + new Vector3(c.x, 0.65f, c.y),
                            "città→città-gioco→parco→vegetazione");
                    }
                }
                catch (Exception e)
                {
                    OsmDiag.Log("[CittaGioco] dettagli naturali non disponibili: " + e.Message);
                }

                int pois = PlacePois(rng, spacing, half, poiMat);
                Physics.SyncTransforms();
                OsmDiag.Log("[CittaGioco] costruita: seed=" + r.seed +
                    " passo=" + spacing.ToString("F1") +
                    " strada=" + width.ToString("F1") +
                    " isolati=" + (BlocksPerSide * BlocksPerSide) +
                    " parchi=" + parks + " edifici=" + buildings +
                    " poi=" + pois + " (nessun tile/DEM a runtime)");
                if (SessionTelemetry.Instance != null)
                    SessionTelemetry.Instance.Mark("citta-gioco: edifici=%d poi=%d",
                        buildings, pois);
            }
        }

        private static int PlacePois(System.Random rng, float spacing, float half,
            Material marker)
        {
            MixDoc mix = RecipeOrFallback().poi_mix;
            float total = mix.bar + mix.school + mix.hospital +
                mix.repair + mix.dealer + mix.garage + mix.rampa;
            if (total <= 0f) return 0;
            int count = 10;
            for (int i = 0; i < count; i++)
            {
                double pick = rng.NextDouble() * total;
                string kind;
                if (pick < mix.bar) kind = "bar";
                else if (pick < mix.bar + mix.school) kind = "school";
                else if (pick < mix.bar + mix.school + mix.hospital) kind = "hospital";
                else if (pick < mix.bar + mix.school + mix.hospital + mix.repair) kind = "repair";
                else if (pick < mix.bar + mix.school + mix.hospital + mix.repair + mix.dealer) kind = "dealer";
                else kind = "garage";

                int cell = rng.Next(BlocksPerSide * BlocksPerSide);
                float x = -half + ((cell % BlocksPerSide) + 0.5f) * spacing;
                float z = -half + ((cell / BlocksPerSide) + 0.5f) * spacing;
                Box("POI_" + kind + "_" + i, new Vector3(x, 1.2f, z),
                    new Vector3(2.4f, 2.4f, 2.4f), marker, _root.transform);
            }
            return count;
        }

        private static Material Mat(Color c)
        {
            Shader s = Shader.Find("Universal Render Pipeline/Lit");
            if (s == null) s = Shader.Find("Standard");
            var m = new Material(s);
            m.color = c;
            return m;
        }

        /// <summary>Cubo con materiale condiviso: collider solo dove serve.</summary>
        private static GameObject Box(string name, Vector3 pos, Vector3 size,
            Material mat, Transform parent, bool building = false,
            bool keepCollider = false)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.position = pos;
            go.transform.localScale = size;
            var r = go.GetComponent<MeshRenderer>();
            if (r != null) r.sharedMaterial = mat;
            var col = go.GetComponent<BoxCollider>();
            if (col != null && !(building || keepCollider)) col.enabled = false;
            if (building) go.layer = GroundSnapper.BuildingLayer;
            return go;
        }

        /// <summary>Distrugge solo il root fittizio: i sistemi OSM restano intatti.</summary>
        public static void DestroyWorld()
        {
            if (_root == null) return;
            UnityEngine.Object.Destroy(_root);
            _root = null;
            OsmDiag.Log("[CittaGioco] quartiere rimosso ( mondo OSM intatto )");
        }
    }
}
