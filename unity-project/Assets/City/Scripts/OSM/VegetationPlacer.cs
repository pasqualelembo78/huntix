using System.Collections.Generic;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Vegetazione e arredo del chunk: alberi Kenney (nature kit) sui punti OSM,
    /// parchi come mesh piatta verde dai poligoni semplificati, semafori come
    /// pali minimi. Tutto deterministico dall'id OSM (stesso albero ad ogni load).
    /// </summary>
    public static class VegetationPlacer
    {
        private static GameObject[] _trees;
        private static Material _parkMat;
        private const int MaxTreesPerChunk = 400;

        private static readonly string[] TreeNames =
        {
            "tree_default", "tree_default_dark", "tree_default_fall",
            "tree_cone", "tree_cone_dark", "tree_cone_fall",
            "tree_oak", "tree_oak_dark", "tree_oak_fall",
            "tree_fat", "tree_detailed", "tree_blocks", "tree_palm",
        };

        private static void LoadPrefabs()
        {
            if (_trees != null) return;
            var list = new List<GameObject>();
            foreach (var n in TreeNames)
            {
                var p = Resources.Load<GameObject>("Nature/" + n);
                if (p != null) list.Add(p);
            }
            if (list.Count == 0)
                foreach (var p in Resources.LoadAll<GameObject>("Nature/"))
                    if (p.name.StartsWith("tree_")) list.Add(p);
            _trees = list.ToArray();
        }

        public static void Build(Transform parent, TileGeoDoc geo,
            System.Func<GeoLL, Vector3> toLocal, Rect localBounds)
        {
            LoadPrefabs();

            int placed = 0;
            if (_trees.Length > 0 && geo.trees != null)
            {
                foreach (var t in geo.trees)
                {
                    if (t == null || placed >= MaxTreesPerChunk) break;
                    var p = toLocal(t);
                    if (!localBounds.Contains(new Vector2(p.x, p.z))) continue;
                    int h = Hash(t.a.GetHashCode() ^ t.o.GetHashCode());
                    var tree = UnityEngine.Object.Instantiate(
                        _trees[h % _trees.Length], parent);
                    tree.transform.localPosition = new Vector3(p.x, 0f, p.z);
                    float s = 0.8f + (h % 50) / 100f;
                    tree.transform.localScale = Vector3.one * s;
                    tree.transform.localRotation = Quaternion.Euler(0f, h % 360, 0f);
                    placed++;
                }
            }

            if (geo.parks != null)
            {
                foreach (var park in geo.parks)
                {
                    if (park?.poly == null || park.poly.Length < 3) continue;
                    // il parco appartiene a UN solo chunk: quello del suo centroide,
                    // altrimenti ogni chunk della tile lo duplicherebbe
                    float clat = 0f, clng = 0f;
                    for (int i = 0; i < park.poly.Length; i++)
                    { clat += (float)park.poly[i].a; clng += (float)park.poly[i].o; }
                    var cGeo = new GeoLL
                    { a = clat / park.poly.Length, o = clng / park.poly.Length };
                    var cp = toLocal(cGeo);
                    var expanded = new Rect(localBounds.x - 200f, localBounds.y - 200f,
                        localBounds.width + 400f, localBounds.height + 400f);
                    if (!expanded.Contains(new Vector2(cp.x, cp.z))) continue;
                    BuildPark(parent, park, toLocal);
                }
            }

            if (geo.signals != null)
            {
                int signals = 0;
                foreach (var s in geo.signals)
                {
                    if (s == null || signals >= 60) break;
                    var p = toLocal(s);
                    if (!localBounds.Contains(new Vector2(p.x, p.z))) continue;
                    BuildSignal(parent, p);
                    signals++;
                }
            }
        }

        private static void BuildPark(Transform parent, TileParkRec park,
            System.Func<GeoLL, Vector3> toLocal)
        {
            if (park?.poly == null || park.poly.Length < 3) return;

            var verts = new List<Vector3>();
            var tris = new List<int>();
            for (int i = 0; i < park.poly.Length; i++)
            {
                var p = toLocal(park.poly[i]);
                verts.Add(new Vector3(p.x, 0.02f, p.z));
            }
            if (verts.Count < 3) return;
            // L'ordine dei vertici OSM e' arbitrario: calcolo l'area firmata
            // e scelgo il winding che guarda in su (come il terreno: orario).
            float area2 = 0f;
            for (int i = 0; i < verts.Count; i++)
            {
                var a = verts[i];
                var b = verts[(i + 1) % verts.Count];
                area2 += a.x * b.z - b.x * a.z;
            }
            bool ccw = area2 > 0f;
            for (int i = 1; i < verts.Count - 1; i++)
            {
                if (ccw) { tris.Add(0); tris.Add(i + 1); tris.Add(i); }
                else { tris.Add(0); tris.Add(i); tris.Add(i + 1); }
            }

            var mesh = new Mesh { name = "Parco" };
            mesh.SetVertices(verts);
            mesh.SetTriangles(tris, 0);
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();

            var go = new GameObject("Parco " + park.id,
                typeof(MeshFilter), typeof(MeshRenderer));
            go.transform.SetParent(parent, false);
            go.GetComponent<MeshFilter>().sharedMesh = mesh;
            if (_parkMat == null) _parkMat = TerrainChunk.ParkMaterial();
            go.GetComponent<MeshRenderer>().sharedMaterial = _parkMat;
        }

        private static void BuildSignal(Transform parent, Vector3 p)
        {
            var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            pole.name = "Semaforo";
            pole.transform.SetParent(parent, false);
            pole.transform.localPosition = new Vector3(p.x, 1.5f, p.z);
            pole.transform.localScale = new Vector3(0.12f, 1.5f, 0.12f);

            var head = GameObject.CreatePrimitive(PrimitiveType.Cube);
            head.name = "SemaforoTesta";
            head.transform.SetParent(pole.transform, false);
            head.transform.localPosition = new Vector3(0f, 0.55f, 0.12f);
            head.transform.localScale = new Vector3(1.6f, 4f, 0.8f);
        }

        private static int Hash(int x)
        {
            uint u = (uint)x;
            u ^= u >> 16; u *= 0x7feb352d; u ^= u >> 15; u *= 0x846ca68b; u ^= u >> 16;
            return (int)(u & 0x7fffffff);
        }
    }
}
