using System;
using System.Collections.Generic;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Skyline di un settore lontano: UN solo GameObject, UNA mesh, UNA
    /// materiale condiviso, zero collider, zero luci, zero prefab.
    /// I settori oltre il raggio di scarico restano visibili all'orizzonte ma
    /// non sono esplorabili: le vere case tornano quando il player si avvicina
    /// e ChunkManager ricostruisce i chunk.
    ///
    /// La mesh viene generata dai record OSM della geo in cache (quando c'e')
    /// oppure, in assenza di geo, dal numero di edifici del catalogo con una
    /// disposizione deterministica: nessuna richiesta di rete in questo percorso.
    /// </summary>
    public sealed class WorldStreamingSkyline
    {
        private static readonly Vector3[] CubeCorners =
        {
            new Vector3(-0.5f, 0f, -0.5f), new Vector3(0.5f, 0f, -0.5f),
            new Vector3(0.5f, 0f, 0.5f),   new Vector3(-0.5f, 0f, 0.5f),
            new Vector3(-0.5f, 1f, -0.5f), new Vector3(0.5f, 1f, -0.5f),
            new Vector3(0.5f, 1f, 0.5f),   new Vector3(-0.5f, 1f, 0.5f)
        };

        private static readonly int[] CubeTriangles =
        {
            0, 2, 1, 0, 3, 2,        // basso
            4, 5, 6, 4, 6, 7,        // alto
            0, 1, 5, 0, 5, 4,        // fronte
            1, 2, 6, 1, 6, 5,        // destra
            2, 3, 7, 2, 7, 6,        // retro
            3, 0, 4, 3, 4, 7         // sinistra
        };

        private static Material _material;

        private GameObject _go;
        private Mesh _mesh;
        private MeshRenderer _renderer;

        public string Key { get; private set; }
        public Vector3 Position { get { return _go != null ? _go.transform.position : Vector3.zero; } }
        public bool UsedRealGeo { get; private set; }

        private static Material Material()
        {
            if (_material != null) return _material;
            Shader shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) shader = Shader.Find("Sprites/Default");
            if (shader == null) return null;
            _material = new Material(shader);
            Color c = new Color(0.30f, 0.34f, 0.42f);
            if (_material.HasProperty("_BaseColor")) _material.SetColor("_BaseColor", c);
            if (_material.HasProperty("_Color")) _material.SetColor("_Color", c);
            return _material;
        }

        /// <summary>
        /// Costruisce la silhouette. [center] e' il centro del settore in
        /// coordinate mondo (quelli di WorldOrigin, soggetti a rebase).
        /// </summary>
        public void Build(string key, Vector3 center, TileGeoDoc geo,
                          WorldSectorInfo info, int maxBuildings, Transform parent = null)
        {
            Destroy();
            Key = key;
            if (maxBuildings < 4) maxBuildings = 4;

            var boxes = new List<Box>();
            if (geo != null && geo.buildings != null && geo.buildings.Length > 0)
            {
                UsedRealGeo = true;
                int stride = Math.Max(1, geo.buildings.Length / maxBuildings);
                for (int i = 0; i < geo.buildings.Length && boxes.Count < maxBuildings; i += stride)
                {
                    var b = geo.buildings[i];
                    if (b == null || b.c == null || b.c.Length < 2) continue;
                    var w = WorldOrigin.ToWorld(b.c[0], b.c[1]);
                    float width = 8f;
                    float depth = 8f;
                    if (b.d != null && b.d.Length >= 2)
                    {
                        width = Mathf.Clamp(Math.Abs(b.d[0]), 5f, 26f);
                        depth = Mathf.Clamp(Math.Abs(b.d[1]), 5f, 26f);
                    }
                    long id = b.id < 0 ? -b.id : b.id;
                    float h = 7f + (id % 19) * 1.5f + Mathf.Min(10f, width * 0.25f);
                    boxes.Add(new Box
                    {
                        x = w.x - center.x, z = w.z - center.z,
                        w = width, d = depth, h = h
                    });
                }
            }
            else
            {
                // Settore non ancora scaricato: silhouette deterministica dal
                // catalogo. Nessun file, nessuna rete, nessun alloc per edificio.
                UsedRealGeo = false;
                int count = info != null ? info.buildings : 0;
                int n = Mathf.Clamp(count / 22, 5, maxBuildings);
                uint seed = Hash(key);
                var rng = new System.Random((int)(seed & 0x7fffffff));
                double latMin = 0, latMax = 0, lonMin = 0, lonMax = 0;
                if (info != null && info.bbox != null && info.bbox.Length >= 4)
                {
                    latMin = info.bbox[0]; lonMin = info.bbox[1];
                    latMax = info.bbox[2]; lonMax = info.bbox[3];
                }
                else
                {
                    var g = CityGrid.ChunkCenter(new Vector2Int(0, 0));
                    latMin = g.lat - 0.045; latMax = g.lat + 0.045;
                    lonMin = g.lng - 0.060; lonMax = g.lng + 0.060;
                }
                for (int i = 0; i < n; i++)
                {
                    double lat = latMin + (latMax - latMin) * rng.NextDouble();
                    double lon = lonMin + (lonMax - lonMin) * rng.NextDouble();
                    var w = WorldOrigin.ToWorld(lat, lon);
                    boxes.Add(new Box
                    {
                        x = w.x - center.x, z = w.z - center.z,
                        w = 9f + (float)rng.NextDouble() * 14f,
                        d = 9f + (float)rng.NextDouble() * 14f,
                        h = 7f + (float)rng.NextDouble() * 30f
                    });
                }
            }

            if (boxes.Count == 0) return;

            var verts = new Vector3[boxes.Count * CubeCorners.Length];
            var tris = new int[boxes.Count * CubeTriangles.Length];
            for (int i = 0; i < boxes.Count; i++)
            {
                var b = boxes[i];
                for (int v = 0; v < CubeCorners.Length; v++)
                {
                    var c = CubeCorners[v];
                    verts[i * CubeCorners.Length + v] =
                        new Vector3(b.x + c.x * b.w, c.y * b.h, b.z + c.z * b.d);
                }
                for (int t = 0; t < CubeTriangles.Length; t++)
                    tris[i * CubeTriangles.Length + t] = i * CubeCorners.Length + CubeTriangles[t];
            }
            _mesh = new Mesh();
            _mesh.vertices = verts;
            _mesh.triangles = tris;
            _mesh.RecalculateNormals();
            _mesh.RecalculateBounds();

            _go = new GameObject("Skyline_" + key);
            if (parent != null) _go.transform.SetParent(parent, false);
            _go.transform.position = center;
            var mf = _go.AddComponent<MeshFilter>();
            mf.sharedMesh = _mesh;
            _renderer = _go.AddComponent<MeshRenderer>();
            var mat = Material();
            if (mat != null) _renderer.sharedMaterial = mat;
        }

        /// <summary>Shift del floating origin: come i root dei chunk.</summary>
        public void Shift(Vector3 delta)
        {
            if (_go != null) _go.transform.position -= delta;
        }

        public void SetVisible(bool on)
        {
            if (_renderer != null) _renderer.enabled = on;
        }

        public void Destroy()
        {
            if (_go != null) UnityEngine.Object.Destroy(_go);
            if (_mesh != null) UnityEngine.Object.Destroy(_mesh);
            _go = null;
            _mesh = null;
            _renderer = null;
            UsedRealGeo = false;
        }

        private struct Box { public float x, z, w, d, h; }

        private static uint Hash(string s)
        {
            uint h = 2166136261u;
            for (int i = 0; i < (s != null ? s.Length : 0); i++)
            {
                h ^= s[i];
                h *= 16777619u;
            }
            return h;
        }
    }
}
