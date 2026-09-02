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
            try
            {
                chunk.terrainGo = TerrainChunk.Create(chunk.root.transform, "Terreno", bounds);
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
            bool stradeOk = false;
            if (geo.roads != null && geo.roads.Length > 0)
            {
                try
                {
                    Mesh sidewalkMesh;
                    Mesh roadMesh = RoadRenderer.Build(
                        geo.roads, ToLocal, bounds, chunk.root.transform,
                        out sidewalkMesh);
                    if (roadMesh != null)
                    {
                        chunk.roadsGo = new GameObject("Strade",
                            typeof(MeshFilter), typeof(MeshRenderer));
                        chunk.roadsGo.transform.SetParent(chunk.root.transform, false);
                        chunk.roadsGo.GetComponent<MeshFilter>().sharedMesh = roadMesh;
                        chunk.roadsGo.GetComponent<MeshRenderer>().sharedMaterial =
                            mgr.SharedRoadMaterial;
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
                    stradeOk = true;
                    OsmDiag.Log("[Builder] " + chunk.key +
                        " stradeIn=" + geo.roads.Length +
                        " stradeVerts=" + (roadMesh != null ? roadMesh.vertexCount : -1) +
                        " marciapiediVerts=" + (sidewalkMesh != null ? sidewalkMesh.vertexCount : -1) +
                        " matStrada=" + (mgr.SharedRoadMaterial != null &&
                            mgr.SharedRoadMaterial.shader != null
                            ? mgr.SharedRoadMaterial.shader.name : "NULL"));
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
                int placed = 0, scanned = 0, skipped = 0;
                foreach (var b in mergedBuildings)
                {
                    if (b?.c == null || b.c.Length < 2) continue;

                    // protezione per-record: un dato anomalo non deve costare
                    // il chunk intero (su Roma: 32k edifici per tile)
                    try
                    {
                        var ll = new GeoLL { a = b.c[0], o = b.c[1] };
                        var p = ToLocal(ll);
                        if (!bounds.Contains(new Vector2(p.x, p.z))) continue;
                        if (poiSpots != null && poiSpots.Count > 0 &&
                            IsNearPoi(poiSpots, p)) continue;
                        if (BuildingPlacer.Place(mgr.Registry, chunk.buildingsGo.transform, b, p))
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
                if (skipped > 3)
                    UnityEngine.Debug.LogWarning("[ChunkBuilder] " + chunk.key +
                        " edifici saltati totali: " + skipped);
            }

            // ── aeroporti: piste + velivoli (dati OSM aeroway=aerodrome) ──
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
            try
            {
                Vehicle.ChunkVehiclePopulator.Populate(chunk, ToLocal, bounds);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE sezione VEICOLI: " + e);
            }

            // ── pedoni sui marciapiedi (deterministici, animati, parlanti) ──
            try
            {
                NPC.NPCPopulator.Populate(chunk, ToLocal, bounds);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE sezione NPC: " + e);
            }

            // ── uova raccoglibili (missioni CollectEggs) ──
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

            // ── POI veicoli: concessionarie / officine / garage da OSM ──
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

            // ── segnali stradali con distanze POI ──
            try
            {
                Vehicle.RoadSignSpawner.Populate(chunk, ToLocal, bounds);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkBuilder] " + chunk.key +
                    " ERRORE segnali stradali: " + e);
            }

            // ── arredo urbano interattivo + POI dagli edifici OSM ──
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
            OsmDiag.Log("[Builder] === BUILD DONE === " + chunk.key + " totalMs=" + totalClock.ElapsedMilliseconds + "ms");

            // telemetria: utile per capire tempi/contenuti dei chunk grandi
            if (!stradeOk && geo.roads != null && geo.roads.Length > 0)
                UnityEngine.Debug.LogWarning("[ChunkBuilder] " + chunk.key +
                    " completato SENZA strade (sezione in errore)");
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
