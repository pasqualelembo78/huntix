using System.Collections.Generic;
using UnityEngine;

namespace City.OSM
{
    public static class TerrainChunk
    {
        private const float Y_TERRAIN = -0.05f;
        private static Material _grassMat;

        public static GameObject Create(Transform parent, string name, Rect area,
            Dictionary<Vector2, float> heights = null, bool demLattice = false)
        {
            EnsureMaterial();
            var go = new GameObject(name, typeof(MeshFilter), typeof(MeshRenderer),
                typeof(MeshCollider));
            go.transform.SetParent(parent, false);
            go.transform.localPosition = new Vector3(0f, Y_TERRAIN, 0f);

            Mesh mesh;
            if (heights != null && heights.Count > 0)
                mesh = BuildHeightMesh(area, heights, demLattice);
            else
                mesh = BuildFlatMesh(area);
            mesh.name = "TerrenoChunk";
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();

            go.GetComponent<MeshFilter>().sharedMesh = mesh;
            go.GetComponent<MeshRenderer>().sharedMaterial = _grassMat;
            go.GetComponent<MeshCollider>().sharedMesh = mesh;

            var col = go.GetComponent<MeshCollider>();
            col.sharedMesh = null;
            col.sharedMesh = mesh;
            col.enabled = true;

            return go;
        }

        private static Mesh BuildFlatMesh(Rect area)
        {
            var mesh = new Mesh();
            mesh.vertices = new[]
            {
                new Vector3(area.xMin, 0f, area.yMin),
                new Vector3(area.xMax, 0f, area.yMin),
                new Vector3(area.xMin, 0f, area.yMax),
                new Vector3(area.xMax, 0f, area.yMax),
            };
            mesh.uv = new[] { new Vector2(0, 0), new Vector2(1, 0), new Vector2(0, 1), new Vector2(1, 1) };
            mesh.triangles = new[] { 0, 2, 1, 2, 3, 1 };
            return mesh;
        }

        private struct HeightEntry { public Vector2 key; public float val; }

        private static Mesh BuildHeightMesh(Rect area, Dictionary<Vector2, float> heights,
            bool demLattice = false)
        {
            const float step = 10f;
            int nx = Mathf.Max(2, Mathf.CeilToInt(area.width / step));
            int nz = Mathf.Max(2, Mathf.CeilToInt(area.height / step));

            // Lattices regolari preparate per le due modalita' di sampling.
            // e' una griglia DEM (regolare ~15 m, copre l'intero chunk):
            //   - prima risolvi la griglia di campionamento (nxS+1)x(nzS+1)
            //     come CollectDemHeights, poi interpolazione BILINEARE
            //     (mai nearest-bucket: sul pendio il terreno salirebbe a scale
            //     di 15 m e squarciarebbe le strade a valle / le seppellirebbe
            //     a monte). Rusultato: il terreno combacia ESATTAMENTE con le
            //     strade (che usano la stessa bilineare DEM).
            // senno' (proxy edifici, griglia 10 m SPAZZA): restano i bucket
            //   nearest esistenti, giusti per un proxy non denso.
            float demStep = 15f;
            int nxS = Mathf.Max(2, Mathf.CeilToInt(area.width / demStep));
            int nzS = Mathf.Max(2, Mathf.CeilToInt(area.height / demStep));
            float[,] demLat = null;
            if (demLattice && heights.Count >= (nxS + 1) * (nzS + 1))
            {
                demLat = new float[nxS + 1, nzS + 1];
                float x0 = area.xMin, z0 = area.yMin;
                foreach (var kv in heights)
                {
                    int i = Mathf.RoundToInt((kv.Key.x - x0) / area.width * nxS);
                    int j = Mathf.RoundToInt((kv.Key.y - z0) / area.height * nzS);
                    i = Mathf.Clamp(i, 0, nxS);
                    j = Mathf.Clamp(j, 0, nzS);
                    demLat[i, j] = kv.Value;
                }
            }

            bool useBilinear = demLat != null;
            // Indicizza le altezze note in bucket spaziali (20 m) per cercare la
            // quota del punto PiU' VICINO (solo per il proxy edifici sparso).
            const int bucket = 20;
            var bins = new Dictionary<Vector2Int, List<HeightEntry>>();
            if (!useBilinear)
            {
                foreach (var kv in heights)
                {
                    var b = new Vector2Int(
                        Mathf.FloorToInt(kv.Key.x / bucket),
                        Mathf.FloorToInt(kv.Key.y / bucket));
                    List<HeightEntry> list;
                    if (!bins.TryGetValue(b, out list))
                    {
                        list = new List<HeightEntry>();
                        bins[b] = list;
                    }
                    list.Add(new HeightEntry { key = kv.Key, val = kv.Value });
                }
            }

            var verts = new Vector3[(nx + 1) * (nz + 1)];
            var uvs = new Vector2[(nx + 1) * (nz + 1)];
            var tris = new List<int>();

            for (int j = 0; j <= nz; j++)
            {
                float z = area.yMin + area.height * j / nz;
                for (int i = 0; i <= nx; i++)
                {
                    float x = area.xMin + area.width * i / nx;
                    int idx = j * (nx + 1) + i;
                    float h = useBilinear
                        ? SampleLattice(demLat, nxS, nzS, x, z, area)
                        : NearestHeight(x, z, bins, bucket);
                    if (float.IsNaN(h) || float.IsInfinity(h)) h = 0f;
                    verts[idx] = new Vector3(x, h, z);
                    uvs[idx] = new Vector2((float)i / nx, (float)j / nz);
                }
            }

            for (int j = 0; j < nz; j++)
            {
                for (int i = 0; i < nx; i++)
                {
                    int bl = j * (nx + 1) + i;
                    int br = bl + 1;
                    int tl = bl + (nx + 1);
                    int tr = tl + 1;
                    tris.Add(bl); tris.Add(tl); tris.Add(br);
                    tris.Add(br); tris.Add(tl); tris.Add(tr);
                }
            }

            var mesh = new Mesh();
            mesh.vertices = verts;
            mesh.uv = uvs;
            mesh.SetTriangles(tris, 0);
            return mesh;
        }

        /// <summary>Bilineare su una griglia DEM regolare (nxS+1)x(nzS+1) che
        /// copre l'intero chunk partendo da area.xMin,yMin. Allinea il terreno
        /// esattamente alle strade (che campionano la stessa griglia ele) invece
        /// del nearest-bucket, che sui pendii faceva emergere il terreno a
        /// gradini e ributtare sotto l'asfalto.</summary>
        private static float SampleLattice(float[,] lat, int nxS, int nzS,
            float x, float z, Rect area)
        {
            float fi = (x - area.xMin) / area.width * nxS;
            float fj = (z - area.yMin) / area.height * nzS;
            if (fi <= 0f) return lat[0, Mathf.RoundToInt(Mathf.Clamp(fj, 0, nzS))];
            if (fj <= 0f) return lat[Mathf.RoundToInt(Mathf.Clamp(fi, 0, nxS)), 0];
            int i0 = Mathf.FloorToInt(fi);
            int j0 = Mathf.FloorToInt(fj);
            if (i0 >= nxS) i0 = nxS - 1;
            if (j0 >= nzS) j0 = nzS - 1;
            int i1 = i0 + 1, j1 = j0 + 1;
            float ti = fi - i0;
            float tj = fj - j0;
            float v00 = lat[i0, j0];
            float v10 = lat[i1, j0];
            float v01 = lat[i0, j1];
            float v11 = lat[i1, j1];
            return Mathf.Lerp(Mathf.Lerp(v00, v10, ti), Mathf.Lerp(v01, v11, ti), tj);
        }

        /// <summary>Quota nota piu' vicina al punto, cercata negli anelli di
        /// bucket che circondano la cella (raggio massimo 60 m): usata solo per
        /// il proxy edifici (griglia 10 m sparsa). Restituisce 0 dove nessuna
        /// quota nota copre il punto (comportamento storico a terreno piatto).</summary>
        private static float NearestHeight(float x, float z,
            Dictionary<Vector2Int, List<HeightEntry>> bins, int bucket)
        {
            var bc = new Vector2Int(Mathf.FloorToInt(x / bucket),
                Mathf.FloorToInt(z / bucket));
            for (int ring = 0; ring <= 2; ring++)
            {
                float bestSq = float.MaxValue;
                float bestVal = 0f;
                bool found = false;
                for (int dx = -ring; dx <= ring; dx++)
                {
                    for (int dz = -ring; dz <= ring; dz++)
                    {
                        if (Mathf.Max(Mathf.Abs(dx), Mathf.Abs(dz)) != ring) continue;
                        List<HeightEntry> list;
                        if (!bins.TryGetValue(
                            new Vector2Int(bc.x + dx, bc.y + dz), out list)) continue;
                        for (int k = 0; k < list.Count; k++)
                        {
                            float ddx = list[k].key.x - x;
                            float ddz = list[k].key.y - z;
                            float d = ddx * ddx + ddz * ddz;
                            if (d < bestSq) { bestSq = d; bestVal = list[k].val; found = true; }
                        }
                    }
                }
                if (found) return bestVal;
            }
            return 0f;
        }

        private static void EnsureMaterial()
        {
            if (_grassMat != null) return;
            _grassMat = SafeMaterial(new Color(0.38f, 0.62f, 0.30f), 0.05f);
        }

        private static Material SafeMaterial(Color baseColor, float smoothness)
        {
            Shader shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) shader = Shader.Find("Sprites/Default");
            if (shader == null) return null;
            var mat = new Material(shader);
            if (mat.HasProperty("_BaseColor"))
                mat.SetColor("_BaseColor", baseColor);
            if (mat.HasProperty("_Color"))
                mat.SetColor("_Color", baseColor);
            if (mat.HasProperty("_Smoothness"))
                mat.SetFloat("_Smoothness", smoothness);
            if (mat.HasProperty("_Cull"))
                mat.SetFloat("_Cull", 0f);
            return mat;
        }

        public static Material RoadMaterial()
        {
            var mat = SafeMaterial(new Color(0.27f, 0.27f, 0.30f), 0.15f);
            if (mat == null) mat = new Material(Shader.Find("Legacy Shaders/Diffuse"));
            return mat;
        }

        public static Material ParkMaterial()
        {
            return SafeMaterial(new Color(0.33f, 0.58f, 0.28f), 0f);
        }

        public static Material WaterMaterial()
        {
            var mat = SafeMaterial(new Color(0.2f, 0.45f, 0.8f, 0.7f), 0.95f);
            if (mat != null && mat.HasProperty("_Surface"))
                mat.SetFloat("_Surface", 1f);
            return mat;
        }

        public static Material SidewalkMaterial()
        {
            var mat = SafeMaterial(new Color(0.58f, 0.58f, 0.56f), 0.05f);
            if (mat == null) mat = new Material(Shader.Find("Legacy Shaders/Diffuse"));
            return mat;
        }
    }
}
