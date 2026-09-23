using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Costruisce il contenuto di un chunk a partire dalle tile (graph + geo)
    /// gia' caricate. Lavora a step con budget di millisecondi per frame per
    /// non scatenare lag durante lo streaming.
    ///
    /// Ogni sezione (terreno, strade, edifici, natura, veicoli) e' isolata:
    /// un'eccezione in una sezione NON abortisce piu' la costruzione del chunk
    /// (prima il chunk restava senza built=true -> niente LOD/collider) ma
    /// viene loggata con il nome della sezione e la costruzione prosegue.
    /// Negli edifici la protezione e' per-record: un solo edificio "velenoso"
    /// viene saltato invece di buttare via tutto il chunk.
    /// </summary>
    public static class ChunkBuilder
    {
        private const int BuildingsPerStep = 40;
        private const int TreesPerStep = 120;

        /// <summary>Sezione (italiano) attualmente in costruzione nell'ultimo
        /// chunk processato: alimenta lo splash "Sto caricando: X". Letto da
        /// ChunkManager per il report CityProgress; vuota se nessuna build.</summary>
        public static string CurrentBuildSection = "";

        private static void Section(string name) { CurrentBuildSection = name; }

        public static IEnumerator Build(ChunkManager mgr, ChunkData chunk,
            TileGeoDoc geo, Stopwatch clock, long budgetMs)
        {
            // ── guardie difensive: se arriva qualcosa di nullo logghiamo
            // esattamente cosa manca invece di fare NRE silenzioso nel prologo
            if (mgr == null) { UnityEngine.Debug.LogError("[ChunkBuilder] mgr == null"); yield break; }
            if (chunk == null) { UnityEngine.Debug.LogError("[ChunkBuilder] chunk == null"); yield break; }
            if (chunk.key == null) { UnityEngine.Debug.LogError("[ChunkBuilder] chunk.key == null index=" + (chunk.index != null ? chunk.index.ToString() : "null")); yield break; }
            if (geo == null) { UnityEngine.Debug.LogError("[ChunkBuilder] geo == null chunk=" + chunk.key); yield break; }
            if (clock == null) { UnityEngine.Debug.LogError("[ChunkBuilder] clock == null"); yield break; }

            var totalClock = Stopwatch.StartNew();
            OsmDiag.Log("[Builder] === BUILD START === " + chunk.key + " roads=" + (geo.roads != null ? geo.roads.Length : 0) + " buildings=" + (geo.buildings != null ? geo.buildings.Length : 0));
            chunk.root = new GameObject(chunk.key);
            if (mgr.ChunkRootParent == null)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " mgr.ChunkRootParent == null (ChunkManager not fully started?)");
                yield break;
            }
            chunk.root.transform.SetParent(mgr.ChunkRootParent, false);

            // WorldOrigin potrebbe non essere inizializzato se Start() non ha ancora
            // girato (race con primo tick). ToWorld usa statici _lat/_lng che
            // valgono 0 prima di Init -> posiziona chunk a 0,0,0 invece che nel
            // punto giusto, ma NON fa NRE. Proteggiamo comunque.
            if (!WorldOrigin.Initialized)
            {
                UnityEngine.Debug.LogWarning("[ChunkBuilder] " + chunk.key +
                    " WorldOrigin non inizializzato, chiamo Init default");
                WorldOrigin.Init(41.9028, 12.4964);
            }
            chunk.root.transform.position = WorldOrigin.ToWorld(chunk.center);

            Vector3 originWorld = chunk.root.transform.position;

            System.Func<GeoLL, Vector3> ToLocal = ll =>
            {
                var w = WorldOrigin.ToWorld(ll.a, ll.o);
                return new Vector3(w.x - originWorld.x, w.y, w.z - originWorld.z);
            };

            // ── area REALE del chunk ──
            // Il passo della griglia e' in gradi (CityGrid): proiettato in metri
            // una colonna chunk a Foggia e' larga ~1010 m, una riga profonda
            // ~995 m. Usare un rettangolo fisso 1000x1000 lasciava ~10 m di
            // vuoto senza collider fra le colonne (la "striscia" dove si cade).
            // Proiettando gli angoli geografici i bordi condivisi combaciano:
            // entrambi i chunk passano dallo stesso ToWorld in double.
            // Guardiamo chunk.index valido (non default 0,0 se non ha senso)
            if (chunk.index == default(Vector2Int) && chunk.key != "C_0000_0000")
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " chunk.index == default (0,0) ma key non coincide -> corruzione dati");
            }
            GeoCoord cornerSW_geo;
            try { cornerSW_geo = CityGrid.ChunkCorner(chunk.index); }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " CityGrid.ChunkCorner(chunk.index) fallito: " + e);
                yield break;
            }
            Vector3 cornerSW = ToLocal(new GeoLL {
                a = cornerSW_geo.lat,
                o = cornerSW_geo.lng });
            var idxNE = new Vector2Int(chunk.index.x + 1, chunk.index.y + 1);
            GeoCoord cornerNE_geo;
            try { cornerNE_geo = CityGrid.ChunkCorner(idxNE); }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " CityGrid.ChunkCorner(idxNE) fallito: " + e);
                yield break;
            }
            Vector3 cornerNE = ToLocal(new GeoLL {
                a = cornerNE_geo.lat,
                o = cornerNE_geo.lng });
            Rect bounds = new Rect(
                cornerSW.x, cornerSW.z,
                cornerNE.x - cornerSW.x, cornerNE.z - cornerSW.z);

            // ── terreno ──
            Section("Terreno");
            Dictionary<Vector2, float> terrainHeights = null;
            try
            {
                // NB: demLattice=true solo se le altezze vengono dalla griglia
                // DEM regolare (~15 m). Col proxy edifici (sparso) il terreno
                // resta sul nearest-bucket. La bilineare fa combaciare il
                // terreno con le strade (stessa griglia ele) anche sui pendii.
                bool dem = geo != null && geo.ele != null && geo.ele.Length > 0 &&
                    geo.ele_nrow > 1 && geo.ele_ncol > 1 &&
                    geo.bbox != null && geo.bbox.Length >= 4;
                terrainHeights = CollectTerrainHeights(geo, ToLocal, bounds, originWorld);
                chunk.terrainGo = TerrainChunk.Create(chunk.root.transform, "Terreno",
                    bounds, terrainHeights, dem);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE sezione TERRENO: " + e);
            }

            Mesh terrMesh = chunk.terrainGo != null
                ? chunk.terrainGo.GetComponent<MeshFilter>().sharedMesh : null;
            Material terrMat = chunk.terrainGo != null
                ? chunk.terrainGo.GetComponent<MeshRenderer>().sharedMaterial : null;
            OsmDiag.Log("[Builder] " + chunk.key +
                " terrenoVerts=" + (terrMesh != null ? terrMesh.vertexCount : -1) +
                " matTerreno=" + (terrMat != null && terrMat.shader != null
                    ? terrMat.shader.name : "NULL"));

            // ── strade (una mesh sola) + targhette col nome delle vie ──
            Section("Strade");
            bool stradeOk = false;
            if (geo.roads != null && geo.roads.Length > 0)
            {
                try
                {
                    Mesh sidewalkMesh;
                    Mesh deckColMesh;
                    Mesh roadMesh = RoadRenderer.Build(
                        geo.roads, ToLocal, bounds, chunk.root.transform,
                        out sidewalkMesh, out deckColMesh);
                    if (roadMesh != null)
                    {
                        chunk.roadsGo = new GameObject("Strade",
                            typeof(MeshFilter), typeof(MeshRenderer),
                            typeof(MeshCollider));
                        chunk.roadsGo.transform.SetParent(chunk.root.transform, false);
                        chunk.roadsGo.GetComponent<MeshFilter>().sharedMesh = roadMesh;
                        chunk.roadsGo.GetComponent<MeshRenderer>().sharedMaterial =
                            mgr.SharedRoadMaterial;
                        // Collider fisico = asfalto VISIBILE (quota roadMesh):
                        // il player/NPC camminano sulla strada vera, niente
                        // sospensione tra terreno (-0.05) e asfalto (0.03).
                        chunk.roadsGo.GetComponent<MeshCollider>().sharedMesh =
                            roadMesh;
                    }
                    // Marciapiedi rialzati: mesh separata (materiale chiaro) con
                    // MeshCollider che fa da cordolo alle auto. Il collider si
                    // accende/spegne col LOD0 in ChunkData.SetLod.
                    if (sidewalkMesh != null)
                    {
                        chunk.sidewalksGo = new GameObject("Marciapiedi",
                            typeof(MeshFilter), typeof(MeshRenderer),
                            typeof(MeshCollider));
                        chunk.sidewalksGo.transform.SetParent(chunk.root.transform, false);
                        chunk.sidewalksGo.GetComponent<MeshFilter>().sharedMesh = sidewalkMesh;
                        chunk.sidewalksGo.GetComponent<MeshRenderer>().sharedMaterial =
                            mgr.SharedSidewalkMaterial;
                        chunk.sidewalksGo.GetComponent<MeshCollider>().sharedMesh = sidewalkMesh;
                    }
                    // Viadotti/gallerie: l'impalcato sospeso non ha marciapiede
                    // (l'unica mesh stradale dotata di collider), quindi restava
                    // una superficie puramente VISIVA e il player/le auto
                    // passavano ATTRAVERSO il ponte. Un collider dedicato,
                    // invisibile (niente renderer), rende percorribile il deck.
                    if (deckColMesh != null)
                    {
                        var deckColGo = new GameObject("DeckColliders",
                            typeof(MeshCollider));
                        deckColGo.transform.SetParent(chunk.root.transform, false);
                        deckColGo.GetComponent<MeshCollider>().sharedMesh = deckColMesh;
                    }
                    stradeOk = true;
                    OsmDiag.Log("[Builder] " + chunk.key +
                        " stradeIn=" + geo.roads.Length +
                        " stradeVerts=" + (roadMesh != null ? roadMesh.vertexCount : -1) +
                        " marciapiediVerts=" + (sidewalkMesh != null ? sidewalkMesh.vertexCount : -1) +
                        " deckColVerts=" + (deckColMesh != null ? deckColMesh.vertexCount : -1) +
                        " matStrada=" + (mgr.SharedRoadMaterial != null &&
                            mgr.SharedRoadMaterial.shader != null
                            ? mgr.SharedRoadMaterial.shader.name : "NULL"));
                    // stradeVerts=-1 NON e' un errore: RoadRenderer.Build ritorna
                    // una mesh nulla quando nessuna way OSM del tile cade dentro
                    // i bounds del chunk (cella di campagna/mare/agricolo senza
                    // strade). Distinguiamo esplicitamente il caso benigno da un
                    // eventuale errore di geometria (che viene loggato qui sopra
                    // come LogError dal catch).
                    if (roadMesh == null)
                        OsmDiag.Log("[Builder] " + chunk.key +
                            " stradeIn=" + geo.roads.Length +
                            " ma 0 geometria nei bounds: cella SENZA strade (OK, edifici si)");
                }
                catch (System.Exception e)
                {
                    UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                        " ERRORE sezione STRADE: " + e);
                }
                if (clock.ElapsedMilliseconds > budgetMs) { clock.Reset(); clock.Start(); yield return null; }
            }
            else
            {
                OsmDiag.Log("[Builder] " + chunk.key + " stradeIn=0");
            }

            // ── edifici (placement record -> prefab Kenney) ──
            Section("Edifici");
            chunk.buildingsGo = new GameObject("Edifici");
            chunk.buildingsGo.transform.SetParent(chunk.root.transform, false);
            BuildingPlacer.ResetChunkBudget();

            // Fase 3.5: fonde gli edifici adiacenti (muri condivisi) in blocchi
            // unici, così il prefab Kenney viene piazzato una volta sola per
            // l'intero isolato anziché uno per ogni record OSM separato.
            // Merge disabilitato (troppo lento su device reali: 26s per
            // 1121 edifici, O(n^2) in confronti spigoli). Piazziamo i record
            // OSM tal quali; il prefab Kenney gestisce ogni edificio singolo.
            TileBuildingRec[] mergedBuildings = geo.buildings;
            OsmDiag.Log("[Builder] " + chunk.key +
                " edifici OSM: " + (geo.buildings != null ? geo.buildings.Length : 0) +
                " (merge disabilitato)");
            if (clock.ElapsedMilliseconds > budgetMs) { clock.Reset(); clock.Start(); yield return null; }

            // i lotti delle concessionarie/officine/garage sono nostri:
            // gli edifici generici OSM che caderci sopra vengono saltati
            List<Vector3> poiSpots = null;
            try { poiSpots = Vehicle.VehiclePoiPlacer.CollectLocalPositions(geo, ToLocal); }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE raccolta POI veicoli: " + e);
                poiSpots = null;
            }

            // yield before heavy building placement
            if (clock.ElapsedMilliseconds > budgetMs) { clock.Reset(); clock.Start(); yield return null; }

            if (mergedBuildings != null)
            {
                int placed = 0, scanned = 0, skipped = 0, skipDegenerate = 0;
                foreach (var b in mergedBuildings)
                {
                    if (b?.c == null || b.c.Length < 2) { skipDegenerate++; continue; }

                    // protezione per-record: un dato anomalo non deve costare
                    // il chunk intero (su Roma: 32k edifici per tile)
                    try
                    {
                        var ll = new GeoLL { a = b.c[0], o = b.c[1] };
                        var p = ToLocal(ll);
                        if (!bounds.Contains(new Vector2(p.x, p.z))) continue;
                        if (poiSpots != null && poiSpots.Count > 0 &&
                            IsNearPoi(poiSpots, p)) continue;
                        if (BuildingPlacer.Place(mgr.Registry, chunk.buildingsGo.transform, b, p, terrainHeights))
                            placed++;
                    }
                    catch (System.Exception e)
                    {
                        skipped++;
                        if (skipped <= 3)
                            UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                                " edificio saltato id=" + b.id + " t=" + b.t + ": " + e);
                    }

                    if (++scanned % BuildingsPerStep == 0 &&
                        clock.ElapsedMilliseconds > budgetMs)
                    { clock.Reset(); clock.Start(); yield return null; }
                }
                OsmDiag.Log("[Builder] " + chunk.key +
                    " edificiTotali=" + scanned +
                    " posizionati=" + placed +
                    " falliti=" + (scanned - placed - skipped - skipDegenerate) +
                    " degeneri=" + skipDegenerate +
                    " erroriPerRecord=" + skipped);
                if (skipped > 3)
                    UnityEngine.Debug.LogWarning("[ChunkBuilder] " + chunk.key +
                        " edifici saltati totali: " + skipped);
            }

            // ── aeroporti: piste + velivoli (dati OSM aeroway=aerodrome) ──
            Section("Aeroporti");
            try
            {
                int airCount = AirportRenderer.Build(chunk, geo, ToLocal, bounds);
                if (airCount > 0)
                    OsmDiag.Log("[Builder] " + chunk.key + " aeroporti=" + airCount);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE sezione AEROPORTI: " + e);
            }
            if (clock.ElapsedMilliseconds > budgetMs) { clock.Reset(); clock.Start(); yield return null; }

            // ── natura e arredo ──
            Section("Alberi e verde");
            chunk.natureGo = new GameObject("Natura");
            chunk.natureGo.transform.SetParent(chunk.root.transform, false);
            try
            {
                VegetationPlacer.Build(chunk.natureGo.transform, geo, ToLocal, bounds);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE sezione NATURA: " + e);
            }
            if (clock.ElapsedMilliseconds > budgetMs) { clock.Reset(); clock.Start(); yield return null; }

            // yield after heavy sections to keep frame budget
            yield return null;

            // ── veicoli: parcheggi deterministici + traffico AI ──
            Section("Veicoli");
            try
            {
                Vehicle.ChunkVehiclePopulator.Populate(chunk, ToLocal, bounds);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE sezione VEICOLI: " + e);
            }
            if (clock.ElapsedMilliseconds > budgetMs) { clock.Reset(); clock.Start(); yield return null; }

            // ── pedoni sui marciapiedi (deterministici, animati, parlanti) ──
            Section("Pedoni");
            try
            {
                NPC.NPCPopulator.Populate(chunk, ToLocal, bounds);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE sezione NPC: " + e);
            }
            if (clock.ElapsedMilliseconds > budgetMs) { clock.Reset(); clock.Start(); yield return null; }

            // ── uova raccoglibili (missioni CollectEggs) ──
            Section("Uova");
            try
            {
                City.Economy.EggSpawnManager.Instance?.SpawnEggsInChunk(
                    chunk.root.transform, geo, ToLocal, bounds,
                    unchecked(chunk.index.x * 73856093 ^ chunk.index.y * 19349663));
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE sezione UOVA: " + e);
            }
            if (clock.ElapsedMilliseconds > budgetMs) { clock.Reset(); clock.Start(); yield return null; }

            // ── POI veicoli: concessionarie / officine / garage da OSM ──
            Section("Concessionarie");
            try
            {
                int poiCount = Vehicle.VehiclePoiPlacer.Populate(chunk, ToLocal, bounds);
                if (poiCount > 0)
                    OsmDiag.Log("[Builder] " + chunk.key + " poiVeicoli=" + poiCount);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE sezione POI VEICOLI: " + e);
            }
            if (clock.ElapsedMilliseconds > budgetMs) { clock.Reset(); clock.Start(); yield return null; }

            // ── segnali stradali con distanze POI ──
            Section("Segnali");
            try
            {
                Vehicle.RoadSignSpawner.Populate(chunk, ToLocal, bounds);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE segnali stradali: " + e);
            }
            if (clock.ElapsedMilliseconds > budgetMs) { clock.Reset(); clock.Start(); yield return null; }

            // ── arredo urbano interattivo + POI dagli edifici OSM ──
            Section("Arredo urbano");
            try
            {
                City.Environment.PropSpawner.Populate(chunk, ToLocal, bounds);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE sezione PROPS: " + e);
            }

            chunk.built = true;
            chunk.lod = -1;
            chunk.SetLod(0);
            // I collider creati a runtime in questo chunk (terreno, strade,
            // edifici) non sono ancora registrati in PhysX: con
            // Physics.autoSyncTransforms off, il CharacterController puo'
            // attraversarli per qualche frame e il player precipita sotto il
            // terreno (mesh one-sided: da sotto non collide piu'). Un sync
            // esplicito rende i collider subito visibili alla fisica.
            Physics.SyncTransforms();
            Section("");
            OsmDiag.Log("[Builder] === BUILD DONE === " + chunk.key + " totalMs=" + totalClock.ElapsedMilliseconds + "ms");

            // telemetria: utile per capire tempi/contenuti dei chunk grandi
            if (!stradeOk && geo.roads != null && geo.roads.Length > 0)
                UnityEngine.Debug.LogWarning("[ChunkBuilder] " + chunk.key +
                    " completato SENZA strade (sezione in errore)");
        }

        /// <summary>
        /// Racoglie le altezze degli edifici dal geo-document del chunk e le
        /// restituisce come dizionario {Vector2 (posizione locale XZ) -> altezza
        /// in metri}. Usato per generare il terreno con altezza realistica invece
        /// di y=0 piatto. NB: va chiamato DURANTE l'elaborazione del geo, PRIMA
        /// che gli edifici vengano istanziati (gli istanziati vengono dopo il
        /// terreno).
        /// </summary>
        private static Dictionary<Vector2, float> CollectTerrainHeights(
            TileGeoDoc geo, System.Func<GeoLL, Vector3> toLocal,
            Rect bounds, Vector3 originWorld)
        {
            // MODALITA' PIATTA (default): nessuna altimetria, nemmeno il
            // proxy edifici. Terreno, strade ed edifici tornano tutti a
            // quota 0 come nel ramo unity (piedi per terra).
            if (CityConfig.WorldHeights == WorldHeightMode.Flat)
                return new Dictionary<Vector2, float>();

            // Elevazione REALE (DEM/SRTM) se il server l'ha iniettata nella geo:
            // griglia 'ele' row-major sul bbox della tile. Usa la bilineare.
            if (geo != null && geo.ele != null && geo.ele.Length > 0 &&
                geo.ele_nrow > 1 && geo.ele_ncol > 1 &&
                geo.bbox != null && geo.bbox.Length >= 4)
            {
                return CollectDemHeights(geo, bounds, originWorld);
            }

            // Fallback (nessun DEM): proxy altimetrico dalle altezze edifici.
            var heights = new Dictionary<Vector2, float>();
            if (geo == null || geo.buildings == null) return heights;

            var buildings = geo.buildings;
            for (int i = 0; i < buildings.Length; i++)
            {
                var b = buildings[i];
                if (b == null || b.c == null || b.c.Length < 2) continue;

                float h;
                BuildingPlacer.PickPrefabName(b, out h, 0);

                var ll = new GeoLL { a = b.c[0], o = b.c[1] };
                var p = toLocal(ll);
                if (p.x < bounds.xMin || p.x > bounds.xMax ||
                    p.z < bounds.yMin || p.z > bounds.yMax) continue;

                int gx = Mathf.RoundToInt(p.x / 10f) * 10;
                int gz = Mathf.RoundToInt(p.z / 10f) * 10;
                var key = new Vector2(gx, gz);
                float cur;
                if (!heights.TryGetValue(key, out cur) || h > cur)
                    heights[key] = h;
            }
            return heights;
        }

        /// <summary>Risolve l'elevazione reale (s.l.m.) su una griglia fine dentro
        /// il chunk, bilineando la griglia DEM fornita dal server. Ritorna un
        /// dizionario {(x,z) locali -> quote metri} consumabile da TerrainChunk.</summary>
        private static Dictionary<Vector2, float> CollectDemHeights(
            TileGeoDoc geo, Rect bounds, Vector3 originWorld)
        {
            var heights = new Dictionary<Vector2, float>();
            int nrow = geo.ele_nrow;
            int ncol = geo.ele_ncol;
            double latMin = geo.bbox[0], lonMin = geo.bbox[1];
            double latMax = geo.bbox[2], lonMax = geo.bbox[3];
            float[] ele = geo.ele;

            // Griglia di campionamento ~ ogni 15 m per avere un vertice near per
            // ogni vertice del terreno (33x33 su ~1000 m) -> interpolazione pulita.
            const float step = 15f;
            int nx = Mathf.Max(2, Mathf.CeilToInt(bounds.width / step));
            int nz = Mathf.Max(2, Mathf.CeilToInt(bounds.height / step));
            for (int i = 0; i <= nx; i++)
            {
                float x = bounds.xMin + bounds.width * i / nx;
                for (int j = 0; j <= nz; j++)
                {
                    float z = bounds.yMin + bounds.height * j / nz;
                    // locali -> mondo -> lat/lon (world y=0 non influisce)
                    var w = new Vector3(originWorld.x + x, 0f, originWorld.z + z);
                    var g = WorldOrigin.ToGeo(w);
                    float h = CityConfig.ApplyMode(SampleBilinear(ele, nrow,
                        ncol, latMin, lonMin, latMax, lonMax, g.lat, g.lng));
                    heights[new Vector2(x, z)] = h;
                }
            }
            return heights;
        }

        /// <summary>Interpolazione bilineare dell'elevazione sul bbox della tile.
        /// Coordinate griglia: riga 0 = latMin (sud, come scritto da dem.py),
        /// colonna 0 = lonMin (ovest). Fuori dal bbox clampa al bordo piu' vicino.</summary>
        private static float SampleBilinear(float[] ele, int nrow, int ncol,
            double latMin, double lonMin, double latMax, double lonMax,
            double lat, double lon)
        {
            if (ele == null || nrow <= 1 || ncol <= 1) return 0f;
            double fy = (lat - latMin) / (latMax - latMin) * (nrow - 1);
            double fx = (lon - lonMin) / (lonMax - lonMin) * (ncol - 1);
            fy = Clamp(fy, 0, nrow - 1);
            fx = Clamp(fx, 0, ncol - 1);
            int y0 = (int)fy, x0 = (int)fx;
            int y1 = Mathf.Min(y0 + 1, nrow - 1);
            int x1 = Mathf.Min(x0 + 1, ncol - 1);
            float dy = (float)(fy - y0);
            float dx = (float)(fx - x0);

            float v00 = ele[y0 * ncol + x0];
            float v10 = ele[y1 * ncol + x0];
            float v01 = ele[y0 * ncol + x1];
            float v11 = ele[y1 * ncol + x1];
            return Mathf.Lerp(Mathf.Lerp(v00, v01, dx),
                             Mathf.Lerp(v10, v11, dx), dy);
        }

        private static double Clamp(double v, double lo, double hi)
        {
            return v < lo ? lo : (v > hi ? hi : v);
        }

        /// <summary>
        /// Ricostruisce SOLO il layer strade (asfalto + marciapiedi + targhette
        /// "Via") di un chunk gia' costruito. Serve quando arriva una tile DEM
        /// nuova: alla build originale quei punti cadevano fuori dal registro
        /// TileElevation (MISS -> y=0) e le strade restavano sepolte sotto un
        /// terreno rialzato. Il riallineamento non tocca terreno/edifici/natura.
        /// </summary>
        public static void BuildRoadsOnly(ChunkManager mgr, ChunkData chunk)
        {
            if (mgr == null || chunk == null || chunk.root == null ||
                chunk.geo == null || !chunk.built)
                return;
            TileGeoDoc geo = chunk.geo;
            if (geo.roads == null || geo.roads.Length == 0)
            {
                OsmDiag.Log("[Builder] " + chunk.key +
                    " roads-only: geo senza strade, niente da riallineare");
                return;
            }

            Vector3 originWorld = chunk.root.transform.position;
            System.Func<GeoLL, Vector3> ToLocal = ll =>
            {
                var w = WorldOrigin.ToWorld(ll.a, ll.o);
                return new Vector3(w.x - originWorld.x, w.y, w.z - originWorld.z);
            };
            GeoCoord sw = CityGrid.ChunkCorner(chunk.index);
            Vector3 swLocal = ToLocal(new GeoLL { a = sw.lat, o = sw.lng });
            var idxNE = new Vector2Int(chunk.index.x + 1, chunk.index.y + 1);
            GeoCoord ne = CityGrid.ChunkCorner(idxNE);
            Vector3 neLocal = ToLocal(new GeoLL { a = ne.lat, o = ne.lng });
            Rect bounds = new Rect(swLocal.x, swLocal.z,
                neLocal.x - swLocal.x, neLocal.z - swLocal.z);

            // le targhette "Via ..." sono figli della root: quelle della build
            // precedente vanno rimosse prima di rigenerarne di nuove, altrimenti
            // a ogni riallineamento si duplicano.
            Transform rootT = chunk.root.transform;
            for (int i = rootT.childCount - 1; i >= 0; i--)
            {
                var child = rootT.GetChild(i);
                if (child != null && child.name.StartsWith("Via "))
                    Object.Destroy(child.gameObject);
            }

            try
            {
                Mesh sidewalkMesh;
                Mesh deckColMesh;
                Mesh roadMesh = RoadRenderer.Build(geo.roads, ToLocal, bounds,
                    rootT, out sidewalkMesh, out deckColMesh);
                if (roadMesh != null)
                {
                    if (chunk.roadsGo == null)
                    {
                        chunk.roadsGo = new GameObject("Strade",
                            typeof(MeshFilter), typeof(MeshRenderer),
                            typeof(MeshCollider));
                        chunk.roadsGo.transform.SetParent(rootT, false);
                        chunk.roadsGo.GetComponent<MeshRenderer>().sharedMaterial =
                            mgr.SharedRoadMaterial;
                    }
                    SwapSharedMesh(chunk.roadsGo, roadMesh);
                    var roadCol = chunk.roadsGo.GetComponent<MeshCollider>();
                    if (roadCol != null) roadCol.sharedMesh = roadMesh;
                }
                if (sidewalkMesh != null)
                {
                    if (chunk.sidewalksGo == null)
                    {
                        chunk.sidewalksGo = new GameObject("Marciapiedi",
                            typeof(MeshFilter), typeof(MeshRenderer),
                            typeof(MeshCollider));
                        chunk.sidewalksGo.transform.SetParent(rootT, false);
                        chunk.sidewalksGo.GetComponent<MeshRenderer>().sharedMaterial =
                            mgr.SharedSidewalkMaterial;
                    }
                    SwapSharedMesh(chunk.sidewalksGo, sidewalkMesh);
                    var col = chunk.sidewalksGo.GetComponent<MeshCollider>();
                    if (col != null) col.sharedMesh = sidewalkMesh;
                }
                if (deckColMesh != null)
                {
                    Transform deckColT = null;
                    for (int i = 0; i < rootT.childCount; i++)
                        if (rootT.GetChild(i).name == "DeckColliders")
                        { deckColT = rootT.GetChild(i); break; }
                    if (deckColT == null)
                    {
                        var go = new GameObject("DeckColliders",
                            typeof(MeshCollider));
                        go.transform.SetParent(rootT, false);
                        deckColT = go.transform;
                    }
                    var mcol = deckColT.GetComponent<MeshCollider>();
                    if (mcol != null) mcol.sharedMesh = deckColMesh;
                }
                OsmDiag.Log("[Builder] " + chunk.key +
                    " roads-only riallineate (DEM aggiornato)");
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE sezione roads-only: " + e);
            }
        }

        /// <summary>Scambia la sharedMesh del GO e butta quella vecchia (una
        /// mesh per build, mai accumularle in memoria).</summary>
        private static void SwapSharedMesh(GameObject go, Mesh fresh)
        {
            var mf = go.GetComponent<MeshFilter>();
            if (mf == null) mf = go.AddComponent<MeshFilter>();
            Mesh old = mf.sharedMesh;
            mf.sharedMesh = fresh;
            if (old != null && old != fresh) Object.Destroy(old);
        }

        /// <summary>Vero se la posizione cade nel lotto libero di un POI
        /// veicolo (dove ChunkBuilder non deve mettere edifici generici).</summary>
        private static bool IsNearPoi(List<Vector3> poiSpots, Vector3 p)
        {
            float r2 = Vehicle.VehiclePoiPlacer.BuildingClearRadius *
                Vehicle.VehiclePoiPlacer.BuildingClearRadius;
            for (int i = 0; i < poiSpots.Count; i++)
            {
                if ((poiSpots[i] - p).sqrMagnitude <= r2) return true;
            }
            return false;
        }
    }
}
