using System.Collections.Generic;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Fonde gli edifici OSM adiacenti che condividono un muro (edge-based).
    ///
    /// Algoritmo: per ogni coppia di edifici vicini (griglia spaziale),
    /// confronta i lati reali dei poligoni OSM. Se almeno un lato di A
    /// è vicino e roughly parallelo a un lato di B (entro soglia), i due
    /// edifici condividono un muro e vengono fusi in un unico blocco.
    ///
    /// Vantaggio rispetto al bbox-based: fonde SOLO edifici con un muro
    /// effettivamente condiviso, evitando fusioni spurie tra palazzi
    /// vicini ma separati (es. lati opposti di una via stretta).
    ///
    /// Complessità ~O(n * k) dove k è il numero medio di lati per poligono
    /// (tipicamente 4-8), limitato dalla griglia spaziale.
    /// </summary>
    public static class BuildingMergeHelper
    {
        // ── Soglie ───────────────────────────────────────────────
        // Distanza massima (metri) tra due lati perché vengano
        // considerati "muro condiviso". 1.5 m è sufficiente per
        // edifici OSM con footprint precisi; lascia margine per
        // semplificazione poligono e errori di rounding.
        private const float EdgeThresholdM = 1.5f;

        // Tolleranza sull'angolo tra i lati (gradi). Lati con
        // angolo > 40° non vengono considerati condivisi (muri
        // perpendicolari di edifici adiacenti non sono lo stesso muro).
        private const float ParallelToleranceDeg = 40f;

        // Tolleranza sulla rotazione complessiva (gradi).
        private const float RotationToleranceDeg = 20f;

        // Dimensione cella della griglia spaziale (metri).
        private const float CellSize = 30f;

        // Dimensione massima del blocco fuso (metri).
        private const float MaxMergedSizeM = 80f;

        // Conversione lat/lon → metri (approssimazione locale).
        private const float M_PER_DEG_LAT = 110540f;
        private static float MPerLonDeg(double lat)
        {
            return (float)(System.Math.Cos(lat * System.Math.PI / 180.0) * 111320.0);
        }

        // ── Union-Find (Disjoint Set) ────────────────────────────
        private struct UnionFind
        {
            private int[] parent, rank;

            public UnionFind(int n)
            {
                parent = new int[n];
                rank = new int[n];
                for (int i = 0; i < n; i++) parent[i] = i;
            }

            public int Find(int x)
            {
                while (parent[x] != x)
                {
                    parent[x] = parent[parent[x]]; // path compression
                    x = parent[x];
                }
                return x;
            }

            public void Union(int a, int b)
            {
                a = Find(a); b = Find(b);
                if (a == b) return;
                if (rank[a] < rank[b]) { int t = a; a = b; b = t; }
                parent[b] = a;
                if (rank[a] == rank[b]) rank[a]++;
            }
        }

        // ── Edge: segmento in coordinate locali (metri) ──────────
        private struct Edge
        {
            public float ax, az, bx, bz; // endpoint

            public float LengthSq()
            {
                float dx = bx - ax, dz = bz - az;
                return dx * dx + dz * dz;
            }
        }

        // ── Poligono convertito in coordinate locali ─────────────
        private struct LocalPolygon
        {
            public float centerX, centerZ;
            public Edge[] edges;
        }

        /// <summary>
        /// Converte i punti GeoLL del poligono in coordinate locali (metri)
        /// rispetto al centro dell'edificio, e costruisce gli edge.
        /// </summary>
        private static LocalPolygon BuildLocalPolygon(TileBuildingRec b, float mPerLon)
        {
            float cx = (float)b.c[1]; // lon
            float cz = (float)b.c[0]; // lat
            var pts = b.pts;
            int count = pts.Length;

            var verts = new Vector2[count];
            for (int i = 0; i < count; i++)
            {
                float lx = ((float)pts[i].o - cx) * mPerLon;
                float lz = ((float)pts[i].a - cz) * M_PER_DEG_LAT;
                verts[i] = new Vector2(lx, lz);
            }

            var edges = new Edge[count];
            for (int i = 0; i < count; i++)
            {
                int j = (i + 1) % count;
                edges[i] = new Edge
                {
                    ax = verts[i].x, az = verts[i].y,
                    bx = verts[j].x, bz = verts[j].y
                };
            }

            return new LocalPolygon { centerX = 0f, centerZ = 0f, edges = edges };
        }

        /// <summary>
        /// Distanza minima tra due segmenti 2D.
        /// Se i segmenti si intersecano, ritorna 0.
        /// </summary>
        private static float SegmentDistance(ref Edge a, ref Edge b)
        {
            // Proietta i 4 punti su entrambi i segmenti e cerca il minimo
            float dx1 = a.bx - a.ax, dz1 = a.bz - a.az;
            float dx2 = b.bx - b.ax, dz2 = b.bz - b.az;
            float len1sq = dx1 * dx1 + dz1 * dz1;
            float len2sq = dx2 * dx2 + dz2 * dz2;

            // Caso degenerato: segmento puntiforme
            if (len1sq < 1e-8f && len2sq < 1e-8f)
            {
                float ex = a.ax - b.ax, ez = a.az - b.az;
                return Mathf.Sqrt(ex * ex + ez * ez);
            }
            if (len1sq < 1e-8f) return PointSegmentDist(a.ax, a.az, ref b);
            if (len2sq < 1e-8f) return PointSegmentDist(b.ax, b.az, ref a);

            // Parametri di proiezione [0,1] dei punti di A sul segmento B e viceversa
            // Punto più vicino di A su B
            float t_b = ((a.ax - b.ax) * dx2 + (a.az - b.az) * dz2) / len2sq;
            t_b = Mathf.Clamp01(t_b);
            float closest_bx = b.ax + t_b * dx2;
            float closest_bz = b.az + t_b * dz2;
            float distAtoB = Mathf.Sqrt((a.ax - closest_bx) * (a.ax - closest_bx) +
                                         (a.az - closest_bz) * (a.az - closest_bz));

            // Punto più vicino di B su A
            float t_a = ((b.ax - a.ax) * dx1 + (b.az - a.az) * dz1) / len1sq;
            t_a = Mathf.Clamp01(t_a);
            float closest_ax = a.ax + t_a * dx1;
            float closest_az = a.az + t_a * dz1;
            float distBtoA = Mathf.Sqrt((b.ax - closest_ax) * (b.ax - closest_ax) +
                                         (b.az - closest_az) * (b.az - closest_az));

            return Mathf.Min(distAtoB, distBtoA);
        }

        private static float PointSegmentDist(float px, float pz, ref Edge seg)
        {
            float dx = seg.bx - seg.ax, dz = seg.bz - seg.az;
            float lenSq = dx * dx + dz * dz;
            if (lenSq < 1e-8f)
            {
                float ex = px - seg.ax, ez = pz - seg.az;
                return Mathf.Sqrt(ex * ex + ez * ez);
            }
            float t = Mathf.Clamp01(((px - seg.ax) * dx + (pz - seg.az) * dz) / lenSq);
            float cx = seg.ax + t * dx - px;
            float cz = seg.az + t * dz - pz;
            return Mathf.Sqrt(cx * cx + cz * cz);
        }

        /// <summary>
        /// Calcola l'angolo assoluto (in gradi) tra le direzioni di due edge.
        /// Ritorna un valore in [0, 90] (simmetria per edge invertiti).
        /// </summary>
        private static float EdgeAngleDiff(ref Edge a, ref Edge b)
        {
            float dx1 = a.bx - a.ax, dz1 = a.bz - a.az;
            float dx2 = b.bx - b.ax, dz2 = b.bz - b.az;
            float len1 = Mathf.Sqrt(dx1 * dx1 + dz1 * dz1);
            float len2 = Mathf.Sqrt(dx2 * dx2 + dz2 * dz2);
            if (len1 < 1e-6f || len2 < 1e-6f) return 90f;

            // Normalizza e calcola il coseno dell'angolo
            float dot = (dx1 * dx2 + dz1 * dz2) / (len1 * len2);
            float angle = Mathf.Acos(Mathf.Clamp(Mathf.Abs(dot), 0f, 1f)) * Mathf.Rad2Deg;
            // Se l'angolo > 90, prendi il supplementare (edge possono essere invertiti)
            if (angle > 90f) angle = 180f - angle;
            return angle;
        }

        /// <summary>
        /// Verifica se due edifici condividono un muro:
        /// almeno un lato del poligono A è vicino e roughly parallelo
        /// a un lato del poligono B.
        /// </summary>
        private static bool ShareWall(ref LocalPolygon polyA, ref LocalPolygon polyB)
        {
            Edge[] edgesA = polyA.edges;
            Edge[] edgesB = polyB.edges;

            for (int i = 0; i < edgesA.Length; i++)
            {
                // Quick reject: se la lunghezza dell'edge è troppo diversa, skip
                float lenAi = edgesA[i].LengthSq();
                if (lenAi < 0.01f) continue; // edge troppo corto

                for (int j = 0; j < edgesB.Length; j++)
                {
                    float lenBj = edgesB[j].LengthSq();
                    if (lenBj < 0.01f) continue;

                    // Check 1: i lati devono essere roughly paralleli
                    float angleDiff = EdgeAngleDiff(ref edgesA[i], ref edgesB[j]);
                    if (angleDiff > ParallelToleranceDeg) continue;

                    // Check 2: la distanza tra i segmenti deve essere < threshold
                    float dist = SegmentDistance(ref edgesA[i], ref edgesB[j]);
                    if (dist <= EdgeThresholdM)
                    {
                        return true; // muro condiviso trovato
                    }
                }
            }
            return false;
        }

        /// <summary>
        /// Fusione dei TileBuildingRec (sistema chunked/streaming).
        /// Edge-based: usa i poligoni reali per trovare muri condivisi.
        /// Restituisce una nuova lista con gli edifici fusi.
        /// </summary>
        public static List<TileBuildingRec> MergeBuildings(List<TileBuildingRec> input)
        {
            if (input == null || input.Count <= 1) return input;

            int n = input.Count;

            // ── 1. Costruisci bounding box + poligoni locali ──
            var bboxMinX = new float[n];
            var bboxMaxX = new float[n];
            var bboxMinZ = new float[n];
            var bboxMaxZ = new float[n];
            var polys = new LocalPolygon[n];
            var valid = new bool[n];

            for (int i = 0; i < n; i++)
            {
                var b = input[i];
                if (b.c == null || b.c.Length < 2 || b.d == null || b.d.Length < 2)
                {
                    valid[i] = false;
                    continue;
                }

                float mPerLon = MPerLonDeg(b.c[0]);

                // Usa i poligoni reali se disponibili, altrimenti fallback bbox
                if (b.pts != null && b.pts.Length >= 3)
                {
                    polys[i] = BuildLocalPolygon(b, mPerLon);

                    // Calcola AABB dal poligono reale
                    float minX = float.MaxValue, maxX = float.MinValue;
                    float minZ = float.MaxValue, maxZ = float.MinValue;
                    for (int p = 0; p < polys[i].edges.Length; p++)
                    {
                        var e = polys[i].edges[p];
                        if (e.ax < minX) minX = e.ax;
                        if (e.ax > maxX) maxX = e.ax;
                        if (e.az < minZ) minZ = e.az;
                        if (e.az > maxZ) maxZ = e.az;
                    }
                    bboxMinX[i] = minX;
                    bboxMaxX[i] = maxX;
                    bboxMinZ[i] = minZ;
                    bboxMaxZ[i] = maxZ;
                    valid[i] = true;
                }
                else
                {
                    // Fallback: costruisci un rettangolo dai dati c/d/r
                    float cx = (float)b.c[1];
                    float cz = (float)b.c[0];
                    float hw = b.d[0] * 0.5f;
                    float hd = b.d[1] * 0.5f;
                    float rad = b.r * Mathf.Deg2Rad;
                    float cos = Mathf.Abs(Mathf.Cos(rad));
                    float sin = Mathf.Abs(Mathf.Sin(rad));
                    float ex = hw * cos + hd * sin;
                    float ez = hw * sin + hd * cos;
                    bboxMinX[i] = cx - ex;
                    bboxMaxX[i] = cx + ex;
                    bboxMinZ[i] = cz - ez;
                    bboxMaxZ[i] = cz + ez;

                    // Crea un rettangolo semplice come poligono di fallback
                    float[] fllx = new float[] { -hw, hw, hw, -hw };
                    float[] fllz = new float[] { -hd, -hd, hd, hd };
                    var fbEdges = new Edge[4];
                    for (int v = 0; v < 4; v++)
                    {
                        int v2 = (v + 1) % 4;
                        fbEdges[v] = new Edge
                        {
                            ax = fllx[v], az = fllz[v],
                            bx = fllx[v2], bz = fllz[v2]
                        };
                    }
                    polys[i] = new LocalPolygon { centerX = 0f, centerZ = 0f, edges = fbEdges };
                    valid[i] = true;
                }
            }

            // ── 2. Griglia spaziale ──
            var grid = new Dictionary<Vector2Int, List<int>>();
            for (int i = 0; i < n; i++)
            {
                if (!valid[i]) continue;
                int gx0 = Mathf.FloorToInt(bboxMinX[i] / CellSize);
                int gz0 = Mathf.FloorToInt(bboxMinZ[i] / CellSize);
                int gx1 = Mathf.FloorToInt(bboxMaxX[i] / CellSize);
                int gz1 = Mathf.FloorToInt(bboxMaxZ[i] / CellSize);
                for (int gx = gx0; gx <= gx1; gx++)
                for (int gz = gz0; gz <= gz1; gz++)
                {
                    var key = new Vector2Int(gx, gz);
                    if (!grid.TryGetValue(key, out var list))
                    {
                        list = new List<int>();
                        grid[key] = list;
                    }
                    list.Add(i);
                }
            }

            // ── 3. Union-Find: fusi edifici con muro condiviso ──
            var uf = new UnionFind(n);
            var checkedPairs = new HashSet<long>();

            foreach (var kvp in grid)
            {
                var cell = kvp.Value;
                for (int ci = 0; ci < cell.Count; ci++)
                {
                    int i = cell[ci];
                    if (!valid[i]) continue;

                    for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        var nkey = new Vector2Int(kvp.Key.x + dx, kvp.Key.y + dz);
                        if (!grid.TryGetValue(nkey, out var neighbors)) continue;

                        for (int ni = 0; ni < neighbors.Count; ni++)
                        {
                            int j = neighbors[ni];
                            if (j <= i || !valid[j]) continue;

                            long pairKey = ((long)i << 32) | (uint)j;
                            if (!checkedPairs.Add(pairKey)) continue;

                            // Quick reject AABB con threshold
                            float overlapX = Mathf.Min(bboxMaxX[i], bboxMaxX[j])
                                            - Mathf.Max(bboxMinX[i], bboxMinX[j]);
                            float overlapZ = Mathf.Min(bboxMaxZ[i], bboxMaxZ[j])
                                            - Mathf.Max(bboxMinZ[i], bboxMinZ[j]);
                            if (overlapX < -EdgeThresholdM || overlapZ < -EdgeThresholdM)
                                continue;

                            // Tolleranza rotazione
                            float rotDiff = Mathf.Abs(input[i].r - input[j].r);
                            if (rotDiff > 180f) rotDiff = 360f - rotDiff;
                            if (rotDiff > RotationToleranceDeg) continue;

                            // Edge-based: condividono un muro?
                            if (ShareWall(ref polys[i], ref polys[j]))
                            {
                                uf.Union(i, j);
                            }
                        }
                    }
                }
            }

            // ── 4. Raggruppa per componente connessa ──
            var groups = new Dictionary<int, List<int>>();
            for (int i = 0; i < n; i++)
            {
                if (!valid[i]) continue;
                int root = uf.Find(i);
                if (!groups.TryGetValue(root, out var g))
                {
                    g = new List<int>();
                    groups[root] = g;
                }
                g.Add(i);
            }

            // ── 5. Costruisci records fusi ──
            var result = new List<TileBuildingRec>(groups.Count);
            foreach (var kvp in groups)
            {
                var g = kvp.Value;
                if (g.Count == 1)
                {
                    result.Add(input[g[0]]);
                    continue;
                }

                // Più edifici: fondoni i bounding box reali
                float minX = float.MaxValue, maxX = float.MinValue;
                float minZ = float.MaxValue, maxZ = float.MinValue;
                float sumCx = 0f, sumCz = 0f;
                string mergedType = input[g[0]].t;
                string mergedName = "";
                int count = g.Count;

                for (int k = 0; k < count; k++)
                {
                    int idx = g[k];
                    if (bboxMinX[idx] < minX) minX = bboxMinX[idx];
                    if (bboxMaxX[idx] > maxX) maxX = bboxMaxX[idx];
                    if (bboxMinZ[idx] < minZ) minZ = bboxMinZ[idx];
                    if (bboxMaxZ[idx] > maxZ) maxZ = bboxMaxZ[idx];
                    sumCx += (float)input[idx].c[1];
                    sumCz += (float)input[idx].c[0];

                    if (!string.IsNullOrEmpty(input[idx].t) && input[idx].t != "yes")
                        mergedType = input[idx].t;
                    if (!string.IsNullOrEmpty(input[idx].nm) && string.IsNullOrEmpty(mergedName))
                        mergedName = input[idx].nm;
                }

                float mergedW = maxX - minX;
                float mergedD = maxZ - minZ;

                if (mergedW > MaxMergedSizeM || mergedD > MaxMergedSizeM)
                {
                    for (int k = 0; k < count; k++)
                        result.Add(input[g[k]]);
                    continue;
                }

                result.Add(new TileBuildingRec
                {
                    id = input[g[0]].id,
                    c = new double[] { sumCz / count, sumCx / count },
                    d = new float[] { mergedW, mergedD },
                    r = input[g[0]].r,
                    t = mergedType,
                    nm = mergedName
                });
            }

            return result;
        }

        /// <summary>
        /// Fusione degli OsmBuilding (sistema legacy CityOSMWorld).
        /// Stesso algoritmo ma lavora con GeoPoint[] poligoni reali.
        /// </summary>
        public static List<OsmBuilding> MergeLegacy(List<OsmBuilding> input)
        {
            if (input == null || input.Count <= 1) return input;

            int n = input.Count;

            var bboxMinX = new float[n];
            var bboxMaxX = new float[n];
            var bboxMinZ = new float[n];
            var bboxMaxZ = new float[n];
            var valid = new bool[n];

            for (int i = 0; i < n; i++)
            {
                var b = input[i];
                if (b.points == null || b.points.Length < 3)
                {
                    valid[i] = false;
                    continue;
                }
                valid[i] = true;
                float minX = float.MaxValue, maxX = float.MinValue;
                float minZ = float.MaxValue, maxZ = float.MinValue;
                for (int p = 0; p < b.points.Length; p++)
                {
                    float x = CoordinateConverter.LonToX(b.points[p].lng);
                    float z = CoordinateConverter.LatToZ(b.points[p].lat);
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (z < minZ) minZ = z;
                    if (z > maxZ) maxZ = z;
                }
                bboxMinX[i] = minX;
                bboxMaxX[i] = maxX;
                bboxMinZ[i] = minZ;
                bboxMaxZ[i] = maxZ;
            }

            var grid = new Dictionary<Vector2Int, List<int>>();
            for (int i = 0; i < n; i++)
            {
                if (!valid[i]) continue;
                int gx0 = Mathf.FloorToInt(bboxMinX[i] / CellSize);
                int gz0 = Mathf.FloorToInt(bboxMinZ[i] / CellSize);
                int gx1 = Mathf.FloorToInt(bboxMaxX[i] / CellSize);
                int gz1 = Mathf.FloorToInt(bboxMaxZ[i] / CellSize);
                for (int gx = gx0; gx <= gx1; gx++)
                for (int gz = gz0; gz <= gz1; gz++)
                {
                    var key = new Vector2Int(gx, gz);
                    if (!grid.TryGetValue(key, out var list))
                    {
                        list = new List<int>();
                        grid[key] = list;
                    }
                    list.Add(i);
                }
            }

            var uf = new UnionFind(n);
            var checkedPairs = new HashSet<long>();

            foreach (var kvp in grid)
            {
                var cell = kvp.Value;
                for (int ci = 0; ci < cell.Count; ci++)
                {
                    int i = cell[ci];
                    if (!valid[i]) continue;
                    for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        var nkey = new Vector2Int(kvp.Key.x + dx, kvp.Key.y + dz);
                        if (!grid.TryGetValue(nkey, out var neighbors)) continue;
                        for (int ni = 0; ni < neighbors.Count; ni++)
                        {
                            int j = neighbors[ni];
                            if (j <= i || !valid[j]) continue;

                            long pairKey = ((long)i << 32) | (uint)j;
                            if (!checkedPairs.Add(pairKey)) continue;

                            float overlapX = Mathf.Min(bboxMaxX[i], bboxMaxX[j])
                                            - Mathf.Max(bboxMinX[i], bboxMinX[j]);
                            float overlapZ = Mathf.Min(bboxMaxZ[i], bboxMaxZ[j])
                                            - Mathf.Max(bboxMinZ[i], bboxMinZ[j]);

                            if (overlapX >= -EdgeThresholdM && overlapZ >= -EdgeThresholdM)
                                uf.Union(i, j);
                        }
                    }
                }
            }

            var groups = new Dictionary<int, List<int>>();
            for (int i = 0; i < n; i++)
            {
                if (!valid[i]) continue;
                int root = uf.Find(i);
                if (!groups.TryGetValue(root, out var g))
                {
                    g = new List<int>();
                    groups[root] = g;
                }
                g.Add(i);
            }

            var result = new List<OsmBuilding>(groups.Count);
            foreach (var kvp in groups)
            {
                var g = kvp.Value;
                if (g.Count == 1)
                {
                    result.Add(input[g[0]]);
                    continue;
                }

                int totalPts = 0;
                for (int k = 0; k < g.Count; k++)
                    totalPts += input[g[k]].points.Length;

                var allPts = new GeoPoint[totalPts];
                int offset = 0;
                float maxH = 0f;
                string kind = input[g[0]].kind;
                string name = "";

                for (int k = 0; k < g.Count; k++)
                {
                    var b = input[g[k]];
                    System.Array.Copy(b.points, 0, allPts, offset, b.points.Length);
                    offset += b.points.Length;
                    if (b.height > maxH) maxH = (float)b.height;
                    if (!string.IsNullOrEmpty(b.kind) && b.kind != "yes")
                        kind = b.kind;
                    if (!string.IsNullOrEmpty(b.name) && string.IsNullOrEmpty(name))
                        name = b.name;
                }

                result.Add(new OsmBuilding
                {
                    id = input[g[0]].id,
                    kind = kind,
                    name = name,
                    shop = input[g[0]].shop,
                    amenity = input[g[0]].amenity,
                    height = maxH,
                    levels = input[g[0]].levels,
                    points = allPts
                });
            }

            return result;
        }
    }
}
